package com.obra.certificaciones.oc.service;

import com.obra.certificaciones.categoria.entity.CategoriaOrden;
import com.obra.certificaciones.categoria.repository.CategoriaOrdenRepository;
import com.obra.certificaciones.certificacion.repository.ItemCertificacionRepository;
import com.obra.certificaciones.material.catalogo.entity.MaterialCatalogo;
import com.obra.certificaciones.material.catalogo.repository.MaterialCatalogoRepository;
import com.obra.certificaciones.oc.dto.OrdenCompraForm;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.entity.ModoSeguimientoOrden;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.repository.ItemOrdenCompraRepository;
import com.obra.certificaciones.oc.repository.OrdenCompraRepository;
import com.obra.certificaciones.proveedor.entity.Proveedor;
import com.obra.certificaciones.proveedor.repository.ProveedorRepository;
import com.obra.certificaciones.rubro.entity.Rubro;
import com.obra.certificaciones.rubro.repository.RubroRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrdenCompraService {
    private final OrdenCompraRepository ordenCompraRepository;
    private final ItemCertificacionRepository itemCertificacionRepository;
    private final ItemOrdenCompraRepository itemOrdenCompraRepository;
    private final ProveedorRepository proveedorRepository;
    private final RubroRepository rubroRepository;
    private final MaterialCatalogoRepository materialCatalogoRepository;
    private final CategoriaOrdenRepository categoriaOrdenRepository;

    @Transactional(readOnly = true)
    public List<OrdenCompra> listar(String proveedor, Long categoriaId, String rubro) {
        return ordenCompraRepository.buscarConFiltros(normalizar(proveedor), categoriaId, normalizar(rubro));
    }

    @Transactional(readOnly = true)
    public List<OrdenCompra> listarPorTipoCategoria(CategoriaItem categoria) {
        return ordenCompraRepository.buscarPorTipoCategoria(categoria);
    }

    @Transactional(readOnly = true)
    public OrdenCompra obtener(Long id) {
        return ordenCompraRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la orden de compra " + id));
    }

    @Transactional
    public OrdenCompra guardar(OrdenCompraForm form) {
        OrdenCompra ordenCompra = form.getId() == null ? new OrdenCompra() : obtener(form.getId());
        validarItemsEliminados(ordenCompra, form);
        validarOrdenCompra(form);
        ordenCompra.setNumero(form.getNumero());
        ordenCompra.setFecha(form.getFecha());
        ordenCompra.setFechaVigencia(form.getFechaVigencia());
        ordenCompra.setModoSeguimiento(form.getModoSeguimiento() == null ? ModoSeguimientoOrden.CERTIFICACION : form.getModoSeguimiento());
        aplicarProveedor(ordenCompra, form);
        ordenCompra.setObservacion(form.getObservacion());

        List<ItemOrdenCompra> itemsValidos = form.getItems().stream()
                .filter(item -> StringUtils.hasText(item.getDetalle()) || StringUtils.hasText(item.getItem()))
                .peek(this::aplicarCategoria)
                .peek(this::aplicarRubro)
                .peek(this::aplicarMaterialCatalogo)
                .peek(this::aplicarItemManoObraVinculado)
                .peek(ItemOrdenCompra::calcularImporte)
                .toList();
        ordenCompra.reemplazarItems(itemsValidos);
        return ordenCompraRepository.save(ordenCompra);
    }

    @Transactional
    public void eliminar(Long id) {
        OrdenCompra ordenCompra = obtener(id);
        ordenCompraRepository.delete(ordenCompra);
    }

    @Transactional(readOnly = true)
    public OrdenCompraForm crearForm(Long id) {
        OrdenCompraForm form = new OrdenCompraForm();
        if (id == null) {
            ItemOrdenCompra item = new ItemOrdenCompra();
            categoriaOrdenRepository.findByTipoAndNombreIgnoreCase(CategoriaItem.MANO_OBRA, "Mano de obra")
                    .ifPresent(categoria -> {
                        item.setCategoriaEntidad(categoria);
                        item.setCategoriaId(categoria.getId());
                        item.setCategoria(categoria.getTipo());
                    });
            form.getItems().add(item);
            return form;
        }
        OrdenCompra ordenCompra = obtener(id);
        form.setId(ordenCompra.getId());
        form.setNumero(ordenCompra.getNumero());
        form.setFecha(ordenCompra.getFecha());
        form.setFechaVigencia(ordenCompra.getFechaVigencia());
        form.setModoSeguimiento(ordenCompra.getModoSeguimientoEfectivo());
        form.setProveedorId(ordenCompra.getProveedorEntidad() == null ? null : ordenCompra.getProveedorEntidad().getId());
        form.setObservacion(ordenCompra.getObservacion());
        ordenCompra.getItems().forEach(item -> {
            item.setRubroId(item.getRubroEntidad() == null ? null : item.getRubroEntidad().getId());
            item.setItemManoObraVinculadoId(item.getItemManoObraVinculado() == null ? null : item.getItemManoObraVinculado().getId());
            item.setMaterialCatalogoId(item.getMaterialCatalogo() == null ? null : item.getMaterialCatalogo().getId());
            item.setCategoriaId(item.getCategoriaEntidad() == null ? null : item.getCategoriaEntidad().getId());
        });
        form.setItems(ordenCompra.getItems());
        return form;
    }

    private String normalizar(String valor) {
        return StringUtils.hasText(valor) ? valor : null;
    }

    private void validarItemsEliminados(OrdenCompra ordenCompra, OrdenCompraForm form) {
        if (ordenCompra.getId() == null) {
            return;
        }

        List<Long> idsFormulario = form.getItems().stream()
                .map(ItemOrdenCompra::getId)
                .filter(id -> id != null)
                .toList();

        ordenCompra.getItems().stream()
                .filter(item -> item.getId() != null)
                .filter(item -> !idsFormulario.contains(item.getId()))
                .filter(item -> itemCertificacionRepository.countByItemOrdenCompraId(item.getId()) > 0)
                .findFirst()
                .ifPresent(item -> {
                    throw new IllegalArgumentException("No se puede eliminar el item " + item.getItem() + " porque ya tiene certificaciones.");
                });
    }

    private void validarOrdenCompra(OrdenCompraForm form) {
        if (!StringUtils.hasText(form.getNumero())) {
            throw new IllegalArgumentException("El numero de OC es obligatorio.");
        }
        if (form.getProveedorId() == null) {
            throw new IllegalArgumentException("El proveedor es obligatorio.");
        }
        boolean numeroDuplicado = form.getId() == null
                ? ordenCompraRepository.existsByNumeroIgnoreCaseAndProveedorEntidadId(form.getNumero(), form.getProveedorId())
                : ordenCompraRepository.existsByNumeroIgnoreCaseAndProveedorEntidadIdAndIdNot(form.getNumero(), form.getProveedorId(), form.getId());
        if (numeroDuplicado) {
            throw new IllegalArgumentException("Ya existe una orden de compra con ese numero para el proveedor seleccionado.");
        }

        List<ItemOrdenCompra> itemsValidos = form.getItems().stream()
                .filter(item -> StringUtils.hasText(item.getDetalle()) || StringUtils.hasText(item.getItem()))
                .toList();
        if (itemsValidos.isEmpty()) {
            throw new IllegalArgumentException("La orden de compra debe tener al menos un item.");
        }

        for (ItemOrdenCompra item : itemsValidos) {
            if (!StringUtils.hasText(item.getDetalle())) {
                throw new IllegalArgumentException("Todos los items deben tener detalle.");
            }
            if (item.getCategoriaId() == null && item.getCategoriaEntidad() == null) {
                throw new IllegalArgumentException("Todos los items deben tener categoria.");
            }
            if (esNegativo(item.getCantidad())) {
                throw new IllegalArgumentException("La cantidad no puede ser negativa.");
            }
            if (esNegativo(item.getPrecioUnitario())) {
                throw new IllegalArgumentException("El precio unitario no puede ser negativo.");
            }
        }
    }

    private boolean esNegativo(BigDecimal valor) {
        return valor != null && valor.compareTo(BigDecimal.ZERO) < 0;
    }

    private void aplicarProveedor(OrdenCompra ordenCompra, OrdenCompraForm form) {
        Proveedor proveedor = proveedorRepository.findById(form.getProveedorId())
                .orElseThrow(() -> new EntityNotFoundException("No existe el proveedor " + form.getProveedorId()));
        ordenCompra.setProveedorEntidad(proveedor);
    }

    private void aplicarRubro(ItemOrdenCompra item) {
        if (item.getRubroId() == null) {
            item.setRubroEntidad(null);
            item.setRubro(null);
            return;
        }
        Rubro rubro = rubroRepository.findById(item.getRubroId())
                .orElseThrow(() -> new EntityNotFoundException("No existe el rubro " + item.getRubroId()));
        item.setRubroEntidad(rubro);
        item.setRubro(null);
    }

    private void aplicarCategoria(ItemOrdenCompra item) {
        CategoriaOrden categoria = item.getCategoriaId() == null
                ? item.getCategoriaEntidad()
                : categoriaOrdenRepository.findById(item.getCategoriaId())
                .orElseThrow(() -> new EntityNotFoundException("No existe la categoria " + item.getCategoriaId()));
        if (categoria == null) {
            throw new IllegalArgumentException("Todos los items deben tener categoria.");
        }
        item.setCategoriaEntidad(categoria);
        item.setCategoria(categoria.getTipo());
    }

    private void aplicarMaterialCatalogo(ItemOrdenCompra item) {
        if (!usaCatalogoMaterial(item) || item.getMaterialCatalogoId() == null) {
            item.setMaterialCatalogo(null);
            return;
        }

        MaterialCatalogo material = materialCatalogoRepository.findById(item.getMaterialCatalogoId())
                .orElseThrow(() -> new EntityNotFoundException("No existe el material " + item.getMaterialCatalogoId()));
        item.setMaterialCatalogo(material);
        item.setDetalle(material.getNombre());
        if (StringUtils.hasText(material.getUnidad())) {
            item.setUnidad(material.getUnidad());
        }
    }

    private void aplicarItemManoObraVinculado(ItemOrdenCompra item) {
        if (item.getCategoria() != CategoriaItem.MATERIAL || item.getItemManoObraVinculadoId() == null) {
            item.setItemManoObraVinculado(null);
            return;
        }

        ItemOrdenCompra itemManoObra = itemOrdenCompraRepository.findById(item.getItemManoObraVinculadoId())
                .orElseThrow(() -> new EntityNotFoundException("No existe el item de mano de obra " + item.getItemManoObraVinculadoId()));
        if (itemManoObra.getCategoria() != CategoriaItem.MANO_OBRA) {
            throw new IllegalArgumentException("El material solo puede vincularse a un item de mano de obra.");
        }
        item.setItemManoObraVinculado(itemManoObra);
    }

    private boolean usaCatalogoMaterial(ItemOrdenCompra item) {
        return item.getCategoria() == CategoriaItem.MATERIAL;
    }
}
