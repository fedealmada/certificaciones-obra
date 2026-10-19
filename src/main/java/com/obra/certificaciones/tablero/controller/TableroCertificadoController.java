package com.obra.certificaciones.tablero.controller;

import com.obra.certificaciones.obra.service.ObraService;
import com.obra.certificaciones.tablero.dto.AvancesTableroForm;
import com.obra.certificaciones.tablero.entity.*;
import com.obra.certificaciones.tablero.service.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Controller @RequestMapping("/tablero-certificados") @RequiredArgsConstructor
public class TableroCertificadoController {
    private final TableroCertificadoService service;
    private final ObraService obras;
    private final TableroExportService export;

    @InitBinder public void limites(org.springframework.web.bind.WebDataBinder binder) { binder.setAutoGrowCollectionLimit(5000); }

    @ModelAttribute
    public void obraActiva(HttpSession session,HttpServletRequest request,Model model) {
        var obra=obras.obraActiva(session); model.addAttribute("obra",obra);
        var matcher=java.util.regex.Pattern.compile("/tablero-certificados/(\\d+)(?:/.*)?").matcher(request.getRequestURI());
        if(matcher.matches()) service.validarObra(Long.valueOf(matcher.group(1)),obra.getId());
    }
    @GetMapping public String listar(Model m,HttpSession s) { m.addAttribute("tableros",service.listar(obras.obraActiva(s))); return "tablero/lista"; }
    @PostMapping public String crear(@RequestParam(required=false) String nombre,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fechaDesde,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fechaHasta,HttpSession s) {
        return red(service.crear(obras.obraActiva(s),nombre,fechaDesde,fechaHasta).getId());
    }
    @GetMapping("/{id}") public String detalle(@PathVariable Long id,Model m) { cargar(id,m,null); return "tablero/detalle"; }
    private void cargar(Long id,Model m,AvancesTableroForm f) {
        var t=service.obtener(id); m.addAttribute("tablero",t); m.addAttribute("totales",service.totales(id)); m.addAttribute("tieneSiguiente",service.tieneSiguiente(id));
        var valores=new HashMap<Long,BigDecimal>(); if(f!=null) f.getFilas().forEach(i->{if(i.getPorcentaje()!=null) valores.put(i.getId(),i.getPorcentaje());}); m.addAttribute("avancesActuales",valores);
    }
    @PostMapping("/{id}") public String datos(@PathVariable Long id,@ModelAttribute TableroCertificado datos,RedirectAttributes r) { datos.setId(id); service.guardarDatos(datos); r.addFlashAttribute("success","Datos del certificado guardados."); return red(id); }
    @PostMapping("/{id}/avances") public String avances(@PathVariable Long id,@ModelAttribute AvancesTableroForm form,Model m,RedirectAttributes r) {
        try { service.guardarAvances(id,form); r.addFlashAttribute("success","Todos los avances fueron guardados."); return red(id); }
        catch(IllegalArgumentException e) { cargar(id,m,form); m.addAttribute("error",e.getMessage()+" No se guardó ningún avance."); return "tablero/detalle"; }
    }
    @PostMapping("/{id}/continuar") public String continuar(@PathVariable Long id,RedirectAttributes r) { var t=service.continuar(id); r.addFlashAttribute("success","Nuevo período creado: tareas y acumulados conservados; avances actuales en cero."); return red(t.getId()); }
    @PostMapping("/{id}/cerrar") public String cerrar(@PathVariable Long id,RedirectAttributes r) { service.cerrar(id); r.addFlashAttribute("success","Certificado cerrado. Sus importes quedan conservados para el siguiente período."); return red(id); }
    @PostMapping("/{id}/reabrir") public String reabrir(@PathVariable Long id,RedirectAttributes r) { service.reabrir(id); r.addFlashAttribute("success","Certificado reabierto para edición."); return red(id); }
    @PostMapping("/{id}/eliminar") public String eliminar(@PathVariable Long id,RedirectAttributes r) { service.eliminarTablero(id); r.addFlashAttribute("success","Borrador eliminado."); return "redirect:/tablero-certificados"; }
    @PostMapping("/{id}/items/manual") public String manual(@PathVariable Long id) { return red(id)+"/items/"+service.crearItemManual(id).getId()+"/editar"; }
    @PostMapping("/{id}/grupos") public String grupo(@PathVariable Long id,@RequestParam(required=false) String nombreGrupo) { return red(id)+"/items/"+service.crearGrupo(id,nombreGrupo).getId()+"/editar"; }
    @GetMapping("/{id}/items/{itemId}/editar") public String editar(@PathVariable Long id,@PathVariable Long itemId,Model m) { m.addAttribute("tablero",service.obtener(id)); m.addAttribute("fila",service.obtenerItem(id,itemId)); return "tablero/item"; }
    @PostMapping("/{id}/items/{itemId}") public String item(@PathVariable Long id,@PathVariable Long itemId,@ModelAttribute TableroCertificadoItem datos,Model m,RedirectAttributes r) {
        try { service.guardarItem(id,itemId,datos); r.addFlashAttribute("success","Tarea y costos guardados."); return red(id)+"#fila-"+itemId; }
        catch(IllegalArgumentException e) { datos.setId(itemId); var original=service.obtenerItem(id,itemId); datos.setAvanceAnteriorPorcentaje(original.getAvanceAnteriorPorcentaje()); datos.setAvanceCertificadoPorcentaje(original.getAvanceCertificadoPorcentaje()); datos.setMontoAnteriorFijo(original.getMontoAnteriorFijo()); datos.setItemOrdenCompra(original.getItemOrdenCompra()); m.addAttribute("tablero",service.obtener(id)); m.addAttribute("fila",datos); m.addAttribute("error",e.getMessage()); return "tablero/item"; }
    }
    @PostMapping("/{id}/items/{itemId}/eliminar") public String quitar(@PathVariable Long id,@PathVariable Long itemId,RedirectAttributes r) { service.eliminarItem(id,itemId); r.addFlashAttribute("success","Tarea quitada del borrador."); return red(id); }
    @GetMapping("/{id}/agregar-oc") public String elegir(@PathVariable Long id,Model m) { m.addAttribute("tablero",service.obtener(id)); m.addAttribute("candidatos",service.candidatos(id)); return "tablero/seleccionar"; }
    @PostMapping("/{id}/agregar-oc") public String agregar(@PathVariable Long id,@RequestParam(required=false) List<Long> items,RedirectAttributes r) { int n=service.agregarItemsOc(id,items==null?List.of():items); r.addFlashAttribute("success",n+" tareas agregadas. Definí el avance de cobro en la planilla."); return red(id); }
    @PostMapping("/{id}/importar-items") public String detectar(@PathVariable Long id,RedirectAttributes r) { int n=service.importarItemsCertificadosPeriodo(id); r.addFlashAttribute("success",n+" tareas del período agregadas como presupuesto, sin copiar su avance real."); return red(id); }
    @GetMapping("/historial") public String historial(HttpSession s,Model m) { m.addAttribute("historial",service.historial(obras.obraActiva(s))); return "tablero/historial"; }
    @GetMapping("/{id}/imprimir") public String imprimir(@PathVariable Long id,@RequestParam(defaultValue="false") boolean completo,Model m) {
        cargar(id,m,null); var t=(TableroCertificado)m.getAttribute("tablero");
        var filas=t.getItems().stream().filter(i->!i.isGrupo()).filter(i->completo || i.montoCertificado().signum()!=0).toList();
        m.addAttribute("filasPresentacion",filas); m.addAttribute("completo",completo);
        m.addAttribute("presupuestoPresentado",filas.stream().filter(i->!i.isRecurrente()).map(TableroCertificadoItem::totalTarea).reduce(BigDecimal.ZERO,BigDecimal::add));
        m.addAttribute("saldoAnteriorNoVisible",t.montoAnterior().subtract(filas.stream().map(TableroCertificadoItem::montoAnterior).reduce(BigDecimal.ZERO,BigDecimal::add)));
        m.addAttribute("ajusteActualPresentacion",t.montoCertificado().subtract(filas.stream().map(TableroCertificadoItem::montoCertificado).reduce(BigDecimal.ZERO,BigDecimal::add)));
        m.addAttribute("saldoAcumuladoNoVisible",t.montoAcumulado().subtract(filas.stream().map(TableroCertificadoItem::montoAcumulado).reduce(BigDecimal.ZERO,BigDecimal::add)));
        return "tablero/imprimir";
    }
    @GetMapping("/{id}/exportar") public ResponseEntity<byte[]> csv(@PathVariable Long id) { return archivo(export.certificado(service.obtener(id)),"certificado-general-"+id+".csv"); }
    @GetMapping("/historial/exportar") public ResponseEntity<byte[]> csvHistorial(HttpSession s) { return archivo(export.historial(service.historial(obras.obraActiva(s))),"historial-certificados-generales.csv"); }
    private ResponseEntity<byte[]> archivo(byte[] bytes,String nombre) { return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename="+nombre).contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).body(bytes); }
    @ExceptionHandler(IllegalArgumentException.class) public String error(IllegalArgumentException e,RedirectAttributes r) { r.addFlashAttribute("error",e.getMessage()); return "redirect:/tablero-certificados"; }
    private String red(Long id) { return "redirect:/tablero-certificados/"+id; }
}
