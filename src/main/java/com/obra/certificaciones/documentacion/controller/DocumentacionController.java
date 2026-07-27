package com.obra.certificaciones.documentacion.controller;

import com.obra.certificaciones.deposito.service.DepositoService;
import com.obra.certificaciones.documentacion.dto.DocumentoObraForm;
import com.obra.certificaciones.documentacion.entity.SujetoDocumental;
import com.obra.certificaciones.documentacion.entity.TipoDocumentoObra;
import com.obra.certificaciones.documentacion.entity.TipoVinculoDocumental;
import com.obra.certificaciones.documentacion.service.DocumentacionService;
import com.obra.certificaciones.obra.service.ObraService;
import com.obra.certificaciones.proveedor.service.ProveedorService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/documentacion")
@RequiredArgsConstructor
public class DocumentacionController {
    private final DocumentacionService documentacionService;
    private final ProveedorService proveedorService;
    private final DepositoService depositoService;
    private final ObraService obraService;

    @GetMapping
    public String index(@RequestParam(defaultValue = "0") int page, Model model, HttpSession session) {
        var obra = obraService.obraActiva(session);
        var documentosPage = documentacionService.listar(obra, PageRequest.of(Math.max(page, 0), 25));
        model.addAttribute("documentos", documentosPage.getContent());
        model.addAttribute("documentosPage", documentosPage);
        model.addAttribute("resumen", documentacionService.resumen(obra));
        return "documentacion/index";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        cargarFormulario(model, new DocumentoObraForm(), false);
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
            return "redirect:/documentacion";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            cargarFormulario(model, form, form.getId() != null);
            return "documentacion/form";
        }
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        documentacionService.eliminar(id);
        redirectAttributes.addFlashAttribute("success", "Documento marcado como inactivo.");
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