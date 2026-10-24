import { apiClient } from './client';
import {
  BenchmarkCatalog, BreakdownLevel, Granularity, PerformanceBreakdown, PerformancePeriodic, PerformanceQuery,
  PerformanceRisk, PerformanceSeries, PerformanceSummary, PerformanceTable, PerformanceWealth,
} from '@/types/performance';

/** Rentabilidade comparativa (docs/planos/PLANO_RENTABILIDADE.md, seção 5). Só os dados do próprio usuário. */
const get = async <T,>(path: string, params: object): Promise<T> => {
  const { data } = await apiClient.get<T>(`/performance/${path}`, { params });
  return data;
};

export const performanceApi = {
  benchmarks: () => get<BenchmarkCatalog>('benchmarks', {}),
  summary: (q: PerformanceQuery) => get<PerformanceSummary>('summary', q),
  series: (q: PerformanceQuery) => get<PerformanceSeries>('series', q),
  periodic: (q: PerformanceQuery, granularity: Granularity) =>
    get<PerformancePeriodic>('periodic', { ...q, granularity }),
  table: (q: PerformanceQuery) => get<PerformanceTable>('table', q),
  breakdown: (q: PerformanceQuery, level: BreakdownLevel) =>
    get<PerformanceBreakdown>('breakdown', { ...q, level }),
  wealth: (q: PerformanceQuery) => get<PerformanceWealth>('wealth', { ...q, benchmarks: undefined }),
  risk: (q: PerformanceQuery) => get<PerformanceRisk>('risk', { ...q, benchmarks: undefined }),
};
