# 06 — Historia y decisiones de producto

> Escrito el 2026-11-17 a partir del `git log` completo (hasta "ola 7.5"),
> `seguimiento/`, `documentación/` y los comentarios del código.
>
> **Para quién:** el próximo desarrollador (persona o Claude) que toque la app.
> La app está **en producción** y la usa a diario un hogar de dos personas.
> Lo que sigue en la sección 3 son decisiones que el dueño del producto ya tomó,
> muchas veces tras probar la alternativa. **No se deshacen sin preguntarle.**

---

## 1. Línea de tiempo por olas

**Antes de las olas (marzo–junio 2026).** Plan maestro de 12 fases (0–11):
microservicios Spring Boot (auth, budget, fasting, gateway, discovery),
frontend React Native/Expo, PostgreSQL, Docker. En junio, taller de calidad con
SonarCloud: cobertura medida, bugs y vulnerabilidades corregidos.

**Ola 1 (2026-08-11) — la identidad sale del token.** Auditoría multiagente con
144 hallazgos; casi todos venían de que el `userId` viajaba en el cuerpo. Se
lee del JWT, cada lectura/escritura comprueba propiedad (se responde "no
existe", no "no puedes"), el registro deja de poder sobrescribir cuentas ajenas,
los servicios internos escuchan solo en local. Nace la matriz de aislamiento.

**Ola 2 (2026-08-11) — las cifras cuadran.** Una sola regla de "qué suma"
(`Computables`: el sub-gasto es detalle del padre). Fechas locales (antes lo
anotado de noche caía en el día siguiente por UTC). El signo del dinero lo pone
el formateador. Sesión caducada ya no se disfraza de "sin datos". Flyway con
`ddl-auto=validate`. Guardarraíles de arquitectura que rompen el build
(`NingunEndpointSinDueno`, `LaIdentidadNoVuelveAlCuerpo`, etc.) y CI como puerta.

**Ola 3 (2026-08-12 a 08-18) — funciones "de libro".** Entraron: cuentas con
saldo calculado (3.1), reparto de gastos entre personas (3.2), sobres con
arrastre asimétrico (3.3), importación de extracto CSV (3.4), recurrencias
reales desde el ancla con "el tercer viernes" y tope de materialización (3.5),
consultas probadas contra PostgreSQL con Testcontainers y spec OpenAPI
commiteada (3.6). Después: accesibilidad (157 etiquetas) y techos de memoria en
Docker. *Casi todo lo funcional de esta ola se retiró en la ola 7.*

**Ola 4 (2026-09-04) — de aplicación a plataforma.** Roles y `/v1/admin`;
módulos → páginas → permisos por rol (el menú lo decide el servidor); pantallas
de administración; auditoría después del commit y en otro hilo; moneda y
decimales configurables (global + preferencia del usuario); Row Level Security
en PostgreSQL (puesta pero dormida mientras se conecte con superusuario);
contraste WCAG y escala tipográfica mínima con guardarraíles.

**Ola 5 (2026-10-31) — el plan de la quincena.** Quién paga cada programado,
clase del programado (pago, descuento de nómina, ahorro, provisión) y notas;
pantalla "Mi quincena" (`/v1/plan`); presupuesto = asignaciones, con copiar del
periodo anterior; cada persona ve lo que le toca pagar; reasignar quién paga un
pendiente común. Excel alineado con las cifras del panel.

**Ola 6 (2026-11-01 a 11-09) — estilo Monefy y lógica financiera validada.**
Fuente única de cifras `/v1/estado/periodo` (6.0) y cierre de mes. Inicio con
dona, menú lateral en vez de 6 pestañas, teclado propio que opera (6.1), suite
adversarial de 72 escenarios. Categorías con un solo dibujo en toda la app
(6.3). Al entrar se ve lo propio, no el hogar (6.4). Liberar plata del plan y
deshacerlo (6.5–6.6). Gastos pagados con un ahorro, y facturas movidas de fecha
que ya no salen vencidas (6.7).

**Ola 7 (2026-11-10) — simplificar.** Ahorros con saldo acumulado; "Pagado con"
(fondos del mes, un ahorro o una tarjeta); tarjetas de crédito con compras a 1 o
N cuotas; Deudas (tarjetas + programados en cuotas, con adelantar cuota).
**Se retiraron** cuentas, importación, repartos, sobres, consolidado,
transferencias entre categorías, ayuno y código muerto (ver sección 2).
Exportar a Excel se queda.

- **7.1** — arreglos de QA (finanzas, UX, TI, datos): día de corte de la
  tarjeta, crear deudas en cuotas, límite de intentos de login, cambiar clave
  pide la actual, botón Atrás del navegador, categorías duplicadas por tildes.
- **7.2** — pagar la tarjeta con el monto y la fecha que se quiera; abono
  parcial; historial de pagos (`oc_budget_card_payment`).
- **7.3** — Inicio como Monefy con todas las categorías alrededor de la dona y
  gestos; Movimientos por categoría o por fecha con la nota adelante; orden por
  más gastado y luego alfabético; la quincena resta lo realmente gastado.
- **7.4** — deslizar hacia arriba abre Movimientos; la dona gana espacio (se
  quitó el texto de ayuda).
- **7.5** — lo pagado con el ahorro no cuenta como gasto del sueldo en el
  centro de la dona ni en el total de Movimientos.

---

## 2. Lo que se retiró a propósito (no reintroducir sin pedirlo)

Todo se retiró en la **ola 7** (commit `354656c`) bajo una sola idea: **la app
debe ser simple; lo que el hogar no usaba estorbaba**. Las tablas viejas **no se
borraron** todavía (`V14__sin_cuentas_ni_repartos.sql` solo pone un valor por
defecto a `is_transfer`) para poder volver a la imagen anterior si hiciera
falta. No las borres ni las reutilices sin preguntar.

| Retirado | Qué era | Por qué se fue |
|---|---|---|
| **Cuentas** (ola 3.1) | Saldo por cuenta bancaria calculado de los movimientos, con apertura fechada | Obligaba a elegir cuenta en cada movimiento; al hogar le importa en qué se gasta, no en qué banco está. La idea de "saldo" sobrevive solo en los **ahorros**. |
| **Repartos / splits** (ola 3.2) | Repartir un gasto entre personas con balance neto y liquidación | El hogar comparte categorías comunes y "quién paga"; el reparto era una capa de contabilidad que no usaban. |
| **Sobres con arrastre** (ola 3.3) | Lo sobrante pasaba al mes siguiente; el sobregiro se descontaba del total | El presupuesto es por periodo; el arrastre confundía. Lo que queda es presupuesto por categoría + liberar plata del plan. |
| **Importar extracto CSV** (ola 3.4) | Vista previa, deduplicación, aprendizaje de categorías | No se usaba; anotar a mano con nota es lo que da información útil. |
| **Transferencias entre categorías** | Mover plata del bote común a otra categoría | Sobraban con el modelo de "Pagado con" y ahorros. |
| **Consolidado** | Pantalla de consolidado del hogar | Sustituido por el selector Hogar / persona en todas las pantallas. |
| **Ayuno** (`fasting-service`) | Segunda aplicación: ayuno, agua, logros | No se usaba. El servicio ya no está en `backend/`. La plataforma de módulos (ola 4) sigue permitiendo añadir aplicaciones, pero no lo resucites. |
| **Barra de 6 pestañas** (ola 6.1) | Navegación inferior | Reemplazada por Inicio estilo Monefy + menú lateral. |
| **Emojis como iconos** (ola 6.3/6.5) | — | Un solo dibujo SVG por categoría en toda la app. |

---

## 3. Decisiones de producto vigentes

1. **La app debe ser simple.**
   - *Por qué:* la usa un hogar a diario desde el celular; cada pantalla o
     campo de más es fricción para anotar. La ola 7 retiró todo lo no usado.
   - *Dónde:* commit `354656c`; `V14__sin_cuentas_ni_repartos.sql`. Antes de
     añadir una función, preguntar si de verdad hace falta.

2. **La nota del movimiento es muy importante.**
   - *Por qué:* es lo que dice *en qué* se gastó; la categoría sola no basta
     para revisar el mes. Por eso está siempre visible al anotar, va adelante
     (en negrita) en las listas y se puede buscar por ella.
   - *Dónde:* `frontend/src/screens/anotar/AnotarScreen.tsx` ("La nota siempre a
     la vista"); `frontend/src/screens/movimientos/MovimientosScreen.tsx`
     (render de la fila y buscador "por nota o categoría").

3. **Una compra con tarjeta es gasto cuando se compra; pagar la tarjeta no es
   gasto.**
   - *Por qué:* el gasto pertenece a la categoría y al mes en que se hizo (a N
     cuotas, cada cuota cuenta en su mes). Pagar la tarjeta solo salda; si
     contara, el gasto se contaría dos veces.
   - *Dónde:* `backend/budget-service/.../service/impl/DeudasService.java`
     (comentario de cabecera); `db/migration/comun/V15__tarjetas_y_cuotas.sql`
     (`card_id`, `card_paid`, `installment_*`, `purchase_id`).

4. **El pago de tarjeta acepta cualquier monto > 0 y ≤ lo debido a la fecha del
   pago** (no solo "todo hasta el corte"), con la fecha que se quiera.
   - *Por qué:* en la vida real se abona parcialmente o se paga en otra fecha.
     El pago salda las compras más viejas primero; lo que no alcanza a cubrir
     una compra completa queda como **abono** (`credit`) y baja la deuda. Las
     cuotas de meses que no han llegado a la fecha del pago no se pueden pagar
     todavía. Sin monto se paga todo lo debido a esa fecha; sin fecha, hoy.
   - *Dónde:* `DeudasService.pagarTarjeta(...)`;
     `V18__pagos_de_tarjeta.sql` (`oc_budget_card.credit`,
     `oc_budget_card_payment`); `frontend/src/screens/deudas/FormPagoTarjeta.tsx`.

5. **Deudas sin intereses ni tablas de amortización.**
   - *Por qué:* "la app no es el banco". Se ve cuántas cuotas van, cuántas
     faltan y cuánto falta; se puede adelantar una cuota.
   - *Dónde:* `DeudasService.java` (cabecera).

6. **Lo pagado con un ahorro sale en la dona por categoría, pero no cuenta
   contra el ingreso del periodo ni en el total rojo.**
   - *Por qué:* ese gasto sí ocurrió (la porción de su categoría lo muestra),
     pero la plata no salió del sueldo del periodo, salió del ahorro. El centro
     de la dona y el total de "Todos" en Movimientos dicen solo lo que salió de
     lo del periodo, y aparte "y X pagados con ahorros". Tampoco baja lo libre
     para gastar ni la quincena, y no se puede pagar con un ahorro más de lo
     que tiene.
   - *Dónde:* campo `funded_from_category_id` (`V13__gasto_pagado_con_el_ahorro.sql`);
     `EstadoDelPeriodoService.java` (`fromSavings`); `MovementServiceImpl.java`
     (`validarSaldoDelAhorro`); `frontend/src/screens/inicio/InicioScreen.tsx`
     (`gastosDelMes`); `frontend/src/components/molecules/DonaDeCategorias.tsx`
     (`conAhorros`); `MovimientosScreen.tsx` (`salidas` / `conAhorros`).

7. **Orden de categorías: de la que más gastó a la que menos; empates y vacías
   por orden alfabético.**
   - *Por qué:* lo importante arriba; el alfabético hace el orden estable.
     Aplica en la dona, en Movimientos (por categoría) y en Presupuesto (en la
     vista "Planeado" ordena por lo asignado).
   - *Dónde:* `DonaDeCategorias.tsx` (línea del `sort`);
     `MovimientosScreen.tsx`; `PresupuestoScreen.tsx` (`localeCompare(..., 'es')`).

8. **Inicio estilo Monefy, con gestos.**
   - *Por qué:* lo que el hogar quiere al abrir es ver en qué se fue la plata y
     anotar rápido. Dona con **todas** las categorías de gasto y ahorro
     alrededor (cada una con su línea y %), ingresos y gastos en el centro,
     recuadros de ingresos / por pagar / ahorros / deudas, "libre para gastar"
     abajo y dos botones. Tocar una categoría ofrece anotar, ver movimientos o
     presupuesto. Gestos: derecha = gasto, izquierda = ingreso, hacia arriba =
     Movimientos; en la dona, a los lados cambia el periodo. En web se le quitan
     esos gestos al navegador (si no, "derecha" es "página anterior").
   - *Dónde:* `frontend/src/screens/inicio/InicioScreen.tsx` (comentario de
     cabecera y `PanResponder`); `DonaDeCategorias.tsx`.

9. **La quincena resta lo realmente gastado.**
   - *Por qué:* antes decía "te sobra X" sin restar el mercado ni nada fuera de
     lo programado. Ahora resta lo pagado en la ventana (hasta hoy) que no es una
     fecha del plan, suma los ingresos sueltos, y la plata separada solo cuenta
     lo que falta. Lo pagado con ahorro no sale del sueldo; un sub-gasto ya lo
     cuenta su padre. Con un solo pago al mes se llama "mes de salario".
   - *Dónde:* `backend/budget-service/.../service/impl/PlanFinancieroService.java`
     (bloque "LO QUE DE VERDAD SE MOVIO", ola 7.3).

10. **Los ingresos programados son editables, porque el monto real puede
    variar.**
    - *Por qué:* un salario o una quincena programada genera un pendiente con el
      monto esperado, pero lo que llega puede ser distinto. Se edita el
      pendiente (monto, fecha) antes o al confirmarlo; editarlo **no** lo
      confirma, y editar el programado solo propaga a sus pendientes lo que
      cambió. Ingresos muestra esperado vs. recibido, e incluye los ingresos
      sueltos no programados.
    - *Dónde:* `frontend/src/screens/movements/MovementFormScreen.tsx` (estado
      Pagado / Por pagar); `ScheduledMovementServiceImpl.java` ("solo se propaga
      lo que cambio"); `frontend/src/screens/ingresos/IngresosScreen.tsx`.

11. **Una sola fuente de cifras.**
    - *Por qué:* las pantallas llegaron a decir cifras distintas para la misma
      plata. Inicio y Presupuesto salen de `/v1/estado/periodo`; "Por pagar" del
      Inicio es la misma cifra que descuenta "Libre para gastar". La regla de
      qué suma vive solo en `Computables`.
    - *Dónde:* `EstadoDelPeriodoService.java`; `util/Computables.java`;
      comentarios en `InicioScreen.tsx`.

12. **Al entrar se ve lo propio, no el hogar.**
    - *Por qué:* cada persona abre la app en lo que paga y le toca; el hogar y
      la pareja están a un toque en el selector Hogar / persona, que comparten
      todas las pantallas. Nadie ve lo privado del otro.
    - *Dónde:* `frontend/src/contexts/AlcanceContext.tsx`; commit `b63e9e4`.

13. **Ahorros: meter plata a un ahorro es anotar en esa categoría (en verde), y
    el saldo se calcula, no se guarda.**
    - *Por qué:* mismo principio que el saldo de cuentas y el arrastre: un dato
      derivado guardado se desincroniza. Saldo = lo metido − lo pagado con él.
    - *Dónde:* `AnotarScreen.tsx` (comentario "Pagado con");
      `EstadoDelPeriodoService.java` (cálculo por ahorro).

14. **Liberar plata del plan sin tocar el presupuesto, y poder deshacerlo.**
    - *Por qué:* a veces lo programado (p. ej. el ahorro del mes) se usa para
      otra cosa; queda en lo libre y el cierre dice cuánto se cumplió y cuánto
      se liberó.
    - *Dónde:* `V12__plata_liberada.sql` (`released_amount`);
      `/v1/movements/unrelease/{id}`.

15. **La app no inventa datos.** Más de 5 ocurrencias atrasadas de un
    programado no se crean en silencio: se proponen. Lo de otro miembro solo se
    materializa hasta hoy.
    - *Dónde:* `util/CalendarioDeRecurrencias.java`,
      `ScheduledMovementServiceImpl.java`.

16. **Un pago de tarjeta o de una deuda que NO se registró en la app se anota
    como gasto normal, y está bien.**
    - *Por qué:* si las compras de esa tarjeta nunca se anotaron (deuda vieja,
      de antes de usar la app, o una tarjeta que no está registrada), pagarla
      es la única huella de ese gasto. Lo mismo con devolverle plata a alguien.
      Los usuarios lo confirmaron: "al final de cuentas fue pagar la tarjeta".
      **No los marques como duplicados ni los "corrijas".** La regla 3 solo
      aplica a tarjetas registradas en Deudas, cuyas compras se anotan con
      "Pagado con: tarjeta".
    - *Dónde:* son movimientos normales, en la categoría que el usuario eligió.

17. **Todo movimiento es editable porque la vida real no cuadra con el plan**
    (llega una extra en el sueldo, un recibo sube). Que un monto real difiera
    del programado no es un error.

Además siguen vigentes las invariantes técnicas de las olas 1–4 (identidad del
token, "no existe" en vez de "no puedes", Flyway con `validate`, guardarraíles de
accesibilidad, contraste y tamaño de texto). Están en
`documentación/invariantes.md` (ver sección 4 sobre su vigencia).

---

## 4. Inventario de documentos viejos

**Regla general:** la documentación de este repo se ha quedado atrás varias
veces. Ante la duda, manda el código y el `git log`.

### `documentación/`

| Documento | Estado |
|---|---|
| `enunciado-detallado.md` | **Histórico.** Alcance original de marzo; él mismo lo declara. |
| `auditoria-y-plan-de-estabilizacion.md` | **Histórico.** Plan de olas 1–3 de agosto; su ola 3 (cuentas, repartos, sobres, CSV) se retiró en la ola 7. La sección "lo que NO hay que hacer" sigue siendo buena lectura. |
| `invariantes.md` | **Parcialmente vigente.** Las reglas de identidad, propiedad, cifras y migraciones siguen; menciona ayuno, cuentas y reparto, que ya no existen. |
| `entidades-y-relaciones.md` | **Desactualizado.** Escrito en agosto (11 entidades, incluye ayuno); no tiene tarjetas, pagos de tarjeta, ahorros ni campos de olas 5–7. |
| `ola-4-plataforma-y-permisos.md` | **Vigente como diseño** de roles/módulos/permisos; las referencias al ayuno son históricas. |
| `como-anadir-una-aplicacion.md` | **Vigente** en el mecanismo (módulos y páginas); el ejemplo del ayuno ya no aplica. |
| `puntos-futuros.md` | **Parcialmente vigente.** Puntos 1, 2, 3 y 8 cerrados en la ola 4; los demás (offline, notificaciones, PDF, multi-moneda) siguen abiertos y no decididos. |
| `taller-calidad-sonarqube.md` | **Histórico.** Entregable universitario de junio. |
| `cobertura-tests-changelog.md` | **Histórico.** Cobertura de junio; cifras y puertos muy superados. |
| `api/*.json` | **Parcialmente vigente.** Spec OpenAPI generada; `fasting-service.json` es histórico. Regenerar antes de fiarse (hay prueba que la compara). |
| `pruebas/00`–`07` | **Históricos.** Métricas y suites de abril (438 pruebas); `07-como-ejecutar.md` aún orienta en los comandos básicos. |
| `pruebas/plan-adversarial-financiero.md` | **Parcialmente vigente.** Método y suite de la ola 6.1 siguen; varios escenarios hablan de cuentas y repartos, ya retirados. |

### `seguimiento/`

| Documento | Estado |
|---|---|
| `plan-maestro.md` | **Histórico.** Fases 0–11 de marzo; su "qué queda por hacer" remite a la ola 3, ya superada y en buena parte retirada. |
| `bitacora.md` | **Histórico hasta la ola 4** (última parte: 2026-09-04; el encabezado dice 2026-08-11). Las olas 5–7 solo están en los mensajes de commit y en este documento. |
