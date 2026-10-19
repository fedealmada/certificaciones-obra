package com.obra.certificaciones.tablero;

import com.obra.certificaciones.tablero.entity.*;
import com.obra.certificaciones.tablero.dto.*;
import com.obra.certificaciones.tablero.service.*;
import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.oc.entity.*;
import com.obra.certificaciones.proveedor.entity.Proveedor;
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
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc(addFilters=false) @Transactional
class TableroGeneralTest {
    @Autowired TableroCertificadoService service;
    @Autowired TableroExportService export;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;

    @Test void tareaManualSePuedeCertificarAlCienSinOcNiAvanceReal() {
        var t=tablero(); var i=tarea(t,"Relevamiento", "1000");
        avances(t,i,"100");
        assertThat(i.getItemOrdenCompra()).isNull(); assertThat(i.totalTarea()).isEqualByComparingTo("1450");
        assertThat(t.montoCertificado()).isEqualByComparingTo("1450");
        service.cerrar(t.getId()); assertThat(t.isCerrado()).isTrue();
    }
    @Test void continuarArrastraAcumuladosSinRepetirExtrasNiAvanceActualYConservaHistorial() {
        var t=tablero(); var i=tarea(t,"Contrapiso","1000"); i.setMaterialesAdicionalesEtapa(new BigDecimal("50")); avances(t,i,"40");
        service.cerrar(t.getId()); var next=service.continuar(t.getId()); var n=next.getItems().getFirst();
        assertThat(n.getAvanceAnteriorPorcentaje()).isEqualByComparingTo("40"); assertThat(n.montoAnterior()).isEqualByComparingTo("630");
        assertThat(n.getAvanceCertificadoPorcentaje()).isZero(); assertThat(n.getMaterialesAdicionalesEtapa()).isZero(); assertThat(next.montoCertificado()).isZero();
        assertThat(n.claveHistorial()).isEqualTo(i.claveHistorial()); assertThat(next.getFechaDesde()).isEqualTo(LocalDate.of(2026,11,1));
        n.setCostoManoObra(new BigDecimal("2000")); avances(next,n,"10");
        assertThat(n.montoAnterior()).isEqualByComparingTo("630"); assertThat(n.montoCertificado()).isEqualByComparingTo("290"); assertThat(n.montoAcumulado()).isEqualByComparingTo("920");
        assertThat(i.montoCertificado()).isEqualByComparingTo("630");
        assertThatThrownBy(()->service.continuar(t.getId())).hasMessageContaining("ya tiene");
    }
    @Test void bloqueaEdicionesDelCerradoYLaReaperturaSiTieneSucesor() {
        var t=tablero(); var i=tarea(t,"Tarea","100"); avances(t,i,"20"); service.cerrar(t.getId());
        assertThatThrownBy(()->service.crearItemManual(t.getId())).hasMessageContaining("cerrado");
        assertThatThrownBy(()->service.guardarAvances(t.getId(),form(i,"10"))).hasMessageContaining("cerrado");
        service.reabrir(t.getId()); assertThat(t.isCerrado()).isFalse(); service.cerrar(t.getId()); service.continuar(t.getId());
        assertThatThrownBy(()->service.reabrir(t.getId())).hasMessageContaining("posterior");
    }
    @Test void avanceMasAnteriorNoPuedeSuperarCienYNoSeGuardaParcialmente() {
        var t=tablero(); var a=tarea(t,"A","100"); var b=tarea(t,"B","100"); a.setAvanceAnteriorPorcentaje(new BigDecimal("80"));
        var f=form(b,"25"); var invalid=new AvancesTableroForm.Fila(); invalid.setId(a.getId()); invalid.setPorcentaje(new BigDecimal("21")); f.getFilas().add(invalid);
        assertThatThrownBy(()->service.guardarAvances(t.getId(),f)).hasMessageContaining("entre 0 y 20");
        assertThat(b.getAvanceCertificadoPorcentaje()).isZero();
    }
    @Test void ocSeSeleccionaSinCertificadosEIncorporaPresupuestoConAvanceCero() {
        var t=tablero(); var oc=oc(t.getObra(),CategoriaItem.OTRO); var material=oc(t.getObra(),CategoriaItem.MATERIAL); var otra=oc(obra("Otra"),CategoriaItem.MANO_OBRA);
        assertThat(service.candidatos(t.getId())).extracting(ItemOrdenCompra::getId).contains(oc.getItems().getFirst().getId()).doesNotContain(material.getItems().getFirst().getId(),otra.getItems().getFirst().getId());
        service.agregarItemsOc(t.getId(),List.of(oc.getItems().getFirst().getId()));
        var i=t.getItems().getFirst(); assertThat(i.getServicios()).isEqualByComparingTo("1000"); assertThat(i.getAvanceCertificadoPorcentaje()).isZero();
        assertThat(service.candidatos(t.getId())).isEmpty();
        avances(t,i,"100"); assertThat(i.montoCertificado()).isEqualByComparingTo("1450");
    }
    @Test void mismaReferenciaManualNoMezclaDosTareasAlContinuar() {
        var t=tablero(); var a=tarea(t,"A","100"); var b=tarea(t,"B","200"); a.setItemCodigo("1");b.setItemCodigo("1"); avances(t,a,"20"); avances(t,b,"40"); service.cerrar(t.getId()); service.continuar(t.getId());
        var h=service.historial(t.getObra()); assertThat(h.filas()).hasSize(2);
        assertThat(h.filas().getFirst().celdas().get(1).acumulado()).isEqualByComparingTo("20");
        assertThat(h.filas().get(1).celdas().get(1).acumulado()).isEqualByComparingTo("40");
    }
    @Test void noSePuedeQuitarUnaTareaConHistoriaNiEditarUnaFilaDeOtroCertificado() {
        var t=tablero(); var a=tarea(t,"A","100"); avances(t,a,"20"); service.cerrar(t.getId()); var next=service.continuar(t.getId());
        assertThatThrownBy(()->service.eliminarItem(next.getId(),next.getItems().getFirst().getId())).hasMessageContaining("importes anteriores");
        assertThatThrownBy(()->service.guardarItem(next.getId(),a.getId(),new TableroCertificadoItem())).hasMessageContaining("no pertenece");
    }
    @Test void pantallasExportacionesYErroresDeCargaFuncionanSinModificarAlAbrir() throws Exception {
        var t=tablero(); var i=tarea(t,"=Tarea propia","1000"); i.setItemCodigo("A"); i.setAvanceAnteriorPorcentaje(new BigDecimal("10")); avances(t,i,"20"); em.flush(); var s=session(t.getObra());
        for(var ruta:List.of("/tablero-certificados","/tablero-certificados/"+t.getId(),"/tablero-certificados/"+t.getId()+"/items/"+i.getId()+"/editar","/tablero-certificados/"+t.getId()+"/agregar-oc","/tablero-certificados/historial","/tablero-certificados/"+t.getId()+"/imprimir"))
            mvc.perform(get(ruta).session(s)).andExpect(status().isOk());
        assertThat(i.getAvanceAnteriorPorcentaje()).isEqualByComparingTo("10");
        var csv=new String(export.certificado(t),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("TOTAL PRESENTE CERTIFICADO","290,00","'=Tarea propia");
        mvc.perform(post("/tablero-certificados/{id}/avances",t.getId()).session(s).param("filas[0].id",i.getId().toString()).param("filas[0].porcentaje","91"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("No se guardó ningún avance")));
        assertThat(i.getAvanceCertificadoPorcentaje()).isEqualByComparingTo("20");
        mvc.perform(get("/tablero-certificados/{id}/exportar",t.getId()).session(s)).andExpect(status().isOk());
        mvc.perform(get("/tablero-certificados/historial/exportar").session(s)).andExpect(status().isOk());
    }
    @Test void protegeObraActivaYPeriodoInvalido() throws Exception {
        var t=tablero(); var s=session(obra("No autorizada"));
        mvc.perform(get("/tablero-certificados/{id}",t.getId()).session(s)).andExpect(redirectedUrl("/tablero-certificados"));
        assertThatThrownBy(()->service.crear(t.getObra(),"Inválido",LocalDate.of(2026,10,31),LocalDate.of(2026,10,1))).hasMessageContaining("período válido");
    }
    @Test void importesOriginalesSeConservanAlGuardarSinCambiosYSeRecalculanAlCambiarAvance() {
        var t=tablero(); var i=tarea(t,"Original","100");
        i.setTotalTareaInformado(new BigDecimal("145.01")); i.setAvanceCertificadoPorcentaje(new BigDecimal("8.33")); i.setMontoActualInformado(new BigDecimal("12.09"));
        service.guardarAvances(t.getId(),form(i,"8.33")); assertThat(i.montoCertificado()).isEqualByComparingTo("12.09");
        service.guardarAvances(t.getId(),form(i,"10")); assertThat(i.getMontoActualInformado()).isNull(); assertThat(i.montoCertificado()).isEqualByComparingTo("14.50");
    }
    @Test void cambiarSoloDescripcionConservaValoresOriginalesYCostosNuevosLosRecalculan() {
        var t=tablero(); var i=tarea(t,"Original","100"); i.setTotalTareaInformado(new BigDecimal("145.01")); i.setMontoActualInformado(new BigDecimal("12.09")); i.setAvanceCertificadoPorcentaje(new BigDecimal("8.33"));
        var datos=new TableroCertificadoItem(); org.springframework.beans.BeanUtils.copyProperties(i,datos); datos.setDescripcionTarea("Texto corregido");
        service.guardarItem(t.getId(),i.getId(),datos); assertThat(i.montoCertificado()).isEqualByComparingTo("12.09"); assertThat(i.totalTarea()).isEqualByComparingTo("145.01");
        datos.setCostoManoObra(new BigDecimal("200")); service.guardarItem(t.getId(),i.getId(),datos); assertThat(i.getTotalTareaInformado()).isNull(); assertThat(i.getMontoActualInformado()).isNull();
    }
    @Test void anexoMensualSeRepiteAlCienSinDuplicarSaldoNiAcumularPorcentajes() {
        var t=tablero(); var i=tarea(t,"Logistica","3500000"); i.setRecurrente(true); i.setCostoEstructuralPorcentaje(BigDecimal.ZERO); i.setBeneficioEmpresarialPorcentaje(BigDecimal.ZERO); i.setMontoAnteriorFijo(new BigDecimal("33740000")); avances(t,i,"100");
        assertThat(i.montoAcumulado()).isEqualByComparingTo("37240000"); assertThat(t.totalTarea()).isZero(); service.cerrar(t.getId());
        var next=service.continuar(t.getId()); var copia=next.getItems().getFirst(); assertThat(copia.getAvanceAnteriorPorcentaje()).isZero(); assertThat(copia.montoAnterior()).isEqualByComparingTo("37240000"); avances(next,copia,"100");
        assertThat(copia.avanceAcumuladoPorcentaje()).isEqualByComparingTo("100"); assertThat(copia.montoAcumulado()).isEqualByComparingTo("40740000");
    }
    @Test void conciliacionCentavosPermiteReproducirLosTotalesDeLaPlanilla() {
        var t=tablero(); var i=tarea(t,"Original","100"); i.setMontoAnteriorFijo(new BigDecimal("112.35")); i.setMontoActualInformado(new BigDecimal("293.64")); t.setAjusteAnterior(new BigDecimal("0.01")); t.setAjusteActual(new BigDecimal("-0.01"));
        assertThat(t.montoAnterior()).isEqualByComparingTo("112.36"); assertThat(t.montoCertificado()).isEqualByComparingTo("293.63"); assertThat(t.montoAcumulado()).isEqualByComparingTo("405.99");
        t.setAjusteAcumulado(new BigDecimal("-0.01")); assertThat(t.montoAcumulado()).isEqualByComparingTo("405.98");
        service.cerrar(t.getId()); var siguiente=service.continuar(t.getId()); assertThat(siguiente.montoAnterior()).isEqualByComparingTo("405.98");
    }

    @Test void presentacionFiltraSinMovimientoYConservaSaldos() throws Exception {
        var t=tablero(); var activo=tarea(t,"Tarea con movimiento","100"); activo.setAvanceCertificadoPorcentaje(new BigDecimal("50"));
        var anterior=tarea(t,"Tarea histórica sin movimiento","200"); anterior.setMontoAnteriorFijo(new BigDecimal("80")); em.flush();
        var resultado=mvc.perform(get("/tablero-certificados/{id}/imprimir",t.getId()).session(session(t.getObra()))).andExpect(status().isOk()).andReturn();
        var modelo=resultado.getModelAndView().getModel();
        assertThat((java.util.List<?>)modelo.get("filasPresentacion")).hasSize(1);
        assertThat((BigDecimal)modelo.get("saldoAnteriorNoVisible")).isEqualByComparingTo("80");
        assertThat((BigDecimal)modelo.get("saldoAcumuladoNoVisible")).isEqualByComparingTo("80");
        assertThat(resultado.getResponse().getContentAsString()).contains("TOTAL PRESENTE CERTIFICADO","DESCRIPCI");
        var completo=mvc.perform(get("/tablero-certificados/{id}/imprimir",t.getId()).param("completo","true").session(session(t.getObra()))).andExpect(status().isOk()).andReturn();
        assertThat((java.util.List<?>)completo.getModelAndView().getModel().get("filasPresentacion")).hasSize(2);
    }

    private TableroCertificado tablero() { return service.crear(obra("Obra de prueba"),"Octubre 2026",LocalDate.of(2026,10,1),LocalDate.of(2026,10,31)); }
    private Obra obra(String nombre) { var o=new Obra();o.setNombre(nombre);o.setActiva(true);o.setCliente("BAPRISUD");em.persist(o);return o; }
    private TableroCertificadoItem tarea(TableroCertificado t,String nombre,String costo) { var i=service.crearItemManual(t.getId()); i.setDescripcionTarea(nombre);i.setCostoManoObra(new BigDecimal(costo));em.flush();return i; }
    private AvancesTableroForm form(TableroCertificadoItem i,String p) { var f=new AvancesTableroForm();var fila=new AvancesTableroForm.Fila();fila.setId(i.getId());fila.setPorcentaje(new BigDecimal(p));f.getFilas().add(fila);return f; }
    private void avances(TableroCertificado t,TableroCertificadoItem i,String p) { service.guardarAvances(t.getId(),form(i,p)); }
    private MockHttpSession session(Obra o) { var s=new MockHttpSession();s.setAttribute("obraActivaId",o.getId());return s; }
    private OrdenCompra oc(Obra obra,CategoriaItem tipo) { var p=new Proveedor();p.setNombre("Proveedor "+UUID.randomUUID());em.persist(p);var oc=new OrdenCompra();oc.setObra(obra);oc.setNumero("475");oc.setProveedorEntidad(p);oc.setFecha(LocalDate.of(2026,10,1));oc.setModoSeguimiento(ModoSeguimientoOrden.CERTIFICACION);em.persist(oc);var i=new ItemOrdenCompra();i.setOrdenCompra(oc);i.setItem("1");i.setDetalle("Tarea origen");i.setCategoria(tipo);i.setCantidad(BigDecimal.ONE);i.setPrecioUnitario(new BigDecimal("1000"));i.calcularImporte();em.persist(i);oc.getItems().add(i);em.flush();return oc; }
}
