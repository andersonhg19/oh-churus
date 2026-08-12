# Oh Churus!

Finanzas personales para una pareja que quiere ver la misma cifra, mas un modulo
de ayuno intermitente. Backend en microservicios con Spring Boot, aplicacion en
React Native (Expo) que corre en web y en movil.

---

## AVISO: este repositorio es PUBLICO

**No subas datos reales.** Ni volcados de la base de datos, ni capturas con
movimientos de verdad, ni correos personales, ni el `.env`.

Lo que el `.gitignore` ya bloquea (y conviene no forzar): `.env` y `.env.*`,
`backups/`, cualquier `*.sql` que no sea una migracion de Flyway, `*.rar`/`*.zip`,
claves (`*.key`, `*.pem`, `*.jks`) y `frontend/node_modules`.

Las contrasenas de los datos semilla que aparecen en este documento son de demo
local y estan pensadas para eso. En cualquier despliegue real se sobrescriben con
variables de entorno (`SEED_*_PASSWORD`, `SECRET`, `POSTGRES_PASSWORD`).

---

## 1. Que es y para quien

Oh Churus! nace de un problema domestico concreto: dos personas que comparten
gastos y quieren, a la vez, llevar sus cuentas por separado. Las apps de finanzas
o asumen una sola persona, o asumen bolsa comun. Aqui conviven las dos cosas.

Tiene tres patas:

### Finanzas personales
Categorias en arbol (padre e hijos), movimientos de ingreso y gasto, sub-gastos
para desglosar una compra, movimientos programados que generan pendientes,
presupuesto por categoria y periodo, panel con totales y tendencia, grafico de
dona con detalle por categoria y exportacion a Excel.

El periodo no es el mes natural: cada persona configura su **dia de corte**
(`budgetStartDay`, 1-31). Si el dia no existe en un mes corto, se usa el ultimo
dia de ese mes.

### Nucleo familiar compartido
Un hogar (`Household`) con miembros. Una categoria puede ser **personal** o
**compartida del hogar**. Los movimientos que cuelgan de una categoria compartida
los ven todos los miembros; los demas, solo su dueno. La invitacion se hace **por
correo**, no por id de fila. El dueno del hogar (rol `OWNER`) es el unico que
puede anadir o expulsar miembros.

Hay tambien **transferencias**: mover plata entre bolsillos (del bote comun al
propio, por ejemplo). Una transferencia no es ingreso ni gasto para nadie.

### Ayuno intermitente
Plan de ayuno (12:12, 14:10, 16:8, 18:6, 20:4 o personalizado), sesiones con
inicio y fin, historial y resumen por periodo, registro de vasos de agua con meta
diaria, y logros por racha y por horas acumuladas.

---

## 2. Stack

| Capa | Tecnologia |
|---|---|
| Backend | Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.3 |
| Descubrimiento | Eureka (Spring Cloud Netflix) |
| Puerta de entrada | Spring Cloud Gateway |
| Persistencia | Spring Data JPA + PostgreSQL 14 (H2 en memoria para las pruebas) |
| Migraciones | Flyway (`ddl-auto=validate`: el esquema lo pone Flyway, Hibernate solo comprueba) |
| Seguridad | JWT (java-jwt), BCrypt |
| Frontend | React Native 0.83 + Expo 55, TypeScript, React Navigation |
| Pruebas backend | JUnit 5, Mockito, MockMvc, `@SpringBootTest` para las de aislamiento, Karate para la API por el gateway |
| Pruebas frontend | Jest + React Testing Library (jest-expo) |
| Cobertura y analisis | JaCoCo, SonarQube Cloud vía GitHub Actions |
| Contenedores | Docker + Docker Compose |

---

## 3. Mapa de servicios y puertos

