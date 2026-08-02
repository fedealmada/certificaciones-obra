package com.obra.certificaciones.calendario.dto;

import com.obra.certificaciones.calendario.entity.EstadoEventoObra;
import com.obra.certificaciones.calendario.entity.TipoEventoObra;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class EventoObraForm {
    private Long id;
    private String titulo;
    private TipoEventoObra tipo = TipoEventoObra.GENERAL;
    private EstadoEventoObra estado = EstadoEventoObra.PROGRAMADO;
    private LocalDate fecha = LocalDate.now();
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String lugar;
    private String responsable;
    private String descripcion;
    private boolean importante = true;
}
