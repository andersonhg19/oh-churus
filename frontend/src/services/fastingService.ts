import api from './api';
import { ResultDTO } from '../types';

const BASE = '/FASTING-SERVICE/oh-churus/v1/fasting';

export const fastingService = {
  // Plan
  getPlan: async (userId: string) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/plan/get`, { userId });
    return res.data;
  },
  savePlan: async (data: any) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/plan/save`, data);
    return res.data;
  },
  getPresets: async () => {
    const res = await api.post<ResultDTO<any[]>>(`${BASE}/plan/presets`, {});
    return res.data;
  },

  // Sessions
  startFasting: async (userId: string, startTime?: string) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/session/start`, {
      userId, startTime: startTime || undefined,
    });
    return res.data;
  },
  stopFasting: async (userId: string, endTime?: string) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/session/stop`, {
      userId, endTime: endTime || undefined,
    });
    return res.data;
  },
  cancelFasting: async (userId: string) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/session/cancel`, { userId });
    return res.data;
  },
  getActiveSession: async (userId: string) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/session/active`, { userId });
    return res.data;
  },
  editSession: async (sessionId: number, startTime?: string, endTime?: string) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/session/edit`, {
      sessionId, startTime: startTime || undefined, endTime: endTime || undefined,
    });
    return res.data;
  },

  // History
  getHistory: async (userId: string, budgetStartDay: number, referenceDate?: string) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/history/by-period`, {
      userId, budgetStartDay, referenceDate: referenceDate || undefined,
    });
    return res.data;
  },
  getSummary: async (userId: string, budgetStartDay: number, referenceDate?: string) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/history/summary`, {
      userId, budgetStartDay, referenceDate: referenceDate || undefined,
    });
    return res.data;
  },

  // Achievements
  getAchievements: async (userId: string) => {
    const res = await api.post<ResultDTO<any[]>>(`${BASE}/achievements`, { userId });
    return res.data;
  },

  // Water
  getWaterToday: async (userId: string) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/water/today`, { userId });
    return res.data;
  },
  addWater: async (userId: string, glasses: number = 1) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/water/add`, { userId, glasses });
    return res.data;
  },
  setWaterGoal: async (userId: string, goalGlasses: number) => {
    const res = await api.post<ResultDTO<any>>(`${BASE}/water/set-goal`, { userId, goalGlasses });
    return res.data;
  },
};
