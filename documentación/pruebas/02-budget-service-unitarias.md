# Budget Service - Pruebas Unitarias

## Cobertura: 91.3% (JaCoCo)
## Total: 180 tests en 16 suites

---

## 1. CategoryServiceImplTest (23 tests)
**Clase bajo prueba:** `CategoryServiceImpl` - Categorias con estructura de arbol.

### Casos Felices
| Test | Que verifica |
|------|-------------|
| `shouldCreateRootCategory` | Crea categoria raiz (parentId = null) |
| `shouldCreateChildCategory` | Crea subcategoria con parentId valido |
| `shouldUpdateCategory` | Actualiza nombre, tipo, icono, color |
| `shouldReturnWhenFound` | getById retorna categoria existente |
| `shouldReturnPaginated` | getAll retorna lista paginada |
| `shouldReturnTree` | getTree retorna arbol jerarquico con hijos anidados |
| `shouldSoftDelete` | Borrado logico (active = false) |

### Casos de Error
| Test | Que verifica |
|------|-------------|
| `shouldFailWhenParentNotFound` | ParentId inexistente retorna error 201 |
| `shouldReturnErrorWhenNotFound` | ID inexistente retorna error 204 |

### Casos Borde
| Test | Que verifica |
|------|-------------|
| `shouldReturnEmptyTree` | Sin categorias retorna lista vacia |

### Validaciones (Reglas de Negocio)
| Test | Que verifica |
|------|-------------|
| `shouldFailOnDuplicateName` | Nombre duplicado en el mismo nivel retorna error 203 |
| `shouldFailWhenMaxDepthExceeded` | Profundidad > 3 niveles retorna error 202 |
| `shouldFailWhenSelfParent` | Categoria no puede ser su propio padre (error 205) |
| `shouldFailWhenHasChildren` | No se puede eliminar si tiene hijos (error 206) |
| `shouldReturnTypes` | type-list retorna INCOME y EXPENSE |

**Regla clave:** La estructura es un arbol con maximo 3 niveles: `Raiz -> Hijo -> Nieto`. No se permite un cuarto nivel.

---

## 2. MovementServiceImplTest (21 tests)
**Clase bajo prueba:** `MovementServiceImpl` - Movimientos financieros (ingresos/gastos).

### Casos Felices
| Test | Que verifica |
|------|-------------|
| `shouldCreateMovement` | Crea movimiento con confirmed=true por defecto |
| `shouldCreateMovementUnconfirmed` | Crea movimiento pendiente (confirmed=false) |
| `shouldUpdateMovement` | Actualiza movimiento existente |
| `shouldReturnWhenFound` | getById retorna movimiento |
| `shouldReturnPaginated` | getAll retorna lista paginada |
| `shouldSoftDelete` | Borrado logico |
| `shouldConfirmMovement` | Marca movimiento como confirmado |
| `shouldReturnMovementsForPeriod` | Filtra por rango de fechas |

### Casos de Error
| Test | Que verifica |
|------|-------------|
| `shouldFailWhenCategoryNotFound` | Categoria inexistente al crear (error 204) |
| `shouldFailWhenNotFound` | Movimiento inexistente al actualizar (error 301) |
| `shouldFailWhenCategoryNotFoundForUpdate` | Categoria inexistente al actualizar (error 204) |
| Error en getById, delete y confirm | Retornan error 301 para IDs inexistentes |

### Casos Borde
| Test | Que verifica |
|------|-------------|
| `shouldReturnEmptyList` | getAll sin resultados retorna lista vacia |
| `shouldReturnEmptyForPeriod` | Periodo sin movimientos retorna lista vacia |

---

## 3. ScheduledMovementServiceImplTest (28 tests)
**Clase bajo prueba:** `ScheduledMovementServiceImpl` - Movimientos programados (presupuestos).

