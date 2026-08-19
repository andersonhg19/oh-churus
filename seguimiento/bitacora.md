# Oh Churus! - Bitácora de Desarrollo

## Estado Actual: estabilizado tras las dos olas de agosto
## Última actualización: 2026-08-11

> Las entradas de marzo (fases 0-11) describen la construcción inicial. A partir
> de "Marzo-abril de 2026" el proyecto sale del plan maestro: crece con el núcleo
> familiar y el ayuno, pasa por una etapa de calidad medida en junio y por dos
> olas de estabilización en agosto.
>
> Fuentes de esta segunda parte: los mensajes de los commits del repositorio y
> `../documentación/auditoria-y-plan-de-estabilizacion.md`.

---

## Registro de Actividades

### 2026-03-17 - Inicio del Proyecto

**Actividad:** Planificación inicial y creación de documentación base.

**Documentos creados:**
| Documento | Ruta | Descripción |
|-----------|------|-------------|
| Enunciado Detallado | `documentación/enunciado-detallado.md` | Versión ampliada del enunciado con stack, arquitectura, entidades, reglas de negocio, UI y entregables |
| Entidades y Relaciones | `documentación/entidades-y-relaciones.md` | Modelo completo de entidades, campos, tipos, restricciones, relaciones cross-service y convenciones |
| Plan Maestro | `seguimiento/plan-maestro.md` | Plan de 12 fases (0-11) con entregables, criterios de aceptación y estructura de archivos por fase |
| Bitácora | `seguimiento/bitacora.md` | Este archivo - registro cronológico del avance |

**Decisiones tomadas:**
1. **Arquitectura:** 5 microservicios (discovery, gateway, auth, core, budget)
2. **Puertos:** 8760, 8820, 8821, 8822, 8823
3. **BD por servicio:** auth_db, core_db, budget_db
4. **Context path:** `/oh-churus`
5. **Prefijo tablas:** `oc_[servicio]_[entidad]`
6. **Referencia:** Proyecto HexaQuantum como base de estructura
7. **Frontend:** React Native (Expo) con tema ardilla, modo oscuro por defecto
8. **Pruebas:** JUnit + Mockito (unitarias), Karate (integración API), Jest (frontend)
9. **Mascota:** Ardilla (concepto de ahorro, recolección)

**Próximo paso:** Iniciar Fase 0 - Infraestructura Base (estructura Maven, Docker, Discovery, Gateway)

**Estado:** Esperando aprobación del plan por parte del usuario.

---

### 2026-03-17 - Simplificación del Proyecto

**Actividad:** Simplificación de entidades y alcance por solicitud del usuario.

**Cambios realizados:**
- **Eliminado Role y UserRole** del alcance actual -> movido a `documentación/puntos-futuros.md`
- **Eliminado AppConfiguration** del alcance actual -> movido a `documentación/puntos-futuros.md`
- **Eliminado avatarUrl** de User (simplificación)
- **Entidades finales (4):** User, Category, Movement, ScheduledMovement
- Actualizado `entidades-y-relaciones.md`, `enunciado-detallado.md` y `plan-maestro.md`
- Creado `documentación/puntos-futuros.md` con backlog de módulos pospuestos

**Decisión:** El proyecto se mantiene simple con 4 entidades en 3 microservicios + infra. Roles, configuraciones globales, notificaciones, reportes y audit van a fases futuras.

**Estado:** Esperando aprobación final del plan simplificado.

---

### 2026-03-17 - Fase 0: Infraestructura Base

**Actividad:** Creación de estructura Maven multi-módulo, Discovery Service, Gateway Service, Docker Compose y PostgreSQL.

**Archivos creados:**
```
backend/
├── pom.xml                          (parent POM, módulos: discovery + gateway)
├── .env                             (POSTGRES_USER, POSTGRES_PASSWORD, SECRET, EXP_TIME)
├── docker-compose.yml               (postgres:14, discovery, gateway, red oh-churus-network)
├── init-db/
│   └── init-databases.sql           (auth_db, core_db, budget_db)
├── discovery-service/
│   ├── pom.xml                      (eureka-server, actuator)
│   ├── Dockerfile                   (eclipse-temurin:17-jre-jammy)
│   └── src/main/java/.../DiscoveryServiceApplication.java
│   └── src/main/resources/application.properties (puerto 8760)
│   └── src/test/java/.../DiscoveryServiceApplicationTests.java
├── gateway-service/
│   ├── pom.xml                      (spring-cloud-gateway, eureka-client, servo-core)
│   ├── Dockerfile                   (eclipse-temurin:17-jre-jammy)
│   └── src/main/java/.../GatewayServiceApplication.java (con DiscoveryClientRouteDefinitionLocator)
│   └── src/main/resources/application.yml (puerto 8820, CORS, eureka)
│   └── src/test/java/.../GatewayServiceApplicationTests.java
```

**Compilación:** `mvn clean package -DskipTests` -> BUILD SUCCESS (4.178s) - 2026-03-17
**Stack:** Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.3

**Próximo paso:** Fase 1 - Auth Service

---

### 2026-03-17 - Fases 1-5: Backend Completo

**Actividad:** Implementación completa de los 3 microservicios de negocio con pruebas unitarias.

**Fase 1 - Auth Service (8821, auth_db):**
- Entity: User (id, name, email, password, budgetStartDay, active, createdAt, updatedAt)
- Autenticación JWT sin roles (simplificado)
- CRUD completo: login, register, save, getById, getAll, delete
- Seed data: admin@ohchurus.com / demo@ohchurus.com
- 35 tests (Service: 15 create/update/getById/getAll/delete, Auth: 4 login/jwt, Controller: 11, Filter: 5)

