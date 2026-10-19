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
class CertificacionCantidadTest {
    @Autowired CertificacionService service;
    @Autowired CertificacionCalculoService calculo;
    @Autowired AjustesCertificacionService ajustes;
    @Autowired CertificadosQuincenalesService quincenas;
    @Autowired CertificacionExportService export;
    @Autowired ImportacionCertificacionesService importar;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;

    @Test void unaUnidadDeDoceConservaElPrecioExactoTrasRecargarYConcuerdaEnTodosLosModulos() throws Exception {
        var oc = oc(); var c = service.guardar(oc.getId(), form(oc, 1, "1"));
        c.setCacMesActual("2026-08"); c.setCacIndiceActual(new BigDecimal("20167.9"));
        em.flush(); var id=c.getId(); em.clear(); c=service.obtener(id); oc=c.getOrdenCompra();
        assertThat(c.getItems()).allSatisfy(i -> assertThat(i.getCantidadActual()).isEqualByComparingTo("1"));
        var l=ajustes.liquidar(c);
        assertThat(l.bruto()).isEqualByComparingTo("4275905");
        assertThat(l.retencion()).isEqualByComparingTo("213795.25");
        assertThat(l.cac()).isZero(); assertThat(l.neto()).isEqualByComparingTo("4062109.75");
        var detalle=calculo.calcularDetalle(c); var resumen=calculo.calcularResumenHasta(c,oc.getItems(),detalle);
        assertThat(resumen.totalActual()).isEqualByComparingTo("4275905");
        assertThat(detalle.getFirst().cantidadActual()).isEqualByComparingTo("1");
        var q=quincenas.resumir(oc.getObra().getId(),YearMonth.of(2026,10),null,1);
        assertThat(q.totalVisible()).isEqualByComparingTo("4062109.75");
        assertThat(q.certificados().getFirst().tareas()).allSatisfy(t -> assertThat(t.cantidad()).isEqualByComparingTo("1"));
        assertThat(new String(export.exportarCsv(oc,c),java.nio.charset.StandardCharsets.UTF_8)).contains("Cantidad actual", "4275905.00", "4062109.75");
        var ses=session(oc);
        mvc.perform(get("/oc/{oc}/certificaciones/{id}",oc.getId(),id).session(ses)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Avance en cantidades")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("4.062.109,75")));
        mvc.perform(get("/oc/{oc}/certificaciones/{id}/editar",oc.getId(),id).session(ses)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Cantidades ejecutadas")));
        mvc.perform(get("/oc/{oc}/editar",oc.getId()).session(ses)).andExpect(status().isOk());
        mvc.perform(get("/certificados-quincenales/exportar").param("mes","2026-10").session(ses)).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Cantidad actual")));
    }

    @Test void doceCertificadosDeUnaUnidadCierranExactamenteSinPerderCentavos() {
        var oc=oc(); Certificacion ultimo=null;
        for(int n=1;n<=12;n++) ultimo=service.guardar(oc.getId(),form(oc,n,"1"));
        em.flush(); em.clear(); ultimo=service.obtener(ultimo.getId());
        var resumen=calculo.calcularResumenHasta(ultimo,ultimo.getOrdenCompra().getItems(),calculo.calcularDetalle(ultimo));
        assertThat(resumen.totalAcumulado()).isEqualByComparingTo("51310860");
        assertThat(resumen.saldoPendiente()).isZero();
        assertThat(calculo.calcularDetalle(ultimo)).allSatisfy(i -> assertThat(i.isTerminado()).isTrue());
        var orden=ultimo.getOrdenCompra();
        assertThatThrownBy(() -> service.guardar(orden.getId(),form(orden,13,"0.000001"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cantidad contratada");
    }

    @Test void editarConsideraCertificadosPosterioresYConservaModalidadAunqueCambieLaOc() {
        var oc=oc(); var primero=service.guardar(oc.getId(),form(oc,1,"1"));
        service.guardar(oc.getId(),form(oc,2,"10"));
        oc.setCertificacionPorCantidad(false);
        assertThat(service.crearFormEdicion(oc.getId(),primero.getId()).isPorCantidad()).isTrue();
        assertThatThrownBy(() -> service.actualizar(oc.getId(),primero.getId(),form(oc,1,"3"))).hasMessageContaining("cantidad contratada");
        var corregido=service.actualizar(oc.getId(),primero.getId(),form(oc,1,"2"));
        assertThat(corregido.getItems().getFirst().getCantidadActual()).isEqualByComparingTo("2");
    }

    @Test void admiteFraccionesYNoReinterpretaCertificadosPorcentualesAnteriores() {
        var oc=oc(); oc.setCertificacionPorCantidad(false);
        var anterior=service.guardar(oc.getId(),form(oc,1,"25"));
        oc.setCertificacionPorCantidad(true);
        assertThat(service.crearFormEdicion(oc.getId(),anterior.getId()).isPorCantidad()).isFalse();
        var actual=service.guardar(oc.getId(),form(oc,2,"0.125")); em.flush();
        assertThat(actual.getItems().getFirst().getCantidadActual()).isEqualByComparingTo("0.125");
        assertThat(ajustes.liquidar(actual).bruto()).isEqualByComparingTo("534488.13"); // Cada item redondea a centavos.
        var detalle=calculo.calcularDetalle(actual);
        assertThat(detalle.getFirst().cantidadAnterior()).isEqualByComparingTo("3");
        assertThat(detalle.getFirst().cantidadAcumulada()).isEqualByComparingTo("3.125");
    }

    @Test void acumuladoPorCantidadRespetaElRedondeoDeCadaCertificadoEnMediosCentavos() {
        var oc=oc(); var c=service.guardar(oc.getId(),form(oc,1,"0.625"));
        var detalle=calculo.calcularDetalle(c);
        var resumen=calculo.calcularResumenHasta(c,oc.getItems(),detalle);
        assertThat(resumen.totalAcumulado()).isEqualByComparingTo(ajustes.liquidar(c).bruto());
        assertThat(detalle.getFirst().montoAcumulado()).isEqualByComparingTo(detalle.getFirst().montoActual());
    }

    @Test void rechazaExcesoSinCambiarLaCantidadQueElUsuarioEscribio() throws Exception {
        var oc=oc(); var f=form(oc,1,"13");
        assertThatThrownBy(() -> service.guardar(oc.getId(),f)).hasMessageContaining("cantidad contratada");
        assertThat(f.getItems().getFirst().getPorcentajeActual()).isEqualByComparingTo("13");
        mvc.perform(post("/oc/{oc}/certificaciones",oc.getId()).session(session(oc))
            .param("numero","1").param("fecha","2026-10-13")
            .param("items[0].itemOrdenCompraId",oc.getItems().getFirst().getId().toString())
            .param("items[0].porcentajeActual","13"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("supera la cantidad contratada")));
    }

    @Test void importarPlanillaConAvanceCantidadUsaUnidadesYPrecioUnitario() {
        var oc=oc();
        String planilla="Obra: Prueba Contratista: Daniela Gomez CERTIFICADO N° 1 FECHA: 13/10/2026\nOC: 475\nAVANCE CANT.\nItem\tDetalle\tUnidad\tCantidad\tUnitario\tTotal\tAnterior\tActual\tAcum\n1\tMini VRF 3 ambientes\tPISO\t12,00\t2051373,00\t24616476,00\t0,00\t1,00\t1,00\n2\tMini VRF 4 ambientes\tPISO\t12,00\t2224532,00\t26694384,00\t0,00\t1,00\t1,00\nTOTAL";
        var r=importar.importar(planilla);
        assertThat(r.puedeImportar()).isTrue();
        var c=service.listarPorOrdenCompra(oc.getId()).getFirst();
        assertThat(c.isPorCantidad()).isTrue();
        assertThat(ajustes.liquidar(c).bruto()).isEqualByComparingTo("4275905");
    }

    private NuevaCertificacionForm form(OrdenCompra oc,int numero,String valor) {
        var f=new NuevaCertificacionForm(); f.setNumero(numero); f.setFecha(LocalDate.of(2026,10,13));
        for(var item:oc.getItems()) { var i=new ItemNuevaCertificacionForm(); i.setItemOrdenCompraId(item.getId()); i.setPorcentajeActual(new BigDecimal(valor)); f.getItems().add(i); }
        return f;
    }
    private MockHttpSession session(OrdenCompra oc) { var s=new MockHttpSession(); s.setAttribute("obraActivaId",oc.getObra().getId()); return s; }
    private OrdenCompra oc() {
        var o=new Obra(); o.setNombre("Prueba cantidades"); o.setActiva(true); em.persist(o);
        var p=new Proveedor(); p.setNombre("Daniela Gomez"); em.persist(p);
        var oc=new OrdenCompra(); oc.setObra(o); oc.setProveedorEntidad(p); oc.setNumero("475"); oc.setFecha(LocalDate.of(2026,9,23)); oc.setModoSeguimiento(ModoSeguimientoOrden.CERTIFICACION); oc.setCertificacionPorCantidad(true);
        oc.setFondoReparoPorcentaje(new BigDecimal("5")); oc.setCacHabilitado(true); oc.setCacMesBase("2026-08"); oc.setCacIndiceBase(new BigDecimal("20167.9")); oc.setCacDuracionMeses(12); em.persist(oc);
        int n=0;
        for(var precio:new String[]{"2051373","2224532"}) { var i=new ItemOrdenCompra(); i.setOrdenCompra(oc); i.setItem(""+(++n)); i.setDetalle("Mini VRF "+(n+2)+" ambientes"); i.setUnidad("PISO"); i.setCantidad(new BigDecimal("12")); i.setPrecioUnitario(new BigDecimal(precio)); i.calcularImporte(); em.persist(i); oc.getItems().add(i); }
        em.flush(); return oc;
    }
}
