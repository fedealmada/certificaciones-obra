package com.obra.certificaciones.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReporteMensual(
        int anio,
        int mes,
        String periodo,
        LocalDate desde,
        LocalDate hasta,
        BigDecimal total,
        BigDecimal totalManoObra,
        BigDecimal totalMateriales,
        BigDecimal totalOtros,
        int cantidadOrdenes,
        int cantidadItems,
        List<GraficoDato> importesPorTipo,
        List<GraficoDato> importesPorCategoria,
        List<GraficoDato> topProveedores,
        List<GastoMensualItem> items
) {
}
