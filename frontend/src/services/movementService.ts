import api from './api';
import { ResultDTO, Movement, MovementFilter, PageDTO } from '../types';

const BASE = '/BUDGET-SERVICE/oh-churus/v1/movements';

export const movementService = {
  save: async (data: Partial<Movement>) => {
    const response = await api.post<ResultDTO<Movement>>(
      `${BASE}/save`,
      data,
    );
    return response.data;
  },

  getAll: async (filter: MovementFilter) => {
    const response = await api.post<ResultDTO<PageDTO<Movement>>>(
      `${BASE}/all`,
      filter,
    );
    return response.data;
  },

  delete: async (id: string) => {
    const response = await api.post<ResultDTO<null>>(
      `${BASE}/delete/${id}`,
    );
    return response.data;
  },

  /** Cambia quien paga UN pendiente comun ("este mes lo pago yo"). */
  reassign: async (id: string, payerUserId: number) => {
    const response = await api.post<ResultDTO<Movement>>(`${BASE}/reassign/${id}`, { payerUserId });
    return response.data;
  },

  /** Suelta plata de un pendiente del plan sin cambiar el presupuesto (todo si no hay monto). */
  release: async (id: string | number, amount?: number) => {
    const response = await api.post<ResultDTO<{ released: number; totalReleased: number; stillPending: number }>>(
      `${BASE}/release/${id}`, amount !== undefined ? { amount } : {});
    return response.data;
  },

  /** Deshace una liberacion: lo liberado vuelve a quedar por pagar. */
  unrelease: async (id: string | number) => {
    const response = await api.post<ResultDTO<{ pending: number }>>(`${BASE}/unrelease/${id}`, {});
    return response.data;
  },

  /** Confirma varios pendientes de una vez: todo o nada. */
  confirmBatch: async (items: { id: string | number; amount?: number }[]) => {
    const response = await api.post<ResultDTO<{ confirmed: number; total: number }>>(
      `${BASE}/confirm-batch`, { items: items.map((i) => ({ id: Number(i.id), amount: i.amount })) });
    return response.data;
  },

  confirm: async (id: string, newAmount?: number) => {
    const response = await api.post<ResultDTO<Movement>>(
      `${BASE}/confirm/${id}`,
      newAmount ? { amount: newAmount } : {},
    );
    return response.data;
  },
};
