# 02 — Reglas de negocio de budget-service

> Guía para quien va a tocar el backend sin conocer el proyecto. Todo lo de aquí
> se sacó **leyendo el código** de `backend/budget-service/src/main` (no de los
> documentos viejos de `documentación/` y `seguimiento/`, que están
> desactualizados: cuentas, repartos/splits, transferencias, importación y
> "sobres con arrastre" ya **no existen en el código**, aunque algunas tablas y
> columnas sigan en la base, ver V14).
>
> Convención de rutas: `BS/` = `backend/budget-service/src/main/java/com/ohchurus/budget/`,
> `MIG/` = `backend/budget-service/src/main/resources/db/migration/`,
> `FE/` = `frontend/src/`.
>
> Los montos de los ejemplos son inventados.

---

## 0. Mapa rápido

| Concepto | Entidad / tabla | Servicio que manda | Endpoint base |
|---|---|---|---|
| Movimiento (gasto o ingreso real o pendiente) | `BS/entity/Movement.java` / `oc_budget_movement` | `BS/service/impl/MovementServiceImpl.java` | `/v1/movements` |
| Categoría | `BS/entity/Category.java` / `oc_budget_category` | `BS/service/impl/CategoryServiceImpl.java` | `/v1/categories` |
| Asignación de presupuesto | `BS/entity/BudgetAllocation.java` / `oc_budget_allocation` | `BS/service/impl/BudgetAllocationServiceImpl.java` | `/v1/budget-allocation` |
| Programado (recurrencia) | `BS/entity/ScheduledMovement.java` / `oc_budget_scheduled_movement` | `BS/service/impl/ScheduledMovementServiceImpl.java` | `/v1/scheduled` |
| Tarjeta de crédito | `BS/entity/Card.java` / `oc_budget_card` | `BS/service/impl/DeudasService.java` | `/v1/cards` |
| Pago de tarjeta | `BS/entity/CardPayment.java` / `oc_budget_card_payment` | `DeudasService.pagarTarjeta` | `/v1/cards/pay/{id}` |
| Deudas (tarjetas + cuotas) | — (calculado) | `DeudasService.deudas` | `/v1/debts` |
| Hogar y miembros | `Household`, `HouseholdMember` | `BS/service/impl/HouseholdServiceImpl.java` | `/v1/household` |
| **Estado del periodo** (todas las cifras) | — (calculado) | `BS/service/impl/EstadoDelPeriodoService.java` | `/v1/estado/periodo`, `/v1/estado/historial` |
| Plan / quincena / calendario / proyección | — (calculado) | `BS/service/impl/PlanFinancieroService.java` | `/v1/plan/*` |
| Pendientes (lista) | — | `BS/service/impl/DashboardServiceImpl.java` | `/v1/dashboard/pending` |
| Excel | — | `BS/service/impl/ExcelExportService.java` | `/v1/export/excel` |

Principio que se repite en todo el código: **nada derivado se guarda**. El plan,
el saldo de un ahorro, lo que se debe en una tarjeta y "lo libre" se calculan
cada vez desde movimientos y programados (comentario de cabecera de
`PlanFinancieroService` y de `Card`). No crees tablas de totales.

Todos los endpoints son `POST`, y **la identidad sale siempre del token**
(`SecurityUtils.getAuthenticatedUserId()`), nunca del cuerpo (ver §9).

---

## 1. El periodo de presupuesto

- **Qué:** un "mes" que empieza el día de corte del usuario (`budgetStartDay`, 1–31,
  vive en el perfil de auth-service y el front lo manda en cada petición).
- **Cómo:** `BS/util/PeriodUtils.java`
  - `getStartOfPeriod(corte, fecha)`: el día `min(corte, largo del mes)` del mes de
    la fecha; si la fecha es anterior, el del mes anterior.
  - `getEndOfPeriod(corte, inicio)`: día `min(corte, largo del mes siguiente)` del
    mes siguiente, menos 1 día.
- Si el corte no llega, se usa 1 (`DashboardController.corte`, `EstadoDelPeriodoService.estado`).
  `EstadoDelPeriodoService.estado/historial` rechazan cortes fuera de 1..31.
- **Excepción importante:** la **quincena** (§7) NO usa el día de corte; usa las
  fechas de pago de los ingresos programados.
- **Cerrado:** un periodo está "cerrado" si `fin < hoy` (`EstadoDelPeriodoService.calcular`,
  variable `cerrado`). No hay acción de "cerrar mes": el cierre es solo mirar un
  periodo pasado (`/v1/estado/historial`, máximo 12 periodos).

---

## 2. Movimientos

Entidad `BS/entity/Movement.java`. Lógica en `BS/service/impl/MovementServiceImpl.java`.

### 2.1 Campos y su significado

| Campo | Significado |
|---|---|
| `userId` | Dueño **y pagador**. Lo pone el token al crear y no cambia al editar (`updateMovement`, comentario "El dueno NUNCA cambia"). Excepción: `reasignarPagador` (§2.6) y `realinearOcurrencias` (§5.5). |
| `categoryId` | Obligatorio. Su `type` (INCOME/EXPENSE) decide si es ingreso o gasto. **No hay tipo en el movimiento.** |
| `date` | Fecha. Validada entre 2000-01-01 y hoy+5 años (`validarFechaYMonto`). |
| `amount` | > 0 en el DTO (`MovementSaveDTO`, `@DecimalMin 0.01`), máx. 100.000.000.000. En BD se permite 0 (pendiente de programado sin importe, `MIG/postgresql/V2__invariantes.sql` §4). |
| `description` | La **nota** (máx. 255). Es lo que el front muestra delante en Movimientos. |
| `confirmed` | `true` = pagado/recibido. `false` = **pendiente** (por pagar / por recibir). Al crear, nulo ⇒ `true`; al editar, nulo ⇒ no se toca. |
| `active` | Borrado lógico. `false` = no existe para ninguna suma. |
| `parentMovementId` | Sub-gasto (hijo) de otro movimiento. |
| `scheduledMovementId` + `periodStart` | Viene de un programado; `periodStart` es la **clave de la ocurrencia** (§5.3). |
| `releasedAmount` | Plata liberada de un pendiente del plan (§5.6). |
| `fundedFromCategoryId` | Pagado con un ahorro (§6). |
| `cardId`, `cardPaid`, `installmentNumber`, `installmentTotal`, `purchaseId` | Tarjeta y cuotas (§8). |

**Medio de pago:** no hay campo "efectivo". El medio se deduce:
`cardId != null` ⇒ tarjeta; `fundedFromCategoryId != null` ⇒ ahorro; ninguno ⇒
"fondos del mes" (efectivo/débito). Ahorro y tarjeta son excluyentes
(`validarTarjeta`: "Se paga con el ahorro o con la tarjeta, no con los dos").
El texto que ve el usuario lo arma el front en `FE/utils/medioDePago.ts` (`comoSePago`).

