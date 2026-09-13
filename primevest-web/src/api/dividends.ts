import { apiClient } from './client';
import { DividendCreate } from '../types';

export const dividendsApi = {
  getSummary: async () => { const { data } = await apiClient.get('/dividends/'); return data; },
  register: async (divData: DividendCreate) => { const { data } = await apiClient.post('/dividends/', divData); return data; },
  getByAsset: async (ticker: string) => { const { data } = await apiClient.get(`/dividends/asset/${ticker}`); return data; },
};
