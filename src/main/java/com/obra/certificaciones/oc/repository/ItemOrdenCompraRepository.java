package com.obra.certificaciones.oc.repository;

import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemOrdenCompraRepository extends JpaRepository<ItemOrdenCompra, Long> {
    @Override
    @EntityGraph(attributePaths = {"ordenCompra", "rubroEntidad"})
    Optional<ItemOrdenCompra> findById(Long id);

    @EntityGraph(attributePaths = {"ordenCompra", "ordenCompra.proveedorEntidad", "rubroEntidad", "itemManoObraVinculado", "materialCatalogo", "categoriaEntidad"})
    List<ItemOrdenCompra> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = {"ordenCompra", "ordenCompra.proveedorEntidad", "rubroEntidad", "itemManoObraVinculado", "materialCatalogo", "categoriaEntidad"})
    @Query(value = """
            select item from ItemOrdenCompra item
            join item.ordenCompra oc
            left join oc.proveedorEntidad proveedor
            left join item.rubroEntidad rubro
            left join item.categoriaEntidad categoria
            where oc.obra.id = :obraId
              and (:busqueda is null
                   or lower(item.item) like lower(concat('%', :busqueda, '%'))
                   or lower(item.detalle) like lower(concat('%', :busqueda, '%'))
                   or lower(oc.numero) like lower(concat('%', :busqueda, '%'))
                   or lower(proveedor.nombre) like lower(concat('%', :busqueda, '%'))
                   or lower(rubro.nombre) like lower(concat('%', :busqueda, '%'))
                   or lower(categoria.nombre) like lower(concat('%', :busqueda, '%')))
              and (:categoria is null or item.categoria = :categoria or categoria.tipo = :categoria)
            order by oc.fecha desc, item.item asc, item.id asc
            """,
            countQuery = """
            select count(item) from ItemOrdenCompra item
            join item.ordenCompra oc
            left join oc.proveedorEntidad proveedor
            left join item.rubroEntidad rubro
            left join item.categoriaEntidad categoria
            where oc.obra.id = :obraId
              and (:busqueda is null
                   or lower(item.item) like lower(concat('%', :busqueda, '%'))
                   or lower(item.detalle) like lower(concat('%', :busqueda, '%'))
                   or lower(oc.numero) like lower(concat('%', :busqueda, '%'))
                   or lower(proveedor.nombre) like lower(concat('%', :busqueda, '%'))
                   or lower(rubro.nombre) like lower(concat('%', :busqueda, '%'))
                   or lower(categoria.nombre) like lower(concat('%', :busqueda, '%')))
              and (:categoria is null or item.categoria = :categoria or categoria.tipo = :categoria)
            """)
    Page<ItemOrdenCompra> buscarItems(@Param("obraId") Long obraId,
                                      @Param("busqueda") String busqueda,
                                      @Param("categoria") CategoriaItem categoria,
                                      Pageable pageable);

    @EntityGraph(attributePaths = {"ordenCompra", "rubroEntidad", "categoriaEntidad"})
    List<ItemOrdenCompra> findByCategoriaOrderByOrdenCompraNumeroAscIdAsc(CategoriaItem categoria);

    @EntityGraph(attributePaths = {"ordenCompra", "rubroEntidad", "itemManoObraVinculado", "materialCatalogo", "categoriaEntidad"})
    List<ItemOrdenCompra> findByOrdenCompraIdAndCategoriaOrderById(Long ordenCompraId, CategoriaItem categoria);


    @EntityGraph(attributePaths = {"ordenCompra", "rubroEntidad"})
    @Query("""
            select item from ItemOrdenCompra item
            join item.ordenCompra oc
            where oc.obra.id = :obraId
              and item.categoria = com.obra.certificaciones.oc.entity.CategoriaItem.MANO_OBRA
              and item.rubroEntidad is not null
            order by item.rubroEntidad.codigo asc, item.item asc, item.id asc
            """)
    List<ItemOrdenCompra> findManoObraConRubroByObraId(@Param("obraId") Long obraId);

    @Query("""
            select coalesce(sum(item.importe), 0) from ItemOrdenCompra item
            join item.ordenCompra oc
            left join item.categoriaEntidad categoria
            where oc.obra.id = :obraId
              and (item.categoria = :categoria or categoria.tipo = :categoria)
            """)
    java.math.BigDecimal sumImporteByObraIdAndCategoria(@Param("obraId") Long obraId, @Param("categoria") CategoriaItem categoria);


    @EntityGraph(attributePaths = {"ordenCompra", "rubroEntidad"})
    List<ItemOrdenCompra> findByRubroEntidadId(Long rubroId);

    long countByRubroEntidadIsNotNull();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ItemOrdenCompra item set item.rubroEntidad = null, item.rubro = null where item.rubroEntidad is not null or item.rubro is not null")
    int desvincularTodosLosRubros();

    boolean existsByRubroEntidadId(Long rubroId);

    boolean existsByMaterialCatalogo_Id(Long materialCatalogoId);

    boolean existsByCategoriaEntidadId(Long categoriaId);
}