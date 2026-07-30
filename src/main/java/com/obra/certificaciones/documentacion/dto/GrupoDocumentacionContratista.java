package com.obra.certificaciones.documentacion.dto;

import com.obra.certificaciones.documentacion.entity.DocumentoObra;
import com.obra.certificaciones.documentacion.entity.EstadoDocumentoObra;

import java.util.List;

public record GrupoDocumentacionContratista(
        Long carpetaId,
        Long carpetaPadreId,
        Long proveedorId,
        String nombre,
        String nombreContratista,
        String apodo,
        String color,
        Integer orden,
        EstadoDocumentoObra estado,
        long total,
        long aptos,
        long porVencer,
        long vencidos,
        long pendientes,
        String tipoCss,
        String tipoDescripcion,
        boolean general,
        List<DocumentoObra> documentos
) {
}
