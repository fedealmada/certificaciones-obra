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
        i.setMaterialesConfirmados(true);
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
        var html=resultado.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("Monto del certificado anterior");
        assertThat(html).doesNotContain("carry-row", "Saldos de tareas sin movimiento");
        var totalFila=java.util.regex.Pattern.compile("<tr class=\"total-row\">(.*?)</tr>",java.util.regex.Pattern.DOTALL).matcher(html);
        assertThat(totalFila.find()).isTrue();
        assertThat(totalFila.group(1)).doesNotContain("80,00");
        var completo=mvc.perform(get("/tablero-certificados/{id}/imprimir",t.getId()).param("completo","true").session(session(t.getObra()))).andExpect(status().isOk()).andReturn();
        assertThat((java.util.List<?>)completo.getModelAndView().getModel().get("filasPresentacion")).hasSize(2);
    }

    private TableroCertificado tablero() { return service.crear(obra("Obra de prueba"),"Octubre 2026",LocalDate.of(2026,10,1),LocalDate.of(2026,10,31)); }
    private Obra obra(String nombre) { var o=new Obra();o.setNombre(nombre);o.setActiva(true);o.setCliente("BAPRISUD");em.persist(o);return o; }
    @org.junit.jupiter.api.Test
    void materialesSePreparanAntesDelAvanceYNoSeReabrenAlVolverACero() {
        var t=tablero(); var i=tarea(t,"Nueva tarea","100");
        i.setMaterialesConfirmados(false);
        assertThatThrownBy(()->service.guardarAvances(t.getId(),form(i,"10"))).hasMessageContaining("confirmá los materiales");
        i.setMaterialesConfirmados(true);
        service.guardarAvances(t.getId(),form(i,"10"));
        service.guardarAvances(t.getId(),form(i,"0"));
        var datos=new TableroCertificadoItem();
        org.springframework.beans.BeanUtils.copyProperties(i,datos);
        datos.setMaterialesAsignados(new BigDecimal("50"));
        assertThatThrownBy(()->service.guardarItem(t.getId(),i.getId(),datos)).hasMessageContaining("bloqueados");
    }

    @Test void comprasApiFiltraFechaYPaginaSinSalirDelEditor() throws Exception {
        var t=tablero();var tarea=tarea(t,"Tarea","100");
        var compra=oc(t.getObra(),CategoriaItem.MATERIAL);compra.setFecha(t.getFechaDesde());
        var fuera=oc(t.getObra(),CategoriaItem.MATERIAL);fuera.setFecha(t.getFechaDesde().minusDays(1));
        em.flush();
        var session=new MockHttpSession();session.setAttribute("obraActivaId",t.getObra().getId());
        mvc.perform(get("/tablero-certificados/{id}/items/{item}/materiales",t.getId(),tarea.getId()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cantidad").value(1))
                .andExpect(jsonPath("$.compras[0].id").value(compra.getItems().getFirst().getId()))
                .andExpect(jsonPath("$.bloqueado").value(false));
        mvc.perform(post("/tablero-certificados/{id}/items/{item}",t.getId(),tarea.getId()).session(session)
                .param("descripcionTarea","Tarea con materiales").param("materialSelectionPresent","true")
                .param("materialIds",compra.getItems().getFirst().getId().toString()).param("materialImportes","125.50"))
                .andExpect(status().is3xxRedirection());
        assertThat(tarea.getMaterialesAsignados()).isEqualByComparingTo("125.50");
        assertThat(tarea.materialesPreparados()).isTrue();
    }

    @Test void comprasPorPeriodoAsignacionesParcialesYContinuidad() {
        var t=tablero(); var a=tarea(t,"Tarea A","100"); var b=tarea(t,"Tarea B","100");
        var compra=oc(t.getObra(),CategoriaItem.MATERIAL); compra.setFecha(t.getFechaDesde());
        var material=compra.getItems().getFirst();em.flush();
        var listado=service.comprasMateriales(t.getId(),a.getId(),null,null,"",null,0);
        assertThat(listado.compras()).extracting(TableroCertificadoService.MaterialCompra::id).contains(material.getId());
        service.asignarMateriales(t.getId(),a.getId(),List.of(new TableroCertificadoService.AsignacionMaterial(material.getId(),new BigDecimal("600"))));
        assertThat(a.getMaterialesAsignados()).isEqualByComparingTo("600");
        em.flush();
        assertThat(service.comprasMateriales(t.getId(),b.getId(),null,null,"",null,0).compras().getFirst().disponible()).isEqualByComparingTo("400");
        assertThatThrownBy(()->service.asignarMateriales(t.getId(),b.getId(),List.of(new TableroCertificadoService.AsignacionMaterial(material.getId(),new BigDecimal("401")))))
                .hasMessageContaining("supera el disponible");
        avances(t,a,"10");service.cerrar(t.getId());var siguiente=service.continuar(t.getId());
        var copia=siguiente.getItems().stream().filter(i->i.claveHistorial().equals(a.claveHistorial())).findFirst().orElseThrow();
        assertThat(service.comprasMateriales(siguiente.getId(),copia.getId(),null,null,"",null,0).seleccionados()).hasSize(1);
        assertThatThrownBy(()->service.asignarMateriales(siguiente.getId(),copia.getId(),List.of())).hasMessageContaining("bloqueados");
    }

    @Test void comprasFueraDeObraNoSePuedenAsignarYGuardarEsAtomico() {
        var t=tablero();var a=tarea(t,"Tarea","100");
        var ajena=oc(obra("Otra obra"),CategoriaItem.MATERIAL).getItems().getFirst();
        assertThatThrownBy(()->service.asignarMateriales(t.getId(),a.getId(),List.of(new TableroCertificadoService.AsignacionMaterial(ajena.getId(),BigDecimal.ONE))))
                .hasMessageContaining("no pertenece");
        var compra=oc(t.getObra(),CategoriaItem.MATERIAL);compra.setFecha(t.getFechaDesde());em.flush();
        var datos=new TableroCertificadoItem();org.springframework.beans.BeanUtils.copyProperties(a,datos);
        service.guardarItemConCompras(t.getId(),a.getId(),datos,List.of(compra.getItems().getFirst().getId()),List.of(new BigDecimal("250.50")));
        assertThat(a.getMaterialesAsignados()).isEqualByComparingTo("250.50");
        service.guardarItemConCompras(t.getId(),a.getId(),datos,List.of(),List.of());em.flush();
        assertThat(a.getMaterialesAsignados()).isZero();
        assertThat(service.comprasMateriales(t.getId(),a.getId(),null,null,"",null,0).seleccionados()).isEmpty();
    }

    private TableroCertificadoItem tarea(TableroCertificado t,String nombre,String costo) { var i=service.crearItemManual(t.getId()); i.setDescripcionTarea(nombre);i.setCostoManoObra(new BigDecimal(costo));i.setMaterialesConfirmados(true);em.flush();return i; }
    private AvancesTableroForm form(TableroCertificadoItem i,String p) { var f=new AvancesTableroForm();var fila=new AvancesTableroForm.Fila();fila.setId(i.getId());fila.setPorcentaje(new BigDecimal(p));f.getFilas().add(fila);return f; }
    private void avances(TableroCertificado t,TableroCertificadoItem i,String p) { service.guardarAvances(t.getId(),form(i,p)); }
    private MockHttpSession session(Obra o) { var s=new MockHttpSession();s.setAttribute("obraActivaId",o.getId());return s; }
    private OrdenCompra oc(Obra obra,CategoriaItem tipo) { var p=new Proveedor();p.setNombre("Proveedor "+UUID.randomUUID());em.persist(p);var oc=new OrdenCompra();oc.setObra(obra);oc.setNumero("475");oc.setProveedorEntidad(p);oc.setFecha(LocalDate.of(2026,10,1));oc.setModoSeguimiento(ModoSeguimientoOrden.CERTIFICACION);em.persist(oc);var i=new ItemOrdenCompra();i.setOrdenCompra(oc);i.setItem("1");i.setDetalle("Tarea origen");i.setCategoria(tipo);i.setCantidad(BigDecimal.ONE);i.setPrecioUnitario(new BigDecimal("1000"));i.calcularImporte();em.persist(i);oc.getItems().add(i);em.flush();return oc; }
}
