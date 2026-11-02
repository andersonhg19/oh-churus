# Plan de pruebas adversariales de la lógica financiera (budget-service)

**Objetivo:** encontrar dónde las cifras de dinero están mal, no hacer que las pruebas pasen. Una prueba roja que expone un bug real cuenta como éxito; se deja roja y marcada `@Tag("bug-conocido")`, con un comentario que dice qué se esperaba y qué pasó.

**Código:** `backend/budget-service/src/test/java/com/ohchurus/budget/adversarial/` (7 clases y una base común `EscenarioFinanciero`). Son pruebas `@SpringBootTest` con H2, MockMvc y JWT, en el mismo estilo que `coherencia/ElCierreDelMesCuadraTest`.

**Cómo correrlas** (desde `backend/`):

```
mvn -q -B -pl budget-service test -Dtest='com.ohchurus.budget.adversarial.*Test'
```

Para excluir los bugs conocidos: añadir `-DexcludedGroups=bug-conocido`.

**Base de código probada:** el trabajo en curso (sin commit) del checkout principal, con `EstadoController`, `EstadoDelPeriodoService`, `Transacciones` y la V11. Esta rama solo versiona las pruebas y este documento, así que compilan una vez integrado ese trabajo.

**Fechas:**
- Los escenarios del pasado usan el periodo cerrado del 28-sep-2026 al 27-oct-2026 (corte 28).
- Los futuros usan 2027.
- Los que dependen de "hoy" (VENCIDO, generación de pendientes, periodo en curso) calculan sus fechas desde `LocalDate.now()`, así que son deterministas cualquier día. La única excepción es un `assumeTrue` que solo se salta en casos imposibles.

**Hogar de prueba:** Andy (1) y Samy (2). Un extraño (99) sin hogar.

---

## 1. Escenarios por tema

### 1.1 Valores y consolidados — `LosValoresCuadranEntrePantallasTest`

El escenario tiene solo categorías compartidas y ningún reparto, para que las definiciones coincidan por construcción. Incluye, a propósito, una apertura, una liquidación, un sub-gasto y una transferencia (en gasto y en ingreso).

| # | Escenario | Resultado |
|---|---|---|
| V1 | Estado del hogar = cuenta a mano (gasto 2.750.000, ahorro 1.000.000, comprometido 200.000, bolsa 50.000, libre 5.000.000) | pasa |
| V2 | `/estado` contra `/dashboard/summary`: gasto + ahorro = totalExpense, e ingresos iguales | pasa |
| V3 | `/estado` contra `/budget-allocation/summary`, categoría por categoría (real, comprometido, asignado, disponible) | pasa |
| V4 | `/estado` contra `/dashboard/consolidated` | pasa |
| V5 | `/estado` contra `/dashboard/by-category` (la dona) | pasa |
| V6 | `/estado` contra `/plan/responsibilities`, total y por persona | pasa |
| V7 | Andy y Samy ven los mismos totales del hogar | pasa |
| V8 | El historial de un periodo = el estado de ese periodo | pasa |
| V9 | PERSONA Andy + PERSONA Samy = HOGAR (gasto, ahorro, comprometido e ingreso) | pasa |
| V10 | Corte 30: todas las pantallas hablan del mismo periodo | **BUG** |

### 1.2 Privacidad y datos expuestos — `LoPrivadoNoSeFiltraTest`

| # | Escenario | Resultado |
|---|---|---|
| P1 | Barrido de 24 endpoints. Ningún nombre ni importe "firma" de lo privado de Samy (categoría, ingreso, programado, asignación, cuenta, pendiente) llega a Andy | pasa |
| P2 | Los totales de Andy no cambian cuando Samy añade gastos o ingresos privados | pasa |
| P3 | Control positivo: Samy sí ve lo suyo | pasa |
| P4 | Nadie de fuera puede pedir PERSONA ni historial, y el extraño no puede mirar a Andy | pasa |
| P5 | Un ex miembro del hogar ya no se puede consultar | pasa |
| P6 | El extraño recibe un estado vacío | pasa |
| P7 | No se puede reasignar un privado ajeno ni pasarle un pago a un extraño | pasa |
| P8 | Un extraño crea una categoría con el `householdId` de un hogar ajeno | **BUG** |
| P9 | Los gastos del extraño entran en el "gastado" del hogar | **BUG** |
| P10 | Una categoría común que pasa a privada deja su asignación visible para la pareja | **BUG** |
| P11 | Los fondos nunca muestran cuentas ajenas | pasa |

### 1.3 Cumplimiento de fechas — `LasFechasSeCumplenTest`

