package com.obra.certificaciones.certificacion.dto;

import com.obra.certificaciones.oc.entity.ItemOrdenCompra;

import java.math.BigDecimal;

public record ItemOrdenCompraResumen(
        ItemOrdenCompra itemOrdenCompra,
        BigDecimal porcentajeAcumulado,
        BigDecimal montoAcumulado,
        BigDecimal saldoPendiente,
        EstadoItemCertificacion estado
) {
}
