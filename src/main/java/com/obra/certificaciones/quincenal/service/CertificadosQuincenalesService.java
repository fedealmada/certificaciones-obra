package com.obra.certificaciones.quincenal.service;

import com.obra.certificaciones.certificacion.entity.ItemCertificacion;
import com.obra.certificaciones.certificacion.repository.ItemCertificacionRepository;
import com.obra.certificaciones.certificacion.service.CertificacionCalculoService;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.quincenal.dto.ResumenQuincenal;
import com.obra.certificaciones.quincenal.dto.ResumenQuincenal.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificadosQuincenalesService {
    private final ItemCertificacionRepository repository;
    private final CertificacionCalculoService calculo;
    private final com.obra.certificaciones.certificacion.service.AjustesCertificacionService ajustes;

    public static final int TOLERANCIA_DIAS = 5;

    // Adelantar los cortes cinco dias agrupa emisiones anticipadas con las de su cierre.
    // Solo se ajusta la fecha de referencia para clasificar; nunca la fecha del certificado.
    public static YearMonth mesTrabajo(LocalDate emision) {
        LocalDate referencia = emision.plusDays(TOLERANCIA_DIAS);
        YearMonth mes = YearMonth.from(referencia);
        return referencia.getDayOfMonth() <= 15 ? mes.minusMonths(1) : mes;
    }

    public static int quincenaTrabajo(LocalDate emision) {
        return emision.plusDays(TOLERANCIA_DIAS).getDayOfMonth() <= 15 ? 2 : 1;
    }

    public static LocalDate inicioEmisiones(YearMonth mes) {
        return mes.atDay(16).minusDays(TOLERANCIA_DIAS);
    }

    public static LocalDate inicioSegundaQuincena(YearMonth mes) {
        return mes.plusMonths(1).atDay(1).minusDays(TOLERANCIA_DIAS);
    }

    public static LocalDate finEmisiones(YearMonth mes) {
        return inicioEmisiones(mes.plusMonths(1)).minusDays(1);
    }

    public List<YearMonth> mesesDisponibles(Long obraId) {
        return repository.fechasCertificables(obraId).stream().map(CertificadosQuincenalesService::mesTrabajo)
                .distinct().sorted(Comparator.reverseOrder()).toList();
    }

    public ResumenQuincenal resumir(Long obraId, YearMonth mes, Long proveedorId, int quincena) {
        List<ItemCertificacion> items = repository.buscarCertificablesPeriodo(obraId, inicioEmisiones(mes), finEmisiones(mes));
        Map<Long, List<ItemCertificacion>> porCertificado = new LinkedHashMap<>();
        for (ItemCertificacion item : items) {
            if (item.getPorcentajeActual() == null || item.getPorcentajeActual().signum() == 0) continue;
            porCertificado.computeIfAbsent(item.getCertificacion().getId(), k -> new ArrayList<>()).add(item);
        }
        List<Certificado> todos = porCertificado.values().stream().map(this::certificado)
                .sorted(Comparator.comparing(Certificado::quincena).thenComparing(Certificado::proveedor, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Certificado::fecha).thenComparing(Certificado::id)).toList();
        List<OpcionProveedor> opciones = todos.stream().map(c -> new OpcionProveedor(c.proveedorId(), c.proveedor()))
                .distinct().sorted(Comparator.comparing(OpcionProveedor::nombre, String.CASE_INSENSITIVE_ORDER)).toList();
        List<Certificado> delProveedor = todos.stream().filter(c -> proveedorId == null || proveedorId.equals(c.proveedorId())).toList();
        Map<Long, List<Certificado>> grupos = new LinkedHashMap<>();
        delProveedor.forEach(c -> grupos.computeIfAbsent(c.proveedorId(), k -> new ArrayList<>()).add(c));
        List<ProveedorResumen> proveedores = grupos.entrySet().stream().map(entry -> {
            List<Certificado> cs = entry.getValue();
            return new ProveedorResumen(entry.getKey(), cs.getFirst().proveedor(), sumar(cs, 1), sumar(cs, 2), sumar(cs, 0), cs.size(), totales(cs));
        }).sorted(Comparator.comparing(ProveedorResumen::nombre, String.CASE_INSENSITIVE_ORDER)).toList();
        List<Certificado> visibles = delProveedor.stream().filter(c -> quincena == 0 || c.quincena() == quincena).toList();
        return new ResumenQuincenal(proveedores, visibles, opciones, sumar(delProveedor, 1), sumar(delProveedor, 2),
                sumar(delProveedor, 0), sumar(visibles, 0), delProveedor.size(), proveedores.size(), totales(delProveedor), totales(visibles));
    }

    private Certificado certificado(List<ItemCertificacion> items) {
        var cert = items.getFirst().getCertificacion();
        var oc = cert.getOrdenCompra();
        var proveedor = oc.getProveedorEntidad();
        List<Tarea> tareas = items.stream().sorted(Comparator.comparing(i -> i.getItemOrdenCompra().getId()))
                .map(i -> new Tarea(i.getItemOrdenCompra().getItem(), i.getItemOrdenCompra().getDetalle(),
                        i.getItemOrdenCompra().getUnidad(), i.getPorcentajeActual(),
                        i.getMontoActual(), tipo(i), i.getCantidadActual())).toList();
        String tipo = tareas.stream().map(Tarea::tipo).distinct().count() > 1 ? "Mano de obra y servicios" : tareas.getFirst().tipo();
        var liquidacion = ajustes.liquidar(cert, tareas.stream().map(Tarea::importe).reduce(BigDecimal.ZERO, BigDecimal::add));
        return new Certificado(cert.getId(), cert.getNumero(), cert.getFecha(), oc.getId(), oc.getNumero(),
                proveedor == null ? 0L : proveedor.getId(), proveedor == null ? "Sin proveedor" : proveedor.getNombre(),
                quincenaTrabajo(cert.getFecha()), liquidacion.neto(), tareas, tipo, liquidacion);
    }

    private String tipo(ItemCertificacion item) {
        var tarea = item.getItemOrdenCompra();
        var categoria = tarea.getCategoriaEntidad() == null ? tarea.getCategoria() : tarea.getCategoriaEntidad().getTipo();
        return categoria == CategoriaItem.MANO_OBRA ? "Mano de obra" : "Servicio certificable";
    }

    private BigDecimal sumar(List<Certificado> certificados, int quincena) {
        return certificados.stream().filter(c -> quincena == 0 || c.quincena() == quincena)
                .map(Certificado::importe).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private com.obra.certificaciones.certificacion.dto.LiquidacionCertificado.Totales totales(List<Certificado> certificados) {
        return com.obra.certificaciones.certificacion.dto.LiquidacionCertificado.Totales.sumar(certificados.stream().map(Certificado::liquidacion).toList());
    }
}
