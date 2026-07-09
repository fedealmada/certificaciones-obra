package com.obra.certificaciones.certificacion.dto;

import com.obra.certificaciones.oc.entity.ItemOrdenCompra;

import java.math.BigDecimal;

public record ItemNuevaCertificacionVista(
        ItemOrdenCompra itemOrdenCompra,
        BigDecimal porcentajeAnterior,
        BigDecimal porcentajePendiente,
        BigDecimal porcentajeActual
) {
}
