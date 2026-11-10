import api from './api';
import { ResultDTO, Movement } from '../types';

const BASE = '/BUDGET-SERVICE/oh-churus/v1/dashboard';

export const dashboardService = {
  getPending: async (userId: string, budgetStartDay: number, referenceDate?: string) => {
    const response = await api.post<ResultDTO<Movement[]>>(
      `${BASE}/pending`,
      { userId, budgetStartDay, referenceDate: referenceDate || undefined },
    );
    return response.data;
  },
};
