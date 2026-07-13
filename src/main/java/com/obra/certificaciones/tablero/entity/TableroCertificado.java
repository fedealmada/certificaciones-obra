package com.obra.certificaciones.tablero.entity;

import com.obra.certificaciones.obra.entity.Obra;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class TableroCertificado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Obra obra;

    private String nombre;
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;

    @Column(length = 1200)
    private String observacion;

    @OneToMany(mappedBy = "tablero", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordenFila ASC, id ASC")
    private List<TableroCertificadoItem> items = new ArrayList<>();

    public void agregarItem(TableroCertificadoItem item) {
        item.setTablero(this);
        items.add(item);
    }

    public BigDecimal totalTarea() {
        return items.stream()
                .map(TableroCertificadoItem::totalTarea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal montoCertificado() {
        return items.stream()
                .map(TableroCertificadoItem::montoCertificado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
