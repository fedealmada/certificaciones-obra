package com.obra.certificaciones.certificacion.repository;

import com.obra.certificaciones.certificacion.entity.ItemCertificacion;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ItemCertificacionRepository extends JpaRepository<ItemCertificacion, Long> {
    String CATEGORIAS_QUINCENALES = """
              and ((coalesce(categoria.tipo, item.categoria) = com.obra.certificaciones.oc.entity.CategoriaItem.MANO_OBRA)
                or (coalesce(categoria.tipo, item.categoria) = com.obra.certificaciones.oc.entity.CategoriaItem.OTRO
                    and (i.certificacion.ordenCompra.modoSeguimiento = com.obra.certificaciones.oc.entity.ModoSeguimientoOrden.CERTIFICACION
                      or (i.certificacion.ordenCompra.modoSeguimiento is null and not exists (
                          select m.id from ItemOrdenCompra m where m.ordenCompra = i.certificacion.ordenCompra
                            and m.categoria = com.obra.certificaciones.oc.entity.CategoriaItem.MATERIAL)))))
            """;

    @EntityGraph(attributePaths = {"certificacion", "certificacion.ordenCompra", "certificacion.ordenCompra.proveedorEntidad", "itemOrdenCompra", "itemOrdenCompra.categoriaEntidad"})
    @Query("""
            select i from ItemCertificacion i join i.itemOrdenCompra item
            left join item.categoriaEntidad categoria
            where i.certificacion.ordenCompra.obra.id = :obraId
              and i.certificacion.fecha between :desde and :hasta
            """ + CATEGORIAS_QUINCENALES)
    List<ItemCertificacion> buscarCertificablesPeriodo(@Param("obraId") Long obraId, @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @Query("""
            select distinct i.certificacion.fecha from ItemCertificacion i join i.itemOrdenCompra item
            left join item.categoriaEntidad categoria
            where i.certificacion.ordenCompra.obra.id = :obraId and i.certificacion.fecha is not null
              and (i.porcentajeActual <> 0 or i.cantidadActual <> 0)
            """ + CATEGORIAS_QUINCENALES)
    List<LocalDate> fechasCertificables(@Param("obraId") Long obraId);

    @EntityGraph(attributePaths = {"certificacion", "certificacion.ordenCompra", "itemOrdenCompra"})
    List<ItemCertificacion> findByCertificacionOrdenCompraIdOrderByCertificacionFechaAscCertificacionIdAsc(Long ordenCompraId);

    @EntityGraph(attributePaths = {"certificacion", "certificacion.ordenCompra", "itemOrdenCompra"})
    List<ItemCertificacion> findByCertificacionOrdenCompraIdIn(List<Long> ordenCompraIds);

    @EntityGraph(attributePaths = {"certificacion", "certificacion.ordenCompra", "itemOrdenCompra"})
    List<ItemCertificacion> findByItemOrdenCompraIdOrderByCertificacionFechaAscCertificacionIdAsc(Long itemOrdenCompraId);

    @EntityGraph(attributePaths = {"certificacion", "certificacion.ordenCompra", "certificacion.ordenCompra.proveedorEntidad", "itemOrdenCompra", "itemOrdenCompra.rubroEntidad"})
    List<ItemCertificacion> findByCertificacionOrdenCompraObraIdAndCertificacionFechaBetweenAndItemOrdenCompraCategoriaOrderByItemOrdenCompraIdAscCertificacionFechaAsc(
            Long obraId,
            LocalDate desde,
            LocalDate hasta,
            CategoriaItem categoria);

    long countByItemOrdenCompraId(Long itemOrdenCompraId);
}
