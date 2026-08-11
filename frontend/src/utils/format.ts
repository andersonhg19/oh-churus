const MONTHS_SHORT = [
  'Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun',
  'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic',
];

const MONTHS_FULL = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];

export const getMonthName = (month: number): string => MONTHS_FULL[month] || '';
export const getMonthShort = (month: number): string => MONTHS_SHORT[month] || '';

/**
 * Fecha de HOY en la zona del usuario, como "AAAA-MM-DD".
 *
 * Existe porque `new Date().toISOString()` pasa a UTC: en Colombia (UTC-5),
 * todo lo registrado despues de las 19:00 se guardaba con la fecha de MANANA.
 * Un gasto de la cena del 11 aparecia el 12, y si caia en el corte de
 * presupuesto, en el mes siguiente.
 *
 * Regla del proyecto: para fechas locales, nunca `toISOString`.
 */
export const fechaLocalISO = (d: Date = new Date()): string => {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
};

/**
 * Convierte "AAAA-MM-DD" en una fecha LOCAL.
 *
 * `new Date("2026-08-11")` se interpreta como medianoche UTC, que en Bogota
 * son las 19:00 del dia 10: por eso las listas mostraban un dia menos.
 */
const desdeISO = (fecha: string): Date => {
  const partes = /^(\d{4})-(\d{2})-(\d{2})/.exec(fecha);
  if (!partes) return new Date(fecha);
  return new Date(Number(partes[1]), Number(partes[2]) - 1, Number(partes[3]));
};

/**
 * Importe con su signo. Un deficit de -500.000 se mostraba como "$500.000":
 * `Math.abs` borraba el menos, y tres pantallas lo volvian a pegar a mano,
 * cada una a su manera.
 */
export const formatCurrency = (amount: number): string => {
  const valor = Number.isFinite(amount) ? amount : 0;
  const formatted = Math.abs(valor).toLocaleString('es-CO', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  });
  return `${valor < 0 ? '-' : ''}$${formatted}`;
};

export const formatDate = (date: string): string => {
  const d = desdeISO(date);
  const day = d.getDate();
  const month = MONTHS_SHORT[d.getMonth()];
  const year = d.getFullYear();
  return `${day} ${month} ${year}`;
};

export const formatDateShort = (date: string): string => {
  const d = desdeISO(date);
  const day = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  return `${day}/${month}`;
};
