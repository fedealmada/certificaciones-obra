package com.obra.certificaciones.oc.entity;

public enum ModoSeguimientoOrden {
    CERTIFICACION("Certificable", "Se controla con certificados de avance"),
    ENTREGA("Entregas / viajes", "Se controla por recepciones, viajes o unidades recibidas"),
    REGISTRO("Solo registro", "Queda asentada sin seguimiento operativo");

    private final String descripcion;
    private final String ayuda;

    ModoSeguimientoOrden(String descripcion, String ayuda) {
        this.descripcion = descripcion;
        this.ayuda = ayuda;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getAyuda() {
        return ayuda;
    }
}
