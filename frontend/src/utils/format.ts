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
 * El simbolo con el que se escribe cada moneda.
 *
 * Un mapa corto y no `Intl` con style:'currency', porque Intl escribe "COP 1.234"
 * y "US$ 1.234" segun el idioma del telefono: la misma cifra se veria distinta
 * en dos moviles de la misma casa. Lo que no esta en el mapa se escribe con su
 * codigo delante, que es feo pero nunca es ambiguo.
 */
const SIMBOLOS: Record<string, string> = {
  COP: '$', USD: 'US$', EUR: '€', MXN: 'MX$', ARS: 'AR$',
  CLP: 'CL$', PEN: 'S/', BRL: 'R$', GBP: '£',
};

/**
 * Como se escriben los importes AHORA MISMO.
 *
 * Vive en el modulo y no en un contexto de React a proposito: `formatCurrency`
 * se llama desde quince pantallas y tambien desde funciones sueltas que no son
 * componentes, y convertirlo en un hook obligaria a tocar los quince sitios
 * para pasar por parametro algo que es igual en toda la app.
 *
 * El valor de partida es el que habia escrito a mano antes de la ola 4, asi
 * que si el backend no contesta, todo se ve exactamente como se veia.
 */
let formatoActual = { currency: 'COP', decimalPlaces: 0, locale: 'es-CO' };

/** Lo llama el contexto de sesion en cuanto el servidor dice las preferencias. */
export const configurarFormatoDeMoneda = (formato: {
  currency?: string;
  decimalPlaces?: number;
  locale?: string;
}): void => {
  formatoActual = {
    currency: formato.currency?.trim() || formatoActual.currency,
    /* Un numero de decimales imposible se ignora en vez de romper
       toLocaleString, que lanza con cualquier cosa fuera de 0..20 y dejaria la
       pantalla en blanco sin explicacion. */
    decimalPlaces:
      typeof formato.decimalPlaces === 'number' &&
      formato.decimalPlaces >= 0 &&
      formato.decimalPlaces <= 4
        ? formato.decimalPlaces
        : formatoActual.decimalPlaces,
    locale: formato.locale?.trim() || formatoActual.locale,
  };
};

export const formatoDeMoneda = () => ({ ...formatoActual });

/**
 * Importe con su signo. Un deficit de -500.000 se mostraba como "$500.000":
 * `Math.abs` borraba el menos, y tres pantallas lo volvian a pegar a mano,
 * cada una a su manera.
 */
export const formatCurrency = (amount: number): string => {
  const valor = Number.isFinite(amount) ? amount : 0;
  const { currency, decimalPlaces, locale } = formatoActual;
  let formatted: string;
  try {
    formatted = Math.abs(valor).toLocaleString(locale, {
      minimumFractionDigits: decimalPlaces,
      maximumFractionDigits: decimalPlaces,
    });
  } catch {
    /* Un locale que este movil no conozca no puede dejar sin cifra a nadie. */
    formatted = Math.abs(valor).toFixed(decimalPlaces);
  }
  const simbolo = SIMBOLOS[currency] ?? `${currency} `;
  return `${valor < 0 ? '-' : ''}${simbolo}${formatted}`;
};

/**
 * Lee un importe tal como lo escribe una persona en Colombia: "1.500.000",
 * "$ 1.500.000", "50.000,50" o "1500000".
 *
 * Existe porque los formularios hacian parseFloat del texto tal cual, y
 * parseFloat("1.500.000") es 1.5: quien escribia el arriendo con sus puntos
 * guardaba un arriendo de un peso y medio, sin ningun aviso. Otros hacian
 * Number(...) y ahi el mismo texto daba NaN y un "no es un numero" que nadie
 * entendia, porque SI era un numero.
 *
 * La regla, en orden:
 *  - Si aparecen punto y coma, el que va de ULTIMO es el decimal.
 *  - Si solo aparece uno de los dos y todos sus grupos tienen tres cifras
 *    ("1.500", "2.300.000"), es separador de miles. Un "1.5" es decimal.
 *  - Devuelve NaN si no queda ningun numero, para que el formulario avise.
 */
/**
 * Para recuadros estrechos: $7,96 M, $85 mil. En la web no hay forma de
 * encoger el texto para que quepa, y "$7.960...." no dice nada.
 */
