import { apiClient } from './client';
import { TokenResponse } from '@/types';

export const authApi = {
  login: async (credentials: any) => {
    const { data } = await apiClient.post<TokenResponse>('/auth/login', credentials);
    return data;
  },
  register: async (userData: any) => {
    const { data } = await apiClient.post<TokenResponse>('/auth/register', userData);
    return data;
  },
  google: async (token: string) => {
    const { data } = await apiClient.post<TokenResponse>('/auth/google', { token });
    return data;
  }
};
