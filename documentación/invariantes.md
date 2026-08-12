# Las invariantes de Oh Churus!

**Esto es lo que hay que leer antes de tocar nada.**

Una invariante es una afirmacion que el proyecto no puede romper nunca. No son
buenas practicas ni preferencias de estilo: cada una de las que estan aqui se
escribio porque **ya se rompio una vez** y costo dinero mal contado, datos de una
persona visibles para otra, o una pantalla vacia donde deberia haber un error.

Cada invariante tiene una prueba que la vigila. Si esa prueba se pone roja no
significa que la prueba este desactualizada: significa que la invariante se
rompio. **No la ajustes. Arregla el codigo.**

Historia completa del diagnostico: `auditoria-y-plan-de-estabilizacion.md`.

---

## Resumen

| # | Invariante | Vigilada por | Casos |
|---|---|---|---|
| 1 | La identidad sale del JWT | `AislamientoEntreUsuariosTest` · `AislamientoEnAyunoTest` · `AislamientoDeCuentasTest` | 18 · 7 · 7 |
| 2 | Un dato solo lo toca su dueno o su hogar | `AislamientoEntreUsuariosTest` | 18 |
| 3 | La misma plata da la misma cifra | `LasCifrasCuadranTest` | 5 |
| 4 | Una transferencia se mueve entera | `LaTransferenciaEsUnParTest` | 11 |
| 5 | Refrescar no multiplica los pendientes | `LasRecurrenciasNoSeDuplicanTest` | 6 |
| 6 | Toda respuesta es 200 + ResultDTO | `ContratoDeErroresTest` (x3) | 16 · 7 · 7 |
| 7 | El esquema lo pone Flyway y la base sostiene sus reglas | `ElEsquemaCuadraConElCodigoTest` (x3) | 7 · 3 · 6 |
| 8 | El nucleo familiar se puede usar y no deja restos | `ElNucleoFamiliarSeUsaTest` | 8 |
| 9 | Las fechas son locales, no UTC | `format.test.ts` (frontend) | 4 |
| 10 | El dinero conserva su signo | `format.test.ts` (frontend) | 1 |
| 11 | Una sesion caducada no se disfraza de "sin datos" | **sin prueba automatica** | — |

Cuatro de esas pruebas son las **redes de seguridad intocables** del proyecto
(invariantes 1, 2 y 3): `AislamientoEntreUsuariosTest` (18),
`AislamientoEnAyunoTest` (7), `AislamientoDeCuentasTest` (7) y
`LasCifrasCuadranTest` (5).

Encima de todas ellas hay una segunda capa: las **pruebas de arquitectura** del
paquete `arquitectura/`, que no comprueban un caso concreto sino que **recorren
el codigo entero** buscando quien se salta la regla. Una prueba de aislamiento
dice "este endpoint esta bien"; una de arquitectura dice "no hay ningun endpoint
mal". Ver la sección "Las pruebas que vigilan al que llega nuevo", más abajo.

---

## 1. La identidad sale del JWT y de ningun otro sitio

**La regla.** Quien hace la peticion se sabe leyendo el claim `userId` del
token, y punto. Al **consultar y al modificar** algo que ya existe, el `userId`
del cuerpo no se mira.

**Donde vive.**
- `security/JWTAuthorizationFilter` en cada uno de los tres servicios: valida el
  token y deja el id en los *details* de la `Authentication`.
- `util/SecurityUtils.getAuthenticatedUserId()` en cada servicio: la unica forma
  de preguntar quien es.
- Los controllers de lectura pisan el `userId` del filtro con el del token antes
  de consultar.

**Donde NO se cumple todavia (deuda abierta, no la pases por alto).** Al
**crear**, tres sitios siguen tomando el `userId` del cuerpo en vez del token:

| Sitio | Endpoint |
|---|---|
| `CategoryServiceImpl.createCategory` | `/v1/categories/save` |
| `MovementServiceImpl.createMovement` | `/v1/movements/save` |
| `ScheduledMovementServiceImpl.createScheduled` | `/v1/scheduled/save` |

