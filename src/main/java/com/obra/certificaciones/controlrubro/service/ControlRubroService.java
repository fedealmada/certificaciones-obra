package com.obra.certificaciones.controlrubro.service;

import com.obra.certificaciones.certificacion.service.CertificacionCalculoService;
import com.obra.certificaciones.controlrubro.dto.ControlRubroFila;
import com.obra.certificaciones.controlrubro.dto.ControlRubroVista;
import com.obra.certificaciones.controlrubro.entity.ControlRubroEstimacion;
import com.obra.certificaciones.controlrubro.repository.ControlRubroEstimacionRepository;
import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.repository.ItemOrdenCompraRepository;
import com.obra.certificaciones.rubro.entity.Rubro;
import com.obra.certificaciones.rubro.service.RubroService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ControlRubroService {
    private final ItemOrdenCompraRepository itemOrdenCompraRepository;
    private final ControlRubroEstimacionRepository estimacionRepository;
    private final RubroService rubroService;
    private final CertificacionCalculoService calculoService;

    @Transactional(readOnly = true)
    public ControlRubroVista generar(Obra obra) {
        List<ItemOrdenCompra> itemsManoObra = itemOrdenCompraRepository.findManoObraConRubroByObraId(obra.getId());
        BigDecimal totalManoObra = itemsManoObra.stream().map(this::importe).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMateriales = itemOrdenCompraRepository.sumImporteByObraIdAndCategoria(obra.getId(), CategoriaItem.MATERIAL);
        if (totalMateriales == null) {
            totalMateriales = BigDecimal.ZERO;
        }

        Map<Long, BigDecimal> techos = estimacionRepository.findByObraId(obra.getId()).stream()
                .filter(estimacion -> estimacion.getRubro() != null)
                .collect(Collectors.toMap(estimacion -> estimacion.getRubro().getId(), this::techo, (a, b) -> b));
        Set<Long> rubrosConDatos = itemsManoObra.stream()
                .map(ItemOrdenCompra::getRubroEntidad)
                .filter(Objects::nonNull)
                .map(Rubro::getId)
                .collect(Collectors.toSet());
        rubrosConDatos.addAll(techos.keySet());

        Map<Long, List<ItemOrdenCompra>> itemsPorRubro = itemsManoObra.stream()
                .filter(item -> item.getRubroEntidad() != null)
                .collect(Collectors.groupingBy(item -> item.getRubroEntidad().getId()));
        Map<Long, Rubro> rubrosPorId = rubroService.listarActivos().stream()
                .collect(Collectors.toMap(Rubro::getId, Function.identity(), (a, b) -> a));
        techos.keySet().forEach(id -> rubrosPorId.putIfAbsent(id, rubroService.obtener(id)));

        Map<Long, Map<Long, BigDecimal>> acumuladosPorOrden = acumuladosPorOrden(itemsManoObra);
        BigDecimal materialesBase = totalMateriales;
        List<ControlRubroFila> filas = rubrosPorId.values().stream()
                .filter(rubro -> rubrosConDatos.contains(rubro.getId()))
                .map(rubro -> armarFila(rubro, itemsPorRubro.getOrDefault(rubro.getId(), List.of()), techos.getOrDefault(rubro.getId(), BigDecimal.ZERO), materialesBase, acumuladosPorOrden))
                .sorted((a, b) -> compararCodigo(a.codigo(), b.codigo()))
                .toList();

        BigDecimal totalIncidencia = filas.stream().map(ControlRubroFila::incidenciaMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGastosReales = filas.stream().map(ControlRubroFila::gastosReales).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTecho = filas.stream().map(ControlRubroFila::techoDireccion).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAvance = filas.stream().map(ControlRubroFila::avancePesos).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPendiente = filas.stream().map(ControlRubroFila::pendienteEjecutar).reduce(BigDecimal.ZERO, BigDecimal::add);
        long sobreTecho = filas.stream().filter(fila -> "danger".equals(fila.estado())).count();

        return new ControlRubroVista(
                filas,
                totalManoObra,
                totalMateriales,
                totalIncidencia,
                totalGastosReales,
                totalTecho,
                totalAvance,
                totalPendiente,
                sobreTecho,
                porcentaje(totalAvance, totalGastosReales)
        );
    }

    @Transactional
    public void guardarTecho(Obra obra, Long rubroId, BigDecimal techoDireccion) {
        Rubro rubro = rubroService.obtener(rubroId);
        ControlRubroEstimacion estimacion = estimacionRepository.findByObraIdAndRubroId(obra.getId(), rubroId)
                .orElseGet(() -> {
                    ControlRubroEstimacion nueva = new ControlRubroEstimacion();
                    nueva.setObra(obra);
                    nueva.setRubro(rubro);
                    return nueva;
                });
        estimacion.setTechoDireccion(valor(techoDireccion));
        estimacionRepository.save(estimacion);
    }

    private ControlRubroFila armarFila(Rubro rubro,
                                       List<ItemOrdenCompra> items,
                                       BigDecimal techoDireccion,
                                       BigDecimal totalMateriales,
                                       Map<Long, Map<Long, BigDecimal>> acumuladosPorOrden) {
        BigDecimal manoObra = items.stream().map(this::importe).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal incidenciaPorcentaje = porcentaje(manoObra, totalMateriales);
        BigDecimal incidenciaMonto = calcularMonto(manoObra, incidenciaPorcentaje);
        BigDecimal gastosReales = manoObra.add(incidenciaMonto);
        BigDecimal manoObraEjecutada = items.stream()
                .map(item -> calculoService.calcularMonto(importe(item), acumulado(item, acumuladosPorOrden)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avancePorcentaje = porcentaje(manoObraEjecutada, manoObra).min(BigDecimal.valueOf(100));
        BigDecimal avancePesos = calcularMonto(gastosReales, avancePorcentaje);
        BigDecimal saldoTecho = techoDireccion.subtract(gastosReales);
        BigDecimal pendienteEjecutar = techoDireccion.subtract(avancePesos).max(BigDecimal.ZERO);
        String estado = estado(saldoTecho, techoDireccion);
        return new ControlRubroFila(
                rubro.getId(),
                rubro.getCodigo(),
                rubro.getNombre(),
                manoObra,
                incidenciaPorcentaje,
                incidenciaMonto,
                gastosReales,
                techoDireccion,
                saldoTecho,
                avancePorcentaje,
                avancePesos,
                pendienteEjecutar,
                estado,
                estadoTexto(estado)
        );
    }

    private Map<Long, Map<Long, BigDecimal>> acumuladosPorOrden(List<ItemOrdenCompra> items) {
        List<Long> ordenIds = items.stream()
                .filter(item -> item.getOrdenCompra() != null)
                .map(item -> item.getOrdenCompra().getId())
                .distinct()
                .toList();
        if (ordenIds.isEmpty()) {
            return Map.of();
        }
        return calculoService.porcentajesAcumuladosPorOrdenes(ordenIds);
    }

    private BigDecimal acumulado(ItemOrdenCompra item, Map<Long, Map<Long, BigDecimal>> acumuladosPorOrden) {
        if (item.getOrdenCompra() == null) {
            return BigDecimal.ZERO;
        }
        return acumuladosPorOrden.getOrDefault(item.getOrdenCompra().getId(), Map.of())
                .getOrDefault(item.getId(), BigDecimal.ZERO);
    }

    private BigDecimal calcularMonto(BigDecimal base, BigDecimal porcentaje) {
        return valor(base).multiply(valor(porcentaje)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal porcentaje(BigDecimal valor, BigDecimal total) {
        if (valor == null || total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return valor.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal importe(ItemOrdenCompra item) {
        return valor(item.getImporte());
    }

    private BigDecimal techo(ControlRubroEstimacion estimacion) {
        return valor(estimacion.getTechoDireccion());
    }

    private BigDecimal valor(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private String estado(BigDecimal saldoTecho, BigDecimal techoDireccion) {
        if (techoDireccion == null || techoDireccion.compareTo(BigDecimal.ZERO) <= 0) {
            return "neutral";
        }
        if (saldoTecho.compareTo(BigDecimal.ZERO) < 0) {
            return "danger";
        }
        if (saldoTecho.compareTo(techoDireccion.multiply(new BigDecimal("0.1"))) <= 0) {
            return "warning";
        }
        return "ok";
    }

    private String estadoTexto(String estado) {
        return switch (estado) {
            case "danger" -> "Sobre techo";
            case "warning" -> "Cerca del techo";
            case "ok" -> "Dentro del techo";
            default -> "Sin techo";
        };
    }

    private int compararCodigo(String left, String right) {
        return normalizarCodigo(left).compareTo(normalizarCodigo(right));
    }

    private String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return "999999";
        }
        StringBuilder resultado = new StringBuilder();
        for (String parte : codigo.split("\\.")) {
            try {
                resultado.append(String.format("%04d", Integer.parseInt(parte.trim())));
            } catch (NumberFormatException ex) {
                resultado.append(parte.trim());
            }
            resultado.append('.');
        }
        return resultado.toString();
    }
}

