package com.obra.certificaciones.calendario.entity;

import com.obra.certificaciones.obra.entity.Obra;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(indexes = {
        @Index(name = "idx_evento_obra_fecha", columnList = "obra_id, fecha, activo"),
        @Index(name = "idx_evento_obra_estado", columnList = "obra_id, estado, activo")
})
@Getter
@Setter
public class EventoObra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Obra obra;

    @Column(length = 220, nullable = false)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private TipoEventoObra tipo = TipoEventoObra.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EstadoEventoObra estado = EstadoEventoObra.PROGRAMADO;

    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;

    @Column(length = 300)
    private String lugar;

    @Column(length = 180)
    private String responsable;

    @Column(length = 1200)
    private String descripcion;

    private boolean importante = true;
    private boolean activo = true;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaUltimaActualizacion;

    @PrePersist
    public void prePersist() {
        LocalDateTime ahora = LocalDateTime.now();
        if (fechaCreacion == null) {
            fechaCreacion = ahora;
        }
        fechaUltimaActualizacion = ahora;
    }

    @PreUpdate
    public void preUpdate() {
        fechaUltimaActualizacion = LocalDateTime.now();
    }

    public boolean historico() {
        return estado != EstadoEventoObra.PROGRAMADO || (fecha != null && fecha.isBefore(LocalDate.now()));
    }

    public boolean hoy() {
        return fecha != null && fecha.equals(LocalDate.now());
    }
}
