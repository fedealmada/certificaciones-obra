package com.obra.certificaciones.oc.service;

import com.obra.certificaciones.oc.dto.ImportacionOrdenCompraItemForm;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportacionOrdenCompraServiceTest {

    @Test
    void parseaItemsDeOrdenSimendeConNumerosPartidos() {
        ImportacionOrdenCompraService service = new ImportacionOrdenCompraService(null, null, null, null, null);
        String texto = """
                O R D E N D E C O M P R A - S I M E N D E II
                Nº Orden : 00000-00000457 Fecha Emision : 03/07/2026
                Proveedor : 00127 MURIEL MIGUEL JESUS
                ALB/MO ALBAÑILERIA MANO DE OBRA 100.00 119372.40 0.00 11,937,240.00
                Colocacion de Carpeta RDC-300 en los Pisos: P14 -
                P13 - P12 - P11 - P10 - P9. Incluye regleo y acaba
                dos finales m2 1.705,32 7 .000,00 $11.937.24
                0,00
                ALB/MO ALBAÑILERIA MANO DE OBRA 100.00 24000.00 0.00 2,400,000.00
                Amure de caños de electricidad en sobre losa en lo
                s Pisos: P14 - P13 - P12 - P11 - P10 - P9. Incluye
                las hiladas de ladrillos comu nes en fosa de ascen
                sores y plenos. Piso 6,00 400.000,00 $2.40
                0.000,00
                ALB/MO ALBAÑILERIA MANO DE OBRA 100.00 36000.00 0.00 3,600,000.00
                Tapado de pases de losa (Exist entes-No utilizados)
                y de Caños 110, en Cocinas y Habitaciones, Dpto C
                yD, para nicho Lavarropa, en p isos: P14 - P13 - P1
                2 - P11 - P10 - P9 Pases 120 ,00 30.000,00 $
                3.600.000,00
                ALB/MO ALBAÑILERIA MANO DE OBRA 100.00 12000.00 0.00 1,200,000.00
                Tapar cañeria de electricidad en paredes de los Dp
                tos Dpto 8,00 150.000,00 $1.200.000,00
                CCSIMENDE
                """;

        @SuppressWarnings("unchecked")
        List<ImportacionOrdenCompraItemForm> items = (List<ImportacionOrdenCompraItemForm>) ReflectionTestUtils
                .invokeMethod(service, "parsearItems", texto + "\n" + texto, "457");

        assertThat(items).hasSize(4);
        assertThat(items).extracting(ImportacionOrdenCompraItemForm::getItem)
                .containsExactly("457.1", "457.2", "457.3", "457.4");
        assertThat(items.get(0).getUnidad()).isEqualTo("M2");
        assertThat(items.get(1).getUnidad()).isEqualTo("Piso");
        assertThat(items.get(2).getUnidad()).isEqualTo("Pases");
        assertThat(items.get(0).getImporte()).isEqualByComparingTo("11937240.00");
        assertThat(items.get(1).getImporte()).isEqualByComparingTo("2400000.00");
    }

    @Test
    void parseaItemsMaterialesYServiciosEnUnaLineaSinDuplicarPaginas() {
        ImportacionOrdenCompraService service = new ImportacionOrdenCompraService(null, null, null, null, null);
        String texto = """
                ALB/ARE Arena fina 8.00 32956.20 0.00 263,649.60
                ALB/CEM/BOL/25 Cemento x 25 kg BOLSA 80.00 5009.00 0.00 400,720.00
                ALB/INTOMAP/3/1 INTOMAP 3 EN 1 AR BAGS X 25 KG 1120.00 6432.96 0.00 7,204,915.20
                LG/SRV/INT Serivicio de internet 1.00 23,975.00 0.00 23,975.00
                SERVICIO DE INTERNET 500 MEGAS + LINEA MOVIL
                CCSIMENDE
                ALB/ARE Arena fina 8.00 32956.20 0.00 263,649.60
                ALB/CEM/BOL/25 Cemento x 25 kg BOLSA 80.00 5009.00 0.00 400,720.00
                ALB/INTOMAP/3/1 INTOMAP 3 EN 1 AR BAGS X 25 KG 1120.00 6432.96 0.00 7,204,915.20
                LG/SRV/INT Serivicio de internet 1.00 23,975.00 0.00 23,975.00
                SERVICIO DE INTERNET 500 MEGAS + LINEA MOVIL
                CCSIMENDE
                """;

        @SuppressWarnings("unchecked")
        List<ImportacionOrdenCompraItemForm> items = (List<ImportacionOrdenCompraItemForm>) ReflectionTestUtils
                .invokeMethod(service, "parsearItems", texto, "14");

        assertThat(items).hasSize(4);
        assertThat(items).extracting(ImportacionOrdenCompraItemForm::getItem)
                .containsExactly("14.1", "14.2", "14.3", "14.4");
        assertThat(items.get(2).getCantidad()).isEqualByComparingTo("1120.00");
        assertThat(items.get(2).getImporte()).isEqualByComparingTo("7204915.20");
        assertThat(items.get(3).getDetalle()).contains("SERVICIO DE INTERNET");
    }
}
