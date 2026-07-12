package com.obra.certificaciones.obra.controller;

import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.obra.service.ObraService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/obras")
@RequiredArgsConstructor
public class ObraController {
    private final ObraService obraService;

    @GetMapping
    public String listar(Model model, HttpSession session) {
        model.addAttribute("obras", obraService.listar());
        model.addAttribute("obra", new Obra());
        model.addAttribute("obraActiva", obraService.obraActiva(session));
        return "obra/lista";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model, HttpSession session) {
        model.addAttribute("obras", obraService.listar());
        model.addAttribute("obra", obraService.obtener(id));
        model.addAttribute("obraActiva", obraService.obraActiva(session));
        return "obra/lista";
    }

    @PostMapping
    public String guardar(@ModelAttribute("obra") Obra obra,
                          Model model,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        try {
            Obra guardada = obraService.guardar(obra);
            obraService.seleccionar(guardada.getId(), session);
            redirectAttributes.addFlashAttribute("success", "Obra guardada y seleccionada correctamente.");
            return "redirect:/obras";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("obras", obraService.listar());
            model.addAttribute("obra", obra);
            model.addAttribute("obraActiva", obraService.obraActiva(session));
            return "obra/lista";
        }
    }

    @PostMapping("/{id}/seleccionar")
    public String seleccionar(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        obraService.seleccionar(id, session);
        redirectAttributes.addFlashAttribute("success", "Obra seleccionada correctamente.");
        return "redirect:/";
    }
}