**Fase 2 - Core Service (8822, core_db):**
- Entity: Category con estructura árbol (parentId, máximo 3 niveles)
- Enum: CategoryType (INCOME, EXPENSE)
- CRUD + endpoint /tree para árbol completo + /type-list
- Seed data: 25 categorías para usuario demo (7 padres + 18 hijos)
- 15 tests (Create: 5, Update: 2, GetById: 2, GetAll: 1, GetTree: 2, Delete: 2, TypeList: 1)

**Fases 3+4 - Budget Service (8823, budget_db) - Movimientos y Programados:**
- Entities: Movement, ScheduledMovement
- Enum: Frequency (7 opciones)
- Lógica de generación de pendientes (confirmed=false) desde programados
- Control de meses cortos: Math.min(budgetStartDay, yearMonth.lengthOfMonth())
- 32 tests (Movement: 16, Scheduled: 16 incluyendo 6 escenarios de generación de pendientes)

**Fase 5 - Dashboard API (dentro de budget-service):**
- Endpoints: /summary, /by-category, /trend, /pending
- Cálculo de períodos con budgetStartDay y manejo de meses cortos
- Tendencia vs período anterior con % de cambio
- 19 tests adicionales (period calculation, summary, category, trend, pending)

**Compilación global:** `mvn clean package` -> BUILD SUCCESS (31s)
**Tests totales:** 103 tests, 0 failures
**Servicios:** 5 (discovery, gateway, auth, core, budget)

**Próximo paso:** Fase 7 - Frontend (React Native)

---

### 2026-03-17 - Fusión core-service en budget-service + Auditoría de calidad

**Actividad:** Refactorización arquitectónica y correcciones de calidad.

**Cambios mayores:**
1. **core-service eliminado** - Category y toda su lógica fusionada en budget-service
2. **Tabla renombrada:** oc_core_category -> oc_budget_category
3. **Base de datos reducida:** core_db eliminada, todo en budget_db
4. **Servicios finales:** 4 (discovery, gateway, auth, budget)

**Correcciones de calidad aplicadas:**
- PeriodUtils.java: cálculo de períodos extraído a utilidad compartida
- SecurityUtils.java: extracción de email del JWT
- DashboardSummaryDTO.CategorySummary: agregados campos categoryName, icon, color
- Validación de existencia de categoría en MovementServiceImpl y ScheduledMovementServiceImpl
- Manejo defensivo de amount null en ScheduledMovement
- LoadData unificado con seed de 25 categorías

**Archivos creados:**
- collection/Oh-Churus.postman_collection.json (28 endpoints)
- docker-compose.yml raíz (postgres, discovery, gateway, auth, budget, frontend)
- .env raíz

**Compilación:** `mvn clean package` -> BUILD SUCCESS (28.5s)
**Tests:** 107 totales (1+1+35+70), 0 failures

**Próximo paso:** Fase 7 - Frontend React Native

---

### 2026-03-17 - Fases 7-9: Frontend + Auditoría de calidad

**Frontend creado (35 archivos):**
- Expo + TypeScript + React Navigation
- Tema dark/light (dark por defecto, paleta ardilla: amber/marrón)
- 6 atoms, 4 molecules, 10 screens
- 6 servicios API con rutas Gateway correctas
- AuthContext con JWT + budgetStartDay
- ThemeContext con persistencia AsyncStorage

**Correcciones de calidad aplicadas al frontend:**
1. URLs corregidas con prefijos Gateway (/AUTH-SERVICE/, /BUDGET-SERVICE/)
2. budgetStartDay integrado en AuthContext y propagado a Dashboard/Scheduled
3. Category picker visual en formularios de Movement y Scheduled (reemplazó input de ID manual)
4. Error handling visible en todas las pantallas (ya no silencioso)
5. formatCurrency sin conflicto de signos
6. ProfileScreen sincroniza budgetStartDay con contexto
7. Textos en español corregidos (contraseña)

**Mejoras manuales del usuario al backend:**
- auth-service: actuator + healthcheck + JWT 401 response + seed configurable
- budget-service: mismo patrón JWT 401 + actuator
- docker-compose raíz: healthchecks para auth y budget
- UserFilterDTO: validaciones @Min/@Max

**Verificación final:**
- Backend: mvn clean package -> BUILD SUCCESS (29s), 107 tests, 0 failures
- Frontend: npx tsc --noEmit -> OK, npx expo export --platform web -> OK

---

## Formato de registro por fase

```
### YYYY-MM-DD - Fase X: Descripción

**Actividad:** Qué se hizo
**Archivos creados/modificados:**
- ruta/archivo.java - descripción del cambio
**Compilación:** mvn clean package -pl servicio -> EXITOSA/FALLIDA
**Pruebas:** X/Y pasando (cobertura %)
**Problemas encontrados:** (si los hay)
**Decisiones:** (si las hay)
**Próximo paso:** Qué sigue
```

---

## Resumen de Progreso por Fase

