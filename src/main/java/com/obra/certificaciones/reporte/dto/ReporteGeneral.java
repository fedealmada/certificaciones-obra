package com.obra.certificaciones.reporte.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReporteGeneral(
        long cantidadOrdenes,
        long cantidadCertificaciones,
        BigDecimal totalContratadoManoObra,
        BigDecimal totalMateriales,
        BigDecimal totalOtros,
        BigDecimal totalCertificado,
        BigDecimal saldoPendiente,
        int porcentajeCertificado,
        long itemsPendientes,
        long itemsEnEjecucion,
        long itemsTerminados,
        List<GraficoDato> estadosItems,
        List<GraficoDato> importesPorTipo,
        List<GraficoDato> topProveedores,
        List<GraficoDato> topOrdenes,
        String estadosPieCss,
        String importesPieCss
) {
}
