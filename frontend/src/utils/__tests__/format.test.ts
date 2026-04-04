import { formatCurrency, formatDate, formatDateShort } from '../format';

describe('formatCurrency', () => {
  it('formats zero as "$0"', () => {
    expect(formatCurrency(0)).toBe('$0');
  });

  it('formats large numbers with thousand separators', () => {
    const result = formatCurrency(1500000);
    // es-CO locale uses period as thousand separator
    expect(result).toMatch(/^\$1[.,]500[.,]000$/);
  });

  it('uses absolute value for negative numbers', () => {
    const result = formatCurrency(-500);
    expect(result).toBe('$500');
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
