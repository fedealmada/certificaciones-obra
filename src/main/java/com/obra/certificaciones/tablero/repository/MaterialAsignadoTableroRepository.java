package com.obra.certificaciones.tablero.repository;

import com.obra.certificaciones.tablero.entity.MaterialAsignadoTablero;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.math.BigDecimal;

public interface MaterialAsignadoTableroRepository extends JpaRepository<MaterialAsignadoTablero,Long> {
    @EntityGraph(attributePaths={"material","material.ordenCompra","material.ordenCompra.proveedorEntidad"})
    List<MaterialAsignadoTablero> findByObraIdAndClaveTarea(Long obraId,String claveTarea);

    interface Uso { Long getMaterialId(); BigDecimal getImporte(); }
    @Query("select a.material.id as materialId, sum(a.importe) as importe from MaterialAsignadoTablero a where a.obraId=:obra and a.claveTarea<>:clave and a.material.id in :ids group by a.material.id")
    List<Uso> usados(@Param("obra") Long obra,@Param("clave") String clave,@Param("ids") List<Long> ids);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MaterialAsignadoTablero a where a.obraId=:obra and a.claveTarea<>:clave and a.material.id in :ids order by a.id")
    List<MaterialAsignadoTablero> bloquearUsados(@Param("obra") Long obra,@Param("clave") String clave,@Param("ids") List<Long> ids);
}
