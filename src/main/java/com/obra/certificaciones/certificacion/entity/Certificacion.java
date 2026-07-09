package com.obra.certificaciones.certificacion.entity;

import com.obra.certificaciones.oc.entity.OrdenCompra;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Certificacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer numero;
    private LocalDate fecha;
    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY)
    private OrdenCompra ordenCompra;

    @OneToMany(mappedBy = "certificacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemCertificacion> items = new ArrayList<>();

    public void agregarItem(ItemCertificacion item) {
        item.setCertificacion(this);
        items.add(item);
    }
}
