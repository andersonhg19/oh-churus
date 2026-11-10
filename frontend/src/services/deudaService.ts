import api from './api';
import { ResultDTO } from '../types';

const CARDS = '/BUDGET-SERVICE/oh-churus/v1/cards';
const DEBTS = '/BUDGET-SERVICE/oh-churus/v1/debts';

/** Una compra con tarjeta que aun no se ha pagado del todo. */
export interface CompraConTarjeta {
  purchaseId: number;
  description?: string | null;
  categoryId: number;
  categoryName?: string | null;
  categoryIcon?: string | null;
  categoryColor?: string | null;
  date: string;
  total: number;
  owed: number;
  installments: number;
  paidInstallments: number;
  nextDate?: string | null;
  nextAmount?: number | null;
}

/** Una tarjeta de credito con lo que se debe en ella. */
export interface Tarjeta {
  id: number;
  name: string;
  color?: string | null;
  paymentDay?: number | null;
  /** Dia de corte y lo que se deriva de el. */
  cutoffDay?: number | null;
  lastCutoff?: string | null;
  nextPaymentDate?: string | null;
  /** Lo comprado despues del ultimo corte: va en el siguiente extracto. */
  currentCycle?: number;
  userId: number;
  ownerName?: string | null;
  mine: boolean;
  owed: number;
  dueNow: number;
  upcoming: number;
  purchases: CompraConTarjeta[];
}

/** Algo que se paga en cuotas (un programado con fecha de fin). */
export interface DeudaEnCuotas {
  scheduledMovementId: number;
  name: string;
  installmentAmount: number;
  totalInstallments: number;
  paidInstallments: number;
  remainingInstallments: number;
  remainingTotal: number;
  nextDate?: string | null;
  endDate: string;
  monthlyFreed: number;
  kind: string;
  payerUserId: number;
  payerName?: string | null;
  categoryId: number;
  categoryName?: string | null;
  categoryIcon?: string | null;
  categoryColor?: string | null;
  shared?: boolean;
}

export interface Deudas {
  totalOwed: number;
  owedOnCards: number;
  owedInInstallments: number;
  monthlyInstallments: number;
  cards: Tarjeta[];
  loans: DeudaEnCuotas[];
}

export const deudaService = {
  /** Todo lo que se debe y que veo: tarjetas (mias y del hogar) y cuotas. */
  todas: async (referenceDate?: string) => {
    const r = await api.post<ResultDTO<Deudas>>(`${DEBTS}/all`, referenceDate ? { referenceDate } : {});
    return r.data;
  },

  /** Paga hoy la ultima cuota que falta. */
  adelantarCuota: async (scheduledMovementId: number | string) => {
    const r = await api.post<ResultDTO<{ movementId: number; amount: number; remainingInstallments: number; newEndDate?: string | null }>>(
      `${DEBTS}/advance/${scheduledMovementId}`, {});
    return r.data;
  },

  /** Mis tarjetas: las que se pueden elegir en "Pagado con". */
  misTarjetas: async () => {
    const r = await api.post<ResultDTO<Tarjeta[]>>(`${CARDS}/mine`, {});
    return r.data;
  },

  guardarTarjeta: async (t: { id?: number; name: string; color?: string | null; paymentDay?: number | null; cutoffDay?: number | null }) => {
    const r = await api.post<ResultDTO<Tarjeta>>(`${CARDS}/save`, t);
    return r.data;
  },

  /** Deja saldado lo comprado con la tarjeta hasta esa fecha (hoy si no se dice). */
  pagarTarjeta: async (id: number, upTo?: string) => {
    const r = await api.post<ResultDTO<{ paidItems: number; paidTotal: number; card: Tarjeta }>>(
      `${CARDS}/pay/${id}`, upTo ? { upTo } : {});
    return r.data;
  },

  borrarTarjeta: async (id: number) => {
    const r = await api.post<ResultDTO<null>>(`${CARDS}/delete/${id}`, {});
    return r.data;
  },
};
