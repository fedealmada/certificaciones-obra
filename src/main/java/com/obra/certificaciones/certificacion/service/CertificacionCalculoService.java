package com.obra.certificaciones.certificacion.service;

import com.obra.certificaciones.certificacion.dto.EstadoItemCertificacion;
import com.obra.certificaciones.certificacion.dto.HistorialItemCertificacion;
import com.obra.certificaciones.certificacion.dto.ItemCertificacionCalculado;
import com.obra.certificaciones.certificacion.dto.ItemOrdenCompraResumen;
import com.obra.certificaciones.certificacion.dto.ResumenCertificacion;
import com.obra.certificaciones.certificacion.entity.Certificacion;
import com.obra.certificaciones.certificacion.entity.ItemCertificacion;
import com.obra.certificaciones.certificacion.repository.ItemCertificacionRepository;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CertificacionCalculoService {
    private final ItemCertificacionRepository itemCertificacionRepository;

    @Transactional(readOnly = true)
    public BigDecimal porcentajeAcumuladoItem(Long ordenCompraId, Long itemOrdenCompraId) {
        return porcentajesAcumuladosPorItem(ordenCompraId).getOrDefault(itemOrdenCompraId, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<ItemCertificacion> historialOrden(Long ordenCompraId) {
        return itemCertificacionRepository.findByCertificacionOrdenCompraIdOrderByCertificacionFechaAscCertificacionIdAsc(ordenCompraId);
    }

    @Transactional(readOnly = true)
    public BigDecimal porcentajeAnteriorItem(Long ordenCompraId, Long itemOrdenCompraId, Long certificacionId) {
        return porcentajesAnterioresPorItem(ordenCompraId, certificacionId).getOrDefault(itemOrdenCompraId, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public BigDecimal porcentajePosteriorItem(Long ordenCompraId, Long itemOrdenCompraId, Long certificacionId) {
        return porcentajesPosterioresPorItem(ordenCompraId, certificacionId).getOrDefault(itemOrdenCompraId, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> porcentajesAcumuladosPorItem(Long ordenCompraId) {
        Map<Long, BigDecimal> acumulados = new HashMap<>();
        for (ItemCertificacion item : historialOrden(ordenCompraId)) {
            Long itemId = item.getItemOrdenCompra().getId();
            acumulados.merge(itemId, porcentajeSeguro(item), BigDecimal::add);
        }
        return acumulados;
    }

    @Transactional(readOnly = true)
    public Map<Long, Map<Long, BigDecimal>> porcentajesAcumuladosPorOrdenes(List<Long> ordenCompraIds) {
        if (ordenCompraIds == null || ordenCompraIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, BigDecimal>> acumulados = new HashMap<>();
        for (ItemCertificacion item : itemCertificacionRepository.findByCertificacionOrdenCompraIdIn(ordenCompraIds)) {
            Long ordenId = item.getCertificacion().getOrdenCompra().getId();
            Long itemId = item.getItemOrdenCompra().getId();
            acumulados.computeIfAbsent(ordenId, id -> new HashMap<>())
                    .merge(itemId, porcentajeSeguro(item), BigDecimal::add);
        }
        return acumulados;
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> porcentajesAnterioresPorItem(Long ordenCompraId, Long certificacionId) {
        Map<Long, BigDecimal> acumulados = new HashMap<>();
        for (ItemCertificacion item : historialOrden(ordenCompraId)) {
            if (item.getCertificacion().getId().equals(certificacionId)) {
                return acumulados;
            }
            Long itemId = item.getItemOrdenCompra().getId();
            acumulados.merge(itemId, porcentajeSeguro(item), BigDecimal::add);
        }
        return acumulados;
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> porcentajesPosterioresPorItem(Long ordenCompraId, Long certificacionId) {
        boolean despuesDeCertificacion = false;
        Map<Long, BigDecimal> posteriores = new HashMap<>();
        for (ItemCertificacion item : historialOrden(ordenCompraId)) {
            if (item.getCertificacion().getId().equals(certificacionId)) {
                despuesDeCertificacion = true;
                continue;
            }
            if (despuesDeCertificacion) {
                Long itemId = item.getItemOrdenCompra().getId();
                posteriores.merge(itemId, porcentajeSeguro(item), BigDecimal::add);
            }
        }
        return posteriores;
    }

    public BigDecimal porcentajePendiente(BigDecimal porcentajeAcumulado) {
        BigDecimal acumuladoSeguro = porcentajeAcumulado == null ? BigDecimal.ZERO : porcentajeAcumulado;
        return BigDecimal.valueOf(100).subtract(acumuladoSeguro).max(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<ItemCertificacionCalculado> calcularDetalle(Certificacion certificacion) {
        List<ItemCertificacion> historial = historialOrden(certificacion.getOrdenCompra().getId());
        Map<Long, BigDecimal> acumulados = new HashMap<>();
        List<ItemCertificacionCalculado> resultado = new ArrayList<>();
        Map<Long, BigDecimal> montos = new HashMap<>();

        for (ItemCertificacion itemHistorial : historial) {
            ItemOrdenCompra itemOrdenCompra = itemHistorial.getItemOrdenCompra();
            Long itemId = itemOrdenCompra.getId();
            BigDecimal anterior = acumulados.getOrDefault(itemId, BigDecimal.ZERO);
            BigDecimal actual = porcentajeSeguro(itemHistorial);
            BigDecimal acumulado = anterior.add(actual);
            acumulados.put(itemId, acumulado);
            BigDecimal montoAnterior = montos.getOrDefault(itemId, BigDecimal.ZERO);
            BigDecimal montoAcumulado = montoAnterior.add(itemHistorial.getMontoActual());
            montos.put(itemId, montoAcumulado);

            if (itemHistorial.getCertificacion().getId().equals(certificacion.getId())) {
                resultado.add(new ItemCertificacionCalculado(itemOrdenCompra, anterior, actual, acumulado, itemHistorial.getMontoActual(), montoAnterior, montoAcumulado));
            }
        }
        return resultado;
    }

    public BigDecimal calcularMontoAcumulado(ItemOrdenCompra item, BigDecimal porcentaje) {
        if (item.getOrdenCompra() != null && item.getOrdenCompra().isPorCantidad()) {
            BigDecimal cantidad = item.getCantidad().multiply(porcentaje).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
            return cantidad.multiply(item.getPrecioUnitario()).setScale(2, RoundingMode.HALF_UP);
        }
        return calcularMonto(item.getImporte(), porcentaje);
    }

    public BigDecimal calcularMonto(BigDecimal importe, BigDecimal porcentaje) {
        BigDecimal importeSeguro = importe == null ? BigDecimal.ZERO : importe;
        BigDecimal porcentajeSeguro = porcentaje == null ? BigDecimal.ZERO : porcentaje;
        return importeSeguro.multiply(porcentajeSeguro).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<ItemOrdenCompraResumen> calcularResumenItems(Long ordenCompraId, List<ItemOrdenCompra> itemsOrdenCompra) {
        return calcularResumenItems(itemsOrdenCompra, porcentajesAcumuladosPorItem(ordenCompraId));
    }

    public List<ItemOrdenCompraResumen> calcularResumenItems(List<ItemOrdenCompra> itemsOrdenCompra, Map<Long, BigDecimal> acumuladosPorItem) {
        return itemsOrdenCompra.stream()
                .map(item -> {
                    BigDecimal acumulado = esItemCertificable(item)
                            ? acumuladosPorItem.getOrDefault(item.getId(), BigDecimal.ZERO)
                            : BigDecimal.ZERO;
                    BigDecimal montoAcumulado = calcularMontoAcumulado(item, acumulado);
                    BigDecimal saldoPendiente = importeSeguro(item).subtract(montoAcumulado).max(BigDecimal.ZERO);
                    return new ItemOrdenCompraResumen(item, acumulado, montoAcumulado, saldoPendiente, calcularEstado(acumulado));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HistorialItemCertificacion> calcularHistorialItem(Long itemOrdenCompraId) {
        List<ItemCertificacion> historial = itemCertificacionRepository
                .findByItemOrdenCompraIdOrderByCertificacionFechaAscCertificacionIdAsc(itemOrdenCompraId);
        BigDecimal acumulado = BigDecimal.ZERO;
        List<HistorialItemCertificacion> resultado = new ArrayList<>();

        for (ItemCertificacion item : historial) {
            BigDecimal anterior = acumulado;
            BigDecimal actual = porcentajeSeguro(item);
            acumulado = acumulado.add(actual);
            resultado.add(new HistorialItemCertificacion(
                    item.getCertificacion().getId(),
                    item.getCertificacion().getNumero(),
                    item.getCertificacion().getFecha(),
                    anterior,
                    actual,
                    acumulado,
                    item.getMontoActual()
            ));
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public ResumenCertificacion calcularResumenHasta(Certificacion certificacion,
                                                List<ItemOrdenCompra> itemsOrdenCompra,
                                                List<ItemCertificacionCalculado> itemsCertificado) {
        Map<Long, BigDecimal> acumulados = new HashMap<>();
        for (ItemCertificacion item : historialOrden(certificacion.getOrdenCompra().getId())) {
            var origen = item.getCertificacion();
            int fecha = origen.getFecha().compareTo(certificacion.getFecha());
            if (fecha > 0 || (fecha == 0 && origen.getId().compareTo(certificacion.getId()) > 0)) break;
            acumulados.merge(item.getItemOrdenCompra().getId(), porcentajeSeguro(item), BigDecimal::add);
        }
        var resumen = calcularResumen(itemsOrdenCompra, itemsCertificado, acumulados);
        var historia = historialOrden(certificacion.getOrdenCompra().getId());
        if (historia.stream().noneMatch(i -> i.getCantidadActual() != null)) return resumen;
        BigDecimal monto = historia.stream()
                .filter(i -> esItemCertificable(i.getItemOrdenCompra()))
                .filter(i -> i.getCertificacion().getFecha().isBefore(certificacion.getFecha())
                    || (i.getCertificacion().getFecha().equals(certificacion.getFecha()) && i.getCertificacion().getId() <= certificacion.getId()))
                .map(ItemCertificacion::getMontoActual).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ResumenCertificacion(resumen.totalContratado(), resumen.totalActual(), monto,
                resumen.totalContratado().subtract(monto).max(BigDecimal.ZERO), resumen.cantidadItemsCertificados());
    }

    public ResumenCertificacion calcularResumen(List<ItemOrdenCompra> itemsOrdenCompra,
                                                List<ItemCertificacionCalculado> itemsCertificado,
                                                Map<Long, BigDecimal> acumuladosPorItem) {
        BigDecimal totalContratado = BigDecimal.ZERO;
        BigDecimal totalAcumulado = BigDecimal.ZERO;
        BigDecimal totalActual = itemsCertificado.stream()
                .map(ItemCertificacionCalculado::montoActual)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (ItemOrdenCompra item : itemsOrdenCompra) {
            if (!esItemCertificable(item)) {
                continue;
            }
            BigDecimal importe = item.getImporte() == null ? BigDecimal.ZERO : item.getImporte();
            BigDecimal acumulado = acumuladosPorItem.getOrDefault(item.getId(), BigDecimal.ZERO);
            totalContratado = totalContratado.add(importe);
            totalAcumulado = totalAcumulado.add(calcularMonto(importe, acumulado));
        }

        BigDecimal saldoPendiente = totalContratado.subtract(totalAcumulado).max(BigDecimal.ZERO);
        return new ResumenCertificacion(
                totalContratado,
                totalActual,
                totalAcumulado,
                saldoPendiente,
                itemsCertificado.size()
        );
    }

    private BigDecimal porcentajeSeguro(ItemCertificacion item) {
        return item.getPorcentajeActual() == null ? BigDecimal.ZERO : item.getPorcentajeActual();
    }

    private BigDecimal importeSeguro(ItemOrdenCompra item) {
        return item.getImporte() == null ? BigDecimal.ZERO : item.getImporte();
    }

    public boolean esItemCertificable(ItemOrdenCompra item) {
        return item.getCategoria() != CategoriaItem.MATERIAL;
    }

    private EstadoItemCertificacion calcularEstado(BigDecimal porcentajeAcumulado) {
        if (porcentajeAcumulado.compareTo(BigDecimal.ZERO) <= 0) {
            return EstadoItemCertificacion.PENDIENTE;
        }
        if (porcentajeAcumulado.setScale(10, RoundingMode.HALF_UP).compareTo(BigDecimal.valueOf(100)) >= 0) {
            return EstadoItemCertificacion.TERMINADO;
        }
        return EstadoItemCertificacion.EN_EJECUCION;
    }
}
