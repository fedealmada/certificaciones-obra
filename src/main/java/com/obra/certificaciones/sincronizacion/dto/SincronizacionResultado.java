package com.obra.certificaciones.sincronizacion.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SincronizacionResultado(
        boolean exitoso,
        boolean backupGenerado,
        boolean commitCreado,
        boolean pushRealizado,
        String mensaje,
        String backupPath,
        String commitMensaje,
        LocalDateTime fecha,
        List<String> salida
) {
}
