package com.obra.certificaciones.certificacion.service;

import com.obra.certificaciones.certificacion.dto.ItemNuevaCertificacionForm;
import com.obra.certificaciones.certificacion.dto.NuevaCertificacionForm;
import com.obra.certificaciones.certificacion.entity.Certificacion;
import com.obra.certificaciones.certificacion.entity.ItemCertificacion;
import com.obra.certificaciones.certificacion.repository.CertificacionRepository;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.repository.ItemOrdenCompraRepository;
import com.obra.certificaciones.oc.service.OrdenCompraService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CertificacionService {
    private final CertificacionRepository certificacionRepository;
    private final ItemOrdenCompraRepository itemOrdenCompraRepository;
    private final OrdenCompraService ordenCompraService;
    private final CertificacionCalculoService calculoService;

    @Transactional(readOnly = true)
    public List<Certificacion> listarPorOrdenCompra(Long ordenCompraId) {
        return certificacionRepository.findByOrdenCompraIdOrderByFechaAscIdAsc(ordenCompraId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> contarPorOrdenes(List<Long> ordenCompraIds) {
        if (ordenCompraIds == null || ordenCompraIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> conteos = new HashMap<>();
        certificacionRepository.countByOrdenCompraIds(ordenCompraIds).forEach(fila ->
                conteos.put((Long) fila[0], (Long) fila[1]));
        return conteos;
    }

    @Transactional(readOnly = true)
    public Certificacion obtener(Long id) {
        return certificacionRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("No existe la certificacion " + id));
    }

    @Transactional(readOnly = true)
    public NuevaCertificacionForm crearForm(Long ordenCompraId) {
        OrdenCompra ordenCompra = ordenCompraService.obtener(ordenCompraId);
        validarOrdenCertificable(ordenCompra);
        NuevaCertificacionForm form = new NuevaCertificacionForm();
        form.setNumero((int) certificacionRepository.countByOrdenCompraId(ordenCompraId) + 1);
        itemOrdenCompraRepository.findByOrdenCompraIdAndCategoriaOrderById(ordenCompraId, CategoriaItem.MANO_OBRA).forEach(item -> {
            ItemNuevaCertificacionForm itemForm = new ItemNuevaCertificacionForm();
            itemForm.setItemOrdenCompraId(item.getId());
            itemForm.setPorcentajeActual(BigDecimal.ZERO);
            form.getItems().add(itemForm);
        });
        return form;
    }

    @Transactional(readOnly = true)
    public NuevaCertificacionForm crearFormEdicion(Long ordenCompraId, Long certificacionId) {
        Certificacion certificacion = obtener(certificacionId);
        validarCertificacionDeOrden(ordenCompraId, certificacion);
        validarOrdenCertificable(certificacion.getOrdenCompra());

        Map<Long, BigDecimal> porcentajesActuales = new HashMap<>();
        certificacion.getItems().forEach(item ->
                porcentajesActuales.put(item.getItemOrdenCompra().getId(), item.getPorcentajeActual()));

        NuevaCertificacionForm form = new NuevaCertificacionForm();
        form.setId(certificacion.getId());
        form.setNumero(certificacion.getNumero());
        form.setFecha(certificacion.getFecha());
        form.setObservacion(certificacion.getObservacion());
        itemOrdenCompraRepository.findByOrdenCompraIdAndCategoriaOrderById(ordenCompraId, CategoriaItem.MANO_OBRA).forEach(item -> {
            ItemNuevaCertificacionForm itemForm = new ItemNuevaCertificacionForm();
            itemForm.setItemOrdenCompraId(item.getId());
            itemForm.setPorcentajeActual(porcentajesActuales.getOrDefault(item.getId(), BigDecimal.ZERO));
            form.getItems().add(itemForm);
        });
        return form;
    }

    @Transactional
    public Certificacion guardar(Long ordenCompraId, NuevaCertificacionForm form) {
        OrdenCompra ordenCompra = ordenCompraService.obtener(ordenCompraId);
        validarOrdenCertificable(ordenCompra);
        Map<Long, ItemOrdenCompra> itemsPorId = itemsManoObraPorId(ordenCompra);
        Map<Long, BigDecimal> acumuladosPorItem = calculoService.porcentajesAcumuladosPorItem(ordenCompraId);
        Certificacion certificacion = new Certificacion();
        certificacion.setOrdenCompra(ordenCompra);
        certificacion.setNumero(form.getNumero());
        certificacion.setFecha(form.getFecha());
        certificacion.setObservacion(form.getObservacion());

        for (ItemNuevaCertificacionForm itemForm : form.getItems()) {
            ItemOrdenCompra itemOrdenCompra = obtenerItemFormulario(itemsPorId, itemForm);
            validarItemBasico(ordenCompraId, itemOrdenCompra, itemForm);
            validarAcumulado(itemOrdenCompra, itemForm, acumuladosPorItem.getOrDefault(itemOrdenCompra.getId(), BigDecimal.ZERO));

            BigDecimal porcentajeActual = porcentajeSeguro(itemForm);
            if (porcentajeActual.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            ItemCertificacion itemCertificacion = new ItemCertificacion();
            itemCertificacion.setItemOrdenCompra(itemOrdenCompra);
            itemCertificacion.setPorcentajeActual(porcentajeActual);
            certificacion.agregarItem(itemCertificacion);
        }
        if (certificacion.getItems().isEmpty()) {
            throw new IllegalArgumentException("Debe cargar al menos un porcentaje mayor a 0.");
        }
        return certificacionRepository.save(certificacion);
    }

    @Transactional
    public Certificacion actualizar(Long ordenCompraId, Long certificacionId, NuevaCertificacionForm form) {
        Certificacion certificacion = obtener(certificacionId);
        validarCertificacionDeOrden(ordenCompraId, certificacion);
        OrdenCompra ordenCompra = ordenCompraService.obtener(ordenCompraId);
        validarOrdenCertificable(ordenCompra);
        Map<Long, ItemOrdenCompra> itemsPorId = itemsManoObraPorId(ordenCompra);

        Map<Long, BigDecimal> porcentajesEditados = new HashMap<>();
        for (ItemNuevaCertificacionForm itemForm : form.getItems()) {
            ItemOrdenCompra itemOrdenCompra = obtenerItemFormulario(itemsPorId, itemForm);
            validarItemBasico(ordenCompraId, itemOrdenCompra, itemForm);
            porcentajesEditados.put(itemOrdenCompra.getId(), porcentajeSeguro(itemForm));
        }
        validarHistorialCompleto(ordenCompraId, certificacionId, itemsPorId.values().stream().toList(), porcentajesEditados);

        certificacion.setNumero(form.getNumero());
        certificacion.setFecha(form.getFecha());
        certificacion.setObservacion(form.getObservacion());
        certificacion.getItems().clear();

        for (ItemNuevaCertificacionForm itemForm : form.getItems()) {
            BigDecimal porcentajeActual = porcentajeSeguro(itemForm);
            if (porcentajeActual.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            ItemOrdenCompra itemOrdenCompra = obtenerItemFormulario(itemsPorId, itemForm);
            ItemCertificacion itemCertificacion = new ItemCertificacion();
            itemCertificacion.setItemOrdenCompra(itemOrdenCompra);
            itemCertificacion.setPorcentajeActual(porcentajeActual);
            certificacion.agregarItem(itemCertificacion);
        }
        if (certificacion.getItems().isEmpty()) {
            throw new IllegalArgumentException("Debe cargar al menos un porcentaje mayor a 0.");
        }
        return certificacionRepository.save(certificacion);
    }

    @Transactional
    public void eliminar(Long ordenCompraId, Long certificacionId) {
        Certificacion certificacion = obtener(certificacionId);
        if (!certificacion.getOrdenCompra().getId().equals(ordenCompraId)) {
            throw new IllegalArgumentException("La certificacion no pertenece a esta orden de compra.");
        }
        certificacionRepository.delete(certificacion);
    }

    private void validarAcumulado(ItemOrdenCompra itemOrdenCompra, ItemNuevaCertificacionForm itemForm, BigDecimal acumuladoAnterior) {
        BigDecimal acumuladoNuevo = acumuladoAnterior.add(porcentajeSeguro(itemForm));
        if (acumuladoNuevo.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("El item " + itemOrdenCompra.getItem() + " supera el 100% acumulado.");
        }
    }

    private void validarItemBasico(Long ordenCompraId, ItemOrdenCompra itemOrdenCompra, ItemNuevaCertificacionForm itemForm) {
        if (!itemOrdenCompra.getOrdenCompra().getId().equals(ordenCompraId)) {
            throw new IllegalArgumentException("El item " + itemOrdenCompra.getItem() + " no pertenece a esta orden de compra.");
        }
        if (itemOrdenCompra.getCategoria() != CategoriaItem.MANO_OBRA) {
            throw new IllegalArgumentException("Solo se pueden certificar items de mano de obra.");
        }

        BigDecimal porcentajeActual = porcentajeSeguro(itemForm);
        if (porcentajeActual.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El porcentaje actual no puede ser negativo.");
        }
    }

    private void validarHistorialCompleto(Long ordenCompraId,
                                          Long certificacionEditadaId,
                                          List<ItemOrdenCompra> itemsManoObra,
                                          Map<Long, BigDecimal> porcentajesEditados) {
        Map<Long, BigDecimal> anteriores = calculoService.porcentajesAnterioresPorItem(ordenCompraId, certificacionEditadaId);
        Map<Long, BigDecimal> posteriores = calculoService.porcentajesPosterioresPorItem(ordenCompraId, certificacionEditadaId);
        for (ItemOrdenCompra item : itemsManoObra) {
            BigDecimal anterior = anteriores.getOrDefault(item.getId(), BigDecimal.ZERO);
            BigDecimal actualEditado = porcentajesEditados.getOrDefault(item.getId(), BigDecimal.ZERO);
            BigDecimal posterior = posteriores.getOrDefault(item.getId(), BigDecimal.ZERO);
            BigDecimal acumuladoFinal = anterior.add(actualEditado).add(posterior);
            if (acumuladoFinal.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("La correccion hace que el item " + item.getItem() + " supere el 100% en el historial.");
            }
        }
    }

    private void validarCertificacionDeOrden(Long ordenCompraId, Certificacion certificacion) {
        if (!certificacion.getOrdenCompra().getId().equals(ordenCompraId)) {
            throw new IllegalArgumentException("La certificacion no pertenece a esta orden de compra.");
        }
    }

    private void validarOrdenCertificable(OrdenCompra ordenCompra) {
        if (!ordenCompra.usaSeguimientoCertificacion()) {
            throw new IllegalArgumentException("Esta orden de compra no esta configurada para certificaciones.");
        }
    }

    private BigDecimal porcentajeSeguro(ItemNuevaCertificacionForm itemForm) {
        return itemForm.getPorcentajeActual() == null ? BigDecimal.ZERO : itemForm.getPorcentajeActual();
    }

    private Map<Long, ItemOrdenCompra> itemsManoObraPorId(OrdenCompra ordenCompra) {
        return ordenCompra.getItems().stream()
                .filter(item -> item.getCategoria() == CategoriaItem.MANO_OBRA)
                .collect(Collectors.toMap(ItemOrdenCompra::getId, Function.identity()));
    }

    private ItemOrdenCompra obtenerItemFormulario(Map<Long, ItemOrdenCompra> itemsPorId, ItemNuevaCertificacionForm itemForm) {
        ItemOrdenCompra itemOrdenCompra = itemsPorId.get(itemForm.getItemOrdenCompraId());
        if (itemOrdenCompra == null) {
            throw new EntityNotFoundException("No existe el item " + itemForm.getItemOrdenCompraId());
        }
        return itemOrdenCompra;
    }
}