### Casos Felices
| Test | Que verifica |
|------|-------------|
| `shouldCreateScheduled` | Crea movimiento recurrente |
| `shouldCreateWithDuration` | Calcula endDate a partir de durationMonths |
| `shouldUpdateScheduled` | Actualiza movimiento programado |
| `shouldReturnWhenFound` | getById retorna programado |
| `shouldReturnPaginated` | getAll retorna lista paginada |
| `shouldSoftDelete` | Borrado logico |
| `shouldGeneratePendingMonthly` | Genera movimiento pendiente para frecuencia mensual |
| `shouldGenerateForQuarterly` | Genera para frecuencia trimestral |

### Casos de Error
| Test | Que verifica |
|------|-------------|
| `shouldFailWhenCategoryNotFound` | Categoria inexistente (error 204) |
| `shouldFailWhenNotFound` | Programado inexistente (error 401) |

### Casos Borde
| Test | Que verifica |
|------|-------------|
| `shouldCreateWithoutDuration` | Sin duracion = indefinido (endDate = null) |
| `shouldReturnEmptyList` | Lista vacia |
| `shouldReturnEmptyWhenNoScheduled` | Sin programados activos |

### Validaciones (Logica de Generacion)
| Test | Que verifica |
|------|-------------|
| `shouldNotGenerateWhenAlreadyExists` | No genera duplicados (idempotente) |
| `shouldNotGenerateForExpired` | No genera para programados vencidos |
| `shouldNotGenerateForFuture` | No genera si startDate aun no llega |
| `shouldReturnFrequencies` | Retorna las 7 frecuencias: DAILY, WEEKLY, BIWEEKLY, MONTHLY, BIMONTHLY, QUARTERLY, SEMIANNUAL, ANNUAL |

**Regla clave:** La generacion de pendientes es **idempotente**: si ya existe un movimiento pendiente para ese programado en ese periodo, no crea otro.

---

## 4. DashboardServiceImplTest (25 tests)
**Clase bajo prueba:** `DashboardServiceImpl` - Calculos del dashboard financiero.

### Casos Felices
| Test | Que verifica |
|------|-------------|
| `shouldReturnSummaryWithData` | Calcula budgetTotal, confirmedTotal, balance correctamente |
| `shouldGroupByCategory` | Agrupa movimientos por categoria con totales |
| `shouldCalculatePositiveTrend` | Calcula % de mejora vs periodo anterior |
| `shouldCalculateNegativeTrend` | Calcula % de empeoramiento |
| `shouldReturnPendingMovements` | Retorna movimientos sin confirmar |

### Casos Borde
| Test | Que verifica |
|------|-------------|
| `shouldReturnSummaryWithNoData` | Sin movimientos retorna todo en cero |
| `shouldReturnEmptyWhenNoMovements` | By-category sin datos retorna lista vacia |
| `shouldHandleZeroPreviousPeriod` | Sin periodo anterior retorna 0% de tendencia |
| `shouldReturnEmptyWhenNoPending` | Sin pendientes retorna lista vacia |

### Validaciones
| Test | Que verifica |
|------|-------------|
| `shouldReturnSummaryWithCustomStartDay` | Respeta el dia de inicio personalizado del usuario |
| `shouldReturnTrendPeriodDates` | Las fechas de periodo estan correctas en el trend |

---

## 5. PeriodUtilsTest (18 tests)
**Clase bajo prueba:** `PeriodUtils` - Calculo de periodos presupuestales.

### Casos Felices
| Test | Que verifica |
|------|-------------|
| `shouldReturnStartForDay1` | Dia 1: periodo inicia el 1 del mes |
| `shouldReturnStartForDay15AfterStartDay` | Dia 15: si hoy > 15, retorna dia 15 del mes actual |
| `shouldReturnEndOfPeriod` | Fin de periodo = dia antes del inicio del siguiente |

