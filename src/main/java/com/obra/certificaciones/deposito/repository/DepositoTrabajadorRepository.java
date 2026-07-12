package com.obra.certificaciones.deposito.repository;

import com.obra.certificaciones.deposito.entity.DepositoTrabajador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepositoTrabajadorRepository extends JpaRepository<DepositoTrabajador, Long> {
    List<DepositoTrabajador> findAllByOrderByActivoDescNombreAsc();

    List<DepositoTrabajador> findByActivoTrueOrderByNombreAsc();

    Optional<DepositoTrabajador> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
