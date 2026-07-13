# Contexto para continuar en Codex

Proyecto Spring Boot para gestionar certificaciones, ordenes de compra, recepciones, deposito, asistencia y tablero financiero de obra.

## Estado actual

- Repo GitHub: `https://github.com/fedealmada/certificaciones-obra`
- Carpeta actual de trabajo: `C:\Users\feede\Documents\Codex\2026-07-09\ho\work\certificaciones-obra`
- Rama principal: `main`
- Ultimo backup versionado de base: `backups/certificaciones_obra.sql`
- Base local: MySQL/MariaDB `certificaciones_obra`
- Obra por defecto: `Terrazas de Quilmes`
- Usuario por defecto: `admin`
- Contrasena por defecto: `control`

## Base tecnica

- Java 21
- Spring Boot 3.5.3
- Maven
- Thymeleaf
- Bootstrap / Bootstrap Icons
- MySQL local
- Configuracion principal: `src/main/resources/application.properties`

Nota: en el entorno de Codex, `mvn -q -DskipTests compile` suele fallar antes de compilar porque no puede descargar dependencias desde Maven Central (`Permission denied: getsockopt`). No asumir que el fallo sea del codigo si aparece ese error.

## Modulos actuales

- Inicio con seleccion contextual por obra.
- Dashboard con graficos, metricas y alertas.
- Ordenes de compra.
- Items de ordenes de compra.
- Certificaciones de OC.
- Recepciones, entregas y viajes para materiales.
- Proveedores con vista de analisis.
- Rubros jerarquicos.
- Itemizado interactivo con arbol, drag and drop, crear/renombrar/eliminar rubros y subrubros.
- Materiales ligados a OC.
- Deposito / panol con stock, movimientos, entradas, salidas, devoluciones y personas.
- Asistencia de personal con carga manual por dia y calendario.
- Obras multiples con obra activa.
- Tablero de certificados generales de obra.
- Reportes.
- Configuracion de modulos.
- Importacion de OC y certificados desde PDF/TXT.

## Reglas de negocio importantes

- Una OC puede ser de mano de obra, material, servicio u otro comportamiento.
- Mano de obra se controla con certificados de avance.
- Materiales se controlan por entregas, recepciones o viajes.
- Servicios pueden ser certificables o solo registro, segun comportamiento de la OC.
- Los rubros de OC son opcionales.
- El modulo de rubros permite eliminar rubros libremente; el itemizado tiene restricciones para no borrar rubros con items.
- En recepciones de materiales el numero de remito no es obligatorio.
- La fecha por defecto al crear certificado o entrega toma la fecha de emision de la OC.
- Las vistas deben respetar modo claro/oscuro y estilo minimalista profesional.

## Tablero de certificados generales de obra

Modulo: `/tablero-certificados`

Objetivo: armar certificados generales para presentar a la empresa/cliente, independientes de las certificaciones reales de OC.

Estado actual:

- Se pueden crear, editar y eliminar tableros/certificados generales.
- Se pueden traer items de mano de obra detectados en un periodo.
- Los items traidos usan la data base del item de OC, pero el avance del tablero es independiente.
- El avance anterior del tablero se correlaciona con el certificado general anterior de la misma obra.
- El anterior se calcula desde el acumulado del tablero previo.
- El usuario edita el avance actual.
- El acumulado se calcula como anterior + actual.
- Cada item tiene costo estructural (%) y beneficio empresarial (%) editables.
- Cambiar esos porcentajes altera el total tarea.
- La tabla financiera muestra columnas tipo planilla:
  - Codigo
  - Descripcion tarea
  - Unidad
  - Cantidad
  - Precio unitario
  - Costo M.O
  - Materiales asignados
  - Servicios
  - Mat. sum. empresa
  - Costo subtotal
  - Costo estructural (%/$)
  - Beneficio empresarial (%/$)
  - Estruc. + benef.
  - Total tarea
  - Mat. adic. etapa
  - Anterior / Actual / Acum en % y $
- La tabla tiene scroll horizontal, columnas fijas para codigo y descripcion, y descripcion contenida/redimensionable para no pisar otros campos.

## Diseno y UX

Lineamiento general:

- Minimalista, profesional, fino y compacto.
- Inspirado en dashboards operativos limpios.
- Sidebar con iconos y comportamiento fluido.
- Tablas compactas, legibles, con botones de accion por icono.
- Etiquetas consistentes en todas las tablas.
- Modo oscuro tipo Dracula, con buen contraste y sin fondos blancos sueltos.
- Alertas centradas con animacion, reutilizadas en itemizado/OC/entregas.

## Base de datos y backup

Backup versionado:

`backups/certificaciones_obra.sql`

El backup actual fue generado desde MySQL/MariaDB local con `mysqldump` e incluye estructura y registros (`CREATE TABLE` + `INSERT`).

Ultimo commit de backup:

`381859e Actualizar backup de base de datos`

Para restaurar en otra PC, crear la base `certificaciones_obra` e importar ese SQL.

## Git y sincronizacion

Comandos utiles:

```powershell
git status --short --branch
git pull origin main
git push origin main
```

Si GitHub rechaza el push con usuario incorrecto, revisar credenciales de Windows o GitHub Desktop. Hubo un caso donde la PC intentaba pushear como `Redlightsamp` y el repo requiere acceso como `fedealmada`.

## Para continuar en otra PC

1. Clonar el repo:

```powershell
git clone https://github.com/fedealmada/certificaciones-obra.git
```

2. Importar la base:

```powershell
mysql -u root certificaciones_obra < backups\certificaciones_obra.sql
```

3. Abrir el proyecto con Codex.

4. En el chat nuevo decir:

```text
Estoy continuando el proyecto certificaciones-obra. Lee CONTEXTO_CODEX.md y revisa el codigo para seguir desde ahi.
```

## Pendientes posibles

- Revisar visualmente el tablero general con datos reales despues de importar en otra PC.
- Mejorar exportacion del tablero general a Excel/Google Sheets.
- Agregar auditoria/historial de cambios.
- Revisar seguridad antes de usar fuera de entorno local.
- Pulir permisos por usuario/rol si se empieza a usar con mas personas.
