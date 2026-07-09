package com.obra.certificaciones.alerta.dto;

public record AlertaSistema(
        String prioridad,
        String titulo,
        String detalle,
        String enlace,
        String icono
) {
}
