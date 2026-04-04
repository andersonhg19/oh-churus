import api from './api';
import { ResultDTO, DashboardSummary, CategorySummary, TrendData, Movement } from '../types';

const BASE = '/BUDGET-SERVICE/oh-churus/v1/dashboard';

export const dashboardService = {
  getSummary: async (userId: string, budgetStartDay: number, referenceDate?: string) => {
    const response = await api.post<ResultDTO<DashboardSummary>>(
      `${BASE}/summary`,
      { userId, budgetStartDay, referenceDate: referenceDate || undefined },
    );
    return response.data;
  },

  getByCategory: async (userId: string, budgetStartDay: number, referenceDate?: string) => {
    const response = await api.post<ResultDTO<CategorySummary[]>>(
      `${BASE}/by-category`,
      { userId, budgetStartDay, referenceDate: referenceDate || undefined },
    );
    return response.data;
  },

  getTrend: async (userId: string, budgetStartDay: number) => {
    const response = await api.post<ResultDTO<TrendData[]>>(
      `${BASE}/trend`,
      { userId, budgetStartDay },
    );
    return response.data;
  },

  getPending: async (userId: string, budgetStartDay: number, referenceDate?: string) => {
    const response = await api.post<ResultDTO<Movement[]>>(
      `${BASE}/pending`,
      { userId, budgetStartDay, referenceDate: referenceDate || undefined },
    );
    return response.data;
  },

  getSplitSummary: async (userId: string, budgetStartDay: number, referenceDate?: string) => {
    const response = await api.post<ResultDTO<any>>(
      `${BASE}/split-summary`,
      { userId, budgetStartDay, referenceDate: referenceDate || undefined },
    );
    return response.data;
  },

  getConsolidated: async (userId: string, budgetStartDay: number, referenceDate?: string) => {
    const response = await api.post<ResultDTO<any>>(
      `${BASE}/consolidated`,
      { userId, budgetStartDay, referenceDate: referenceDate || undefined },
    );
    return response.data;
  },
};
