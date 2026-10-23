import { IndicatorCode, IndicatorUnit, IssuerKind, IssuerListingRow } from '@/types';
import { INDICATOR_META } from '@/utils/indicators';

/**
 * Regras da listagem de emissoras (docs/planos/PLANO_LISTAGEM_EMISSORAS.md, 3 e 5): colunas de cada aba,
 * filtros e ordenação, tudo no navegador sobre a aba inteira que o backend devolve. O estado fica na URL,
 * com os parâmetros de cada aba prefixados por ela (`stocks.dy=6~12`, `fiis.page=2`).
 */

export type ListingTab = 'stocks' | 'fiis';

export const TAB_KIND: Record<ListingTab, IssuerKind> = { stocks: 'COMPANY', fiis: 'FII' };
export const TAB_LABEL: Record<ListingTab, string> = { stocks: 'Ações', fiis: 'FIIs' };

/** Coluna numérica: ordenável e com faixa mín–máx no painel de filtros. */
export interface NumericColumn {
  /** Chave na URL e na ordenação. */
  key: string;
  label: string;
  unit: IndicatorUnit | 'EQUITY';
  /** Indicador de onde vem o valor; sem ele, preço ou PL. */
  indicator?: IndicatorCode;
  /** Pode ser negativo (lucro com prejuízo, passivo a descoberto): o filtro ganha o botão ± do Preço Teto. */
  negative?: boolean;
}

/**
 * Indicadores que podem sair negativos pelas regras do backend (IndicatorRules): ROE, LPA, VPA e VP por cota. P/L,
 * P/VP, payout e DY ficam nulos em vez de negativos; preço, cotistas e os percentuais do FII nunca são.
 */
const NEGATIVE = new Set<IndicatorCode>(['ROE', 'LPA', 'VPA', 'VP_COTA']);

const ind = (code: IndicatorCode, label = INDICATOR_META[code].label.toUpperCase()): NumericColumn => ({
  key: code.toLowerCase(), label, unit: unitOf(code), indicator: code, negative: NEGATIVE.has(code),
});

const unitOf = (code: IndicatorCode): IndicatorUnit => {
  switch (code) {
    case 'PL': case 'PVP': return 'RATIO';
    case 'LPA': case 'VPA': case 'VP_COTA': return 'BRL';
    case 'COTISTAS': return 'COUNT';
    default: return 'PERCENT';
  }
};

const PRICE: NumericColumn = { key: 'price', label: 'PREÇO', unit: 'BRL' };
const EQUITY: NumericColumn = { key: 'equity', label: 'PATRIMÔNIO LÍQUIDO', unit: 'EQUITY', negative: true };

/** Colunas numéricas na ordem da tabela (D5). */
export const NUMERIC_COLUMNS: Record<ListingTab, NumericColumn[]> = {
  stocks: [PRICE, ind('PL'), ind('PVP'), ind('DY'), ind('ROE'), ind('PAYOUT'), ind('LPA'), ind('VPA'), EQUITY],
  fiis: [PRICE, ind('PVP'), ind('DY'), ind('VP_COTA', 'VP/COTA'), ind('COTISTAS'), ind('IMOVEIS', 'IMÓVEIS'),
    ind('PAPEL', 'PAPEL'), ind('ALAVANCAGEM'), EQUITY],
};

/** Linha pronta para a tabela: os indicadores viram campos, para filtrar e ordenar. */
export interface ListingRow extends IssuerListingRow {
  values: Record<string, number | null>;
  notes: Record<string, string | null>;
}

export const toListingRows = (rows: IssuerListingRow[], tab: ListingTab): ListingRow[] =>
  rows.map(r => {
    const values: Record<string, number | null> = { price: r.price, equity: r.equity };
    const notes: Record<string, string | null> = {};
    for (const i of r.indicators) {
      values[i.indicator.toLowerCase()] = i.value;
      notes[i.indicator.toLowerCase()] = i.note;
    }
    for (const c of NUMERIC_COLUMNS[tab]) {
      if (!(c.key in values)) values[c.key] = null;
    }
    return { ...r, values, notes };
  });

// ------------------------------------------------------------------ estado na URL

export interface Range { min: number | null; max: number | null }

export interface TabState {
  /** Ordenação escolhida; null = padrão (PL decrescente). */
  sort: { key: string; dir: 'asc' | 'desc' } | null;
  page: number;
  pageSize: number;
  name: string;
  sector: string;
  subsector: string;
  segment: string;
  ranges: Record<string, Range>;
}

export const PAGE_SIZES = [10, 25, 50, 100];
const DEFAULT_PAGE_SIZE = 10;

/** "6~12", "~1", "0,5~" → faixa; texto em pt-BR (vírgula decimal). */
export const parseRange = (raw: string | null): Range | null => {
  if (!raw || !raw.includes('~')) return null;
  const [a, b] = raw.split('~');
  const min = parseNumber(a);
  const max = parseNumber(b);
  return min === null && max === null ? null : { min, max };
};

export const formatRange = (r: Range): string | null =>
  r.min === null && r.max === null ? null : `${r.min ?? ''}~${r.max ?? ''}`;

/** Número digitado em pt-BR ("1.234,5", "-3,2", "12") ou o da URL ("1234.5"). */
export const parseNumber = (s: string | null | undefined): number | null => {
  if (s === null || s === undefined) return null;
  const t = s.trim();
  if (t === '' || t === '-') return null;
  const normalized = t.includes(',') ? t.replace(/\./g, '').replace(',', '.') : t;
  const n = Number(normalized);
  return Number.isFinite(n) ? n : null;
};

