-- ============================================================================
-- V9 — Row Level Security: la unica capa que convierte "no deberia" en "no puede"
-- ============================================================================
--
-- La ola 2 saco el userId del token y la matriz de aislamiento comprueba, caso
-- a caso, que nadie llega a los datos de otro. Eso es una promesa del CODIGO:
-- vale mientras cada consulta nueva se acuerde de filtrar. La primera que se
-- olvide devuelve datos de todo el mundo y la matriz no lo vera, porque la
-- matriz prueba los endpoints que ya existen.
--
-- Esto lo mueve a la base de datos: aunque la consulta no filtre, PostgreSQL
-- no entrega la fila.
--
-- -------------------------------------------------------------------------
-- POR QUE ESTA MIGRACION NO CAMBIA NADA HOY, Y ESO ES LO CORRECTO
-- -------------------------------------------------------------------------
-- Un SUPERUSUARIO se salta RLS siempre, incluso con FORCE. La aplicacion
-- conecta hoy como "postgres", que lo es, asi que al aplicar esto todo sigue
-- funcionando exactamente igual: las politicas quedan puestas y dormidas.
--
-- Se activan el dia que la conexion pase a un rol normal. Eso es un cambio de
-- credenciales —de despliegue, no de codigo— y esta escrito al final del
-- fichero. Se hace asi a proposito: una migracion que ademas cambia como se
-- conecta la aplicacion es una migracion que puede dejarla sin base de datos,
-- y este proyecto tiene datos reales dentro.
--
-- Mientras tanto, ComprobacionDeRls avisa en el arranque de que la proteccion
-- esta puesta pero no activa, para que no se confunda "existe" con "protege".
--
-- -------------------------------------------------------------------------
-- LO QUE LAS POLITICAS TIENEN QUE RESPETAR
-- -------------------------------------------------------------------------
-- Aqui los datos NO son solo personales: dos personas de un mismo hogar ven
-- los movimientos que caen en categorias compartidas —de ahi sale el
-- consolidado—. Una politica de "solo lo tuyo" seria mas simple y romperia la
-- mitad de la aplicacion.
--
-- La regla real, la misma que ya usan las @Query del repositorio:
--   · lo tuyo siempre, y
--   · lo que cuelga de una categoria de un hogar del que eres miembro.
--
-- Las funciones son STABLE y SECURITY DEFINER porque tienen que poder leer
-- household_member y category SIN que se les aplique la politica de esas
-- mismas tablas: si no, la politica de movement necesitaria leer category, y
-- la de category necesitaria leer household_member, y la de household_member
-- volveria a preguntar... Es la recursion clasica de RLS, y se corta aqui.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Quien pregunta
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION oc_usuario_actual() RETURNS bigint
LANGUAGE sql STABLE AS $$
    SELECT NULLIF(current_setting('app.user_id', true), '')::bigint
$$;

COMMENT ON FUNCTION oc_usuario_actual() IS
    'El usuario de la peticion en curso. NULL si nadie lo puso: entonces las politicas no dejan ver nada, que es el fallo seguro.';

CREATE OR REPLACE FUNCTION oc_mis_hogares() RETURNS SETOF bigint
LANGUAGE sql STABLE SECURITY DEFINER AS $$
    SELECT hm.household_id
    FROM oc_budget_household_member hm
    WHERE hm.user_id = oc_usuario_actual()
      AND hm.active = true
$$;

CREATE OR REPLACE FUNCTION oc_categorias_de_mis_hogares() RETURNS SETOF bigint
LANGUAGE sql STABLE SECURITY DEFINER AS $$
    SELECT c.id
    FROM oc_budget_category c
    WHERE c.household_id IN (SELECT oc_mis_hogares())
      AND c.active = true
$$;

-- ---------------------------------------------------------------------------
-- Las politicas
--
-- FORCE ademas de ENABLE: sin FORCE, el DUENO de la tabla se salta su propia
-- politica, y el dueno es justamente quien creo el esquema.
-- ---------------------------------------------------------------------------

-- Lo estrictamente personal: nadie mas, ni del hogar.
ALTER TABLE oc_budget_import_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE oc_budget_import_profile FORCE ROW LEVEL SECURITY;
CREATE POLICY p_import_profile ON oc_budget_import_profile
    USING (user_id = oc_usuario_actual())
    WITH CHECK (user_id = oc_usuario_actual());

ALTER TABLE oc_budget_import_rule ENABLE ROW LEVEL SECURITY;
ALTER TABLE oc_budget_import_rule FORCE ROW LEVEL SECURITY;
CREATE POLICY p_import_rule ON oc_budget_import_rule
    USING (user_id = oc_usuario_actual())
    WITH CHECK (user_id = oc_usuario_actual());

ALTER TABLE oc_budget_scheduled_movement ENABLE ROW LEVEL SECURITY;
ALTER TABLE oc_budget_scheduled_movement FORCE ROW LEVEL SECURITY;
CREATE POLICY p_scheduled ON oc_budget_scheduled_movement
    USING (user_id = oc_usuario_actual())
    WITH CHECK (user_id = oc_usuario_actual());

