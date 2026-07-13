package com.obra.certificaciones.tablero.repository;

import com.obra.certificaciones.tablero.entity.TableroCertificadoItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TableroCertificadoItemRepository extends JpaRepository<TableroCertificadoItem, Long> {
    @EntityGraph(attributePaths = {"tablero", "itemOrdenCompra"})
    List<TableroCertificadoItem> findByTableroIdOrderByOrdenFilaAscIdAsc(Long tableroId);

    Optional<TableroCertificadoItem> findByTableroIdAndItemOrdenCompraId(Long tableroId, Long itemOrdenCompraId);

    boolean existsByTableroIdAndItemOrdenCompraId(Long tableroId, Long itemOrdenCompraId);
}
