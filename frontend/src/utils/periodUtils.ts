/**
 * Replica la logica de PeriodUtils.java del backend.
 * Calcula inicio y fin del periodo presupuestal basado en budgetStartDay.
 */

function daysInMonth(year: number, month: number): number {
  return new Date(year, month + 1, 0).getDate();
}

function toISODate(year: number, month: number, day: number): string {
  const m = String(month + 1).padStart(2, '0');
  const d = String(day).padStart(2, '0');
  return `${year}-${m}-${d}`;
}

export function getStartOfPeriod(budgetStartDay: number, referenceDate: Date): string {
  const year = referenceDate.getFullYear();
  const month = referenceDate.getMonth();
  const dayOfRef = referenceDate.getDate();

  const adjustedDay = Math.min(budgetStartDay, daysInMonth(year, month));

  if (dayOfRef >= adjustedDay) {
    return toISODate(year, month, adjustedDay);
  } else {
    // Mes anterior
    const prevMonth = month === 0 ? 11 : month - 1;
    const prevYear = month === 0 ? year - 1 : year;
    const prevDay = Math.min(budgetStartDay, daysInMonth(prevYear, prevMonth));
    return toISODate(prevYear, prevMonth, prevDay);
  }
}

export function getEndOfPeriod(budgetStartDay: number, periodStartStr: string): string {
  const parts = periodStartStr.split('-');
  const year = parseInt(parts[0]);
  const month = parseInt(parts[1]) - 1;

  // Siguiente mes
  const nextMonth = month === 11 ? 0 : month + 1;
  const nextYear = month === 11 ? year + 1 : year;
  const nextDay = Math.min(budgetStartDay, daysInMonth(nextYear, nextMonth));

  // Restar 1 dia
  const endDate = new Date(nextYear, nextMonth, nextDay);
  endDate.setDate(endDate.getDate() - 1);

  return toISODate(endDate.getFullYear(), endDate.getMonth(), endDate.getDate());
}

export function navigatePeriod(currentStart: string, budgetStartDay: number, direction: 'prev' | 'next'): { start: string; end: string } {
  const parts = currentStart.split('-');
  const year = parseInt(parts[0]);
  const month = parseInt(parts[1]) - 1;

  let refDate: Date;
  if (direction === 'next') {
    // Ir al dia budgetStartDay del mes siguiente para caer exactamente en el siguiente periodo
    const nextMonth = month === 11 ? 0 : month + 1;
    const nextYear = month === 11 ? year + 1 : year;
    const day = Math.min(budgetStartDay, daysInMonth(nextYear, nextMonth));
    refDate = new Date(nextYear, nextMonth, day);
  } else {
    // Usar el dia anterior al periodStart para caer en el periodo previo
    const d = new Date(year, month, parseInt(currentStart.split('-')[2]));
    d.setDate(d.getDate() - 1);
    refDate = d;
  }

  const start = getStartOfPeriod(budgetStartDay, refDate);
  const end = getEndOfPeriod(budgetStartDay, start);
  return { start, end };
}
