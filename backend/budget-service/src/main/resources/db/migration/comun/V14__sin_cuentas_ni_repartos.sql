-- Ola 7: la app deja de usar cuentas, repartos, importacion y transferencias
-- entre categorias. El codigo ya no escribe esas columnas, asi que la unica
-- que lo exigia (is_transfer, NOT NULL sin valor por defecto) pasa a tener uno.
--
-- No se borra nada todavia: las tablas y columnas viejas se quedan hasta la
-- siguiente version para que volver a la imagen anterior siga funcionando.
ALTER TABLE oc_budget_movement ALTER COLUMN is_transfer SET DEFAULT FALSE;
