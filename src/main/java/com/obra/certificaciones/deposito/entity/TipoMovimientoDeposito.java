package com.obra.certificaciones.deposito.entity;

public enum TipoMovimientoDeposito {
    ENTRADA("Entrada"),
    SALIDA("Salida"),
    DEVOLUCION("Devolucion"),
    AJUSTE("Ajuste");

    private final String descripcion;

    TipoMovimientoDeposito(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
