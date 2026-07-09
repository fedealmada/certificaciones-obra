package com.obra.certificaciones.reporte.dto;

import java.math.BigDecimal;

public record EvolucionMensualGasto(
        String periodo,
        String etiqueta,
        BigDecimal total,
        int porcentajeBarra,
        String variacionTexto,
        String variacionEstado
) {
}