En el DTO, `0` en `fundedFromCategoryId` o `cardId` significa "quitarlo" y nulo
"no lo dije" (`MovementServiceImpl.origen`, `idONulo`).

### 2.2 Qué cuenta en una suma: `Computables`

`BS/util/Computables.java` es **la única definición** de "este movimiento suma":

```java
suma(m) = m != null && m.active != false && m.parentMovementId == null
```

- Lo borrado no existe.
- **El hijo es detalle del padre, nunca un gasto aparte** (si desglosas el mercado en
  tres líneas, gastaste el total del mercado, no el total más las tres líneas).
- `importe(m)` trata `amount` nulo como 0; `total(col)` suma los que computan.

Lo usan `EstadoDelPeriodoService.calcular`, `saldoDelAhorro`,
`PlanFinancieroService.ventana` y `MovementServiceImpl.validarSaldoDelAhorro`.
**Si cambias la regla, cámbiala aquí.** Ojo: no todos los sitios la usan (ver §12).

Confirmado/no confirmado **no** está en `Computables`: cada cálculo elige
(`real` = confirmados, `comprometido` = pendientes).

### 2.3 Sub-gastos (padre/hijo)

`MovementServiceImpl.createMovement`:
- El padre debe existir, estar activo y ser visible para mí.
- Profundidad máxima 3 niveles (padre → hijo → nieto); un cuarto nivel da 400.
- **El hijo hereda la categoría del padre** (se sobreescribe la del DTO).
- Un sub-gasto no se puede dividir en cuotas.
- `getChildren(parentId)` devuelve `childrenTotal`, `remaining = padre − hijos` y
  `executionPct`. Que los hijos no cuadren con el padre está permitido.

### 2.4 Validaciones al crear/editar

`createMovement` / `updateMovement`, en este orden:
1. La categoría tiene que existir y ser visible (`ControlAcceso.puedeVerCategoria`), si no 404 "Category not found".
2. Fecha y monto en rango (`validarFechaYMonto`).
3. Si trae `scheduledMovementId`, el programado debe ser visible (`validarProgramado`).
4. Ahorro de origen válido (`validarOrigen`) y con saldo suficiente (`validarSaldoDelAhorro`).
5. Tarjeta válida (`validarTarjeta`): **mía**, activa, y la categoría debe ser EXPENSE.
6. Al editar: no se puede mover un gasto de la pareja a **mi** categoría privada (404).
7. Al editar: no se cambian las cuotas (400 "borra la compra y anótala de nuevo").
8. Al editar: `scheduledMovementId` nulo no desengancha (solo se cambia si llega valor).

Errores: cualquier excepción ⇒ `Transacciones.revertir()` + `ResultDTO(false, ..., 500)`.
Esto es necesario porque atrapar una excepción dentro de `@Transactional` no
revierte (`BS/util/Transacciones.java`).

### 2.5 Confirmar

- `confirmWithAmount(id, monto)`: pone `confirmed = true`; si llega monto distinto
  lo reemplaza; monto ≤ 0 ⇒ 400 (antes se ignoraba en silencio).
- `confirmarVarios(ids, montos)` (`/confirm-batch`): **todo o nada**. Valida todo
  antes de escribir (ids duplicados, ajenos, montos ≤ 0); salta los ya confirmados;
  si uno falla, revierte. Devuelve `confirmed` y `total` del lote.

### 2.6 Reasignar quién paga un pendiente

`reasignarPagador(id, nuevoPagador)` (`/reassign/{id}`): cambia `userId` del
movimiento. Solo pendientes (`confirmed=false`), solo en categorías **compartidas**,
y el nuevo pagador debe ser del hogar (`companerosDeHogar`). Es "esta vez lo paga
el otro"; el cambio permanente se hace en el programado (`payerUserId`).

### 2.7 Borrar

`delete(id)`: borrado lógico (`active=false`). Si es una compra a cuotas
(`purchaseId`), **se borran todas las cuotas**. Borrar un pendiente de un
programado significa "omite esta ocurrencia": no se regenera (§5.3).

### 2.8 Listar

- `getAll` (`/all`): paginado por fecha desc. Con hogar usa
  `MovementRepository.findAllWithFiltersAndHousehold`: **mis movimientos + todos
  los de categorías de mis hogares**. El filtro por alcance (HOGAR/PERSONA) lo
  hace el front.
- `getByPeriod` (`/by-period`): igual pero sin paginar.
- Ninguno filtra sub-gastos: eso lo hace el que suma.

---

## 3. Categorías

`BS/entity/Category.java`, `BS/service/impl/CategoryServiceImpl.java`.

- **Tipos:** solo `INCOME` y `EXPENSE` (`BS/enums/CategoryType.java`). **No existe
  un tipo SAVING.** Una categoría "de ahorro" es una EXPENSE cuyos programados son
  de clase `SAVING` (§6.1).
- **Personal vs del hogar:** `householdId == null` ⇒ personal (solo la ve su dueño);
  `householdId != null` ⇒ compartida con todo el hogar. Solo se puede compartir con
  un hogar del que se es miembro (403).
- **Jerarquía:** `parentId`, máximo 3 niveles (`MAX_DEPTH`), sin ciclos (`getDepth`).
- **Nombre único** por dueño y nivel, comparando sin mayúsculas, tildes ni espacios
  extra, contra las propias **y las del hogar** (`yaExisteParecida`, `normalizar`).
- **Borrar:** lógico; prohibido si tiene hijas activas o movimientos activos.
- **Árbol:** `getTree` — si el padre no es visible, el hijo sube a raíz (pasaba al
  salir de un hogar).
- `reimbursable`: se guarda y se edita, pero **ningún cálculo lo usa hoy** (era
  del arrastre de sobres, que ya no existe).

### 3.1 Modo PERSONA vs HOGAR (alcance)

El "modo" no es de la categoría, es de la **consulta**:
`EstadoDelPeriodoService.Alcance.de(tipo, persona, yo)`.

| Alcance | Qué entra |
|---|---|
| `HOGAR` (o tipo nulo) | Solo lo de **categorías compartidas** (de cualquiera del hogar). |
| `PERSONA` (persona nula ⇒ yo) | Lo que **esa persona paga** (movimientos con su `userId`, programados con ella como pagador), en categorías que **yo** veo. De otra persona, solo lo compartido. |

Pedir PERSONA de alguien que no es del hogar ⇒ 400. El front arranca en PERSONA
(yo) y el selector vive en `FE/contexts/AlcanceContext.tsx`.

---

## 4. Presupuesto (asignaciones)

`BS/service/impl/BudgetAllocationServiceImpl.java`.

- Una asignación = (categoría, periodo) → monto. **Upsert**: si ya existe una activa
  para esa categoría y `periodStart`, se actualiza (`save`). Índice único parcial
  `ux_asignacion_viva_categoria_periodo` (`MIG/postgresql/V2__invariantes.sql`).
