package com.obra.certificaciones.documentacion.entity;

public enum EstadoCarpetaFisica {
    ACTUALIZADA("Actualizada", "is-ok"),
    DESACTUALIZADA("Desactualizada", "is-warning"),
    FALTANTE("Faltante", "is-danger");

    private final String descripcion;
    private final String cssClass;

    EstadoCarpetaFisica(String descripcion, String cssClass) {
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
