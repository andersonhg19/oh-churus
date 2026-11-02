import api from './api';
import { ResultDTO } from '../types';

const BASE = '/BUDGET-SERVICE/oh-churus/v1/plan';

/** Una fecha concreta de un programado, con su estado. */
export interface PlanItem {
  scheduledMovementId: number;
  movementId?: number;
  name: string;
  date: string;
  amount: number;
  status: 'PAGADO' | 'PENDIENTE' | 'VENCIDO' | 'PREVISTO' | 'PROVISION';
  kind: 'PAYMENT' | 'DEDUCTION' | 'SAVING' | 'PROVISION';
  income: boolean;
  categoryId: number;
  categoryName: string;
  categoryIcon?: string;
  categoryColor?: string;
  shared: boolean;
  payerUserId: number;
  payerName?: string;
  notes?: string;
}

export interface PlanWindow {
  start: string;
  end: string;
  grossIncome: number;
  deductions: number;
  netIncome: number;
  payments: number;
  paymentsPaid: number;
  paymentsLeft: number;
  savings: number;
  provisions: number;
  leftover: number;
  tight: boolean;
  incomeItems?: PlanItem[];
  deductionItems?: PlanItem[];
  paymentItems?: PlanItem[];
  savingItems?: PlanItem[];
  provisionItems?: PlanItem[];
  endingInstallments?: string[];
  freedNextPeriod?: number;
}

export interface Installment {
  scheduledMovementId: number;
  name: string;
  installmentAmount: number;
  totalInstallments: number;
  paidInstallments: number;
  remainingInstallments: number;
  remainingTotal: number;
  nextDate?: string;
  endDate: string;
  monthlyFreed: number;
  kind: string;
}

export interface PlanAlert {
  type: 'VENCIDO' | 'PROXIMO' | 'CUOTA_TERMINA' | 'QUINCENA_AJUSTADA';
  message: string;
  date?: string;
  amount?: number;
}

export interface Quincena {
  today: string;
  payDates: string[];
  hasIncome: boolean;
  current: PlanWindow;
  next: PlanWindow;
  overdueFromBefore: PlanItem[];
  available: { accountsBalance: number; stillToPay: number; reservedInProvisions: number; freeToSpend: number };
  installments: Installment[];
  alerts: PlanAlert[];
}

export interface Responsabilidades {
  periodStart: string;
  periodEnd: string;
  sharedTotal: number;
  people: {
    userId: number;
    name?: string;
    isMe: boolean;
    income: number;
    sharedTotal: number;
    sharedPaid: number;
    sharePct: number;
    sharedByKind: Record<string, number>;
    items: PlanItem[];
  }[];
}

export const planService = {
  quincena: async (referenceDate?: string) => {
    const r = await api.post<ResultDTO<Quincena>>(`${BASE}/quincena`, { referenceDate });
    return r.data;
  },
  calendar: async (from: string, to: string) => {
    const r = await api.post<ResultDTO<{ from: string; to: string; items: PlanItem[] }>>(`${BASE}/calendar`, { from, to });
    return r.data;
  },
  responsibilities: async (budgetStartDay: number, referenceDate?: string) => {
    const r = await api.post<ResultDTO<Responsabilidades>>(`${BASE}/responsibilities`, { budgetStartDay, referenceDate });
    return r.data;
  },
  projection: async (budgetStartDay: number, periods = 3) => {
    const r = await api.post<ResultDTO<PlanWindow[]>>(`${BASE}/projection`, { budgetStartDay, periods });
    return r.data;
  },
};
