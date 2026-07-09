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
import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private final OrdenCompraService ordenCompraService;
    private final ItemOrdenCompraRepository itemOrdenCompraRepository;
    private final RecepcionMaterialRepository recepcionMaterialRepository;
    private final ItemRecepcionMaterialRepository itemRecepcionMaterialRepository;

    @Transactional(readOnly = true)
    public List<OrdenCompra> listarOrdenesConMateriales() {
        return ordenCompraService.listarPorTipoCategoria(CategoriaItem.MATERIAL);
    }

    @Transactional(readOnly = true)
    public RecepcionMaterial obtenerRecepcion(Long id) {
        return recepcionMaterialRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la recepcion " + id));
    }

    @Transactional(readOnly = true)
    public List<RecepcionMaterial> listarRecepciones(Long ordenCompraId) {
        return recepcionMaterialRepository.findByOrdenCompraIdOrderByFechaAscIdAsc(ordenCompraId);
    }

    @Transactional(readOnly = true)
    public RecepcionMaterialForm crearForm(Long ordenCompraId) {
        RecepcionMaterialForm form = new RecepcionMaterialForm();
        itemOrdenCompraRepository.findByOrdenCompraIdAndCategoriaOrderById(ordenCompraId, CategoriaItem.MATERIAL)
                .forEach(item -> {
                    ItemRecepcionMaterialForm itemForm = new ItemRecepcionMaterialForm();
                    itemForm.setItemOrdenCompraId(item.getId());
                    itemForm.setCantidadRecibida(BigDecimal.ZERO);
                    form.getItems().add(itemForm);
                });
        return form;
    }

    @Transactional
    public RecepcionMaterial guardar(Long ordenCompraId, RecepcionMaterialForm form) {
        OrdenCompra ordenCompra = ordenCompraService.obtener(ordenCompraId);
        validarRecepcion(ordenCompraId, form);

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
    public void eliminarRecepcion(Long ordenCompraId, Long recepcionId) {
        RecepcionMaterial recepcion = obtenerRecepcion(recepcionId);
        if (!recepcion.getOrdenCompra().getId().equals(ordenCompraId)) {
            throw new IllegalArgumentException("La recepcion no pertenece a esta orden de compra.");
        }
        recepcionMaterialRepository.delete(recepcion);
    }

    @Transactional(readOnly = true)
    public List<ItemMaterialResumen> calcularResumenItems(Long ordenCompraId) {
        return itemOrdenCompraRepository.findByOrdenCompraIdAndCategoriaOrderById(ordenCompraId, CategoriaItem.MATERIAL)
                .stream()
                .map(item -> {
                    BigDecimal comprada = cantidadSegura(item.getCantidad());
                    BigDecimal recibida = cantidadRecibidaItem(ordenCompraId, item.getId());
                    BigDecimal pendiente = comprada.subtract(recibida).max(BigDecimal.ZERO);
                    return new ItemMaterialResumen(item, comprada, recibida, pendiente, calcularEstado(comprada, recibida));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal cantidadRecibidaItem(Long ordenCompraId, Long itemOrdenCompraId) {
        return itemRecepcionMaterialRepository.findByRecepcionMaterialOrdenCompraIdOrderByRecepcionMaterialFechaAscRecepcionMaterialIdAsc(ordenCompraId)
                .stream()
                .filter(item -> item.getItemOrdenCompra().getId().equals(itemOrdenCompraId))
                .map(item -> cantidadSegura(item.getCantidadRecibida()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validarRecepcion(Long ordenCompraId, RecepcionMaterialForm form) {
        if (form.getFecha() == null) {
            throw new IllegalArgumentException("La fecha de recepcion es obligatoria.");
        }
        if (!StringUtils.hasText(form.getRemito())) {
            throw new IllegalArgumentException("El remito es obligatorio.");
        }
        for (ItemRecepcionMaterialForm itemForm : form.getItems()) {
            ItemOrdenCompra itemOrdenCompra = itemOrdenCompraRepository.findById(itemForm.getItemOrdenCompraId())
                    .orElseThrow(() -> new EntityNotFoundException("No existe el item " + itemForm.getItemOrdenCompraId()));
            if (!itemOrdenCompra.getOrdenCompra().getId().equals(ordenCompraId)) {
                throw new IllegalArgumentException("El item no pertenece a esta orden de compra.");
            }
            if (itemOrdenCompra.getCategoria() != CategoriaItem.MATERIAL) {
                throw new IllegalArgumentException("Solo se pueden recibir items de materiales.");
            }
            BigDecimal cantidad = cantidadSegura(itemForm.getCantidadRecibida());
            if (cantidad.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("La cantidad recibida no puede ser negativa.");
            }
            BigDecimal yaRecibida = cantidadRecibidaItem(ordenCompraId, itemOrdenCompra.getId());
            BigDecimal nuevaRecepcion = yaRecibida.add(cantidad);
            if (nuevaRecepcion.compareTo(cantidadSegura(itemOrdenCompra.getCantidad())) > 0) {
                throw new IllegalArgumentException("La recepcion supera la cantidad comprada del item " + itemOrdenCompra.getItem() + ".");
            }
        }
    }

    private BigDecimal cantidadSegura(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
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
