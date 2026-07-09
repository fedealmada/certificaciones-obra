package com.obra.certificaciones.material.entity;

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
public class RecepcionMaterial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate fecha;
    private String remito;
    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY)
    private OrdenCompra ordenCompra;

    @OneToMany(mappedBy = "recepcionMaterial", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemRecepcionMaterial> items = new ArrayList<>();

    public void agregarItem(ItemRecepcionMaterial item) {
        item.setRecepcionMaterial(this);
        items.add(item);
    }
}
