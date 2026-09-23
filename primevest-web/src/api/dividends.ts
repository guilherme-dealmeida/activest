import { apiClient } from './client';

export const dividendsApi = {
  getSummary: async () => { const { data } = await apiClient.get('/dividends/'); return data; },
  getByAsset: async (ticker: string) => { const { data } = await apiClient.get(`/dividends/asset/${ticker}`); return data; },
};
