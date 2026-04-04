import api from './api';
import { ResultDTO } from '../types';

const BASE = '/BUDGET-SERVICE/oh-churus/v1/budget-allocation';

export const budgetAllocationService = {
  save: async (data: { userId: string; categoryId: number; amount: number; budgetStartDay: number; notes?: string; referenceDate?: string }) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/save`, data);
    return res.data;
  },

  list: async (userId: string, budgetStartDay: number, referenceDate?: string) => {
    const res = await api.post<ResultDTO<any[]>>(`${BASE}/list`, {
      userId, budgetStartDay, referenceDate: referenceDate || undefined,
    });
    return res.data;
  },

  summary: async (userId: string, budgetStartDay: number, referenceDate?: string) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/summary`, {
      userId, budgetStartDay, referenceDate: referenceDate || undefined,
    });
    return res.data;
  },

  delete: async (id: number) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/delete/${id}`);
    return res.data;
  },
};