| Fase | Estado | Inicio | Fin | Notas |
|------|--------|--------|-----|-------|
| 0 - Infraestructura | **Completada** | 2026-03-17 | 2026-03-17 | BUILD SUCCESS |
| 1 - Auth Service | **Completada** | 2026-03-17 | 2026-03-17 | 35 tests OK |
| 2 - Core Service | **Completada** | 2026-03-17 | 2026-03-17 | 15 tests OK |
| 3 - Movimientos | **Completada** | 2026-03-17 | 2026-03-17 | 16 tests OK |
| 4 - Programados | **Completada** | 2026-03-17 | 2026-03-17 | 16 tests OK |
| 5 - Dashboard API | **Completada** | 2026-03-17 | 2026-03-17 | 19 tests OK |
| 6 - Pruebas Backend | **Integrada** | 2026-03-17 | 2026-03-17 | 103 tests totales en fases 1-5. Karate pendiente |
| 7 - Frontend Infra | **Completada** | 2026-03-17 | 2026-03-17 | Expo, tema, nav, auth, API |
| 8 - Frontend Core | **Completada** | 2026-03-17 | 2026-03-17 | 10 pantallas, CRUD completo |
| 9 - Frontend Dashboard | **Completada** | 2026-03-17 | 2026-03-17 | Stats, pendientes, trend |
| 10 - Frontend Pruebas | **Completada** | 2026-03-18 | 2026-03-18 | 50 tests Jest |
| 11 - Integración | **Completada** | 2026-03-18 | 2026-03-18 | Docker + Postman |

---

# Segunda parte: de marzo a agosto de 2026

---

### 2026-03-21 a 2026-04-04 - El proyecto crece fuera del plan maestro

**Actividad:** entre el cierre de la fase 11 y el primer commit del repositorio,
el proyecto añadió dos módulos completos y tres conceptos nuevos de dominio.

**Advertencia sobre esta entrada:** este crecimiento **no está trazado paso a
paso**. El repositorio de git empieza el 2026-04-04 con un único commit
("Initial commit: Oh Churus! personal finance platform", 316 ficheros) que ya
contiene todo lo de abajo. Lo que sigue se reconstruye comparando el estado de
marzo con el contenido de ese commit, no de un registro de la época.

**Lo que había en ese primer commit y no estaba el 21 de marzo:**

| Añadido | Detalle |
|---|---|
| `fasting-service` | Tercer servicio de negocio (puerto 8825, `fasting_db`). Entidades `FastingSession`, `FastingPlanConfig`, `WaterLog`, `Achievement` |
| Núcleo familiar | `Household` y `HouseholdMember` en budget-service. Categorías compartidas (`householdId`) |
| Presupuesto por periodo | `BudgetAllocation`: cuánto se piensa gastar por categoría y periodo |
| Sub-movimientos | `parentMovementId`: desglosar un gasto en líneas de detalle |
| Transferencias | `isTransfer` + `transferPairId`: mover plata entre bolsillos |
| Exportación a Excel | `ExportController` + `ExcelExportService` (Apache POI) |
| Pantallas nuevas | Consolidado, Resumen con dona y drill-down, Ayuno (panel, historial, config), Hogar |

**Resultado:** de 4 entidades y 2 servicios de negocio (auth y budget, tras la
fusión de core) a **11 entidades y 3 servicios de negocio** — cinco
microservicios contando discovery y gateway. De 28 endpoints a 56.

**Consecuencia documental:** a partir de aquí `enunciado-detallado.md`,
`entidades-y-relaciones.md` y `plan-maestro.md` dejaron de corresponderse con el
código. Se corrigió el 2026-08-11.

---

### 2026-06-10 - Cobertura y análisis estático con SonarQube Cloud

**Actividad:** conectar el proyecto a SonarQube Cloud, restaurar la cobertura de
pruebas y llevar el reporte de calidad a Quality Gate en verde.

**Cronología del día** (13 commits, del más antiguo al más reciente):

1. `cfa9e6b` — cobertura backend >90% y frontend ~70%, y montaje de SonarCloud.
2. `749ae83` — corregir la importación de cobertura: `sonar.coverage.jacoco.xmlReportPaths`.
3. `c85d453` — 5 bugs detectados por SonarCloud: "leaked value" en JSX y
   `Duration.between` sin zona horaria.
4. `c11044d` — commit deliberado con el estado "ANTES" para poder enseñar el
   contraste en el taller.
5. `47e6695` — `sonar.java.libraries` (copia de dependencias) para que el
   análisis de Java sea preciso.
6. `7f12f34` — restaurar cobertura y corregir vulnerabilidades y bugs.
7. `19a2e99` — regex de email lineal: resuelve el hotspot de ReDoS.
8. `6dbbaff` y `61bcae6` — documento del taller de calidad, con la cobertura
   final (80.9%).
9. `5cfadec`, `05c7939`, `6228560`, `ff4c7a9` — pruebas de frontend: componentes,
   guardado en formularios, e interacción en pantallas (Household,
   FastingDashboard, Budget, Consolidated, Summary). GitHub Actions a v5.
10. `c02df81` — salida de las pruebas limpia para la demo: `logback-test.xml`,
    banner de Spring apagado y filtro de los warnings inofensivos de `act()`.

**Resultado medido** (de `documentación/taller-calidad-sonarqube.md`):

| Métrica | Antes | Después |
|---|---|---|
| Quality Gate | ERROR | **PASSED** |
| Bugs | 7 | **0** |
| Vulnerabilidades | 4 | **0** |
| Hotspots sin revisar | 4 | **0** |
| Code smells abiertos | 243 | **0** (triados) |
| Cobertura | 44.7% | **80.9%** |

**Qué se corrigió de verdad:** contraseñas del seed externalizadas a
configuración (`app.seed.*-password`, sobrescribibles por variables de entorno),
el ReDoS del validador de correo, `Duration.between` con zona horaria, y el
"leaked value" de JSX. Los CSRF deshabilitados se **revisaron y aceptaron**: es
una API REST sin estado con JWT, sin cookies.

**Infraestructura:** `.github/workflows/sonarcloud.yml` corre en cada push y PR a
`main`: `mvn -B clean verify` con JaCoCo, `npx jest --coverage` con LCOV, y el
escaneo de SonarCloud.

