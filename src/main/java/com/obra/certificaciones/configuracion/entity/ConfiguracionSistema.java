package com.obra.certificaciones.configuracion.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class ConfiguracionSistema {
    @Id
    private String clave;
    private boolean activo = true;
}