- Solo sobre categorías visibles. `householdId` se copia de la categoría.
- `list`: oculta asignaciones de categorías INCOME y de categorías que hoy son
  privadas de otro (`visibles`): **manda cómo está la categoría HOY**.
- `copiarDelAnterior` (`/copy-previous`): copia las del periodo anterior **solo a
  categorías sin asignación** en el actual, conservando el dueño original.
- `delete`: lógico.
- El campo `status` y el "cierre automático" ya no existen (comentario en `BudgetAllocation`, V7).

**Si una categoría no tiene asignación**, el estado del periodo usa como
presupuesto lo planeado en sus programados del periodo (`planDeLineas`, §10).

---

## 5. Programados (recurrencias)

`BS/entity/ScheduledMovement.java`, `BS/service/impl/ScheduledMovementServiceImpl.java`,
`BS/util/CalendarioDeRecurrencias.java`.

### 5.1 Campos

| Campo | Regla |
|---|---|
| `frequency` | `DAILY, WEEKLY, BIWEEKLY, MONTHLY, BIMONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL`. |
| `startDate` | El **ancla**. Obligatoria. |
| `durationMonths` → `endDate` | `endDate = startDate + n meses − 1 día` (`calculateEndDate`). Duración ≤ 0 ⇒ 400. Sin duración = no termina. |
| `dayOfMonth` | Día fijo para mensual y más espaciados; si falta, el día del ancla. Recortado al último día del mes. |
| `weekOfMonth` + `dayOfWeek` | "El tercer viernes" (ISO, lunes=1). Van los dos o ninguno (400). Mandan sobre `dayOfMonth`. Ordinal 5 = "el último". |
| `weekendPolicy` | `KEEP` (defecto, nulo = KEEP), `PREVIOUS_BUSINESS_DAY` (viernes antes), `NEXT_BUSINESS_DAY` (lunes después). |
| `payerUserId` | Quién paga. Nulo ⇒ quien lo creó (`pagadorDe`). Debe ser del hogar; en categoría privada solo puede ser su dueño (`pagadorInvalido`). |
| `kind` | `PAYMENT` (defecto, nulo = PAYMENT), `DEDUCTION`, `SAVING`, `PROVISION` (`BS/enums/ScheduledKind.java`, `MIG/comun/V10__...sql`). |
| `notes` | Supuestos sin confirmar. En edición, nulo = no tocar; texto vacío = borrar. |

### 5.2 Las cuatro clases (`kind`)

| Clase | ¿Genera pendiente? | En el plan / estado |
|---|---|---|
| `PAYMENT` | Sí | Obligación con fecha ("factura"). |
| `DEDUCTION` | Sí | Descuento de nómina: en la quincena **resta del ingreso** (`netIncome`); no cuenta como factura ni como vencida, no genera alertas. |
| `SAVING` | Sí | Ahorro: sale pero no es consumo. Marca la categoría como de ahorro. |
| `PROVISION` | **No** | "Bolsa": plata separada (mercado). Lo real se anota al comprar y se descuenta de la bolsa. |

El **salario** no es especial: es un programado en una categoría `INCOME`
(típicamente `MONTHLY` o con patrón "tercer viernes"). Sus fechas definen la quincena (§7).

### 5.3 Calendario e idempotencia

`CalendarioDeRecurrencias.ocurrenciasHasta(programado, hasta)`:
- La ocurrencia n se calcula **siempre desde el ancla** (`ancla + n periodos`), nunca
  "la anterior + 1". Así el 31 vuelve a ser 31 después de febrero, y DAILY/WEEKLY/BIWEEKLY
  generan de verdad varias por mes.
- Tope de 4000 ocurrencias por enumeración (`TOPE_DE_ENUMERACION`).
- Cada ocurrencia tiene tres fechas: **canónica** (del ancla), **fecha** (tras la
  política de fin de semana, es la que se graba) y **clave**.
- **Clave** (`claveDeLaOcurrencia`): para DAILY/WEEKLY/BIWEEKLY es la fecha canónica;
  para mensual o más espaciado es **el día 1 del mes** de la canónica. Se graba en
  `Movement.periodStart`. Así, mover el cobro del 5 al 25 no genera un segundo
  cobro ese mes, y mover la fecha de un pendiente no lo regenera.
- Índice único `ux_movement_ocurrencia (scheduled_movement_id, period_start)`
  (`MIG/postgresql/V11__una_ocurrencia_un_movimiento.sql`): si dos personas
  refrescan a la vez, la segunda inserción falla.
- Filas viejas sin `periodStart` se reconocen por su fecha dentro de la ventana de
  la clave (`cubiertaPorUnaFilaVieja`, `ventanaDeLaClave`).

### 5.4 Generar pendientes

`ScheduledMovementServiceImpl.generatePending(userId, corte)` (`/v1/scheduled/generate-pending`),
transacción propia (`REQUIRES_NEW`):

1. Lee programados visibles (míos + de categorías del hogar).
2. Salta `PROVISION` y los que terminaron antes del periodo actual.
3. Hasta dónde: **lo mío hasta el fin de mi periodo; lo de otro solo hasta hoy**
   (no se inventan pendientes futuros con el calendario de otra persona).
4. Faltantes = ocurrencias cuya clave no existe en **ningún** movimiento del
   programado, **vivo o borrado** (borrar = omitir).
5. Si hay más de 5 atrasadas (`TOPE_DE_MATERIALIZACION`), **no crea ninguna** de
   ese programado: las devuelve como `proposals` (máx. 50 por programado) y
   `needsReview=true`. Se aceptan con `/v1/scheduled/materialize`, que revalida
   cada clave contra el calendario (`buscarPorClave`) y salta las existentes.
6. `crearPendiente`: `userId = pagadorDe(programado)`, `amount = programado.amount`
   (o 0), `description = name`, `confirmed=false`, `periodStart = clave`.

En el front hoy solo lo llama `FE/screens/scheduled/ScheduledScreen.tsx`. Las
pantallas de Inicio/Presupuesto no lo necesitan para sus cifras porque el estado
cuenta las ocurrencias no generadas como `PREVISTO` (§10).

### 5.5 Editar un programado

`updateScheduled` → `realinearOcurrencias(p, cambioElCalendario, corte, antes)`:
- El dueño (`userId`) nunca cambia.
- **Si ahora es PROVISION:** se **borran físicamente** (`deleteAll`) sus pendientes sin confirmar.
- **Si cambió el calendario** (frecuencia, fechas, día, patrón, política):
  - Pendientes sin confirmar de hoy en adelante y sin liberar ⇒ `deleteAll` (borrado
    físico, para que el siguiente refresco los genere con el calendario nuevo).
  - Lo demás (confirmado, atrasado, omitido, liberado) se **re-clava** a la
    ocurrencia nueva del mismo periodo de presupuesto, la más cercana, sin repetir.
    Lo que no tiene pareja queda con `periodStart = null`.
