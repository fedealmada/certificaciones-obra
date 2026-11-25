package com.obra.certificaciones.certificacion.dto;

import java.time.LocalDate;
import lombok.Getter;

@Getter
public class UltimoCertificadoOrden {
    private final Long ordenCompraId;
    private final Integer numero;
    private final LocalDate fecha;

    public UltimoCertificadoOrden(Long ordenCompraId, Integer numero, LocalDate fecha) {
        this.ordenCompraId = ordenCompraId;
        this.numero = numero;
        this.fecha = fecha;
    }
}
