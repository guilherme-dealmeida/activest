import { apiClient } from './client';
import { AssetIndicators, CompanyData, IndicatorCode, IndicatorHistory, IndicatorPeriod, IssuerKind, IssuerListingRow } from '../types';

/** Indicadores de ações e FIIs (app-api, F9 do docs/planos/PLANO_EMPRESAS_BALANCOS.md). */
export const indicatorsApi = {
  /** Listagem de emissoras: uma aba inteira, já na ordem do PL decrescente (PLANO_LISTAGEM_EMISSORAS, 4.2). */
  listing: async (kind: IssuerKind) => {
    const { data } = await apiClient.get<IssuerListingRow[]>('/assets/indicators', { params: { kind } });
    return data;
  },
  current: async (ticker: string) => {
    const { data } = await apiClient.get<AssetIndicators>(`/assets/${encodeURIComponent(ticker)}/indicators`);
    return data;
  },
  /** Card "Dados da empresa" / "Dados do fundo" (docs/planos/PLANO_TELA_ATIVOS.md, D2 a D7). */
  companyData: async (ticker: string) => {
    const { data } = await apiClient.get<CompanyData>(`/assets/${encodeURIComponent(ticker)}/company-data`);
    return data;
  },
  /** Histórico de todos os indicadores numa chamada — sparklines (B4). */
  historyAll: async (ticker: string, period: IndicatorPeriod = 'YEAR') => {
    const { data } = await apiClient.get<IndicatorHistory[]>(
      `/assets/${encodeURIComponent(ticker)}/indicators/history`, { params: { period } });
    return data;
  },
  history: async (ticker: string, indicator: IndicatorCode, period: IndicatorPeriod) => {
    const { data } = await apiClient.get<IndicatorHistory>(
      `/assets/${encodeURIComponent(ticker)}/indicators/${indicator}/history`, { params: { period } });
    return data;
  },
};
