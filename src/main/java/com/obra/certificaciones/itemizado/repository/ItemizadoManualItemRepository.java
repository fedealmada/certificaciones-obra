package com.obra.certificaciones.itemizado.repository;

import com.obra.certificaciones.itemizado.entity.ItemizadoManualItem;
import com.obra.certificaciones.itemizado.entity.TipoItemizadoManual;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemizadoManualItemRepository extends JpaRepository<ItemizadoManualItem, Long> {
    @EntityGraph(attributePaths = {"rubro", "rubro.padre", "itemPadre"})
    List<ItemizadoManualItem> findByActivoTrueOrderByOrdenAscIdAsc();

    @EntityGraph(attributePaths = {"rubro", "rubro.padre"})
    List<ItemizadoManualItem> findByActivoTrueAndTipoOrderByOrdenAscIdAsc(TipoItemizadoManual tipo);

    boolean existsByRubroIdAndActivoTrue(Long rubroId);
}
