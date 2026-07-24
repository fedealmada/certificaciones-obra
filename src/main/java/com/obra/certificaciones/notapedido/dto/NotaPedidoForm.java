package com.obra.certificaciones.notapedido.dto;

import com.obra.certificaciones.notapedido.entity.EstadoNotaPedido;
import com.obra.certificaciones.notapedido.entity.ItemNotaPedido;
import com.obra.certificaciones.notapedido.entity.PrioridadNotaPedido;
import com.obra.certificaciones.notapedido.entity.TipoNotaPedido;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class NotaPedidoForm {
    private Long id;
    private String numero;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fecha = LocalDate.now();
    private String solicitante;
    private boolean comparativa;
    private boolean verStock;
    private TipoNotaPedido tipo = TipoNotaPedido.MATERIALES;
    private EstadoNotaPedido estado = EstadoNotaPedido.BORRADOR;
    private PrioridadNotaPedido prioridad = PrioridadNotaPedido.NORMAL;
    private Long proveedorId;
    private Long ordenCompraId;
    private String observaciones;
    private List<ItemNotaPedido> items = new ArrayList<>();
}
