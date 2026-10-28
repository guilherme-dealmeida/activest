import { DividendResponse, DividendType } from '@/types';

/**
 * Proventos por período — aba "Evolução dos Proventos" da tela de Dividendos
 * (docs/planos/PLANO_DIVIDENDOS_POR_PERIODO.md). Funções puras, testadas em `dividendPeriods.test.ts`.
 *
 * Regras (seção 2.1 do plano):
 * - C1: valor sempre líquido — `netAmount ?? totalAmount` (o TOTAL da tabela; sem regra de IR, o bruto), em reais.
 * - C2: mês sempre pelo pagamento; o recebido sem data de pagamento (histórico longo da B3) entra pela data com, como o
 *   `last12Months` do Dashboard; o a receber sem data não entra em mês nenhum (`undated`).
 * - C3: recebido × a receber pelo `received` do backend.
 * - C4: do primeiro ao último mês com provento, todo mês aparece, com zero quando não houve nada.
 * - C5: mês e ano corrente são parciais.
 * - C7: somas em centavos inteiros.
 * - D5: o recebido se divide por tipo de ativo, com FIIs na base; o a receber é um bloco só, sem tipo.
 */

/** O que a agregação lê de cada provento. */
export type PeriodDividend = Pick<
  DividendResponse,
  'paymentDate' | 'exDate' | 'received' | 'netAmount' | 'totalAmount' | 'assetTypeId' | 'assetTypeDescription'
>;

/** Tipo de ativo FIIs no cadastro (`asset_types.id` = 2, seed da V1): a base das barras (D5). */
export const FII_TYPE_ID = 2;
/** Chave do provento sem tipo de ativo. */
export const NO_TYPE_KEY = 'none';
/** `String(assetTypeId)` ou `NO_TYPE_KEY`. */
export type TypeKey = string;

/** Tipo de ativo que aparece no recebido, na ordem de empilhar (o primeiro fica na base). */
export interface AssetTypeSeries {
  key: TypeKey;
  id: number | null;
  label: string;
  receivedCents: number;
}

export interface MonthRow {
  /** `YYYY-MM`. */
  key: string;
  year: number;
  /** 1 a 12. */
  month: number;
  /** Recebido por tipo de ativo, em centavos. */
  received: Record<TypeKey, number>;
  receivedCents: number;
  toReceiveCents: number;
  totalCents: number;
  receivedCount: number;
  toReceiveCount: number;
  /** Mês corrente: parcial (C5). */
  current: boolean;
}

export interface YearRow {
  year: number;
  received: Record<TypeKey, number>;
  receivedCents: number;
  toReceiveCents: number;
  totalCents: number;
  count: number;
  /** Meses do ano dentro do intervalo da carteira (C4): 12, ou menos no primeiro e no último ano. */
  months: number;
  /** Total do ano ÷ `months`, em centavos. */
  monthlyAverageCents: number;
  /** Ano corrente (C5). */
  partial: boolean;
  /**
   * Crescimento sobre o ano anterior, em %: só entre dois anos completos (passados e com os 12 meses no intervalo);
   * nulo nos demais e quando o anterior é zero.
   */
  growthPct: number | null;
}

export interface DividendPeriods {
  months: MonthRow[];
  years: YearRow[];
  types: AssetTypeSeries[];
  /** A receber sem data de pagamento: fora dos meses (C2). */
  undated: { count: number; cents: number };
  /** `YYYY-MM` de hoje. */
  currentMonth: string;
}

export const toCents = (value: number): number => Math.round(value * 100);

/** C1: líquido; sem regra de IR, o bruto. */
export const netCents = (d: Pick<DividendResponse, 'netAmount' | 'totalAmount'>): number =>
  toCents(d.netAmount ?? d.totalAmount ?? 0);

/** C2: `YYYY-MM` do pagamento; recebido sem pagamento, o da data com; a receber sem pagamento, nulo. */
export const referenceMonth = (d: Pick<DividendResponse, 'paymentDate' | 'exDate' | 'received'>): string | null => {
  if (d.paymentDate) return d.paymentDate.slice(0, 7);
  return d.received && d.exDate ? d.exDate.slice(0, 7) : null;
};

export const typeKeyOf = (d: Pick<DividendResponse, 'assetTypeId'>): TypeKey =>
  d.assetTypeId == null ? NO_TYPE_KEY : String(d.assetTypeId);

export const nextMonthKey = (key: string): string => {
  let [y, m] = key.split('-').map(Number);
  m += 1;
  if (m > 12) { m = 1; y += 1; }
  return `${y}-${String(m).padStart(2, '0')}`;
};

