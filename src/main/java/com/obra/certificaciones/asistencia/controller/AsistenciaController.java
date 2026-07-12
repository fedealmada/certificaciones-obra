package com.obra.certificaciones.asistencia.controller;

import com.obra.certificaciones.asistencia.dto.AsistenciaForm;
import com.obra.certificaciones.asistencia.service.AsistenciaService;
import com.obra.certificaciones.deposito.service.DepositoService;
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

@Controller
@RequestMapping("/asistencia")
@RequiredArgsConstructor
public class AsistenciaController {
    private final AsistenciaService asistenciaService;
    private final DepositoService depositoService;

    @GetMapping
    public String listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                         Model model) {
        LocalDate fechaSeleccionada = fecha == null ? LocalDate.now() : fecha;
        var asistencias = asistenciaService.listarPorFecha(fechaSeleccionada);
        model.addAttribute("fecha", fechaSeleccionada);
        model.addAttribute("asistencias", asistencias);
        model.addAttribute("resumenEmpresas", asistenciaService.resumenPorEmpresa(asistencias));
        model.addAttribute("totalHoras", asistenciaService.totalHoras(asistencias));
        model.addAttribute("recientes", asistenciaService.recientes());
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
                          RedirectAttributes redirectAttributes) {
        try {
            var asistencia = asistenciaService.guardar(form);
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
