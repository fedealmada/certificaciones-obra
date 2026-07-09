package com.obra.certificaciones.configuracion.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final ModuloActivoInterceptor moduloActivoInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(moduloActivoInterceptor)
                .excludePathPatterns("/", "/configuracion/**", "/css/**", "/js/**", "/images/**", "/webjars/**");
    }
}
