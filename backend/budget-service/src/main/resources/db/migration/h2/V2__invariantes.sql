-- ============================================================================
-- INVARIANTES: gemelo para H2 del fichero de db/migration/postgresql
-- ============================================================================
--
-- Este es el mismo V2 que hay en db/migration/postgresql, traducido a H2, que
-- es la base de datos que usan las pruebas. Dice exactamente lo mismo; cambian
-- dos cosas de sintaxis y ninguna de significado:
--
--   · H2 NO tiene indices parciales (CREATE UNIQUE INDEX ... WHERE active).
--     Se consigue lo mismo con una columna calculada que vale la clave cuando
--     la fila esta viva y NULL cuando esta apagada: en un indice unico los
--     NULL no chocan entre si, asi que caben todas las filas apagadas que
--     hagan falta y solo una encendida. La columna sobra para el codigo Java
--     y Hibernate la ignora al validar: solo comprueba que esten las columnas
--     que el mapeo declara.
--
--   · H2 no conoce NOT VALID (anadir una restriccion sin revisar lo que ya
--     hay). Aqui no hace falta: la base de datos de las pruebas nace vacia en
--     cada arranque, no hay pasado que respetar.
--
-- Si tocas el fichero de PostgreSQL, toca este.
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
-- ---------------------------------------------------------------------------
ALTER TABLE oc_budget_allocation
    ADD COLUMN categoria_si_viva BIGINT
        GENERATED ALWAYS AS (CASE WHEN active THEN category_id END);

CREATE UNIQUE INDEX ux_asignacion_viva_categoria_periodo
    ON oc_budget_allocation (categoria_si_viva, period_start);

ALTER TABLE oc_budget_household_member
    ADD COLUMN hogar_si_vivo BIGINT
        GENERATED ALWAYS AS (CASE WHEN active THEN household_id END);

CREATE UNIQUE INDEX ux_miembro_vivo_hogar_usuario
    ON oc_budget_household_member (hogar_si_vivo, user_id);


-- ---------------------------------------------------------------------------
-- 3. Claves foraneas
-- ---------------------------------------------------------------------------
ALTER TABLE oc_budget_household_member
    ADD CONSTRAINT fk_miembro_hogar
        FOREIGN KEY (household_id) REFERENCES oc_budget_household (id);

ALTER TABLE oc_budget_category
    ADD CONSTRAINT fk_categoria_hogar
        FOREIGN KEY (household_id) REFERENCES oc_budget_household (id);

ALTER TABLE oc_budget_category
    ADD CONSTRAINT fk_categoria_padre
        FOREIGN KEY (parent_id) REFERENCES oc_budget_category (id) ON DELETE SET NULL;

ALTER TABLE oc_budget_movement
    ADD CONSTRAINT fk_movimiento_categoria
        FOREIGN KEY (category_id) REFERENCES oc_budget_category (id);

ALTER TABLE oc_budget_movement
    ADD CONSTRAINT fk_movimiento_padre
        FOREIGN KEY (parent_movement_id) REFERENCES oc_budget_movement (id) ON DELETE SET NULL;

ALTER TABLE oc_budget_scheduled_movement
    ADD CONSTRAINT fk_programado_categoria
        FOREIGN KEY (category_id) REFERENCES oc_budget_category (id);

ALTER TABLE oc_budget_allocation
    ADD CONSTRAINT fk_asignacion_categoria
        FOREIGN KEY (category_id) REFERENCES oc_budget_category (id);

ALTER TABLE oc_budget_allocation
    ADD CONSTRAINT fk_asignacion_hogar
        FOREIGN KEY (household_id) REFERENCES oc_budget_household (id);


-- ---------------------------------------------------------------------------
-- 4. Importes (el porque del >= 0 esta en el fichero de PostgreSQL)
-- ---------------------------------------------------------------------------
ALTER TABLE oc_budget_movement
    ADD CONSTRAINT ck_movimiento_importe CHECK (amount >= 0);

ALTER TABLE oc_budget_scheduled_movement
    ADD CONSTRAINT ck_programado_importe CHECK (amount IS NULL OR amount >= 0);

ALTER TABLE oc_budget_allocation
    ADD CONSTRAINT ck_asignacion_importe CHECK (allocated_amount >= 0);

ALTER TABLE oc_budget_allocation
    ADD CONSTRAINT ck_asignacion_periodo CHECK (period_end >= period_start);
