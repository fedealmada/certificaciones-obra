package com.obra.certificaciones.tablero.repository;

import com.obra.certificaciones.tablero.entity.TableroCertificado;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TableroCertificadoRepository extends JpaRepository<TableroCertificado, Long> {
    @EntityGraph(attributePaths = {"obra", "items", "items.itemOrdenCompra"})
    List<TableroCertificado> findByObraIdOrderByFechaHastaDescIdDesc(Long obraId);

    @Override
    @EntityGraph(attributePaths = {"obra", "items", "items.itemOrdenCompra"})
    Optional<TableroCertificado> findById(Long id);
}