| # | Escenario | Resultado |
|---|---|---|
| F1 | `PeriodUtils`: cortes 1..31 de 2026 a 2029, con periodos contiguos y de 28 a 31 días | pasa |
| F2 | Con corte 28, el 27 pertenece al periodo anterior y el 28 abre el nuevo (en el estado y en el panel) | pasa |
| F3 | Febrero con corte 28, también en año bisiesto | pasa |
| F4 | Un pago el día 31 cae el 31-ene, 28-feb, 31-mar y 30-abr | pasa |
| F5 | Lo de ayer sale VENCIDO y lo de hoy PENDIENTE; el cumplimiento y el comprometido cuadran | pasa |
| F6 | Un pago que la regla "día hábil anterior" adelanta al último día del periodo no tiene pendiente antes de vencer | **BUG** |
| F7 | Con una política de fin de semana, un pago mensual sale 2 veces en un periodo y 0 en otro | **HALLAZGO de dominio** (tag bug-conocido) |
| F8 | Generar tres veces (Andy, Samy, Andy) no duplica y no adelanta pendientes | pasa |

### 1.4 Planificación futura — `LaPlanificacionFuturaTest`

| # | Escenario | Resultado |
|---|---|---|
| PF1 | Una factura bimestral sale en periodos alternos durante 12 periodos | pasa |
| PF2 | Una cuota a 3 meses que arranca a mitad de periodo: 3 cuotas, la última aparece y `endDate` es el 14-abr | pasa |
| PF3 | Una deuda tomada el 20 y pagadera el 5: la primera cuota es el 5 del mes siguiente | pasa |
| PF4 | Una cuota del 31 a 2 meses: 31-ene y 28-feb, sin tercera | pasa |
| PF5 | Una bimestral "a 4 meses" son 2 cuotas, y `remainingOccurrences` es 2 | pasa |
| PF6 | Un periodo futuro es todo PREVISTO: sin vencidos, sin pagados y sin comprometido | pasa |
| PF7 | `durationMonths` 0 o negativo se acepta | **BUG** |

### 1.5 Presupuestos, ahorro, bolsas y lo libre — `PresupuestoAhorroYBolsasTest`

| # | Escenario | Resultado |
|---|---|---|
| A1 | Meta de ahorro parcial en un mes cerrado: META_INCUMPLIDA, y no cuenta como gasto | pasa |
| A2 | Ahorrar más de la meta la cumple, y la tasa de ahorro es 43,3 % | pasa |
| A3 | Una meta del periodo en curso está EN_CURSO | pasa |
| A4 | Una meta fijada en 0 se marca META_INCUMPLIDA | **BUG** |
| B1 | Bolsa excedida: EXCEDIDO, bolsa en 0 (nunca negativa) y libre correcto | pasa |
| B2 | Bolsa y factura en la misma categoría: la factura no se come la bolsa | pasa |
| B3 | Una compra pendiente dentro de la bolsa se resta dos veces de lo libre | **BUG** |
| L1 | Lo libre cambia según si la pareja ya generó su pendiente | **BUG** |
| PP1 | Presupuesto contra lo pagado de verdad (monto real, sin pendiente negativo) | pasa |
| PP2 | Un presupuesto negativo no se guarda (lo frena el CHECK de la base y devuelve un 500 genérico) | pasa (observación) |
| PP3 | Una asignación a una categoría de INGRESO se suma como presupuesto de gasto en resumen y consolidado | **BUG** |
| D1 | Una deducción de nómina cuenta como factura vencida | **BUG** |
| D2 | La quincena avisa "venció" por una deducción | **BUG** |

### 1.6 Responsables de pago y ediciones — `ResponsablesYEdicionesTest`

| # | Escenario | Resultado |
|---|---|---|
| R1 | Reasignar mueve el comprometido de una persona a otra; el hogar no cambia | pasa |
| R2 | Tras reasignar, las responsabilidades (estado y plan) siguen con el pagador original | **BUG** |
| R3 | Editar el importe del programado deshace la reasignación puntual | **BUG** |
| R4 | Editar solo las notas del programado pisa el importe corregido del pendiente | **BUG** |
| R5 | Corregir el valor de un pendiente desde `/movements/save` lo marca como PAGADO | **BUG** |
| E1 | Pasar a PROVISION tras confirmar: lo pagado se queda y el pendiente se va | pasa |
| E2 | Cambiar el pagador tras confirmar: lo confirmado no cambia de dueño | pasa |
| E3 | Cambiar el día (27 → 20) tras confirmar no duplica | pasa |
| E4 | Acortar una deuda tras pagar la primera cuota no deja pendientes | pasa |
| C1 | `confirm-batch` rechaza ids repetidos, montos negativos, texto y listas vacías, sin confirmar nada | pasa |
| C2 | `confirm-batch` recuenta un pago ya pagado (`confirmed=2`, total inflado) | **BUG** |
| C3 | `confirm-batch` con un pendiente privado ajeno es todo o nada | pasa |
| C4 | Un responsable fuera del hogar o una clase inventada se rechazan | pasa |

