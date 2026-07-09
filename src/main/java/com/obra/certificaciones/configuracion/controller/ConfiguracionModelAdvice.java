package com.obra.certificaciones.configuracion.controller;

import com.obra.certificaciones.configuracion.dto.ConfiguracionVista;
import com.obra.certificaciones.configuracion.service.ConfiguracionSistemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class ConfiguracionModelAdvice {
    private final ConfiguracionSistemaService configuracionService;

    @ModelAttribute("configuracion")
    public ConfiguracionVista configuracion() {
        return new ConfiguracionVista(configuracionService.valores());
    }
}
