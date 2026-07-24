package com.obra.certificaciones.notapedido.service;

import com.obra.certificaciones.notapedido.dto.NotaPedidoForm;
import com.obra.certificaciones.notapedido.entity.EstadoNotaPedido;
import com.obra.certificaciones.notapedido.entity.ItemNotaPedido;
import com.obra.certificaciones.notapedido.entity.NotaPedido;
import com.obra.certificaciones.notapedido.entity.TipoNotaPedido;
import com.obra.certificaciones.notapedido.repository.NotaPedidoRepository;
import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.repository.OrdenCompraRepository;
import com.obra.certificaciones.proveedor.entity.Proveedor;
import com.obra.certificaciones.proveedor.repository.ProveedorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotaPedidoService {
    private final NotaPedidoRepository notaPedidoRepository;
    private final ProveedorRepository proveedorRepository;
    private final OrdenCompraRepository ordenCompraRepository;

    @Transactional(readOnly = true)
    public Page<NotaPedido> listar(String termino, TipoNotaPedido tipo, EstadoNotaPedido estado, Pageable pageable) {
        return notaPedidoRepository.buscar(normalizar(termino), tipo, estado, pageable);
    }

    @Transactional(readOnly = true)
    public NotaPedido obtener(Long id) {
        return notaPedidoRepository.findWithItemsById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la nota de pedido " + id));
    }

    @Transactional(readOnly = true)
    public NotaPedidoForm crearForm(Long id, Obra obra) {
        NotaPedidoForm form = new NotaPedidoForm();
        if (id == null) {
            form.setNumero(sugerirNumero(obra));
            form.setSolicitante("AGT");
            form.getItems().add(new ItemNotaPedido());
            return form;
        }
        NotaPedido nota = obtener(id);
        form.setId(nota.getId());
        form.setNumero(nota.getNumero());
        form.setFecha(nota.getFecha());
        form.setSolicitante(nota.getSolicitante());
        form.setComparativa(nota.isComparativa());
        form.setVerStock(nota.isVerStock());
        form.setTipo(nota.getTipo());
        form.setEstado(nota.getEstado());
        form.setPrioridad(nota.getPrioridad());
        form.setProveedorId(nota.getProveedorEntidad() == null ? null : nota.getProveedorEntidad().getId());
        form.setOrdenCompraId(nota.getOrdenCompra() == null ? null : nota.getOrdenCompra().getId());
        form.setObservaciones(nota.getObservaciones());
        form.setItems(nota.getItems());
        return form;
    }

    @Transactional
    public NotaPedido guardar(NotaPedidoForm form, Obra obra) {
        validar(form, obra);
        NotaPedido nota = form.getId() == null ? new NotaPedido() : obtener(form.getId());
        nota.setObra(obra);
        nota.setNumero(form.getNumero().trim());
        nota.setFecha(form.getFecha() == null ? LocalDate.now() : form.getFecha());
        nota.setSolicitante(form.getSolicitante());
        nota.setComparativa(form.isComparativa());
        nota.setVerStock(form.isVerStock());
        nota.setTipo(form.getTipo() == null ? TipoNotaPedido.MATERIALES : form.getTipo());
        nota.setEstado(form.getEstado() == null ? EstadoNotaPedido.BORRADOR : form.getEstado());
        nota.setPrioridad(form.getPrioridad());
        nota.setObservaciones(form.getObservaciones());
        aplicarProveedor(nota, form.getProveedorId());
        aplicarOrdenCompra(nota, form.getOrdenCompraId());

        if (nota.getOrdenCompra() != null) {
            nota.setEstado(EstadoNotaPedido.CONVERTIDA_OC);
        }

        boolean usaPrecio = nota.usaPrecio();
        List<ItemNotaPedido> itemsValidos = form.getItems().stream()
                .filter(item -> StringUtils.hasText(item.getDetalle()) || StringUtils.hasText(item.getItem()))
                .peek(item -> item.calcularImporte(usaPrecio))
                .toList();
        nota.reemplazarItems(itemsValidos);
        return notaPedidoRepository.save(nota);
    }

    @Transactional
    public void eliminar(Long id) {
        notaPedidoRepository.delete(obtener(id));
    }

    private String sugerirNumero(Obra obra) {
        long siguiente = notaPedidoRepository.countByObraId(obra.getId()) + 1;
        return String.valueOf(siguiente);
    }

    private void validar(NotaPedidoForm form, Obra obra) {
        if (!StringUtils.hasText(form.getNumero())) {
            throw new IllegalArgumentException("El numero de nota es obligatorio.");
        }
        boolean duplicada = form.getId() == null
                ? notaPedidoRepository.existsByNumeroIgnoreCaseAndObraId(form.getNumero(), obra.getId())
                : notaPedidoRepository.existsByNumeroIgnoreCaseAndObraIdAndIdNot(form.getNumero(), obra.getId(), form.getId());
        if (duplicada) {
            throw new IllegalArgumentException("Ya existe una nota de pedido con ese numero en la obra activa.");
        }
        List<ItemNotaPedido> itemsValidos = form.getItems().stream()
                .filter(item -> StringUtils.hasText(item.getDetalle()) || StringUtils.hasText(item.getItem()))
                .toList();
        if (itemsValidos.isEmpty()) {
            throw new IllegalArgumentException("La nota debe tener al menos un item.");
        }
        for (ItemNotaPedido item : itemsValidos) {
            if (!StringUtils.hasText(item.getDetalle())) {
                throw new IllegalArgumentException("Todos los items deben tener detalle.");
            }
            if (esNegativo(item.getCantidad()) || esNegativo(item.getPrecioUnitario())) {
                throw new IllegalArgumentException("Las cantidades y precios no pueden ser negativos.");
            }
        }
    }

    private void aplicarProveedor(NotaPedido nota, Long proveedorId) {
        if (proveedorId == null) {
            nota.setProveedorEntidad(null);
            return;
        }
        Proveedor proveedor = proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el proveedor " + proveedorId));
        nota.setProveedorEntidad(proveedor);
    }

    private void aplicarOrdenCompra(NotaPedido nota, Long ordenCompraId) {
        if (ordenCompraId == null) {
            nota.setOrdenCompra(null);
            return;
        }
        OrdenCompra ordenCompra = ordenCompraRepository.findById(ordenCompraId)
                .orElseThrow(() -> new EntityNotFoundException("No existe la OC " + ordenCompraId));
        nota.setOrdenCompra(ordenCompra);
    }

    private String normalizar(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    private boolean esNegativo(BigDecimal valor) {
        return valor != null && valor.compareTo(BigDecimal.ZERO) < 0;
    }
}
