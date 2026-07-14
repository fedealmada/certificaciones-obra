package com.obra.certificaciones.asistencia.service;

import com.obra.certificaciones.asistencia.dto.AsistenciaEmpresaResumen;
import com.obra.certificaciones.asistencia.dto.AsistenciaForm;
import com.obra.certificaciones.asistencia.entity.AsistenciaPersonal;
import com.obra.certificaciones.asistencia.repository.AsistenciaPersonalRepository;
import com.obra.certificaciones.deposito.entity.DepositoTrabajador;
import com.obra.certificaciones.deposito.repository.DepositoTrabajadorRepository;
import com.obra.certificaciones.obra.entity.Obra;
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

@Service
@RequiredArgsConstructor
public class AsistenciaService {
    private final AsistenciaPersonalRepository asistenciaRepository;
    private final DepositoTrabajadorRepository trabajadorRepository;

    @Transactional(readOnly = true)
    public List<AsistenciaPersonal> listarPorFecha(Obra obra, LocalDate fecha) {
        return asistenciaRepository.findByObraIdAndFechaOrderByEmpresaAscTrabajadorNombreAsc(obra.getId(), fecha == null ? LocalDate.now() : fecha);
    }

    @Transactional(readOnly = true)
    public List<AsistenciaPersonal> recientes(Obra obra) {
        return asistenciaRepository.findTop80ByObraIdOrderByFechaDescIdDesc(obra.getId());
    }

    @Transactional(readOnly = true)
    public Map<Long, AsistenciaPersonal> mapaPorTrabajador(Obra obra, LocalDate fecha) {
        return mapaPorTrabajador(listarPorFecha(obra, fecha));
    }