- **Si no cambió el calendario:** solo lo que cambió (importe, categoría, nombre,
  pagador) pasa a los pendientes sin confirmar. Tocar solo la nota no pisa un
  pendiente corregido a mano.

### 5.6 Liberar plata de un pendiente

`MovementServiceImpl.liberar(id, monto)` (`/release/{id}`) y `deshacerLiberacion`
(`/unrelease/{id}`), migración `MIG/comun/V12__plata_liberada.sql`:
- Solo pendientes. Monto entre 1 y lo pendiente; sin monto = todo.
- `releasedAmount += monto`. Si se libera todo: `active=false` (cerrado sin pagar).
  Si es parte: `amount -= monto`, sigue pendiente por el resto.
- **El presupuesto no cambia**: lo planeado = `amount + releasedAmount`
  (`PlanFinancieroService.Fecha.planeado`). En el plan la fecha sale como `LIBERADO`.
- Deshacer usa `findById` (no `ActiveTrue`) a propósito, para encontrar el inactivo.

### 5.7 Borrar un programado

`delete`: lógico, y desactiva sus pendientes **sin confirmar**. Lo confirmado queda.

---

## 6. Ahorros

### 6.1 Qué es un ahorro

Una categoría EXPENSE es "de ahorro" en el estado del periodo si **tiene algún
programado visible de clase SAVING y ninguno de clase PAYMENT/DEDUCTION**
(`EstadoDelPeriodoService.calcular`, bloque `deAhorro`/`dePago`; y `esAhorro =
c.ahorro && !c.pagoOFactura`). Se decide por sus programados, no por si este periodo
tiene una fecha suya.

- **Depositar** = anotar un gasto (confirmado) en esa categoría.
- **Meta del periodo** = asignación, o lo planeado en sus programados SAVING.
- Estado: `META_CUMPLIDA` si real ≥ meta; `META_INCUMPLIDA` si el periodo está cerrado y no; si no `EN_CURSO`. Meta 0 nunca se incumple.
- `currentGoal = max(0, meta − liberado)` (lo que sigue pidiendo el mes).

### 6.2 Saldo

`EstadoDelPeriodoService.saldoDelAhorro(cat, alcance, hasta)`:

```
saldo = Σ confirmados activos y computables EN la categoría (fecha ≤ fin del periodo)
      − Σ confirmados activos y computables con fundedFromCategoryId = categoría (fecha ≤ fin)
```

No se reinicia cada mes. En HOGAR cuenta lo de todos; en PERSONA, lo que metió y
sacó esa persona. Sale como `categories[].balance` y `totals.savingsBalance`.

### 6.3 Pagar con el ahorro (`fundedFromCategoryId`)

Migración `MIG/comun/V13__gasto_pagado_con_el_ahorro.sql`. Reglas (`MovementServiceImpl.validarOrigen`, `validarSaldoDelAhorro`):
- El origen debe ser una categoría EXPENSE visible, **distinta** de la del gasto, y el movimiento debe ser un gasto.
- **No se puede dejar el ahorro en negativo**: el saldo que se valida es el **de
  quien paga** en ese ahorro (lo que él metió menos lo que él pagó con él, todas
  las fechas), no el del hogar. Al editar se suma de vuelta lo que el propio
  movimiento ya restaba (`yaSalia`).
- No se combina con tarjeta.

**Cómo cuenta** (`EstadoDelPeriodoService.calcular`, bloque "Pagado con plata ya ahorrada"):
- **Sí** es gasto de su categoría (`actual` lo incluye; la reparación del carro es gasto del carro).
- **No** baja lo libre: se suma a `delAhorro`/`totals.fromSavings` y se resta de lo gastado al calcular `freeToSpend`.
- Baja el saldo del ahorro de origen (`withdrawn`, `withdrawals`).
- El presupuesto de la categoría se mide con `realDelMes = actual − fromSavings`
  (no "la pasa" por algo pagado con ahorros).
- Pendientes pagados con ahorro (`comprometidoDelAhorro`) tampoco cuentan en `toPay`.
- En la quincena, `PlanFinancieroService.ventana` **excluye** de `otherSpending` todo movimiento con `fundedFromCategoryId`.

`totals.netSaved = saved − fromSavings`.

---

## 7. Plan financiero y quincena

`BS/service/impl/PlanFinancieroService.java`. Todo de solo lectura.

### 7.1 Piezas

- `visibles(yo)` → lista de `Linea` (programado + categoría + pagador + clase + sus
  movimientos indexados por clave). Incluye pendientes inactivos **solo si fueron liberados**.
- `fechas(linea, desde, hasta, hoy)` → `Fecha` con estado:

| Estado | Condición (en este orden) |
|---|---|
| `PROVISION` | La línea es PROVISION. |
| `LIBERADO` | El movimiento existe y está inactivo (liberado del todo). Monto 0. |
| `PAGADO` | Movimiento confirmado. |
| `VENCIDO` | Fecha < hoy y no confirmado (exista o no el movimiento). |
| `PENDIENTE` | Movimiento existe, no confirmado, fecha ≥ hoy. |
| `PREVISTO` | Aún no generado. |

  El monto es el **real** del movimiento si existe; si no, el del programado. Si el
  pendiente existe, manda **su** fecha. Se enumeran 3 días de más para no perder
  pagos movidos al viernes anterior.

### 7.2 La quincena (`quincena(yo, referencia)`, `/v1/plan/quincena`)

- Solo líneas donde **yo soy el pagador** (`mias`).
- Días de pago = fechas de **mis** programados INCOME entre hoy−70 y hoy+100.
- Ventana actual = del último pago ≤ hoy hasta el día antes del siguiente pago.
  Sin pagos: de hoy a hoy+1 mes−1. También se calcula la siguiente (`next`).
- `overdueFromBefore`: VENCIDOS de los 90 días anteriores (sin ingresos ni provisiones).

### 7.3 `ventana(...)`: fórmula de lo que queda

```
grossIncome    = Σ fechas de líneas INCOME
deductions     = Σ fechas DEDUCTION
netIncome      = grossIncome − deductions
payments       = Σ fechas PAYMENT            (paymentsPaid = las PAGADO)
savings        = Σ fechas SAVING
provisions     = Σ fechas PROVISION
otherIncome    = Σ mis movimientos confirmados INCOME en [inicio, min(hoy, fin)]
                 sin scheduledMovementId y sin fundedFromCategoryId, computables
otherSpending  = lo mismo pero EXPENSE
provisionsLeft = Σ por categoría max(0, provisión − otherSpending de esa categoría)

leftover = netIncome + otherIncome − payments − savings − provisionsLeft − otherSpending
tight    = leftover < 0
```

