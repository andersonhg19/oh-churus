# Oh Churus! - Modelo de Entidades y Relaciones

> Reescrito el 2026-08-11 leyendo las clases `@Entity` y las migraciones de
> Flyway. La version anterior describia 4 entidades en 3 servicios: era el
> alcance de marzo y llevaba meses sin corresponderse con el codigo.
>
> **Son 11 entidades en 3 bases de datos.** Los otros dos servicios
> (discovery y gateway) no persisten nada.

---

## 1. Mapa

```
┌─ AUTH SERVICE ── auth_db ──────────────────────────────────────────────┐
│                                                                        │
│   User (oc_auth_user)                                                  │
│                                                                        │
└────────────┬───────────────────────────────────────────────────────────┘
             │  User.id viaja como el claim "userId" del JWT
             │  y aparece como columna user_id en TODAS las tablas
             │  de los otros dos servicios. No es una FK: son bases
             │  de datos distintas.
             │
   ┌─────────┴────────────────────────────┬─────────────────────────────┐
   │                                      │                             │
┌──▼─ BUDGET SERVICE ── budget_db ────────┴──┐  ┌─ FASTING SERVICE ── fasting_db ─┐
│                                            │  │                                 │
│   Household ◄──── HouseholdMember          │  │   FastingPlanConfig             │
│       ▲                                    │  │        ▲                        │
│       │ household_id (categoria compartida)│  │        │ plan_config_id          │
│       │                                    │  │        │                        │
│   Category ◄──┐ parent_id (arbol)          │  │   FastingSession                │
│       ▲       └──                          │  │                                 │
│       │ category_id                        │  │   WaterLog      Achievement     │
│       ├──────────── Movement ◄──┐          │  │   (por dia)     (por codigo)    │
│       │                 └───────┘ parent_movement_id             │              │
│       │                 └─ transfer_pair_id (la otra pata)       │              │
│       │                 └─ scheduled_movement_id                 │              │
│       ├──────────── ScheduledMovement                            │              │
│       └──────────── BudgetAllocation                             │              │
│                                                                  │              │
└──────────────────────────────────────────────────────────────────┴──────────────┘
```

---

## 2. AUTH SERVICE (`auth_db`)

### User — `oc_auth_user`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | Long | PK, IDENTITY |
| `name` | String(100) | NOT NULL |
| `email` | String(150) | NOT NULL, UNIQUE |
| `password` | String | NOT NULL (BCrypt) |
| `budgetStartDay` | Integer | NOT NULL, por defecto 1 |
| `active` | Boolean | NOT NULL, por defecto true |
| `createdAt` | LocalDateTime | `@CreationTimestamp`, no actualizable |
| `updatedAt` | LocalDateTime | `@UpdateTimestamp` |

**Invariantes en la base** (`V2__invariantes.sql`):
- `ck_usuario_dia_de_corte`: `budget_start_day BETWEEN 1 AND 31`
- `ck_usuario_correo_no_vacio`: `CHAR_LENGTH(TRIM(email)) > 0`

**`budgetStartDay` y los meses cortos.** El dia de corte admite del 1 al 31.
Cuando el dia configurado no existe en un mes dado (un 31 en febrero), el periodo
se calcula con el ultimo dia de ese mes. La logica esta en `PeriodUtils` del
budget-service, y el valor lo lee del token / del propio usuario, nunca del
cuerpo de la peticion.

**Borrado.** Logico: `active = false`. Nada se borra de verdad en este proyecto.

---

## 3. BUDGET SERVICE (`budget_db`)

Seis entidades. Es donde vive el grueso del dominio.

### Category — `oc_budget_category`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | Long | PK |
| `userId` | Long | NOT NULL. Quien la creo |
| `name` | String(100) | NOT NULL |
| `description` | String(255) | |
| `parentId` | Long | FK a si misma. null = categoria raiz |
| `icon` | String(50) | |
| `color` | String(7) | hexadecimal |
| `householdId` | Long | **null = personal · con valor = compartida del hogar** |
| `type` | enum `CategoryType` | NOT NULL: `INCOME` \| `EXPENSE` |
| `active` | Boolean | NOT NULL |
| `createdAt` / `updatedAt` | LocalDateTime | timestamps de Hibernate |

Indices: `(userId, active)` y `(userId, parentId, active)`.

