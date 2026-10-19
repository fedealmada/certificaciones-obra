package com.obra.certificaciones.certificacion.service;

import com.obra.certificaciones.certificacion.dto.*;
import com.obra.certificaciones.certificacion.dto.LiquidacionCertificado.*;
import com.obra.certificaciones.certificacion.entity.Certificacion;
import com.obra.certificaciones.certificacion.repository.CertificacionRepository;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.repository.OrdenCompraRepository;
import com.obra.certificaciones.quincenal.service.CertificadosQuincenalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AjustesCertificacionService {
    private final CertificacionRepository certificados;
    private final OrdenCompraRepository ordenes;
    private final CertificacionCalculoService calculo;

    public LiquidacionCertificado liquidar(Certificacion c, BigDecimal bruto) {
        if (c.getBrutoInformado() != null) bruto = c.getBrutoInformado();
        var oc = c.getOrdenCompra();
        BigDecimal porcentaje = c.getFondoReparoPorcentaje() != null ? c.getFondoReparoPorcentaje() : valor(oc.getFondoReparoPorcentaje());
        BigDecimal retencion = calculo.calcularMonto(bruto, porcentaje);
        BigDecimal coeficiente = BigDecimal.ZERO;
        BigDecimal cac = BigDecimal.ZERO;
        String estado = "Sin CAC";
        boolean pendiente = false;
        boolean habilitado = c.getCacHabilitado() != null ? c.getCacHabilitado() : Boolean.TRUE.equals(oc.getCacHabilitado());
        if (habilitado && !Boolean.FALSE.equals(c.getCacAplicar())) {
            String baseTexto = c.getCacHabilitado() != null ? c.getCacMesBase() : oc.getCacMesBase();
            BigDecimal base = c.getCacHabilitado() != null ? c.getCacIndiceBase() : oc.getCacIndiceBase();
            Integer meses = c.getCacHabilitado() != null ? c.getCacDuracionMeses() : oc.getCacDuracionMeses();
            YearMonth trabajo = CertificadosQuincenalesService.mesTrabajo(c.getFecha());
            if (baseTexto == null || base == null || base.signum() <= 0 || meses == null || meses < 1) {
                estado = "Configuración CAC pendiente"; pendiente = true;
            } else {
                YearMonth inicio = mes(baseTexto);
                YearMonth hasta = inicio.plusMonths(meses - 1L);
                if (trabajo.isAfter(hasta)) estado = "Plazo vencido";
                else if (trabajo.isBefore(inicio)) estado = "Fuera de vigencia";
                else if (c.getCacMontoManual() != null) {
                    cac = dinero(c.getCacMontoManual()); estado = "Importe histórico informado";
                    coeficiente = bruto.signum() == 0 ? BigDecimal.ZERO : cac.multiply(new BigDecimal("100")).divide(bruto, 8, RoundingMode.HALF_UP);
                } else if (c.getCacIndiceActual() == null || c.getCacMesActual() == null || c.getCacMesActual().isBlank()) {
                    estado = "Pendiente de índice"; pendiente = true;
                } else {
                    YearMonth indiceMes = mes(c.getCacMesActual());
                    if (indiceMes.isAfter(trabajo) || indiceMes.isBefore(inicio)) {
                        estado = "Mes del índice fuera del período"; pendiente = true;
                    } else {
                        // El coeficiente se conserva con precision; el dinero se redondea al final.
                        coeficiente = c.getCacIndiceActual().subtract(base).multiply(new BigDecimal("100")).divide(base, 12, RoundingMode.HALF_UP);
                        cac = calculo.calcularMonto(bruto, coeficiente);
                        estado = "Calculado por índice";
                    }
                }
            }
        } else if (habilitado) estado = "Sin ajuste en este certificado";
        return new LiquidacionCertificado(dinero(bruto), porcentaje, retencion, coeficiente, cac,
                dinero(bruto.subtract(retencion).add(cac)), estado, pendiente);
    }

    public LiquidacionCertificado liquidar(Certificacion c) {
        BigDecimal bruto = c.getItems().stream().map(i -> i.getMontoActual())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return liquidar(c, bruto);
    }

    public Acumulado acumulado(Long ordenId) {
        var filas = certificados.findByOrdenCompraIdOrderByFechaAscIdAsc(ordenId).stream()
                .map(c -> new Fila(c.getId(), c.getNumero(), c.getFecha(), liquidar(c))).toList();
        return new Acumulado(filas, Totales.sumar(filas.stream().map(Fila::liquidacion).toList()));
    }

    public CondicionesOcForm formularioOc(OrdenCompra oc) {
        var f = new CondicionesOcForm();
        f.setFondoHabilitado(valor(oc.getFondoReparoPorcentaje()).signum() > 0);
        if (f.isFondoHabilitado()) f.setFondoPorcentaje(oc.getFondoReparoPorcentaje());
        f.setCacHabilitado(Boolean.TRUE.equals(oc.getCacHabilitado()));
        f.setCacMesBase(oc.getCacMesBase()); f.setCacIndiceBase(oc.getCacIndiceBase()); f.setCacDuracionMeses(oc.getCacDuracionMeses());
        return f;
    }

    public AjustesCertificadoForm formularioCertificado(Certificacion c) {
        var f = new AjustesCertificadoForm(); var l = liquidar(c);
        f.setBrutoInformado(c.getBrutoInformado());
        f.setFondoHabilitado(l.porcentajeFondo().signum() > 0);
        if (f.isFondoHabilitado()) f.setFondoPorcentaje(l.porcentajeFondo());
        f.setCacAplicar(!Boolean.FALSE.equals(c.getCacAplicar()));
        f.setCacMesActual(c.getCacMesActual()); f.setCacIndiceActual(c.getCacIndiceActual()); f.setCacMontoManual(c.getCacMontoManual());
        return f;
    }

    @Transactional
    public void guardarCondiciones(Long ordenId, CondicionesOcForm f) {
        validarPorcentaje(f.isFondoHabilitado(), f.getFondoPorcentaje());
        if (f.isCacHabilitado()) {
            mes(f.getCacMesBase());
            validarIndice(f.getCacIndiceBase());
            if (f.getCacDuracionMeses() == null || f.getCacDuracionMeses() < 1 || f.getCacDuracionMeses() > 1200)
                throw new IllegalArgumentException("La vigencia CAC debe ser de 1 a 1200 meses, incluyendo el mes base.");
        }
        var oc = ordenes.findById(ordenId).orElseThrow();
        var historial = certificados.findByOrdenCompraIdOrderByFechaAscIdAsc(ordenId);
        // Congelar primero las condiciones anteriores: cambiar una OC no reescribe su historial.
        historial.forEach(this::congelarCondiciones);
        oc.setFondoReparoPorcentaje(f.isFondoHabilitado() ? f.getFondoPorcentaje() : BigDecimal.ZERO);
        oc.setCacHabilitado(f.isCacHabilitado()); oc.setCacMesBase(vacio(f.getCacMesBase()));
        oc.setCacIndiceBase(f.getCacIndiceBase()); oc.setCacDuracionMeses(f.getCacDuracionMeses());
        for (var c : historial) {
            if (f.isAplicarFondoExistentes()) c.setFondoReparoPorcentaje(oc.getFondoReparoPorcentaje());
            if (f.isAplicarCacExistentes()) { copiarCac(c, oc); c.setCacAplicar(true); }
        }
    }

    @Transactional
    public void guardarAjustes(Long ordenId, Long certificadoId, AjustesCertificadoForm f) {
        validarPorcentaje(f.isFondoHabilitado(), f.getFondoPorcentaje());
        var c = certificados.findById(certificadoId).orElseThrow();
        if (!c.getOrdenCompra().getId().equals(ordenId)) throw new IllegalArgumentException("El certificado no pertenece a la OC.");
        if (f.getCacIndiceActual() != null) { validarIndice(f.getCacIndiceActual()); mes(f.getCacMesActual()); }
        if (f.getCacMontoManual() != null && f.getCacIndiceActual() != null)
            throw new IllegalArgumentException("Cargá un índice actual o un importe histórico CAC, no ambos.");
        if (f.getCacMontoManual() != null && (f.getCacMontoManual().scale() > 2 || f.getCacMontoManual().precision() - f.getCacMontoManual().scale() > 17))
            throw new IllegalArgumentException("El importe CAC admite hasta dos decimales.");
        if (f.getBrutoInformado() != null && (f.getBrutoInformado().signum() < 0 || f.getBrutoInformado().scale() > 2 || f.getBrutoInformado().precision() - f.getBrutoInformado().scale() > 17))
            throw new IllegalArgumentException("El bruto original debe ser positivo, con hasta dos decimales.");
        congelarCondiciones(c);
        if (f.isCacAplicar() && (f.getCacIndiceActual() != null || f.getCacMontoManual() != null) && !Boolean.TRUE.equals(c.getCacHabilitado()))
            throw new IllegalArgumentException("Primero habilitá la base y vigencia CAC de esta OC y aplicala al certificado existente si corresponde.");
        c.setFondoReparoPorcentaje(f.isFondoHabilitado() ? f.getFondoPorcentaje() : BigDecimal.ZERO);
        c.setCacAplicar(f.isCacAplicar()); c.setCacMesActual(vacio(f.getCacMesActual()));
        c.setCacIndiceActual(f.getCacIndiceActual()); c.setCacMontoManual(f.getCacMontoManual());
        c.setBrutoInformado(f.getBrutoInformado());
        if (liquidar(c).estadoCac().equals("Mes del índice fuera del período"))
            throw new IllegalArgumentException("El mes del índice debe estar entre el mes base y el mes de trabajo del certificado.");
    }

    public void congelarCondiciones(Certificacion c) {
        if (c.getFondoReparoPorcentaje() == null) c.setFondoReparoPorcentaje(valor(c.getOrdenCompra().getFondoReparoPorcentaje()));
        if (c.getCacHabilitado() == null) copiarCac(c, c.getOrdenCompra());
    }

    private void copiarCac(Certificacion c, OrdenCompra oc) {
        c.setCacHabilitado(Boolean.TRUE.equals(oc.getCacHabilitado())); c.setCacMesBase(oc.getCacMesBase());
        c.setCacIndiceBase(oc.getCacIndiceBase()); c.setCacDuracionMeses(oc.getCacDuracionMeses());
    }

    private void validarPorcentaje(boolean habilitado, BigDecimal valor) {
        if (habilitado && (valor == null || valor.signum() < 0 || valor.compareTo(new BigDecimal("100")) > 0 || valor.scale() > 4))
            throw new IllegalArgumentException("El fondo de reparo debe estar entre 0 y 100 %, con hasta cuatro decimales.");
    }
    private void validarIndice(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0 || valor.scale() > 6 || valor.precision() - valor.scale() > 13)
            throw new IllegalArgumentException("El índice CAC debe ser positivo, con hasta seis decimales.");
    }
    public static YearMonth mes(String valor) {
        try { var m = YearMonth.parse(valor); if (m.getYear() < 1900 || m.getYear() > 9998) throw new IllegalArgumentException(); return m; }
        catch (Exception ex) { throw new IllegalArgumentException("Indicá el mes y año con formato AAAA-MM."); }
    }
    private String vacio(String texto) { return texto == null || texto.isBlank() ? null : texto; }
    private BigDecimal valor(BigDecimal valor) { return valor == null ? BigDecimal.ZERO : valor; }
    private BigDecimal dinero(BigDecimal valor) { return valor.setScale(2, RoundingMode.HALF_UP); }
}
