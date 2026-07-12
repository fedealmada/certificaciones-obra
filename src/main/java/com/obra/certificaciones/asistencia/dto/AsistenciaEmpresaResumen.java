package com.obra.certificaciones.asistencia.dto;

import java.math.BigDecimal;

public record AsistenciaEmpresaResumen(
        String empresa,
        long personas,
        BigDecimal horas
) {
}
