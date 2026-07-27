package com.obra.certificaciones.itemizado.service;

import com.obra.certificaciones.itemizado.dto.ItemizadoItemFila;
import com.obra.certificaciones.itemizado.dto.ItemizadoNodo;
import com.obra.certificaciones.itemizado.dto.ItemizadoVista;
import com.obra.certificaciones.itemizado.entity.ItemizadoManualItem;
import com.obra.certificaciones.itemizado.entity.TipoItemizadoManual;
import com.obra.certificaciones.itemizado.repository.ItemizadoManualItemRepository;
import com.obra.certificaciones.oc.entity.CategoriaItem;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ItemizadoService {

    private final RubroRepository rubroRepository;
    private final ItemOrdenCompraRepository itemOrdenCompraRepository;
    private final ItemizadoManualItemRepository itemizadoManualItemRepository;

    @Transactional(readOnly = true)
    public ItemizadoVista generar() {
        List<Rubro> rubros = rubroRepository.findAllByOrderByCodigoAscNombreAsc().stream()
                .sorted(RubroComparators.porCodigoNatural())
                .toList();
        List<ItemOrdenCompra> items = itemOrdenCompraRepository.findAllByOrderByIdAsc();
        List<ItemizadoManualItem> itemsManuales = itemizadoManualItemRepository.findByActivoTrueOrderByOrdenAscIdAsc();
        Map<Long, List<ItemOrdenCompra>> materialesPorManoObra = agruparMateriales(items);
        Map<Long, List<ItemizadoManualItem>> materialesManualesPorItem = agruparMaterialesManuales(itemsManuales);
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
                        "", item, null, materiales, List.of(), totalMateriales, importeSeguro(item).add(totalMateriales)
                ));
            }
        }

        for (ItemizadoManualItem manual : itemsManuales) {
            if (manual.getTipo() != TipoItemizadoManual.MANO_OBRA || manual.getRubro() == null || !nodos.containsKey(manual.getRubro().getId())) {
                continue;
            }
            List<ItemizadoManualItem> materialesManuales = materialesManualesPorItem.getOrDefault(manual.getId(), List.of());
            BigDecimal totalMateriales = materialesManuales.stream()
                    .map(this::importeSeguro)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            nodos.get(manual.getRubro().getId()).getItems().add(new ItemizadoItemFila(
                    "", null, manual, List.of(), materialesManuales, totalMateriales, importeSeguro(manual).add(totalMateriales)
            ));
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
                .cantidadItems((int) items.stream().filter(item -> item.getCategoria() == CategoriaItem.MANO_OBRA).count()
                        + (int) itemsManuales.stream().filter(item -> item.getTipo() == TipoItemizadoManual.MANO_OBRA).count())
                .totalManoObra(totalManoObra)
                .totalMateriales(totalMateriales)
                .totalGeneral(totalManoObra.add(totalMateriales))
                .build();
    }

    @Transactional
    public ItemizadoManualItem crearManual(Long rubroId, String item, String detalle, String unidad, BigDecimal cantidad, BigDecimal precioUnitario) {
        Rubro rubro = rubroRepository.findById(rubroId)
                .orElseThrow(() -> new EntityNotFoundException("No existe el rubro " + rubroId));
        if (!StringUtils.hasText(detalle)) {
            throw new IllegalArgumentException("El detalle del item es obligatorio.");
        }
        ItemizadoManualItem manual = new ItemizadoManualItem();
        manual.setRubro(rubro);
        manual.setTipo(TipoItemizadoManual.MANO_OBRA);
        manual.setItem(StringUtils.hasText(item) ? item.trim() : null);
        manual.setDetalle(detalle.trim());
        manual.setUnidad(StringUtils.hasText(unidad) ? unidad.trim() : null);
        manual.setCantidad(cantidad == null ? BigDecimal.ZERO : cantidad);
        manual.setPrecioUnitario(precioUnitario == null ? BigDecimal.ZERO : precioUnitario);
        manual.setOrden(siguienteOrdenManual(rubroId));
        manual.calcularImporte();
        return itemizadoManualItemRepository.save(manual);
    }

    @Transactional
    public ItemizadoManualItem crearMaterialManual(Long itemPadreId, String detalle, String unidad, BigDecimal cantidad, BigDecimal precioUnitario) {
        ItemizadoManualItem padre = itemizadoManualItemRepository.findById(itemPadreId)
                .filter(ItemizadoManualItem::isActivo)
                .filter(item -> item.getTipo() == TipoItemizadoManual.MANO_OBRA)
                .orElseThrow(() -> new EntityNotFoundException("No existe el item manual " + itemPadreId));
        if (!StringUtils.hasText(detalle)) {
            throw new IllegalArgumentException("El detalle del material es obligatorio.");
        }
        ItemizadoManualItem material = new ItemizadoManualItem();
        material.setRubro(padre.getRubro());
        material.setItemPadre(padre);
        material.setTipo(TipoItemizadoManual.MATERIAL);
        material.setDetalle(detalle.trim());
        material.setUnidad(StringUtils.hasText(unidad) ? unidad.trim() : null);
        material.setCantidad(cantidad == null ? BigDecimal.ZERO : cantidad);
        material.setPrecioUnitario(precioUnitario == null ? BigDecimal.ZERO : precioUnitario);
        material.calcularImporte();
        return itemizadoManualItemRepository.save(material);
    }

    @Transactional
    public void eliminarManual(Long id) {
        ItemizadoManualItem item = itemizadoManualItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe el item manual " + id));
        item.setActivo(false);
        itemizadoManualItemRepository.findByActivoTrueOrderByOrdenAscIdAsc().stream()
                .filter(material -> material.getItemPadre() != null)
                .filter(material -> Objects.equals(material.getItemPadre().getId(), id))
                .forEach(material -> material.setActivo(false));
        itemizadoManualItemRepository.save(item);
    }

    private void prepararNodo(ItemizadoNodo nodo, int nivel) {
        nodo.setNivel(nivel);
        nodo.getHijos().sort(compararNodos());
        nodo.getItems().sort(compararItems());
        numerarItems(nodo);

        BigDecimal manoObra = nodo.getItems().stream()
                .map(ItemizadoItemFila::importeManoObra)
                .map(this::importeSeguro)
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
                    item.manualItem(),
                    item.materiales(),
                    item.materialesManuales(),
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
                .comparing((ItemizadoItemFila item) -> item.esManual() ? item.manualItem().getOrden() : item.manoObra().getOrdenItemizado(), Comparator.nullsLast(Integer::compareTo))
                .thenComparing(ItemizadoItemFila::ocNumero)
                .thenComparing(item -> textoSeguro(item.item()))
                .thenComparing(item -> textoSeguro(item.detalle()));
    }

    private int siguienteOrdenManual(Long rubroId) {
        return itemizadoManualItemRepository.findByActivoTrueAndTipoOrderByOrdenAscIdAsc(TipoItemizadoManual.MANO_OBRA).stream()
                .filter(item -> item.getRubro() != null && Objects.equals(item.getRubro().getId(), rubroId))
                .map(ItemizadoManualItem::getOrden)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 10;
    }

    private BigDecimal importeSeguro(ItemOrdenCompra item) {
        return item.getImporte() == null ? BigDecimal.ZERO : item.getImporte();
    }

    private BigDecimal importeSeguro(ItemizadoManualItem item) {
        return item.getImporte() == null ? BigDecimal.ZERO : item.getImporte();
    }

    private BigDecimal importeSeguro(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
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

    private Map<Long, List<ItemizadoManualItem>> agruparMaterialesManuales(List<ItemizadoManualItem> items) {
        return items.stream()
                .filter(item -> item.getTipo() == TipoItemizadoManual.MATERIAL)
                .filter(item -> item.getItemPadre() != null)
                .collect(java.util.stream.Collectors.groupingBy(item -> item.getItemPadre().getId()));
    }
}