Con un token valido se puede, por tanto, crear una categoria, un movimiento o un
programado **a nombre de otra persona**. Se escribe aqui porque una regla que se
enuncia como absoluta y no lo es hace mas dano que la propia deuda: quien lea
"nunca viaja en el cuerpo" no ira a mirar estas tres lineas. Estan tambien en la
lista `PENDIENTES` de `NingunEndpointSinDuenoTest`, con el ataque descrito.

**Por que.** Se decidio al principio que todos los endpoints fueran POST y que
los parametros viajaran en el cuerpo. Al hacerlo el `userId` se convirtio en un
parametro mas, indistinguible de `categoryId` o de `amount`, y por tanto lo
decidia el cliente. Con eso, cambiando un numero, cualquiera leia los movimientos
de otro, le reescribia el importe, se apropiaba de ellos y se metia en su nucleo
familiar. En auth-service, ademas, se daba de baja la cuenta de cualquiera.

**Quien la vigila.**
- `budget-service/.../security/AislamientoEntreUsuariosTest` — 18 casos
- `fasting-service/.../security/AislamientoEnAyunoTest` — 7 casos
- `auth-service/.../security/AislamientoDeCuentasTest` — 7 casos

Las tres levantan la aplicacion entera (contexto, cadena de filtros, JPA sobre
H2) y atacan la API como lo haria una persona: Ana crea sus datos, Bruno se
autentica con un token **legitimo** e intenta llegar a los de Ana por su id.

No comprueban un codigo de estado concreto, sino las dos propiedades que
importan: **la respuesta no lleva datos de Ana**, y **los datos de Ana siguen
intactos despues del intento**. Asi sobreviven a que se decida devolver 403 o 404.

**Si anades un endpoint que reciba un id, anadelo a la matriz.**

**Nota sobre `SecurityUtils`.** Devuelve `null` si no hay token, no lanza. Es
intencionado: quien llama decide si eso es un 401 o un flujo publico. Y un dato
sin dueno **no es de todos**: `esDelUsuario(null)` es `false`.

---

## 2. Un dato solo lo toca su dueno, o alguien de su hogar

**La regla.** Un dato es tuyo si lo creaste, **o** si vive en una categoria
compartida de un hogar al que perteneces. Ante la duda, no.

**Donde vive.** `budget-service/util/ControlAcceso`. Un solo sitio para toda la
aplicacion, aplicado en `get`/`delete`/`confirm`/`children`/`update` de
movimientos, categorias, programados y asignaciones.

**Por que no basta comparar `userId`.** La funcion estrella del producto es que el
gasto del arriendo lo vean los dos. Si la regla fuera "es tuyo si tu id coincide",
el nucleo familiar no funcionaria; si fuera "todo el mundo ve todo", no habria
privacidad. La categoria compartida es la frontera.

**Se responde "no existe", no "no puedes".** Contestar "no puedes" confirma que
ese id existe, que es informacion que el que pregunta no deberia obtener.

**Quien la vigila.** `AislamientoEntreUsuariosTest`, en sus bloques de Lectura,
Escritura, Nucleo familiar y Listados, mas el caso "sin token no se llega a nada".

---

## 3. La misma plata da la misma cifra en todas las pantallas

**La regla, en dos frases:**

- **El hijo es DETALLE del padre, nunca un gasto aparte.** Si desglosas la compra
  del mercado en tres lineas, gastaste el total de la compra, no el total mas las
  tres lineas.
- **La transferencia no es ingreso ni gasto para nadie.** Mueve plata entre
  bolsillos. Si contara, pasar del bote comun al bolsillo propio inventaria un
  gasto y un ingreso que no existieron.

**Donde vive.** `budget-service/util/Computables`. Un `Predicate<Movement>` y una
funcion `total(...)`, usados en los cinco puntos de agregacion.

