package com.obra.certificaciones.calendario.controller;

import com.obra.certificaciones.calendario.dto.EventoObraForm;
import com.obra.certificaciones.calendario.entity.EstadoEventoObra;
import com.obra.certificaciones.calendario.entity.TipoEventoObra;
import com.obra.certificaciones.calendario.service.CalendarioObraService;
import com.obra.certificaciones.calendario.service.FeriadoArgentinaService;
import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.obra.service.ObraService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Controller
@RequestMapping("/calendario")
@RequiredArgsConstructor
public class CalendarioObraController {
    private final CalendarioObraService calendarioService;
    private final ObraService obraService;
    private final FeriadoArgentinaService feriadoArgentinaService;

    @GetMapping
    public String index(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                        @RequestParam(defaultValue = "mes") String vista,
                        Model model,
                        HttpSession session) {
        LocalDate seleccionada = fecha == null ? LocalDate.now() : fecha;
        EventoObraForm form = new EventoObraForm();
        form.setFecha(seleccionada);
        cargarModelo(model, session, seleccionada, vista, form, false);
        return "calendario/index";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id,
                         @RequestParam(defaultValue = "mes") String vista,
                         Model model,
                         HttpSession session) {
        EventoObraForm form = calendarioService.formDesde(calendarioService.obtener(id));
        cargarModelo(model, session, form.getFecha() == null ? LocalDate.now() : form.getFecha(), vista, form, true);
        return "calendario/index";
    }

    @PostMapping
    public String guardar(@ModelAttribute("form") EventoObraForm form,
                          RedirectAttributes redirectAttributes,
                          HttpSession session) {
        try {
            var evento = calendarioService.guardar(form, obraService.obraActiva(session));
            redirectAttributes.addFlashAttribute("success", form.getId() == null ? "Evento agendado correctamente." : "Evento actualizado correctamente.");
            return "redirect:/calendario?fecha=" + evento.getFecha();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/calendario?fecha=" + (form.getFecha() == null ? LocalDate.now() : form.getFecha());
        }
    }

    @PostMapping("/{id}/estado")
    public String estado(@PathVariable Long id,
                         @RequestParam EstadoEventoObra estado,
                         RedirectAttributes redirectAttributes) {
        var evento = calendarioService.obtener(id);
        calendarioService.cambiarEstado(id, estado);
        redirectAttributes.addFlashAttribute("success", "Estado del evento actualizado.");
        return "redirect:/calendario?fecha=" + evento.getFecha();
    }

    private void cargarModelo(Model model, HttpSession session, LocalDate seleccionada, String vista, EventoObraForm form, boolean modoEdicion) {
        YearMonth mes = YearMonth.from(seleccionada);
        var obra = obraService.obraActiva(session);
        String vistaNormalizada = normalizarVista(vista);
        LocalDate inicioSemana = seleccionada.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<LocalDate> diasSemana = IntStream.range(0, 7).mapToObj(inicioSemana::plusDays).toList();
        List<LocalTime> bloquesHorario = bloquesHorario();
        Map<LocalDate, String> feriadosMes = feriadoArgentinaService.feriadosEntre(mes.atDay(1), mes.atEndOfMonth());
        Map<LocalDate, String> feriadosSemana = feriadoArgentinaService.feriadosEntre(diasSemana.getFirst(), diasSemana.getLast());
        model.addAttribute("fecha", seleccionada);
        model.addAttribute("vista", vistaNormalizada);
        model.addAttribute("form", form);
        model.addAttribute("modoEdicion", modoEdicion);
        model.addAttribute("tipos", TipoEventoObra.values());
        model.addAttribute("estados", EstadoEventoObra.values());
        model.addAttribute("eventosDia", calendarioService.listarDia(obra, seleccionada));
        model.addAttribute("eventosPorDia", calendarioService.mapaPorDia(obra, mes));
        model.addAttribute("feriadosMes", feriadosMes);
        model.addAttribute("feriadosSemana", feriadosSemana);
        model.addAttribute("feriadoDia", feriadoArgentinaService.feriadosEntre(seleccionada, seleccionada).get(seleccionada));
        model.addAttribute("diasSemana", diasSemana);
        model.addAttribute("bloquesSemana", bloques(obra, diasSemana, bloquesHorario));
        model.addAttribute("bloquesDia", bloques(obra, List.of(seleccionada), bloquesHorario));
        model.addAttribute("proximosEventos", calendarioService.proximos(obra));
        model.addAttribute("historialEventos", calendarioService.historial(obra));
        model.addAttribute("diasMes", diasMes(seleccionada));
        model.addAttribute("espaciosInicioMes", IntStream.range(0, espaciosInicioMes(seleccionada)).boxed().toList());
        model.addAttribute("mesAnterior", seleccionada.minusMonths(1).withDayOfMonth(1));
        model.addAttribute("mesSiguiente", seleccionada.plusMonths(1).withDayOfMonth(1));
        model.addAttribute("periodoAnterior", periodoAnterior(seleccionada, vistaNormalizada));
        model.addAttribute("periodoSiguiente", periodoSiguiente(seleccionada, vistaNormalizada));
        model.addAttribute("tituloMes", seleccionada);
    }

