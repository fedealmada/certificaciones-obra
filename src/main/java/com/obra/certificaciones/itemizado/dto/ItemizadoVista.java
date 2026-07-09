package com.obra.certificaciones.itemizado.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record ItemizadoVista(
        List<ItemizadoNodo> raices,
        List<ItemizadoNodo> nodos,
        int cantidadRubros,
        int cantidadItems,
        BigDecimal totalManoObra,
        BigDecimal totalMateriales,
        BigDecimal totalGeneral
) {
}
