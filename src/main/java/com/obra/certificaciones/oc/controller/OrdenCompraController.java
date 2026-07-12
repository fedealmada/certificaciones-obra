package com.obra.certificaciones.oc.controller;

import com.obra.certificaciones.alerta.service.AlertaSistemaService;
import com.obra.certificaciones.categoria.service.CategoriaOrdenService;
import com.obra.certificaciones.certificacion.service.CertificacionCalculoService;
import com.obra.certificaciones.certificacion.service.CertificacionService;
import com.obra.certificaciones.material.dto.EstadoRecepcionMaterial;
import com.obra.certificaciones.material.dto.ItemMaterialResumen;
import com.obra.certificaciones.material.catalogo.service.MaterialCatalogoService;
import com.obra.certificaciones.material.service.MaterialService;
import com.obra.certificaciones.oc.dto.OrdenCompraForm;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.entity.ModoSeguimientoOrden;
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
        Map<Long, BigDecimal> avancesPorOrden = calcularAvancesOrdenes(ordenes);
        Map<Long, Boolean> ordenesMateriales = calcularOrdenesConEntregas(ordenes);
        model.addAttribute("avancesPorOrden", avancesPorOrden);
        model.addAttribute("ordenesCerradas", calcularOrdenesCerradas(avancesPorOrden));
        model.addAttribute("ordenesMateriales", ordenesMateriales);
        model.addAttribute("ordenesCertificables", calcularOrdenesCertificables(ordenes));
        model.addAttribute("ordenesSoloRegistro", calcularOrdenesSoloRegistro(ordenes));
        model.addAttribute("estadosEntregaPorOrden", materialService.calcularEstadosOrdenes(ordenes));
        model.addAttribute("viajesPorOrden", materialService.contarRecepcionesPorOrdenes(ordenesMateriales.entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList()));
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
        model.addAttribute("modosSeguimiento", ModoSeguimientoOrden.values());
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
        model.addAttribute("modosSeguimiento", ModoSeguimientoOrden.values());
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
            model.addAttribute("modosSeguimiento", ModoSeguimientoOrden.values());
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
        Map<Long, BigDecimal> acumuladosPorItem = calculoService.porcentajesAcumuladosPorItem(id);
        var itemsResumen = calculoService.calcularResumenItems(ordenCompra.getItems(), acumuladosPorItem);
        var certificadosResumen = certificacionService.listarPorOrdenCompra(id).stream()
                .map(certificacion -> {
                    var detalleCertificacion = calculoService.calcularDetalle(certificacion);
                    return new com.obra.certificaciones.certificacion.dto.CertificacionResumenVista(
                            certificacion,
                            calculoService.calcularResumen(ordenCompra.getItems(), detalleCertificacion, acumuladosPorItem));
                })
                .toList();
        model.addAttribute("orden", ordenCompra);
        model.addAttribute("itemsResumen", itemsResumen);
        model.addAttribute("certificaciones", certificadosResumen);
        model.addAttribute("totalManoObra", totalPorCategoria(ordenCompra, CategoriaItem.MANO_OBRA));
        model.addAttribute("totalCertificable", totalCertificable(ordenCompra));
        model.addAttribute("totalMateriales", totalPorCategoria(ordenCompra, CategoriaItem.MATERIAL));
        model.addAttribute("totalOtros", totalPorCategoria(ordenCompra, CategoriaItem.OTRO));
        BigDecimal totalCertificado = itemsResumen.stream()
                .filter(resumen -> calculoService.esItemCertificable(resumen.itemOrdenCompra()))
                .map(resumen -> resumen.montoAcumulado() == null ? BigDecimal.ZERO : resumen.montoAcumulado())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldoOc = itemsResumen.stream()
                .filter(resumen -> calculoService.esItemCertificable(resumen.itemOrdenCompra()))
                .map(resumen -> resumen.saldoPendiente() == null ? BigDecimal.ZERO : resumen.saldoPendiente())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCertificable = totalCertificable(ordenCompra);
        BigDecimal porcentajeAvanceOc = porcentaje(totalCertificado, totalCertificable);
        boolean ordenMaterial = ordenCompra.usaSeguimientoEntregas();
        boolean ordenCertificable = ordenCompra.usaSeguimientoCertificacion();
        boolean ordenSoloRegistro = ordenCompra.usaSoloRegistro();
        List<ItemMaterialResumen> materialItemsResumen = ordenMaterial
                ? materialService.calcularResumenItems(id)
                : List.of();
        BigDecimal totalMaterialCompradoCantidad = totalCantidadMaterial(materialItemsResumen, TipoCantidadMaterial.COMPRADA);
        BigDecimal totalMaterialRecibidoCantidad = totalCantidadMaterial(materialItemsResumen, TipoCantidadMaterial.RECIBIDA);
        BigDecimal totalMaterialPendienteCantidad = totalCantidadMaterial(materialItemsResumen, TipoCantidadMaterial.PENDIENTE);
        BigDecimal porcentajeRecepcionOc = porcentaje(totalMaterialRecibidoCantidad, totalMaterialCompradoCantidad);
        EstadoRecepcionMaterial estadoEntregaOrden = ordenMaterial
                ? estadoEntregaOrden(materialItemsResumen)
                : null;
        model.addAttribute("totalCertificado", totalCertificado);
        model.addAttribute("saldoOc", saldoOc);
        model.addAttribute("porcentajeAvanceOc", porcentajeAvanceOc);
        model.addAttribute("porcentajeAvanceOcBarra", porcentajeAvanceOc.min(BigDecimal.valueOf(100)));
        model.addAttribute("ordenCerrada", porcentajeAvanceOc.compareTo(BigDecimal.valueOf(100)) >= 0);
        model.addAttribute("ordenMaterial", ordenMaterial);
        model.addAttribute("ordenCertificable", ordenCertificable);
        model.addAttribute("ordenSoloRegistro", ordenSoloRegistro);
        model.addAttribute("estadoEntregaOrden", estadoEntregaOrden);
        model.addAttribute("totalMaterialCompradoCantidad", totalMaterialCompradoCantidad);
        model.addAttribute("totalMaterialRecibidoCantidad", totalMaterialRecibidoCantidad);
        model.addAttribute("totalMaterialPendienteCantidad", totalMaterialPendienteCantidad);
        model.addAttribute("porcentajeRecepcionOc", porcentajeRecepcionOc);
        model.addAttribute("porcentajeRecepcionOcBarra", porcentajeRecepcionOc.min(BigDecimal.valueOf(100)));
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

    private BigDecimal totalCertificable(OrdenCompra ordenCompra) {
        return ordenCompra.getItems().stream()
                .filter(calculoService::esItemCertificable)
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
            if (!orden.usaSeguimientoCertificacion()) {
                avances.put(orden.getId(), BigDecimal.ZERO);
                continue;
            }
            Map<Long, BigDecimal> acumuladosItems = acumuladosPorOrden.getOrDefault(orden.getId(), Map.of());
            BigDecimal totalCertificable = BigDecimal.ZERO;
            BigDecimal totalCertificado = BigDecimal.ZERO;
            for (ItemOrdenCompra item : orden.getItems()) {
                if (!calculoService.esItemCertificable(item)) {
                    continue;
                }
                BigDecimal importe = item.getImporte() == null ? BigDecimal.ZERO : item.getImporte();
                BigDecimal acumulado = acumuladosItems.getOrDefault(item.getId(), BigDecimal.ZERO);
                totalCertificable = totalCertificable.add(importe);
                totalCertificado = totalCertificado.add(calculoService.calcularMonto(importe, acumulado));
            }
            avances.put(orden.getId(), porcentaje(totalCertificado, totalCertificable));
        }
        return avances;
    }

    private Map<Long, Boolean> calcularOrdenesCerradas(Map<Long, BigDecimal> avancesPorOrden) {
        Map<Long, Boolean> resultado = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> avance : avancesPorOrden.entrySet()) {
            BigDecimal porcentaje = avance.getValue() == null ? BigDecimal.ZERO : avance.getValue();
            resultado.put(avance.getKey(), porcentaje.compareTo(BigDecimal.valueOf(100)) >= 0);
        }
        return resultado;
    }

    private BigDecimal totalCantidadMaterial(List<ItemMaterialResumen> items, TipoCantidadMaterial tipo) {
        return items.stream()
                .map(item -> switch (tipo) {
                    case COMPRADA -> item.cantidadComprada();
                    case RECIBIDA -> item.cantidadRecibida();
                    case PENDIENTE -> item.cantidadPendiente();
                })
                .map(valor -> valor == null ? BigDecimal.ZERO : valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, Boolean> calcularOrdenesConEntregas(List<OrdenCompra> ordenes) {
        Map<Long, Boolean> resultado = new HashMap<>();
        for (OrdenCompra orden : ordenes) {
            resultado.put(orden.getId(), orden.usaSeguimientoEntregas());
        }
        return resultado;
    }

    private Map<Long, Boolean> calcularOrdenesCertificables(List<OrdenCompra> ordenes) {
        Map<Long, Boolean> resultado = new HashMap<>();
        for (OrdenCompra orden : ordenes) {
            resultado.put(orden.getId(), orden.usaSeguimientoCertificacion());
        }
        return resultado;
    }

    private Map<Long, Boolean> calcularOrdenesSoloRegistro(List<OrdenCompra> ordenes) {
        Map<Long, Boolean> resultado = new HashMap<>();
        for (OrdenCompra orden : ordenes) {
            resultado.put(orden.getId(), orden.usaSoloRegistro());
        }
        return resultado;
    }

    private EstadoRecepcionMaterial estadoEntregaOrden(List<ItemMaterialResumen> resumenItems) {
        if (resumenItems.isEmpty() || resumenItems.stream().allMatch(item -> item.estado() == EstadoRecepcionMaterial.PENDIENTE)) {
            return EstadoRecepcionMaterial.PENDIENTE;
        }
        if (resumenItems.stream().allMatch(item -> item.estado() == EstadoRecepcionMaterial.COMPLETO)) {
            return EstadoRecepcionMaterial.COMPLETO;
        }
        return EstadoRecepcionMaterial.PARCIAL;
    }

    private enum TipoCantidadMaterial {
        COMPRADA,
        RECIBIDA,
        PENDIENTE
    }
}
