package com.obra.certificaciones.documentacion.entity;

public enum TipoDocumentoObra {
    DNI("DNI"),
    CONSTANCIA_AFIP("Constancia AFIP / monotributo"),
    PAGO_MONOTRIBUTO("Ultimo pago monotributo / autonomo"),
    FORMULARIO_931("Formulario 931"),
    ART("ART con nomina y clausula"),
    SVO("Seguro de vida obligatorio"),
    ACCIDENTES_PERSONALES("Poliza accidentes personales"),
    CLAUSULA_NO_REPETICION("Clausula de no repeticion"),
    IERIC("IERIC"),
    UOCRA("UOCRA"),
    UECARA("UECARA"),
    AVISO_OBRA("Aviso de obra"),
    PROGRAMA_SEGURIDAD("Programa de seguridad"),
    ATS("ATS"),
    CAPACITACIONES("Capacitaciones"),
    ENTREGA_EPP("Registro entrega EPP"),
    LICENCIA_CONDUCIR("Licencia de conducir"),
    SEGURO_AUTOMOTOR("Seguro automotor"),
    SEGURO_TECNICO("Seguro tecnico"),
    VTV("VTV / verificacion tecnica"),
    ALTAS_BAJAS_NOMINA("Altas / bajas de nomina"),
    CONTRATO_SERVICIO("Contrato de locacion de servicio"),
    VISITA_SEGURIDAD_HIGIENE("Visita seguridad e higiene"),
    TECNICO_PERMANENTE("Tecnico permanente"),
    OTRO("Otro");

    private final String descripcion;

    TipoDocumentoObra(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}