    private List<BloqueCalendario> bloques(Obra obra, List<LocalDate> dias, List<LocalTime> horarios) {
        LocalDate desde = dias.getFirst();
        LocalDate hasta = dias.getLast();
        List<EventoBloque> eventos = calendarioService.listarRango(obra, desde, hasta).stream()
                .map(evento -> new EventoBloque(evento.getFecha(), ajustarBloque(evento.getHoraInicio()), evento))
                .toList();
        return horarios.stream()
                .map(hora -> {
                    Map<LocalDate, List<EventoBloque>> porDia = new LinkedHashMap<>();
                    for (LocalDate dia : dias) {
                        porDia.put(dia, eventos.stream()
                                .filter(evento -> dia.equals(evento.fecha()) && hora.equals(evento.hora()))
                                .toList());
                    }
                    return new BloqueCalendario(hora, porDia);
                })
                .toList();
    }

    private List<LocalTime> bloquesHorario() {
        return IntStream.rangeClosed(14, 39)
                .mapToObj(indice -> LocalTime.of(indice / 2, indice % 2 == 0 ? 0 : 30))
                .toList();
    }

    private LocalTime ajustarBloque(LocalTime hora) {
        if (hora == null) {
            return LocalTime.of(7, 0);
        }
        return LocalTime.of(hora.getHour(), hora.getMinute() < 30 ? 0 : 30);
    }

    private String normalizarVista(String vista) {
        if ("semana".equalsIgnoreCase(vista) || "dia".equalsIgnoreCase(vista)) {
            return vista.toLowerCase();
        }
        return "mes";
    }

    private LocalDate periodoAnterior(LocalDate fecha, String vista) {
        return switch (vista) {
            case "dia" -> fecha.minusDays(1);
            case "semana" -> fecha.minusWeeks(1);
            default -> fecha.minusMonths(1).withDayOfMonth(1);
        };
    }

    private LocalDate periodoSiguiente(LocalDate fecha, String vista) {
        return switch (vista) {
            case "dia" -> fecha.plusDays(1);
            case "semana" -> fecha.plusWeeks(1);
            default -> fecha.plusMonths(1).withDayOfMonth(1);
        };
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        var evento = calendarioService.obtener(id);
        calendarioService.eliminar(id);
        redirectAttributes.addFlashAttribute("success", "Evento eliminado correctamente.");
        return "redirect:/calendario?fecha=" + evento.getFecha();
    }

    private List<DiaCalendario> diasMes(LocalDate fecha) {
        YearMonth mes = YearMonth.from(fecha);
        return IntStream.rangeClosed(1, mes.lengthOfMonth())
                .mapToObj(dia -> {
                    LocalDate fechaDia = mes.atDay(dia);
                    boolean sabado = fechaDia.getDayOfWeek() == DayOfWeek.SATURDAY;
                    boolean domingo = fechaDia.getDayOfWeek() == DayOfWeek.SUNDAY;
                    return new DiaCalendario(fechaDia, sabado || domingo, sabado, domingo, fechaDia.equals(fecha), fechaDia.equals(LocalDate.now()));
                })
                .toList();
    }

    private int espaciosInicioMes(LocalDate fecha) {
        return YearMonth.from(fecha).atDay(1).getDayOfWeek().getValue() - 1;
    }

    public record DiaCalendario(LocalDate fecha, boolean finDeSemana, boolean sabado, boolean domingo, boolean seleccionado, boolean hoy) {
    }

    public record EventoBloque(LocalDate fecha, LocalTime hora, com.obra.certificaciones.calendario.entity.EventoObra evento) {
    }

    public record BloqueCalendario(LocalTime hora, Map<LocalDate, List<EventoBloque>> eventosPorDia) {
    }
}
