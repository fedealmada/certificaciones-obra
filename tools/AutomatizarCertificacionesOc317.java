import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AutomatizarCertificacionesOc317 {
    private static final String URL = "jdbc:mysql://localhost:3306/certificaciones_obra?useSSL=false&serverTimezone=America/Argentina/Buenos_Aires&allowPublicKeyRetrieval=true";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d/M/yyyy");

    public static void main(String[] args) throws Exception {
        List<Certificado> certificados = List.of(
                new Certificado(1, "5/8/2025", "8.33"),
                new Certificado(2, "29/8/2025", "7.75"),
                new Certificado(3, "15/9/2025", "4.46"),
                new Certificado(4, "26/9/2025", "5.00"),
                new Certificado(5, "28/10/2025", "6.25"),
                new Certificado(6, "5/12/2025", "6.93"),
                new Certificado(7, "19/1/2026", "1.75"),
                new Certificado(8, "2/2/2026", "6.83"),
                new Certificado(9, "16/3/2026", "10.07"),
                new Certificado(10, "27/3/2026", "12.18"),
                new Certificado(11, "10/4/2026", "1.75"),
                new Certificado(12, "27/4/2026", "15.16"),
                new Certificado(13, "26/5/2026", "1.85"),
                new Certificado(14, "19/6/2026", "7.72"),
                new Certificado(15, "6/7/2026", "3.97")
        );

        try (Connection connection = DriverManager.getConnection(URL, "root", "")) {
            connection.setAutoCommit(false);
            long ordenCompraId = obtenerOrdenCompra(connection);
            long itemId = obtenerItem(connection, ordenCompraId);
            int existentes = contarCertificaciones(connection, ordenCompraId);
            if (existentes > 0) {
                throw new IllegalStateException("La OC 317 ya tiene " + existentes + " certificados. No se hicieron cambios para evitar duplicados.");
            }

            BigDecimal acumulado = BigDecimal.ZERO;
            for (Certificado certificado : certificados) {
                acumulado = acumulado.add(certificado.porcentaje());
                if (acumulado.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new IllegalStateException("El acumulado supera 100% en el certificado " + certificado.numero());
                }
                long certificacionId = insertarCertificacion(connection, ordenCompraId, certificado);
                insertarItemCertificacion(connection, certificacionId, itemId, certificado.porcentaje());
            }
            connection.commit();
            System.out.println("Certificaciones cargadas para OC 317.");
            System.out.println("OC id: " + ordenCompraId);
            System.out.println("Item id: " + itemId);
            System.out.println("Cantidad: " + certificados.size());
            System.out.println("Acumulado: " + acumulado + "%");
        }
    }

    private static long obtenerOrdenCompra(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                select oc.id
                from orden_compra oc
                left join proveedor p on p.id = oc.proveedor_entidad_id
                where oc.numero = '317'
                  and lower(p.nombre) like '%gloria%'
                order by oc.id
                limit 1
                """);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new IllegalStateException("No se encontro la OC 317 de Gloria Coronel.");
            }
            return rs.getLong(1);
        }
    }

    private static long obtenerItem(Connection connection, long ordenCompraId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                select id
                from item_orden_compra
                where orden_compra_id = ?
                  and categoria = 'MANO_OBRA'
                  and item = '317.1'
                order by id
                limit 1
                """)) {
            ps.setLong(1, ordenCompraId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("No se encontro el item 317.1 de mano de obra.");
                }
                return rs.getLong(1);
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

    private static long insertarCertificacion(Connection connection, long ordenCompraId, Certificado certificado) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into certificacion (numero, fecha, observacion, orden_compra_id) values (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, certificado.numero());
            ps.setDate(2, Date.valueOf(certificado.fecha()));
            ps.setString(3, "Carga automatica desde planilla OC 317");
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

    private record Certificado(int numero, String fechaTexto, String porcentajeTexto) {
        LocalDate fecha() {
            return LocalDate.parse(fechaTexto, FECHA);
        }

        BigDecimal porcentaje() {
            return new BigDecimal(porcentajeTexto);
        }
    }
}
