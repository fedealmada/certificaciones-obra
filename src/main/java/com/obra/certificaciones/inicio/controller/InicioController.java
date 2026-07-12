package com.obra.certificaciones.inicio.controller;

import com.obra.certificaciones.configuracion.dto.ModuloSistema;
import com.obra.certificaciones.configuracion.service.ConfiguracionSistemaService;
import com.obra.certificaciones.obra.service.ObraService;
import com.obra.certificaciones.reporte.service.ReporteService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class InicioController {
    private final ConfiguracionSistemaService configuracionService;
    private final ReporteService reporteService;
    private final ObraService obraService;

    @GetMapping("/")
    public String inicio(Model model, HttpSession session) {
        var obra = obraService.obraActiva(session);
        List<ModuloSistema> modulos = configuracionService.modulosActivos();
        model.addAttribute("modulos", modulos);
        model.addAttribute("obraPeriodo", obra.getNombre());
        model.addAttribute("areas", List.of(
                new AreaTrabajo(
                        "Administracion",
                        "Compras, proveedores, importaciones y control economico.",
                        "bi-briefcase",
                        "admin",
                        modulos.stream().filter(modulo -> Set.of(
                                "modulo.oc",
                                "modulo.importarOc",
                                "modulo.proveedores",
                                "modulo.importarCertificados",
                                "modulo.reportes"
                        ).contains(modulo.clave())).toList()
                ),
                new AreaTrabajo(
                        "Obra y produccion",
                        "Itemizado, avances, certificados, entregas y viajes.",
                        "bi-buildings",
                        "obra",
                        modulos.stream().filter(modulo -> Set.of(
                                "modulo.dashboard",
                                "modulo.itemizado",
                                "modulo.items",
                                "modulo.materiales",
                                "modulo.asistencia"
                        ).contains(modulo.clave())).toList()
                ),
                new AreaTrabajo(
                        "Deposito / Panol",
                        "Stock, movimientos, devoluciones e ingresos desde recepciones.",
                        "bi-boxes",
                        "deposito",
                        modulos.stream().filter(modulo -> Set.of(
                                "modulo.deposito",
                                "modulo.catalogo"
                        ).contains(modulo.clave())).toList()
                ),
                new AreaTrabajo(
                        "Configuracion",
                        "Rubros, categorias y parametros generales del sistema.",
                        "bi-sliders",
                        "config",
                        modulos.stream().filter(modulo -> Set.of(
                                "modulo.rubros",
                                "modulo.categorias"
                        ).contains(modulo.clave())).toList()
                )
        ));
        model.addAttribute("reporte", reporteService.generarGeneral(obra));
        return "inicio/index";
    }

    public record AreaTrabajo(
            String nombre,
            String descripcion,
            String icono,
            String tono,
            List<ModuloSistema> modulos
    ) {
    }
}
