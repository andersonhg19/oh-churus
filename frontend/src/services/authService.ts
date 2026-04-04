import api from './api';
import { ResultDTO, AuthResponse, LoginRequest, RegisterRequest, ResultUserDTO, UserFilter, PageDTO } from '../types';

const AUTH_BASE = '/AUTH-SERVICE/oh-churus/v1/auth';
const USERS_BASE = '/AUTH-SERVICE/oh-churus/v1/users';

export const authService = {
  login: async (email: string, password: string) => {
    const response = await api.post<ResultDTO<AuthResponse>>(
      `${AUTH_BASE}/login`,
      { email, password } as LoginRequest,
    );
    return response.data;
  },

  register: async (data: RegisterRequest) => {
    const response = await api.post<ResultDTO<AuthResponse>>(
      `${AUTH_BASE}/register`,
      data,
    );
    return response.data;
  },

  getUser: async (id: string) => {
    const response = await api.post<ResultDTO<ResultUserDTO>>(
      `${USERS_BASE}/get/${id}`,
    );
    return response.data;
  },

  updateUser: async (data: Partial<ResultUserDTO>) => {
    const response = await api.post<ResultDTO<ResultUserDTO>>(
      `${USERS_BASE}/save`,
      data,
    );
    return response.data;
  },

  getAllUsers: async (filter: UserFilter) => {
    const response = await api.post<ResultDTO<PageDTO<ResultUserDTO>>>(
      `${USERS_BASE}/all`,
      filter,
    );
    return response.data;
  },
};
