import { describe, expect, it } from 'vitest';
import { IndicatorCode, IndicatorUnit, IssuerListingRow } from '@/types';
import {
  activeFilterCount, applyFilters, distinct, formatRange, nextSort, normalize, parseNumber, parseRange, readTabState,
  sortRows, toListingRows, writeTabState, TabState,
} from './issuerListing';

const row = (ticker: string, over: Partial<IssuerListingRow> = {},
  indicators: Partial<Record<IndicatorCode, number | null>> = {}): IssuerListingRow => ({
  ticker, name: ticker, legalName: null, issuerId: 1, hasLogo: false, sector: null, subsector: null, segment: null,
  price: null, priceDate: null, priceStale: false, basePeriodEnd: null, baseStale: false, equity: null,
  indicators: Object.entries(indicators).map(([indicator, value]) => ({
    indicator: indicator as IndicatorCode, unit: 'PERCENT' as IndicatorUnit, value: value ?? null, note: null,
  })),
  ...over,
});

const state = (over: Partial<TabState> = {}): TabState => ({
  sort: null, page: 1, pageSize: 10, name: '', sector: '', subsector: '', segment: '', ranges: {}, ...over,
});

const stocks = toListingRows([
  row('PETR4', { name: 'Petrobras', legalName: 'Petróleo Brasileiro S.A.', sector: 'Petróleo', equity: 400e9, price: 38 },
    { DY: 12.5, ROE: 20 }),
  row('ITUB4', { name: 'Itaú Unibanco', sector: 'Financeiro', equity: 200e9, price: 33 }, { DY: 6, ROE: -2 }),
  row('MGLU3', { name: 'Magazine Luiza', sector: 'Varejo', equity: 10e9, price: 9 }, { DY: null }),
  row('ABCD3', { name: 'Sem PL', sector: 'Varejo', equity: null, price: 5 }, { DY: 1 }),
], 'stocks');

describe('linhas da listagem', () => {
  it('indicadores viram campos; colunas sem indicador ficam nulas', () => {
    expect(stocks[0].values).toMatchObject({ price: 38, equity: 400e9, dy: 12.5, roe: 20, pl: null });
  });
});

describe('filtros', () => {
  it('busca por ticker, nome ou razão social, sem acento e sem caixa', () => {
    expect(applyFilters(stocks, 'stocks', state({ name: 'itau' })).map(r => r.ticker)).toEqual(['ITUB4']);
    expect(applyFilters(stocks, 'stocks', state({ name: 'petróleo bras' })).map(r => r.ticker)).toEqual(['PETR4']);
    expect(normalize('Itaú')).toBe('ITAU');
  });

  it('faixa na unidade da coluna (percentual em pontos); só mín ou só máx', () => {
    expect(applyFilters(stocks, 'stocks', state({ ranges: { dy: { min: 6, max: 12 } } })).map(r => r.ticker))
      .toEqual(['ITUB4']);
    expect(applyFilters(stocks, 'stocks', state({ ranges: { dy: { min: 6, max: null } } })).map(r => r.ticker))
      .toEqual(['PETR4', 'ITUB4']);
    expect(applyFilters(stocks, 'stocks', state({ ranges: { roe: { min: null, max: 0 } } })).map(r => r.ticker))
      .toEqual(['ITUB4']);
  });

  it('patrimônio líquido digitado em R$ milhões', () => {
    expect(applyFilters(stocks, 'stocks', state({ ranges: { equity: { min: 100_000, max: null } } })).map(r => r.ticker))
      .toEqual(['PETR4', 'ITUB4']);
  });

  it('faixa preenchida tira quem não tem o indicador', () => {
    expect(applyFilters(stocks, 'stocks', state({ ranges: { dy: { min: 0, max: null } } })).map(r => r.ticker))
      .not.toContain('MGLU3');
  });

  it('setor', () => {
    expect(applyFilters(stocks, 'stocks', state({ sector: 'Varejo' })).map(r => r.ticker)).toEqual(['MGLU3', 'ABCD3']);
  });

  it('quantos filtros estão ligados', () => {
    expect(activeFilterCount(state({ sector: 'Varejo', ranges: { dy: { min: 1, max: null }, roe: { min: null, max: null } } })))
      .toBe(2);
  });
});

