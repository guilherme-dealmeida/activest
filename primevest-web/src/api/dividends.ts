import { apiClient } from './client';
import { DividendResponse, DividendSummary } from '../types';

export const dividendsApi = {
  getSummary: async () => { const { data } = await apiClient.get<DividendSummary>('/dividends/'); return data; },
  getByAsset: async (ticker: string) => { const { data } = await apiClient.get<DividendResponse[]>(`/dividends/asset/${ticker}`); return data; },
};
