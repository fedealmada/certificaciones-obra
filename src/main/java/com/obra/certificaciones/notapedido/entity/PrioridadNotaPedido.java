package com.obra.certificaciones.notapedido.entity;

public enum PrioridadNotaPedido {
    BAJA("Baja"),
    NORMAL("Normal"),
    ALTA("Alta"),
    URGENTE("Urgente");

    private final String descripcion;

    PrioridadNotaPedido(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
