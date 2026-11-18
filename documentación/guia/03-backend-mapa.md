# 03 — Mapa técnico del backend

> Para quien llega a tocar el backend sin contexto: **dónde está cada cosa**.
> Fuente: el código de `backend/`, `docker-compose.yml` y `.github/workflows/` (noviembre 2026, tras la ola 7.5).
> El `README.md` y el resto de `documentación/` están **desactualizados**: hablan de fasting-service, cuentas, repartos y transferencias, que ya no existen. Si chocan con esto, manda el código.

Rutas abreviadas en este documento:

- `AUTH` = `backend/auth-service/src/main/java/com/ohchurus/auth/`
- `BUDGET` = `backend/budget-service/src/main/java/com/ohchurus/budget/`
- `MIG-AUTH` = `backend/auth-service/src/main/resources/db/migration/`
- `MIG-BUDGET` = `backend/budget-service/src/main/resources/db/migration/`

---

## 1. Servicios, puertos y stack

### 1.1 Módulos Maven

`backend/pom.xml` es el padre (`com.ohchurus:oh-churus`, packaging `pom`) con 4 módulos:

| Servicio | Puerto | Context path | Base de datos | Qué hace |
|---|---|---|---|---|
| `discovery-service` | 8760 | — | — | Servidor Eureka (no se registra a sí mismo). |
| `gateway-service` | 8820 | — | — | Spring Cloud Gateway (reactivo). Única puerta publicada hacia la red. CORS abierto (`allowedOriginPatterns: *`). |
| `auth-service` | 8821 | `/oh-churus` | `auth_db` | Usuarios, login/registro, emisión del JWT, roles, módulos/páginas/permisos (menú), preferencias, auditoría. |
| `budget-service` | 8823 | `/oh-churus` | `budget_db` | Todo lo financiero: categorías, movimientos, programados, presupuesto, hogar, tarjetas/deudas, plan, estado del periodo, Excel. |

No hay más servicios. **fasting-service ya no existe** (queda su spec en `documentación/api/fasting-service.json`, ver §9).

### 1.2 Stack exacto (según los pom y el compose)

| Pieza | Versión | Dónde se fija |
|---|---|---|
| Java | 17 (`eclipse-temurin:17-jre-jammy` en los Dockerfile) | `backend/pom.xml` (`java.version`) |
| Spring Boot | 3.2.5 | parent de `backend/pom.xml` |
| Spring Cloud | 2023.0.3 | `spring-cloud.version` |
| PostgreSQL | 14 (imagen `postgres:14`) | `docker-compose.yml` |
| Flyway | la que gestiona Boot 3.2.5 (`flyway-core`, sin versión explícita) | pom de auth y budget |
| JPA/Hibernate | la de Boot; `ddl-auto=validate` en todos los perfiles | `application.properties` |
| JWT | `com.auth0:java-jwt` 4.4.0 (HMAC256) | pom de auth y budget |
| ModelMapper | 2.4.2 | pom de auth y budget |
| springdoc-openapi | 2.3.0 (rama para Boot 3; la 3.x no vale) | `springdoc.version` en el pom raíz |
| Apache POI | 5.2.5 (`poi-ooxml`, para el Excel) | pom de budget |
| Lombok | la de Boot; `lombok.config` añade `@lombok.Generated` para JaCoCo/Sonar | `backend/lombok.config` |
| Pruebas | JUnit 5, Mockito, spring-security-test, H2, Testcontainers (`postgresql`, `junit-jupiter`), Karate 1.5.0, JaCoCo 0.8.11 | pom de auth y budget |

Otras cosas del pom raíz que conviene conocer:

- `surefire` con `runOrder=alphabetical` (orden de pruebas igual en Windows y Linux).
- `docker.api.version=1.44` se pasa a las pruebas: con Docker Engine 29, sin esto Testcontainers concluye que "no hay Docker" y **se salta** las pruebas en silencio.
- `actualizar.spec=false` por defecto (ver §8, prueba de la spec OpenAPI).

### 1.3 Cómo se hablan

```
cliente (app Expo) ──HTTP──► gateway-service :8820
                                │  rutas automáticas por Eureka:
                                │  /AUTH-SERVICE/**   → auth-service
                                │  /BUDGET-SERVICE/** → budget-service
                                ▼
                     discovery-service :8760 (Eureka)

budget-service ──RestClient directo──► auth-service (/v1/users/all) solo para invitar por correo
```

- **Gateway sin rutas escritas a mano.** `GatewayServiceApplication` declara un bean `DiscoveryClientRouteDefinitionLocator`: cada servicio registrado en Eureka se publica con su nombre en MAYÚSCULAS como prefijo. Una URL completa queda así:
  `http://<gateway>:8820/BUDGET-SERVICE/oh-churus/v1/movements/all`
  (prefijo del gateway + context path `/oh-churus` + ruta del controller). El frontend usa exactamente estas bases (`frontend/src/services/*.ts`, p. ej. `'/BUDGET-SERVICE/oh-churus/v1/movements'`).
