package com.obra.certificaciones.asistencia.controller;

import com.obra.certificaciones.asistencia.dto.AsistenciaForm;
import com.obra.certificaciones.asistencia.service.AsistenciaService;
import com.obra.certificaciones.deposito.service.DepositoService;
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
import java.time.YearMonth;
import java.util.List;
import java.util.stream.IntStream;

@Controller
@RequestMapping("/asistencia")
@RequiredArgsConstructor
public class AsistenciaController {
    private final AsistenciaService asistenciaService;
    private final DepositoService depositoService;
    private final ObraService obraService;

    @GetMapping
    public String listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                         Model model,
                         HttpSession session) {
        LocalDate fechaSeleccionada = fecha == null ? LocalDate.now() : fecha;
        var obra = obraService.obraActiva(session);
        var asistencias = asistenciaService.listarPorFecha(obra, fechaSeleccionada);
        var personas = depositoService.listarTrabajadoresActivos();
        var asistenciaPorTrabajador = asistenciaService.mapaPorTrabajador(asistencias);
        var resumenDia = asistenciaService.resumenDia(asistencias);
        model.addAttribute("fecha", fechaSeleccionada);
        model.addAttribute("fechaAnterior", fechaSeleccionada.minusDays(1));
        model.addAttribute("fechaSiguiente", fechaSeleccionada.plusDays(1));
        model.addAttribute("diasCalendario", IntStream.rangeClosed(-3, 3)
                .mapToObj(dia -> fechaSeleccionada.plusDays(dia))
                .toList());
        model.addAttribute("diasMes", diasMes(fechaSeleccionada));
        model.addAttribute("espaciosInicioMes", IntStream.range(0, espaciosInicioMes(fechaSeleccionada)).boxed().toList());
        model.addAttribute("mesAnterior", fechaSeleccionada.minusMonths(1).withDayOfMonth(1));
        model.addAttribute("mesSiguiente", fechaSeleccionada.plusMonths(1).withDayOfMonth(1));
        model.addAttribute("tituloMes", fechaSeleccionada);
        model.addAttribute("asistencias", asistencias);
        model.addAttribute("personas", personas);
        model.addAttribute("asistenciaPorTrabajador", asistenciaPorTrabajador);
        model.addAttribute("resumenEmpresas", resumenDia.resumenEmpresas());
        model.addAttribute("totalHoras", resumenDia.totalHoras());
        model.addAttribute("presentes", resumenDia.presentes());
        model.addAttribute("enObra", resumenDia.enObra());
        model.addAttribute("salieron", resumenDia.salieron());
        model.addAttribute("incompletos", resumenDia.incompletos());
        model.addAttribute("ausentes", Math.max(0, personas.size() - resumenDia.presentes() - resumenDia.incompletos()));
        model.addAttribute("recientes", asistenciaService.recientes(obra));
        return "asistencia/lista";
    }

    @GetMapping("/nueva")
    public String nueva(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                        Model model) {
        AsistenciaForm form = new AsistenciaForm();
        form.setFecha(fecha == null ? LocalDate.now() : fecha);
        cargarFormulario(model, form, false);
        return "asistencia/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        cargarFormulario(model, asistenciaService.formDesdeAsistencia(asistenciaService.obtener(id)), true);
        return "asistencia/form";
    }

    @PostMapping
    public String guardar(@ModelAttribute("form") AsistenciaForm form,
                          Model model,
                          RedirectAttributes redirectAttributes,
                          HttpSession session) {
        try {
            var asistencia = asistenciaService.guardar(form, obraService.obraActiva(session));
            redirectAttributes.addFlashAttribute("success", "Asistencia guardada correctamente.");
            return "redirect:/asistencia?fecha=" + asistencia.getFecha();
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            cargarFormulario(model, form, form.getId() != null);
            return "asistencia/form";
        }
    }

    @PostMapping("/personas/{trabajadorId}/ingreso-ahora")
    public String ingresoAhora(@PathVariable Long trabajadorId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                               RedirectAttributes redirectAttributes,
                               HttpSession session) {
        LocalDate fechaTrabajo = fecha == null ? LocalDate.now() : fecha;
        try {
            asistenciaService.marcarIngresoAhora(obraService.obraActiva(session), fechaTrabajo, trabajadorId);
            redirectAttributes.addFlashAttribute("success", "Ingreso marcado correctamente. La persona quedo en obra.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/asistencia?fecha=" + fechaTrabajo;
    }

    @PostMapping("/personas/{trabajadorId}/salida-ahora")
    public String salidaAhora(@PathVariable Long trabajadorId,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                              RedirectAttributes redirectAttributes,
                              HttpSession session) {
        LocalDate fechaTrabajo = fecha == null ? LocalDate.now() : fecha;
        try {
            asistenciaService.marcarSalidaAhora(obraService.obraActiva(session), fechaTrabajo, trabajadorId);
            redirectAttributes.addFlashAttribute("success", "Salida marcada correctamente. La persona ya no figura en obra.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/asistencia?fecha=" + fechaTrabajo;
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        var asistencia = asistenciaService.obtener(id);
        LocalDate fecha = asistencia.getFecha();
        asistenciaService.eliminar(id);
        redirectAttributes.addFlashAttribute("success", "Asistencia eliminada correctamente.");
        return "redirect:/asistencia?fecha=" + fecha;
    }
    private void cargarFormulario(Model model, AsistenciaForm form, boolean modoEdicion) {
        model.addAttribute("form", form);
        model.addAttribute("personas", depositoService.listarTrabajadoresActivos());
        model.addAttribute("modoEdicion", modoEdicion);
    }

    private List<DiaAsistencia> diasMes(LocalDate fecha) {
        YearMonth mes = YearMonth.from(fecha);
        return IntStream.rangeClosed(1, mes.lengthOfMonth())
                .mapToObj(dia -> {
                    LocalDate fechaDia = mes.atDay(dia);
                    boolean finDeSemana = fechaDia.getDayOfWeek() == DayOfWeek.SATURDAY || fechaDia.getDayOfWeek() == DayOfWeek.SUNDAY;
                    return new DiaAsistencia(fechaDia, finDeSemana, fechaDia.equals(fecha));
                })
                .toList();
    }

    private int espaciosInicioMes(LocalDate fecha) {
        int valor = YearMonth.from(fecha).atDay(1).getDayOfWeek().getValue();
        return valor - 1;
    }

    public record DiaAsistencia(LocalDate fecha, boolean finDeSemana, boolean seleccionado) {
    }
}


