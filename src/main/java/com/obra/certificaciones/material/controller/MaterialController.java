package com.obra.certificaciones.material.controller;

import com.obra.certificaciones.material.dto.ItemMaterialResumen;
import com.obra.certificaciones.material.dto.RecepcionMaterialForm;
import com.obra.certificaciones.material.entity.RecepcionMaterial;
import com.obra.certificaciones.material.service.MaterialService;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.service.OrdenCompraService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/materiales")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;
    private final OrdenCompraService ordenCompraService;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("ordenes", materialService.listarOrdenesConMateriales());
        return "material/lista";
    }

    @GetMapping("/oc/{ordenCompraId}")
    public String detalle(@PathVariable Long ordenCompraId, Model model) {
        OrdenCompra orden = ordenCompraService.obtener(ordenCompraId);
        var itemsResumen = materialService.calcularResumenItems(ordenCompraId);
        model.addAttribute("orden", orden);
        cargarResumenMateriales(itemsResumen, model);
        model.addAttribute("recepciones", materialService.listarRecepciones(ordenCompraId));
        return "material/detalle";
    }

    @GetMapping("/oc/{ordenCompraId}/recepciones/nueva")
    public String nuevaRecepcion(@PathVariable Long ordenCompraId, Model model) {
        cargarFormulario(ordenCompraId, materialService.crearForm(ordenCompraId), model);
        return "material/form";
    }

    @PostMapping("/oc/{ordenCompraId}/recepciones")
    public String guardarRecepcion(@PathVariable Long ordenCompraId,
                                   @ModelAttribute("form") RecepcionMaterialForm form,
                                   Model model) {
        try {
            RecepcionMaterial recepcion = materialService.guardar(ordenCompraId, form);
            return "redirect:/materiales/oc/" + ordenCompraId + "/recepciones/" + recepcion.getId();
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            cargarFormulario(ordenCompraId, form, model);
            return "material/form";
        }
    }

    @GetMapping("/oc/{ordenCompraId}/recepciones/{recepcionId}")
    public String detalleRecepcion(@PathVariable Long ordenCompraId,
                                   @PathVariable Long recepcionId,
                                   Model model) {
        model.addAttribute("orden", ordenCompraService.obtener(ordenCompraId));
        model.addAttribute("recepcion", materialService.obtenerRecepcion(recepcionId));
        return "material/recepcion-detalle";
    }

    @PostMapping("/oc/{ordenCompraId}/recepciones/{recepcionId}/eliminar")
    public String eliminarRecepcion(@PathVariable Long ordenCompraId, @PathVariable Long recepcionId) {
        materialService.eliminarRecepcion(ordenCompraId, recepcionId);
        return "redirect:/materiales/oc/" + ordenCompraId;
    }

    private void cargarFormulario(Long ordenCompraId, RecepcionMaterialForm form, Model model) {
        OrdenCompra orden = ordenCompraService.obtener(ordenCompraId);
        var itemsResumen = materialService.calcularResumenItems(ordenCompraId);
        model.addAttribute("orden", orden);
        cargarResumenMateriales(itemsResumen, model);
        model.addAttribute("form", form);
    }

    private void cargarResumenMateriales(List<ItemMaterialResumen> itemsResumen, Model model) {
        model.addAttribute("itemsResumen", itemsResumen);
        model.addAttribute("totalMaterialComprado", sumar(itemsResumen, "comprado"));
        model.addAttribute("totalMaterialRecibido", sumar(itemsResumen, "recibido"));
        model.addAttribute("totalMaterialPendiente", sumar(itemsResumen, "pendiente"));
    }

    private BigDecimal sumar(List<ItemMaterialResumen> itemsResumen, String campo) {
        return itemsResumen.stream()
                .map(resumen -> switch (campo) {
                    case "recibido" -> resumen.cantidadRecibida();
                    case "pendiente" -> resumen.cantidadPendiente();
                    default -> resumen.cantidadComprada();
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
