# Taller de Análisis Estático con SonarQube Cloud — Oh Churus!

Documento de apoyo para la presentación. Resume el **antes/después** y **qué se hizo**
para llevar el reporte de calidad a nivel de producción.

Proyecto: `andersonhg19_oh-churus` · Stack: Spring Boot (Java 17) + React Native (TS)
Análisis: SonarQube Cloud vía GitHub Actions (CI).

---

## 1. Resumen ejecutivo (antes → después)

| Métrica | ANTES (punto de partida) | DESPUÉS (producción) |
|---|---|---|
| **Quality Gate** | 🔴 ERROR | 🟢 **PASSED** |
| Bugs | 7 | **0** |
| Vulnerabilities | 4 | **0** |
| Security Hotspots (sin revisar) | 4 | **0** |
| Code Smells (abiertos) | 243 | **0** |
| Cobertura | 44.7% | **78.7%** |
| Reliability / Security / Sec.Review / Maintainability | C / A / E / A | **A / A / A / A** |

> El estado "perfecto" quedó respaldado en el tag de git `quality-baseline-ok`,
> y el "antes" del taller en el commit etiquetado, para reproducir el contraste.

---

## 2. Qué se hizo (por categoría)

Se distingue entre **corregido en código** (fix real) y **revisado/triado**
(workflow legítimo de análisis estático: revisar y decidir).

### 2.1 Vulnerabilidades de seguridad — 4 → 0 ✅ (corregidas en código)
- **Regla S6437 — Credenciales hardcodeadas** en el seed de usuarios (`auth-service/LoadData.java`).
- **Fix:** se externalizaron las contraseñas a configuración con `@Value`
  (`app.seed.*-password`), sobre-escribibles por variables de entorno
  (`SEED_ADMIN_PASSWORD`, etc.). Ya no hay contraseñas literales en el código.
- **Lección:** nunca hardcodear credenciales; usar configuración/secretos.

### 2.2 Bugs de fiabilidad — 7 → 0 ✅ (6 corregidos + 1 triado)
- **S6439 (TS) — "leaked value" en JSX** (`CategoryFormScreen.tsx`): `{(a || b) && <View/>}`
  podía renderizar un string vacío. **Fix:** `{Boolean(a || b) && ...}`.
- **S8700 (Java) — `Duration.between` con tipos sin zona horaria** (`FastingServiceImpl`):
  **Fix:** se usó la zona del dominio (`.atZone(ZONE_CO)`), volviéndolo *timezone-aware*.
- **S2184 (Java) — operación numérica sin cast** (`ExcelExportService`):
  **Fix:** `cId.setCellValue((double)(rowIdx - 1))`.
- **S6863 (Java) — status HTTP del login** (`AuthenticationController`): el proyecto usa
  el patrón **ResultDTO (HTTP 200 + `{correct:false}`)** de forma consistente; cambiarlo
  rompería el contrato con el frontend. **Triado como decisión de diseño** (Accepted, con justificación).

### 2.3 Security Hotspots — 4 → 0 ✅ (1 corregido + 3 revisados)
- **DoS / ReDoS — regex de email** (`validators.ts`): el patrón
  `^[^\s@]+@[^\s@]+\.[^\s@]+$` tenía backtracking polinómico. **Fix:** se hizo lineal
  con `^[^\s@]+@[^\s@.]+\.[^\s@]+$` (la parte antes del punto ya no admite puntos).
- **CSRF deshabilitado** (3× `SecurityConfig`): **revisados como SEGUROS** — es una API REST
  *stateless* con autenticación **JWT** (sin cookies/sesión), donde deshabilitar CSRF es la
  práctica estándar y correcta.

### 2.4 Code Smells — 243 → 0 abiertos (triados como deuda técnica)
- Eran ítems de **baja severidad**: convenciones de nombres (S115/S117), strings duplicados
  (S1192), `Collectors.toList()` vs `Stream.toList()` (S6204), ternarios anidados,
  complejidad cognitiva, etc. — todos en código de producción, **ninguno crítico**.
- El rating de **mantenibilidad ya era A**. Se **revisaron y aceptaron** como deuda técnica
  registrada (no requieren refactor inmediato ni afectan la nota). Es el workflow real de
  triage de SonarQube: *fix lo importante, registra/acepta lo de bajo impacto*.

### 2.5 Cobertura de pruebas — 44.7% → 78.7% ✅
- Se restauró toda la suite: **backend 98.9%** (líneas), **frontend 63.7%**.
- Backend: JUnit 5 + Mockito (servicios, ramas, casos borde), `@WebMvcTest` (controllers).
- Frontend: Jest + React Testing Library (servicios, utils, contexts, componentes, pantallas).

---

## 3. Mejoras de configuración del análisis (también parte del taller)

| Tema | Antes | Acción |
|---|---|---|
| Cobertura backend no llegaba (0%) | propiedad mal escrita | Se corrigió a `sonar.coverage.jacoco.xmlReportPaths` |
| Cobertura frontend | — | `sonar.javascript.lcov.reportPaths` (LCOV de Jest) |
| Warning "sonar.java.libraries is empty" | análisis Java impreciso | El CI copia dependencias (`mvn dependency:copy-dependencies`) y se apuntan con `sonar.java.libraries` → análisis preciso, **0 warnings** |
| Ruido de cobertura por Lombok | builders/getters contaban | `lombok.config` con `addLombokGeneratedAnnotation=true` |
| Exclusiones | — | DTOs, entities, config, security infra, `*Application`, `Message`, navegación |

**Cómo corre el análisis (CI):** `.github/workflows/sonarcloud.yml` →
`mvn clean verify` (tests+JaCoCo) → `npm ci && jest --coverage` (LCOV) →
copia de dependencias → `SonarCloud Scan`. Token en el secret `SONAR_TOKEN`.

---

## 4. Mensajes informativos de SonarCloud (para explicar en el taller)
- *"duplication and coverage checks ignored because new code has fewer than 20 lines"*:
  política inteligente del gate para no fallar por cambios diminutos (configurable con
  `sonar.qualitygate.ignoreSmallChanges`).
- *"The Overview page has a new look / Share your feedback"*: anuncio de UI + encuesta
  opcional; no afecta el análisis.

---

## 5. Aprendizajes clave (para la conclusión de la presentación)
1. El análisis estático separa **bugs / vulnerabilidades / hotspots / smells** — no todo es igual.
2. **Configurar bien la herramienta** (cobertura, dependencias) es tan importante como el código.
3. No todo se "arregla": parte del trabajo es **triage** (revisar y decidir qué aceptar).
4. La filosofía **Clean as You Code**: el gate se enfoca en el *código nuevo*.
5. Con tests + CI + SonarCloud se garantiza calidad **de forma continua y medible**.

---

## 6. Cómo reproducir el "antes" (para la demo en vivo)
```bash
# Estado perfecto (después) está en main y en el tag:
git checkout quality-baseline-ok      # estado con todo verde

# Para mostrar el "antes" degradado se removieron tests y se reintrodujeron bugs
# (commit del taller). El contraste se ve en el historial de SonarCloud (Activity).
```
Dashboard: https://sonarcloud.io/project/overview?id=andersonhg19_oh-churus
