import { apiClient } from './client';
import { StrategyUpdate } from '../types';

export const strategyApi = {
  get: async () => { const { data } = await apiClient.get('/strategy/'); return data; },
  update: async (stratData: StrategyUpdate) => { const { data } = await apiClient.put('/strategy/', stratData); return data; },
  getRebalance: async () => { const { data } = await apiClient.get('/strategy/rebalance'); return data; },
};
