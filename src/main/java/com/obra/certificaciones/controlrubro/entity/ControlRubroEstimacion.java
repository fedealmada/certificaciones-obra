package com.obra.certificaciones.controlrubro.entity;

import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.rubro.entity.Rubro;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"obra_id", "rubro_id"}))
public class ControlRubroEstimacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Obra obra;

    @ManyToOne(fetch = FetchType.LAZY)
    private Rubro rubro;

    private BigDecimal techoDireccion = BigDecimal.ZERO;
}
