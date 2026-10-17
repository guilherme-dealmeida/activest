/**
 * Rentabilidade comparativa (docs/planos/PLANO_RENTABILIDADE.md, seção 5) — espelho de PerformanceDTOs.java.
 * Retornos em pontos percentuais (12,5 = 12,5%); valores em reais. null = não calculável (o motivo vem na cobertura
 * ou na nota).
 */

export type PerformanceRange = '1M' | '6M' | 'YTD' | '1A' | '5A' | 'MAX';
export type PerformanceBasis = 'NET' | 'GROSS';
export type ReservationFilter = 'WITH' | 'WITHOUT';
export type Granularity = 'MONTH' | 'QUARTER' | 'YEAR';
export type BreakdownLevel = 'TYPE' | 'CATEGORY' | 'POSITION' | 'ISSUER';

export interface BenchmarkInfo {
  code: string;
  name: string;
  category: string;
  kind: 'INDICE' | 'ATIVO';
  note: string | null;
  acceptsSpread: boolean;
  defaultSelected: boolean;
  source: string;
  periodicity: string;
  firstDate: string | null;
  lastDate: string | null;
}

/** Tesouro IPCA+ à venda mais curto: a taxa do IPCA + x% padrão do Tesouro. */
export interface TreasuryReference {
  ticker: string;
  maturity: string;
  rate: number;
  rateDate: string;
}

export interface BenchmarkCatalog {
  benchmarks: BenchmarkInfo[];
  /** Benchmarks padrão ao filtrar uma classe (D4), pelo nome interno do tipo; o primeiro é o principal ("IPCA_MAIS:6.8"). */
  defaultsByType: Record<string, string[]>;
  treasuryIpca?: TreasuryReference | null;
}

export interface Gap {
  ticker: string;
  from: string;
  to: string;
  status: 'SEM_PRECO' | 'ULTIMO_NEGOCIO' | 'ESTIMADO';
}

export interface Coverage {
  firstDate: string | null;
  from: string | null;
  baseDate: string | null;
  to: string | null;
  available: boolean;
  unavailable: string[];
  gaps: Gap[];
  realUntil: string | null;
  basisApplies: boolean;
  basis: PerformanceBasis;
  reservation: ReservationFilter;
  empty: boolean;
}

export interface BenchmarkResult {
  code: string;
  name: string;
  value: number | null;
  until: string | null;
  diff: number | null;
  partial: boolean;
}

export interface PerformanceSummary {
  coverage: Coverage;
  twr: number | null;
  primary: BenchmarkResult | null;
  diff: number | null;
  real: number | null;
  realUntil: string | null;
  inflation: number | null;
  percentOfCdi: number | null;
  cdi: number | null;
  xirr: number | null;
  xirrNote: string | null;
  valueStart: number | null;
  valueEnd: number | null;
  contributions: number | null;
  withdrawals: number | null;
  distributions: number | null;
  result: number | null;
  benchmarks: BenchmarkResult[];
}

export interface SeriesPoint {
  date: string;
  portfolio: number | null;
  benchmarks: Record<string, number | null>;
  unavailable: boolean;
  stale: boolean;
  estimated: boolean;
}

export interface PerformanceSeries {
  coverage: Coverage;
  benchmarks: BenchmarkResult[];
  density: 'DIARIA' | 'SEMANAL' | 'MENSAL';
  points: SeriesPoint[];
}

export interface PeriodRow {
  key: string;
  from: string;
  to: string;
  partial: boolean;
  portfolio: number | null;
  benchmarks: Record<string, number | null>;
}

export interface PerformancePeriodic {
  coverage: Coverage;
  granularity: Granularity;
  benchmarks: BenchmarkResult[];
  rows: PeriodRow[];
}

export interface TableRow {
  code: string;
  name: string;
  portfolio: boolean;
  month: number | null;
  year: number | null;
  months12: number | null;
  period: number | null;
  diffPeriod: number | null;
  /** O indicador para antes do fim (IPCA: último mês publicado); ano, 12 meses, período e diferença vão até aqui. */
  until: string | null;
}

export interface PerformanceTable {
  coverage: Coverage;
  monthFrom: string | null;
  yearFrom: string | null;
  months12From: string | null;
  rows: TableRow[];
}

export interface BreakdownRow {
  key: string;
  label: string;
  detail: string | null;
  typeName: string;
  typeDescription: string | null;
  categoryId: number | null;
  categoryName: string | null;
  positionId: string | null;
  ticker: string | null;
  hasLogo: boolean | null;
  individual: number | null;
  gain: number | null;
  contribution: number | null;
  weight: number | null;
  valueEnd: number;
  closed: boolean;
  unavailable: boolean;
  firstDate: string | null;
  lastDate: string | null;
}

export interface PerformanceBreakdown {
  coverage: Coverage;
  level: BreakdownLevel;
  total: number | null;
  totalGain: number | null;
  rows: BreakdownRow[];
}

export interface WealthPoint {
  date: string;
  nominal: number | null;
  real: number | null;
  invested: number;
  investedReal: number;
  distributions: number;
  result: number | null;
}

export interface PerformanceWealth {
  coverage: Coverage;
  realBase: string | null;
  density: string;
  points: WealthPoint[];
}

export interface PerformanceRisk {
  coverage: Coverage;
  volatility: number | null;
  maxDrawdown: number | null;
  peak: string | null;
  trough: string | null;
  sharpe: number | null;
  sortino: number | null;
  annualReturn: number | null;
  annualCdi: number | null;
  days: number;
  from: string | null;
  to: string | null;
  note: string | null;
}

/** Parâmetros comuns das consultas. */
export interface PerformanceQuery {
  range?: PerformanceRange;
  from?: string;
  to?: string;
  type?: string;
  category?: number;
  position?: string;
  reservation?: ReservationFilter;
  basis?: PerformanceBasis;
  /** "CDI,IBOV,IPCA_MAIS:6" */
  benchmarks?: string;
}
