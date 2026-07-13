package com.obra.certificaciones.tablero.controller;

import com.obra.certificaciones.obra.service.ObraService;
import com.obra.certificaciones.tablero.entity.TableroCertificado;
import com.obra.certificaciones.tablero.entity.TableroCertificadoItem;
import com.obra.certificaciones.tablero.service.TableroCertificadoService;
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

@Controller
@RequestMapping("/tablero-certificados")
@RequiredArgsConstructor
public class TableroCertificadoController {
    private final TableroCertificadoService tableroService;
    private final ObraService obraService;

    @GetMapping
    public String listar(Model model, HttpSession session) {
        var obra = obraService.obraActiva(session);
        model.addAttribute("tableros", tableroService.listar(obra));
        return "tablero/lista";
    }

    @PostMapping
    public String crear(@RequestParam(required = false) String nombre,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {
        var tablero = tableroService.crear(obraService.obraActiva(session), nombre, fechaDesde, fechaHasta);
        redirectAttributes.addFlashAttribute("success", "Tablero creado correctamente.");
        return "redirect:/tablero-certificados/" + tablero.getId();
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        model.addAttribute("tablero", tableroService.obtener(id));
        model.addAttribute("totales", tableroService.totales(id));
        return "tablero/detalle";
    }

    @PostMapping("/{id}")
    public String guardarDatos(@PathVariable Long id,
                               @ModelAttribute("tablero") TableroCertificado tablero,
                               RedirectAttributes redirectAttributes) {
        tablero.setId(id);
        tableroService.guardarDatos(tablero);
        redirectAttributes.addFlashAttribute("success", "Datos del tablero actualizados.");
        return "redirect:/tablero-certificados/" + id;
    }

    @PostMapping("/{id}/importar-items")
    public String importarItems(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        int agregados = tableroService.importarItemsDesdeOrdenes(id);
        redirectAttributes.addFlashAttribute("success", agregados + " items agregados al tablero.");
        return "redirect:/tablero-certificados/" + id;
    }

    @PostMapping("/{id}/items/manual")
    public String crearItemManual(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        tableroService.crearItemManual(id);
        redirectAttributes.addFlashAttribute("success", "Item manual agregado.");
        return "redirect:/tablero-certificados/" + id;
    }

    @PostMapping("/{id}/grupos")
    public String crearGrupo(@PathVariable Long id,
                             @RequestParam(required = false) String nombreGrupo,
                             RedirectAttributes redirectAttributes) {
        tableroService.crearGrupo(id, nombreGrupo);
        redirectAttributes.addFlashAttribute("success", "Grupo agregado.");
        return "redirect:/tablero-certificados/" + id;
    }

    @PostMapping("/{id}/items/{itemId}")
    public String guardarItem(@PathVariable Long id,
                              @PathVariable Long itemId,
                              @ModelAttribute("item") TableroCertificadoItem item,
                              RedirectAttributes redirectAttributes) {
        tableroService.guardarItem(itemId, item);
        redirectAttributes.addFlashAttribute("success", "Item actualizado.");
        return "redirect:/tablero-certificados/" + id;
    }

    @PostMapping("/{id}/items/{itemId}/eliminar")
    public String eliminarItem(@PathVariable Long id,
                               @PathVariable Long itemId,
                               RedirectAttributes redirectAttributes) {
        tableroService.eliminarItem(itemId);
        redirectAttributes.addFlashAttribute("success", "Item eliminado del tablero.");
        return "redirect:/tablero-certificados/" + id;
    }
}
