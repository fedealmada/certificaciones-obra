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

        for (ItemCertificacion itemHistorial : historial) {
            ItemOrdenCompra itemOrdenCompra = itemHistorial.getItemOrdenCompra();
            Long itemId = itemOrdenCompra.getId();
            BigDecimal anterior = acumulados.getOrDefault(itemId, BigDecimal.ZERO);
            BigDecimal actual = porcentajeSeguro(itemHistorial);
            BigDecimal acumulado = anterior.add(actual);
            acumulados.put(itemId, acumulado);

            if (itemHistorial.getCertificacion().getId().equals(certificacion.getId())) {
                resultado.add(new ItemCertificacionCalculado(itemOrdenCompra, anterior, actual, acumulado, calcularMonto(itemOrdenCompra.getImporte(), actual)));
            }
        }
        return resultado;
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
                    BigDecimal acumulado = item.getCategoria() == CategoriaItem.MANO_OBRA
                            ? acumuladosPorItem.getOrDefault(item.getId(), BigDecimal.ZERO)
                            : BigDecimal.ZERO;
                    BigDecimal montoAcumulado = calcularMonto(item.getImporte(), acumulado);
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
                    calcularMonto(item.getItemOrdenCompra().getImporte(), actual)
            ));
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public ResumenCertificacion calcularResumen(Long ordenCompraId,
                                                List<ItemOrdenCompra> itemsOrdenCompra,
                                                List<ItemCertificacionCalculado> itemsCertificado) {
        return calcularResumen(itemsOrdenCompra, itemsCertificado, porcentajesAcumuladosPorItem(ordenCompraId));
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
            if (item.getCategoria() != CategoriaItem.MANO_OBRA) {
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

    private EstadoItemCertificacion calcularEstado(BigDecimal porcentajeAcumulado) {
        if (porcentajeAcumulado.compareTo(BigDecimal.ZERO) <= 0) {
            return EstadoItemCertificacion.PENDIENTE;
        }
        if (porcentajeAcumulado.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return EstadoItemCertificacion.TERMINADO;
        }
        return EstadoItemCertificacion.EN_EJECUCION;
    }
}
