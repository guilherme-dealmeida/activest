import { apiClient } from './client';
import { Currency, CurrencyAdminRow, CurrencyRate, CurrencyRatePoint, ForeignTaxRule } from '../types';

/**
 * Moedas e câmbio (docs/planos/PLANO_CADASTRO_MOEDAS.md, 10.4). Leitura na app-api; o cadastro (tela de administrador
 * Moedas) mora na adm-api — o proxy do front manda /api/v1/admin/currencies para lá.
 */
export const currenciesApi = {
  list: async () => { const { data } = await apiClient.get<Currency[]>('/currencies'); return data; },
  /** Câmbio padrão de uma data (PTAX; sem boletim no dia, o último carregado). null = nenhum câmbio até a data. */
  rate: async (code: string, date: string): Promise<CurrencyRate | null> => {
    try {
      const { data } = await apiClient.get<CurrencyRate>(`/currencies/${code}/rate`, { params: { date } });
      return data;
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } })?.response?.status;
      if (status === 404) return null;
      throw e;
    }
  },
  rates: async (code: string, from?: string, to?: string) => {
    const { data } = await apiClient.get<CurrencyRatePoint[]>(`/currencies/${code}/rates`, { params: { from, to } });
    return data;
  },
  taxRules: async () => { const { data } = await apiClient.get<ForeignTaxRule[]>('/foreign-tax-rules'); return data; },
};

export interface CurrencyAdminInput {
  code?: string;
  name: string;
  symbol: string;
  decimals: number;
  countryCode: string | null;
  rateSource: 'NONE' | 'BCB_PTAX';
  active: boolean;
}

export const currencyAdminApi = {
  list: async () => { const { data } = await apiClient.get<CurrencyAdminRow[]>('/admin/currencies'); return data; },
  create: async (body: CurrencyAdminInput) => {
    const { data } = await apiClient.post<CurrencyAdminRow>('/admin/currencies', body); return data;
  },
  update: async (code: string, body: CurrencyAdminInput) => {
    const { data } = await apiClient.put<CurrencyAdminRow>(`/admin/currencies/${code}`, body); return data;
  },
};