describe('ordenação', () => {
  it('padrão: PL decrescente, vazios no fim', () => {
    expect(sortRows(stocks, null).map(r => r.ticker)).toEqual(['PETR4', 'ITUB4', 'MGLU3', 'ABCD3']);
  });

  it('vazios no fim também na crescente; empate pelo ticker', () => {
    expect(sortRows(stocks, { key: 'dy', dir: 'asc' }).map(r => r.ticker)).toEqual(['ABCD3', 'ITUB4', 'PETR4', 'MGLU3']);
    const tie = toListingRows([row('BBB3', { price: 10 }), row('AAA3', { price: 10 })], 'stocks');
    expect(sortRows(tie, { key: 'price', dir: 'desc' }).map(r => r.ticker)).toEqual(['AAA3', 'BBB3']);
  });

  it('clique no cabeçalho: crescente → decrescente → padrão', () => {
    expect(nextSort(null, 'dy')).toEqual({ key: 'dy', dir: 'asc' });
    expect(nextSort({ key: 'dy', dir: 'asc' }, 'dy')).toEqual({ key: 'dy', dir: 'desc' });
    expect(nextSort({ key: 'dy', dir: 'desc' }, 'dy')).toBeNull();
    expect(nextSort({ key: 'dy', dir: 'desc' }, 'roe')).toEqual({ key: 'roe', dir: 'asc' });
  });

  it('opções distintas em ordem alfabética', () => {
    expect(distinct(['Varejo', null, 'Energia', 'Varejo', ''])).toEqual(['Energia', 'Varejo']);
  });
});

describe('estado na URL', () => {
  it('faixas: "6~12", "~1", "0,5~"', () => {
    expect(parseRange('6~12')).toEqual({ min: 6, max: 12 });
    expect(parseRange('~1')).toEqual({ min: null, max: 1 });
    expect(parseRange('0,5~')).toEqual({ min: 0.5, max: null });
    expect(parseRange('~')).toBeNull();
    expect(parseRange('12')).toBeNull();
    expect(formatRange({ min: 6, max: null })).toBe('6~');
    expect(formatRange({ min: null, max: null })).toBeNull();
  });

  it('números em pt-BR e da URL', () => {
    expect(parseNumber('1.234,5')).toBe(1234.5);
    expect(parseNumber('-3,2')).toBe(-3.2);
    expect(parseNumber('1234.5')).toBe(1234.5);
    expect(parseNumber('-')).toBeNull();
    expect(parseNumber('abc')).toBeNull();
  });

  it('A2: "1.234" sem vírgula é lido como 1,234 (o formato da URL; os campos do painel já entregam número)', () => {
    expect(parseNumber('1.234')).toBe(1.234);
  });

  it('ida e volta: grava só o que difere do padrão e não toca a outra aba', () => {
    const s = state({ sort: { key: 'dy', dir: 'desc' }, page: 2, pageSize: 25, name: 'banco', sector: 'Financeiro',
      ranges: { dy: { min: 6, max: 12 } } });
    const params = writeTabState(new URLSearchParams('tab=fiis&fiis.page=3&range=5A'), 'stocks', s);
    expect(params.get('stocks.sort')).toBe('dy.desc');
    expect(params.get('stocks.dy')).toBe('6~12');
    expect(params.get('fiis.page')).toBe('3');
    expect(params.get('range')).toBe('5A');
    expect(readTabState(params, 'stocks')).toEqual(s);
    expect(writeTabState(new URLSearchParams(), 'stocks', state()).toString()).toBe('');
  });

  it('valores inválidos na URL voltam ao padrão', () => {
    const s = readTabState(new URLSearchParams('stocks.sort=xyz.asc&stocks.page=-4&stocks.size=7'), 'stocks');
    expect(s).toMatchObject({ sort: null, page: 1, pageSize: 10 });
  });
});
