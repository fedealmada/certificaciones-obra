package com.obra.certificaciones.item.controller;

import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.repository.ItemOrdenCompraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
@RequiredArgsConstructor
public class ItemController {

    private static final Pattern PARTES_CODIGO = Pattern.compile("\\d+|\\D+");

    private final ItemOrdenCompraRepository itemOrdenCompraRepository;

    @GetMapping("/items")
    public String listar(Model model) {
        List<ItemOrdenCompra> items = itemOrdenCompraRepository.findAllByOrderByIdAsc().stream()
                .sorted(compararItems())
                .toList();
        model.addAttribute("items", items);
        return "item/lista";
    }

    private Comparator<ItemOrdenCompra> compararItems() {
        return Comparator
                .comparing(ItemOrdenCompra::getItem, ItemController::compararCodigoNatural)
                .thenComparing(item -> textoSeguro(item.getOrdenCompra().getNumero()), ItemController::compararCodigoNatural)
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
