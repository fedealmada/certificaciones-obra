package com.obra.certificaciones.tablero.entity;

import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Getter
@Setter
public class TableroCertificadoItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private TableroCertificado tablero;

    @ManyToOne(fetch = FetchType.LAZY)
    private ItemOrdenCompra itemOrdenCompra;

    private boolean grupo;
    private String grupoNombre;
    private Integer ordenFila;
    private String contratista;
    private String rubro;
    private String codigoTarea;
    private String itemCodigo;

    @Column(length = 2000)
    private String descripcionTarea;

    private String unidad;
    private BigDecimal cantidad = BigDecimal.ZERO;
    private BigDecimal precioUnitario = BigDecimal.ZERO;
    private BigDecimal costoManoObra = BigDecimal.ZERO;
    private BigDecimal materialesAsignados = BigDecimal.ZERO;
    private BigDecimal servicios = BigDecimal.ZERO;
    private BigDecimal materialesSuministradosEmpresa = BigDecimal.ZERO;
    private BigDecimal materialesAdicionalesEtapa = BigDecimal.ZERO;
    private BigDecimal subtotalManual;
    private BigDecimal costoEstructuralPorcentaje = BigDecimal.valueOf(20);
    private BigDecimal beneficioEmpresarialPorcentaje = BigDecimal.valueOf(25);
    private BigDecimal avanceAnteriorPorcentaje = BigDecimal.ZERO;
    private BigDecimal avanceCertificadoPorcentaje = BigDecimal.ZERO;

    @Column(length = 1200)
    private String observacion;

    @Transient
    public BigDecimal costoSubtotal() {
        if (grupo && subtotalManual != null) {
            return escalar(subtotalManual);
        }
        return escalar(valor(costoManoObra)
                .add(valor(materialesAsignados))
                .add(valor(servicios))
                .add(valor(materialesSuministradosEmpresa)));
    }

    @Transient
    public BigDecimal costoEstructuralMonto() {
        return porcentaje(costoSubtotal(), costoEstructuralPorcentaje);
    }

    @Transient
    public BigDecimal beneficioEmpresarialMonto() {
        return porcentaje(costoSubtotal(), beneficioEmpresarialPorcentaje);
    }

    @Transient
    public BigDecimal estructuraMasBeneficio() {
        return escalar(costoEstructuralMonto().add(beneficioEmpresarialMonto()));
    }

    @Transient
    public BigDecimal totalTarea() {
        return escalar(costoSubtotal().add(estructuraMasBeneficio()));
    }

    @Transient
    public BigDecimal montoCertificado() {
        return escalar(porcentaje(totalTarea(), avanceCertificadoPorcentaje).add(valor(materialesAdicionalesEtapa)));
    }

    @Transient
    public BigDecimal avanceAcumuladoPorcentaje() {
        return valor(avanceAnteriorPorcentaje).add(valor(avanceCertificadoPorcentaje));
    }

    @Transient
    public BigDecimal montoAnterior() {
        return porcentaje(totalTarea(), avanceAnteriorPorcentaje);
    }

    @Transient
    public BigDecimal montoAcumulado() {
        return escalar(porcentaje(totalTarea(), avanceAcumuladoPorcentaje()).add(valor(materialesAdicionalesEtapa)));
    }

    public void recalcularManoObra() {
        costoManoObra = escalar(valor(cantidad).multiply(valor(precioUnitario)));
    }

    private BigDecimal porcentaje(BigDecimal base, BigDecimal porcentaje) {
        return escalar(valor(base).multiply(valor(porcentaje)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    }

    private BigDecimal valor(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private BigDecimal escalar(BigDecimal valor) {
        return valor(valor).setScale(2, RoundingMode.HALF_UP);
    }
}