`householdId` es el campo que hace posible el nucleo familiar: **una categoria
compartida es la unidad de lo comun**. Todo lo que cuelga de ella (movimientos,
programados, asignaciones) lo ven todos los miembros del hogar. Esa es la regla
que implementa `ControlAcceso`.

El arbol admite padre e hijos; el servicio limita la profundidad y no deja borrar
una categoria que tenga hijos vivos.

### Movement — `oc_budget_movement`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | Long | PK |
| `userId` | Long | NOT NULL |
| `categoryId` | Long | NOT NULL, FK a `oc_budget_category` |
| `date` | LocalDate | NOT NULL |
| `amount` | BigDecimal(15,2) | NOT NULL |
| `description` | String(255) | |
| `scheduledMovementId` | Long | de que programado nacio, si nacio de uno |
| `periodStart` | LocalDate | primer dia del periodo de la ocurrencia |
| `parentMovementId` | Long | FK a si misma. Si esta, **es un sub-gasto** |
| `isTransfer` | Boolean | NOT NULL, por defecto false |
| `transferPairId` | Long | la otra pata de la transferencia |
| `confirmed` | Boolean | NOT NULL, por defecto true. false = pendiente |
| `active` | Boolean | NOT NULL |
| `createdAt` / `updatedAt` | LocalDateTime | |

Indices: `(userId, date, active)` y `(userId, confirmed, active)`.

Tres campos merecen explicacion:

- **`parentMovementId`** — el desglose. La compra del mercado de 500.000 puede
  tener hijos "carne 200.000" y "verdura 100.000". Los hijos **no suman**: son
  detalle del padre. Si sumaran, esa compra costaria 800.000.
- **`isTransfer` / `transferPairId`** — una transferencia son **dos** filas
  enlazadas (salida de un bolsillo, entrada en otro). No es ingreso ni gasto para
  nadie. Editar una transferencia toca las dos patas en la misma transaccion:
  antes no, y corregir una de 500.000 a 300.000 dejaba el consolidado descuadrado
  en 200.000 inexistentes.
- **`periodStart`** — la clave de idempotencia de las recurrencias. No puede ser
  la fecha, porque el usuario puede moverla: bastaba con cambiar la fecha de un
  pendiente a otro mes para que el generador lo volviera a crear y el arriendo
  saliera dos veces. La clave real es `(scheduledMovementId, periodStart)`.

### ScheduledMovement — `oc_budget_scheduled_movement`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | Long | PK |
| `userId` | Long | NOT NULL |
| `categoryId` | Long | NOT NULL, FK a categoria |
| `name` | String(100) | NOT NULL |
| `amount` | BigDecimal(15,2) | **admite null** |
| `frequency` | enum `Frequency` | NOT NULL |
| `durationMonths` | Integer | |
| `startDate` | LocalDate | NOT NULL |
| `endDate` | LocalDate | |
| `dayOfMonth` | Integer | |
| `active` | Boolean | NOT NULL |
| `createdAt` / `updatedAt` | LocalDateTime | |

`Frequency`: `DAILY`, `WEEKLY`, `BIWEEKLY`, `MONTHLY`, `BIMONTHLY`, `QUARTERLY`,
`SEMIANNUAL`, `ANNUAL`.

El `amount` nulo es intencionado: la factura de la luz cambia cada mes, asi que el
programado genera su pendiente en 0 y la persona lo rellena al confirmarlo.

Las ocurrencias se generan **a nombre del dueno del programado**, no de quien
pulsa el boton de refrescar: en un hogar compartido, si no, el arriendo de uno
acababa atribuido al otro.

### BudgetAllocation — `oc_budget_allocation`

Cuanto se piensa gastar en una categoria durante un periodo.

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | Long | PK |
| `userId` | Long | NOT NULL |
| `categoryId` | Long | NOT NULL, FK a categoria |
| `householdId` | Long | FK a hogar, opcional |
| `periodStart` | LocalDate | NOT NULL |
| `periodEnd` | LocalDate | NOT NULL |
| `allocatedAmount` | BigDecimal(15,2) | NOT NULL |
| `status` | String(20) | NOT NULL, por defecto `ACTIVE` |
| `notes` | String(255) | |
| `active` | Boolean | NOT NULL |
| `createdAt` / `updatedAt` | LocalDateTime | |

