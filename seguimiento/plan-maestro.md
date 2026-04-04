# Oh Churus! - Plan Maestro de Desarrollo

## Estado General: Completado
## Fecha inicio: 2026-03-17
## Fecha verificacion: 2026-03-21
## Tecnologías: Java 17, Spring Boot 3.2, React Native (Expo), PostgreSQL, Docker

---

## Resumen de Fases

| Fase | Nombre | Alcance | Estado |
|------|--------|---------|--------|
| 0 | Infraestructura Base | Proyecto Maven, Docker, Discovery, Gateway | Completado |
| 1 | Auth Service | Usuarios, autenticación JWT, seed data | Completado |
| 2 | Core Service | Categorías (árbol) en budget-service | Completado |
| 3 | Budget Service - Movimientos | CRUD movimientos con borrado lógico | Completado |
| 4 | Budget Service - Programados | Movimientos programados, lógica de generación | Completado |
| 5 | Dashboard API | Endpoints de dashboard, cálculos, agregaciones | Completado |
| 6 | Pruebas Backend Completas | JUnit + Mockito (auth: 91.9%, budget: 91.3%) | Completado |
| 7 | Frontend - Infraestructura | Proyecto React Native, tema, navegación, auth | Completado |
| 8 | Frontend - Pantallas Core | Categorías, movimientos, presupuestos, perfil | Completado |
| 9 | Frontend - Dashboard | Dashboard con stat cards, pendientes, tendencias | Completado |
| 10 | Frontend - Pruebas | Jest + React Testing Library (74.3% cobertura) | Completado |
| 11 | Integración y Polish | Docker Compose, colección Postman | Completado |

### Métricas de pruebas (2026-03-21)
- **auth-service**: 79 tests, 91.9% cobertura (JaCoCo)
- **budget-service**: 180 tests, 91.3% cobertura (JaCoCo)
- **frontend**: 179 tests (30 suites), 74.3% statements (Jest)

---

## FASE 0: Infraestructura Base
**Objetivo:** Establecer la estructura del proyecto, configuración Maven multi-módulo, Docker Compose, y los servicios de infraestructura (Discovery + Gateway).

### Entregables:
- [ ] Estructura de carpetas del proyecto completa
- [ ] POM padre con todos los módulos declarados
- [ ] `discovery-service` funcional (Eureka Server, puerto 8760)
- [ ] `gateway-service` funcional (Spring Cloud Gateway, puerto 8820)
- [ ] `docker-compose.yml` con PostgreSQL + Discovery + Gateway
- [ ] Script `init-databases.sql` para crear las 3 BD
- [ ] Archivo `.env` con variables de entorno
- [ ] Dockerfiles para cada servicio
- [ ] Verificación: servicios levantan y se registran en Eureka

### Archivos a crear:
```
backend/
├── pom.xml (parent)
├── docker-compose.yml
├── .env
├── init-db/
│   └── init-databases.sql
├── discovery-service/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/ohchurus/discovery/
│       └── DiscoveryServiceApplication.java
│   └── src/main/resources/
│       └── application.properties
├── gateway-service/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/ohchurus/gateway/
│       ├── GatewayServiceApplication.java
│       └── config/GatewayConfig.java
│   └── src/main/resources/
│       └── application.yml
```

### Criterios de aceptación:
1. `mvn clean package` compila sin errores
2. `docker-compose up` levanta PostgreSQL, Discovery y Gateway
3. Eureka dashboard visible en http://localhost:8760
4. Gateway responde en http://localhost:8820

---

## FASE 1: Auth Service
**Objetivo:** Implementar el servicio de autenticación con usuarios, JWT y data semilla. Sin roles por ahora (fase futura).

