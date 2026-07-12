package com.obra.certificaciones.asistencia.repository;

import com.obra.certificaciones.asistencia.entity.AsistenciaPersonal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AsistenciaPersonalRepository extends JpaRepository<AsistenciaPersonal, Long> {
    List<AsistenciaPersonal> findByFechaOrderByEmpresaAscTrabajadorNombreAsc(LocalDate fecha);

    List<AsistenciaPersonal> findTop80ByOrderByFechaDescIdDesc();

    Optional<AsistenciaPersonal> findByFechaAndTrabajadorId(LocalDate fecha, Long trabajadorId);
}
