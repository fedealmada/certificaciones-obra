package com.obra.certificaciones.itemizado.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.obra.certificaciones.itemizado.dto.ItemizadoItemFila;
import com.obra.certificaciones.itemizado.dto.ItemizadoNodo;
import com.obra.certificaciones.itemizado.dto.ItemizadoVista;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ItemizadoExportService {

    private static final String[] ENCABEZADOS = {
            "Codigo", "Item", "Rubro / Item", "OC", "Unidad", "Cantidad",
            "Precio Unitario", "Mano de Obra", "Materiales", "Total"
    };
    private static final String[] ENCABEZADOS_AVANCES = {
            "Nivel", "Tipo", "Codigo", "Rubro", "Item OC", "OC", "Proveedor", "Detalle",
            "Unidad", "Cantidad", "Importe Mano de Obra", "Materiales Vinculados", "Total Item",
            "Avance %", "Monto Certificado", "Saldo Pendiente", "Estado"
    };
    private static final float[] ANCHOS_PDF = {8, 8, 34, 7, 7, 8, 10, 10, 10, 10};
    private static final DateTimeFormatter FECHA_ARCHIVO = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final DecimalFormat formatoNumero = new DecimalFormat("#,##0.00", simbolosArgentinos());

    public byte[] generarExcel(ItemizadoVista itemizado) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <html>
                <head>
                <meta charset="UTF-8">
                <style>
                table { border-collapse: collapse; font-family: Arial, sans-serif; font-size: 11px; }
                th, td { border: 1px solid #1f2933; padding: 5px; vertical-align: top; }
                th { background: #18202a; color: #ffffff; font-weight: bold; }
                .num { text-align: right; mso-number-format:"\\#\\.\\#\\#0,00"; }
                .root td { background: #1d6b3a; color: #ffffff; font-weight: bold; }
                .middle td { background: #dff1df; font-weight: bold; }
                .leaf td { background: #fff7cc; font-weight: bold; }
                .total td { background: #18202a; color: #ffffff; font-weight: bold; }
                </style>
                </head>
                <body>
                <table>
                """);
        html.append("<tr>");
        for (String encabezado : ENCABEZADOS) {
            html.append("<th>").append(escaparHtml(encabezado)).append("</th>");
        }
        html.append("</tr>");

        for (ItemizadoNodo nodo : itemizado.nodos()) {
            String clase = nodo.getNivel() == 0 ? "root" : nodo.getNivel() == 1 ? "middle" : "leaf";
            html.append("<tr class=\"").append(clase).append("\">")
                    .append(celda(nodo.getRubro().getCodigo()))
                    .append(celda(""))
                    .append(celda(conIndentacion(nodo.getNivel(), nodo.getRubro().getNombre())))
                    .append(celda(""))
                    .append(celda(""))
                    .append(celdaNumero(null))
                    .append(celdaNumero(null))
                    .append(celdaNumero(nodo.getTotalManoObra()))
                    .append(celdaNumero(nodo.getTotalMateriales()))
                    .append(celdaNumero(nodo.getTotalGeneral()))
                    .append("</tr>");
            for (ItemizadoItemFila item : nodo.getItems()) {
                html.append("<tr>")
                        .append(celda(item.codigoItemizado()))
                        .append(celda(item.manoObra().getItem()))
                        .append(celda(conIndentacion(nodo.getNivel() + 1, item.manoObra().getDetalle())))
                        .append(celda(item.manoObra().getOrdenCompra().getNumero()))
                        .append(celda(item.manoObra().getUnidad()))
                        .append(celdaNumero(item.manoObra().getCantidad()))
                        .append(celdaNumero(item.manoObra().getPrecioUnitario()))
                        .append(celdaNumero(item.manoObra().getImporte()))
                        .append(celdaNumero(item.totalMateriales()))
                        .append(celdaNumero(item.totalGeneral()))
                        .append("</tr>");
            }
        }

        html.append("<tr class=\"total\"><td colspan=\"7\">TOTAL GENERAL</td>")
                .append(celdaNumero(itemizado.totalManoObra()))
                .append(celdaNumero(itemizado.totalMateriales()))
                .append(celdaNumero(itemizado.totalGeneral()))
                .append("</tr>");
        html.append("</table></body></html>");
        return html.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] generarPdf(ItemizadoVista itemizado) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 18, 18, 18, 18);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titulo = new Font(Font.HELVETICA, 14, Font.BOLD, Color.BLACK);
            Font subtitulo = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.DARK_GRAY);
            document.add(new Paragraph("Itemizado de Obra", titulo));
            document.add(new Paragraph("Exportado el " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), subtitulo));
            document.add(new Paragraph(" "));

            PdfPTable tabla = new PdfPTable(ENCABEZADOS.length);
            tabla.setWidthPercentage(100);
            tabla.setWidths(ANCHOS_PDF);
            for (String encabezado : ENCABEZADOS) {
                tabla.addCell(celdaPdf(encabezado, Color.decode("#18202a"), Color.WHITE, true, Element.ALIGN_CENTER));
            }

            for (ItemizadoNodo nodo : itemizado.nodos()) {
                Color fondo = nodo.getNivel() == 0 ? Color.decode("#1d6b3a") : nodo.getNivel() == 1 ? Color.decode("#dff1df") : Color.decode("#fff7cc");
                Color texto = nodo.getNivel() == 0 ? Color.WHITE : Color.BLACK;
                agregarFilaPdf(tabla, fondo, texto, true, List.of(
                        texto(nodo.getRubro().getCodigo()), "", conIndentacion(nodo.getNivel(), nodo.getRubro().getNombre()),
                        "", "", "", "", formato(nodo.getTotalManoObra()), formato(nodo.getTotalMateriales()), formato(nodo.getTotalGeneral())
                ));
                for (ItemizadoItemFila item : nodo.getItems()) {
                    agregarFilaPdf(tabla, Color.WHITE, Color.BLACK, false, List.of(
                            texto(item.codigoItemizado()),
                            texto(item.manoObra().getItem()),
                            conIndentacion(nodo.getNivel() + 1, item.manoObra().getDetalle()),
                            texto(item.manoObra().getOrdenCompra().getNumero()),
                            texto(item.manoObra().getUnidad()),
                            formato(item.manoObra().getCantidad()),
                            formato(item.manoObra().getPrecioUnitario()),
                            formato(item.manoObra().getImporte()),
                            formato(item.totalMateriales()),
                            formato(item.totalGeneral())
                    ));
                }
            }

            PdfPCell total = celdaPdf("TOTAL GENERAL", Color.decode("#18202a"), Color.WHITE, true, Element.ALIGN_RIGHT);
            total.setColspan(7);
            tabla.addCell(total);
            tabla.addCell(celdaPdf(formato(itemizado.totalManoObra()), Color.decode("#18202a"), Color.WHITE, true, Element.ALIGN_RIGHT));
            tabla.addCell(celdaPdf(formato(itemizado.totalMateriales()), Color.decode("#18202a"), Color.WHITE, true, Element.ALIGN_RIGHT));
            tabla.addCell(celdaPdf(formato(itemizado.totalGeneral()), Color.decode("#18202a"), Color.WHITE, true, Element.ALIGN_RIGHT));

            document.add(tabla);
            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el PDF del itemizado.", ex);
        }
    }

    public byte[] generarAvancesSheets(ItemizadoVista itemizado, Map<Long, BigDecimal> avancesPorItem) {
        StringBuilder html = new StringBuilder();
        int[] fila = {1};
        List<Integer> rubrosRaiz = new ArrayList<>();
        html.append("""
                <html xmlns:x="urn:schemas-microsoft-com:office:excel">
                <head>
                <meta charset="UTF-8">
                <style>
                table { border-collapse: collapse; font-family: Arial, sans-serif; font-size: 11px; }
                th, td { border: 1px solid #1f2933; padding: 5px; vertical-align: top; }
                th { background: #18202a; color: #ffffff; font-weight: bold; }
                .num { text-align: right; mso-number-format:"0.00"; }
                .pct { text-align: right; mso-number-format:"0.00%"; }
                .root td { background: #1d6b3a; color: #ffffff; font-weight: bold; }
                .middle td { background: #dff1df; font-weight: bold; }
                .leaf td { background: #fff7cc; font-weight: bold; }
                .item td { background: #ffffff; }
                .material td { background: #eef6ff; color: #1f2933; }
                .total td { background: #18202a; color: #ffffff; font-weight: bold; }
                </style>
                </head>
                <body>
                <table>
                """);
        html.append("<tr>");
        for (String encabezado : ENCABEZADOS_AVANCES) {
            html.append("<th>").append(escaparHtml(encabezado)).append("</th>");
        }
        html.append("</tr>");
        for (ItemizadoNodo raiz : itemizado.raices()) {
            rubrosRaiz.add(agregarNodoAvanceHtml(html, raiz, avancesPorItem, fila));
        }
        int totalRow = ++fila[0];
        boolean tieneRubros = !rubrosRaiz.isEmpty();
        html.append("<tr class=\"total\">")
                .append(celda("")).append(celda("TOTAL")).append(celda("")).append(celda("TOTAL GENERAL"))
                .append(celda("")).append(celda("")).append(celda("")).append(celda("")).append(celda("")).append(celda(""))
                .append(celdaFormula(sumarReferencias("K", rubrosRaiz), "num"))
                .append(celdaFormula(sumarReferencias("L", rubrosRaiz), "num"))
                .append(celdaFormula("K" + totalRow + "+L" + totalRow, "num"))
                .append(tieneRubros ? celdaFormula(formulaPorcentaje("O" + totalRow, "K" + totalRow), "pct") : celdaPorcentajePlano(BigDecimal.ZERO))
                .append(celdaFormula(sumarReferencias("O", rubrosRaiz), "num"))
                .append(celdaFormula(formulaSaldo("K" + totalRow, "O" + totalRow), "num"))
                .append(celda(""))
                .append("</tr>");
        html.append("</table></body></html>");
        return html.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public String nombreArchivo(String extension) {
        return "itemizado-obra-" + LocalDate.now().format(FECHA_ARCHIVO) + "." + extension;
    }

    public String nombreArchivo(String sufijo, String extension) {
        return "itemizado-" + sufijo + "-" + LocalDate.now().format(FECHA_ARCHIVO) + "." + extension;
    }

    private void agregarFilaPdf(PdfPTable tabla, Color fondo, Color texto, boolean negrita, List<String> valores) {
        for (int i = 0; i < valores.size(); i++) {
            int alineacion = i >= 5 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
            tabla.addCell(celdaPdf(valores.get(i), fondo, texto, negrita, alineacion));
        }
    }

    private PdfPCell celdaPdf(String valor, Color fondo, Color texto, boolean negrita, int alineacion) {
        Font fuente = new Font(Font.HELVETICA, 6.5f, negrita ? Font.BOLD : Font.NORMAL, texto);
        PdfPCell celda = new PdfPCell(new Phrase(texto(valor), fuente));
        celda.setBackgroundColor(fondo);
        celda.setHorizontalAlignment(alineacion);
        celda.setVerticalAlignment(Element.ALIGN_TOP);
        celda.setPadding(3);
        celda.setBorderColor(Color.decode("#1f2933"));
        return celda;
    }

    private String celda(String valor) {
        return "<td>" + escaparHtml(valor) + "</td>";
    }

    private String celdaNumero(BigDecimal valor) {
        return "<td class=\"num\">" + (valor == null ? "" : escaparHtml(formato(valor))) + "</td>";
    }

    private String celdaNumeroPlano(BigDecimal valor) {
        if (valor == null) {
            return "<td class=\"num\"></td>";
        }
        String numero = valor.stripTrailingZeros().toPlainString();
        return "<td class=\"num\" x:num=\"" + escaparHtml(numero) + "\">" + escaparHtml(numero) + "</td>";
    }

    private String celdaPorcentajePlano(BigDecimal valor) {
        if (valor == null) {
            return "<td class=\"pct\"></td>";
        }
        BigDecimal porcentaje = valor.divide(BigDecimal.valueOf(100), 8, java.math.RoundingMode.HALF_UP);
        String numero = porcentaje.stripTrailingZeros().toPlainString();
        return "<td class=\"pct\" x:num=\"" + escaparHtml(numero) + "\">" + escaparHtml(numero) + "</td>";
    }

    private String celdaFormula(String formula, String clase) {
        String formulaExcel = "=" + formula;
        return "<td class=\"" + escaparHtml(clase) + "\" x:fmla=\"" + escaparHtml(formulaExcel) + "\">"
                + escaparHtml(formulaExcel)
                + "</td>";
    }

    private String token(String columna, int fila) {
        return "__FORMULA_" + columna + fila + "__";
    }

    private void reemplazarUltimaFormula(StringBuilder html, String token, String formula) {
        int indice = html.lastIndexOf(token);
        if (indice >= 0) {
            html.replace(indice, indice + token.length(), formula);
        }
    }

    private String sumarReferencias(String columna, List<Integer> filas) {
        if (filas.isEmpty()) {
            return "0";
        }
        return filas.stream()
                .map(fila -> columna + fila)
                .collect(java.util.stream.Collectors.joining("+"));
    }

    private String formulaPorcentaje(String certificado, String total) {
        return "IFERROR(" + certificado + "/" + total + ",0)";
    }

    private String formulaSaldo(String total, String certificado) {
        return total + "-" + certificado;
    }

    private String formato(BigDecimal valor) {
        return valor == null ? "" : formatoNumero.format(valor);
    }

    private int agregarNodoAvanceHtml(StringBuilder html,
                                       ItemizadoNodo nodo,
                                       Map<Long, BigDecimal> avancesPorItem,
                                       int[] fila) {
        int rubroRow = ++fila[0];
        List<Integer> filasDirectas = new ArrayList<>();
        String clase = nodo.getNivel() == 0 ? "root" : nodo.getNivel() == 1 ? "middle" : "leaf";
        String tokenK = token("K", rubroRow);
        String tokenL = token("L", rubroRow);
        String tokenM = token("M", rubroRow);
        String tokenN = token("N", rubroRow);
        String tokenO = token("O", rubroRow);
        String tokenP = token("P", rubroRow);
        html.append("<tr class=\"").append(clase).append("\">")
                .append(celda(String.valueOf(nodo.getNivel())))
                .append(celda("Rubro"))
                .append(celda(nodo.getRubro().getCodigo()))
                .append(celda(conIndentacion(nodo.getNivel(), nodo.getRubro().getNombre())))
                .append(celda("")).append(celda("")).append(celda("")).append(celda("")).append(celda("")).append(celda(""))
                .append(celdaFormula(tokenK, "num"))
                .append(celdaFormula(tokenL, "num"))
                .append(celdaFormula(tokenM, "num"))
                .append(celdaFormula(tokenN, "pct"))
                .append(celdaFormula(tokenO, "num"))
                .append(celdaFormula(tokenP, "num"))
                .append(celda(""))
                .append("</tr>");

        for (ItemizadoItemFila item : nodo.getItems()) {
            BigDecimal avanceItem = avancesPorItem.getOrDefault(item.manoObra().getId(), BigDecimal.ZERO);
            BigDecimal importeManoObra = item.manoObra().getImporte() == null ? BigDecimal.ZERO : item.manoObra().getImporte();
            int itemRow = ++fila[0];
            filasDirectas.add(itemRow);
            int primerMaterialRow = itemRow + 1;
            String tokenItemL = token("L", itemRow);
            html.append("<tr class=\"item\">")
                    .append(celda(String.valueOf(nodo.getNivel() + 1)))
                    .append(celda("Item"))
                    .append(celda(item.codigoItemizado()))
                    .append(celda(nodo.getRubro().getNombre()))
                    .append(celda(item.manoObra().getItem()))
                    .append(celda(item.manoObra().getOrdenCompra().getNumero()))
                    .append(celda(item.manoObra().getOrdenCompra().getProveedorEntidad() == null ? "" : item.manoObra().getOrdenCompra().getProveedorEntidad().getNombre()))
                    .append(celda(conIndentacion(nodo.getNivel() + 1, item.manoObra().getDetalle())))
                    .append(celda(item.manoObra().getUnidad()))
                    .append(celdaNumeroPlano(item.manoObra().getCantidad()))
                    .append(celdaNumeroPlano(importeManoObra))
                    .append(celdaFormula(tokenItemL, "num"))
                    .append(celdaFormula("K" + itemRow + "+L" + itemRow, "num"))
                    .append(celdaPorcentajePlano(avanceItem))
                    .append(celdaFormula("K" + itemRow + "*N" + itemRow, "num"))
                    .append(celdaFormula(formulaSaldo("K" + itemRow, "O" + itemRow), "num"))
                    .append(celda(estadoAvance(avanceItem)))
                    .append("</tr>");

            for (int i = 0; i < item.materiales().size(); i++) {
                var material = item.materiales().get(i);
                int materialRow = ++fila[0];
                html.append("<tr class=\"material\">")
                        .append(celda(String.valueOf(nodo.getNivel() + 2)))
                        .append(celda("Material"))
                        .append(celda(item.codigoItemizado() + ".M" + (i + 1)))
                        .append(celda(nodo.getRubro().getNombre()))
                        .append(celda(material.getItem()))
                        .append(celda(material.getOrdenCompra().getNumero()))
                        .append(celda(material.getOrdenCompra().getProveedorEntidad() == null ? "" : material.getOrdenCompra().getProveedorEntidad().getNombre()))
                        .append(celda(conIndentacion(nodo.getNivel() + 2, material.getDetalle())))
                        .append(celda(material.getUnidad()))
                        .append(celdaNumeroPlano(material.getCantidad()))
                        .append(celdaNumeroPlano(BigDecimal.ZERO))
                        .append(celdaNumeroPlano(material.getImporte()))
                        .append(celdaFormula("K" + materialRow + "+L" + materialRow, "num"))
                        .append(celda(""))
                        .append(celda(""))
                        .append(celda(""))
                        .append(celda("Material vinculado"))
                        .append("</tr>");
            }

            if (!item.materiales().isEmpty()) {
                int ultimoMaterialRow = fila[0];
                reemplazarUltimaFormula(html, tokenItemL, sumarRango("L", primerMaterialRow, ultimoMaterialRow));
            } else {
                reemplazarUltimaFormula(html, tokenItemL, "0");
            }
        }

        for (ItemizadoNodo hijo : nodo.getHijos()) {
            filasDirectas.add(agregarNodoAvanceHtml(html, hijo, avancesPorItem, fila));
        }

        reemplazarUltimaFormula(html, tokenK, sumarReferencias("K", filasDirectas));
        reemplazarUltimaFormula(html, tokenL, sumarReferencias("L", filasDirectas));
        reemplazarUltimaFormula(html, tokenM, "K" + rubroRow + "+L" + rubroRow);
        reemplazarUltimaFormula(html, tokenN, formulaPorcentaje("O" + rubroRow, "K" + rubroRow));
        reemplazarUltimaFormula(html, tokenO, sumarReferencias("O", filasDirectas));
        reemplazarUltimaFormula(html, tokenP, formulaSaldo("K" + rubroRow, "O" + rubroRow));
        return rubroRow;
    }

    private String sumarRango(String columna, int inicio, int fin) {
        List<Integer> filas = new ArrayList<>();
        for (int fila = inicio; fila <= fin; fila++) {
            filas.add(fila);
        }
        return sumarReferencias(columna, filas);
    }

    private String estadoAvance(BigDecimal avance) {
        BigDecimal avanceSeguro = avance == null ? BigDecimal.ZERO : avance;
        if (avanceSeguro.compareTo(BigDecimal.ZERO) <= 0) {
            return "Pendiente";
        }
        if (avanceSeguro.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return "Terminado";
        }
        return "En ejecucion";
    }

    private String conIndentacion(int nivel, String texto) {
        return "   ".repeat(Math.max(0, nivel)) + texto(texto);
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private String escaparHtml(String valor) {
        return texto(valor)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static DecimalFormatSymbols simbolosArgentinos() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("es", "AR"));
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        return symbols;
    }
}
