# Sistema de Certificaciones de Obra

Aplicacion web Spring Boot para gestionar Ordenes de Compra y certificaciones de avance.

## Ejecutar

```bash
mvn spring-boot:run
```

Abrir http://localhost:8080

La configuracion inicial usa H2 para probar rapido. En `src/main/resources/application.properties` estan comentadas las propiedades para MySQL.

## Certificados generales de cobro

En **Tablero de certificados**, crea un borrador con el periodo de cobro. Agrega tareas propias o selecciona items de ordenes de compra. En **Costos** podes definir MO, materiales, servicios, estructura y beneficio. El avance actual de este modulo es el porcentaje a cobrar al cliente, independiente del avance real de las OC.

Carga los porcentajes y usa **Guardar avances**. Revisa la vista de presentacion y exporta CSV o imprime/guarda PDF desde el navegador. Al finalizar usa **Cerrar certificado** y luego **Crear siguiente periodo**: se arrastran tareas y acumulados; los avances actuales y adicionales vuelven a cero. El certificado anterior conserva sus importes. **Historial comparativo** permite comparar los periodos y sus tareas.
