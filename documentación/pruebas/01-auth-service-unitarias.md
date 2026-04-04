# Auth Service - Pruebas Unitarias

## Cobertura: 91.9% (JaCoCo)
## Total: 79 tests en 11 suites

---

## 1. AuthenticationServiceImplTest (10 tests)
**Clase bajo prueba:** `AuthenticationServiceImpl` - Logica de login y generacion de JWT.

### Casos Felices
| Test | Que verifica |
|------|-------------|
| `shouldAuthenticateSuccessfully` | Login exitoso retorna JWT con datos del usuario (token, userId, name, email) |

### Casos de Error
| Test | Que verifica |
|------|-------------|
| `shouldThrowBadCredentialsForInvalidPassword` | Contrasena incorrecta lanza `BadCredentialsException` |
| `shouldThrowWhenUserNotFoundAfterAuth` | Si el usuario se elimina entre auth y busqueda, falla |
| `shouldThrowWhenEmailIsNull` | Email nulo lanza excepcion |
| `shouldThrowWhenPasswordIsEmpty` | Contrasena vacia lanza excepcion |

### Validaciones
| Test | Que verifica |
|------|-------------|
| `shouldIncludeClaimsInJWT` | El JWT generado tiene 3 partes (formato estandar) |
| `shouldGenerateJwtWithCorrectClaims` | El JWT contiene subject=email, claim userId y claim name |

**Como funciona:** Se usa `@Mock` para simular `AuthenticationManager`, `UserRepository` y `SecParams`. El servicio genera un JWT real que se decodifica para verificar sus claims.

---

## 2. UserServiceImplTest (23 tests)
**Clase bajo prueba:** `UserServiceImpl` - CRUD de usuarios.

### Casos Felices
| Test | Que verifica |
|------|-------------|
| `shouldCreateUserSuccessfully` | Crea usuario, encripta password con BCrypt, retorna DTO |
| `shouldUpdateUserSuccessfully` | Actualiza usuario existente conservando datos previos |
| `shouldUpdatePasswordWhenProvided` | Si se envia password en update, se encripta y guarda |
| `shouldReturnUserWhenFound` | getById retorna usuario cuando existe |
| `shouldReturnPaginatedList` | getAll retorna lista paginada con PageDTO |
| `shouldSoftDeleteUser` | Delete pone `active=false` (borrado logico) |

### Casos de Error
| Test | Que verifica |
|------|-------------|
| `shouldFailWhenEmailExists` | Email duplicado retorna error 102 |
| `shouldFailWhenUserNotFound` | Update de usuario inexistente retorna error 103 |
| `shouldFailWhenEmailTakenByAnother` | Email tomado por otro usuario retorna error |
| `shouldReturnErrorWhenNotFound` | getById de ID inexistente retorna error 103 |
| `shouldFailWhenUserNotFoundForDeletion` | Delete de usuario inexistente retorna error |

### Casos Borde
| Test | Que verifica |
|------|-------------|
| `shouldUseDefaultBudgetStartDay` | Si budgetStartDay es null, usa 1 por defecto |
| `shouldNotChangePasswordWhenNull` | En update, si password es null no lo cambia |
| `shouldKeepExistingBudgetStartDayWhenNull` | En update, conserva el dia existente si no se envia |
| `shouldReturnEmptyList` | getAll con 0 resultados retorna lista vacia (no null) |
| `shouldCreateUserWithEmailContainingSpaces` | Preserva espacios en email (no hace trim) |

### Validaciones
| Test | Que verifica |
|------|-------------|
| `shouldFailWhenPasswordMissing` | Password nulo en creacion es rechazado |
| `shouldFailWhenPasswordBlank` | Password con solo espacios es rechazado |
| `shouldAcceptBudgetStartDay31` | Acepta el dia maximo valido (31) |
| `shouldUpdateBudgetStartDayTo31` | Permite actualizar al dia maximo |
| `shouldPassNameFilterToRepository` | El filtro de nombre se pasa al repository |
| `shouldPassEmailFilterToRepository` | El filtro de email se pasa al repository |

---

## 3. JWTAuthorizationFilterTest (5 tests)
**Clase bajo prueba:** `JWTAuthorizationFilter` - Filtro que valida JWT en cada request.

### Casos Felices
| Test | Que verifica |
|------|-------------|
| `shouldPassWithoutAuthHeader` | Sin header Authorization, permite continuar (endpoints publicos) |
| `shouldAuthenticateWithValidJWT` | JWT valido establece el SecurityContext correctamente |

