package com.obra.certificaciones.calendario.entity;

public enum EstadoEventoObra {
    PROGRAMADO("Programado", "is-scheduled"),
    REALIZADO("Realizado", "is-done"),
    CANCELADO("Cancelado", "is-cancelled");

    private final String descripcion;
    private final String cssClass;

    EstadoEventoObra(String descripcion, String cssClass) {
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
