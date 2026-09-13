import { apiClient } from './client';
import { TransactionCreate } from '../types';

export const transactionsApi = {
  list: async () => { const { data } = await apiClient.get('/transactions/'); return data; },
  create: async (txData: TransactionCreate) => { const { data } = await apiClient.post('/transactions/', txData); return data; },
  getById: async (id: string) => { const { data } = await apiClient.get(`/transactions/${id}`); return data; },
  delete: async (id: string) => { await apiClient.delete(`/transactions/${id}`); },
};
