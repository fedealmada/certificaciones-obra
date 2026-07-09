package com.obra.certificaciones.categoria.entity;

import com.obra.certificaciones.oc.entity.CategoriaItem;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class CategoriaOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String nombre;

    @Enumerated(EnumType.STRING)
    private CategoriaItem tipo = CategoriaItem.OTRO;

    private boolean activo = true;
}
