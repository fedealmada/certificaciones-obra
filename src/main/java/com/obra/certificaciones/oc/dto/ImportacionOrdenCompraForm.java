package com.obra.certificaciones.oc.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

@Getter
@Setter
public class ImportacionOrdenCompraForm {
    private boolean seleccionado = true;
    private String archivoNombre;
    private String numero;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fecha = LocalDate.now();
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaVigencia;
    private Long proveedorId;
    private String proveedorSugerido;
    private String proveedorNuevo;
    private Long categoriaId;
    private Long rubroId;
    private String observacion;
    private String textoExtraido;
    private boolean ordenExistente;
    private Long ordenExistenteId;
    private String ordenExistenteMensaje;
    private List<String> diferencias = new ArrayList<>();
    private List<ImportacionOrdenCompraItemForm> items = new ArrayList<>();

    public BigDecimal getTotalImportado() {
        return items.stream()
                .map(item -> item.getImporte() == null ? BigDecimal.ZERO : item.getImporte())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
