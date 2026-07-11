package com.obra.certificaciones.itemizado.controller;

import com.obra.certificaciones.certificacion.service.CertificacionCalculoService;
import com.obra.certificaciones.itemizado.dto.ItemizadoVista;
import com.obra.certificaciones.itemizado.service.ItemizadoExportService;
import com.obra.certificaciones.itemizado.service.ItemizadoService;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/itemizado")
@RequiredArgsConstructor
public class ItemizadoController {

    private final ItemizadoService itemizadoService;
    private final ItemizadoExportService itemizadoExportService;
    private final CertificacionCalculoService calculoService;

    @GetMapping
    public String ver(Model model) {
        model.addAttribute("itemizado", itemizadoService.generar());
        return "itemizado/arbol";
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
