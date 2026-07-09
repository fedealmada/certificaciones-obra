package com.obra.certificaciones.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GastoMensualItem(
        LocalDate fecha,
        Long ordenCompraId,
        String ordenCompraNumero,
        String proveedor,
        String categoria,
        String item,
        String detalle,
        String rubro,
        BigDecimal importe
) {
}
