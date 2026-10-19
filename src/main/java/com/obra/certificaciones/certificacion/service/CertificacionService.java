package com.obra.certificaciones.certificacion.service;

import com.obra.certificaciones.certificacion.dto.ItemNuevaCertificacionForm;
import com.obra.certificaciones.certificacion.dto.NuevaCertificacionForm;
import com.obra.certificaciones.certificacion.entity.Certificacion;
import com.obra.certificaciones.certificacion.entity.ItemCertificacion;
import com.obra.certificaciones.certificacion.repository.CertificacionRepository;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.service.OrdenCompraService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CertificacionService {
    private final CertificacionRepository certificacionRepository;
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
        form.setFecha(fechaOrden(ordenCompra));
        form.setPorCantidad(ordenCompra.isPorCantidad());
        itemsCertificables(ordenCompra).forEach(item -> {
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
                porcentajesActuales.put(item.getItemOrdenCompra().getId(), certificacion.isPorCantidad() ? cantidadEquivalente(item) : item.getPorcentajeActual()));

        NuevaCertificacionForm form = new NuevaCertificacionForm();
        form.setId(certificacion.getId());
        form.setPorCantidad(certificacion.isPorCantidad());
        form.setNumero(certificacion.getNumero());
        form.setFecha(certificacion.getFecha());
        form.setObservacion(certificacion.getObservacion());
        itemsCertificables(certificacion.getOrdenCompra()).forEach(item -> {
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
        Map<Long, ItemOrdenCompra> itemsPorId = itemsCertificablesPorId(ordenCompra);
        Map<Long, BigDecimal> acumuladosPorItem = calculoService.porcentajesAcumuladosPorItem(ordenCompraId);
        normalizarCantidades(form, ordenCompra, ordenCompra.isPorCantidad(), null);
        Certificacion certificacion = new Certificacion();
        certificacion.setPorCantidad(ordenCompra.isPorCantidad());
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
            itemCertificacion.setCantidadActual(form.isPorCantidad() ? itemForm.getCantidadActual() : null);
            certificacion.agregarItem(itemCertificacion);
        }
        if (certificacion.getItems().isEmpty()) {
            throw new IllegalArgumentException("Debe cargar al menos un avance mayor a 0.");
        }
        return certificacionRepository.save(certificacion);
    }

    @Transactional
    public Certificacion actualizar(Long ordenCompraId, Long certificacionId, NuevaCertificacionForm form) {
        Certificacion certificacion = obtener(certificacionId);
        validarCertificacionDeOrden(ordenCompraId, certificacion);
        OrdenCompra ordenCompra = ordenCompraService.obtener(ordenCompraId);
        validarOrdenCertificable(ordenCompra);
        Map<Long, ItemOrdenCompra> itemsPorId = itemsCertificablesPorId(ordenCompra);

        normalizarCantidades(form, ordenCompra, certificacion.isPorCantidad(), certificacionId);
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
            itemCertificacion.setCantidadActual(form.isPorCantidad() ? itemForm.getCantidadActual() : null);
            certificacion.agregarItem(itemCertificacion);
        }
        if (certificacion.getItems().isEmpty()) {
            throw new IllegalArgumentException("Debe cargar al menos un avance mayor a 0.");
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
        if (acumuladoNuevo.setScale(10, java.math.RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("El item " + itemOrdenCompra.getItem() + " supera el 100% acumulado.");
        }
    }

    private void validarItemBasico(Long ordenCompraId, ItemOrdenCompra itemOrdenCompra, ItemNuevaCertificacionForm itemForm) {
        if (!itemOrdenCompra.getOrdenCompra().getId().equals(ordenCompraId)) {
            throw new IllegalArgumentException("El item " + itemOrdenCompra.getItem() + " no pertenece a esta orden de compra.");
        }
        if (!calculoService.esItemCertificable(itemOrdenCompra)) {
            throw new IllegalArgumentException("Solo se pueden certificar items que no sean materiales.");
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
            if (acumuladoFinal.setScale(10, java.math.RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(100)) > 0) {
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

    private BigDecimal cantidadEquivalente(ItemCertificacion item) {
        return item.getCantidadActual() != null ? item.getCantidadActual()
                : item.getItemOrdenCompra().getCantidad().multiply(item.getPorcentajeActual())
                    .divide(new BigDecimal("100"), 12, java.math.RoundingMode.HALF_UP);
    }

    private void normalizarCantidades(NuevaCertificacionForm form, OrdenCompra oc, boolean porCantidad, Long excluir) {
        form.setPorCantidad(porCantidad);
        form.getItems().forEach(i -> { i.setPorcentajeCalculado(null); i.setCantidadActual(null); });
        if (!porCantidad) return;
        var items = itemsCertificablesPorId(oc);
        var usados = new HashMap<Long, BigDecimal>();
        for (var historico : calculoService.historialOrden(oc.getId())) {
            if (excluir != null && excluir.equals(historico.getCertificacion().getId())) continue;
            usados.merge(historico.getItemOrdenCompra().getId(), cantidadEquivalente(historico), BigDecimal::add);
        }
        var vistos = new java.util.HashSet<Long>();
        for (var fila : form.getItems()) {
            var item = obtenerItemFormulario(items, fila);
            if (!vistos.add(item.getId())) throw new IllegalArgumentException("Item repetido en el certificado.");
            var cantidad = fila.getPorcentajeActual() == null ? BigDecimal.ZERO : fila.getPorcentajeActual();
            if (item.getCantidad() == null || item.getCantidad().signum() <= 0)
                throw new IllegalArgumentException("La cantidad contratada del item " + item.getItem() + " debe ser mayor a cero.");
            if (cantidad.signum() < 0 || cantidad.stripTrailingZeros().scale() > 6)
                throw new IllegalArgumentException("Ingrese una cantidad positiva con hasta seis decimales.");
            if (cantidad.add(usados.getOrDefault(item.getId(), BigDecimal.ZERO)).compareTo(item.getCantidad()) > 0)
                throw new IllegalArgumentException("El item " + item.getItem() + " supera la cantidad contratada, incluyendo los otros certificados.");
            fila.setCantidadActual(cantidad);
            fila.setPorcentajeCalculado(cantidad.multiply(new BigDecimal("100")).divide(item.getCantidad(), 16, java.math.RoundingMode.HALF_UP));
        }
    }

    private BigDecimal porcentajeSeguro(ItemNuevaCertificacionForm itemForm) {
        if (itemForm.getPorcentajeCalculado() != null) return itemForm.getPorcentajeCalculado();
        return itemForm.getPorcentajeActual() == null ? BigDecimal.ZERO : itemForm.getPorcentajeActual();
    }

    private LocalDate fechaOrden(OrdenCompra ordenCompra) {
        return ordenCompra.getFecha() == null ? LocalDate.now() : ordenCompra.getFecha();
    }

    private Map<Long, ItemOrdenCompra> itemsCertificablesPorId(OrdenCompra ordenCompra) {
        return itemsCertificables(ordenCompra).stream()
                .collect(Collectors.toMap(ItemOrdenCompra::getId, Function.identity()));
    }

    private List<ItemOrdenCompra> itemsCertificables(OrdenCompra ordenCompra) {
        return ordenCompra.getItems().stream()
                .filter(calculoService::esItemCertificable)
                .toList();
    }

    private ItemOrdenCompra obtenerItemFormulario(Map<Long, ItemOrdenCompra> itemsPorId, ItemNuevaCertificacionForm itemForm) {
        ItemOrdenCompra itemOrdenCompra = itemsPorId.get(itemForm.getItemOrdenCompraId());
        if (itemOrdenCompra == null) {
            throw new EntityNotFoundException("No existe el item " + itemForm.getItemOrdenCompraId());
        }
        return itemOrdenCompra;
    }
}