Indices: `(userId, periodStart)`, `(householdId, periodStart)`,
`(categoryId, periodStart)`.

El guardado es un *upsert*: busca la asignacion de (categoria, periodo) y si no
esta la crea. Por eso hace falta el unico de la base — ver seccion 5.

### Household — `oc_budget_household`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | Long | PK |
| `name` | String(100) | NOT NULL |
| `active` | Boolean | NOT NULL |
| `createdAt` | LocalDateTime | sin `updatedAt`: un hogar no se edita |

### HouseholdMember — `oc_budget_household_member`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | Long | PK |
| `householdId` | Long | NOT NULL, FK a `oc_budget_household` |
| `userId` | Long | NOT NULL. Referencia logica a `auth_db` |
| `role` | String(20) | NOT NULL: `OWNER` \| `MEMBER` |
| `active` | Boolean | NOT NULL |
| `createdAt` | LocalDateTime | |

Indices: `(userId)` y `(householdId)`.

Solo el `OWNER` puede anadir o expulsar miembros. Antes no se comprobaba nada y
cualquiera se metia en la casa de otra pareja probando ids consecutivos.

---

## 4. FASTING SERVICE (`fasting_db`)

Cuatro entidades. No conoce ni hogares ni categorias: solo el `userId` del token.

### FastingPlanConfig — `oc_fasting_plan_config`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | Long | PK |
| `userId` | Long | NOT NULL |
| `planType` | enum `PlanType` | NOT NULL |
| `fastingHours` | Integer | NOT NULL |
| `eatingHours` | Integer | NOT NULL |
| `suggestedStartTime` | String(5) | "20:00" |
| `remindersEnabled` | Boolean | NOT NULL, por defecto false |
| `active` | Boolean | NOT NULL |
| `createdAt` / `updatedAt` | LocalDateTime | |

`PlanType` lleva las horas dentro: `PLAN_12_12`, `PLAN_14_10`, `PLAN_16_8`,
`PLAN_18_6`, `PLAN_20_4` y `CUSTOM` (0/0, las pone el usuario).
Indice: `(userId, active)`.

### FastingSession — `oc_fasting_session`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | Long | PK |
| `userId` | Long | NOT NULL |
| `planConfigId` | Long | FK a `oc_fasting_plan_config`, ON DELETE SET NULL |
| `startTime` | LocalDateTime | NOT NULL |
| `targetEndTime` | LocalDateTime | NOT NULL |
| `actualEndTime` | LocalDateTime | null mientras esta en curso |
| `fastingHours` | Integer | NOT NULL |
| `status` | enum `SessionStatus` | NOT NULL, por defecto `IN_PROGRESS` |
| `active` | Boolean | NOT NULL |
| `createdAt` | LocalDateTime | |

`SessionStatus`: `IN_PROGRESS`, `COMPLETED`, `INCOMPLETE`, `CANCELLED`.
Indices: `(userId, status, active)` y `(userId, startTime, active)`.

### WaterLog — `oc_fasting_water_log`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | Long | PK |
| `userId` | Long | NOT NULL |
| `logDate` | LocalDate | NOT NULL. Una fila por dia |
| `glasses` | Integer | NOT NULL, por defecto 0 (vasos de 250 ml) |
| `goalGlasses` | Integer | NOT NULL, por defecto 8 |
| `createdAt` | LocalDateTime | |

Indice: `(userId, logDate)`.

### Achievement — `oc_fasting_achievement`

| Campo | Tipo | Restricciones |
|---|---|---|
| `id` | Long | PK |
| `userId` | Long | NOT NULL |
| `code` | String(50) | NOT NULL |
| `name` | String(100) | NOT NULL |
| `description` | String(255) | |
| `icon` | String(10) | |
| `unlockedAt` | LocalDateTime | `@CreationTimestamp` |

Indice: `(userId)`. Codigos que hoy concede el servicio: `STREAK_3`, `STREAK_7`,
`STREAK_14`, `STREAK_30`, `HOURS_50`, `HOURS_100`, `HOURS_500`.

Un logro se otorga una vez y no se retira: no tiene `active`.

---

## 5. Invariantes que ahora viven en la base (Flyway)

Hasta agosto de 2026 el esquema lo generaba Hibernate con `ddl-auto=update` y no
habia **ni una sola** clave foranea, unicidad ni CHECK. Todas las reglas vivian en
Java, donde una condicion de carrera o un error de un servicio se las salta.

