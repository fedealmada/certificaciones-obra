package com.obra.certificaciones.oc.entity;

public enum CategoriaItem {
    MANO_OBRA("Mano de Obra"),
    MATERIAL("Material"),
    OTRO("Otro");

    private final String descripcion;

    CategoriaItem(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
