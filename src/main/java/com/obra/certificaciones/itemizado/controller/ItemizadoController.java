package com.obra.certificaciones.itemizado.controller;

import com.obra.certificaciones.certificacion.service.CertificacionCalculoService;
import com.obra.certificaciones.itemizado.dto.ItemizadoVista;
import com.obra.certificaciones.itemizado.service.ItemizadoExportService;
import com.obra.certificaciones.itemizado.service.ItemizadoService;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.rubro.service.RubroService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/itemizado")
@RequiredArgsConstructor
public class ItemizadoController {

    private final ItemizadoService itemizadoService;
    private final ItemizadoExportService itemizadoExportService;
    private final CertificacionCalculoService calculoService;
    private final RubroService rubroService;

    @GetMapping
    public String ver(Model model) {
        model.addAttribute("itemizado", itemizadoService.generar());
        model.addAttribute("rubros", rubroService.listarActivos());
        return "itemizado/arbol";
    }

    @PostMapping("/manuales")
    public String crearManual(@RequestParam Long rubroId,
                              @RequestParam(required = false) String item,
                              @RequestParam String detalle,
                              @RequestParam(required = false) String unidad,
                              @RequestParam(required = false) BigDecimal cantidad,
                              @RequestParam(required = false) BigDecimal precioUnitario,
                              RedirectAttributes redirectAttributes) {
        try {
            itemizadoService.crearManual(rubroId, item, detalle, unidad, cantidad, precioUnitario);
            redirectAttributes.addFlashAttribute("accionCompletada", true);
            redirectAttributes.addFlashAttribute("accionTitulo", "Item agregado");
            redirectAttributes.addFlashAttribute("accionMensaje", "El item manual ya forma parte del itemizado.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/itemizado";
    }

    @PostMapping("/manuales/{id}/materiales")
    public String crearMaterialManual(@PathVariable Long id,
                                      @RequestParam String detalle,
                                      @RequestParam(required = false) String unidad,
                                      @RequestParam(required = false) BigDecimal cantidad,
                                      @RequestParam(required = false) BigDecimal precioUnitario,
                                      RedirectAttributes redirectAttributes) {
        try {
            itemizadoService.crearMaterialManual(id, detalle, unidad, cantidad, precioUnitario);
            redirectAttributes.addFlashAttribute("accionCompletada", true);
            redirectAttributes.addFlashAttribute("accionTitulo", "Material asociado");
            redirectAttributes.addFlashAttribute("accionMensaje", "El material manual se sumo al item.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/itemizado";
    }

    @PostMapping("/manuales/{id}/eliminar")
    public String eliminarManual(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        itemizadoService.eliminarManual(id);
        redirectAttributes.addFlashAttribute("accionCompletada", true);
        redirectAttributes.addFlashAttribute("accionTitulo", "Item eliminado");
        redirectAttributes.addFlashAttribute("accionMensaje", "El item manual se quito del itemizado.");
        return "redirect:/itemizado";
    }

    @GetMapping("/exportar/excel")
    public ResponseEntity<byte[]> exportarExcel() {
        ItemizadoVista itemizado = itemizadoService.generar();
        byte[] contenido = itemizadoExportService.generarExcel(itemizado);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, adjunto(itemizadoExportService.nombreArchivo("xls")))
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel; charset=UTF-8"))
                .body(contenido);
    }

    @GetMapping("/exportar/pdf")
    public ResponseEntity<byte[]> exportarPdf() {
        ItemizadoVista itemizado = itemizadoService.generar();
        byte[] contenido = itemizadoExportService.generarPdf(itemizado);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, adjunto(itemizadoExportService.nombreArchivo("pdf")))
                .contentType(MediaType.APPLICATION_PDF)
                .body(contenido);
    }

    @GetMapping("/exportar/avances-sheets")
    public ResponseEntity<byte[]> exportarAvancesSheets() {
        ItemizadoVista itemizado = itemizadoService.generar();
        Map<Long, java.math.BigDecimal> avancesPorItem = calculoService.porcentajesAcumuladosPorOrdenes(itemizado.nodos().stream()
                        .flatMap(nodo -> nodo.getItems().stream())
                        .filter(item -> !item.esManual())
                        .map(item -> item.manoObra().getOrdenCompra())
                        .map(OrdenCompra::getId)
                        .distinct()
                        .toList())
                .values()
                .stream()
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, java.math.BigDecimal::add));
        byte[] contenido = itemizadoExportService.generarAvancesSheets(itemizado, avancesPorItem);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, adjunto(itemizadoExportService.nombreArchivo("avances-sheets", "xls")))
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel; charset=UTF-8"))
                .body(contenido);
    }

    private String adjunto(String archivo) {
        return ContentDisposition.attachment().filename(archivo).build().toString();
    }
}
