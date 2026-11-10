-- Ola 7.1: el dia de corte de la tarjeta. Con el se sabe que compras entran
-- en el extracto que toca pagar y cuales van para el siguiente.
ALTER TABLE oc_budget_card ADD COLUMN IF NOT EXISTS cutoff_day INTEGER;