### Entregables:
- [ ] Entidad: User (id, name, email, password, budgetStartDay, active, createdAt, updatedAt)
- [ ] DTOs: UserSaveDTO, UserFilterDTO, ResultUserDTO, AuthenticationRequest, AuthenticationResponse
- [ ] Repository: UserRepository
- [ ] Service: UserService, AuthenticationService + Impls
- [ ] Controller: AuthenticationController, UserController
- [ ] Security: SecurityConfig, JWTAuthorizationFilter, MyUserDetailsService
- [ ] Config: AppConfig (ModelMapper, BCrypt)
- [ ] LoadData: Seed de usuarios por defecto (admin, demo)
- [ ] Enums: Message (mensajes de error)
- [ ] Pruebas unitarias (Mockito): >= 80% cobertura
  - [ ] UserServiceImpl tests
  - [ ] AuthenticationServiceImpl tests
  - [ ] JWTAuthorizationFilter tests
  - [ ] Controller tests
- [ ] Pruebas Karate: flujos completos
  - [ ] Login exitoso / fallido
  - [ ] CRUD de usuarios
  - [ ] Validación de token
- [ ] Dockerfile
- [ ] Integración en docker-compose.yml
- [ ] Endpoints Postman documentados

### Estructura de paquetes:
```
auth-service/src/main/java/com/ohchurus/auth/
├── AuthServiceApplication.java
├── config/
│   └── AppConfig.java
├── controller/
│   ├── AuthenticationController.java
│   └── UserController.java
├── dto/
│   ├── input/
│   │   ├── AuthenticationRequest.java
│   │   ├── UserSaveDTO.java
│   │   └── UserFilterDTO.java
│   └── output/
│       ├── AuthenticationResponse.java
│       ├── ResultUserDTO.java
│       └── ResultDTO.java
├── entity/
│   └── User.java
├── enums/
│   └── Message.java
├── mapper/
│   ├── UserMapper.java
│   └── impl/UserMapperImpl.java
├── repository/
│   └── UserRepository.java
├── security/
│   ├── JWTAuthorizationFilter.java
│   ├── MyUserDetailsService.java
│   ├── SecParams.java
│   └── SecurityConfig.java
├── service/
│   ├── AuthenticationService.java
│   ├── UserService.java
│   └── impl/
│       ├── AuthenticationServiceImpl.java
│       ├── UserServiceImpl.java
│       └── LoadData.java
└── util/
    ├── SecurityUtil.java
    └── ValidationUtils.java
```

### Endpoints:
| Método | URL | Descripción | Auth |
|--------|-----|-------------|------|
| POST | /oh-churus/v1/auth/login | Login | No |
| POST | /oh-churus/v1/auth/register | Registro | No |
| POST | /oh-churus/v1/users/save | Crear/actualizar usuario | Sí |
| POST | /oh-churus/v1/users/get/{id} | Obtener usuario | Sí |
| POST | /oh-churus/v1/users/all | Listar usuarios | Sí |
| POST | /oh-churus/v1/users/delete/{id} | Borrado lógico | Sí |

### Criterios de aceptación:
1. Login retorna JWT válido
2. Endpoints protegidos rechazan sin token
3. Seed data carga usuarios por defecto al iniciar
4. Pruebas unitarias pasan con >= 80% cobertura
5. Pruebas Karate cubren flujos completos
6. Servicio se registra en Eureka y es accesible via Gateway

---

## FASE 2: Core Service
**Objetivo:** Implementar categorías con estructura de árbol. Sin AppConfiguration por ahora (fase futura).

### Entregables:
- [ ] Entidad: Category
- [ ] Enums: CategoryType (INCOME, EXPENSE)
- [ ] DTOs: CategorySaveDTO, CategoryFilterDTO, ResultCategoryDTO, ResultCategoryTreeDTO
- [ ] Repository: CategoryRepository (con custom queries para árbol)
- [ ] Service: CategoryService + Impl
- [ ] Controller: CategoryController
- [ ] Security: SecurityConfig, JWTAuthorizationFilter (validación JWT)
- [ ] Feign Client: AuthClient (para validar usuarios)
- [ ] LoadData: Seed de categorías por defecto para el usuario demo
- [ ] Pruebas unitarias (Mockito): >= 80% cobertura
  - [ ] CategoryServiceImpl tests (incluir lógica de árbol)
  - [ ] Controller tests
