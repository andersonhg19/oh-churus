-- =====================================================================
-- V3 — La columna period_start de los movimientos, para la base HEREDADA
-- =====================================================================
--
-- Esta migracion existe por un agujero entre dos cambios que se hicieron por
-- separado y que juntos impedian ARRANCAR contra la base de datos real:
--
--   · Se anadio Movement.periodStart (la clave de idempotencia de las
--     recurrencias dejo de ser la fecha, que el usuario puede mover).
--   · Se anadio Flyway con baseline-on-migrate y ddl-auto=validate.
--
-- El detalle que se escapo: sobre una base que YA tiene tablas, Flyway la
-- marca como baselined en la version 1 y NO ejecuta la V1. Como la columna
-- solo estaba declarada en la V1 (el volcado del esquema actual) y la V2 no la
-- anade, en la base heredada la columna nunca aparecia. Hibernate, ahora en
-- validate, se negaba a arrancar: "missing column [period_start] in table
-- [oc_budget_movement]". No se detecto antes porque la base "heredada" con la
-- que se probo se genero con el codigo que YA traia la columna.
--
-- IF NOT EXISTS: en una instalacion limpia y en el H2 de las pruebas la
-- columna ya viene de la V1, asi que aqui no hace nada. Solo actua sobre la
-- base que venia de antes.
--
-- Se deja NULL a proposito en las filas existentes: la consulta
-- existeOcurrenciaDelPeriodo tiene una clausula de compatibilidad que trata el
-- periodo nulo cayendo de vuelta a la fecha, para que el primer refresco tras
-- el despliegue no duplique los pendientes vivos.

ALTER TABLE oc_budget_movement
    ADD COLUMN IF NOT EXISTS period_start DATE;