**Por que.** `DashboardServiceImpl` contaba la misma plata de dos formas **en el
mismo metodo**: `totalExpense` sumaba los sub-movimientos y `budgetTotal` los
excluia. Con un gasto padre de 500.000 y dos hijos de 200.000 y 100.000, "Gastos"
decia 800.000 y "Presupuesto" 500.000 en la misma pantalla, y la dona sumaba una
tercera cosa distinta. De ahi salia la sensacion de que las cifras no cuadran.

**No sumar no es esconder.** Los hijos siguen apareciendo en la lista de
movimientos con todo su detalle. Lo unico que no hacen es sumar dos veces.

**Quien la vigila.** `budget-service/.../coherencia/LasCifrasCuadranTest`, 5
casos. Monta un escenario con numeros escogidos a mano:

```
Sueldo (ingreso)                       3.000.000
Mercado (gasto, padre)                   500.000
  - carne (hijo)                         200.000   <- detalle, NO suma
  - verdura (hijo)                       100.000   <- detalle, NO suma
Transferencia bote comun -> bolsillo     400.000   <- ni ingreso ni gasto

Resultado exigido en TODAS las superficies:
  ingresos 3.000.000 · gastos 500.000 · balance 2.500.000
```

Y exige que panel, presupuesto, dona, detalle por categoria y lista devuelvan lo
mismo. Si algun dia hay que cambiar la regla, se cambia en `Computables` y cambia
en las cinco pantallas a la vez. Ese es el objetivo.

---

## 4. Una transferencia se edita entera o no se edita

**La regla.** Una transferencia son **dos** filas enlazadas por `transferPairId`.
Editar, confirmar o borrar toca las dos, en la misma transaccion. Y se cumple:

```
shared.balance + personal.balance == total.balance
```

tambien cuando lo mira la pareja, que ve salir la plata del bote comun pero no la
ve entrar en un bolsillo que no es suyo.

**Por que.** Borrar ya propagaba a las dos patas; editar no. Corregir una
transferencia de 500.000 a 300.000 cambiaba una sola pata y dejaba 200.000
inexistentes flotando en el consolidado, sin forma de arreglarlo desde la app
porque la otra pata no se puede editar por separado.

**Se eligio propagar, no bloquear.** Bloquear la edicion y pedir "anula y rehaz"
era mas barato de programar y peor de usar.

**Quien la vigila.** `budget-service/.../coherencia/LaTransferenciaEsUnParTest`,
11 casos repartidos en dos grupos: `Editar` (4) comprueba que corregir el
importe, la fecha o confirmar con otro importe mueve las dos patas, y que una
pata no se puede mudar de categoria; `Invariante` (7, parametrizados por los dos
miembros del hogar) comprueba que `compartido + personal == total` sigue
cuadrando recien hecha la transferencia, tras corregirla y tras anularla.

---

## 5. Refrescar el panel mil veces no multiplica los pendientes

**La regla.** La clave de idempotencia de una ocurrencia es
`(scheduledMovementId, periodStart)`, y `periodStart` es una columna grabada, no
la fecha. El pendiente se crea **a nombre del dueno del programado**, no de quien
pulsa. Borrar un pendiente lo **omite**: no resucita.

**Por que.** El panel llama a `generate-pending` cada vez que se abre, y eso
deberia ser inofensivo. No lo era: la clave era la fecha, que el usuario puede
mover, asi que mover la fecha —o editar el programado— y refrescar creaba un
segundo arriendo del mismo mes. Ademas cada miembro del hogar elige su propio dia
de corte, asi que dos personas abrian periodos distintos para el mismo mes y el
arriendo se generaba dos veces. Y el pendiente quedaba a nombre de quien abriera
la app primero.

**Quien la vigila.**
`budget-service/.../coherencia/LasRecurrenciasNoSeDuplicanTest`, 6 casos, con un
hogar de dos personas, dias de corte distintos y refrescos repetidos.

---

## 6. Toda respuesta es HTTP 200 con un ResultDTO

