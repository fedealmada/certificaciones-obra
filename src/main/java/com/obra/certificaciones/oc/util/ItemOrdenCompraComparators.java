package com.obra.certificaciones.oc.util;

import com.obra.certificaciones.oc.entity.ItemOrdenCompra;

import java.util.Comparator;

/** Comparadores para mostrar los códigos de ítem en orden numérico natural. */
public final class ItemOrdenCompraComparators {
    private ItemOrdenCompraComparators() {
    }

    public static final Comparator<ItemOrdenCompra> POR_CODIGO_NATURAL =
            Comparator.comparing(ItemOrdenCompra::getItem, ItemOrdenCompraComparators::compararCodigos)
                    .thenComparing(ItemOrdenCompra::getId, Comparator.nullsLast(Long::compareTo));

    private static int compararCodigos(String izquierdo, String derecho) {
        if (izquierdo == derecho) return 0;
        if (izquierdo == null) return 1;
        if (derecho == null) return -1;

        int i = 0;
        int j = 0;
        while (i < izquierdo.length() && j < derecho.length()) {
            char a = izquierdo.charAt(i);
            char b = derecho.charAt(j);
            if (Character.isDigit(a) && Character.isDigit(b)) {
                int finA = finDeDigitos(izquierdo, i);
                int finB = finDeDigitos(derecho, j);
                int inicioSignificativoA = inicioSignificativo(izquierdo, i, finA);
                int inicioSignificativoB = inicioSignificativo(derecho, j, finB);
                int longitudA = finA - inicioSignificativoA;
                int longitudB = finB - inicioSignificativoB;
                if (longitudA != longitudB) return Integer.compare(longitudA, longitudB);
                int comparacionNumerica = izquierdo.regionMatches(inicioSignificativoA, derecho,
                        inicioSignificativoB, longitudA) ? 0
                        : izquierdo.substring(inicioSignificativoA, finA)
                        .compareTo(derecho.substring(inicioSignificativoB, finB));
                if (comparacionNumerica != 0) return comparacionNumerica;
                i = finA;
                j = finB;
                continue;
            }

            int comparacionTexto = Character.compare(Character.toLowerCase(a), Character.toLowerCase(b));
            if (comparacionTexto != 0) return comparacionTexto;
            i++;
            j++;
        }
        return Integer.compare(izquierdo.length() - i, derecho.length() - j);
    }

    private static int finDeDigitos(String codigo, int inicio) {
        int fin = inicio;
        while (fin < codigo.length() && Character.isDigit(codigo.charAt(fin))) fin++;
        return fin;
    }

    private static int inicioSignificativo(String codigo, int inicio, int fin) {
        int significativo = inicio;
        while (significativo < fin - 1 && codigo.charAt(significativo) == '0') significativo++;
        return significativo;
    }
}
