package com.obra.certificaciones.itemizado.dto;

import com.obra.certificaciones.rubro.entity.Rubro;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@RequiredArgsConstructor
public class ItemizadoNodo {

    private final Rubro rubro;
    private int nivel;
    private List<ItemizadoNodo> hijos = new ArrayList<>();
    private List<ItemizadoItemFila> items = new ArrayList<>();
    private BigDecimal totalManoObra = BigDecimal.ZERO;
    private BigDecimal totalMateriales = BigDecimal.ZERO;
    private BigDecimal totalGeneral = BigDecimal.ZERO;
}
