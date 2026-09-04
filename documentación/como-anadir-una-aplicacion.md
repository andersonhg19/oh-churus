# Cómo añadir una aplicación nueva a Oh Churus

> Escrito el 4 de septiembre de 2026, al cerrar la Ola 4.
>
> Hasta esta ola, meter una aplicación aquí significaba **editar un array a mano
> en `AppNavigator.tsx` y volver a publicar la app**. Todo el mundo veía lo
> mismo, siempre, porque así estaba escrito en el fichero.
>
> Ahora una aplicación es una fila con sus páginas, y quién la ve lo decide un
> permiso. Este documento es el paso a paso.

---

## 1. Lo mínimo: una aplicación que ya tiene pantallas

Si las pantallas existen en el móvil y solo hace falta que la plataforma las
conozca, son **dos inserciones y ningún despliegue de código nuevo**.

### 1.1 · El módulo y sus páginas

Una migración en `auth-service/src/main/resources/db/migration/`:

```sql
INSERT INTO oc_auth_module (code, name, icon, sort_order, active, created_at, updated_at)
VALUES ('recetas', 'Recetas', 'book', 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO oc_auth_page (module_id, code, name, route, icon, in_menu, sort_order, active, created_at, updated_at)
SELECT m.id, 'recetas-lista', 'Mis recetas', 'RecipesList', 'list', TRUE, 1, TRUE,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM oc_auth_module m WHERE m.code = 'recetas';
```

**`route` tiene que ser el nombre EXACTO** con el que el navegador del móvil
conoce esa pantalla (`name="RecipesList"` en `AppNavigator.tsx`). No es un
detalle: es un acuerdo entre un `.sql` y un `.tsx` que no se compilan juntos, y
esa clase de acuerdo se rompe en silencio — el usuario pulsa y no pasa nada.

Por eso hay una prueba, `ElMenuLlevaAPantallasQueExisten`, que compara las dos
listas en cada construcción. Si la pantalla todavía no existe, la prueba te
obliga a anotarla en su lista de pendientes **con el motivo escrito**, y a
sacarla en cuanto exista.

### 1.2 · Quién la ve

```sql
INSERT INTO oc_auth_role_permission (role_id, page_id, can_read, can_create, can_update, can_delete, active, created_at, updated_at)
SELECT r.id, p.id, TRUE, TRUE, TRUE, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM oc_auth_role r
CROSS JOIN oc_auth_page p
JOIN oc_auth_module m ON m.id = p.module_id
WHERE r.name = 'USER' AND m.code = 'recetas';
```

Sin fila no hay permiso. **No existir es no poder**, así que no hace falta
insertar nada para "denegar": lo que no se concede, no está.

Desde la pantalla de *Módulos y permisos* esto mismo se hace con la rejilla de
cuatro casillas, sin escribir SQL.

---

## 2. Si la aplicación trae backend propio

### 2.1 · Un servicio nuevo, siguiendo el patrón

Copia la estructura de `fasting-service`, que es la más pequeña. Lo que **no**
es opcional:

- `JWTAuthorizationFilter` y `SecurityUtils` **iguales que los de los otros tres**.
  Están duplicados a propósito hoy; si tocas uno, tocas los tres. El filtro lee
  el `userId` y los `roles` del token: **ningún endpoint vuelve a aceptar un
  userId de entrada**, esa es la regla que cerró la Ola 2.
- `SecurityConfig` con la constante `ADMIN` y `hasAuthority` sobre lo que sea de
  administración. La cadena suelta no: un `"ADMN"` mal tecleado no falla, **abre**.
- Flyway con `ddl-auto=validate`. El esquema lo pone la migración, no Hibernate.
- Una prueba `NingunEndpointSinDueno` propia. Es la que impide que el endpoint
  número 57 se quede sin clasificar.

### 2.2 · Si guarda datos por usuario, ponle sus políticas

Si el servicio nuevo tiene tablas con `user_id`, copia el patrón de
`budget-service/.../postgresql/V9__row_level_security.sql`:

1. `ENABLE` + `FORCE ROW LEVEL SECURITY` en cada tabla.
2. Una política `USING (user_id = oc_usuario_actual())`, y si el dato se
   comparte por hogar, la variante con `oc_mis_hogares()`.
3. El aspecto `ContextoDeUsuarioEnLaBase`, que anuncia la identidad con
   `set_config(..., true)` **dentro** de la transacción.
4. `RlsConfig`, que fija el orden del interceptor transaccional para que el
   punto 3 funcione.

Y recuerda lo que dice `ComprobacionDeRls` al arrancar: mientras la aplicación
conecte como superusuario, **las políticas están puestas y dormidas**.

---

## 3. En el móvil

1. Escribe las pantallas donde te toque (`screens/<loquesea>/`).
2. Regístralas en `AppNavigator.tsx` con el **mismo `name`** que pusiste en
   `route`.
3. Pinta la entrada usando `useMenu().puedeVer('RecipesList')`. Esconder un
   botón es cortesía con quien lo usa; **la seguridad es que la ruta esté
   cerrada en el servidor**, que responde 403 aunque alguien llegue a la
   pantalla.

Tres cosas que la construcción va a exigirte, y las tres a propósito:

- **Todo pulsable dice qué hace** (`NadaInteractivoSinNombre`).
- **Ningún texto baja de 14 px** (`NadaSeEscribeMasPequeno`).
- **Los colores nuevos se miden** (`ElContrasteSeMide`), y si el fondo es de
  color, el texto encima se elige con `textoLegibleSobre(fondo)` en vez de
  escribirse a mano.

---

## 4. Lo que la plataforma ya te da, y no tienes que volver a escribir

| Ya está | Dónde |
|---|---|
| Identidad: quién pide, sacada del token | `SecurityUtils` de cada servicio |
| Autorización por rol, y por página con cuatro acciones | `oc_auth_role_permission` + `/v1/menu/mine` |
| Administración de usuarios, roles, módulos y permisos | `/v1/admin/**` y sus cuatro pantallas |
| Auditoría de quién cambió qué | publica un `CambioAuditable` y ya está |
| Moneda y decimales de cada persona | `formatCurrency` los coge solo |
| Aislamiento en la propia base de datos | políticas RLS de `V9` |

---

## 5. La lista corta

```
[] Migración: fila en oc_auth_module
[] Migración: filas en oc_auth_page, con el route EXACTO del navegador
[] Migración: permisos para los roles que deban verla
[] Pantallas registradas en AppNavigator.tsx con ese mismo name
[] La entrada se pinta con useMenu().puedeVer(...)
[] Si hay backend nuevo: filtro JWT, SecurityConfig con ADMIN, Flyway validate
[] Si guarda datos por usuario: políticas RLS y el aspecto de identidad
[] mvn -B clean verify y npx jest en verde
[] Regenerar la spec: -Dtest=LaSpecPublicadaEstaAlDiaTest -Dactualizar.spec=true
[] Colección Postman actualizada
```
