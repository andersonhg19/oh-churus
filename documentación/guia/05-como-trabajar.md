# Cómo trabajar: local, pruebas, ramas y producción

## 1. Levantar en local

Requisitos: Java 17 y Maven, Docker Desktop y Node.js 20 o superior (solo para
el frontend fuera de Docker).

### Todo con Docker (recomendado)

```bash
# en la raíz del repo; el .env es opcional para probar en local
docker compose up --build -d
docker compose ps          # esperar a que auth y budget estén "healthy"
```

Abre `http://localhost:3000`. Para apagar: `docker compose down`. Si añades
`-v`, también borras la base local.

> El `README.md` todavía menciona `fasting-service` (ayuno). Ese servicio ya no
> existe. Los servicios actuales son: postgres, discovery, gateway, auth,
> budget y frontend.

Con la base vacía, los servicios **siembran datos de demo**: usuarios, árbol de
categorías y un hogar de ejemplo. Las credenciales de demo están en
`README.md §4.3`. Son solo para local.

### Frontend sin Docker (lo más cómodo para tocar pantallas)

```bash
cd frontend
npm ci
EXPO_PUBLIC_API_URL=http://localhost:8820 npx expo start   # luego "w" para web
```

La app se usa sobre todo **como web en el celular**. Revisa tus cambios con el
navegador en un ancho de 390 px: los gestos y la dona se diseñaron para eso.

## 2. Pruebas: todas en verde antes de entregar

```bash
# backend (H2 en memoria; las de Testcontainers necesitan Docker)
cd backend && mvn -B clean verify

# frontend
cd frontend && npx tsc --noEmit && npx jest
```

Referencia a la ola 7.5: backend con 586 pruebas en verde y 1 omitida;
frontend con 67 suites y 517 pruebas.

Sobre las pruebas:

- **Las pruebas de Testcontainers se saltan solas si Docker no está
  corriendo.** Un "verde" sin Docker no garantiza que se ejecutaron. Confírmalo
  con `Skipped: 0`.
- **Si cambias un endpoint o un DTO**, falla `LaSpecPublicadaEstaAlDiaTest`. Así
  se regenera la especificación OpenAPI publicada:
  `cd backend && mvn -B test -Dtest=LaSpecPublicadaEstaAlDiaTest -Dactualizar.spec=true`.
  Después revisa el diff y súbelo junto con el cambio.
- **Las migraciones se prueban de verdad.** Las pruebas montan el esquema con
  las mismas migraciones de Flyway que producción (`ddl-auto=validate`), así
  que una migración rota falla aquí.
- **El frontend tiene pruebas de reglas.** Por ejemplo, ningún texto puede
  tener letra menor de 14 px. Si fallan, ajusta tu cambio a la regla; no cambies
  la prueba.
- **Escribe la prueba de la regla nueva.** Nombra la prueba en español,
  describiendo la regla, siguiendo el estilo de las que ya existen
  (`ElCierreDelMesCuadraTest`, `ElPlanDeLaQuincenaTest`, `InicioYDona.test.tsx`…).

## 3. Ramas y PR

- `main` está **protegida**. Para entrar, un cambio tiene que pasar en GitHub
  Actions tres comprobaciones: *Backend (mvn verify + JaCoCo)*,
  *Frontend (tsc + jest)* y *API por el gateway (Karate)*.
- **Estado del CI a noviembre de 2026: está en rojo desde la ola 5.2.**
  - Las pruebas de backend pasan, pero el job *Backend* falla porque
    `budget-service` no llega al **mínimo de cobertura de JaCoCo**
    (`suelo-de-cobertura`).
  - Por eso *Karate* se salta. Además Karate está desactualizado:
    `dashboard.feature` llama endpoints retirados en la ola 7.
  - Mientras esto no se arregle, un PR no puede entrar solo con verde. Avísale a
    Andy. No "arregles" el CI bajando el mínimo de cobertura sin acordarlo:
    lo correcto es agregar pruebas.
- Trabaja en una rama (`mejora/...`, `arreglo/...`) y abre un **Pull Request**
  a `main`, con lo que cambia, por qué, lo que buscaste para confirmar que no
  existía y cómo lo probaste.
- **Commits en español**, con un título que diga lo que cambia para el
  usuario. Mira `git log` para ver el estilo:
  `ola 7.5: lo pagado con el ahorro no se cuenta como gasto del sueldo…`.
- Para renombrar archivos, usa `git mv`.

## 4. Producción

- La app corre en **un PC de la casa**, con Docker Compose, y se publica en la
  red privada de la familia. La base es **PostgreSQL con datos reales**.
- **El despliegue lo hace Andy (o su asistente). Desde una rama no se
  despliega nada.** El flujo es: merge a `main` → respaldo de la base →
  construir imágenes → levantar → prueba de humo → si algo falla, volver a la
  imagen anterior (cada despliegue deja la etiqueta `:antes-<versión>`).
- **Las migraciones se aplican solas al arrancar** (Flyway). Por eso una
  migración mala rompe producción en cuanto se despliega. Reglas:
  - nunca edites una migración que ya existe; crea la siguiente versión
    (`V20__...`, etc.);
  - las migraciones solo añaden: columnas nuevas con `DEFAULT` y tablas nuevas;
  - no borres ni reescribas datos en una migración sin acordarlo antes y sin
    respaldo;
  - si la tabla nueva guarda datos de un usuario, necesita su política RLS en
    la carpeta `postgresql/` (ver `03-backend-mapa.md`).
- **Nunca uses datos reales en pruebas, capturas o el repo.** Si hace falta
  revisar un caso real, Andy lo revisa en una copia de la base, no en
  producción.

## 5. Checklist antes de pedir revisión

- [ ] Busqué si ya existía (procedimiento de `00-LEEME-PRIMERO.md`) y lo dije en el PR.
- [ ] No deshice ninguna decisión de `06-historia-y-decisiones.md`.
- [ ] Backend `mvn -B clean verify` en verde (con Docker).
- [ ] Frontend `npx tsc --noEmit && npx jest` en verde.
- [ ] Si toqué la API: spec regenerada.
- [ ] Si toqué la base: migración nueva (no editada), aditiva, con RLS si aplica.
- [ ] Revisé la pantalla en 390 px de ancho, en tema claro y oscuro.
- [ ] Si cambié una regla o un flujo, actualicé esta guía.
