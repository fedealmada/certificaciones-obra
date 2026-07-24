package com.obra.certificaciones.notapedido.repository;

import com.obra.certificaciones.notapedido.entity.EstadoNotaPedido;
import com.obra.certificaciones.notapedido.entity.NotaPedido;
import com.obra.certificaciones.notapedido.entity.TipoNotaPedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotaPedidoRepository extends JpaRepository<NotaPedido, Long> {
    @EntityGraph(attributePaths = {"proveedorEntidad", "ordenCompra", "items"})
    @Query("select nota from NotaPedido nota where nota.id = :id")
    Optional<NotaPedido> findWithItemsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"proveedorEntidad", "ordenCompra", "items"})
    @Query(value = """
            select distinct nota from NotaPedido nota
            left join nota.proveedorEntidad proveedor
            where (:termino is null
                   or lower(nota.numero) like lower(concat('%', :termino, '%'))
                   or lower(coalesce(nota.solicitante, '')) like lower(concat('%', :termino, '%'))
                   or lower(coalesce(proveedor.nombre, '')) like lower(concat('%', :termino, '%')))
              and (:tipo is null or nota.tipo = :tipo)
              and (:estado is null or nota.estado = :estado)
            """,
            countQuery = """
            select count(distinct nota.id) from NotaPedido nota
            left join nota.proveedorEntidad proveedor
            where (:termino is null
                   or lower(nota.numero) like lower(concat('%', :termino, '%'))
                   or lower(coalesce(nota.solicitante, '')) like lower(concat('%', :termino, '%'))
                   or lower(coalesce(proveedor.nombre, '')) like lower(concat('%', :termino, '%')))
              and (:tipo is null or nota.tipo = :tipo)
              and (:estado is null or nota.estado = :estado)
            """)
    Page<NotaPedido> buscar(@Param("termino") String termino,
                            @Param("tipo") TipoNotaPedido tipo,
                            @Param("estado") EstadoNotaPedido estado,
                            Pageable pageable);

    long countByObraId(Long obraId);

    boolean existsByNumeroIgnoreCaseAndObraId(String numero, Long obraId);

    boolean existsByNumeroIgnoreCaseAndObraIdAndIdNot(String numero, Long obraId, Long id);
}

