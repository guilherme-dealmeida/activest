import { apiClient } from './client';
import { Asset, AssetCategory, AssetType, IssuerKind, PriceRange, PriceSeries } from '../types';

export const assetsApi = {
  getAll: async (typeId?: number, query?: string) => {
    const params: Record<string, string> = {};
    if (typeId) params.typeId = String(typeId);
    if (query) params.query = query;
    const { data } = await apiClient.get<Asset[]>('/assets/', { params });
    return data;
  },
  /** Autocomplete só com ativos ligados a emissor (tela Ativos). */
  /** Ações e FIIs ligados a emissor; `issuerKind` restringe a um tipo (COMPANY = só ações, no Preço Teto). */
  searchWithIssuer: async (query: string, issuerKind?: IssuerKind) => {
    const { data } = await apiClient.get<Asset[]>('/assets/', { params: { query, withIssuer: 'true', issuerKind } });
    return data;
  },
  /** Cotação histórica ajustada: por atalho ou por período (de/até, AAAA-MM-DD). */
  prices: async (ticker: string, period: { range: PriceRange } | { from?: string; to?: string }) => {
    const { data } = await apiClient.get<PriceSeries>(`/assets/${encodeURIComponent(ticker)}/prices`,
      { params: period });
    return data;
  },
  getByTicker: async (ticker: string) => {
    const { data } = await apiClient.get<Asset>(`/assets/${ticker}`);
    return data;
  },
  getTypes: async () => {
    const { data } = await apiClient.get<AssetType[]>('/assets/types');
    return data;
  },
  getCategories: async (typeId?: number) => {
    const params: Record<string, string> = {};
    if (typeId) params.typeId = String(typeId);
    const { data } = await apiClient.get<AssetCategory[]>('/assets/categories', { params });
    return data;
  },
};