Hoy: `spring.flyway.enabled=true` con `baseline-on-migrate` y
`spring.jpa.hibernate.ddl-auto=validate`. El esquema lo pone Flyway; Hibernate
solo comprueba que el mapeo coincida y, si no, **el servicio no arranca**.

### Ficheros

| Servicio | Migraciones |
|---|---|
| auth | `db/migration/V1__linea_base.sql`, `V2__invariantes.sql` |
| budget | `db/migration/comun/V1__linea_base.sql`, `comun/V3__periodo_de_la_ocurrencia.sql`, y un `V2__invariantes.sql` **por motor** en `postgresql/` y `h2/` |
| fasting | `db/migration/V1__linea_base.sql`, `V2__invariantes.sql` |

Budget necesita dos V2 porque el indice unico parcial (`WHERE active`) es de
PostgreSQL y H2 no lo tiene: alli se consigue lo mismo con una columna calculada
que vale la clave cuando la fila esta viva y NULL cuando esta apagada (en un
indice unico los NULL no chocan entre si). **Dicen lo mismo. Si tocas uno, toca el
otro.**

### Unicidad de lo que esta vivo (budget)

```
ux_asignacion_viva_categoria_periodo  UNIQUE (category_id, period_start) WHERE active
ux_miembro_vivo_hogar_usuario         UNIQUE (household_id, user_id)     WHERE active
```

Parciales a proposito: el borrado es logico, asi que puede haber tantas filas
apagadas como quiera para la misma clave; lo que no puede haber es dos
encendidas. Sin esto, dos toques seguidos en el presupuesto insertaban dos
asignaciones y el `Optional` del servicio lanzaba `NonUniqueResultException` en
esa categoria **para siempre**, sin forma de arreglarlo desde la app.

Antes de crear los indices, la migracion **desactiva** los duplicados que ya
existan quedandose el mas reciente. No borra ninguna fila.

### Claves foraneas (budget)

```
oc_budget_household_member.household_id     -> oc_budget_household(id)
oc_budget_category.household_id             -> oc_budget_household(id)
oc_budget_category.parent_id                -> oc_budget_category(id)   ON DELETE SET NULL
oc_budget_movement.category_id              -> oc_budget_category(id)
oc_budget_movement.parent_movement_id       -> oc_budget_movement(id)   ON DELETE SET NULL
oc_budget_scheduled_movement.category_id    -> oc_budget_category(id)
oc_budget_allocation.category_id            -> oc_budget_category(id)
oc_budget_allocation.household_id           -> oc_budget_household(id)
```

Los "padres" van con `ON DELETE SET NULL`: la app nunca borra de verdad, pero una
limpieza manual no tiene por que quedarse bloqueada por el detalle que cuelga.

### CHECK

| Servicio | Restriccion | Regla |
|---|---|---|
| auth | `ck_usuario_dia_de_corte` | `budget_start_day` entre 1 y 31 |
| auth | `ck_usuario_correo_no_vacio` | correo no vacio tras `TRIM` |
| budget | `ck_movimiento_importe` | `amount >= 0` |
| budget | `ck_programado_importe` | `amount` nulo o `>= 0` |
| budget | `ck_asignacion_importe` | `allocated_amount >= 0` |
| budget | `ck_asignacion_periodo` | `period_end >= period_start` |
| fasting | `ck_plan_horas` | ayuno y comida entre 0 y 24, y **suman 24** |
| fasting | `ck_sesion_horas` | `fasting_hours` entre 0 y 24 |
| fasting | `ck_sesion_fin_previsto` | `target_end_time >= start_time` |
| fasting | `ck_sesion_fin_real` | `actual_end_time` nulo o `>= start_time` |

Son **10 CHECK**. La novena clave foranea del proyecto, `fk_sesion_plan` (la
sesion apunta a un plan que existe), tambien vive en `fasting/V2__invariantes.sql`:
esta aparte porque las ocho de la lista de arriba son de budget.

Se exige `>= 0` y no `> 0` en los importes a proposito: un programado sin importe
genera su pendiente en 0 y eso es un flujo que hoy funciona. Lo que no tiene
sentido nunca es un importe negativo, que invierte la suma entera sin avisar.

