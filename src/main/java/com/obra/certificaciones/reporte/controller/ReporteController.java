package com.obra.certificaciones.reporte.controller;

import com.obra.certificaciones.categoria.service.CategoriaOrdenService;
import com.obra.certificaciones.reporte.service.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.stream.IntStream;

@Controller
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;
    private final CategoriaOrdenService categoriaOrdenService;

    @GetMapping("/reportes")
    public String reportes(@RequestParam(required = false) Integer anio,
                           @RequestParam(required = false) Integer mes,
                           @RequestParam(required = false) Long categoriaId,
                           Model model) {
        LocalDate hoy = LocalDate.now();
        int anioSeleccionado = anio == null ? hoy.getYear() : anio;
        int mesSeleccionado = mes == null ? hoy.getMonthValue() : mes;
        model.addAttribute("reporte", reporteService.generarGeneral());
        model.addAttribute("reporteMensual", reporteService.generarMensual(anioSeleccionado, mesSeleccionado, categoriaId));
        model.addAttribute("evolucionMensual", reporteService.generarEvolucionMensual(categoriaId));
        model.addAttribute("categorias", categoriaOrdenService.listarActivas());
        model.addAttribute("categoriaSeleccionada", categoriaId);
        model.addAttribute("anios", IntStream.rangeClosed(2025, hoy.getYear() + 1).boxed().toList());
        return "reportes/index";
    }
}
