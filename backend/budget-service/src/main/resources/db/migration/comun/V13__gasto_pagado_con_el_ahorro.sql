-- Un gasto pagado con plata ya ahorrada (no con lo que entro este mes):
-- se anota en su categoria y dice de que categoria de ahorro salio. Nulo = del dia a dia.
ALTER TABLE oc_budget_movement ADD COLUMN funded_from_category_id BIGINT;
