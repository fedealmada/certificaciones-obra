package com.obra.certificaciones.controlrubro.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    public BigDecimal totalIncidenciaPorcentaje() {
        if (totalManoObra == null || totalMateriales == null || totalMateriales.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return totalManoObra.multiply(BigDecimal.valueOf(100)).divide(totalMateriales, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal totalPresupuestoPendiente() {
        BigDecimal techo = totalTechoDireccion == null ? BigDecimal.ZERO : totalTechoDireccion;
        BigDecimal gasto = totalGastosReales == null ? BigDecimal.ZERO : totalGastosReales;
        return techo.subtract(gasto);
    }
}
