import { apiClient } from './client';
import { StrategyUpdate } from '../types';

export const strategyApi = {
  get: async () => { const { data } = await apiClient.get('/strategy/'); return data; },
  update: async (stratData: StrategyUpdate) => { const { data } = await apiClient.put('/strategy/', stratData); return data; },
  delete: async (assetTypeId: number) => { await apiClient.delete(`/strategy/${assetTypeId}`); },
  getRebalance: async () => { const { data } = await apiClient.get('/strategy/rebalance'); return data; },
  getAssetTypes: async () => { const { data } = await apiClient.get('/assets/types'); return data; },
};
