import { EstadoCategoria, CategoriaDelPeriodo, ElementoDelPlan } from '../services/estadoService';
import { formatCurrency } from './format';

/** Como se dice cada estado de una categoria, y de que color va. */
export const ESTADOS: Record<EstadoCategoria, { texto: string; tono: 'bien' | 'aviso' | 'mal' | 'neutro' }> = {
  DENTRO: { texto: 'Dentro del presupuesto', tono: 'bien' },
  EN_RIESGO: { texto: 'Con lo pendiente se pasa', tono: 'aviso' },
  EXCEDIDO: { texto: 'Se pasó', tono: 'mal' },
  SIN_PRESUPUESTO: { texto: 'Sin presupuesto', tono: 'mal' },
  META_CUMPLIDA: { texto: 'Meta cumplida', tono: 'bien' },
  META_INCUMPLIDA: { texto: 'Meta no cumplida', tono: 'mal' },
  EN_CURSO: { texto: 'Ahorro en curso', tono: 'neutro' },
};

export const ESTADOS_ELEMENTO: Record<ElementoDelPlan['status'], { texto: string; tono: 'bien' | 'aviso' | 'mal' | 'neutro' }> = {
  PAGADO: { texto: 'Pagado', tono: 'bien' },
  PENDIENTE: { texto: 'Por pagar', tono: 'aviso' },
  VENCIDO: { texto: 'Vencido', tono: 'mal' },
  PREVISTO: { texto: 'Previsto', tono: 'neutro' },
  PROVISION: { texto: 'Bolsa', tono: 'neutro' },
};

export const colorDeTono = (tono: string, c: { income: string; pending: string; expense: string; textSecondary: string }) =>
  tono === 'bien' ? c.income : tono === 'aviso' ? c.pending : tono === 'mal' ? c.expense : c.textSecondary;

/** La frase del cierre: "se esperaban 500.000 y se gastaron 700.000". */
export function fraseDelCierre(c: Pick<CategoriaDelPeriodo, 'kind' | 'allocated' | 'actual' | 'status'>, cerrado: boolean): string {
  const plan = formatCurrency(c.allocated);
  const real = formatCurrency(c.actual);
  if (c.kind === 'SAVING') {
    if (c.actual >= c.allocated && c.allocated > 0) return `Se quería ahorrar ${plan} y se ahorró ${real}.`;
    const falta = formatCurrency(Math.max(0, c.allocated - c.actual));
    return cerrado
      ? `Se quería ahorrar ${plan} y se ahorró ${real}: faltaron ${falta}.`
      : `Meta ${plan}: van ${real}, faltan ${falta}.`;
  }
  if (c.status === 'SIN_PRESUPUESTO') return `No tenía presupuesto y se ${cerrado ? 'gastaron' : 'llevan'} ${real}.`;
  const verbo = cerrado ? 'se gastaron' : 'van';
  if (c.actual > c.allocated) return `Se esperaban ${plan} y ${verbo} ${real}: ${formatCurrency(c.actual - c.allocated)} de más.`;
  return `Se esperaban ${plan} y ${verbo} ${real}: ${cerrado ? 'sobraron' : 'quedan'} ${formatCurrency(c.allocated - c.actual)}.`;
}
