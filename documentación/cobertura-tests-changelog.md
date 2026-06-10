# Oh Churus! - Cobertura de Tests - Registro de Actividades

## Estado: En desarrollo
## Objetivo: Cobertura backend > 90% (meta medida con JaCoCo, sin gate que rompa build)
## Microservicios backend: discovery (8760), gateway (8820), auth (8821), budget (8823), fasting

---

## Diagnóstico inicial (baseline)

**auth-service** — ✅ ~98% (solo `User.UserBuilder` parcial, cosmético).

**budget-service** — ⚠️ Lo viejo bien (Movement/Category/ScheduledMovement Svc 92-96%), pero 3 features nuevas SIN test:
- `BudgetAllocationServiceImpl` ~0%, `ExcelExportService` 0%, `HouseholdServiceImpl` ~1%
- `BudgetAllocationController`, `ExportController`, `HouseholdController` 0%
- `DashboardServiceImpl` ~66% (faltaba `getSplitSummary` y ramas household)
- `MovementController` / `DashboardController` parciales

**fasting-service** — ❌ Servicio completo 0%. `pom.xml` sin JaCoCo ni deps de test (H2, security-test).

**discovery / gateway** — Solo smoke test de contexto (fuera del objetivo 90% por decisión).

---

## Convenciones de test del proyecto (a respetar)
- **Servicios:** `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks` + clases `@Nested`.
- **Controllers:** `@WebMvcTest(X.class)` + `@AutoConfigureMockMvc(addFilters=false)` + `@MockBean` (servicio + `JWTAuthorizationFilter` + `SecParams`).
- ResultDTO: `isCorrect()`, `getObject()`, `getErrorCode()`. Constructores: `new ResultDTO(data)`, `new ResultDTO(false, msg, code)`.
- JaCoCo excluye: `*Application`, `config/*`, `security/SecurityConfig|SecParams|JWTAuthorizationFilter|SecurityBeansConfig`, `enums/Message*`.

---

## Fase 1: budget-service (en progreso)

### Tests creados:
- `service/HouseholdServiceImplTest.java` — create, addMember (dup), removeMember (owner/404), getByUser, getHouseholdIds + excepciones.
- `service/BudgetAllocationServiceImplTest.java` — save (upsert/404/exc), list, delete, summary (varianza/favorable/transfers), transfer (todas las validaciones + par enlazado), consolidated (superávit/déficit/missing cat), autoCloseExpired.
- `service/ExcelExportServiceTest.java` — genera workbook y lo re-lee con POI; personal + household + campos nulos + refDate null.
- `controller/HouseholdControllerTest.java`, `controller/BudgetAllocationControllerTest.java`, `controller/ExportControllerTest.java`.
- Extensión `service/DashboardServiceImplTest.java` — `getSplitSummary` + ramas household para summary/byCategory/trend/pending.

### Limpieza de calidad (producción):
- `ExcelExportService`: eliminadas variables muertas `balRow` y `configData` (code smells SonarQube).
- `backend/lombok.config` creado con `lombok.addLombokGeneratedAnnotation = true` →
  JaCoCo/SonarQube ignoran getters/setters/builders generados por Lombok (métrica de
  cobertura limpia, sin ruido de boilerplate). Aplica a TODOS los módulos.

### Compilación / cobertura: ✅ COMPLETADO 2026-06-10
- `mvn -pl budget-service clean test` → BUILD SUCCESS, **283 tests**.
- Cobertura: **97.6% instrucciones**, 0 clases por debajo de 90%.

---

## Fase 2: fasting-service ✅ COMPLETADO 2026-06-10
- `pom.xml`: agregadas deps test (h2, spring-security-test) + plugin JaCoCo con mismos excludes.
- Tests creados: `FastingServiceImplTest` (planes, sesiones, logros con todos los badges/early-bird/night-owl, agua, history/summary con streaks), `FastingControllerTest` (14 endpoints), `PeriodUtilsTest`, `LoadDataTest`, `EnumsTest`.
- `mvn -pl fasting-service clean test` → BUILD SUCCESS, **71 tests**.
- Cobertura: **98.9% instrucciones**, 0 clases por debajo de 90%.

## Fase 3: auth-service ✅ COMPLETADO 2026-06-10
- Sin tests nuevos necesarios; con `lombok.config` el reporte quedó en **100% instrucciones** (97.7% ramas).

## Fase 4: cierre ✅ COMPLETADO 2026-06-10
- discovery/gateway: smoke test de contexto OK (1 test c/u, BUILD SUCCESS).
- `mvn clean test` (reactor completo): **BUILD SUCCESS** en los 5 módulos.
  - discovery ✓ · gateway ✓ · auth ✓ · budget ✓ · fasting ✓
- Total tests backend: 283 (budget) + 71 (fasting) + auth + smoke = >360 tests verdes.

---

## Resumen de cobertura final
| Servicio | Líneas | Ramas | Sonar-coverage | Clases <90% ramas |
|----------|-------:|------:|---------------:|:-----------------:|
| auth-service | 100.0% | 97.7% | 99.6% | 0 |
| budget-service | 99.8% | 95.8% | 98.8% | 0 |
| fasting-service | 99.7% | 96.2% | 98.7% | 0 |
| discovery / gateway | smoke (sin lógica) | - | - | - |

