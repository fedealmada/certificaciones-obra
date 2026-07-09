package com.obra.certificaciones.certificacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistorialItemCertificacion(
        Long certificacionId,
        Integer numeroCertificacion,
        LocalDate fecha,
        BigDecimal porcentajeAnterior,
        BigDecimal porcentajeActual,
        BigDecimal porcentajeAcumulado,
        BigDecimal montoActual
) {
}