### Casos Borde (Manejo de meses cortos)
| Test | Que verifica |
|------|-------------|
| `shouldAdjustDay31ToFeb28` | Dia 31 en febrero no bisiesto -> usa dia 28 |
| `shouldAdjustDay31ToFeb29LeapYear` | Dia 31 en febrero bisiesto -> usa dia 29 |
| `shouldUseDay30InApril` | Dia 31 en abril (30 dias) -> usa dia 30 |
| `shouldUseDay31InJanuary` | Dia 31 en enero (31 dias) -> usa dia 31 |
| `shouldHandleYearBoundary` | Diciembre -> Enero cruza correctamente el anio |
| `shouldReturnStartForDay15OnStartDay` | Exactamente en el dia de inicio |
| `shouldReturnPreviousMonthWhenBeforeStartDay` | Si hoy < dia inicio, retorna mes anterior |

**Regla clave:** Si el usuario configura dia 31 y el mes tiene menos dias, se usa el ultimo dia del mes. Ejemplo: `budgetStartDay=31` en febrero 2026 -> se usa dia 28.

```
| budgetStartDay | Feb (no bisiesto) | Feb (bisiesto) | Abril | Enero |
|----------------|-------------------|----------------|-------|-------|
| 28             | 28                | 28             | 28    | 28    |
| 31             | 28                | 29             | 30    | 31    |
```

---

## 6. Mappers (8 tests total)

### CategoryMapperImplTest (4 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| `shouldMapCategoryToResultDTO` | Caso feliz | Entity -> DTO con todos los campos |
| `shouldReturnNullWhenCategoryIsNull` | Caso borde | null -> null (no explota) |
| `shouldMapCategoryToTreeDTO` | Caso feliz | Entity -> TreeDTO con lista de hijos vacia |
| `shouldReturnNullWhenCategoryIsNull` (tree) | Caso borde | null -> null |

### MovementMapperImplTest (2 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| `shouldMapMovementToResultDTO` | Caso feliz | Movement -> ResultMovementDTO |
| `shouldReturnNullWhenMovementIsNull` | Caso borde | null -> null |

### ScheduledMovementMapperImplTest (2 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| `shouldMapScheduledMovementToResultDTO` | Caso feliz | ScheduledMovement -> ResultDTO |
| `shouldReturnNullWhenScheduledMovementIsNull` | Caso borde | null -> null |

---

## 7. DTOTest (22 tests)
Verifica que todos los DTOs (constructores, getters, setters, builders) funcionan.

Clases verificadas:
- `ResultDTO` (3 constructores)
- `DashboardRequestDTO`, `PeriodRequestDTO`, `GeneratePendingRequestDTO`
- `CategorySaveDTO`, `MovementSaveDTO`, `ScheduledMovementSaveDTO`
- `MovementFilterDTO`, `ScheduledMovementFilterDTO`, `CategoryFilterDTO`
- `DashboardSummaryDTO` (incluyendo clases anidadas: `CategorySummary`, `TrendDTO`)
- `ResultCategoryDTO`, `ResultMovementDTO`, `ResultScheduledMovementDTO`, `ResultCategoryTreeDTO`
- Entities con `@Builder`: Category, Movement, ScheduledMovement
- Enums: `Frequency` (7 valores), `CategoryType` (2 valores)

---

## 8. SecurityUtilsTest (2 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| `shouldReturnAuthenticatedEmail` | Caso feliz | Extrae email del SecurityContext |
| `shouldThrowWhenNoAuth` | Caso error | Sin autenticacion lanza NullPointerException |

---

## 9. LoadDataTest (3 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| `shouldSeedCategoriesWhenEmpty` | Caso feliz | Crea 25+ categorias semilla (Salario, Vivienda, etc.) |
| `shouldSkipWhenCategoriesExist` | Caso borde | No duplica si ya hay datos |
| `shouldSkipWhenDisabled` | Validacion | Respeta flag de configuracion |

---

## Como explicar en la presentacion

> "El budget-service es el servicio mas complejo del sistema. Sus 180 tests cubren la logica de categorias jerarquicas (arbol de 3 niveles), movimientos financieros con borrado logico, movimientos programados con generacion idempotente de pendientes, y un dashboard con calculos de tendencias. Los tests de PeriodUtils son un ejemplo claro de **pruebas de borde**: verificamos que el sistema maneja correctamente dias como el 31 en meses con menos dias, anios bisiestos, y cruces de anio."
