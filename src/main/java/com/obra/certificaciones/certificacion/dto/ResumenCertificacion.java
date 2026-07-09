package com.obra.certificaciones.certificacion.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ResumenCertificacion(
        BigDecimal totalContratado,
        BigDecimal totalActual,
        BigDecimal totalAcumulado,
        BigDecimal saldoPendiente,
        int cantidadItemsCertificados
) {
    public BigDecimal porcentajeActual() {
        return porcentaje(totalActual);
    }

    public BigDecimal porcentajeAcumulado() {
        return porcentaje(totalAcumulado);
    }

    private BigDecimal porcentaje(BigDecimal monto) {
        if (totalContratado == null || totalContratado.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal montoSeguro = monto == null ? BigDecimal.ZERO : monto;
        return montoSeguro.multiply(BigDecimal.valueOf(100)).divide(totalContratado, 2, RoundingMode.HALF_UP);
    }
}
