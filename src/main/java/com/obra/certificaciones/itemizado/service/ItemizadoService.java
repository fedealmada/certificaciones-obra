package com.obra.certificaciones.itemizado.service;

import com.obra.certificaciones.itemizado.dto.ItemizadoItemFila;
import com.obra.certificaciones.itemizado.dto.ItemizadoNodo;
import com.obra.certificaciones.itemizado.dto.ItemizadoVista;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.repository.ItemOrdenCompraRepository;
import com.obra.certificaciones.rubro.entity.Rubro;
import com.obra.certificaciones.rubro.repository.RubroRepository;
import com.obra.certificaciones.rubro.util.RubroComparators;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ItemizadoService {

    private final RubroRepository rubroRepository;
    private final ItemOrdenCompraRepository itemOrdenCompraRepository;

    @Transactional(readOnly = true)
    public ItemizadoVista generar() {
        List<Rubro> rubros = rubroRepository.findAllByOrderByCodigoAscNombreAsc().stream()
                .sorted(RubroComparators.porCodigoNatural())
                .toList();
        List<ItemOrdenCompra> items = itemOrdenCompraRepository.findAllByOrderByIdAsc();
        Map<Long, List<ItemOrdenCompra>> materialesPorManoObra = agruparMateriales(items);
        Map<Long, ItemizadoNodo> nodos = new LinkedHashMap<>();
        rubros.forEach(rubro -> nodos.put(rubro.getId(), new ItemizadoNodo(rubro)));

        List<ItemizadoNodo> raices = new ArrayList<>();
        for (ItemizadoNodo nodo : nodos.values()) {
            Rubro padre = nodo.getRubro().getPadre();
            if (padre != null && nodos.containsKey(padre.getId())) {
                nodos.get(padre.getId()).getHijos().add(nodo);
            } else {
                raices.add(nodo);
            }
        }

        for (ItemOrdenCompra item : items) {
            if (item.getCategoria() != CategoriaItem.MANO_OBRA) {
                continue;
            }
            Rubro rubro = item.getRubroEntidad();
            if (rubro != null && nodos.containsKey(rubro.getId())) {
                List<ItemOrdenCompra> materiales = materialesPorManoObra.getOrDefault(item.getId(), List.of());
                BigDecimal totalMateriales = materiales.stream()
                        .map(this::importeSeguro)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                nodos.get(rubro.getId()).getItems().add(new ItemizadoItemFila(
                        "",
                        item,
                        materiales,
                        totalMateriales,
                        importeSeguro(item).add(totalMateriales)
                ));
            }
        }

        raices.sort(compararNodos());
        raices.forEach(raiz -> prepararNodo(raiz, 0));
        List<ItemizadoNodo> nodosPlanos = new ArrayList<>();
        raices.forEach(raiz -> agregarNodoPlano(raiz, nodosPlanos));

        BigDecimal totalManoObra = raices.stream()
                .map(ItemizadoNodo::getTotalManoObra)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMateriales = raices.stream()
                .map(ItemizadoNodo::getTotalMateriales)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ItemizadoVista.builder()
                .raices(raices)
                .nodos(nodosPlanos)
                .cantidadRubros(rubros.size())
                .cantidadItems((int) items.stream().filter(item -> item.getCategoria() == CategoriaItem.MANO_OBRA).count())
                .totalManoObra(totalManoObra)
                .totalMateriales(totalMateriales)
                .totalGeneral(totalManoObra.add(totalMateriales))
                .build();
    }

    private void prepararNodo(ItemizadoNodo nodo, int nivel) {
        nodo.setNivel(nivel);
        nodo.getHijos().sort(compararNodos());
        nodo.getItems().sort(compararItems());
        numerarItems(nodo);

        BigDecimal manoObra = nodo.getItems().stream()
                .map(item -> importeSeguro(item.manoObra()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal materiales = nodo.getItems().stream()
                .map(ItemizadoItemFila::totalMateriales)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (ItemizadoNodo hijo : nodo.getHijos()) {
            prepararNodo(hijo, nivel + 1);
            manoObra = manoObra.add(hijo.getTotalManoObra());
            materiales = materiales.add(hijo.getTotalMateriales());
        }

        nodo.setTotalManoObra(manoObra);
        nodo.setTotalMateriales(materiales);
        nodo.setTotalGeneral(manoObra.add(materiales));
    }

    private void numerarItems(ItemizadoNodo nodo) {
        String codigoBase = textoSeguro(nodo.getRubro().getCodigo());
        List<ItemizadoItemFila> numerados = new ArrayList<>();
        for (int i = 0; i < nodo.getItems().size(); i++) {
            ItemizadoItemFila item = nodo.getItems().get(i);
            numerados.add(new ItemizadoItemFila(
                    codigoItemizado(codigoBase, i + 1),
                    item.manoObra(),
                    item.materiales(),
                    item.totalMateriales(),
                    item.totalGeneral()
            ));
        }
        nodo.setItems(numerados);
    }

    private String codigoItemizado(String codigoBase, int numero) {
        if (codigoBase == null || codigoBase.isBlank()) {
            return String.valueOf(numero);
        }
        return codigoBase + "." + numero;
    }

    private void agregarNodoPlano(ItemizadoNodo nodo, List<ItemizadoNodo> nodosPlanos) {
        nodosPlanos.add(nodo);
        nodo.getHijos().forEach(hijo -> agregarNodoPlano(hijo, nodosPlanos));
    }

    private Comparator<ItemizadoNodo> compararNodos() {
        return Comparator.comparing(ItemizadoNodo::getRubro, RubroComparators.porCodigoNatural());
    }

    private Comparator<ItemizadoItemFila> compararItems() {
        return Comparator
                .comparing((ItemizadoItemFila item) -> textoSeguro(item.manoObra().getOrdenCompra().getNumero()))
                .thenComparing(item -> textoSeguro(item.manoObra().getItem()))
                .thenComparing(item -> textoSeguro(item.manoObra().getDetalle()));
    }

    private BigDecimal importeSeguro(ItemOrdenCompra item) {
        return item.getImporte() == null ? BigDecimal.ZERO : item.getImporte();
    }

    private String textoSeguro(String valor) {
        return valor == null ? "" : valor;
    }

    private Map<Long, List<ItemOrdenCompra>> agruparMateriales(List<ItemOrdenCompra> items) {
        return items.stream()
                .filter(item -> item.getCategoria() == CategoriaItem.MATERIAL)
                .filter(item -> item.getItemManoObraVinculado() != null)
                .collect(java.util.stream.Collectors.groupingBy(item -> item.getItemManoObraVinculado().getId()));
    }

}
