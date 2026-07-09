# Contexto para continuar en Codex

Este proyecto es una aplicacion Spring Boot para gestionar certificaciones de obra de Terrazas de Quilmes.

## Ruta original

`C:\Users\Usuario\Documents\Codex\2026-07-06\es\outputs\sistema-certificaciones-obra`

## Repositorio GitHub

`https://github.com/fedealmada/certificaciones-obra`

## Base tecnica

- Java 21
- Spring Boot 3.5.3
- Maven
- Thymeleaf
- Bootstrap Icons / Bootstrap
- MySQL local: base `certificaciones_obra`
- Config actual en `src/main/resources/application.properties`
- Tests: `mvn test`

## Modulos principales

- Inicio con tarjetas grandes e iconos.
- Dashboard con graficos y alertas.
- Ordenes de compra.
- Items de OC.
- Proveedores.
- Categorias de items.
- Rubros jerarquicos para itemizado.
- Itemizado estilo tabla de Excel, con exportacion PDF/Excel.
- Materiales y recepcion de materiales.
- Certificaciones de OC.
- Reportes de gastos mensuales y evolucion por meses.
- Configuracion para activar/desactivar modulos y alertas.
- Importacion de OC desde PDF/TXT.

## Reglas de negocio importantes

- Una OC puede ser de mano de obra, material u otro tipo.
- En OC de mano de obra no se debe mostrar la accion de vincular a mano de obra.
- Los materiales pueden vincularse a items de mano de obra.
- El itemizado debe mostrar solo mano de obra, con importes separados de mano de obra y materiales vinculados.
- En itemizado no se muestra el nivel; se muestran codigo jerarquico, item OC, rubro/item e importes.
- El codigo jerarquico debe ordenarse naturalmente: 1, 2, 3 ... 9, 10, 11, no alfabeticamente.
- La tabla de itemizado no debe tener el efecto de resize de columnas.
- Las demas tablas buscan evitar scroll horizontal y usar acciones solo con iconos.

## Importacion de ordenes de compra

El modulo esta en `/oc/importar`.

Capacidades actuales:

- Importa PDF o TXT.
- Permite seleccionar varios archivos a la vez.
- Detecta numero de OC, fecha, vigencia, proveedor, items, unidad, cantidad, precio unitario e importe.
- Renumera items como `OC.1`, `OC.2`, etc.
- Recalcula importes como cantidad por precio.
- Deduplica items repetidos cuando el PDF trae paginas duplicadas.
- Si la OC con ese proveedor ya existe, muestra alerta.
- Para OC existentes compara diferencias contra la base: fecha, vigencia, cantidad de items, total, detalle, unidad, cantidad, precio e importe.
- Las OC nuevas quedan marcadas para importar; las existentes quedan desmarcadas por defecto.

## Mejoras visuales aplicadas

- Estilo moderno inspirado en UX de operaciones/logistica.
- Pantallas con paneles limpios, iconos grandes y animaciones sutiles.
- Detalle de OC mas informativo, con totales y layout tipo comprobante/ticket.
- Certificados con diseño inspirado en planillas de certificacion.
- Barras de avance animadas para certificados.
- Navegacion con botones de volver y accesos principales.

## Trabajo reciente

- Se corrigieron errores de importacion por proveedores/categorias duplicadas.
- Se agrandaron campos `nombre` en entidades importantes para evitar truncamientos.
- Se agrego importacion multiple de OC.
- Se preparo Git y se creo el primer commit local.
- Se configuro remoto GitHub: `https://github.com/fedealmada/certificaciones-obra.git`

## Para continuar en otra PC

1. Clonar el repo:

```powershell
git clone https://github.com/fedealmada/certificaciones-obra.git
```

2. Abrir la carpeta con Codex.

3. En el nuevo chat decir:

```text
Estoy continuando el proyecto certificaciones-obra. Lee CONTEXTO_CODEX.md y revisa el codigo para seguir desde ahi.
```

4. Configurar MySQL local si se quiere ejecutar la app con la misma base.

## Pendientes posibles

- Mejorar aun mas precision de importacion PDF con casos reales nuevos.
- Agregar auditoria/historial de cambios.
- Agregar backups/exportacion de base.
- Mejorar validaciones para evitar duplicados antes de guardar.
- Revisar seguridad antes de usarlo fuera de entorno local.