- El gateway **no valida el JWT**; cada servicio lo valida por su cuenta.
- En `docker-compose.yml` auth (8821), budget (8823) y Postgres (5432) se publican solo en `127.0.0.1`; gateway (8820) y discovery (8760) en todas las interfaces.
- Única llamada entre servicios: `BUDGET/client/DirectorioDeUsuariosHttp` hace `POST /v1/users/all` contra auth-service **reenviando el token del usuario que invita**, con timeouts cortos (2 s conexión / 3 s lectura). La URL sale de `app.auth-service-url` (por defecto apunta al nombre del contenedor de auth, sin pasar por Eureka ni gateway). Interfaz: `BUDGET/client/DirectorioDeUsuarios`.
- `backend/init-db/init-databases.sql` crea `auth_db` y `budget_db` al iniciar el contenedor de Postgres por primera vez.
- Variables de entorno relevantes (con valor por defecto en `application.properties`): `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `SECRET` (clave HMAC del JWT, **debe ser la misma en auth y budget**), `EXP_TIME` (ms, por defecto 10 días), `AUTH_SERVICE_URL`, `AUDIT_RETENTION_DAYS`, `AUDIT_CLEANUP_CRON`, y `SEED_*_PASSWORD` para las cuentas semilla. Los valores reales viven en `.env` (ignorado por git); **nunca** se copian a la documentación.
- Orden de arranque en compose: postgres y discovery (healthcheck) → gateway, auth, budget. Cada contenedor de servicio tiene techo de memoria (`mem_limit`) y `JAVA_TOOL_OPTIONS` con `MaxRAMPercentage=75` y `UseSerialGC`.
- Las imágenes solo copian el jar de `target/`: hay que hacer `mvn -DskipTests package` antes de `docker compose up --build`.

Swagger UI por servicio (sin token): `/oh-churus/swagger-ui.html`; spec: `/oh-churus/v3/api-docs`.

---

## 2. Autenticación y autorización

### 2.1 Emisión del token (auth-service)

- `POST /v1/auth/login` → `AUTH/controller/AuthenticationController.login` → `AUTH/service/impl/AuthenticationServiceImpl.authenticate`.
  1. `LimiteDeIntentosDeLogin` (`AUTH/security/`): 5 fallos en 5 minutos **por correo** bloquean ese correo (en memoria; se pierde al reiniciar). Bloqueado → `ResultDTO(false, ..., 429)`.
  2. `AuthenticationManager` + `DaoAuthenticationProvider` + `BCryptPasswordEncoder` (`AUTH/security/SecurityBeansConfig`, `MyUserDetailsService`).
  3. Firma JWT HMAC256 con `secret`:
     - `sub` = email
     - `userId` (Long)
     - `name`
     - `roles` = array con los nombres de los roles **activos** del usuario (ordenados)
     - `exp` = ahora + `expired-time`
  4. Publica un `CambioAuditable` de tipo `LOGIN` (solo logins exitosos).
  5. Respuesta: `ResultDTO` con `{token, email, name, userId, roles}`.
  - Credenciales malas → `ResultDTO(false, "Correo o contraseña incorrectos", 101)` (el mismo mensaje exista o no el correo).
- `POST /v1/auth/register` → `UserServiceImpl.register(UserRegisterDTO)` y, si va bien, hace login y devuelve **lo mismo que /login** (token incluido). `UserRegisterDTO` no tiene `id` a propósito.

### 2.2 Validación del token (ambos servicios)

`JWTAuthorizationFilter` (idéntico en `AUTH/security/` y `BUDGET/security/`), registrado antes de `UsernamePasswordAuthenticationFilter`:

- Sin cabecera `Authorization: Bearer ...` → sigue sin autenticación (Spring Security responderá 401/403 si la ruta lo exige).
- Token inválido o caducado → **401** con cuerpo `{"correct":false,"message":"Invalid or expired token","errorCode":401}`.
- Token válido → `Authentication` con `principal = email`, `authorities = roles` del claim y **`details = userId`**.

`SecurityUtils` (`AUTH/util/` y `BUDGET/util/`) es la única forma de saber quién llama:
`getAuthenticatedUserId()`, `getAuthenticatedEmail()`, y en auth también `esDelUsuario`, `tieneRol`, `esAdmin`, `puedeAcceder`.

> **Regla del proyecto:** el `userId` sale **siempre del token**, nunca del cuerpo. Algunos DTO todavía traen un campo `userId` (p. ej. `DashboardRequestDTO`, `HogarPorUsuarioDTO`) por compatibilidad con el frontend, pero los controllers lo ignoran. La prueba `LaIdentidadNoVuelveAlCuerpoTest` lo vigila.

### 2.3 Reglas de `SecurityConfig`

| Servicio | Público | Exige rol | Resto |
|---|---|---|---|
| auth | `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/v1/auth/login`, `/v1/auth/register` | `/v1/admin/**` → `ADMIN` | autenticado |
| budget | `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**` | — (no hay reglas por rol) | autenticado |

Ambos: CSRF desactivado, sesión `STATELESS`.

### 2.4 Roles y permisos (auth_db)

- Roles semilla (migración V3): **`ADMIN`** y **`USER`**. Tabla puente `oc_auth_user_role`.
- Modelo de plataforma (V4): **módulo → páginas → permisos por rol** (`can_read/create/update/delete`). `POST /v1/menu/mine` (`MenuServiceImpl`) arma el menú del usuario a partir de los roles del token. Cada `page.route` debe coincidir con un `name="..."` de `AppNavigator.tsx` del frontend (lo comprueba `ElMenuLlevaAPantallasQueExistenTest`).
- `AdminUserServiceImpl` impide desactivarse a uno mismo; `RoleServiceImpl`/`AdminModuleServiceImpl` protegen el último ADMIN y que ADMIN no pierda el módulo de administración (mensajes en `AUTH/enums/Message.Msj`).

### 2.5 Hogar (household) y miembros — budget-service

- Entidades `Household` y `HouseholdMember` (`role` = `OWNER` | `MEMBER`, `displayName` = apodo).
- `HouseholdServiceImpl` (`BUDGET/service/impl/`):
  - `create`: crea el hogar y mete a quien llama como `OWNER`.
  - `invitarPorCorreo`: solo el OWNER; resuelve el correo contra auth-service (`DirectorioDeUsuariosHttp`) y llama a `addMember`.
  - `addMember`: solo el OWNER, y solo si el invitado **no pertenece ya a un hogar activo** (una persona = un hogar activo). Mismo mensaje exista o no el usuario (no se revela quién tiene cuenta).
  - `removeMember`, `ponerApodo` (OWNER o el propio miembro), `getByUser`, `getHouseholdIds`, `apodoDe`.
- **No hay invitaciones pendientes/aceptación**: invitar agrega directamente.
- Qué se comparte: una `Category` con `householdId != null` es compartida; los movimientos, programados y asignaciones **en esa categoría** los ven todos los miembros. Lo decide `BUDGET/util/ControlAcceso` (`esMio`, `esDeMiHogar`, `puedeVerCategoria`, `puedeVer`) y, en Postgres, las políticas RLS (§6).

---

## 3. Endpoints

Todos son **POST** y devuelven `ResponseEntity<ResultDTO>` con HTTP 200 (salvo el Excel, que devuelve bytes). Rutas relativas al context path `/oh-churus`.

### 3.1 auth-service

| Ruta | Controller | Service → método | Para qué |
|---|---|---|---|
| `/v1/auth/login` | `AuthenticationController` | `AuthenticationService.authenticate` | Login; devuelve el JWT. Público. |
| `/v1/auth/register` | `AuthenticationController` | `UserService.register` + `authenticate` | Alta pública; devuelve sesión como /login. |
| `/v1/users/save` | `UserController` | `UserService.saveAndUpdate` | Editar **la propia** cuenta (sin `id` → 403; las altas van por register o admin). |
| `/v1/users/get/{id}` | `UserController` | `UserService.getById` | Ficha propia (otro id → "Usuario no encontrado", 103). |
| `/v1/users/all` | `UserController` | `UserService.getAll` | Sin email: devuelve solo a uno mismo. Con email: coincidencia exacta (lo usa budget para invitar). |
| `/v1/users/delete/{id}` | `UserController` | `UserService.delete` | Baja lógica de la propia cuenta. |
| `/v1/menu/mine` | `MenuController` | `MenuService.miMenu` | Menú (módulos/páginas) según los roles del token. |
| `/v1/preferences/mine` | `PreferenciasController` | `PreferenciasService.misPreferencias` | Moneda y decimales del usuario (con valor global por defecto). |
| `/v1/admin/config/all` | `PreferenciasController` | `PreferenciasService.getConfiguracion` | ADMIN: configuración global (`oc_auth_app_config`). |
| `/v1/admin/config/save` | `PreferenciasController` | `PreferenciasService.guardarConfiguracion` | ADMIN: guardar una clave de configuración. |
| `/v1/admin/users/all` | `AdminUserController` | `AdminUserService.getAll` | ADMIN: listado paginado de usuarios. |
| `/v1/admin/users/get/{id}` | `AdminUserController` | `AdminUserService.getById` | ADMIN: ficha de cualquier usuario. |
| `/v1/admin/users/save` | `AdminUserController` | `AdminUserService.crear` | ADMIN: crear usuario. |
| `/v1/admin/users/{id}/roles` | `AdminUserController` | `AdminUserService.asignarRoles` | ADMIN: asignar roles. |
| `/v1/admin/users/{id}/activate` | `AdminUserController` | `AdminUserService.cambiarActivacion(id,true)` | ADMIN: reactivar usuario. |
| `/v1/admin/users/{id}/deactivate` | `AdminUserController` | `AdminUserService.cambiarActivacion(id,false)` | ADMIN: desactivar (no a uno mismo). |
| `/v1/admin/roles/all` | `RoleController` | `RoleService.getAll` | ADMIN: listar roles. |
| `/v1/admin/roles/save` | `RoleController` | `RoleService.saveAndUpdate` | ADMIN: crear/editar rol. |
| `/v1/admin/roles/delete/{id}` | `RoleController` | `RoleService.desactivar` | ADMIN: desactivar rol. |
| `/v1/admin/modules/all` | `AdminModuleController` | `AdminModuleService.getModulos` | ADMIN: módulos con sus páginas. |
| `/v1/admin/permissions/{roleName}` | `AdminModuleController` | `AdminModuleService.getPermisosDeRol` | ADMIN: permisos de un rol por página. |
| `/v1/admin/permissions/save` | `AdminModuleController` | `AdminModuleService.guardarPermiso` | ADMIN: guardar permiso rol×página. |
| `/v1/admin/audit/all` | `AuditoriaController` | `AuditoriaService.consultar` | ADMIN: consultar el registro de auditoría con filtros. |

Implementaciones en `AUTH/service/impl/*ServiceImpl.java`. Auditoría: los servicios publican `CambioAuditable` (`AUTH/service/CambioAuditable`) y `AuditoriaListener` lo escribe en `oc_auth_audit_log`; `RetencionDeAuditoria` borra lo antiguo con `@Scheduled` (`audit.cleanup-cron`, retención `audit.retention-days`).

### 3.2 budget-service

| Ruta | Controller | Service → método | Para qué |
|---|---|---|---|
| `/v1/categories/save` | `CategoryController` | `CategoryService.saveAndUpdate` | Crear/editar categoría (personal o del hogar). |
| `/v1/categories/get/{id}` | `CategoryController` | `CategoryService.getById` | Una categoría. |
| `/v1/categories/all` | `CategoryController` | `CategoryService.getAll` | Listado con filtros. |
| `/v1/categories/tree` | `CategoryController` | `CategoryService.getTree(userId del token)` | Árbol padre/hijas (el cuerpo se ignora). |
| `/v1/categories/delete/{id}` | `CategoryController` | `CategoryService.delete` | Baja lógica (falla si tiene hijas). |
| `/v1/categories/type-list` | `CategoryController` | `CategoryService.typeList` | Opciones `INCOME`/`EXPENSE`. |
| `/v1/movements/save` | `MovementController` | `MovementService.saveAndUpdate` | Crear/editar movimiento (incluye compras con tarjeta/cuotas y gasto pagado con ahorro). |
| `/v1/movements/get/{id}` | `MovementController` | `MovementService.getById` | Un movimiento. |
| `/v1/movements/all` | `MovementController` | `MovementService.getAll` | Listado con filtros y búsqueda. |
| `/v1/movements/delete/{id}` | `MovementController` | `MovementService.delete` | Baja lógica. |
| `/v1/movements/confirm/{id}` | `MovementController` | `MovementService.confirm` / `confirmWithAmount` | Confirmar un pendiente (opcionalmente con otro monto). |
| `/v1/movements/confirm-batch` | `MovementController` | `MovementService.confirmarVarios` | Confirmar varios pendientes, todo o nada. |
| `/v1/movements/release/{id}` | `MovementController` | `MovementService.liberar` | Liberar plata de un compromiso del plan (total o parcial). |
| `/v1/movements/unrelease/{id}` | `MovementController` | `MovementService.deshacerLiberacion` | Deshacer una liberación. |
| `/v1/movements/reassign/{id}` | `MovementController` | `MovementService.reasignarPagador` | Cambiar quién paga un pendiente concreto (miembro del hogar). |
| `/v1/movements/by-period` | `MovementController` | `MovementService.getByPeriod` | Movimientos entre dos fechas. |
| `/v1/movements/children/{parentId}` | `MovementController` | `MovementService.getChildren` | Desglose (hijos) de un movimiento. |
| `/v1/scheduled/save` | `ScheduledMovementController` | `ScheduledMovementService.saveAndUpdate` | Crear/editar programado (recurrencia). |
| `/v1/scheduled/get/{id}` | `ScheduledMovementController` | `ScheduledMovementService.getById` | Un programado. |
| `/v1/scheduled/all` | `ScheduledMovementController` | `ScheduledMovementService.getAll` | Listado con filtros. |
| `/v1/scheduled/delete/{id}` | `ScheduledMovementController` | `ScheduledMovementService.delete` | Baja lógica. |
| `/v1/scheduled/generate-pending` | `ScheduledMovementController` | `ScheduledMovementService.generatePending` | Genera los pendientes del periodo y propone ocurrencias atrasadas (`REQUIRES_NEW`). |
| `/v1/scheduled/materialize` | `ScheduledMovementController` | `ScheduledMovementService.materialize` | Crea las ocurrencias atrasadas que el usuario acepte. |
| `/v1/scheduled/frequency-list` | `ScheduledMovementController` | `ScheduledMovementService.frequencyList` | Opciones de `Frequency`. |
| `/v1/budget-allocation/save` | `BudgetAllocationController` | `BudgetAllocationServiceImpl.save` | Fijar el presupuesto de una categoría en un periodo. |
| `/v1/budget-allocation/list` | `BudgetAllocationController` | `BudgetAllocationServiceImpl.list` | Asignaciones del periodo. |
| `/v1/budget-allocation/copy-previous` | `BudgetAllocationController` | `BudgetAllocationServiceImpl.copiarDelAnterior` | Copiar el presupuesto del periodo anterior a las categorías sin asignación. |
| `/v1/budget-allocation/delete/{id}` | `BudgetAllocationController` | `BudgetAllocationServiceImpl.delete` | Borrar una asignación. |
| `/v1/household/create` | `HouseholdController` | `HouseholdServiceImpl.create` | Crear hogar (quien llama queda OWNER). |
| `/v1/household/add-member` | `HouseholdController` | `HouseholdServiceImpl.addMember` | Agregar miembro por id (solo OWNER). |
| `/v1/household/invite` | `HouseholdController` | `HouseholdServiceImpl.invitarPorCorreo` | Agregar miembro por correo (solo OWNER). |
| `/v1/household/remove-member` | `HouseholdController` | `HouseholdServiceImpl.removeMember` | Sacar a un miembro. |
| `/v1/household/display-name` | `HouseholdController` | `HouseholdServiceImpl.ponerApodo` | Apodo de un miembro. |
| `/v1/household/by-user` | `HouseholdController` | `HouseholdServiceImpl.getByUser` | Mi hogar y sus miembros. |
| `/v1/cards/save` | `CardController` | `DeudasService.guardarTarjeta` | Crear/editar tarjeta de crédito (nombre, color, día de pago y de corte). |
| `/v1/cards/mine` | `CardController` | `DeudasService.misTarjetas` | Mis tarjetas con lo que debo en cada una. |
| `/v1/cards/pay/{id}` | `CardController` | `DeudasService.pagarTarjeta` | Pagar la tarjeta con monto y fecha (salda las compras más viejas; el sobrante queda como abono). |
| `/v1/cards/delete/{id}` | `CardController` | `DeudasService.borrarTarjeta` | Baja lógica de la tarjeta. |
| `/v1/debts/all` | `DebtController` | `DeudasService.deudas` | Lo que se debe: tarjetas y compras en cuotas. |
| `/v1/debts/advance/{scheduledMovementId}` | `DebtController` | `DeudasService.adelantarCuota` | Pagar hoy la siguiente cuota que falta de una deuda. |
| `/v1/plan/quincena` | `PlanController` | `PlanFinancieroService.quincena` | Plan de la quincena (solo lectura). |
| `/v1/plan/calendar` | `PlanController` | `PlanFinancieroService.calendario` | Calendario de pagos entre dos fechas. |
| `/v1/plan/responsibilities` | `PlanController` | `PlanFinancieroService.responsabilidades` | Quién paga qué en el hogar. |
| `/v1/plan/projection` | `PlanController` | `PlanFinancieroService.proyeccion` | Proyección de N periodos (por defecto 3). |
| `/v1/estado/periodo` | `EstadoController` | `EstadoDelPeriodoService.estado` | Cifras únicas del periodo (ingresos, presupuestado, real, comprometido, disponible, ahorro, bolsas...). Todas las pantallas leen de aquí. |
| `/v1/estado/historial` | `EstadoController` | `EstadoDelPeriodoService.historial` | Presupuestado vs. real por periodo (por defecto 6). |
| `/v1/dashboard/pending` | `DashboardController` | `DashboardService.getPending` | Pendientes del periodo. |
| `/v1/export/excel` | `ExportController` | `ExcelExportService.exportPeriod` | Descarga .xlsx del periodo (si falla devuelve `ResultDTO` de error, no un 500 vacío). |

Ojo: algunos controllers inyectan la **clase** del service (no una interfaz): `BudgetAllocationServiceImpl`, `HouseholdServiceImpl`, `DeudasService`, `PlanFinancieroService`, `EstadoDelPeriodoService`, `ExcelExportService`. Solo `Category`, `Movement`, `ScheduledMovement` y `Dashboard` tienen interfaz en `BUDGET/service/`.

La spec OpenAPI generada de cada servicio está commiteada en `documentación/api/auth-service.json` y `documentación/api/budget-service.json` y el CI comprueba que no se quede atrás.

---

## 4. Entidades → tablas

Todas las PK son `Long` con `GenerationType.IDENTITY`. **No hay relaciones JPA en budget** (solo columnas `*_id` sueltas); las FK reales están en la base (migración V2). Casi todas tienen `active` (borrado lógico) y `created_at`/`updated_at`.

### 4.1 auth_db (`AUTH/entity/`)

| Entidad | Tabla | Campos clave | Relaciones |
|---|---|---|---|
| `User` | `oc_auth_user` | `email` (único), `password` (BCrypt), `name`, `budget_start_day` (1–31, CHECK en V2), `currency`, `decimal_places`, `active` | `@ManyToMany` → `Role` vía `oc_auth_user_role(user_id, role_id)` |
| `Role` | `oc_auth_role` | `name` (único: `ADMIN`, `USER`), `description`, `active` | — |
| `Module` | `oc_auth_module` | `code` (único), `name`, `icon`, `sort_order` | — |
| `Page` | `oc_auth_page` | `code`, `name`, `route` (nombre de pantalla del frontend), `in_menu`, `sort_order`; único `(module_id, code)` | `@ManyToOne` → `Module` |
| `RolePermission` | `oc_auth_role_permission` | `can_read/can_create/can_update/can_delete`; único `(role_id, page_id)` | `@ManyToOne` → `Role`, `@ManyToOne` → `Page` |
| `AppConfiguration` | `oc_auth_app_config` | `config_key` (único), `config_value`, `description` | — |
| `AuditLog` | `oc_auth_audit_log` | `actor_user_id`, `actor_email`, `entity_type`, `entity_id`, `action` (enum `Accion`: LOGIN, USER_CREATE, USER_ACTIVATE, USER_DEACTIVATE, ROLE_CREATE, ROLE_UPDATE, ROLE_DEACTIVATE, ROLES_ASSIGNED, PERMISSION_CHANGE), `details`, `occurred_at` | — |

### 4.2 budget_db (`BUDGET/entity/`)

| Entidad | Tabla | Campos clave | Relaciones (por id) |
|---|---|---|---|
| `Category` | `oc_budget_category` | `user_id`, `name`, `type` (`INCOME`/`EXPENSE`), `parent_id`, `household_id` (null = personal; valor = compartida), `reimbursable`, `icon`, `color` | padre → `Category`; → `Household` |
| `Movement` | `oc_budget_movement` | `user_id`, `category_id`, `date`, `amount`, `confirmed` (false = pendiente), `scheduled_movement_id` + `period_start` (clave de la ocurrencia), `parent_movement_id` (desglose), `released_amount` (plata liberada), `funded_from_category_id` (pagado con ahorro), `card_id`, `card_paid`, `installment_number/total`, `purchase_id` | → `Category`, `ScheduledMovement`, `Movement` (padre), `Card` |
| `ScheduledMovement` | `oc_budget_scheduled_movement` | `user_id`, `category_id`, `name`, `amount`, `frequency`, `start_date`, `end_date`, `duration_months`, `day_of_month`, `week_of_month` + `day_of_week` ("el tercer viernes"), `weekend_policy`, `payer_user_id` (quién paga), `kind` | → `Category` |
| `BudgetAllocation` | `oc_budget_allocation` | `user_id`, `category_id`, `household_id`, `period_start`, `period_end`, `allocated_amount`, `notes`; única viva por (categoría, periodo) | → `Category`, `Household` |
| `Household` | `oc_budget_household` | `name`, `active` | — |
| `HouseholdMember` | `oc_budget_household_member` | `household_id`, `user_id`, `role` (`OWNER`/`MEMBER`), `display_name`; único vivo (hogar, usuario) | → `Household` |
| `Card` | `oc_budget_card` | `user_id`, `household_id`, `name`, `color`, `payment_day`, `cutoff_day`, `credit` (abono a favor) | → `Household` |
| `CardPayment` | `oc_budget_card_payment` | `card_id`, `user_id`, `amount`, `payment_date` | → `Card` |

Enums (`BUDGET/enums/`):
- `CategoryType`: INCOME, EXPENSE.
- `Frequency`: DAILY, WEEKLY, BIWEEKLY, MONTHLY, BIMONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL.
- `WeekendPolicy`: KEEP (por defecto), PREVIOUS_BUSINESS_DAY, NEXT_BUSINESS_DAY.
- `ScheduledKind`: PAYMENT, DEDUCTION, SAVING, PROVISION (null se lee como PAYMENT; PROVISION no genera pendiente).

Los `user_id` de budget apuntan a usuarios de **otra base** (auth_db): no hay FK posible entre ambas.

Tablas que **siguen existiendo en budget_db sin entidad** (restos de funciones retiradas en la ola 7): `oc_budget_account`, `oc_budget_movement_split`, `oc_budget_import_profile`, `oc_budget_import_rule`, y columnas viejas en `oc_budget_movement` (`is_transfer`, la cuenta, el id externo de importación...). Ver §9.

---

## 5. Migraciones Flyway

### 5.1 Reglas

- **NUNCA editar una migración ya aplicada.** Flyway guarda su checksum; tocarla rompe el arranque en producción. Todo cambio va en la **siguiente versión** (`V20__...` en budget, `V9__...` en auth).
- El número de versión es **único por servicio**, aunque la migración esté en `postgresql/` o `h2/`. Si un cambio necesita sintaxis distinta por motor, se crea la misma versión en `postgresql/` y en `h2/` (como `V2`). Si solo aplica a Postgres (RLS, índices parciales), va solo en `postgresql/`.
- `ddl-auto=validate`: si añades un campo a una entidad sin migración, el servicio **no arranca** (y las pruebas fallan).
- `baseline-on-migrate=true`: sobre la base de producción, V1 se dio por aplicada (la línea base es el DDL que antes generaba Hibernate).
- Pruebas: H2 en memoria con las mismas migraciones (`comun` + `h2`). Producción: PostgreSQL 14 (`comun` + `postgresql`), con RLS. Las pruebas de Testcontainers corren las migraciones de `postgresql/` contra un Postgres real.

### 5.2 budget-service — `MIG-BUDGET`

`spring.flyway.locations=classpath:db/migration/comun,classpath:db/migration/{vendor}` (`{vendor}` = `postgresql` o `h2`).

| Versión | Carpeta | Qué hace |
|---|---|---|
| V1 `linea_base` | comun | Esquema inicial (category, movement, scheduled_movement, allocation, household, household_member) e índices. |
| V2 `invariantes` | postgresql **y** h2 | NOT NULL, FK, CHECK y únicos que el código daba por supuestos; único parcial de asignación viva y de miembro vivo (en H2 sin `WHERE`). Limpia duplicados antes. **Si tocas uno, toca el gemelo.** |
| V3 `periodo_de_la_ocurrencia` | comun | Añade `period_start` a movement (clave de idempotencia de recurrencias). |
| V4 `cuentas` | comun | Tabla `oc_budget_account` y cuenta por movimiento. **Retirado** en ola 7. |
| V5 `patron_semanal_y_politica_de_fin_de_semana` | comun | `week_of_month`, `day_of_week`, `weekend_policy` en programados. |
| V6 `reparto_entre_personas` | comun | Tabla `oc_budget_movement_split`. **Retirado** en ola 7. |
| V7 `sobres_con_arrastre` | comun | Arrastre de sobres en category/allocation; quita columna `status`. Sobres **retirados** de la app. |
| V8 `importacion_csv` | comun | Tablas `oc_budget_import_profile`, `oc_budget_import_rule`, id externo en movement. **Retirado**. |
| V9 `row_level_security` | postgresql | Funciones `oc_usuario_actual`, `oc_mis_hogares`, `oc_categorias_de_mis_hogares` y políticas RLS (ver §6). |
| V10 `responsable_y_clase_del_programado` | comun | `payer_user_id` y `kind` en programados; ajuste en household_member. |
| V11 `una_ocurrencia_un_movimiento` | postgresql | Índice único `ux_movement_ocurrencia` (una ocurrencia = un movimiento); borra duplicados. **No existe en H2.** |
| V12 `plata_liberada` | comun | `released_amount` en movement. |
| V13 `gasto_pagado_con_el_ahorro` | comun | `funded_from_category_id` en movement. |
| V14 `sin_cuentas_ni_repartos` | comun | Ola 7: `is_transfer` pasa a tener DEFAULT FALSE; no borra tablas viejas (para poder volver a la imagen anterior). |
| V15 `tarjetas_y_cuotas` | comun | Tabla `oc_budget_card`; en movement `card_id`, `card_paid`, `installment_number/total`, `purchase_id`. |
| V16 `tarjetas_con_rls` | postgresql | Política RLS `p_card`. |
| V17 `corte_de_la_tarjeta` | comun | `cutoff_day` en card. |
| V18 `pagos_de_tarjeta` | comun | `credit` en card; tabla `oc_budget_card_payment`. |
| V19 `pagos_de_tarjeta_con_rls` | postgresql | Política RLS `p_card_payment`. |

Siguiente versión libre: **V20**.

### 5.3 auth-service — `MIG-AUTH`

Una sola carpeta (`db/migration/`, sin vendor).

| Versión | Qué hace |
|---|---|
| V1 `linea_base` | Tabla `oc_auth_user`. |
| V2 `invariantes` | CHECK de `budget_start_day` (1–31) y otras restricciones. |
| V3 `roles` | `oc_auth_role`, `oc_auth_user_role`; siembra `ADMIN` y `USER` y asigna roles a los usuarios existentes. |
| V4 `modulos_paginas_y_permisos` | `oc_auth_module`, `oc_auth_page`, `oc_auth_role_permission` + siembra del menú. |
| V5 `auditoria` | `oc_auth_audit_log` e índices. |
| V6 `pagina_de_auditoria` | Página de auditoría en el menú de administración (solo lectura para ADMIN). |
| V7 `preferencias_y_configuracion` | `currency` y `decimal_places` en user; tabla `oc_auth_app_config` con valores globales. |
| V8 `retirar_paginas_que_ya_no_existen` | Renombra rutas al frontend nuevo y borra páginas de funciones retiradas (cuentas, conciliación, importar, ayuno, consolidado, sobres...). |

Siguiente versión libre: **V9**.

---

## 6. Row Level Security (solo budget_db, solo PostgreSQL)

### 6.1 Cómo se fija el usuario en la sesión

- `BUDGET/security/ContextoDeUsuarioEnLaBase` es un **aspecto** (`@Order(200)`) que envuelve los métodos de las clases anotadas con `@Transactional` **a nivel de clase** dentro de `com.ohchurus.budget.service.impl..*`.
- Dentro de la transacción ejecuta:
  `SELECT set_config('app.user_id', :userId, true)`
  El `true` = local a la transacción (se deshace solo al hacer commit/rollback; no se queda pegado a la conexión del pool).
- `BUDGET/config/RlsConfig` pone `@EnableTransactionManagement(order = 100)` para que la transacción empiece **antes** que el aspecto. No cambiar ese orden.
- Sin usuario autenticado no se fija nada → las políticas no devuelven filas (fallo seguro).
- Solo se activa si el motor es PostgreSQL y `rls.enabled` (por defecto `true`). En H2 no hace nada.
- `BUDGET/security/ComprobacionDeRls` comprueba al arrancar si el rol de conexión es superusuario o tiene `BYPASSRLS` y lo escribe en el log.

> **Estado real:** la app conecta como superusuario de Postgres, que **se salta RLS** incluso con `FORCE`. Las políticas están puestas pero **dormidas**; el aislamiento lo sostiene hoy el código (`ControlAcceso`, `@Query` filtradas). Para activarlas hay que conectar con un rol normal (instrucciones al final de V9; Flyway seguiría con su rol propio). La prueba `LaBaseDeDatosFiltraPorUsuarioTest` sí las ejercita con un rol normal en Testcontainers.

### 6.2 Funciones (V9)

- `oc_usuario_actual()` → `current_setting('app.user_id', true)::bigint` (NULL si no se puso).
- `oc_mis_hogares()` → hogares activos del usuario (`SECURITY DEFINER`, para evitar recursión de RLS).
- `oc_categorias_de_mis_hogares()` → categorías activas de esos hogares (`SECURITY DEFINER`).

### 6.3 Políticas (todas con `ENABLE` + `FORCE`)

| Tabla | Política | Ve (USING) | Escribe (WITH CHECK) |
|---|---|---|---|
| `oc_budget_scheduled_movement` | `p_scheduled` | solo lo suyo | solo lo suyo |
| `oc_budget_category` | `p_category` | lo suyo o de mis hogares | solo lo suyo |
| `oc_budget_allocation` | `p_allocation` | lo suyo o de mis hogares | solo lo suyo |
| `oc_budget_movement` | `p_movement` | lo suyo o en categorías de mis hogares | solo lo suyo |
| `oc_budget_household_member` | `p_household_member` | yo o miembros de mis hogares | abierto |
| `oc_budget_household` | `p_household` | hogares de los que soy miembro | abierto (para poder crear el hogar antes del miembro) |
| `oc_budget_card` (V16) | `p_card` | lo suyo o de mis hogares | solo lo suyo |
| `oc_budget_card_payment` (V19) | `p_card_payment` | solo lo suyo | solo lo suyo |
| `oc_budget_account`, `oc_budget_movement_split`, `oc_budget_import_profile`, `oc_budget_import_rule` | `p_account`, `p_movement_split`, `p_import_*` | (tablas retiradas, las políticas siguen) | |

Si añades una tabla nueva con datos de usuario: crea su política en una migración `postgresql/V20+__..._con_rls.sql`, como V16/V19.

---

## 7. Convenciones del código

**Paquetes por servicio:** `controller`, `service` (interfaces) + `service/impl`, `repository`, `entity`, `dto/input`, `dto/output`, `mapper` + `mapper/impl` (ModelMapper), `enums`, `exception`, `security`, `util`, `config`, y en budget `client`.

**Nombres:**
- Lo antiguo, en inglés y con interfaz: `MovementService` / `MovementServiceImpl`, `CategoryServiceImpl`, etc.
- Lo nuevo (olas 6–7), **en español, sin interfaz y sin sufijo Impl**: `DeudasService`, `PlanFinancieroService`, `EstadoDelPeriodoService`, `ExcelExportService`, utilidades `ControlAcceso`, `Computables`, `CalendarioDeRecurrencias`, `Transacciones`. Métodos y variables también en español (`guardarTarjeta`, `yo`, `diaDeCorte`).
- Las rutas HTTP siguen en inglés salvo `/v1/estado/*`.
- DTO de entrada: `XxxSaveDTO`, `XxxFilterDTO`, `XxxRequestDTO` o nombres en español (`AsignacionGuardarDTO`, `CrearHogarDTO`). Salida: `ResultXxxDTO`.
- Comentarios largos que explican el **porqué** (historia del bug, decisión). Se mantienen así.

**Formato de respuesta** (`dto/output/ResultDTO`, igual en ambos servicios):
```json
{ "correct": true, "message": "OK", "errorCode": 0, "object": { ... } }
```
- Éxito: `new ResultDTO(objeto)`. Error: `new ResultDTO(false, "mensaje", código)`.
- **Siempre HTTP 200**, también en errores; el `errorCode` imita el código HTTP (400, 403, 404, 429, 500) o un código propio (101 credenciales, 103 usuario no encontrado...). Excepción: el filtro JWT responde 401 de verdad.
- Listas paginadas: `object = {page, size, totalPage, totalElements, list}`.
- No-encontrado y sin-permiso suelen devolver el **mismo mensaje** ("Household not found", "Usuario no encontrado") para no filtrar qué existe.

**Errores y excepciones:**
- `GlobalExceptionHandler` (`@RestControllerAdvice` limitado al paquete `controller`) convierte validación (`@Valid`), JSON ilegible, parámetros con tipo incorrecto, `AccessDeniedException` y cualquier `Exception` en `ResultDTO` con HTTP 200.
- Los services suelen envolver en `try/catch` y devolver `ResultDTO` de error; cuando atrapan dentro de un `@Transactional`, llaman a `Transacciones.revertir()` (`BUDGET/util/`) para que no se confirme lo escrito a medias.
- Mensajes: mezcla de texto literal en español/inglés y `Message.Msj` (en auth sí se usa; en budget el enum existe pero **no se usa**, ver §9).

**Validaciones:** Bean Validation (`jakarta.validation`) en los DTO + `@Valid` en el controller; reglas de negocio en el service; `AUTH/util/ValidationUtils` (pequeño: `isBlank`, `isValidBudgetStartDay`, `error`). Invariantes duplicados en la base (V2: CHECK/FK/únicos).

**Lógica compartida en budget:**
- `Computables`: única regla de "¿este movimiento suma?" (activo y sin padre; los hijos son desglose).
- `PeriodUtils`: inicio/fin del periodo según el día de corte (`budgetStartDay`).
- `CalendarioDeRecurrencias`: enumera ocurrencias de un programado desde su ancla.
- `ControlAcceso`: quién puede ver qué.
- Moneda en propiedades: `app.currency=COP`, `app.decimal-places=0`.

**Datos semilla:** `LoadData` (`CommandLineRunner`) en auth y budget siembran usuarios/categorías demo si la base está vacía; se apagan con `app.seed-data-enabled=false` (las pruebas lo apagan).

### Pruebas de arquitectura (no hay ArchUnit; son JUnit con reflexión y lectura de fuentes)

En `src/test/java/com/ohchurus/<servicio>/arquitectura/`:

| Prueba | Qué impone |
|---|---|
| `ElEsquemaNoSeGeneraSoloTest` | `ddl-auto=validate` en todos los `application*.properties` y `@TestPropertySource`. |
| `LaIdentidadNoVuelveAlCuerpoTest` | Ningún DTO con `@NotNull userId` ni controller que lea `userId` del cuerpo (lista cerrada de excepciones). |
| `NingunEndpointSinDuenoTest` | Cada ruta de cada controller está en la matriz de aislamiento (`AislamientoEntreUsuariosTest` / `AislamientoDeCuentasTest`), exenta con motivo, o pendiente con el ataque escrito. **Un endpoint nuevo sin clasificar pone la construcción en rojo.** |
| `TodoControllerDevuelveElContratoTest` | Todo método de controller devuelve `ResponseEntity<ResultDTO>` (excepciones listadas, como el Excel). |
| `ElMenuLlevaAPantallasQueExistenTest` (auth) | Las `route` sembradas en el menú existen en `AppNavigator.tsx` (se salta si no encuentra el frontend). |

Otras "guardas": `esquema/ElEsquemaCuadraConElCodigoTest` (FK/únicos/CHECK reales), `exception/ContratoDeErroresTest`, `spec/LaSpecPublicadaEstaAlDiaTest` (spec OpenAPI = JSON commiteado), `consultas/*` (las `@Query` corren en Postgres de verdad).

---

## 8. Pruebas del backend

### 8.1 Dónde y cuántas (aprox.)

| Módulo | Clases de prueba | Métodos `@Test`/`@ParameterizedTest` |
|---|---|---|
| auth-service | ~31 | ~244 |
| budget-service | ~56 | ~579 |
| discovery / gateway | 1 + 1 | 1 + 1 (arranque del contexto) |
| **Total** | ~89 | **~825** |

Más **36 escenarios Karate** (auth: `auth.feature`, `users.feature`; budget: `categories`, `dashboard`, `movements`, `scheduled`).

### 8.2 Tipos (por carpeta, en `src/test/java/com/ohchurus/<servicio>/`)

- `service/` — unitarias con Mockito (`@ExtendWith(MockitoExtension.class)`), incluidos `*EdgeCasesTest`.
- `controller/` — `@WebMvcTest` con MockMvc.
- `mapper/`, `dto/`, `util/`, `client/` — unitarias.
- `security/` — filtro JWT, límite de intentos y **matrices de aislamiento** (`AislamientoEntreUsuariosTest` en budget, `AislamientoDeCuentasTest` en auth, `UnTokenRobadoNoBastaTest`).
- `adversarial/` y `coherencia/` (budget) — integración `@SpringBootTest` + H2 con escenarios financieros completos (`EscenarioFinanciero` es la base): fechas, planificación, cifras que cuadran entre pantallas, ahorro y bolsas, responsables, parámetros inválidos, cierre del mes, recurrencias sin duplicar.
- `hogar/` — el núcleo familiar.
- `consultas/` — **Testcontainers** con `postgres:14` (`PostgresDeVerdad` es la base). `seguridad/LaBaseDeDatosFiltraPorUsuarioTest` prueba RLS con un rol no superusuario.
- `arquitectura/`, `esquema/`, `exception/`, `spec/` — guardas (§7).
- `karate/` — pruebas de API contra el stack levantado; **excluidas** del `mvn test` normal.

Configuración de pruebas: `src/test/resources/application.properties` (H2, Flyway con las mismas migraciones, Eureka apagado, semilla apagada).

### 8.3 Comandos (desde `backend/`)

```bash
mvn -B clean verify                       # todo: pruebas + suelo de cobertura JaCoCo (lo que corre el CI)
mvn test -pl budget-service               # solo un servicio
mvn test -pl budget-service -Dtest=MovementServiceImplTest
# Regenerar la spec OpenAPI tras cambiar un endpoint (y commitear documentación/api):
mvn -B test -Dtest=LaSpecPublicadaEstaAlDiaTest -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true -Dactualizar.spec=true
# Karate (con el stack levantado; por defecto habla directo con 8821/8823):
mvn -Pkarate test -pl budget-service -Djacoco.skip=true \
    -DbaseUrl=http://localhost:8820/BUDGET-SERVICE/oh-churus -DauthUrl=http://localhost:8820/AUTH-SERVICE/oh-churus
```

- Las pruebas de Testcontainers necesitan Docker; sin él se saltan (no fallan).
- `backend/run-tests.bat` (Windows) compila, corre `mvn test` y abre los informes HTML.
- Suelo de cobertura JaCoCo en `verify` (bundle): budget instrucción 93 %, rama 84 %, línea 94 %; auth 84 / 74 / 86 %. Borrar pruebas puede romper la construcción.

### 8.4 CI (`.github/workflows/`)

- `pruebas.yml` (la puerta): job `backend` (`mvn -B clean verify` + regenera la spec y falla si `documentación/api` cambió), job `frontend` (tsc + jest), job `karate` (empaqueta, `docker compose up` de los 5 servicios, espera a que el gateway enrute, corre Karate **por el gateway**).
- `sonarcloud.yml`: solo mide (verify + jest con cobertura → SonarCloud). No bloquea.

---

## 9. Código muerto y restos de cosas retiradas

En la ola 7 se retiraron cuentas/conciliación, repartos entre personas, importación CSV, transferencias entre categorías, sobres, consolidado y el servicio de ayuno. Lo que queda:

- **Tablas y columnas huérfanas en budget_db**: `oc_budget_account`, `oc_budget_movement_split`, `oc_budget_import_profile`, `oc_budget_import_rule`, y columnas viejas de `oc_budget_movement` (`is_transfer` y las de cuenta/importación). V14 dejó todo a propósito para poder volver a la imagen anterior. Sus políticas RLS siguen en V9. Ninguna entidad las mapea. Si se borran, hay que hacerlo con una migración nueva, nunca editando V4/V6/V8.
- **`documentación/api/fasting-service.json`**: spec de un servicio que ya no existe.
- **Karate `budget-service/.../karate/dashboard.feature`** llama a `/v1/dashboard/summary`, `/by-category`, `/trend`, `/split-summary`, que **ya no existen** (solo queda `/v1/dashboard/pending`). Esos escenarios deberían fallar en el job `karate` del CI.
- **`BUDGET/enums/Message.java`**: enum `Msj` sin ningún uso en budget-service.
- **Endpoints que el frontend no parece llamar** (búsqueda en `frontend/src/services`): `movements/get/{id}`, `movements/by-period`, `movements/children/{parentId}`, `categories/get/{id}`, `categories/type-list`, `scheduled/get/{id}`, `scheduled/frequency-list`. Algunos los usa Karate. No borrar sin revisar `NingunEndpointSinDuenoTest` ni la spec.
- **Campos `userId` en DTO que se ignoran** (`DashboardRequestDTO.userId`, el `Map` del cuerpo de `/categories/tree`, `HogarPorUsuarioDTO`): restos de cuando la identidad venía en el cuerpo.
- `/v1/household/add-member` (por id) convive con `/v1/household/invite` (por correo).
- Comentarios que mencionan cosas que ya no están: `AUTH/entity/Module.java` habla de "las finanzas y el ayuno"; `DirectorioDeUsuariosHttp` dice que "los servicios no se hablan entre sí" (ahora sí lo hacen, justamente por esa clase).
- **`backend/docker-compose.yml`**: copia antigua del compose (sin techos de memoria ni frontend). El que se usa es el de la raíz del repo.
- `backend/.idea/runConfigurations/` incluye configuraciones de Karate y cobertura; `backend/.idea` y `backend/.claude` son de entorno local, no del producto.
- Pom raíz: repositorio `spring-milestones` que no hace falta con versiones GA. Pom del gateway: dependencia `servo-core 0.12.21` sin uso aparente.

### Otras cosas a vigilar (no son código muerto)

- **RLS y services sin `@Transactional` de clase:** el aspecto que fija `app.user_id` usa `@within(Transactional)`, que solo detecta la anotación **en la clase**. `EstadoDelPeriodoService` y `PlanFinancieroService` la tienen solo en los métodos, y `ExcelExportService` no la tiene. Hoy da igual porque RLS está dormido, pero el día que se active RLS con un rol normal esas pantallas verían **cero filas**. Antes de activarlo: poner `@Transactional(readOnly = true)` a nivel de clase o ampliar el pointcut.
- **H2 no tiene V9, V11, V16 ni V19:** las pruebas en H2 no tienen el índice único `ux_movement_ocurrencia` ni RLS. Lo cubren solo las pruebas de Testcontainers.
- `LoadData` de auth tiene contraseñas semilla **por defecto en el código** (se pueden cambiar con `SEED_*_PASSWORD`). En producción deben venir del entorno.
- El límite de intentos de login vive en memoria: con más de una instancia de auth-service habría que pasarlo a la base o a Redis.
- El compose de la raíz pone por defecto una URL de API con una IP de red local para el build del frontend; se sobreescribe con `API_URL`.
