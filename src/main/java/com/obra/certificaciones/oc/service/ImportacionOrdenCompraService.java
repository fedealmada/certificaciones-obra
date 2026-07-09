package com.obra.certificaciones.oc.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.obra.certificaciones.categoria.entity.CategoriaOrden;
import com.obra.certificaciones.categoria.repository.CategoriaOrdenRepository;
import com.obra.certificaciones.oc.dto.ImportacionOrdenCompraForm;
import com.obra.certificaciones.oc.dto.ImportacionOrdenCompraItemForm;
import com.obra.certificaciones.oc.dto.ImportacionOrdenCompraLoteForm;
import com.obra.certificaciones.oc.dto.OrdenCompraForm;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.repository.OrdenCompraRepository;
import com.obra.certificaciones.proveedor.entity.Proveedor;
import com.obra.certificaciones.proveedor.repository.ProveedorRepository;
import com.obra.certificaciones.rubro.entity.Rubro;
import com.obra.certificaciones.rubro.repository.RubroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ImportacionOrdenCompraService {
    private static final Pattern OC_PATTERN = Pattern.compile("(?i)\\b(?:N.?\\s*ORDEN|NRO\\.?\\s*ORDEN|OC|ORDEN\\s+DE\\s+COMPRA)\\s*(?:N.?|NUMERO|NRO|#|:)?\\s*([A-Z0-9.-]+)");
    private static final Pattern OC_COMPACTA_PATTERN = Pattern.compile("(?m)^\\s*:\\s*([0-9]{5}-[0-9]{6,})\\s*$");
    private static final Pattern FECHA_PATTERN = Pattern.compile("(?i)\\b(?:FECHA\\s+EMISI.N|FECHA|EMISION)\\s*:?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}-\\d{1,2}-\\d{1,2})");
    private static final Pattern FECHA_COMPACTA_PATTERN = Pattern.compile("(?m)^\\s*:\\s*(\\d{1,2}/\\d{1,2}/\\d{4})\\s*$");
    private static final Pattern VIGENCIA_PATTERN = Pattern.compile("(?i)\\bFECHA\\s+VIGENCIA\\s*:?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}-\\d{1,2}-\\d{1,2})");
    private static final Pattern PROVEEDOR_PATTERN = Pattern.compile("(?i)\\b(?:PROVEEDOR|CONTRATISTA)\\s*:?\\s*([^\\n\\r]+)");
    private static final Pattern PROVEEDOR_COMPACTO_PATTERN = Pattern.compile("(?m)^\\s*:\\s*(\\d{3,}\\s+[^\\n\\r]+?)\\s*$");
    private static final Pattern ITEM_PATTERN = Pattern.compile("^([A-Z]?\\d+(?:[.,]\\d+)*)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODIGO_ITEM_PATTERN = Pattern.compile("^[A-Z]{2,}(?:/[A-Z0-9.]+)+$");
    private static final Pattern BLOQUE_CODIGO_PATTERN = Pattern.compile("(?ims)^\\s*[A-Z]{2,}(?:/[A-Z0-9.]+)+\\s+.*?(?=^\\s*[A-Z]{2,}(?:/[A-Z0-9.]+)+\\s+|^\\s*CCSIMENDE\\b|\\z)");
    private static final List<String> UNIDADES = List.of("GL", "UN", "UND", "M2", "M3", "M", "ML", "KG", "TN", "HS", "DIA", "DIAS", "MES", "L", "LT", "BOLSA", "PISO", "PASES", "DPTO", "SG");

    private final OrdenCompraService ordenCompraService;
    private final OrdenCompraRepository ordenCompraRepository;
    private final ProveedorRepository proveedorRepository;
    private final CategoriaOrdenRepository categoriaOrdenRepository;
    private final RubroRepository rubroRepository;

    public ImportacionOrdenCompraLoteForm previsualizar(MultipartFile[] archivos) {
        if (archivos == null || archivos.length == 0) {
            throw new IllegalArgumentException("Tenes que adjuntar uno o mas archivos PDF o TXT.");
        }
        ImportacionOrdenCompraLoteForm lote = new ImportacionOrdenCompraLoteForm();
        for (MultipartFile archivo : archivos) {
            if (archivo == null || archivo.isEmpty()) {
                continue;
            }
            lote.getOrdenes().add(previsualizar(archivo));
        }
        if (lote.getOrdenes().isEmpty()) {
            throw new IllegalArgumentException("No encontre archivos con contenido para importar.");
        }
        return lote;
    }

    public ImportacionOrdenCompraForm previsualizar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Tenes que adjuntar un archivo PDF o TXT.");
        }
        String texto = extraerTexto(archivo);
        if (!StringUtils.hasText(texto)) {
            throw new IllegalArgumentException("No pude leer texto del archivo. Si es un PDF escaneado como imagen, primero hay que pasarlo por OCR.");
        }

        ImportacionOrdenCompraForm form = new ImportacionOrdenCompraForm();
        form.setArchivoNombre(archivo.getOriginalFilename());
        form.setTextoExtraido(texto);
        form.setNumero(buscar(OC_PATTERN, texto).or(() -> buscar(OC_COMPACTA_PATTERN, texto)).map(this::normalizarNumeroOc).orElse(""));
        form.setFecha(buscar(FECHA_PATTERN, texto).or(() -> buscar(FECHA_COMPACTA_PATTERN, texto)).flatMap(this::parsearFecha).orElse(LocalDate.now()));
        form.setFechaVigencia(buscar(VIGENCIA_PATTERN, texto).flatMap(this::parsearFecha).orElse(form.getFecha()));
        form.setProveedorSugerido(limpiarProveedor(buscar(PROVEEDOR_PATTERN, texto).or(() -> buscarProveedorCompacto(texto)).orElse("")));
        form.setProveedorId(buscarProveedorExistente(form.getProveedorSugerido()).map(Proveedor::getId).orElse(null));
        if (form.getProveedorId() == null && StringUtils.hasText(form.getProveedorSugerido())) {
            form.setProveedorNuevo(form.getProveedorSugerido());
        }
        form.setCategoriaId(buscarCategoriaExistente(CategoriaItem.MANO_OBRA, "Mano de obra")
                .map(CategoriaOrden::getId)
                .orElseGet(() -> categoriaOrdenRepository.findByActivoTrueOrderByNombreAsc().stream().findFirst().map(CategoriaOrden::getId).orElse(null)));
        form.setRubroId(null);
        form.setObservacion("Importado desde " + archivo.getOriginalFilename());
        form.setItems(parsearItems(texto, form.getNumero()));
        detectarOrdenExistente(form);
        return form;
    }

    @Transactional
    public OrdenCompra importar(ImportacionOrdenCompraForm form) {
        validar(form);
        List<ImportacionOrdenCompraItemForm> itemsImportados = finalizarItems(form.getItems(), form.getNumero());
        OrdenCompraForm ordenForm = new OrdenCompraForm();
        ordenForm.setNumero(form.getNumero());
        ordenForm.setFecha(form.getFecha());
        ordenForm.setFechaVigencia(form.getFechaVigencia());
        ordenForm.setProveedorId(resolverProveedor(form));
        ordenForm.setObservacion(form.getObservacion());
        ordenForm.setItems(itemsImportados.stream()
                .filter(item -> StringUtils.hasText(item.getDetalle()) || StringUtils.hasText(item.getItem()))
                .map(item -> convertirItem(item, form))
                .toList());
        return ordenCompraService.guardar(ordenForm);
    }

    @Transactional
    public List<OrdenCompra> importarLote(ImportacionOrdenCompraLoteForm lote) {
        if (lote == null || lote.getOrdenes() == null || lote.getOrdenes().isEmpty()) {
            throw new IllegalArgumentException("No hay ordenes para importar.");
        }
        List<OrdenCompra> importadas = new ArrayList<>();
        for (ImportacionOrdenCompraForm form : lote.getOrdenes()) {
            if (form.isSeleccionado()) {
                importadas.add(importar(form));
            }
        }
        if (importadas.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos una OC para importar.");
        }
        return importadas;
    }

    private String extraerTexto(MultipartFile archivo) {
        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (nombre.endsWith(".pdf") || "application/pdf".equalsIgnoreCase(archivo.getContentType())) {
                PdfReader reader = new PdfReader(archivo.getBytes());
                try {
                    PdfTextExtractor extractor = new PdfTextExtractor(reader);
                    StringBuilder texto = new StringBuilder();
                    for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
                        texto.append('\n').append(extractor.getTextFromPage(pagina));
                    }
                    return texto.toString();
                } finally {
                    reader.close();
                }
            }
            return new String(archivo.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalArgumentException("No pude leer el archivo adjunto.");
        }
    }

    private List<ImportacionOrdenCompraItemForm> parsearItems(String texto, String numeroOc) {
        List<ImportacionOrdenCompraItemForm> items = parsearBloquesConCodigo(texto, numeroOc);
        if (!items.isEmpty()) {
            return finalizarItems(items, numeroOc);
        }

        for (String lineaOriginal : texto.split("\\R")) {
            String linea = normalizarEspacios(lineaOriginal);
            if (!StringUtils.hasText(linea) || esLineaDescartable(linea)) {
                continue;
            }
            parsearLineaItem(linea).ifPresent(items::add);
        }
        if (items.isEmpty()) {
            ImportacionOrdenCompraItemForm item = new ImportacionOrdenCompraItemForm();
            item.setDetalle("Completar detalle detectado manualmente");
            item.setUnidad("GL");
            item.setCantidad(BigDecimal.ONE);
            item.setPrecioUnitario(BigDecimal.ZERO);
            item.setImporte(BigDecimal.ZERO);
            items.add(item);
        }
        return finalizarItems(items, numeroOc);
    }

    private List<ImportacionOrdenCompraItemForm> parsearBloquesConCodigo(String texto, String numeroOc) {
        List<ImportacionOrdenCompraItemForm> items = new ArrayList<>();
        String textoNormalizado = texto.replace("\r\n", "\n").replaceAll("(?m)(?<!^)\\s+(?=[A-Z]{2,}(?:/[A-Z0-9.]+)+\\s+)", "\n");
        Matcher matcher = BLOQUE_CODIGO_PATTERN.matcher(textoNormalizado);
        int indice = 1;
        while (matcher.find()) {
            Optional<ImportacionOrdenCompraItemForm> item = parsearBloqueConCodigo(matcher.group(), numeroOc, indice);
            if (item.isPresent()) {
                items.add(item.get());
                indice++;
            }
        }
        return items;
    }

    private Optional<ImportacionOrdenCompraItemForm> parsearBloqueConCodigo(String bloque, String numeroOc, int indice) {
        String[] lineas = bloque.lines().map(this::normalizarEspacios).filter(StringUtils::hasText).toArray(String[]::new);
        if (lineas.length == 0) {
            return Optional.empty();
        }

        String cabecera = compactarNumerosPartidos(lineas[0]);
        String detalleExtra = compactarNumerosPartidos(String.join(" ", Arrays.copyOfRange(lineas, 1, lineas.length)));

        Optional<ImportacionOrdenCompraItemForm> simple = parsearBloqueSimple(cabecera, detalleExtra, numeroOc, indice);
        if (simple.isPresent()) {
            return simple;
        }

        return parsearBloqueManoObra(detalleExtra, numeroOc, indice);
    }

    private Optional<ImportacionOrdenCompraItemForm> parsearBloqueSimple(String cabecera, String detalleExtra, String numeroOc, int indice) {
        if (normalizarTexto(cabecera).contains("mano de obra")) {
            return Optional.empty();
        }
        String[] partes = cabecera.split(" ");
        if (partes.length < 5 || !CODIGO_ITEM_PATTERN.matcher(partes[0]).matches()) {
            return Optional.empty();
        }
        List<Integer> posicionesNumeros = posicionesUltimosNumeros(partes, 4);
        if (posicionesNumeros.size() < 4) {
            return Optional.empty();
        }
        List<BigDecimal> numeros = posicionesNumeros.stream()
                .map(posicion -> parsearDecimal(partes[posicion]).orElse(BigDecimal.ZERO))
                .toList();
        int descripcionFin = posicionesNumeros.get(0);
        String descripcion = String.join(" ", Arrays.copyOfRange(partes, 1, descripcionFin)).trim();
        String extra = limpiarTextoPosterior(detalleExtra);
        if (StringUtils.hasText(extra)) {
            descripcion = (descripcion + " " + extra).trim();
        }

        ImportacionOrdenCompraItemForm item = crearItem(numeroOc, indice);
        item.setDetalle(descripcion);
        item.setUnidad("UN");
        item.setCantidad(numeros.get(0));
        item.setPrecioUnitario(numeros.get(1));
        item.setImporte(numeros.get(3));
        return Optional.of(item);
    }

    private Optional<ImportacionOrdenCompraItemForm> parsearBloqueManoObra(String detalleCompleto, String numeroOc, int indice) {
        if (!StringUtils.hasText(detalleCompleto)) {
            return Optional.empty();
        }
        String[] partes = detalleCompleto.split(" ");
        int unidadIndex = buscarUltimaUnidad(partes);
        if (unidadIndex < 0) {
            ImportacionOrdenCompraItemForm item = crearItem(numeroOc, indice);
            item.setDetalle(detalleCompleto);
            item.setUnidad("GL");
            item.setCantidad(BigDecimal.ONE);
            item.setPrecioUnitario(BigDecimal.ZERO);
            item.setImporte(BigDecimal.ZERO);
            return Optional.of(item);
        }

        List<BigDecimal> numeros = numerosPosteriores(partes, unidadIndex);
        ImportacionOrdenCompraItemForm item = crearItem(numeroOc, indice);
        item.setDetalle(String.join(" ", Arrays.copyOfRange(partes, 0, unidadIndex)).trim());
        item.setUnidad(normalizarUnidad(partes[unidadIndex]));
        if (!numeros.isEmpty()) {
            item.setCantidad(numeros.get(0));
        }
        if (numeros.size() > 1) {
            item.setPrecioUnitario(numeros.get(1));
        }
        if (numeros.size() > 2) {
            item.setImporte(numeros.get(2));
        } else {
            item.setImporte(item.getCantidad().multiply(item.getPrecioUnitario()));
        }
        return Optional.of(item);
    }

    private Optional<ImportacionOrdenCompraItemForm> parsearLineaItem(String linea) {
        Matcher matcher = ITEM_PATTERN.matcher(linea);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String codigo = matcher.group(1);
        String resto = matcher.group(2);
        String[] partes = resto.split(" ");
        int unidadIndex = buscarUnidad(partes);
        if (unidadIndex <= 0) {
            return Optional.empty();
        }

        ImportacionOrdenCompraItemForm item = new ImportacionOrdenCompraItemForm();
        item.setItem(codigo.replace(',', '.'));
        item.setDetalle(String.join(" ", Arrays.copyOfRange(partes, 0, unidadIndex)));
        item.setUnidad(normalizarUnidad(partes[unidadIndex]));
        List<BigDecimal> numeros = numerosPosteriores(partes, unidadIndex);
        if (!numeros.isEmpty()) {
            item.setCantidad(numeros.get(0));
        }
        if (numeros.size() > 1) {
            item.setPrecioUnitario(numeros.get(1));
        }
        if (numeros.size() > 2) {
            item.setImporte(numeros.get(2));
        } else {
            item.setImporte(item.getCantidad().multiply(item.getPrecioUnitario()));
        }
        return Optional.of(item);
    }

    private List<ImportacionOrdenCompraItemForm> finalizarItems(List<ImportacionOrdenCompraItemForm> items, String numeroOc) {
        Map<String, ImportacionOrdenCompraItemForm> unicos = new LinkedHashMap<>();
        for (ImportacionOrdenCompraItemForm item : items) {
            calcularImporte(item);
            unicos.putIfAbsent(claveDuplicado(item), item);
        }
        List<ImportacionOrdenCompraItemForm> resultado = new ArrayList<>(unicos.values());
        if (StringUtils.hasText(numeroOc)) {
            for (int i = 0; i < resultado.size(); i++) {
                resultado.get(i).setItem(numeroOc + "." + (i + 1));
            }
        }
        return resultado;
    }

    private ImportacionOrdenCompraItemForm crearItem(String numeroOc, int indice) {
        ImportacionOrdenCompraItemForm item = new ImportacionOrdenCompraItemForm();
        item.setItem(StringUtils.hasText(numeroOc) ? numeroOc + "." + indice : String.valueOf(indice));
        return item;
    }

    private List<Integer> posicionesUltimosNumeros(String[] partes, int cantidad) {
        List<Integer> posiciones = new ArrayList<>();
        for (int i = partes.length - 1; i >= 1 && posiciones.size() < cantidad; i--) {
            if (parsearDecimal(partes[i]).isPresent()) {
                posiciones.add(0, i);
            }
        }
        return posiciones;
    }

    private void calcularImporte(ImportacionOrdenCompraItemForm item) {
        BigDecimal cantidad = numeroSeguro(item.getCantidad());
        BigDecimal precio = numeroSeguro(item.getPrecioUnitario());
        if (cantidad.compareTo(BigDecimal.ZERO) > 0 && precio.compareTo(BigDecimal.ZERO) > 0) {
            item.setImporte(cantidad.multiply(precio).setScale(2, RoundingMode.HALF_UP));
            return;
        }
        item.setImporte(numeroSeguro(item.getImporte()));
    }

    private String claveDuplicado(ImportacionOrdenCompraItemForm item) {
        return normalizarTexto(item.getDetalle()) + "|" + normalizarTexto(item.getUnidad()) + "|" + normalizarNumero(item.getCantidad()) + "|" + normalizarNumero(item.getPrecioUnitario());
    }

    private String normalizarTexto(String valor) {
        return valor == null ? "" : Normalizer.normalize(valor.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("\\s+", " ").trim();
    }

    private String normalizarNumero(BigDecimal valor) {
        return numeroSeguro(valor).stripTrailingZeros().toPlainString();
    }

    private BigDecimal numeroSeguro(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private int buscarUnidad(String[] partes) {
        for (int i = 0; i < partes.length; i++) {
            if (UNIDADES.contains(limpiarUnidad(partes[i]))) {
                return i;
            }
        }
        return -1;
    }

    private int buscarUltimaUnidad(String[] partes) {
        for (int i = partes.length - 1; i >= 0; i--) {
            if (UNIDADES.contains(limpiarUnidad(partes[i]))) {
                return i;
            }
        }
        return -1;
    }

    private List<BigDecimal> numerosPosteriores(String[] partes, int unidadIndex) {
        List<BigDecimal> numeros = new ArrayList<>();
        for (int i = unidadIndex + 1; i < partes.length; i++) {
            String actual = limpiarTokenNumero(partes[i]);
            if (!StringUtils.hasText(actual)) {
                continue;
            }
            while (i + 1 < partes.length && debeUnirNumero(actual, limpiarTokenNumero(partes[i + 1]))) {
                i++;
                actual = actual + limpiarTokenNumero(partes[i]);
            }
            parsearDecimal(actual).ifPresent(numeros::add);
        }
        return numeros;
    }

    private boolean debeUnirNumero(String actual, String siguiente) {
        if (!StringUtils.hasText(actual) || !StringUtils.hasText(siguiente)) {
            return false;
        }
        return siguiente.startsWith(".")
                || siguiente.startsWith(",")
                || (actual.matches(".*\\.\\d{1,2}$") && siguiente.matches("\\d(?:\\.\\d{3})*,\\d{2}"))
                || (actual.matches("\\d+") && siguiente.matches("\\d,\\d{2}"))
                || (actual.matches("\\d+") && siguiente.matches("\\d\\.\\d{3},\\d{2}"));
    }

    private String limpiarTokenNumero(String token) {
        return token.replace("$", "").replace("%", "").replaceAll("[^0-9,.-]", "");
    }

    private Optional<BigDecimal> parsearDecimal(String valor) {
        String limpio = compactarNumerosPartidos(valor).replace("$", "").replace("%", "").replaceAll("[^0-9,.-]", "");
        if (!StringUtils.hasText(limpio) || limpio.equals("-")) {
            return Optional.empty();
        }
        int ultimaComa = limpio.lastIndexOf(',');
        int ultimoPunto = limpio.lastIndexOf('.');
        if (ultimaComa > ultimoPunto) {
            limpio = limpio.replace(".", "").replace(',', '.');
        } else if (ultimoPunto > ultimaComa && ultimaComa >= 0) {
            limpio = limpio.replace(",", "");
        } else if (ultimaComa >= 0) {
            limpio = limpio.replace(',', '.');
        }
        try {
            return Optional.of(new BigDecimal(limpio));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private Optional<String> buscar(Pattern pattern, String texto) {
        Matcher matcher = pattern.matcher(texto);
        return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
    }

    private Optional<String> buscarProveedorCompacto(String texto) {
        Matcher matcher = PROVEEDOR_COMPACTO_PATTERN.matcher(texto);
        while (matcher.find()) {
            String valor = matcher.group(1).trim();
            String normalizado = valor.toUpperCase(Locale.ROOT);
            if (!normalizado.contains("TERRAZAS") && !normalizado.contains("CUENTA CORRIENTE") && !normalizado.contains("CERTIFICACION")) {
                return Optional.of(valor);
            }
        }
        return Optional.empty();
    }

    private Optional<LocalDate> parsearFecha(String valor) {
        List<DateTimeFormatter> formatos = List.of(DateTimeFormatter.ofPattern("d/M/yyyy"), DateTimeFormatter.ofPattern("d-M-yyyy"), DateTimeFormatter.ofPattern("d/M/yy"), DateTimeFormatter.ofPattern("d-M-yy"), DateTimeFormatter.ISO_LOCAL_DATE);
        return formatos.stream().map(formato -> {
            try {
                return LocalDate.parse(valor, formato);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }).filter(fecha -> fecha != null).findFirst();
    }

    private String limpiarProveedor(String proveedor) {
        return proveedor.replaceAll("^\\d+\\s+", "").replaceAll("\\s{2,}.*$", "").trim();
    }

    private String normalizarNumeroOc(String numero) {
        String limpio = numero.trim();
        if (limpio.contains("-")) {
            limpio = limpio.substring(limpio.lastIndexOf('-') + 1);
        }
        limpio = limpio.replaceAll("[^A-Za-z0-9.]", "");
        String sinCeros = limpio.replaceFirst("^0+(?!$)", "");
        return StringUtils.hasText(sinCeros) ? sinCeros : limpio;
    }

    private String compactarNumerosPartidos(String valor) {
        return valor.replaceAll("(\\d)\\s+([.,])", "$1$2")
                .replaceAll("(?<=\\d)([.,])\\s+(\\d)", "$1$2")
                .replaceAll("\\$\\s+", "\\$")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizarUnidad(String unidad) {
        return switch (limpiarUnidad(unidad)) {
            case "PISO" -> "Piso";
            case "PASES" -> "Pases";
            case "DPTO" -> "Dpto.";
            case "SG" -> "sg";
            default -> limpiarUnidad(unidad);
        };
    }

    private String limpiarUnidad(String unidad) {
        String limpia = unidad.replace(".", "").replace(":", "").toUpperCase(Locale.ROOT);
        if (limpia.equals("M2") || limpia.equals("M²") || limpia.equals("M�")) {
            return "M2";
        }
        return limpia;
    }

    private String limpiarTextoPosterior(String valor) {
        return valor.replaceAll("(?is)CCSIMENDE.*$", "").replaceAll("(?is)Subtotal\\s*:.*$", "").replaceAll("\\s+", " ").trim();
    }

    private String normalizarEspacios(String linea) {
        return linea.replace('\t', ' ').replaceAll("\\s+", " ").trim();
    }

    private boolean esLineaDescartable(String linea) {
        String mayuscula = linea.toUpperCase(Locale.ROOT);
        return mayuscula.startsWith("ITEM ") || mayuscula.contains("DETALLE") || mayuscula.startsWith("TOTAL") || mayuscula.contains("AVANCE %") || mayuscula.contains("AVANCE $");
    }

    private void validar(ImportacionOrdenCompraForm form) {
        if (!StringUtils.hasText(form.getNumero())) {
            throw new IllegalArgumentException("El numero de OC es obligatorio.");
        }
        if (form.getFecha() == null) {
            throw new IllegalArgumentException("La fecha de la OC es obligatoria.");
        }
        if (form.getProveedorId() == null && !StringUtils.hasText(form.getProveedorNuevo())) {
            throw new IllegalArgumentException("Selecciona un proveedor o escribi uno nuevo.");
        }
        if (form.getCategoriaId() == null) {
            throw new IllegalArgumentException("Selecciona la categoria para los items importados.");
        }
    }

    private Long resolverProveedor(ImportacionOrdenCompraForm form) {
        if (form.getProveedorId() != null) {
            return form.getProveedorId();
        }
        String nombre = form.getProveedorNuevo().trim();
        return buscarProveedorExistente(nombre).orElseGet(() -> {
            Proveedor proveedor = new Proveedor();
            proveedor.setNombre(nombre);
            proveedor.setActivo(true);
            return proveedorRepository.save(proveedor);
        }).getId();
    }

    private Optional<Proveedor> buscarProveedorExistente(String nombre) {
        if (!StringUtils.hasText(nombre)) {
            return Optional.empty();
        }
        return proveedorRepository.findByNombreIgnoreCaseOrderByActivoDescIdAsc(nombre.trim()).stream().findFirst();
    }

    private Optional<CategoriaOrden> buscarCategoriaExistente(CategoriaItem tipo, String nombre) {
        if (!StringUtils.hasText(nombre)) {
            return Optional.empty();
        }
        return categoriaOrdenRepository.findByTipoAndNombreIgnoreCaseOrderByActivoDescIdAsc(tipo, nombre.trim()).stream().findFirst();
    }

    private void detectarOrdenExistente(ImportacionOrdenCompraForm form) {
        if (!StringUtils.hasText(form.getNumero()) || form.getProveedorId() == null) {
            return;
        }
        ordenCompraRepository.findByNumeroIgnoreCaseAndProveedorEntidadIdOrderByIdAsc(form.getNumero().trim(), form.getProveedorId())
                .stream()
                .findFirst()
                .ifPresent(orden -> {
                    String proveedor = orden.getProveedorEntidad() == null ? "este proveedor" : orden.getProveedorEntidad().getNombre();
                    form.setOrdenExistente(true);
                    form.setSeleccionado(false);
                    form.setOrdenExistenteId(orden.getId());
                    form.setOrdenExistenteMensaje("Esta OC " + form.getNumero() + " con el proveedor " + proveedor + " ya existe en la base de datos.");
                    form.setDiferencias(compararConOrdenExistente(form, orden));
                });
    }

    private List<String> compararConOrdenExistente(ImportacionOrdenCompraForm form, OrdenCompra existente) {
        List<String> diferencias = new ArrayList<>();
        if (!mismoDia(form.getFecha(), existente.getFecha())) {
            diferencias.add("Fecha distinta: archivo " + valorFecha(form.getFecha()) + " / base " + valorFecha(existente.getFecha()) + ".");
        }
        if (!mismoDia(form.getFechaVigencia(), existente.getFechaVigencia())) {
            diferencias.add("Vigencia distinta: archivo " + valorFecha(form.getFechaVigencia()) + " / base " + valorFecha(existente.getFechaVigencia()) + ".");
        }
        if (form.getItems().size() != existente.getItems().size()) {
            diferencias.add("Cantidad de items distinta: archivo " + form.getItems().size() + " / base " + existente.getItems().size() + ".");
        }
        BigDecimal totalArchivo = form.getTotalImportado().setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalBase = existente.getTotal().setScale(2, RoundingMode.HALF_UP);
        if (totalArchivo.compareTo(totalBase) != 0) {
            diferencias.add("Total distinto: archivo $" + totalArchivo + " / base $" + totalBase + ".");
        }

        Map<String, ItemOrdenCompra> itemsBase = new LinkedHashMap<>();
        for (ItemOrdenCompra item : existente.getItems()) {
            if (StringUtils.hasText(item.getItem())) {
                itemsBase.put(item.getItem().trim().toLowerCase(Locale.ROOT), item);
            }
        }
        for (ImportacionOrdenCompraItemForm itemArchivo : form.getItems()) {
            if (!StringUtils.hasText(itemArchivo.getItem())) {
                continue;
            }
            ItemOrdenCompra itemBase = itemsBase.get(itemArchivo.getItem().trim().toLowerCase(Locale.ROOT));
            if (itemBase == null) {
                diferencias.add("Item " + itemArchivo.getItem() + " no existe en la OC cargada.");
                continue;
            }
            compararItem(itemArchivo, itemBase).ifPresent(diferencias::add);
        }
        if (diferencias.isEmpty()) {
            diferencias.add("No encontre diferencias relevantes contra la OC cargada.");
        }
        return diferencias;
    }

    private Optional<String> compararItem(ImportacionOrdenCompraItemForm archivo, ItemOrdenCompra base) {
        List<String> cambios = new ArrayList<>();
        if (!normalizarTexto(archivo.getDetalle()).equals(normalizarTexto(base.getDetalle()))) {
            cambios.add("detalle");
        }
        if (!normalizarTexto(archivo.getUnidad()).equals(normalizarTexto(base.getUnidad()))) {
            cambios.add("unidad");
        }
        if (compararNumero(archivo.getCantidad(), base.getCantidad()) != 0) {
            cambios.add("cantidad " + valorNumero(archivo.getCantidad()) + "/" + valorNumero(base.getCantidad()));
        }
        if (compararNumero(archivo.getPrecioUnitario(), base.getPrecioUnitario()) != 0) {
            cambios.add("precio " + valorNumero(archivo.getPrecioUnitario()) + "/" + valorNumero(base.getPrecioUnitario()));
        }
        if (compararNumero(archivo.getImporte(), base.getImporte()) != 0) {
            cambios.add("importe " + valorNumero(archivo.getImporte()) + "/" + valorNumero(base.getImporte()));
        }
        return cambios.isEmpty() ? Optional.empty() : Optional.of("Item " + archivo.getItem() + " con diferencia en " + String.join(", ", cambios) + ".");
    }

    private int compararNumero(BigDecimal a, BigDecimal b) {
        return numeroSeguro(a).setScale(2, RoundingMode.HALF_UP).compareTo(numeroSeguro(b).setScale(2, RoundingMode.HALF_UP));
    }

    private boolean mismoDia(LocalDate a, LocalDate b) {
        return a == null ? b == null : a.equals(b);
    }

    private String valorFecha(LocalDate fecha) {
        return fecha == null ? "sin dato" : fecha.toString();
    }

    private String valorNumero(BigDecimal numero) {
        return numeroSeguro(numero).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private ItemOrdenCompra convertirItem(ImportacionOrdenCompraItemForm itemImportado, ImportacionOrdenCompraForm form) {
        ItemOrdenCompra item = new ItemOrdenCompra();
        item.setItem(itemImportado.getItem());
        item.setDetalle(itemImportado.getDetalle());
        item.setUnidad(itemImportado.getUnidad());
        item.setCantidad(itemImportado.getCantidad());
        item.setPrecioUnitario(itemImportado.getPrecioUnitario());
        item.setImporte(itemImportado.getImporte());
        item.setCategoriaId(form.getCategoriaId());
        item.setRubroId(form.getRubroId());
        return item;
    }
}
