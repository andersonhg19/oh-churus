-- ============================================================================
-- INVARIANTES: las reglas que hasta ahora solo vivian en Java
-- ============================================================================
--
-- Sintaxis valida tal cual en PostgreSQL y en H2: no hace falta un gemelo por
-- motor como en budget-service.
--
-- LO QUE NO ESTA AQUI, Y POR QUE
-- ------------------------------
-- Falta la unicidad de (user_id, log_date) en el diario de agua y de
-- (user_id, code) en los logros. Las dos harian falta —el servicio los busca
-- con un Optional, asi que un duplicado los rompe para siempre igual que
-- pasaba con las asignaciones del presupuesto—, pero ninguna de esas dos
-- tablas tiene columna "active": si la base de datos real ya trajera un
-- duplicado, la unica forma de crear el indice seria BORRAR una fila con
-- vasos de agua que alguien apunto. Eso no lo decide una migracion. Queda
-- pendiente de una limpieza mirada a mano.
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. La sesion apunta a un plan que existe.
--    ON DELETE SET NULL porque el plan no se borra nunca (se apaga con
--    active=false); si alguien lo borrase a mano, el historial de ayunos no
--    tiene por que irse con el.
-- ---------------------------------------------------------------------------
ALTER TABLE oc_fasting_session
    ADD CONSTRAINT fk_sesion_plan
        FOREIGN KEY (plan_config_id) REFERENCES oc_fasting_plan_config (id) ON DELETE SET NULL;


-- ---------------------------------------------------------------------------
-- 2. Las horas de un plan.
--    El servicio ya exige que ayuno + comida sumen 24, pero con planType
--    CUSTOM acepta cualquier pareja que sume 24: un -5 y un 29 pasan la
--    validacion y dejan un plan imposible del que salen sesiones con la hora
--    de fin ANTES que la de inicio. Aqui se cierra.
-- ---------------------------------------------------------------------------
ALTER TABLE oc_fasting_plan_config
    ADD CONSTRAINT ck_plan_horas CHECK (
        fasting_hours BETWEEN 0 AND 24
            AND eating_hours BETWEEN 0 AND 24
            AND fasting_hours + eating_hours = 24);


-- ---------------------------------------------------------------------------
-- 3. Una sesion no puede terminar antes de empezar
-- ---------------------------------------------------------------------------
ALTER TABLE oc_fasting_session
    ADD CONSTRAINT ck_sesion_horas CHECK (fasting_hours BETWEEN 0 AND 24);

ALTER TABLE oc_fasting_session
    ADD CONSTRAINT ck_sesion_fin_previsto CHECK (target_end_time >= start_time);

ALTER TABLE oc_fasting_session
    ADD CONSTRAINT ck_sesion_fin_real CHECK (actual_end_time IS NULL OR actual_end_time >= start_time);


-- ---------------------------------------------------------------------------
-- 4. Vasos de agua: ni negativos ni una meta de cero
-- ---------------------------------------------------------------------------
ALTER TABLE oc_fasting_water_log
    ADD CONSTRAINT ck_agua_vasos CHECK (glasses >= 0);

ALTER TABLE oc_fasting_water_log
    ADD CONSTRAINT ck_agua_meta CHECK (goal_glasses > 0);
