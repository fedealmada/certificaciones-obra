package com.obra.certificaciones.documentacion.controller;

import com.obra.certificaciones.deposito.service.DepositoService;
import com.obra.certificaciones.documentacion.dto.DocumentoObraForm;
import com.obra.certificaciones.documentacion.entity.SujetoDocumental;
import com.obra.certificaciones.documentacion.entity.TipoDocumentoObra;
import com.obra.certificaciones.documentacion.entity.TipoVinculoDocumental;
import com.obra.certificaciones.documentacion.service.DocumentacionPdfService;
import com.obra.certificaciones.documentacion.service.DocumentacionService;
import com.obra.certificaciones.obra.service.ObraService;
import com.obra.certificaciones.proveedor.service.ProveedorService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.MalformedURLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/documentacion")
@RequiredArgsConstructor
public class DocumentacionController {
    private final DocumentacionService documentacionService;
    private final ProveedorService proveedorService;
    private final DepositoService depositoService;
    private final ObraService obraService;
    private final DocumentacionPdfService documentacionPdfService;

    @GetMapping
    public String index(Model model, HttpSession session) {
        var obra = obraService.obraActiva(session);
        var grupos = documentacionService.agruparPorContratista(obra);
        model.addAttribute("gruposDocumentacion", grupos);
        model.addAttribute("totalDocumentos", grupos.stream().mapToLong(grupo -> grupo.total()).sum());
        model.addAttribute("resumen", documentacionService.resumenDesdeGrupos(grupos));
        model.addAttribute("proveedores", proveedorService.listarActivos());
        return "documentacion/index";
    }

    @GetMapping("/nuevo")
    public String nuevo(@RequestParam(required = false) Long proveedorId,
                        @RequestParam(required = false) Long carpetaId,
                        @RequestParam(required = false) SujetoDocumental sujeto,
                        Model model,
                        HttpSession session) {
        DocumentoObraForm form = new DocumentoObraForm();
        form.setProveedorId(proveedorId);
        form.setCarpetaId(carpetaId);
        form.setFechaUltimaVerificacionFisica(LocalDate.now());
        if (sujeto != null) {
            form.setSujeto(sujeto);
        }
        if (carpetaId != null) {
            var carpeta = documentacionService.obtenerCarpeta(carpetaId, obraService.obraActiva(session));
            if (carpeta.getProveedor() != null) {
                form.setProveedorId(carpeta.getProveedor().getId());
                form.setSujeto(SujetoDocumental.CONTRATISTA);
            } else if (carpeta.getProveedor() == null) {
                form.setSujeto(SujetoDocumental.OBRA);
            }
            model.addAttribute("carpetaSeleccionada", carpeta);
        }
        cargarFormulario(model, form, false);
        return "documentacion/form";
    }

