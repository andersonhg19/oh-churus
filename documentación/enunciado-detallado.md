# Oh Churus! - Enunciado Detallado del Proyecto

## 1. Visión General

**Oh Churus!** es un sistema de control de finanzas personales con énfasis en presupuestos mensuales. La metáfora central del producto es una ardilla que ahorra para su casita: cada movimiento financiero, categoría y presupuesto se presenta de forma visual y lúdica, como si la ardilla estuviera recolectando y organizando sus recursos.

**Objetivo académico:** Proyecto universitario de alta calidad que demuestre dominio completo del ciclo de desarrollo de software: backend con microservicios, frontend híbrido, base de datos relacional, pruebas automatizadas, contenedorización y CI/CD básico.

---

## 2. Stack Tecnológico

| Capa | Tecnología | Detalles |
|------|-----------|----------|
| **Backend** | Java 17, Spring Boot 3.2.x, Spring Cloud 2023.x | Arquitectura de microservicios |
| **Frontend** | React Native (Expo) | Híbrido web/móvil, modo claro/oscuro |
| **Base de datos** | PostgreSQL 14+ | Una BD por microservicio |
| **Contenedores** | Docker, Docker Compose | Orquestación local |
| **Pruebas Backend** | JUnit 5, Mockito, Karate Framework | Unitarias, integración, E2E API |
| **Pruebas Frontend** | Jest, React Testing Library | Unitarias y de componentes |
| **Service Discovery** | Eureka (Spring Cloud Netflix) | Registro y descubrimiento |
| **API Gateway** | Spring Cloud Gateway | Enrutamiento centralizado |
| **Autenticación** | JWT (java-jwt) | Tokens con expiración configurable |
| **Mapeo** | MapStruct + ModelMapper | Según conveniencia por servicio |
| **Documentación API** | SpringDoc OpenAPI (Swagger) | Auto-generada |

---

## 3. Arquitectura de Microservicios

```
                    ┌─────────────────┐
                    │   Frontend      │
                    │  React Native   │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  Gateway Service │ (8820)
                    │  Spring Cloud    │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │ Discovery Service│ (8760)
                    │    Eureka        │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │                             │
      ┌───────▼───────┐           ┌─────────▼─────────┐
      │  Auth Service  │           │  Budget Service    │
      │   (8821)       │           │   (8823)           │
      │  auth_db       │           │  budget_db         │
      └────────────────┘           │  (categorías,      │
                                   │   movimientos,     │
                                   │   programados,     │
                                   │   dashboard)       │
                                   └────────────────────┘
```

### 3.1 Servicios y Puertos

| Puerto | Servicio | Base de Datos | Responsabilidad |
|--------|----------|---------------|-----------------|
| 8760 | `discovery-service` | - | Registro Eureka |
| 8820 | `gateway-service` | - | API Gateway, CORS, enrutamiento |
| 8821 | `auth-service` | `auth_db` | Usuarios, autenticación JWT |
| 8823 | `budget-service` | `budget_db` | Categorías, movimientos, movimientos programados, dashboard |

### 3.2 Context Path

Todos los servicios usan: `/oh-churus`

### 3.3 Versionado API

Todos los endpoints bajo: `/oh-churus/v1/{recurso}`

---

## 4. Entidades Detalladas

### 4.1 Auth Service (`auth_db`)

#### User (Tabla: `oc_auth_user`)
| Campo | Tipo | Restricciones | Descripción |
|-------|------|---------------|-------------|
| id | Long (auto) | PK | Identificador único |
| name | String(100) | NOT NULL | Nombre completo |
| email | String(150) | NOT NULL, UNIQUE | Correo electrónico (login) |
| password | String(255) | NOT NULL | Contraseña encriptada (BCrypt) |
| budgetStartDay | Integer | DEFAULT 1, CHECK(1-31) | Día del mes en que inicia el ciclo presupuestal |
| active | Boolean | DEFAULT true | Borrado lógico |
| createdAt | LocalDateTime | AUTO | Fecha de creación |
| updatedAt | LocalDateTime | AUTO | Última actualización |

> **IMPORTANTE - Control de día en meses cortos:** Si el usuario configura día 31 y el mes tiene 30 días (o 28/29 en febrero), el sistema usa automáticamente el último día del mes. Ejemplo: día 31 en abril -> se usa el 30.

> **Configuraciones globales** (en `application.properties`):
> - `app.currency=COP` (moneda)
> - `app.decimal-places=0` (decimales a mostrar)
> Personalización por usuario de currency/decimales se implementará en fase futura.

> **Nota:** Roles (Role, UserRole) se implementarán en una fase futura. Ver `documentación/puntos-futuros.md`.

### 4.2 Core Service (`core_db`)

