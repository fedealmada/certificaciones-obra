package com.obra.certificaciones.oc.entity;

import com.obra.certificaciones.categoria.entity.CategoriaOrden;
import com.obra.certificaciones.material.catalogo.entity.MaterialCatalogo;
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
import jakarta.persistence.Transient;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(indexes = {
        @Index(name = "idx_item_oc_categoria", columnList = "orden_compra_id, categoria"),
        @Index(name = "idx_item_categoria_entidad", columnList = "categoria_entidad_id"),
        @Index(name = "idx_item_rubro_entidad", columnList = "rubro_entidad_id"),
        @Index(name = "idx_item_material_catalogo", columnList = "material_catalogo_id"),
        @Index(name = "idx_item_mo_vinculado", columnList = "item_mano_obra_vinculado_id")
})
@Getter
@Setter
public class ItemOrdenCompra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private OrdenCompra ordenCompra;

    private String item;
    @Column(length = 2000)
    private String detalle;
    private String unidad;

    @Enumerated(EnumType.STRING)
    private CategoriaItem categoria = CategoriaItem.MANO_OBRA;

    @ManyToOne(fetch = FetchType.LAZY)
    private CategoriaOrden categoriaEntidad;

    @ManyToOne(fetch = FetchType.LAZY)
    private Rubro rubroEntidad;

    @ManyToOne(fetch = FetchType.LAZY)
    private ItemOrdenCompra itemManoObraVinculado;

    @ManyToOne(fetch = FetchType.LAZY)
    private MaterialCatalogo materialCatalogo;

    @Transient
    private Long rubroId;

    @Transient
    private Long itemManoObraVinculadoId;

    @Transient
    private Long materialCatalogoId;

    @Transient
    private Long categoriaId;

    @Column(length = 1000)
    private String rubro;
    private Integer ordenItemizado;
    private BigDecimal cantidad = BigDecimal.ZERO;
    private BigDecimal precioUnitario = BigDecimal.ZERO;
    private BigDecimal importe = BigDecimal.ZERO;

    public void calcularImporte() {
        BigDecimal cantidadSegura = cantidad == null ? BigDecimal.ZERO : cantidad;
        BigDecimal precioSeguro = precioUnitario == null ? BigDecimal.ZERO : precioUnitario;
        importe = cantidadSegura.multiply(precioSeguro).setScale(2, RoundingMode.HALF_UP);
    }
}
