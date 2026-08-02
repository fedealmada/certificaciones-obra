package com.obra.certificaciones.sincronizacion.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SincronizacionResultado(
        boolean exitoso,
        String accion,
        boolean backupGenerado,
        boolean commitCreado,
        boolean pushRealizado,
        boolean pullRealizado,
        boolean baseRestaurada,
        String mensaje,
        String backupPath,
        String commitMensaje,
        LocalDateTime fecha,
        List<String> salida
) {
}
