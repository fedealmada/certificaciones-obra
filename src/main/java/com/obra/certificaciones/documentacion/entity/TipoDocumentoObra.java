package com.obra.certificaciones.documentacion.entity;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;

public enum TipoDocumentoObra {
    DNI("DNI", TipoVinculoDocumental.MONOTRIBUTISTA, TipoVinculoDocumental.RELACION_DEPENDENCIA),
    CONSTANCIA_AFIP("Constancia AFIP / monotributo", TipoVinculoDocumental.MONOTRIBUTISTA, TipoVinculoDocumental.RELACION_DEPENDENCIA, TipoVinculoDocumental.VISITA_MONOTRIBUTISTA),
    PAGO_MONOTRIBUTO("Ultimo pago monotributo / autonomo", TipoVinculoDocumental.MONOTRIBUTISTA, TipoVinculoDocumental.VISITA_MONOTRIBUTISTA),
    FORMULARIO_931("Formulario 931", TipoVinculoDocumental.RELACION_DEPENDENCIA),
    ART("ART con nomina y clausula", TipoVinculoDocumental.RELACION_DEPENDENCIA, TipoVinculoDocumental.VISITA_PROVEEDOR),
    SVO("Seguro de vida obligatorio", TipoVinculoDocumental.RELACION_DEPENDENCIA, TipoVinculoDocumental.VISITA_PROVEEDOR),
    ACCIDENTES_PERSONALES("Poliza accidentes personales", TipoVinculoDocumental.MONOTRIBUTISTA, TipoVinculoDocumental.VISITA_PROVEEDOR, TipoVinculoDocumental.VISITA_MONOTRIBUTISTA),
    CLAUSULA_NO_REPETICION("Clausula de no repeticion", TipoVinculoDocumental.MONOTRIBUTISTA, TipoVinculoDocumental.RELACION_DEPENDENCIA, TipoVinculoDocumental.VISITA_PROVEEDOR, TipoVinculoDocumental.VISITA_MONOTRIBUTISTA, TipoVinculoDocumental.VEHICULO_MAQUINARIA),
    IERIC("IERIC", TipoVinculoDocumental.RELACION_DEPENDENCIA),
    UOCRA("UOCRA", TipoVinculoDocumental.RELACION_DEPENDENCIA),
    UECARA("UECARA", TipoVinculoDocumental.RELACION_DEPENDENCIA),
    AVISO_OBRA("Aviso de obra", TipoVinculoDocumental.RELACION_DEPENDENCIA),
    PROGRAMA_SEGURIDAD("Programa de seguridad", TipoVinculoDocumental.RELACION_DEPENDENCIA),
    ATS("ATS", TipoVinculoDocumental.MONOTRIBUTISTA),
    CAPACITACIONES("Capacitaciones", TipoVinculoDocumental.MONOTRIBUTISTA, TipoVinculoDocumental.RELACION_DEPENDENCIA),
    ENTREGA_EPP("Registro entrega EPP", TipoVinculoDocumental.MONOTRIBUTISTA, TipoVinculoDocumental.RELACION_DEPENDENCIA),
    LICENCIA_CONDUCIR("Licencia de conducir", TipoVinculoDocumental.MONOTRIBUTISTA, TipoVinculoDocumental.RELACION_DEPENDENCIA),
    SEGURO_AUTOMOTOR("Seguro automotor", TipoVinculoDocumental.VEHICULO_MAQUINARIA),
    SEGURO_TECNICO("Seguro tecnico", TipoVinculoDocumental.VEHICULO_MAQUINARIA),
    VTV("VTV / verificacion tecnica", TipoVinculoDocumental.VEHICULO_MAQUINARIA),
    ALTAS_BAJAS_NOMINA("Altas / bajas de nomina", TipoVinculoDocumental.MONOTRIBUTISTA, TipoVinculoDocumental.RELACION_DEPENDENCIA),
    CONTRATO_SERVICIO("Contrato de locacion de servicio", TipoVinculoDocumental.MONOTRIBUTISTA),
    VISITA_SEGURIDAD_HIGIENE("Visita seguridad e higiene", TipoVinculoDocumental.MONOTRIBUTISTA, TipoVinculoDocumental.RELACION_DEPENDENCIA),
    TECNICO_PERMANENTE("Tecnico permanente", TipoVinculoDocumental.MONOTRIBUTISTA, TipoVinculoDocumental.RELACION_DEPENDENCIA),
    OTRO("Otro");

    private final String descripcion;
    private final EnumSet<TipoVinculoDocumental> vinculos;

    TipoDocumentoObra(String descripcion, TipoVinculoDocumental... vinculos) {
        this.descripcion = descripcion;
        this.vinculos = vinculos.length == 0
                ? EnumSet.allOf(TipoVinculoDocumental.class)
                : EnumSet.copyOf(Arrays.asList(vinculos));
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean aplicaA(TipoVinculoDocumental vinculo) {
        return vinculo == TipoVinculoDocumental.GENERAL || vinculos.contains(vinculo);
    }

    public String getVinculosCsv() {
        return vinculos.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
