-- =====================================================================
-- V5 — "El tercer viernes" y que hacer si cae fin de semana
-- =====================================================================
--
-- Las recurrencias pasaron a enumerarse desde el ancla, y con eso aparecieron
-- dos cosas que un dia del mes no sabe decir:
--
--   · week_of_month + day_of_week: el patron "el TERCER VIERNES", que es como
--     se paga la nomina en Colombia. No hay dia del mes que lo exprese: en
--     agosto es el 21 y en septiembre el 18. El 5 en week_of_month significa
--     "el ultimo", no "el quinto o nada".
--   · weekend_policy: que hacer cuando la ocurrencia cae sabado o domingo.
--     KEEP la deja donde cae, PREVIOUS_BUSINESS_DAY la adelanta al viernes y
--     NEXT_BUSINESS_DAY la atrasa al lunes.
--
-- POR QUE weekend_policy ADMITE NULL Y SE RELLENA CON 'KEEP'
-- ---------------------------------------------------------
-- Los programados que ya existen se crearon cuando esto no se podia elegir.
-- Cualquier otro valor por defecto les moveria la fecha de golpe a todos, y
-- mover la fecha de un gasto le cambia el mes al que pertenece: dos periodos
-- descuadrados por un despliegue. El codigo lee NULL como KEEP, asi que una
-- fila que se cuele sin valor tampoco se mueve.
--
-- POR QUE ESTE FICHERO ES 'comun' Y NO TIENE UN GEMELO POR MOTOR
-- --------------------------------------------------------------
-- El V2 esta partido en dos porque usa indices parciales (solo PostgreSQL) y
-- anade restricciones NOT VALID sobre tablas con datos reales. Aqui no hace
-- falta ninguna de las dos: las tres columnas nacen ahora, asi que no hay
-- ninguna fila que pueda incumplir los CHECK y no hay nada que validar
-- despues. ADD COLUMN IF NOT EXISTS y CHECK los entienden los dos motores.

ALTER TABLE oc_budget_scheduled_movement
    ADD COLUMN IF NOT EXISTS week_of_month INTEGER;

ALTER TABLE oc_budget_scheduled_movement
    ADD COLUMN IF NOT EXISTS day_of_week INTEGER;

ALTER TABLE oc_budget_scheduled_movement
    ADD COLUMN IF NOT EXISTS weekend_policy VARCHAR(30);

UPDATE oc_budget_scheduled_movement
SET weekend_policy = 'KEEP'
WHERE weekend_policy IS NULL;

-- El 5 es "la ultima semana", de ahi que el rango llegue a 5 y no a 4.
ALTER TABLE oc_budget_scheduled_movement
    ADD CONSTRAINT ck_programado_semana_del_mes
        CHECK (week_of_month IS NULL OR week_of_month BETWEEN 1 AND 5);

-- ISO: 1 es lunes y 7 es domingo, igual que java.time.DayOfWeek.
ALTER TABLE oc_budget_scheduled_movement
    ADD CONSTRAINT ck_programado_dia_de_la_semana
        CHECK (day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7);

ALTER TABLE oc_budget_scheduled_movement
    ADD CONSTRAINT ck_programado_politica_finde
        CHECK (weekend_policy IS NULL
               OR weekend_policy IN ('KEEP', 'PREVIOUS_BUSINESS_DAY', 'NEXT_BUSINESS_DAY'));
