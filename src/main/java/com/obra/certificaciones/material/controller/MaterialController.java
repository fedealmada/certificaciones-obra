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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        try {
            RecepcionMaterial recepcion = materialService.guardar(ordenCompraId, form);
            redirectAttributes.addFlashAttribute("accionCompletada", true);
            redirectAttributes.addFlashAttribute("accionTitulo", "¡Entrega agregada!");
            redirectAttributes.addFlashAttribute("accionMensaje", "El envio quedo registrado correctamente en la orden de compra.");
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
        OrdenCompra orden = ordenCompraService.obtener(ordenCompraId);
        RecepcionMaterial recepcion = materialService.obtenerRecepcion(recepcionId);
        List<RecepcionMaterial> recepcionesOrden = materialService.listarRecepciones(ordenCompraId);
        BigDecimal totalRecibido = totalRecibido(recepcion);
        BigDecimal importeRecibido = importeRecibido(recepcion);
        BigDecimal totalComprado = cantidadCompradaRecepcion(recepcion);
        model.addAttribute("orden", orden);
        model.addAttribute("recepcion", recepcion);
        model.addAttribute("recepcionesOrden", recepcionesOrden);
        model.addAttribute("numeroRecepcion", numeroRecepcion(recepcionesOrden, recepcionId));
        model.addAttribute("totalRecibidoRecepcion", totalRecibido);
        model.addAttribute("importeRecibidoRecepcion", importeRecibido);
        model.addAttribute("totalCompradoRecepcion", totalComprado);
        model.addAttribute("porcentajeRecepcionActual", porcentaje(totalRecibido, totalComprado));
        model.addAttribute("importesPorItemRecepcion", importesPorItem(recepcion));
        model.addAttribute("porcentajesPorItemRecepcion", porcentajesPorItem(recepcion));
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

    private int numeroRecepcion(List<RecepcionMaterial> recepciones, Long recepcionId) {
        for (int indice = 0; indice < recepciones.size(); indice++) {
            if (recepciones.get(indice).getId().equals(recepcionId)) {
                return indice + 1;
            }
        }
        return 1;
    }

    private BigDecimal totalRecibido(RecepcionMaterial recepcion) {
        return recepcion.getItems().stream()
                .map(item -> item.getCantidadRecibida() == null ? BigDecimal.ZERO : item.getCantidadRecibida())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal importeRecibido(RecepcionMaterial recepcion) {
        return recepcion.getItems().stream()
                .map(item -> {
                    BigDecimal cantidad = item.getCantidadRecibida() == null ? BigDecimal.ZERO : item.getCantidadRecibida();
                    BigDecimal unitario = item.getItemOrdenCompra().getPrecioUnitario() == null ? BigDecimal.ZERO : item.getItemOrdenCompra().getPrecioUnitario();
                    return unitario.multiply(cantidad);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal cantidadCompradaRecepcion(RecepcionMaterial recepcion) {
        return recepcion.getItems().stream()
                .map(item -> item.getItemOrdenCompra().getCantidad() == null ? BigDecimal.ZERO : item.getItemOrdenCompra().getCantidad())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal porcentaje(BigDecimal valor, BigDecimal total) {
        if (valor == null || total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return valor.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private Map<Long, BigDecimal> importesPorItem(RecepcionMaterial recepcion) {
        Map<Long, BigDecimal> importes = new HashMap<>();
        recepcion.getItems().forEach(item -> {
            BigDecimal cantidad = item.getCantidadRecibida() == null ? BigDecimal.ZERO : item.getCantidadRecibida();
            BigDecimal unitario = item.getItemOrdenCompra().getPrecioUnitario() == null ? BigDecimal.ZERO : item.getItemOrdenCompra().getPrecioUnitario();
            importes.put(item.getId(), unitario.multiply(cantidad));
        });
        return importes;
    }

    private Map<Long, BigDecimal> porcentajesPorItem(RecepcionMaterial recepcion) {
        Map<Long, BigDecimal> porcentajes = new HashMap<>();
        recepcion.getItems().forEach(item -> {
            BigDecimal cantidad = item.getCantidadRecibida() == null ? BigDecimal.ZERO : item.getCantidadRecibida();
            BigDecimal comprada = item.getItemOrdenCompra().getCantidad() == null ? BigDecimal.ZERO : item.getItemOrdenCompra().getCantidad();
            porcentajes.put(item.getId(), porcentaje(cantidad, comprada));
        });
        return porcentajes;
    }
}
