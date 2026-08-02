package com.obra.certificaciones.documentacion.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentoObraTest {

    @Test
    void documentoPresentadoSinFechaQuedaSinVencimiento() {
        DocumentoObra documento = new DocumentoObra();
        documento.setActivo(true);
        documento.setPresentado(true);
        documento.setFechaVencimiento(null);

        assertThat(documento.estado()).isEqualTo(EstadoDocumentoObra.SIN_VENCIMIENTO);
        assertThat(documento.sinVencimiento()).isTrue();
        assertThat(documento.diasAlVencimiento()).isZero();
    }

    @Test
    void documentoConFechaMantieneControlDeVencimiento() {
        DocumentoObra documento = new DocumentoObra();
        documento.setActivo(true);
        documento.setPresentado(true);
        documento.setFechaVencimiento(LocalDate.now().plusDays(10));

        assertThat(documento.estado()).isEqualTo(EstadoDocumentoObra.POR_VENCER);
        assertThat(documento.sinVencimiento()).isFalse();
    }
}
