/*
 * Fechas como se dicen en voz alta: "6 nov", "mar 2027". Las de utils/format
 * escriben "06/11", que en una lista de compras o de cuotas obliga a pensar
 * si es dia/mes o mes/dia.
 */
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

const partes = (iso?: string | null): [number, number, number] | null => {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || '');
  return m ? [Number(m[1]), Number(m[2]) - 1, Number(m[3])] : null;
};

/** "6 nov" */
export const diaMes = (iso?: string | null): string => {
  const p = partes(iso);
  return p ? `${p[2]} ${MESES[p[1]]}` : '';
};

/** "mar 2027" */
export const mesAnio = (iso?: string | null): string => {
  const p = partes(iso);
  return p ? `${MESES[p[1]]} ${p[0]}` : '';
};
