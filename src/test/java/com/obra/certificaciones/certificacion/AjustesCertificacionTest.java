package com.obra.certificaciones.certificacion;

import com.obra.certificaciones.certificacion.dto.*;
import com.obra.certificaciones.certificacion.entity.*;
import com.obra.certificaciones.certificacion.service.*;
import com.obra.certificaciones.oc.entity.*;
import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.proveedor.entity.Proveedor;
import com.obra.certificaciones.quincenal.service.CertificadosQuincenalesService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc(addFilters = false) @Transactional
class AjustesCertificacionTest {
    @Autowired AjustesCertificacionService ajustes;
    @Autowired CertificadosQuincenalesService quincenas;
    @Autowired CertificacionExportService export;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;

    @Test void retieneCincoPorCientoDelBrutoDelCertificado439() {
        var c = certificadoSinPersistir("2026-05-11");
        var l = ajustes.liquidar(c, new BigDecimal("1638000"));
        assertThat(l.retencion()).isEqualByComparingTo("81900");
        assertThat(l.neto()).isEqualByComparingTo("1556100");
    }

    @Test void cacUsaVariacionDelIndiceYNoFormaParteDeLaBaseDelFondo() {
        var c = certificadoSinPersistir("2026-01-16"); activarCac(c.getOrdenCompra());
        c.setCacIndiceActual(new BigDecimal("15708.2")); c.setCacMesActual("2026-01");
        var l = ajustes.liquidar(c, new BigDecimal("100000"));
        assertThat(l.porcentajeCac()).isCloseTo(new BigDecimal("16.846"), within(new BigDecimal("0.001")));
        assertThat(l.cac()).isEqualByComparingTo("16846.06");
        assertThat(l.retencion()).isEqualByComparingTo("5000");
        assertThat(l.neto()).isEqualByComparingTo("111846.06");
        c.setCacIndiceActual(new BigDecimal("13443.5"));
        assertThat(ajustes.liquidar(c, new BigDecimal("100000")).cac()).isZero();
    }

    @Test void cacVenceLuegoDeOchoMesesInclusoSiHayUnImporteManual() {
        var c = certificadoSinPersistir("2026-02-13"); activarCac(c.getOrdenCompra()); c.setCacMontoManual(new BigDecimal("695902.72"));
        var l = ajustes.liquidar(c, new BigDecimal("100000"));
        assertThat(l.estadoCac()).isEqualTo("Plazo vencido"); assertThat(l.cac()).isZero(); assertThat(l.pendiente()).isFalse();
        c.setFecha(LocalDate.of(2026, 2, 2)); // Emision del 2 corresponde a enero, ultimo mes vigente.
        assertThat(ajustes.liquidar(c, new BigDecimal("100000")).cac()).isEqualByComparingTo("695902.72");
    }

    @Test void brutoOriginalReproduceLaPlanillaSinModificarElAvance() {
        var c = certificadoSinPersistir("2025-06-25"); c.setBrutoInformado(new BigDecimal("1574300")); activarCac(c.getOrdenCompra()); c.setCacAplicar(false);
        var l = ajustes.liquidar(c, new BigDecimal("1576271.62"));
        assertThat(l.bruto()).isEqualByComparingTo("1574300"); assertThat(l.retencion()).isEqualByComparingTo("78715"); assertThat(l.neto()).isEqualByComparingTo("1495585");
    }

    @Test void faltanteDeIndiceEsPendienteYSePuedeDesactivarPorCertificado() {
        var c = certificadoSinPersistir("2025-08-29"); activarCac(c.getOrdenCompra());
        assertThat(ajustes.liquidar(c, new BigDecimal("100000")).pendiente()).isTrue();
        c.setCacAplicar(false); assertThat(ajustes.liquidar(c, new BigDecimal("100000")).pendiente()).isFalse();
        c.setCacAplicar(true); c.setCacMontoManual(new BigDecimal("62530.86"));
        assertThat(ajustes.liquidar(c, new BigDecimal("3133185.51")).neto()).isEqualByComparingTo("3039057.09");
    }

