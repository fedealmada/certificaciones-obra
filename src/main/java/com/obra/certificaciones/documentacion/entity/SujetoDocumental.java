package com.obra.certificaciones.documentacion.entity;

public enum SujetoDocumental {
    CONTRATISTA("Contratista"),
    PERSONA("Persona"),
    VEHICULO("Vehiculo / maquinaria"),
    OBRA("Obra general");

    private final String descripcion;

    SujetoDocumental(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}