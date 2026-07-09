package com.obra.certificaciones.rubro.util;

import com.obra.certificaciones.rubro.entity.Rubro;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RubroComparators {

    private static final Pattern PARTES_CODIGO = Pattern.compile("\\d+|\\D+");

    private RubroComparators() {
    }

    public static Comparator<Rubro> porCodigoNatural() {
        return Comparator
                .comparing(Rubro::getCodigo, RubroComparators::compararCodigoNatural)
                .thenComparing(rubro -> textoSeguro(rubro.getNombre()), String.CASE_INSENSITIVE_ORDER);
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

        while (partesIzquierda.find() && partesDerecha.find()) {
            String parteIzquierda = partesIzquierda.group();
            String parteDerecha = partesDerecha.group();
            int comparacion = compararParte(parteIzquierda, parteDerecha);
            if (comparacion != 0) {
                return comparacion;
            }
        }

        if (partesIzquierda.find()) {
            return 1;
        }
        if (partesDerecha.find()) {
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
