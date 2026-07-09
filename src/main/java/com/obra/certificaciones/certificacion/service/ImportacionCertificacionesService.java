package com.obra.certificaciones.certificacion.service;

import com.obra.certificaciones.certificacion.dto.CertificadoImportadoVista;
import com.obra.certificaciones.certificacion.dto.ImportacionCertificacionesResultado;
import com.obra.certificaciones.certificacion.dto.ItemImportadoVista;
import com.obra.certificaciones.certificacion.dto.OrdenImportadaVista;
import com.obra.certificaciones.certificacion.entity.Certificacion;
import com.obra.certificaciones.certificacion.entity.ItemCertificacion;
import com.obra.certificaciones.certificacion.repository.CertificacionRepository;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.repository.OrdenCompraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ImportacionCertificacionesService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d/M/yyyy");
    private static final Pattern CERTIFICADO_PATTERN = Pattern.compile("CERTIFICADO\\s*N[^\\d]*(\\d+)\\s+FECHA:\\s*(\\d{1,2}/\\d{1,2}/\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern OC_PATTERN = Pattern.compile("\\bOC:\\s*([^\\s\\t]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAREA_PATTERN = Pattern.compile("TAREAS:\\s*(.*?)(?:\\s+Monto\\b|$)", Pattern.CASE_INSENSITIVE);

    private final OrdenCompraRepository ordenCompraRepository;
    private final CertificacionRepository certificacionRepository;
    private final CertificacionCalculoService calculoService;

    @Transactional(readOnly = true)
    public ImportacionCertificacionesResultado previsualizar(String contenido) {
        List<OrdenParseada> ordenes = parsear(contenido);
        List<OrdenImportadaVista> vistas = ordenes.stream()
                .map(orden -> construirVista(orden, false))
                .toList();
        return new ImportacionCertificacionesResultado(
                vistas,
                vistas.size(),
                vistas.stream().mapToInt(orden -> orden.certificados().size()).sum(),
                0,
                false
        );
    }

    @Transactional
    public ImportacionCertificacionesResultado importar(String contenido) {
        List<OrdenParseada> ordenes = parsear(contenido);
        List<OrdenImportadaVista> vistas = ordenes.stream()
                .map(orden -> construirVista(orden, false))
                .toList();
        ImportacionCertificacionesResultado previo = new ImportacionCertificacionesResultado(
                vistas,
                vistas.size(),
                vistas.stream().mapToInt(orden -> orden.certificados().size()).sum(),
                0,
                false
        );
        if (!previo.puedeImportar()) {
            return previo;
        }

        int importados = 0;
        for (OrdenParseada ordenParseada : ordenes) {
            OrdenCompra ordenCompra = seleccionarOrden(ordenParseada).orElseThrow();
            Map<String, ItemOrdenCompra> items = itemsPorCodigo(ordenCompra);
            for (CertificadoParseado certificadoParseado : ordenParseada.certificados()) {
                if (certificacionRepository.existsByOrdenCompraIdAndNumero(ordenCompra.getId(), certificadoParseado.numero())) {
                    continue;
                }
                Certificacion certificacion = new Certificacion();
                certificacion.setOrdenCompra(ordenCompra);
                certificacion.setNumero(certificadoParseado.numero());
                certificacion.setFecha(certificadoParseado.fecha());
                certificacion.setObservacion("Importado desde planilla pegada");

                for (ItemParseado itemParseado : ordenParseada.items()) {
                    BigDecimal porcentaje = itemParseado.avancesPorCertificado().getOrDefault(certificadoParseado.numero(), BigDecimal.ZERO);
                    if (porcentaje.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    ItemOrdenCompra item = items.get(itemParseado.codigo());
                    ItemCertificacion itemCertificacion = new ItemCertificacion();
                    itemCertificacion.setItemOrdenCompra(item);
                    itemCertificacion.setPorcentajeActual(porcentaje);
                    certificacion.agregarItem(itemCertificacion);
                }

                if (!certificacion.getItems().isEmpty()) {
                    certificacionRepository.save(certificacion);
                    importados++;
                }
            }
        }

        List<OrdenImportadaVista> resultadoFinal = ordenes.stream()
                .map(orden -> construirVista(orden, true))
                .toList();
        return new ImportacionCertificacionesResultado(
                resultadoFinal,
                resultadoFinal.size(),
                resultadoFinal.stream().mapToInt(orden -> orden.certificados().size()).sum(),
                importados,
                true
        );
    }

    private OrdenImportadaVista construirVista(OrdenParseada ordenParseada, boolean despuesDeImportar) {
        List<String> errores = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        Optional<OrdenCompra> ordenCompraOptional = seleccionarOrden(ordenParseada);

        if (ordenParseada.numeroOc().isBlank()) {
            errores.add("No se detecto el numero de OC.");
        }
        if (ordenParseada.certificados().isEmpty()) {
            errores.add("No se detectaron certificados con fecha.");
        }
        if (ordenParseada.items().isEmpty()) {
            errores.add("No se detectaron items certificables.");
        }
        if (ordenCompraOptional.isEmpty()) {
            errores.add("No se encontro una OC cargada que coincida con el numero y contratista.");
        }

        Long ordenCompraId = null;
        String proveedorEncontrado = "-";
        int certificadosExistentes = 0;
        Map<String, ItemOrdenCompra> items = Map.of();

        if (ordenCompraOptional.isPresent()) {
            OrdenCompra ordenCompra = ordenCompraOptional.get();
            ordenCompraId = ordenCompra.getId();
            proveedorEncontrado = ordenCompra.getProveedorEntidad() == null ? "-" : ordenCompra.getProveedorEntidad().getNombre();
            certificadosExistentes = (int) certificacionRepository.countByOrdenCompraId(ordenCompraId);
            items = itemsPorCodigo(ordenCompra);

            for (CertificadoParseado certificado : ordenParseada.certificados()) {
                if (!despuesDeImportar && certificacionRepository.existsByOrdenCompraIdAndNumero(ordenCompraId, certificado.numero())) {
                    errores.add("El certificado " + certificado.numero() + " ya existe para esta OC.");
                }
            }

            validarItemsYAcumulados(ordenParseada, ordenCompra, items, errores, avisos);
        }

        Map<String, ItemOrdenCompra> itemsFinal = items;
        List<ItemImportadoVista> itemsVista = ordenParseada.items().stream()
                .map(item -> new ItemImportadoVista(item.codigo(), item.detalle(), itemsFinal.containsKey(item.codigo())))
                .toList();
        List<CertificadoImportadoVista> certificadosVista = ordenParseada.certificados().stream()
                .map(certificado -> new CertificadoImportadoVista(
                        certificado.numero(),
                        certificado.fecha(),
                        (int) ordenParseada.items().stream()
                                .filter(item -> item.avancesPorCertificado().getOrDefault(certificado.numero(), BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0)
                                .count()
                ))
                .toList();

        return new OrdenImportadaVista(
                ordenParseada.numeroOc(),
                ordenParseada.contratista(),
                ordenParseada.tarea(),
                ordenCompraId,
                proveedorEncontrado,
                certificadosExistentes,
                certificadosVista,
                itemsVista,
                errores,
                avisos
        );
    }

    private void validarItemsYAcumulados(OrdenParseada ordenParseada,
                                         OrdenCompra ordenCompra,
                                         Map<String, ItemOrdenCompra> items,
                                         List<String> errores,
                                         List<String> avisos) {
        Map<Long, BigDecimal> acumulados = new LinkedHashMap<>();
        for (ItemParseado itemParseado : ordenParseada.items()) {
            ItemOrdenCompra item = items.get(itemParseado.codigo());
            if (item == null) {
                errores.add("No se encontro el item " + itemParseado.codigo() + " en la OC.");
                continue;
            }
            BigDecimal acumulado = calculoService.porcentajeAcumuladoItem(ordenCompra.getId(), item.getId());
            acumulados.put(item.getId(), acumulado);
            for (CertificadoParseado certificado : ordenParseada.certificados()) {
                acumulado = acumulado.add(itemParseado.avancesPorCertificado().getOrDefault(certificado.numero(), BigDecimal.ZERO));
                if (acumulado.compareTo(BigDecimal.valueOf(100)) > 0) {
                    errores.add("El item " + itemParseado.codigo() + " supera el 100% acumulado.");
                    break;
                }
            }
        }

        long certificadosSinAvance = ordenParseada.certificados().stream()
                .filter(certificado -> ordenParseada.items().stream()
                        .noneMatch(item -> item.avancesPorCertificado().getOrDefault(certificado.numero(), BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0))
                .count();
        if (certificadosSinAvance > 0) {
            avisos.add(certificadosSinAvance + " certificados no tienen avance actual y no se importaran.");
        }
    }

    private Optional<OrdenCompra> seleccionarOrden(OrdenParseada ordenParseada) {
        if (ordenParseada.numeroOc().isBlank()) {
            return Optional.empty();
        }
        List<OrdenCompra> candidatas = ordenCompraRepository.findByNumeroIgnoreCaseOrderByIdAsc(ordenParseada.numeroOc());
        if (candidatas.isEmpty()) {
            return Optional.empty();
        }
        String contratista = normalizar(ordenParseada.contratista());
        if (!contratista.isBlank()) {
            List<OrdenCompra> filtradas = candidatas.stream()
                    .filter(orden -> orden.getProveedorEntidad() != null && contienePalabras(normalizar(orden.getProveedorEntidad().getNombre()), contratista))
                    .toList();
            if (filtradas.size() == 1) {
                return Optional.of(filtradas.get(0));
            }
        }
        return candidatas.size() == 1 ? Optional.of(candidatas.get(0)) : Optional.empty();
    }

    private Map<String, ItemOrdenCompra> itemsPorCodigo(OrdenCompra ordenCompra) {
        Map<String, ItemOrdenCompra> items = new LinkedHashMap<>();
        ordenCompra.getItems().stream()
                .filter(item -> item.getCategoria() == CategoriaItem.MANO_OBRA)
                .forEach(item -> items.put(item.getItem(), item));
        return items;
    }

    private List<OrdenParseada> parsear(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            return List.of();
        }
        List<String> bloques = separarBloques(contenido.replace("\r\n", "\n").replace('\r', '\n'));
        return bloques.stream()
                .map(this::parsearBloque)
                .toList();
    }

    private List<String> separarBloques(String contenido) {
        List<String> bloques = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String linea : contenido.split("\n")) {
            if (linea.contains("Obra:") && !actual.isEmpty()) {
                bloques.add(actual.toString());
                actual.setLength(0);
            }
            actual.append(linea).append('\n');
        }
        if (!actual.isEmpty()) {
            bloques.add(actual.toString());
        }
        return bloques;
    }

    private OrdenParseada parsearBloque(String bloque) {
        String numeroOc = extraer(OC_PATTERN, bloque);
        String contratista = extraerContratista(bloque);
        String tarea = extraer(TAREA_PATTERN, bloque);
        List<CertificadoParseado> certificados = parsearCertificados(bloque);
        List<ItemParseado> items = parsearItems(bloque, numeroOc, certificados);
        return new OrdenParseada(numeroOc, contratista, tarea, certificados, items);
    }

    private List<CertificadoParseado> parsearCertificados(String bloque) {
        List<CertificadoParseado> certificados = new ArrayList<>();
        Matcher matcher = CERTIFICADO_PATTERN.matcher(bloque.replace('\t', ' '));
        while (matcher.find()) {
            certificados.add(new CertificadoParseado(Integer.parseInt(matcher.group(1)), LocalDate.parse(matcher.group(2), FECHA)));
        }
        return certificados;
    }

    private List<ItemParseado> parsearItems(String bloque, String numeroOc, List<CertificadoParseado> certificados) {
        List<ItemParseado> items = new ArrayList<>();
        boolean dentroTabla = false;
        int itemSecuencial = 1;
        for (String linea : bloque.split("\n")) {
            if (linea.trim().startsWith("Item")) {
                dentroTabla = true;
                continue;
            }
            if (!dentroTabla) {
                continue;
            }
            String limpia = linea.trim();
            if (limpia.isBlank()) {
                continue;
            }
            if (limpia.startsWith("TOTAL") || limpia.contains("\tTOTAL")) {
                break;
            }

            String[] celdas = linea.split("\t", -1);
            if (celdas.length < 7) {
                continue;
            }
            String itemPlanilla = celdas[0].trim();
            String detalle = celdas.length > 1 ? celdas[1].trim() : "";
            if (itemPlanilla.isBlank() && !detalle.isBlank()) {
                itemPlanilla = String.valueOf(itemSecuencial);
            }
            if (!itemPlanilla.matches("\\d+(?:\\.\\d+)?") || detalle.isBlank()) {
                continue;
            }

            String codigo = itemPlanilla.contains(".") ? itemPlanilla : numeroOc + "." + itemPlanilla;
            Map<Integer, BigDecimal> avances = new LinkedHashMap<>();
            int inicio = 6;
            while (inicio < celdas.length && celdas[inicio].trim().isBlank()) {
                inicio++;
            }
            for (int i = 0; i < certificados.size(); i++) {
                int indiceActual = inicio + (i * 6) + 1;
                BigDecimal porcentaje = indiceActual < celdas.length ? parsearPorcentaje(celdas[indiceActual]) : BigDecimal.ZERO;
                avances.put(certificados.get(i).numero(), porcentaje);
            }
            items.add(new ItemParseado(codigo, detalle, avances));
            itemSecuencial++;
        }
        return items;
    }

    private BigDecimal parsearPorcentaje(String valor) {
        String normalizado = valor == null ? "" : valor.replace("%", "").replace("$", "").replace(".", "").replace(",", ".").trim();
        if (normalizado.isBlank() || normalizado.equals("-")) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(normalizado);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private String extraer(Pattern pattern, String texto) {
        Matcher matcher = pattern.matcher(texto);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String extraerContratista(String bloque) {
        int inicio = bloque.indexOf("Contratista:");
        if (inicio < 0) {
            return "";
        }
        String primeraLinea = bloque.substring(inicio).split("\n", 2)[0];
        String valor = primeraLinea.replaceFirst("Contratista:", "");
        int certificado = valor.toUpperCase(Locale.ROOT).indexOf("CERTIFICADO");
        if (certificado >= 0) {
            valor = valor.substring(0, certificado);
        }
        return valor.replace('\t', ' ').trim();
    }

    private String normalizar(String valor) {
        String sinAcentos = Normalizer.normalize(valor == null ? "" : valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private boolean contienePalabras(String texto, String palabras) {
        for (String palabra : palabras.split(" ")) {
            if (!palabra.isBlank() && !texto.contains(palabra)) {
                return false;
            }
        }
        return true;
    }

    private record OrdenParseada(String numeroOc, String contratista, String tarea, List<CertificadoParseado> certificados, List<ItemParseado> items) {
    }

    private record CertificadoParseado(int numero, LocalDate fecha) {
    }

    private record ItemParseado(String codigo, String detalle, Map<Integer, BigDecimal> avancesPorCertificado) {
    }
}
