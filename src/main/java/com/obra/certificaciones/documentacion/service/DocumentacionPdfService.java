package com.obra.certificaciones.documentacion.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.obra.certificaciones.documentacion.dto.DocumentacionResumen;
import com.obra.certificaciones.documentacion.dto.GrupoDocumentacionContratista;
import com.obra.certificaciones.documentacion.entity.DocumentoObra;
import com.obra.certificaciones.documentacion.entity.EstadoCarpetaFisica;
import com.obra.certificaciones.documentacion.entity.EstadoDocumentoObra;
import com.obra.certificaciones.obra.entity.Obra;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DocumentacionPdfService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_ARCHIVO = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Color AZUL = Color.decode("#2563EB");
    private static final Color TINTA = Color.decode("#0F172A");
    private static final Color MUTED = Color.decode("#64748B");
    private static final Color LINEA = Color.decode("#D9E2EF");
    private static final Color FONDO_SUAVE = Color.decode("#F7F9FC");
    private static final Color VERDE = Color.decode("#22C55E");
    private static final Color NARANJA = Color.decode("#F59E0B");
    private static final Color ROJO = Color.decode("#EF4444");

    private final DocumentacionService documentacionService;

    public byte[] generarResumenEstado(Obra obra) {
        List<GrupoDocumentacionContratista> grupos = documentacionService.agruparPorContratista(obra);
        DocumentacionResumen resumen = documentacionService.resumen(obra);
        List<DocumentoObra> documentos = grupos.stream()
                .flatMap(grupo -> grupo.documentos().stream())
                .toList();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 28, 28, 30, 28);
            PdfWriter.getInstance(document, out);
            document.open();

            agregarPortada(document, obra, resumen, grupos.size());
            agregarSintesis(document, documentos);
            agregarResumenCarpetas(document, grupos);
            agregarAlertas(document, "Documentos vencidos", documentos.stream()
                    .filter(documento -> documento.estado() == EstadoDocumentoObra.VENCIDO)
                    .sorted(Comparator.comparing(DocumentoObra::getFechaVencimiento, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList(), ROJO);
            agregarAlertas(document, "Documentos proximos a vencer", documentos.stream()
                    .filter(documento -> documento.estado() == EstadoDocumentoObra.POR_VENCER)
                    .sorted(Comparator.comparing(DocumentoObra::getFechaVencimiento, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList(), NARANJA);
            agregarAlertas(document, "Documentos pendientes de presentar", documentos.stream()
                    .filter(documento -> documento.estado() == EstadoDocumentoObra.PENDIENTE)
                    .sorted(Comparator.comparing(DocumentoObra::sujetoNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList(), AZUL);
            agregarCarpetaFisica(document, documentos.stream()
                    .filter(DocumentoObra::carpetaFisicaPendiente)
                    .sorted(Comparator.comparing(DocumentoObra::sujetoNombre, Comparator.nullsLast(String::compareToIgnoreCase))
                            .thenComparing(DocumentoObra::nombreDocumento, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList());

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar el resumen PDF de documentacion.", ex);
        }
    }

    public String nombreArchivoResumen() {
        return "resumen-documentacion-obra-" + LocalDate.now().format(FECHA_ARCHIVO) + ".pdf";
    }

    private void agregarPortada(Document document, Obra obra, DocumentacionResumen resumen, int totalCarpetas) throws Exception {
        PdfPTable header = new PdfPTable(new float[]{1.2f, 5f});
        header.setWidthPercentage(100);
        PdfPCell icono = celda("DOC", fuente(18, Font.BOLD, Color.WHITE), AZUL, Color.WHITE, Element.ALIGN_CENTER);
        icono.setMinimumHeight(52);
        header.addCell(icono);
        PdfPCell titulo = sinBorde();
        titulo.addElement(parrafo("Resumen ejecutivo de documentacion de obra", fuente(18, Font.BOLD, TINTA), 0, 2));
        titulo.addElement(parrafo(texto(obra.getNombre()) + " - Emitido el " + LocalDate.now().format(FECHA), fuente(9, Font.NORMAL, MUTED), 0, 0));
        if (tieneTexto(obra.getCliente()) || tieneTexto(obra.getUbicacion())) {
            titulo.addElement(parrafo("Cliente: " + texto(obra.getCliente()) + " | Ubicacion: " + texto(obra.getUbicacion()), fuente(8, Font.NORMAL, MUTED), 2, 0));
        }
        header.addCell(titulo);
        document.add(header);
        document.add(espacio(10));

        PdfPTable kpis = new PdfPTable(5);
        kpis.setWidthPercentage(100);
        kpis.setWidths(new float[]{1, 1, 1, 1, 1});
        kpi(kpis, String.valueOf(totalCarpetas), "Carpetas", "legajos controlados", AZUL);
        kpi(kpis, String.valueOf(resumen.aptos()), "Vigentes", "documentos al dia", VERDE);
        kpi(kpis, String.valueOf(resumen.porVencer()), "Por vencer", "seguimiento cercano", NARANJA);
        kpi(kpis, String.valueOf(resumen.vencidos()), "Vencidos", "requieren gestion", ROJO);
        kpi(kpis, String.valueOf(resumen.carpetaFisicaPendiente()), "Fisico pendiente", "impresion o archivo", Color.decode("#7C3AED"));
        document.add(kpis);
        document.add(espacio(12));
    }

    private void agregarSintesis(Document document, List<DocumentoObra> documentos) throws Exception {
        long digitalesCriticos = documentos.stream().filter(documento ->
                documento.estado() == EstadoDocumentoObra.VENCIDO
                        || documento.estado() == EstadoDocumentoObra.POR_VENCER
                        || documento.estado() == EstadoDocumentoObra.PENDIENTE).count();
        long fisicosPendientes = documentos.stream().filter(DocumentoObra::carpetaFisicaPendiente).count();
        String conclusion = digitalesCriticos == 0 && fisicosPendientes == 0
                ? "La documentacion relevada no presenta alertas criticas al momento de la emision."
                : "Se detectaron puntos de seguimiento para mantener el legajo digital y la carpeta fisica actualizados.";
        PdfPTable panel = new PdfPTable(1);
        panel.setWidthPercentage(100);
        PdfPCell celda = celda("", fuente(9, Font.NORMAL, TINTA), FONDO_SUAVE, LINEA, Element.ALIGN_LEFT);
        celda.addElement(parrafo("Lectura ejecutiva", fuente(11, Font.BOLD, TINTA), 0, 4));
        celda.addElement(parrafo(conclusion, fuente(9, Font.NORMAL, TINTA), 0, 4));
        celda.addElement(parrafo("Este informe prioriza vencidos, proximos vencimientos, documentos pendientes y copias fisicas faltantes o desactualizadas.", fuente(8, Font.NORMAL, MUTED), 0, 0));
        panel.addCell(celda);
        document.add(panel);
        document.add(espacio(10));
    }

    private void agregarResumenCarpetas(Document document, List<GrupoDocumentacionContratista> grupos) throws Exception {
        document.add(tituloSeccion("Estado por carpeta documental"));
        PdfPTable tabla = new PdfPTable(new float[]{3.2f, 0.8f, 0.9f, 0.9f, 0.9f, 1f, 1.1f});
        tabla.setWidthPercentage(100);
        encabezados(tabla, "Carpeta", "Total", "Vig.", "Por venc.", "Venc.", "Pend.", "Estado");
        for (GrupoDocumentacionContratista grupo : grupos) {
            tabla.addCell(celdaTexto(grupo.nombre()));
            tabla.addCell(celdaCentro(String.valueOf(grupo.total())));
            tabla.addCell(celdaCentro(String.valueOf(grupo.aptos())));
            tabla.addCell(celdaCentro(String.valueOf(grupo.porVencer())));
            tabla.addCell(celdaCentro(String.valueOf(grupo.vencidos())));
            tabla.addCell(celdaCentro(String.valueOf(grupo.pendientes())));
            tabla.addCell(celdaEstado(grupo.estado().getDescripcion(), colorEstado(grupo.estado())));
        }
        document.add(tabla);
        document.add(espacio(10));
    }

    private void agregarAlertas(Document document, String titulo, List<DocumentoObra> documentos, Color color) throws Exception {
        document.add(tituloSeccion(titulo));
        if (documentos.isEmpty()) {
            document.add(parrafo("Sin registros en esta categoria.", fuente(8, Font.NORMAL, MUTED), 0, 8));
            return;
        }
        PdfPTable tabla = new PdfPTable(new float[]{2.4f, 2.7f, 1.2f, 1.1f, 1.4f});
        tabla.setWidthPercentage(100);
        encabezados(tabla, "Carpeta / sujeto", "Documento", "Vencimiento", "Dias", "Accion sugerida");
        for (DocumentoObra documento : documentos) {
            tabla.addCell(celdaTexto(documento.sujetoNombre()));
            tabla.addCell(celdaTexto(documento.nombreDocumento()));
            tabla.addCell(celdaCentro(fecha(documento.getFechaVencimiento())));
            tabla.addCell(celdaCentro(documento.getFechaVencimiento() == null ? "-" : String.valueOf(documento.diasAlVencimiento())));
            tabla.addCell(celdaEstado(accionDigital(documento), color));
        }
        document.add(tabla);
        document.add(espacio(10));
    }

    private void agregarCarpetaFisica(Document document, List<DocumentoObra> documentos) throws Exception {
        document.add(tituloSeccion("Carpeta fisica: copias impresas y actualizaciones"));
        if (documentos.isEmpty()) {
            document.add(parrafo("No hay pendientes de impresion o archivo fisico.", fuente(8, Font.NORMAL, MUTED), 0, 8));
            return;
        }
        PdfPTable tabla = new PdfPTable(new float[]{2.2f, 2.5f, 1.4f, 1.4f, 2.1f});
        tabla.setWidthPercentage(100);
        encabezados(tabla, "Carpeta / sujeto", "Documento", "Estado fisico", "Vto. impreso", "Que hacer");
        for (DocumentoObra documento : documentos) {
            tabla.addCell(celdaTexto(documento.sujetoNombre()));
            tabla.addCell(celdaTexto(documento.nombreDocumento()));
            tabla.addCell(celdaEstado(documento.estadoCarpetaFisica().getDescripcion(), colorFisico(documento.estadoCarpetaFisica())));
            tabla.addCell(celdaCentro(fecha(documento.getFechaVencimientoFisico())));
            tabla.addCell(celdaTexto(documento.accionCarpetaFisica()));
        }
        document.add(tabla);
    }

    private void kpi(PdfPTable tabla, String numero, String titulo, String subtitulo, Color color) {
        PdfPCell cell = celda("", fuente(9, Font.NORMAL, TINTA), Color.WHITE, LINEA, Element.ALIGN_LEFT);
        cell.setPadding(8);
        cell.addElement(parrafo(numero, fuente(20, Font.BOLD, color), 0, 0));
        cell.addElement(parrafo(titulo, fuente(8, Font.BOLD, TINTA), 0, 1));
        cell.addElement(parrafo(subtitulo, fuente(7, Font.NORMAL, MUTED), 0, 0));
        tabla.addCell(cell);
    }

    private void encabezados(PdfPTable tabla, String... valores) {
        for (String valor : valores) {
            tabla.addCell(celda(valor, fuente(7, Font.BOLD, Color.WHITE), TINTA, TINTA, Element.ALIGN_CENTER));
        }
    }

    private Paragraph tituloSeccion(String texto) {
        return parrafo(texto, fuente(11, Font.BOLD, TINTA), 4, 6);
    }

    private PdfPCell celdaTexto(String texto) {
        return celda(texto(texto), fuente(7, Font.NORMAL, TINTA), Color.WHITE, LINEA, Element.ALIGN_LEFT);
    }

    private PdfPCell celdaCentro(String texto) {
        return celda(texto(texto), fuente(7, Font.NORMAL, TINTA), Color.WHITE, LINEA, Element.ALIGN_CENTER);
    }

    private PdfPCell celdaEstado(String texto, Color color) {
        PdfPCell cell = celda(texto(texto), fuente(7, Font.BOLD, color), Color.WHITE, color, Element.ALIGN_CENTER);
        cell.setPadding(5);
        return cell;
    }

    private PdfPCell celda(String texto, Font font, Color fondo, Color borde, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(texto(texto), font));
        cell.setBackgroundColor(fondo);
        cell.setBorderColor(borde);
        cell.setBorderWidth(0.6f);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6);
        return cell;
    }

    private PdfPCell sinBorde() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingLeft(10);
        return cell;
    }

    private Paragraph parrafo(String texto, Font font, float before, float after) {
        Paragraph p = new Paragraph(texto(texto), font);
        p.setSpacingBefore(before);
        p.setSpacingAfter(after);
        return p;
    }

    private Paragraph espacio(float alto) {
        Paragraph p = new Paragraph(new Chunk(" "));
        p.setSpacingAfter(alto);
        return p;
    }

    private Font fuente(int size, int style, Color color) {
        return new Font(Font.HELVETICA, size, style, color);
    }

    private Color colorEstado(EstadoDocumentoObra estado) {
        return switch (estado) {
            case APTO, SIN_VENCIMIENTO -> VERDE;
            case POR_VENCER -> NARANJA;
            case VENCIDO -> ROJO;
            case PENDIENTE -> AZUL;
            case NO_APLICA -> MUTED;
        };
    }

    private Color colorFisico(EstadoCarpetaFisica estado) {
        return switch (estado) {
            case ACTUALIZADA -> VERDE;
            case DESACTUALIZADA -> NARANJA;
            case FALTANTE -> ROJO;
        };
    }

    private String accionDigital(DocumentoObra documento) {
        return switch (documento.estado()) {
            case VENCIDO -> "Renovar urgente";
            case POR_VENCER -> "Pedir renovacion";
            case PENDIENTE -> "Solicitar carga";
            default -> "Sin accion";
        };
    }

    private String fecha(LocalDate fecha) {
        return fecha == null ? "No aplica" : fecha.format(FECHA);
    }

    private boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private String texto(String valor) {
        return Objects.toString(valor, "-").isBlank() ? "-" : valor;
    }
}
