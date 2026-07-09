package com.obra.certificaciones.certificacion.dto;

import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import java.math.BigDecimal;

public record ItemCertificacionCalculado(
        ItemOrdenCompra itemOrdenCompra,
        BigDecimal porcentajeAnterior,
        BigDecimal porcentajeActual,
        BigDecimal porcentajeAcumulado,
        BigDecimal montoActual
) {
    public boolean isTerminado() {
        return porcentajeAcumuladoSeguro().compareTo(BigDecimal.valueOf(100)) >= 0;
    }

    public BigDecimal porcentajeBarra() {
        return porcentajeAcumuladoSeguro().min(BigDecimal.valueOf(100));
    }

    public BigDecimal montoAnterior() {
        return calcularMonto(porcentajeAnterior);
    }

    public BigDecimal montoAcumulado() {
        return calcularMonto(porcentajeAcumulado);
    }

    private BigDecimal porcentajeAcumuladoSeguro() {
        return porcentajeAcumulado == null ? BigDecimal.ZERO : porcentajeAcumulado;
    }

    private BigDecimal calcularMonto(BigDecimal porcentaje) {
        BigDecimal importe = itemOrdenCompra == null || itemOrdenCompra.getImporte() == null
                ? BigDecimal.ZERO
                : itemOrdenCompra.getImporte();
        BigDecimal porcentajeSeguro = porcentaje == null ? BigDecimal.ZERO : porcentaje;
        return importe.multiply(porcentajeSeguro).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }
}