### Casos Borde
| Test | Que verifica |
|------|-------------|
| `shouldPassWithNonBearerHeader` | Header con valor que no es "Bearer ..." es ignorado |

### Casos de Error
| Test | Que verifica |
|------|-------------|
| `shouldClearContextWithInvalidJWT` | JWT malformado retorna 401 |
| `shouldClearContextWithExpiredJWT` | JWT expirado retorna 401 |

---

## 4. MyUserDetailsServiceTest (4 tests)
**Clase bajo prueba:** `MyUserDetailsService` - Carga usuario desde BD para Spring Security.

### Casos Felices
| Test | Que verifica |
|------|-------------|
| `shouldReturnUserDetailsForValidActiveUser` | Retorna UserDetails con email y password |
| `shouldReturnUserDetailsWithCorrectEmail` | El username en UserDetails es el email |

### Casos de Error
| Test | Que verifica |
|------|-------------|
| `shouldThrowExceptionForNonExistentEmail` | Email inexistente lanza `UsernameNotFoundException` |
| `shouldThrowExceptionForInactiveUser` | Usuario inactivo lanza excepcion |

---

## 5. UserMapperImplTest (4 tests)
**Clase bajo prueba:** `UserMapperImpl` - Convierte entre User entity y DTOs.

### Casos Felices
| Test | Que verifica |
|------|-------------|
| `shouldMapValidUserToResultDTO` | User -> ResultUserDTO mapea todos los campos |
| `shouldMapValidDTOToEntity` | ResultUserDTO -> User mapea correctamente |

### Casos Borde
| Test | Que verifica |
|------|-------------|
| `shouldReturnNullWhenUserIsNull` | Input null retorna null (no NullPointerException) |
| `shouldReturnNullWhenDTOIsNull` | Input null retorna null |

---

## 6. ValidationUtilsTest (8 tests)
**Clase bajo prueba:** `ValidationUtils` - Utilidades de validacion.

### Validaciones
| Test | Que verifica |
|------|-------------|
| `shouldCreateResultDTOWithCorrectFields` | `error()` crea ResultDTO con correct=false |
| `shouldCreateErrorWithZeroErrorCode` | Error code 0 funciona correctamente |
| `shouldReturnTrueForNull` | `isBlank(null)` retorna true |
| `shouldReturnTrueForEmptyString` | `isBlank("")` retorna true |
| `shouldReturnTrueForWhitespaceOnly` | `isBlank("   ")` retorna true |
| `shouldReturnFalseForTextWithContent` | `isBlank("texto")` retorna false |
| `shouldReturnFalseForTextWithSpaces` | `isBlank("hola mundo")` retorna false |
| Budget day validation tests | Valida rango 1-31, rechaza 0, 32, negativos, null |

---

## 7. LoadDataTest (3 tests)
**Clase bajo prueba:** `LoadData` - Seed data al iniciar la aplicacion.

| Test | Tipo | Que verifica |
|------|------|-------------|
| `shouldSeedUsersWhenEmpty` | Caso feliz | Crea 2 usuarios (admin, demo) si BD esta vacia |
| `shouldSkipWhenUsersExist` | Caso borde | No hace nada si ya hay usuarios |
| `shouldSkipWhenDisabled` | Validacion | Respeta flag `seedDataEnabled=false` |

---

## 8. DTOTest (5 tests)
**Que verifica:** Que todos los DTOs (constructores, getters, setters, builders) funcionan correctamente.

| Test | Clases verificadas |
|------|-------------------|
| `testResultDTO` | ResultDTO - 3 constructores |
| `testAuthRequest` | AuthenticationRequest |
| `testAuthResponse` | AuthenticationResponse |
| `testUserSaveDTO` | UserSaveDTO con @AllArgsConstructor |
| `testUserFilterDTO` | UserFilterDTO |
| `testUserEntity` | User con @Builder |

---

## Como explicar en la presentacion

> "Las pruebas unitarias del auth-service verifican la logica de autenticacion JWT, el CRUD de usuarios y las validaciones de entrada. Usamos Mockito para simular las dependencias (repositorio, encriptador) y probar cada capa de forma aislada. Los 79 tests cubren el 91.9% del codigo, incluyendo casos felices, de error y condiciones borde como meses cortos y campos nulos."