- [ ] Pruebas Karate: flujos completos
  - [ ] CRUD categorías
  - [ ] Estructura de árbol (crear padre, crear hijo, obtener árbol)
  - [ ] Validaciones (máximo 3 niveles, nombre duplicado)
- [ ] Dockerfile
- [ ] Integración en docker-compose.yml
- [ ] Endpoints Postman documentados

### Endpoints:
| Método | URL | Descripción | Auth |
|--------|-----|-------------|------|
| POST | /oh-churus/v1/categories/save | Crear/actualizar | Sí |
| POST | /oh-churus/v1/categories/get/{id} | Obtener por ID | Sí |
| POST | /oh-churus/v1/categories/all | Listar con filtros | Sí |
| POST | /oh-churus/v1/categories/tree | Obtener árbol completo | Sí |
| POST | /oh-churus/v1/categories/delete/{id} | Borrado lógico | Sí |
| POST | /oh-churus/v1/categories/type-list | Listar tipos (enum) | Sí |

### Criterios de aceptación:
1. Categorías se guardan con estructura de árbol
2. Endpoint `/tree` retorna árbol completo con hijos anidados
3. Validación de máximo 3 niveles de profundidad
4. Seed data carga categorías por defecto para el usuario demo
5. Pruebas pasan con >= 80% cobertura

---

## FASE 3: Budget Service - Movimientos
**Objetivo:** Implementar el CRUD de movimientos financieros con borrado lógico.

### Entregables:
- [ ] Entidad: Movement
- [ ] DTOs: MovementDTO, MovementSaveDTO, MovementFilterDTO, ResultMovementDTO
- [ ] Repository: MovementRepository (con custom queries para filtros por fecha/categoría/usuario)
- [ ] Service: MovementService + Impl
- [ ] Controller: MovementController
- [ ] Security: SecurityConfig, JWTAuthorizationFilter
- [ ] Feign Client: CoreClient (para obtener nombre de categoría)
- [ ] CompletionUtils: Enriquecer response con categoryName, categoryType
- [ ] Pruebas unitarias (Mockito): >= 80% cobertura
  - [ ] MovementServiceImpl tests
  - [ ] Controller tests
  - [ ] CompletionUtils tests
- [ ] Pruebas Karate: flujos completos
  - [ ] CRUD movimientos
  - [ ] Filtros por fecha, categoría, usuario
  - [ ] Borrado lógico
  - [ ] Validaciones (monto > 0, categoría existe, etc.)
- [ ] Integración en docker-compose.yml
- [ ] Endpoints Postman documentados

### Endpoints:
| Método | URL | Descripción | Auth |
|--------|-----|-------------|------|
| POST | /oh-churus/v1/movements/save | Crear/actualizar | Sí |
| POST | /oh-churus/v1/movements/get/{id} | Obtener por ID | Sí |
| POST | /oh-churus/v1/movements/all | Listar con filtros | Sí |
| POST | /oh-churus/v1/movements/delete/{id} | Borrado lógico | Sí |
| POST | /oh-churus/v1/movements/confirm/{id} | Confirmar movimiento | Sí |
| POST | /oh-churus/v1/movements/by-period | Por período presupuestal | Sí |

### Criterios de aceptación:
1. CRUD completo funcional
2. Filtros por fecha, categoría y estado (confirmado/pendiente)
3. Response enriquecido con nombre y tipo de categoría
4. Borrado lógico funcional
5. Pruebas con >= 80% cobertura

---

## FASE 4: Budget Service - Movimientos Programados
**Objetivo:** Implementar movimientos programados (presupuestos) con la lógica de generación automática de pendientes.

