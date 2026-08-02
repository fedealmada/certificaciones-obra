package com.obra.certificaciones.calendario.entity;

public enum TipoEventoObra {
    ART("Visita ART", "is-art"),
    INMOBILIARIA("Visita inmobiliaria", "is-real-estate"),
    HORMIGON("Hormigon", "is-concrete"),
    INSPECCION("Inspeccion", "is-inspection"),
    ENTREGA("Entrega importante", "is-delivery"),
    REUNION("Reunion", "is-meeting"),
    SEGURIDAD_HIGIENE("Seguridad e higiene", "is-safety"),
    GENERAL("General", "is-general");

    private final String descripcion;
    private final String cssClass;

    TipoEventoObra(String descripcion, String cssClass) {
        this.descripcion = descripcion;
        this.cssClass = cssClass;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getCssClass() {
        return cssClass;
    }
}
