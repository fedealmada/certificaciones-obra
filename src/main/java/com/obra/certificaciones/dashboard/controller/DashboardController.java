package com.obra.certificaciones.dashboard.controller;

import com.obra.certificaciones.alerta.service.AlertaSistemaService;
import com.obra.certificaciones.obra.service.ObraService;
import com.obra.certificaciones.oc.repository.OrdenCompraRepository;
import com.obra.certificaciones.reporte.service.ReporteService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {
    private final OrdenCompraRepository ordenCompraRepository;
    private final ReporteService reporteService;
    private final AlertaSistemaService alertaSistemaService;
    private final ObraService obraService;

    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpSession session) {
        var obra = obraService.obraActiva(session);
        model.addAttribute("reporte", reporteService.generarGeneral(obra));
        model.addAttribute("ultimasOrdenes", ordenCompraRepository.findTop5ByObraIdOrderByFechaDescIdDesc(obra.getId()));
        model.addAttribute("alertas", alertaSistemaService.alertasGenerales());
        return "dashboard/index";
    }
}
