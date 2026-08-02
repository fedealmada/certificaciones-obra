package com.obra.certificaciones.documentacion.dto;

import com.obra.certificaciones.documentacion.entity.SujetoDocumental;
import com.obra.certificaciones.documentacion.entity.TipoDocumentoObra;
import com.obra.certificaciones.documentacion.entity.TipoVinculoDocumental;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class DocumentoObraForm {
    private Long id;
    private Long proveedorId;
    private Long trabajadorId;
    private SujetoDocumental sujeto = SujetoDocumental.CONTRATISTA;
    private TipoVinculoDocumental vinculo = TipoVinculoDocumental.GENERAL;
    private TipoDocumentoObra tipo = TipoDocumentoObra.OTRO;
    private String nombrePersonalizado;
    private String vehiculoDominio;
    private String vehiculoDetalle;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaEmision;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaVencimiento;

    private boolean sinVencimiento;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaPresentacion = LocalDate.now();

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaVencimientoFisico;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaUltimaVerificacionFisica;

    private boolean mensual;
    private boolean obligatorioIngreso = true;
    private boolean presentado = true;
    private boolean impresoLegajo;
    private boolean activo = true;
    private String referenciaArchivo;
    private String ubicacionFisica;
    private String observacion;
}
