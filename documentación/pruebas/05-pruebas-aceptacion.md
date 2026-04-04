# Pruebas de Aceptacion

## Que son

Las pruebas de aceptacion verifican que el sistema cumple con los **criterios de aceptacion** definidos en los requerimientos. No prueban una clase o un endpoint individual, sino un **flujo completo de negocio** tal como lo usaria el usuario final.

En nuestro proyecto, las pruebas de aceptacion estan implementadas como:
1. **Escenarios Karate encadenados** - flujos end-to-end en el backend
2. **Tests de pantallas en el frontend** - verifican que la UI cumple los requisitos

---

## Criterios de Aceptacion y sus Pruebas

### CA-1: "El usuario puede registrarse y hacer login"

**Pruebas que lo verifican:**
- `auth.feature` -> "Register new user" + "Login with valid credentials"
- `LoginScreen.test.tsx` -> Renderiza formulario con campos requeridos
- `RegisterScreen.test.tsx` -> Valida campos vacios, longitud de password
- `AuthContext.test.tsx` -> Login almacena token y datos de usuario

**Flujo probado:**
```
Register (name, email, password)
  -> Recibe token JWT
  -> Token se guarda en AsyncStorage
  -> App muestra Dashboard (usuario autenticado)
```

---

### CA-2: "Las categorias tienen estructura de arbol con maximo 3 niveles"

**Pruebas que lo verifican:**
- `CategoryServiceImplTest` -> `shouldCreateRootCategory`, `shouldCreateChildCategory`
- `CategoryServiceImplTest` -> `shouldFailWhenMaxDepthExceeded` (error 202)
- `categories.feature` -> "Create root category" + "Create child category"
- `CategoriesScreen.test.tsx` -> Renderiza arbol, expande/colapsa hijos
- `CategoryFormScreen.test.tsx` -> Formulario con tipo, icono, color

**Regla de negocio validada:**
```
Nivel 1: Vivienda (raiz)
  Nivel 2: Arriendo (hijo)
    Nivel 3: Arriendo oficina (nieto)
      Nivel 4: RECHAZADO (error 202: max depth)
```

---

### CA-3: "Los movimientos tienen borrado logico"

**Pruebas que lo verifican:**
- `MovementServiceImplTest` -> `shouldSoftDelete` (verifica active=false)
- `movements.feature` -> "Delete movement (soft delete)" + verifica que ya no aparece en getById
- `MovementsScreen.test.tsx` -> Renderiza lista de movimientos

**Flujo probado:**
```
Crear movimiento -> Existe en lista
  -> Delete -> active = false
  -> getById -> No encontrado (solo busca active=true)
  -> getAll -> No aparece
```

---

### CA-4: "Los movimientos programados generan pendientes al inicio del periodo"

**Pruebas que lo verifican:**
- `ScheduledMovementServiceImplTest` -> `shouldGeneratePendingMonthly`
- `ScheduledMovementServiceImplTest` -> `shouldNotGenerateWhenAlreadyExists` (idempotencia)
- `ScheduledMovementServiceImplTest` -> `shouldNotGenerateForExpired`, `shouldNotGenerateForFuture`
- `scheduled.feature` -> "Generate pending movements"
- `ScheduledScreen.test.tsx` -> Boton "Generar Pendientes"

**Flujo probado:**
```
Crear programado (Arriendo, mensual, $1,500,000)
  -> Generar pendientes
  -> Se crea Movement(confirmed=false, amount=1,500,000)
  -> Generar de nuevo -> NO crea duplicado (idempotente)
```

---

### CA-5: "El dashboard muestra balance, ingresos, gastos y pendientes"

**Pruebas que lo verifican:**
- `DashboardServiceImplTest` -> `shouldReturnSummaryWithData` (calcula balance correcto)
- `DashboardServiceImplTest` -> `shouldCalculatePositiveTrend` (tendencia)
- `dashboard.feature` -> "Get dashboard summary"
- `DashboardScreen.test.tsx` -> Renderiza 4 stat cards: Balance, Ingresos, Gastos, Pendientes
- `DashboardScreen.test.tsx` -> Muestra pendientes con boton Confirmar

**Datos calculados:**
```
Balance = totalIncome - totalExpense
Tendencia = ((balanceActual - balanceAnterior) / |balanceAnterior|) * 100
```

---

### CA-6: "El dia de inicio de presupuesto es configurable y maneja meses cortos"

**Pruebas que lo verifican:**
- `PeriodUtilsTest` -> 18 tests de calculo de periodos
- `PeriodUtilsTest` -> `shouldAdjustDay31ToFeb28`, `shouldAdjustDay31ToFeb29LeapYear`
- `DashboardServiceImplTest` -> `shouldReturnSummaryWithCustomStartDay`
- `dashboard.feature` -> "Dashboard with budgetStartDay 31 (handles short months)"
- `ProfileScreen.test.tsx` -> Campo de configuracion "Dia de inicio de presupuesto"

**Regla de negocio validada:**
```
Usuario con budgetStartDay = 31
  -> Enero: periodo 31 ene - 27 feb
  -> Febrero (no bisiesto): periodo 28 feb - 30 mar (ajusta a ultimo dia)
  -> Abril: periodo 30 abr - 30 may (ajusta a 30)
```

---

### CA-7: "Sin token JWT no se puede acceder a endpoints protegidos"

**Pruebas que lo verifican:**
- `JWTAuthorizationFilterTest` -> `shouldClearContextWithInvalidJWT` (401)
- `users.feature` -> "Access without token should fail" (403)
- `AuthContext.test.tsx` -> `isAuthenticated` controla acceso a pantallas

---

### CA-8: "Los movimientos pendientes se pueden confirmar"

**Pruebas que lo verifican:**
- `MovementServiceImplTest` -> `shouldConfirmMovement`
- `movements.feature` -> "Confirm a pending movement"
- `DashboardScreen.test.tsx` -> Boton "Confirmar" en pendientes
- `MovementItem.test.tsx` -> Muestra boton solo si `confirmed=false`

---

## Matriz de Trazabilidad

| Requisito | Unitaria | Controlador | Karate | Frontend |
|-----------|----------|-------------|--------|----------|
| Login/Registro | AuthServiceImplTest | AuthControllerTest | auth.feature | LoginScreen, AuthContext |
| Categorias arbol | CategoryServiceImplTest | CategoryControllerTest | categories.feature | CategoriesScreen, CategoryForm |
| CRUD Movimientos | MovementServiceImplTest | MovementControllerTest | movements.feature | MovementsScreen, MovementForm |
| Borrado logico | *ServiceImplTest (soft delete) | *ControllerTest (delete) | *.feature (delete) | - |
| Programados | ScheduledServiceImplTest | ScheduledControllerTest | scheduled.feature | ScheduledScreen, ScheduledForm |
| Dashboard | DashboardServiceImplTest | DashboardControllerTest | dashboard.feature | DashboardScreen |
| Periodos/dias | PeriodUtilsTest | - | dashboard.feature | ProfileScreen |
| Seguridad JWT | JWTFilterTest | - | users.feature | AuthContext |

---

## Como explicar en la presentacion

> "Las pruebas de aceptacion validan que el sistema cumple con los requisitos del negocio. Cada criterio de aceptacion se prueba en multiples capas: la logica de negocio con pruebas unitarias, los endpoints con MockMvc, el flujo completo con Karate, y la interfaz de usuario con React Testing Library. Esta cobertura en multiples capas nos da confianza de que el sistema funciona correctamente de extremo a extremo."
