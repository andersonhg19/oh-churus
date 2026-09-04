-- ============================================================================
-- V6 — La auditoria tambien es una pantalla
-- ============================================================================
--
-- La V5 creo la tabla y el endpoint, pero un registro que solo se puede
-- consultar con Postman no lo consulta nadie. Aqui entra en el menu del modulo
-- de administracion, con permiso SOLO DE LECTURA para ADMIN.
--
-- can_create, can_update y can_delete van en falso a proposito y no por
-- descuido: una linea de auditoria que se puede editar o borrar desde la
-- aplicacion no prueba nada. El unico borrado que existe es el de retencion
-- por antiguedad, y lo hace un proceso, no una persona.
-- ============================================================================

INSERT INTO oc_auth_page (module_id, code, name, route, icon, in_menu, sort_order, active, created_at, updated_at)
SELECT m.id, 'admin-auditoria', 'Auditoria', 'AdminAudit', 'file-text', TRUE, 4, TRUE,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM oc_auth_module m
WHERE m.code = 'administracion';

INSERT INTO oc_auth_role_permission (role_id, page_id, can_read, can_create, can_update, can_delete, active, created_at, updated_at)
SELECT r.id, p.id, TRUE, FALSE, FALSE, FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM oc_auth_role r
CROSS JOIN oc_auth_page p
WHERE r.name = 'ADMIN'
  AND p.code = 'admin-auditoria';
