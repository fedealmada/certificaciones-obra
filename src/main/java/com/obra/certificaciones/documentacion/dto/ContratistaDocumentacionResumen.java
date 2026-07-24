package com.obra.certificaciones.documentacion.dto;

import com.obra.certificaciones.documentacion.entity.EstadoDocumentoObra;

public record ContratistaDocumentacionResumen(
        String nombre,
        long total,
        long vencidos,
        long porVencer,
        long pendientes,
        EstadoDocumentoObra estado
) {
}