package com.obra.certificaciones.migracion;

import com.obra.certificaciones.material.catalogo.entity.MaterialCatalogo;
import com.obra.certificaciones.material.catalogo.repository.MaterialCatalogoRepository;
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
public class MaterialCatalogoMigration {

    private final JdbcTemplate jdbcTemplate;
    private final MaterialCatalogoRepository materialCatalogoRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrarMaterialesTexto() {
        List<ItemMaterialTexto> items = buscarItemsPendientes();
        for (ItemMaterialTexto item : items) {
            if (!StringUtils.hasText(item.detalle())) {
                continue;
            }
            MaterialCatalogo material = obtenerOCrearMaterial(item.detalle().trim(), item.unidad());
            jdbcTemplate.update(
                    "update item_orden_compra set material_catalogo_id = ? where id = ?",
                    material.getId(),
                    item.id()
            );
        }
    }

    private List<ItemMaterialTexto> buscarItemsPendientes() {
        try {
            return jdbcTemplate.query(
                    """
                    select id, detalle, unidad
                    from item_orden_compra
                    where categoria = 'MATERIAL'
                      and material_catalogo_id is null
                      and detalle is not null
                      and trim(detalle) <> ''
                    """,
                    (rs, rowNum) -> new ItemMaterialTexto(
                            rs.getLong("id"),
                            rs.getString("detalle"),
                            rs.getString("unidad")
                    )
            );
        } catch (BadSqlGrammarException ex) {
            return List.of();
        }
    }

    private MaterialCatalogo obtenerOCrearMaterial(String nombre, String unidad) {
        return materialCatalogoRepository.findByNombreIgnoreCaseOrderByActivoDescIdAsc(nombre).stream().findFirst()
                .orElseGet(() -> {
                    MaterialCatalogo material = new MaterialCatalogo();
                    material.setNombre(nombre);
                    material.setUnidad(unidad);
                    material.setActivo(true);
                    return materialCatalogoRepository.save(material);
                });
    }

    private record ItemMaterialTexto(Long id, String detalle, String unidad) {
    }
}
