package com.obra.certificaciones.inicio.controller;

import com.obra.certificaciones.configuracion.service.ConfiguracionSistemaService;
import com.obra.certificaciones.reporte.service.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class InicioController {
    private final ConfiguracionSistemaService configuracionService;
    private final ReporteService reporteService;

    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("modulos", configuracionService.modulosActivos());
        model.addAttribute("reporte", reporteService.generarGeneral());
        return "inicio/index";
    }
}
