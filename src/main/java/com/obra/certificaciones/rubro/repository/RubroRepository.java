package com.obra.certificaciones.rubro.repository;

import com.obra.certificaciones.rubro.entity.Rubro;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RubroRepository extends JpaRepository<Rubro, Long> {

    @Override
    @EntityGraph(attributePaths = "padre")
    Optional<Rubro> findById(Long id);

    @EntityGraph(attributePaths = "padre")
    List<Rubro> findAllByOrderByCodigoAscNombreAsc();

    @EntityGraph(attributePaths = "padre")
    List<Rubro> findByActivoTrueOrderByCodigoAscNombreAsc();

    @EntityGraph(attributePaths = "padre")
    List<Rubro> findByPadre_Id(Long padreId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Rubro rubro set rubro.padre = null where rubro.padre is not null")
    int desvincularTodosLosPadres();

    List<Rubro> findByNombreIgnoreCaseOrderByActivoDescIdAsc(String nombre);

    Optional<Rubro> findByNombreIgnoreCase(String nombre);

    boolean existsByPadre_Id(Long padreId);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);
}
