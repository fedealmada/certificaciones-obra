package com.obra.certificaciones.quincenal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.obra.certificaciones.certificacion.dto.LiquidacionCertificado;
import com.obra.certificaciones.certificacion.dto.LiquidacionCertificado.Totales;

public record ResumenQuincenal(List<ProveedorResumen> proveedores, List<Certificado> certificados,
                               List<OpcionProveedor> opcionesProveedores,
                               BigDecimal primera, BigDecimal segunda, BigDecimal totalMes,
                               BigDecimal totalVisible, int cantidadMes, int proveedoresMes,
                               Totales totalesMes, Totales totalesVisibles) {
    public record OpcionProveedor(Long id, String nombre) { }
    public record ProveedorResumen(Long id, String nombre, BigDecimal primera, BigDecimal segunda,
                                   BigDecimal total, int cantidad, Totales totales) { }
    public record Tarea(String codigo, String descripcion, String unidad, BigDecimal porcentaje,
                        BigDecimal importe, String tipo, BigDecimal cantidad) { }
    public record Certificado(Long id, Integer numero, LocalDate fecha, Long ordenId, String ordenNumero,
                              Long proveedorId, String proveedor, int quincena, BigDecimal importe,
                              List<Tarea> tareas, String tipo, LiquidacionCertificado liquidacion) { }
}
