package com.obra.certificaciones.configuracion.dto;

import java.util.Map;

public record ConfiguracionVista(Map<String, Boolean> valores) {
    public boolean activo(String clave) {
        return valores.getOrDefault(clave, true);
    }
}
