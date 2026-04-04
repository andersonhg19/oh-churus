# Oh Churus! - Modelo de Entidades y Relaciones

## Diagrama de Relaciones

```
┌──────────────────────────────┐
│    AUTH SERVICE (auth_db)    │
│                              │
│  ┌──────────┐               │
│  │   User   │               │
│  └──────────┘               │
│       │ userId (ref. lógica) │
└───────┼──────────────────────┘
        │
┌───────▼──────────────────────────────────────┐
│          BUDGET SERVICE (budget_db)           │
│                                               │
│  ┌──────────────┐     ┌───────────────┐      │
│  │   Category   │──┐  │   Movement    │      │
│  │              │<─┘  └───────┬───────┘      │
│  └──────┬───────┘             │ scheduledId  │
│         │ categoryId   ┌──────▼──────────┐   │
│         └─────────────>│ScheduledMovement│   │
│                        └─────────────────┘   │
└──────────────────────────────────────────────┘
```

---

## Entidades (4 en total)

### 1. User (Auth Service)

```
Tabla: oc_auth_user
Base de datos: auth_db

┌──────────────────┬──────────────────┬────────────────────────────┐
│ Campo            │ Tipo             │ Restricciones              │
├──────────────────┼──────────────────┼────────────────────────────┤
│ id               │ Long (AUTO)      │ PK                         │
│ name             │ String(100)      │ NOT NULL                   │
│ email            │ String(150)      │ NOT NULL, UNIQUE           │
│ password         │ String(255)      │ NOT NULL (BCrypt)          │
│ budgetStartDay   │ Integer          │ DEFAULT 1, CHECK(1-31)     │
│ active           │ Boolean          │ DEFAULT true               │
│ createdAt        │ LocalDateTime    │ @CreationTimestamp          │
│ updatedAt        │ LocalDateTime    │ @UpdateTimestamp            │
└──────────────────┴──────────────────┴────────────────────────────┘

Índices:
  - UNIQUE(email) WHERE active = true

Nota: currency y decimalPlaces son globales via application.properties.
Ver puntos-futuros.md para personalización por usuario.

### Regla de negocio: budgetStartDay y meses cortos

El campo budgetStartDay permite valores de 1 a 31. Cuando el día configurado
NO existe en un mes dado, el sistema usa el ÚLTIMO día del mes:

| budgetStartDay | Febrero (no bisiesto) | Febrero (bisiesto) | Abril (30 días) | Enero (31 días) |
|----------------|----------------------|--------------------|-----------------:|----------------:|
| 28             | 28                   | 28                 | 28               | 28              |
| 29             | **28**               | 29                 | 29               | 29              |
| 30             | **28**               | **29**             | 30               | 30              |
| 31             | **28**               | **29**             | **30**           | 31              |

Implementación en Java:
```java
LocalDate actualStartDate(int year, int month, int budgetStartDay) {
    YearMonth ym = YearMonth.of(year, month);
    int day = Math.min(budgetStartDay, ym.lengthOfMonth());
    return ym.atDay(day);
}
```
```

### 2. Category (Budget Service)

```
Tabla: oc_budget_category
Base de datos: budget_db

┌──────────────────┬───────────────────┬────────────────────────────┐
│ Campo            │ Tipo              │ Restricciones              │
├──────────────────┼───────────────────┼────────────────────────────┤
│ id               │ Long (AUTO)       │ PK                         │
│ userId           │ Long              │ NOT NULL                   │
│ name             │ String(100)       │ NOT NULL                   │
│ description      │ String(255)       │ NULLABLE                   │
│ parentId         │ Long              │ NULLABLE, FK -> self(id)   │
│ icon             │ String(50)        │ NULLABLE                   │
│ color            │ String(7)         │ NULLABLE (#HEX)            │
│ type             │ Enum(CategoryType)│ NOT NULL (INCOME/EXPENSE)  │
│ active           │ Boolean           │ DEFAULT true               │
│ createdAt        │ LocalDateTime     │ @CreationTimestamp          │
│ updatedAt        │ LocalDateTime     │ @UpdateTimestamp            │
└──────────────────┴───────────────────┴────────────────────────────┘

Índices:
  - INDEX(userId, active)
  - INDEX(userId, parentId, active)

Relación: Self-referencing (parentId -> Category.id) para estructura árbol.
Máximo 3 niveles de profundidad.

Enum CategoryType: INCOME, EXPENSE
```

