package com.obra.certificaciones.migracion;

import com.obra.certificaciones.proveedor.entity.Proveedor;
import com.obra.certificaciones.proveedor.repository.ProveedorRepository;
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
public class ProveedorTextoMigration {

    private final JdbcTemplate jdbcTemplate;
    private final ProveedorRepository proveedorRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrarProveedoresTexto() {
        List<OrdenProveedorTexto> ordenes = buscarOrdenesPendientes();
        for (OrdenProveedorTexto orden : ordenes) {
            if (!StringUtils.hasText(orden.proveedor())) {
                continue;
            }
            Proveedor proveedor = obtenerOCrearProveedor(orden.proveedor().trim());
            jdbcTemplate.update(
                    "update orden_compra set proveedor_entidad_id = ? where id = ?",
                    proveedor.getId(),
                    orden.id()
            );
        }
    }

    private List<OrdenProveedorTexto> buscarOrdenesPendientes() {
        try {
            return jdbcTemplate.query(
                    """
                    select id, proveedor
                    from orden_compra
                    where proveedor_entidad_id is null
                      and proveedor is not null
                      and trim(proveedor) <> ''
                    """,
                    (rs, rowNum) -> new OrdenProveedorTexto(rs.getLong("id"), rs.getString("proveedor"))
            );
        } catch (BadSqlGrammarException ex) {
            return List.of();
        }
    }

    private Proveedor obtenerOCrearProveedor(String nombre) {
        return proveedorRepository.findByNombreIgnoreCaseOrderByActivoDescIdAsc(nombre).stream().findFirst()
                .orElseGet(() -> {
                    Proveedor proveedor = new Proveedor();
                    proveedor.setNombre(nombre);
                    proveedor.setActivo(true);
                    return proveedorRepository.save(proveedor);
                });
    }

    private record OrdenProveedorTexto(Long id, String proveedor) {
    }
}
