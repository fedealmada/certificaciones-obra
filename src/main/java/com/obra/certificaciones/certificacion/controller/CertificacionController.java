package com.obra.certificaciones.certificacion.controller;

import com.obra.certificaciones.certificacion.dto.ItemNuevaCertificacionVista;
import com.obra.certificaciones.certificacion.dto.NuevaCertificacionForm;
import com.obra.certificaciones.certificacion.entity.Certificacion;
import com.obra.certificaciones.certificacion.service.CertificacionCalculoService;
import com.obra.certificaciones.certificacion.service.CertificacionExportService;
import com.obra.certificaciones.certificacion.service.CertificacionService;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.service.OrdenCompraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/oc/{ordenCompraId}/certificaciones")
@RequiredArgsConstructor
public class CertificacionController {
    private final CertificacionService certificacionService;
    private final CertificacionCalculoService calculoService;
    private final OrdenCompraService ordenCompraService;
    private final CertificacionExportService exportService;

    @GetMapping("/nueva")
    public String nueva(@PathVariable Long ordenCompraId, Model model) {
        cargarModeloFormulario(ordenCompraId, null, certificacionService.crearForm(ordenCompraId), model);
        return "certificacion/form";
    }

    @PostMapping
    public String guardar(@PathVariable Long ordenCompraId, @ModelAttribute("form") NuevaCertificacionForm form, Model model) {
        try {
            Certificacion certificacion = certificacionService.guardar(ordenCompraId, form);
            return "redirect:/oc/" + ordenCompraId + "/certificaciones/" + certificacion.getId();
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            cargarModeloFormulario(ordenCompraId, null, form, model);
            return "certificacion/form";
        }
    }

    @GetMapping("/{certificacionId}/editar")
    public String editar(@PathVariable Long ordenCompraId, @PathVariable Long certificacionId, Model model) {
        cargarModeloFormulario(ordenCompraId, certificacionId, certificacionService.crearFormEdicion(ordenCompraId, certificacionId), model);
        return "certificacion/form";
    }

    @PostMapping("/{certificacionId}")
    public String actualizar(@PathVariable Long ordenCompraId,
                             @PathVariable Long certificacionId,
                             @ModelAttribute("form") NuevaCertificacionForm form,
                             Model model) {
        try {
            Certificacion certificacion = certificacionService.actualizar(ordenCompraId, certificacionId, form);
            return "redirect:/oc/" + ordenCompraId + "/certificaciones/" + certificacion.getId();
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            cargarModeloFormulario(ordenCompraId, certificacionId, form, model);
            return "certificacion/form";
        }
    }

    private void cargarModeloFormulario(Long ordenCompraId, Long certificacionId, NuevaCertificacionForm form, Model model) {
        OrdenCompra orden = ordenCompraService.obtener(ordenCompraId);
        Map<Long, BigDecimal> porcentajesActualesForm = form.getItems().stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.getItemOrdenCompraId(),
                        item -> item.getPorcentajeActual() == null ? BigDecimal.ZERO : item.getPorcentajeActual(),
                        (primero, segundo) -> segundo
                ));
        Map<Long, BigDecimal> anteriores = certificacionId == null
                ? calculoService.porcentajesAcumuladosPorItem(ordenCompraId)
                : calculoService.porcentajesAnterioresPorItem(ordenCompraId, certificacionId);
        Map<Long, BigDecimal> posteriores = certificacionId == null
                ? Map.of()
                : calculoService.porcentajesPosterioresPorItem(ordenCompraId, certificacionId);
        List<ItemNuevaCertificacionVista> itemsCertificables = orden.getItems().stream()
                .filter(item -> item.getCategoria() == CategoriaItem.MANO_OBRA)
                .map(item -> {
                    BigDecimal actual = porcentajesActualesForm.getOrDefault(item.getId(), BigDecimal.ZERO);
                    BigDecimal anterior = anteriores.getOrDefault(item.getId(), BigDecimal.ZERO);
                    BigDecimal posterior = posteriores.getOrDefault(item.getId(), BigDecimal.ZERO);
                    BigDecimal maximoActual = BigDecimal.valueOf(100).subtract(anterior).subtract(posterior).max(BigDecimal.ZERO);
                    return new ItemNuevaCertificacionVista(item, anterior, maximoActual, actual);
                })
                .toList();
        model.addAttribute("orden", orden);
        model.addAttribute("itemsCertificables", itemsCertificables);
        model.addAttribute("form", form);
        model.addAttribute("modoEdicion", certificacionId != null);
        model.addAttribute("certificacionId", certificacionId);
    }

    @GetMapping("/{certificacionId}")
    public String detalle(@PathVariable Long ordenCompraId, @PathVariable Long certificacionId, Model model) {
        Certificacion certificacion = certificacionService.obtener(certificacionId);
        OrdenCompra orden = ordenCompraService.obtener(ordenCompraId);
        var itemsCalculados = calculoService.calcularDetalle(certificacion);
        model.addAttribute("orden", orden);
        model.addAttribute("certificacion", certificacion);
        model.addAttribute("certificacionesOrden", certificacionService.listarPorOrdenCompra(ordenCompraId));
        model.addAttribute("itemsCalculados", itemsCalculados);
        model.addAttribute("resumen", calculoService.calcularResumen(ordenCompraId, orden.getItems(), itemsCalculados));
        return "certificacion/detalle";
    }

    @GetMapping("/{certificacionId}/exportar")
    public ResponseEntity<byte[]> exportar(@PathVariable Long ordenCompraId, @PathVariable Long certificacionId) {
        Certificacion certificacion = certificacionService.obtener(certificacionId);
        OrdenCompra orden = ordenCompraService.obtener(ordenCompraId);
        byte[] contenido = exportService.exportarCsv(orden, certificacion);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(exportService.nombreArchivo(orden, certificacion))
                        .build()
                        .toString())
                .contentType(new MediaType("text", "csv"))
                .body(contenido);
    }

    @PostMapping("/{certificacionId}/eliminar")
    public String eliminar(@PathVariable Long ordenCompraId, @PathVariable Long certificacionId) {
        certificacionService.eliminar(ordenCompraId, certificacionId);
        return "redirect:/oc/" + ordenCompraId;
    }
}