---

## Etapa adicional 1: Branch coverage > 90% ✅ COMPLETADO 2026-06-10
- Tests de ramas (casos borde) nuevos:
  - `MovementServiceImplEdgeCasesTest`, `ScheduledMovementServiceImplEdgeCasesTest`,
    `CategoryServiceImplEdgeCasesTest`, `DashboardServiceImplEdgeCasesTest`.
  - Extensiones en `FastingServiceImplTest`, `FastingControllerTest`, `MovementControllerTest`,
    `BudgetAllocationControllerTest`, `DashboardControllerTest`, `CategoryMapperImplTest`.
- Ramas: budget 81.8%→**95.8%**, fasting 86.5%→**96.2%**. Ninguna clase < 90% ramas.
- Total tests: budget **336+**, fasting **80+**.

### Bug de producción corregido (autorizado):
- `MovementServiceImpl.enrichWithCategory` usaba un `ThreadLocal<Map>` **estático que nunca se
  limpiaba** → en un pool de hilos servía categorías cacheadas obsoletas entre requests y fugaba
  memoria. Fix: limpiar el cache (`categoryCacheTL.remove()`) al inicio de cada método público
  (saveAndUpdate, getById, getAll, confirmWithAmount, getByPeriod, getChildren). Test que valida
  el fix: `MovementServiceImplEdgeCasesTest.shouldNotServeStaleCategory`.

### Rama muerta identificada:
- `ScheduledMovementServiceImpl` L246 (break por expiración mid-span) es inalcanzable: el guard
  previo (L228) ya descarta los programados expirados antes de iterar. Documentado, no testeado.

## Etapa adicional 2: Configuración SonarQube ✅ COMPLETADO 2026-06-10
- `sonar-project.properties` en la raíz del repo:
  - Agregación de los 3 reportes JaCoCo (auth, budget, fasting) vía `sonar.coverage.jacocoReportPaths`.
  - `sonar.java.binaries`/`test.binaries` de los 5 módulos, `sonar.junit.reportPaths`.
  - Exclusiones de cobertura consistentes con JaCoCo (entity, dto, config, security, Application, Message).
  - Sección frontend (LCOV) comentada, lista para activar tras la ronda de Jest.
- Flujo: `cd backend && mvn clean verify` → `sonar-scanner` (o `mvn sonar:sonar`).

## Etapa 3: Tests frontend (Jest/RTL) ✅ COMPLETADO 2026-06-10
Objetivo acordado: ~70% (meta del enunciado para frontend).

### Reparación de breakage (54 tests rotos por evolución de componentes):
- Causa dominante (102 fallos): pantallas usan `useToast` sin `ToastProvider` en el wrapper.
  Fix central: mock global de `useToast` en `jest.setup.js` con spy estable
  (`globalThis.__mockShowToast`), manteniendo `ToastProvider` real via `requireActual`.
- Asserts desactualizados corregidos: `MovementItem` (texto "Confirmar" duplicado → getAllByText),
  `DashboardScreen` (faltaba `useNavigation` en mock + `getSplitSummary` + modal de confirmación),
  validaciones que migraron de `Alert.alert` a `showToast`, taglines de Login/Register,
  botón "Generar", endpoint `confirm` con body `{}`.

### Tests nuevos (cobertura):
- Servicios: `fastingService`, `householdService`, `budgetAllocationService`.
- Utils: `periodUtils` (meses cortos, navegación de período, cruces de año).
- Context: `ToastContext` (timers, durez por tipo, hide manual, error sin provider).
- Componentes: `Toast`, `ToastContainer`, `BudgetVsReal`, `DonutChart`, `CenterFAB`.
- Pantallas (render-smoke con servicios mockeados): FastingDashboard, FastingConfig,
  FastingHistory, Budget, Consolidated, Summary, Household, MovementsList,
  CategoryDrillDown, Settings, PeriodConfig, ExportImport, Onboarding.

### Config de testing:
- `package.json`: `collectCoverageFrom` (excluye types/theme/AppNavigator), `coverageReporters`
  (text-summary, lcov, json-summary), `testTimeout: 20000` (resuelve flakiness por carga paralela
  de jest-expo).

### Resultado: ✅ **54 suites / 301 tests verdes**
- **Statements 70.18% · Lines 72.24% · Functions 57.54% · Branches 52.38%**
- Subió de 24.56% (baseline con 54 tests rotos) a 70%+.
- SonarQube: `sonar-project.properties` ampliado con `frontend/src`, `test.inclusions`,
  `javascript.lcov.reportPaths` y exclusiones de cobertura combinadas backend+frontend.

---

## ESTADO FINAL DEL PROYECTO (2026-06-10)
| Capa | Cobertura | Tests |
|------|-----------|-------|
| auth-service | 100% líneas / 97.7% ramas | ✓ |
| budget-service | 99.8% líneas / 95.8% ramas | 336 |
| fasting-service | 99.7% líneas / 96.2% ramas | 80+ |
| frontend | 72.2% líneas / 70.2% statements | 301 |
| discovery/gateway | smoke | 1 c/u |

Listo para SonarQube (`mvn clean verify` en backend + `jest --coverage` en frontend → `sonar-scanner`).