| Servicio | Puerto | Base de datos | Que hace |
|---|---|---|---|
| `postgres` | 5432 (solo 127.0.0.1) | — | Motor. Crea las tres BD al arrancar |
| `discovery-service` | 8760 | — | Eureka. Todos se registran aqui |
| `gateway-service` | 8820 | — | Unica puerta publica. Enruta por nombre de servicio |
| `auth-service` | 8821 (solo 127.0.0.1) | `auth_db` | Usuarios, registro, login, emision del JWT |
| `budget-service` | 8823 (solo 127.0.0.1) | `budget_db` | Categorias, movimientos, programados, presupuesto, hogar, panel, Excel |
| `fasting-service` | 8825 (solo 127.0.0.1) | `fasting_db` | Plan, sesiones, agua, logros |
| `frontend` | 3000 | — | Build web de Expo servido con `serve` |

Los tres servicios de negocio se publican **solo en `127.0.0.1`** a proposito: la
unica entrada desde la red es el gateway. Todos tienen `context-path` `/oh-churus`.

**Como se llama a la API.** Siempre a traves del gateway, con el nombre del
servicio en mayusculas como primer segmento:

```
POST http://localhost:8820/AUTH-SERVICE/oh-churus/v1/auth/login
POST http://localhost:8820/BUDGET-SERVICE/oh-churus/v1/dashboard/summary
POST http://localhost:8820/FASTING-SERVICE/oh-churus/v1/fasting/session/active
```

Son 56 endpoints y **todos son POST** con el cuerpo en JSON. La respuesta es
siempre HTTP 200 con un `ResultDTO`:

```json
{ "correct": true, "message": "OK", "errorCode": 0, "object": { } }
```

Cuando algo falla, `correct` viene en `false`, `errorCode` imita al codigo HTTP
que no se envia, y `message` dice que campo falla. Esto no es una costumbre: hay
un `@RestControllerAdvice` por servicio que lo garantiza incluso ante una
excepcion no capturada.

La unica excepcion es `/v1/export/excel`: su camino feliz devuelve los bytes del
`.xlsx`, y el de error, un `ResultDTO` en JSON.

**La identidad sale del claim del JWT**, no del cuerpo. Al consultar o al
modificar algo existente, el `userId` que mandes en la peticion se ignora: no
sirve para ver ni para tocar lo de otro.

Con una excepcion que conviene saber antes de tocar nada: **al crear**,
`/v1/categories/save`, `/v1/movements/save` y `/v1/scheduled/save` todavia
guardan el `userId` que venga en el cuerpo, asi que ahi si decide el cliente de
quien es lo que se crea. Es deuda conocida y esta anotada en
`documentación/invariantes.md` y en la lista `PENDIENTES` de
`NingunEndpointSinDuenoTest`.

---

## 4. Como levantarlo

### Requisitos
- Java 17 (JDK) y Maven
- Docker Desktop
- Node.js 20 o superior, solo si vas a tocar el frontend fuera de Docker (el CI
  fija la 24.14.0; la imagen del frontend se construye con node:20-alpine)

### 4.1 Con Docker Compose (la forma recomendada)

Todo el sistema, incluido el frontend web, esta en el `docker-compose.yml` de la
raiz. El de `backend/docker-compose.yml` es mas antiguo y **no incluye
fasting-service ni frontend**: usa el de la raiz.

1. **Crea un `.env` en la raiz.** No esta en el repo (es un secreto). Contenido
   minimo:

   ```properties
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=cambia-esto
   SECRET=cambia-esta-clave-de-firma
   EXP_TIME=864000000
   # URL que el build del frontend empotra para llamar al gateway.
   # localhost sirve para el navegador del mismo equipo; para probar en el
   # movil pon la IP de tu WiFi (por ejemplo http://192.168.1.9:8820).
   API_URL=http://localhost:8820
   ```

   Todas tienen valor por defecto en el compose, asi que arranca igual sin
   `.env`, pero entonces la clave de firma del JWT es publica. Para algo mas que
   una prueba de cinco minutos, ponlas.

2. **Levanta:**

   ```bash
   docker compose up --build -d
   docker compose ps        # esperar a que auth, budget y fasting esten healthy
   ```

   El orden lo resuelve el compose: postgres y discovery tienen healthcheck y los
   demas esperan a que respondan.

3. **Entra:** `http://localhost:3000`

4. **Apaga:** `docker compose down` (anade `-v` si quieres borrar tambien los
   datos de PostgreSQL).

### 4.2 En local, sin Docker para el backend

