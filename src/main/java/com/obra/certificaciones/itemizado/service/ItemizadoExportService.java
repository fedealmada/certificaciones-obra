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
import java.math.RoundingMode;
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
        StringBuilder csv = new StringBuilder("\uFEFF");
        agregarFilaCsv(csv, List.of(ENCABEZADOS_AVANCES));
        for (ItemizadoNodo raiz : itemizado.raices()) {
            agregarNodoAvanceCsv(csv, raiz, avancesPorItem);
        }
        agregarFilaCsv(csv, List.of(
                "", "TOTAL", "", "TOTAL GENERAL", "", "", "", "", "", "",
                formato(itemizado.totalManoObra()),
                formato(itemizado.totalMateriales()),
                formato(itemizado.totalGeneral()),
                "", "", "", ""
        ));
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
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

    private String formato(BigDecimal valor) {
        return valor == null ? "" : formatoNumero.format(valor);
    }

    private void agregarNodoAvanceCsv(StringBuilder csv, ItemizadoNodo nodo, Map<Long, BigDecimal> avancesPorItem) {
        BigDecimal montoCertificado = montoCertificadoNodo(nodo, avancesPorItem);
        BigDecimal avance = porcentaje(montoCertificado, nodo.getTotalManoObra());
        agregarFilaCsv(csv, List.of(
                String.valueOf(nodo.getNivel()),
                "Rubro",
                texto(nodo.getRubro().getCodigo()),
                texto(nodo.getRubro().getNombre()),
                "", "", "", "", "", "",
                formato(nodo.getTotalManoObra()),
                formato(nodo.getTotalMateriales()),
                formato(nodo.getTotalGeneral()),
                formato(avance),
                formato(montoCertificado),
                formato(nodo.getTotalManoObra().subtract(montoCertificado).max(BigDecimal.ZERO)),
                estadoAvance(avance)
        ));

        for (ItemizadoItemFila item : nodo.getItems()) {
            BigDecimal avanceItem = avancesPorItem.getOrDefault(item.manoObra().getId(), BigDecimal.ZERO);
            BigDecimal importeManoObra = item.manoObra().getImporte() == null ? BigDecimal.ZERO : item.manoObra().getImporte();
            BigDecimal montoItem = montoCertificado(importeManoObra, avanceItem);
            agregarFilaCsv(csv, List.of(
                    String.valueOf(nodo.getNivel() + 1),
                    "Item",
                    texto(item.codigoItemizado()),
                    texto(nodo.getRubro().getNombre()),
                    texto(item.manoObra().getItem()),
                    texto(item.manoObra().getOrdenCompra().getNumero()),
                    item.manoObra().getOrdenCompra().getProveedorEntidad() == null ? "" : texto(item.manoObra().getOrdenCompra().getProveedorEntidad().getNombre()),
                    texto(item.manoObra().getDetalle()),
                    texto(item.manoObra().getUnidad()),
                    formato(item.manoObra().getCantidad()),
                    formato(importeManoObra),
                    formato(item.totalMateriales()),
                    formato(item.totalGeneral()),
                    formato(avanceItem),
                    formato(montoItem),
                    formato(importeManoObra.subtract(montoItem).max(BigDecimal.ZERO)),
                    estadoAvance(avanceItem)
            ));
        }

        for (ItemizadoNodo hijo : nodo.getHijos()) {
            agregarNodoAvanceCsv(csv, hijo, avancesPorItem);
        }
    }

    private BigDecimal montoCertificadoNodo(ItemizadoNodo nodo, Map<Long, BigDecimal> avancesPorItem) {
        BigDecimal total = nodo.getItems().stream()
                .map(item -> montoCertificado(
                        item.manoObra().getImporte() == null ? BigDecimal.ZERO : item.manoObra().getImporte(),
                        avancesPorItem.getOrDefault(item.manoObra().getId(), BigDecimal.ZERO)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        for (ItemizadoNodo hijo : nodo.getHijos()) {
            total = total.add(montoCertificadoNodo(hijo, avancesPorItem));
        }
        return total;
    }

    private BigDecimal montoCertificado(BigDecimal importe, BigDecimal porcentaje) {
        BigDecimal importeSeguro = importe == null ? BigDecimal.ZERO : importe;
        BigDecimal porcentajeSeguro = porcentaje == null ? BigDecimal.ZERO : porcentaje;
        return importeSeguro.multiply(porcentajeSeguro).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal porcentaje(BigDecimal valor, BigDecimal total) {
        if (valor == null || total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return valor.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
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

    private void agregarFilaCsv(StringBuilder csv, List<String> valores) {
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) {
                csv.append(';');
            }
            csv.append(escaparCsv(valores.get(i)));
        }
        csv.append("\r\n");
    }

    private String escaparCsv(String valor) {
        String texto = texto(valor);
        if (texto.contains(";") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
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
