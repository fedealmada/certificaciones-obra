package com.obra.certificaciones.proveedor.service;

import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.repository.OrdenCompraRepository;
import com.obra.certificaciones.proveedor.entity.Proveedor;
import com.obra.certificaciones.proveedor.repository.ProveedorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;
    private final OrdenCompraRepository ordenCompraRepository;

    @Transactional(readOnly = true)
    public List<Proveedor> listarTodos() {
        return proveedorRepository.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public List<Proveedor> listarActivos() {
        return proveedorRepository.findByActivoTrueOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public Proveedor obtener(Long id) {
        return proveedorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el proveedor " + id));
    }

    @Transactional(readOnly = true)
    public List<OrdenCompra> ordenesProveedor(Long proveedorId) {
        return ordenCompraRepository.findByProveedorEntidadIdOrderByFechaDescIdDesc(proveedorId);
    }

    @Transactional
    public Proveedor guardar(Proveedor proveedor) {
        validar(proveedor);
        return proveedorRepository.save(proveedor);
    }

    @Transactional
    public void eliminar(Long id) {
        Proveedor proveedor = obtener(id);
        if (!ordenesProveedor(id).isEmpty()) {
            proveedor.setActivo(false);
            proveedorRepository.save(proveedor);
            return;
        }
        proveedorRepository.delete(proveedor);
    }

    private void validar(Proveedor proveedor) {
        if (!StringUtils.hasText(proveedor.getNombre())) {
            throw new IllegalArgumentException("El nombre del proveedor es obligatorio.");
        }
        boolean duplicado = proveedor.getId() == null
                ? proveedorRepository.existsByNombreIgnoreCase(proveedor.getNombre())
                : proveedorRepository.existsByNombreIgnoreCaseAndIdNot(proveedor.getNombre(), proveedor.getId());
        if (duplicado) {
            throw new IllegalArgumentException("Ya existe un proveedor con ese nombre.");
        }
    }
}
