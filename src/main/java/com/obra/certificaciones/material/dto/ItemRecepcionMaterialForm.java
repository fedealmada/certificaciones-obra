package com.obra.certificaciones.material.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ItemRecepcionMaterialForm {
    private Long itemOrdenCompraId;
    private BigDecimal cantidadRecibida = BigDecimal.ZERO;
}
