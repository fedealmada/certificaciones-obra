package com.obra.certificaciones.material.controller;

import com.obra.certificaciones.deposito.dto.IngresoDepositoRecepcionForm;
import com.obra.certificaciones.deposito.entity.DepositoItem;
import com.obra.certificaciones.deposito.entity.TipoInsumoDeposito;
import com.obra.certificaciones.deposito.service.DepositoService;
import com.obra.certificaciones.material.dto.ItemMaterialResumen;
import com.obra.certificaciones.material.dto.RecepcionMaterialForm;
import com.obra.certificaciones.material.entity.ItemRecepcionMaterial;
import com.obra.certificaciones.material.entity.RecepcionMaterial;
import com.obra.certificaciones.material.service.MaterialService;
import com.obra.certificaciones.material.dto.EstadoRecepcionMaterial;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    private final DepositoService depositoService;

    @GetMapping
    public String listar(Model model) {
        List<OrdenCompra> ordenes = materialService.listarOrdenesConMateriales();
        Map<Long, List<ItemMaterialResumen>> resumenesPorOrden = new HashMap<>();
        ordenes.forEach(orden -> resumenesPorOrden.put(orden.getId(), materialService.calcularResumenItems(orden.getId())));
        model.addAttribute("ordenes", ordenes);
        model.addAttribute("viajesPorOrden", materialService.contarRecepcionesPorOrdenes(ordenes.stream().map(OrdenCompra::getId).toList()));
        model.addAttribute("estadosPorOrden", materialService.calcularEstadosOrdenes(ordenes));
        model.addAttribute("previstoPorOrden", totalesPorOrden(resumenesPorOrden, "comprado"));
        model.addAttribute("recibidoPorOrden", totalesPorOrden(resumenesPorOrden, "recibido"));
        model.addAttribute("pendientePorOrden", totalesPorOrden(resumenesPorOrden, "pendiente"));
        model.addAttribute("avancePorOrden", avancesPorOrden(resumenesPorOrden));
        model.addAttribute("cero", BigDecimal.ZERO);
        return "material/lista";
    }

    @GetMapping("/oc/{ordenCompraId}")
    public String detalle(@PathVariable Long ordenCompraId,
                          @RequestParam(required = false) String origen,
                          Model model) {
        OrdenCompra orden = ordenCompraService.obtener(ordenCompraId);
        var itemsResumen = materialService.calcularResumenItems(ordenCompraId);
        List<RecepcionMaterial> recepciones = materialService.listarRecepciones(ordenCompraId);
        model.addAttribute("orden", orden);
        cargarResumenMateriales(itemsResumen, model);
        model.addAttribute("recepciones", recepciones);
        model.addAttribute("cantidadesPorRecepcion", cantidadesPorRecepcion(recepciones));
        model.addAttribute("importesPorRecepcion", importesPorRecepcion(recepciones));
        model.addAttribute("avancesPorRecepcion", avancesPorRecepcion(recepciones));
        model.addAttribute("estadosPorRecepcion", estadosPorRecepcion(recepciones, itemsResumen));
        model.addAttribute("cero", BigDecimal.ZERO);
        model.addAttribute("origen", origen);
        model.addAttribute("desdeModuloEntregas", esOrigenEntregas(origen));
        return "material/detalle";
    }

    @GetMapping("/oc/{ordenCompraId}/recepciones/nueva")
    public String nuevaRecepcion(@PathVariable Long ordenCompraId,
                                 @RequestParam(required = false) String origen,
                                 Model model) {
        cargarFormulario(ordenCompraId, null, materialService.crearForm(ordenCompraId), false, origen, model);
        return "material/form";
    }

    @PostMapping("/oc/{ordenCompraId}/recepciones")
    public String guardarRecepcion(@PathVariable Long ordenCompraId,
                                   @RequestParam(required = false) String origen,
                                   @ModelAttribute("form") RecepcionMaterialForm form,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        try {
            RecepcionMaterial recepcion = materialService.guardar(ordenCompraId, form);
            redirectAttributes.addFlashAttribute("accionCompletada", true);
            redirectAttributes.addFlashAttribute("accionTitulo", "Entrega agregada");
            redirectAttributes.addFlashAttribute("accionMensaje", "El envio quedo registrado correctamente en la orden de compra.");
            return "redirect:/materiales/oc/" + ordenCompraId + "/recepciones/" + recepcion.getId() + sufijoOrigen(origen);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            cargarFormulario(ordenCompraId, null, form, false, origen, model);
            return "material/form";
        }
    }

    @GetMapping("/oc/{ordenCompraId}/recepciones/{recepcionId}/editar")
    public String editarRecepcion(@PathVariable Long ordenCompraId,
                                  @PathVariable Long recepcionId,
                                  @RequestParam(required = false) String origen,
                                  Model model) {
        cargarFormulario(ordenCompraId, recepcionId, materialService.crearFormEdicion(ordenCompraId, recepcionId), true, origen, model);
        return "material/form";
    }

    @PostMapping("/oc/{ordenCompraId}/recepciones/{recepcionId}")
    public String actualizarRecepcion(@PathVariable Long ordenCompraId,
                                      @PathVariable Long recepcionId,
                                      @RequestParam(required = false) String origen,
                                      @ModelAttribute("form") RecepcionMaterialForm form,
                                      Model model,
                                      RedirectAttributes redirectAttributes) {
        try {
            RecepcionMaterial recepcion = materialService.actualizar(ordenCompraId, recepcionId, form);
            redirectAttributes.addFlashAttribute("accionCompletada", true);
            redirectAttributes.addFlashAttribute("accionTitulo", "Entrega actualizada");
            redirectAttributes.addFlashAttribute("accionMensaje", "Los cambios del viaje quedaron guardados correctamente.");
            return "redirect:/materiales/oc/" + ordenCompraId + "/recepciones/" + recepcion.getId() + sufijoOrigen(origen);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            cargarFormulario(ordenCompraId, recepcionId, form, true, origen, model);
            return "material/form";
        }
    }

    @GetMapping("/oc/{ordenCompraId}/recepciones/{recepcionId}")
    public String detalleRecepcion(@PathVariable Long ordenCompraId,
                                   @PathVariable Long recepcionId,
                                   @RequestParam(required = false) String origen,
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
        model.addAttribute("origen", origen);
        model.addAttribute("desdeModuloEntregas", esOrigenEntregas(origen));
        return "material/recepcion-detalle";
    }

    @PostMapping("/oc/{ordenCompraId}/recepciones/{recepcionId}/eliminar")
    public String eliminarRecepcion(@PathVariable Long ordenCompraId, @PathVariable Long recepcionId) {
        materialService.eliminarRecepcion(ordenCompraId, recepcionId);
        return "redirect:/materiales/oc/" + ordenCompraId;
    }

    @GetMapping("/oc/{ordenCompraId}/recepciones/{recepcionId}/items/{itemRecepcionId}/deposito")
    public String ingresoDeposito(@PathVariable Long ordenCompraId,
                                  @PathVariable Long recepcionId,
                                  @PathVariable Long itemRecepcionId,
                                  @RequestParam(required = false) String origen,
                                  Model model) {
        ItemRecepcionMaterial itemRecepcion = obtenerItemRecepcionValido(ordenCompraId, recepcionId, itemRecepcionId);
        IngresoDepositoRecepcionForm form = new IngresoDepositoRecepcionForm();
        form.setCantidad(itemRecepcion.getCantidadRecibida());
        form.setNuevoInsumoNombre(nombreSugeridoDeposito(itemRecepcion));
        form.setCategoria("Materiales");
        cargarIngresoDeposito(model, ordenCompraId, recepcionId, itemRecepcion, form, origen);
        return "material/ingreso-deposito";
    }

    @PostMapping("/oc/{ordenCompraId}/recepciones/{recepcionId}/items/{itemRecepcionId}/deposito")
    public String guardarIngresoDeposito(@PathVariable Long ordenCompraId,
                                         @PathVariable Long recepcionId,
                                         @PathVariable Long itemRecepcionId,
                                         @RequestParam(required = false) String origen,
                                         @ModelAttribute("form") IngresoDepositoRecepcionForm form,
                                         Model model,
                                         RedirectAttributes redirectAttributes) {
        ItemRecepcionMaterial itemRecepcion = obtenerItemRecepcionValido(ordenCompraId, recepcionId, itemRecepcionId);
        try {
            DepositoItem itemDeposito = obtenerOCrearInsumoDeposito(form, itemRecepcion);
            depositoService.registrarEntradaDesdeRecepcion(itemRecepcion, itemDeposito, form.getCantidad(), form.getResponsable(), form.getDestino(), form.getObservacion());
            redirectAttributes.addFlashAttribute("accionCompletada", true);
            redirectAttributes.addFlashAttribute("accionTitulo", "Ingreso al deposito");
            redirectAttributes.addFlashAttribute("accionMensaje", "El material recibido quedo registrado como stock del deposito.");
            return "redirect:/materiales/oc/" + ordenCompraId + "/recepciones/" + recepcionId + sufijoOrigen(origen);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            cargarIngresoDeposito(model, ordenCompraId, recepcionId, itemRecepcion, form, origen);
            return "material/ingreso-deposito";
        }
    }

    private void cargarFormulario(Long ordenCompraId, Long recepcionId, RecepcionMaterialForm form, boolean modoEdicion, String origen, Model model) {
        OrdenCompra orden = ordenCompraService.obtener(ordenCompraId);
        var itemsResumen = modoEdicion
                ? materialService.calcularResumenItemsParaEdicion(ordenCompraId, recepcionId)
                : materialService.calcularResumenItems(ordenCompraId);
        model.addAttribute("orden", orden);
        cargarResumenMateriales(itemsResumen, model);
        model.addAttribute("form", form);
        model.addAttribute("modoEdicion", modoEdicion);
        model.addAttribute("recepcionId", recepcionId);
        model.addAttribute("origen", origen);
        model.addAttribute("desdeModuloEntregas", esOrigenEntregas(origen));
    }

    private void cargarResumenMateriales(List<ItemMaterialResumen> itemsResumen, Model model) {
        model.addAttribute("itemsResumen", itemsResumen);
        model.addAttribute("totalMaterialComprado", sumar(itemsResumen, "comprado"));
        model.addAttribute("totalMaterialRecibido", sumar(itemsResumen, "recibido"));
        model.addAttribute("totalMaterialPendiente", sumar(itemsResumen, "pendiente"));
        model.addAttribute("porcentajeMaterialRecibido", porcentaje(sumar(itemsResumen, "recibido"), sumar(itemsResumen, "comprado")));
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

    private Map<Long, BigDecimal> totalesPorOrden(Map<Long, List<ItemMaterialResumen>> resumenesPorOrden, String campo) {
        Map<Long, BigDecimal> totales = new HashMap<>();
        resumenesPorOrden.forEach((ordenId, resumenes) -> totales.put(ordenId, sumar(resumenes, campo)));
        return totales;
    }

    private Map<Long, BigDecimal> avancesPorOrden(Map<Long, List<ItemMaterialResumen>> resumenesPorOrden) {
        Map<Long, BigDecimal> avances = new HashMap<>();
        resumenesPorOrden.forEach((ordenId, resumenes) ->
                avances.put(ordenId, porcentaje(sumar(resumenes, "recibido"), sumar(resumenes, "comprado"))));
        return avances;
    }

    private Map<Long, BigDecimal> cantidadesPorRecepcion(List<RecepcionMaterial> recepciones) {
        Map<Long, BigDecimal> cantidades = new HashMap<>();
        recepciones.forEach(recepcion -> cantidades.put(recepcion.getId(), totalRecibido(recepcion)));
        return cantidades;
    }

    private Map<Long, BigDecimal> importesPorRecepcion(List<RecepcionMaterial> recepciones) {
        Map<Long, BigDecimal> importes = new HashMap<>();
        recepciones.forEach(recepcion -> importes.put(recepcion.getId(), importeRecibido(recepcion)));
        return importes;
    }

    private Map<Long, BigDecimal> avancesPorRecepcion(List<RecepcionMaterial> recepciones) {
        Map<Long, BigDecimal> avances = new HashMap<>();
        recepciones.forEach(recepcion -> avances.put(recepcion.getId(),
                porcentaje(totalRecibido(recepcion), cantidadCompradaRecepcion(recepcion))));
        return avances;
    }

    private Map<Long, EstadoRecepcionMaterial> estadosPorRecepcion(List<RecepcionMaterial> recepciones, List<ItemMaterialResumen> itemsResumen) {
        Map<Long, EstadoRecepcionMaterial> estados = new HashMap<>();
        Map<Long, EstadoRecepcionMaterial> estadosItems = new HashMap<>();
        itemsResumen.forEach(resumen -> estadosItems.put(resumen.itemOrdenCompra().getId(), resumen.estado()));
        recepciones.forEach(recepcion -> estados.put(recepcion.getId(), estadoRecepcion(recepcion, estadosItems)));
        return estados;
    }

    private EstadoRecepcionMaterial estadoRecepcion(RecepcionMaterial recepcion, Map<Long, EstadoRecepcionMaterial> estadosItems) {
        if (recepcion.getItems().isEmpty()) {
            return EstadoRecepcionMaterial.PENDIENTE;
        }
        boolean todosCompletos = recepcion.getItems().stream()
                .allMatch(item -> estadosItems.getOrDefault(item.getItemOrdenCompra().getId(), EstadoRecepcionMaterial.PENDIENTE) == EstadoRecepcionMaterial.COMPLETO);
        return todosCompletos ? EstadoRecepcionMaterial.COMPLETO : EstadoRecepcionMaterial.PARCIAL;
    }

    private boolean esOrigenEntregas(String origen) {
        return "entregas".equalsIgnoreCase(origen);
    }

    private String sufijoOrigen(String origen) {
        return esOrigenEntregas(origen) ? "?origen=entregas" : "";
    }

    private ItemRecepcionMaterial obtenerItemRecepcionValido(Long ordenCompraId, Long recepcionId, Long itemRecepcionId) {
        ItemRecepcionMaterial itemRecepcion = materialService.obtenerItemRecepcion(itemRecepcionId);
        if (!itemRecepcion.getRecepcionMaterial().getId().equals(recepcionId)
                || !itemRecepcion.getRecepcionMaterial().getOrdenCompra().getId().equals(ordenCompraId)) {
            throw new IllegalArgumentException("El item no pertenece a esta recepcion.");
        }
        return itemRecepcion;
    }

    private void cargarIngresoDeposito(Model model,
                                       Long ordenCompraId,
                                       Long recepcionId,
                                       ItemRecepcionMaterial itemRecepcion,
                                       IngresoDepositoRecepcionForm form,
                                       String origen) {
        model.addAttribute("orden", ordenCompraService.obtener(ordenCompraId));
        model.addAttribute("recepcion", itemRecepcion.getRecepcionMaterial());
        model.addAttribute("itemRecepcion", itemRecepcion);
        model.addAttribute("form", form);
        model.addAttribute("insumosDeposito", depositoService.listarItemsActivos());
        model.addAttribute("origen", origen);
        model.addAttribute("desdeModuloEntregas", esOrigenEntregas(origen));
    }

    private DepositoItem obtenerOCrearInsumoDeposito(IngresoDepositoRecepcionForm form, ItemRecepcionMaterial itemRecepcion) {
        if (form.getDepositoItemId() != null) {
            return depositoService.obtener(form.getDepositoItemId());
        }
        DepositoItem nuevo = new DepositoItem();
        nuevo.setNombre(form.getNuevoInsumoNombre());
        nuevo.setCategoria(form.getCategoria());
        nuevo.setUbicacion(form.getUbicacion());
        nuevo.setStockMinimo(form.getStockMinimo());
        nuevo.setUnidad(itemRecepcion.getItemOrdenCompra().getUnidad());
        nuevo.setTipo(TipoInsumoDeposito.CONSUMIBLE);
        nuevo.setObservacion("Creado desde recepcion OC " + itemRecepcion.getRecepcionMaterial().getOrdenCompra().getNumero());
        return depositoService.guardarItem(nuevo);
    }

    private String nombreSugeridoDeposito(ItemRecepcionMaterial itemRecepcion) {
        if (itemRecepcion.getItemOrdenCompra().getMaterialCatalogo() != null
                && itemRecepcion.getItemOrdenCompra().getMaterialCatalogo().getNombre() != null) {
            return itemRecepcion.getItemOrdenCompra().getMaterialCatalogo().getNombre();
        }
        return itemRecepcion.getItemOrdenCompra().getDetalle();
    }
}
