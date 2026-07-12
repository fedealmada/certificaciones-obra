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
        long ordenesEntrega,
        long entregasPendientes,
        long entregasParciales,
        long entregasCompletas,
        BigDecimal materialRecibidoEstimado,
        BigDecimal materialPendienteEntrega,
        int porcentajeMaterialRecibido,
        List<GraficoDato> estadosItems,
        List<GraficoDato> importesPorTipo,
        List<GraficoDato> estadosEntregas,
        List<GraficoDato> topProveedores,
        List<GraficoDato> topOrdenes,
        List<GraficoDato> topMaterialesPendientes,
        List<GraficoDato> topProveedoresMateriales,
        String estadosPieCss,
        String importesPieCss,
        String estadosEntregasPieCss
) {
}