#### Category (Tabla: `oc_core_category`)
| Campo | Tipo | Restricciones | Descripción |
|-------|------|---------------|-------------|
| id | Long (auto) | PK | Identificador único |
| userId | Long | NOT NULL | Dueño de la categoría |
| name | String(100) | NOT NULL | Nombre de la categoría |
| description | String(255) | NULLABLE | Descripción opcional |
| parentId | Long | NULLABLE, FK -> Category | Categoría padre (null = raíz) |
| icon | String(50) | NULLABLE | Nombre del ícono (frontend) |
| color | String(7) | NULLABLE | Color hex (#FF5733) |
| type | Enum | NOT NULL | INCOME / EXPENSE |
| orderIndex | Integer | DEFAULT 0 | Orden de visualización |
| active | Boolean | DEFAULT true | Borrado lógico |
| createdAt | LocalDateTime | AUTO | Fecha de creación |
| updatedAt | LocalDateTime | AUTO | Última actualización |

**Regla de negocio:** La estructura es tipo árbol. Una categoría con `parentId = null` es categoría raíz. Las subcategorías apuntan al `id` de su padre. Máximo 3 niveles de profundidad.

> **Nota:** AppConfiguration (tabla clave-valor para configs globales) se implementará en una fase futura. Por ahora las preferencias van en el User (budgetStartDay, currency, decimalPlaces) y en `application.properties`. Ver `documentación/puntos-futuros.md`.

### 4.3 Budget Service (`budget_db`)

#### Movement (Tabla: `oc_budget_movement`)
| Campo | Tipo | Restricciones | Descripción |
|-------|------|---------------|-------------|
| id | Long (auto) | PK | Identificador único |
| userId | Long | NOT NULL | Usuario dueño |
| categoryId | Long | NOT NULL | Categoría del movimiento |
| date | LocalDate | NOT NULL | Fecha del movimiento |
| amount | BigDecimal(15,2) | NOT NULL | Monto (positivo=ingreso, negativo=gasto según tipo categoría) |
| description | String(255) | NULLABLE | Descripción opcional |
| scheduledMovementId | Long | NULLABLE | Referencia al mov. programado que lo originó |
| confirmed | Boolean | DEFAULT true | Si es confirmado o pendiente |
| active | Boolean | DEFAULT true | Borrado lógico |
| createdAt | LocalDateTime | AUTO | Fecha de creación |
| updatedAt | LocalDateTime | AUTO | Última actualización |

#### ScheduledMovement (Tabla: `oc_budget_scheduled_movement`)
| Campo | Tipo | Restricciones | Descripción |
|-------|------|---------------|-------------|
| id | Long (auto) | PK | Identificador único |
| userId | Long | NOT NULL | Usuario dueño |
| categoryId | Long | NOT NULL | Categoría asociada |
| name | String(100) | NOT NULL | Nombre descriptivo (ej: "Arriendo", "Netflix") |
| amount | BigDecimal(15,2) | NULLABLE | Monto estimado (null = variable) |
| frequency | Enum | NOT NULL | WEEKLY, BIWEEKLY, MONTHLY, BIMONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL |
| durationMonths | Integer | NULLABLE | Duración en meses (null = indefinido) |
| startDate | LocalDate | NOT NULL | Fecha de inicio |
| endDate | LocalDate | NULLABLE | Fecha de fin calculada (o null si indefinido) |
| dayOfMonth | Integer | NULLABLE | Día específico del mes para generar (1-28) |
| active | Boolean | DEFAULT true | Borrado lógico |
| createdAt | LocalDateTime | AUTO | Fecha de creación |
| updatedAt | LocalDateTime | AUTO | Última actualización |

**Regla de negocio:** Al inicio del período (según `budgetStartDay` del usuario), el sistema genera automáticamente movimientos `confirmed = false` a partir de los `ScheduledMovement` activos. El usuario puede confirmarlos (marcarlos como realizados) o descartarlos.

---

## 5. Reglas de Negocio Clave

### 5.1 Ciclo de Presupuesto
1. Cada usuario tiene `budgetStartDay` (1-31) que define el día en que inicia su ciclo presupuestal.
2. **Control de meses cortos:** Si `budgetStartDay` > días del mes, se usa el último día del mes. Ejemplo: usuario con día 31, en febrero no bisiesto -> se usa día 28.
3. Al consultar el dashboard, el sistema calcula el período actual: desde el día ajustado del mes actual hasta el día ajustado del mes siguiente.
3. Los movimientos programados activos generan "pendientes" (movimientos no confirmados) al inicio de cada período.

### 5.2 Dashboard - Datos
- **Presupuesto tentativo:** Suma de montos de movimientos programados activos para el período.
- **Gastos confirmados:** Suma de movimientos con `confirmed = true` en el período actual.
- **Pendientes por confirmar:** Lista de movimientos con `confirmed = false` en el período actual.
- **Balance:** Ingresos confirmados - Gastos confirmados.
- **Tendencia:** Comparación con el período anterior (% de cambio).

### 5.3 Categorías
- Estructura jerárquica tipo árbol (máximo 3 niveles).
- Cada usuario gestiona sus propias categorías.
- Se proveen categorías semilla por defecto al crear un usuario.
- Tipos: INCOME (ingresos) y EXPENSE (gastos).

### 5.4 Borrado Lógico
- Todas las entidades usan campo `active`.
- Al "eliminar", se cambia `active = false`.
- Las consultas por defecto solo traen registros con `active = true`.

### 5.5 Modo Offline (Consideración)
- El MVP funciona 100% online (requiere conexión al backend).
- Fase futura: implementar caché local con sincronización.

---

## 6. Interfaz de Usuario (Frontend)

### 6.1 Tema y Estética
- **Mascota:** Ardilla (personaje principal de la app).
- **Logo:** Silueta de ardilla con forma inspirada en orejas de Mickey Mouse.
- **Paleta:** Tonos cálidos (marrones, naranjas, verdes bosque) para modo oscuro; tonos claros (beige, crema, verde menta) para modo claro.
- **Modo por defecto:** Oscuro.
- **Estilo:** Minimalista pero con toques de caricatura/ilustración en el dashboard.
- **Widgets:** Diseños que evocan al mundo de la ardilla (bellotas = ahorros, árbol = categorías, nido = presupuesto).

### 6.2 Pantallas Principales
1. **Login / Registro** - Pantalla con la ardilla como personaje central.
2. **Dashboard** - Vista principal con widgets estilo caricatura:
   - Widget de balance (ardilla feliz/triste según estado).
   - Widget de presupuesto tentativo vs real.
   - Widget de pendientes por confirmar.
   - Gráfico de distribución por categorías.
3. **Movimientos** - Lista con filtros, búsqueda, y acciones rápidas.
4. **Categorías** - Vista de árbol interactiva con íconos y colores.
5. **Presupuestos** - Gestión de movimientos programados.
6. **Perfil / Configuración** - Datos del usuario, toggle modo oscuro/claro, cerrar sesión.

### 6.3 Patrones de Arquitectura Frontend
- **Atomic Design:** Átomos, moléculas, organismos, templates, páginas.
- **State Management:** Context API + useReducer (o Zustand para mayor escala).
- **Navegación:** React Navigation (stack + bottom tabs).
- **Tema:** Sistema de tema con provider (dark/light).
- **API Layer:** Servicio centralizado con Axios, interceptores para JWT.

---

## 7. Data Semilla (Seed Data)

### 7.1 Usuarios por defecto
- admin@ohchurus.com / Admin123!
- demo@ohchurus.com / Demo123!

### 7.3 Categorías por defecto (para usuario demo)

**Ingresos:**
- Salario
  - Salario principal
  - Freelance
- Inversiones
  - Dividendos
  - Intereses

**Gastos:**
- Vivienda
  - Arriendo/Hipoteca
  - Servicios públicos
  - Mantenimiento
- Alimentación
  - Mercado
  - Restaurantes
- Transporte
  - Combustible
  - Transporte público
  - Mantenimiento vehículo
- Entretenimiento
  - Suscripciones
  - Salidas
- Educación
  - Matrícula
  - Materiales
- Salud
  - Medicamentos
  - Consultas

### 7.4 Movimientos programados de ejemplo (usuario demo)
- Arriendo: $1,500,000 COP, mensual, indefinido
- Netflix: $33,900 COP, mensual, indefinido
- Salario: $3,500,000 COP, mensual, indefinido
- Servicios públicos: $250,000 COP, mensual, indefinido

### 7.4 Properties globales (application.properties del budget-service)
```properties
app.currency=COP
app.decimal-places=0
```

---

## 8. Consideraciones Técnicas

### 8.1 Seguridad
- Contraseñas hasheadas con BCrypt.
- JWT con expiración configurable.
- Endpoints protegidos excepto login/register.
- CORS configurado en Gateway.
- Validación de inputs en todos los endpoints.

### 8.2 Comunicación entre Servicios
- Feign Clients para llamadas síncronas (ej: budget-service consulta categorías en core-service).
- Gateway como único punto de entrada externo.

### 8.3 Pruebas
- **Unitarias (Mockito + JUnit 5):** Servicios, mappers, utilidades. Cobertura >= 80%.
- **Integración (Karate):** Flujos completos API. Todos los endpoints cubiertos.
- **Frontend (Jest):** Componentes, hooks, servicios. Cobertura >= 70%.

### 8.4 Docker
- Dockerfile por microservicio.
- docker-compose.yml para orquestación completa.
- PostgreSQL con script de inicialización de bases de datos.
- Volúmenes para persistencia de datos.

---

## 9. Entregables Finales
1. Código fuente completo (backend + frontend).
2. Colección Postman con todos los endpoints documentados.
3. Scripts de base de datos (inicialización + seed data).
4. docker-compose.yml funcional.
5. Documentación técnica.
6. Pruebas automatizadas con reporte de cobertura.
7. Manual de usuario básico.
