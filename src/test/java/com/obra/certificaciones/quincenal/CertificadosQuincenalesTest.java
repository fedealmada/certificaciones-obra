package com.obra.certificaciones.quincenal;

import com.obra.certificaciones.certificacion.entity.Certificacion;
import com.obra.certificaciones.certificacion.entity.ItemCertificacion;
import com.obra.certificaciones.categoria.entity.CategoriaOrden;
import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.oc.entity.CategoriaItem;
import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.entity.ModoSeguimientoOrden;
import com.obra.certificaciones.proveedor.entity.Proveedor;
import com.obra.certificaciones.quincenal.service.CertificadosQuincenalesService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class CertificadosQuincenalesTest {
    @Autowired EntityManager em;
    @Autowired CertificadosQuincenalesService service;
    @Autowired MockMvc mvc;

    @Test
    void asignaLaQuincenaAnteriorInclusoAlCambiarDeMesOAnio() {
        assertThat(CertificadosQuincenalesService.mesTrabajo(LocalDate.of(2026, 10, 1))).isEqualTo(YearMonth.of(2026, 9));
        assertThat(CertificadosQuincenalesService.quincenaTrabajo(LocalDate.of(2026, 10, 1))).isEqualTo(2);
        assertThat(CertificadosQuincenalesService.mesTrabajo(LocalDate.of(2026, 10, 16))).isEqualTo(YearMonth.of(2026, 10));
        assertThat(CertificadosQuincenalesService.quincenaTrabajo(LocalDate.of(2026, 10, 16))).isEqualTo(1);
        assertThat(CertificadosQuincenalesService.mesTrabajo(LocalDate.of(2027, 1, 10))).isEqualTo(YearMonth.of(2026, 12));
        assertThat(CertificadosQuincenalesService.mesTrabajo(LocalDate.of(2027, 1, 11))).isEqualTo(YearMonth.of(2027, 1));
        assertThat(CertificadosQuincenalesService.mesTrabajo(LocalDate.of(2024, 2, 29))).isEqualTo(YearMonth.of(2024, 2));
    }

    @Test
    void sumaSoloManoDeObraActualAgrupaCertificadosYRespetaObraYFechas() {
        var obra = obra("Prueba quincenal");
        var proveedor = proveedor("Muriel");
        var oc = orden(obra, proveedor);
        var manoObra = item(oc, CategoriaItem.MANO_OBRA, "1000.00");
        var material = item(oc, CategoriaItem.MATERIAL, "9999.00");
        // Categoria manual tiene prioridad sobre el tipo antiguo del item.
        var categoria = new CategoriaOrden(); categoria.setNombre("Material especial"); categoria.setTipo(CategoriaItem.MATERIAL); em.persist(categoria);
        var materialManual = item(oc, CategoriaItem.MANO_OBRA, "9999.00"); materialManual.setCategoriaEntidad(categoria);
        certificado(oc, "2026-10-10", manoObra, "90"); // Segunda de septiembre: queda fuera.
        var primero = certificado(oc, "2026-10-11", manoObra, "10");
        avance(primero, material, "100"); avance(primero, materialManual, "100");
        certificado(oc, "2026-10-26", manoObra, "5");
        certificado(oc, "2026-10-27", manoObra, "20");
        certificado(oc, "2026-11-10", manoObra, "5");
        certificado(oc, "2026-11-11", manoObra, "30"); // Primera de noviembre: queda fuera.
        certificado(oc, "2026-11-02", manoObra, "0"); // Sin avance: no cuenta como movimiento.
        var otraOc = orden(obra("Otra obra"), proveedor);
        certificado(otraOc, "2026-10-16", item(otraOc, CategoriaItem.MANO_OBRA, "9000"), "100");
        em.flush(); em.clear();
        var resumen = service.resumir(obra.getId(), YearMonth.of(2026, 10), null, 0);
        assertThat(resumen.primera()).isEqualByComparingTo("150.00");
        assertThat(resumen.segunda()).isEqualByComparingTo("250.00");
        assertThat(resumen.totalMes()).isEqualByComparingTo("400.00");
        assertThat(resumen.certificados()).hasSize(4);
        assertThat(resumen.certificados().getFirst().tareas()).hasSize(1);
        assertThat(resumen.proveedores()).hasSize(1);
        var segunda = service.resumir(obra.getId(), YearMonth.of(2026, 10), proveedor.getId(), 2);
        assertThat(segunda.certificados()).hasSize(2);
        assertThat(segunda.totalVisible()).isEqualByComparingTo("250");
        assertThat(segunda.totalMes()).isEqualByComparingTo("400");
        assertThat(service.resumir(obra.getId(), YearMonth.of(2026, 10), -1L, 0).certificados()).isEmpty();
        assertThat(service.mesesDisponibles(obra.getId())).contains(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11));
    }

    @Test
    void incluyeServiciosCertificablesFiltraProveedorYExcluyeMaterialesRegistroYEntregas() throws Exception {
        var obra = obra("Servicios quincenales");
        var paz = proveedor("Daniela Marisol Paz");
        var rondinoni = proveedor("Alfredo Rondinoni");
        var oc = orden(obra, paz); oc.setModoSeguimiento(ModoSeguimientoOrden.CERTIFICACION);
        var categoria = new CategoriaOrden(); categoria.setNombre("Servicio"); categoria.setTipo(CategoriaItem.OTRO); em.persist(categoria);
        // Categoria manual de servicio tiene prioridad sobre el tipo antiguo MANO_OBRA.
        var servicio = item(oc, CategoriaItem.MANO_OBRA, "1000"); servicio.setCategoriaEntidad(categoria);
        var cert = certificado(oc, "2026-10-14", servicio, "25");
        avance(cert, item(oc, CategoriaItem.MANO_OBRA, "500"), "20");
        avance(cert, item(oc, CategoriaItem.MATERIAL, "9000"), "100");
        var ocRondinoni = orden(obra, rondinoni); ocRondinoni.setModoSeguimiento(ModoSeguimientoOrden.CERTIFICACION);
        certificado(ocRondinoni, "2026-11-02", item(ocRondinoni, CategoriaItem.OTRO, "2000"), "50");
        for (var modo : new ModoSeguimientoOrden[]{ModoSeguimientoOrden.REGISTRO, ModoSeguimientoOrden.ENTREGA}) {
            var excluida = orden(obra, proveedor("Excluir " + modo)); excluida.setModoSeguimiento(modo);
            certificado(excluida, "2026-10-14", item(excluida, CategoriaItem.OTRO, "9000"), "100");
        }
        // Sin modo explicito: respetar el comportamiento certificable legado si no hay materiales.
        var legado = orden(obra, rondinoni);
        certificado(legado, "2026-01-14", item(legado, CategoriaItem.OTRO, "100"), "100");
        var legadoConMaterial = orden(obra, paz);
        item(legadoConMaterial, CategoriaItem.MATERIAL, "100");
        certificado(legadoConMaterial, "2025-05-14", item(legadoConMaterial, CategoriaItem.OTRO, "9000"), "100");
        em.flush(); em.clear();
        var resumen = service.resumir(obra.getId(), YearMonth.of(2026, 10), null, 0);
        assertThat(resumen.certificados()).hasSize(2);
        assertThat(resumen.primera()).isEqualByComparingTo("350");
        assertThat(resumen.segunda()).isEqualByComparingTo("1000");
        assertThat(resumen.totalMes()).isEqualByComparingTo("1350");
        assertThat(resumen.certificados().getFirst().tipo()).isEqualTo("Mano de obra y servicios");
        assertThat(resumen.certificados().getFirst().tareas()).hasSize(2);
        assertThat(resumen.certificados().getLast().tipo()).isEqualTo("Servicio certificable");
        assertThat(resumen.opcionesProveedores()).extracting(p -> p.nombre()).containsExactly("Alfredo Rondinoni", "Daniela Marisol Paz");
        assertThat(service.resumir(obra.getId(), YearMonth.of(2026, 10), rondinoni.getId(), 0).totalMes()).isEqualByComparingTo("1000");
        assertThat(service.mesesDisponibles(obra.getId())).containsExactly(YearMonth.of(2026, 10), YearMonth.of(2026, 1));
        var session = new MockHttpSession(); session.setAttribute("obraActivaId", obra.getId());
        mvc.perform(get("/certificados-quincenales").session(session).param("mes", "2026-10").param("proveedorId", rondinoni.getId().toString()))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Servicio certificable")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alfredo Rondinoni")));
        var csv = mvc.perform(get("/certificados-quincenales/exportar").session(session).param("mes", "2026-10").param("proveedorId", rondinoni.getId().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("Servicio certificable", "1000,00").doesNotContain("Daniela Marisol Paz");
        var total = csv.lines().filter(line -> line.startsWith("\"TOTAL\"")).findFirst().orElseThrow().split(";", -1);
        assertThat(total).hasSize(14);
        assertThat(total[11]).isEqualTo("\"1000,00\"");
    }

    @Test
    void agrupaCincoDiasAntesYDespuesDeCadaCierreSinSepararElFinDeSemana() {
        for (YearMonth mes : new YearMonth[]{YearMonth.of(2026, 8), YearMonth.of(2026, 12), YearMonth.of(2024, 2), YearMonth.of(2025, 2)}) {
            for (int desfase = -5; desfase <= 5; desfase++) {
                var primera = mes.atDay(16).plusDays(desfase);
                var segunda = mes.plusMonths(1).atDay(1).plusDays(desfase);
                assertThat(CertificadosQuincenalesService.mesTrabajo(primera)).isEqualTo(mes);
                assertThat(CertificadosQuincenalesService.quincenaTrabajo(primera)).isEqualTo(1);
                assertThat(CertificadosQuincenalesService.mesTrabajo(segunda)).isEqualTo(mes);
                assertThat(CertificadosQuincenalesService.quincenaTrabajo(segunda)).isEqualTo(2);
            }
            for (var fecha = CertificadosQuincenalesService.inicioEmisiones(mes); !fecha.isAfter(CertificadosQuincenalesService.finEmisiones(mes)); fecha = fecha.plusDays(1)) {
                assertThat(CertificadosQuincenalesService.mesTrabajo(fecha)).isEqualTo(mes);
            }
        }
    }

    @Test
    void oc463CertificadoUnoDel14DeAgostoPerteneceALaPrimeraDeAgosto() throws Exception {
        var obra = obra("Regresion OC 463");
        var oc = orden(obra, proveedor("Miguel Muriel")); oc.setNumero("463");
        certificado(oc, "2026-08-14", item(oc, CategoriaItem.MANO_OBRA, "10694217.00"), "100");
        em.flush();
        var agosto = service.resumir(obra.getId(), YearMonth.of(2026, 8), null, 1);
        assertThat(agosto.certificados()).hasSize(1);
        assertThat(agosto.primera()).isEqualByComparingTo("10694217.00");
        assertThat(agosto.certificados().getFirst().fecha()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(service.resumir(obra.getId(), YearMonth.of(2026, 7), null, 0).certificados()).isEmpty();
        assertThat(service.mesesDisponibles(obra.getId())).containsExactly(YearMonth.of(2026, 8));
        var session = new MockHttpSession(); session.setAttribute("obraActivaId", obra.getId());
        mvc.perform(get("/certificados-quincenales").session(session).param("mes", "2026-08").param("quincena", "1"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("10.694.217,00")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("14/08/2026")));
        var csv = mvc.perform(get("/certificados-quincenales/exportar").session(session).param("mes", "2026-08").param("quincena", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("\"2026-08\";\"1\";\"Miguel Muriel\";\"463\"", "10694217,00");
    }

    @Test
    void renderizaResumenDetalleVacioYExportaLosMismosImportes() throws Exception {
        var obra = obra("Obra de prueba"); var oc = orden(obra, proveedor("Muriel & Asociados"));
        var tarea = item(oc, CategoriaItem.MANO_OBRA, "1234.56"); tarea.setDetalle("Colocación; pisos \"PB\"");
        certificado(oc, "2026-11-01", tarea, "25"); em.flush();
        var session = new MockHttpSession(); session.setAttribute("obraActivaId", obra.getId());
        mvc.perform(get("/certificados-quincenales").session(session).param("mes", "2026-10"))
                .andExpect(status().isOk()).andExpect(view().name("quincenal/lista"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("308,64")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Muriel &amp; Asociados")));
        mvc.perform(get("/certificados-quincenales").session(session).param("mes", "2026-10").param("proveedorId", "-1"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("sin certificados este mes")));
        var csv = mvc.perform(get("/certificados-quincenales/exportar").session(session).param("mes", "2026-10").param("quincena", "2"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", "attachment; filename=certificados-quincenales-2026-10.csv"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF").contains("308,64", "01/11/2026", "Colocación; pisos \"\"PB\"\"");
        mvc.perform(get("/certificados-quincenales").session(session).param("mes", "2026-13")).andExpect(status().isBadRequest());
        mvc.perform(get("/certificados-quincenales").session(session).param("quincena", "3")).andExpect(status().isBadRequest());
    }

    private Obra obra(String nombre) { var o = new Obra(); o.setNombre(nombre); o.setActiva(true); em.persist(o); return o; }
    private Proveedor proveedor(String nombre) { var p = new Proveedor(); p.setNombre(nombre); em.persist(p); return p; }
    private OrdenCompra orden(Obra obra, Proveedor proveedor) { var o = new OrdenCompra(); o.setObra(obra); o.setProveedorEntidad(proveedor); o.setNumero("457"); em.persist(o); return o; }
    private ItemOrdenCompra item(OrdenCompra oc, CategoriaItem categoria, String importe) {
        var i = new ItemOrdenCompra(); i.setOrdenCompra(oc); i.setCategoria(categoria); i.setImporte(new BigDecimal(importe));
        i.setItem("457.1"); i.setDetalle("Tarea de prueba"); i.setUnidad("m2"); em.persist(i); return i;
    }
    private Certificacion certificado(OrdenCompra oc, String fecha, ItemOrdenCompra item, String porcentaje) {
        var c = new Certificacion(); c.setOrdenCompra(oc); c.setNumero(1); c.setFecha(LocalDate.parse(fecha)); em.persist(c); avance(c, item, porcentaje); return c;
    }
    private void avance(Certificacion cert, ItemOrdenCompra item, String porcentaje) {
        var i = new ItemCertificacion(); i.setCertificacion(cert); i.setItemOrdenCompra(item); i.setPorcentajeActual(new BigDecimal(porcentaje)); em.persist(i);
    }
}
