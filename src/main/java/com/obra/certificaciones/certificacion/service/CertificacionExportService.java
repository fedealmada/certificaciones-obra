package com.obra.certificaciones.certificacion.service;

import com.obra.certificaciones.certificacion.dto.ItemCertificacionCalculado;
import com.obra.certificaciones.certificacion.dto.ResumenCertificacion;
import com.obra.certificaciones.certificacion.entity.Certificacion;
import com.obra.certificaciones.oc.entity.OrdenCompra;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CertificacionExportService {

    private static final DateTimeFormatter FECHA_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CertificacionCalculoService calculoService;
    private final AjustesCertificacionService ajustesService;

    @Transactional(readOnly = true)
    public byte[] exportarCsv(OrdenCompra orden, Certificacion certificacion) {
        List<ItemCertificacionCalculado> items = calculoService.calcularDetalle(certificacion);
        ResumenCertificacion resumen = calculoService.calcularResumenHasta(certificacion, orden.getItems(), items);
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("Certificado;").append(valor(certificacion.getNumero())).append('\n');
        csv.append("OC;").append(valor(orden.getNumero())).append('\n');
        csv.append("Proveedor;").append(valor(orden.getProveedorEntidad() == null ? null : orden.getProveedorEntidad().getNombre())).append('\n');
        csv.append("Fecha;").append(valor(certificacion.getFecha().format(FECHA_FORMATTER))).append('\n');
        csv.append("Observacion;").append(valor(certificacion.getObservacion())).append('\n');
        csv.append('\n');
        csv.append("Total contratado;").append(numero(resumen.totalContratado())).append('\n');
        csv.append("Certificado actual;").append(numero(resumen.totalActual())).append('\n');
        var liquidacion = ajustesService.liquidar(certificacion);
        csv.append("Bruto de liquidacion;").append(numero(liquidacion.bruto())).append('\n');
        csv.append("Origen del bruto;").append(valor(certificacion.getBrutoInformado() == null ? "Suma de tareas certificadas" : "Bruto original informado; avances conservados")).append('\n');
        csv.append("Fondo de reparo (%);").append(numero(liquidacion.porcentajeFondo())).append('\n');
        csv.append("Retencion fondo de reparo;").append(numero(liquidacion.retencion().negate())).append('\n');
        csv.append("Coeficiente CAC (%);").append(numero(liquidacion.porcentajeCac())).append('\n');
        csv.append("Ajuste CAC;").append(numero(liquidacion.cac())).append('\n');
        csv.append("Estado CAC;").append(valor(liquidacion.estadoCac())).append('\n');
        csv.append("Neto a pagar").append(liquidacion.pendiente() ? " (provisional);" : ";").append(numero(liquidacion.neto())).append('\n');
        csv.append("Anterior a este certificado;").append(numero(resumen.totalAnterior())).append('\n');
        csv.append("Acumulado hasta este certificado;").append(numero(resumen.totalAcumulado())).append('\n');
        csv.append("Saldo a esa fecha;").append(numero(resumen.saldoPendiente())).append('\n');
        csv.append('\n');
        csv.append("Modalidad;").append(certificacion.isPorCantidad() ? "Cantidad" : "Porcentaje").append('\n');
        csv.append("Item;Detalle;Importe;% anterior;% actual;% acumulado;Monto actual;Unidad;Cantidad contratada;Cantidad anterior;Cantidad actual;Cantidad acumulada\n");

        for (ItemCertificacionCalculado item : items) {
            csv.append(valor(item.itemOrdenCompra().getItem())).append(';')
                    .append(valor(item.itemOrdenCompra().getDetalle())).append(';')
                    .append(numero(item.itemOrdenCompra().getImporte())).append(';')
                    .append(numero(item.porcentajeAnterior())).append(';')
                    .append(numero(item.porcentajeActual())).append(';')
                    .append(numero(item.porcentajeAcumulado())).append(';')
                    .append(numero(item.montoActual())).append(';')
                    .append(valor(item.itemOrdenCompra().getUnidad())).append(';')
                    .append(numero(item.itemOrdenCompra().getCantidad())).append(';')
                    .append(certificacion.isPorCantidad() ? numero(item.cantidadAnterior()) : "").append(';')
                    .append(certificacion.isPorCantidad() ? numero(item.cantidadActual()) : "").append(';')
                    .append(certificacion.isPorCantidad() ? numero(item.cantidadAcumulada()) : "").append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public String nombreArchivo(OrdenCompra orden, Certificacion certificacion) {
        return "certificado-oc-" + limpiar(orden.getNumero()) + "-n" + certificacion.getNumero() + ".csv";
    }

    private String valor(Object valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor.toString().replace("\"", "\"\"");
        return "\"" + texto + "\"";
    }

    private String numero(BigDecimal valor) {
        return valor == null ? "0.00" : valor.toPlainString();
    }

    private String limpiar(String valor) {
        return valor == null ? "sin-numero" : valor.replaceAll("[^a-zA-Z0-9-_]", "-");
    }
}
