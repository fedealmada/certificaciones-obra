package com.obra.certificaciones.material.service;

import com.obra.certificaciones.material.dto.EstadoRecepcionMaterial;
import com.obra.certificaciones.material.dto.ItemMaterialResumen;
import com.obra.certificaciones.material.dto.ItemRecepcionMaterialForm;
import com.obra.certificaciones.material.dto.RecepcionMaterialForm;
import com.obra.certificaciones.material.entity.ItemRecepcionMaterial;
import com.obra.certificaciones.material.entity.RecepcionMaterial;
import com.obra.certificaciones.material.repository.ItemRecepcionMaterialRepository;
import com.obra.certificaciones.material.repository.RecepcionMaterialRepository;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.repository.ItemOrdenCompraRepository;
import com.obra.certificaciones.oc.service.OrdenCompraService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private final OrdenCompraService ordenCompraService;
    private final ItemOrdenCompraRepository itemOrdenCompraRepository;
    private final RecepcionMaterialRepository recepcionMaterialRepository;
    private final ItemRecepcionMaterialRepository itemRecepcionMaterialRepository;

    @Transactional(readOnly = true)
    public List<OrdenCompra> listarOrdenesConMateriales() {
        return ordenCompraService.listar(null, null, null).stream()
                .filter(OrdenCompra::usaSeguimientoEntregas)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecepcionMaterial obtenerRecepcion(Long id) {
        return recepcionMaterialRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la recepcion " + id));
    }

    @Transactional(readOnly = true)
    public ItemRecepcionMaterial obtenerItemRecepcion(Long id) {
        return itemRecepcionMaterialRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el item de recepcion " + id));
    }

    @Transactional(readOnly = true)
    public List<RecepcionMaterial> listarRecepciones(Long ordenCompraId) {
        return recepcionMaterialRepository.findByOrdenCompraIdOrderByFechaAscIdAsc(ordenCompraId);
    }

    @Transactional(readOnly = true)
    public long contarRecepciones(Long ordenCompraId) {
        return recepcionMaterialRepository.countByOrdenCompraId(ordenCompraId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> contarRecepcionesPorOrdenes(List<Long> ordenCompraIds) {
        if (ordenCompraIds == null || ordenCompraIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> conteos = new HashMap<>();
        recepcionMaterialRepository.countByOrdenCompraIds(ordenCompraIds)
                .forEach(fila -> conteos.put((Long) fila[0], (Long) fila[1]));
        return conteos;
    }

    @Transactional(readOnly = true)
    public EstadoRecepcionMaterial calcularEstadoOrden(Long ordenCompraId) {
        List<ItemMaterialResumen> resumenItems = calcularResumenItems(ordenCompraId);
        return calcularEstadoOrden(resumenItems);
    }

    @Transactional(readOnly = true)
    public Map<Long, EstadoRecepcionMaterial> calcularEstadosOrdenes(List<OrdenCompra> ordenes) {
        if (ordenes == null || ordenes.isEmpty()) {
            return Map.of();
        }
        List<Long> ordenCompraIds = ordenes.stream()
                .map(OrdenCompra::getId)
                .toList();
        Map<Long, Map<Long, BigDecimal>> recibidasPorOrden = cantidadesRecibidasPorOrdenes(ordenCompraIds);
        Map<Long, EstadoRecepcionMaterial> estados = new HashMap<>();
        for (OrdenCompra orden : ordenes) {
            if (!orden.usaSeguimientoEntregas()) {
                continue;
            }
            estados.put(orden.getId(), calcularEstadoOrden(resumenItems(orden.getItems(), recibidasPorOrden.getOrDefault(orden.getId(), Map.of()))));
        }
        return estados;
    }

    private EstadoRecepcionMaterial calcularEstadoOrden(List<ItemMaterialResumen> resumenItems) {
        if (resumenItems.isEmpty() || resumenItems.stream().allMatch(item -> item.estado() == EstadoRecepcionMaterial.PENDIENTE)) {
            return EstadoRecepcionMaterial.PENDIENTE;
        }
        if (resumenItems.stream().allMatch(item -> item.estado() == EstadoRecepcionMaterial.COMPLETO)) {
            return EstadoRecepcionMaterial.COMPLETO;
        }
        return EstadoRecepcionMaterial.PARCIAL;
    }

    @Transactional(readOnly = true)
    public RecepcionMaterialForm crearForm(Long ordenCompraId) {
        RecepcionMaterialForm form = new RecepcionMaterialForm();
        OrdenCompra ordenCompra = ordenCompraService.obtener(ordenCompraId);
        validarOrdenConEntregas(ordenCompra);
        form.setFecha(fechaOrden(ordenCompra));
        itemsRecepcionables(ordenCompra).forEach(item -> {
            ItemRecepcionMaterialForm itemForm = new ItemRecepcionMaterialForm();
            itemForm.setItemOrdenCompraId(item.getId());
            itemForm.setCantidadRecibida(BigDecimal.ZERO);
            form.getItems().add(itemForm);
        });
        return form;
    }

    @Transactional(readOnly = true)
    public RecepcionMaterialForm crearFormEdicion(Long ordenCompraId, Long recepcionId) {
        RecepcionMaterial recepcion = obtenerRecepcion(recepcionId);
        validarRecepcionDeOrden(ordenCompraId, recepcion);
        validarOrdenConEntregas(recepcion.getOrdenCompra());

        Map<Long, BigDecimal> cantidadesActuales = new HashMap<>();
        recepcion.getItems().forEach(item ->
                cantidadesActuales.put(item.getItemOrdenCompra().getId(), cantidadSegura(item.getCantidadRecibida())));

        RecepcionMaterialForm form = new RecepcionMaterialForm();
        form.setFecha(recepcion.getFecha());
        form.setRemito(recepcion.getRemito());
        form.setObservacion(recepcion.getObservacion());
        itemsRecepcionables(recepcion.getOrdenCompra()).forEach(item -> {
            ItemRecepcionMaterialForm itemForm = new ItemRecepcionMaterialForm();
            itemForm.setItemOrdenCompraId(item.getId());
            itemForm.setCantidadRecibida(cantidadesActuales.getOrDefault(item.getId(), BigDecimal.ZERO));
            form.getItems().add(itemForm);
        });
        return form;
    }

    @Transactional
    public RecepcionMaterial guardar(Long ordenCompraId, RecepcionMaterialForm form) {
        OrdenCompra ordenCompra = ordenCompraService.obtener(ordenCompraId);
        validarOrdenConEntregas(ordenCompra);
        validarRecepcion(ordenCompraId, form, null);

        RecepcionMaterial recepcion = new RecepcionMaterial();
        recepcion.setOrdenCompra(ordenCompra);
        recepcion.setFecha(form.getFecha());
        recepcion.setRemito(form.getRemito());
        recepcion.setObservacion(form.getObservacion());

        for (ItemRecepcionMaterialForm itemForm : form.getItems()) {
            BigDecimal cantidad = cantidadSegura(itemForm.getCantidadRecibida());
            if (cantidad.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            ItemOrdenCompra itemOrdenCompra = itemOrdenCompraRepository.findById(itemForm.getItemOrdenCompraId())
                    .orElseThrow(() -> new EntityNotFoundException("No existe el item " + itemForm.getItemOrdenCompraId()));
            ItemRecepcionMaterial itemRecepcion = new ItemRecepcionMaterial();
            itemRecepcion.setItemOrdenCompra(itemOrdenCompra);
            itemRecepcion.setCantidadRecibida(cantidad);
            recepcion.agregarItem(itemRecepcion);
        }
        if (recepcion.getItems().isEmpty()) {
            throw new IllegalArgumentException("Debe cargar al menos una cantidad recibida mayor a 0.");
        }
        return recepcionMaterialRepository.save(recepcion);
    }

    @Transactional
    public RecepcionMaterial actualizar(Long ordenCompraId, Long recepcionId, RecepcionMaterialForm form) {
        RecepcionMaterial recepcion = obtenerRecepcion(recepcionId);
        validarRecepcionDeOrden(ordenCompraId, recepcion);
        OrdenCompra ordenCompra = ordenCompraService.obtener(ordenCompraId);
        validarOrdenConEntregas(ordenCompra);
        validarRecepcion(ordenCompraId, form, recepcionId);

        recepcion.setFecha(form.getFecha());
        recepcion.setRemito(form.getRemito());
        recepcion.setObservacion(form.getObservacion());
        recepcion.getItems().clear();

        for (ItemRecepcionMaterialForm itemForm : form.getItems()) {
            BigDecimal cantidad = cantidadSegura(itemForm.getCantidadRecibida());
            if (cantidad.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            ItemOrdenCompra itemOrdenCompra = itemOrdenCompraRepository.findById(itemForm.getItemOrdenCompraId())
                    .orElseThrow(() -> new EntityNotFoundException("No existe el item " + itemForm.getItemOrdenCompraId()));
            ItemRecepcionMaterial itemRecepcion = new ItemRecepcionMaterial();
            itemRecepcion.setItemOrdenCompra(itemOrdenCompra);
            itemRecepcion.setCantidadRecibida(cantidad);
            recepcion.agregarItem(itemRecepcion);
        }
        if (recepcion.getItems().isEmpty()) {
            throw new IllegalArgumentException("Debe cargar al menos una cantidad recibida mayor a 0.");
        }
        return recepcionMaterialRepository.save(recepcion);
    }

    @Transactional
    public void eliminarRecepcion(Long ordenCompraId, Long recepcionId) {
        RecepcionMaterial recepcion = obtenerRecepcion(recepcionId);
        if (!recepcion.getOrdenCompra().getId().equals(ordenCompraId)) {
            throw new IllegalArgumentException("La recepcion no pertenece a esta orden de compra.");
        }
        recepcionMaterialRepository.delete(recepcion);
    }

    @Transactional(readOnly = true)
    public List<ItemMaterialResumen> calcularResumenItems(Long ordenCompraId) {
        OrdenCompra ordenCompra = ordenCompraService.obtener(ordenCompraId);
        if (!ordenCompra.usaSeguimientoEntregas()) {
            return List.of();
        }
        return resumenItems(itemsRecepcionables(ordenCompra), cantidadesRecibidasPorItem(ordenCompraId));
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ItemMaterialResumen>> calcularResumenOrdenes(List<OrdenCompra> ordenes) {
        if (ordenes == null || ordenes.isEmpty()) {
            return Map.of();
        }
        List<Long> ordenCompraIds = ordenes.stream()
                .filter(OrdenCompra::usaSeguimientoEntregas)
                .map(OrdenCompra::getId)
                .toList();
        Map<Long, Map<Long, BigDecimal>> recibidasPorOrden = cantidadesRecibidasPorOrdenes(ordenCompraIds);
        Map<Long, List<ItemMaterialResumen>> resumenes = new HashMap<>();
        for (OrdenCompra orden : ordenes) {
            if (!orden.usaSeguimientoEntregas()) {
                continue;
            }
            resumenes.put(orden.getId(), resumenItems(itemsRecepcionables(orden), recibidasPorOrden.getOrDefault(orden.getId(), Map.of())));
        }
        return resumenes;
    }

    @Transactional(readOnly = true)
    public List<ItemMaterialResumen> calcularResumenItemsParaEdicion(Long ordenCompraId, Long recepcionId) {
        OrdenCompra ordenCompra = ordenCompraService.obtener(ordenCompraId);
        if (!ordenCompra.usaSeguimientoEntregas()) {
            return List.of();
        }
        return resumenItems(itemsRecepcionables(ordenCompra), cantidadesRecibidasPorItemExcluyendoRecepcion(ordenCompraId, recepcionId));
    }

    @Transactional(readOnly = true)
    public BigDecimal cantidadRecibidaItem(Long ordenCompraId, Long itemOrdenCompraId) {
        return cantidadesRecibidasPorItem(ordenCompraId).getOrDefault(itemOrdenCompraId, BigDecimal.ZERO);
    }

    private List<ItemMaterialResumen> resumenItems(List<ItemOrdenCompra> items, Map<Long, BigDecimal> recibidasPorItem) {
        return items.stream()
                .filter(this::esItemRecepcionable)
                .map(item -> {
                    BigDecimal comprada = cantidadSegura(item.getCantidad());
                    BigDecimal recibida = recibidasPorItem.getOrDefault(item.getId(), BigDecimal.ZERO);
                    BigDecimal pendiente = comprada.subtract(recibida).max(BigDecimal.ZERO);
                    return new ItemMaterialResumen(item, comprada, recibida, pendiente, calcularEstado(comprada, recibida));
                })
                .toList();
    }

    private Map<Long, BigDecimal> cantidadesRecibidasPorItem(Long ordenCompraId) {
        return cantidadesRecibidasPorOrdenes(List.of(ordenCompraId)).getOrDefault(ordenCompraId, Map.of());
    }

    private Map<Long, Map<Long, BigDecimal>> cantidadesRecibidasPorOrdenes(List<Long> ordenCompraIds) {
        if (ordenCompraIds == null || ordenCompraIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, BigDecimal>> recibidas = new HashMap<>();
        for (Object[] fila : itemRecepcionMaterialRepository.sumCantidadesByOrdenCompraIds(ordenCompraIds)) {
            Long ordenId = (Long) fila[0];
            Long itemId = (Long) fila[1];
            BigDecimal cantidad = fila[2] == null ? BigDecimal.ZERO : (BigDecimal) fila[2];
            recibidas.computeIfAbsent(ordenId, id -> new HashMap<>()).put(itemId, cantidad);
        }
        return recibidas;
    }

    private void validarRecepcion(Long ordenCompraId, RecepcionMaterialForm form, Long recepcionIgnoradaId) {
        if (form.getFecha() == null) {
            throw new IllegalArgumentException("La fecha de recepcion es obligatoria.");
        }
        Map<Long, BigDecimal> recibidasSinRecepcionActual = cantidadesRecibidasPorItemExcluyendoRecepcion(ordenCompraId, recepcionIgnoradaId);
        for (ItemRecepcionMaterialForm itemForm : form.getItems()) {
            ItemOrdenCompra itemOrdenCompra = itemOrdenCompraRepository.findById(itemForm.getItemOrdenCompraId())
                    .orElseThrow(() -> new EntityNotFoundException("No existe el item " + itemForm.getItemOrdenCompraId()));
            if (!itemOrdenCompra.getOrdenCompra().getId().equals(ordenCompraId)) {
                throw new IllegalArgumentException("El item no pertenece a esta orden de compra.");
            }
            if (!itemOrdenCompra.getOrdenCompra().usaSeguimientoEntregas()) {
                throw new IllegalArgumentException("Esta orden de compra no esta configurada para entregas o viajes.");
            }
            if (!esItemRecepcionable(itemOrdenCompra)) {
                throw new IllegalArgumentException("Solo se pueden recibir items que no sean mano de obra.");
            }
            BigDecimal cantidad = cantidadSegura(itemForm.getCantidadRecibida());
            if (cantidad.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("La cantidad recibida no puede ser negativa.");
            }
            BigDecimal yaRecibida = recibidasSinRecepcionActual.getOrDefault(itemOrdenCompra.getId(), BigDecimal.ZERO);
            BigDecimal nuevaRecepcion = yaRecibida.add(cantidad);
            if (nuevaRecepcion.compareTo(cantidadSegura(itemOrdenCompra.getCantidad())) > 0) {
                throw new IllegalArgumentException("La recepcion supera la cantidad comprada del item " + itemOrdenCompra.getItem() + ".");
            }
        }
    }

    private Map<Long, BigDecimal> cantidadesRecibidasPorItemExcluyendoRecepcion(Long ordenCompraId, Long recepcionIgnoradaId) {
        Map<Long, BigDecimal> recibidas = new HashMap<>(cantidadesRecibidasPorItem(ordenCompraId));
        if (recepcionIgnoradaId == null) {
            return recibidas;
        }
        RecepcionMaterial recepcion = obtenerRecepcion(recepcionIgnoradaId);
        if (!recepcion.getOrdenCompra().getId().equals(ordenCompraId)) {
            return recibidas;
        }
        for (ItemRecepcionMaterial item : recepcion.getItems()) {
            Long itemId = item.getItemOrdenCompra().getId();
            BigDecimal restante = recibidas.getOrDefault(itemId, BigDecimal.ZERO).subtract(cantidadSegura(item.getCantidadRecibida()));
            recibidas.put(itemId, restante.max(BigDecimal.ZERO));
        }
        return recibidas;
    }

    private BigDecimal cantidadSegura(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private LocalDate fechaOrden(OrdenCompra ordenCompra) {
        return ordenCompra.getFecha() == null ? LocalDate.now() : ordenCompra.getFecha();
    }

    private void validarOrdenConEntregas(OrdenCompra ordenCompra) {
        if (!ordenCompra.usaSeguimientoEntregas()) {
            throw new IllegalArgumentException("Esta orden de compra no esta configurada para entregas o viajes.");
        }
    }

    private void validarRecepcionDeOrden(Long ordenCompraId, RecepcionMaterial recepcion) {
        if (!recepcion.getOrdenCompra().getId().equals(ordenCompraId)) {
            throw new IllegalArgumentException("La recepcion no pertenece a esta orden de compra.");
        }
    }

    private List<ItemOrdenCompra> itemsRecepcionables(OrdenCompra ordenCompra) {
        return ordenCompra.getItems().stream()
                .filter(this::esItemRecepcionable)
                .toList();
    }

    private boolean esItemRecepcionable(ItemOrdenCompra item) {
        return item.getCategoria() != CategoriaItem.MANO_OBRA;
    }

    private EstadoRecepcionMaterial calcularEstado(BigDecimal comprada, BigDecimal recibida) {
        if (recibida.compareTo(BigDecimal.ZERO) <= 0) {
            return EstadoRecepcionMaterial.PENDIENTE;
        }
        if (recibida.compareTo(comprada) >= 0) {
            return EstadoRecepcionMaterial.COMPLETO;
        }
        return EstadoRecepcionMaterial.PARCIAL;
    }
}
