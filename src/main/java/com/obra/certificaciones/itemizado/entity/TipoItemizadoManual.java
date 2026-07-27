package com.obra.certificaciones.itemizado.entity;

public enum TipoItemizadoManual {
    MANO_OBRA("Mano de obra"),
    MATERIAL("Material");

    private final String descripcion;

    TipoItemizadoManual(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
