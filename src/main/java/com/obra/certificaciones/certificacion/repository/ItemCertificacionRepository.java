package com.obra.certificaciones.certificacion.repository;

import com.obra.certificaciones.certificacion.entity.ItemCertificacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemCertificacionRepository extends JpaRepository<ItemCertificacion, Long> {
    @EntityGraph(attributePaths = {"certificacion", "certificacion.ordenCompra", "itemOrdenCompra"})
    List<ItemCertificacion> findByCertificacionOrdenCompraIdOrderByCertificacionFechaAscCertificacionIdAsc(Long ordenCompraId);

    @EntityGraph(attributePaths = {"certificacion", "certificacion.ordenCompra", "itemOrdenCompra"})
    List<ItemCertificacion> findByCertificacionOrdenCompraIdIn(List<Long> ordenCompraIds);

    @EntityGraph(attributePaths = {"certificacion", "certificacion.ordenCompra", "itemOrdenCompra"})
    List<ItemCertificacion> findByItemOrdenCompraIdOrderByCertificacionFechaAscCertificacionIdAsc(Long itemOrdenCompraId);

    long countByItemOrdenCompraId(Long itemOrdenCompraId);
}