-- Lo que se comparte por hogar.
ALTER TABLE oc_budget_category ENABLE ROW LEVEL SECURITY;
ALTER TABLE oc_budget_category FORCE ROW LEVEL SECURITY;
CREATE POLICY p_category ON oc_budget_category
    USING (user_id = oc_usuario_actual()
           OR household_id IN (SELECT oc_mis_hogares()))
    WITH CHECK (user_id = oc_usuario_actual());

ALTER TABLE oc_budget_account ENABLE ROW LEVEL SECURITY;
ALTER TABLE oc_budget_account FORCE ROW LEVEL SECURITY;
CREATE POLICY p_account ON oc_budget_account
    USING (user_id = oc_usuario_actual()
           OR household_id IN (SELECT oc_mis_hogares()))
    WITH CHECK (user_id = oc_usuario_actual());

ALTER TABLE oc_budget_allocation ENABLE ROW LEVEL SECURITY;
ALTER TABLE oc_budget_allocation FORCE ROW LEVEL SECURITY;
CREATE POLICY p_allocation ON oc_budget_allocation
    USING (user_id = oc_usuario_actual()
           OR household_id IN (SELECT oc_mis_hogares()))
    WITH CHECK (user_id = oc_usuario_actual());

/* El movimiento no guarda hogar: se comparte a traves de su CATEGORIA, que es
   exactamente lo que hacen las consultas del consolidado desde la ola 2. Si
   esta politica dijera solo "lo tuyo", el consolidado del hogar se quedaria a
   la mitad sin que nada fallara — que es la peor forma de romperse. */
ALTER TABLE oc_budget_movement ENABLE ROW LEVEL SECURITY;
ALTER TABLE oc_budget_movement FORCE ROW LEVEL SECURITY;
CREATE POLICY p_movement ON oc_budget_movement
    USING (user_id = oc_usuario_actual()
           OR category_id IN (SELECT oc_categorias_de_mis_hogares()))
    WITH CHECK (user_id = oc_usuario_actual());

/* El reparto se ve si se ve el movimiento al que pertenece: es la lista de a
   quien le toca cada parte, y esconderla dejaria el consolidado sin poder
   explicar sus propias cifras. */
ALTER TABLE oc_budget_movement_split ENABLE ROW LEVEL SECURITY;
ALTER TABLE oc_budget_movement_split FORCE ROW LEVEL SECURITY;
CREATE POLICY p_movement_split ON oc_budget_movement_split
    USING (user_id = oc_usuario_actual()
           OR movement_id IN (SELECT m.id FROM oc_budget_movement m
                              WHERE m.user_id = oc_usuario_actual()
                                 OR m.category_id IN (SELECT oc_categorias_de_mis_hogares())))
    WITH CHECK (true);

/* Los miembros del hogar: se ven los de TUS hogares. Sin esto, la pantalla del
   nucleo familiar dejaria de poder decir quien mas esta dentro. */
ALTER TABLE oc_budget_household_member ENABLE ROW LEVEL SECURITY;
ALTER TABLE oc_budget_household_member FORCE ROW LEVEL SECURITY;
CREATE POLICY p_household_member ON oc_budget_household_member
    USING (user_id = oc_usuario_actual()
           OR household_id IN (SELECT oc_mis_hogares()))
    WITH CHECK (true);

/* El hogar en si no tiene dueno: se ve si eres miembro. El WITH CHECK abierto
   es a proposito — hay que poder CREAR un hogar del que todavia no eres
   miembro, porque el miembro se inserta justo despues. */
ALTER TABLE oc_budget_household ENABLE ROW LEVEL SECURITY;
ALTER TABLE oc_budget_household FORCE ROW LEVEL SECURITY;
CREATE POLICY p_household ON oc_budget_household
    USING (id IN (SELECT oc_mis_hogares()))
    WITH CHECK (true);

-- ---------------------------------------------------------------------------
-- COMO SE ACTIVA (no lo hace esta migracion; ver arriba el por que)
-- ---------------------------------------------------------------------------
--
--   CREATE ROLE oc_app LOGIN PASSWORD '...';        -- NO superusuario
--   GRANT CONNECT ON DATABASE budget_db TO oc_app;
--   GRANT USAGE ON SCHEMA public TO oc_app;
--   GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO oc_app;
--   GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO oc_app;
--   ALTER DEFAULT PRIVILEGES IN SCHEMA public
--       GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO oc_app;
--
-- y despues, en el .env, SPRING_DATASOURCE_USERNAME=oc_app.
--
-- FLYWAY SE QUEDA CON EL ROL DE SIEMPRE. Migrar exige ser dueno de las tablas,
-- y darle esa capacidad al rol con el que corre la aplicacion anularia media
-- proteccion: quien puede hacer ALTER TABLE puede quitar la politica.
-- Con spring.flyway.user / spring.flyway.password se le da a Flyway el suyo.
-- ============================================================================
