package com.obra.certificaciones.documentacion.entity;

import com.obra.certificaciones.deposito.entity.DepositoTrabajador;
import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.proveedor.entity.Proveedor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Getter
@Setter
public class DocumentoObra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Obra obra;

    @ManyToOne(fetch = FetchType.LAZY)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    private DepositoTrabajador trabajador;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private SujetoDocumental sujeto = SujetoDocumental.CONTRATISTA;

    @Enumerated(EnumType.STRING)
    @Column(length = 60)
    private TipoVinculoDocumental vinculo = TipoVinculoDocumental.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(length = 80)
    private TipoDocumentoObra tipo = TipoDocumentoObra.OTRO;

    @Column(length = 500)
    private String nombrePersonalizado;

    @Column(length = 500)
    private String vehiculoDominio;

    @Column(length = 700)
    private String vehiculoDetalle;

    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private LocalDate fechaPresentacion;
    private boolean mensual;
    private boolean obligatorioIngreso = true;
    private boolean presentado;
    private boolean activo = true;

    @Column(length = 700)
    private String referenciaArchivo;

    @Column(length = 1200)
    private String observacion;

    public EstadoDocumentoObra estado() {
        if (!activo) {
            return EstadoDocumentoObra.NO_APLICA;
        }
        if (!presentado) {
            return EstadoDocumentoObra.PENDIENTE;
        }
        if (fechaVencimiento == null) {
            return EstadoDocumentoObra.APTO;
        }
        LocalDate hoy = LocalDate.now();
        if (fechaVencimiento.isBefore(hoy)) {
            return EstadoDocumentoObra.VENCIDO;
        }
        if (!fechaVencimiento.isAfter(hoy.plusDays(15))) {
            return EstadoDocumentoObra.POR_VENCER;
        }
        return EstadoDocumentoObra.APTO;
    }

    public long diasAlVencimiento() {
        if (fechaVencimiento == null) {
            return 99999;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), fechaVencimiento);
    }

    public String nombreDocumento() {
        return tipo == TipoDocumentoObra.OTRO && nombrePersonalizado != null && !nombrePersonalizado.isBlank()
                ? nombrePersonalizado
                : tipo.getDescripcion();
    }

    public String sujetoNombre() {
        if (trabajador != null) {
            return trabajador.getNombre();
        }
        if (proveedor != null) {
            return proveedor.getNombre();
        }
        if (vehiculoDominio != null && !vehiculoDominio.isBlank()) {
            return vehiculoDominio;
        }
        return obra == null ? "Sin identificar" : obra.getNombre();
    }
}