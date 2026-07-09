package com.obra.certificaciones.migracion;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class CategoriaOrdenMigration {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    ApplicationRunner migrarCategoriasOrden() {
        return args -> {
            crearColumnasSiFaltan();
            Long manoObraId = asegurarCategoria("Mano de obra", "MANO_OBRA");
            Long materialId = asegurarCategoria("Material", "MATERIAL");
            Long servicioId = asegurarCategoria("Servicio", "OTRO");
            Long eppId = asegurarCategoria("EPP", "OTRO");

            actualizarItems("MANO_OBRA", "MANO_OBRA", manoObraId);
            actualizarItems("MATERIAL", "MATERIAL", materialId);
            actualizarItems("SERVICIO", "OTRO", servicioId);
            actualizarItems("EPP", "OTRO", eppId);

            jdbcTemplate.update("""
                    update item_orden_compra
                    set categoria = 'OTRO', categoria_entidad_id = ?
                    where categoria not in ('MANO_OBRA', 'MATERIAL', 'OTRO')
                    """, servicioId);
            jdbcTemplate.update("""
                    update item_orden_compra
                    set categoria_entidad_id = ?
                    where categoria = 'OTRO' and categoria_entidad_id is null
                    """, servicioId);
        };
    }

    private void crearColumnasSiFaltan() {
        ejecutarSiSePuede("alter table categoria_orden modify tipo varchar(50)");
        ejecutarSiSePuede("alter table item_orden_compra modify categoria varchar(50)");
        ejecutarSiSePuede("alter table item_orden_compra add column categoria_entidad_id bigint null");
    }

    private Long asegurarCategoria(String nombre, String tipo) {
        List<Long> existentes = jdbcTemplate.query(
                "select id from categoria_orden where lower(nombre) = lower(?) limit 1",
                (rs, rowNum) -> rs.getLong("id"),
                nombre);
        if (!existentes.isEmpty()) {
            jdbcTemplate.update("update categoria_orden set tipo = ?, activo = true where id = ?", tipo, existentes.get(0));
            return existentes.get(0);
        }
        jdbcTemplate.update("insert into categoria_orden (nombre, tipo, activo) values (?, ?, true)", nombre, tipo);
        return jdbcTemplate.queryForObject(
                "select id from categoria_orden where lower(nombre) = lower(?)",
                Long.class,
                nombre);
    }

    private void actualizarItems(String categoriaAnterior, String categoriaNueva, Long categoriaId) {
        jdbcTemplate.update("""
                update item_orden_compra
                set categoria = ?, categoria_entidad_id = ?
                where categoria = ? and categoria_entidad_id is null
                """, categoriaNueva, categoriaId, categoriaAnterior);
        jdbcTemplate.update("""
                update item_orden_compra
                set categoria = ?
                where categoria_entidad_id = ? and categoria <> ?
                """, categoriaNueva, categoriaId, categoriaNueva);
    }

    private void ejecutarSiSePuede(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (RuntimeException ignored) {
            // La tabla/columna puede no existir aun en H2 durante tests o ya estar creada en MySQL.
        }
    }
}
