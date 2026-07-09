package com.obra.certificaciones.configuracion.repository;

import com.obra.certificaciones.configuracion.entity.ConfiguracionSistema;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionSistemaRepository extends JpaRepository<ConfiguracionSistema, String> {
}
