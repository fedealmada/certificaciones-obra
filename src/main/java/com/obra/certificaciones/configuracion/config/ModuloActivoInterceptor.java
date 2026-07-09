package com.obra.certificaciones.configuracion.config;

import com.obra.certificaciones.configuracion.dto.ModuloSistema;
import com.obra.certificaciones.configuracion.service.ConfiguracionSistemaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class ModuloActivoInterceptor implements HandlerInterceptor {
    private final ConfiguracionSistemaService configuracionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        var valores = configuracionService.valores();
        for (ModuloSistema modulo : configuracionService.modulos()) {
            if (!valores.getOrDefault(modulo.clave(), true) && coincide(path, modulo.url())) {
                response.sendRedirect(request.getContextPath() + "/");
                return false;
            }
        }
        return true;
    }

    private boolean coincide(String path, String urlModulo) {
        return path.equals(urlModulo) || path.startsWith(urlModulo + "/");
    }
}
