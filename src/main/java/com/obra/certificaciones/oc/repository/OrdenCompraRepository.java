package com.obra.certificaciones.oc.repository;

import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ModoSeguimientoOrden;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @EntityGraph(attributePaths = {"items", "items.categoriaEntidad", "items.rubroEntidad", "items.itemManoObraVinculado", "items.materialCatalogo", "proveedorEntidad", "obra"})
    @Query("""
            select distinct oc from OrdenCompra oc
            left join oc.items item
            left join item.rubroEntidad rubroEntidad
            left join oc.proveedorEntidad proveedorEntidad
            where oc.obra.id = :obraId
              and (:proveedor is null or lower(proveedorEntidad.nombre) like lower(concat('%', :proveedor, '%')))
              and (:categoriaId is null or item.categoriaEntidad.id = :categoriaId)
              and (:rubro is null
                   or lower(rubroEntidad.nombre) like lower(concat('%', :rubro, '%'))
                   or lower(rubroEntidad.codigo) like lower(concat('%', :rubro, '%')))
            order by oc.fecha desc, oc.numero desc
            """)
    List<OrdenCompra> buscarConFiltros(@Param("obraId") Long obraId,
                                       @Param("proveedor") String proveedor,
                                       @Param("categoriaId") Long categoriaId,
                                       @Param("rubro") String rubro);

    @Query(value = """
            select oc.id from OrdenCompra oc
            left join oc.proveedorEntidad proveedorEntidad
            where oc.obra.id = :obraId
              and (:proveedor is null
                   or lower(proveedorEntidad.nombre) like lower(concat('%', :proveedor, '%'))
                   or lower(oc.numero) like lower(concat('%', :proveedor, '%')))
              and (:categoriaId is null
                   or exists (
                       select 1 from ItemOrdenCompra itemCategoria
                       where itemCategoria.ordenCompra = oc
                         and itemCategoria.categoriaEntidad.id = :categoriaId
                   ))
              and (:rubro is null
                   or exists (
                       select 1 from ItemOrdenCompra itemRubro
                       left join itemRubro.rubroEntidad rubroEntidad
                       where itemRubro.ordenCompra = oc
                         and (lower(rubroEntidad.nombre) like lower(concat('%', :rubro, '%'))
                              or lower(rubroEntidad.codigo) like lower(concat('%', :rubro, '%')))
                   ))
            order by oc.fecha desc, oc.id desc
            """,
            countQuery = """
            select count(oc.id) from OrdenCompra oc
            left join oc.proveedorEntidad proveedorEntidad
            where oc.obra.id = :obraId
              and (:proveedor is null
                   or lower(proveedorEntidad.nombre) like lower(concat('%', :proveedor, '%'))
                   or lower(oc.numero) like lower(concat('%', :proveedor, '%')))
              and (:categoriaId is null
                   or exists (
                       select 1 from ItemOrdenCompra itemCategoria
                       where itemCategoria.ordenCompra = oc
                         and itemCategoria.categoriaEntidad.id = :categoriaId
                   ))
              and (:rubro is null
                   or exists (
                       select 1 from ItemOrdenCompra itemRubro
                       left join itemRubro.rubroEntidad rubroEntidad
                       where itemRubro.ordenCompra = oc
                         and (lower(rubroEntidad.nombre) like lower(concat('%', :rubro, '%'))
                              or lower(rubroEntidad.codigo) like lower(concat('%', :rubro, '%')))
                   ))
            """)
    Page<Long> buscarIdsConFiltros(@Param("obraId") Long obraId,
                                   @Param("proveedor") String proveedor,
                                   @Param("categoriaId") Long categoriaId,
                                   @Param("rubro") String rubro,
                                   Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.categoriaEntidad", "items.rubroEntidad", "items.itemManoObraVinculado", "items.materialCatalogo", "proveedorEntidad", "obra"})
    @Query("""
            select distinct oc from OrdenCompra oc
            join oc.items item
            left join oc.proveedorEntidad proveedorEntidad
            where item.categoria = :categoria
              and oc.obra.id = :obraId
            order by oc.fecha desc, oc.numero desc
            """)
    List<OrdenCompra> buscarPorTipoCategoria(@Param("obraId") Long obraId, @Param("categoria") CategoriaItem categoria);

    @Query(value = """
            select oc.id from OrdenCompra oc
            where oc.obra.id = :obraId
              and exists (
                select 1 from ItemOrdenCompra item
                left join item.categoriaEntidad categoriaEntidad
                where item.ordenCompra = oc
                  and (item.categoria = :categoria or categoriaEntidad.tipo = :categoria)
              )
            order by oc.fecha desc, oc.id desc
            """,
            countQuery = """
            select count(oc.id) from OrdenCompra oc
            where oc.obra.id = :obraId
              and exists (
                select 1 from ItemOrdenCompra item
                left join item.categoriaEntidad categoriaEntidad
                where item.ordenCompra = oc
                  and (item.categoria = :categoria or categoriaEntidad.tipo = :categoria)
              )
            """)
    Page<Long> buscarIdsPorTipoCategoria(@Param("obraId") Long obraId, @Param("categoria") CategoriaItem categoria, Pageable pageable);

    @Query(value = """
            select oc.id from OrdenCompra oc
            where oc.obra.id = :obraId
              and (oc.modoSeguimiento = :modoEntrega
                   or (oc.modoSeguimiento is null and exists (
                       select 1 from ItemOrdenCompra item
                       left join item.categoriaEntidad categoriaEntidad
                       where item.ordenCompra = oc
                         and (item.categoria = :categoriaMaterial or categoriaEntidad.tipo = :categoriaMaterial)
                   )))
            order by oc.fecha desc, oc.id desc
            """,
            countQuery = """
            select count(oc.id) from OrdenCompra oc
            where oc.obra.id = :obraId
              and (oc.modoSeguimiento = :modoEntrega
                   or (oc.modoSeguimiento is null and exists (
                       select 1 from ItemOrdenCompra item
                       left join item.categoriaEntidad categoriaEntidad
                       where item.ordenCompra = oc
                         and (item.categoria = :categoriaMaterial or categoriaEntidad.tipo = :categoriaMaterial)
                   )))
            """)
    Page<Long> buscarIdsConSeguimientoEntregas(@Param("obraId") Long obraId,
                                               @Param("modoEntrega") ModoSeguimientoOrden modoEntrega,
                                               @Param("categoriaMaterial") CategoriaItem categoriaMaterial,
                                               Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.categoriaEntidad", "items.rubroEntidad", "items.itemManoObraVinculado", "items.materialCatalogo", "proveedorEntidad", "obra"})
    List<OrdenCompra> findByIdIn(List<Long> ids);

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

    @EntityGraph(attributePaths = {"items", "items.categoriaEntidad", "items.rubroEntidad", "items.itemManoObraVinculado", "items.materialCatalogo", "proveedorEntidad"})
    List<OrdenCompra> findTop5ByObraIdOrderByFechaDescIdDesc(Long obraId);

    List<OrdenCompra> findByObraIsNull();
}