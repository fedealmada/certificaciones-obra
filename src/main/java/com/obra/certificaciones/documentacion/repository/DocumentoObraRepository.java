package com.obra.certificaciones.documentacion.repository;

import com.obra.certificaciones.documentacion.entity.DocumentoObra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoObraRepository extends JpaRepository<DocumentoObra, Long> {
    @EntityGraph(attributePaths = {"obra", "proveedor", "trabajador"})
    List<DocumentoObra> findByObraIdAndActivoTrueOrderByFechaVencimientoAscIdDesc(Long obraId);

    @EntityGraph(attributePaths = {"obra", "proveedor", "trabajador"})
    Page<DocumentoObra> findByObraIdAndActivoTrueOrderByFechaVencimientoAscIdDesc(Long obraId, Pageable pageable);

    @EntityGraph(attributePaths = {"obra", "proveedor", "trabajador"})
    List<DocumentoObra> findByObraIdOrderByActivoDescFechaVencimientoAscIdDesc(Long obraId);
}