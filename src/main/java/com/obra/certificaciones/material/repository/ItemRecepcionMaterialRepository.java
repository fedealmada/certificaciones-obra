package com.obra.certificaciones.material.repository;

import com.obra.certificaciones.material.entity.ItemRecepcionMaterial;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRecepcionMaterialRepository extends JpaRepository<ItemRecepcionMaterial, Long> {
    @Override
    @EntityGraph(attributePaths = {"recepcionMaterial", "recepcionMaterial.ordenCompra", "itemOrdenCompra", "itemOrdenCompra.materialCatalogo"})
    Optional<ItemRecepcionMaterial> findById(Long id);

    @EntityGraph(attributePaths = {"recepcionMaterial", "itemOrdenCompra"})
    List<ItemRecepcionMaterial> findByRecepcionMaterialOrdenCompraIdOrderByRecepcionMaterialFechaAscRecepcionMaterialIdAsc(Long ordenCompraId);

    @Query("""
            select item.recepcionMaterial.ordenCompra.id, item.itemOrdenCompra.id, sum(item.cantidadRecibida)
            from ItemRecepcionMaterial item
            where item.recepcionMaterial.ordenCompra.id in :ordenCompraIds
            group by item.recepcionMaterial.ordenCompra.id, item.itemOrdenCompra.id
            """)
    List<Object[]> sumCantidadesByOrdenCompraIds(@Param("ordenCompraIds") List<Long> ordenCompraIds);
}
