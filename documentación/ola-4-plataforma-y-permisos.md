# Ola 4 — de aplicación a plataforma

> Escrito el 4 de septiembre de 2026, antes de tocar código.
>
> Las tres olas de agosto arreglaron lo que estaba roto. Esta no arregla: **cambia
> lo que el proyecto es**. Oh Churus deja de ser una app de finanzas con un módulo
> de ayuno pegado al lado, y pasa a ser una base sobre la que caben varias
> aplicaciones. Eso obliga a resolver primero lo que hasta hoy no existía:
> **quién es cada quien y qué puede hacer**.

---

## 1. El hueco, dicho sin rodeos

La Ola 2 cerró la identidad: el `userId` sale del token y ningún endpoint vuelve
a aceptarlo por el cuerpo. Eso responde a **"¿quién eres?"**.

Nunca se respondió a la segunda pregunta: **"¿y qué puedes hacer?"**. Hoy, en
los tres servicios, la única regla de autorización es `anyRequest().authenticated()`.
Traducido: **cualquier usuario con un token válido puede llamar a cualquier
endpoint de la aplicación.** No hay administrador, no hay roles, y `/v1/users/all`
—la lista de todos los usuarios con sus correos— la puede pedir el usuario demo.

El backlog lo tenía anotado como el punto 1 de `puntos-futuros.md` desde la fase 1,
en marzo. Seis meses después sigue igual, y ahora estorba de verdad: **no se pueden
meter varias aplicaciones aquí sin decidir quién ve cada una.**

---

## 2. Qué aprendemos de los otros proyectos

Se leyeron `dropshipping` (6 servicios más una librería común) y
`HexaQuantum/Hexaquantum-Microservice-V1` (9 servicios, con `administration-service`
y `audit-service` propios). Ambos resuelven esto; ambos tienen partes que **no**
hay que copiar.

### Lo que hacen bien, y adoptamos

| Práctica | Dónde está | Por qué la adoptamos |
|---|---|---|
| **El rol viaja en el token como claim `roles`** | `AuthenticationServiceImpl` de ambos | El servicio que autoriza no tiene que preguntarle a auth-service en cada petición. Con la identidad ya en el token (Ola 2), esto es el paso natural. |
| **Cada servicio se defiende solo** (`hasAuthority(ADMIN)` en su propio `SecurityConfig`) | `commerce`, `acquisition`, `administration`, `audit` de dropshipping | El gateway no es la única puerta. Si alguien alcanza el puerto del microservicio, la regla sigue ahí. |
| **La constante, no la cadena suelta** (`private static final String ADMIN`) | dropshipping, los seis | Un `"ADMN"` mal escrito en un `requestMatchers` no falla: **abre**. La constante convierte el error de dedo en error de compilación. |
| **Permisos por página y por acción**, no solo por rol | `PageTypeUser` de HexaQuantum: `canCreate/canRead/canUpdate/canDelete` | Es exactamente la forma de "meter varias aplicaciones aquí": un módulo por app, y el rol decide qué módulos existen para cada quien. |
| **Auditoría desacoplada por eventos y asíncrona** | `AuditLogPublisher` de dropshipping (`@EventListener` más `@Async`) | La auditoría **nunca** puede tumbar la operación que audita. |
| **Índices explícitos y retención programada en la tabla de auditoría** | `AuditLog` (seis índices) y `AuditLogCleanupScheduler` | Una tabla de auditoría sin índices y sin borrado es una bomba de disco a doce meses vista. |
| **Una librería común para lo que se repite** | `visnex-common` (JWT, `ResultDTO`, `PageDTO`) | Oh Churus tiene **tres copias** del filtro JWT y **tres** de `ResultDTO`. Cada corrección de seguridad hay que acordarse de hacerla tres veces. |

### Lo que hacen mal, y no copiamos

Esto vale tanto como lo anterior. Son defectos reales, localizados en el código:

1. **`@ManyToMany(cascade = CascadeType.ALL)` de usuario a roles** — `User.java` de
   HexaQuantum y de dropshipping. Es un fallo con consecuencias: borrar un usuario
   **intenta borrar los roles**, que son compartidos con todos los demás. Aquí el
   `cascade` no se pone.
2. **`fetch = FetchType.EAGER` en esa misma relación.** Cada consulta de usuario
   arrastra sus roles aunque solo se quiera el nombre. Lo nuestro va `LAZY` y el
   token se firma con una consulta explícita.
3. **`@Data` de Lombok sobre entidades JPA** (`User`, `Role` de ambos proyectos).
   Genera `equals`/`hashCode` sobre **todos** los campos, incluido el id
   autogenerado y las colecciones perezosas: dos entidades sin persistir son
   "iguales", y compararlas dispara la carga de la colección. El propio
   HexaQuantum ya lo hace bien en `administration-service` con
   `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`. Copiamos la versión buena,
   no la mala.
