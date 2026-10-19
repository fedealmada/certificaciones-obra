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

## Certificados quincenales de mano de obra y servicios (17/10/2026)

- Nuevo modulo `/certificados-quincenales`, accesible como "Quincenales" en la barra lateral y configurable desde modulos.
- El usuario confirmo que la fecha corresponde a la quincena anterior y luego corrigio que admite desfases de hasta cinco dias. Se adelantan cinco dias los cortes nominales del 16 y del 1, clasificando con `fecha.plusDays(5)` sin modificar el certificado. Los cinco dias anteriores y posteriores a cada cierre quedan en la misma quincena.
- Un mes de trabajo consulta emisiones desde el dia 11 de ese mes hasta el dia 10 del siguiente, inclusive. La segunda quincena inicia cinco dias antes del dia 1 del siguiente mes (varia segun la longitud del mes). Los intervalos no se solapan.
- Caso confirmado: Miguel Muriel, OC 463, certificado 1, fecha 14/08/2026, corresponde a primera de agosto, no segunda de julio. El importe real registrado es 10.694.217,00.
- Resume el avance actual de mano de obra y servicios certificables, usando el mismo calculo y redondeo de los certificados existentes. La categoria manual del item tiene prioridad sobre el tipo antiguo. Los servicios (tipo OTRO) se incluyen si la OC tiene seguimiento CERTIFICACION; con modo nulo se respeta el comportamiento legado (certificable si no tiene materiales). Se excluyen materiales, servicios de REGISTRO/ENTREGA, avances anteriores y margenes del certificado general.
- Vista mensual con ambas quincenas, resumen por contratista, detalle desplegable por certificado y tarea, enlace al original, filtros de contratista/quincena, historial de meses, exportacion CSV e impresion. Usa siempre la obra activa.
- Al entrar sin mes se abre el ultimo mes con certificados de mano de obra o servicios certificables; los avances cero no se cuentan como movimientos. Una quincena sin registros no implica cierre ni pago.
- El selector de contratista incluye proveedores de servicios certificados (Daniela Paz, Rondinoni, etc.) del mes seleccionado y permite buscar por partes del nombre, sin tildes y sin importar el orden de palabras. Elegir la opcion y pulsar Ver resumen aplica el filtro.
- Los certificados y tareas indican si son mano de obra, servicio certificable o mixtos. El CSV incluye la columna Tipo y totales alineados con Importe certificado.
- Pruebas de integracion en `CertificadosQuincenalesTest`: limites de fechas, aislamiento de obra, categorias, importes, filtros, renderizado y exportacion.

## Fondo de reparo y CAC (17/10/2026)

- Cada OC certificable tiene condiciones financieras en `/oc/{id}/ajustes`: porcentaje de fondo de reparo, CAC habilitado, mes/indice base y duracion en meses. El mes base cuenta como el primero y el ultimo mes es inclusivo.
- Cada certificado tiene sus ajustes en `/oc/{ocId}/certificaciones/{certId}/ajustes`: retencion configurable, mes/indice actual CAC o importe historico conocido (alternativas), y bruto original opcional para conciliar porcentajes historicos redondeados sin modificar avances.
- Neto = bruto - fondo de reparo + CAC. Confirmado por el usuario: la retencion SOLO se calcula sobre trabajos; nunca sobre el CAC. Se redondean importes por certificado a dos decimales HALF_UP.
- CAC = bruto * (indice actual / base - 1), calculado con precision antes de redondear dinero. Puede estar pendiente (neto provisional), desactivado en un certificado o vencido. La vigencia se evalua sobre el mismo mes de trabajo de Quincenas, con margen de cinco dias.
- Las condiciones anteriores se congelan en cada certificado al cambiar la OC. Cambiar valores por defecto no reescribe el historial; hay casillas explicitas para aplicar fondo y/o base CAC a certificados existentes. Los nuevos certificados/importaciones heredan defaults mientras no tengan condiciones propias.
- Acumulados por OC calculados desde los certificados, sin contadores que puedan desincronizarse. Detalle de OC, certificado, Quincenas y CSV muestran retencion, CAC y neto; el CSV quincenal usa filas separadas por ajuste para no duplicar importes por tarea.
- Configuradas OC 439 (id 13) y OC 301 (id 149), de Gloria Cristina Coronel, con 5% para certificados existentes y nuevos.
- OC 301: base junio 2025, indice 13443.5, vigencia OCHO meses confirmada por el usuario, hasta enero 2026 inclusive. Se conciliaron los 25 brutos y los importes CAC de su planilla adjunta. Primeros cinco certificados sin CAC. Importes CAC historicos fuera de esa vigencia se conservan como referencia pero se aplican como cero (Plazo vencido).
- Backup previo: `target/backup-antes-fondo-cac-20261017.sql`. Archivo fuente de conciliacion: `target/carga-fondo-cac-fuente.json`. Datos cargados mediante endpoints de la app tras validar numero y fecha de los 25 certificados.
- OC 439 certificado 1: bruto 1.638.000, retencion 81.900, neto 1.556.100. OC 301 certificado 1: bruto original 1.574.300, retencion 78.715, neto 1.495.585; se conservaron los porcentajes anteriores.
- Pruebas: `AjustesCertificacionTest` y `CertificadosQuincenalesTest` cubren calculos, vigencia, historial, bruto original, acumulados, pantallas, filtros y exportaciones.

