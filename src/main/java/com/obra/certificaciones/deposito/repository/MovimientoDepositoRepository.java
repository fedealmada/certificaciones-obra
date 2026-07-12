package com.obra.certificaciones.deposito.repository;

import com.obra.certificaciones.deposito.entity.MovimientoDeposito;
import com.obra.certificaciones.deposito.entity.TipoMovimientoDeposito;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface MovimientoDepositoRepository extends JpaRepository<MovimientoDeposito, Long> {
    @EntityGraph(attributePaths = {"item"})
    List<MovimientoDeposito> findTop12ByOrderByFechaDescIdDesc();

    @EntityGraph(attributePaths = {"item"})
    List<MovimientoDeposito> findTop80ByItemIdOrderByFechaDescIdDesc(Long itemId);

    boolean existsByItemId(Long itemId);

    @Query("""
            select sum(movimiento.cantidad)
            from MovimientoDeposito movimiento
            where movimiento.itemRecepcionMaterialId = :itemRecepcionMaterialId
              and movimiento.tipo = :tipo
            """)
    BigDecimal sumCantidadByItemRecepcionMaterialIdAndTipo(@Param("itemRecepcionMaterialId") Long itemRecepcionMaterialId,
                                                           @Param("tipo") TipoMovimientoDeposito tipo);
}
