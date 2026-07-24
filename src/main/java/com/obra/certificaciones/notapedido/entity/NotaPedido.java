package com.obra.certificaciones.notapedido.entity;

import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.proveedor.entity.Proveedor;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class NotaPedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String numero;
    private LocalDate fecha = LocalDate.now();
    private String solicitante;
    private boolean comparativa;
    private boolean verStock;

    @Enumerated(EnumType.STRING)
    private TipoNotaPedido tipo = TipoNotaPedido.MATERIALES;

    @Enumerated(EnumType.STRING)
    private EstadoNotaPedido estado = EstadoNotaPedido.BORRADOR;

    @Enumerated(EnumType.STRING)
    private PrioridadNotaPedido prioridad = PrioridadNotaPedido.NORMAL;

    @ManyToOne(fetch = FetchType.LAZY)
    private Proveedor proveedorEntidad;

    @ManyToOne(fetch = FetchType.LAZY)
    private OrdenCompra ordenCompra;

    @ManyToOne(fetch = FetchType.LAZY)
    private Obra obra;

    @Column(length = 1500)
    private String observaciones;

    @OneToMany(mappedBy = "notaPedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemNotaPedido> items = new ArrayList<>();

    public void agregarItem(ItemNotaPedido item) {
        item.setNotaPedido(this);
        items.add(item);
    }

    public void reemplazarItems(List<ItemNotaPedido> nuevosItems) {
        items.clear();
        nuevosItems.forEach(this::agregarItem);
    }

    public BigDecimal getTotal() {
        return items.stream()
                .map(item -> item.getImporte() == null ? BigDecimal.ZERO : item.getImporte())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean usaPrecio() {
        return tipo != null && tipo.usaPrecio();
    }
}
