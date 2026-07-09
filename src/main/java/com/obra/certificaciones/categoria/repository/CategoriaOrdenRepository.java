package com.obra.certificaciones.categoria.repository;

import com.obra.certificaciones.categoria.entity.CategoriaOrden;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaOrdenRepository extends JpaRepository<CategoriaOrden, Long> {

    List<CategoriaOrden> findAllByOrderByNombreAsc();

    List<CategoriaOrden> findByActivoTrueOrderByNombreAsc();

    List<CategoriaOrden> findByNombreIgnoreCaseOrderByActivoDescIdAsc(String nombre);

    Optional<CategoriaOrden> findByNombreIgnoreCase(String nombre);

    List<CategoriaOrden> findByTipoAndNombreIgnoreCaseOrderByActivoDescIdAsc(CategoriaItem tipo, String nombre);

    Optional<CategoriaOrden> findByTipoAndNombreIgnoreCase(CategoriaItem tipo, String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