### 3. Movement (Budget Service)

```
Tabla: oc_budget_movement
Base de datos: budget_db

┌──────────────────────┬──────────────────┬─────────────────────────┐
│ Campo                │ Tipo             │ Restricciones           │
├──────────────────────┼──────────────────┼─────────────────────────┤
│ id                   │ Long (AUTO)      │ PK                      │
│ userId               │ Long             │ NOT NULL                │
│ categoryId           │ Long             │ NOT NULL                │
│ date                 │ LocalDate        │ NOT NULL                │
│ amount               │ BigDecimal(15,2) │ NOT NULL, > 0           │
│ description          │ String(255)      │ NULLABLE                │
│ scheduledMovementId  │ Long             │ NULLABLE                │
│ confirmed            │ Boolean          │ DEFAULT true            │
│ active               │ Boolean          │ DEFAULT true            │
│ createdAt            │ LocalDateTime    │ @CreationTimestamp       │
│ updatedAt            │ LocalDateTime    │ @UpdateTimestamp         │
└──────────────────────┴──────────────────┴─────────────────────────┘

Índices:
  - INDEX(userId, date, active)
  - INDEX(userId, confirmed, active)

Notas:
  - amount siempre positivo; ingreso/gasto se determina por el type de la categoría
  - confirmed = false -> pendiente generado automáticamente desde un ScheduledMovement
```

### 4. ScheduledMovement (Budget Service)

```
Tabla: oc_budget_scheduled_movement
Base de datos: budget_db

┌──────────────────────┬──────────────────┬─────────────────────────┐
│ Campo                │ Tipo             │ Restricciones           │
├──────────────────────┼──────────────────┼─────────────────────────┤
│ id                   │ Long (AUTO)      │ PK                      │
│ userId               │ Long             │ NOT NULL                │
│ categoryId           │ Long             │ NOT NULL                │
│ name                 │ String(100)      │ NOT NULL                │
│ amount               │ BigDecimal(15,2) │ NULLABLE (variable)     │
│ frequency            │ Enum(Frequency)  │ NOT NULL                │
│ durationMonths       │ Integer          │ NULLABLE (null=indef.)  │
│ startDate            │ LocalDate        │ NOT NULL                │
│ endDate              │ LocalDate        │ NULLABLE (calculado)    │
│ dayOfMonth           │ Integer          │ NULLABLE, CHECK(1-28)   │
│ active               │ Boolean          │ DEFAULT true            │
│ createdAt            │ LocalDateTime    │ @CreationTimestamp       │
│ updatedAt            │ LocalDateTime    │ @UpdateTimestamp         │
└──────────────────────┴──────────────────┴─────────────────────────┘

Índices:
  - INDEX(userId, active)

Enum Frequency: WEEKLY, BIWEEKLY, MONTHLY, BIMONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL
```

---

## Relaciones

Category, Movement y ScheduledMovement viven en el MISMO servicio (budget-service).
Solo userId es referencia cross-service hacia auth-service.

```
Auth Service (auth_db)        Budget Service (budget_db)
┌──────┐                     ┌──────────────────────────────┐
│ User │──userId────────────>│ Category (árbol, FK directa) │
│ (id) │──userId────────────>│ Movement (FK a Category)     │
└──────┘                     │ ScheduledMovement (FK a Cat.) │
                             └──────────────────────────────┘
```

---

## Convenciones de Nombrado

| Elemento | Convención | Ejemplo |
|----------|-----------|---------|
| Tablas | `oc_[servicio]_[entidad]` | `oc_auth_user` |
| Campos Java | camelCase | `budgetStartDay` |
| Entity | PascalCase | `ScheduledMovement` |
| DTO Input | `[Entity]SaveDTO` | `MovementSaveDTO` |
| DTO Filter | `[Entity]FilterDTO` | `MovementFilterDTO` |
| DTO Output | `Result[Entity]DTO` | `ResultMovementDTO` |
| Repository | `[Entity]Repository` | `MovementRepository` |
| Service | `[Entity]Service` / `Impl` | `MovementServiceImpl` |
| Controller | `[Entity]Controller` | `MovementController` |

---

## Scripts de Inicialización

```sql
-- init-databases.sql
CREATE DATABASE auth_db;
CREATE DATABASE budget_db;
```

Tablas creadas automáticamente por JPA/Hibernate (`ddl-auto=update`).