### 1.7 Parámetros inválidos — `ParametrosInvalidosTest`

| # | Escenario | Resultado |
|---|---|---|
| I1 | Un movimiento con importe 0, negativo, nulo o texto se rechaza y no se guarda | pasa |
| I2 | Un programado negativo y una confirmación con 0 se rechazan | pasa |
| I3 | Un presupuesto sin importe y una reasignación sin responsable se rechazan | pasa |
| I4 | El estado sin corte usa el 1 | pasa |
| I5 | El estado acepta en silencio corte 0 o 45 | **BUG** |
| I6 | `/dashboard/summary` con `budgetStartDay:null` devuelve un 500 | **BUG** |
| I7 | El historial acota los periodos (0 → 1, -5 → 1, 1000 → 12) | pasa |
| I8 | Un alcance inventado ("HOGARES") se trata como PERSONA | **BUG** |
| I9 | Fechas imposibles o extremas (año 1 y año 9999) responden con contrato y sin cuelgue | pasa |
| I10 | Un diario suma cada día (30 × 1.000) | pasa |

"Datos reales": aperturas, liquidaciones, sub-movimientos y transferencias quedan cubiertos por V1–V5. Están en el escenario de la sección 1.1 y ninguna pantalla los cuenta.

---

## 2. Resultado de la ejecución (2026-11-01)

- **Escritas:** 72 pruebas.
- **Pasan:** 51.
- **Fallan:** 21, todas marcadas `@Tag("bug-conocido")`. Ninguna falla sin esa marca.
- Con `-DexcludedGroups=bug-conocido` la suite queda en verde (51/51).

## 3. Bugs reales, por severidad financiera

1. **Inyección de categorías en un hogar ajeno** (P8, P9). `CategoryServiceImpl` (líneas 91 y 132) copia `householdId` sin comprobar que quien crea o edita sea miembro de ese hogar. Un extraño puede meter gastos y programados en el presupuesto del hogar. **Arreglo:** validar `acceso.esDeMiHogar(dto.householdId)` al crear y al editar.
2. **Lo libre depende de si se materializó el pendiente** (L1). En `EstadoDelPeriodoService:378`, `freeToSpend` solo resta los pendientes generados, no lo PREVISTO del plan; lo mismo pasa con el ahorro planeado. **Arreglo:** restar las facturas y el ahorro del periodo que no estén PAGADOS (PREVISTO, PENDIENTE y VENCIDO) en lugar del comprometido de movimientos, o el máximo de los dos por elemento.
3. **Editar un pendiente lo confirma** (R5). `MovementSaveDTO:65` (`confirmed = true` por defecto) junto con `MovementServiceImpl:220`. **Arreglo:** que el valor por defecto sea `null` en la edición (o que `updateMovement` lo ignore cuando no viene).
4. **Pago adelantado por fin de semana sin pendiente a tiempo** (F6). `generatePending` (línea 575) usa `ocurrenciasHasta`, que corta por la fecha canónica (`CalendarioDeRecurrencias:102`), mientras que `PlanFinancieroService.fechas` enumera hasta +3 días. **Arreglo:** generar hasta `fin + 3` y filtrar por la fecha ajustada, igual que el plan.
5. **Responsabilidades ignoran la reasignación** (R2). `EstadoDelPeriodoService:217` y `PlanFinancieroService:472` agrupan por el pagador del programado. **Arreglo:** si la ocurrencia ya tiene movimiento, usar `movimiento.userId` como responsable.
6. **Editar el programado pisa los pendientes** (R3, R4). `realinearOcurrencias`, líneas 313 y 317. **Arreglo:** propagar el importe y el pagador solo si cambiaron en el programado (comparando el valor anterior con el nuevo) y solo a los pendientes que aún tienen el valor anterior.
7. **Doble resta de la compra pendiente en la bolsa** (B3). `EstadoDelPeriodoService:344-345`. **Arreglo:** que la bolsa la consuman también los pendientes no programados de la categoría, y no sumarlos además al comprometido.
8. **Presupuesto de ingreso sumado como gasto** (PP3). `BudgetAllocationServiceImpl:277` y `544`. **Arreglo:** filtrar las asignaciones de categorías INCOME, como hace el estado (o separarlas en `incomeBudgeted`).
9. **Deducciones como facturas** (D1, D2). `EstadoDelPeriodoService:252` y `PlanFinancieroService:382`. **Arreglo:** excluir DEDUCTION de `billsTotal`, `billsOverdue` y de las alertas.
10. **Corte fuera de 1..28 en el estado** (V10, I5). `EstadoDelPeriodoService:106` y `128`. **Arreglo:** aceptar 1..31 como el resto (PeriodUtils ya lo soporta) y añadir `@Min/@Max` en `EstadoRequestDTO`.
11. **Menores:**
    - Meta 0 marcada incumplida (A4, línea 331).
    - Alcance inventado aceptado (I8, línea 91).
    - `budgetStartDay:null` en el panel (I6, `DashboardRequestDTO`).
    - `durationMonths` ≤ 0 aceptado (PF7, `ScheduledMovementSaveDTO:42`).
    - `confirm-batch` recuenta lo ya pagado (C2, `MovementServiceImpl:375`).

