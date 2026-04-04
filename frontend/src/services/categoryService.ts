import api from './api';
import { ResultDTO, Category, CategoryTree, CategoryFilter, PageDTO } from '../types';

const BASE = '/BUDGET-SERVICE/oh-churus/v1/categories';

export const categoryService = {
  save: async (data: Partial<Category>) => {
    const response = await api.post<ResultDTO<Category>>(
      `${BASE}/save`,
      data,
    );
    return response.data;
  },

  getById: async (id: string) => {
    const response = await api.post<ResultDTO<Category>>(
      `${BASE}/get/${id}`,
    );
    return response.data;
  },

  getAll: async (filter: CategoryFilter) => {
    const response = await api.post<ResultDTO<PageDTO<Category>>>(
      `${BASE}/all`,
      filter,
    );
    return response.data;
  },

  getTree: async (userId: string) => {
    const response = await api.post<ResultDTO<CategoryTree[]>>(
      `${BASE}/tree`,
      { userId },
    );
    return response.data;
  },

  delete: async (id: string) => {
    const response = await api.post<ResultDTO<null>>(
      `${BASE}/delete/${id}`,
    );
    return response.data;
  },

  typeList: async () => {
    const response = await api.post<ResultDTO<Array<{ key: string; name: string }>>>(
      `${BASE}/type-list`,
    );
    return response.data;
  },
};
