package com.obra.certificaciones.reporte.dto;

import java.math.BigDecimal;

public record GraficoDato(
        String nombre,
        BigDecimal valor,
        int porcentaje
) {
}
