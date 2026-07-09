import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ImportarPlanillaOrdenes {
    private static final DateTimeFormatter FECHA_FORMATTER = DateTimeFormatter.ofPattern("d/M/yyyy");
    private static final String URL = "jdbc:mysql://localhost:3306/certificaciones_obra?useSSL=false&serverTimezone=America/Argentina/Buenos_Aires&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    public static void main(String[] args) throws Exception {
        Path archivo = args.length > 0
                ? Path.of(args[0])
                : Path.of("src/main/resources/import/ordenes-iniciales.tsv");
        List<Fila> filas = leerFilas(archivo);

        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            connection.setAutoCommit(false);
            prepararColumnas(connection);
            limpiarBase(connection);

            Map<String, Long> categorias = asegurarCategorias(connection);
            Map<String, Long> proveedores = crearProveedores(connection, filas);
            Map<String, Long> rubros = crearRubros(connection, filas);
            Map<String, Long> catalogo = crearCatalogo(connection, filas);
            int ordenes = crearOrdenes(connection, filas, proveedores, rubros, categorias, catalogo);

            connection.commit();
            System.out.println("Importacion terminada.");
            System.out.println("Ordenes: " + ordenes);
            System.out.println("Items: " + filas.size());
            System.out.println("Proveedores: " + proveedores.size());
            System.out.println("Rubros: " + rubros.size());
            System.out.println("Materiales en catalogo: " + catalogo.size());
        }
    }

    private static List<Fila> leerFilas(Path archivo) throws Exception {
        Map<String, Fila> filasUnicas = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(archivo), StandardCharsets.UTF_8))) {
            String linea = reader.readLine();
            while ((linea = reader.readLine()) != null) {
                if (linea.isBlank()) {
                    continue;
                }
                String[] c = linea.split("\t", -1);
                if (c.length < 11) {
                    continue;
                }
                if (c[1].isBlank() || c[2].isBlank() || c[3].isBlank() || c[5].isBlank()) {
                    continue;
                }
                Fila fila = new Fila(
                        repararTexto(c[0]),
                        LocalDate.parse(c[1].trim(), FECHA_FORMATTER),
                        repararTexto(c[2]),
                        c[3].trim(),
                        parseCategoria(c[4]),
                        c[5].trim(),
                        repararTexto(c[6]),
                        repararTexto(c[7]),
                        parseDecimal(c[8]),
                        parseDecimal(c[9]),
                        parseDecimal(c[10])
                );
                filasUnicas.putIfAbsent(fila.claveUnica(), fila);
            }
        }
        return new ArrayList<>(filasUnicas.values());
    }

    private static void limpiarBase(Connection connection) throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String tabla : List.of(
                    "item_recepcion_material",
                    "recepcion_material",
                    "item_certificacion",
                    "certificacion",
                    "item_orden_compra",
                    "orden_compra",
                    "material_catalogo",
                    "proveedor",
                    "rubro"
            )) {
                st.execute("DELETE FROM " + tabla);
                st.execute("ALTER TABLE " + tabla + " AUTO_INCREMENT = 1");
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private static void prepararColumnas(Connection connection) throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE material_catalogo MODIFY nombre VARCHAR(2000)");
            st.execute("ALTER TABLE item_orden_compra MODIFY categoria VARCHAR(50)");
            st.execute("ALTER TABLE item_orden_compra MODIFY detalle VARCHAR(2000)");
            st.execute("ALTER TABLE item_orden_compra MODIFY rubro VARCHAR(1000)");
            st.execute("ALTER TABLE proveedor MODIFY nombre VARCHAR(500)");
            st.execute("ALTER TABLE rubro MODIFY nombre VARCHAR(1000)");
        }
    }

    private static Map<String, Long> asegurarCategorias(Connection connection) throws Exception {
        Map<String, Long> categorias = new LinkedHashMap<>();
        categorias.put(Categoria.MANO_OBRA.name(), asegurarCategoria(connection, "Mano de obra", "MANO_OBRA"));
        categorias.put(Categoria.MATERIAL.name(), asegurarCategoria(connection, "Material", "MATERIAL"));
        categorias.put(Categoria.SERVICIO.name(), asegurarCategoria(connection, "Servicio", "OTRO"));
        categorias.put(Categoria.EPP.name(), asegurarCategoria(connection, "EPP", "OTRO"));
        return categorias;
    }

    private static long asegurarCategoria(Connection connection, String nombre, String tipo) throws Exception {
        try (PreparedStatement buscar = connection.prepareStatement("SELECT id FROM categoria_orden WHERE lower(nombre) = lower(?)")) {
            buscar.setString(1, nombre);
            try (ResultSet rs = buscar.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        try (PreparedStatement insertar = connection.prepareStatement(
                "INSERT INTO categoria_orden (activo, nombre, tipo) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insertar.setBoolean(1, true);
            insertar.setString(2, nombre);
            insertar.setString(3, tipo);
            insertar.executeUpdate();
            return idGenerado(insertar);
        }
    }

    private static Map<String, Long> crearProveedores(Connection connection, List<Fila> filas) throws Exception {
        Set<String> nombres = new LinkedHashSet<>();
        filas.forEach(fila -> nombres.add(fila.proveedor()));
        Map<String, Long> proveedores = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO proveedor (activo, nombre, cuit, contacto, telefono, email, observacion) VALUES (?, ?, NULL, NULL, NULL, NULL, NULL)",
                Statement.RETURN_GENERATED_KEYS)) {
            for (String nombre : nombres) {
                ps.setBoolean(1, true);
                ps.setString(2, nombre);
                ps.executeUpdate();
                proveedores.put(clave(nombre), idGenerado(ps));
            }
        }
        return proveedores;
    }

    private static Map<String, Long> crearRubros(Connection connection, List<Fila> filas) throws Exception {
        Map<String, Long> rubros = new LinkedHashMap<>();
        int codigo = 1;
        for (Fila fila : filas) {
            String nombre = fila.rubro().isBlank() ? "Sin rubro" : fila.rubro();
            String key = clave(nombre);
            if (rubros.containsKey(key)) {
                continue;
            }
            rubros.put(key, insertarRubro(connection, String.valueOf(codigo++), nombre));
        }
        return rubros;
    }

    private static long insertarRubro(Connection connection, String codigo, String nombre) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO rubro (activo, codigo, nombre, padre_id) VALUES (?, ?, ?, NULL)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setBoolean(1, true);
            ps.setString(2, codigo);
            ps.setString(3, nombre);
            ps.executeUpdate();
            return idGenerado(ps);
        }
    }

    private static Map<String, Long> crearCatalogo(Connection connection, List<Fila> filas) throws Exception {
        Map<String, Long> catalogo = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO material_catalogo (activo, nombre, unidad, observacion) VALUES (?, ?, ?, NULL)",
                Statement.RETURN_GENERATED_KEYS)) {
            for (Fila fila : filas) {
                if (fila.categoria() != Categoria.MATERIAL) {
                    continue;
                }
                String clave = claveCatalogo(fila);
                if (catalogo.containsKey(clave)) {
                    continue;
                }
                ps.setBoolean(1, true);
                ps.setString(2, fila.detalle());
                ps.setString(3, fila.unidad());
                ps.executeUpdate();
                catalogo.put(clave, idGenerado(ps));
            }
        }
        return catalogo;
    }

    private static int crearOrdenes(Connection connection,
                                    List<Fila> filas,
                                    Map<String, Long> proveedores,
                                    Map<String, Long> rubros,
                                    Map<String, Long> categorias,
                                    Map<String, Long> catalogo) throws Exception {
        Map<String, Long> ordenes = new LinkedHashMap<>();
        try (PreparedStatement oc = connection.prepareStatement(
                "INSERT INTO orden_compra (numero, fecha, fecha_vigencia, observacion, proveedor_entidad_id) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
             PreparedStatement item = connection.prepareStatement(
                     "INSERT INTO item_orden_compra (cantidad, categoria, categoria_entidad_id, detalle, importe, item, precio_unitario, rubro, unidad, material_catalogo_id, orden_compra_id, rubro_entidad_id, item_mano_obra_vinculado_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)")) {
            for (Fila fila : filas) {
                String claveOrden = clave(fila.oc()) + "|" + clave(fila.proveedor());
                Long ordenId = ordenes.get(claveOrden);
                if (ordenId == null) {
                    oc.setString(1, fila.oc());
                    oc.setDate(2, Date.valueOf(fila.fecha()));
                    oc.setDate(3, Date.valueOf(fila.fecha().plusDays(30)));
                    oc.setString(4, "Importada desde planilla separando OC por proveedor");
                    oc.setLong(5, proveedores.get(clave(fila.proveedor())));
                    oc.executeUpdate();
                    ordenId = idGenerado(oc);
                    ordenes.put(claveOrden, ordenId);
                }

                Long rubroId = rubros.get(clave(fila.rubro().isBlank() ? "Sin rubro" : fila.rubro()));
                Long materialId = catalogo.get(claveCatalogo(fila));
                item.setBigDecimal(1, fila.cantidad());
                item.setString(2, fila.categoria().tipoEntidad());
                item.setLong(3, categorias.get(fila.categoria().name()));
                item.setString(4, fila.detalle());
                item.setBigDecimal(5, fila.importe());
                item.setString(6, fila.item());
                item.setBigDecimal(7, fila.precio());
                item.setString(8, nombreRubro(connection, rubroId));
                item.setString(9, fila.unidad());
                if (materialId == null) {
                    item.setObject(10, null);
                } else {
                    item.setLong(10, materialId);
                }
                item.setLong(11, ordenId);
                item.setLong(12, rubroId);
                item.executeUpdate();
            }
        }
        return ordenes.size();
    }

    private static String nombreRubro(Connection connection, Long rubroId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT codigo, nombre FROM rubro WHERE id = ?")) {
            ps.setLong(1, rubroId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("codigo") + " - " + rs.getString("nombre");
                }
            }
        }
        return "";
    }

    private static long idGenerado(PreparedStatement ps) throws Exception {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (!rs.next()) {
                throw new IllegalStateException("No se pudo obtener el ID generado.");
            }
            return rs.getLong(1);
        }
    }

    private static Categoria parseCategoria(String valor) {
        String normalizada = repararTexto(valor).trim().toUpperCase(Locale.ROOT);
        if (normalizada.contains("MANO")) {
            return Categoria.MANO_OBRA;
        }
        if (normalizada.contains("MATER")) {
            return Categoria.MATERIAL;
        }
        if (normalizada.equals("EPP")) {
            return Categoria.EPP;
        }
        return Categoria.SERVICIO;
    }

    private static BigDecimal parseDecimal(String valor) {
        String normalizado = valor
                .replace("$", "")
                .replace("\u00A0", "")
                .replace(" ", "")
                .replace(".", "")
                .replace(",", ".")
                .trim();
        if (normalizado.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(normalizado);
    }

    private static String repararTexto(String valor) {
        String limpio = valor == null ? "" : valor.trim();
        if (!limpio.contains("Ã") && !limpio.contains("Â")) {
            return limpio;
        }
        try {
            return new String(limpio.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            return limpio;
        }
    }

    private static String clave(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    private static String claveCatalogo(Fila fila) {
        return clave(fila.detalle()) + "|" + clave(fila.unidad());
    }

    private enum Categoria {
        MANO_OBRA("MANO_OBRA"),
        MATERIAL("MATERIAL"),
        SERVICIO("OTRO"),
        EPP("OTRO");

        private final String tipoEntidad;

        Categoria(String tipoEntidad) {
            this.tipoEntidad = tipoEntidad;
        }

        private String tipoEntidad() {
            return tipoEntidad;
        }
    }

    private record Fila(
            String rubro,
            LocalDate fecha,
            String proveedor,
            String oc,
            Categoria categoria,
            String item,
            String detalle,
            String unidad,
            BigDecimal cantidad,
            BigDecimal precio,
            BigDecimal importe
    ) {
        private String claveUnica() {
            return rubro + "|" + fecha + "|" + proveedor + "|" + oc + "|" + categoria + "|" + item + "|" + detalle + "|" + unidad + "|" + cantidad + "|" + precio + "|" + importe;
        }
    }
}
