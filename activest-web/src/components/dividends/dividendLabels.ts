import { DividendType } from '@/types';

/**
 * Rótulo de cada tipo de provento nos filtros e nos cards "por Tipo" (plural: é um grupo).
 * A ordem das opções é a do enum; só aparecem os tipos que existem nos proventos.
 */
export const TYPE_GROUP_LABELS: Record<DividendType, string> = {
  [DividendType.DIVIDENDO]: 'Dividendos',
  [DividendType.JCP]: 'JCP',
  [DividendType.RENDIMENTO]: 'Rendimentos',
  [DividendType.AMORTIZACAO]: 'Amortizações',
  [DividendType.RESTITUICAO_CAPITAL]: 'Restituições de capital',
};
export const TYPE_ORDER = Object.values(DividendType);
