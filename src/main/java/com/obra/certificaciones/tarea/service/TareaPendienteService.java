package com.obra.certificaciones.tarea.service;

import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.tarea.dto.TareaPendienteForm;
import com.obra.certificaciones.tarea.entity.EstadoTarea;
import com.obra.certificaciones.tarea.entity.TareaChecklistItem;
import com.obra.certificaciones.tarea.entity.TareaPendiente;
import com.obra.certificaciones.tarea.repository.TareaChecklistItemRepository;
import com.obra.certificaciones.tarea.repository.TareaPendienteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TareaPendienteService {
    private final TareaPendienteRepository tareaRepository;
    private final TareaChecklistItemRepository checklistRepository;

    @Transactional(readOnly = true)
    public List<TareaPendiente> pendientes(Obra obra) {
        return tareaRepository.findByObraAndActivoTrueAndEstadoInOrderByFechaVencimientoAscPrioridadDescIdDesc(
                obra,
                List.of(EstadoTarea.PENDIENTE, EstadoTarea.EN_CURSO, EstadoTarea.PAUSADA));
    }

    @Transactional(readOnly = true)
    public List<TareaPendiente> historial(Obra obra) {
        return tareaRepository.findTop30ByObraAndActivoTrueAndEstadoOrderByFechaCierreDescIdDesc(obra, EstadoTarea.COMPLETADA);
    }

    @Transactional(readOnly = true)
    public TareaPendiente obtener(Long id) {
        return tareaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la tarea " + id));
    }

    @Transactional
    public TareaPendiente guardar(TareaPendienteForm form, Obra obra) {
        if (!StringUtils.hasText(form.getTitulo())) {
            throw new IllegalArgumentException("El titulo de la tarea es obligatorio.");
        }
        TareaPendiente tarea = form.getId() == null ? new TareaPendiente() : obtener(form.getId());
        if (form.getId() == null) {
            tarea.setObra(obra);
        }
        tarea.setTitulo(form.getTitulo().trim());
        tarea.setDescripcion(texto(form.getDescripcion()));
        tarea.setPrioridad(form.getPrioridad() == null ? tarea.getPrioridad() : form.getPrioridad());
        tarea.setEstado(form.getEstado() == null ? tarea.getEstado() : form.getEstado());
        tarea.setFechaVencimiento(form.getFechaVencimiento());
        tarea.setResponsable(texto(form.getResponsable()));
        tarea.setEtiqueta(texto(form.getEtiqueta()));
        return tareaRepository.save(tarea);
    }

    @Transactional
    public void cambiarEstado(Long id, EstadoTarea estado) {
        TareaPendiente tarea = obtener(id);
        tarea.setEstado(estado == null ? EstadoTarea.PENDIENTE : estado);
        tareaRepository.save(tarea);
    }

    @Transactional
    public void agregarChecklist(Long tareaId, String descripcion) {
        if (!StringUtils.hasText(descripcion)) {
            throw new IllegalArgumentException("La descripcion del checklist es obligatoria.");
        }
        TareaPendiente tarea = obtener(tareaId);
        TareaChecklistItem item = new TareaChecklistItem();
        item.setTarea(tarea);
        item.setDescripcion(descripcion.trim());
        checklistRepository.save(item);
    }

    @Transactional
    public void alternarChecklist(Long itemId) {
        TareaChecklistItem item = checklistRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el punto de checklist " + itemId));
        item.setCompletado(!item.isCompletado());
        checklistRepository.save(item);
    }

    @Transactional
    public void eliminarChecklist(Long itemId) {
        checklistRepository.deleteById(itemId);
    }

    @Transactional
    public void eliminar(Long id) {
        TareaPendiente tarea = obtener(id);
        tarea.setActivo(false);
        tareaRepository.save(tarea);
    }

    public TareaPendienteForm formDesde(TareaPendiente tarea) {
        TareaPendienteForm form = new TareaPendienteForm();
        form.setId(tarea.getId());
        form.setTitulo(tarea.getTitulo());
        form.setDescripcion(tarea.getDescripcion());
        form.setPrioridad(tarea.getPrioridad());
        form.setEstado(tarea.getEstado());
        form.setFechaVencimiento(tarea.getFechaVencimiento());
        form.setResponsable(tarea.getResponsable());
        form.setEtiqueta(tarea.getEtiqueta());
        return form;
    }

    private String texto(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
