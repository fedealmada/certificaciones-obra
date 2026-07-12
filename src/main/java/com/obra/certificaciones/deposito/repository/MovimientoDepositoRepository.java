package com.obra.certificaciones.deposito.repository;

import com.obra.certificaciones.deposito.entity.MovimientoDeposito;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoDepositoRepository extends JpaRepository<MovimientoDeposito, Long> {
    @EntityGraph(attributePaths = {"item"})
    List<MovimientoDeposito> findTop12ByOrderByFechaDescIdDesc();

    @EntityGraph(attributePaths = {"item"})
    List<MovimientoDeposito> findTop80ByItemIdOrderByFechaDescIdDesc(Long itemId);

    boolean existsByItemId(Long itemId);
}
