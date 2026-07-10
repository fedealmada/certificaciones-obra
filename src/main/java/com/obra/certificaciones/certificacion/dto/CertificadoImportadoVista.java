package com.obra.certificaciones.certificacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CertificadoImportadoVista(
        int numero,
        LocalDate fecha,
        int itemsConAvance,
        BigDecimal porcentajeActual,
        BigDecimal porcentajeAcumulado,
        BigDecimal importeActual
) {
    public boolean tieneAvance() {
        return itemsConAvance > 0;
    }
}