**Hallazgo de dominio a decidir (F7):** con un corte de periodo igual al día de pago y una política de fin de semana, un periodo tiene dos arriendos y el siguiente ninguno. Es correcto como flujo de caja, pero no como "presupuesto del mes". Opciones:
- (A) asignar la ocurrencia al periodo de su fecha canónica;
- (B) mantenerlo y explicarlo en la pantalla.

## 4. Lo que no se pudo probar y por qué

- **Repartos (splits) contra el estado:** el panel cuenta "mi parte" y el estado el total; las definiciones difieren a propósito. Sin una regla de negocio escrita no hay invariante que afirmar.
- **Concurrencia real** (dos refrescos simultáneos que chocan con el índice V11): necesita PostgreSQL e hilos; H2 en modo de prueba no reproduce el bloqueo.
- **Importe nulo en un programado** (facturas variables): no está claro si es intencional. No se marcó como bug.
- **Dependencia de la fecha real:** F5, F6, L1 y la sección 1.6 usan `LocalDate.now()`. Son deterministas porque calculan sus fechas desde hoy, pero F6 depende de que exista un corte cuyo periodo termine en viernes después de hoy (casi siempre lo hay; si no, se salta con `assumeTrue`).
- **Fondos de cuentas compartidas creadas por la pareja:** el comentario de `fondos()` dice "las cuentas comunes y las mías", pero el modelo de cuentas compartidas no estaba claro en el código. No se afirmó nada.

---

## 5. Fase final E2E contra producción (solo lectura)

> No ejecutada. Son solo consultas POST de lectura con el token de cada persona. No se crea, confirma, reasigna ni genera nada; en particular, **no** se llama a `/dashboard/summary` del periodo en curso, porque esa llamada genera pendientes. Para el periodo en curso se usa `/estado`, que no escribe.

1. **Periodo cerrado** (el último periodo de corte 28), con el token de Andy y luego con el de Samy:
   - `/estado/periodo` (HOGAR): `totals` y `compliance` idénticos entre los dos.
   - `spent + saved` = `consolidated.shared.expense`.
   - Por categoría compartida, `actual` y `committed` de `/estado` = `actualAmount` y `committedAmount` de `/budget-allocation/summary`.
   - La dona (`/dashboard/by-category`) por categoría = `/estado` `actual`.
   - Σ `responsibilities.total` = `/plan/responsibilities.sharedTotal`. Si no coincide, buscar DEDUCTIONs en categorías comunes.
2. **Privacidad:** guardar las respuestas de Andy de los 24 endpoints del barrido P1 y buscar en ellas los nombres de las categorías privadas de Samy (los da Samy) y los importes de sus movimientos privados. Hacer lo mismo en sentido inverso.
3. **Personas:** PERSONA Andy + PERSONA Samy = HOGAR en `spent`, `saved`, `committed` e `incomeReceived`.
4. **Contra el banco:** `funds.accounts[].balance` contra el extracto de cada cuenta propia. Anotar la diferencia y su fecha.
5. **Bugs en datos reales:**
   - Pendientes con `scheduledMovementId` cuyo `userId` ≠ pagador del programado (reasignaciones que R3 puede haber deshecho).
   - Movimientos confirmados sin fecha de pago esperada, que podrían venir de R5.
   - Asignaciones con `householdId` cuya categoría ya es privada (P10).
   - Categorías con `householdId` cuyo dueño no es miembro de ese hogar (P8).
   - Ocurrencias "día hábil anterior" que llegaron ya VENCIDAS (F6).
6. **Periodo en curso:** `/estado` HOGAR. Comparar `freeToSpend` con: recibido − gastado − ahorrado − (facturas y ahorro no PAGADOS del plan) − bolsas restantes. La diferencia es el efecto de L1 en plata real.
7. **Historial de 6 periodos:** para cada periodo cerrado, `/estado/historial` = `/estado/periodo` de ese periodo.
