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
    private Integer numero;
    private String cliente;
    private String empresa;
    private Boolean emitido = false;
    private Long anteriorId;
    private String origenImportacion;
    @Column(precision = 19, scale = 2)
    private BigDecimal ajusteAnterior = BigDecimal.ZERO;
    @Column(precision = 19, scale = 2)
    private BigDecimal ajusteActual = BigDecimal.ZERO;
    @Column(precision=19, scale=2)
    private BigDecimal ajusteAcumulado = BigDecimal.ZERO;
    @Column(precision = 19, scale = 2)
    private BigDecimal presupuestoFuente;
    private BigDecimal seguro(BigDecimal n) { return n == null ? BigDecimal.ZERO : n; }
    public boolean tieneConciliacion() { return seguro(ajusteAnterior).signum()!=0 || seguro(ajusteActual).signum()!=0 || seguro(ajusteAcumulado).signum()!=0; }


    public boolean isCerrado() { return Boolean.TRUE.equals(emitido); }
    public BigDecimal montoAnterior() { return items.stream().map(TableroCertificadoItem::montoAnterior).reduce(BigDecimal.ZERO, BigDecimal::add).add(seguro(ajusteAnterior)); }
    public BigDecimal montoAcumulado() { return montoAnterior().add(montoCertificado()).add(seguro(ajusteAcumulado)); }

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
        return items.stream().filter(i -> !i.isRecurrente())
                .map(TableroCertificadoItem::totalTarea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal montoCertificado() {
        return items.stream()
                .map(TableroCertificadoItem::montoCertificado)
                .reduce(BigDecimal.ZERO, BigDecimal::add).add(seguro(ajusteActual));
    }
}
