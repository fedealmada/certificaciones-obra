package com.obra.certificaciones.notapedido.entity;

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
import java.math.RoundingMode;

@Entity
@Getter
@Setter
public class ItemNotaPedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private NotaPedido notaPedido;

    private String item;

    @Column(length = 2000)
    private String detalle;

    private String unidad;
    private BigDecimal cantidad = BigDecimal.ZERO;
    private BigDecimal precioUnitario = BigDecimal.ZERO;
    private BigDecimal importe = BigDecimal.ZERO;

    @Column(length = 1000)
    private String observacion;

    private String base;
    private String entrega;

    public void calcularImporte(boolean usaPrecio) {
        BigDecimal cantidadSegura = cantidad == null ? BigDecimal.ZERO : cantidad;
        BigDecimal precioSeguro = usaPrecio && precioUnitario != null ? precioUnitario : BigDecimal.ZERO;
        importe = cantidadSegura.multiply(precioSeguro).setScale(2, RoundingMode.HALF_UP);
        if (!usaPrecio) {
            precioUnitario = BigDecimal.ZERO;
        }
    }
}
