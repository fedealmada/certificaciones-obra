package com.obra.certificaciones.categoria.controller;

import com.obra.certificaciones.categoria.entity.CategoriaOrden;
import com.obra.certificaciones.categoria.service.CategoriaOrdenService;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/categorias")
@RequiredArgsConstructor
public class CategoriaOrdenController {

    private final CategoriaOrdenService categoriaOrdenService;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("categorias", categoriaOrdenService.listarTodos());
        return "categoria/lista";
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        cargarFormulario(new CategoriaOrden(), model);
        return "categoria/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        cargarFormulario(categoriaOrdenService.obtener(id), model);
        return "categoria/form";
    }

    @PostMapping
    public String guardar(@ModelAttribute("categoria") CategoriaOrden categoria, Model model) {
        try {
            categoriaOrdenService.guardar(categoria);
            return "redirect:/categorias";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            cargarFormulario(categoria, model);
            return "categoria/form";
        }
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id) {
        categoriaOrdenService.eliminar(id);
        return "redirect:/categorias";
    }

    private void cargarFormulario(CategoriaOrden categoria, Model model) {
        model.addAttribute("categoria", categoria);
        model.addAttribute("tipos", CategoriaItem.values());
    }
}