**Lo que este capítulo NO detectó, y por qué importa:** el 99-100% de cobertura
se calculaba **después de excluir la capa de seguridad** (`SecurityConfig`,
`SecParams`, `JWTAuthorizationFilter`), los tests de controller corrían con
`addFilters = false`, y los 36 escenarios de Karate nunca se ejecutaron en CI.
Por eso los tres fallos de diseño que se encontraron en agosto sobrevivieron a
esta revisión: se estaba midiendo donde no importaba.

---

### 2026-08-11 - Auditoría y Ola 1: la identidad sale del token

**Actividad:** auditoría multiagente del proyecto (8 dimensiones con verificación
adversarial) y primera ola de corrección.

**Diagnóstico.** 144 hallazgos confirmados que se reducían a **tres decisiones
de diseño** tomadas al principio:

- **Raíz A — la identidad la ponía el cliente.** Como todos los endpoints son
  POST y los parámetros viajan en el cuerpo, el `userId` acabó siendo un
  parámetro más, indistinguible de `categoryId` o de `amount`. El filtro JWT
  existía, el token **sí llevaba** el claim `userId`, `SecurityUtils` estaba
  escrito... y nadie cerró el círculo.
- **Raíz B — no había fuente única de verdad** ni para el periodo ni para "qué
  movimiento suma".
- **Raíz C — las convenciones no tenían mecanismo que las impusiera:** ni un
  `@ControllerAdvice` en todo el backend, y `ddl-auto=update` sin una sola clave
  foránea, UNIQUE ni CHECK.

**Se midió antes de arreglar.** Se escribió una matriz de aislamiento entre dos
usuarios **antes** de tocar el código de producción. Arrancó con 10 de 13 casos
en rojo: Bruno leía los movimientos de Ana, le reescribía el importe a 9.999.999,
se apropiaba de ellos y se metía en su núcleo familiar.

**Qué se corrigió** (`3925be8`):

1. **Toma de control de cuentas sin autenticarse.** `/v1/auth/register` es
   público y recibía un DTO con campo `id`; el servicio leía "trae id" como
   "actualiza". Enviando `{"id":3,...}` cualquiera cambiaba el correo y la
   contraseña de otro. Ahora recibe `UserRegisterDTO`, sin `id`, y llama a un
   método que solo puede crear. De paso, `register` devuelve token como `login`:
   registrarse dejaba la sesión vacía.
2. **La identidad sale del JWT.** El filtro recoge el claim, `SecurityUtils` lo
   reparte, y los controllers ignoran el `userId` del cuerpo. Cubre paneles,
   listados, árbol de categorías, por-periodo, Excel, transferencias y hogar.
3. **Propiedad en cada lectura y escritura.** Nuevo `ControlAcceso` con una sola
   regla: un dato es tuyo si lo creaste o si vive en una categoría compartida de
   un hogar al que perteneces. Se responde "no existe" y no "no puedes":
   contestar "no puedes" confirma que ese id existe.
4. **El núcleo familiar exige ser OWNER.** `addMember`/`removeMember` no
   comprobaban nada: cualquiera se metía en la casa de otra pareja probando
   `householdId` consecutivos.
5. **Los servicios internos solo escuchan en `127.0.0.1`** (antes, toda la red).

**Pruebas:**

- `AislamientoEntreUsuariosTest`: 18 casos, y es la **primera prueba de
  integración del proyecto**. Las 502 anteriores eran unitarias con mocks, que
  por construcción no pueden detectar que el filtro no está cableado. Levanta la
  app entera con dos usuarios y tokens de verdad, y no comprueba códigos de
  estado sino dos propiedades: la respuesta no lleva datos de Ana, y los datos de
  Ana siguen intactos.
- Se corrigió `MovementServiceImplEdgeCasesTest`, que **asertaba el robo de datos
  como comportamiento correcto** (que un update reasignara el movimiento del
  usuario 99 al 2). La suite defendía el bug; por eso sobrevivió.
- `SecurityUtilsTest` fijaba como contrato que estallara con
  `NullPointerException` sin sesión. Ahora devuelve `null`, y "sin dueño" nunca
  significa "de todos".

**Higiene del repositorio:** los volcados de base de datos dejaron de estar
versionados. Seguían en el repo **público** pese al `.gitignore`, porque ignorar
no destraquea lo ya trazado.

**Estado:** BUILD SUCCESS, 524 tests (22 nuevos), 0 fallos. Frontend intacto: 58
suites, 331 tests.

**Documento:** `1a49219` publica
`documentación/auditoria-y-plan-de-estabilizacion.md` con el diagnóstico, el plan
en tres olas con criterio de "hecho" por elemento, y —tan importante como el
plan— la lista de lo que **no** hay que hacer y por qué.

---

### 2026-08-11 - Ola 2, primera tanda: coherencia de cifras y fechas

**Commit:** `5f210cc`. Todo verificado con pruebas que fallaban antes.

1. **La misma plata, la misma cifra.** `DashboardServiceImpl` contaba la misma
   plata de dos formas **en el mismo método**: `totalExpense` sumaba los
   sub-movimientos y `budgetTotal` los excluía. Con un gasto padre de 500.000 y
   dos hijos de 200.000 y 100.000, "Gastos" decía 800.000 y "Presupuesto"
   500.000 en la misma pantalla, y la dona sumaba una tercera cosa. Ahora la
   regla vive en un solo sitio (`Computables`) y se aplica en los cinco puntos de
   agregación: el hijo es detalle del padre, y la transferencia no es ingreso ni
   gasto.
   Nuevo `LasCifrasCuadranTest`: escenario con números escogidos (sueldo
   3.000.000, mercado 500.000 con dos hijos, transferencia de 400.000) y
   exigencia de que panel, presupuesto, dona y detalle den lo mismo. Comprueba
   también que los hijos **siguen viéndose** en la lista: no sumar no puede
   significar esconder.

