package com.obra.certificaciones.material.catalogo.controller;

import com.obra.certificaciones.material.catalogo.entity.MaterialCatalogo;
import com.obra.certificaciones.material.catalogo.service.MaterialCatalogoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/catalogo-materiales")
@RequiredArgsConstructor
public class MaterialCatalogoController {

    private final MaterialCatalogoService materialCatalogoService;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("materiales", materialCatalogoService.listarTodos());
        return "material/catalogo/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("material", new MaterialCatalogo());
        return "material/catalogo/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("material", materialCatalogoService.obtener(id));
        return "material/catalogo/form";
    }

    @PostMapping
    public String guardar(@ModelAttribute("material") MaterialCatalogo material, Model model) {
        try {
            materialCatalogoService.guardar(material);
            return "redirect:/catalogo-materiales";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("material", material);
            return "material/catalogo/form";
        }
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id) {
        materialCatalogoService.eliminar(id);
        return "redirect:/catalogo-materiales";
    }
}
