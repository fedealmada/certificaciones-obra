package com.obra.certificaciones.proveedor.repository;

import com.obra.certificaciones.proveedor.entity.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {
    List<Proveedor> findByActivoTrueOrderByNombreAsc();
    List<Proveedor> findAllByOrderByNombreAsc();
    List<Proveedor> findByNombreIgnoreCaseOrderByActivoDescIdAsc(String nombre);
    Optional<Proveedor> findByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
