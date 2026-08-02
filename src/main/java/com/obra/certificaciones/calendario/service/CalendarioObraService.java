package com.obra.certificaciones.calendario.service;

import com.obra.certificaciones.calendario.dto.EventoObraForm;
import com.obra.certificaciones.calendario.entity.EstadoEventoObra;
import com.obra.certificaciones.calendario.entity.EventoObra;
import com.obra.certificaciones.calendario.repository.EventoObraRepository;
import com.obra.certificaciones.obra.entity.Obra;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CalendarioObraService {
    private final EventoObraRepository repository;

    @Transactional(readOnly = true)
    public List<EventoObra> listarMes(Obra obra, YearMonth mes) {
        return listarRango(obra, mes.atDay(1), mes.atEndOfMonth());
    }

    @Transactional(readOnly = true)
    public List<EventoObra> listarRango(Obra obra, LocalDate desde, LocalDate hasta) {
        return repository.findByObraIdAndActivoTrueAndFechaBetweenOrderByFechaAscHoraInicioAscIdAsc(obra.getId(), desde, hasta);
    }

    @Transactional(readOnly = true)
    public List<EventoObra> listarDia(Obra obra, LocalDate fecha) {
        return listarMes(obra, YearMonth.from(fecha)).stream()
                .filter(evento -> fecha.equals(evento.getFecha()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, List<EventoObra>> mapaPorDia(Obra obra, YearMonth mes) {
        Map<LocalDate, List<EventoObra>> mapa = new LinkedHashMap<>();
        listarMes(obra, mes).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        EventoObra::getFecha,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ))
                .forEach(mapa::put);
        return mapa;
    }

    @Transactional(readOnly = true)
    public List<EventoObra> proximos(Obra obra) {
        return repository.findTop8ByObraIdAndActivoTrueAndFechaGreaterThanEqualOrderByFechaAscHoraInicioAscIdAsc(obra.getId(), LocalDate.now())
                .stream()
                .filter(evento -> evento.getEstado() == EstadoEventoObra.PROGRAMADO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventoObra> historial(Obra obra) {
        return repository.findTop20ByObraIdAndActivoTrueOrderByFechaDescHoraInicioDescIdDesc(obra.getId())
                .stream()
                .filter(EventoObra::historico)
                .sorted(Comparator.comparing(EventoObra::getFecha, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(EventoObra::getHoraInicio, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(12)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventoObra obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el evento " + id));
    }

    @Transactional
    public EventoObra guardar(EventoObraForm form, Obra obra) {
        validar(form);
        EventoObra evento = form.getId() == null ? new EventoObra() : obtener(form.getId());
        evento.setObra(obra);
        evento.setTitulo(texto(form.getTitulo()));
        evento.setTipo(form.getTipo());
        evento.setEstado(form.getEstado());
        evento.setFecha(form.getFecha());
        evento.setHoraInicio(form.getHoraInicio());
        evento.setHoraFin(form.getHoraFin());
        evento.setLugar(texto(form.getLugar()));
        evento.setResponsable(texto(form.getResponsable()));
        evento.setDescripcion(texto(form.getDescripcion()));
        evento.setImportante(form.isImportante());
        evento.setActivo(true);
        return repository.save(evento);
    }

    @Transactional
    public void cambiarEstado(Long id, EstadoEventoObra estado) {
        EventoObra evento = obtener(id);
        evento.setEstado(estado);
        repository.save(evento);
    }

    @Transactional
    public void eliminar(Long id) {
        EventoObra evento = obtener(id);
        evento.setActivo(false);
        repository.save(evento);
    }

    @Transactional(readOnly = true)
    public EventoObraForm formDesde(EventoObra evento) {
        EventoObraForm form = new EventoObraForm();
        form.setId(evento.getId());
        form.setTitulo(evento.getTitulo());
        form.setTipo(evento.getTipo());
        form.setEstado(evento.getEstado());
        form.setFecha(evento.getFecha());
        form.setHoraInicio(evento.getHoraInicio());
        form.setHoraFin(evento.getHoraFin());
        form.setLugar(evento.getLugar());
        form.setResponsable(evento.getResponsable());
        form.setDescripcion(evento.getDescripcion());
        form.setImportante(evento.isImportante());
        return form;
    }

    private void validar(EventoObraForm form) {
        if (!StringUtils.hasText(form.getTitulo())) {
            throw new IllegalArgumentException("Indica un titulo para el evento.");
        }
        if (form.getFecha() == null) {
            throw new IllegalArgumentException("Indica la fecha del evento.");
        }
        if (form.getTipo() == null) {
            throw new IllegalArgumentException("Selecciona el tipo de evento.");
        }
        if (form.getEstado() == null) {
            form.setEstado(EstadoEventoObra.PROGRAMADO);
        }
        if (form.getHoraInicio() != null && form.getHoraFin() != null && form.getHoraFin().isBefore(form.getHoraInicio())) {
            throw new IllegalArgumentException("La hora de finalizacion no puede ser anterior al inicio.");
        }
    }

    private String texto(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
