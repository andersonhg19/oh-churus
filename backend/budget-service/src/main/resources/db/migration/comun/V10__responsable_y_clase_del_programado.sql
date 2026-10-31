-- =====================================================================
-- V10 — Quien paga cada programado, y que clase de salida de plata es
-- =====================================================================
--
-- En un hogar de dos, "el arriendo" no lo paga "el hogar": lo paga una
-- persona concreta, y la otra paga el gimnasio y la natillera. Hasta ahora el
-- unico "quien" de un programado era quien lo CREO, que no es lo mismo: si
-- Anderson daba de alta el gimnasio de Samy, el pendiente caia en la cuenta de
-- Anderson y el plan de la quincena de Samy no lo veia.
--
-- payer_user_id NULO significa "lo paga quien lo creo", que es exactamente lo
-- que pasaba antes: ningun programado existente cambia de dueno ni de cuenta.
--
-- LA CLASE (kind)
-- ---------------
-- Un plan por quincenas necesita distinguir cuatro destinos de la plata que
-- hasta ahora eran todos "un gasto programado":
--
--   PAYMENT    un pago con fecha: el arriendo, la seguridad social. Genera su
--              pendiente, como siempre.
--   DEDUCTION  un descuento de nomina: el computador que se cobra del sueldo.
--              Genera su pendiente (la plata salio), pero en el plan RESTA DEL
--              INGRESO en vez de sumarse a lo que hay que pagar. Es la unica
--              forma de no descontarlo dos veces.
--   SAVING     ahorro: tambien sale y genera pendiente, pero se ensena aparte
--              y no cuenta como consumo del hogar.
--   PROVISION  plata que se SEPARA, no un pago: el mercado, las mascotas, la
--              prima. NO genera pendiente. Lo real se anota cuando se compra,
--              y asi una reserva nunca se cuenta como gasto y luego otra vez
--              al pagar.
--
-- Nulo se lee como PAYMENT para que lo que ya existia siga comportandose igual.
--
-- notes: donde queda escrito un SUPUESTO ("7 % sobre el bruto, sin
-- confirmar") para que no se pierda en una conversacion.
ALTER TABLE oc_budget_scheduled_movement
    ADD COLUMN IF NOT EXISTS payer_user_id BIGINT;

ALTER TABLE oc_budget_scheduled_movement
    ADD COLUMN IF NOT EXISTS kind VARCHAR(20);

UPDATE oc_budget_scheduled_movement
SET kind = 'PAYMENT'
WHERE kind IS NULL;

ALTER TABLE oc_budget_scheduled_movement
    ADD CONSTRAINT ck_programado_clase
        CHECK (kind IS NULL OR kind IN ('PAYMENT', 'DEDUCTION', 'SAVING', 'PROVISION'));

ALTER TABLE oc_budget_scheduled_movement
    ADD COLUMN IF NOT EXISTS notes VARCHAR(255);

-- ---------------------------------------------------------------------
-- El nombre con el que el hogar llama a cada miembro
-- ---------------------------------------------------------------------
--
-- budget-service no sabe como se llama nadie: los nombres viven en el
-- servicio de autenticacion, y ese solo deja leer la ficha PROPIA. Por eso
-- la pantalla del hogar no podia decir "esto lo paga Samy". El apodo lo pone
-- el hogar ("Andy", "Samy") y no expone el correo ni nada de la cuenta.
ALTER TABLE oc_budget_household_member
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(50);
