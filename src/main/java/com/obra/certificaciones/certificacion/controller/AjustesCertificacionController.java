package com.obra.certificaciones.certificacion.controller;

import com.obra.certificaciones.certificacion.dto.*;
import com.obra.certificaciones.certificacion.entity.Certificacion;
import com.obra.certificaciones.certificacion.repository.CertificacionRepository;
import com.obra.certificaciones.certificacion.service.AjustesCertificacionService;
import com.obra.certificaciones.obra.service.ObraService;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.repository.OrdenCompraRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/oc/{ordenId}")
@RequiredArgsConstructor
public class AjustesCertificacionController {
    private final AjustesCertificacionService ajustes;
    private final OrdenCompraRepository ordenes;
    private final CertificacionRepository certificados;
    private final ObraService obras;

    @GetMapping("/ajustes")
    public String condiciones(@PathVariable Long ordenId, HttpSession session, Model model) {
        var oc = orden(ordenId, session);
        model.addAttribute("form", ajustes.formularioOc(oc));
        return modeloOc(oc, model);
    }

    @PostMapping("/ajustes")
    public String guardarCondiciones(@PathVariable Long ordenId, @ModelAttribute("form") CondicionesOcForm form,
                                    HttpSession session, Model model, RedirectAttributes flash) {
        var oc = orden(ordenId, session);
        try {
            ajustes.guardarCondiciones(ordenId, form);
            flash.addFlashAttribute("success", "Condiciones guardadas. Los acumulados se actualizaron.");
            return "redirect:/oc/" + ordenId + "/ajustes";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage()); return modeloOc(oc, model);
        }
    }

    @GetMapping("/certificaciones/{certificadoId}/ajustes")
    public String certificado(@PathVariable Long ordenId, @PathVariable Long certificadoId, HttpSession session, Model model) {
        var oc = orden(ordenId, session); var c = certificado(ordenId, certificadoId);
        model.addAttribute("form", ajustes.formularioCertificado(c));
        return modeloCertificado(oc, c, model);
    }

    @PostMapping("/certificaciones/{certificadoId}/ajustes")
    public String guardarCertificado(@PathVariable Long ordenId, @PathVariable Long certificadoId,
                                     @ModelAttribute("form") AjustesCertificadoForm form,
                                     HttpSession session, Model model, RedirectAttributes flash) {
        var oc = orden(ordenId, session); var c = certificado(ordenId, certificadoId);
        try {
            ajustes.guardarAjustes(ordenId, certificadoId, form);
            flash.addFlashAttribute("success", "Fondo de reparo y CAC guardados para este certificado.");
            return "redirect:/oc/" + ordenId + "/certificaciones/" + certificadoId;
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage()); return modeloCertificado(oc, c, model);
        }
    }

    private String modeloOc(OrdenCompra oc, Model model) {
        model.addAttribute("orden", oc); model.addAttribute("acumulado", ajustes.acumulado(oc.getId()));
        if (oc.getCacMesBase() != null && oc.getCacDuracionMeses() != null)
            model.addAttribute("cacHasta", AjustesCertificacionService.mes(oc.getCacMesBase()).plusMonths(oc.getCacDuracionMeses() - 1L).toString());
        return "certificacion/condiciones-oc";
    }

    private String modeloCertificado(OrdenCompra oc, Certificacion c, Model model) {
        model.addAttribute("orden", oc); model.addAttribute("certificacion", c); model.addAttribute("liquidacion", ajustes.liquidar(c));
        model.addAttribute("cacBaseMes", c.getCacHabilitado() == null ? oc.getCacMesBase() : c.getCacMesBase());
        model.addAttribute("cacBaseIndice", c.getCacHabilitado() == null ? oc.getCacIndiceBase() : c.getCacIndiceBase());
        return "certificacion/ajustes";
    }

    private OrdenCompra orden(Long id, HttpSession session) {
        var oc = ordenes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // Acceder solo al identificador de la asociacion no requiere inicializar el proxy.
        if (oc.getObra() == null || !oc.getObra().getId().equals(obras.obraActiva(session).getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return oc;
    }

    private Certificacion certificado(Long ordenId, Long id) {
        var c = certificados.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!c.getOrdenCompra().getId().equals(ordenId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return c;
    }
}
