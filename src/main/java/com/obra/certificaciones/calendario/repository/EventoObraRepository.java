package com.obra.certificaciones.calendario.repository;

import com.obra.certificaciones.calendario.entity.EventoObra;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EventoObraRepository extends JpaRepository<EventoObra, Long> {
    @Override
    @EntityGraph(attributePaths = {"obra"})
    Optional<EventoObra> findById(Long id);

    @EntityGraph(attributePaths = {"obra"})
    List<EventoObra> findByObraIdAndActivoTrueAndFechaBetweenOrderByFechaAscHoraInicioAscIdAsc(Long obraId, LocalDate desde, LocalDate hasta);

    @EntityGraph(attributePaths = {"obra"})
    List<EventoObra> findTop8ByObraIdAndActivoTrueAndFechaGreaterThanEqualOrderByFechaAscHoraInicioAscIdAsc(Long obraId, LocalDate desde);

    @EntityGraph(attributePaths = {"obra"})
    List<EventoObra> findTop12ByObraIdAndActivoTrueAndFechaLessThanEqualOrderByFechaDescHoraInicioDescIdDesc(Long obraId, LocalDate hasta);

    @EntityGraph(attributePaths = {"obra"})
    List<EventoObra> findTop20ByObraIdAndActivoTrueOrderByFechaDescHoraInicioDescIdDesc(Long obraId);
}
