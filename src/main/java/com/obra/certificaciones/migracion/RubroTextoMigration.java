package com.obra.certificaciones.migracion;

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
            vincularRubroExistente(item);
        }
        limpiarRubrosTexto();
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

    private void vincularRubroExistente(ItemRubroTexto item) {
        rubroRepository.findByNombreIgnoreCaseOrderByActivoDescIdAsc(item.rubro().trim()).stream().findFirst()
                .ifPresent(rubro -> jdbcTemplate.update(
                        "update item_orden_compra set rubro_entidad_id = ? where id = ?",
                        rubro.getId(),
                        item.id()
                ));
    }

    private void limpiarRubrosTexto() {
        try {
            jdbcTemplate.update("update item_orden_compra set rubro = null where rubro is not null");
        } catch (BadSqlGrammarException ignored) {
            // La columna puede no existir en bases nuevas.
        }
    }

    private record ItemRubroTexto(Long id, String rubro) {
    }
}
