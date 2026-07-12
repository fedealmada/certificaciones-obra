package com.obra.certificaciones.obra.repository;

import com.obra.certificaciones.obra.entity.Obra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ObraRepository extends JpaRepository<Obra, Long> {
    List<Obra> findAllByOrderByActivaDescNombreAsc();

    List<Obra> findByActivaTrueOrderByNombreAsc();

    Optional<Obra> findByNombreIgnoreCase(String nombre);
}
