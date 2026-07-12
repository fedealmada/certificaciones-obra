package com.obra.certificaciones.configuracion.service;

import com.obra.certificaciones.configuracion.dto.ModuloSistema;
import com.obra.certificaciones.configuracion.entity.ConfiguracionSistema;
import com.obra.certificaciones.configuracion.repository.ConfiguracionSistemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConfiguracionSistemaService {
    public static final String ALERTAS_DASHBOARD = "alertas.dashboard";
    public static final String ALERTAS_OC = "alertas.oc";

    private final ConfiguracionSistemaRepository repository;

    public List<ModuloSistema> modulos() {
        return List.of(
                new ModuloSistema("modulo.dashboard", "Dashboard", "Indicadores, graficos y resumen de obra.", "/dashboard", "bi-speedometer2", "blue"),
                new ModuloSistema("modulo.oc", "Ordenes de compra", "Crear, revisar y certificar OC.", "/oc", "bi-receipt", "yellow"),
                new ModuloSistema("modulo.importarOc", "Importar OC", "Cargar ordenes desde PDF o texto.", "/oc/importar", "bi-file-earmark-arrow-up", "blue"),
                new ModuloSistema("modulo.items", "Items", "Buscador general de items de OC.", "/items", "bi-list-task", "green"),
                new ModuloSistema("modulo.importarCertificados", "Importar certificados", "Pegar planillas y cargar avances automaticamente.", "/certificaciones/importar", "bi-clipboard-check", "blue"),
                new ModuloSistema("modulo.itemizado", "Itemizado", "Arbol de rubros e itemizado general.", "/itemizado", "bi-diagram-3", "green"),
                new ModuloSistema("modulo.asistencia", "Asistencia", "Control diario de personal, empresas y horas.", "/asistencia", "bi-person-check", "green"),
                new ModuloSistema("modulo.proveedores", "Proveedores", "Contratistas, contactos y OC asociadas.", "/proveedores", "bi-person-lines-fill", "yellow"),
                new ModuloSistema("modulo.categorias", "Categorias", "Categorias manuales para compras e items.", "/categorias", "bi-tags", "blue"),
                new ModuloSistema("modulo.rubros", "Rubros", "Estructura jerarquica de rubros de obra.", "/rubros", "bi-folder2-open", "green"),
                new ModuloSistema("modulo.materiales", "Entregas y viajes", "Entregas, viajes y seguimiento por unidades.", "/materiales", "bi-truck", "yellow"),
                new ModuloSistema("modulo.deposito", "Deposito", "Stock de panol, movimientos y devoluciones.", "/deposito", "bi-boxes", "green"),
                new ModuloSistema("modulo.catalogo", "Catalogo", "Catalogo maestro de materiales.", "/catalogo-materiales", "bi-archive", "blue"),
                new ModuloSistema("modulo.reportes", "Reportes", "Gastos, evolucion mensual y analisis.", "/reportes", "bi-graph-up-arrow", "green")
        );
    }

    @Transactional(readOnly = true)
    public boolean activo(String clave) {
        return repository.findById(clave).map(ConfiguracionSistema::isActivo).orElse(true);
    }

    @Transactional(readOnly = true)
    public List<ModuloSistema> modulosActivos() {
        Map<String, Boolean> valores = valores();
        return modulos().stream().filter(modulo -> valores.getOrDefault(modulo.clave(), true)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> valores() {
        Map<String, Boolean> valores = new LinkedHashMap<>();
        modulos().forEach(modulo -> valores.put(modulo.clave(), true));
        valores.put(ALERTAS_DASHBOARD, true);
        valores.put(ALERTAS_OC, true);
        repository.findAll().forEach(configuracion -> valores.put(configuracion.getClave(), configuracion.isActivo()));
        return valores;
    }

    @Transactional
    public void guardar(Map<String, Boolean> valores) {
        valores.forEach((clave, activo) -> {
            ConfiguracionSistema configuracion = repository.findById(clave).orElseGet(() -> {
                ConfiguracionSistema nueva = new ConfiguracionSistema();
                nueva.setClave(clave);
                return nueva;
            });
            configuracion.setActivo(activo);
            repository.save(configuracion);
        });
    }
}
