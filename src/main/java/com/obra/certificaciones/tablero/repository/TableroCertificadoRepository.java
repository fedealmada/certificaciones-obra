package com.obra.certificaciones.tablero.repository;

import com.obra.certificaciones.tablero.entity.TableroCertificado;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface TableroCertificadoRepository extends JpaRepository<TableroCertificado, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from TableroCertificado t where t.id = :id")
    Optional<TableroCertificado> bloquear(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByAnteriorId(Long anteriorId);

    @EntityGraph(attributePaths = {"obra", "items", "items.itemOrdenCompra"})
    List<TableroCertificado> findByObraIdOrderByFechaHastaDescIdDesc(Long obraId);

    @Override
    @EntityGraph(attributePaths = {"obra", "items", "items.itemOrdenCompra"})
    Optional<TableroCertificado> findById(Long id);

    @EntityGraph(attributePaths = {"obra", "items", "items.itemOrdenCompra"})
    Optional<TableroCertificado> findFirstByObraIdAndFechaHastaLessThanEqualOrderByFechaHastaDescIdDesc(Long obraId, LocalDate fechaHasta);
}
