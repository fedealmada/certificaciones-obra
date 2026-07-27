package com.obra.certificaciones.itemizado.dto;

import com.obra.certificaciones.itemizado.entity.ItemizadoManualItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ItemizadoItemFila(
        String codigoItemizado,
        ItemOrdenCompra manoObra,
        ItemizadoManualItem manualItem,
        List<ItemOrdenCompra> materiales,
        List<ItemizadoManualItem> materialesManuales,
        BigDecimal totalMateriales,
        BigDecimal totalGeneral
) {
    public boolean esManual() {
        return manualItem != null;
    }

    public Long idVista() {
        return esManual() ? manualItem.getId() : manoObra.getId();
    }

    public String item() {
        return esManual() ? manualItem.getItem() : manoObra.getItem();
    }

    public String detalle() {
        return esManual() ? manualItem.getDetalle() : manoObra.getDetalle();
    }

    public String unidad() {
        return esManual() ? manualItem.getUnidad() : manoObra.getUnidad();
    }

    public BigDecimal cantidad() {
        return esManual() ? manualItem.getCantidad() : manoObra.getCantidad();
    }

    public BigDecimal precioUnitario() {
        return esManual() ? manualItem.getPrecioUnitario() : manoObra.getPrecioUnitario();
    }

    public BigDecimal importeManoObra() {
        return esManual() ? manualItem.getImporte() : manoObra.getImporte();
    }

    public String ocNumero() {
        return esManual() ? "Manual" : manoObra.getOrdenCompra().getNumero();
    }

    public Long ocId() {
        return esManual() ? null : manoObra.getOrdenCompra().getId();
    }

    public String materialesDescripcion() {
        return Stream.concat(
                        materiales.stream().map(ItemOrdenCompra::getDetalle),
                        materialesManuales.stream().map(ItemizadoManualItem::getDetalle))
                .collect(Collectors.joining(", "));
    }

    public boolean tieneMateriales() {
        return !materiales.isEmpty() || !materialesManuales.isEmpty();
    }
}
