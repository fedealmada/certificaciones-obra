package com.obra.certificaciones.certificacion.dto;

import java.time.LocalDate;

public record CertificadoImportadoVista(
        int numero,
        LocalDate fecha,
        int itemsConAvance
) {
    public boolean tieneAvance() {
        return itemsConAvance > 0;
    }
}
