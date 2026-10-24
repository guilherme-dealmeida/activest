import { formatDate } from '@/utils/format';
import { PerformanceRange } from '@/types/performance';

/**
 * Cores da Rentabilidade (docs/planos/PLANO_RENTABILIDADE.md, 6.1), validadas com o validador da skill de
 * visualização contra as superfícies do tema (#111111 escuro, #ffffff claro): banda de luminosidade, croma,
 * separação para daltonismo entre vizinhos e contraste. A carteira usa o dourado da marca escurecido para a banda;
 * os benchmarks seguem a ordem categórica que passou (azul, verde-água, laranja, violeta, magenta, verde).
 * A cor é do benchmark, nunca da posição na lista: tirar um não repinta os outros.
 */
const DARK: Record<string, string> = {
  CARTEIRA: '#ad8a32',
  CDI: '#3987e5',
  IBOV: '#199e70',
  IPCA: '#d95926',
  IFIX: '#9085e9',
  IPCA_MAIS: '#d55181',
  BTC: '#008300',
  SP500_IVVB11: '#e66767',
  SELIC: '#8c8c8c',
};
const LIGHT: Record<string, string> = {
  CARTEIRA: '#9a7a26',
  CDI: '#2a78d6',
  IBOV: '#1baf7a',
  IPCA: '#eb6834',
  IFIX: '#6250d6',
  IPCA_MAIS: '#e87ba4',
  BTC: '#008300',
  SP500_IVVB11: '#e34948',
  SELIC: '#7a7a7a',
};

/** Cor de uma série: CARTEIRA ou o código do benchmark ("IPCA_MAIS:6.0" usa a do IPCA_MAIS). */
export const seriesColor = (key: string, isDark: boolean) => {
  const code = key.split(':')[0];
  return (isDark ? DARK : LIGHT)[code] ?? (isDark ? '#8c8c8c' : '#7a7a7a');
};

/** Pontos percentuais com sinal: "+12,34%". null = "—". */
export const pct = (v: number | null | undefined, digits = 2, sign = true) => {
  if (v == null) return '—';
  const s = v.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits });
  return `${sign && v > 0 ? '+' : ''}${s}%`;
};

/** Diferença em pontos percentuais: "+1,20 p.p.". */
export const pp = (v: number | null | undefined) => {
  if (v == null) return '—';
  const s = v.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return `${v > 0 ? '+' : ''}${s} p.p.`;
};

export const date = (iso: string | null | undefined) => (iso ? formatDate(iso) : '—');

export const monthYear = (iso: string | null | undefined) => {
  if (!iso) return '—';
  const [y, m] = iso.split('-');
  return `${m}/${y}`;
};

export const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/** Rótulo de um período da API ("2026-09" → "set/26", "2026-T3" → "3º tri/26", "2026" → "2026"). */
export const periodLabel = (key: string) => {
  const m = /^(\d{4})-(\d{2})$/.exec(key);
  if (m) return `${MONTHS[Number(m[2]) - 1]}/${m[1].slice(2)}`;
  const q = /^(\d{4})-T(\d)$/.exec(key);
  if (q) return `${q[2]}º tri/${q[1].slice(2)}`;
  return key;
};

export const RANGES: PerformanceRange[] = ['1M', '6M', 'YTD', '1A', '5A', 'MAX'];
export const RANGE_LABEL: Record<PerformanceRange, string> = {
  '1M': '1M', '6M': '6M', YTD: 'No ano', '1A': '12M', '5A': '5A', MAX: 'Desde o início',
};
export const MAX_BENCHMARKS = 5;

/** Um benchmark escolhido: código e, no IPCA + x%, a taxa. */
export interface BenchmarkPick {
  code: string;
  spread?: number;
}

export const benchKey = (b: BenchmarkPick) => (b.spread != null ? `${b.code}:${b.spread.toFixed(1)}` : b.code);
export const benchParam = (list: BenchmarkPick[]) => list.map(b => (b.spread != null ? `${b.code}:${b.spread}` : b.code)).join(',');

