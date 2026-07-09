package com.obra.certificaciones.itemizado.dto;

import com.obra.certificaciones.oc.entity.ItemOrdenCompra;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public record ItemizadoItemFila(
        String codigoItemizado,
        ItemOrdenCompra manoObra,
        List<ItemOrdenCompra> materiales,
        BigDecimal totalMateriales,
        BigDecimal totalGeneral
) {
    public String materialesDescripcion() {
        return materiales.stream()
                .map(ItemOrdenCompra::getDetalle)
                .collect(Collectors.joining(", "));
    }
}
