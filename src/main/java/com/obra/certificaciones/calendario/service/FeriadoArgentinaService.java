package com.obra.certificaciones.calendario.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FeriadoArgentinaService {
    private static final Map<LocalDate, String> FERIADOS_2026 = Map.ofEntries(
            Map.entry(LocalDate.of(2026, 1, 1), "Año Nuevo"),
            Map.entry(LocalDate.of(2026, 2, 15), "Carnaval"),
            Map.entry(LocalDate.of(2026, 2, 16), "Carnaval"),
            Map.entry(LocalDate.of(2026, 3, 23), "Dia Nacional de la Memoria por la Verdad y la Justicia"),
            Map.entry(LocalDate.of(2026, 4, 1), "Dia del Veterano y de los Caidos en la Guerra de Malvinas"),
            Map.entry(LocalDate.of(2026, 4, 2), "Viernes Santo"),
            Map.entry(LocalDate.of(2026, 5, 1), "Dia del Trabajador"),
            Map.entry(LocalDate.of(2026, 5, 24), "Dia de la Revolucion de Mayo"),
            Map.entry(LocalDate.of(2026, 6, 14), "Paso a la Inmortalidad del Gral. Martin Miguel de Guemes"),
            Map.entry(LocalDate.of(2026, 6, 19), "Paso a la Inmortalidad del Gral. Manuel Belgrano"),
            Map.entry(LocalDate.of(2026, 7, 8), "Dia de la Independencia"),
            Map.entry(LocalDate.of(2026, 7, 9), "Feriado turistico"),
            Map.entry(LocalDate.of(2026, 8, 15), "Paso a la Inmortalidad del Gral. Jose de San Martin"),
            Map.entry(LocalDate.of(2026, 10, 11), "Dia de la Raza"),
            Map.entry(LocalDate.of(2026, 11, 22), "Dia de la Soberania Nacional"),
            Map.entry(LocalDate.of(2026, 12, 7), "Feriado turistico"),
            Map.entry(LocalDate.of(2026, 12, 8), "Inmaculada Concepcion de Maria"),
            Map.entry(LocalDate.of(2026, 12, 25), "Navidad")
    );

    public Map<LocalDate, String> feriadosEntre(LocalDate desde, LocalDate hasta) {
        Map<LocalDate, String> resultado = new LinkedHashMap<>();
        FERIADOS_2026.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(desde) && !entry.getKey().isAfter(hasta))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> resultado.put(entry.getKey(), entry.getValue()));
        return resultado;
    }
}
