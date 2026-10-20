package com.obra.certificaciones.tablero.entity;

import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity @Getter @Setter
@Table(uniqueConstraints=@UniqueConstraint(columnNames={"obra_id","clave_tarea","material_id"}),
       indexes=@Index(name="idx_asignacion_material_obra",columnList="obra_id,material_id"))
public class MaterialAsignadoTablero {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long obraId;
    @Column(nullable=false) private String claveTarea;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) private ItemOrdenCompra material;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal importe;
}
