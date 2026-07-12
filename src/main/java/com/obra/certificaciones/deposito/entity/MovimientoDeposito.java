package com.obra.certificaciones.deposito.entity;

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
import java.time.LocalDate;

@Entity
@Getter
@Setter
public class MovimientoDeposito {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private DepositoItem item;

    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    private TipoMovimientoDeposito tipo;

    private BigDecimal cantidad = BigDecimal.ZERO;
    private BigDecimal stockAnterior = BigDecimal.ZERO;
    private BigDecimal stockResultante = BigDecimal.ZERO;
    private String responsable;
    private String destino;

    @Column(length = 1200)
    private String observacion;

    private Long ordenCompraId;
    private Long recepcionMaterialId;
    private Long itemRecepcionMaterialId;
    private String ordenCompraNumero;
}
