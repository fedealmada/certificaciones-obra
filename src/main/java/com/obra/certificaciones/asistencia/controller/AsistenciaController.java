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

import java.time.LocalDate;
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
        long presentes = asistenciaService.contarPresentes(asistencias);
        long incompletos = asistenciaService.contarIncompletos(asistencias);
        model.addAttribute("fecha", fechaSeleccionada);
        model.addAttribute("fechaAnterior", fechaSeleccionada.minusDays(1));
        model.addAttribute("fechaSiguiente", fechaSeleccionada.plusDays(1));
        model.addAttribute("diasCalendario", IntStream.rangeClosed(-3, 3)
                .mapToObj(dia -> fechaSeleccionada.plusDays(dia))
                .toList());
        model.addAttribute("asistencias", asistencias);
        model.addAttribute("personas", personas);
        model.addAttribute("asistenciaPorTrabajador", asistenciaService.mapaPorTrabajador(obra, fechaSeleccionada));
        model.addAttribute("resumenEmpresas", asistenciaService.resumenPorEmpresa(asistencias));
        model.addAttribute("totalHoras", asistenciaService.totalHoras(asistencias));
        model.addAttribute("presentes", presentes);
        model.addAttribute("incompletos", incompletos);
        model.addAttribute("ausentes", Math.max(0, personas.size() - presentes - incompletos));
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
}
