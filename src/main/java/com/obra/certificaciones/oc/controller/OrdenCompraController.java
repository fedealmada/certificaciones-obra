package com.obra.certificaciones.oc.controller;

import com.obra.certificaciones.alerta.service.AlertaSistemaService;
import com.obra.certificaciones.categoria.service.CategoriaOrdenService;
import com.obra.certificaciones.certificacion.service.CertificacionCalculoService;
import com.obra.certificaciones.certificacion.service.CertificacionService;
import com.obra.certificaciones.material.dto.EstadoRecepcionMaterial;
import com.obra.certificaciones.material.catalogo.service.MaterialCatalogoService;
import com.obra.certificaciones.material.service.MaterialService;
import com.obra.certificaciones.oc.dto.OrdenCompraForm;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.repository.ItemOrdenCompraRepository;
import com.obra.certificaciones.oc.service.OrdenCompraService;
import com.obra.certificaciones.proveedor.service.ProveedorService;
import com.obra.certificaciones.rubro.service.RubroService;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/oc")
@RequiredArgsConstructor
public class OrdenCompraController {
    private final OrdenCompraService ordenCompraService;
    private final CertificacionService certificacionService;
    private final CertificacionCalculoService calculoService;
    private final ProveedorService proveedorService;
    private final RubroService rubroService;
    private final ItemOrdenCompraRepository itemOrdenCompraRepository;
    private final MaterialCatalogoService materialCatalogoService;
    private final MaterialService materialService;
    private final CategoriaOrdenService categoriaOrdenService;
    private final AlertaSistemaService alertaSistemaService;

    @GetMapping
    public String listar(@RequestParam(required = false) String proveedor,
                         @RequestParam(required = false) Long categoriaId,
                         @RequestParam(required = false) String rubro,
                         Model model) {
        List<OrdenCompra> ordenes = ordenCompraService.listar(proveedor, categoriaId, rubro);
        model.addAttribute("ordenes", ordenes);
        model.addAttribute("certificadosPorOrden", certificacionService.contarPorOrdenes(ordenes.stream()
                .map(OrdenCompra::getId)
                .toList()));
        model.addAttribute("avancesPorOrden", calcularAvancesOrdenes(ordenes));
        model.addAttribute("ordenesMateriales", calcularOrdenesMateriales(ordenes));
        model.addAttribute("estadosEntregaPorOrden", calcularEstadosEntrega(ordenes));
        model.addAttribute("viajesPorOrden", calcularViajesEntrega(ordenes));
        model.addAttribute("categorias", categoriaOrdenService.listarActivas());
        model.addAttribute("proveedor", proveedor);
        model.addAttribute("categoriaSeleccionada", categoriaId);
        model.addAttribute("rubro", rubro);
        return "oc/lista";
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("form", ordenCompraService.crearForm(null));
        model.addAttribute("categorias", categoriaOrdenService.listarActivas());
        model.addAttribute("proveedores", proveedorService.listarTodos());
        model.addAttribute("rubros", rubroService.listarActivos());
        model.addAttribute("itemsManoObra", itemOrdenCompraRepository.findByCategoriaOrderByOrdenCompraNumeroAscIdAsc(CategoriaItem.MANO_OBRA));
        model.addAttribute("catalogoMateriales", materialCatalogoService.listarActivos());
        return "oc/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("form", ordenCompraService.crearForm(id));
        model.addAttribute("categorias", categoriaOrdenService.listarActivas());
        model.addAttribute("proveedores", proveedorService.listarTodos());
        model.addAttribute("rubros", rubroService.listarActivos());
        model.addAttribute("itemsManoObra", itemOrdenCompraRepository.findByCategoriaOrderByOrdenCompraNumeroAscIdAsc(CategoriaItem.MANO_OBRA));
        model.addAttribute("catalogoMateriales", materialCatalogoService.listarActivos());
        return "oc/form";
    }

