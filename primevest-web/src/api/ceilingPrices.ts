import { apiClient } from './client';
import { CeilingPriceCreate } from '../types';

export const ceilingPricesApi = {
  list: async () => { const { data } = await apiClient.get('/ceiling-prices/'); return data; },
  create: async (cpData: CeilingPriceCreate) => { const { data } = await apiClient.post('/ceiling-prices/', cpData); return data; },
  update: async (id: string, cpData: CeilingPriceCreate) => { const { data } = await apiClient.put(`/ceiling-prices/${id}`, cpData); return data; },
  delete: async (id: string) => { await apiClient.delete(`/ceiling-prices/${id}`); },
};
