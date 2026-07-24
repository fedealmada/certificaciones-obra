package com.obra.certificaciones.notapedido.entity;

public enum EstadoNotaPedido {
    BORRADOR("Borrador", "is-draft"),
    ENVIADA("Enviada", "is-sent"),
    EN_REVISION("En revision", "is-review"),
    APROBADA("Aprobada", "is-approved"),
    RECHAZADA("Rechazada", "is-rejected"),
    CONVERTIDA_OC("Convertida en OC", "is-converted");

    private final String descripcion;
    private final String estilo;

    EstadoNotaPedido(String descripcion, String estilo) {
        this.descripcion = descripcion;
        this.estilo = estilo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getEstilo() {
        return estilo;
    }
}
