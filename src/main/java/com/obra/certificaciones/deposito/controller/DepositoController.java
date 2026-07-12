package com.obra.certificaciones.deposito.controller;

import com.obra.certificaciones.deposito.dto.MovimientoDepositoForm;
import com.obra.certificaciones.deposito.entity.DepositoItem;
import com.obra.certificaciones.deposito.entity.DepositoTrabajador;
import com.obra.certificaciones.deposito.entity.TipoInsumoDeposito;
import com.obra.certificaciones.deposito.entity.TipoMovimientoDeposito;
import com.obra.certificaciones.deposito.service.DepositoService;
import com.obra.certificaciones.obra.entity.Obra;
import com.obra.certificaciones.obra.service.ObraService;
import jakarta.servlet.http.HttpSession;
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
    private final ObraService obraService;

    @GetMapping
    public String listar(Model model, HttpSession session) {
        Obra obra = obraService.obraActiva(session);
        var items = depositoService.listarItems(obra);
        model.addAttribute("items", items);
        model.addAttribute("movimientos", depositoService.movimientosRecientes(obra));
        model.addAttribute("devolucionesPendientes", depositoService.devolucionesPendientes(obra));
        model.addAttribute("itemsBajoStock", depositoService.itemsBajoStock(obra));
        model.addAttribute("trabajadores", depositoService.listarTrabajadoresActivos());
        model.addAttribute("totalItems", items.stream().filter(DepositoItem::isActivo).count());
        model.addAttribute("bajoStock", depositoService.contarBajoStock(obra));
        model.addAttribute("totalUnidades", depositoService.totalUnidades(obra));
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
    public String guardar(@ModelAttribute("item") DepositoItem item, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            depositoService.guardarItem(item, obraService.obraActiva(session));
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

    @GetMapping("/personas")
    public String personas(Model model) {
        model.addAttribute("personas", depositoService.listarTrabajadores());
        model.addAttribute("persona", new DepositoTrabajador());
        return "deposito/personas";
    }

    @GetMapping("/personas/{id}/editar")
    public String editarPersona(@PathVariable Long id, Model model) {
        model.addAttribute("personas", depositoService.listarTrabajadores());
        model.addAttribute("persona", depositoService.obtenerTrabajador(id));
        return "deposito/personas";
    }

    @PostMapping("/personas")
    public String guardarPersona(@ModelAttribute("persona") DepositoTrabajador persona,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        try {
            depositoService.guardarTrabajador(persona);
            redirectAttributes.addFlashAttribute("success", "Persona guardada correctamente.");
            return "redirect:/deposito/personas";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("personas", depositoService.listarTrabajadores());
            model.addAttribute("persona", persona);
            return "deposito/personas";
        }
    }

    @PostMapping("/personas/{id}/desactivar")
    public String desactivarPersona(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        depositoService.desactivarTrabajador(id);
        redirectAttributes.addFlashAttribute("success", "Persona desactivada correctamente.");
        return "redirect:/deposito/personas";
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
