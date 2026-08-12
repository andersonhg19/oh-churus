-- ============================================================================
-- INVARIANTES: las reglas que hasta ahora solo vivian en Java
-- ============================================================================
--
-- El correo unico ya estaba en la linea base (venia del mapeo). Lo que faltaba
-- era lo que solo comprobaba ValidationUtils: el dia de corte del presupuesto
-- es un dia del mes, no un numero cualquiera. Si alguna vez se escribe un 0 o
-- un 45 saltandose el servicio, PeriodUtils calcula un periodo imposible y
-- todas las cifras de esa persona salen mal sin que nada avise.
--
-- Sintaxis valida tal cual en PostgreSQL y en H2: no hace falta un gemelo por
-- motor como en budget-service.
-- ============================================================================

ALTER TABLE oc_auth_user
    ADD CONSTRAINT ck_usuario_dia_de_corte CHECK (budget_start_day BETWEEN 1 AND 31);

ALTER TABLE oc_auth_user
    ADD CONSTRAINT ck_usuario_correo_no_vacio CHECK (CHAR_LENGTH(TRIM(email)) > 0);
