package com.obra.certificaciones.controlrubro.controller;

import com.obra.certificaciones.controlrubro.service.ControlRubroService;
import com.obra.certificaciones.obra.service.ObraService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/control-rubros")
@RequiredArgsConstructor
public class ControlRubroController {
    private final ControlRubroService controlRubroService;
    private final ObraService obraService;

    @GetMapping
    public String index(Model model, HttpSession session) {
        var obra = obraService.obraActiva(session);
        model.addAttribute("control", controlRubroService.generar(obra));
        return "control-rubro/index";
    }

    @PostMapping("/techo")
    public String guardarTecho(@RequestParam Long rubroId,
                               @RequestParam(required = false) BigDecimal techoDireccion,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        controlRubroService.guardarTecho(obraService.obraActiva(session), rubroId, techoDireccion);
        redirectAttributes.addFlashAttribute("mensaje", "Techo de Direccion actualizado.");
        return "redirect:/control-rubros";
    }
}