Util para depurar. Necesitas PostgreSQL corriendo con las tres bases creadas
(`auth_db`, `budget_db`, `fasting_db`); el script esta en
`backend/init-db/init-databases.sql`.

**Dos detalles que hay que saber o no arranca:**

- La direccion de Eureka esta escrita como `http://discovery-service:8760/eureka`,
  que es el nombre del contenedor. Fuera de Docker hay que sobrescribirla.
- `budget-service` le pregunta a `auth-service` por el correo al invitar al hogar,
  y su URL por defecto tambien es un nombre de contenedor.

```bash
cd backend
mvn clean package -DskipTests

# 1) Discovery (dejalo corriendo)
mvn spring-boot:run -pl discovery-service

# 2) Gateway
mvn spring-boot:run -pl gateway-service \
  -Dspring-boot.run.jvmArguments="-Deureka.client.serviceUrl.defaultZone=http://localhost:8760/eureka"

# 3) Auth
mvn spring-boot:run -pl auth-service \
  -Dspring-boot.run.jvmArguments="-Deureka.client.serviceUrl.defaultZone=http://localhost:8760/eureka"

# 4) Budget
mvn spring-boot:run -pl budget-service \
  -Dspring-boot.run.jvmArguments="-Deureka.client.serviceUrl.defaultZone=http://localhost:8760/eureka -Dapp.auth-service-url=http://localhost:8821/oh-churus"

# 5) Fasting
mvn spring-boot:run -pl fasting-service \
  -Dspring-boot.run.jvmArguments="-Deureka.client.serviceUrl.defaultZone=http://localhost:8760/eureka"
```

Las credenciales de PostgreSQL se cogen de `SPRING_DATASOURCE_*` y, si no estan,
de los valores por defecto de cada `application.properties`.

Frontend:

```bash
cd frontend
npm ci
EXPO_PUBLIC_API_URL=http://localhost:8820 npx expo start
```

`npx expo start` abre el menu de Expo: `w` para el navegador, o escanear el QR con
Expo Go en el movil (para el movil, `EXPO_PUBLIC_API_URL` tiene que ser la IP de
tu WiFi, no `localhost`).

### 4.3 Datos semilla

La primera vez que arrancan contra una base vacia, los servicios siembran datos
de demo (`app.seed-data-enabled=true`, y solo si la tabla esta vacia):

| Correo | Contrasena por defecto | Dia de corte |
|---|---|---|
| `admin@ohchurus.com` | `Admin123!` (`SEED_ADMIN_PASSWORD`) | 1 |
| `demo@ohchurus.com` | `Demo123!` (`SEED_DEMO_PASSWORD`) | 1 |
| `anderson@ohchurus.com` | `Admin123!` (`SEED_ANDERSON_PASSWORD`) | 28 |
| `samy@ohchurus.com` | `Samy123!` (`SEED_SAMY_PASSWORD`) | 28 |

`budget-service` siembra ademas un arbol de categorias y un hogar de ejemplo, y
`fasting-service` un plan 16:8. Para arrancar sin nada de esto:
`-Dapp.seed-data-enabled=false`.

---

## 5. Como correr las pruebas

### Backend

```bash
cd backend
mvn -B clean verify
```

Esperado: **BUILD SUCCESS**, mas de 600 pruebas (en la ultima medida, 671: auth
98 / budget 424 / fasting 100, mas los dos smoke de discovery y gateway). No hace
falta Docker: las pruebas usan H2 en memoria. En auth y budget el esquema lo
montan **las mismas migraciones de Flyway que en produccion** (`ddl-auto=validate`
tambien en pruebas), asi que una migracion rota se nota aqui y no al desplegar.
En fasting el perfil de pruebas general usa `create-drop` porque casi todas son
unitarias; quien ejecuta sus migraciones de verdad es
`ElEsquemaCuadraConElCodigoTest`, que se trae su propio perfil.

La cobertura JaCoCo se genera sola:
`auth-service/target/site/jacoco/index.html` (y lo mismo en budget y fasting).
Ojo: `verify` incluye un **suelo de cobertura** (`jacoco:check`), asi que borrar
pruebas rompe la construccion. Los umbrales estan unos puntos por debajo de la
cobertura real: no estan para obligar a escribir pruebas nuevas, sino para que
quitar las que hay se note.