## Regreso a Quincenas (18/10/2026)

- Al abrir un certificado o sus ajustes desde Quincenas se guarda en sessionStorage el mes/filtros aplicados, certificados desplegados, busqueda de proveedor y scroll de origen.
- `static/js/quincenas-navigation.js`, cargado desde el navbar, propaga un identificador de contexto en los enlaces/formularios internos de OC. En certificados, OC y ajustes aparece "Volver a Quincenas"; el enlace del modulo en el menu tambien retoma el origen.
- El regreso restaura los detalles abiertos y el desplazamiento tras cargar la pagina. Cada contexto tiene su propio identificador para no mezclar recorridos. Las URL de regreso se validan como locales y del modulo quincenal.
- Verificado visualmente: Quincenas mayo 2026 / Gloria / primera quincena -> certificado OC 439 -> OC 439 -> volver; misma posicion y siete certificados desplegados.

## Acumulados historicos de certificados (18/10/2026)

- `CertificacionCalculoService.calcularResumenHasta(certificacion, itemsOc, detalle)` corta el historial en fecha/id del certificado seleccionado. Incluye avances previos de items sin movimiento actual y excluye los posteriores, incluso del mismo dia con id mayor.
- Detalle del certificado: barra "Acumulado a este certificado", tarjetas Anterior / Este certificado / Acumulado al certificado / Saldo a esa fecha. CSV y listado de certificados de la OC usan el mismo resumen historico. El avance general de la OC sigue incluyendo todos los certificados.
- Caso verificado: OC 317, certificado 7 (id 7, OC id 128): anterior 38.72 %, actual 1.75 %, acumulado 40.47 %, pese a que la OC hoy acumula 100 %.
- Regresion en `CertificacionResumenHistoricoTest`; tambien se verificaron los tests de ajustes financieros y Quincenas.

## Certificacion por cantidades (18/10/2026)

- Nueva opcion «Certificar por» en alta/edicion de OC: porcentaje (legacy/default) o cantidad ejecutada. OrdenCompra.certificacionPorCantidad define nuevos certificados; Certificacion.porCantidad conserva la modalidad original aunque se cambie luego la OC.
- ItemCertificacion.cantidadActual nullable DECIMAL(19,6) almacena unidades originales. Si existe, el porcentaje se deriva a precision alta y el importe se calcula directamente cantidad × precio unitario, redondeando cada item a centavos. Las filas anteriores sin cantidad siguen usando porcentajeActual.
- Formularios muestran cantidades anteriores/actuales/acumuladas, unidad, contratado, precio y maximo disponible. Se valida cantidad positiva, hasta seis decimales, y saldo contando todos los otros certificados, incluidos posteriores en edicion.
- Detalle, resumen historico, liquidacion FR/CAC y Quincenas usan los importes exactos. Los acumulados historicos de certificados con cantidades suman importes originales para conservar redondeos de medios centavos. El porcentaje general sigue siendo referencia economica ponderada.
- CSV de certificado agrega unidad/contratado/cantidades. CSV quincenal agrega Cantidad actual, conserva porcentaje derivado y mantiene 14 columnas en filas de ajustes/totales.
- Importador de certificados identifica encabezado AVANCE CANT, valida modalidad de OC, importa cantidades con precio unitario y admite codigos simples (1,2) o completos (475.1,475.2). Planillas anteriores por porcentaje conservan esa modalidad.
- Corregida OC 475 de Daniela Agustina Gomez (OC id267, fecha23/09/2026; existe otra OC475 de materiales que no se modifico). Items791/792: 12 PISO cada uno, precios2051373/2224532 y descripciones de 3/4 ambientes.
- Certificado1 id291 fecha13/10/2026: cantidad1 por item; bruto4275905, FR5%=213795.25, CAC0, neto4062109.75. CAC base2026-08 indice20167.9, actual2026-08 mismo indice. Usuario confirmo duracion12 meses: agosto2026 a julio2027 inclusive.
- Backup antes de corregir: target/backup-antes-cantidades-20261018.sql. Correccion acotada y validada en target/corregir-oc475-cantidades.sql.
- Suite completa: 27 pruebas OK, 7 nuevas en CertificacionCantidadTest: persistencia exacta, pantallas/CSV/Quincenas, doce certificados de una unidad, fracciones/medios centavos, mezcla legacy y modalidad conservada, limites/edicion con posteriores e importacion por cantidades.
- Verificado visualmente en localhost el detalle, edicion y Quincenas octubre2026/proveedor30: 1 PISO por item y neto4062109.75. Captura target/oc475-certificado-cantidades.png.

