-- Los pagos de una tarjeta solo los ve y los escribe su dueno.
ALTER TABLE oc_budget_card_payment ENABLE ROW LEVEL SECURITY;
ALTER TABLE oc_budget_card_payment FORCE ROW LEVEL SECURITY;
CREATE POLICY p_card_payment ON oc_budget_card_payment
    USING (user_id = oc_usuario_actual())
    WITH CHECK (user_id = oc_usuario_actual());
