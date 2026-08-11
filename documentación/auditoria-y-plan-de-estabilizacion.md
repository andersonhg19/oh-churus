<!-- Generado por una auditoria multiagente (19 agentes, 8 dimensiones con
     verificacion adversarial + 2 investigaciones externas) el 2026-08-11.
     La OLA 1.0-1.3 ya esta aplicada: ver el commit "fix(seguridad)". -->

# Plan de estabilización — Oh Churus!

**Raíz del proyecto:** `C:\Users\ander\Documents\Anderson\Universidad\Oh Churus`
Las rutas de este documento son relativas a esa raíz.

---

## 1. Diagnóstico en cinco frases

1. Tienes razón en que está inestable, pero no por la razón que crees: no hay fallos aleatorios ni un bug esquivo, hay **tres decisiones de diseño tomadas al principio que se propagaron a los 55 endpoints y a las 22 pantallas**, y cada síntoma que percibes es una consecuencia mecánica y reproducible de alguna de ellas.
2. Tienes razón en que está a medias, y de forma más literal de lo que sospechabas: hay **tres pantallas completas escritas, probadas y no registradas en ninguna ruta** —incluida la única que permite crear presupuestos, lo que deja muerta una sección entera del Consolidado— más doce métodos de servicio que ninguna pantalla invoca.
3. En lo que **no** tienes razón es en la calidad del trabajo: el código está bien estructurado, es legible, la separación por capas es correcta y el nivel de detalle (sub-gastos, split personal/conjunto, consolidado, ayuno) es muy superior al de un proyecto universitario típico — el problema no es que esté mal hecho, es que **nadie le puso una frontera al cliente ni una fuente única de verdad al dominio**.
4. Los números de calidad que te tranquilizan son falsos como garantía: el 99-100 % de cobertura se calcula **después de excluir explícitamente la capa de seguridad**, los 10 tests de controller **desactivan los filtros**, las 36 pruebas Karate **nunca se ejecutan en CI**, ninguna prueba toca SQL real, y existe un test (`MovementServiceImplEdgeCasesTest:214`) que **certifica la fuga de datos como comportamiento correcto** — es decir, tu suite hoy defiende activamente el bug más grave.
5. La buena noticia es que el trabajo por delante es **mayoritariamente sustractivo y convergente**: quitar el `userId` de los cuerpos, quitar el `budgetStartDay` de los cuerpos, y unificar en un solo sitio la regla de qué movimiento suma — tres cambios que, por sí solos, apagan alrededor de 40 de los ~90 hallazgos.

---

## 2. La causa raíz

No son noventa problemas. Son tres decisiones, más una consecuencia estructural.

### Raíz A — La identidad la pone el cliente

Se decidió que todos los endpoints fueran POST y que los parámetros viajaran en el cuerpo. Al hacerlo, el `userId` se convirtió en *un parámetro más*, indistinguible del `categoryId` o del `amount`. El filtro JWT llegó a existir, el token **sí lleva el claim `userId`** (`AuthenticationServiceImpl:48-53`), y hasta se escribió `SecurityUtils` — pero nadie cerró el círculo, y `budget-service`/`fasting-service` ni siquiera tienen forma de traducir email→id.

**Todo esto es la misma decisión:** el takeover de cuentas por `/v1/auth/register`, la autoinscripción en el núcleo familiar ajeno, los 12 endpoints `get/{id}`/`delete/{id}` sin dueño, `transfer()` desde la bolsa de otra familia, `session/edit` de ayuno ajeno, `/v1/users/all` como directorio abierto, el `budgetStartDay` que el cliente decide, y `generatePending` atribuyendo el arriendo de Anderson a Samy.

### Raíz B — No existe una fuente única de verdad para el periodo ni para "qué suma"

Dos sub-decisiones que se refuerzan:

- **El periodo se recalcula en todas partes:** en `useState` de seis pantallas, en `PeriodUtils` del backend, y viaja como `budgetStartDay` en cada petición. Nadie es dueño de él.
- **La regla de agregación se decide método a método:** `getSummary` excluye hijos para `budgetTotal` (línea 127, comentada como "Bug 4") y los incluye para `totalExpense` (líneas 104-113) — *en el mismo método*. `getByCategory` no los excluye. `consolidated` tampoco. El Excel ignora el hogar. `list()` devuelve `allocatedAmount` y la pantalla lee `amount`.

De aquí sale todo lo que "no cuadra": el `$NaN` de Presupuesto, la dona que contradice a su drill-down, el Dashboard que contradice al Resumen, el consolidado donde `shared + personal ≠ total`, los presupuestos que desaparecen al cambiar el día de corte, y las barras "Presupuesto vs Real" comparando meses distintos.

### Raíz C — Las convenciones no tienen mecanismo que las imponga

El patrón `ResultDTO` (HTTP 200 siempre) es un **acuerdo verbal**: no hay un solo `@ControllerAdvice` en todo el backend, hay controllers sobre `Map<String,Object>` sin validar, y `ddl-auto=update` sin una sola FK, UNIQUE ni CHECK. Del otro lado, el frontend tiene catches literalmente vacíos en ocho pantallas y `if (res.correct)` sin `else` en doce.

