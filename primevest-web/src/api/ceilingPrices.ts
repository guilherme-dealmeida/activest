import { apiClient } from './client';
import { CeilingPrice, CeilingPriceCreate } from '../types';

export const ceilingPricesApi = {
  list: async () => { const { data } = await apiClient.get<CeilingPrice[]>('/ceiling-prices/'); return data; },
  create: async (cpData: CeilingPriceCreate) => { const { data } = await apiClient.post<CeilingPrice>('/ceiling-prices/', cpData); return data; },
  update: async (id: string, cpData: CeilingPriceCreate) => { const { data } = await apiClient.put<CeilingPrice>(`/ceiling-prices/${id}`, cpData); return data; },
  delete: async (id: string) => { await apiClient.delete(`/ceiling-prices/${id}`); },
};
