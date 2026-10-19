package com.obra.certificaciones.tablero.service;
import com.obra.certificaciones.tablero.entity.TableroCertificado;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
@Service
public class TableroExportService {
    public byte[] certificado(TableroCertificado t) {
        var s=new StringBuilder("\uFEFF");
        fila(s,"Obra",t.getObra().getNombre()); fila(s,"Cliente",t.getCliente()); fila(s,"Constructora",t.getEmpresa());
        fila(s,"Certificado",t.getNumero()==null?t.getNombre():t.getNumero().toString()); fila(s,"Nombre",t.getNombre()); fila(s,"Estado",t.isCerrado()?"Cerrado":"Borrador"); fila(s,"Desde",t.getFechaDesde().toString()); fila(s,"Hasta",t.getFechaHasta().toString());
        fila(s,"Codigo","Tarea","Contratista","Rubro","Unidad","Cantidad","Precio unitario","Mano de obra","Materiales","Servicios","Material suministrado empresa","Costo base informado","Costo subtotal","Estructura %","Estructura $","Beneficio %","Beneficio $","Total tarea","Anterior %","Actual %","Acumulado %","Anterior $","Actual $","Acumulado $","Adicional del periodo $");
        for(var i:t.getItems()) fila(s,i.getItemCodigo(),i.getDescripcionTarea(),i.getContratista(),i.getRubro(),i.getUnidad(),i.getCantidad(),i.getPrecioUnitario(),i.getCostoManoObra(),i.getMaterialesAsignados(),i.getServicios(),i.getMaterialesSuministradosEmpresa(),i.getSubtotalManual(),i.costoSubtotal(),i.getCostoEstructuralPorcentaje(),i.costoEstructuralMonto(),i.getBeneficioEmpresarialPorcentaje(),i.beneficioEmpresarialMonto(),i.totalTarea(),i.getAvanceAnteriorPorcentaje(),i.getAvanceCertificadoPorcentaje(),i.avanceAcumuladoPorcentaje(),i.montoAnterior(),i.montoCertificado(),i.montoAcumulado(),i.getMaterialesAdicionalesEtapa());
        fila(s,"CONCILIACION CENTAVOS ANTERIOR",t.getAjusteAnterior()); fila(s,"CONCILIACION CENTAVOS ACTUAL",t.getAjusteActual()); fila(s,"CONCILIACION CENTAVOS ACUMULADO",t.getAjusteAcumulado());
        fila(s,"TOTAL ANTERIOR",t.montoAnterior()); fila(s,"TOTAL PRESENTE CERTIFICADO",t.montoCertificado()); fila(s,"TOTAL ACUMULADO",t.montoAcumulado()); fila(s,"Observaciones",t.getObservacion());
        return s.toString().getBytes(StandardCharsets.UTF_8);
    }
    public byte[] historial(TableroCertificadoService.Historial h) {
        var s=new StringBuilder("\uFEFF"); var headers=new java.util.ArrayList<Object>(java.util.List.of("Codigo","Tarea"));
        for(var t:h.certificados()) { String n=t.getNombre(); headers.add(n+" · Actual %"); headers.add(n+" · Acumulado %"); headers.add(n+" · Actual $"); headers.add(n+" · Acumulado $"); }
        fila(s,headers.toArray());
        for(var f:h.filas()) { var c=new java.util.ArrayList<Object>(); c.add(f.codigo()); c.add(f.descripcion()); for(var cell:f.celdas()) { if(cell==null) c.addAll(java.util.List.of("","","","")); else { c.add(cell.actual()); c.add(cell.acumulado()); c.add(cell.monto()); c.add(cell.montoAcumulado()); } } fila(s,c.toArray()); }
        var total=new java.util.ArrayList<Object>(); total.add("TOTAL"); total.add(""); for(var t:h.certificados()) { total.add(""); total.add(""); total.add(t.montoCertificado()); total.add(t.montoAcumulado()); } fila(s,total.toArray());
        return s.toString().getBytes(StandardCharsets.UTF_8);
    }
    private void fila(StringBuilder s,Object... valores) { for(int n=0;n<valores.length;n++) { if(n>0)s.append(';'); Object v=valores[n]; String text=v==null?"":(v instanceof BigDecimal d?d.toPlainString().replace('.',','):v.toString()); if(v instanceof String && text.matches("^[=+@-].*"))text="'"+text; s.append('"').append(text.replace("\"","\"\"")).append('"'); } s.append('\n'); }
}
