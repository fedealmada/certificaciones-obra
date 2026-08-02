package com.obra.certificaciones.documentacion.entity;

public enum EstadoDocumentoObra {
    APTO("Apto", "is-ok"),
    SIN_VENCIMIENTO("Sin vencimiento", "is-info"),
    POR_VENCER("Por vencer", "is-warning"),
    VENCIDO("Vencido", "is-danger"),
    PENDIENTE("Pendiente", "is-pending"),
    NO_APLICA("No aplica", "is-muted");

    private final String descripcion;
    private final String cssClass;

    EstadoDocumentoObra(String descripcion, String cssClass) {
        this.descripcion = descripcion;
        this.cssClass = cssClass;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getCssClass() {
        return cssClass;
    }
}
