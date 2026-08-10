package com.obra.certificaciones.tarea.entity;

public enum PrioridadTarea {
    BAJA("Baja", "is-low"),
    MEDIA("Media", "is-medium"),
    ALTA("Alta", "is-high"),
    URGENTE("Urgente", "is-urgent");

    private final String etiqueta;
    private final String cssClass;

    PrioridadTarea(String etiqueta, String cssClass) {
        this.etiqueta = etiqueta;
        this.cssClass = cssClass;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getCssClass() {
        return cssClass;
    }
}
