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
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(indexes = {
        @Index(name = "idx_doc_obra_activo_vencimiento", columnList = "obra_id, activo, fecha_vencimiento, id"),
        @Index(name = "idx_doc_proveedor_activo", columnList = "proveedor_id, activo"),
        @Index(name = "idx_doc_trabajador_activo", columnList = "trabajador_id, activo")
})
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
    private CarpetaDocumentacion carpeta;

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
    private LocalDate fechaVencimientoFisico;
    private LocalDate fechaUltimaVerificacionFisica;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaUltimaActualizacion;
    private boolean mensual;
    private boolean obligatorioIngreso = true;
    private boolean presentado;
    private boolean impresoLegajo;
    private boolean activo = true;

    @Column(length = 700)
    private String referenciaArchivo;

    @Column(length = 500)
    private String ubicacionFisica;

    @Column(length = 1200)
    private String observacion;

    @PrePersist
    public void prePersist() {
        LocalDateTime ahora = LocalDateTime.now();
        if (fechaCreacion == null) {
            fechaCreacion = ahora;
        }
        fechaUltimaActualizacion = ahora;
    }

    @PreUpdate
    public void preUpdate() {
        fechaUltimaActualizacion = LocalDateTime.now();
    }

    public EstadoDocumentoObra estado() {
        if (!activo) {
            return EstadoDocumentoObra.NO_APLICA;
        }
        if (!presentado) {
            return EstadoDocumentoObra.PENDIENTE;
        }
        if (fechaVencimiento == null) {
            return EstadoDocumentoObra.SIN_VENCIMIENTO;
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
            return 0;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), fechaVencimiento);
    }

    public boolean sinVencimiento() {
        return fechaVencimiento == null;
    }

    public boolean tienePdf() {
        return referenciaArchivo != null && !referenciaArchivo.isBlank();
    }

    public boolean faltaCopiaImpresa() {
        return estadoCarpetaFisica() == EstadoCarpetaFisica.FALTANTE;
    }

    public boolean carpetaFisicaPendiente() {
        return estadoCarpetaFisica() != EstadoCarpetaFisica.ACTUALIZADA;
    }

    public EstadoCarpetaFisica estadoCarpetaFisica() {
        if (!activo || !presentado) {
            return EstadoCarpetaFisica.FALTANTE;
        }
        if (!impresoLegajo) {
            return EstadoCarpetaFisica.FALTANTE;
        }
        if (fechaVencimiento == null) {
            return EstadoCarpetaFisica.ACTUALIZADA;
        }
        if (fechaVencimientoFisico == null) {
            return EstadoCarpetaFisica.DESACTUALIZADA;
        }
        return fechaVencimientoFisico.isBefore(fechaVencimiento)
                ? EstadoCarpetaFisica.DESACTUALIZADA
                : EstadoCarpetaFisica.ACTUALIZADA;
    }

    public String accionCarpetaFisica() {
        EstadoCarpetaFisica estadoFisico = estadoCarpetaFisica();
        if (estadoFisico == EstadoCarpetaFisica.ACTUALIZADA) {
            return "Carpeta fisica al dia";
        }
        if (estadoFisico == EstadoCarpetaFisica.DESACTUALIZADA) {
            return "Pendiente imprimir actualizacion";
        }
        return "Pendiente archivar copia fisica";
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
