import { apiClient } from './client';
import { Asset } from '@/types';

/** Fundos de investimento (docs/planos/PLANO_FUNDOS_INVESTIMENTO.md, 11.5). */
export const fundsApi = {
  /** Busca por nome ou CNPJ; {@code inactive} = quantos encerrados também casam ("Mostrar N encerrados", D11). */
  search: async (typeId: number, query: string, includeInactive: boolean) => {
    const r = await apiClient.get<Asset[]>('/assets/', { params: { typeId, query, includeInactive } });
    return { items: r.data, inactive: Number(r.headers['x-inactive-count'] ?? 0) };
  },
  /** Cota do fundo na data, ou a última anterior (com a data dela): pré-preenche a Cota da Nova transação. */
  quote: async (ticker: string, date: string) => {
    const { data } = await apiClient.get<{ date: string; quote: number }>(
      `/funds/${encodeURIComponent(ticker)}/quote`, { params: { date } });
    return data;
  },
};
