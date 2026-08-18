-- =====================================================================
-- V7 — Sobres: el arrastre, y la unica excepcion
-- =====================================================================
--
-- LA REGLA, en una frase, y es asimetrica a proposito:
--
--   Lo que SOBRA en una categoria se queda en ella para el mes siguiente.
--   Lo que te PASASTE no se arrastra a la categoria: se descuenta de lo que
--   tienes para repartir el mes que viene.
--
-- POR QUE ASIMETRICA. Arrastrar el sobregiro a la propia categoria castiga dos
-- veces: te pasaste 100.000 en Mercado y ademas el mes que viene Mercado
-- empieza con 100.000 menos, asi que para cuadrar tendrias que comer menos que
-- de costumbre. Descontarlo del total a repartir dice la verdad —esa plata
-- salio de algun sitio— y te deja decidir DE DONDE sale, que es justo la
-- decision que un presupuesto tiene que ayudarte a tomar.
--
-- NO HAY TABLA DE ARRASTRE, y es la decision estructural de esta migracion.
-- El arrastre se RECALCULA desde el origen cada vez que se pregunta. Una tabla
-- con el arrastre de cada mes es un dato derivado que puede quedar
-- desincronizado de los movimientos que resume, y cuando eso pasa no hay forma
-- de saber cual miente — el mismo motivo por el que el saldo de una cuenta
-- tampoco se guarda. Ademas, editar un gasto de marzo tendria que reescribir
-- todos los meses siguientes, y basta con que una de esas escrituras falle.

-- ---------------------------------------------------------------------
-- La excepcion, nombrada por su caso de uso
-- ---------------------------------------------------------------------
--
-- Una categoria marcada asi no descuenta su sobregiro del total a repartir:
-- se queda esperando el reembolso.
--
-- Se llama "es dinero que me van a devolver" en la app y no "excluida del
-- arrastre" porque nadie sabe si quiere lo segundo. La gente sabe perfectamente
-- si le van a devolver la plata. El caso real: pusiste la cuenta del almuerzo
-- del equipo y la empresa te lo reembolsa; sin esto, ese mes tu presupuesto
-- entero aparece roto por una plata que ni siquiera era tuya.
--
-- Ojo: cuando el gasto se REPARTE (ola 3.2) esto no hace falta, porque en tu
-- categoria solo entra tu parte. El interruptor es para cuando pagaste el total
-- y no hay con quien repartirlo dentro de la app.
ALTER TABLE oc_budget_category
    ADD COLUMN IF NOT EXISTS reimbursable BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------------
-- Fuera el status que solo se escribia
-- ---------------------------------------------------------------------
--
-- oc_budget_allocation.status nacio para marcar asignaciones cerradas y nunca
-- llego a significar nada: lo unico que lo ponia en algo distinto de 'ACTIVE'
-- era autoCloseExpired(), un metodo sin endpoint y sin @Scheduled al que no
-- llamaba nadie. La consulta que filtraba por status = 'ACTIVE' devolvia por
-- tanto absolutamente todas las filas.
--
-- Se quita en vez de dejarlo por si acaso: una columna que promete un estado y
-- siempre vale lo mismo hace que el siguiente que la lea escriba codigo
-- defensivo contra un caso que no existe. El frontend no la usaba.
ALTER TABLE oc_budget_allocation
    DROP COLUMN IF EXISTS status;
