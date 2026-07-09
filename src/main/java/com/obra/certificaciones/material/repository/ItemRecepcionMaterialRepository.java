package com.obra.certificaciones.material.repository;

import com.obra.certificaciones.material.entity.ItemRecepcionMaterial;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemRecepcionMaterialRepository extends JpaRepository<ItemRecepcionMaterial, Long> {
    @EntityGraph(attributePaths = {"recepcionMaterial", "itemOrdenCompra"})
    List<ItemRecepcionMaterial> findByRecepcionMaterialOrdenCompraIdOrderByRecepcionMaterialFechaAscRecepcionMaterialIdAsc(Long ordenCompraId);
}