    @PostMapping
    public String guardar(@ModelAttribute("form") OrdenCompraForm form, Model model, RedirectAttributes redirectAttributes) {
        try {
            boolean esNueva = form.getId() == null;
            OrdenCompra ordenCompra = ordenCompraService.guardar(form);
            redirectAttributes.addFlashAttribute("accionCompletada", true);
            redirectAttributes.addFlashAttribute("accionTitulo", "¡Terminaste!");
            redirectAttributes.addFlashAttribute("accionMensaje", esNueva
                    ? "La orden de compra quedo cargada y lista para seguir la obra."
                    : "Los cambios de la orden de compra quedaron guardados.");
            return "redirect:/oc/" + ordenCompra.getId();
        } catch (IllegalArgumentException ex) {
            model.addAttribute("form", form);
            model.addAttribute("categorias", categoriaOrdenService.listarActivas());
            model.addAttribute("proveedores", proveedorService.listarTodos());
            model.addAttribute("rubros", rubroService.listarActivos());
            model.addAttribute("itemsManoObra", itemOrdenCompraRepository.findByCategoriaOrderByOrdenCompraNumeroAscIdAsc(CategoriaItem.MANO_OBRA));
            model.addAttribute("catalogoMateriales", materialCatalogoService.listarActivos());
            model.addAttribute("error", ex.getMessage());
            return "oc/form";
        }
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id) {
        ordenCompraService.eliminar(id);
        return "redirect:/oc";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        OrdenCompra ordenCompra = ordenCompraService.obtener(id);
        var itemsResumen = calculoService.calcularResumenItems(ordenCompra.getId(), ordenCompra.getItems());
        var certificadosResumen = certificacionService.listarPorOrdenCompra(id).stream()
                .map(certificacion -> new com.obra.certificaciones.certificacion.dto.CertificacionResumenVista(
                        certificacion,
                        calculoService.calcularResumen(id, ordenCompra.getItems(), calculoService.calcularDetalle(certificacion))))
                .toList();
        model.addAttribute("orden", ordenCompra);
        model.addAttribute("itemsResumen", itemsResumen);
        model.addAttribute("certificaciones", certificadosResumen);
        model.addAttribute("totalManoObra", totalPorCategoria(ordenCompra, CategoriaItem.MANO_OBRA));
        model.addAttribute("totalMateriales", totalPorCategoria(ordenCompra, CategoriaItem.MATERIAL));
        model.addAttribute("totalOtros", totalPorCategoria(ordenCompra, CategoriaItem.OTRO));
        BigDecimal totalCertificado = itemsResumen.stream()
                .map(resumen -> resumen.montoAcumulado() == null ? BigDecimal.ZERO : resumen.montoAcumulado())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldoOc = itemsResumen.stream()
                .map(resumen -> resumen.saldoPendiente() == null ? BigDecimal.ZERO : resumen.saldoPendiente())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalManoObra = totalPorCategoria(ordenCompra, CategoriaItem.MANO_OBRA);
        BigDecimal porcentajeAvanceOc = porcentaje(totalCertificado, totalManoObra);
        boolean ordenMaterial = esOrdenMaterial(ordenCompra);
        model.addAttribute("totalCertificado", totalCertificado);
        model.addAttribute("saldoOc", saldoOc);
        model.addAttribute("porcentajeAvanceOc", porcentajeAvanceOc);
        model.addAttribute("porcentajeAvanceOcBarra", porcentajeAvanceOc.min(BigDecimal.valueOf(100)));
        model.addAttribute("ordenCerrada", porcentajeAvanceOc.compareTo(BigDecimal.valueOf(100)) >= 0);
        model.addAttribute("ordenMaterial", ordenMaterial);
        model.addAttribute("estadoEntregaOrden", ordenMaterial ? materialService.calcularEstadoOrden(id) : null);
        model.addAttribute("alertasOrden", alertaSistemaService.alertasOrden(id));
        return "oc/detalle";
    }

