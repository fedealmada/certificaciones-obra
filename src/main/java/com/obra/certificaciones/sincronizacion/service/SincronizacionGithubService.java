package com.obra.certificaciones.sincronizacion.service;

import com.obra.certificaciones.sincronizacion.dto.SincronizacionResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SincronizacionGithubService {
    private static final DateTimeFormatter COMMIT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DataSource dataSource;

    @Value("${app.sync.project-root:.}")
    private String projectRoot;

    @Value("${app.sync.mysqldump:C:\\xampp\\mysql\\bin\\mysqldump.exe}")
    private String mysqldumpPath;

    @Value("${app.sync.backup-path:backups\\certificaciones_obra.sql}")
    private String backupPath;

    @Value("${spring.datasource.username:root}")
    private String dbUser;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    public List<String> estadoGit() {
        List<String> salida = new ArrayList<>();
        ejecutarGit(salida, "status", "--short");
        return salida;
    }

    public SincronizacionResultado sincronizar(String mensajeUsuario) {
        List<String> salida = new ArrayList<>();
        boolean backupGenerado = false;
        boolean commitCreado = false;
        boolean pushRealizado = false;
        Path root = Path.of(projectRoot).toAbsolutePath().normalize();
        Path backup = root.resolve(backupPath).normalize();
        String commitMensaje = limpiarMensaje(mensajeUsuario) + " - " + LocalDateTime.now().format(COMMIT_FORMAT);

        try {
            generarBackup(root, backup, salida);
            backupGenerado = true;

            ejecutarGitOk(salida, "add", "-A");
            List<String> cambios = ejecutarGit(salida, "status", "--short");
            if (cambios.isEmpty()) {
                salida.add("No habia cambios nuevos para commitear.");
            } else {
                ejecutarGitOk(salida, "commit", "-m", commitMensaje);
                commitCreado = true;
            }

            String rama = ramaActual(salida);
            ejecutarGitOk(salida, "push", "origin", rama);
            pushRealizado = true;

            return new SincronizacionResultado(
                    true,
                    backupGenerado,
                    commitCreado,
                    pushRealizado,
                    "Sincronizacion completada. Backup actualizado y cambios subidos a GitHub.",
                    backup.toString(),
                    commitMensaje,
                    LocalDateTime.now(),
                    salida
            );
        } catch (Exception e) {
            salida.add("ERROR: " + e.getMessage());
            return new SincronizacionResultado(
                    false,
                    backupGenerado,
                    commitCreado,
                    pushRealizado,
                    "No se pudo completar la sincronizacion. Revisa el detalle del proceso.",
                    backup.toString(),
                    commitMensaje,
                    LocalDateTime.now(),
                    salida
            );
        }
    }

    private void generarBackup(Path root, Path backup, List<String> salida) throws IOException, InterruptedException, SQLException {
        Path mysqldump = Path.of(mysqldumpPath).toAbsolutePath().normalize();
        if (!Files.exists(mysqldump)) {
            throw new IOException("No se encontro mysqldump en " + mysqldump);
        }
        Files.createDirectories(backup.getParent());

        String databaseName = obtenerBaseDatos();
        salida.add("Generando backup de base de datos: " + databaseName);

        List<String> comando = new ArrayList<>();
        comando.add(mysqldump.toString());
        comando.add("--default-character-set=utf8mb4");
        comando.add("--routines");
        comando.add("--events");
        comando.add("-u");
        comando.add(dbUser);
        if (dbPassword != null && !dbPassword.isBlank()) {
            comando.add("-p" + dbPassword);
        }
        comando.add("--result-file=" + backup);
        comando.add(databaseName);

        ResultadoProceso resultado = ejecutar(root, comando);
        salida.addAll(resultado.salida());
        if (resultado.exitCode() != 0) {
            throw new IOException("Fallo el backup de MySQL. Codigo: " + resultado.exitCode());
        }
        salida.add("Backup generado: " + backup);
    }

    private String obtenerBaseDatos() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            if (catalog != null && !catalog.isBlank()) {
                return catalog;
            }
            String url = connection.getMetaData().getURL();
            int slash = url.lastIndexOf('/');
            if (slash >= 0) {
                String name = url.substring(slash + 1);
                int query = name.indexOf('?');
                return query >= 0 ? name.substring(0, query) : name;
            }
        }
        return "certificaciones_obra";
    }

    private List<String> ejecutarGit(List<String> salida, String... args) {
        List<String> comando = new ArrayList<>();
        comando.add("git");
        comando.addAll(List.of(args));
        try {
            ResultadoProceso resultado = ejecutar(Path.of(projectRoot).toAbsolutePath().normalize(), comando);
            salida.add("$ " + String.join(" ", comando));
            salida.addAll(resultado.salida());
            return resultado.salida().stream().filter(linea -> !linea.isBlank()).toList();
        } catch (Exception e) {
            salida.add("ERROR git " + String.join(" ", args) + ": " + e.getMessage());
            return List.of();
        }
    }

    private void ejecutarGitOk(List<String> salida, String... args) throws IOException, InterruptedException {
        List<String> comando = new ArrayList<>();
        comando.add("git");
        comando.addAll(List.of(args));
        ResultadoProceso resultado = ejecutar(Path.of(projectRoot).toAbsolutePath().normalize(), comando);
        salida.add("$ " + String.join(" ", comando));
        salida.addAll(resultado.salida());
        if (resultado.exitCode() != 0) {
            throw new IOException("Fallo git " + String.join(" ", args) + ". Codigo: " + resultado.exitCode());
        }
    }

    private String ramaActual(List<String> salida) throws IOException, InterruptedException {
        ResultadoProceso resultado = ejecutar(Path.of(projectRoot).toAbsolutePath().normalize(), List.of("git", "branch", "--show-current"));
        salida.add("$ git branch --show-current");
        salida.addAll(resultado.salida());
        if (resultado.exitCode() != 0) {
            throw new IOException("No se pudo detectar la rama actual.");
        }
        return resultado.salida().stream().filter(linea -> !linea.isBlank()).findFirst().orElse("main");
    }

    private ResultadoProceso ejecutar(Path directorio, List<String> comando) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(comando);
        builder.directory(directorio.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        Process process = builder.start();
        List<String> salida = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                salida.add(line);
            }
        }
        boolean finished = process.waitFor(180, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("El proceso tardo demasiado y fue cancelado.");
        }
        return new ResultadoProceso(process.exitValue(), salida);
    }

    private String limpiarMensaje(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return "Backup y sincronizacion";
        }
        return mensaje.replaceAll("[\\r\\n]+", " ").trim();
    }

    private record ResultadoProceso(int exitCode, List<String> salida) {
    }
}
