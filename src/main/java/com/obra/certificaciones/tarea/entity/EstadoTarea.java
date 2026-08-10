package com.obra.certificaciones.tarea.entity;

public enum EstadoTarea {
    PENDIENTE("Pendiente", "is-pending"),
    EN_CURSO("En curso", "is-progress"),
    PAUSADA("Pausada", "is-paused"),
    COMPLETADA("Completada", "is-done");

    private final String etiqueta;
    private final String cssClass;

    EstadoTarea(String etiqueta, String cssClass) {
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
