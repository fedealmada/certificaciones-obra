package com.obra.certificaciones.documentacion.repository;

import com.obra.certificaciones.documentacion.entity.CarpetaDocumentacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CarpetaDocumentacionRepository extends JpaRepository<CarpetaDocumentacion, Long> {
    @EntityGraph(attributePaths = {"obra", "proveedor", "padre"})
    List<CarpetaDocumentacion> findByObraIdAndActivoTrueOrderByOrdenAscGeneralDescNombreAsc(Long obraId);

    Optional<CarpetaDocumentacion> findByObraIdAndGeneralTrueAndActivoTrue(Long obraId);

    boolean existsByObraIdAndProveedorIdAndActivoTrue(Long obraId, Long proveedorId);

    boolean existsByObraIdAndNombreIgnoreCaseAndActivoTrue(Long obraId, String nombre);
}
