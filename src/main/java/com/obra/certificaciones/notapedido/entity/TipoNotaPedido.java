package com.obra.certificaciones.notapedido.entity;

public enum TipoNotaPedido {
    MATERIALES("Pedido de materiales", "Descripcion, unidad y cantidad", false),
    MANO_OBRA_CON_PRECIO("Presupuesto de mano de obra", "Tareas con precio presupuestado", true),
    PRESUPUESTO_SIN_PRECIO("Pedido de presupuesto", "Tareas para consultar precio", false);

    private final String descripcion;
    private final String ayuda;
    private final boolean usaPrecio;

    TipoNotaPedido(String descripcion, String ayuda, boolean usaPrecio) {
        this.descripcion = descripcion;
        this.ayuda = ayuda;
        this.usaPrecio = usaPrecio;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getAyuda() {
        return ayuda;
    }

    public boolean usaPrecio() {
        return usaPrecio;
    }
}
