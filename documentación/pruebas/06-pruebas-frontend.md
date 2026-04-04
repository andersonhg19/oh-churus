# Pruebas Frontend (Jest + React Testing Library)

## Cobertura: 74.3% statements
## Total: 179 tests en 30 suites

---

## Herramientas

| Herramienta | Proposito |
|-------------|-----------|
| **Jest** | Framework de pruebas: ejecuta tests, assertions, mocks |
| **React Testing Library** | Renderiza componentes y simula interacciones del usuario |
| **jest-expo** | Preset de Jest para proyectos Expo/React Native |
| **AsyncStorage mock** | Simula almacenamiento local en tests |

---

## 1. Pruebas de Servicios API (27 tests)

**Que prueban:** Que cada funcion del servicio llama al endpoint correcto con los parametros correctos.

### authService.test.ts (5 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| `login posts to correct endpoint` | Caso feliz | POST /AUTH-SERVICE/.../login con email y password |
| `register posts to correct endpoint` | Caso feliz | POST /AUTH-SERVICE/.../register con datos |
| `getUser posts to correct endpoint` | Caso feliz | POST /users/get/{id} |
| `updateUser posts to save endpoint` | Caso feliz | POST /users/save con datos parciales |
| `getAllUsers posts with filter` | Caso feliz | POST /users/all con paginacion |

### categoryService.test.ts (6 tests)
save, getById, getAll, getTree, delete, typeList - cada uno verifica URL y parametros.

### movementService.test.ts (6 tests)
save, getById, getAll, delete, confirm, getByPeriod.

### dashboardService.test.ts (4 tests)
getSummary, getByCategory, getTrend, getPending - verifican userId y budgetStartDay.

### scheduledService.test.ts (6 tests)
save, getById, getAll, delete, generatePending, frequencyList.

**Patron usado:**
```typescript
jest.mock('../api', () => ({
  __esModule: true,
  default: { post: jest.fn() },  // Mock de axios.post
}));

it('login posts to correct endpoint', async () => {
  mockPost.mockResolvedValueOnce({ data: { correct: true, object: { token: 't' } } });
  const result = await authService.login('a@b.com', 'pass');
  expect(mockPost).toHaveBeenCalledWith('/AUTH-SERVICE/oh-churus/v1/auth/login',
    { email: 'a@b.com', password: 'pass' });
  expect(result.correct).toBe(true);
});
```

---

## 2. Pruebas de Contextos (11 tests)

### ThemeContext.test.tsx (3 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| Dark theme by default | Caso feliz | El tema inicia en modo oscuro |
| Toggle to light | Caso feliz | toggleTheme cambia a modo claro |
| Toggle back to dark | Caso borde | Doble toggle vuelve a oscuro |

### AuthContext.test.tsx (8 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| Starts not authenticated | Caso feliz | Estado inicial: sin usuario |
| Loads stored auth on mount | Caso feliz | Recupera token y usuario de AsyncStorage al iniciar |
| Sets budgetStartDay default to 1 | Caso borde | Si el usuario guardado no tiene budgetStartDay, usa 1 |
| Login stores token and user | Caso feliz | Login guarda en AsyncStorage y actualiza estado |
| Register stores token and user | Caso feliz | Register guarda en AsyncStorage y actualiza estado |
| Logout clears token and user | Caso feliz | Logout borra AsyncStorage y limpia estado |
| UpdateUser merges updates | Caso feliz | updateUser mezcla datos nuevos con existentes |

---

## 3. Pruebas de Componentes Atomicos (19 tests)

### Button.test.tsx (6 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| Renders title | Caso feliz | Muestra el texto del boton |
| Calls onPress | Caso feliz | Al presionar, llama la funcion |
| Shows loader when loading | Caso borde | En estado loading muestra ActivityIndicator |
| Disabled prevents onPress | Validacion | Boton deshabilitado no ejecuta onPress |
| Renders primary variant | Caso feliz | Estilo primario |
| Renders outline variant | Caso feliz | Estilo outline |

### Text.test.tsx (6 tests)
Renderiza con variantes: title, body, caption, subtitle, label.

### Input.test.tsx (4 tests)
Renderiza label, muestra errores, acepta input.

### Badge.test.tsx (2 tests)
Muestra contador, maneja tamanios (small/medium/large).

### Spinner.test.tsx (4 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| Renders without crashing | Caso feliz | Componente renderiza |
| Renders with small size | Caso feliz | Prop size="small" |
| Renders fullScreen | Caso feliz | Prop fullScreen muestra pantalla completa |
| Non-fullScreen by default | Caso borde | Sin fullScreen usa layout parcial |

---

## 4. Pruebas de Moleculas (25 tests)

### CategoryItem.test.tsx (7 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| Renders category name | Caso feliz | Muestra "Salario" |
| Renders category type | Caso feliz | Muestra "INCOME" |
| Calls onPress | Caso feliz | Al tocar, navega al formulario |
| Shows expand button with children | Caso feliz | Muestra "+" si tiene hijos |
| Shows minus when expanded | Caso feliz | Muestra "-" cuando esta expandido |
| No expand button without children | Caso borde | Sin hijos no muestra boton |
| Applies depth indentation | Validacion | Profundidad 2 tiene mas margen izquierdo |

