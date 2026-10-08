import { apiClient } from './client';
import { IndexChart, IndexView, MarketIndex, PriceRange } from '../types';

/** Índices de mercado (docs/planos/PLANO_INDICES_MERCADO.md): lista e gráfico de cada card da tela Índices. */
export const indicesApi = {
  list: async () => {
    const { data } = await apiClient.get<MarketIndex[]>('/indices');
    return data;
  },
  /** `view` só nos mensais (IPCA, IGP-M, INPC): MENSAL ou ANUAL (acumulado em 12 meses). */
  chart: async (code: string, period: { range: PriceRange } | { from?: string; to?: string }, view?: IndexView) => {
    const { data } = await apiClient.get<IndexChart>(`/indices/${encodeURIComponent(code)}/chart`,
      { params: { ...period, view } });
    return data;
  },
};