2. **Las fechas.** `new Date().toISOString()` pasa a UTC: en Bogotá, todo lo
   registrado después de las 19:00 se guardaba con la fecha de mañana. Y al
   revés al pintar: `new Date("2026-08-11")` es medianoche UTC, o sea las 19:00
   del día anterior. Nuevo `fechaLocalISO` y parseo local en `formatDate`, con
   pruebas en `TZ=America/Bogota` y la hora fijada a las 22:30.

3. **El signo del dinero.** `formatCurrency` hacía `Math.abs`: un déficit de
   -500.000 se veía igual que un superávit, y tres pantallas lo parcheaban a
   mano, cada una a su manera. Ahora el signo lo pone la función.

4. **La sesión caducada deja de disfrazarse de "sin datos".** Un 401 se ignoraba
   en silencio y cada pantalla pintaba su estado vacío: tras unos días sin abrir
   la app entrabas sin contraseña y veías balance $0 y "Sin núcleo familiar",
   indistinguible de haberlo perdido todo. Ahora el interceptor cierra la sesión
   y lleva a la entrada.

5. **Presupuesto vuelve a existir.** `BudgetNavigator` estaba escrito, con
   pantalla, navegador y pruebas, y no se montó nunca en ninguna pestaña. En una
   app de presupuestos.

6. Los DTO de entrada dejan de **exigir** `userId`: ya no se usa (viene del
   token), y exigirlo rechazaba peticiones legítimas.

**Pruebas que defendían bugs, corregidas:** la que fijaba `totalExpense=150` con
`budgetTotal=50` sobre los mismos datos, la que pedía que -500 se mostrara como
"$500", y las 12 que exigían un 400 al faltar el `userId` (un contrato que
desapareció: pedírselo al cliente **era** el fallo).

**Estado:** backend BUILD SUCCESS, 529 tests. Frontend 58 suites, 336 tests, en
`TZ=America/Bogota`.

---

### 2026-08-11 - Ola 2, cierre: la identidad se cierra en los tres servicios

**Commit:** `cade2ef`. Trabajo de seis agentes en paralelo, un verificador
adversarial encima, y verificación arrancando contra una copia de la base de
datos real.

**Seguridad — la invariante ya es cierta en toda la plataforma.** La ola anterior
arregló budget-service y dejó dos servicios atrás, así que "la identidad sale del
JWT" era verdad a medias, que en seguridad es mentira.

- **fasting-service:** sus 13 endpoints tomaban el `userId` del cuerpo y
  `session/edit` no comprobaba dueño. Cualquiera leía y modificaba el ayuno, el
  agua y los logros de otro cambiando un número.
- **auth-service:** `/users/delete/{id}` daba de baja la cuenta de **cualquiera**
  con solo su id, `/users/save` le cambiaba correo y contraseña, y `/users/all`
  era un directorio abierto con LIKE (buscar "a" los sacaba a todos). Ahora solo
  tu propia cuenta, y el listado sin correo solo te devuelve a ti; con correo,
  coincidencia exacta. La invitación al hogar sigue funcionando porque siempre
  pregunta por un correo concreto.
- **Dos redes de seguridad nuevas:** `AislamientoEnAyunoTest` (7 casos) y
  `AislamientoDeCuentasTest` (7). Las dos se probaron **en rojo** revirtiendo el
  arreglo a propósito, para comprobar que cazan lo que dicen cazar.

**Contrato de errores.** Un `@RestControllerAdvice` por servicio: cuerpo mal
formado, campo con tipo incorrecto o excepción no capturada devuelven siempre
HTTP 200 con `correct:false` y un mensaje que nombra el campo. Antes salía el
400/500 de Spring y el frontend, que solo sabe leer `ResultDTO`, lo convertía en
"Request failed with status code 400" o en una pantalla vacía. Los `Map` de
cuerpo pasan a DTO con validación en household, asignaciones, transferencias y
los 13 de ayuno.

**Integridad.**

- **Transferencias atómicas al editar:** corregir una de 500.000 a 300.000
  dejaba el consolidado descuadrado en 200.000 inexistentes.
- **Recurrencias idempotentes** con clave `(programado, periodo)`, a nombre del
  **dueño** del programado y no de quien pulsa, y con ocurrencias omitibles:
  borrar un pendiente ya no lo resucita en el siguiente refresco.
- **Núcleo familiar usable:** invitación por **correo** en vez de por id de fila
  de la base de datos, expulsión cableada con confirmación, y sin subcategorías
  colgando de un padre que ya no se ve.

**Migraciones.** Flyway en los tres servicios y `ddl-auto=validate`. Las
invariantes que solo vivían en el código —únicos parciales, claves foráneas y
CHECK— pasan a la base, todo con `NOT VALID` para que los datos existentes no
bloqueen el arranque, y desactivando duplicados en vez de borrarlos. Verificado
arrancando los tres servicios contra una copia de la base real: Flyway hace
baseline, aplica V2 y V3, Hibernate valida, y los 42 movimientos y 37 categorías
siguen ahí.

**Frontend.** Los fallos se ven (tres estados: cargando / vacío / error con
reintentar), las listas se refrescan al volver, borrar pide confirmación y los
botones de guardar se bloquean mientras guardan. Presupuesto recupera la
navegación por periodo: estaba en `MovementsScreen`, una pantalla completa **con
pruebas** que nunca se registró en ninguna ruta. Se trasladó lo útil a la
pantalla viva y se retiraron las dos huérfanas (esa y `ProfileScreen`, sustituida
por Ajustes).

