package com.obra.certificaciones.obra.service;

import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.obra.repository.ObraRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ObraService {
    private static final String OBRA_ACTIVA_ID = "obraActivaId";
    public static final String OBRA_INICIAL = "Terrazas de Quilmes";

    private final ObraRepository obraRepository;

    @Transactional(readOnly = true)
    public List<Obra> listar() {
        return obraRepository.findAllByOrderByActivaDescNombreAsc();
    }

    @Transactional(readOnly = true)
    public List<Obra> listarActivas() {
        return obraRepository.findByActivaTrueOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public Obra obtener(Long id) {
        return obraRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la obra " + id));
    }

    @Transactional
    public Obra obraActiva(HttpSession session) {
        Object id = session.getAttribute(OBRA_ACTIVA_ID);
        if (id instanceof Long obraId) {
            return obraRepository.findById(obraId).orElseGet(this::primeraObra);
        }
        Obra obra = primeraObra();
        session.setAttribute(OBRA_ACTIVA_ID, obra.getId());
        return obra;
    }

    @Transactional
    public Obra guardar(Obra obra) {
        validar(obra);
        if (obra.getId() == null) {
            obra.setNombre(obra.getNombre().trim());
            return obraRepository.save(obra);
        }
        Obra existente = obtener(obra.getId());
        existente.setNombre(obra.getNombre().trim());
        existente.setUbicacion(obra.getUbicacion());
        existente.setCliente(obra.getCliente());
        existente.setActiva(obra.isActiva());
        return obraRepository.save(existente);
    }

    public void seleccionar(Long id, HttpSession session) {
        session.setAttribute(OBRA_ACTIVA_ID, obtener(id).getId());
    }

    @Transactional
    public Obra asegurarObraInicial() {
        return obraRepository.findByNombreIgnoreCase(OBRA_INICIAL)
                .orElseGet(() -> {
                    Obra obra = new Obra();
                    obra.setNombre(OBRA_INICIAL);
                    obra.setUbicacion("Quilmes");
                    obra.setActiva(true);
                    return obraRepository.save(obra);
                });
    }

    private Obra primeraObra() {
        return listarActivas().stream().findFirst().orElseGet(this::asegurarObraInicial);
    }

    private void validar(Obra obra) {
        if (!StringUtils.hasText(obra.getNombre())) {
            throw new IllegalArgumentException("El nombre de la obra es obligatorio.");
        }
    }
}
