import { apiClient } from './client';
import { IndexChart, IndexView, MarketIndex, PriceRange, YieldCurve, YieldCurveKind } from '../types';

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
  /** Curva do dia (PLANO_JUROS_FUTUROS): sem `dates`, hoje, 1 mês e 1 ano antes. */
  curve: async (curve: YieldCurveKind, dates?: string[]) => {
    const { data } = await apiClient.get<YieldCurve>(`/indices/curves/${curve}`,
      { params: dates?.length ? { dates: dates.join(',') } : undefined });
    return data;
  },
};
