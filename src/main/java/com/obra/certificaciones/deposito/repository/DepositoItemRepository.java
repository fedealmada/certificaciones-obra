package com.obra.certificaciones.deposito.repository;

import com.obra.certificaciones.deposito.entity.DepositoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepositoItemRepository extends JpaRepository<DepositoItem, Long> {
    List<DepositoItem> findAllByOrderByActivoDescNombreAsc();

    List<DepositoItem> findByObraIdOrderByActivoDescNombreAsc(Long obraId);

    List<DepositoItem> findByObraIsNull();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    boolean existsByNombreIgnoreCaseAndObraId(String nombre, Long obraId);

    boolean existsByNombreIgnoreCaseAndObraIdAndIdNot(String nombre, Long obraId, Long id);
}
