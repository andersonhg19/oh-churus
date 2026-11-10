/*
 * COMO SE PAGO UN MOVIMIENTO, dicho como lo diria una persona.
 *
 * Existe porque cada pantalla lo escribia a su manera: unas anteponian
 * "Tarjeta " al nombre y salia "Tarjeta Tarjeta de Crédito Nu"; Movimientos
 * solo decia "con tarjeta" sin el nombre, y de lo pagado con los fondos del
 * mes no decia nada.
 */

const sinTildes = (t: string) => t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

/** "Nu" -> "Tarjeta Nu"; "Tarjeta de Crédito Nu" se queda igual. */
export function nombreTarjeta(nombre?: string | null): string {
  const n = (nombre || '').trim();
  if (!n) return 'Con tarjeta';
  return /^tarjeta\b/.test(sinTildes(n)) ? n : `Tarjeta ${n}`;
}

/** "Ahorro viaje" -> "con el Ahorro viaje"; "Carro" -> "con el ahorro Carro". */
export function conElAhorro(nombre?: string | null): string {
  const n = (nombre || '').trim();
  if (!n) return 'con el ahorro';
  return /^ahorro/.test(sinTildes(n)) ? `con el ${n}` : `con el ahorro ${n}`;
}

const tiene = (id: unknown) => id !== undefined && id !== null && id !== '' && Number(id) !== 0;

export interface MovimientoConMedio {
  categoryType?: string;
  fundedFromCategoryId?: string | number | null;
  cardId?: string | number | null;
  installmentNumber?: number | null;
  installmentTotal?: number | null;
}

/**
 * El medio de pago de un movimiento: "Tarjeta Nu · cuota 1/6",
 * "con el Ahorro viaje" o "Fondos del mes". Los ingresos no llevan medio.
 * Si la tarjeta no es mia (no esta en la lista) se dice "Con tarjeta".
 */
export function comoSePago(
  m: MovimientoConMedio,
  ahorros: { id: string; nombre: string }[] = [],
  tarjetas: { id: number | string; name: string }[] = [],
): string {
  if (m.categoryType === 'INCOME') return '';
  if (tiene(m.cardId)) {
    const t = tarjetas.find((x) => String(x.id) === String(m.cardId));
    const base = t ? nombreTarjeta(t.name) : 'Con tarjeta';
    return (m.installmentTotal || 1) > 1 ? `${base} · cuota ${m.installmentNumber || 1}/${m.installmentTotal}` : base;
  }
  if (tiene(m.fundedFromCategoryId)) {
    const a = ahorros.find((x) => String(x.id) === String(m.fundedFromCategoryId));
    const texto = conElAhorro(a?.nombre);
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }
  return 'Fondos del mes';
}
