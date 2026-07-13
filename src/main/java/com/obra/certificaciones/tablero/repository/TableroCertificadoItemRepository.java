package com.obra.certificaciones.tablero.repository;

import com.obra.certificaciones.tablero.entity.TableroCertificadoItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TableroCertificadoItemRepository extends JpaRepository<TableroCertificadoItem, Long> {
    @EntityGraph(attributePaths = {"tablero", "itemOrdenCompra"})
    List<TableroCertificadoItem> findByTableroIdOrderByOrdenFilaAscIdAsc(Long tableroId);

    boolean existsByTableroIdAndItemOrdenCompraId(Long tableroId, Long itemOrdenCompraId);
}