Resultado: **los fallos no se manifiestan, se disfrazan de estado vacío.** Token caducado, gateway caído, 403 de propiedad y "aún no tienes datos" son el mismo píxel para el usuario. Ésta es, literalmente, la fuente de tu sensación de inestabilidad.

### Consecuencia estructural — La calidad se mide donde no importa

`jacoco` y `sonar` excluyen `SecurityConfig`, `SecParams` y `JWTAuthorizationFilter`; los tests de controller llevan `addFilters = false`; surefire excluye `**/karate/**` y el CI nunca pasa `-Pkarate`; no hay `@DataJpaTest`, ni Testcontainers, ni umbral de cobertura en ningún lado. **Por eso ninguna de las tres raíces se detectó nunca.** No es un hallazgo más: es la explicación de por qué los otros noventa sobrevivieron hasta hoy.

---

## 3. Plan por olas

### 🩸 OLA 1 — SANGRÍA

*Objetivo: que nadie pueda leer ni tocar los datos de otro, que los tres flujos que el usuario usa a diario funcionen, y que un clon del repo arranque.*

#### 1.0 — Parche de urgencia (hoy, 30 minutos, antes que nada)

| | |
|---|---|
| **Qué** | Crear `UserRegisterDTO` **sin campo `id`** y que `/v1/auth/register` llame a `createUser`, nunca a `saveAndUpdate`. Quitar `ports: 8821/8823` de ambos docker-compose. |
| **Por qué** | Es la única vulnerabilidad explotable **sin autenticarse**: un POST con `{"id":3,...}` te quita tu propia cuenta. Todo lo demás exige al menos un token. |
| **Archivos** | `backend/auth-service/.../controller/AuthenticationController.java:39-43`, `.../dto/input/UserSaveDTO.java`, `.../service/impl/UserServiceImpl.java:46-52`, `backend/docker-compose.yml:57-58,81-83`, `docker-compose.yml` |
| **Esfuerzo** | Muy bajo |
| **Prueba** | `POST /v1/auth/register` con `{"id":1,...}` responde 400/`correct:false` y el usuario 1 conserva su email y su hash de contraseña. |

#### 1.1 — La identidad sale del token y de ningún otro sitio

| | |
|---|---|
| **Qué** | El filtro lee `decodedJWT.getClaim("userId").asLong()` y lo guarda en el `Authentication`. `SecurityUtils.getAuthenticatedUserId()` lo expone. Se **elimina el campo `userId` de todos los DTOs de entrada y de todos los `Map` de cuerpo**, y los controllers lo obtienen del token. Idem `budgetStartDay`: sale del usuario, no del cuerpo. |
| **Por qué** | Raíz A completa. Sin esto, ninguna comprobación de propiedad tiene con qué comparar. Es prerequisito duro de 1.2 y de 1.3. |
| **Archivos** | `*/security/JWTAuthorizationFilter.java:47-50` (los 3, hoy copias byte a byte), `budget-service/.../util/SecurityUtils.java`, todos los `dto/input/*`, `MovementController`, `CategoryController`, `DashboardController`, `HouseholdController`, `BudgetAllocationController`, `ExportController`, `FastingController`, y en el front: quitar `userId` de los 6 servicios de `frontend/src/services/` |
| **Esfuerzo** | Alto (es amplio, no difícil) |
| **Prueba** | Test de arquitectura (ArchUnit o grep en CI): **cero ocurrencias de `"userId"` en `dto/input/` y en literales de `body.get(...)`** en todo el backend. |

#### 1.2 — La matriz de aislamiento, escrita **antes** del arreglo

| | |
|---|---|
| **Qué** | Un `CrossUserAccessTest` por servicio: usuario A crea N recursos, usuario B ataca los 55 endpoints (get/save/delete/confirm/children/transfer/add-member/session-edit) con su propio token válido, y debe recibir 403 **y el recurso de A debe seguir existiendo e intacto**. Más un test reflexivo sobre `RequestMappingHandlerMapping` que **falle el build si aparece un endpoint nuevo que no esté en la matriz**. Y reescribir `shouldReassignOnPersonal` para que asserte que el `userId` **no** cambia. |
| **Por qué** | Es el criterio de "hecho" de toda la Ola 1, no un extra. Hoy la suite certifica la fuga; hasta que exista un test que diga la verdad, cualquier arreglo de seguridad es una opinión. Escrito antes, arranca 100 % en rojo y esa cifra **es** la medida real del hueco. |
| **Archivos** | nuevos en `*/src/test/java/.../security/`; corregir `budget-service/src/test/.../MovementServiceImplEdgeCasesTest.java:199-216` |
| **Esfuerzo** | Medio |
| **Prueba** | Ella misma. Verifica su propio poder: quita a mano un `AND userId = ?` y confirma que se pone roja. |

#### 1.3 — Propiedad y pertenencia en cada lectura y escritura

