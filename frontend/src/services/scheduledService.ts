import api from './api';
import {
  ResultDTO,
  ScheduledMovement,
  ScheduledFilter,
  PageDTO,
  GeneratePendingResult,
  OccurrenceRef,
  Movement,
} from '../types';

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

  // Ya no devuelve una lista pelada: devuelve lo creado y lo PROPUESTO por
  // separado. Cuando un programado acumula mas de cinco ocurrencias atrasadas,
  // el backend no las crea solas; las propone para que se revisen.
  generatePending: async (userId: string, budgetStartDay: number) => {
    const response = await api.post<ResultDTO<GeneratePendingResult>>(
      `${BASE}/generate-pending`,
      { userId, budgetStartDay },
    );
    return response.data;
  },

  // La otra mitad: crear las ocurrencias propuestas que la persona acepte. Solo
  // viaja cual es cada una; el importe y la fecha los pone el servidor.
  materialize: async (occurrences: OccurrenceRef[]) => {
    const response = await api.post<ResultDTO<Movement[]>>(
      `${BASE}/materialize`,
      { occurrences },
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