Un solo servicio: `mvn test -pl budget-service`.

**Las tres redes de seguridad.** Si alguna se pone roja, se acaba de abrir un
agujero: no se ajusta la prueba, se arregla el codigo.

```bash
mvn test -pl budget-service  -Dtest=AislamientoEntreUsuariosTest   # 18 casos
mvn test -pl budget-service  -Dtest=LasCifrasCuadranTest           #  5 casos
mvn test -pl fasting-service -Dtest=AislamientoEnAyunoTest         #  7 casos
mvn test -pl auth-service    -Dtest=AislamientoDeCuentasTest       #  7 casos
```

Ver `documentación/invariantes.md` para saber que vigila cada una.

**Karate** (escenarios de API contra un backend de verdad levantado) vive en un
perfil de Maven y **no se ejecuta con `verify`**: hay que pedirlo. En CI se corre
por el gateway, que es el camino que recorre la app real.

```bash
docker compose up -d          # el stack tiene que estar arriba

# Igual que en CI: por el gateway (puerto 8820, con prefijo de servicio)
mvn -Pkarate test -pl auth-service \
  -DbaseUrl=http://localhost:8820/AUTH-SERVICE/oh-churus
mvn -Pkarate test -pl budget-service \
  -DbaseUrl=http://localhost:8820/BUDGET-SERVICE/oh-churus \
  -DauthUrl=http://localhost:8820/AUTH-SERVICE/oh-churus
```

Sin `-DbaseUrl` habla directo con el puerto del microservicio (8821), util para
depurar pero se salta el enrutado por Eureka. Los informes quedan en
`*/target/karate-reports/karate-summary.html`.

### Frontend

```bash
cd frontend
npx tsc --noEmit                     # tipos
npx jest --coverage --ci             # en la ultima medida: 60 suites, 375 tests
```

Hay pruebas que dependen de la zona horaria (las fechas se calculan en local, no
en UTC). Para reproducir el entorno de referencia: `TZ=America/Bogota npx jest`.

Jest tambien tiene un suelo de cobertura (`coverageThreshold` en
`package.json`), y solo se comprueba cuando se pasa `--coverage`.

### Integracion continua

Dos workflows en `.github/workflows/`:

| Workflow | Cuando | Que hace |
|---|---|---|
| `pruebas.yml` | push a `main` y cualquier PR | **La puerta.** Tres trabajos: backend (`mvn -B clean verify`, con el suelo de JaCoCo), frontend (`tsc --noEmit` + `jest --coverage`), y Karate levantando el stack con Docker Compose y atacando **por el gateway**. Guarda informes como artefactos |
| `sonarcloud.yml` | push a `main` y PR a `main` | Analisis en SonarQube Cloud (proyecto `andersonhg19_oh-churus`). **Mide, no decide** |

El trabajo de Karate espera a que el gateway responda por
`/AUTH-SERVICE/oh-churus/actuator/health` y `/BUDGET-SERVICE/...` antes de
empezar, en vez de dormir un rato y cruzar los dedos.

> **SonarCloud esta en rojo y no es por el codigo.** El escaner corta con
> `403` al pedir `https://api.sonarcloud.io/analysis/jres`: el secreto
> `SONAR_TOKEN` se genero el 10 de junio de 2026 y los tokens de SonarCloud
> caducan. Todos los analisis de junio pasaron; todos los de agosto fallan en
> ese mismo punto, antes incluso de mirar una linea de codigo.
> Se arregla generando un token nuevo en SonarCloud
> (*My Account -> Security*) y actualizando el secreto del repositorio con
> `gh secret set SONAR_TOKEN`. No bloquea nada: quien decide es `pruebas.yml`,
> y por eso se separaron.

---

## 6. Donde esta cada cosa

