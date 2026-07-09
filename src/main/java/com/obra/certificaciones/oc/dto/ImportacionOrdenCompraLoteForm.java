package com.obra.certificaciones.oc.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ImportacionOrdenCompraLoteForm {
    private List<ImportacionOrdenCompraForm> ordenes = new ArrayList<>();
}
