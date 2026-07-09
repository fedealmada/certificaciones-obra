package com.obra.certificaciones.migracion;

import com.obra.certificaciones.rubro.entity.Rubro;
import com.obra.certificaciones.rubro.repository.RubroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RubroTextoMigration {

    private final JdbcTemplate jdbcTemplate;
    private final RubroRepository rubroRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrarRubrosTexto() {
        List<ItemRubroTexto> items = buscarItemsPendientes();
        for (ItemRubroTexto item : items) {
            if (!StringUtils.hasText(item.rubro())) {
                continue;
            }
            Rubro rubro = obtenerOCrearRubro(item.rubro().trim());
            jdbcTemplate.update(
                    "update item_orden_compra set rubro_entidad_id = ? where id = ?",
                    rubro.getId(),
                    item.id()
            );
        }
    }

    private List<ItemRubroTexto> buscarItemsPendientes() {
        try {
            return jdbcTemplate.query(
                    """
                    select id, rubro
                    from item_orden_compra
                    where rubro_entidad_id is null
                      and rubro is not null
                      and trim(rubro) <> ''
                    """,
                    (rs, rowNum) -> new ItemRubroTexto(
                            rs.getLong("id"),
                            rs.getString("rubro")
                    )
            );
        } catch (BadSqlGrammarException ex) {
            return List.of();
        }
    }

    private Rubro obtenerOCrearRubro(String nombre) {
        return rubroRepository.findByNombreIgnoreCaseOrderByActivoDescIdAsc(nombre).stream().findFirst()
                .orElseGet(() -> {
                    Rubro rubro = new Rubro();
                    rubro.setNombre(nombre);
                    rubro.setActivo(true);
                    return rubroRepository.save(rubro);
                });
    }

    private record ItemRubroTexto(Long id, String rubro) {
    }
}
