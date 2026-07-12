package com.obra.certificaciones.deposito.service;

import com.obra.certificaciones.deposito.dto.MovimientoDepositoForm;
import com.obra.certificaciones.deposito.entity.DepositoItem;
import com.obra.certificaciones.deposito.entity.DepositoTrabajador;
import com.obra.certificaciones.deposito.entity.MovimientoDeposito;
import com.obra.certificaciones.deposito.entity.TipoInsumoDeposito;
import com.obra.certificaciones.deposito.entity.TipoMovimientoDeposito;
import com.obra.certificaciones.deposito.repository.DepositoItemRepository;
import com.obra.certificaciones.deposito.repository.DepositoTrabajadorRepository;
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
    private final DepositoTrabajadorRepository trabajadorRepository;

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
    public List<DepositoTrabajador> listarTrabajadoresActivos() {
        return trabajadorRepository.findByActivoTrueOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public List<DepositoTrabajador> listarTrabajadores() {
        return trabajadorRepository.findAllByOrderByActivoDescNombreAsc();
    }

    @Transactional(readOnly = true)
    public DepositoTrabajador obtenerTrabajador(Long id) {
        return trabajadorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la persona de deposito " + id));
    }

    @Transactional
    public DepositoTrabajador guardarTrabajador(DepositoTrabajador trabajador) {
        validarTrabajador(trabajador);
        if (trabajador.getId() == null) {
            trabajador.setNombre(trabajador.getNombre().trim());
            return trabajadorRepository.save(trabajador);
        }
        DepositoTrabajador existente = obtenerTrabajador(trabajador.getId());
        existente.setNombre(trabajador.getNombre().trim());
        existente.setSector(trabajador.getSector());
        existente.setEmpresa(trabajador.getEmpresa());
        existente.setActivo(trabajador.isActivo());
        return trabajadorRepository.save(existente);
    }

    @Transactional
    public void desactivarTrabajador(Long id) {
        DepositoTrabajador trabajador = obtenerTrabajador(id);
        trabajador.setActivo(false);
        trabajadorRepository.save(trabajador);
    }

    @Transactional(readOnly = true)
    public List<MovimientoDeposito> devolucionesPendientes() {
        return movimientoRepository.findByTipoAndRequiereDevolucionTrueAndDevueltoFalseOrderByFechaAscIdAsc(TipoMovimientoDeposito.SALIDA);
    }

    @Transactional(readOnly = true)
    public DepositoItem obtener(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el insumo de deposito " + id));
    }

    @Transactional(readOnly = true)
    public MovimientoDeposito obtenerMovimiento(Long id) {
        return movimientoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el movimiento de deposito " + id));
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
        movimiento.setStockAnterior(anterior);
        movimiento.setStockResultante(resultante);
        aplicarDatosMovimiento(movimiento, form, cantidad);

        movimientoRepository.save(movimiento);
        recalcularStockItem(item);
        return movimiento;
    }

    @Transactional
    public MovimientoDeposito actualizarMovimiento(Long movimientoId, MovimientoDepositoForm form) {
        MovimientoDeposito movimiento = obtenerMovimiento(movimientoId);
        if (movimiento.getOrdenCompraId() != null || movimiento.getMovimientoOrigenId() != null) {
            throw new IllegalArgumentException("Este movimiento esta vinculado a otro proceso y no puede editarse desde aqui.");
        }
        DepositoItem item = movimiento.getItem();
        validarDatosMovimiento(item, form);
        BigDecimal cantidad = valorSeguro(form.getCantidad());

        aplicarDatosMovimiento(movimiento, form, cantidad);
        movimientoRepository.save(movimiento);
        recalcularStockItem(item);
        return movimiento;
    }

    @Transactional
    public MovimientoDeposito registrarDevolucion(Long movimientoSalidaId, MovimientoDepositoForm form) {
        MovimientoDeposito salida = obtenerMovimiento(movimientoSalidaId);
        if (salida.getTipo() != TipoMovimientoDeposito.SALIDA || !salida.isRequiereDevolucion()) {
            throw new IllegalArgumentException("El movimiento seleccionado no tiene devolucion pendiente.");
        }
        if (salida.isDevuelto()) {
            throw new IllegalArgumentException("Este movimiento ya fue devuelto.");
        }
        form.setTipo(TipoMovimientoDeposito.DEVOLUCION);
        form.setCantidad(salida.getCantidad());
        DepositoItem item = salida.getItem();
        validarMovimiento(item, form);
        BigDecimal anterior = valorSeguro(item.getStockActual());
        BigDecimal resultante = calcularStockResultante(anterior, TipoMovimientoDeposito.DEVOLUCION, valorSeguro(salida.getCantidad()));

        MovimientoDeposito devolucion = new MovimientoDeposito();
        devolucion.setItem(item);
        devolucion.setStockAnterior(anterior);
        devolucion.setStockResultante(resultante);
        aplicarDatosMovimiento(devolucion, form, valorSeguro(salida.getCantidad()));
        devolucion.setMovimientoOrigenId(salida.getId());
        devolucion.setTrabajadorId(salida.getTrabajadorId());
        devolucion.setTrabajadorNombre(salida.getTrabajadorNombre());
        devolucion.setResponsable(StringUtils.hasText(form.getResponsable()) ? form.getResponsable() : salida.getResponsable());
        devolucion.setDestino(salida.getDestino());

        salida.setDevuelto(true);
        movimientoRepository.save(salida);
        movimientoRepository.save(devolucion);
        recalcularStockItem(item);
        return devolucion;
    }

    @Transactional(readOnly = true)
    public MovimientoDepositoForm formDesdeMovimiento(MovimientoDeposito movimiento) {
        MovimientoDepositoForm form = new MovimientoDepositoForm();
        form.setFecha(movimiento.getFecha());
        form.setTipo(movimiento.getTipo());
        form.setCantidad(movimiento.getCantidad());
        form.setResponsable(movimiento.getResponsable());
        form.setTrabajadorId(movimiento.getTrabajadorId());
        form.setTrabajadorNombre(movimiento.getTrabajadorNombre());
        form.setDestino(movimiento.getDestino());
        form.setObservacion(movimiento.getObservacion());
        form.setRequiereDevolucion(movimiento.isRequiereDevolucion());
        return form;
    }

    @Transactional
    public MovimientoDeposito registrarEntradaDesdeRecepcion(ItemRecepcionMaterial itemRecepcion,
                                                            DepositoItem itemDeposito,
                                                            BigDecimal cantidad,
                                                            String responsable,
                                                            String destino,
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
        movimiento.setDestino(destino);
        movimiento.setObservacion(observacion);
        movimiento.setOrdenCompraId(itemRecepcion.getRecepcionMaterial().getOrdenCompra().getId());
        movimiento.setRecepcionMaterialId(itemRecepcion.getRecepcionMaterial().getId());
        movimiento.setItemRecepcionMaterialId(itemRecepcion.getId());
        movimiento.setOrdenCompraNumero(itemRecepcion.getRecepcionMaterial().getOrdenCompra().getNumero());

        movimientoRepository.save(movimiento);
        recalcularStockItem(itemDeposito);
        return movimiento;
    }

    @Transactional(readOnly = true)
    public long contarBajoStock() {
        return listarItems().stream()
                .filter(DepositoItem::isActivo)
                .filter(DepositoItem::bajoStock)
                .count();
    }

    @Transactional(readOnly = true)
    public List<DepositoItem> itemsBajoStock() {
        return listarItems().stream()
                .filter(DepositoItem::isActivo)
                .filter(DepositoItem::bajoStock)
                .toList();
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

    private void validarTrabajador(DepositoTrabajador trabajador) {
        if (!StringUtils.hasText(trabajador.getNombre())) {
            throw new IllegalArgumentException("El nombre de la persona es obligatorio.");
        }
        String nombre = trabajador.getNombre().trim();
        boolean duplicado = trabajador.getId() == null
                ? trabajadorRepository.existsByNombreIgnoreCase(nombre)
                : trabajadorRepository.existsByNombreIgnoreCaseAndIdNot(nombre, trabajador.getId());
        if (duplicado) {
            throw new IllegalArgumentException("Ya existe una persona con ese nombre.");
        }
    }

    private void validarMovimiento(DepositoItem item, MovimientoDepositoForm form) {
        validarMovimientoConStock(item, form, valorSeguro(item.getStockActual()));
    }

    private void validarMovimientoConStock(DepositoItem item, MovimientoDepositoForm form, BigDecimal stockBase) {
        validarDatosMovimiento(item, form);
        calcularStockResultante(stockBase, form.getTipo(), valorSeguro(form.getCantidad()));
    }

    private void validarDatosMovimiento(DepositoItem item, MovimientoDepositoForm form) {
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

    private void aplicarDatosMovimiento(MovimientoDeposito movimiento, MovimientoDepositoForm form, BigDecimal cantidad) {
        DepositoTrabajador trabajador = obtenerOCrearTrabajador(form);
        movimiento.setFecha(form.getFecha() == null ? LocalDate.now() : form.getFecha());
        movimiento.setTipo(form.getTipo());
        movimiento.setCantidad(cantidad);
        movimiento.setResponsable(form.getResponsable());
        movimiento.setTrabajadorId(trabajador == null ? null : trabajador.getId());
        movimiento.setTrabajadorNombre(trabajador == null ? form.getTrabajadorNombre() : trabajador.getNombre());
        movimiento.setDestino(form.getDestino());
        movimiento.setObservacion(form.getObservacion());
        movimiento.setRequiereDevolucion(form.getTipo() == TipoMovimientoDeposito.SALIDA && form.isRequiereDevolucion());
        if (form.getTipo() != TipoMovimientoDeposito.SALIDA) {
            movimiento.setRequiereDevolucion(false);
            movimiento.setDevuelto(false);
        }
    }

    private DepositoTrabajador obtenerOCrearTrabajador(MovimientoDepositoForm form) {
        if (form.getTrabajadorId() != null) {
            return trabajadorRepository.findById(form.getTrabajadorId()).orElse(null);
        }
        if (!StringUtils.hasText(form.getTrabajadorNombre())) {
            return null;
        }
        return trabajadorRepository.findByNombreIgnoreCase(form.getTrabajadorNombre().trim())
                .orElseGet(() -> {
                    DepositoTrabajador trabajador = new DepositoTrabajador();
                    trabajador.setNombre(form.getTrabajadorNombre().trim());
                    return trabajadorRepository.save(trabajador);
                });
    }

    private void recalcularStockItem(DepositoItem item) {
        BigDecimal stock = BigDecimal.ZERO;
        for (MovimientoDeposito movimiento : movimientoRepository.findByItemIdOrderByFechaAscIdAsc(item.getId())) {
            BigDecimal anterior = stock;
            stock = calcularStockResultante(stock, movimiento.getTipo(), valorSeguro(movimiento.getCantidad()));
            movimiento.setStockAnterior(anterior);
            movimiento.setStockResultante(stock);
            movimientoRepository.save(movimiento);
        }
        item.setStockActual(stock);
        itemRepository.save(item);
    }
}