    @GetMapping("/{ordenCompraId}/items/{itemId}")
    public String detalleItem(@PathVariable Long ordenCompraId, @PathVariable Long itemId, Model model) {
        OrdenCompra ordenCompra = ordenCompraService.obtener(ordenCompraId);
        ItemOrdenCompra item = ordenCompra.getItems().stream()
                .filter(itemOrdenCompra -> itemOrdenCompra.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El item no pertenece a esta orden de compra."));
        model.addAttribute("orden", ordenCompra);
        model.addAttribute("item", item);
        model.addAttribute("historial", calculoService.calcularHistorialItem(itemId));
        model.addAttribute("resumenItem", calculoService.calcularResumenItems(ordenCompraId, ordenCompra.getItems()).stream()
                .filter(resumen -> resumen.itemOrdenCompra().getId().equals(itemId))
                .findFirst()
                .orElseThrow());
        return "oc/item-detalle";
    }

    private BigDecimal totalPorCategoria(OrdenCompra ordenCompra, CategoriaItem categoria) {
        return ordenCompra.getItems().stream()
                .filter(item -> item.getCategoria() == categoria)
                .map(item -> item.getImporte() == null ? BigDecimal.ZERO : item.getImporte())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal porcentaje(BigDecimal valor, BigDecimal total) {
        if (valor == null || total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return valor.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private Map<Long, BigDecimal> calcularAvancesOrdenes(List<OrdenCompra> ordenes) {
        Map<Long, Map<Long, BigDecimal>> acumuladosPorOrden = calculoService.porcentajesAcumuladosPorOrdenes(ordenes.stream()
                .map(OrdenCompra::getId)
                .toList());
        Map<Long, BigDecimal> avances = new HashMap<>();
        for (OrdenCompra orden : ordenes) {
            Map<Long, BigDecimal> acumuladosItems = acumuladosPorOrden.getOrDefault(orden.getId(), Map.of());
            BigDecimal totalManoObra = BigDecimal.ZERO;
            BigDecimal totalCertificado = BigDecimal.ZERO;
            for (ItemOrdenCompra item : orden.getItems()) {
                if (item.getCategoria() != CategoriaItem.MANO_OBRA) {
                    continue;
                }
                BigDecimal importe = item.getImporte() == null ? BigDecimal.ZERO : item.getImporte();
                BigDecimal acumulado = acumuladosItems.getOrDefault(item.getId(), BigDecimal.ZERO);
                totalManoObra = totalManoObra.add(importe);
                totalCertificado = totalCertificado.add(calculoService.calcularMonto(importe, acumulado));
            }
            avances.put(orden.getId(), porcentaje(totalCertificado, totalManoObra));
        }
        return avances;
    }

    private Map<Long, Boolean> calcularOrdenesMateriales(List<OrdenCompra> ordenes) {
        Map<Long, Boolean> resultado = new HashMap<>();
        for (OrdenCompra orden : ordenes) {
            resultado.put(orden.getId(), esOrdenMaterial(orden));
        }
        return resultado;
    }

    private Map<Long, EstadoRecepcionMaterial> calcularEstadosEntrega(List<OrdenCompra> ordenes) {
        Map<Long, EstadoRecepcionMaterial> resultado = new HashMap<>();
        for (OrdenCompra orden : ordenes) {
            if (esOrdenMaterial(orden)) {
                resultado.put(orden.getId(), materialService.calcularEstadoOrden(orden.getId()));
            }
        }
        return resultado;
    }

    private Map<Long, Long> calcularViajesEntrega(List<OrdenCompra> ordenes) {
        Map<Long, Long> resultado = new HashMap<>();
        for (OrdenCompra orden : ordenes) {
            if (esOrdenMaterial(orden)) {
                resultado.put(orden.getId(), materialService.contarRecepciones(orden.getId()));
            }
        }
        return resultado;
    }

    private boolean esOrdenMaterial(OrdenCompra ordenCompra) {
        return ordenCompra.getItems().stream().anyMatch(item -> item.getCategoria() == CategoriaItem.MATERIAL);
    }
}
