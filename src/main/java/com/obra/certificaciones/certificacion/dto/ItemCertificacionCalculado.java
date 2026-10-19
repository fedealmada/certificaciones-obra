package com.obra.certificaciones.certificacion.dto;

import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import java.math.BigDecimal;

public record ItemCertificacionCalculado(
        ItemOrdenCompra itemOrdenCompra,
        BigDecimal porcentajeAnterior,
        BigDecimal porcentajeActual,
        BigDecimal porcentajeAcumulado,
        BigDecimal montoActual,
        BigDecimal importeAnterior,
        BigDecimal importeAcumulado
) {
    public ItemCertificacionCalculado(ItemOrdenCompra item, BigDecimal anterior, BigDecimal actual, BigDecimal acumulado, BigDecimal monto) {
        this(item, anterior, actual, acumulado, monto, null, null);
    }
    public BigDecimal cantidadAnterior() { return cantidad(porcentajeAnterior); }
    public BigDecimal cantidadActual() { return cantidad(porcentajeActual); }
    public BigDecimal cantidadAcumulada() { return cantidad(porcentajeAcumulado); }
    private BigDecimal cantidad(BigDecimal porcentaje) {
        return itemOrdenCompra.getCantidad().multiply(porcentaje).divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP);
    }
    public boolean isTerminado() {
        return porcentajeAcumuladoSeguro().compareTo(BigDecimal.valueOf(100)) >= 0;
    }

    public BigDecimal porcentajeBarra() {
        return porcentajeAcumuladoSeguro().min(BigDecimal.valueOf(100));
    }

    public BigDecimal montoAnterior() {
        return importeAnterior != null ? importeAnterior : calcularMonto(porcentajeAnterior);
    }

    public BigDecimal montoAcumulado() {
        return importeAcumulado != null ? importeAcumulado : calcularMonto(porcentajeAcumulado);
    }

    private BigDecimal porcentajeAcumuladoSeguro() {
        return porcentajeAcumulado == null ? BigDecimal.ZERO : porcentajeAcumulado.setScale(10, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal calcularMonto(BigDecimal porcentaje) {
        BigDecimal importe = itemOrdenCompra == null || itemOrdenCompra.getImporte() == null
                ? BigDecimal.ZERO
                : itemOrdenCompra.getImporte();
        BigDecimal porcentajeSeguro = porcentaje == null ? BigDecimal.ZERO : porcentaje;
        return importe.multiply(porcentajeSeguro).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }
}
