package com.obra.certificaciones.certificacion.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ItemNuevaCertificacionForm {
    private Long itemOrdenCompraId;
    private BigDecimal porcentajeActual = BigDecimal.ZERO;
}