**La regla.** Pase lo que pase dentro de un controller sale un `ResultDTO` con
HTTP 200, `correct:false` y un mensaje que **nombra el campo** que falla.

```json
{ "correct": false, "message": "Revisa los datos enviados: ...", "errorCode": 400, "object": null }
```

**Donde vive.** Un `@RestControllerAdvice` por servicio, en
`exception/GlobalExceptionHandler`.

**Por que.** El contrato existia como acuerdo verbal y el frontend **solo** sabe
leer `ResultDTO`, pero no habia un solo advice: un cuerpo vacio, un campo con el
tipo cambiado o cualquier excepcion no capturada salian como el 400/500 propio de
Spring, con su JSON de `timestamp/status/path`. El usuario veia "Request failed
with status code 400" o, peor, una pantalla en blanco.

**Alcance deliberado.** El advice esta acotado al paquete `controller`. Una ruta
que no existe sigue siendo un 404 de verdad: convertirla en 200 esconderia URLs
mal escritas.

**Quien la vigila.** `ContratoDeErroresTest` en los tres servicios (16 casos en
budget, 7 en auth, 7 en fasting). Por cada controller ataca con las tres formas de
romper un cuerpo —vacio, con el tipo cambiado y con un texto que no cabe— y exige
siempre 200 + `correct:false` + mensaje util.

---

## 7. El esquema lo pone Flyway, y la base sostiene sus propias reglas

**La regla.** `spring.flyway.enabled=true` y
`spring.jpa.hibernate.ddl-auto=validate` en los tres servicios. Hibernate no crea
ni modifica nada: solo compara el mapeo contra el esquema y, si no coinciden, **el
servicio no arranca**.

**Corolario para quien programa: cambiar una entidad exige su migracion.** No es
una molestia, es la red.

**Por que.** Antes era `ddl-auto=update` y no habia **ni una sola** clave foranea,
unicidad o CHECK. Dos consecuencias reales:

- Un doble toque en Presupuesto insertaba **dos** asignaciones para la misma
  categoria y periodo. Desde ese momento el `Optional` del servicio encontraba dos
  filas y lanzaba `NonUniqueResultException` en esa categoria **para siempre**,
  sin forma de arreglarlo desde la app.
- Con `update`, anadir un `@Column(nullable=false)` a una tabla con datos fallaba
  como WARN y la aplicacion arrancaba igual, con el esquema desincronizado y sin
  que nadie se enterara.

**Las reglas que ahora viven en la base.** Estan listadas una a una en
`entidades-y-relaciones.md`, seccion 5. En resumen: unicidad parcial de lo que
esta vivo (asignaciones y miembros del hogar), ocho claves foraneas en budget y
una en fasting, y CHECK de importes, periodos y horas de ayuno.

**Detalles que hay que respetar al tocar migraciones:**

- **Budget tiene DOS `V2`**, uno por motor (`postgresql/` y `h2/`). Dicen lo mismo
  con otra sintaxis: PostgreSQL tiene indices parciales y H2 no. **Si tocas uno,
  toca el otro.**
- En PostgreSQL las FK y los CHECK se anaden `NOT VALID`, y los duplicados
  existentes se **desactivan**, no se borran. Las migraciones corren sobre datos
  reales: ninguna fila desaparece.

**Quien la vigila.** `ElEsquemaCuadraConElCodigoTest` en los tres servicios (7
casos en budget, 3 en auth, 6 en fasting). Que el contexto levante ya prueba que
el mapeo cuadra; ademas escribe a mano en la base lo que el codigo daba por
imposible y exige que la base lo rechace.

Esto no sustituye a la validacion en Java. **La base es el ultimo muro**, el que
aguanta cuando dos peticiones entran a la vez y las dos ven "aqui no hay nada".

---

## 8. El nucleo familiar se puede usar y no deja datos colgando

