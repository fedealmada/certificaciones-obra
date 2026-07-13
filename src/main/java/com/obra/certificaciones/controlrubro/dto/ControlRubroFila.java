package com.obra.certificaciones.controlrubro.dto;

import java.math.BigDecimal;

public record ControlRubroFila(
        Long rubroId,
        String codigo,
        String actividad,
        BigDecimal manoObra,
        BigDecimal incidenciaPorcentaje,
        BigDecimal incidenciaMonto,
        BigDecimal gastosReales,
        BigDecimal techoDireccion,
        BigDecimal saldoTecho,
        BigDecimal avancePorcentaje,
        BigDecimal avancePesos,
        BigDecimal pendienteEjecutar,
        String estado,
        String estadoTexto
) {
}
