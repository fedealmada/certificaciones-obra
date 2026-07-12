package com.obra.certificaciones.configuracion.controller;

import com.obra.certificaciones.configuracion.dto.ConfiguracionVista;
import com.obra.certificaciones.configuracion.service.ConfiguracionSistemaService;
import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.obra.service.ObraService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class ConfiguracionModelAdvice {
    private final ConfiguracionSistemaService configuracionService;
    private final ObraService obraService;

    @ModelAttribute("configuracion")
    public ConfiguracionVista configuracion() {
        return new ConfiguracionVista(configuracionService.valores());
    }

    @ModelAttribute("obraActiva")
    public Obra obraActiva(HttpSession session) {
        return obraService.obraActiva(session);
    }
}
