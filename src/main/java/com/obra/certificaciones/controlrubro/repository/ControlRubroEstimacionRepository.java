package com.obra.certificaciones.controlrubro.repository;

import com.obra.certificaciones.controlrubro.entity.ControlRubroEstimacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ControlRubroEstimacionRepository extends JpaRepository<ControlRubroEstimacion, Long> {
    @EntityGraph(attributePaths = {"rubro"})
    List<ControlRubroEstimacion> findByObraId(Long obraId);

    Optional<ControlRubroEstimacion> findByObraIdAndRubroId(Long obraId, Long rubroId);
}
