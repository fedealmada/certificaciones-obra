package com.obra.certificaciones.certificacion;

import com.obra.certificaciones.certificacion.entity.Certificacion;
import com.obra.certificaciones.certificacion.entity.ItemCertificacion;
import com.obra.certificaciones.certificacion.repository.ItemCertificacionRepository;
import com.obra.certificaciones.certificacion.service.CertificacionCalculoService;
import com.obra.certificaciones.oc.entity.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CertificacionResumenHistoricoTest {
    private final ItemCertificacionRepository repository = mock(ItemCertificacionRepository.class);
    private final CertificacionCalculoService service = new CertificacionCalculoService(repository);

    @Test void oc317Certificado7Acumula4047AunqueLaOrdenAhoraEsteCompleta() {
        var oc = new OrdenCompra(); oc.setId(128L);
        var tarea = tarea(1L, "3840000");
        String[] avances = {"8.33", "7.75", "4.46", "5", "6.25", "6.93", "1.75", "6.83", "10.07", "12.18", "1.75", "15.16", "1.85", "7.72", "3.97"};
        var historial = new ArrayList<ItemCertificacion>();
        for (int i = 0; i < avances.length; i++) {
            var c = certificado(oc, i + 1L, LocalDate.of(2025, 8, 5).plusDays(i * 15L));
            historial.add(avance(c, tarea, avances[i]));
        }
        when(repository.findByCertificacionOrdenCompraIdOrderByCertificacionFechaAscCertificacionIdAsc(128L)).thenReturn(historial);
        var c = historial.get(6).getCertificacion();
        var resumen = service.calcularResumenHasta(c, List.of(tarea), service.calcularDetalle(c));
        assertThat(resumen.porcentajeAnterior()).isEqualByComparingTo("38.72");
        assertThat(resumen.porcentajeActual()).isEqualByComparingTo("1.75");
        assertThat(resumen.porcentajeAcumulado()).isEqualByComparingTo("40.47");
        assertThat(resumen.totalAcumulado()).isEqualByComparingTo("1554048");
        assertThat(resumen.saldoPendiente()).isEqualByComparingTo("2285952");
        var ultimo = historial.getLast().getCertificacion();
        assertThat(service.calcularResumenHasta(ultimo, List.of(tarea), service.calcularDetalle(ultimo)).porcentajeAcumulado()).isEqualByComparingTo("100");
        var primero = historial.getFirst().getCertificacion();
        assertThat(service.calcularResumenHasta(primero, List.of(tarea), service.calcularDetalle(primero)).totalAnterior()).isZero();
    }

    @Test void conservaItemsAnterioresSinNuevoAvanceYExcluyeCertificadosPosterioresDelMismoDia() {
        var oc = new OrdenCompra(); oc.setId(10L);
        var a = tarea(1L, "1000"); var b = tarea(2L, "1000"); var material = tarea(3L, "9000"); material.setCategoria(CategoriaItem.MATERIAL);
        var anterior = certificado(oc, 1L, LocalDate.of(2026, 1, 1));
        var actual = certificado(oc, 2L, LocalDate.of(2026, 1, 16));
        var posterior = certificado(oc, 3L, LocalDate.of(2026, 1, 16));
        when(repository.findByCertificacionOrdenCompraIdOrderByCertificacionFechaAscCertificacionIdAsc(10L)).thenReturn(List.of(
                avance(anterior, b, "50"), avance(anterior, material, "100"), avance(actual, a, "20"), avance(posterior, a, "80")));
        var resumen = service.calcularResumenHasta(actual, List.of(a, b, material), service.calcularDetalle(actual));
        assertThat(resumen.totalAnterior()).isEqualByComparingTo("500");
        assertThat(resumen.totalActual()).isEqualByComparingTo("200");
        assertThat(resumen.totalAcumulado()).isEqualByComparingTo("700");
        assertThat(resumen.porcentajeAcumulado()).isEqualByComparingTo("35");
        assertThat(resumen.saldoPendiente()).isEqualByComparingTo("1300");
    }

    private ItemOrdenCompra tarea(Long id, String importe) { var i = new ItemOrdenCompra(); i.setId(id); i.setImporte(new BigDecimal(importe)); return i; }
    private Certificacion certificado(OrdenCompra oc, Long id, LocalDate fecha) { var c = new Certificacion(); c.setId(id); c.setNumero(id.intValue()); c.setFecha(fecha); c.setOrdenCompra(oc); return c; }
    private ItemCertificacion avance(Certificacion c, ItemOrdenCompra i, String porcentaje) { var a = new ItemCertificacion(); a.setCertificacion(c); a.setItemOrdenCompra(i); a.setPorcentajeActual(new BigDecimal(porcentaje)); return a; }
}
