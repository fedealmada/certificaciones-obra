package com.obra.certificaciones.deposito.entity;

public enum TipoInsumoDeposito {
    CONSUMIBLE("Consumible"),
    HERRAMIENTA("Herramienta"),
    EPP("EPP"),
    OTRO("Otro");

    private final String descripcion;

    TipoInsumoDeposito(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
