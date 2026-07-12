package com.obra.certificaciones.obra.config;

import com.obra.certificaciones.obra.service.ObraService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObraInicialMigration {
    @Bean
    ApplicationRunner asegurarObraInicial(ObraService obraService) {
        return args -> obraService.asegurarObraInicial();
    }
}
