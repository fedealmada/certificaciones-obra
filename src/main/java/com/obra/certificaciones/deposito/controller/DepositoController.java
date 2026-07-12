package com.obra.certificaciones.deposito.controller;

import com.obra.certificaciones.deposito.dto.MovimientoDepositoForm;
import com.obra.certificaciones.deposito.entity.DepositoItem;
import com.obra.certificaciones.deposito.entity.TipoInsumoDeposito;
import com.obra.certificaciones.deposito.entity.TipoMovimientoDeposito;
import com.obra.certificaciones.deposito.service.DepositoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/deposito")
@RequiredArgsConstructor
public class DepositoController {
    private final DepositoService depositoService;

    @GetMapping
    public String listar(Model model) {
        var items = depositoService.listarItems();
        model.addAttribute("items", items);
        model.addAttribute("movimientos", depositoService.movimientosRecientes());
        model.addAttribute("devolucionesPendientes", depositoService.devolucionesPendientes());
        model.addAttribute("totalItems", items.stream().filter(DepositoItem::isActivo).count());
        model.addAttribute("bajoStock", depositoService.contarBajoStock());
        model.addAttribute("totalUnidades", depositoService.totalUnidades());
        return "deposito/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("item", new DepositoItem());
        model.addAttribute("tipos", TipoInsumoDeposito.values());
        return "deposito/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("item", depositoService.obtener(id));
        model.addAttribute("tipos", TipoInsumoDeposito.values());
        return "deposito/form";
    }

    @PostMapping("/items")
    public String guardar(@ModelAttribute("item") DepositoItem item, Model model, RedirectAttributes redirectAttributes) {
        try {
            depositoService.guardarItem(item);
            redirectAttributes.addFlashAttribute("success", "Insumo guardado correctamente.");
            return "redirect:/deposito";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("item", item);
            model.addAttribute("tipos", TipoInsumoDeposito.values());
            return "deposito/form";
        }
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        depositoService.eliminarItem(id);
        redirectAttributes.addFlashAttribute("success", "Insumo eliminado o desactivado correctamente.");
        return "redirect:/deposito";
    }

    @GetMapping("/{id}/movimiento")
    public String nuevoMovimiento(@PathVariable Long id, Model model) {
        model.addAttribute("item", depositoService.obtener(id));
        model.addAttribute("form", new MovimientoDepositoForm());
        model.addAttribute("tiposMovimiento", TipoMovimientoDeposito.values());
        model.addAttribute("trabajadores", depositoService.listarTrabajadoresActivos());
        model.addAttribute("movimientos", depositoService.movimientosItem(id));
        model.addAttribute("modoEdicion", false);
        return "deposito/movimiento";
    }

    @PostMapping("/{id}/movimiento")
    public String guardarMovimiento(@PathVariable Long id,
                                    @ModelAttribute("form") MovimientoDepositoForm form,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        try {
            depositoService.registrarMovimiento(id, form);
            redirectAttributes.addFlashAttribute("success", "Movimiento registrado correctamente.");
            return "redirect:/deposito";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("item", depositoService.obtener(id));
            model.addAttribute("form", form);
            model.addAttribute("tiposMovimiento", TipoMovimientoDeposito.values());
            model.addAttribute("trabajadores", depositoService.listarTrabajadoresActivos());
            model.addAttribute("movimientos", depositoService.movimientosItem(id));
            model.addAttribute("modoEdicion", false);
            return "deposito/movimiento";
        }
    }

    @GetMapping("/movimientos/{movimientoId}")
    public String detalleMovimiento(@PathVariable Long movimientoId, Model model) {
        model.addAttribute("movimiento", depositoService.obtenerMovimiento(movimientoId));
        return "deposito/movimiento-detalle";
    }

    @GetMapping("/movimientos/{movimientoId}/editar")
    public String editarMovimiento(@PathVariable Long movimientoId, Model model) {
        var movimiento = depositoService.obtenerMovimiento(movimientoId);
        model.addAttribute("item", movimiento.getItem());
        model.addAttribute("movimiento", movimiento);
        model.addAttribute("form", depositoService.formDesdeMovimiento(movimiento));
        model.addAttribute("tiposMovimiento", TipoMovimientoDeposito.values());
        model.addAttribute("trabajadores", depositoService.listarTrabajadoresActivos());
        model.addAttribute("movimientos", depositoService.movimientosItem(movimiento.getItem().getId()));
        model.addAttribute("modoEdicion", true);
        return "deposito/movimiento";
    }

    @PostMapping("/movimientos/{movimientoId}")
    public String actualizarMovimiento(@PathVariable Long movimientoId,
                                       @ModelAttribute("form") MovimientoDepositoForm form,
                                       Model model,
                                       RedirectAttributes redirectAttributes) {
        var movimiento = depositoService.obtenerMovimiento(movimientoId);
        try {
            depositoService.actualizarMovimiento(movimientoId, form);
            redirectAttributes.addFlashAttribute("success", "Movimiento actualizado correctamente.");
            return "redirect:/deposito/movimientos/" + movimientoId;
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("item", movimiento.getItem());
            model.addAttribute("movimiento", movimiento);
            model.addAttribute("form", form);
            model.addAttribute("tiposMovimiento", TipoMovimientoDeposito.values());
            model.addAttribute("trabajadores", depositoService.listarTrabajadoresActivos());
            model.addAttribute("movimientos", depositoService.movimientosItem(movimiento.getItem().getId()));
            model.addAttribute("modoEdicion", true);
            return "deposito/movimiento";
        }
    }

    @GetMapping("/movimientos/{movimientoId}/devolver")
    public String devolverMovimiento(@PathVariable Long movimientoId, Model model) {
        var movimiento = depositoService.obtenerMovimiento(movimientoId);
        MovimientoDepositoForm form = new MovimientoDepositoForm();
        form.setTipo(TipoMovimientoDeposito.DEVOLUCION);
        form.setCantidad(movimiento.getCantidad());
        form.setTrabajadorId(movimiento.getTrabajadorId());
        form.setTrabajadorNombre(movimiento.getTrabajadorNombre());
        form.setResponsable(movimiento.getResponsable());
        form.setDestino(movimiento.getDestino());
        model.addAttribute("movimiento", movimiento);
        model.addAttribute("form", form);
        model.addAttribute("trabajadores", depositoService.listarTrabajadoresActivos());
        return "deposito/devolucion";
    }

    @PostMapping("/movimientos/{movimientoId}/devolver")
    public String guardarDevolucion(@PathVariable Long movimientoId,
                                    @ModelAttribute("form") MovimientoDepositoForm form,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        try {
            var devolucion = depositoService.registrarDevolucion(movimientoId, form);
            redirectAttributes.addFlashAttribute("success", "Devolucion registrada correctamente.");
            return "redirect:/deposito/movimientos/" + devolucion.getId();
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("movimiento", depositoService.obtenerMovimiento(movimientoId));
            model.addAttribute("form", form);
            model.addAttribute("trabajadores", depositoService.listarTrabajadoresActivos());
            return "deposito/devolucion";
        }
    }
}
