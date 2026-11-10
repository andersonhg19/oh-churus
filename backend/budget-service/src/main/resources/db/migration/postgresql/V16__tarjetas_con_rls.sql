-- La tarjeta la ve su dueno y su hogar; solo el dueno la escribe (igual que
-- las categorias, V9).
ALTER TABLE oc_budget_card ENABLE ROW LEVEL SECURITY;
ALTER TABLE oc_budget_card FORCE ROW LEVEL SECURITY;
CREATE POLICY p_card ON oc_budget_card
    USING (user_id = oc_usuario_actual()
           OR household_id IN (SELECT oc_mis_hogares()))
    WITH CHECK (user_id = oc_usuario_actual());
