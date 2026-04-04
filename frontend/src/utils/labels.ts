export const CATEGORY_TYPE_LABELS: Record<string, string> = {
  INCOME: 'Ingreso',
  EXPENSE: 'Gasto',
};

export const FREQUENCY_LABELS: Record<string, string> = {
  DAILY: 'Diario',
  WEEKLY: 'Semanal',
  BIWEEKLY: 'Quincenal',
  MONTHLY: 'Mensual',
  BIMONTHLY: 'Bimestral',
  QUARTERLY: 'Trimestral',
  SEMIANNUAL: 'Semestral',
  ANNUAL: 'Anual',
};

export const getCategoryTypeLabel = (type?: string): string =>
  type ? CATEGORY_TYPE_LABELS[type] || type : '';

export const getFrequencyLabel = (freq?: string): string =>
  freq ? FREQUENCY_LABELS[freq] || freq : '';