**Calidad de las pruebas.** Cuatro asserts que prometían más de lo que
comprobaban, corregidos. El más grave: "Should include userId and name in JWT
claims" solo contaba que el token tuviera tres trozos —lo cumple cualquier JWT—.
Ahora decodifica y comprueba los claims, que es de donde los tres servicios sacan
la identidad.

**Dos regresiones cruzadas entre agentes** las cazó el verificador, no los
autores: una columna declarada solo en la línea base que impedía arrancar contra
una base ya existente (de ahí la migración `V3__periodo_de_la_ocurrencia.sql`), y
el Excel roto por la interacción de dos tareas. Las dos arregladas.

**Estado final:** backend BUILD SUCCESS, **671 tests** (auth 98 / budget 424 /
fasting 100), cobertura 89-98% de líneas. Frontend **60 suites, 375 tests**,
85.4% de líneas. Sin ficheros temporales, sin pruebas desactivadas, sin
`console.log` ni `System.out`.

---

### 2026-08-11 - La puerta: pruebas de arquitectura y CI que decide

**Actividad:** cerrar el hueco que quedaba tras las dos olas. Las pruebas de las
olas comprueban **casos**; su punto ciego es el futuro: mañana alguien añade el
endpoint 57, o copia y pega un controller viejo, y nada se pone rojo.

**Pruebas de arquitectura** (paquete `arquitectura/` de cada servicio). No
prueban comportamiento: recorren el código y exigen que nadie se salte la regla.
Todas llevan listas de exenciones cerradas y con el motivo escrito al lado, de
forma que una exención nueva —o la desaparición de una vieja— también las pone
rojas.

| Prueba | Qué impide |
|---|---|
| `LaIdentidadNoVuelveAlCuerpoTest` | Que un DTO vuelva a exigir `userId` con `@NotNull`, o que un controller lea `body.get("userId")` |
| `NingunEndpointSinDuenoTest` | Que exista una ruta que no esté ni en la matriz de aislamiento, ni exenta con motivo, ni marcada como deuda con el ataque descrito |
| `TodoControllerDevuelveElContratoTest` | Que un método de controller devuelva algo que no sea `ResponseEntity<ResultDTO>` |
| `ElEsquemaNoSeGeneraSoloTest` | Que cualquier perfil de cualquier servicio vuelva a `ddl-auto=update` |

**Suelo de cobertura** en los dos lados: `jacoco:check` en la fase `verify` del
backend y `coverageThreshold` en el `package.json` del frontend. Los umbrales van
unos puntos **por debajo** de la cobertura real a propósito: no están para
obligar a escribir pruebas nuevas, están para que borrar las que hay rompa la
construcción.

**CI que decide.** Nuevo `.github/workflows/pruebas.yml`, la puerta: backend
(`mvn -B clean verify`), frontend (`tsc --noEmit` + `jest --coverage`) y **Karate
por el gateway** levantando el stack con Docker Compose. Espera a que el gateway
enrute preguntando por `/AUTH-SERVICE/oh-churus/actuator/health` en vez de dormir
un rato y cruzar los dedos, y guarda los informes como artefactos.
`sonarcloud.yml` se queda aparte: mide, no decide.

Con esto se cierra el punto **3.6** de la Ola 3 en su parte de Karate: dejan de
ser 36 escenarios que nadie ejecuta. Siguen pendientes Testcontainers con
`@DataJpaTest` para las consultas JPQL y la spec de OpenAPI versionada.

---

### 2026-08-11 - La documentación deja de mentir

**Actividad:** los documentos se habían congelado en marzo mientras el código
seguía. Quien leyera `plan-maestro.md` o `entidades-y-relaciones.md` creería que
hay 4 entidades y 3 servicios.

**Hecho:**

| Documento | Qué se hizo |
|---|---|
| `README.md` (raíz) | **Creado.** Era entregable de la fase 11 y no existía. Qué es el proyecto, las tres patas, stack, cómo levantarlo (Docker y local), cómo correr las pruebas, mapa de servicios y puertos, y el aviso de que el repositorio es público |
| `documentación/entidades-y-relaciones.md` | **Reescrito** contra las entidades reales: 11, con las relaciones cruzadas entre servicios y las invariantes que ahora viven en la base |
| `documentación/invariantes.md` | **Creado.** Las reglas que el proyecto no puede romper y qué prueba vigila cada una |
| `documentación/enunciado-detallado.md` | Marcado como histórico + sección "Qué pasó con este enunciado" |
| `seguimiento/plan-maestro.md` | Marcado como histórico + sección "Qué pasó después de la fase 11" |
| `seguimiento/bitacora.md` | Esta segunda parte |

**Nota honesta sobre lo que sigue desfasado:** `comandos-pruebas.txt` conserva
números de pruebas y cobertura de marzo. No se borró porque los comandos siguen
siendo válidos, pero las cifras que da no lo son: las buenas están en el README.

---

## Estado a 2026-08-11

| Comprobación | Resultado |
|---|---|
| `cd backend && mvn -B clean verify` | BUILD SUCCESS, 671 tests |
| `cd frontend && npx tsc --noEmit` | sin errores |
| `cd frontend && npx jest --coverage --ci` | 60 suites, 375 tests |
| Quality Gate SonarQube Cloud | PASSED |