Notas:
- Los montos de las fechas son reales si ya existe el movimiento (lo pagado con
  otro valor cuenta con el valor real).
- Lo gastado en una categoría con provisión se resta una sola vez (consume la bolsa).
- Las compras con tarjeta **sí** entran en `otherSpending` el día de la compra.
- Una cuota adelantada (§8.4) tiene `scheduledMovementId`, así que entra por el plan, no por `otherSpending`.

### 7.4 Otros endpoints del plan

- `cuotas(mias, hoy)`: programados (no INCOME) con `endDate ≥ hoy`: cuotas totales,
  pagadas (estado PAGADO), restantes, `remainingTotal` (Σ montos no pagados),
  `endDate` = última que falta, `monthlyFreed = mensualizado(p)`.
- `mensualizado(p)`: DAILY ×30; WEEKLY ×52/12; BIWEEKLY ×26/12; MONTHLY ×1; BIMONTHLY /2; QUARTERLY /3; SEMIANNUAL /6; ANNUAL /12.
- `alertas`: VENCIDO, PROXIMO (≤ 3 días, `DIAS_DE_AVISO`), CUOTA_TERMINA (quedan ≤ 2), QUINCENA_AJUSTADA. Sin ingresos, provisiones ni deducciones.
- `calendario(desde, hasta)`: todas las fechas visibles, máx. 400 días.
- `responsabilidades(corte, ref)`: quién paga qué en el periodo; de los demás solo lo compartido. Cada fecha va con el **pagador efectivo** (el del pendiente si existe).
- `proyeccion(corte, n)`: `ventana` por periodo (máx. 12) + cuotas que terminan y cuánto liberan.

---

## 8. Tarjetas de crédito y deudas

`BS/service/impl/DeudasService.java`, migraciones `MIG/comun/V15`, `V17`, `V18`.

### 8.1 Regla central: sin doble conteo

- **Una compra con tarjeta es un gasto normal de su categoría**, confirmado, el día
  de la compra (`createMovement` la crea con `confirmed` normal y `cardId`).
- **Pagar la tarjeta NO es un gasto**: crea un `CardPayment` (tabla aparte) y marca
  compras como `cardPaid=true`. No crea ningún `Movement`, así que no aparece en
  Movimientos, ni en `spent`, ni en lo libre.
- La tarjeta **no guarda saldo**: lo que se debe = Σ compras activas con
  `cardPaid=false` − abono (`credit`).
- Solo se paga con tarjeta un gasto (EXPENSE), con una tarjeta **mía**.

### 8.2 Compras a cuotas

`MovementServiceImpl.crearCompraACuotas` (solo con tarjeta, 2..72 cuotas):
- N movimientos, uno por mes desde la fecha de compra (`date + (k−1) meses`), todos `confirmed=true`.
- Monto: `floor(total / N)` en pesos enteros; el resto va en la **primera** cuota.
- Todas comparten `purchaseId` = id de la primera. Borrar una borra todas;
  cambiar la tarjeta cambia todas (`cambiarTarjeta`).
- Cada cuota cuenta como gasto **en su mes**, no todo el mes de la compra.

### 8.3 Corte, pago y `payableNow`

`DeudasService.resumen(card, yo, hoy)`:
- `ultimoCorte`: el último día de corte ≤ hoy (sin corte, hoy).
- Por cada compra no pagada: `dueNow` (fecha ≤ corte), `currentCycle` (corte < fecha ≤ hoy), resto = futuras (`upcoming`).
- El abono se aplica de lo más viejo a lo más nuevo: primero `dueNow`, luego `currentCycle`, luego en la lista de compras.
- `owed = Σ no pagadas − abono`; **`payableNow = dueNow + currentCycle`** (todo lo comprado hasta hoy menos abono).
- De la tarjeta de la pareja solo se ven compras en categorías que yo veo.

`pagarTarjeta(yo, id, fecha, monto)` (`/v1/cards/pay/{id}`, cuerpo `{upTo, amount}`):
- Solo el dueño. Fecha nula = hoy; fecha futura ⇒ 400.
- `máximo = Σ compras no pagadas con fecha ≤ fecha del pago − abono`. Si es 0 ⇒ 400.
- Monto nulo = el máximo; ≤ 0 o > máximo ⇒ 400.
- `bolsa = abono + pago`; se marcan `cardPaid=true` las compras más viejas **que quepan completas**; lo que sobra queda como nuevo `credit`.
- Se guarda un `CardPayment(cardId, userId, amount, paymentDate)`.
- No hay "deshacer pago".

`borrarTarjeta`: solo si no tiene compras sin pagar. La tarjeta nueva toma el primer hogar del dueño como `householdId`.

### 8.4 Deudas en cuotas (programados con fin)

`DeudasService.deudas(yo, ref)` (`/v1/debts/all`):
- Tarjetas visibles (mías + del hogar) con su `owed`.
- `loans` = `plan.cuotas(...)` sin SAVING ni PROVISION y sin las que ya no tienen cuotas pendientes.
- `totalOwed = owedOnCards + owedInInstallments`; `monthlyInstallments = Σ monthlyFreed`.

`adelantarCuota(yo, programadoId)` (`/v1/debts/advance/{id}`): paga **hoy** la
**última** cuota que falta (no PAGADO ni LIBERADO). Si su pendiente existe se
confirma con fecha de hoy; si no, se crea confirmado con la clave de esa última
ocurrencia. Cuenta como gasto de este mes y la deuda termina antes.

---

## 9. Seguridad de datos

### 9.1 Identidad

- `BS/security/JWTAuthorizationFilter.java` lee el claim `userId` y lo pone en
  `authentication.details`; los roles salen del claim `roles`.
- `BS/util/SecurityUtils.getAuthenticatedUserId()` es **la única** fuente de identidad.
  Regla escrita: ningún servicio acepta un `userId` de entrada. Los DTO conservan
  `userId` por compatibilidad, pero los controladores lo **pisan** con el del token
  (ej. `MovementController.getAll`).
- Excepción explícita: en `/household/add-member`, `/remove-member`, `/display-name`
  y `/movements/reassign`, el `userId`/`payerUserId` del cuerpo es el **objetivo**,
  no quien llama, y el servicio lo valida.
- Todo excepto `/actuator/**` y swagger exige token (`SecurityConfig`).

### 9.2 Quién ve qué (`BS/util/ControlAcceso.java`)

- Un dato es tuyo si lo creaste, o si cuelga de una categoría **compartida** de un hogar al que perteneces (`puedeVer(dueño, categoriaId)`).
- Una categoría: tuya o compartida en tu hogar (`puedeVerCategoria`).
- Ante la duda, no. Nulo nunca concede.
- Un recurso ajeno responde **404 "no existe"**, nunca "no puedes" (no confirmar que el id existe; los ids son consecutivos). Ver `MovementServiceImpl.puedoTocar`.
- Las consultas con hogar del repositorio repiten la misma regla: `m.userId = :yo OR m.categoryId IN (categorías activas de mis hogares)` (`MovementRepository.findHouseholdConfirmed`, `findHouseholdPending`, etc.; `ScheduledMovementRepository.findHouseholdActive`).
- Tarjetas: la ve su dueño y su hogar (`CardRepository.visibles`), solo el dueño la usa, paga o borra. `CardPayment` solo lo ve el dueño (`lastPaymentAmount` solo si `mine`).