    @GetMapping("/resumen-pdf")
    public ResponseEntity<byte[]> resumenPdf(HttpSession session) {
        byte[] pdf = documentacionPdfService.generarResumenEstado(obraService.obraActiva(session));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + documentacionPdfService.nombreArchivoResumen() + "\"")
                .body(pdf);
    }

    @GetMapping("/carpetas/{id}")
    public String carpeta(@PathVariable Long id, Model model, HttpSession session) {
        var grupo = documentacionService.obtenerGrupoCarpeta(id, obraService.obraActiva(session));
        model.addAttribute("grupo", grupo);
        model.addAttribute("proveedor", grupo.proveedorId() == null ? null : proveedorService.obtener(grupo.proveedorId()));
        model.addAttribute("proveedores", proveedorService.listarActivos());
        model.addAttribute("vinculos", TipoVinculoDocumental.values());
        model.addAttribute("tipos", TipoDocumentoObra.values());
        model.addAttribute("hoy", LocalDate.now());
        model.addAttribute("inicioLegajo", grupo.documentos().stream()
                .map(documento -> documento.getFechaCreacion())
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null));
        model.addAttribute("proximosVencimientos", grupo.documentos().stream()
                .filter(documento -> documento.getFechaVencimiento() != null)
                .sorted(Comparator.comparing(documento -> documento.getFechaVencimiento()))
                .limit(4)
                .toList());
        return "documentacion/carpeta";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        model.addAttribute("documento", documentacionService.obtener(id));
        return "documentacion/detalle";
    }

    @GetMapping("/{id}/duplicar")
    public String duplicar(@PathVariable Long id, Model model) {
        DocumentoObraForm form = documentacionService.formDesde(documentacionService.obtener(id));
        form.setId(null);
        cargarFormulario(model, form, false);
        return "documentacion/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        cargarFormulario(model, documentacionService.formDesde(documentacionService.obtener(id)), true);
        return "documentacion/form";
    }

    @PostMapping
    public String guardar(@ModelAttribute("form") DocumentoObraForm form,
                          Model model,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        try {
            documentacionService.guardar(form, obraService.obraActiva(session));
            redirectAttributes.addFlashAttribute("success", "Documento guardado correctamente.");
            if (form.getCarpetaId() != null) {
                return "redirect:/documentacion/carpetas/" + form.getCarpetaId();
            }
            return "redirect:/documentacion";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            cargarFormulario(model, form, form.getId() != null);
            return "documentacion/form";
        }
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id,
                           @RequestParam(required = false) String returnTo,
                           RedirectAttributes redirectAttributes) {
        documentacionService.eliminar(id);
        redirectAttributes.addFlashAttribute("success", "Documento marcado como inactivo.");
        if (returnTo != null && returnTo.startsWith("/documentacion")) {
            return "redirect:" + returnTo;
        }
        return "redirect:/documentacion";
    }

    @PostMapping("/{id}/archivo")
    public String adjuntarArchivo(@PathVariable Long id,
                                  @RequestParam("archivo") MultipartFile archivo,
                                  RedirectAttributes redirectAttributes) {
        try {
            documentacionService.adjuntarPdf(id, archivo);
            redirectAttributes.addFlashAttribute("success", "PDF adjuntado correctamente.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/documentacion/" + id;
    }

    @GetMapping("/{id}/archivo")
    public ResponseEntity<Resource> descargarArchivo(@PathVariable Long id) throws MalformedURLException {
        Resource archivo = documentacionService.archivo(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + archivo.getFilename() + "\"")
                .body(archivo);
    }

    @PostMapping("/contratistas/{id}/renombrar")
    public String renombrarContratista(@PathVariable Long id,
                                       @RequestParam String nombre,
                                       RedirectAttributes redirectAttributes) {
        try {
            proveedorService.renombrar(id, nombre);
            redirectAttributes.addFlashAttribute("success", "Contratista actualizado correctamente.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/documentacion";
    }

    @PostMapping("/contratistas")
    public String crearContratista(@RequestParam String nombre,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        try {
            var carpeta = documentacionService.crearCarpetaContratista(nombre, obraService.obraActiva(session));
            redirectAttributes.addFlashAttribute("success", "Carpeta documental creada correctamente.");
            return "redirect:/documentacion/nuevo?proveedorId=" + carpeta.getProveedor().getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/documentacion";
        }
    }

    @PostMapping("/carpetas/{id}/personalizar")
    public String personalizarCarpeta(@PathVariable Long id,
                                      @RequestParam(required = false) String nombre,
                                      @RequestParam(required = false) String apodo,
                                      @RequestParam(required = false) String color,
                                      @RequestParam(required = false) Long proveedorId,
                                      @RequestParam(required = false) String returnTo,
                                      RedirectAttributes redirectAttributes) {
        try {
            documentacionService.personalizarCarpeta(id, nombre, apodo, color, proveedorId);
            redirectAttributes.addFlashAttribute("success", "Carpeta personalizada correctamente.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        if (returnTo != null && returnTo.startsWith("/documentacion")) {
            return "redirect:" + returnTo;
        }
        return "redirect:/documentacion";
    }

    @PostMapping("/carpetas")
    public String crearCarpeta(@RequestParam String nombre,
                               @RequestParam(required = false) String apodo,
                               @RequestParam(required = false) String color,
                               @RequestParam(required = false) Long proveedorId,
                               @RequestParam(required = false) Long carpetaPadreId,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        try {
            documentacionService.crearCarpetaLibre(nombre, proveedorId, apodo, color, carpetaPadreId, obraService.obraActiva(session));
            redirectAttributes.addFlashAttribute("success", "Carpeta documental creada correctamente.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/documentacion";
    }

    @PostMapping("/carpetas/{id}/eliminar")
    public String eliminarCarpeta(@PathVariable Long id,
                                  RedirectAttributes redirectAttributes) {
        try {
            documentacionService.eliminarCarpeta(id);
            redirectAttributes.addFlashAttribute("success", "Carpeta eliminada. Los documentos y contratistas no fueron borrados.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/documentacion";
    }

    @PostMapping("/carpetas/{id}/mover")
    public String moverCarpeta(@PathVariable Long id,
                               @RequestParam int direccion,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        try {
            documentacionService.moverCarpeta(id, direccion, obraService.obraActiva(session));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/documentacion";
    }

    @PostMapping("/carpetas/mover-a")
    public String moverCarpetas(@RequestParam("carpetaIds") List<Long> carpetaIds,
                                @RequestParam(required = false) Long carpetaPadreId,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        try {
            documentacionService.moverCarpetas(carpetaIds, carpetaPadreId, obraService.obraActiva(session));
            redirectAttributes.addFlashAttribute("success", "Carpetas movidas correctamente.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/documentacion";
    }

    @PostMapping("/carpetas/reordenar")
    public String reordenarCarpetas(@RequestParam("carpetaIds") List<Long> carpetaIds,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        try {
            documentacionService.reordenarCarpetas(carpetaIds, obraService.obraActiva(session));
            redirectAttributes.addFlashAttribute("success", "Orden de carpetas actualizado.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/documentacion";
    }

    private void cargarFormulario(Model model, DocumentoObraForm form, boolean modoEdicion) {
        model.addAttribute("form", form);
        model.addAttribute("modoEdicion", modoEdicion);
        model.addAttribute("proveedores", proveedorService.listarActivos());
        model.addAttribute("personas", depositoService.listarTrabajadoresActivos());
        model.addAttribute("sujetos", SujetoDocumental.values());
        model.addAttribute("vinculos", TipoVinculoDocumental.values());
        model.addAttribute("tipos", TipoDocumentoObra.values());
    }
}

