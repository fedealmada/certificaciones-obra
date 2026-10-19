package com.obra.certificaciones.certificacion.dto;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class AjustesCertificadoForm {
    private BigDecimal brutoInformado;
    private boolean fondoHabilitado;
    private BigDecimal fondoPorcentaje = new BigDecimal("5");
    private boolean cacAplicar;
    private String cacMesActual;
    private BigDecimal cacIndiceActual;
    private BigDecimal cacMontoManual;
}
