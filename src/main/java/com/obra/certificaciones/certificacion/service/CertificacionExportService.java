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

    @Transactional(readOnly = true)
    public byte[] exportarCsv(OrdenCompra orden, Certificacion certificacion) {
        List<ItemCertificacionCalculado> items = calculoService.calcularDetalle(certificacion);
        ResumenCertificacion resumen = calculoService.calcularResumen(orden.getId(), orden.getItems(), items);
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
        csv.append("Acumulado;").append(numero(resumen.totalAcumulado())).append('\n');
        csv.append("Saldo pendiente;").append(numero(resumen.saldoPendiente())).append('\n');
        csv.append('\n');
        csv.append("Item;Detalle;Importe;% anterior;% actual;% acumulado;Monto actual\n");

        for (ItemCertificacionCalculado item : items) {
            csv.append(valor(item.itemOrdenCompra().getItem())).append(';')
                    .append(valor(item.itemOrdenCompra().getDetalle())).append(';')
                    .append(numero(item.itemOrdenCompra().getImporte())).append(';')
                    .append(numero(item.porcentajeAnterior())).append(';')
                    .append(numero(item.porcentajeActual())).append(';')
                    .append(numero(item.porcentajeAcumulado())).append(';')
                    .append(numero(item.montoActual())).append('\n');
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
