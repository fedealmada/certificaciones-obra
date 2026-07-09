package com.obra.certificaciones.importacion;

import com.obra.certificaciones.material.catalogo.entity.MaterialCatalogo;
import com.obra.certificaciones.material.catalogo.repository.MaterialCatalogoRepository;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.repository.OrdenCompraRepository;
import com.obra.certificaciones.proveedor.entity.Proveedor;
import com.obra.certificaciones.proveedor.repository.ProveedorRepository;
import com.obra.certificaciones.rubro.entity.Rubro;
import com.obra.certificaciones.rubro.repository.RubroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CargaInicialOrdenesRunner implements CommandLineRunner {

    private static final DateTimeFormatter FECHA_FORMATTER = DateTimeFormatter.ofPattern("d/M/yyyy");
    private static final String ARCHIVO_IMPORTACION = "import/ordenes-iniciales.tsv";

    private final JdbcTemplate jdbcTemplate;
    private final OrdenCompraRepository ordenCompraRepository;
    private final ProveedorRepository proveedorRepository;
    private final RubroRepository rubroRepository;
    private final MaterialCatalogoRepository materialCatalogoRepository;

    @Value("${app.importar-datos-iniciales:false}")
    private boolean importarDatosIniciales;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!importarDatosIniciales) {
            return;
        }

        List<FilaImportacion> filas = leerFilas();
        limpiarBaseDeDatos();
        Map<CategoriaItem, Rubro> rubros = crearRubrosImportacion();
        Map<String, Proveedor> proveedores = crearProveedores(filas);
        Map<String, MaterialCatalogo> catalogo = crearCatalogo(filas);
        crearOrdenes(filas, proveedores, rubros, catalogo);
    }

    private List<FilaImportacion> leerFilas() throws Exception {
        ClassPathResource resource = new ClassPathResource(ARCHIVO_IMPORTACION);
        Map<String, FilaImportacion> filasUnicas = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String linea = reader.readLine();
            while ((linea = reader.readLine()) != null) {
                if (!StringUtils.hasText(linea)) {
                    continue;
                }
                String[] columnas = linea.split("\t", -1);
                if (columnas.length < 10) {
                    continue;
                }
                FilaImportacion fila = new FilaImportacion(
                        parseFecha(columnas[0]),
                        repararTexto(columnas[1]),
                        columnas[2].trim(),
                        parseCategoria(columnas[3]),
                        columnas[4].trim(),
                        repararTexto(columnas[5]),
                        repararTexto(columnas[6]),
                        parseDecimal(columnas[7]),
                        parseDecimal(columnas[8]),
                        parseDecimal(columnas[9])
                );
                filasUnicas.putIfAbsent(fila.claveUnica(), fila);
            }
        }
        return filasUnicas.values().stream().toList();
    }

    private void limpiarBaseDeDatos() {
        ejecutarSiSePuede("SET FOREIGN_KEY_CHECKS = 0");
        List<String> tablas = List.of(
                "item_recepcion_material",
                "recepcion_material",
                "item_certificacion",
                "certificacion",
                "item_orden_compra",
                "orden_compra",
                "material_catalogo",
                "proveedor",
                "rubro"
        );
        tablas.forEach(tabla -> ejecutarSiSePuede("DELETE FROM " + tabla));
        tablas.forEach(tabla -> ejecutarSiSePuede("ALTER TABLE " + tabla + " AUTO_INCREMENT = 1"));
        ejecutarSiSePuede("SET FOREIGN_KEY_CHECKS = 1");
    }

    private void ejecutarSiSePuede(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (RuntimeException ignored) {
            // El importador se usa con MySQL; se ignoran variantes no soportadas por otros motores.
        }
    }

    private Map<CategoriaItem, Rubro> crearRubrosImportacion() {
        Rubro raiz = new Rubro();
        raiz.setCodigo("IMP");
        raiz.setNombre("Importado desde planilla");
        rubroRepository.save(raiz);

        Map<CategoriaItem, Rubro> rubros = new LinkedHashMap<>();
        rubros.put(CategoriaItem.MANO_OBRA, crearRubro("IMP.1", "Mano de obra", raiz));
        rubros.put(CategoriaItem.MATERIAL, crearRubro("IMP.2", "Materiales", raiz));
        rubros.put(CategoriaItem.OTRO, crearRubro("IMP.3", "Otros", raiz));
        return rubros;
    }

    private Rubro crearRubro(String codigo, String nombre, Rubro padre) {
        Rubro rubro = new Rubro();
        rubro.setCodigo(codigo);
        rubro.setNombre(nombre);
        rubro.setPadre(padre);
        return rubroRepository.save(rubro);
    }

    private Map<String, Proveedor> crearProveedores(List<FilaImportacion> filas) {
        Set<String> nombres = new LinkedHashSet<>();
        filas.forEach(fila -> nombres.add(fila.proveedor()));

        Map<String, Proveedor> proveedores = new LinkedHashMap<>();
        for (String nombre : nombres) {
            Proveedor proveedor = new Proveedor();
            proveedor.setNombre(nombre);
            proveedor.setActivo(true);
            proveedores.put(clave(nombre), proveedorRepository.save(proveedor));
        }
        return proveedores;
    }

    private Map<String, MaterialCatalogo> crearCatalogo(List<FilaImportacion> filas) {
        Map<String, MaterialCatalogo> catalogo = new LinkedHashMap<>();
        for (FilaImportacion fila : filas) {
            if (fila.categoria() != CategoriaItem.MATERIAL) {
                continue;
            }
            String clave = clave(fila.detalle()) + "|" + clave(fila.unidad());
            if (catalogo.containsKey(clave)) {
                continue;
            }
            MaterialCatalogo material = new MaterialCatalogo();
            material.setNombre(fila.detalle());
            material.setUnidad(fila.unidad());
            material.setActivo(true);
            catalogo.put(clave, materialCatalogoRepository.save(material));
        }
        return catalogo;
    }

    private void crearOrdenes(List<FilaImportacion> filas,
                              Map<String, Proveedor> proveedores,
                              Map<CategoriaItem, Rubro> rubros,
                              Map<String, MaterialCatalogo> catalogo) {
        Map<String, OrdenCompra> ordenes = new LinkedHashMap<>();
        for (FilaImportacion fila : filas) {
            OrdenCompra ordenCompra = ordenes.computeIfAbsent(fila.oc(), numero -> {
                OrdenCompra nueva = new OrdenCompra();
                nueva.setNumero(numero);
                nueva.setFecha(fila.fecha());
                nueva.setProveedorEntidad(proveedores.get(clave(fila.proveedor())));
                nueva.setObservacion("Importada desde planilla inicial");
                return nueva;
            });

            ItemOrdenCompra item = new ItemOrdenCompra();
            item.setItem(fila.item());
            item.setDetalle(fila.detalle());
            item.setUnidad(fila.unidad());
            item.setCategoria(fila.categoria());
            item.setCantidad(fila.cantidad());
            item.setPrecioUnitario(fila.precio());
            item.setImporte(fila.importe());

            Rubro rubro = rubros.get(fila.categoria());
            item.setRubroEntidad(rubro);
            item.setRubro(null);

            if (fila.categoria() == CategoriaItem.MATERIAL) {
                item.setMaterialCatalogo(catalogo.get(clave(fila.detalle()) + "|" + clave(fila.unidad())));
            }
            ordenCompra.agregarItem(item);
        }
        ordenCompraRepository.saveAll(ordenes.values());
    }

    private LocalDate parseFecha(String valor) {
        return LocalDate.parse(valor.trim(), FECHA_FORMATTER);
    }

    private CategoriaItem parseCategoria(String valor) {
        String normalizada = repararTexto(valor).trim().toUpperCase(Locale.ROOT);
        if (normalizada.contains("MANO")) {
            return CategoriaItem.MANO_OBRA;
        }
        if (normalizada.contains("MATER")) {
            return CategoriaItem.MATERIAL;
        }
        if (normalizada.equals("EPP")) {
            return CategoriaItem.OTRO;
        }
        return CategoriaItem.OTRO;
    }

    private BigDecimal parseDecimal(String valor) {
        String normalizado = valor
                .replace("$", "")
                .replace("\u00A0", "")
                .replace(" ", "")
                .replace(".", "")
                .replace(",", ".")
                .trim();
        if (!StringUtils.hasText(normalizado)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(normalizado);
    }

    private String repararTexto(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        if (!limpio.contains("Ã") && !limpio.contains("Â") && !limpio.contains("â")) {
            return limpio;
        }
        try {
            return new String(limpio.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            return limpio;
        }
    }

    private String clave(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    private record FilaImportacion(
            LocalDate fecha,
            String proveedor,
            String oc,
            CategoriaItem categoria,
            String item,
            String detalle,
            String unidad,
            BigDecimal cantidad,
            BigDecimal precio,
            BigDecimal importe
    ) {
        private String claveUnica() {
            return fecha + "|" + proveedor + "|" + oc + "|" + categoria + "|" + item + "|" + detalle + "|" + unidad + "|" + cantidad + "|" + precio + "|" + importe;
        }
    }
}
