package com.obra.certificaciones.certificacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LiquidacionCertificado(BigDecimal bruto, BigDecimal porcentajeFondo, BigDecimal retencion,
                                     BigDecimal porcentajeCac, BigDecimal cac, BigDecimal neto,
                                     String estadoCac, boolean pendiente) {
    public record Totales(BigDecimal bruto, BigDecimal retencion, BigDecimal cac, BigDecimal neto, int pendientes) {
        public static Totales sumar(List<LiquidacionCertificado> liquidaciones) {
            return new Totales(liquidaciones.stream().map(LiquidacionCertificado::bruto).reduce(BigDecimal.ZERO, BigDecimal::add),
                    liquidaciones.stream().map(LiquidacionCertificado::retencion).reduce(BigDecimal.ZERO, BigDecimal::add),
                    liquidaciones.stream().map(LiquidacionCertificado::cac).reduce(BigDecimal.ZERO, BigDecimal::add),
                    liquidaciones.stream().map(LiquidacionCertificado::neto).reduce(BigDecimal.ZERO, BigDecimal::add),
                    (int) liquidaciones.stream().filter(LiquidacionCertificado::pendiente).count());
        }
    }
    public record Fila(Long id, Integer numero, LocalDate fecha, LiquidacionCertificado liquidacion) { }
    public record Acumulado(List<Fila> certificados, Totales totales) { }
}