4. **Reflexión para leer campos de entidades** — `AuditLogPublisher` de dropshipping
   resuelve `getCompanyId`, `getSubsidiary` y `getModifiedBy` con `Method.invoke`
   dentro de bloques que devuelven `null` en silencio. Un renombrado de campo no
   rompe la compilación: **apaga la auditoría sin avisar**. Nuestro evento lleva
   los datos explícitos.
5. **`System.err.println` y `printStackTrace`** en ese mismo archivo. Hay un logger
   configurado; en producción eso no va a ningún sitio consultable.
6. **`catch (Exception e) { // Ignorar }`** — cuatro veces en ese archivo. Es la
   razón por la que un fallo de auditoría es invisible.
7. **Clases con nombre en minúscula**: `securityUtil`, `utilities` en ambos
   auth-service.
8. **`authenticate()` y `authenticateInternal()` con el cuerpo idéntico**, copiado
   y pegado, en `AuthenticationServiceImpl` de dropshipping.
9. **El filtro JWT se traga el token inválido** (limpia el contexto **y deja pasar
   la petición**). Un token caducado se vuelve indistinguible de "sin token".
   Oh Churus ya lo corrigió en la Ola 2 devolviendo un 401 explícito; **no volvemos
   atrás**.
10. **Rol sin `active` ni auditoría** — la tabla `hq_auth_rol` tiene id y nombre.
    No se puede desactivar un rol ni saber quién lo tocó.

### La conclusión que ordena la ola

> Los dos proyectos de referencia responden a **"¿qué puedes hacer?"** con una tabla
> de roles y un `hasAuthority` por endpoint. Eso es el suelo, y aquí no existe.
> HexaQuantum va un paso más allá con `Module → Page → PageTypeUser`, y **ése es
> justamente el paso que Oh Churus necesita** para albergar varias aplicaciones:
> el módulo es la app, y el permiso decide quién la ve.

---

## 3. El plan, por bloques y en orden de dependencia

### Bloque A — Autorización y administración

- **A1 · Roles y permisos.** `Role` (con `active` y auditoría), `oc_auth_user_role`
  sin cascada y perezosa, claim `roles` en el token, autoridades en los tres filtros
  JWT, `hasAuthority(ADMIN)` en los endpoints que hoy están abiertos de par en par.
  Semilla y migración con relleno: los usuarios existentes pasan a `USER`, el
  administrador a `ADMIN`.
- **A2 · Módulos y páginas.** `Module → Page → RolePermission(canRead/canCreate/canUpdate/canDelete)`,
  y un endpoint de menú que devuelve **lo que ese usuario puede ver**. El front pinta
  la navegación con eso, en vez de tenerla escrita a mano.
- **A3 · Administración de usuarios.** Alta, baja, activación y asignación de roles,
  todo bajo `ADMIN`. Pantalla en la app.
- **A4 · Auditoría.** Evento explícito (sin reflexión), asíncrono, tabla indexada y
  retención. Quién cambió qué, cuándo, y de qué valor a qué valor.
- **A5 · Configuración global y preferencias por usuario.** `AppConfiguration`
  clave-valor, y `currency`/`decimalPlaces` por usuario — hoy son globales en
  `application.properties`.

### Bloque B — Row Level Security en Postgres

Lo que la Ola 3 dejó escrito como "fase 2, después de 2.7": rol de aplicación sin
*ownership*, `SET LOCAL` por transacción con su `RESET` garantizado (una fuga aquí
cruza datos entre peticiones del pool), rol aparte para Flyway, y Testcontainers
probando que un usuario **no puede** leer filas de otro ni escribiendo el SQL a mano.

### Bloque C — Lo visible

Los tres puntos que la auditoría dejó abiertos "por decisión, no por descuido": la
escala tipográfica con dos escalones idénticos, los colores del tema claro por
debajo de 3:1, y los estados que hoy solo se distinguen por opacidad —deshabilitado,
logro conseguido— más las acciones que solo existen si adivinas que hay un gesto
largo.

### Bloque D — Los módulos aparcados

De `puntos-futuros.md`: metas de ahorro, notificaciones, reportes y modo offline.
En ese orden, y con el mismo criterio de siempre: **primero lo que sostiene, luego
lo que luce.**

---

## 4. Lo que esta ola NO hace

- **El token de SonarCloud.** Decisión explícita: se ve después.
- **Capturas y vídeo de la entrega.** El proyecto ya no se entrega a nadie; es de casa.
- **Multi-tenant (`Company`/`Subsidiary`).** Los dos proyectos de referencia lo
  llevan porque venden a empresas. Aquí no hay empresas: hay personas y hogares, y
  `Household` ya cumple ese papel. Meter `idCompany` en once entidades sería copiar
  la forma sin el motivo.
- **Un microservicio de auditoría aparte.** Dropshipping lo tiene porque seis
  servicios escriben en él. Aquí serían tres, y ya hay cinco servicios para dos
  personas. La auditoría vive donde vive el dato.
