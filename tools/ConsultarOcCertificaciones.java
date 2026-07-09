import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ConsultarOcCertificaciones {
    private static final String URL = "jdbc:mysql://localhost:3306/certificaciones_obra?useSSL=false&serverTimezone=America/Argentina/Buenos_Aires&allowPublicKeyRetrieval=true";

    public static void main(String[] args) throws Exception {
        String numero = args.length == 0 ? "317" : args[0];
        try (Connection connection = DriverManager.getConnection(URL, "root", "")) {
            try (PreparedStatement ps = connection.prepareStatement("""
                    select oc.id, oc.numero, oc.fecha, p.nombre proveedor, count(c.id) certificados
                    from orden_compra oc
                    left join proveedor p on p.id = oc.proveedor_entidad_id
                    left join certificacion c on c.orden_compra_id = oc.id
                    where oc.numero = ?
                    group by oc.id, oc.numero, oc.fecha, p.nombre
                    order by oc.id
                    """)) {
                ps.setString(1, numero);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.printf("OC id=%d numero=%s fecha=%s proveedor=%s certificados=%d%n",
                                rs.getLong("id"),
                                rs.getString("numero"),
                                rs.getDate("fecha"),
                                rs.getString("proveedor"),
                                rs.getInt("certificados"));
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                    select i.id, oc.id oc_id, i.item, i.detalle, i.categoria, i.importe
                    from item_orden_compra i
                    join orden_compra oc on oc.id = i.orden_compra_id
                    where oc.numero = ?
                    order by oc.id, i.id
                    """)) {
                ps.setString(1, numero);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.printf("  item id=%d ocId=%d item=%s categoria=%s importe=%s detalle=%s%n",
                                rs.getLong("id"),
                                rs.getLong("oc_id"),
                                rs.getString("item"),
                                rs.getString("categoria"),
                                rs.getBigDecimal("importe"),
                                rs.getString("detalle"));
                    }
                }
            }
        }
    }
}
