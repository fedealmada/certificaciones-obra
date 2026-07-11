package com.obra.certificaciones.item.controller;

import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.repository.ItemOrdenCompraRepository;
import com.obra.certificaciones.rubro.entity.Rubro;
import com.obra.certificaciones.rubro.service.RubroService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
@RequiredArgsConstructor
public class ItemController {

    private static final Pattern PARTES_CODIGO = Pattern.compile("\\d+|\\D+");

    private final ItemOrdenCompraRepository itemOrdenCompraRepository;
    private final RubroService rubroService;

    @GetMapping("/items")
    public String listar(Model model) {
        List<ItemOrdenCompra> items = itemOrdenCompraRepository.findAllByOrderByIdAsc().stream()
                .sorted(compararItems())
                .toList();
        model.addAttribute("items", items);
        model.addAttribute("rubros", rubroService.listarActivos());
        return "item/lista";
    }

    @PostMapping("/items/{id}/rubro")
    public String actualizarRubro(@PathVariable Long id,
                                  @RequestParam(required = false) String rubroId,
                                  RedirectAttributes redirectAttributes) {
        actualizarRubroItem(id, rubroId);
        redirectAttributes.addFlashAttribute("mensaje", "Rubro actualizado.");
        return "redirect:/items";
    }

    @PostMapping("/items/{id}/rubro/async")
    @ResponseBody
    public ResponseEntity<Map<String, String>> actualizarRubroAsync(@PathVariable Long id,
                                                                    @RequestParam(required = false) String rubroId) {
        Rubro rubro = actualizarRubroItem(id, rubroId);
        return ResponseEntity.ok(Map.of(
                "rubro", rubro == null ? "" : rubro.getNombreCompleto()
        ));
    }

    @PostMapping("/items/{id}/itemizado-orden/async")
    @ResponseBody
    public ResponseEntity<Map<String, String>> reordenarItemizadoAsync(@PathVariable Long id,
                                                                       @RequestParam Long targetItemId,
                                                                       @RequestParam(defaultValue = "before") String position) {
        Rubro rubro = reordenarItemizado(id, targetItemId, position);
        return ResponseEntity.ok(Map.of(
                "rubro", rubro == null ? "" : rubro.getNombreCompleto()
        ));
    }

    private Rubro actualizarRubroItem(Long id, String rubroId) {
        ItemOrdenCompra item = itemOrdenCompraRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el item."));
        if (!StringUtils.hasText(rubroId)) {
            item.setRubroEntidad(null);
            item.setRubro(null);
            item.setOrdenItemizado(null);
            itemOrdenCompraRepository.save(item);
            return null;
        }
        Rubro rubro = rubroService.obtener(Long.valueOf(rubroId));
        item.setRubroEntidad(rubro);
        item.setRubro(null);
        item.setOrdenItemizado(siguienteOrdenItemizado(rubro.getId(), item.getId()));
        itemOrdenCompraRepository.save(item);
        return rubro;
    }

    private Rubro reordenarItemizado(Long id, Long targetItemId, String position) {
        ItemOrdenCompra item = itemOrdenCompraRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el item."));
        ItemOrdenCompra target = itemOrdenCompraRepository.findById(targetItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el item destino."));
        if (Objects.equals(item.getId(), target.getId())) {
            return target.getRubroEntidad();
        }
        Rubro rubro = target.getRubroEntidad();
        if (rubro == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El item destino no tiene rubro.");
        }

        item.setRubroEntidad(rubro);
        item.setRubro(null);

        List<ItemOrdenCompra> ordenados = itemOrdenCompraRepository.findByRubroEntidadId(rubro.getId()).stream()
                .filter(itemOrden -> itemOrden.getCategoria() == CategoriaItem.MANO_OBRA)
                .filter(itemOrden -> !Objects.equals(itemOrden.getId(), item.getId()))
                .sorted(compararItemsParaItemizado())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        int targetIndex = 0;
        for (int i = 0; i < ordenados.size(); i++) {
            if (Objects.equals(ordenados.get(i).getId(), target.getId())) {
                targetIndex = i;
                break;
            }
        }
        int insertIndex = "after".equalsIgnoreCase(position) ? targetIndex + 1 : targetIndex;
        ordenados.add(Math.min(insertIndex, ordenados.size()), item);
        guardarOrdenItemizado(ordenados);
        return rubro;
    }

    private int siguienteOrdenItemizado(Long rubroId, Long excluirItemId) {
        return itemOrdenCompraRepository.findByRubroEntidadId(rubroId).stream()
                .filter(item -> item.getCategoria() == CategoriaItem.MANO_OBRA)
                .filter(item -> !Objects.equals(item.getId(), excluirItemId))
                .map(ItemOrdenCompra::getOrdenItemizado)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 10;
    }

    private void guardarOrdenItemizado(List<ItemOrdenCompra> items) {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setOrdenItemizado((i + 1) * 10);
        }
        itemOrdenCompraRepository.saveAll(items);
    }

    private Comparator<ItemOrdenCompra> compararItems() {
        return Comparator
                .comparing(ItemOrdenCompra::getItem, ItemController::compararCodigoNatural)
                .thenComparing(item -> textoSeguro(item.getOrdenCompra().getNumero()), ItemController::compararCodigoNatural)
                .thenComparing(item -> textoSeguro(item.getDetalle()), String.CASE_INSENSITIVE_ORDER);
    }

    private Comparator<ItemOrdenCompra> compararItemsParaItemizado() {
        return Comparator
                .comparing(ItemOrdenCompra::getOrdenItemizado, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(item -> textoSeguro(item.getOrdenCompra().getNumero()), ItemController::compararCodigoNatural)
                .thenComparing(ItemOrdenCompra::getItem, ItemController::compararCodigoNatural)
                .thenComparing(item -> textoSeguro(item.getDetalle()), String.CASE_INSENSITIVE_ORDER);
    }

    private static int compararCodigoNatural(String izquierdo, String derecho) {
        String codigoIzquierdo = textoSeguro(izquierdo).trim();
        String codigoDerecho = textoSeguro(derecho).trim();
        if (codigoIzquierdo.isBlank() && codigoDerecho.isBlank()) {
            return 0;
        }
        if (codigoIzquierdo.isBlank()) {
            return 1;
        }
        if (codigoDerecho.isBlank()) {
            return -1;
        }

        Matcher partesIzquierda = PARTES_CODIGO.matcher(codigoIzquierdo);
        Matcher partesDerecha = PARTES_CODIGO.matcher(codigoDerecho);
        boolean hayIzquierda = partesIzquierda.find();
        boolean hayDerecha = partesDerecha.find();

        while (hayIzquierda && hayDerecha) {
            int comparacion = compararParte(partesIzquierda.group(), partesDerecha.group());
            if (comparacion != 0) {
                return comparacion;
            }
            hayIzquierda = partesIzquierda.find();
            hayDerecha = partesDerecha.find();
        }
        if (hayIzquierda) {
            return 1;
        }
        if (hayDerecha) {
            return -1;
        }
        return codigoIzquierdo.compareToIgnoreCase(codigoDerecho);
    }

    private static int compararParte(String izquierda, String derecha) {
        boolean izquierdaEsNumero = izquierda.chars().allMatch(Character::isDigit);
        boolean derechaEsNumero = derecha.chars().allMatch(Character::isDigit);
        if (izquierdaEsNumero && derechaEsNumero) {
            return new BigInteger(izquierda).compareTo(new BigInteger(derecha));
        }
        return izquierda.compareToIgnoreCase(derecha);
    }

    private static String textoSeguro(String valor) {
        return valor == null ? "" : valor;
    }
}
