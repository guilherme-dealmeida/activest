import { apiClient } from './client';
import { DividendResponse, DividendSummary } from '../types';

export const dividendsApi = {
  getSummary: async () => { const { data } = await apiClient.get<DividendSummary>('/dividends/'); return data; },
  getByAsset: async (ticker: string, assetType?: string | null) => { const { data } = await apiClient.get<DividendResponse[]>(`/dividends/asset/${encodeURIComponent(ticker)}`, { params: { assetType: assetType ?? undefined } }); return data; },
};
