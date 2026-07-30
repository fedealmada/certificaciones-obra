package com.obra.certificaciones.documentacion.entity;

import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.proveedor.entity.Proveedor;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(indexes = {
        @Index(name = "idx_carpeta_doc_obra_activo", columnList = "obra_id, activo"),
        @Index(name = "idx_carpeta_doc_proveedor", columnList = "proveedor_id")
})
@Getter
@Setter
public class CarpetaDocumentacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Obra obra;

    @ManyToOne(fetch = FetchType.LAZY)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    private CarpetaDocumentacion padre;

    private String nombre;
    private String apodo;
    private String color = "#facc15";
    private Integer orden = 0;
    private boolean general;
    private boolean activo = true;

    public String nombreVisible() {
        return apodo != null && !apodo.isBlank() ? apodo : nombre;
    }
}
