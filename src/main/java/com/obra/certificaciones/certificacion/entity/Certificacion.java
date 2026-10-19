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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Certificacion {
    // Snapshot: los cambios de modalidad de la OC no reinterpretan certificados previos.
    private Boolean porCantidad = false;
    public boolean isPorCantidad() { return Boolean.TRUE.equals(porCantidad); }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer numero;
    private LocalDate fecha;
    private String observacion;
    @jakarta.persistence.Column(precision = 19, scale = 2)
    private BigDecimal brutoInformado;
    // Nulos: utilizar las condiciones vigentes de la OC hasta guardar una configuracion propia.
    @jakarta.persistence.Column(precision = 7, scale = 4)
    private BigDecimal fondoReparoPorcentaje;
    private Boolean cacHabilitado;
    private String cacMesBase;
    @jakarta.persistence.Column(precision = 19, scale = 6)
    private BigDecimal cacIndiceBase;
    private Integer cacDuracionMeses;
    private Boolean cacAplicar;
    private String cacMesActual;
    @jakarta.persistence.Column(precision = 19, scale = 6)
    private BigDecimal cacIndiceActual;
    @jakarta.persistence.Column(precision = 19, scale = 2)
    private BigDecimal cacMontoManual;

    @ManyToOne(fetch = FetchType.LAZY)
    private OrdenCompra ordenCompra;

    @OneToMany(mappedBy = "certificacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemCertificacion> items = new ArrayList<>();

    public void agregarItem(ItemCertificacion item) {
        item.setCertificacion(this);
        items.add(item);
    }
}
