package com.obra.certificaciones.certificacion.repository;

import com.obra.certificaciones.certificacion.entity.ItemCertificacion;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ItemCertificacionRepository extends JpaRepository<ItemCertificacion, Long> {
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

    @EntityGraph(attributePaths = {"certificacion", "certificacion.ordenCompra", "certificacion.ordenCompra.proveedorEntidad", "itemOrdenCompra", "itemOrdenCompra.rubroEntidad"})
    List<ItemCertificacion> findByCertificacionOrdenCompraObraIdAndCertificacionFechaBeforeAndItemOrdenCompraCategoriaOrderByItemOrdenCompraIdAscCertificacionFechaAsc(
            Long obraId,
            LocalDate fecha,
            CategoriaItem categoria);

    long countByItemOrdenCompraId(Long itemOrdenCompraId);
}