/** `YYYY-MM` deslocado em `delta` meses. */
export const shiftMonthKey = (key: string, delta: number): string => {
  const [y, m] = key.split('-').map(Number);
  const total = y * 12 + (m - 1) + delta;
  return `${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, '0')}`;
};

const add = (rec: Record<TypeKey, number>, key: TypeKey, cents: number) => {
  rec[key] = (rec[key] ?? 0) + cents;
};

/**
 * Agrega os proventos em meses e anos. `today` = `YYYY-MM-DD` (o mês corrente e o ano parcial saem dele; a situação de
 * cada provento vem pronta no `received`).
 */
export const buildDividendPeriods = (dividends: PeriodDividend[], today: string): DividendPeriods => {
  const currentMonth = today.slice(0, 7);
  const currentYear = Number(today.slice(0, 4));
  const byMonth = new Map<string, MonthRow>();
  const typeTotals = new Map<TypeKey, AssetTypeSeries>();
  const undated = { count: 0, cents: 0 };

  const monthRow = (key: string): MonthRow => {
    let row = byMonth.get(key);
    if (!row) {
      const [year, month] = key.split('-').map(Number);
      row = {
        key, year, month, received: {}, receivedCents: 0, toReceiveCents: 0, totalCents: 0,
        receivedCount: 0, toReceiveCount: 0, current: key === currentMonth,
      };
      byMonth.set(key, row);
    }
    return row;
  };

  for (const d of dividends) {
    const cents = netCents(d);
    const key = referenceMonth(d);
    if (key == null) {
      undated.count += 1;
      undated.cents += cents;
      continue;
    }
    const row = monthRow(key);
    row.totalCents += cents;
    if (d.received) {
      const tk = typeKeyOf(d);
      add(row.received, tk, cents);
      row.receivedCents += cents;
      row.receivedCount += 1;
      const t = typeTotals.get(tk) ?? {
        key: tk, id: d.assetTypeId ?? null, label: d.assetTypeDescription ?? 'Sem tipo de ativo', receivedCents: 0,
      };
      t.receivedCents += cents;
      typeTotals.set(tk, t);
    } else {
      row.toReceiveCents += cents;
      row.toReceiveCount += 1;
    }
  }

  // C4: meses contínuos do primeiro ao último.
  const keys = [...byMonth.keys()].sort();
  const months: MonthRow[] = [];
  if (keys.length) {
    const last = keys[keys.length - 1];
    for (let k = keys[0]; k <= last; k = nextMonthKey(k)) months.push(monthRow(k));
  }

  // D5: FIIs na base; os demais pelo recebido, do maior para o menor (empate pelo id).
  const types = [...typeTotals.values()].sort((a, b) => {
    if (a.id === FII_TYPE_ID) return -1;
    if (b.id === FII_TYPE_ID) return 1;
    return b.receivedCents - a.receivedCents || (a.id ?? Infinity) - (b.id ?? Infinity);
  });

  const yearMap = new Map<number, YearRow>();
  for (const m of months) {
    let y = yearMap.get(m.year);
    if (!y) {
      y = {
        year: m.year, received: {}, receivedCents: 0, toReceiveCents: 0, totalCents: 0, count: 0, months: 0,
        monthlyAverageCents: 0, partial: m.year === currentYear, growthPct: null,
      };
      yearMap.set(m.year, y);
    }
    Object.entries(m.received).forEach(([k, c]) => add(y!.received, k, c));
    y.receivedCents += m.receivedCents;
    y.toReceiveCents += m.toReceiveCents;
    y.totalCents += m.totalCents;
    y.count += m.receivedCount + m.toReceiveCount;
    y.months += 1;
  }
  const years = [...yearMap.values()].sort((a, b) => a.year - b.year);
  const complete = (y: YearRow | undefined) => y != null && y.year < currentYear && y.months === 12;
  years.forEach((y, i) => {
    y.monthlyAverageCents = Math.round(y.totalCents / y.months);
    const prev = i > 0 ? years[i - 1] : undefined;
    if (complete(y) && complete(prev) && prev!.year === y.year - 1 && prev!.totalCents > 0) {
      y.growthPct = (y.totalCents / prev!.totalCents - 1) * 100;
    }
  });

  return { months, years, types, undated, currentMonth };
};

/** Quantos meses depois do corrente as janelas 12M e 24M mostram (C10): o a receber dos próximos meses. */
export const AHEAD_MONTHS = 6;

/**
 * Janela do gráfico mensal (C10): 12 ou 24 meses até o corrente e mais até `AHEAD_MONTHS` meses à frente, só até o
 * último mês com provento (o a receber mais distante fica no MAX, no ano dele e na tabela); um ano; ou tudo.
 */
