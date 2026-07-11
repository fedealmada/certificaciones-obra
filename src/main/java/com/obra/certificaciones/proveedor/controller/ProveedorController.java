package com.obra.certificaciones.proveedor.controller;

import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.proveedor.entity.Proveedor;
import com.obra.certificaciones.proveedor.service.ProveedorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/proveedores")
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService proveedorService;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("proveedores", proveedorService.listarTodos());
        return "proveedor/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("proveedor", new Proveedor());
        return "proveedor/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("proveedor", proveedorService.obtener(id));
        return "proveedor/form";
    }

    @PostMapping
    public String guardar(@ModelAttribute("proveedor") Proveedor proveedor, Model model) {
        try {
            Proveedor guardado = proveedorService.guardar(proveedor);
            return "redirect:/proveedores/" + guardado.getId();
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("proveedor", proveedor);
            return "proveedor/form";
        }
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        List<OrdenCompra> ordenes = proveedorService.ordenesProveedor(id);
        AnalisisProveedor analisis = analizarProveedor(ordenes);
        model.addAttribute("proveedor", proveedorService.obtener(id));
        model.addAttribute("ordenes", ordenes);
        model.addAttribute("analisis", analisis);
        return "proveedor/detalle";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id) {
        proveedorService.eliminar(id);
        return "redirect:/proveedores";
    }

    private AnalisisProveedor analizarProveedor(List<OrdenCompra> ordenes) {
        BigDecimal total = ordenes.stream()
                .map(OrdenCompra::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal promedio = ordenes.isEmpty()
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(ordenes.size()), 2, RoundingMode.HALF_UP);
        OrdenCompra ultimaOrden = ordenes.stream()
                .max(Comparator.comparing(OrdenCompra::getFecha, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(OrdenCompra::getId))
                .orElse(null);
        List<DatoAnalisisProveedor> categorias = totalesPorCategoria(ordenes, total);
        List<DatoAnalisisProveedor> evolucionMensual = evolucionMensual(ordenes);
        return new AnalisisProveedor(total, promedio, ultimaOrden, categorias, evolucionMensual);
    }

    private List<DatoAnalisisProveedor> totalesPorCategoria(List<OrdenCompra> ordenes, BigDecimal totalProveedor) {
        Map<String, BigDecimal> totales = ordenes.stream()
                .flatMap(orden -> orden.getItems().stream())
                .collect(Collectors.groupingBy(
                        item -> item.getCategoria() == null ? "Sin categoria" : item.getCategoria().getDescripcion(),
                        Collectors.mapping(item -> item.getImporte() == null ? BigDecimal.ZERO : item.getImporte(),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));
        return totales.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> new DatoAnalisisProveedor(entry.getKey(), entry.getValue(), porcentaje(entry.getValue(), totalProveedor)))
                .toList();
    }

    private List<DatoAnalisisProveedor> evolucionMensual(List<OrdenCompra> ordenes) {
        if (ordenes.isEmpty()) {
            return List.of();
        }
        Map<YearMonth, BigDecimal> totalesPorMes = ordenes.stream()
                .filter(orden -> orden.getFecha() != null)
                .collect(Collectors.groupingBy(
                        orden -> YearMonth.from(orden.getFecha()),
                        Collectors.mapping(OrdenCompra::getTotal,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));
        BigDecimal maximo = totalesPorMes.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        List<YearMonth> meses = totalesPorMes.keySet().stream()
                .sorted()
                .skip(Math.max(0, totalesPorMes.size() - 6))
                .toList();
        List<DatoAnalisisProveedor> resultado = new ArrayList<>();
        for (YearMonth mes : meses) {
            BigDecimal totalMes = totalesPorMes.getOrDefault(mes, BigDecimal.ZERO);
            String etiqueta = mes.getMonth().getDisplayName(TextStyle.SHORT, new Locale("es", "AR")) + " " + mes.getYear();
            resultado.add(new DatoAnalisisProveedor(etiqueta, totalMes, porcentaje(totalMes, maximo)));
        }
        return resultado;
    }

    private BigDecimal porcentaje(BigDecimal valor, BigDecimal total) {
        if (valor == null || total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return valor.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    public record AnalisisProveedor(BigDecimal totalComprado,
                                    BigDecimal promedioOrden,
                                    OrdenCompra ultimaOrden,
                                    List<DatoAnalisisProveedor> categorias,
                                    List<DatoAnalisisProveedor> evolucionMensual) {
    }

    public record DatoAnalisisProveedor(String nombre, BigDecimal valor, BigDecimal porcentaje) {
    }
}
