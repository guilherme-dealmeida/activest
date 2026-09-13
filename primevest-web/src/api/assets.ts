import { apiClient } from './client';

export const assetsApi = {
  getAll: async (typeId?: number, query?: string) => {
    const params: Record<string, string> = {};
    if (typeId) params.typeId = String(typeId);
    if (query) params.query = query;
    const { data } = await apiClient.get('/assets/', { params });
    return data;
  },
  getByTicker: async (ticker: string) => {
    const { data } = await apiClient.get(`/assets/${ticker}`);
    return data;
  },
  getTypes: async () => {
    const { data } = await apiClient.get('/assets/types');
    return data;
  },
  getCategories: async (typeId?: number) => {
    const params: Record<string, string> = {};
    if (typeId) params.typeId = String(typeId);
    const { data } = await apiClient.get('/assets/categories', { params });
    return data;
  },
};
