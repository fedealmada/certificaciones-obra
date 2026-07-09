package com.obra.certificaciones.oc.dto;

import com.obra.certificaciones.oc.entity.ItemOrdenCompra;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class OrdenCompraForm {
    private Long id;
    private String numero;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fecha = LocalDate.now();
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaVigencia;
    private Long proveedorId;
    private String observacion;
    private List<ItemOrdenCompra> items = new ArrayList<>();
}
