package com.obra.certificaciones.asistencia.entity;

import com.obra.certificaciones.obra.entity.Obra;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Getter
@Setter
public class AsistenciaPersonal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate fecha;
    private Long trabajadorId;

    @Column(length = 500)
    private String trabajadorNombre;

    private String empresa;
    private String sector;
    private LocalTime horaIngreso;
    private LocalTime horaSalida;
    private BigDecimal horasTrabajadas = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    private Obra obra;
    @Column(length = 1200)
    private String observacion;

    public boolean estaEnObra() {
        return horaIngreso != null && horaSalida == null;
    }

    public boolean salioDeObra() {
        return horaIngreso != null && horaSalida != null;
    }

    public boolean registroIncompleto() {
        return horaIngreso == null && horaSalida != null;
    }

    public String getEstadoObra() {
        if (estaEnObra()) {
            return "EN OBRA";
        }
        if (salioDeObra()) {
            return "SALIO";
        }
        if (registroIncompleto()) {
            return "REVISAR";
        }
        return "AUSENTE";
    }

    public String getEstadoObraClase() {
        if (estaEnObra()) {
            return "is-work-open";
        }
        if (salioDeObra()) {
            return "is-work-closed";
        }
        if (registroIncompleto()) {
            return "is-partial";
        }
        return "is-pending";
    }
}
