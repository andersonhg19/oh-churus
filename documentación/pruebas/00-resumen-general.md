# Oh Churus! - Resumen General de Pruebas

## Metricas Globales

| Componente | Suites | Tests | Cobertura | Herramientas |
|------------|--------|-------|-----------|-------------|
| auth-service | 11 | 79 | 91.9% | JUnit 5, Mockito, MockMvc, Karate |
| budget-service | 16 | 180 | 91.3% | JUnit 5, Mockito, MockMvc, Karate |
| frontend | 30 | 179 | 74.3% | Jest, React Testing Library |
| **TOTAL** | **57** | **438** | **> 70%** | |

---

## Tipos de Pruebas Implementadas

### 1. Pruebas Unitarias (JUnit + Mockito)
- **Que son:** Prueban una clase de forma aislada, reemplazando sus dependencias con objetos simulados (mocks).
- **Donde estan:** `src/test/java/.../service/`, `mapper/`, `util/`, `dto/`
- **Patron:** `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`

### 2. Pruebas de Controlador (MockMvc)
- **Que son:** Prueban los endpoints HTTP sin levantar el servidor completo. Verifican status codes, JSON de respuesta y validaciones de entrada.
- **Donde estan:** `src/test/java/.../controller/`
- **Patron:** `@WebMvcTest` + `MockMvc` + `mockMvc.perform(post(...))`

### 3. Pruebas de Integracion (Karate)
- **Que son:** Prueban flujos completos end-to-end contra el servidor real. Escritas en formato BDD (Given/When/Then).
- **Donde estan:** `src/test/java/.../karate/*.feature`
- **Patron:** Archivos `.feature` con sintaxis Gherkin ejecutados por `KarateRunnerTest`

### 4. Pruebas de Aceptacion
- **Que son:** Validan que el sistema cumple con los criterios de aceptacion del negocio.
- **Implementadas como:** Escenarios Karate que verifican los flujos de negocio completos (login -> crear categoria -> crear movimiento -> ver dashboard).

### 5. Pruebas de Frontend (Jest + React Testing Library)
- **Que son:** Prueban componentes React Native de forma aislada, verificando renderizado, interacciones y logica.
- **Donde estan:** `frontend/src/**/__tests__/`
- **Patron:** `render()` + `fireEvent` + `waitFor` + mocks de servicios API

---

## Clasificacion de Casos de Prueba

| Tipo | Descripcion | Ejemplo |
|------|------------|---------|
| **Caso Feliz** | Flujo normal esperado | Crear usuario exitosamente |
| **Caso de Error** | Situacion de fallo controlado | Usuario no encontrado (404) |
| **Caso Borde** | Limites y condiciones extremas | Dia 31 en febrero, lista vacia |
| **Validacion** | Verificar reglas de negocio | Email duplicado, profundidad maxima 3 |

---

## Herramientas Utilizadas

| Herramienta | Version | Proposito |
|-------------|---------|-----------|
| JUnit 5 | 5.x | Framework base de pruebas Java |
| Mockito | 5.x | Simulacion de dependencias (mocks) |
| Spring MockMvc | 3.2.x | Pruebas de controladores HTTP |
| JaCoCo | 0.8.11 | Medicion de cobertura de codigo |
| Karate | 1.4.x | Pruebas de integracion BDD |
| Jest | 29.7 | Framework de pruebas JavaScript/TypeScript |
| React Testing Library | 12.x | Pruebas de componentes React Native |

---

## Arquitectura de Pruebas

```
tests/
├── Unitarias (Mockito)
│   ├── ServiceImpl tests     → Logica de negocio
│   ├── Mapper tests          → Transformacion de datos
│   ├── Util tests            → Funciones utilitarias
│   └── DTO tests             → Constructores y getters
│
├── Controlador (MockMvc)
│   └── Controller tests      → Endpoints HTTP + validaciones
│
├── Integracion (Karate)
│   └── *.feature files       → Flujos end-to-end BDD
│
└── Frontend (Jest)
    ├── Atoms/Molecules        → Componentes UI
    ├── Services               → Llamadas API
    ├── Contexts               → Estado global
    └── Screens                → Pantallas completas
```