    @Test void condicionesNoReescribenElHistorialSalvoQueSePidaYLosAcumuladosSeRecalculan() {
        var c = persistir("2025-08-14", "1638000"); var oc = c.getOrdenCompra(); oc.setFondoReparoPorcentaje(BigDecimal.ZERO);
        var f = new CondicionesOcForm(); f.setFondoHabilitado(true); f.setFondoPorcentaje(new BigDecimal("5"));
        ajustes.guardarCondiciones(oc.getId(), f); em.flush();
        assertThat(ajustes.liquidar(c).retencion()).isZero();
        f.setAplicarFondoExistentes(true); ajustes.guardarCondiciones(oc.getId(), f);
        assertThat(ajustes.acumulado(oc.getId()).totales().retencion()).isEqualByComparingTo("81900");
        var propio = new AjustesCertificadoForm(); propio.setFondoHabilitado(false);
        ajustes.guardarAjustes(oc.getId(), c.getId(), propio);
        assertThat(ajustes.acumulado(oc.getId()).totales().retencion()).isZero();
    }

    @Test void pantallasQuincenasYExportacionesMuestranLaMismaLiquidacion() throws Exception {
        var c = persistir("2025-08-14", "1638000"); var oc = c.getOrdenCompra(); activarCac(oc);
        c.setPorCantidad(null); // Certificados antiguos sin modalidad definida.
        c.setBrutoInformado(new BigDecimal("1574300")); c.setCacMontoManual(new BigDecimal("62530.86")); em.flush();
        var session = new MockHttpSession(); session.setAttribute("obraActivaId", oc.getObra().getId());
        mvc.perform(get("/oc/{id}/ajustes", oc.getId()).session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-01")));
        mvc.perform(get("/oc/{id}", oc.getId()).session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Acumulados de fondo de reparo y CAC")));
        mvc.perform(get("/oc/{oc}/certificaciones/{id}/ajustes", oc.getId(), c.getId()).session(session)).andExpect(status().isOk());
        mvc.perform(get("/oc/{oc}/certificaciones/{id}", oc.getId(), c.getId()).session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1.558.115,86")));
        var r = quincenas.resumir(oc.getObra().getId(), YearMonth.of(2025, 8), null, 1);
        assertThat(r.totalesMes().retencion()).isEqualByComparingTo("78715");
        assertThat(r.totalMes()).isEqualByComparingTo("1558115.86");
        var csv = mvc.perform(get("/certificados-quincenales/exportar").session(session).param("mes", "2025-08"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("-78715,00", "62530,86", "1558115,86", "BRUTO ORIGINAL").doesNotContain("'-78715");
        assertThat(new String(export.exportarCsv(oc, c), java.nio.charset.StandardCharsets.UTF_8)).contains("Neto a pagar;1558115.86");
        mvc.perform(post("/oc/{oc}/certificaciones/{id}/ajustes", oc.getId(), c.getId()).session(session)
                .param("fondoHabilitado", "true").param("fondoPorcentaje", "5").param("cacAplicar", "false"))
                .andExpect(status().is3xxRedirection());
        assertThat(ajustes.liquidar(c).cac()).isZero();
    }

    private Certificacion certificadoSinPersistir(String fecha) {
        var oc = new OrdenCompra(); oc.setFondoReparoPorcentaje(new BigDecimal("5"));
        var c = new Certificacion(); c.setOrdenCompra(oc); c.setFecha(LocalDate.parse(fecha)); c.setNumero(1); return c;
    }
    private void activarCac(OrdenCompra oc) { oc.setCacHabilitado(true); oc.setCacMesBase("2025-06"); oc.setCacIndiceBase(new BigDecimal("13443.5")); oc.setCacDuracionMeses(8); }
    private Certificacion persistir(String fecha, String bruto) {
        var c = certificadoSinPersistir(fecha); var oc = c.getOrdenCompra(); oc.setNumero("301"); oc.setFecha(LocalDate.of(2025, 6, 17)); oc.setModoSeguimiento(ModoSeguimientoOrden.CERTIFICACION);
        var o = new Obra(); o.setNombre("Prueba financiera"); o.setActiva(true); em.persist(o); oc.setObra(o);
        var p = new Proveedor(); p.setNombre("Gloria Coronel"); em.persist(p); oc.setProveedorEntidad(p); em.persist(oc);
        var item = new ItemOrdenCompra(); item.setOrdenCompra(oc); item.setItem("301.1"); item.setDetalle("Revoque"); item.setCantidad(BigDecimal.ONE); item.setPrecioUnitario(new BigDecimal(bruto)); item.setImporte(new BigDecimal(bruto)); em.persist(item);
        oc.getItems().add(item); var ic = new ItemCertificacion(); ic.setItemOrdenCompra(item); ic.setPorcentajeActual(new BigDecimal("100")); c.agregarItem(ic); em.persist(c); return c;
    }
}