### 9.3 Hogar

`HouseholdServiceImpl`:
- Crear: quien crea es `OWNER`.
- Invitar: solo el OWNER, por **correo** (el id lo resuelve auth-service vía `DirectorioDeUsuarios`), y solo a quien **no** pertenece a ningún hogar activo. Mismo mensaje para "no existe" y "ya tiene hogar" (no revelar correos registrados).
- Salir/expulsar: el OWNER saca, o uno mismo se sale; el OWNER no se puede sacar. Al salir, `limpiarRastroDelExpulsado`: sus subcategorías personales colgadas de categorías del hogar suben a raíz, y sus asignaciones sobre lo del hogar se desactivan.
- Apodo (`displayName`): lo pone el OWNER o el propio miembro. Es el nombre que se ve en pantalla (budget-service no puede leer el nombre de la cuenta del otro, V10).

### 9.4 RLS de PostgreSQL

`MIG/postgresql/V9__row_level_security.sql`, `V16`, `V19`; código en
`BS/security/ContextoDeUsuarioEnLaBase.java`, `BS/config/RlsConfig.java`,
`BS/security/ComprobacionDeRls.java`.

- Políticas por tabla leyendo `current_setting('app.user_id')` (función `oc_usuario_actual()`); funciones `SECURITY DEFINER` (`oc_mis_hogares`, `oc_categorias_de_mis_hogares`) para cortar la recursión.
  - `movement`: tuyo o en categoría de tus hogares; escribir solo con tu `user_id`.
  - `category`, `allocation`, `card`: tuyo o de tus hogares; escribir solo lo tuyo.
  - `scheduled_movement`, `card_payment`: **solo lo tuyo**.
- El aspecto `ContextoDeUsuarioEnLaBase` hace `set_config('app.user_id', yo, true)` (local a la transacción) dentro de cada método de clases `@Transactional` de `service.impl`. `RlsConfig` fija el orden (transacción 100, aspecto 200) para que corra dentro.
- **Hoy RLS está dormido**: la app conecta como superusuario (`postgres`), que se salta las políticas. `ComprobacionDeRls` lo avisa en el arranque. El aislamiento real lo da el código (§9.2). Activarlo es un cambio de credenciales descrito al final de V9 — **lee §14 (puntos 1 y 2) antes de hacerlo**.

---

## 10. Estado del periodo y pantalla de inicio

### 10.1 `EstadoDelPeriodoService.calcular(...)` — la fuente única de cifras

Endpoint `/v1/estado/periodo` (cuerpo: `budgetStartDay`, `referenceDate`, `scope`, `personUserId`).
Pasos:

1. **Asignaciones** del periodo (sin INCOME; manda cómo está la categoría hoy; las privadas de otro no entran).
2. **Programados** (`plan.visibles(yo)` + `plan.fechas` en [ini, fin]):
   - INCOME ⇒ `incomeExpected`.
   - PROVISION ⇒ `bolsa` de la categoría.
   - Otras: si no PAGADO ⇒ suma a `toPay` (`porPagarDelPlan`); si PAGADO ⇒ `realDeElementos`. DEDUCTION ⇒ `payrollDeductions` (no cuenta como factura).
   - `planDeLineas` = Σ planeado (monto + liberado).
   - `responsibilities` por pagador (siempre de ambos, sobre lo que yo veo).
3. **Movimientos** del periodo (solo `Computables.suma`, filtrados por alcance):
   - Confirmados INCOME ⇒ `incomeReceived`; si no vienen de un programado también suman a `incomeExpected` (ingreso extra).
   - Confirmados EXPENSE ⇒ `actual` de la categoría; si tienen `fundedFromCategoryId` ⇒ `fromSavings` y `withdrawn` del ahorro.
   - Pendientes EXPENSE ⇒ `committed`; si no son de un programado y no se pagan con ahorro ⇒ también suman a `toPay`.
4. **Por categoría**: `allocated` (asignación o `planDeLineas`), `realDelMes = actual − fromSavings`,
   `available = allocated − realDelMes − comprometidoDelMes`, `difference = allocated − realDelMes`,
   estado `DENTRO / EN_RIESGO / EXCEDIDO / SIN_PRESUPUESTO` (o de meta si es ahorro),
   `otherSpending = max(0, realDelMes − realDeElementos)`,
   `provisionLeft = max(0, bolsa − otherSpending − comprometidoSinElemento)`.
5. **Totales**:

```
spent           = Σ actual de categorías NO de ahorro (incluye lo pagado con ahorro)
saved           = Σ actual de categorías de ahorro
fromSavings     = Σ gastos confirmados pagados con ahorro
toPay           = lo no pagado del plan (PENDIENTE+VENCIDO+PREVISTO) + pendientes manuales
provisionsLeft  = Σ provisionLeft

freeToSpend = incomeExpected − (spent − fromSavings + saved + toPay + provisionsLeft)
freeNow     = incomeReceived − (el mismo paréntesis)
```

Otros: `incomePending`, `budgeted`, `unassigned = incomeExpected − budgeted`,
`committed`, `savingsGoal`, `savingsRatePct`, `unbudgetedSpending`, `released`,
`netSaved`, `savingsBalance`, `executionPct = spent / (budgeted − savingsGoal)`,
y `compliance` (facturas pagadas/vencidas, categorías dentro/excedidas, metas).

`/v1/estado/historial` llama a `calcular` para N periodos (1..12, defecto 6) sin detalle.

### 10.2 La pantalla de inicio

`FE/screens/inicio/InicioScreen.tsx`, método `traer`. Llama en paralelo:

| Llamada | Para qué |
|---|---|
| `POST /v1/estado/periodo` (alcance del selector) | Dona, ingresos, por pagar, libre, ahorros. |
| `POST /v1/dashboard/pending` | Contar pendientes (aviso "te toca confirmar") — `DashboardServiceImpl.getPending`: pendientes del periodo + pendientes de periodos anteriores, con categoría y `payerName`. |
| `POST /v1/categories/all` | Poner en la dona las categorías sin movimiento. |
| `POST /v1/debts/all` | Recuadro de deudas (filtrado por alcance en el front). |

