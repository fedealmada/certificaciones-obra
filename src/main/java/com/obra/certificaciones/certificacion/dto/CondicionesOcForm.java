package com.obra.certificaciones.certificacion.dto;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class CondicionesOcForm {
    private boolean fondoHabilitado;
    private BigDecimal fondoPorcentaje = new BigDecimal("5");
    private boolean cacHabilitado;
    private String cacMesBase;
    private BigDecimal cacIndiceBase;
    private Integer cacDuracionMeses;
    private boolean aplicarFondoExistentes;
    private boolean aplicarCacExistentes;
}
