import { apiClient } from './client';
import { Asset, AssetCategory, AssetType } from '../types';

export const assetsApi = {
  getAll: async (typeId?: number, query?: string) => {
    const params: Record<string, string> = {};
    if (typeId) params.typeId = String(typeId);
    if (query) params.query = query;
    const { data } = await apiClient.get<Asset[]>('/assets/', { params });
    return data;
  },
  getByTicker: async (ticker: string) => {
    const { data } = await apiClient.get<Asset>(`/assets/${ticker}`);
    return data;
  },
  getTypes: async () => {
    const { data } = await apiClient.get<AssetType[]>('/assets/types');
    return data;
  },
  getCategories: async (typeId?: number) => {
    const params: Record<string, string> = {};
    if (typeId) params.typeId = String(typeId);
    const { data } = await apiClient.get<AssetCategory[]>('/assets/categories', { params });
    return data;
  },
};
