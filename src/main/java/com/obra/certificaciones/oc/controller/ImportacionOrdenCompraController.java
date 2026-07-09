package com.obra.certificaciones.oc.controller;

import com.obra.certificaciones.categoria.service.CategoriaOrdenService;
import com.obra.certificaciones.oc.dto.ImportacionOrdenCompraForm;
import com.obra.certificaciones.oc.dto.ImportacionOrdenCompraLoteForm;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import com.obra.certificaciones.oc.service.ImportacionOrdenCompraService;
import com.obra.certificaciones.proveedor.service.ProveedorService;
import com.obra.certificaciones.rubro.service.RubroService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/oc/importar")
@RequiredArgsConstructor
public class ImportacionOrdenCompraController {
    private final ImportacionOrdenCompraService importacionService;
    private final ProveedorService proveedorService;
    private final CategoriaOrdenService categoriaOrdenService;
    private final RubroService rubroService;

    @GetMapping
    public String importar(Model model) {
        model.addAttribute("form", new ImportacionOrdenCompraForm());
        model.addAttribute("lote", new ImportacionOrdenCompraLoteForm());
        cargarListas(model);
        return "oc/importar";
    }

    @PostMapping("/previsualizar")
    public String previsualizar(@RequestParam("archivos") MultipartFile[] archivos, Model model) {
        try {
            ImportacionOrdenCompraLoteForm lote = importacionService.previsualizar(archivos);
            model.addAttribute("lote", lote);
            model.addAttribute("form", lote.getOrdenes().isEmpty() ? new ImportacionOrdenCompraForm() : lote.getOrdenes().get(0));
            model.addAttribute("previsualizacion", true);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("form", new ImportacionOrdenCompraForm());
            model.addAttribute("lote", new ImportacionOrdenCompraLoteForm());
            model.addAttribute("error", ex.getMessage());
        }
        cargarListas(model);
        return "oc/importar";
    }

    @PostMapping
    public String confirmar(@ModelAttribute("lote") ImportacionOrdenCompraLoteForm lote,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        try {
            List<OrdenCompra> ordenes = importacionService.importarLote(lote);
            redirectAttributes.addFlashAttribute("accionCompletada", true);
            redirectAttributes.addFlashAttribute("accionTitulo", "Importacion lista");
            redirectAttributes.addFlashAttribute("accionMensaje", ordenes.size() == 1
                    ? "La OC quedo cargada desde el archivo adjunto."
                    : "Se importaron " + ordenes.size() + " ordenes de compra.");
            return ordenes.size() == 1 ? "redirect:/oc/" + ordenes.get(0).getId() : "redirect:/oc";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("previsualizacion", true);
            model.addAttribute("lote", lote);
            model.addAttribute("form", lote.getOrdenes().isEmpty() ? new ImportacionOrdenCompraForm() : lote.getOrdenes().get(0));
            cargarListas(model);
            return "oc/importar";
        }
    }

    private void cargarListas(Model model) {
        model.addAttribute("proveedores", proveedorService.listarActivos());
        model.addAttribute("categorias", categoriaOrdenService.listarActivas());
        model.addAttribute("rubros", rubroService.listarActivos());
    }
}
