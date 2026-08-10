package com.obra.certificaciones.tarea.entity;

import com.obra.certificaciones.obra.entity.Obra;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(indexes = {
        @Index(name = "idx_tarea_obra_estado", columnList = "obra_id, estado, activo"),
        @Index(name = "idx_tarea_obra_fecha", columnList = "obra_id, fecha_vencimiento, activo")
})
@Getter
@Setter
public class TareaPendiente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Obra obra;

    @Column(length = 220, nullable = false)
    private String titulo;

    @Column(length = 1800)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PrioridadTarea prioridad = PrioridadTarea.MEDIA;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EstadoTarea estado = EstadoTarea.PENDIENTE;

    private LocalDate fechaVencimiento;

    @Column(length = 120)
    private String responsable;

    @Column(length = 120)
    private String etiqueta;

    private boolean activo = true;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaUltimaActualizacion;
    private LocalDateTime fechaCierre;

    @OneToMany(mappedBy = "tarea", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TareaChecklistItem> checklist = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        LocalDateTime ahora = LocalDateTime.now();
        if (fechaCreacion == null) {
            fechaCreacion = ahora;
        }
        fechaUltimaActualizacion = ahora;
        actualizarFechaCierre();
    }

    @PreUpdate
    public void preUpdate() {
        fechaUltimaActualizacion = LocalDateTime.now();
        actualizarFechaCierre();
    }

    public int totalChecklist() {
        return checklist == null ? 0 : checklist.size();
    }

    public int checklistCompletado() {
        return checklist == null ? 0 : (int) checklist.stream().filter(TareaChecklistItem::isCompletado).count();
    }

    public int avanceChecklist() {
        int total = totalChecklist();
        if (total == 0) {
            return estado == EstadoTarea.COMPLETADA ? 100 : 0;
        }
        return Math.round((checklistCompletado() * 100f) / total);
    }

    public boolean vencida() {
        return fechaVencimiento != null && fechaVencimiento.isBefore(LocalDate.now()) && estado != EstadoTarea.COMPLETADA;
    }

    public boolean venceHoy() {
        return fechaVencimiento != null && fechaVencimiento.equals(LocalDate.now()) && estado != EstadoTarea.COMPLETADA;
    }

    public List<TareaChecklistItem> checklistOrdenado() {
        if (checklist == null) {
            return List.of();
        }
        return checklist.stream()
                .sorted(Comparator.comparing(TareaChecklistItem::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private void actualizarFechaCierre() {
        if (estado == EstadoTarea.COMPLETADA && fechaCierre == null) {
            fechaCierre = LocalDateTime.now();
        }
        if (estado != EstadoTarea.COMPLETADA) {
            fechaCierre = null;
        }
    }
}
