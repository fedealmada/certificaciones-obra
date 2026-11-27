package com.obra.certificaciones.certificacion.repository;

import com.obra.certificaciones.certificacion.entity.Certificacion;
import com.obra.certificaciones.certificacion.dto.UltimoCertificadoOrden;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CertificacionRepository extends JpaRepository<Certificacion, Long> {
    @Override
    @EntityGraph(attributePaths = {"ordenCompra", "items", "items.itemOrdenCompra"})
    Optional<Certificacion> findById(Long id);

    @EntityGraph(attributePaths = {"ordenCompra", "items", "items.itemOrdenCompra"})
    List<Certificacion> findByOrdenCompraIdOrderByFechaAscIdAsc(Long ordenCompraId);
    long countByOrdenCompraId(Long ordenCompraId);
    long countByOrdenCompraObraId(Long obraId);
    boolean existsByOrdenCompraIdAndNumero(Long ordenCompraId, Integer numero);

    @Query("""
            select c.ordenCompra.id, count(c)
            from Certificacion c
            where c.ordenCompra.id in :ordenCompraIds
            group by c.ordenCompra.id
            """)
    List<Object[]> countByOrdenCompraIds(@Param("ordenCompraIds") List<Long> ordenCompraIds);

    @Query("""
            select new com.obra.certificaciones.certificacion.dto.UltimoCertificadoOrden(c.ordenCompra.id, c.numero, c.fecha)
            from Certificacion c
            where c.ordenCompra.id in :ordenCompraIds
              and c.fecha is not null
              and not exists (
                  select c2.id from Certificacion c2
                  where c2.ordenCompra.id = c.ordenCompra.id
                    and c2.fecha is not null
                    and (c2.fecha > c.fecha or (c2.fecha = c.fecha and c2.id > c.id))
              )
            """)
    List<UltimoCertificadoOrden> ultimosPorOrdenCompraIds(@Param("ordenCompraIds") List<Long> ordenCompraIds);

    @Query("""
            select distinct c.ordenCompra.id
            from Certificacion c
            where c.ordenCompra.id in :ordenCompraIds
              and (c.fechaUltimaModificacion between :desde and :hasta
                   or (c.fechaUltimaModificacion is null and c.fecha between :desde and :hasta))
            """)
    List<Long> ordenesConCertificadosModificadosEnPeriodo(@Param("ordenCompraIds") List<Long> ordenCompraIds,
                                                           @Param("desde") LocalDate desde,
                                                           @Param("hasta") LocalDate hasta);
}
