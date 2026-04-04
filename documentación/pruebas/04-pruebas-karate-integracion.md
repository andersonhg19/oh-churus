# Pruebas de Integracion - Karate (BDD)

## Que es Karate

Karate es un framework de pruebas que permite escribir tests de API en formato **BDD** (Behavior-Driven Development) usando la sintaxis **Given/When/Then** (Dado/Cuando/Entonces). A diferencia de las pruebas unitarias que prueban clases aisladas, Karate prueba el sistema **completo** haciendo requests HTTP reales contra el servidor.

**Ventajas:**
- Archivos `.feature` legibles como documentacion
- No requiere conocimiento de Java para escribir los tests
- Encadenamiento de escenarios (usar el resultado de uno en otro)
- Validacion de JSON completa

---

## Auth Service - Karate Tests (11 escenarios)

### auth.feature (5 escenarios)

```gherkin
Feature: Autenticacion y Registro

  # CASO FELIZ: Login exitoso
  Scenario: Login with valid credentials
    Given url baseUrl + '/oh-churus/v1/auth/login'
    And request { email: 'demo@ohchurus.com', password: 'Demo123!' }
    When method post
    Then status 200
    And match response.correct == true
    And match response.object.token == '#notnull'    # Hay un token
    And match response.object.userId == '#notnull'   # Hay un userId

  # CASO DE ERROR: Password incorrecta
  Scenario: Login with invalid password
    Given url baseUrl + '/oh-churus/v1/auth/login'
    And request { email: 'demo@ohchurus.com', password: 'wrongpassword' }
    When method post
    Then status 401    # Unauthorized

  # CASO DE ERROR: Email que no existe
  Scenario: Login with non-existent email
    Given url baseUrl + '/oh-churus/v1/auth/login'
    And request { email: 'noexiste@test.com', password: '123456' }
    When method post
    Then status 401

  # CASO FELIZ: Registro nuevo
  Scenario: Register new user
    Given url baseUrl + '/oh-churus/v1/auth/register'
    And request { name: 'Test User', email: 'test_[timestamp]@test.com', password: 'Test123!' }
    When method post
    Then status 200
    And match response.correct == true
    And match response.object.token == '#notnull'

  # VALIDACION: Email duplicado
  Scenario: Register with duplicate email should fail
    Given url baseUrl + '/oh-churus/v1/auth/register'
    And request { name: 'Otro', email: 'demo@ohchurus.com', password: 'Test123!' }
    When method post
    Then status 200
    And match response.correct == false
    And match response.errorCode == 102
```

### users.feature (6 escenarios)

```gherkin
Feature: Gestion de Usuarios (requiere autenticacion)

  Background:
    # Login para obtener token (se ejecuta antes de cada escenario)
    Given url baseUrl + '/oh-churus/v1/auth/login'
    And request { email: 'admin@ohchurus.com', password: 'Admin123!' }
    When method post
    Then status 200
    * def token = response.object.token
    * def userId = response.object.userId

  # CASO FELIZ: Obtener usuario
  Scenario: Get user by ID
    Given url baseUrl + '/oh-churus/v1/users/get/' + userId
    And header Authorization = 'Bearer ' + token
    When method post
    Then status 200
    And match response.correct == true

  # CASO DE ERROR: Usuario inexistente
  Scenario: Get non-existent user
    Given url baseUrl + '/oh-churus/v1/users/get/99999'
    And header Authorization = 'Bearer ' + token
    When method post
    Then status 200
    And match response.correct == false
    And match response.errorCode == 103

  # VALIDACION: Sin token = 403
  Scenario: Access without token should fail
    Given url baseUrl + '/oh-churus/v1/users/get/1'
    When method post
    Then status 403    # Forbidden
```

---

## Budget Service - Karate Tests (21 escenarios)

### categories.feature (5 escenarios)

| Escenario | Tipo | Que prueba |
|-----------|------|-----------|
| Get category tree | Caso feliz | Obtener arbol de categorias del usuario demo |
| Create root category | Caso feliz | Crear categoria raiz con todos los campos |
| Create child category | Caso feliz | Crear subcategoria con parentId |
| Get type list | Validacion | Retorna INCOME y EXPENSE |
| Get all paginated | Caso feliz | Lista paginada con filtros |

### movements.feature (7 escenarios)

| Escenario | Tipo | Que prueba |
|-----------|------|-----------|
| Create movement | Caso feliz | Crear movimiento confirmado |
| Get by ID | Caso feliz | Obtener movimiento creado |
| Get all paginated | Caso feliz | Lista con paginacion |
| Delete (soft) | Caso feliz | Borrado logico y verificar que ya no aparece |
| Confirm pending | Caso feliz | Confirmar movimiento pendiente |
| Get by period | Caso feliz | Filtrar por rango de fechas |

**Flujo encadenado:** Create -> Get (verificar) -> Delete -> Get (verificar que no existe)

### scheduled.feature (5 escenarios)

| Escenario | Tipo | Que prueba |
|-----------|------|-----------|
| Create scheduled | Caso feliz | Crear movimiento recurrente mensual |
| Create with duration | Caso feliz | Crear con duracion y verificar endDate calculado |
| Get frequency list | Validacion | Retorna 7 frecuencias |
| Generate pending | Caso feliz | Genera movimientos pendientes |
| Get all paginated | Caso feliz | Lista con filtros |

### dashboard.feature (5 escenarios)

| Escenario | Tipo | Que prueba |
|-----------|------|-----------|
| Get summary | Caso feliz | Retorna balance, ingresos, gastos, pendientes |
| Get by category | Caso feliz | Distribucion por categoria |
| Get trend | Caso feliz | Tendencia vs periodo anterior |
| Get pending | Caso feliz | Movimientos sin confirmar |
| Day 31 in short months | Caso borde | budgetStartDay=31 en febrero funciona correctamente |

---

## Diferencia entre Unitarias vs Integracion

```
PRUEBA UNITARIA (Mockito)          PRUEBA DE INTEGRACION (Karate)
┌─────────────────────────┐        ┌─────────────────────────────┐
│  Test                   │        │  Test (.feature)            │
│    │                    │        │    │                        │
│    ▼                    │        │    ▼                        │
│  ServiceImpl            │        │  HTTP Request               │
│    │                    │        │    │                        │
│    ▼                    │        │    ▼                        │
│  Mock Repository        │        │  Gateway -> Service ->      │
│  (datos simulados)      │        │  Repository -> PostgreSQL   │
│                         │        │  (datos REALES)             │
└─────────────────────────┘        └─────────────────────────────┘

Velocidad: Rapida (~5 seg)         Velocidad: Lenta (~30 seg)
Aislamiento: Total                 Aislamiento: Ninguno
Que detecta: Errores de logica     Que detecta: Errores de integracion
                                   (configs, queries, permisos)
```

---

## Como ejecutar las pruebas Karate

```bash
# Requiere que los servicios esten corriendo
docker-compose up -d

# Ejecutar desde Maven
mvn test -pl auth-service -Dtest=KarateRunnerTest
mvn test -pl budget-service -Dtest=KarateRunnerTest
```

**Nota:** Las pruebas Karate necesitan el servidor levantado. Las unitarias (Mockito) no.

---

## Como explicar en la presentacion

> "Las pruebas de Karate son pruebas de integracion escritas en formato BDD. A diferencia de las unitarias que usan mocks, Karate hace requests HTTP reales contra el servidor completo, incluyendo base de datos. Esto nos permite detectar errores que las unitarias no ven, como problemas de configuracion de Spring, queries SQL incorrectas o errores en el JWT. Los archivos .feature son tan legibles que sirven como documentacion del API."
