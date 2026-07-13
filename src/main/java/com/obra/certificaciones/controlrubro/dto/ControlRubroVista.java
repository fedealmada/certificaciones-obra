package com.obra.certificaciones.controlrubro.dto;

import java.math.BigDecimal;
import java.util.List;

public record ControlRubroVista(
        List<ControlRubroFila> filas,
        BigDecimal totalManoObra,
        BigDecimal totalMateriales,
        BigDecimal totalIncidencia,
        BigDecimal totalGastosReales,
        BigDecimal totalTechoDireccion,
        BigDecimal totalAvancePesos,
        BigDecimal totalPendienteEjecutar,
        long rubrosSobreTecho,
        BigDecimal avanceGeneralPorcentaje
) {
}
