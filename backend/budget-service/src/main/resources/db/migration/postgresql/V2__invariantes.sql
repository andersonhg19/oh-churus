-- ============================================================================
-- INVARIANTES: las reglas que el codigo daba por supuestas (PostgreSQL)
-- ============================================================================
--
-- Hay un gemelo de este fichero en db/migration/h2 para las pruebas. Dice lo
-- MISMO con otra sintaxis: PostgreSQL tiene indices parciales (WHERE active) y
-- H2 no. Si tocas uno, toca el otro.
--
-- POR QUE ESTO EXISTE
-- -------------------
-- 1. El presupuesto hace un "upsert": busca la asignacion de (categoria,
--    periodo) y si no esta la crea. Dos toques seguidos entraban a la vez, los
--    dos veian "no hay", y los dos insertaban. A partir de ese momento el
--    Optional del servicio encontraba DOS filas y lanzaba
--    NonUniqueResultException en esa categoria para siempre, sin forma de
--    arreglarlo desde la app.
-- 2. Lo mismo con los miembros del hogar: la comprobacion "ya es miembro" vive
--    en Java y no protege de dos invitaciones simultaneas.
--
-- CUIDADO CON LOS DATOS REALES
-- ----------------------------
-- Esta migracion corre sobre la base de datos de desarrollo, que tiene datos
-- de verdad. Por eso:
--   · Los duplicados que YA existan no se borran: se marcan active=false
--     quedandose la fila mas reciente. Ninguna fila desaparece.
--   · Las claves foraneas y los CHECK se anaden NOT VALID: obligan a todo lo
--     que se escriba de ahora en adelante y no revisan lo viejo. Si hubiera
--     una fila incoherente de antes, la aplicacion arranca igual en vez de
--     quedarse sin arrancar. Se pueden validar despues, cuando se sepa que la
--     base esta limpia, con ALTER TABLE ... VALIDATE CONSTRAINT.
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. Desactivar duplicados ya existentes (se conserva el mas reciente)
-- ---------------------------------------------------------------------------
UPDATE oc_budget_allocation a
SET active = FALSE
WHERE a.active
  AND EXISTS (SELECT 1
              FROM oc_budget_allocation b
              WHERE b.active
                AND b.category_id = a.category_id
                AND b.period_start = a.period_start
                AND b.id > a.id);

UPDATE oc_budget_household_member m
SET active = FALSE
WHERE m.active
  AND EXISTS (SELECT 1
              FROM oc_budget_household_member n
              WHERE n.active
                AND n.household_id = m.household_id
                AND n.user_id = m.user_id
                AND n.id > m.id);


-- ---------------------------------------------------------------------------
-- 2. Unicidad de lo que esta VIVO
--    Parcial a proposito: el borrado de esta app es logico, asi que puede
--    haber tantas filas apagadas como quiera para la misma clave; lo que no
--    puede haber es dos encendidas.
-- ---------------------------------------------------------------------------
CREATE UNIQUE INDEX ux_asignacion_viva_categoria_periodo
    ON oc_budget_allocation (category_id, period_start)
    WHERE active;

CREATE UNIQUE INDEX ux_miembro_vivo_hogar_usuario
    ON oc_budget_household_member (household_id, user_id)
    WHERE active;


-- ---------------------------------------------------------------------------
-- 3. Claves foraneas
--    Los "padres" (categoria padre, movimiento padre) van con ON DELETE SET
--    NULL: la app nunca borra de verdad, pero una limpieza manual no tiene por
--    que quedarse bloqueada por el detalle que cuelga de la fila que borra.
-- ---------------------------------------------------------------------------
ALTER TABLE oc_budget_household_member
    ADD CONSTRAINT fk_miembro_hogar
        FOREIGN KEY (household_id) REFERENCES oc_budget_household (id) NOT VALID;

ALTER TABLE oc_budget_category
    ADD CONSTRAINT fk_categoria_hogar
        FOREIGN KEY (household_id) REFERENCES oc_budget_household (id) NOT VALID;

ALTER TABLE oc_budget_category
    ADD CONSTRAINT fk_categoria_padre
        FOREIGN KEY (parent_id) REFERENCES oc_budget_category (id) ON DELETE SET NULL NOT VALID;

ALTER TABLE oc_budget_movement
    ADD CONSTRAINT fk_movimiento_categoria
        FOREIGN KEY (category_id) REFERENCES oc_budget_category (id) NOT VALID;

ALTER TABLE oc_budget_movement
    ADD CONSTRAINT fk_movimiento_padre
        FOREIGN KEY (parent_movement_id) REFERENCES oc_budget_movement (id) ON DELETE SET NULL NOT VALID;

ALTER TABLE oc_budget_scheduled_movement
    ADD CONSTRAINT fk_programado_categoria
        FOREIGN KEY (category_id) REFERENCES oc_budget_category (id) NOT VALID;

ALTER TABLE oc_budget_allocation
    ADD CONSTRAINT fk_asignacion_categoria
        FOREIGN KEY (category_id) REFERENCES oc_budget_category (id) NOT VALID;

ALTER TABLE oc_budget_allocation
    ADD CONSTRAINT fk_asignacion_hogar
        FOREIGN KEY (household_id) REFERENCES oc_budget_household (id) NOT VALID;


-- ---------------------------------------------------------------------------
-- 4. Importes
--    Se exige >= 0 y no > 0, y no es pereza: un programado sin importe (una
--    factura de luz, que cambia cada mes) genera su pendiente en 0 y la
--    persona lo rellena al confirmarlo. Prohibir el cero romperia ese flujo
--    que hoy funciona. Lo que no tiene sentido en ningun caso es un importe
--    negativo: invierte la suma entera sin avisar.
-- ---------------------------------------------------------------------------
ALTER TABLE oc_budget_movement
    ADD CONSTRAINT ck_movimiento_importe CHECK (amount >= 0) NOT VALID;

ALTER TABLE oc_budget_scheduled_movement
    ADD CONSTRAINT ck_programado_importe CHECK (amount IS NULL OR amount >= 0) NOT VALID;

ALTER TABLE oc_budget_allocation
    ADD CONSTRAINT ck_asignacion_importe CHECK (allocated_amount >= 0) NOT VALID;

ALTER TABLE oc_budget_allocation
    ADD CONSTRAINT ck_asignacion_periodo CHECK (period_end >= period_start) NOT VALID;
