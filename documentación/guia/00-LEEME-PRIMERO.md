# Oh Churus: léeme primero

> Esta guía es para quien llegue a trabajar en el proyecto **sin conocerlo**:
> una persona o un asistente de código (Claude, Codex…). Léela entera antes de
> proponer cualquier cambio. Está al día a **noviembre de 2026 (ola 7.5)**.

## Qué es, en una frase

Oh Churus es la app de finanzas de **un hogar de dos personas**. Cada una anota
sus gastos e ingresos y además ven juntas lo del hogar: cuánto se gastó por
categoría, cuánto queda libre para gastar, qué pagos vienen, cuánto se debe en
tarjetas y cuánto hay ahorrado.

**Está en PRODUCCIÓN, con datos reales de la familia.** No es un ejercicio.

## Las 6 reglas de oro

1. **Antes de crear algo, busca si ya existe.** La app tiene muchos flujos
   enredados: el mismo concepto (lo libre para gastar, lo gastado en el mes,
   lo que se debe en una tarjeta…) se usa en varias pantallas, y casi todo lo
   que parece que falta ya existe con otro nombre. Sigue el procedimiento de
   abajo, **siempre**, antes de escribir código.
2. **No dañes datos de producción.** Nada de migraciones que borren o
   reescriban datos sin respaldo, nada de "limpiezas" de datos que nadie pidió.
   Una migración de Flyway que ya se aplicó **nunca se edita**: se crea la
   siguiente versión.
3. **No deshagas decisiones de producto.** Muchas cosas que parecen errores
   son decisiones tomadas con los usuarios. Están en
   [06-historia-y-decisiones.md](06-historia-y-decisiones.md). Si algo de ahí
   te parece mal, **pregunta**; no lo "arregles".
4. **Simple.** Los usuarios pidieron una app simple. No agregues cuentas
   bancarias, repartos de gastos entre personas, ni pantallas nuevas si se
   puede resolver dentro de una existente. Varias de esas cosas ya existieron
   y se retiraron a propósito.
5. **Plan primero, código después.** Explica qué archivos vas a tocar, qué
   cambia, qué podría romperse y cómo lo vas a probar. Espera el visto bueno.
6. **Todo con pruebas, y todas en verde.** Hay ~590 pruebas de backend y ~520 de
   frontend. Varias son "pruebas de reglas" (tamaño mínimo de letra, que la
   especificación de la API esté al día, arquitectura…): si una falla, es que
   rompiste una convención, no que la prueba esté mal.

## Procedimiento obligatorio: "¿esto ya existe?"

Antes de implementar cualquier mejora o arreglo:

1. **Busca el concepto en esta guía** (reglas de negocio, mapa de back y de
   front). Busca por la palabra en español **y** en inglés: el código mezcla
   ambos (p. ej. `ahorro` / `saving`, `tarjeta` / `card`, `programado` /
   `scheduled`, `pendiente` / `confirmed=false`, `libre` / `freeToSpend`).
2. **Busca en el código** con grep en `backend/budget-service/src/main` y en
   `frontend/src`. Ejemplos:
   ```bash
   grep -rni "fromSavings\|fundedFromCategoryId" backend frontend/src --include=*.java --include=*.ts --include=*.tsx
   grep -rni "freeToSpend" backend frontend/src
   ```
3. **Mira quién más usa lo que vas a tocar.** Si cambias un cálculo del backend,
   busca qué pantallas muestran ese campo. Si cambias una pantalla, mira si
   otra pantalla muestra el mismo número y debe seguir coincidiendo (p. ej.
   "Libre para gastar" en Inicio y "Queda libre" en el Plan deben decir lo
   mismo).
4. **Mira las pruebas que ya cubren ese flujo** (`backend/budget-service/src/test`,
   `frontend/src/**/__tests__`). Sus nombres están en español y describen la
   regla (`ElCierreDelMesCuadraTest`, `ElPlanDeLaQuincenaTest`…).
5. **Lee `git log` del archivo** (`git log --oneline -- ruta`): los mensajes de
   commit explican por qué está así.
6. Solo entonces, propone el cambio, diciendo explícitamente: *"Busqué X, Y, Z;
   existe/no existe en tal sitio; lo voy a reutilizar/extender así"*.

Si encuentras que algo existe dos veces (dos cálculos del mismo total, dos
pantallas que hacen lo mismo), **no agregues una tercera**: dilo y propón
unificar.

## Índice de la guía

| Archivo | Para qué |
|---|---|
| [01-que-hace-la-app.md](01-que-hace-la-app.md) | La app desde el punto de vista de quien la usa: pantallas, gestos, conceptos |
| [02-reglas-de-negocio.md](02-reglas-de-negocio.md) | Cómo se calcula cada cosa y en qué archivo/método vive |
| [03-backend-mapa.md](03-backend-mapa.md) | Servicios, endpoints, tablas, migraciones, seguridad (RLS) |
| [04-frontend-mapa.md](04-frontend-mapa.md) | Navegación, pantallas, componentes, servicios, convenciones de UI |
| [05-como-trabajar.md](05-como-trabajar.md) | Levantar en local, correr pruebas, ramas, despliegue y producción |
| [06-historia-y-decisiones.md](06-historia-y-decisiones.md) | Cómo llegó la app hasta aquí y decisiones que no se deben deshacer |

## Documentos viejos

`README.md`, `documentación/*.md` (fuera de esta carpeta) y `seguimiento/`
cuentan la historia del proyecto pero **están desactualizados en partes**:
hablan de un módulo de ayuno, cuentas, repartos, transferencias e importación
de extractos que ya no existen. Si contradicen a esta guía o al código, **manda
el código**, y después esta guía. El inventario de qué sigue vigente está al
final de [06-historia-y-decisiones.md](06-historia-y-decisiones.md).

## El repositorio es PÚBLICO

No subas datos reales: ni volcados de la base, ni capturas con movimientos de
verdad, ni montos, correos, contraseñas, IPs o nombres de máquinas. En los
ejemplos usa cifras inventadas.
