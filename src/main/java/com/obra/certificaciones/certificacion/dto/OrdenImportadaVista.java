package com.obra.certificaciones.certificacion.dto;

import java.util.List;

public record OrdenImportadaVista(
        String numeroOc,
        String contratista,
        String tarea,
        Long ordenCompraId,
        String proveedorEncontrado,
        int certificadosExistentes,
        List<CertificadoImportadoVista> certificados,
        List<ItemImportadoVista> items,
        List<String> errores,
        List<String> avisos
) {
    public boolean encontrada() {
        return ordenCompraId != null;
    }

    public boolean tieneErrores() {
        return !errores.isEmpty();
    }

    public int certificadosConAvance() {
        return (int) certificados.stream().filter(CertificadoImportadoVista::tieneAvance).count();
    }
}
