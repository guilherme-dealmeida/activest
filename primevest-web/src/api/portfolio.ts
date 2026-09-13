import { apiClient } from './client';

export const portfolioApi = {
  getSummary: async () => { const { data } = await apiClient.get('/portfolio/'); return data; },
  getAssets: async () => { const { data } = await apiClient.get('/portfolio/assets'); return data; },
  getAllocation: async () => { const { data } = await apiClient.get('/portfolio/allocation'); return data; },
  getAllocationDetailed: async () => { const { data } = await apiClient.get('/portfolio/allocation/detailed'); return data; },
  getPerformance: async () => { const { data } = await apiClient.get('/portfolio/performance'); return data; },
  getDividends: async () => { const { data } = await apiClient.get('/portfolio/dividends'); return data; },
};
