package com.obra.certificaciones.deposito.dto;

import com.obra.certificaciones.deposito.entity.TipoMovimientoDeposito;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class MovimientoDepositoForm {
    private LocalDate fecha = LocalDate.now();
    private TipoMovimientoDeposito tipo = TipoMovimientoDeposito.ENTRADA;
    private BigDecimal cantidad = BigDecimal.ZERO;
    private String responsable;
    private Long trabajadorId;
    private String trabajadorNombre;
    private String destino;
    private String observacion;
    private boolean requiereDevolucion;
}
