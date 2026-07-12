package com.obra.certificaciones.asistencia.service;

import com.obra.certificaciones.asistencia.dto.AsistenciaEmpresaResumen;
import com.obra.certificaciones.asistencia.dto.AsistenciaForm;
import com.obra.certificaciones.asistencia.entity.AsistenciaPersonal;
import com.obra.certificaciones.asistencia.repository.AsistenciaPersonalRepository;
import com.obra.certificaciones.deposito.entity.DepositoTrabajador;
import com.obra.certificaciones.deposito.repository.DepositoTrabajadorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AsistenciaService {
    private final AsistenciaPersonalRepository asistenciaRepository;
    private final DepositoTrabajadorRepository trabajadorRepository;

    @Transactional(readOnly = true)
    public List<AsistenciaPersonal> listarPorFecha(LocalDate fecha) {
        return asistenciaRepository.findByFechaOrderByEmpresaAscTrabajadorNombreAsc(fecha == null ? LocalDate.now() : fecha);
    }

    @Transactional(readOnly = true)
    public List<AsistenciaPersonal> recientes() {
        return asistenciaRepository.findTop80ByOrderByFechaDescIdDesc();
    }

    @Transactional(readOnly = true)
    public Map<Long, AsistenciaPersonal> mapaPorTrabajador(LocalDate fecha) {
        Map<Long, AsistenciaPersonal> mapa = new LinkedHashMap<>();
        for (AsistenciaPersonal asistencia : listarPorFecha(fecha)) {
            if (asistencia.getTrabajadorId() != null) {
                mapa.put(asistencia.getTrabajadorId(), asistencia);
            }
        }
        return mapa;
    }

    @Transactional(readOnly = true)
    public AsistenciaPersonal obtener(Long id) {
        return asistenciaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la asistencia " + id));
    }

    @Transactional
    public AsistenciaPersonal guardar(AsistenciaForm form) {
        validar(form);
        AsistenciaPersonal asistencia = form.getId() == null ? new AsistenciaPersonal() : obtener(form.getId());
        aplicar(asistencia, form);
        return asistenciaRepository.save(asistencia);
    }

    @Transactional
    public AsistenciaPersonal marcarEntrada(Long trabajadorId, LocalDate fecha) {
        DepositoTrabajador trabajador = trabajadorRepository.findById(trabajadorId)
                .orElseThrow(() -> new EntityNotFoundException("No existe la persona " + trabajadorId));
        LocalDate fechaSegura = fecha == null ? LocalDate.now() : fecha;
        Optional<AsistenciaPersonal> existente = asistenciaRepository.findByFechaAndTrabajadorId(fechaSegura, trabajadorId);
        if (existente.isPresent()) {
            return existente.get();
        }
        AsistenciaPersonal asistencia = new AsistenciaPersonal();
        asistencia.setFecha(fechaSegura);
        asistencia.setTrabajadorId(trabajador.getId());
        asistencia.setTrabajadorNombre(trabajador.getNombre());
        asistencia.setEmpresa(trabajador.getEmpresa());
        asistencia.setSector(trabajador.getSector());
        asistencia.setHoraIngreso(LocalTime.now().withSecond(0).withNano(0));
        asistencia.setHorasTrabajadas(BigDecimal.ZERO);
        return asistenciaRepository.save(asistencia);
    }

    @Transactional
    public AsistenciaPersonal marcarSalida(Long asistenciaId) {
        AsistenciaPersonal asistencia = obtener(asistenciaId);
        if (asistencia.getHoraIngreso() == null) {
            asistencia.setHoraIngreso(LocalTime.now().withSecond(0).withNano(0));
        }
        asistencia.setHoraSalida(LocalTime.now().withSecond(0).withNano(0));
        if (asistencia.getHoraSalida().isAfter(asistencia.getHoraIngreso())) {
            long minutos = Duration.between(asistencia.getHoraIngreso(), asistencia.getHoraSalida()).toMinutes();
            asistencia.setHorasTrabajadas(BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
        }
        return asistenciaRepository.save(asistencia);
    }

    @Transactional
    public void eliminar(Long id) {
        asistenciaRepository.delete(obtener(id));
    }

    @Transactional(readOnly = true)
    public AsistenciaForm formDesdeAsistencia(AsistenciaPersonal asistencia) {
        AsistenciaForm form = new AsistenciaForm();
        form.setId(asistencia.getId());
        form.setFecha(asistencia.getFecha());
        form.setTrabajadorId(asistencia.getTrabajadorId());
        form.setTrabajadorNombre(asistencia.getTrabajadorNombre());
        form.setEmpresa(asistencia.getEmpresa());
        form.setSector(asistencia.getSector());
        form.setHoraIngreso(asistencia.getHoraIngreso());
        form.setHoraSalida(asistencia.getHoraSalida());
        form.setHorasTrabajadas(asistencia.getHorasTrabajadas());
        form.setObservacion(asistencia.getObservacion());
        return form;
    }

    public List<AsistenciaEmpresaResumen> resumenPorEmpresa(List<AsistenciaPersonal> asistencias) {
        Map<String, ResumenMutable> resumen = new LinkedHashMap<>();
        for (AsistenciaPersonal asistencia : asistencias) {
            String empresa = StringUtils.hasText(asistencia.getEmpresa()) ? asistencia.getEmpresa() : "Sin empresa";
            ResumenMutable actual = resumen.computeIfAbsent(empresa, key -> new ResumenMutable());
            actual.personas++;
            actual.horas = actual.horas.add(valorSeguro(asistencia.getHorasTrabajadas()));
        }
        return resumen.entrySet().stream()
                .map(entry -> new AsistenciaEmpresaResumen(entry.getKey(), entry.getValue().personas, entry.getValue().horas))
                .toList();
    }

    public BigDecimal totalHoras(List<AsistenciaPersonal> asistencias) {
        return asistencias.stream()
                .map(asistencia -> valorSeguro(asistencia.getHorasTrabajadas()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void aplicar(AsistenciaPersonal asistencia, AsistenciaForm form) {
        DepositoTrabajador trabajador = obtenerOCrearTrabajador(form);
        asistencia.setFecha(form.getFecha() == null ? LocalDate.now() : form.getFecha());
        asistencia.setTrabajadorId(trabajador == null ? null : trabajador.getId());
        asistencia.setTrabajadorNombre(trabajador == null ? form.getTrabajadorNombre().trim() : trabajador.getNombre());
        asistencia.setEmpresa(elegirTexto(form.getEmpresa(), trabajador == null ? null : trabajador.getEmpresa()));
        asistencia.setSector(elegirTexto(form.getSector(), trabajador == null ? null : trabajador.getSector()));
        asistencia.setHoraIngreso(form.getHoraIngreso());
        asistencia.setHoraSalida(form.getHoraSalida());
        asistencia.setHorasTrabajadas(calcularHoras(form));
        asistencia.setObservacion(form.getObservacion());
    }

    private DepositoTrabajador obtenerOCrearTrabajador(AsistenciaForm form) {
        if (form.getTrabajadorId() != null) {
            return trabajadorRepository.findById(form.getTrabajadorId()).orElse(null);
        }
        String nombre = form.getTrabajadorNombre().trim();
        return trabajadorRepository.findByNombreIgnoreCase(nombre)
                .orElseGet(() -> {
                    DepositoTrabajador trabajador = new DepositoTrabajador();
                    trabajador.setNombre(nombre);
                    trabajador.setEmpresa(form.getEmpresa());
                    trabajador.setSector(form.getSector());
                    return trabajadorRepository.save(trabajador);
                });
    }

    private BigDecimal calcularHoras(AsistenciaForm form) {
        if (form.getHorasTrabajadas() != null && form.getHorasTrabajadas().compareTo(BigDecimal.ZERO) > 0) {
            return form.getHorasTrabajadas();
        }
        if (form.getHoraIngreso() != null && form.getHoraSalida() != null && form.getHoraSalida().isAfter(form.getHoraIngreso())) {
            long minutos = Duration.between(form.getHoraIngreso(), form.getHoraSalida()).toMinutes();
            return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private void validar(AsistenciaForm form) {
        if (form.getFecha() == null) {
            throw new IllegalArgumentException("La fecha es obligatoria.");
        }
        if (!StringUtils.hasText(form.getTrabajadorNombre())) {
            throw new IllegalArgumentException("La persona es obligatoria.");
        }
        if (form.getHorasTrabajadas() != null && form.getHorasTrabajadas().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Las horas no pueden ser negativas.");
        }
        if (form.getHoraIngreso() != null && form.getHoraSalida() != null && !form.getHoraSalida().isAfter(form.getHoraIngreso())) {
            throw new IllegalArgumentException("La hora de salida debe ser posterior a la hora de ingreso.");
        }
    }

    private String elegirTexto(String principal, String alternativa) {
        return StringUtils.hasText(principal) ? principal.trim() : alternativa;
    }

    private BigDecimal valorSeguro(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static class ResumenMutable {
        long personas;
        BigDecimal horas = BigDecimal.ZERO;
    }
}