**La regla.**
- Se invita **por correo**, nunca por id de fila.
- Solo el `OWNER` invita y expulsa.
- Al expulsar a alguien no queda nada colgando de lo que ya no ve: sus
  subcategorias suben a raiz y sus asignaciones sobre categorias del hogar se
  desactivan.
- El arbol nunca se traga una categoria cuyo padre ya no es visible.

**Por que.** El nucleo familiar es una de las tres patas del producto y desde la
app no se podia usar: para invitar habia que escribir el id de la base de datos,
que nadie conoce. Y al sacar a un miembro solo se apagaba su fila, con lo que sus
subcategorias desaparecian del arbol sin avisar.

**Como resuelve el correo.** `budget-service` no tiene los correos (viven en
`auth_db`): `DirectorioDeUsuariosHttp` le pregunta a auth-service reenviando el
**mismo token de quien invita**. Ese listado no es un directorio: sin correo solo
te devuelve a ti mismo, y con correo la coincidencia es exacta, nunca parcial.
Vigilado por `AislamientoDeCuentasTest`.

**Quien la vigila.** `budget-service/.../hogar/ElNucleoFamiliarSeUsaTest`, 8
casos, con la aplicacion entera levantada porque el fallo estaba en el cableado.

---

## 9. Las fechas son las del reloj de quien usa la app, no las de UTC

**La regla.** Para guardar, `fechaLocalISO()` (en `frontend/src/utils/format.ts`).
Para pintar, parseo local en `formatDate` / `formatDateShort`. Nunca
`new Date().toISOString()` ni `new Date("2026-08-11")` a pelo.

**Por que.** `toISOString()` pasa a UTC: en Bogota, todo lo registrado despues de
las 19:00 se guardaba con la fecha de **manana**. La cena del 11 aparecia el 12 y,
si caia en el corte, en el mes siguiente. Y al reves al pintar:
`new Date("2026-08-11")` es medianoche UTC, o sea las 19:00 del dia 10, asi que
las listas mostraban un dia menos.

**Quien la vigila.** `frontend/src/utils/__tests__/format.test.ts`, bloque
"fechas locales (el bug de la zona horaria)": 4 casos con `TZ=America/Bogota` y la
hora fijada a las 22:30 y de madrugada.

---

## 10. El dinero conserva su signo

**La regla.** `formatCurrency` pone el signo. Ninguna pantalla se lo vuelve a
pegar a mano.

**Por que.** Hacia `Math.abs`, asi que un deficit de -500.000 se veia igual que un
superavit, y tres pantallas lo parcheaban cada una a su manera.

**Quien la vigila.** `format.test.ts`, caso "conserva el signo de los importes
negativos". El mismo fichero comprueba que no salga `NaN` si le llega basura.

---

## 11. Una sesion caducada no se disfraza de "sin datos"

**La regla.** Un 401 cierra la sesion y lleva a la pantalla de entrada. El
interceptor de `frontend/src/services/api.ts` lo hace en un solo sitio
(`registrarCierreDeSesion`, que registra `AuthContext`), porque la sesion puede
caducar en **cualquier** peticion y si cada pantalla tuviera que acordarse siempre
habria una que no lo hace.

**Por que.** Antes el 401 se ignoraba en silencio y cada pantalla pintaba su
estado vacio: tras unos dias sin abrir la app entrabas sin contrasena y veias
balance $0 y "Sin nucleo familiar", indistinguible de haberlo perdido todo.

**Quien la vigila.** **Nadie, todavia.** Es la unica invariante de esta lista sin
prueba automatica: `api.test.ts` cubre el interceptor de peticion (el token en la
cabecera) pero no el de respuesta. Si tocas `api.ts` o `AuthContext`, comprueba
esto a mano.

---

## Las pruebas que vigilan al que llega nuevo

Las de arriba comprueban **casos**: este endpoint, esta pantalla, este escenario.
Su punto ciego es el futuro: mañana alguien añade el endpoint 57, o copia y pega
un controller viejo, y ninguna se pone roja.

