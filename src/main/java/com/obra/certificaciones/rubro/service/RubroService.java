package com.obra.certificaciones.rubro.service;

import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.repository.ItemOrdenCompraRepository;
import com.obra.certificaciones.rubro.entity.Rubro;
import com.obra.certificaciones.rubro.repository.RubroRepository;
import com.obra.certificaciones.rubro.util.RubroComparators;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RubroService {

    private final RubroRepository rubroRepository;
    private final ItemOrdenCompraRepository itemOrdenCompraRepository;

    @Transactional(readOnly = true)
    public List<Rubro> listarTodos() {
        return ordenarNatural(rubroRepository.findAllByOrderByCodigoAscNombreAsc());
    }

    @Transactional(readOnly = true)
    public List<Rubro> listarActivos() {
        return ordenarNatural(rubroRepository.findByActivoTrueOrderByCodigoAscNombreAsc());
    }

    @Transactional(readOnly = true)
    public Rubro obtener(Long id) {
        return rubroRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el rubro " + id));
    }

    @Transactional
    public Rubro guardar(Rubro rubro) {
        validar(rubro);
        aplicarPadre(rubro);
        Rubro guardado = rubroRepository.save(rubro);
        renumerarCodigosJerarquia();
        return guardado;
    }

    @Transactional
    public Rubro actualizarBasico(Long id, String nombre) {
        Rubro rubro = obtener(id);
        rubro.setNombre(nombre);
        validar(rubro);
        Rubro guardado = rubroRepository.save(rubro);
        renumerarCodigosJerarquia();
        return guardado;
    }

    @Transactional
    public Rubro mover(Long id, Long padreId) {
        Rubro rubro = obtener(id);
        if (padreId == null) {
            rubro.setPadre(null);
        } else {
            if (id.equals(padreId)) {
                throw new IllegalArgumentException("Un rubro no puede moverse dentro de si mismo.");
            }
            Rubro padre = obtener(padreId);
            validarPadreNoSeaDescendiente(rubro, padre);
            rubro.setPadre(padre);
        }
        Rubro guardado = rubroRepository.save(rubro);
        renumerarCodigosJerarquia();
        return guardado;
    }

    @Transactional
    public String eliminarVacio(Long id) {
        Rubro rubro = obtener(id);
        if (itemOrdenCompraRepository.existsByRubroEntidadId(id)) {
            throw new IllegalArgumentException("No se puede eliminar porque tiene items vinculados.");
        }
        if (rubroRepository.existsByPadre_Id(id)) {
            throw new IllegalArgumentException("No se puede eliminar porque tiene subrubros.");
        }
        rubroRepository.delete(rubro);
        renumerarCodigosJerarquia();
        return "Rubro eliminado.";
    }

    @Transactional
    public String eliminar(Long id) {
        Rubro rubro = obtener(id);
        int itemsDesvinculados = desvincularItems(rubro);
        int subrubrosReubicados = reubicarSubrubros(rubro);
        rubroRepository.delete(rubro);
        return armarMensajeEliminacion(itemsDesvinculados, subrubrosReubicados);
    }

    @Transactional
    public String eliminarTodos() {
        long rubrosEliminados = rubroRepository.count();
        if (rubrosEliminados == 0) {
            return "No hay rubros para eliminar.";
        }
        long itemsDesvinculados = itemOrdenCompraRepository.countByRubroEntidadIsNotNull();
        itemOrdenCompraRepository.desvincularTodosLosRubros();
        rubroRepository.desvincularTodosLosPadres();
        rubroRepository.deleteAllInBatch();
        return "Se eliminaron " + rubrosEliminados + " rubros. Se conservaron "
                + itemsDesvinculados + " items de OC sin rubro vinculado.";
    }

    private int desvincularItems(Rubro rubro) {
        List<ItemOrdenCompra> items = itemOrdenCompraRepository.findByRubroEntidadId(rubro.getId());
        items.forEach(item -> {
            item.setRubroEntidad(null);
            item.setRubro(null);
        });
        itemOrdenCompraRepository.saveAll(items);
        return items.size();
    }

    private int reubicarSubrubros(Rubro rubro) {
        List<Rubro> hijos = rubroRepository.findByPadre_Id(rubro.getId());
        hijos.forEach(hijo -> hijo.setPadre(rubro.getPadre()));
        rubroRepository.saveAll(hijos);
        return hijos.size();
    }

    private String armarMensajeEliminacion(int itemsDesvinculados, int subrubrosReubicados) {
        if (itemsDesvinculados == 0 && subrubrosReubicados == 0) {
            return "Rubro eliminado correctamente.";
        }
        return "Rubro eliminado correctamente. Se conservaron "
                + itemsDesvinculados + " items y "
                + subrubrosReubicados + " subrubros.";
    }

    private List<Rubro> ordenarNatural(List<Rubro> rubros) {
        return rubros.stream()
                .sorted(RubroComparators.porCodigoNatural())
                .toList();
    }

    private void validar(Rubro rubro) {
        if (!StringUtils.hasText(rubro.getNombre())) {
            throw new IllegalArgumentException("El nombre del rubro es obligatorio.");
        }
        if (rubro.getId() != null && rubro.getId().equals(rubro.getPadreId())) {
            throw new IllegalArgumentException("Un rubro no puede ser padre de si mismo.");
        }
    }

    private void aplicarPadre(Rubro rubro) {
        if (rubro.getPadreId() == null) {
            rubro.setPadre(null);
            return;
        }
        Rubro padre = obtener(rubro.getPadreId());
        rubro.setPadre(padre);
    }

    private void validarPadreNoSeaDescendiente(Rubro rubro, Rubro posiblePadre) {
        Rubro actual = posiblePadre;
        while (actual != null) {
            if (actual.getId().equals(rubro.getId())) {
                throw new IllegalArgumentException("No se puede mover un rubro dentro de un subrubro propio.");
            }
            actual = actual.getPadre();
        }
    }

    private void renumerarCodigosJerarquia() {
        List<Rubro> rubros = rubroRepository.findAllByOrderByCodigoAscNombreAsc();
        List<Rubro> raices = rubros.stream()
                .filter(rubro -> rubro.getPadre() == null)
                .sorted(RubroComparators.porCodigoNatural())
                .toList();
        for (int i = 0; i < raices.size(); i++) {
            renumerarRubro(raices.get(i), String.valueOf(i + 1), rubros);
        }
        rubroRepository.saveAll(rubros);
    }

    private void renumerarRubro(Rubro rubro, String codigo, List<Rubro> todos) {
        rubro.setCodigo(codigo);
        List<Rubro> hijos = todos.stream()
                .filter(hijo -> hijo.getPadre() != null && hijo.getPadre().getId().equals(rubro.getId()))
                .sorted(RubroComparators.porCodigoNatural())
                .toList();
        for (int i = 0; i < hijos.size(); i++) {
            renumerarRubro(hijos.get(i), codigo + "." + (i + 1), todos);
        }
    }
}
