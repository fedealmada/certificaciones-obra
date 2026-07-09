package com.obra.certificaciones.categoria.service;

import com.obra.certificaciones.categoria.entity.CategoriaOrden;
import com.obra.certificaciones.categoria.repository.CategoriaOrdenRepository;
import com.obra.certificaciones.oc.repository.ItemOrdenCompraRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaOrdenService {

    private final CategoriaOrdenRepository categoriaOrdenRepository;
    private final ItemOrdenCompraRepository itemOrdenCompraRepository;

    @Transactional(readOnly = true)
    public List<CategoriaOrden> listarTodos() {
        return categoriaOrdenRepository.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public List<CategoriaOrden> listarActivas() {
        return categoriaOrdenRepository.findByActivoTrueOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public CategoriaOrden obtener(Long id) {
        return categoriaOrdenRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la categoria " + id));
    }

    @Transactional
    public CategoriaOrden guardar(CategoriaOrden categoria) {
        validar(categoria);
        return categoriaOrdenRepository.save(categoria);
    }

    @Transactional
    public void eliminar(Long id) {
        CategoriaOrden categoria = obtener(id);
        if (itemOrdenCompraRepository.existsByCategoriaEntidadId(id)) {
            categoria.setActivo(false);
            categoriaOrdenRepository.save(categoria);
            return;
        }
        categoriaOrdenRepository.delete(categoria);
    }

    private void validar(CategoriaOrden categoria) {
        if (!StringUtils.hasText(categoria.getNombre())) {
            throw new IllegalArgumentException("El nombre de la categoria es obligatorio.");
        }
        if (categoria.getTipo() == null) {
            throw new IllegalArgumentException("El tipo de comportamiento es obligatorio.");
        }
        boolean duplicado = categoria.getId() == null
                ? categoriaOrdenRepository.existsByNombreIgnoreCase(categoria.getNombre())
                : categoriaOrdenRepository.existsByNombreIgnoreCaseAndIdNot(categoria.getNombre(), categoria.getId());
        if (duplicado) {
            throw new IllegalArgumentException("Ya existe una categoria con ese nombre.");
        }
    }
}
