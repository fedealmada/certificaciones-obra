package com.obra.certificaciones.material.dto;

public enum EstadoRecepcionMaterial {
    PENDIENTE("Pendiente"),
    PARCIAL("Parcial"),
    COMPLETO("Completo");

    private final String descripcion;

    EstadoRecepcionMaterial(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
