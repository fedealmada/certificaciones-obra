package com.obra.certificaciones.documentacion.service;

import com.obra.certificaciones.deposito.service.DepositoService;
import com.obra.certificaciones.documentacion.dto.ContratistaDocumentacionResumen;
import com.obra.certificaciones.documentacion.dto.DocumentacionResumen;
import com.obra.certificaciones.documentacion.dto.DocumentoObraForm;
import com.obra.certificaciones.documentacion.entity.DocumentoObra;
import com.obra.certificaciones.documentacion.entity.EstadoDocumentoObra;
import com.obra.certificaciones.documentacion.entity.SujetoDocumental;
import com.obra.certificaciones.documentacion.repository.DocumentoObraRepository;
import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.proveedor.service.ProveedorService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentacionService {
    private final DocumentoObraRepository repository;
    private final ProveedorService proveedorService;
    private final DepositoService depositoService;

    @Transactional(readOnly = true)
    public List<DocumentoObra> listar(Obra obra) {
        return repository.findByObraIdAndActivoTrueOrderByFechaVencimientoAscIdDesc(obra.getId());
    }

    @Transactional(readOnly = true)
    public Page<DocumentoObra> listar(Obra obra, Pageable pageable) {
        return repository.findByObraIdAndActivoTrueOrderByFechaVencimientoAscIdDesc(obra.getId(), pageable);
    }

    @Transactional(readOnly = true)
    public DocumentacionResumen resumen(Obra obra) {
        return resumen(listar(obra));
    }

    @Transactional(readOnly = true)
    public DocumentoObra obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el documento " + id));
    }

    @Transactional
    public DocumentoObra guardar(DocumentoObraForm form, Obra obra) {
        validar(form);
        DocumentoObra documento = form.getId() == null ? new DocumentoObra() : obtener(form.getId());
        documento.setObra(obra);
        documento.setSujeto(form.getSujeto());
        documento.setVinculo(form.getVinculo());
        documento.setTipo(form.getTipo());
        documento.setNombrePersonalizado(texto(form.getNombrePersonalizado()));
        documento.setVehiculoDominio(texto(form.getVehiculoDominio()));
        documento.setVehiculoDetalle(texto(form.getVehiculoDetalle()));
        documento.setFechaEmision(form.getFechaEmision());
        documento.setFechaVencimiento(form.getFechaVencimiento());
        documento.setFechaPresentacion(form.getFechaPresentacion());
        documento.setMensual(form.isMensual());
        documento.setObligatorioIngreso(form.isObligatorioIngreso());
        documento.setPresentado(form.isPresentado());
        documento.setActivo(form.isActivo());
        documento.setReferenciaArchivo(texto(form.getReferenciaArchivo()));
        documento.setObservacion(texto(form.getObservacion()));
        documento.setProveedor(form.getProveedorId() == null ? null : proveedorService.obtener(form.getProveedorId()));
        documento.setTrabajador(form.getTrabajadorId() == null ? null : depositoService.obtenerTrabajador(form.getTrabajadorId()));
        return repository.save(documento);
    }

    @Transactional
    public void eliminar(Long id) {
        DocumentoObra documento = obtener(id);
        documento.setActivo(false);
        repository.save(documento);
    }

    @Transactional(readOnly = true)
    public DocumentoObraForm formDesde(DocumentoObra documento) {
        DocumentoObraForm form = new DocumentoObraForm();
        form.setId(documento.getId());
        form.setProveedorId(documento.getProveedor() == null ? null : documento.getProveedor().getId());
        form.setTrabajadorId(documento.getTrabajador() == null ? null : documento.getTrabajador().getId());
        form.setSujeto(documento.getSujeto());
        form.setVinculo(documento.getVinculo());
        form.setTipo(documento.getTipo());
        form.setNombrePersonalizado(documento.getNombrePersonalizado());
        form.setVehiculoDominio(documento.getVehiculoDominio());
        form.setVehiculoDetalle(documento.getVehiculoDetalle());
        form.setFechaEmision(documento.getFechaEmision());
        form.setFechaVencimiento(documento.getFechaVencimiento());
        form.setFechaPresentacion(documento.getFechaPresentacion());
        form.setMensual(documento.isMensual());
        form.setObligatorioIngreso(documento.isObligatorioIngreso());
        form.setPresentado(documento.isPresentado());
        form.setActivo(documento.isActivo());
        form.setReferenciaArchivo(documento.getReferenciaArchivo());
        form.setObservacion(documento.getObservacion());
        return form;
    }

    public DocumentacionResumen resumen(List<DocumentoObra> documentos) {
        long aptos = 0, porVencer = 0, vencidos = 0, pendientes = 0, mensualesPendientes = 0;
        Map<String, MutableContratista> porContratista = new LinkedHashMap<>();
        LocalDate hoy = LocalDate.now();
        for (DocumentoObra documento : documentos) {
            EstadoDocumentoObra estado = documento.estado();
            if (estado == EstadoDocumentoObra.APTO) aptos++;
            if (estado == EstadoDocumentoObra.POR_VENCER) porVencer++;
            if (estado == EstadoDocumentoObra.VENCIDO) vencidos++;
            if (estado == EstadoDocumentoObra.PENDIENTE) pendientes++;
            if (documento.isMensual() && (documento.getFechaPresentacion() == null
                    || documento.getFechaPresentacion().getMonth() != hoy.getMonth()
                    || documento.getFechaPresentacion().getYear() != hoy.getYear())) {
                mensualesPendientes++;
            }
            String contratista = documento.getProveedor() == null ? "Sin contratista" : documento.getProveedor().getNombre();
            MutableContratista resumen = porContratista.computeIfAbsent(contratista, key -> new MutableContratista());
            resumen.total++;
            if (estado == EstadoDocumentoObra.VENCIDO) resumen.vencidos++;
            if (estado == EstadoDocumentoObra.POR_VENCER) resumen.porVencer++;
            if (estado == EstadoDocumentoObra.PENDIENTE) resumen.pendientes++;
        }
        List<ContratistaDocumentacionResumen> contratistas = porContratista.entrySet().stream()
                .map(entry -> entry.getValue().toResumen(entry.getKey()))
                .sorted(Comparator.comparing(ContratistaDocumentacionResumen::vencidos).reversed()
                        .thenComparing(ContratistaDocumentacionResumen::porVencer).reversed())
                .toList();
        return new DocumentacionResumen(documentos.size(), aptos, porVencer, vencidos, pendientes, mensualesPendientes, contratistas);
    }

    private void validar(DocumentoObraForm form) {
        if (form.getSujeto() == null) {
            throw new IllegalArgumentException("Debe indicar si corresponde a contratista, persona, vehiculo u obra.");
        }
        if (form.getTipo() == null) {
            throw new IllegalArgumentException("El tipo de documento es obligatorio.");
        }
        if (form.getSujeto() == SujetoDocumental.CONTRATISTA && form.getProveedorId() == null) {
            throw new IllegalArgumentException("Debe seleccionar un contratista/proveedor.");
        }
        if (form.getSujeto() == SujetoDocumental.PERSONA && form.getTrabajadorId() == null) {
            throw new IllegalArgumentException("Debe seleccionar una persona.");
        }
        if (form.getSujeto() == SujetoDocumental.VEHICULO && !StringUtils.hasText(form.getVehiculoDominio())) {
            throw new IllegalArgumentException("Debe indicar dominio o identificacion del vehiculo/maquinaria.");
        }
    }

    private String texto(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    private static class MutableContratista {
        long total;
        long vencidos;
        long porVencer;
        long pendientes;

        ContratistaDocumentacionResumen toResumen(String nombre) {
            EstadoDocumentoObra estado = EstadoDocumentoObra.APTO;
            if (vencidos > 0) estado = EstadoDocumentoObra.VENCIDO;
            else if (pendientes > 0) estado = EstadoDocumentoObra.PENDIENTE;
            else if (porVencer > 0) estado = EstadoDocumentoObra.POR_VENCER;
            return new ContratistaDocumentacionResumen(nombre, total, vencidos, porVencer, pendientes, estado);
        }
    }
}