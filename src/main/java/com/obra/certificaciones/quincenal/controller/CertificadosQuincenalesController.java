package com.obra.certificaciones.quincenal.controller;

import com.obra.certificaciones.obra.service.ObraService;
import com.obra.certificaciones.quincenal.dto.ResumenQuincenal;
import com.obra.certificaciones.quincenal.service.CertificadosQuincenalesService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/certificados-quincenales")
@RequiredArgsConstructor
public class CertificadosQuincenalesController {
    private final CertificadosQuincenalesService service;
    private final ObraService obraService;
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es-AR"));

    @GetMapping
    public String listar(@RequestParam(required = false) String mes,
                         @RequestParam(required = false) Long proveedorId,
                         @RequestParam(defaultValue = "0") int quincena, Model model, HttpSession session) {
        validarQuincena(quincena);
        Long obraId = obraService.obraActiva(session).getId();
        List<YearMonth> meses = service.mesesDisponibles(obraId);
        YearMonth periodo = periodo(mes, meses);
        var resumen = service.resumir(obraId, periodo, proveedorId, quincena);
        model.addAttribute("resumen", resumen);
        model.addAttribute("proveedorAusente", proveedorId != null && resumen.opcionesProveedores().stream().noneMatch(p -> p.id().equals(proveedorId)));
        model.addAttribute("mes", periodo.toString());
        model.addAttribute("mesTitulo", periodo.format(MES));
        model.addAttribute("mesAnterior", periodo.minusMonths(1).toString());
        model.addAttribute("mesSiguiente", periodo.plusMonths(1).toString());
        model.addAttribute("meses", meses.stream().map(m -> new MesOpcion(m.toString(), m.format(MES))).toList());
        model.addAttribute("ultimoDia", periodo.lengthOfMonth());
        model.addAttribute("emisionDesde", CertificadosQuincenalesService.inicioEmisiones(periodo));
        model.addAttribute("emisionHasta", CertificadosQuincenalesService.finEmisiones(periodo));
        model.addAttribute("emisionSegundaDesde", CertificadosQuincenalesService.inicioSegundaQuincena(periodo));
        model.addAttribute("emisionPrimeraHasta", CertificadosQuincenalesService.inicioSegundaQuincena(periodo).minusDays(1));
        model.addAttribute("proveedorId", proveedorId);
        model.addAttribute("quincena", quincena);
        return "quincenal/lista";
    }

    public record MesOpcion(String valor, String etiqueta) { }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(@RequestParam(required = false) String mes,
                                          @RequestParam(required = false) Long proveedorId,
                                          @RequestParam(defaultValue = "0") int quincena, HttpSession session) {
        validarQuincena(quincena);
        var obra = obraService.obraActiva(session);
        YearMonth periodo = periodo(mes, service.mesesDisponibles(obra.getId()));
        ResumenQuincenal resumen = service.resumir(obra.getId(), periodo, proveedorId, quincena);
        StringBuilder csv = new StringBuilder("\uFEFF");
        fila(csv, "Obra", "Mes de trabajo", "Quincena", "Proveedor", "OC", "Certificado", "Fecha de emision", "Codigo tarea", "Tarea", "Unidad", "Avance actual (%)", "Importe certificado", "Tipo", "Cantidad actual");
        for (var c : resumen.certificados()) {
            for (var t : c.tareas()) {
                fila(csv, obra.getNombre(), periodo.toString(), String.valueOf(c.quincena()), c.proveedor(), c.ordenNumero(),
                        String.valueOf(c.numero()), c.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), t.codigo(),
                        t.descripcion(), t.unidad(), decimal(t.porcentaje()), decimal(t.importe()), t.tipo(), t.cantidad() == null ? "" : decimal(t.cantidad()));
            }
            BigDecimal diferenciaBruto = c.liquidacion().bruto().subtract(c.tareas().stream().map(ResumenQuincenal.Tarea::importe).reduce(BigDecimal.ZERO, BigDecimal::add));
            if (diferenciaBruto.signum() != 0) {
                fila(csv, obra.getNombre(), periodo.toString(), String.valueOf(c.quincena()), c.proveedor(), c.ordenNumero(),
                        String.valueOf(c.numero()), c.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), "BRUTO ORIGINAL",
                        "Diferencia entre bruto original informado y suma de tareas", "", "", decimal(diferenciaBruto), "Conciliacion de bruto", "");
            }
            if (c.liquidacion().retencion().signum() != 0) {
                fila(csv, obra.getNombre(), periodo.toString(), String.valueOf(c.quincena()), c.proveedor(), c.ordenNumero(),
                        String.valueOf(c.numero()), c.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), "FONDO REPARO",
                        "Retencion sobre trabajos certificados", "", decimal(c.liquidacion().porcentajeFondo()),
                        decimal(c.liquidacion().retencion().negate()), "Retencion", "");
            }
            if (c.liquidacion().cac().signum() != 0 || c.liquidacion().pendiente() || !c.liquidacion().estadoCac().equals("Sin CAC")) {
                fila(csv, obra.getNombre(), periodo.toString(), String.valueOf(c.quincena()), c.proveedor(), c.ordenNumero(),
                        String.valueOf(c.numero()), c.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), "CAC",
                        c.liquidacion().estadoCac(), "", decimal(c.liquidacion().porcentajeCac()), decimal(c.liquidacion().cac()), "Ajuste CAC", "");
            }
        }
        fila(csv, "TOTAL", "", "", "", "", "", "", "", "", "", "", decimal(resumen.totalVisible()), "", "");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=certificados-quincenales-" + periodo + ".csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private YearMonth periodo(String mes, List<YearMonth> disponibles) {
        if (mes == null || mes.isBlank()) return disponibles.isEmpty()
                ? YearMonth.now(ZoneId.of("America/Argentina/Buenos_Aires")) : disponibles.getFirst();
        try {
            YearMonth value = YearMonth.parse(mes);
            if (value.getYear() < 1900 || value.getYear() > 9998) throw new IllegalArgumentException();
            return value;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mes debe tener formato AAAA-MM.");
        }
    }

    private void validarQuincena(int quincena) {
        if (quincena < 0 || quincena > 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quincena invalida.");
    }

    private static String decimal(BigDecimal valor) { return valor.toPlainString().replace('.', ','); }

    private static void fila(StringBuilder csv, String... valores) {
        for (int i = 0; i < valores.length; i++) {
            if (i > 0) csv.append(';');
            String valor = valores[i] == null ? "" : valores[i];
            // Evitar que el texto de un proveedor o tarea se interprete como formula al abrirlo en Excel.
            if (!valor.matches("-?\\d+(,\\d+)?") && !valor.stripLeading().isEmpty() && "=+@-".indexOf(valor.stripLeading().charAt(0)) >= 0) valor = "'" + valor;
            csv.append('"').append(valor.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }
}
