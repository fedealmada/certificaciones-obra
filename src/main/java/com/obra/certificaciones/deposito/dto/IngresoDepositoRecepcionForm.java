package com.obra.certificaciones.deposito.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class IngresoDepositoRecepcionForm {
    private Long depositoItemId;
    private String nuevoInsumoNombre;
    private String categoria;
    private String ubicacion;
    private BigDecimal stockMinimo = BigDecimal.ZERO;
    private BigDecimal cantidad = BigDecimal.ZERO;
    private String responsable;
    private String observacion;
}