export const readTabState = (params: URLSearchParams, tab: ListingTab): TabState => {
  const g = (k: string) => params.get(`${tab}.${k}`);
  const ord = g('sort');
  let sort: TabState['sort'] = null;
  if (ord) {
    const [key, dir] = ord.split('.');
    const known = key === 'ticker' || key === 'name' || key === 'sector'
      || NUMERIC_COLUMNS[tab].some(c => c.key === key);
    if (known && (dir === 'asc' || dir === 'desc')) sort = { key, dir };
  }
  const size = Number(g('size'));
  const ranges: Record<string, Range> = {};
  for (const c of NUMERIC_COLUMNS[tab]) {
    const r = parseRange(g(c.key));
    if (r) ranges[c.key] = r;
  }
  return {
    sort,
    page: Math.max(1, Number(g('page')) || 1),
    pageSize: PAGE_SIZES.includes(size) ? size : DEFAULT_PAGE_SIZE,
    name: g('name') ?? '',
    sector: g('sector') ?? '',
    subsector: g('subsector') ?? '',
    segment: g('segment') ?? '',
    ranges,
  };
};

/** Grava o estado da aba nos parâmetros, sem tocar os da outra aba nem os demais (aba, período do gráfico). */
export const writeTabState = (params: URLSearchParams, tab: ListingTab, s: TabState): URLSearchParams => {
  const next = new URLSearchParams(params);
  for (const k of [...next.keys()]) {
    if (k.startsWith(`${tab}.`)) next.delete(k);
  }
  const set = (k: string, v: string | null) => { if (v) next.set(`${tab}.${k}`, v); };
  set('sort', s.sort ? `${s.sort.key}.${s.sort.dir}` : null);
  set('page', s.page > 1 ? String(s.page) : null);
  set('size', s.pageSize !== DEFAULT_PAGE_SIZE ? String(s.pageSize) : null);
  set('name', s.name || null);
  set('sector', s.sector || null);
  set('subsector', s.subsector || null);
  set('segment', s.segment || null);
  for (const [k, r] of Object.entries(s.ranges)) set(k, formatRange(r));
  return next;
};

/** Quantos filtros estão ligados (o número do botão Filtros). */
export const activeFilterCount = (s: TabState): number =>
  (s.sector ? 1 : 0) + (s.subsector ? 1 : 0) + (s.segment ? 1 : 0)
  + Object.values(s.ranges).filter(r => r.min !== null || r.max !== null).length;

// ------------------------------------------------------------------ filtro e ordem

/** Sem acento e em maiúsculas: "Itaú" acha "ITAU". Mesma regra do LocalAssetSearch. */
export const normalize = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase();

/** O PL é digitado em R$ milhões; os demais na unidade da coluna (percentual em pontos). */
const rangeFactor = (c: NumericColumn) => (c.unit === 'EQUITY' ? 1_000_000 : 1);

export const applyFilters = (rows: ListingRow[], tab: ListingTab, s: TabState): ListingRow[] => {
  const q = normalize(s.name.trim());
  const ranges = NUMERIC_COLUMNS[tab]
    .map(c => ({ c, r: s.ranges[c.key] }))
    .filter(x => x.r && (x.r.min !== null || x.r.max !== null));
  return rows.filter(row => {
    if (q && !normalize(row.ticker).includes(q) && !normalize(row.name ?? '').includes(q)
      && !normalize(row.legalName ?? '').includes(q)) return false;
    if (s.sector && row.sector !== s.sector) return false;
    if (s.subsector && row.subsector !== s.subsector) return false;
    if (s.segment && row.segment !== s.segment) return false;
    for (const { c, r } of ranges) {
      const v = row.values[c.key];
      // faixa preenchida: quem não tem o indicador sai (F3)
      if (v === null || v === undefined) return false;
      const f = rangeFactor(c);
      if (r.min !== null && v < r.min * f) return false;
      if (r.max !== null && v > r.max * f) return false;
    }
    return true;
  });
};

const sortValue = (row: ListingRow, key: string): number | string | null => {
  if (key === 'ticker') return row.ticker;
  if (key === 'name') return row.name;
  if (key === 'sector') {
    const parts = [row.sector, row.subsector, row.segment].filter(Boolean);
    return parts.length ? parts.join(' | ') : null;
  }
  return row.values[key] ?? null;
};

const collator = new Intl.Collator('pt-BR', { numeric: true, sensitivity: 'base' });

/**
 * Ordem (D11): a escolhida, ou PL decrescente; vazios sempre no fim; empate pelo ticker crescente.
 */
export const sortRows = (rows: ListingRow[], sort: TabState['sort']): ListingRow[] => {
  const key = sort?.key ?? 'equity';
  const dir = sort?.dir ?? 'desc';
  const sign = dir === 'asc' ? 1 : -1;
  return [...rows].sort((a, b) => {
    const va = sortValue(a, key);
    const vb = sortValue(b, key);
    if (va === null && vb !== null) return 1;
    if (vb === null && va !== null) return -1;
    if (va !== null && vb !== null && va !== vb) {
      const cmp = typeof va === 'number' && typeof vb === 'number' ? va - vb : collator.compare(String(va), String(vb));
      if (cmp !== 0) return sign * cmp;
    }
    return collator.compare(a.ticker, b.ticker);
  });
};

/** Clique no cabeçalho: crescente → decrescente → volta ao padrão. */
export const nextSort = (current: TabState['sort'], key: string): TabState['sort'] => {
  if (!current || current.key !== key) return { key, dir: 'asc' };
  if (current.dir === 'asc') return { key, dir: 'desc' };
  return null;
};

/** Opções distintas, em ordem alfabética. */
export const distinct = (values: (string | null)[]): string[] =>
  [...new Set(values.filter((v): v is string => !!v))].sort((a, b) => collator.compare(a, b));
