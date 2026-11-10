-- ============================================================================
-- V8 — El menu deja de apuntar a pantallas que ya no existen
-- ============================================================================
--
-- Andy y Samy retiraron de la aplicacion lo que nadie usaba: cuentas y
-- conciliacion, importar del banco, el ayuno, el consolidado, los sobres, el
-- reparto "entre nosotros" y la busqueda de movimientos como pantalla aparte
-- (ahora es una caja de busqueda dentro de Movimientos). Sus paginas seguian
-- sembradas por la V4, y una pagina cuyo "route" no corresponde con ninguna
-- pantalla es un boton que no lleva a ningun sitio.
--
-- Ademas, cinco rutas de la V4 eran las de las antiguas pestanas de abajo
-- (Dashboard, Summary, Budget, Scheduled, Settings), que desaparecieron cuando
-- la app paso a ser una sola pila al estilo Monefy. Cuatro de esas paginas
-- siguen existiendo con otro nombre de ruta y aqui se corrigen; la de Resumen
-- no tiene sucesora (la dona de Inicio la sustituyo) y se borra.
--
-- POR QUE SE BORRA Y NO SE DESACTIVA: paginas, modulos y permisos son
-- configuracion, no datos de usuario. Una fila inactiva seguiria saliendo en
-- la pantalla de administracion de modulos y alguien podria reactivarla hacia
-- una pantalla que no existe. Los permisos de esas paginas se borran primero
-- (el ON DELETE CASCADE de la V4 lo haria igual, pero asi se lee).
--
-- Una sentencia por linea a proposito: ElMenuLlevaAPantallasQueExistenTest lee
-- los UPDATE ... SET route y los DELETE ... WHERE route de este fichero para
-- saber que rutas de la V4 ya no estan sembradas.
--
-- Sintaxis valida tal cual en PostgreSQL y en H2.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Las paginas de las antiguas pestanas: misma pagina, ruta de hoy
-- ---------------------------------------------------------------------------

UPDATE oc_auth_page SET route = 'Inicio', updated_at = CURRENT_TIMESTAMP WHERE route = 'Dashboard';
UPDATE oc_auth_page SET route = 'Presupuesto', updated_at = CURRENT_TIMESTAMP WHERE route = 'Budget';
UPDATE oc_auth_page SET route = 'ScheduledList', updated_at = CURRENT_TIMESTAMP WHERE route = 'Scheduled';
UPDATE oc_auth_page SET route = 'SettingsMain', updated_at = CURRENT_TIMESTAMP WHERE route = 'Settings';

-- La lista de movimientos sigue existiendo: es la pantalla Movimientos, que
-- ahora trae la busqueda dentro. El permiso de la pagina se conserva.
UPDATE oc_auth_page SET route = 'Movimientos', updated_at = CURRENT_TIMESTAMP WHERE route = 'MovementsList';

-- ---------------------------------------------------------------------------
-- Las paginas retiradas: primero sus permisos, despues ellas
-- ---------------------------------------------------------------------------

DELETE FROM oc_auth_role_permission WHERE page_id IN (SELECT id FROM oc_auth_page WHERE route IN ('Summary', 'Envelopes', 'Consolidated', 'AccountsList', 'Reconcile', 'Balances', 'Import', 'FastingDashboard', 'FastingHistory', 'FastingConfig'));

DELETE FROM oc_auth_page WHERE route = 'Summary';
DELETE FROM oc_auth_page WHERE route = 'Envelopes';
DELETE FROM oc_auth_page WHERE route = 'Consolidated';
DELETE FROM oc_auth_page WHERE route = 'AccountsList';
DELETE FROM oc_auth_page WHERE route = 'Reconcile';
DELETE FROM oc_auth_page WHERE route = 'Balances';
DELETE FROM oc_auth_page WHERE route = 'Import';
DELETE FROM oc_auth_page WHERE route = 'FastingDashboard';
DELETE FROM oc_auth_page WHERE route = 'FastingHistory';
DELETE FROM oc_auth_page WHERE route = 'FastingConfig';

-- ---------------------------------------------------------------------------
-- El modulo de ayuno se queda sin paginas y se va con ellas. Solo si de
-- verdad quedo vacio: si un administrador le colgo una pagina a mano, la FK
-- de oc_auth_page impide borrarlo y es mejor que se quede.
-- ---------------------------------------------------------------------------

DELETE FROM oc_auth_module WHERE code = 'ayuno' AND NOT EXISTS (SELECT 1 FROM oc_auth_page p WHERE p.module_id = oc_auth_module.id);
