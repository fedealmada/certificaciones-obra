package com.obra.certificaciones.tarea.repository;

import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.tarea.entity.EstadoTarea;
import com.obra.certificaciones.tarea.entity.TareaPendiente;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TareaPendienteRepository extends JpaRepository<TareaPendiente, Long> {
    @EntityGraph(attributePaths = "checklist")
    List<TareaPendiente> findByObraAndActivoTrueAndEstadoInOrderByFechaVencimientoAscPrioridadDescIdDesc(Obra obra, Collection<EstadoTarea> estados);

    List<TareaPendiente> findTop30ByObraAndActivoTrueAndEstadoOrderByFechaCierreDescIdDesc(Obra obra, EstadoTarea estado);
}