export type MonthWindow = '12M' | '24M' | 'MAX' | number;

export const monthsInWindow = (months: MonthRow[], window: MonthWindow, currentMonth: string): MonthRow[] => {
  if (window === 'MAX') return months;
  if (typeof window === 'number') return months.filter(m => m.year === window);
  const from = shiftMonthKey(currentMonth, window === '12M' ? -11 : -23);
  const until = shiftMonthKey(currentMonth, AHEAD_MONTHS);
  return months.filter(m => m.key >= from && m.key <= until);
};

/** Média mensal da janela, em centavos: total dos meses até o corrente ÷ quantos são; nula sem nenhum. */
export const windowAverageCents = (months: MonthRow[], currentMonth: string): number | null => {
  const elapsed = months.filter(m => m.key <= currentMonth);
  if (!elapsed.length) return null;
  return Math.round(elapsed.reduce((s, m) => s + m.totalCents, 0) / elapsed.length);
};

/** Linha do detalhe do mês: um ativo. */
export interface MonthDetailRow {
  ticker: string;
  assetName: string;
  assetTypeId: number | null;
  assetTypeDescription: string | null;
  types: DividendType[];
  count: number;
  receivedCount: number;
  toReceiveCount: number;
  grossCents: number;
  taxCents: number;
  netCents: number;
}

/** IR do provento: retido na fonte + retenção do exterior + IR brasileiro estimado (o mesmo do `addRow` da tela). */
export const taxCents = (d: DividendResponse): number =>
  toCents((d.withholdingTax ?? 0) + (d.foreign?.withholdingTax ?? 0) + (d.foreign?.incomeTax ?? 0));

/** Proventos de um mês (C2) agrupados por ativo, do maior líquido para o menor. */
export const monthDetail = (dividends: DividendResponse[], key: string): MonthDetailRow[] => {
  const byTicker = new Map<string, MonthDetailRow>();
  for (const d of dividends) {
    if (referenceMonth(d) !== key) continue;
    const row = byTicker.get(d.ticker) ?? {
      ticker: d.ticker, assetName: d.assetName ?? '', assetTypeId: d.assetTypeId ?? null, assetTypeDescription: d.assetTypeDescription ?? null, types: [],
      count: 0, receivedCount: 0, toReceiveCount: 0, grossCents: 0, taxCents: 0, netCents: 0,
    };
    if (!row.types.includes(d.type)) row.types.push(d.type);
    row.count += 1;
    if (d.received) row.receivedCount += 1; else row.toReceiveCount += 1;
    row.grossCents += toCents(d.totalAmount ?? 0);
    row.taxCents += taxCents(d);
    row.netCents += netCents(d);
    byTicker.set(d.ticker, row);
  }
  return [...byTicker.values()].sort((a, b) => b.netCents - a.netCents || a.ticker.localeCompare(b.ticker, 'pt-BR'));
};

export const MONTH_SHORT = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/** "jun/2026". */
export const monthLabel = (key: string): string => {
  const [y, m] = key.split('-').map(Number);
  return `${MONTH_SHORT[m - 1]}/${y}`;
};

/** Centavos no formato que o Excel e o Sheets em português leem como número: "1234,56". */
const sheetNumber = (cents: number): string => (cents / 100).toFixed(2).replace('.', ',');

/**
 * Tabela mês a mês em texto separado por tabulação (D7): cabeçalho, um ano por linha (do mais recente ao mais antigo,
 * como na tela) e a linha Total. Mês fora do intervalo da carteira fica vazio.
 */
export const periodsToTsv = (periods: Pick<DividendPeriods, 'months' | 'years'>): string => {
  const header = ['Ano', ...MONTH_SHORT.map(m => m[0].toUpperCase() + m.slice(1)), 'Total', 'Média mensal'];
  const byKey = new Map(periods.months.map(m => [m.key, m]));
  const lines = [header.join('\t')];
  const columnTotals = new Array<number>(12).fill(0);
  [...periods.years].sort((a, b) => b.year - a.year).forEach(y => {
    const cells = MONTH_SHORT.map((_, i) => {
      const m = byKey.get(`${y.year}-${String(i + 1).padStart(2, '0')}`);
      if (!m) return '';
      columnTotals[i] += m.totalCents;
      return sheetNumber(m.totalCents);
    });
    lines.push([String(y.year), ...cells, sheetNumber(y.totalCents), sheetNumber(y.monthlyAverageCents)].join('\t'));
  });
  const grand = periods.years.reduce((s, y) => s + y.totalCents, 0);
  lines.push(['Total', ...columnTotals.map(sheetNumber), sheetNumber(grand), ''].join('\t'));
  return lines.join('\n');
};
