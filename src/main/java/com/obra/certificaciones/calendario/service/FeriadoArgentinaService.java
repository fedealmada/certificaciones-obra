package com.obra.certificaciones.calendario.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FeriadoArgentinaService {
    private static final Map<LocalDate, String> FERIADOS = Map.ofEntries(
            Map.entry(LocalDate.of(2025, 1, 1), "Anio Nuevo"),
            Map.entry(LocalDate.of(2025, 3, 3), "Carnaval"),
            Map.entry(LocalDate.of(2025, 3, 4), "Carnaval"),
            Map.entry(LocalDate.of(2025, 3, 24), "Dia Nacional de la Memoria por la Verdad y la Justicia"),
            Map.entry(LocalDate.of(2025, 4, 2), "Dia del Veterano y de los Caidos en la Guerra de Malvinas"),
            Map.entry(LocalDate.of(2025, 4, 18), "Viernes Santo"),
            Map.entry(LocalDate.of(2025, 5, 1), "Dia del Trabajador"),
            Map.entry(LocalDate.of(2025, 5, 2), "Puente turistico no laborable"),
            Map.entry(LocalDate.of(2025, 5, 25), "Dia de la Revolucion de Mayo"),
            Map.entry(LocalDate.of(2025, 6, 16), "Paso a la Inmortalidad del Gral. Martin Miguel de Guemes"),
            Map.entry(LocalDate.of(2025, 6, 20), "Paso a la Inmortalidad del Gral. Manuel Belgrano"),
            Map.entry(LocalDate.of(2025, 7, 9), "Dia de la Independencia"),
            Map.entry(LocalDate.of(2025, 8, 15), "Puente turistico no laborable"),
            Map.entry(LocalDate.of(2025, 8, 17), "Paso a la Inmortalidad del Gral. Jose de San Martin"),
            Map.entry(LocalDate.of(2025, 10, 10), "Puente turistico no laborable"),
            Map.entry(LocalDate.of(2025, 10, 12), "Dia del Respeto a la Diversidad Cultural"),
            Map.entry(LocalDate.of(2025, 11, 21), "Puente turistico no laborable"),
            Map.entry(LocalDate.of(2025, 11, 24), "Dia de la Soberania Nacional"),
            Map.entry(LocalDate.of(2025, 12, 8), "Dia de la Inmaculada Concepcion de Maria"),
            Map.entry(LocalDate.of(2025, 12, 25), "Navidad"),
            Map.entry(LocalDate.of(2026, 1, 1), "Anio Nuevo"),
            Map.entry(LocalDate.of(2026, 2, 16), "Carnaval"),
            Map.entry(LocalDate.of(2026, 2, 17), "Carnaval"),
            Map.entry(LocalDate.of(2026, 3, 23), "Puente turistico no laborable"),
            Map.entry(LocalDate.of(2026, 3, 24), "Dia Nacional de la Memoria por la Verdad y la Justicia"),
            Map.entry(LocalDate.of(2026, 4, 2), "Dia del Veterano y de los Caidos en la Guerra de Malvinas"),
            Map.entry(LocalDate.of(2026, 4, 3), "Viernes Santo"),
            Map.entry(LocalDate.of(2026, 5, 1), "Dia del Trabajador"),
            Map.entry(LocalDate.of(2026, 5, 25), "Dia de la Revolucion de Mayo"),
            Map.entry(LocalDate.of(2026, 6, 15), "Paso a la Inmortalidad del Gral. Martin Miguel de Guemes"),
            Map.entry(LocalDate.of(2026, 6, 20), "Paso a la Inmortalidad del Gral. Manuel Belgrano"),
            Map.entry(LocalDate.of(2026, 7, 9), "Dia de la Independencia"),
            Map.entry(LocalDate.of(2026, 7, 10), "Puente turistico no laborable"),
            Map.entry(LocalDate.of(2026, 8, 17), "Paso a la Inmortalidad del Gral. Jose de San Martin"),
            Map.entry(LocalDate.of(2026, 10, 12), "Dia del Respeto a la Diversidad Cultural"),
            Map.entry(LocalDate.of(2026, 11, 23), "Dia de la Soberania Nacional"),
            Map.entry(LocalDate.of(2026, 12, 7), "Puente turistico no laborable"),
            Map.entry(LocalDate.of(2026, 12, 8), "Dia de la Inmaculada Concepcion de Maria"),
            Map.entry(LocalDate.of(2026, 12, 25), "Navidad")
    );

    public Map<LocalDate, String> feriadosEntre(LocalDate desde, LocalDate hasta) {
        Map<LocalDate, String> resultado = new LinkedHashMap<>();
        FERIADOS.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(desde) && !entry.getKey().isAfter(hasta))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> resultado.put(entry.getKey(), entry.getValue()));
        return resultado;
    }
}
