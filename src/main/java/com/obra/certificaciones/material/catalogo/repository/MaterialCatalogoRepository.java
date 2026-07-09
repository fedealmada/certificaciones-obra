package com.obra.certificaciones.material.catalogo.repository;

import com.obra.certificaciones.material.catalogo.entity.MaterialCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaterialCatalogoRepository extends JpaRepository<MaterialCatalogo, Long> {

    List<MaterialCatalogo> findAllByOrderByNombreAsc();

    List<MaterialCatalogo> findByActivoTrueOrderByNombreAsc();

    List<MaterialCatalogo> findByNombreIgnoreCaseOrderByActivoDescIdAsc(String nombre);

    Optional<MaterialCatalogo> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
