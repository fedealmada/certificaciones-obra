package com.obra.certificaciones.asistencia.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class AsistenciaForm {
    private Long id;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fecha = LocalDate.now();

    private Long trabajadorId;
    private String trabajadorNombre;
    private String empresa;
    private String sector;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime horaIngreso;

    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime horaSalida;

    private BigDecimal horasTrabajadas;
    private String observacion;
}