Para eso está el paquete `arquitectura/` de cada servicio. Estas pruebas no
prueban comportamiento: **recorren el código por reflexión y por fichero fuente**
y exigen que nadie se salte la regla. Todas tienen listas de exenciones
**cerradas y con el motivo escrito al lado**: si aparece una exención nueva, o si
desaparece una de las listadas, se ponen rojas. Así la lista no se pudre ni crece
sin que nadie la mire.

| Prueba | Qué recorre | Qué exige |
|---|---|---|
| `LaIdentidadNoVuelveAlCuerpoTest` | Los DTO de entrada y los controllers | Que ningún DTO lleve un `@NotNull` sobre `userId` ("el cliente TIENE que decirme quién es"), y que ningún controller lea `body.get("userId")` ni `dto.getUserId()` |
| `NingunEndpointSinDuenoTest` | Todos los mapeos de todos los controllers | Que cada ruta esté **en la matriz de aislamiento**, **exenta con su motivo**, o **pendiente con el ataque concreto escrito**. Una ruta nueva no cae en ninguno de los tres y la construcción se para. La matriz no se escribe a mano: se lee del fichero fuente de `AislamientoEntreUsuariosTest`, así que borrar un caso de allí también rompe esto |
| `TodoControllerDevuelveElContratoTest` | La firma de todos los métodos de controller | Que devuelvan `ResponseEntity<ResultDTO>`. `ContratoDeErroresTest` defiende el contrato cuando algo falla; este lo defiende antes, en la firma |
| `ElEsquemaNoSeGeneraSoloTest` | **Todos** los `application*.properties` y `@TestPropertySource` del árbol, de los cinco servicios | Que `ddl-auto` sea `validate` en todos los perfiles. Un servicio nuevo con `update` también se cae aquí |

`NingunEndpointSinDuenoTest` **no comprueba seguridad: comprueba que se haya
decidido algo.** Es la diferencia entre "este endpoint está bien" y "no hay
ningún endpoint sin mirar".

Además hay un **suelo de cobertura** en los dos lados: `jacoco:check` en la fase
`verify` del backend y `coverageThreshold` en el `package.json` del frontend. Los
umbrales están unos puntos **por debajo** de la cobertura real a propósito: no
están para obligar a escribir pruebas nuevas, están para que **borrar** las que
existen rompa la construcción.

---

## Como se comprueban todas

```bash
# Backend completo (incluye todas las de arriba salvo las del frontend,
# mas el suelo de cobertura de JaCoCo)
cd backend && mvn -B clean verify

# Solo las redes de seguridad
mvn test -pl budget-service  -Dtest=AislamientoEntreUsuariosTest
mvn test -pl budget-service  -Dtest=LasCifrasCuadranTest
mvn test -pl fasting-service -Dtest=AislamientoEnAyunoTest
mvn test -pl auth-service    -Dtest=AislamientoDeCuentasTest

# Frontend (--coverage no es opcional: sin el, coverageThreshold no mira nada)
cd frontend && npx tsc --noEmit && npx jest --coverage --ci
```

En CI lo hace `.github/workflows/pruebas.yml`, que ademas levanta el stack con
Docker Compose y pasa los escenarios de Karate **por el gateway**.

---

## Que hacer si una de estas pruebas se pone roja

1. **No la modifiques.** Ni el assert, ni el escenario, ni el `@Disabled`.
2. Lee el comentario de cabecera de la clase: explica que agujero tapa y como se
   manifestaba.
3. Arregla el codigo hasta que vuelva a verde.
4. Si de verdad crees que la invariante debe cambiar —puede pasar; el producto
   evoluciona— eso es una decision de diseno, no un arreglo de una prueba. Se
   documenta aqui antes de tocar nada.

Y al reves: cuando arregles un fallo de este calibre, **escribe la prueba primero
y compruebala en rojo**. Las cuatro redes de seguridad de este proyecto se
verificaron revirtiendo el arreglo a proposito, para saber que cazan lo que dicen
cazar.
