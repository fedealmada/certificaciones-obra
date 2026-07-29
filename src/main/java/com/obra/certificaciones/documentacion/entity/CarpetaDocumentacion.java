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

    private String nombre;
    private boolean general;
    private boolean activo = true;
}
