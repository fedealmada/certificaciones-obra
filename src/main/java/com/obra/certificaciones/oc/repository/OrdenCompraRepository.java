package com.obra.certificaciones.oc.repository;

import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long> {
    @Override
    @EntityGraph(attributePaths = {"items", "items.categoriaEntidad", "items.rubroEntidad", "items.itemManoObraVinculado", "items.materialCatalogo", "proveedorEntidad"})
    List<OrdenCompra> findAll();

    @Override
    @EntityGraph(attributePaths = {"items", "items.categoriaEntidad", "items.rubroEntidad", "items.itemManoObraVinculado", "items.materialCatalogo", "proveedorEntidad"})
    Optional<OrdenCompra> findById(Long id);

    @EntityGraph(attributePaths = {"items", "items.categoriaEntidad", "items.rubroEntidad", "items.itemManoObraVinculado", "items.materialCatalogo", "proveedorEntidad"})
    @Query("""
            select distinct oc from OrdenCompra oc
            left join oc.items item
            left join item.rubroEntidad rubroEntidad
            left join oc.proveedorEntidad proveedorEntidad
            where (:proveedor is null or lower(proveedorEntidad.nombre) like lower(concat('%', :proveedor, '%')))
              and (:categoriaId is null or item.categoriaEntidad.id = :categoriaId)
              and (:rubro is null
                   or lower(rubroEntidad.nombre) like lower(concat('%', :rubro, '%'))
                   or lower(rubroEntidad.codigo) like lower(concat('%', :rubro, '%')))
            order by oc.fecha desc, oc.numero desc
            """)
    List<OrdenCompra> buscarConFiltros(@Param("proveedor") String proveedor,
                                       @Param("categoriaId") Long categoriaId,
                                       @Param("rubro") String rubro);

    @EntityGraph(attributePaths = {"items", "items.categoriaEntidad", "items.rubroEntidad", "items.itemManoObraVinculado", "items.materialCatalogo", "proveedorEntidad"})
    @Query("""
            select distinct oc from OrdenCompra oc
            join oc.items item
            left join oc.proveedorEntidad proveedorEntidad
            where item.categoria = :categoria
            order by oc.fecha desc, oc.numero desc
            """)
    List<OrdenCompra> buscarPorTipoCategoria(@Param("categoria") CategoriaItem categoria);

    boolean existsByNumeroIgnoreCaseAndProveedorEntidadId(String numero, Long proveedorId);

    boolean existsByNumeroIgnoreCaseAndProveedorEntidadIdAndIdNot(String numero, Long proveedorId, Long id);

    @EntityGraph(attributePaths = {"items", "items.categoriaEntidad", "items.rubroEntidad", "items.itemManoObraVinculado", "items.materialCatalogo", "proveedorEntidad"})
    List<OrdenCompra> findByNumeroIgnoreCaseOrderByIdAsc(String numero);

    @EntityGraph(attributePaths = {"items", "proveedorEntidad"})
    List<OrdenCompra> findByNumeroIgnoreCaseAndProveedorEntidadIdOrderByIdAsc(String numero, Long proveedorId);

    @EntityGraph(attributePaths = {"items", "items.categoriaEntidad", "items.rubroEntidad", "items.itemManoObraVinculado", "items.materialCatalogo", "proveedorEntidad"})
    List<OrdenCompra> findByProveedorEntidadIdOrderByFechaDescIdDesc(Long proveedorId);

    @EntityGraph(attributePaths = {"items", "items.categoriaEntidad", "items.rubroEntidad", "items.itemManoObraVinculado", "items.materialCatalogo", "proveedorEntidad"})
    List<OrdenCompra> findTop5ByOrderByFechaDescIdDesc();
}
