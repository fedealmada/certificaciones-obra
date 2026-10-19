package com.obra.certificaciones.tablero.dto;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
@Getter @Setter
public class AvancesTableroForm {
    private List<Fila> filas = new ArrayList<>();
    @Getter @Setter
    public static class Fila { private Long id; private BigDecimal porcentaje; }
}
