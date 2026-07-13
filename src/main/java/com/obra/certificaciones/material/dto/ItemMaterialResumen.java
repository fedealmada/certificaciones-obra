package com.obra.certificaciones.material.dto;

import com.obra.certificaciones.oc.entity.ItemOrdenCompra;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ItemMaterialResumen(
        ItemOrdenCompra itemOrdenCompra,
        BigDecimal cantidadComprada,
        BigDecimal cantidadRecibida,
        BigDecimal cantidadPendiente,
        EstadoRecepcionMaterial estado
) {
    public BigDecimal porcentajeRecibido() {
        if (cantidadComprada == null || cantidadComprada.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal porcentaje = cantidadRecibida == null
                ? BigDecimal.ZERO
                : cantidadRecibida.multiply(BigDecimal.valueOf(100)).divide(cantidadComprada, 2, RoundingMode.HALF_UP);
        return porcentaje.min(BigDecimal.valueOf(100));
    }

    public BigDecimal montoRecibido() {
        return montoPorCantidad(cantidadRecibida);
    }

    public BigDecimal montoPendiente() {
        return montoPorCantidad(cantidadPendiente);
    }

    private BigDecimal montoPorCantidad(BigDecimal cantidad) {
        if (cantidad == null || itemOrdenCompra == null || itemOrdenCompra.getPrecioUnitario() == null) {
            return BigDecimal.ZERO;
        }
        return cantidad.multiply(itemOrdenCompra.getPrecioUnitario());
    }
}
