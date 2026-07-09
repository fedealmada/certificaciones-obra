package com.obra.certificaciones.certificacion.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class NuevaCertificacionForm {
    private Long id;
    private Integer numero;
    private LocalDate fecha = LocalDate.now();
    private String observacion;
    private List<ItemNuevaCertificacionForm> items = new ArrayList<>();
}
