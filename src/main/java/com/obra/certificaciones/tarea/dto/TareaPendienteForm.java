package com.obra.certificaciones.tarea.dto;

import com.obra.certificaciones.tarea.entity.EstadoTarea;
import com.obra.certificaciones.tarea.entity.PrioridadTarea;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class TareaPendienteForm {
    private Long id;
    private String titulo;
    private String descripcion;
    private PrioridadTarea prioridad = PrioridadTarea.MEDIA;
    private EstadoTarea estado = EstadoTarea.PENDIENTE;
    private LocalDate fechaVencimiento;
    private String responsable;
    private String etiqueta;
}
