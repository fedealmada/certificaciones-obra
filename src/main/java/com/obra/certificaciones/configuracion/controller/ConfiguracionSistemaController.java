package com.obra.certificaciones.configuracion.controller;

import com.obra.certificaciones.configuracion.service.ConfiguracionSistemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ConfiguracionSistemaController {
    private final ConfiguracionSistemaService configuracionService;

    @GetMapping("/configuracion")
    public String editar(Model model) {
        model.addAttribute("modulos", configuracionService.modulos());
        model.addAttribute("valores", configuracionService.valores());
        return "configuracion/index";
    }

    @PostMapping("/configuracion")
    public String guardar(@RequestParam Map<String, String> parametros, RedirectAttributes redirectAttributes) {
        Map<String, Boolean> valores = new LinkedHashMap<>();
        configuracionService.modulos().forEach(modulo -> valores.put(modulo.clave(), parametros.containsKey(modulo.clave())));
        valores.put(ConfiguracionSistemaService.ALERTAS_DASHBOARD, parametros.containsKey(ConfiguracionSistemaService.ALERTAS_DASHBOARD));
        valores.put(ConfiguracionSistemaService.ALERTAS_OC, parametros.containsKey(ConfiguracionSistemaService.ALERTAS_OC));
        configuracionService.guardar(valores);
        redirectAttributes.addFlashAttribute("mensaje", "Configuracion guardada correctamente.");
        return "redirect:/configuracion";
    }
}
