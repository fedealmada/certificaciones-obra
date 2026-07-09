package com.obra.certificaciones.certificacion.dto;

public enum EstadoItemCertificacion {
    PENDIENTE("Pendiente"),
    EN_EJECUCION("En ejecucion"),
    TERMINADO("Terminado");

    private final String descripcion;

    EstadoItemCertificacion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
