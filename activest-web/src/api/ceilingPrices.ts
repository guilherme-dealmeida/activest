import { apiClient } from './client';
import {
  CeilingDefaults,
  CeilingPriceDetail,
  CeilingPriceInput,
  CeilingPriceListItem,
  CeilingResult,
} from '@/types';

/** Preço Teto pelo modelo PREÇO TETO V2 (docs/planos/PLANO_PRECO_TETO.md, 5.3). */
export const ceilingPricesApi = {
  /** Coluna "Alerta": avisar nas Notificações quando o preço atual ficar abaixo do ideal. */
  setAlert: async (id: string, enabled: boolean) => {
    const { data } = await apiClient.patch<{ alertEnabled: boolean }>(`/ceiling-prices/${id}/alert`, { enabled });
    return data;
  },
  list: async () => {
    const { data } = await apiClient.get<CeilingPriceListItem[]>('/ceiling-prices');
    return data;
  },
  /** Padrões de hoje para o ticker; traz o id se o usuário já tem cálculo para ele. */
  defaults: async (ticker: string) => {
    const { data } = await apiClient.get<CeilingDefaults>(`/ceiling-prices/defaults/${encodeURIComponent(ticker)}`);
    return data;
  },
  /** Calcula sem gravar. */
  calculate: async (input: CeilingPriceInput) => {
    const { data } = await apiClient.post<CeilingResult>('/ceiling-prices/calculate', input);
    return data;
  },
  get: async (id: string) => {
    const { data } = await apiClient.get<CeilingPriceDetail>(`/ceiling-prices/${id}`);
    return data;
  },
  create: async (input: CeilingPriceInput) => {
    const { data } = await apiClient.post<CeilingPriceDetail>('/ceiling-prices', input);
    return data;
  },
  update: async (id: string, input: CeilingPriceInput) => {
    const { data } = await apiClient.put<CeilingPriceDetail>(`/ceiling-prices/${id}`, input);
    return data;
  },
  delete: async (id: string) => {
    await apiClient.delete(`/ceiling-prices/${id}`);
  },
};