Lo que se pinta:
- **Centro de la dona:** ingresos = `totals.incomeReceived`; gastos = `max(0, spent − fromSavings)`; presupuesto = `budgeted − savingsGoal`.
- **Porciones:** `categories[].actual` de las EXPENSE (las SAVING aparecen con 0).
- **Por pagar:** `totals.toPay` (incluye previstos aún no generados; el número de pagos sale de la lista de pendientes).
- **Libre para gastar:** `totals.freeToSpend`.
- **Ahorros:** `totals.savingsBalance`.
- **Deudas:** `FE/utils/deudas.ts` `deudasDelAlcance` (`totalOwed`, `monthlyInstallments`).

---

## 11. Reglas que parecen bug pero son decisiones

Citadas de comentarios del código (parafraseadas).

1. **Borrar un pendiente no lo regenera** — `ScheduledMovementServiceImpl.ocurrenciasQueFaltan`: las ocurrencias muertas cuentan como existentes; borrar es "omitir".
2. **La clave de un mensual es el mes, no el día** — `CalendarioDeRecurrencias.claveDeLaOcurrencia`: cambiar el día de cobro no genera un segundo cobro ese mes.
3. **Más de 5 atrasadas ⇒ no se crea ninguna** — `ScheduledMovementServiceImpl.TOPE_DE_MATERIALIZACION`: la app no inventa historial; propone.
4. **Lo de otro solo se genera hasta hoy** — `generatePending`, comentario "HASTA DONDE SE GENERA": no adelantar pendientes ajenos con tu día de corte.
5. **WeekendPolicy por defecto KEEP** — `BS/enums/WeekendPolicy.java`: mover la fecha es inventar un dato y cambiaría de periodo gastos existentes.
6. **"Quinto viernes" = el último**, nunca el primero del mes siguiente — `CalendarioDeRecurrencias.elEnesimoDiaDeLaSemana`.
7. **Duración de N meses termina el día anterior** — `calculateEndDate`: 6 meses desde el 15-ene terminan el 14-jul.
8. **PROVISION no genera pendiente** — `ScheduledKind`, `generatePending`: separar plata no es gastarla; si no, el mercado contaría dos veces.
9. **DEDUCTION no avisa ni cuenta como factura vencida** — `PlanFinancieroService.alertas`, `EstadoDelPeriodoService.calcular`: la empresa lo cobra sola.
10. **Liberar no cambia el presupuesto** — `MovementServiceImpl.liberar`, `EstadoDelPeriodoService`: el cierre debe poder decir "se quería ahorrar X y no se ahorró".
11. **Lo pagado con ahorro sí es gasto de su categoría pero no baja lo libre** — `EstadoDelPeriodoService.calcular`.
12. **El saldo validado al pagar con ahorro es el de quien paga, no el del hogar** — `MovementServiceImpl.validarSaldoDelAhorro`.
13. **Pagar la tarjeta no es gasto; la compra sí, el día de la compra** — `DeudasService` cabecera, `CardPayment`.
14. **Las cuotas futuras se crean ya confirmadas** — `crearCompraACuotas`: cada cuota cuenta como gasto en su propio mes.
15. **El resto de la división va en la primera cuota** — `crearCompraACuotas`.
16. **Solo se borra una tarjeta saldada** — `DeudasService.borrarTarjeta`.
17. **Recurso ajeno ⇒ 404, no 403** — `MovementServiceImpl.puedoTocar` y similares.
18. **Ingreso suelto también es "esperado"** — `EstadoDelPeriodoService.calcular`: una venta o bono que ya llegó suma a `incomeExpected` y sale en la lista.
19. **`freeToSpend` usa lo esperado, no lo recibido** — si la quincena de la pareja no ha llegado, el hogar está "esperando", no en rojo; lo de hoy es `freeNow`.
20. **Una categoría es de ahorro por sus programados, no por el periodo** — `EstadoDelPeriodoService.calcular` bloque `deAhorro`.
21. **Manda cómo está la categoría HOY** — asignaciones de una categoría que pasó a privada desaparecen del hogar (`EstadoDelPeriodoService` paso 1, `BudgetAllocationServiceImpl.visibles`).
22. **Copiar el presupuesto anterior no pisa lo ya puesto** — `copiarDelAnterior`.
23. **Subcategoría con padre invisible sube a raíz** — `CategoryServiceImpl.getTree`.
24. **Invitar a alguien que ya tiene hogar falla con el mismo mensaje que si no existiera** — `HouseholdServiceImpl.noSePuedeAgregar`.
25. **`confirmed` nulo al editar no confirma** — `MovementSaveDTO`: si no, corregir el valor de un pendiente lo daba por pagado.
26. **Cambiar las cuotas de una compra exige borrarla** — `updateMovement`.
27. **RLS puesto pero dormido** — V9 y `ComprobacionDeRls`: es el estado previsto hasta cambiar credenciales.
28. **Generar pendientes corre en transacción propia** — `generatePending` (`REQUIRES_NEW`): si choca con el índice V11, el resto sigue.
29. **Importe 0 permitido en BD** — `V2__invariantes.sql` §4: un programado sin importe (la luz) genera su pendiente en 0 y se completa al confirmar (aunque el DTO de movimiento exige > 0 al guardar a mano).

---

## 12. Cálculos que existen en más de un sitio

**Antes de calcular un total nuevo, búscalo aquí.** Si necesitas una cifra, pídela
a `EstadoDelPeriodoService` o `PlanFinancieroService`; no la sumes de nuevo.

