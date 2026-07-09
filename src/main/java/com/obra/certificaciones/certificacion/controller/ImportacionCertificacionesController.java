package com.obra.certificaciones.certificacion.controller;

import com.obra.certificaciones.certificacion.dto.ImportacionCertificacionesForm;
import com.obra.certificaciones.certificacion.service.ImportacionCertificacionesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/certificaciones/importar")
@RequiredArgsConstructor
public class ImportacionCertificacionesController {
    private final ImportacionCertificacionesService importacionService;

    @GetMapping
    public String formulario(Model model) {
        model.addAttribute("form", new ImportacionCertificacionesForm());
        return "certificacion/importar";
    }

    @PostMapping("/previsualizar")
    public String previsualizar(@ModelAttribute("form") ImportacionCertificacionesForm form, Model model) {
        model.addAttribute("resultado", importacionService.previsualizar(form.getContenido()));
        model.addAttribute("form", form);
        return "certificacion/importar";
    }

    @PostMapping
    public String importar(@ModelAttribute("form") ImportacionCertificacionesForm form, Model model) {
        model.addAttribute("resultado", importacionService.importar(form.getContenido()));
        model.addAttribute("form", form);
        return "certificacion/importar";
    }
}
