package com.obra.certificaciones.documentacion.entity;

public enum TipoVinculoDocumental {
    RELACION_DEPENDENCIA("Relacion de dependencia"),
    MONOTRIBUTISTA("Monotributista / autonomo"),
    VISITA_PROVEEDOR("Visita / proveedor"),
    VISITA_MONOTRIBUTISTA("Visita monotributista"),
    VEHICULO_MAQUINARIA("Vehiculo / maquinaria"),
    GENERAL("General");

    private final String descripcion;

    TipoVinculoDocumental(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}