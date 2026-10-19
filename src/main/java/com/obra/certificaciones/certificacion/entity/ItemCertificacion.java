package com.obra.certificaciones.certificacion.entity;

import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
public class ItemCertificacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Certificacion certificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    private ItemOrdenCompra itemOrdenCompra;

    private BigDecimal porcentajeActual = BigDecimal.ZERO;

    @jakarta.persistence.Column(precision = 19, scale = 6)
    private BigDecimal cantidadActual;

    public BigDecimal getPorcentajeActual() {
        if (cantidadActual == null) return porcentajeActual;
        return cantidadActual.multiply(new BigDecimal("100"))
                .divide(itemOrdenCompra.getCantidad(), 16, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal getMontoActual() {
        if (cantidadActual != null) return cantidadActual.multiply(itemOrdenCompra.getPrecioUnitario())
                .setScale(2, java.math.RoundingMode.HALF_UP);
        return itemOrdenCompra.getImporte().multiply(porcentajeActual == null ? BigDecimal.ZERO : porcentajeActual)
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
    }

}
