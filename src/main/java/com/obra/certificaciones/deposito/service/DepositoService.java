package com.obra.certificaciones.deposito.service;

import com.obra.certificaciones.deposito.dto.MovimientoDepositoForm;
import com.obra.certificaciones.deposito.entity.DepositoItem;
import com.obra.certificaciones.deposito.entity.MovimientoDeposito;
import com.obra.certificaciones.deposito.entity.TipoInsumoDeposito;
import com.obra.certificaciones.deposito.entity.TipoMovimientoDeposito;
import com.obra.certificaciones.deposito.repository.DepositoItemRepository;
import com.obra.certificaciones.deposito.repository.MovimientoDepositoRepository;
import com.obra.certificaciones.material.entity.ItemRecepcionMaterial;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepositoService {
    private final DepositoItemRepository itemRepository;
    private final MovimientoDepositoRepository movimientoRepository;

    @Transactional(readOnly = true)
    public List<DepositoItem> listarItems() {
        return itemRepository.findAllByOrderByActivoDescNombreAsc();
    }

    @Transactional(readOnly = true)
    public List<DepositoItem> listarItemsActivos() {
        return itemRepository.findAllByOrderByActivoDescNombreAsc().stream()
                .filter(DepositoItem::isActivo)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MovimientoDeposito> movimientosRecientes() {
        return movimientoRepository.findTop12ByOrderByFechaDescIdDesc();
    }

    @Transactional(readOnly = true)
    public List<MovimientoDeposito> movimientosItem(Long itemId) {
        return movimientoRepository.findTop80ByItemIdOrderByFechaDescIdDesc(itemId);
    }

    @Transactional(readOnly = true)
    public DepositoItem obtener(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el insumo de deposito " + id));
    }

    @Transactional
    public DepositoItem guardarItem(DepositoItem item) {
        validarItem(item);
        if (item.getId() == null) {
            item.setStockActual(BigDecimal.ZERO);
            return itemRepository.save(item);
        }
        DepositoItem existente = obtener(item.getId());
        existente.setNombre(item.getNombre());
        existente.setCategoria(item.getCategoria());
        existente.setUnidad(item.getUnidad());
        existente.setUbicacion(item.getUbicacion());
        existente.setStockMinimo(valorSeguro(item.getStockMinimo()));
        existente.setTipo(item.getTipo() == null ? TipoInsumoDeposito.CONSUMIBLE : item.getTipo());
        existente.setObservacion(item.getObservacion());
        existente.setActivo(item.isActivo());
        return itemRepository.save(existente);
    }

    @Transactional
    public void eliminarItem(Long id) {
        DepositoItem item = obtener(id);
        if (movimientoRepository.existsByItemId(id)) {
            item.setActivo(false);
            itemRepository.save(item);
            return;
        }
        itemRepository.delete(item);
    }

    @Transactional
    public MovimientoDeposito registrarMovimiento(Long itemId, MovimientoDepositoForm form) {
        DepositoItem item = obtener(itemId);
        validarMovimiento(item, form);

        BigDecimal anterior = valorSeguro(item.getStockActual());
        BigDecimal cantidad = valorSeguro(form.getCantidad());
        BigDecimal resultante = calcularStockResultante(anterior, form.getTipo(), cantidad);

        MovimientoDeposito movimiento = new MovimientoDeposito();
        movimiento.setItem(item);
        movimiento.setFecha(form.getFecha() == null ? LocalDate.now() : form.getFecha());
        movimiento.setTipo(form.getTipo());
        movimiento.setCantidad(cantidad);
        movimiento.setStockAnterior(anterior);
        movimiento.setStockResultante(resultante);
        movimiento.setResponsable(form.getResponsable());
        movimiento.setDestino(form.getDestino());
        movimiento.setObservacion(form.getObservacion());

        item.setStockActual(resultante);
        itemRepository.save(item);
        return movimientoRepository.save(movimiento);
    }

    @Transactional
    public MovimientoDeposito registrarEntradaDesdeRecepcion(ItemRecepcionMaterial itemRecepcion,
                                                            DepositoItem itemDeposito,
                                                            BigDecimal cantidad,
                                                            String responsable,
                                                            String observacion) {
        if (itemRecepcion == null || itemRecepcion.getRecepcionMaterial() == null) {
            throw new IllegalArgumentException("No se pudo identificar la recepcion de origen.");
        }
        if (itemDeposito == null) {
            throw new IllegalArgumentException("Debe seleccionar o crear un insumo de deposito.");
        }
        BigDecimal cantidadSegura = valorSeguro(cantidad);
        if (cantidadSegura.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad a ingresar al deposito debe ser mayor a 0.");
        }
        BigDecimal yaIngresado = movimientoRepository.sumCantidadByItemRecepcionMaterialIdAndTipo(itemRecepcion.getId(), TipoMovimientoDeposito.ENTRADA);
        if (valorSeguro(itemRecepcion.getCantidadRecibida()).compareTo(valorSeguro(yaIngresado).add(cantidadSegura)) < 0) {
            throw new IllegalArgumentException("No se puede ingresar al deposito mas cantidad que la recibida en este viaje.");
        }
        if (!itemDeposito.isActivo()) {
            throw new IllegalArgumentException("No se puede ingresar stock a un insumo inactivo.");
        }

        BigDecimal anterior = valorSeguro(itemDeposito.getStockActual());
        BigDecimal resultante = anterior.add(cantidadSegura);
        MovimientoDeposito movimiento = new MovimientoDeposito();
        movimiento.setItem(itemDeposito);
        movimiento.setFecha(itemRecepcion.getRecepcionMaterial().getFecha());
        movimiento.setTipo(TipoMovimientoDeposito.ENTRADA);
        movimiento.setCantidad(cantidadSegura);
        movimiento.setStockAnterior(anterior);
        movimiento.setStockResultante(resultante);
        movimiento.setResponsable(responsable);
        movimiento.setDestino("OC " + itemRecepcion.getRecepcionMaterial().getOrdenCompra().getNumero());
        movimiento.setObservacion(observacion);
        movimiento.setOrdenCompraId(itemRecepcion.getRecepcionMaterial().getOrdenCompra().getId());
        movimiento.setRecepcionMaterialId(itemRecepcion.getRecepcionMaterial().getId());
        movimiento.setItemRecepcionMaterialId(itemRecepcion.getId());
        movimiento.setOrdenCompraNumero(itemRecepcion.getRecepcionMaterial().getOrdenCompra().getNumero());

        itemDeposito.setStockActual(resultante);
        itemRepository.save(itemDeposito);
        return movimientoRepository.save(movimiento);
    }

    @Transactional(readOnly = true)
    public long contarBajoStock() {
        return listarItems().stream()
                .filter(DepositoItem::isActivo)
                .filter(DepositoItem::bajoStock)
                .count();
    }

    @Transactional(readOnly = true)
    public BigDecimal totalUnidades() {
        return listarItems().stream()
                .filter(DepositoItem::isActivo)
                .map(item -> valorSeguro(item.getStockActual()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validarItem(DepositoItem item) {
        if (!StringUtils.hasText(item.getNombre())) {
            throw new IllegalArgumentException("El nombre del insumo es obligatorio.");
        }
        boolean duplicado = item.getId() == null
                ? itemRepository.existsByNombreIgnoreCase(item.getNombre())
                : itemRepository.existsByNombreIgnoreCaseAndIdNot(item.getNombre(), item.getId());
        if (duplicado) {
            throw new IllegalArgumentException("Ya existe un insumo con ese nombre en deposito.");
        }
        if (valorSeguro(item.getStockMinimo()).compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El stock minimo no puede ser negativo.");
        }
    }

    private void validarMovimiento(DepositoItem item, MovimientoDepositoForm form) {
        if (!item.isActivo()) {
            throw new IllegalArgumentException("No se pueden registrar movimientos en un insumo inactivo.");
        }
        if (form.getTipo() == null) {
            throw new IllegalArgumentException("El tipo de movimiento es obligatorio.");
        }
        if (form.getFecha() == null) {
            throw new IllegalArgumentException("La fecha del movimiento es obligatoria.");
        }
        if (valorSeguro(form.getCantidad()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0.");
        }
        calcularStockResultante(valorSeguro(item.getStockActual()), form.getTipo(), valorSeguro(form.getCantidad()));
    }

    private BigDecimal calcularStockResultante(BigDecimal anterior, TipoMovimientoDeposito tipo, BigDecimal cantidad) {
        BigDecimal resultante = switch (tipo) {
            case ENTRADA, DEVOLUCION -> anterior.add(cantidad);
            case SALIDA -> anterior.subtract(cantidad);
            case AJUSTE -> cantidad;
        };
        if (resultante.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("La salida supera el stock disponible.");
        }
        return resultante;
    }

    private BigDecimal valorSeguro(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
