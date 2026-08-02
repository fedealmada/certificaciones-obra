package com.obra.certificaciones.sincronizacion.controller;

import com.obra.certificaciones.sincronizacion.dto.SincronizacionResultado;
import com.obra.certificaciones.sincronizacion.service.SincronizacionGithubService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class SincronizacionGithubController {
    private final SincronizacionGithubService sincronizacionService;

    @GetMapping("/sincronizacion")
    public String index(Model model) {
        model.addAttribute("estadoGit", sincronizacionService.estadoGit());
        return "sincronizacion/index";
    }

    @PostMapping("/sincronizacion/ejecutar")
    public String ejecutar(@RequestParam(defaultValue = "SUBIR") String accion,
                           @RequestParam(defaultValue = "Backup y sincronizacion") String mensaje,
                           RedirectAttributes redirectAttributes) {
        SincronizacionResultado resultado = sincronizacionService.ejecutar(accion, mensaje);
        redirectAttributes.addFlashAttribute("resultado", resultado);
        return "redirect:/sincronizacion";
    }
}