**Lo siguiente** está en la Ola 3 de
`../documentación/auditoria-y-plan-de-estabilizacion.md`: cuentas con saldo
calculado, reparto de gastos entre personas, sobres con arrastre, importación
CSV, recurrencias reales y el resto de la infraestructura de confianza
(Testcontainers y `@DataJpaTest` para las consultas JPQL, OpenAPI con la spec
versionada). De esa lista, **Karate por el gateway en CI ya está hecho**.

---

## Tercera parte — la Ola 3, del 12 al 18 de agosto de 2026

Aquí se dejó de arreglar y se empezó a decidir qué producto es. Las seis
entradas de la Ola 3 están hechas.

### 3.1 · Cuentas y saldo calculado

La app dejó de ser una lista de deseos. Antes decía "gastaste 500.000 en
mercado" —cierto, y contrastable contra nada—; ahora dice "en tu cuenta hay
3.500.000", y el banco lo confirma o lo desmiente. Ese es el momento en que uno
empieza a fiarse de lo que ve.

**El saldo nunca se guarda.** Se calcula sumando los movimientos en cada
petición. Un saldo guardado se desincroniza de los movimientos que resume y
entonces no hay forma de saber cuál de los dos miente. **El saldo inicial
tampoco es un campo**: es un movimiento de apertura con su fecha, porque con un
campo solo se puede calcular el saldo de hoy y no el de un día cualquiera —que
es justo lo que hace falta para conciliar contra un extracto viejo.

Salió una distinción que no era obvia: **"qué suma en el presupuesto" y "qué
mueve el saldo" no son la misma pregunta.** Difieren en los dos casos que más
importan: la transferencia no suma pero sí mueve saldo, y la apertura tampoco
suma pero es de donde sale el saldo inicial.

Probado contra el volcado real (36 movimientos, 2 personas) restaurado en un
Postgres desechable: 0 movimientos sin cuenta y 2 cuentas creadas.

### 3.2 · El reparto entre personas

La regla de oro: un gasto de 120.000 pagado por ti entre tres son **120.000 en
tu cuenta** —eso salió del banco y tiene que cuadrar con el extracto— pero solo
**40.000 en tu categoría**, porque eso es lo que gastaste tú. Los otros 80.000
son un derecho de cobro, no un gasto.

Las dos mitades tienen que cumplirse a la vez, y ahí está la gracia: si el
reparto tocara el saldo, la app dejaría de cuadrar con el banco; si no tocara la
categoría, el presupuesto seguiría mintiendo cada vez que alguien pone la cuenta
del restaurante.

Nadie más lo resuelve entero: Firefly III documenta compartir usuario y
contraseña, YNAB tiene un producto de terceros para taparlo, Monarch asume bolsa
común y Splitwise no presupuesta.

### 3.3 · Sobres con la regla asimétrica

Lo que sobra en una categoría se queda; lo que te pasaste se descuenta de lo que
tienes para repartir el mes que viene. **Asimétrica a propósito**: arrastrar el
sobregiro a la propia categoría castiga dos veces.

El arrastre **no se guarda**: se recalcula desde el primer periodo con datos, por
el mismo motivo que el saldo. Se fue con esto el `status` de las asignaciones,
que nunca significó nada —lo único que lo cambiaba era un método sin endpoint ni
`@Scheduled`— junto con las tres pruebas que lo cubrían con todo detalle.

### 3.4 · Importar el extracto del banco

Es lo que decide si la app se usa o se abandona: nadie la deja por informes
feos, todo el mundo la deja por teclear sesenta movimientos al mes. Sustituye al
botón "Importar Excel" que solo abría un aviso de *próximamente*.

Dos pasos, y el primero no escribe nada. **Tres listas**: nuevos, duplicados, y
los que confirman un pendiente que ya esperaba una recurrencia — la tercera es
la que evita importar el arriendo como nuevo dejando el pendiente colgando.

### 3.5 · Recurrencias reales

`DAILY`, `WEEKLY` y `BIWEEKLY` generaban todos lo mismo: un movimiento al mes.
Ahora las ocurrencias se enumeran desde el ancla, hay patrón "el tercer viernes"
y política de fin de semana, y más de cinco ocurrencias atrasadas se **proponen**
en vez de crearse en silencio.

### 3.6 · Infraestructura de confianza

18 sentencias JPQL que nunca se habían ejecutado contra un motor real ahora
corren contra PostgreSQL en Testcontainers. La spec OpenAPI se genera del código
y está commiteada, con un guardarraíl que rompe el build si queda atrás.

---

## Tres fallos que aparecieron solos, y son los más valiosos

**1. Testcontainers en verde sin ejecutar nada.** Testcontainers 1.19.7 pide la
API 1.32 del demonio y Docker Engine 29 exige la 1.44. El síntoma no es un
error: concluye "aquí no hay Docker" habiéndolo, se salta las 50 pruebas y deja
la construcción **en verde**. Habrían sido cincuenta pruebas decorativas.

**2. La ventana de generación dependía de quién refrescaba.** Una prueba llevaba
días en verde y se puso roja sola: había cambiado la fecha del sistema. El corte
de uno es el 1 y el del otro el 15, así que a partir del día 15 el refresco del
segundo le creaba al primero el arriendo del mes siguiente. El mismo programado
generaba cosas distintas según quién abriera la app y qué día fuera.

**3. Pruebas que dependían del orden de ejecución.** Un escenario no limpiaba
los programados, y el orden por defecto de Maven es el del sistema de ficheros
—que no coincide entre Windows y Linux—. Verde durante semanas en local, rojo en
el CI. Auditando aparecieron **tres más** con la misma bomba sin estallar. Se
arreglaron los cuatro escenarios y además se fijó el orden a alfabético, para
que si alguna vuelve a depender de él falle igual en las dos partes.

