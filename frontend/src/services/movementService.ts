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

  getById: async (id: string) => {
    const response = await api.post<ResultDTO<Movement>>(
      `${BASE}/get/${id}`,
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

  confirm: async (id: string, newAmount?: number) => {
    const response = await api.post<ResultDTO<Movement>>(
      `${BASE}/confirm/${id}`,
      newAmount ? { amount: newAmount } : {},
    );
    return response.data;
  },

  getByPeriod: async (userId: string, startDate: string, endDate: string) => {
    const response = await api.post<ResultDTO<Movement[]>>(
      `${BASE}/by-period`,
      { userId, startDate, endDate },
    );
    return response.data;
  },

  getChildren: async (parentId: number) => {
    const response = await api.post<ResultDTO<any>>(`${BASE}/children/${parentId}`, {});
    return response.data;
  },

  transfer: async (data: { userId: string; fromCategoryId: number; toCategoryId: number; amount: number; description?: string }) => {
    const response = await api.post<ResultDTO<any>>(`${BASE}/transfer`, data);
    return response.data;
  },
};
