package com.obra.certificaciones.alerta.service;

import com.obra.certificaciones.alerta.dto.AlertaSistema;
import com.obra.certificaciones.certificacion.service.CertificacionCalculoService;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.repository.OrdenCompraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertaSistemaService {
    private final OrdenCompraRepository ordenCompraRepository;
    private final CertificacionCalculoService calculoService;

    @Transactional(readOnly = true)
    public List<AlertaSistema> alertasGenerales() {
        List<AlertaSistema> alertas = new ArrayList<>();
        List<OrdenCompra> ordenes = ordenCompraRepository.buscarConFiltros(null, null, null);
        Map<Long, Map<Long, BigDecimal>> acumuladosPorOrden = calculoService.porcentajesAcumuladosPorOrdenes(ordenes.stream()
                .map(OrdenCompra::getId)
                .toList());
        ordenes.forEach(orden -> alertas.addAll(alertasOrden(orden, acumuladosPorOrden.getOrDefault(orden.getId(), Map.of()))));
        return alertas.stream().limit(12).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertaSistema> alertasOrden(Long ordenCompraId) {
        return ordenCompraRepository.findById(ordenCompraId)
                .map(this::alertasOrden)
                .orElseGet(List::of);
    }

    private List<AlertaSistema> alertasOrden(OrdenCompra orden) {
        return alertasOrden(orden, calculoService.porcentajesAcumuladosPorItem(orden.getId()));
    }

    private List<AlertaSistema> alertasOrden(OrdenCompra orden, Map<Long, BigDecimal> acumuladosPorItem) {
        List<AlertaSistema> alertas = new ArrayList<>();
        String enlace = "/oc/" + orden.getId();

        if (orden.getProveedorEntidad() == null) {
            alertas.add(alerta("alta", "OC sin proveedor", "La OC " + orden.getNumero() + " no tiene proveedor asociado.", enlace, "bi-person-x"));
        }

        Map<String, List<ItemOrdenCompra>> itemsPorCodigo = orden.getItems().stream()
                .filter(item -> item.getItem() != null && !item.getItem().isBlank())
                .collect(Collectors.groupingBy(item -> item.getItem().trim().toLowerCase(Locale.ROOT), LinkedHashMap::new, Collectors.toList()));
        itemsPorCodigo.values().stream()
                .filter(items -> items.size() > 1)
                .forEach(items -> alertas.add(alerta("alta", "Items repetidos", "La OC " + orden.getNumero() + " tiene repetido el item " + items.get(0).getItem() + ".", enlace, "bi-copy")));

        long sinCategoria = orden.getItems().stream()
                .filter(item -> item.getCategoriaEntidad() == null)
                .count();
        if (sinCategoria > 0) {
            alertas.add(alerta("media", "Items sin categoria", sinCategoria + " items de la OC " + orden.getNumero() + " no tienen categoria manual asignada.", enlace, "bi-tags"));
        }

        long manoObraSinRubro = orden.getItems().stream()
                .filter(item -> item.getCategoria() == CategoriaItem.MANO_OBRA)
                .filter(item -> item.getRubroEntidad() == null)
                .count();
        if (manoObraSinRubro > 0) {
            alertas.add(alerta("media", "Mano de obra sin rubro", manoObraSinRubro + " items de mano de obra no estan vinculados a rubro.", enlace, "bi-diagram-3"));
        }

        long materialesSinVincular = orden.getItems().stream()
                .filter(item -> item.getCategoria() == CategoriaItem.MATERIAL)
                .filter(item -> item.getItemManoObraVinculado() == null)
                .count();
        if (materialesSinVincular > 0) {
            alertas.add(alerta("baja", "Materiales sin mano de obra", materialesSinVincular + " materiales no estan vinculados a un item de mano de obra.", enlace, "bi-link-45deg"));
        }

        orden.getItems().stream()
                .filter(item -> item.getCategoria() == CategoriaItem.MANO_OBRA)
                .filter(item -> acumuladosPorItem.getOrDefault(item.getId(), BigDecimal.ZERO).compareTo(BigDecimal.valueOf(100)) > 0)
                .map(ItemOrdenCompra::getItem)
                .filter(Objects::nonNull)
                .forEach(item -> alertas.add(alerta("alta", "Certificacion supera 100%", "El item " + item + " supera el 100% acumulado.", enlace + "#certificados", "bi-exclamation-octagon")));

        return alertas;
    }

    private AlertaSistema alerta(String prioridad, String titulo, String detalle, String enlace, String icono) {
        return new AlertaSistema(prioridad, titulo, detalle, enlace, icono);
    }
}