## Etiquetas de ordenes de compra (18/10/2026)

- Hoja de estilo local `static/css/oc-labels.css`, cargada despues del sistema visual en listado y detalle de OC. Etiquetas compactas, esquinas suaves, tipografia uniforme y variantes de modo oscuro.
- Semantica: mano de obra violeta, material naranja, servicio turquesa; abierta azul, cerrada/entrega completa verde, parcial ambar, pendiente/registro gris. Contadores de certificados violetas y viajes azules.
- Categoria y color se obtienen del tipo real de cada item (categoriaEntidad.tipo con fallback al enum), independientemente del seguimiento. Listado muestra categorias distintas, conserva sus nombres personalizados y no repite etiquetas por cada item. Un servicio certificable ya no recibe color de mano de obra.
- Simplificados textos de estado en listado y eliminada segunda etiqueta redundante de sin seguimiento en registros. En detalle, la etiqueta de seguimiento utiliza la clase correspondiente a certificacion/entrega/registro.
- Tabla ajustada a minimo1080px y anchos proporcionados para mostrar categorias y seguimiento juntos en escritorio. Sigue admitiendo desplazamiento horizontal en pantallas pequenas y ajuste de texto de proveedores/rubros.
- Verificacion visual en listado con servicios, materiales, mano de obra, abiertas/cerradas y entregas; modo claro/oscuro y detalle OC488 (id272). Cambios de presentacion, sin modificaciones de datos ni calculos.

## Reestructura de certificados generales (19/10/2026)

