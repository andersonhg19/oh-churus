-- =====================================================================
-- V11 — Una ocurrencia de un programado, un solo movimiento
-- =====================================================================
--
-- El panel genera los pendientes al abrirse. Si Andy y Samy lo abren a la vez,
-- los dos ven la misma ocurrencia "faltante" y los dos la crean: nada en la
-- base lo impedia. Este indice lo impide; la segunda insercion falla y el
-- servicio la trata como "ya estaba".
--
-- Antes de crearlo se limpian los duplicados que ya existan, dejando el mejor
-- de cada pareja: el confirmado antes que el pendiente, el vivo antes que el
-- borrado y, a igualdad, el mas antiguo. Lo que se borra es siempre la copia.
-- (Solo PostgreSQL: H2, el de las pruebas, no tiene indices parciales.)

DELETE FROM oc_budget_movement_split s
 USING oc_budget_movement m, oc_budget_movement k
 WHERE s.movement_id = m.id
   AND m.scheduled_movement_id IS NOT NULL AND m.period_start IS NOT NULL
   AND k.scheduled_movement_id = m.scheduled_movement_id
   AND k.period_start = m.period_start
   AND k.id <> m.id
   AND (  (k.confirmed AND NOT m.confirmed)
       OR (k.confirmed = m.confirmed AND k.active AND NOT m.active)
       OR (k.confirmed = m.confirmed AND k.active = m.active AND k.id < m.id));

DELETE FROM oc_budget_movement m
 USING oc_budget_movement k
 WHERE m.scheduled_movement_id IS NOT NULL AND m.period_start IS NOT NULL
   AND k.scheduled_movement_id = m.scheduled_movement_id
   AND k.period_start = m.period_start
   AND k.id <> m.id
   AND (  (k.confirmed AND NOT m.confirmed)
       OR (k.confirmed = m.confirmed AND k.active AND NOT m.active)
       OR (k.confirmed = m.confirmed AND k.active = m.active AND k.id < m.id));

CREATE UNIQUE INDEX IF NOT EXISTS ux_movement_ocurrencia
    ON oc_budget_movement (scheduled_movement_id, period_start)
 WHERE scheduled_movement_id IS NOT NULL AND period_start IS NOT NULL;
