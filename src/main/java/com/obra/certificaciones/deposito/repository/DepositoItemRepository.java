package com.obra.certificaciones.deposito.repository;

import com.obra.certificaciones.deposito.entity.DepositoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepositoItemRepository extends JpaRepository<DepositoItem, Long> {
    List<DepositoItem> findAllByOrderByActivoDescNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
