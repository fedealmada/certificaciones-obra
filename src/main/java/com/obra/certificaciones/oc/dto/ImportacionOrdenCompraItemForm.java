package com.obra.certificaciones.oc.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ImportacionOrdenCompraItemForm {
    private String item;
    private String detalle;
    private String unidad;
    private BigDecimal cantidad = BigDecimal.ZERO;
    private BigDecimal precioUnitario = BigDecimal.ZERO;
    private BigDecimal importe = BigDecimal.ZERO;
}