- Usuario quiere certificados de cobro al cliente por periodo, con tareas propias o de OC y avances comerciales independientes del avance real. Planilla de referencia: attachment717e0f21-4a13-4ed4-a15d-59a96029d3df/Texto pegado.txt (236 filas; certificados1 febrero-mayo2025 y2 junio-noviembre2025, incluye margenes variables y tareas manuales al100%). CONFIRMO: solo reestructurar, NO cargar esa planilla como borradores.
- Reescritos TableroCertificadoService/Controller y templates/tablero/lista,detalle. Nuevas vistas item,seleccionar,historial,imprimir; CSS/JS propios tablero-certificados. Formularios de costos separados de avances; avances se guardan juntos, busqueda local, filtro con saldo, boton Saldo, calculos en vivo con BigInt a centavos y aviso de cambios sin guardar.
- TableroCertificado agrega numero,cliente,empresa,emitido(Boolean, null legacy=borrrador),anteriorId. Numero configurable y unico por obra al guardar. Cada nuevo certificado parte como borrador con periodo configurable (mensual por defecto en service); no altera los anteriores.
- Cerrar valida tareas/avance0..100 e importeactualpositivo. Crear siguiente periodo requiere anterior cerrado, arrastra todas las tareas (incluidas completas), costos y acumulados, pone avanceactual/adicionalperiodo en0. Se admite un unico sucesor. Se reabre solo si no tiene sucesor. Bloqueos pesimistas de raiz en mutaciones evitan editar durante cierre/continuacion.
- TableroCertificadoItem agrega claveContinuidad UUID (fallback legacy-fila:id) y montoAnteriorFijo DECIMAL19,2. Clones conservan misma clave sin enlazar tareas distintas por codigo. Anterior monetario se conserva como snapshot incluso si se cambian costos futuros; acumulado$=anterior$+actual$. Adicionalperiodo no se repite en el siguiente. Al editar una fila legacy se congela su anterior antes de cambiar costos.
- Se conserva el modelo original de costos: MO+materiales+servicios+mat.empresa; estructura y beneficio calculados ambos sobre costo subtotal. Defaults nuevos20%/25%; los valores historicos (incluido40%) se conservan. SubtotalManual opcional reemplaza base para cualquier fila. Se permiten creditos en componentes siempre que subtotal>=0.
- Selector permite elegir MO o servicios certificables de cualquier OC de obra activa sin requerir certificados previos; agrega presupuestos con avance0 y no duplica item de OC en el mismo tablero. Deteccion por periodo ahora opcional y no copia avance real. Importa monto OC bruto como referencia, sin modificar OC ni certificados de contratistas.
- Eliminada sincronizacion destructiva en GET detalle. Los datos no cambian al abrir pantallas. No se eliminan filas con acumulados anteriores.
- Historial comparativo agrupa solo continuidades explicitas y muestra actual/acumulado en% y$. Filas nuevas aparecen desde su alta. Tableros legacy sin relacion conservan lineas independientes; no se infieren vinculos por codigos ambiguos.
- CSV de certificado detalla costos y avances; CSV de historial compara periodos. Textos protegidos de formulas de Excel. Vista imprimible A4 apaisada con encabezado constructora/cliente/obra/periodo, filas anterior/actual/acumulado y total del certificado. Boton imprimir permite guardar PDF desde navegador.
- Control de obra activa en todas las rutas. DTO de avances valida ids repetidos/ajenos y valida lote antes de aplicar cambios; formulario conserva porcentajes ingresados ante error. Costos preservan datos ingresados si hay error. Binder admite hasta5000 filas.
- Backup real ANTES: backups/antes-reestructura-tablero-20261019.sql. Arranque MySQL aplica solo nuevas columnas. Huella de todos los campos originales en target/preservacion-tablero-antes.txt y -despues.txt: iguales; 3 tableros (8,9,10),177 tareas conservadas. Pantallas/lista/detalle/historial/selector/impresion/CSV verificados HTTP200 con datos reales.
- 36 pruebas completas OK (9 nuevas TableroGeneralTest: manual100, snapshots/extras, continuidad, bloqueo cierre, validacion atomica, selector servicios, claves no ambiguas, permisos de obra, pantallas/exports). JS verificado y QA visual con base H2 temporal en8081: guardado en vivo, cierre/continuacion, tarea nueva, busqueda, comparativa y presentacion. No se agrego ningun dato de prueba a MySQL. Captura demostrativa backups/tablero-historial-demostracion-20261019.png.
- Se detecto interferencia de compilador VSCode/ECJ con Lombok: emitia clases con java.lang.Error Unresolved compilation problems (Flexible Constructor Bodies). .vscode/settings.json conserva settings previos y desactiva autobuild y forceBuildBeforeLaunch; launch.json ObraControl - Iniciar usa tarea previa ObraControl: compilar con Maven (tasks.json, shell, mvn-o-qcompile, JAVA_HOME JDK21). Clases defectuosas regeneradas con Maven. .vscode esta ignorado en Git.

## Pendientes posibles

- Revisar visualmente el tablero general con datos reales despues de importar en otra PC.
- Mejorar exportacion del tablero general a Excel/Google Sheets.
- Agregar auditoria/historial de cambios.
- Revisar seguridad antes de usar fuera de entorno local.
- Pulir permisos por usuario/rol si se empieza a usar con mas personas.


## Importación SIMENDE 19/10/2026
Se importaron 8 certificados correlacionados (IDs 11 a 18), 207 tareas diferentes: 156 vinculadas a OC y 51 manuales; 1650 filas históricas. Importes y porcentajes comerciales conservados independientemente del avance real. Anexos Logística y Seguridad/Higiene desde enero, con saldo anterior informado de $45.740.000 aprobado por el usuario, sin reparto ficticio anterior. Conciliaciones explícitas de centavos mantienen los totales impresos; presupuesto de la fuente omite gammagrafía $17.320.000 y difiere dos centavos de las tareas. Auditoría y copia previa en backups/. SHA256 fuente f4d752d109bca5090d8596194c0413419bfba597c9bd5c20fd1d5c7cdf4192f6.

Presentación PDF 19/10/2026: plantilla tablero/imprimir.html con estilo independiente certificado-presentacion.css, encabezado verde SIMENDE, 12 columnas y total amarillo. Por defecto tareas con monto actual no cero; ?completo=true incluye todas. Fila separada conserva saldos de tareas ocultas y conciliaciones para cuadrar totales. Anexos mensuales fuera del presupuesto. Probado certificado 8 en una hoja A4 horizontal; PDF de ejemplo en output/pdf/. Prueba de controlador filtro/acumulados incluida.