### Entregables:
- [ ] Entidad: ScheduledMovement
- [ ] Enum: Frequency
- [ ] DTOs: ScheduledMovementDTO, ScheduledMovementSaveDTO, ScheduledMovementFilterDTO, ResultScheduledMovementDTO
- [ ] Repository: ScheduledMovementRepository
- [ ] Service: ScheduledMovementService + Impl
- [ ] Controller: ScheduledMovementController
- [ ] Lógica de generación: Servicio que genera movimientos pendientes al consultar
- [ ] Pruebas unitarias (Mockito): >= 80% cobertura
  - [ ] ScheduledMovementServiceImpl tests
  - [ ] Lógica de generación tests (múltiples escenarios)
  - [ ] Controller tests
- [ ] Pruebas Karate: flujos completos
  - [ ] CRUD movimientos programados
  - [ ] Generación de pendientes
  - [ ] Frecuencias: mensual, semanal, etc.
  - [ ] Duración finita vs indefinida
  - [ ] Endpoint de frecuencias (enum list)
- [ ] Endpoints Postman documentados

### Endpoints:
| Método | URL | Descripción | Auth |
|--------|-----|-------------|------|
| POST | /oh-churus/v1/scheduled/save | Crear/actualizar | Sí |
| POST | /oh-churus/v1/scheduled/get/{id} | Obtener por ID | Sí |
| POST | /oh-churus/v1/scheduled/all | Listar con filtros | Sí |
| POST | /oh-churus/v1/scheduled/delete/{id} | Borrado lógico | Sí |
| POST | /oh-churus/v1/scheduled/generate-pending | Generar pendientes del período | Sí |
| POST | /oh-churus/v1/scheduled/frequency-list | Listar frecuencias (enum) | Sí |

### Criterios de aceptación:
1. CRUD completo funcional
2. Generación de pendientes respeta frecuencia y fecha de inicio
3. No genera duplicados (idempotente)
4. Maneja duración finita e indefinida
5. Pruebas con >= 80% cobertura

---

## FASE 5: Dashboard API
**Objetivo:** Implementar los endpoints del dashboard con cálculos de presupuesto, balance y tendencias.

### Entregables:
- [ ] DTOs: DashboardDTO, BudgetSummaryDTO, PeriodComparisonDTO
- [ ] Service: DashboardService + Impl
- [ ] Controller: DashboardController
- [ ] Lógica:
  - [ ] Cálculo de período actual (basado en budgetStartDay del usuario)
  - [ ] Presupuesto tentativo (suma de programados activos)
  - [ ] Gastos/ingresos confirmados del período
  - [ ] Pendientes por confirmar
  - [ ] Balance (ingresos - gastos)
  - [ ] Tendencia vs período anterior
  - [ ] Distribución por categoría
- [ ] Pruebas unitarias (Mockito): >= 80% cobertura
  - [ ] DashboardServiceImpl tests (múltiples escenarios de cálculo)
  - [ ] Edge cases: período sin movimientos, primer mes de uso, etc.
- [ ] Pruebas Karate: flujos del dashboard
- [ ] Endpoints Postman documentados

### Endpoints:
| Método | URL | Descripción | Auth |
|--------|-----|-------------|------|
| POST | /oh-churus/v1/dashboard/summary | Resumen general | Sí |
| POST | /oh-churus/v1/dashboard/by-category | Distribución por categoría | Sí |
| POST | /oh-churus/v1/dashboard/trend | Tendencia vs período anterior | Sí |
| POST | /oh-churus/v1/dashboard/pending | Pendientes del período | Sí |

### Criterios de aceptación:
1. Dashboard calcula correctamente basado en el budgetStartDay del usuario
2. Maneja correctamente períodos sin datos
3. Tendencia calcula % de cambio correctamente
4. Pruebas con >= 80% cobertura

---

## FASE 6: Pruebas Backend Completas
**Objetivo:** Garantizar cobertura completa de pruebas en todos los servicios, consolidar Karate tests.

### Entregables:
- [ ] Revisión y completar pruebas unitarias faltantes en todos los servicios
- [ ] Suite completa de Karate tests:
  - [ ] Auth Service: login, register, CRUD users, token validation
  - [ ] Core Service: CRUD categories, tree structure, CRUD config
  - [ ] Budget Service: CRUD movements, CRUD scheduled, dashboard, generación de pendientes