export const formatCompacto = (monto: number): string => {
  const a = Math.abs(monto || 0);
  const signo = monto < 0 ? '-' : '';
  if (a >= 1_000_000) {
    const m = a / 1_000_000;
    return `${signo}$${m.toLocaleString('es-CO', { maximumFractionDigits: m >= 10 ? 1 : 2 })} M`;
  }
  if (a >= 10_000) return `${signo}$${Math.round(a / 1000).toLocaleString('es-CO')} mil`;
  return formatCurrency(monto);
};

export const parseMonto = (texto: string | number | null | undefined): number => {
  if (typeof texto === 'number') return texto;
  const limpio = String(texto ?? '').replace(/[^0-9.,-]/g, '');
  if (!/[0-9]/.test(limpio)) return NaN;
  const negativo = limpio.trim().startsWith('-');
  const cuerpo = limpio.replace(/-/g, '');
  const ultimoPunto = cuerpo.lastIndexOf('.');
  const ultimaComa = cuerpo.lastIndexOf(',');
  let normal: string;
  if (ultimoPunto >= 0 && ultimaComa >= 0) {
    const decimal = ultimoPunto > ultimaComa ? '.' : ',';
    const miles = decimal === '.' ? ',' : '.';
    normal = cuerpo.split(miles).join('').replace(decimal, '.');
  } else if (ultimoPunto >= 0 || ultimaComa >= 0) {
    const sep = ultimoPunto >= 0 ? '.' : ',';
    const grupos = cuerpo.split(sep);
    const sonMiles = grupos.length > 1 && grupos.slice(1).every((g) => g.length === 3) && grupos[0].length > 0;
    normal = sonMiles ? grupos.join('') : grupos.slice(0, -1).join('') + '.' + grupos[grupos.length - 1];
  } else {
    normal = cuerpo;
  }
  const n = Number(normal);
  return Number.isNaN(n) ? NaN : negativo ? -n : n;
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

/**
 * Fecha como la dice una persona: "1 nov", y con el año solo si no es el
 * actual ("3 ene 2027"). Para avisos y textos corridos, donde "2026-11-01"
 * se lee como un codigo y no como una fecha.
 */
export const fechaHumana = (date: string, hoy: Date = new Date()): string => {
  const d = desdeISO(date);
  if (Number.isNaN(d.getTime())) return date;
  const base = `${d.getDate()} ${MONTHS_SHORT[d.getMonth()].toLowerCase()}`;
  return d.getFullYear() === hoy.getFullYear() ? base : `${base} ${d.getFullYear()}`;
};

/**
 * Pasa a lenguaje de persona un aviso que viene del servidor: las fechas
 * "2026-11-01" a "1 nov", las cifras "190000.00" a "$190.000", "cuota(s)"
 * al singular o plural que toque, y algunas tildes que el servidor no pone.
 */
export const humanizarAviso = (texto: string, hoy: Date = new Date()): string => {
  if (!texto) return texto;
  return texto
    .replace(/\b(\d{4}-\d{2}-\d{2})\b/g, (_m, f) => fechaHumana(f, hoy))
    .replace(/(^|[^\d.,$])(\d+\.\d{1,2})(?!\d|[.,]\d)/g, (_m, antes, n) => `${antes}${formatCurrency(Number(n))}`)
    .replace(/\b(liberan|faltan|libera|falta)\s+(\d{4,})(?!\d|[.,]\d)/gi, (_m, verbo, n) => `${verbo} ${formatCurrency(Number(n))}`)
    .replace(/\b(\d+) cuota\(s\)/g, (_m, n) => `${n} ${Number(n) === 1 ? 'cuota' : 'cuotas'}`)
    .replace(/\b(\d+) periodo\(s\)/g, (_m, n) => `${n} ${Number(n) === 1 ? 'periodo' : 'periodos'}`)
    .replace(/\bvencio\b/g, 'venció')
    .replace(/\bRevisa que provision\b/g, 'Revisa qué plata separada')
    .replace(/\bprovision\b/g, 'plata separada');
};

/** "1 pago", "3 pagos": el plural que toca, sin "(s)". */
export const plural = (n: number, singular: string, pluralTexto = `${singular}s`): string =>
  `${n} ${n === 1 ? singular : pluralTexto}`;
