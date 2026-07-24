package com.obra.certificaciones.documentacion.dto;

import java.util.List;

public record DocumentacionResumen(
        long total,
        long aptos,
        long porVencer,
        long vencidos,
        long pendientes,
        long mensualesPendientes,
        List<ContratistaDocumentacionResumen> contratistas
) {
}