import { formatCurrency, formatDate, formatDateShort, fechaLocalISO } from '../format';

describe('formatCurrency', () => {
  it('formats zero as "$0"', () => {
    expect(formatCurrency(0)).toBe('$0');
  });

  it('formats large numbers with thousand separators', () => {
    const result = formatCurrency(1500000);
    // es-CO locale uses period as thousand separator
    expect(result).toMatch(/^\$1[.,]500[.,]000$/);
  });

  it('conserva el signo de los importes negativos', () => {
    /* Esta prueba pedia lo contrario: que -500 se mostrara como "$500".
       Con eso, un deficit se veia igual que un superavit, y tres pantallas
       tenian que volver a pegar el menos a mano, cada una a su manera. */
    expect(formatCurrency(-500)).toBe('-$500');
    expect(formatCurrency(500)).toBe('$500');
    expect(formatCurrency(0)).toBe('$0');
  });

  it('no escupe NaN si le llega basura', () => {
    expect(formatCurrency(NaN as unknown as number)).toBe('$0');
    expect(formatCurrency(undefined as unknown as number)).toBe('$0');
  });

  it('rounds decimals (no fraction digits for COP)', () => {
    const result = formatCurrency(99.99);
    expect(result).toBe('$100');
  });
});

describe('formatDate', () => {
  it('formats a date string with day, month abbreviation, and year', () => {
    // Use a format that avoids timezone offset issues: YYYY-MM-DDT12:00:00
    const result = formatDate('2026-03-17T12:00:00');
    expect(result).toContain('17');
    expect(result).toContain('Mar');
    expect(result).toContain('2026');
  });

  it('formats another date correctly', () => {
    const result = formatDate('2026-01-05T12:00:00');
    expect(result).toContain('Ene');
    expect(result).toContain('2026');
  });

  it('handles invalid date string gracefully', () => {
    const result = formatDate('not-a-date');
    expect(typeof result).toBe('string');
  });
});

describe('formatDateShort', () => {
  it('formats date as DD/MM', () => {
    const result = formatDateShort('2026-03-17T12:00:00');
    expect(result).toBe('17/03');
  });

  it('pads single-digit day and month', () => {
    const result = formatDateShort('2026-01-05T12:00:00');
    expect(result).toBe('05/01');
  });
});

describe('fechas locales (el bug de la zona horaria)', () => {
  /* En Bogota (UTC-5), `new Date().toISOString()` devuelve la fecha de MANANA
     a partir de las 19:00, y `new Date("2026-08-11")` se interpreta como
     medianoche UTC, o sea las 19:00 del dia 10. Resultado: los gastos de la
     noche se guardaban con la fecha del dia siguiente, y las listas los
     mostraban con la del anterior. */

  it('fechaLocalISO usa el dia local, no el de UTC', () => {
    // 11 de agosto, 22:30 hora local: en UTC ya es dia 12
    const nocheDelOnce = new Date(2026, 7, 11, 22, 30, 0);
    expect(fechaLocalISO(nocheDelOnce)).toBe('2026-08-11');
  });

  it('fechaLocalISO tambien acierta de madrugada', () => {
    expect(fechaLocalISO(new Date(2026, 0, 1, 0, 15, 0))).toBe('2026-01-01');
  });

  it('formatDate no adelanta ni atrasa el dia', () => {
    expect(formatDate('2026-08-11')).toBe('11 Ago 2026');
    expect(formatDate('2026-01-01')).toBe('1 Ene 2026');
  });

  it('formatDateShort tampoco', () => {
    expect(formatDateShort('2026-08-11')).toBe('11/08');
  });
});