### MovementItem.test.tsx (9 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| Renders description | Caso feliz | Muestra "Pago mensual" |
| Renders category name in caption | Caso feliz | Muestra nombre de categoria |
| Shows + sign for income | Validacion | Ingresos tienen signo "+" |
| Shows - sign for expense | Validacion | Gastos tienen signo "-" |
| Calls onPress | Caso feliz | Al tocar, navega al detalle |
| Shows confirm button for unconfirmed | Caso feliz | Pendientes muestran "Confirmar" |
| Calls onConfirm | Caso feliz | Al confirmar, ejecuta callback |
| No confirm for confirmed | Caso borde | Confirmados NO muestran boton |
| Falls back to categoryName | Caso borde | Sin descripcion, muestra nombre de categoria |

### ColorPicker.test.tsx (6 tests)
Renderiza label, placeholder, color seleccionado, toggle dropdown, boton quitar.

### StatCard.test.tsx (6 tests) - (ya existia)
### EmptyState.test.tsx (3 tests) - (ya existia)

---

## 5. Pruebas de Pantallas (61 tests)

### LoginScreen.test.tsx (5 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| Renders login form | Caso feliz | Titulo, boton, campos de email y password |
| Renders squirrel mascot | Caso feliz | Muestra emoji de ardilla |
| Shows validation errors | Validacion | Campos vacios muestran mensajes de error |
| Navigates to register | Caso feliz | Boton "Registrate" navega a registro |
| Renders tagline | Caso feliz | "Tu asistente de finanzas personales" |

### RegisterScreen.test.tsx (5 tests)
Similar: formulario, validaciones (nombre, email, password min 6 chars), navegacion.

### DashboardScreen.test.tsx (6 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| Renders greeting | Caso feliz | "Hola, Anderson!" |
| Renders stat cards | Caso feliz | Balance, Ingresos, Gastos, Pendientes |
| Renders pending section | Caso feliz | Lista de pendientes con boton Confirmar |
| Shows empty state | Caso borde | Sin pendientes muestra "Todo al dia" |
| Handles confirm action | Caso feliz | Confirmar llama a movementService.confirm |
| Shows error on failure | Caso error | Error de red muestra mensaje |

### MovementsScreen.test.tsx (6 tests)
Lista, empty state, FAB, categorias cargadas, error de carga.

### CategoriesScreen.test.tsx (6 tests)
Arbol, expand/collapse, empty state, FAB, error.

### ScheduledScreen.test.tsx (6 tests)
Lista, boton generar pendientes, empty state, error.

### MovementFormScreen.test.tsx (7 tests)
Formulario nuevo/editar, tipo ingreso/gasto, validacion sin categoria, cancelar.

### CategoryFormScreen.test.tsx (7 tests)
Formulario nuevo/editar, tipo, color picker, validacion nombre vacio, preview.

### ScheduledFormScreen.test.tsx (7 tests)
Formulario, frecuencias (8 botones), tipo, validacion campos requeridos, editar.

### ProfileScreen.test.tsx (7 tests)
Avatar, nombre, email, tema, logout, version, configuracion.

---

## 6. Pruebas de Utilidades (12 tests)

### format.test.ts (6 tests)
Formateo de moneda colombiana (COP), fechas en espanol.

### iconMap.test.ts (6 tests)
| Test | Tipo | Que verifica |
|------|------|-------------|
| Exports known keys | Caso feliz | wallet -> 👛, home -> 🏠 |
| Returns emoji for valid key | Caso feliz | getIconEmoji('wallet') -> 👛 |
| Returns default for undefined | Caso borde | getIconEmoji() -> 📁 |
| Returns fallback for unknown | Caso borde | getIconEmoji('xxx', '❓') -> ❓ |
| Returns default for unknown no fallback | Caso borde | getIconEmoji('xxx') -> 📁 |
| Returns custom fallback for undefined | Caso borde | getIconEmoji(undefined, '🔍') -> 🔍 |

---

## Como ejecutar

```bash
cd frontend

# Todos los tests
npm test

# Con cobertura
npx jest --coverage

# Un archivo especifico
npx jest src/screens/__tests__/DashboardScreen.test.tsx

# En modo watch (re-ejecuta al cambiar archivos)
npx jest --watch
```

---

## Como explicar en la presentacion

> "Las pruebas del frontend verifican que la interfaz de usuario se renderiza correctamente y responde a las interacciones del usuario. Usamos React Testing Library que promueve probar el componente como lo veria el usuario, no los detalles de implementacion. Probamos desde los componentes mas pequenos (atomos como Button y Input) hasta pantallas completas (Dashboard, Login). Los servicios API se mockean para aislar el frontend del backend durante las pruebas."
