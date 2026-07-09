package com.obra.certificaciones.proveedor.controller;

import com.obra.certificaciones.proveedor.entity.Proveedor;
import com.obra.certificaciones.proveedor.service.ProveedorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/proveedores")
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService proveedorService;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("proveedores", proveedorService.listarTodos());
        return "proveedor/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("proveedor", new Proveedor());
        return "proveedor/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("proveedor", proveedorService.obtener(id));
        return "proveedor/form";
    }

    @PostMapping
    public String guardar(@ModelAttribute("proveedor") Proveedor proveedor, Model model) {
        try {
            Proveedor guardado = proveedorService.guardar(proveedor);
            return "redirect:/proveedores/" + guardado.getId();
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("proveedor", proveedor);
            return "proveedor/form";
        }
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        model.addAttribute("proveedor", proveedorService.obtener(id));
        model.addAttribute("ordenes", proveedorService.ordenesProveedor(id));
        return "proveedor/detalle";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id) {
        proveedorService.eliminar(id);
        return "redirect:/proveedores";
    }
}