- [ ] Configuración de Karate runner
- [ ] Reportes de cobertura (JaCoCo)
- [ ] Documentación de pruebas (qué se cubre, cómo ejecutar)

### Criterios de aceptación:
1. Cobertura >= 80% en todos los servicios
2. Karate tests cubren todos los endpoints
3. Todos los tests pasan con `mvn test`
4. Reporte de cobertura generado

---

## FASE 7: Frontend - Infraestructura
**Objetivo:** Establecer el proyecto React Native con tema oscuro/claro, navegación, autenticación y diseño base.

### Entregables:
- [ ] Proyecto React Native (Expo) inicializado
- [ ] Sistema de tema (dark/light) con provider
  - [ ] Paleta de colores: tonos ardilla (marrón, naranja, verde bosque / beige, crema, verde menta)
  - [ ] Modo oscuro por defecto
  - [ ] Toggle de tema persistente
- [ ] Navegación configurada (React Navigation)
  - [ ] Stack Navigator para auth (Login, Register)
  - [ ] Bottom Tab Navigator para app (Dashboard, Movimientos, Categorías, Presupuestos, Perfil)
- [ ] Estructura Atomic Design:
  - [ ] atoms/ (Button, Input, Text, Icon, Badge, Toggle)
  - [ ] molecules/ (Card, ListItem, SearchBar, ModalConfirm)
  - [ ] organisms/ (Header, BottomNav, CategoryTree, MovementList)
  - [ ] templates/ (AuthTemplate, MainTemplate)
  - [ ] screens/ (todas las pantallas)
- [ ] Servicio API centralizado (Axios + interceptores JWT)
- [ ] Context de autenticación (AuthContext)
- [ ] Almacenamiento local (AsyncStorage para token y preferencias)
- [ ] Logo y mascota (ardilla) - assets base
- [ ] Fuentes y tipografía configuradas
- [ ] Splash screen con la ardilla

### Criterios de aceptación:
1. App compila y ejecuta en web y móvil
2. Tema oscuro/claro funcional con toggle
3. Login funcional contra auth-service
4. Navegación fluida entre tabs
5. Interceptor JWT redirige a login si token expirado

---

## FASE 8: Frontend - Pantallas Core
**Objetivo:** Implementar las pantallas de categorías, movimientos y presupuestos.

### Entregables:
- [ ] Pantalla de Categorías:
  - [ ] Vista de árbol interactiva (expandir/colapsar)
  - [ ] Crear/editar categoría (modal o pantalla)
  - [ ] Selector de ícono y color
  - [ ] Indicador visual de tipo (ingreso/gasto)
  - [ ] Borrado lógico con confirmación
- [ ] Pantalla de Movimientos:
  - [ ] Lista con scroll infinito
  - [ ] Filtros (fecha, categoría, tipo, estado)
  - [ ] Crear/editar movimiento
  - [ ] Selector de categoría (con árbol)
  - [ ] Confirmar movimiento pendiente (swipe o botón)
  - [ ] Borrado con confirmación
- [ ] Pantalla de Presupuestos (Mov. Programados):
  - [ ] Lista de programados activos
  - [ ] Crear/editar programado
  - [ ] Selector de frecuencia
  - [ ] Indicador de estado (activo, finalizado)
  - [ ] Borrado con confirmación
- [ ] Pantalla de Perfil/Configuración:
  - [ ] Datos del usuario
  - [ ] Cambiar día de inicio de presupuesto
  - [ ] Cambiar moneda
  - [ ] Cambiar decimales
  - [ ] Toggle modo oscuro/claro
  - [ ] Cerrar sesión

### Criterios de aceptación:
1. Todas las pantallas conectan con el backend
2. CRUD completo funcional en cada pantalla
3. Diseño minimalista con toques de la mascota ardilla
4. Responsive (web y móvil)
5. Manejo de estados de carga y errores

---

