import { getStartOfPeriod, getEndOfPeriod, navigatePeriod } from '../periodUtils';

describe('periodUtils', () => {
  describe('getStartOfPeriod', () => {
    it('returns same-month start when reference is on/after the start day', () => {
      expect(getStartOfPeriod(15, new Date(2026, 2, 20))).toBe('2026-03-15');
    });

    it('rolls back to previous month when reference is before the start day', () => {
      expect(getStartOfPeriod(15, new Date(2026, 2, 10))).toBe('2026-02-15');
    });

    it('handles January reference rolling back to previous December', () => {
      expect(getStartOfPeriod(15, new Date(2026, 0, 5))).toBe('2025-12-15');
    });

    it('clamps the start day on short months', () => {
      // budgetStartDay 31 in February (28 days) -> clamp to 28
      expect(getStartOfPeriod(31, new Date(2026, 1, 28))).toBe('2026-02-28');
    });
  });

  describe('getEndOfPeriod', () => {
    it('ends the day before next period start', () => {
      expect(getEndOfPeriod(15, '2026-03-15')).toBe('2026-04-14');
    });

    it('handles December rolling into next year', () => {
      expect(getEndOfPeriod(1, '2026-12-01')).toBe('2026-12-31');
    });

    it('clamps when the next month is shorter', () => {
      expect(getEndOfPeriod(31, '2026-01-31')).toBe('2026-02-27');
    });
  });

  describe('navigatePeriod', () => {
    it('navigates to the next period', () => {
      expect(navigatePeriod('2026-03-15', 15, 'next')).toEqual({ start: '2026-04-15', end: '2026-05-14' });
    });

    it('navigates to the previous period', () => {
      expect(navigatePeriod('2026-03-15', 15, 'prev')).toEqual({ start: '2026-02-15', end: '2026-03-14' });
    });

    it('navigates next across a year boundary', () => {
      expect(navigatePeriod('2026-12-15', 15, 'next')).toEqual({ start: '2027-01-15', end: '2027-02-14' });
    });
  });
});
