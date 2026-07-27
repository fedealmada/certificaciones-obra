package com.obra.certificaciones.itemizado.entity;

import com.obra.certificaciones.rubro.entity.Rubro;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(indexes = {
        @Index(name = "idx_itemizado_manual_rubro_tipo", columnList = "rubro_id, tipo"),
        @Index(name = "idx_itemizado_manual_padre", columnList = "item_padre_id")
})
@Getter
@Setter
public class ItemizadoManualItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Rubro rubro;

    @ManyToOne(fetch = FetchType.LAZY)
    private ItemizadoManualItem itemPadre;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private TipoItemizadoManual tipo = TipoItemizadoManual.MANO_OBRA;

    @Column(length = 80)
    private String item;

    @Column(length = 2000, nullable = false)
    private String detalle;

    @Column(length = 40)
    private String unidad;

    private BigDecimal cantidad = BigDecimal.ZERO;
    private BigDecimal precioUnitario = BigDecimal.ZERO;
    private BigDecimal importe = BigDecimal.ZERO;
    private Integer orden;
    private boolean activo = true;

    public void calcularImporte() {
        BigDecimal cantidadSegura = cantidad == null ? BigDecimal.ZERO : cantidad;
        BigDecimal precioSeguro = precioUnitario == null ? BigDecimal.ZERO : precioUnitario;
        importe = cantidadSegura.multiply(precioSeguro).setScale(2, RoundingMode.HALF_UP);
    }
}
