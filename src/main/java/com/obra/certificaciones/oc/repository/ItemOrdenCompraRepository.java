package com.obra.certificaciones.oc.repository;

import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ItemOrdenCompraRepository extends JpaRepository<ItemOrdenCompra, Long> {
    @Override
    @EntityGraph(attributePaths = {"ordenCompra", "rubroEntidad"})
    Optional<ItemOrdenCompra> findById(Long id);

    @EntityGraph(attributePaths = {"ordenCompra", "ordenCompra.proveedorEntidad", "rubroEntidad", "itemManoObraVinculado", "materialCatalogo", "categoriaEntidad"})
    List<ItemOrdenCompra> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = {"ordenCompra", "rubroEntidad", "categoriaEntidad"})
    List<ItemOrdenCompra> findByCategoriaOrderByOrdenCompraNumeroAscIdAsc(CategoriaItem categoria);

    @EntityGraph(attributePaths = {"ordenCompra", "rubroEntidad", "itemManoObraVinculado", "materialCatalogo", "categoriaEntidad"})
    List<ItemOrdenCompra> findByOrdenCompraIdAndCategoriaOrderById(Long ordenCompraId, CategoriaItem categoria);

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