---

## Estado a 2026-08-18

| Comprobación | Resultado |
|---|---|
| `cd backend && mvn -B clean verify` | BUILD SUCCESS, **935 tests**, los tres suelos de cobertura cumplidos |
| `cd frontend && npx tsc --noEmit` | sin errores |
| `cd frontend && npx jest --coverage --ci` | **66 suites, 420 tests** |
| CI `Pruebas` (backend + frontend + Karate por el gateway) | verde |
| Matriz de aislamiento | 48 casos |
| Pruebas de arquitectura | 12, y se comprobó que muerden rompiéndolas a propósito |
| Endpoints | 70, con la spec generada del código y Postman contrastada contra ella |
| Migraciones Flyway (budget) | V1 a V8 |

**Lo único pendiente que no depende del código:** el `SONAR_TOKEN` caducó el 10
de junio. El escáner corta con un 403 antes de mirar una línea; se renueva en
SonarCloud (*My Account → Security*) y con `gh secret set SONAR_TOKEN`. No
bloquea nada: quien decide es el workflow `Pruebas`.

**Lo que queda como backlog**, y está en la sección "Lo que NO hay que hacer" de
`../documentación/auditoria-y-plan-de-estabilizacion.md` con el motivo escrito:
Row Level Security en Postgres, accesibilidad completa (0 `accessibilityLabel`
en 22 pantallas), y el rediseño de identidad de marca. Ninguna es la razón por
la que la app se sentía rota; esa lista ya está en cero.

---

## Cuarta parte — accesibilidad y cierre, 19 de agosto de 2026

Se cerró lo último que quedaba señalado del plan de auditoría.

### Accesibilidad: de 0 a 157 etiquetas

La app tenía **cero** `accessibilityLabel`. Un lector de pantalla no podía usarla.

Se empezó por los **átomos**, que es donde una sola corrección cubre 86 usos: el
botón anuncia `busy` mientras carga —sin eso, quien no ve la ruedecita pulsa
otra vez creyendo que no pasó nada— y el campo de texto une su etiqueta visible
con el propio campo.

**Los peores casos eran símbolos.** `<` y `>` del navegador de periodo se leían
"menor que" y "mayor que": la navegación principal de media app no significaba
nada. El `+` central de la barra, la acción más usada, se anunciaba "más" — y el
mismo `+` sale en otros tres sitios con tres significados distintos. `- 1` para
quitar un vaso de agua es "menos uno" de nada. `1a`, `Ultima`, `Lun`, `Mar` no
son palabras al oírlas, y "Mar" puede ser martes o marzo. Los selectores de
color e icono eran el extremo: sin nombre no hay literalmente nada que oír.

### Y tres defectos que no eran de accesibilidad

Etiquetar obliga a decir en voz alta qué hace cada cosa, y ahí se ve lo que no
se puede usar:

1. **El campo del MONTO y el buscador no existían.** Los dos pasan `label=""` a
   propósito para no repetir texto, y el átomo hacía `accessibilityLabel={label}`:
   cadena vacía. Los dos campos más usados de la app, invisibles.
2. **Un botón de 32×32**, por debajo del mínimo de 44 para acertar con el pulgar.
   No era que no se anunciara: es que no se podía dar.
3. **Estados que solo existían como opacidad** — deshabilitado, logro conseguido.
   Quedan dichos con palabras; la señal visible sigue pendiente.

### El bug del tope de página

Antes de esto salió otro, y llevaba meses vivo: `SummaryScreen` pedía 200
movimientos contra un `@Max(100)`. El validador rechazaba, el advice lo
convertía en `correct: false`, la pantalla hacía `if (res.correct)` y no
entraba. **Las barras de "presupuesto vs real" no funcionaron nunca**, sin un
solo error a la vista, porque la dona y los totales de al lado sí funcionan.

Estaba anotado en la auditoría de la ola 1 y se quedó sin hacer tres olas. Y
volvió a aparecer el mismo día en la pantalla de importación recién escrita.

### Protección de rama

`main` ya no admite *force push* ni borrado, y las tres comprobaciones del CI
son obligatorias. **No se bloquea a administradores a propósito**: eres el único
que trabaja aquí y eso obligaría a abrir un PR para cada cambio. La consecuencia
hay que saberla — GitHub avisa con *"Bypassed rule violations"* en cada push
directo, y las comprobaciones no frenan nada. Si algún día se quiere lo
segundo, hay que pasar a trabajar por PR.

---

## Estado a 2026-08-19

| Comprobación | Resultado |
|---|---|
| `cd backend && mvn -B clean verify` | BUILD SUCCESS, **939 tests**, los tres suelos cumplidos |
| `cd frontend && npx tsc --noEmit` | sin errores |
| `cd frontend && npx jest --coverage --ci` | **67 suites, 422 tests** |
| CI `Pruebas` | verde, incluido Karate por el gateway |
| Elementos interactivos sin nombre | **0**, vigilado por `NadaInteractivoSinNombre` |
| Matriz de aislamiento | 48 casos |
| Pruebas de arquitectura | 12 |

**Sigue pendiente y no depende del código:** renovar el `SONAR_TOKEN`.

**Sigue abierto por decisión, no por descuido:** los 7 módulos aparcados de
`documentación/puntos-futuros.md` (roles, configuración global, moneda por
usuario, modo offline, notificaciones, auditoría, metas de ahorro), Row Level
Security en Postgres, el rediseño de identidad de marca, y las tres señales
visibles que hoy solo se explican con palabras (estado deshabilitado y logros
por opacidad, y las acciones que solo existen por gesto largo).