## FASE 9: Frontend - Dashboard
**Objetivo:** Implementar el dashboard principal con widgets interactivos, gráficos y la mascota ardilla.

### Entregables:
- [ ] Widget de Balance:
  - [ ] Ardilla feliz (balance positivo) / triste (negativo)
  - [ ] Animación sutil de la ardilla
  - [ ] Monto total formateado
- [ ] Widget de Presupuesto:
  - [ ] Barra de progreso (gastado vs presupuestado)
  - [ ] Estilo "bellota llenándose" o "árbol creciendo"
  - [ ] Porcentaje de uso
- [ ] Widget de Pendientes:
  - [ ] Lista compacta de movimientos por confirmar
  - [ ] Acción rápida de confirmar
  - [ ] Contador tipo badge
- [ ] Gráfico de distribución:
  - [ ] Pie chart o donut por categoría
  - [ ] Colores según las categorías
  - [ ] Interactivo (tap para detalle)
- [ ] Widget de Tendencia:
  - [ ] Comparación con período anterior
  - [ ] Flecha arriba/abajo con porcentaje
  - [ ] Mini gráfico de línea
- [ ] Elementos decorativos de la ardilla:
  - [ ] Personaje en header/esquina del dashboard
  - [ ] Frases motivacionales contextuales
  - [ ] Micro-animaciones al interactuar

### Criterios de aceptación:
1. Dashboard carga datos reales del backend
2. Widgets actualizan al confirmar pendientes
3. Gráficos interactivos y legibles
4. La ardilla reacciona al estado financiero del usuario
5. Performance fluida (no lag en animaciones)

---

## FASE 10: Frontend - Pruebas
**Objetivo:** Implementar pruebas automatizadas para el frontend.

### Entregables:
- [ ] Configuración de Jest + React Testing Library
- [ ] Tests de componentes atómicos (atoms/)
- [ ] Tests de moléculas (molecules/)
- [ ] Tests de hooks personalizados
- [ ] Tests de servicios API (mocks)
- [ ] Tests de contextos (AuthContext, ThemeContext)
- [ ] Tests de navegación
- [ ] Tests de pantallas principales (snapshot + interacción)
- [ ] Cobertura >= 70%

### Criterios de aceptación:
1. Todos los tests pasan
2. Cobertura >= 70%
3. CI puede ejecutar `npm test` sin fallos

---

## FASE 11: Integración y Polish
**Objetivo:** Integración completa, documentación final, colección Postman, y pulido general.

### Entregables:
- [ ] Colección Postman completa y organizada por servicio
- [ ] docker-compose.yml final con todos los servicios
- [ ] Verificación de flujo completo end-to-end
- [ ] README.md del proyecto
- [ ] Seed data completa y verificada
- [ ] Revisión de seguridad básica
- [ ] Revisión de performance
- [ ] Fix de bugs encontrados en integración
- [ ] Screenshots de la app para documentación
- [ ] Video demo (opcional)

### Criterios de aceptación:
1. `docker-compose up` levanta todo el backend funcional
2. Frontend conecta y funciona con backend dockerizado
3. Postman collection importable y funcional
4. Seed data carga correctamente
5. Documentación completa

---

## Notas Generales

### Orden de implementación estricto
Las fases son secuenciales. No se avanza a la siguiente hasta que la actual tenga:
- Código completo y compilando
- Pruebas pasando con la cobertura requerida
- Documentación actualizada en la bitácora

### Principios de calidad
- **Cada fase es completa:** No se deja deuda técnica para "después"
- **Pruebas primero (o al menos junto):** Cada servicio/componente se entrega con sus pruebas
- **Revisión de código:** Cada fase incluye revisión antes de cerrar
- **Compatibilidad:** Todo nuevo código es compatible con lo existente

### Convenciones de código
- Backend: Seguir patrones de HexaQuantum como referencia
- Frontend: Atomic Design + patrones React modernos
- Nombres: Consistentes con las convenciones definidas en entidades-y-relaciones.md
