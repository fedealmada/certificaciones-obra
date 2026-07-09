import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class AsignarRubrosDesdeItemizado {
    private static final String URL = "jdbc:mysql://localhost:3306/certificaciones_obra?useSSL=false&serverTimezone=America/Argentina/Buenos_Aires&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "";
    private static final Pattern CODIGO = Pattern.compile("\\d+(?:\\.\\d+)*");
    private static final Pattern ITEM_ID = Pattern.compile("\\d+(?:\\.\\d+)+");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Indica el archivo del itemizado.");
        }
        Path archivo = Path.of(args[0]);
        boolean aplicar = args.length > 1 && "--apply".equalsIgnoreCase(args[1]);
        List<FilaItemizado> filas = leerItemizado(archivo);

        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            connection.setAutoCommit(false);
            Resultado resultado = procesar(connection, filas, aplicar);
            if (aplicar) {
                connection.commit();
            } else {
                connection.rollback();
            }
            imprimir(resultado, aplicar);
        }
    }

    private static Resultado procesar(Connection connection, List<FilaItemizado> filas, boolean aplicar) throws Exception {
        Map<String, RubroInfo> rubrosExistentes = cargarRubros(connection);
        Map<String, RubroInfo> rubrosCreados = new LinkedHashMap<>();
        Map<String, Integer> itemsEnBase = contarItemsPorCodigo(connection);
        Map<String, Long> asignaciones = new LinkedHashMap<>();
        Map<String, String> rubroPorItem = new LinkedHashMap<>();
        Set<String> itemsNoEncontrados = new LinkedHashSet<>();
        Set<String> itemsSinRubro = new LinkedHashSet<>();
        List<String> rubrosNuevos = new ArrayList<>();
        List<RubroJerarquico> pila = new ArrayList<>();
        Long rubroActualId = null;
        int itemsDetectados = 0;
        int rubrosActualizados = 0;
        int filasActualizadas = 0;

        for (FilaItemizado fila : filas) {
            if (fila.esRubro()) {
                int nivel = nivel(fila.codigo());
                while (pila.size() >= nivel) {
                    pila.remove(pila.size() - 1);
                }
                Long padreId = pila.isEmpty() ? null : pila.get(pila.size() - 1).id();
                String clave = claveRubro(fila.codigo(), fila.actividad(), padreId);
                RubroInfo rubro = rubrosExistentes.get(clave);
                if (rubro == null) {
                    rubro = rubrosCreados.get(clave);
                }
                if (rubro == null) {
                    long id = insertarRubro(connection, fila.codigo(), fila.actividad(), padreId);
                    rubro = new RubroInfo(id, fila.codigo(), fila.actividad(), padreId);
                    rubrosCreados.put(clave, rubro);
                    rubrosNuevos.add(fila.codigo() + " - " + fila.actividad());
                } else if (!Objects.equals(rubro.padreId(), padreId) || !rubro.nombre().equals(fila.actividad())) {
                    actualizarRubro(connection, rubro.id(), fila.codigo(), fila.actividad(), padreId);
                    rubro = new RubroInfo(rubro.id(), fila.codigo(), fila.actividad(), padreId);
                    rubrosActualizados++;
                }
                pila.add(new RubroJerarquico(nivel, rubro.id()));
                rubroActualId = rubro.id();
                continue;
            }

            if (!fila.esItem()) {
                continue;
            }
            itemsDetectados++;
            if (rubroActualId == null) {
                itemsSinRubro.add(fila.itemId());
                continue;
            }
            int cantidadBase = itemsEnBase.getOrDefault(fila.itemId(), 0);
            if (cantidadBase == 0) {
                itemsNoEncontrados.add(fila.itemId() + " - " + fila.actividad());
                continue;
            }
            int actualizados = actualizarItem(connection, fila.itemId(), rubroActualId, aplicar);
            filasActualizadas += actualizados;
            asignaciones.put(fila.itemId(), rubroActualId);
            rubroPorItem.put(fila.itemId(), nombreRubro(connection, rubroActualId));
        }

        return new Resultado(
                itemsDetectados,
                asignaciones.size(),
                filasActualizadas,
                rubrosNuevos,
                rubrosActualizados,
                itemsNoEncontrados,
                itemsSinRubro,
                rubroPorItem
        );
    }

    private static List<FilaItemizado> leerItemizado(Path archivo) throws Exception {
        List<FilaItemizado> filas = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(archivo), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                String[] columnas = linea.split("\t", -1);
                if (columnas.length < 4) {
                    continue;
                }
                String codigo = limpiar(columna(columnas, 1));
                String actividad = repararTexto(limpiar(columna(columnas, 2)));
                String itemId = limpiar(columna(columnas, 3));
                if (actividad.isBlank() || codigo.equalsIgnoreCase("ITEM") || actividad.equalsIgnoreCase("ACTIVIDAD")) {
                    continue;
                }
                filas.add(new FilaItemizado(codigo, actividad, itemId));
            }
        }
        return filas;
    }

    private static Map<String, RubroInfo> cargarRubros(Connection connection) throws Exception {
        Map<String, RubroInfo> rubros = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT id, codigo, nombre, padre_id FROM rubro");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RubroInfo rubro = new RubroInfo(
                        rs.getLong("id"),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getObject("padre_id") == null ? null : rs.getLong("padre_id")
                );
                rubros.put(claveRubro(rubro.codigo(), rubro.nombre(), rubro.padreId()), rubro);
            }
        }
        return rubros;
    }

    private static Map<String, Integer> contarItemsPorCodigo(Connection connection) throws Exception {
        Map<String, Integer> items = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT item, COUNT(*) cantidad FROM item_orden_compra WHERE categoria = 'MANO_OBRA' GROUP BY item");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.put(rs.getString("item"), rs.getInt("cantidad"));
            }
        }
        return items;
    }

    private static long insertarRubro(Connection connection, String codigo, String nombre, Long padreId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO rubro (activo, codigo, nombre, padre_id) VALUES (true, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codigo);
            ps.setString(2, nombre);
            if (padreId == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, padreId);
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void actualizarRubro(Connection connection, long id, String codigo, String nombre, Long padreId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE rubro SET codigo = ?, nombre = ?, padre_id = ?, activo = true WHERE id = ?")) {
            ps.setString(1, codigo);
            ps.setString(2, nombre);
            if (padreId == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, padreId);
            }
            ps.setLong(4, id);
            ps.executeUpdate();
        }
    }

    private static int actualizarItem(Connection connection, String itemId, long rubroId, boolean aplicar) throws Exception {
        String sql = aplicar
                ? "UPDATE item_orden_compra SET rubro_entidad_id = ? WHERE categoria = 'MANO_OBRA' AND item = ?"
                : "SELECT COUNT(*) FROM item_orden_compra WHERE categoria = 'MANO_OBRA' AND item = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (aplicar) {
                ps.setLong(1, rubroId);
                ps.setString(2, itemId);
                return ps.executeUpdate();
            }
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static String nombreRubro(Connection connection, long id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT codigo, nombre FROM rubro WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("codigo") + " - " + rs.getString("nombre");
            }
        }
    }

    private static void imprimir(Resultado resultado, boolean aplicar) {
        System.out.println(aplicar ? "Asignacion aplicada." : "Simulacion terminada. No se guardaron cambios.");
        System.out.println("Items del archivo con ID: " + resultado.itemsDetectados());
        System.out.println("Items encontrados por ID unico: " + resultado.itemsEncontrados());
        System.out.println("Filas de base alcanzadas: " + resultado.filasActualizadas());
        System.out.println("Rubros nuevos detectados/creados: " + resultado.rubrosNuevos().size());
        System.out.println("Rubros existentes ajustados: " + resultado.rubrosActualizados());
        System.out.println("Items no encontrados: " + resultado.itemsNoEncontrados().size());
        resultado.itemsNoEncontrados().stream().limit(30).forEach(item -> System.out.println("  NO: " + item));
        if (resultado.itemsNoEncontrados().size() > 30) {
            System.out.println("  ... " + (resultado.itemsNoEncontrados().size() - 30) + " mas");
        }
        System.out.println("Primeras asignaciones:");
        resultado.rubroPorItem().entrySet().stream()
                .limit(40)
                .forEach(entry -> System.out.println("  " + entry.getKey() + " -> " + entry.getValue()));
    }

    private static int nivel(String codigo) {
        return codigo.split("\\.").length;
    }

    private static String claveRubro(String codigo, String nombre, Long padreId) {
        return normalizar(codigo) + "|" + normalizar(nombre) + "|" + (padreId == null ? "" : padreId);
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String limpiar(String texto) {
        return texto == null ? "" : texto.replace("\"", "").trim();
    }

    private static String columna(String[] columnas, int indice) {
        return indice >= columnas.length ? "" : columnas[indice];
    }

    private static String repararTexto(String texto) {
        if (!texto.contains("Ã") && !texto.contains("Â")) {
            return texto;
        }
        return new String(texto.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    private record FilaItemizado(String codigo, String actividad, String itemId) {
        boolean esRubro() {
            return CODIGO.matcher(codigo).matches() && itemId.isBlank();
        }

        boolean esItem() {
            return ITEM_ID.matcher(itemId).matches();
        }
    }

    private record RubroInfo(Long id, String codigo, String nombre, Long padreId) {
    }

    private record RubroJerarquico(int nivel, Long id) {
    }

    private record Resultado(
            int itemsDetectados,
            int itemsEncontrados,
            int filasActualizadas,
            List<String> rubrosNuevos,
            int rubrosActualizados,
            Set<String> itemsNoEncontrados,
            Set<String> itemsSinRubro,
            Map<String, String> rubroPorItem
    ) {
    }
}
