package com.obra.certificaciones.notapedido.controller;

import com.obra.certificaciones.notapedido.dto.NotaPedidoForm;
import com.obra.certificaciones.notapedido.entity.EstadoNotaPedido;
import com.obra.certificaciones.notapedido.entity.NotaPedido;
import com.obra.certificaciones.notapedido.entity.PrioridadNotaPedido;
import com.obra.certificaciones.notapedido.entity.TipoNotaPedido;
import com.obra.certificaciones.notapedido.service.NotaPedidoService;
import com.obra.certificaciones.obra.service.ObraService;
import com.obra.certificaciones.oc.repository.OrdenCompraRepository;
import com.obra.certificaciones.proveedor.repository.ProveedorRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/notas-pedido")
public class NotaPedidoController {
    private static final int PAGE_SIZE = 25;

    private final NotaPedidoService notaPedidoService;
    private final ObraService obraService;
    private final ProveedorRepository proveedorRepository;
    private final OrdenCompraRepository ordenCompraRepository;

    @GetMapping
    public String listar(@RequestParam(required = false) String busqueda,
                         @RequestParam(required = false) TipoNotaPedido tipo,
                         @RequestParam(required = false) EstadoNotaPedido estado,
                         @RequestParam(defaultValue = "0") int page,
                         Model model,
                         HttpSession session) {
        Page<NotaPedido> notasPage = notaPedidoService.listar(
                busqueda,
                tipo,
                estado,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        model.addAttribute("notasPage", notasPage);
        model.addAttribute("notas", notasPage.getContent());
        model.addAttribute("busqueda", busqueda);
        model.addAttribute("tipoSeleccionado", tipo);
        model.addAttribute("estadoSeleccionado", estado);
        model.addAttribute("tipos", TipoNotaPedido.values());
        model.addAttribute("estados", EstadoNotaPedido.values());
        return "notas-pedido/lista";
    }

    @GetMapping("/nueva")
    public String nueva(Model model, HttpSession session) {
        model.addAttribute("form", notaPedidoService.crearForm(null, obraService.obraActiva(session)));
        cargarCombos(model, session);
        return "notas-pedido/form";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        NotaPedido nota = notaPedidoService.obtener(id);
        model.addAttribute("nota", nota);
        model.addAttribute("mostrarItem", nota.getItems().stream().anyMatch(item -> StringUtils.hasText(item.getItem())));
        model.addAttribute("mostrarUnidad", nota.getItems().stream().anyMatch(item -> StringUtils.hasText(item.getUnidad())));
        model.addAttribute("mostrarObservacionItem", nota.getItems().stream().anyMatch(item -> StringUtils.hasText(item.getObservacion())));
        model.addAttribute("mostrarBase", nota.getItems().stream().anyMatch(item -> StringUtils.hasText(item.getBase())));
        model.addAttribute("mostrarEntrega", nota.getItems().stream().anyMatch(item -> StringUtils.hasText(item.getEntrega())));
        return "notas-pedido/detalle";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model, HttpSession session) {
        model.addAttribute("form", notaPedidoService.crearForm(id, obraService.obraActiva(session)));
        cargarCombos(model, session);
        return "notas-pedido/form";
    }

    @PostMapping
    public String guardar(@ModelAttribute("form") NotaPedidoForm form,
                          Model model,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        try {
            NotaPedido nota = notaPedidoService.guardar(form, obraService.obraActiva(session));
            redirectAttributes.addFlashAttribute("success", "Nota de pedido guardada correctamente.");
            return "redirect:/notas-pedido/" + nota.getId();
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            cargarCombos(model, session);
            return "notas-pedido/form";
        }
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        notaPedidoService.eliminar(id);
        redirectAttributes.addFlashAttribute("success", "Nota de pedido eliminada.");
        return "redirect:/notas-pedido";
    }

    private void cargarCombos(Model model, HttpSession session) {
        var obra = obraService.obraActiva(session);
        model.addAttribute("proveedores", proveedorRepository.findByActivoTrueOrderByNombreAsc());
        model.addAttribute("ordenesCompra", ordenCompraRepository.buscarConFiltros(obra.getId(), null, null, null));
        model.addAttribute("tipos", TipoNotaPedido.values());
        model.addAttribute("estados", EstadoNotaPedido.values());
        model.addAttribute("prioridades", PrioridadNotaPedido.values());
    }
}
