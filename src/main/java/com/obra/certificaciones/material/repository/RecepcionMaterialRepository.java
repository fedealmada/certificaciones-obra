package com.obra.certificaciones.material.repository;

import com.obra.certificaciones.material.entity.RecepcionMaterial;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecepcionMaterialRepository extends JpaRepository<RecepcionMaterial, Long> {
    @Override
    @EntityGraph(attributePaths = {"ordenCompra", "items", "items.itemOrdenCompra"})
    Optional<RecepcionMaterial> findById(Long id);

    @EntityGraph(attributePaths = {"items", "items.itemOrdenCompra"})
    List<RecepcionMaterial> findByOrdenCompraIdOrderByFechaAscIdAsc(Long ordenCompraId);

    long countByOrdenCompraId(Long ordenCompraId);

    @Query("""
            select recepcion.ordenCompra.id, count(recepcion)
            from RecepcionMaterial recepcion
            where recepcion.ordenCompra.id in :ordenCompraIds
            group by recepcion.ordenCompra.id
            """)
    List<Object[]> countByOrdenCompraIds(@Param("ordenCompraIds") List<Long> ordenCompraIds);
}
