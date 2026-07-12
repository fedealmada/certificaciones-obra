package com.obra.certificaciones.oc.entity;

import com.obra.certificaciones.certificacion.entity.Certificacion;
import com.obra.certificaciones.proveedor.entity.Proveedor;
import jakarta.persistence.CascadeType;
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
public class OrdenCompra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String numero;
    private LocalDate fecha;
    private LocalDate fechaVigencia;
    private String observacion;
    @Enumerated(EnumType.STRING)
    private ModoSeguimientoOrden modoSeguimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    private Proveedor proveedorEntidad;

    @OneToMany(mappedBy = "ordenCompra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemOrdenCompra> items = new ArrayList<>();

    @OneToMany(mappedBy = "ordenCompra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Certificacion> certificaciones = new ArrayList<>();

    public void agregarItem(ItemOrdenCompra item) {
        item.setOrdenCompra(this);
        items.add(item);
    }

    public void reemplazarItems(List<ItemOrdenCompra> nuevosItems) {
        items.clear();
        nuevosItems.forEach(this::agregarItem);
    }

    public BigDecimal getTotal() {
        return items.stream()
                .map(item -> item.getImporte() == null ? BigDecimal.ZERO : item.getImporte())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public ModoSeguimientoOrden getModoSeguimientoEfectivo() {
        if (modoSeguimiento != null) {
            return modoSeguimiento;
        }
        return items.stream().anyMatch(item -> item.getCategoria() == CategoriaItem.MATERIAL)
                ? ModoSeguimientoOrden.ENTREGA
                : ModoSeguimientoOrden.CERTIFICACION;
    }

    public boolean usaSeguimientoCertificacion() {
        return getModoSeguimientoEfectivo() == ModoSeguimientoOrden.CERTIFICACION;
    }

    public boolean usaSeguimientoEntregas() {
        return getModoSeguimientoEfectivo() == ModoSeguimientoOrden.ENTREGA;
    }

    public boolean usaSoloRegistro() {
        return getModoSeguimientoEfectivo() == ModoSeguimientoOrden.REGISTRO;
    }
}