En PostgreSQL las FK y los CHECK se anaden `NOT VALID`: obligan a todo lo que se
escriba de ahora en adelante y no revisan lo viejo, para que una fila incoherente
de antes no impida arrancar. Se pueden validar despues con
`ALTER TABLE ... VALIDATE CONSTRAINT` cuando se sepa que la base esta limpia.

### Lo que falta, y por que

En fasting **no** hay unicidad de `(user_id, log_date)` en el agua ni de
`(user_id, code)` en los logros, y las dos harian falta (el servicio los busca con
un `Optional`, asi que un duplicado los rompe igual que pasaba con las
asignaciones). El problema es que esas dos tablas no tienen columna `active`: si
la base real ya trajera un duplicado, la unica forma de crear el indice seria
**borrar** una fila con vasos de agua que alguien apunto. Eso no lo decide una
migracion. Queda pendiente de una limpieza mirada a mano.

---

## 6. Relaciones cruzadas entre servicios

Cada servicio tiene su base de datos. **No hay JOIN posible entre servicios y no
hay FK entre bases.**

### `User.id` como referencia logica

La columna `user_id` de las diez tablas de budget y fasting apunta a
`oc_auth_user.id`, pero no existe la restriccion: son bases distintas. El valor
llega en el claim `userId` del JWT que emite auth-service, lo extrae el
`JWTAuthorizationFilter` de cada servicio y lo reparte `SecurityUtils`.

Consecuencia practica: **dar de baja un usuario en auth no borra nada en budget ni
en fasting.** El borrado es logico en todas partes y sus datos quedan
inaccesibles porque nadie puede volver a autenticarse como el.

### budget-service -> auth-service (la unica llamada entre servicios)

Invitar al nucleo familiar es **por correo**, y los correos viven en `auth_db`.
`DirectorioDeUsuariosHttp` (budget) le pregunta a auth por HTTP:

```
POST {app.auth-service-url}/v1/users/all     con el filtro { email: "..." }
```

Decisiones de esa llamada, tal como estan en el codigo:

- No se toca auth-service: se reutiliza un endpoint que ya existia.
- No se anaden dependencias: `RestClient` viene con `spring-boot-starter-web`.
- No se inventa un secreto entre servicios: **se reenvia el mismo token de quien
  invita**. Si el que llama no esta autenticado, auth tampoco le contesta, y la
  consulta queda a su nombre.
- Timeouts cortos (2 s de conexion, 3 s de lectura): si auth esta caido, el
  usuario ve "no se pudo resolver el correo" en un par de segundos, no una
  pantalla colgada.

Ese listado **no es un directorio**: sin correo en el filtro solo te devuelve a ti
mismo, y con correo la coincidencia es exacta, nunca parcial. Lo vigila
`AislamientoDeCuentasTest`.

### fasting-service

No llama a nadie ni nadie le llama. Solo necesita el `userId` del token.

---

## 7. Convenciones del modelo

| Convencion | Detalle |
|---|---|
| Nombre de tabla | `oc_[servicio]_[entidad]` en singular: `oc_budget_movement` |
| Clave primaria | `id` Long, `GenerationType.IDENTITY` |
| Borrado | **Siempre logico** (`active = false`). Nada se borra de verdad |
| Timestamps | `@CreationTimestamp` en `createdAt` (no actualizable) y `@UpdateTimestamp` en `updatedAt` donde la entidad se edita |
| Dinero | `BigDecimal` con `precision = 15, scale = 2`. Moneda COP y 0 decimales de presentacion (`app.currency`, `app.decimal-places`) |
| Enums | `@Enumerated(EnumType.STRING)`: se guarda el nombre, nunca el ordinal |
| Relaciones | Por **id suelto** (`categoryId`, `parentId`), no con `@ManyToOne`. Las FK las pone Flyway |
| Identidad | Ninguna entidad se escribe con un `userId` que venga del cliente |

---

## 8. Antes de tocar el modelo

1. Lee `documentación/invariantes.md`.
2. Cambiar una entidad **exige** su migracion Flyway. Con `ddl-auto=validate` el
   servicio no arranca si el mapeo y el esquema no coinciden — eso es la red, no
   un estorbo.
3. Si el cambio afecta a budget, recuerda el gemelo H2 del `V2`.
4. Si anades un endpoint que reciba un id, anadelo a la matriz de aislamiento.
