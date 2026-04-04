# Oh Churus! - Bitácora de Desarrollo

## Estado Actual: FASE 11 - Completada (Proyecto completo)
## Última actualización: 2026-03-17

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