| | |
|---|---|
| **Qué** | Cambiar las consultas a `findByIdAndUserIdAndActiveTrue` donde el recurso es personal; donde puede ser del hogar, exigir `entity.userId == caller || getHouseholdIds(caller).contains(entity.householdId)`. `addMember`/`removeMember` exigen que el llamante sea **OWNER** de ese hogar. `transfer()` exige que el llamante pertenezca al hogar de la categoría origen. `/v1/users/all` y `/v1/users/delete` pasan a exigir rol ADMIN. `updateCategory` valida contra el `userId` de la entidad existente, no contra el del cuerpo, y no acepta `householdId` arbitrario. |
| **Por qué** | Los 12 endpoints sin dueño son los que producen literalmente "cifras que cambian solas": `confirm/{id}` con `{"amount":9999999}` reescribe el gasto de otro y corrompe su varianza. |
| **Archivos** | `MovementServiceImpl.java:130,185-200,221,258`, `CategoryServiceImpl.java:90-121,148,224`, `ScheduledMovementServiceImpl.java:134,186`, `BudgetAllocationServiceImpl.java:128,219-243`, `HouseholdServiceImpl.java:52-87`, `FastingServiceImpl.java:198-223`, `UserServiceImpl.java:120-154` |
| **Esfuerzo** | Alto |
| **Prueba** | 1.2 pasa a verde completo. |

#### 1.4 — El contrato deja de ser un acuerdo verbal

