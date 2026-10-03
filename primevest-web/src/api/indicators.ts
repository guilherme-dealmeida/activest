import { apiClient } from './client';
import { AssetIndicators, IndicatorCode, IndicatorHistory, IndicatorPeriod } from '../types';

/** Indicadores de ações e FIIs (app-api, F9 do docs/planos/PLANO_EMPRESAS_BALANCOS.md). */
export const indicatorsApi = {
  current: async (ticker: string) => {
    const { data } = await apiClient.get<AssetIndicators>(`/assets/${encodeURIComponent(ticker)}/indicators`);
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
