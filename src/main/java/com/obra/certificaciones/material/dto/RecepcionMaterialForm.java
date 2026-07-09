package com.obra.certificaciones.material.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RecepcionMaterialForm {
    private LocalDate fecha = LocalDate.now();
    private String remito;
    private String observacion;
    private List<ItemRecepcionMaterialForm> items = new ArrayList<>();
}
