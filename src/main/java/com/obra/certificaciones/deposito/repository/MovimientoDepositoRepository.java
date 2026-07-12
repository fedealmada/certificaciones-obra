package com.obra.certificaciones.deposito.repository;

import com.obra.certificaciones.deposito.entity.MovimientoDeposito;
import com.obra.certificaciones.deposito.entity.TipoMovimientoDeposito;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MovimientoDepositoRepository extends JpaRepository<MovimientoDeposito, Long> {
    @Override
    @EntityGraph(attributePaths = {"item"})
    Optional<MovimientoDeposito> findById(Long id);

    @EntityGraph(attributePaths = {"item"})
    List<MovimientoDeposito> findTop12ByOrderByFechaDescIdDesc();

    @EntityGraph(attributePaths = {"item"})
    List<MovimientoDeposito> findTop12ByItemObraIdOrderByFechaDescIdDesc(Long obraId);

    @EntityGraph(attributePaths = {"item"})
    List<MovimientoDeposito> findTop80ByItemIdOrderByFechaDescIdDesc(Long itemId);

    List<MovimientoDeposito> findByItemIdOrderByFechaAscIdAsc(Long itemId);

    @EntityGraph(attributePaths = {"item"})
    List<MovimientoDeposito> findByTipoAndRequiereDevolucionTrueAndDevueltoFalseOrderByFechaAscIdAsc(TipoMovimientoDeposito tipo);

    @EntityGraph(attributePaths = {"item"})
    List<MovimientoDeposito> findByItemObraIdAndTipoAndRequiereDevolucionTrueAndDevueltoFalseOrderByFechaAscIdAsc(Long obraId, TipoMovimientoDeposito tipo);

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
