import api from './api';
import { ResultDTO } from '../types';

const BASE = '/BUDGET-SERVICE/oh-churus/v1/estado';

/** HOGAR = lo comun; PERSONA = lo que paga o le toca a una persona del hogar. */
export type Alcance = { tipo: 'HOGAR' } | { tipo: 'PERSONA'; userId: number };

export type EstadoCategoria =
  | 'DENTRO' | 'EN_RIESGO' | 'EXCEDIDO' | 'SIN_PRESUPUESTO'
  | 'META_CUMPLIDA' | 'META_INCUMPLIDA' | 'EN_CURSO';

export interface ElementoDelPlan {
  scheduledMovementId: number;
  movementId?: number;
  name: string;
  date: string;
  amount: number;
  status: 'PAGADO' | 'PENDIENTE' | 'VENCIDO' | 'PREVISTO' | 'PROVISION' | 'LIBERADO';
  kind: 'PAYMENT' | 'DEDUCTION' | 'SAVING' | 'PROVISION';
  /** Lo que se libero de esta fecha para usarlo en otra cosa. */
  released?: number;
  payerUserId: number;
  payerName?: string;
}

export interface CategoriaDelPeriodo {
  categoryId: number;
  categoryName: string;
  categoryIcon?: string;
  categoryColor?: string;
  shared: boolean;
  kind: 'EXPENSE' | 'SAVING';
  allocated: number;
  actual: number;
  committed: number;
  available: number;
  difference: number;
  executionPct: number;
  status: EstadoCategoria;
  hasAllocation: boolean;
  plannedInItems: number;
  released?: number;
  provision: number;
  provisionLeft: number;
  otherSpending: number;
  byPerson: { userId: number; name?: string; actual: number; committed: number }[];
  items?: ElementoDelPlan[];
  /** Gastado en esta categoria con plata ya ahorrada (no del mes). */
  fromSavings?: number;
  /** En un ahorro: cuanto hay, sumando todos los meses (lo metido menos lo pagado con el). */
  balance?: number;
  /** Sacado de esta categoria de ahorro para pagar otras cosas. */
  withdrawn?: number;
  withdrawals?: { movementId: number; date: string; amount: number; description?: string; toCategoryId: number; toCategoryName: string; userId: number }[];
}

export interface TotalesDelPeriodo {
  incomeExpected: number;
  incomeReceived: number;
  incomePending: number;
  payrollDeductions: number;
  budgeted: number;
  unassigned: number;
  spent: number;
  committed: number;
  savingsGoal: number;
  saved: number;
  savingsRatePct: number;
  unbudgetedSpending: number;
  provisionsLeft: number;
  /** Lo que falta pagar segun el plan, generado o no su pendiente. */
  toPay: number;
  /** Lo gastado con plata ya ahorrada: esta en lo gastado pero no baja lo libre. */
  fromSavings?: number;
  /** Cuanto hay en todos los ahorros, al cierre del periodo. */
  savingsBalance?: number;
  /** Lo ahorrado menos lo que se saco del ahorro. */
  netSaved?: number;
  /** Lo liberado de compromisos del plan en el periodo. */
  released?: number;
  /** Lo libre al final del periodo, con los ingresos esperados. */
  freeToSpend: number;
  /** Lo mismo, con lo que ya llego. */
  freeNow: number;
  executionPct: number;
}

export interface Responsabilidad {
  userId: number;
  name?: string;
  bills: number;
  provisions: number;
  savings: number;
  total: number;
  paid: number;
  pending: number;
  sharePct: number;
}

export interface EstadoDelPeriodo {
  scope: 'HOGAR' | 'PERSONA';
  personUserId?: number;
  personName?: string;
  periodStart: string;
  periodEnd: string;
  closed: boolean;
  totals: TotalesDelPeriodo;
  compliance: {
    billsTotal: number; billsPaid: number; billsOverdue: number;
    categoriesWithin: number; categoriesOver: number;
    savingsGoalsMet: number; savingsGoalsMissed: number;
  };
  categories: CategoriaDelPeriodo[];
  pendingCount: number;
  incomeByPerson: { userId: number; name?: string; expected: number; received: number }[];
  incomeItems: ElementoDelPlan[];
  responsibilities: Responsabilidad[];
}

export interface PeriodoDelHistorial {
  periodStart: string;
  periodEnd: string;
  closed: boolean;
  totals: TotalesDelPeriodo;
  categories: Pick<CategoriaDelPeriodo, 'categoryId' | 'categoryName' | 'categoryIcon' | 'categoryColor'
    | 'kind' | 'allocated' | 'actual' | 'difference' | 'status'>[];
}

const cuerpo = (budgetStartDay: number, referenceDate: string, alcance: Alcance, extra: object = {}) => ({
  budgetStartDay,
  referenceDate,
  scope: alcance.tipo,
  personUserId: alcance.tipo === 'PERSONA' ? alcance.userId : undefined,
  ...extra,
});

export const estadoService = {
  periodo: async (budgetStartDay: number, referenceDate: string, alcance: Alcance) => {
    const r = await api.post<ResultDTO<EstadoDelPeriodo>>(`${BASE}/periodo`, cuerpo(budgetStartDay, referenceDate, alcance));
    return r.data;
  },
  historial: async (budgetStartDay: number, referenceDate: string, alcance: Alcance, periods = 6) => {
    const r = await api.post<ResultDTO<{ periods: PeriodoDelHistorial[] }>>(
      `${BASE}/historial`, cuerpo(budgetStartDay, referenceDate, alcance, { periods }));
    return r.data;
  },
};
