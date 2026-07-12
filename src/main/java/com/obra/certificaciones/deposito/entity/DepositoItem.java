package com.obra.certificaciones.deposito.entity;

import com.obra.certificaciones.obra.entity.Obra;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class DepositoItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 600)
    private String nombre;
    private String categoria;
    private String unidad;
    private String ubicacion;
    private BigDecimal stockActual = BigDecimal.ZERO;
    private BigDecimal stockMinimo = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private TipoInsumoDeposito tipo = TipoInsumoDeposito.CONSUMIBLE;

    @Column(length = 1200)
    private String observacion;
    private boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    private Obra obra;

    public boolean bajoStock() {
        BigDecimal actual = stockActual == null ? BigDecimal.ZERO : stockActual;
        BigDecimal minimo = stockMinimo == null ? BigDecimal.ZERO : stockMinimo;
        return minimo.compareTo(BigDecimal.ZERO) > 0 && actual.compareTo(minimo) <= 0;
    }
}
