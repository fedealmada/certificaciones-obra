package com.obra.certificaciones.tarea.controller;

import com.obra.certificaciones.obra.service.ObraService;
import com.obra.certificaciones.tarea.dto.TareaPendienteForm;
import com.obra.certificaciones.tarea.entity.EstadoTarea;
import com.obra.certificaciones.tarea.entity.PrioridadTarea;
import com.obra.certificaciones.tarea.service.TareaPendienteService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/tareas")
@RequiredArgsConstructor
public class TareaPendienteController {
    private final TareaPendienteService tareaService;
    private final ObraService obraService;

    @GetMapping
    public String index(Model model, HttpSession session) {
        cargarModelo(model, session, new TareaPendienteForm(), false);
        return "tareas/index";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model, HttpSession session) {
        cargarModelo(model, session, tareaService.formDesde(tareaService.obtener(id)), true);
        return "tareas/index";
    }

    @PostMapping
    public String guardar(@ModelAttribute("form") TareaPendienteForm form,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        try {
            var tarea = tareaService.guardar(form, obraService.obraActiva(session));
            redirectAttributes.addFlashAttribute("success", form.getId() == null ? "Tarea creada correctamente." : "Tarea actualizada correctamente.");
            return "redirect:/tareas#tarea-" + tarea.getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/tareas";
        }
    }

    @PostMapping("/{id}/estado")
    public String estado(@PathVariable Long id,
                         @RequestParam EstadoTarea estado,
                         RedirectAttributes redirectAttributes) {
        tareaService.cambiarEstado(id, estado);
        redirectAttributes.addFlashAttribute("success", "Estado actualizado.");
        return "redirect:/tareas#tarea-" + id;
    }

    @PostMapping("/{id}/checklist")
    public String agregarChecklist(@PathVariable Long id,
                                   @RequestParam String descripcion,
                                   RedirectAttributes redirectAttributes) {
        try {
            tareaService.agregarChecklist(id, descripcion);
            redirectAttributes.addFlashAttribute("success", "Punto agregado al checklist.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/tareas#tarea-" + id;
    }

    @PostMapping("/checklist/{id}/toggle")
    public String alternarChecklist(@PathVariable Long id,
                                    @RequestParam Long tareaId) {
        tareaService.alternarChecklist(id);
        return "redirect:/tareas#tarea-" + tareaId;
    }

    @PostMapping("/checklist/{id}/eliminar")
    public String eliminarChecklist(@PathVariable Long id,
                                    @RequestParam Long tareaId) {
        tareaService.eliminarChecklist(id);
        return "redirect:/tareas#tarea-" + tareaId;
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        tareaService.eliminar(id);
        redirectAttributes.addFlashAttribute("success", "Tarea eliminada del anotador.");
        return "redirect:/tareas";
    }

    private void cargarModelo(Model model, HttpSession session, TareaPendienteForm form, boolean modoEdicion) {
        var obra = obraService.obraActiva(session);
        var pendientes = tareaService.pendientes(obra);
        var historial = tareaService.historial(obra);
        model.addAttribute("form", form);
        model.addAttribute("modoEdicion", modoEdicion);
        model.addAttribute("prioridades", PrioridadTarea.values());
        model.addAttribute("estados", EstadoTarea.values());
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("historial", historial);
        model.addAttribute("totalPendientes", pendientes.size());
        model.addAttribute("totalUrgentes", pendientes.stream().filter(t -> t.getPrioridad() == PrioridadTarea.URGENTE).count());
        model.addAttribute("totalVencidas", pendientes.stream().filter(t -> t.vencida() || t.venceHoy()).count());
        model.addAttribute("totalCerradas", historial.size());
    }
}
