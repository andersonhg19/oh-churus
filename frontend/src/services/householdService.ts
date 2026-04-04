import api from './api';
import { ResultDTO } from '../types';

const BASE = '/BUDGET-SERVICE/oh-churus/v1/household';

export interface HouseholdInfo {
  householdId: number;
  name: string;
  role: string;
  memberCount: number;
  members: { userId: number; role: string }[];
}

export const householdService = {
  create: async (name: string, userId: string) => {
    const response = await api.post<ResultDTO<any>>(`${BASE}/create`, { name, userId });
    return response.data;
  },

  addMember: async (householdId: number, userId: number) => {
    const response = await api.post<ResultDTO<any>>(`${BASE}/add-member`, { householdId, userId });
    return response.data;
  },

  removeMember: async (householdId: number, userId: number) => {
    const response = await api.post<ResultDTO<any>>(`${BASE}/remove-member`, { householdId, userId });
    return response.data;
  },

  getByUser: async (userId: string) => {
    const response = await api.post<ResultDTO<HouseholdInfo[]>>(`${BASE}/by-user`, { userId });
    return response.data;
  },
};
