package com.obra.certificaciones.certificacion.repository;

import com.obra.certificaciones.certificacion.entity.Certificacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CertificacionRepository extends JpaRepository<Certificacion, Long> {
    @Override
    @EntityGraph(attributePaths = {"ordenCompra", "items", "items.itemOrdenCompra"})
    Optional<Certificacion> findById(Long id);

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
}