```
Oh Churus/
├── README.md                  este archivo
├── docker-compose.yml         el sistema completo (usa este)
├── sonar-project.properties   configuracion del analisis estatico
├── comandos-pruebas.txt       chuleta antigua de comandos (parcialmente desfasada)
├── .github/workflows/         CI: pruebas.yml (la puerta) y sonarcloud.yml (mide)
├── collection/                coleccion Postman
├── screenshots/               una captura del IDE (SDK 17). No hay capturas de la app
│
├── backend/
│   ├── pom.xml                POM padre (Java 17, Boot 3.2.5, Cloud 2023.0.3)
│   ├── init-db/               creacion de auth_db, budget_db, fasting_db
│   ├── discovery-service/     Eureka
│   ├── gateway-service/       Spring Cloud Gateway
│   ├── auth-service/          User + JWT
│   ├── budget-service/        el grueso del dominio
│   └── fasting-service/       ayuno, agua, logros
│
├── frontend/
│   └── src/
│       ├── components/        atoms / molecules / organisms
│       ├── contexts/          Auth, Theme, Toast
│       ├── hooks/             useCarga, useAccionUnica
│       ├── navigation/        AppNavigator (drawer de dos modulos + pestañas)
│       ├── screens/           auth, dashboard, movements, categories, budget,
│       │                      scheduled, summary, consolidated, settings, fasting
│       ├── services/          un cliente por servicio del backend + api.ts
│       └── utils/             format, fechas, periodo, validadores
│
├── documentación/
│   ├── invariantes.md                        LEE ESTO ANTES DE TOCAR NADA
│   ├── entidades-y-relaciones.md             las 11 entidades reales
│   ├── auditoria-y-plan-de-estabilizacion.md el diagnostico de agosto y el plan
│   ├── enunciado-detallado.md                alcance original de marzo (historico)
│   ├── taller-calidad-sonarqube.md           el capitulo de calidad de junio
│   ├── cobertura-tests-changelog.md          registro del trabajo de cobertura
│   ├── puntos-futuros.md                     backlog aparcado
│   └── pruebas/                              evidencias
│
└── seguimiento/
    ├── bitacora.md            cronologia del proyecto
    └── plan-maestro.md        plan original de 12 fases (historico)
```

**Dentro de un servicio del backend** la estructura se repite:
`controller/` → `service/` (interfaz) + `service/impl/` → `repository/` →
`entity/`, con `dto/input` y `dto/output`, `mapper/`, `security/` (filtro JWT y
configuracion), `util/` (`SecurityUtils`, `PeriodUtils`, `Computables`,
`ControlAcceso`) y `exception/GlobalExceptionHandler`.

---

## 7. Las cuatro cosas que hay que entender antes de programar

Estan explicadas a fondo en `documentación/invariantes.md`. En corto:

1. **La identidad sale del JWT.** `SecurityUtils.getAuthenticatedUserId()` y de
   ningun otro sitio. Ningun endpoint acepta un `userId` de entrada.
2. **Quien puede tocar que lo decide `ControlAcceso`.** Un dato es tuyo si lo
   creaste o si vive en una categoria compartida de un hogar al que perteneces.
   Ante la duda, no.
3. **Que movimiento suma lo decide `Computables`.** El hijo es detalle del padre;
   la transferencia no es ingreso ni gasto. Una sola regla para las cinco
   agregaciones.
4. **El esquema lo pone Flyway.** `ddl-auto=validate`. Un cambio en una entidad
   sin su migracion hace que el servicio no arranque, y eso es intencionado.

---

## 8. Estado actual

Medido el 2026-08-11. Las cifras exactas suben con cada tanda de pruebas; lo que
no cambia es que las tres ordenes de abajo tienen que terminar en verde.

- Backend: `mvn -B clean verify` → BUILD SUCCESS, 671 pruebas, cobertura 89-98%
  de lineas segun servicio.
- Frontend: 60 suites, 375 pruebas, ~85% de lineas.
- Quality Gate de SonarQube Cloud: PASSED (0 bugs, 0 vulnerabilidades, 0 hotspots
  sin revisar).

Lo que sigue esta en la Ola 3 de `documentación/auditoria-y-plan-de-estabilizacion.md`
(cuentas con saldo calculado, reparto de gastos entre personas, sobres,
importacion CSV, recurrencias reales, Testcontainers). Y la seccion 4 de ese
mismo documento —lo que **no** hay que hacer— vale tanto como el plan.
