package com.obra.certificaciones.certificacion.dto;

import com.obra.certificaciones.certificacion.entity.Certificacion;

public record CertificacionResumenVista(
        Certificacion certificacion,
        ResumenCertificacion resumen
) {
}
