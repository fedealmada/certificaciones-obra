import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AutomatizarCertificacionesGloriaLote2 {
    private static final String URL = "jdbc:mysql://localhost:3306/certificaciones_obra?useSSL=false&serverTimezone=America/Argentina/Buenos_Aires&allowPublicKeyRetrieval=true";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d/M/yyyy");

    public static void main(String[] args) throws Exception {
        List<OcCertificada> ordenes = List.of(
                new OcCertificada("340", List.of(
                        new Certificado(1, "13/10/2025", Map.of(
                                "340.1", "100.00",
                                "340.2", "100.00",
                                "340.3", "100.00",
                                "340.4", "100.00",
                                "340.5", "100.00",
                                "340.6", "0.00",
                                "340.7", "100.00"
                        )),
                        new Certificado(2, "3/12/2025", Map.of(
                                "340.1", "0.00",
                                "340.2", "0.00",
                                "340.3", "0.00",
                                "340.4", "0.00",
                                "340.5", "0.00",
                                "340.6", "46.38",
                                "340.7", "0.00"
                        )),
                        new Certificado(3, "22/12/2025", Map.of(
                                "340.1", "0.00",
                                "340.2", "0.00",
                                "340.3", "0.00",
                                "340.4", "0.00",
                                "340.5", "0.00",
                                "340.6", "43.33",
                                "340.7", "0.00"
                        ))
                )),
                new OcCertificada("345", List.of(
                        new Certificado(1, "5/12/2025", Map.of(
                                "345.1", "100.00",
                                "345.2", "100.00",
                                "345.3", "100.00"
                        ))
                )),
                new OcCertificada("383", List.of(
                        new Certificado(1, "19/1/2026", Map.of("383.1", "20.00")),
                        new Certificado(2, "2/2/2026", Map.of("383.1", "21.96")),
                        new Certificado(3, "13/2/2026", Map.of("383.1", "53.37")),
                        new Certificado(4, "3/3/2026", Map.of("383.1", "4.67"))
                ))
        );

        try (Connection connection = DriverManager.getConnection(URL, "root", "")) {
            connection.setAutoCommit(false);
            try {
                for (OcCertificada orden : ordenes) {
                    cargarOrden(connection, orden);
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private static void cargarOrden(Connection connection, OcCertificada orden) throws Exception {
        long ordenCompraId = obtenerOrdenCompra(connection, orden.numeroOc());
        int existentes = contarCertificaciones(connection, ordenCompraId);
        if (existentes > 0) {
            throw new IllegalStateException("La OC " + orden.numeroOc() + " ya tiene " + existentes + " certificados. No se hicieron cambios para evitar duplicados.");
        }

        Map<String, Long> items = obtenerItems(connection, ordenCompraId);
        validarItems(orden, items);
        validarAcumulados(orden);

        for (Certificado certificado : orden.certificados()) {
            long certificacionId = insertarCertificacion(connection, ordenCompraId, orden.numeroOc(), certificado);
            for (Map.Entry<String, String> avance : certificado.avancesPorItem().entrySet()) {
                insertarItemCertificacion(connection, certificacionId, items.get(avance.getKey()), new BigDecimal(avance.getValue()));
            }
        }

        System.out.println("OC " + orden.numeroOc() + ": " + orden.certificados().size() + " certificados cargados.");
    }

    private static long obtenerOrdenCompra(Connection connection, String numeroOc) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                select oc.id
                from orden_compra oc
                left join proveedor p on p.id = oc.proveedor_entidad_id
                where oc.numero = ?
                  and lower(p.nombre) like '%gloria%'
                order by oc.id
                limit 1
                """)) {
            ps.setString(1, numeroOc);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("No se encontro la OC " + numeroOc + " de Gloria Coronel.");
                }
                return rs.getLong(1);
            }
        }
    }

    private static Map<String, Long> obtenerItems(Connection connection, long ordenCompraId) throws Exception {
        Map<String, Long> items = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                select id, item
                from item_orden_compra
                where orden_compra_id = ?
                  and categoria = 'MANO_OBRA'
                order by item
                """)) {
            ps.setLong(1, ordenCompraId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.put(rs.getString("item"), rs.getLong("id"));
                }
            }
        }
        return items;
    }

    private static void validarItems(OcCertificada orden, Map<String, Long> items) {
        for (Certificado certificado : orden.certificados()) {
            for (String item : certificado.avancesPorItem().keySet()) {
                if (!items.containsKey(item)) {
                    throw new IllegalStateException("No se encontro el item " + item + " para la OC " + orden.numeroOc() + ".");
                }
            }
        }
    }

    private static void validarAcumulados(OcCertificada orden) {
        Map<String, BigDecimal> acumulados = new LinkedHashMap<>();
        for (Certificado certificado : orden.certificados()) {
            for (Map.Entry<String, String> avance : certificado.avancesPorItem().entrySet()) {
                BigDecimal acumulado = acumulados.getOrDefault(avance.getKey(), BigDecimal.ZERO).add(new BigDecimal(avance.getValue()));
                if (acumulado.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new IllegalStateException("El item " + avance.getKey() + " supera 100% en la OC " + orden.numeroOc() + ".");
                }
                acumulados.put(avance.getKey(), acumulado);
            }
        }
    }

    private static int contarCertificaciones(Connection connection, long ordenCompraId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("select count(*) from certificacion where orden_compra_id = ?")) {
            ps.setLong(1, ordenCompraId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static long insertarCertificacion(Connection connection, long ordenCompraId, String numeroOc, Certificado certificado) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into certificacion (numero, fecha, observacion, orden_compra_id) values (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, certificado.numero());
            ps.setDate(2, Date.valueOf(certificado.fecha()));
            ps.setString(3, "Carga automatica desde planilla OC " + numeroOc);
            ps.setLong(4, ordenCompraId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void insertarItemCertificacion(Connection connection, long certificacionId, long itemId, BigDecimal porcentaje) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into item_certificacion (certificacion_id, item_orden_compra_id, porcentaje_actual) values (?, ?, ?)")) {
            ps.setLong(1, certificacionId);
            ps.setLong(2, itemId);
            ps.setBigDecimal(3, porcentaje);
            ps.executeUpdate();
        }
    }

    private record OcCertificada(String numeroOc, List<Certificado> certificados) {
    }

    private record Certificado(int numero, String fechaTexto, Map<String, String> avancesPorItem) {
        LocalDate fecha() {
            return LocalDate.parse(fechaTexto, FECHA);
        }
    }
}
