package com.obra.certificaciones.reporte.service;

import com.obra.certificaciones.certificacion.dto.EstadoItemCertificacion;
import com.obra.certificaciones.certificacion.dto.ItemOrdenCompraResumen;
import com.obra.certificaciones.certificacion.service.CertificacionCalculoService;
import com.obra.certificaciones.certificacion.repository.CertificacionRepository;
import com.obra.certificaciones.material.dto.EstadoRecepcionMaterial;
import com.obra.certificaciones.material.dto.ItemMaterialResumen;
import com.obra.certificaciones.material.service.MaterialService;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.repository.OrdenCompraRepository;
import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.reporte.dto.EvolucionMensualGasto;
import com.obra.certificaciones.reporte.dto.GastoMensualItem;
import com.obra.certificaciones.reporte.dto.GraficoDato;
import com.obra.certificaciones.reporte.dto.ReporteGeneral;
import com.obra.certificaciones.reporte.dto.ReporteMensual;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReporteService {

    private final OrdenCompraRepository ordenCompraRepository;
    private final CertificacionRepository certificacionRepository;
    private final CertificacionCalculoService calculoService;
    private final MaterialService materialService;

    @Transactional(readOnly = true)
    public ReporteGeneral generarGeneral(Obra obra) {
        BigDecimal totalManoObra = BigDecimal.ZERO;
        BigDecimal totalMateriales = BigDecimal.ZERO;
        BigDecimal totalOtros = BigDecimal.ZERO;
        BigDecimal totalCertificado = BigDecimal.ZERO;
        long pendientes = 0;
        long enEjecucion = 0;
        long terminados = 0;
        long entregasPendientes = 0;
        long entregasParciales = 0;
        long entregasCompletas = 0;
        BigDecimal materialRecibidoEstimado = BigDecimal.ZERO;
        BigDecimal materialPendienteEntrega = BigDecimal.ZERO;
        Map<String, BigDecimal> totalPorProveedor = new LinkedHashMap<>();
        Map<String, BigDecimal> totalPorOrden = new LinkedHashMap<>();
        Map<String, BigDecimal> totalMaterialPorProveedor = new LinkedHashMap<>();
        Map<String, BigDecimal> pendientePorMaterial = new LinkedHashMap<>();

        List<OrdenCompra> ordenes = ordenesDeObra(obra);
        List<OrdenCompra> ordenesEntrega = ordenes.stream()
                .filter(OrdenCompra::usaSeguimientoEntregas)
                .toList();
        Map<Long, EstadoRecepcionMaterial> estadosEntregaPorOrden = materialService.calcularEstadosOrdenes(ordenesEntrega);
        Map<Long, List<ItemMaterialResumen>> resumenMaterialPorOrden = materialService.calcularResumenOrdenes(ordenesEntrega);
        Map<Long, Map<Long, BigDecimal>> acumuladosPorOrden = calculoService.porcentajesAcumuladosPorOrdenes(ordenes.stream()
                .map(OrdenCompra::getId)
                .toList());
        for (EstadoRecepcionMaterial estado : estadosEntregaPorOrden.values()) {
            if (estado == EstadoRecepcionMaterial.PENDIENTE) {
                entregasPendientes++;
            } else if (estado == EstadoRecepcionMaterial.PARCIAL) {
                entregasParciales++;
            } else if (estado == EstadoRecepcionMaterial.COMPLETO) {
                entregasCompletas++;
            }
        }
        for (OrdenCompra orden : ordenes) {
            BigDecimal totalOrden = orden.getTotal();
            String proveedor = orden.getProveedorEntidad() == null ? "Sin proveedor" : orden.getProveedorEntidad().getNombre();
            totalPorProveedor.merge(proveedor, totalOrden, BigDecimal::add);
            totalPorOrden.put("OC " + orden.getNumero(), totalOrden);

            List<ItemOrdenCompraResumen> resumenes = calculoService.calcularResumenItems(
                    orden.getItems(),
                    acumuladosPorOrden.getOrDefault(orden.getId(), Map.of()));
            for (ItemOrdenCompraResumen resumen : resumenes) {
                BigDecimal importe = resumen.itemOrdenCompra().getImporte() == null ? BigDecimal.ZERO : resumen.itemOrdenCompra().getImporte();
                CategoriaItem categoria = resumen.itemOrdenCompra().getCategoria();
                if (categoria == CategoriaItem.MATERIAL) {
                    totalMateriales = totalMateriales.add(importe);
                    totalMaterialPorProveedor.merge(proveedor, importe, BigDecimal::add);
                    continue;
                }
                if (categoria != CategoriaItem.MANO_OBRA) {
                    totalOtros = totalOtros.add(importe);
                    continue;
                }
                totalManoObra = totalManoObra.add(importe);
                totalCertificado = totalCertificado.add(resumen.montoAcumulado());
                if (resumen.estado() == EstadoItemCertificacion.PENDIENTE) {
                    pendientes++;
                } else if (resumen.estado() == EstadoItemCertificacion.EN_EJECUCION) {
                    enEjecucion++;
                } else if (resumen.estado() == EstadoItemCertificacion.TERMINADO) {
                    terminados++;
                }
            }
            for (ItemMaterialResumen resumenMaterial : resumenMaterialPorOrden.getOrDefault(orden.getId(), List.of())) {
                BigDecimal importe = valorSeguro(resumenMaterial.itemOrdenCompra().getImporte());
                BigDecimal recibido = importe.multiply(resumenMaterial.porcentajeRecibido())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal pendiente = importe.subtract(recibido).max(BigDecimal.ZERO);
                materialRecibidoEstimado = materialRecibidoEstimado.add(recibido);
                materialPendienteEntrega = materialPendienteEntrega.add(pendiente);
                if (pendiente.compareTo(BigDecimal.ZERO) > 0) {
                    pendientePorMaterial.merge(nombreMaterial(resumenMaterial.itemOrdenCompra()), pendiente, BigDecimal::add);
                }
            }
        }

        BigDecimal saldoPendiente = totalManoObra.subtract(totalCertificado).max(BigDecimal.ZERO);
        BigDecimal totalGeneral = totalManoObra.add(totalMateriales).add(totalOtros);
        BigDecimal totalMaterialEntrega = materialRecibidoEstimado.add(materialPendienteEntrega);
        List<GraficoDato> estadosItems = List.of(
                new GraficoDato("Pendientes", BigDecimal.valueOf(pendientes), porcentaje(BigDecimal.valueOf(pendientes), BigDecimal.valueOf(pendientes + enEjecucion + terminados))),
                new GraficoDato("En ejecucion", BigDecimal.valueOf(enEjecucion), porcentaje(BigDecimal.valueOf(enEjecucion), BigDecimal.valueOf(pendientes + enEjecucion + terminados))),
                new GraficoDato("Terminados", BigDecimal.valueOf(terminados), porcentaje(BigDecimal.valueOf(terminados), BigDecimal.valueOf(pendientes + enEjecucion + terminados)))
        );
        List<GraficoDato> importesPorTipo = List.of(
                new GraficoDato("Mano de obra", totalManoObra, porcentaje(totalManoObra, totalGeneral)),
                new GraficoDato("Materiales", totalMateriales, porcentaje(totalMateriales, totalGeneral)),
                new GraficoDato("Otros", totalOtros, porcentaje(totalOtros, totalGeneral))
        );
        List<GraficoDato> estadosEntregas = List.of(
                new GraficoDato("Pendientes", BigDecimal.valueOf(entregasPendientes), porcentaje(BigDecimal.valueOf(entregasPendientes), BigDecimal.valueOf(ordenesEntrega.size()))),
                new GraficoDato("Parciales", BigDecimal.valueOf(entregasParciales), porcentaje(BigDecimal.valueOf(entregasParciales), BigDecimal.valueOf(ordenesEntrega.size()))),
                new GraficoDato("Completas", BigDecimal.valueOf(entregasCompletas), porcentaje(BigDecimal.valueOf(entregasCompletas), BigDecimal.valueOf(ordenesEntrega.size())))
        );
        List<GraficoDato> topProveedores = topBarras(totalPorProveedor, 5);
        List<GraficoDato> topOrdenes = topBarras(totalPorOrden, 5);
        List<GraficoDato> topMaterialesPendientes = topBarras(pendientePorMaterial, 6);
        List<GraficoDato> topProveedoresMateriales = topBarras(totalMaterialPorProveedor, 6);

        return new ReporteGeneral(
                ordenes.size(),
                certificacionRepository.countByOrdenCompraObraId(obra.getId()),
                totalManoObra,
                totalMateriales,
                totalOtros,
                totalCertificado,
                saldoPendiente,
                porcentaje(totalCertificado, totalManoObra),
                pendientes,
                enEjecucion,
                terminados,
                ordenesEntrega.size(),
                entregasPendientes,
                entregasParciales,
                entregasCompletas,
                materialRecibidoEstimado,
                materialPendienteEntrega,
                porcentaje(materialRecibidoEstimado, totalMaterialEntrega),
                estadosItems,
                importesPorTipo,
                estadosEntregas,
                topProveedores,
                topOrdenes,
                topMaterialesPendientes,
                topProveedoresMateriales,
                pieCss(estadosItems, List.of("#d34b42", "#ffd21a", "#5aa142")),
                pieCss(importesPorTipo, List.of("#2f80ed", "#ffd21a", "#5b6470")),
                pieCss(estadosEntregas, List.of("#d34b42", "#ffd21a", "#5aa142"))
        );
    }

    @Transactional(readOnly = true)
    public ReporteMensual generarMensual(Obra obra, int anio, int mes) {
        return generarMensual(obra, anio, mes, null);
    }

    @Transactional(readOnly = true)
    public ReporteMensual generarMensual(Obra obra, int anio, int mes, Long categoriaId) {
        YearMonth periodo = YearMonth.of(anio, mes);
        BigDecimal totalManoObra = BigDecimal.ZERO;
        BigDecimal totalMateriales = BigDecimal.ZERO;
        BigDecimal totalOtros = BigDecimal.ZERO;
        Map<String, BigDecimal> totalPorProveedor = new LinkedHashMap<>();
        Map<String, BigDecimal> totalPorCategoria = new LinkedHashMap<>();
        List<GastoMensualItem> itemsReporte = new ArrayList<>();
        Set<Long> ordenesIncluidas = new HashSet<>();

        for (OrdenCompra orden : ordenesDeObra(obra)) {
            if (orden.getFecha() == null || !YearMonth.from(orden.getFecha()).equals(periodo)) {
                continue;
            }
            String proveedor = orden.getProveedorEntidad() == null ? "Sin proveedor" : orden.getProveedorEntidad().getNombre();
            for (ItemOrdenCompra item : orden.getItems()) {
                if (!coincideCategoria(item, categoriaId)) {
                    continue;
                }
                BigDecimal importe = item.getImporte() == null ? BigDecimal.ZERO : item.getImporte();
                if (item.getCategoria() == CategoriaItem.MANO_OBRA) {
                    totalManoObra = totalManoObra.add(importe);
                } else if (item.getCategoria() == CategoriaItem.MATERIAL) {
                    totalMateriales = totalMateriales.add(importe);
                } else {
                    totalOtros = totalOtros.add(importe);
                }
                String categoriaNombre = categoriaNombre(item);
                ordenesIncluidas.add(orden.getId());
                totalPorProveedor.merge(proveedor, importe, BigDecimal::add);
                totalPorCategoria.merge(categoriaNombre, importe, BigDecimal::add);
                itemsReporte.add(new GastoMensualItem(
                        orden.getFecha(),
                        orden.getId(),
                        orden.getNumero(),
                        proveedor,
                        categoriaNombre,
                        item.getItem(),
                        item.getDetalle(),
                        item.getRubroEntidad() == null ? null : item.getRubroEntidad().getNombreCompleto(),
                        importe
                ));
            }
        }

        BigDecimal total = totalManoObra.add(totalMateriales).add(totalOtros);
        List<GraficoDato> importesPorTipo = List.of(
                new GraficoDato("Mano de obra", totalManoObra, porcentaje(totalManoObra, total)),
                new GraficoDato("Materiales", totalMateriales, porcentaje(totalMateriales, total)),
                new GraficoDato("Otros", totalOtros, porcentaje(totalOtros, total))
        );

        itemsReporte.sort(Comparator
                .comparing(GastoMensualItem::fecha).reversed()
                .thenComparing(GastoMensualItem::ordenCompraNumero)
                .thenComparing(item -> item.item() == null ? "" : item.item()));

        return new ReporteMensual(
                anio,
                mes,
                nombrePeriodo(periodo),
                periodo.atDay(1),
                periodo.atEndOfMonth(),
                total,
                totalManoObra,
                totalMateriales,
                totalOtros,
                ordenesIncluidas.size(),
                itemsReporte.size(),
                importesPorTipo,
                topBarras(totalPorCategoria, 10),
                topBarras(totalPorProveedor, 8),
                itemsReporte
        );
    }

    @Transactional(readOnly = true)
    public List<EvolucionMensualGasto> generarEvolucionMensual(Obra obra, Long categoriaId) {
        List<OrdenCompra> ordenes = ordenesDeObra(obra);
        YearMonth inicio = ordenes.stream()
                .map(OrdenCompra::getFecha)
                .filter(fecha -> fecha != null)
                .min(LocalDate::compareTo)
                .map(YearMonth::from)
                .orElse(YearMonth.now());
        YearMonth actual = YearMonth.now();
        if (inicio.isAfter(actual)) {
            inicio = actual;
        }

        Map<YearMonth, BigDecimal> totalesPorMes = new LinkedHashMap<>();
        for (YearMonth cursor = inicio; !cursor.isAfter(actual); cursor = cursor.plusMonths(1)) {
            totalesPorMes.put(cursor, BigDecimal.ZERO);
        }

        for (OrdenCompra orden : ordenes) {
            if (orden.getFecha() == null) {
                continue;
            }
            YearMonth periodo = YearMonth.from(orden.getFecha());
            if (!totalesPorMes.containsKey(periodo)) {
                continue;
            }
            for (ItemOrdenCompra item : orden.getItems()) {
                if (!coincideCategoria(item, categoriaId)) {
                    continue;
                }
                BigDecimal importe = item.getImporte() == null ? BigDecimal.ZERO : item.getImporte();
                totalesPorMes.merge(periodo, importe, BigDecimal::add);
            }
        }

        BigDecimal maximo = totalesPorMes.values().stream()
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        List<EvolucionMensualGasto> evolucion = new ArrayList<>();
        BigDecimal anterior = null;
        for (Map.Entry<YearMonth, BigDecimal> entry : totalesPorMes.entrySet()) {
            BigDecimal total = entry.getValue();
            VariacionMensual variacion = variacionMensual(total, anterior);
            evolucion.add(new EvolucionMensualGasto(
                    entry.getKey().toString(),
                    etiquetaMesCorta(entry.getKey()),
                    total,
                    porcentaje(total, maximo),
                    variacion.texto(),
                    variacion.estado()
            ));
            anterior = total;
        }
        return evolucion;
    }

    private List<OrdenCompra> ordenesDeObra(Obra obra) {
        return ordenCompraRepository.buscarConFiltros(obra.getId(), null, null, null);
    }

    private boolean coincideCategoria(ItemOrdenCompra item, Long categoriaId) {
        if (categoriaId == null) {
            return true;
        }
        return item.getCategoriaEntidad() != null && categoriaId.equals(item.getCategoriaEntidad().getId());
    }

    private String categoriaNombre(ItemOrdenCompra item) {
        if (item.getCategoriaEntidad() != null) {
            return item.getCategoriaEntidad().getNombre();
        }
        return item.getCategoria() == null ? "Sin categoria" : item.getCategoria().getDescripcion();
    }

    private BigDecimal valorSeguro(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private String nombreMaterial(ItemOrdenCompra item) {
        if (item.getMaterialCatalogo() != null && item.getMaterialCatalogo().getNombre() != null) {
            return item.getMaterialCatalogo().getNombre();
        }
        if (item.getDetalle() != null && !item.getDetalle().isBlank()) {
            return item.getDetalle();
        }
        return item.getItem() == null ? "Material sin detalle" : item.getItem();
    }

    private String nombrePeriodo(YearMonth periodo) {
        String mes = Month.of(periodo.getMonthValue()).getDisplayName(TextStyle.FULL, Locale.of("es", "AR"));
        return mes.substring(0, 1).toUpperCase(Locale.ROOT) + mes.substring(1) + " " + periodo.getYear();
    }

    private String etiquetaMesCorta(YearMonth periodo) {
        String mes = Month.of(periodo.getMonthValue()).getDisplayName(TextStyle.SHORT, Locale.of("es", "AR"));
        return mes.substring(0, 1).toUpperCase(Locale.ROOT) + mes.substring(1).replace(".", "") + " " + periodo.getYear();
    }

    private VariacionMensual variacionMensual(BigDecimal total, BigDecimal anterior) {
        if (anterior == null) {
            return new VariacionMensual("Inicio", "neutral");
        }
        if (anterior.compareTo(BigDecimal.ZERO) == 0) {
            if (total.compareTo(BigDecimal.ZERO) == 0) {
                return new VariacionMensual("0%", "neutral");
            }
            return new VariacionMensual("Nuevo gasto", "up");
        }
        BigDecimal variacion = total.subtract(anterior)
                .multiply(BigDecimal.valueOf(100))
                .divide(anterior, 1, RoundingMode.HALF_UP);
        String signo = variacion.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
        String texto = signo + variacion.toPlainString().replace(".", ",") + "%";
        String estado = variacion.compareTo(BigDecimal.ZERO) > 0 ? "up" : variacion.compareTo(BigDecimal.ZERO) < 0 ? "down" : "neutral";
        return new VariacionMensual(texto, estado);
    }

    private record VariacionMensual(String texto, String estado) {
    }

    private List<GraficoDato> topBarras(Map<String, BigDecimal> valores, int limite) {
        BigDecimal maximo = valores.values().stream()
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        return valores.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(limite)
                .map(entry -> new GraficoDato(entry.getKey(), entry.getValue(), porcentaje(entry.getValue(), maximo)))
                .toList();
    }

    private int porcentaje(BigDecimal valor, BigDecimal total) {
        if (valor == null || total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return valor.multiply(BigDecimal.valueOf(100))
                .divide(total, 0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100))
                .intValue();
    }

    private String pieCss(List<GraficoDato> datos, List<String> colores) {
        int acumulado = 0;
        StringBuilder css = new StringBuilder("conic-gradient(");
        for (int i = 0; i < datos.size(); i++) {
            int desde = acumulado;
            acumulado = i == datos.size() - 1 ? 100 : acumulado + datos.get(i).porcentaje();
            if (i > 0) {
                css.append(", ");
            }
            css.append(colores.get(i)).append(" ").append(desde).append("% ").append(acumulado).append("%");
        }
        css.append(")");
        return css.toString();
    }
}