| Cálculo | Sitios | Diferencias / riesgo |
|---|---|---|
| "¿Este movimiento suma?" | `BS/util/Computables.suma` (canónico); `FE/screens/movimientos/MovimientosScreen.tsx` (`grupos`: `active !== false && !parentMovementId`) | El front la reimplementa a mano. `DeudasService.resumen`/`pagarTarjeta` y `ExcelExportService` **no** la usan (un sub-gasto con `cardId` o en el Excel sumaría junto a su padre). |
| Pagador efectivo de una fecha | `EstadoDelPeriodoService.pagadorDe(Linea, Fecha)` y `PlanFinancieroService.pagadorEfectivo` | Código idéntico duplicado. |
| Pagador de un programado | `ScheduledMovementServiceImpl.pagadorDe(p)` | Canónico; úsalo. |
| Saldo de un ahorro | `EstadoDelPeriodoService.saldoDelAhorro` (por alcance, hasta fin del periodo) y `MovementServiceImpl.validarSaldoDelAhorro` (por pagador, todas las fechas) | Distintos a propósito, pero la fórmula está copiada. |
| Qué categoría es de ahorro | `EstadoDelPeriodoService.calcular` (SAVING y no PAYMENT/DEDUCTION, programados visibles) y `ExcelExportService.categoriasDeAhorro` (cualquier SAVING activo de **toda la tabla**, `findAll`) | Pueden dar respuestas distintas. El front (`useAhorros`, `AhorrosScreen`) usa `kind === 'SAVING'` del estado: bien. |
| Gastos del mes sin ahorro | Back: `freeToSpend` usa `spent − fromSavings`. Front: `InicioScreen` `gastosDelMes = max(0, spent − fromSavings)`; `MovimientosScreen` `salidas`/`conAhorros` filtrando `fundedFromCategoryId` | Tres implementaciones. |
| Presupuesto de gasto | Back: `executionPct` usa `budgeted − savingsGoal`. Front: `InicioScreen` `presupuestoDeGasto` | Duplicado. |
| Cuotas que faltan de un programado | `PlanFinancieroService.cuotas` (por estado ≠ PAGADO, montos reales, desde el inicio) y `ScheduledMovementServiceImpl.enriquecer` (`remainingOccurrences` = ocurrencias con fecha ≥ hoy; `remainingTotal = amount × n`) | **Dan cifras distintas**: una vencida sin pagar cuenta en la primera y no en la segunda; una cuota adelantada al revés. |
| Total de deudas por alcance | Back: `DeudasService.deudas` (`totalOwed`, `monthlyInstallments` = Σ `monthlyFreed`). Front: `FE/utils/deudas.ts` `deudasDelAlcance` recalcula filtrando por persona, con fallback `monthlyFreed || installmentAmount` | El front recalcula; el back no tiene filtro por alcance. |
| Lo que se debe en tarjeta / pagable | `DeudasService.resumen` (`owed`, `payableNow`) y `DeudasService.pagarTarjeta` (`maximo`) | Misma idea con código separado; coinciden cuando la fecha del pago es hoy. |
| Inicio y fin de periodo | `BS/util/PeriodUtils` y `FE/utils/periodUtils.ts` | Si cambias la regla del corte, cambia los dos. |
| Pendientes por pagar (conteo/monto) | Back: `totals.toPay`. Front: `InicioScreen` (`porPagar`, `misPendientes`) y `PorConfirmarScreen` suman `/dashboard/pending` | `toPay` incluye PREVISTOS (no generados) y los de la lista no; Inicio muestra la diferencia como "+ previstos". |
| "Otros gastos" fuera del plan | `PlanFinancieroService.ventana` (`otherSpending`: míos, hasta hoy, sin programado ni ahorro) y `EstadoDelPeriodoService` (`otherSpending` por categoría = `realDelMes − realDeElementos`) | Definiciones distintas con el mismo nombre de campo. |
| Totales del Excel | `ExcelExportService` (fórmulas SUMIFS por "Ingreso"/"Egreso"/"Ahorro" y "Ejecutado"/"Presupuestado") | No coincide con el estado: no excluye sub-gastos, no separa lo pagado con ahorro, no cuenta previstos ni bolsas. |
| Visibilidad hogar | `ControlAcceso`, las `@Query` "Household" de los repositorios y las políticas RLS de V9 | Tres copias de la misma regla; cambiarla exige tocar las tres. |

---

## 13. Checklist antes de tocar algo

- ¿Suma dinero? Usa `Computables` y, si es una cifra de pantalla, añádela a `EstadoDelPeriodoService.calcular` en vez de crear otra suma.
- ¿Lee datos de otra persona? Pasa por `ControlAcceso` o por las consultas "Household" y responde 404 si no.
- ¿Crea movimientos desde programados? Usa la clave de `CalendarioDeRecurrencias` y respeta el índice V11.
- ¿Atrapa excepciones en un `@Transactional`? Llama a `Transacciones.revertir()`.
- ¿Nueva tabla? Añade su política RLS en `MIG/postgresql/` (como V16/V19) y piensa en el `WITH CHECK` (ver §9.4).
- ¿Cambia una regla del periodo? `PeriodUtils` y `FE/utils/periodUtils.ts`.

---

## 14. Rarezas encontradas (sin comentario que las justifique)

No se han corregido; se listan para que nadie las tome por reglas ni las "arregle"
sin pedir permiso (sistema en producción).

1. **RLS rompería flujos si se activa tal cual.** Las políticas de V9 no cuadran con lo que hace el código:
   `p_scheduled` solo deja ver programados propios (pero `findHouseholdActive` lee los de categorías del hogar);
   `p_movement` `WITH CHECK (user_id = yo)` bloquearía `crearPendiente` cuando el pagador es la pareja,
   `reasignarPagador`, `realinearOcurrencias` (cambio de pagador) y `copiarDelAnterior` (copia con el dueño original).
2. **El aspecto RLS no cubre Estado ni Plan.** `ContextoDeUsuarioEnLaBase` usa `@within(Transactional)` (anotación a nivel de clase);
   `EstadoDelPeriodoService` y `PlanFinancieroService` solo la tienen en métodos. Con RLS activo esas consultas irían sin `app.user_id` y no verían filas.
3. **Borrado físico en un sistema de borrado lógico.** `ScheduledMovementServiceImpl.realinearOcurrencias` usa `deleteAll` (pendientes futuros al cambiar el calendario, y todos los sin confirmar al pasar a PROVISION).
4. **Posible fuga de nombre de categoría privada en HOGAR.** En `EstadoDelPeriodoService.calcular`, un gasto en categoría compartida pagado con un ahorro **privado** crea el acumulador del ahorro (`acum(...)` no mira el alcance) y, como `withdrawn > 0`, esa categoría privada sale en la lista del hogar.
5. **`CategoryServiceImpl.updateCategory`**: el primer chequeo de duplicado usa `dto.getUserId()` (del cuerpo, normalmente nulo); además cualquier miembro que vea una categoría compartida puede cambiarle el tipo (INCOME↔EXPENSE, aun con movimientos) o volverla privada (`householdId = null`).
6. **Sub-gastos con tarjeta**: `validarTarjeta` no impide `cardId` en un hijo; `DeudasService.resumen`/`pagarTarjeta` no usan `Computables`, así que padre e hijo con tarjeta sumarían dos veces en lo que se debe.
7. **`Category.reimbursable`** se guarda pero no lo usa ningún cálculo. `BudgetAllocationRepository.findTodasParaElArrastre` y `ScheduledMovementRepository.findActiveInPeriod` no tienen llamadas (restos del arrastre).
8. **Comentarios que hablan de "el panel"** (`generatePending`, `DashboardServiceImpl`, `ExcelExportService`): ese panel ya no existe; `generate-pending` solo lo llama la pantalla de Programados.
9. **Front con texto desactualizado**: `FE/screens/ahorros/AhorrosScreen.tsx` dice "Un ahorro es una categoría de tipo Ahorro", pero ese tipo no existe (§6.1).
10. **`mensualizado`**: DAILY ×30 mientras WEEKLY/BIWEEKLY usan 52/12 y 26/12 (año real).
11. **`LoadData`** siembra datos demo si `app.seed-data-enabled` (por defecto `true`) y la tabla de categorías está vacía.
12. **Pago de tarjeta sin reverso**: borrar o editar una compra ya marcada `cardPaid` no toca el `CardPayment` ni el `credit`.
