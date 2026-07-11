package com.obra.certificaciones.rubro.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Rubro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigo;
    private Integer ordenItemizado;

    @Column(columnDefinition = "TEXT")
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    private Rubro padre;

    @OneToMany(mappedBy = "padre", cascade = CascadeType.ALL)
    private List<Rubro> hijos = new ArrayList<>();

    @Transient
    private Long padreId;

    private boolean activo = true;

    public String getNombreCompleto() {
        return codigo == null || codigo.isBlank() ? nombre : codigo + " - " + nombre;
    }
}
