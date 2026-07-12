package com.obra.certificaciones.obra.config;

import com.obra.certificaciones.asistencia.repository.AsistenciaPersonalRepository;
import com.obra.certificaciones.deposito.repository.DepositoItemRepository;
import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.obra.service.ObraService;
import com.obra.certificaciones.oc.repository.OrdenCompraRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObraInicialMigration {
    @Bean
    ApplicationRunner asegurarObraInicial(ObraService obraService,
                                          OrdenCompraRepository ordenCompraRepository,
                                          DepositoItemRepository depositoItemRepository,
                                          AsistenciaPersonalRepository asistenciaRepository) {
        return args -> {
            Obra obraInicial = obraService.asegurarObraInicial();
            var ordenesSinObra = ordenCompraRepository.findByObraIsNull();
            ordenesSinObra.forEach(orden -> orden.setObra(obraInicial));
            ordenCompraRepository.saveAll(ordenesSinObra);

            var itemsSinObra = depositoItemRepository.findByObraIsNull();
            itemsSinObra.forEach(item -> item.setObra(obraInicial));
            depositoItemRepository.saveAll(itemsSinObra);

            var asistenciasSinObra = asistenciaRepository.findByObraIsNull();
            asistenciasSinObra.forEach(asistencia -> asistencia.setObra(obraInicial));
            asistenciaRepository.saveAll(asistenciasSinObra);
        };
    }
}
