# AGENTS.md: Oh Churus

Estas instrucciones son para cualquier asistente de código (Codex, Claude…) cuando
trabaja en este repositorio. **Responde siempre en español.**

## Antes de nada

1. Lee **`documentación/guia/00-LEEME-PRIMERO.md`** y, según la tarea, el resto
   de la guía (`documentación/guia/`). Es la fuente de verdad del proyecto
   después del código.
2. Esta app está **en PRODUCCIÓN con datos reales de una familia**. No hagas
   nada que pueda dañar o borrar datos.

## Regla principal: valida si ya existe ANTES de cambiar nada

La app tiene muchos flujos enredados y conceptos que se calculan o se muestran
en varios sitios. Antes de proponer o escribir cualquier cambio:

1. Busca el concepto en la guía y en el código, en español **y** en inglés
   (`ahorro/saving`, `tarjeta/card`, `programado/scheduled`, `libre/freeToSpend`…).
2. Encuentra todos los sitios que ya lo usan: back, front y pruebas.
3. Revisa `git log -- <archivo>` para entender por qué está así.
4. Di explícitamente qué buscaste, qué encontraste y si vas a reutilizar,
   extender o crear algo nuevo, y por qué.
5. Si algo ya existe dos veces, no crees una tercera: avisa y propón unificar.

## Forma de trabajar

- **Primero el plan**: archivos a tocar, qué cambia, riesgos y cómo se va a
  validar. Espera aprobación antes de implementar.
- **Cambios mínimos.** No refactorices lo que funciona si nadie lo pidió.
- **No deshagas decisiones de producto**
  (`documentación/guia/06-historia-y-decisiones.md`). Si dudas, pregunta.
- **App simple.** No reintroduzcas lo que se retiró a propósito: cuentas,
  repartos entre personas, transferencias, ayuno, importación de extractos.
- **Migraciones Flyway**: nunca edites una existente; crea la siguiente versión,
  aditiva, con su política RLS en `postgresql/` si guarda datos de usuario.
- **Pruebas en verde** antes de entregar: `cd backend && mvn -B clean verify`
  y `cd frontend && npx tsc --noEmit && npx jest`. Escribe la prueba de cada
  regla nueva, en español, con el estilo de las existentes.
- Trabaja en una rama y entrega por Pull Request (`main` está protegida). **No
  despliegues a producción**: eso lo hace el dueño del servidor.
- Para renombrar archivos, usa `git mv`.
- **El repositorio es público**: nunca subas datos reales, montos, correos,
  contraseñas, `.env`, IPs ni nombres de máquinas.
- Si cambias una regla o un flujo, **actualiza la guía** en el mismo PR.

## Mapa rápido

- `backend/budget-service`: todo el dominio financiero (Spring Boot + PostgreSQL con RLS).
- `backend/auth-service`: usuarios, JWT y hogar.
- `backend/gateway-service` y `backend/discovery-service`: entrada de la API (gateway) y registro de servicios (Eureka).
- `frontend/`: Expo React Native, usada sobre todo como web en el celular.
- `documentación/guia/`: la guía actual. El resto de `documentación/`,
  `seguimiento/` y el `README.md` son en parte históricos.
