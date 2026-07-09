package com.obra.certificaciones.material.catalogo.service;

import com.obra.certificaciones.material.catalogo.entity.MaterialCatalogo;
import com.obra.certificaciones.material.catalogo.repository.MaterialCatalogoRepository;
import com.obra.certificaciones.oc.repository.ItemOrdenCompraRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialCatalogoService {

    private final MaterialCatalogoRepository materialCatalogoRepository;
    private final ItemOrdenCompraRepository itemOrdenCompraRepository;

    @Transactional(readOnly = true)
    public List<MaterialCatalogo> listarTodos() {
        return materialCatalogoRepository.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public List<MaterialCatalogo> listarActivos() {
        return materialCatalogoRepository.findByActivoTrueOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public MaterialCatalogo obtener(Long id) {
        return materialCatalogoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el material " + id));
    }

    @Transactional
    public MaterialCatalogo guardar(MaterialCatalogo material) {
        validar(material);
        return materialCatalogoRepository.save(material);
    }

    @Transactional
    public void eliminar(Long id) {
        MaterialCatalogo material = obtener(id);
        if (itemOrdenCompraRepository.existsByMaterialCatalogo_Id(id)) {
            material.setActivo(false);
            materialCatalogoRepository.save(material);
            return;
        }
        materialCatalogoRepository.delete(material);
    }

    private void validar(MaterialCatalogo material) {
        if (!StringUtils.hasText(material.getNombre())) {
            throw new IllegalArgumentException("El nombre del material es obligatorio.");
        }
        boolean duplicado = material.getId() == null
                ? materialCatalogoRepository.existsByNombreIgnoreCase(material.getNombre())
                : materialCatalogoRepository.existsByNombreIgnoreCaseAndIdNot(material.getNombre(), material.getId());
        if (duplicado) {
            throw new IllegalArgumentException("Ya existe un material con ese nombre.");
        }
    }
}
