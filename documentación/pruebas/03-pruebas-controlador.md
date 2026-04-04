# Pruebas de Controlador (MockMvc)

## Que son y por que son importantes

Las pruebas de controlador verifican la **capa HTTP** del sistema:
- Que los endpoints responden con el status code correcto (200, 400, 401, etc.)
- Que el JSON de respuesta tiene la estructura esperada
- Que las validaciones de entrada (`@NotNull`, `@NotBlank`) funcionan
- Que el controlador delega correctamente al servicio

**Diferencia con pruebas unitarias:** Las pruebas unitarias prueban logica de negocio aislada. Las de controlador prueban el "contrato HTTP" - como el API se comporta ante diferentes requests.

**Herramienta:** Spring MockMvc - simula requests HTTP sin levantar un servidor real.

---

## Auth Service - Controladores

### AuthenticationControllerTest (6 tests)

**Endpoints probados:** `/oh-churus/v1/auth/login`, `/oh-churus/v1/auth/register`

| Test | Tipo | Endpoint | Status | Que verifica |
|------|------|----------|--------|-------------|
| `loginSuccess` | Caso feliz | POST /login | 200 | Retorna token JWT |
| `loginFailure` | Caso error | POST /login | 401 | Credenciales invalidas |
| `registerSuccess` | Caso feliz | POST /register | 200 | Crea usuario nuevo |
| `registerMissingName` | Validacion | POST /register | 400 | @NotBlank name faltante |
| `registerInvalidEmailFormat` | Validacion | POST /register | 400 | Email mal formateado |

### UserControllerTest (9 tests)

**Endpoints probados:** `/oh-churus/v1/users/*`

| Test | Tipo | Endpoint | Status | Que verifica |
|------|------|----------|--------|-------------|
| `saveUser` | Caso feliz | POST /save | 200 | Crea/actualiza usuario |
| `getUserById` | Caso feliz | POST /get/{id} | 200 | Retorna usuario |
| `getAllUsers` | Caso feliz | POST /all | 200 | Lista paginada |
| `deleteUser` | Caso feliz | POST /delete/{id} | 200 | Borrado logico |
| `saveUserDuplicateEmail` | Caso error | POST /save | 200 | Error 102 en body |
| `getUserByIdNotFound` | Caso error | POST /get/{id} | 200 | Error 103 en body |
| `saveUserMissingEmail` | Validacion | POST /save | 400 | @NotBlank email |
| `getAllReturnsPaginatedStructure` | Validacion | POST /all | 200 | Estructura PageDTO |

---

## Budget Service - Controladores

### CategoryControllerTest (13 tests)

**Endpoints probados:** `/oh-churus/v1/categories/*`

| Test | Tipo | Endpoint | Que verifica |
|------|------|----------|-------------|
| `shouldSaveCategory` | Caso feliz | POST /save | Crea categoria |
| `shouldReturnCategoryById` | Caso feliz | POST /get/{id} | Retorna categoria |
| `shouldReturnAllCategories` | Caso feliz | POST /all | Lista paginada |
| `shouldReturnTree` | Caso feliz | POST /tree | Arbol jerarquico |
| `shouldDeleteCategory` | Caso feliz | POST /delete/{id} | Borrado logico |
| `shouldReturnTypeList` | Caso feliz | POST /type-list | Enum INCOME/EXPENSE |
| `shouldReturn400WhenUserIdMissing` | Validacion | POST /save | @NotNull userId |
| `shouldReturn400WhenNameBlank` | Validacion | POST /save | @NotBlank name |
| `shouldReturn400WhenTypeMissing` | Validacion | POST /save | @NotNull type |
| Varios *ErrorOnServiceFailure* | Caso error | Todos | Error del servicio |

### MovementControllerTest (16 tests)

**Endpoints probados:** `/oh-churus/v1/movements/*`

| Test | Tipo | Endpoint | Que verifica |
|------|------|----------|-------------|
| `shouldSaveMovement` | Caso feliz | POST /save | Crea movimiento |
| `shouldReturnMovementById` | Caso feliz | POST /get/{id} | Retorna movimiento |
| `shouldReturnAllMovements` | Caso feliz | POST /all | Lista paginada |
| `shouldDeleteMovement` | Caso feliz | POST /delete/{id} | Borrado logico |
| `shouldConfirmMovement` | Caso feliz | POST /confirm/{id} | Confirma pendiente |
| `shouldReturnByPeriod` | Caso feliz | POST /by-period | Filtra por fechas |
| `shouldReturn400WhenUserIdMissing` | Validacion | POST /save | @NotNull userId |
| `shouldReturn400WhenAmountMissing` | Validacion | POST /save | @NotNull amount |
| `shouldReturn400WhenDateMissing` | Validacion | POST /save | @NotNull date |

### ScheduledMovementControllerTest (17 tests)
Similar estructura: CRUD + generate-pending + frequency-list con validaciones de entrada.

### DashboardControllerTest (15 tests)
4 endpoints (summary, by-category, trend, pending) x (caso feliz + error + validacion userId).

---

## Patron de codigo de un test de controlador

```java
@WebMvcTest(CategoryController.class)           // Solo carga el controlador
@AutoConfigureMockMvc(addFilters = false)        // Sin filtros de seguridad
class CategoryControllerTest {

    @Autowired MockMvc mockMvc;                  // Cliente HTTP simulado
    @MockBean CategoryService categoryService;   // Servicio simulado

    @Test
    void shouldSaveCategory() throws Exception {
        // 1. Configurar mock: cuando el servicio reciba cualquier DTO, retorna exito
        when(categoryService.saveAndUpdate(any())).thenReturn(new ResultDTO(savedCategory));

        // 2. Ejecutar request HTTP
        mockMvc.perform(post("/v1/categories/save")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1, \"name\":\"Test\", \"type\":\"INCOME\"}"))
            // 3. Verificar respuesta
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.correct").value(true))
            .andExpect(jsonPath("$.object.name").value("Test"));
    }

    @Test
    void shouldReturn400WhenUserIdMissing() throws Exception {
        mockMvc.perform(post("/v1/categories/save")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\", \"type\":\"INCOME\"}"))  // Sin userId
            .andExpect(status().isBadRequest());  // 400 por @NotNull
    }
}
```

---

## Como explicar en la presentacion

> "Las pruebas de controlador verifican que nuestro API REST se comporta correctamente ante diferentes tipos de requests. Usamos Spring MockMvc para simular llamadas HTTP sin necesidad de levantar un servidor real. Probamos tres escenarios: el caso feliz (200 OK con datos correctos), las validaciones de entrada (400 Bad Request cuando faltan campos obligatorios marcados con @NotNull/@NotBlank), y los casos de error del servicio (cuando el servicio retorna un error de negocio)."
