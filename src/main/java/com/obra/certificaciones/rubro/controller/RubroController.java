package com.obra.certificaciones.rubro.controller;

import com.obra.certificaciones.rubro.entity.Rubro;
import com.obra.certificaciones.rubro.service.RubroService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/rubros")
@RequiredArgsConstructor
public class RubroController {

    private final RubroService rubroService;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("rubros", rubroService.listarTodos());
        return "rubro/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("rubro", new Rubro());
        model.addAttribute("rubrosPadre", rubroService.listarTodos());
        return "rubro/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        Rubro rubro = rubroService.obtener(id);
        rubro.setPadreId(rubro.getPadre() == null ? null : rubro.getPadre().getId());
        model.addAttribute("rubro", rubro);
        model.addAttribute("rubrosPadre", rubroService.listarTodos().stream()
                .filter(rubroPadre -> !rubroPadre.getId().equals(id))
                .toList());
        return "rubro/form";
    }

    @PostMapping
    public String guardar(@ModelAttribute("rubro") Rubro rubro, Model model) {
        try {
            rubroService.guardar(rubro);
            return "redirect:/rubros";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("rubro", rubro);
            model.addAttribute("rubrosPadre", rubroService.listarTodos());
            return "rubro/form";
        }
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("mensaje", rubroService.eliminar(id));
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar el rubro porque esta vinculado a otros datos. Editalo para dejarlo inactivo.");
        }
        return "redirect:/rubros";
    }

    @PostMapping("/eliminar-todos")
    public String eliminarTodos(RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("mensaje", rubroService.eliminarTodos());
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("error", "No se pudieron eliminar todos los rubros porque hay datos vinculados que deben revisarse.");
        }
        return "redirect:/rubros";
    }
}
