import { formatDate } from '@/utils/format';
import { PerformanceRange } from '@/types/performance';

// A paleta categórica das séries mora no tema dos gráficos (src/theme/chart.ts).
export { seriesColor } from '@/theme/chart';

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

