import api from './api';
import { ResultDTO, ScheduledMovement, ScheduledFilter, PageDTO } from '../types';

const BASE = '/BUDGET-SERVICE/oh-churus/v1/scheduled';

export const scheduledService = {
  save: async (data: Partial<ScheduledMovement>) => {
    const response = await api.post<ResultDTO<ScheduledMovement>>(
      `${BASE}/save`,
      data,
    );
    return response.data;
  },

  getById: async (id: string) => {
    const response = await api.post<ResultDTO<ScheduledMovement>>(
      `${BASE}/get/${id}`,
    );
    return response.data;
  },

  getAll: async (filter: ScheduledFilter) => {
    const response = await api.post<ResultDTO<PageDTO<ScheduledMovement>>>(
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

  generatePending: async (userId: string, budgetStartDay: number) => {
    const response = await api.post<ResultDTO<number>>(
      `${BASE}/generate-pending`,
      { userId, budgetStartDay },
    );
    return response.data;
  },

  frequencyList: async () => {
    const response = await api.post<ResultDTO<Array<{ key: string; name: string }>>>(
      `${BASE}/frequency-list`,
    );
    return response.data;
  },
};
