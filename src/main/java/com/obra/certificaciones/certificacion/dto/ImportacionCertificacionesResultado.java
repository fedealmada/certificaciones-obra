package com.obra.certificaciones.certificacion.dto;

import java.util.List;

public record ImportacionCertificacionesResultado(
        List<OrdenImportadaVista> ordenes,
        int ordenesDetectadas,
        int certificadosDetectados,
        int certificadosImportados,
        boolean importado
) {
    public boolean tieneErrores() {
        return ordenes.stream().anyMatch(OrdenImportadaVista::tieneErrores);
    }

    public boolean puedeImportar() {
        return !tieneErrores() && certificadosDetectados > 0;
    }
}