    public Map<Long, AsistenciaPersonal> mapaPorTrabajador(List<AsistenciaPersonal> asistencias) {
        Map<Long, AsistenciaPersonal> mapa = new LinkedHashMap<>();
        for (AsistenciaPersonal asistencia : asistencias) {
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
    public AsistenciaPersonal guardar(AsistenciaForm form, Obra obra) {
        validar(form);
        AsistenciaPersonal asistencia = form.getId() == null ? new AsistenciaPersonal() : obtener(form.getId());
        asistencia.setObra(obra);
        aplicar(asistencia, form);
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

    public ResumenDia resumenDia(List<AsistenciaPersonal> asistencias) {
        Map<String, ResumenMutable> resumenEmpresas = new LinkedHashMap<>();
        long presentes = 0;
        long enObra = 0;
        long salieron = 0;
        long incompletos = 0;
        BigDecimal totalHoras = BigDecimal.ZERO;

        for (AsistenciaPersonal asistencia : asistencias) {
            if (asistencia.getHoraIngreso() != null) {
                presentes++;
                String empresa = StringUtils.hasText(asistencia.getEmpresa()) ? asistencia.getEmpresa() : "Sin empresa";
                ResumenMutable actual = resumenEmpresas.computeIfAbsent(empresa, key -> new ResumenMutable());
                actual.personas++;
                actual.horas = actual.horas.add(valorSeguro(asistencia.getHorasTrabajadas()));
            }
            if (asistencia.estaEnObra()) {
                enObra++;
            } else if (asistencia.salioDeObra()) {
                salieron++;
                totalHoras = totalHoras.add(valorSeguro(asistencia.getHorasTrabajadas()));
            } else if (asistencia.registroIncompleto()) {
                incompletos++;
            }
        }

        List<AsistenciaEmpresaResumen> empresas = resumenEmpresas.entrySet().stream()
                .map(entry -> new AsistenciaEmpresaResumen(entry.getKey(), entry.getValue().personas, entry.getValue().horas))
                .toList();
        return new ResumenDia(presentes, enObra, salieron, incompletos, totalHoras, empresas);
    }

    public List<AsistenciaEmpresaResumen> resumenPorEmpresa(List<AsistenciaPersonal> asistencias) {
        return resumenDia(asistencias).resumenEmpresas();
    }

    public BigDecimal totalHoras(List<AsistenciaPersonal> asistencias) {
        return resumenDia(asistencias).totalHoras();
    }

    public long contarPresentes(List<AsistenciaPersonal> asistencias) {
        return resumenDia(asistencias).presentes();
    }

    public long contarEnObra(List<AsistenciaPersonal> asistencias) {
        return resumenDia(asistencias).enObra();
    }

    public long contarSalieron(List<AsistenciaPersonal> asistencias) {
        return resumenDia(asistencias).salieron();
    }

    public long contarIncompletos(List<AsistenciaPersonal> asistencias) {
        return resumenDia(asistencias).incompletos();
    }

    @Transactional
    public AsistenciaPersonal marcarIngresoAhora(Obra obra, LocalDate fecha, Long trabajadorId) {
        AsistenciaPersonal asistencia = obtenerOCrearRegistroDia(obra, fecha, trabajadorId);
        if (asistencia.getHoraIngreso() == null) {
            asistencia.setHoraIngreso(horaActual());
        }
        asistencia.setHorasTrabajadas(BigDecimal.ZERO);
        return asistenciaRepository.save(asistencia);
    }

    @Transactional
    public AsistenciaPersonal marcarSalidaAhora(Obra obra, LocalDate fecha, Long trabajadorId) {
        AsistenciaPersonal asistencia = obtenerOCrearRegistroDia(obra, fecha, trabajadorId);
        if (asistencia.getHoraIngreso() == null) {
            throw new IllegalArgumentException("Primero debe cargarse el ingreso de la persona.");
        }
        LocalTime salida = horaActual();
        if (!salida.isAfter(asistencia.getHoraIngreso())) {
            throw new IllegalArgumentException("La salida debe ser posterior al ingreso.");
        }
        asistencia.setHoraSalida(salida);
        asistencia.setHorasTrabajadas(calcularHoras(asistencia.getHoraIngreso(), salida));
        return asistenciaRepository.save(asistencia);
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
            return trabajadorRepository.findById(form.getTrabajadorId())
                    .orElseThrow(() -> new EntityNotFoundException("No existe la persona " + form.getTrabajadorId()));
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
        if (form.getHoraIngreso() == null || form.getHoraSalida() == null) {
            return BigDecimal.ZERO;
        }
        if (form.getHorasTrabajadas() != null && form.getHorasTrabajadas().compareTo(BigDecimal.ZERO) > 0) {
            return form.getHorasTrabajadas();
        }
        return calcularHoras(form.getHoraIngreso(), form.getHoraSalida());
    }

    private BigDecimal calcularHoras(LocalTime ingreso, LocalTime salida) {
        long minutos = Duration.between(ingreso, salida).toMinutes();
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private AsistenciaPersonal obtenerOCrearRegistroDia(Obra obra, LocalDate fecha, Long trabajadorId) {
        LocalDate fechaTrabajo = fecha == null ? LocalDate.now() : fecha;
        return asistenciaRepository.findByObraIdAndFechaAndTrabajadorId(obra.getId(), fechaTrabajo, trabajadorId)
                .orElseGet(() -> crearRegistroDia(obra, fechaTrabajo, trabajadorId));
    }

    private AsistenciaPersonal crearRegistroDia(Obra obra, LocalDate fecha, Long trabajadorId) {
        DepositoTrabajador trabajador = trabajadorRepository.findById(trabajadorId)
                .orElseThrow(() -> new EntityNotFoundException("No existe la persona " + trabajadorId));
        AsistenciaPersonal asistencia = new AsistenciaPersonal();
        asistencia.setObra(obra);
        asistencia.setFecha(fecha);
        asistencia.setTrabajadorId(trabajador.getId());
        asistencia.setTrabajadorNombre(trabajador.getNombre());
        asistencia.setEmpresa(trabajador.getEmpresa());
        asistencia.setSector(trabajador.getSector());
        asistencia.setHorasTrabajadas(BigDecimal.ZERO);
        return asistencia;
    }

    private LocalTime horaActual() {
        return LocalTime.now().withSecond(0).withNano(0);
    }

    private void validar(AsistenciaForm form) {
        if (form.getFecha() == null) {
            throw new IllegalArgumentException("La fecha es obligatoria.");
        }
        if (form.getTrabajadorId() == null && !StringUtils.hasText(form.getTrabajadorNombre())) {
            throw new IllegalArgumentException("La persona es obligatoria.");
        }
        if (form.getHorasTrabajadas() != null && form.getHorasTrabajadas().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Las horas no pueden ser negativas.");
        }
        if (form.getHorasTrabajadas() != null && form.getHorasTrabajadas().compareTo(BigDecimal.ZERO) > 0
                && (form.getHoraIngreso() == null || form.getHoraSalida() == null)) {
            throw new IllegalArgumentException("Para cargar horas trabajadas debe indicar ingreso y salida.");
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

    private boolean presente(AsistenciaPersonal asistencia) {
        return asistencia.getHoraIngreso() != null && asistencia.getHoraSalida() != null;
    }

    public record ResumenDia(long presentes, long enObra, long salieron, long incompletos, BigDecimal totalHoras, List<AsistenciaEmpresaResumen> resumenEmpresas) {
    }

    private static class ResumenMutable {
        long personas;
        BigDecimal horas = BigDecimal.ZERO;
    }
}