| | |
|---|---|
| **Qué** | Un `@RestControllerAdvice` por servicio que capture `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `AccessDeniedException` y `Exception`, y devuelva **siempre** `ResponseEntity.ok(new ResultDTO(false, mensaje, código))`. Sustituir los `Map<String,Object>` de `HouseholdController`, `BudgetAllocationController`, `MovementController.transfer` y `FastingController` por DTOs con `@Valid`. `ExportController` deja de devolver `internalServerError().build()`. Mover los `try/catch` fuera de la frontera `@Transactional` (o marcar `setRollbackOnly`). |
| **Por qué** | Raíz C. Es lo que convierte "Request failed with status code 400" en el mensaje de negocio que el backend ya se molesta en calcular. |
| **Archivos** | nuevos `*/exception/GlobalExceptionHandler.java`; `HouseholdController.java:23-43`, `BudgetAllocationController.java:24-32,73-75`, `MovementController.java:71-74`, `FastingController.java` (11 puntos), `ExportController.java:35-38`, y los `catch` de `BudgetAllocationServiceImpl:82`, `CategoryServiceImpl:51`, `MovementServiceImpl:52`, `ScheduledMovementServiceImpl:64`, `HouseholdServiceImpl:46` |
| **Esfuerzo** | Medio |
| **Prueba** | Test por controller: cuerpo vacío, campo faltante, tipo incorrecto y `notes` de 300 caracteres → **siempre HTTP 200 con `correct:false`**. Ningún 400 ni 500 en toda la superficie. |

#### 1.5 — Los tres flujos que el usuario toca a diario

| Flujo | Fix | Archivos |
|---|---|---|
| **Registro** | `register` autentica tras crear y devuelve `AuthenticationResponse{token,userId}` como `/login` | `AuthenticationController.java:39-43` ↔ `frontend/src/contexts/AuthContext.tsx:112-130` |
| **Día de corte** | `UserSaveDTO` con validación por grupos (o endpoint dedicado) para que `{id, budgetStartDay}` sea válido | `UserSaveDTO.java:17-24` ↔ `PeriodConfigScreen.tsx:30`, `ProfileScreen.tsx:30-33` |
| **Pantalla Presupuesto** | Cruzar `list()` con `summary().categories` (que sí trae `allocatedAmount`/`actualAmount`/`variance`) | `BudgetScreen.tsx:35-38,278-282,313` ↔ `BudgetAllocationServiceImpl.java:100-119` |

**Esfuerzo:** bajo los tres. **Prueba:** un test de front por flujo con fixture derivado del tipo real (no `jest.Mock` sin tipo) que asserte el **valor formateado**, no la etiqueta. Cero `$NaN` en el render.

#### 1.6 — Los fallos dejan de disfrazarse de vacío

| | |
|---|---|
| **Qué** | Interceptor de `api.ts` detecta 401 → logout global + toast "Tu sesión expiró". `loadStoredAuth` trata el 401 de `getUser` como sesión inválida. `data?.shared`/`data?.personal` en el Consolidado. `.catch()` en la lectura del onboarding. |
| **Por qué** | Hoy, tras unos días sin abrir, el usuario entra sin contraseña y ve balance $0, "Sin datos", "Sin núcleo familiar" — **indistinguible de haber perdido todo**. Es el hallazgo que más directamente produce la palabra "inestable". |
| **Archivos** | `frontend/src/services/api.ts:31-38`, `AuthContext.tsx:55-71`, `ConsolidatedScreen.tsx:191,202`, `AppNavigator.tsx:399-405` |
| **Esfuerzo** | Bajo |
| **Prueba** | Test que mockea 401 en arranque en frío y asserta que se llega a la pantalla de login. |

#### 1.7 — Dejar de mantener lo que nadie puede ver

| | |
|---|---|
| **Qué** | Montar `BudgetNavigator` en `MainTabs` o en el Drawer. **Borrar** `ProfileScreen.tsx` y `MovementsScreen.tsx` — pero antes trasladar a `MovementsListScreen` las dos cosas que la huérfana sí hacía bien: `PeriodNavigator` y filtro por periodo. Quitar `"!src/navigation/AppNavigator.tsx"` del `collectCoverageFrom`. |
| **Por qué** | Es un cambio de una línea que devuelve **un tercio del producto** al usuario, y elimina la sección muerta "Ejecución presupuestal" del Consolidado. |
| **Archivos** | `AppNavigator.tsx:178-192,214-295,351-361`, `frontend/package.json:12-40`, `MovementsListScreen.tsx:32,49-52` |
| **Esfuerzo** | Bajo |
| **Prueba** | Test de navegación que renderiza el árbol completo y asserta que **toda pantalla exportada es alcanzable**. |

#### 1.8 — Que un clon del repo arranque

| | |
|---|---|
| **Qué** | Un solo `docker-compose.yml` (el de la raíz, con `name: oh-churus`), con `fasting-service` incluido, `restart: unless-stopped`, defaults `${VAR:-valor}` en todo, `TZ=America/Bogota`, y `API_URL` por defecto a `localhost` (no `192.168.1.9`). `.env.example` versionado. Dockerfiles multi-stage. `README.md` en la raíz. Parametrizar `EUREKA_URL`. Anclar el seed a `LocalDate.now()`. |
| **Por qué** | Hoy `git clone && docker compose up --build` falla en el primer comando, y si sobrevive, el frontend llama a una IP privada de tu casa horneada en el bundle. |
| **Archivos** | `docker-compose.yml:136`, `backend/docker-compose.yml` (eliminar), `*/Dockerfile`, `*/application.properties`, `budget-service/.../LoadData.java:117,154`, `fasting-service/.../LoadData.java:20-25`, nuevo `README.md` y `.env.example` |
| **Esfuerzo** | Medio |
| **Prueba** | Job de smoke en `sonarcloud.yml`: `docker compose up -d --build` → esperar healthchecks → login **vía gateway** → una llamada a budget y otra a fasting. Verde en un runner limpio. |

> **Criterio de HECHO de la Ola 1**
> (a) `CrossUserAccessTest` en verde para los 55 endpoints, y el test reflexivo bloqueando endpoints nuevos sin cobertura de aislamiento.
> (b) Cero ocurrencias de `userId` en DTOs de entrada.
> (c) Ningún endpoint devuelve 400/500 crudo ante ningún cuerpo malformado.
> (d) Un usuario nuevo puede registrarse, cambiar su día de corte y ver la pantalla de Presupuesto con números.
> (e) `docker compose up --build` sobre un clon limpio pasa el smoke en CI.

---

### 🧭 OLA 2 — COHERENCIA

*Objetivo: que la misma plata dé la misma cifra en todas las pantallas, y que lo que la app promete exista.*

#### 2.1 — Un solo dueño del periodo

**Qué:** el backend resuelve `budgetStartDay` desde el usuario (ya no llega en el cuerpo, por 1.1) y **devuelve `periodStart`/`periodEnd` en cada respuesta**. El frontend deja de calcularlos: los pinta desde la respuesta. Un hook `usePeriod()` compartido reemplaza los seis `useState` con inicializador perezoso.
**Por qué:** apaga de golpe cuatro hallazgos: la cabecera que dice "1–31" mientras muestra el ciclo 28–27, los presupuestos que "se borran" al cambiar el corte, `SummaryScreen` comparando contra el mes anterior (`new Date('2026-08-01')` → 31 de julio), y el fallback silencioso a `budgetStartDay = 1`.
**Archivos:** `DashboardServiceImpl.java:75-87`, `BudgetAllocationServiceImpl.java:47,91-97,142`, `DashboardScreen.tsx:40-42`, `SummaryScreen.tsx:32-34,56-58`, `BudgetScreen.tsx:46-48`, `ConsolidatedScreen.tsx:29-31`, `MovementsScreen/ListScreen`, `FastingHistoryScreen.tsx:25-27`
**Esfuerzo:** medio
**Prueba:** cambiar `budgetStartDay` de 1 a 28 y verificar que las cinco pantallas rotulan y consultan el mismo rango, sin reiniciar la app.

#### 2.2 — Un solo predicado de "movimiento computable"

**Qué:** un helper `esComputable(m) = activo && !isTransfer && parentMovementId == null`, aplicado en los **seis** puntos de agregación. Alinear `ExcelExportService` con las consultas *household-aware*. Definir con una frase en la doc: *el hijo es detalle del padre, nunca suma aparte*.
**Por qué:** hoy `totalExpense` y `budgetTotal` usan reglas opuestas **en el mismo método**, y el Excel exporta el catálogo compartido pero solo tus movimientos.
**Archivos:** `DashboardServiceImpl.java:104-113,124-131,189-215,367`, `BudgetAllocationServiceImpl.java:160-166,318-338`, `ExcelExportService.java:45-48`
**Esfuerzo:** bajo (una vez decidida la regla)
**Prueba:** test de integración con un fixture conocido — padre 500.000 + dos hijos + una transferencia + una categoría compartida de la pareja — que asserta **números exactos** en Dashboard, Resumen, Presupuesto, Consolidado y Excel. Los cinco deben coincidir. *Éste es el test que compra la coherencia entera.*

#### 2.3 — Fechas y signos

**Qué:** prohibir `toISOString()` para derivar fechas locales (usar el `toISODate` que ya existe en `periodUtils.ts`); `formatDate` parsea en local; `formatCurrency` conserva el signo y se quitan los tres prefijos manuales que hoy lo parchean; `DonutChart` pinta `line.label`. Una regla de lint que prohíba `toISOString` fuera de `periodUtils`.
**Por qué:** en Bogotá, todo lo registrado después de las 19:00 se fecha mañana; un ayuno iniciado a las 20:30 se registra **24 h en el futuro** y el cronómetro corre en negativo; y un déficit de -500.000 se muestra como "$500.000".
**Archivos:** `MovementFormScreen.tsx:39`, `ScheduledFormScreen.tsx:56`, `FastingDashboardScreen.tsx:72-81`, `utils/format.ts:14-33`, `DonutChart.tsx:93-105`
**Esfuerzo:** bajo
**Prueba:** tests de `format.ts` y de los tres formularios con `TZ=America/Bogota` y hora fijada a las 22:00.

#### 2.4 — Recurrencias idempotentes y omitibles

**Qué:** persistir el periodo de la ocurrencia en el `Movement` y usar `(scheduledMovementId, periodStart)` como clave de idempotencia, con **UNIQUE en BD** (2.7). Crear el movimiento con `scheduled.getUserId()`, no con el del que pulsa. Estado `OMITIDO` para que borrar un pendiente sirva. `updateMovement` solo sobrescribe `scheduledMovementId` si viene no nulo. Resincronizar pendientes no confirmados al editar el programado. `calculateEndDate` → `plusMonths(n).minusDays(1)`.
**Por qué:** hoy borrar el pendiente del gimnasio lo resucita en el siguiente foco del Dashboard; editar el monto del arriendo lo desvincula y genera un duplicado; y el arriendo de Anderson queda a nombre de quien abrió la app primero.
**Archivos:** `ScheduledMovementServiceImpl.java:105-129,213-295`, `MovementServiceImpl.java:120,258-278`, `DashboardServiceImpl.java:81-87`, `MovementFormScreen.tsx:124-133`
**Esfuerzo:** medio
**Prueba:** invocar `generatePending` 5 veces seguidas con dos usuarios del mismo hogar y días de corte distintos → exactamente un pendiente por programado y periodo, a nombre del dueño del programado.

#### 2.5 — La transferencia es un par atómico

**Qué:** en `updateMovement` y `confirmWithAmount`, si `isTransfer`, propagar importe/fecha al `transferPairId` en la misma transacción — o bloquear la edición y ofrecer "anular y rehacer" (`delete` ya lo hace bien). Excluir **estructuralmente** ambas patas de los agregados de ingreso/gasto.
**Por qué:** hoy corregir una transferencia de 500.000 a 300.000 deja `shared.balance + personal.balance ≠ total.balance` en 200.000 inexistentes.
**Archivos:** `MovementServiceImpl.java:109-128,193-197`, `BudgetAllocationServiceImpl.java:328-365`, `DashboardServiceImpl.java:375-399`
**Esfuerzo:** bajo-medio
**Prueba:** invariante en test — para cualquier fixture, `shared.balance + personal.balance == total.balance`, desde la vista de **ambos** miembros del hogar.

#### 2.6 — Los errores se ven, y las listas se refrescan

**Qué:** un hook `useFetch` compartido que distinga tres estados (cargando / vacío / error con `res.message` y botón Reintentar) y sustituya los ocho catch vacíos y los doce `if (res.correct)` sin `else`. `useFocusEffect` en `MovementsListScreen`. `Spinner` en `HouseholdScreen`. `RefreshControl` con estado real en las dos pantallas de ayuno.
**Archivos:** `SummaryScreen.tsx:52-62`, `MovementsListScreen.tsx:37-52`, `HouseholdScreen.tsx:23,54,150`, `CategoryDrillDownScreen.tsx:44-48`, `FastingDashboardScreen.tsx:50,164`, `FastingHistoryScreen.tsx:43,59`, `DashboardScreen.tsx:53-57`, `ScheduledScreen.tsx:45`, `CategoriesScreen.tsx:36-40`
**Esfuerzo:** medio
**Prueba:** por pantalla, un caso `{correct:false, message:'X'}` que asserta que "X" es visible. Hoy solo existe en 3 de 23 archivos, y las tres son escrituras.

#### 2.7 — Flyway y las invariantes que el código olvida

**Qué:** baseline con `pg_dump --schema-only` de la BD real → `V1__linea_base.sql`, `baseline-on-migrate: true`, y **`ddl-auto=validate`** en los tres servicios. Luego `V2`: `UNIQUE(categoryId, periodStart) WHERE active`, `UNIQUE(scheduledMovementId, periodStart)`, `UNIQUE(householdId, userId) WHERE active`, FK sobre `categoryId`/`parentId`/`householdId`, `CHECK amount > 0`. Test en CI que falle si `ddl-auto` no es `validate` en ningún perfil.
**Por qué:** hoy un doble toque en Presupuesto inserta dos filas y a partir de ahí ese `Optional` lanza `NonUniqueResultException` **para siempre** en esa categoría, sin forma de arreglarlo desde la app. Y con `ddl-auto=update`, un `@Column(nullable=false)` sobre tabla con datos falla como WARN y **la app arranca igual** con el esquema desincronizado.
**Archivos:** `*/src/main/resources/application.properties` (los tres), nuevo `db/migration/`, `backend/init-db/`
**Esfuerzo:** medio
**Prueba:** Postgres vacío en Testcontainers → Flyway → app con `validate` → arranca. Más `pg_dump` de ambos y `diff`.

#### 2.8 — Núcleo familiar usable

**Qué:** invitación **por correo** (el backend ya identifica por email en el login), no por ID de fila. Cablear `removeMember` (visible solo al OWNER, con confirmación). Validar que el hogar y el usuario existen. Al expulsar: reparentar a raíz las subcategorías huérfanas y desactivar las asignaciones sobre categorías del hogar. En `getTree`, promover a raíz los nodos sin padre visible. Usar el `...AndHouseholdIdIsNull` que ya está declarado y nadie invoca.
**Por qué:** es una de las tres patas del producto y hoy exige que el usuario adivine un número de base de datos, y una vez agregado alguien no hay forma de quitarlo desde la app.
**Archivos:** `HouseholdServiceImpl.java:52-87,116`, `HouseholdScreen.tsx:40-51,91-97,138-146`, `CategoryServiceImpl.java:214-217`, `BudgetAllocationServiceImpl.java:97`
**Esfuerzo:** medio

#### 2.9 — Coherencia de lenguaje y de gestos

**Qué:** un glosario aplicado: **"Asignación"** = `BudgetAllocation`, **"Gasto previsto"** = `budgetTotal`, y **Programado → Pendiente → Confirmado** como único ciclo de vida (fuera "Por pagar", "Ejecutados"). Confirmación en las cuatro acciones destructivas (reutilizando el helper que ya existe en `SettingsScreen:28-37` para el logout) y `loading` en esos botones. Título dinámico del modal de movimiento. `statusLabel` derivado del mismo `balance` que colorea el badge. Ocultar la tarjeta "Presupuesto" cuando `viewMode !== 'total'` en vez de mostrar el gasto bajo ese rótulo. Confirmar con monto 0 devuelve error en vez de aplicar el original. Borrar los tres `tabPress` con `reset`. Conectar `editSession` y `setWaterGoal`; **borrar** `getTrend` y `users/delete` del front.
**Por qué:** son ~15 hallazgos individualmente pequeños, todos baratos, y son exactamente los que producen "la app se mueve sola" y "no sé qué significa Presupuesto aquí".
**Esfuerzo:** bajo cada uno, medio en conjunto

> **Criterio de HECHO de la Ola 2**
> El test de fixture conocido (2.2) da **la misma cifra** en las cinco superficies; cambiar el día de corte no pierde ni un presupuesto; ninguna acción destructiva ocurre sin confirmación; y cuando el backend responde `correct:false`, el usuario **lee el mensaje** en las 23 pantallas.

---

### 🚀 OLA 3 — SIGUIENTE NIVEL

*Aquí ya no arreglas: decides qué producto es. El orden importa porque hay una dependencia dura.*

#### 3.1 — Cuentas y saldo calculado (**el cimiento**)

**Qué:** dos clases de cuenta (`propia`, `pasivo`). El saldo **nunca se guarda, se calcula** (`saldoInicial + Σ movimientos`), como en Firefly III y Maybe. Movimiento de tipo `apertura` con fecha explícita, no un campo de la cuenta. Migración: lo existente va a una cuenta "Sin asignar" — nadie clasifica 400 movimientos para poder abrir la app. Conciliación pobre pero suficiente: "¿cuánto dice tu banco?" → si hay diferencia, crear movimiento de ajuste.
**Por qué:** sin saldo, la app es una lista de deseos: nada te dice si olvidaste anotar tres gastos. Con saldo, la app hace una afirmación falsable que el banco confirma o desmiente — **ése es el momento en que el usuario empieza a confiar**. Y desbloquea 3.4 y la mitad de 3.2.
**Esfuerzo:** medio (es sobre todo modelo de datos) · **Beneficio:** muy alto

#### 3.2 — El reparto bien hecho (**la joya, y nadie la tiene**)

**Qué:** separar tres conceptos hoy fundidos: **quién pagó**, **entre quiénes se reparte**, y **cuánto me toca**. La regla de oro: un gasto de 120.000 pagado por ti entre tres personas es **120.000 en tu cuenta** (para que cuadre con el banco) pero **40.000 en tu categoría** (para que el presupuesto no mienta); los otros 80.000 son un derecho de cobro, no un gasto. Cuatro modos de reparto (partes iguales / participaciones / porcentaje / importe). Balance neto por persona al estilo Cospend, no "A le debe X a B". Liquidación como movimiento propio, excluido de ingresos igual que las transferencias.
**Por qué:** Firefly III no lo resuelve (documenta compartir usuario y contraseña), YNAB tampoco (existe un producto de terceros entero para taparlo), Monarch asume bolsa común y no calcula deudas, y Splitwise no presupuesta. **Es la única razón por la que alguien elegiría Oh Churus sobre Actual Budget**, que es gratis y mejor en todo lo demás. Además, la `transfer()` actual es un parche artesanal de este problema — esto la reemplaza y arregla de paso el descuadre del consolidado.
**Esfuerzo:** medio-alto (y con riesgo de diseño: no hay a quién copiar entero) · **Beneficio:** muy alto

#### 3.3 — Sobres con la regla asimétrica

**Qué:** una sola regla, explicada en una frase dentro de la app: *lo que sobra en una categoría se queda; lo que te pasaste se descuenta de lo que tienes para repartir el mes que viene*. Una única excepción, el interruptor por categoría de Actual, **nombrado por su caso de uso**: "Es dinero que me van a devolver". Mover entre sobres a un toque. Recalcular el arrastre desde el origen, nunca materializado por mes.
**Por qué:** reemplaza el `BudgetAllocation` actual, cuyo `status` solo se escribe y nunca filtra nada (`autoCloseExpired` es código muerto sin endpoint ni `@Scheduled`). Y el interruptor conecta directamente con 3.2: "puse la cuenta del restaurante" deja de romper el mes.
**Esfuerzo:** medio · **NO incluir:** Age of Money.

#### 3.4 — Importación CSV

**Qué:** asignación manual de columnas con **perfil recordado por banco** (el 80 % del valor del importador de Firefly III sin su complejidad). Dedupe al estilo Actual, no al de Firefly: identificador del banco si existe; si no, **mismo importe (obligatorio) + ventana ±5 días + descripción parecida** (Levenshtein normalizado, sin librerías). Pantalla previa con tres listas: nuevos, duplicados, y los que **se casan con una recurrencia pendiente**. Diccionario `descripción → categoría` aprendido del usuario.
**Por qué:** el coste de entrada de datos es lo único que decide la retención. Nadie abandona por informes feos; todos abandonan por teclear 60 movimientos al mes.
**Requiere 3.1.** · **Esfuerzo:** medio · **Beneficio:** muy alto

#### 3.5 — Recurrencias reales

**Qué:** enumerar ocurrencias desde el ancla (nunca "última + 1 mes"), lo que hace que DAILY/WEEKLY/BIWEEKLY generen lo que prometen en vez de un movimiento al mes. Patrón "el tercer viernes" (así se paga la nómina) y política de fin de semana. Tope de materialización: si hay más de 5 ocurrencias atrasadas, no las cree en silencio — muéstralas para revisar. **La app no inventa datos.**
**La clave de idempotencia ya la habrás puesto en 2.4**, así que esto es incremental.

#### 3.6 — Infraestructura de confianza

Testcontainers + `@DataJpaTest` para las 16 consultas JPQL que hoy nunca se han ejecutado contra un motor. `springdoc-openapi` con la spec commiteada + `oasdiff` fallando el PR ante cambios rompedores + tipos generados en el front (`orval` si adoptáis TanStack Query). Umbrales de cobertura **de ramas** en `service/` y `controller/`. Karate contra el **gateway**, no contra el puerto del microservicio, y corriendo en CI.

---

## 4. Lo que NO hay que hacer

Esta sección vale tanto como el plan.

### Ruido: hallazgos reales que no merecen tu tiempo

| Hallazgo | Por qué es ruido |
|---|---|
| **Emails en logs de auth** | Loguear el email en eventos de autenticación es práctica estándar. Lo accionable ahí era el rate limiting, no el log. |
| **`SecurityUtilsTest` "certifica el NPE"** | Es código muerto. En cuanto lo cablees en 1.1, el test se reescribe solo. Cero impacto hoy. |
| **`parentMovementId` inmutable en update** | La UI ni siquiera ofrece reasignar padre. La mitad que sí importa (validar que la suma de hijos no supere al padre) va en 2.2. |
| **`api.test.ts` comparando constantes consigo mismas** | Bórralos, no los arregles. |
| **`DTOTest`/`EnymsTest` (58 tests de Lombok)** | Bórralos. Sonar ya los excluye; jacoco no, y por eso el número local miente. |
| **Los tres `tabPress` con `reset`** | Bórralos (3 líneas). React Navigation ya hace `popToTop`. |
| **Catálogo de iconos triplicado** | Molesto, invisible para el usuario. Ola 3 o nunca. |

### Mejoras "de libro" que en **este** proyecto no compensan

1. **Partida doble literal de Firefly III.** Duplica el volumen de datos y obliga a modelar cuentas de gasto e ingreso que en Oh Churus ya son categorías. **Roba la regla estructural de las transferencias, no la maquinaria.**
2. **Row Level Security en Postgres — ahora no.** Es la única capa que convierte "no debería pasar" en "no puede pasar", pero exige un rol de app sin ownership, `SET LOCAL`/`RESET` por transacción (una fuga aquí cruza datos entre peticiones del pool), otro rol para Flyway, y Testcontainers para probarla. **Es fase 2 de la Ola 3, después de 2.7.** Ponerla antes es construir el techo sin los muros.
3. **Pact / consumer-driven contract testing.** Con un único consumidor, OpenAPI + validador de respuestas tiene mucha mejor relación coste/beneficio.
4. **Cron obligatorio para recurrencias.** Es la fuente número uno de fricción de soporte de Firefly III. El modelo de propuesta de Actual + idempotencia da el mismo resultado percibido con una fracción del riesgo.
5. **Rediseño de identidad de marca** (ardilla SVG, repaletizar a marrones, tokens de radio y sombra, sustituir los 51 hex sueltos). Todo cierto, nada de ello es la razón por la que la app se siente rota. **Excepción barata que sí hago:** corregir `fontSize.xs === fontSize.sm` (dos escalones idénticos), subir los textos de 10-11 px, y los ~6 colores del tema claro que están bajo 3:1 — o, si no quieres tocarlos, **quita el toggle de tema claro** en vez de dejarlo roto. Media hora en cualquiera de los dos casos.
6. **Accesibilidad completa** (0 `accessibilityLabel` / `accessibilityRole` en 22 pantallas). Es un hueco real y en un producto público sería bloqueante. Para una app que hoy usan dos personas, no compite con "mis cifras están mal". **Lo que sí hago ya: añadir `testID`**, porque paga inmediatamente en las pruebas de la Ola 1 y 2.
7. **Convertir los 55 POST en verbos REST.** Cosmética. El patrón `ResultDTO` es una decisión legítima y el front entero depende de ella. No la toques; **impónla** (1.4).
8. **Fusionar los microservicios.** Cinco servicios + Eureka + gateway para dos usuarios es sobreingeniería, sí. Pero consolidarlos ahora es un coste enorme con **cero** beneficio para el usuario y riesgo de romper lo que acabas de estabilizar. Es deuda que **no hay que pagar**.
9. **Escribir más features de Karate antes de cablearlas al CI.** Hoy tienes 36 escenarios que nadie ejecuta. Añadir cinco más produce 41 escenarios que nadie ejecuta. **Primero el job, después los escenarios.**
10. **Subir a Spring Boot 3.4+ durante la Ola 1.** Tiene sentido (Boot 3.2 está fuera de soporte OSS, y las meta-anotaciones parametrizadas de `@AuthenticationPrincipal` exigen Security 6.4+), pero mezclar un salto de versión con el refactor de identidad convierte cada fallo en una investigación de dos causas. **Hazlo entre la Ola 1 y la 2, con la matriz de aislamiento ya en verde como red.**
11. **Observabilidad (Micrometer, Zipkin, Prometheus, `X-Request-Id`).** Correcto en abstracto, desproporcionado aquí. Lo único que sí vale: **actuator + healthcheck en el gateway**, que hoy es el único servicio sin ninguno y por el que pasa el 100 % del tráfico.
12. **Paginación infinita.** Sube el `size` a 200 en `MovementFilterDTO`, arregla `SummaryScreen` (que hoy pide 200 contra un `@Max(100)` y se traga el 400 en un catch vacío) y sigue adelante. Con dos usuarios no vas a superar el tope.

---

## 5. El primer paso

Si solo se pudiera hacer **una cosa** esta semana:

> ### Escribir la matriz de aislamiento cruzado — en rojo — y luego hacerla verde propagando el `userId` del JWT.

Concretamente, en este orden:

1. **Lunes, primera hora (30 min):** el parche 1.0. `UserRegisterDTO` sin `id` y fuera los puertos publicados. Es el único agujero explotable **sin token** y no puede seguir abierto ni un día más.
2. **Resto de la semana:** `CrossUserAccessTest` sobre los 55 endpoints + el test reflexivo que bloquea endpoints nuevos. **Escríbelo antes de tocar el código de producción.** Arrancará casi 100 % en rojo, y ese porcentaje es la primera medida honesta que este proyecto ha tenido.
3. Y con la matriz en la mano, 1.1 (el claim `userId` en el filtro) + 1.3 (propiedad en cada servicio). El test te dice **cuándo has terminado** — algo que hoy ninguna métrica del proyecto sabe hacer.

**Por qué esto y no el `$NaN`, ni la IP quemada, ni el registro roto** —que son más visibles y más baratos:

- Porque es la **única** tarea que es prerequisito duro de todo lo demás. `budgetStartDay` no puede salir del usuario si no sabes quién es el usuario; `generatePending` no puede atribuir al dueño; el reparto de la Ola 3 no puede existir.
- Porque **hoy tu suite defiende el bug**: `MovementServiceImplEdgeCasesTest:214` asserta que un update reasigna el movimiento del usuario 99 al usuario 2. En cuanto añadas la primera comprobación de propiedad, ese test se pondrá rojo y, sin la matriz al lado, la tentación será "arreglar el test". La matriz es lo que hace que ese rojo signifique *progreso* en vez de *regresión*.
- Porque los tres flujos rotos son **una tarde cada uno** y no se van a ir a ningún lado. La deuda de identidad, en cambio, **crece con cada endpoint nuevo** que escribas.
- Y porque cambia la conversación: hoy tienes 502 tests verdes y una sensación de inestabilidad que no puedes señalar. El viernes tendrás un número rojo que **es exactamente la distancia** entre donde estás y donde quieres estar. Eso es lo que le falta a este proyecto: no más tests, sino un test que pueda fallar.