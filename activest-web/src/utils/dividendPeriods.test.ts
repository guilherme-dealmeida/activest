import { describe, expect, it } from 'vitest';
import { DividendResponse, DividendType } from '@/types';
import {
  buildDividendPeriods, monthDetail, monthsInWindow, netCents, periodsToTsv, referenceMonth, shiftMonthKey,
  windowAverageCents,
} from './dividendPeriods';

const ACAO = { assetTypeId: 1, assetTypeDescription: 'Ações' };
const FII = { assetTypeId: 2, assetTypeDescription: 'FIIs' };
const ETF = { assetTypeId: 5, assetTypeDescription: 'ETFs' };

let nextId = 1;
/** Provento de teste: recebido, líquido = bruto, com data de pagamento. */
const div = (over: Partial<DividendResponse>): DividendResponse => ({
  id: nextId++, ticker: 'TEST11', assetName: 'Teste', amountPerShare: 1, totalAmount: 10, exDate: '2026-01-10',
  paymentDate: '2026-01-20', type: DividendType.RENDIMENTO, sharesOnDate: 10, withholdingRate: 0, withholdingTax: 0,
  netAmountPerShare: 1, netAmount: 10, withholdingLegalBasis: null, received: true, cashEventSource: 'B3',
  paymentDateSource: null, assetTypeId: 2, assetTypeDescription: 'FIIs', hasLogo: null, issuerId: null, foreign: null,
  ...over,
});

const TODAY = '2026-10-27';

describe('regras de um provento', () => {
  it('C1: líquido; sem regra de IR, o bruto', () => {
    expect(netCents({ netAmount: 8.5, totalAmount: 10 })).toBe(850);
    expect(netCents({ netAmount: null, totalAmount: 10.07 })).toBe(1007);
  });

  it('C2: mês do pagamento; recebido sem pagamento pela data com; a receber sem pagamento fica fora', () => {
    expect(referenceMonth({ paymentDate: '2026-02-05', exDate: '2026-01-30', received: true })).toBe('2026-02');
    expect(referenceMonth({ paymentDate: null, exDate: '2019-03-14', received: true })).toBe('2019-03');
    expect(referenceMonth({ paymentDate: null, exDate: '2026-10-01', received: false })).toBeNull();
  });

  it('desloca meses atravessando o ano', () => {
    expect(shiftMonthKey('2026-10', -11)).toBe('2025-11');
    expect(shiftMonthKey('2026-01', -1)).toBe('2025-12');
    expect(shiftMonthKey('2025-12', 1)).toBe('2026-01');
    expect(shiftMonthKey('2026-10', -23)).toBe('2024-11');
  });
});

describe('buildDividendPeriods', () => {
  it('C4: meses contínuos do primeiro ao último, com zero nos vazios', () => {
    const p = buildDividendPeriods([
      div({ paymentDate: '2025-11-10' }),
      div({ paymentDate: '2026-02-10' }),
    ], TODAY);
    expect(p.months.map(m => m.key)).toEqual(['2025-11', '2025-12', '2026-01', '2026-02']);
    expect(p.months[1].totalCents).toBe(0);
    expect(p.months[2].totalCents).toBe(0);
  });

  it('separa recebido por tipo de ativo e o a receber num bloco só', () => {
    const p = buildDividendPeriods([
      div({ paymentDate: '2026-10-15', ...FII, netAmount: 100 }),
      div({ paymentDate: '2026-10-20', ...ACAO, netAmount: 50.5 }),
      div({ paymentDate: '2026-10-30', ...ACAO, netAmount: 20, received: false }),
      div({ paymentDate: '2026-10-31', ...FII, netAmount: 9.99, received: false }),
    ], TODAY);
    const oct = p.months.find(m => m.key === '2026-10')!;
    expect(oct.received).toEqual({ '2': 10000, '1': 5050 });
    expect(oct.receivedCents).toBe(15050);
    expect(oct.toReceiveCents).toBe(2999);
    expect(oct.totalCents).toBe(18049);
    expect(oct.receivedCount).toBe(2);
    expect(oct.toReceiveCount).toBe(2);
    expect(oct.current).toBe(true);
  });

  it('D5: FIIs na base mesmo recebendo menos; os demais do maior para o menor', () => {
    const p = buildDividendPeriods([
      div({ ...ACAO, netAmount: 500 }),
      div({ ...ETF, netAmount: 700 }),
      div({ ...FII, netAmount: 1 }),
    ], TODAY);
    expect(p.types.map(t => t.label)).toEqual(['FIIs', 'ETFs', 'Ações']);
  });

  it('tipo que só tem a receber não vira série', () => {
    const p = buildDividendPeriods([
      div({ ...FII }),
      div({ ...ACAO, paymentDate: '2026-12-01', received: false }),
    ], TODAY);
    expect(p.types.map(t => t.label)).toEqual(['FIIs']);
  });

  it('a receber sem data de pagamento fica fora dos meses', () => {
    const p = buildDividendPeriods([
      div({ paymentDate: '2026-09-10' }),
      div({ paymentDate: null, received: false, netAmount: 12.34 }),
    ], TODAY);
    expect(p.undated).toEqual({ count: 1, cents: 1234 });
    expect(p.months.reduce((s, m) => s + m.totalCents, 0)).toBe(1000);
  });

  it('C7: soma em centavos sem ruído de ponto flutuante', () => {
    const p = buildDividendPeriods(
      Array.from({ length: 10 }, () => div({ paymentDate: '2026-03-10', netAmount: 0.1 })),
      TODAY,
    );
    expect(p.months[0].totalCents).toBe(100);
  });

  it('anos: total, a receber, meses no intervalo, média mensal e parcial', () => {
    const p = buildDividendPeriods([
      div({ paymentDate: '2024-12-10', netAmount: 13.07 }),
      div({ paymentDate: '2025-06-10', netAmount: 120 }),
      div({ paymentDate: '2026-05-10', netAmount: 240 }),
      div({ paymentDate: '2026-12-10', netAmount: 60, received: false }),
      div({ paymentDate: '2027-03-10', netAmount: 30, received: false }),
    ], TODAY);
    const [y24, y25, y26, y27] = p.years;
    expect(y24).toMatchObject({ year: 2024, months: 1, totalCents: 1307, monthlyAverageCents: 1307, partial: false });
    expect(y25).toMatchObject({ months: 12, totalCents: 12000, monthlyAverageCents: 1000, growthPct: null });
    expect(y26).toMatchObject({ months: 12, receivedCents: 24000, toReceiveCents: 6000, totalCents: 30000, partial: true });
    expect(y27).toMatchObject({ months: 3, totalCents: 3000, toReceiveCents: 3000, monthlyAverageCents: 1000 });
  });

  it('crescimento só entre dois anos completos', () => {
    const p = buildDividendPeriods([
      div({ paymentDate: '2023-01-10', netAmount: 100 }),
      div({ paymentDate: '2024-06-10', netAmount: 150 }),
      div({ paymentDate: '2025-12-10', netAmount: 300 }),
      div({ paymentDate: '2026-02-10', netAmount: 10 }),
    ], TODAY);
    const g = Object.fromEntries(p.years.map(y => [y.year, y.growthPct]));
    expect(g[2023]).toBeNull();
    expect(g[2024]).toBeCloseTo(50);
    expect(g[2025]).toBeCloseTo(100);
    expect(g[2026]).toBeNull();
  });

  it('sem proventos', () => {
    const p = buildDividendPeriods([], TODAY);
    expect(p.months).toEqual([]);
    expect(p.years).toEqual([]);
    expect(p.currentMonth).toBe('2026-10');
  });
});

describe('janela do gráfico mensal', () => {
  const p = buildDividendPeriods([
    div({ paymentDate: '2024-01-10', netAmount: 10 }),
    div({ paymentDate: '2025-11-10', netAmount: 30 }),
    div({ paymentDate: '2026-10-10', netAmount: 60 }),
    div({ paymentDate: '2027-01-10', netAmount: 99, received: false }),
    div({ paymentDate: '2027-12-10', netAmount: 33, received: false }),
  ], TODAY);

  it('12M: do mês corrente − 11 até 6 meses à frente (C10)', () => {
    const w = monthsInWindow(p.months, '12M', p.currentMonth);
    expect(w[0].key).toBe('2025-11');
    expect(w[w.length - 1].key).toBe('2027-04');
    expect(w.some(m => m.key === '2027-01' && m.toReceiveCents === 9900)).toBe(true);
  });

  it('ano e MAX', () => {
    expect(monthsInWindow(p.months, 2025, p.currentMonth)).toHaveLength(12);
    expect(monthsInWindow(p.months, 'MAX', p.currentMonth)).toHaveLength(p.months.length);
    const all = monthsInWindow(p.months, 'MAX', p.currentMonth);
    expect(all[all.length - 1].key).toBe('2027-12');
  });

  it('média: meses até o corrente', () => {
    const w = monthsInWindow(p.months, '12M', p.currentMonth);
    // nov/2025 a out/2026 = 12 meses, R$ 90 → R$ 7,50; o a receber de jan/2027 não entra
    expect(windowAverageCents(w, p.currentMonth)).toBe(750);
    expect(windowAverageCents(monthsInWindow(p.months, 2027, p.currentMonth), p.currentMonth)).toBeNull();
  });
});

describe('detalhe do mês', () => {
  it('agrupa por ativo com bruto, IR (inclusive do exterior) e líquido', () => {
    const rows = monthDetail([
      div({ ticker: 'BBAS3', type: DividendType.JCP, totalAmount: 10, withholdingTax: 1.75, netAmount: 8.25, paymentDate: '2026-06-10' }),
      div({ ticker: 'BBAS3', type: DividendType.DIVIDENDO, totalAmount: 5, withholdingTax: 0, netAmount: 5, paymentDate: '2026-06-20' }),
      div({ ticker: 'O', totalAmount: 20, withholdingTax: null, netAmount: 13.6, paymentDate: '2026-06-15',
        foreign: { currencyCode: 'USD', symbol: 'US$', fxRate: 5, originalAmountPerShare: 1, originalTotalAmount: 4,
          withholdingRate: 0.3, withholdingTax: 6, withholdingLegalBasis: null, incomeTax: 0.4 } }),
      div({ ticker: 'XPML11', paymentDate: '2026-07-10' }),
    ], '2026-06');
    expect(rows.map(r => r.ticker)).toEqual(['O', 'BBAS3']);
    expect(rows[0]).toMatchObject({ grossCents: 2000, taxCents: 640, netCents: 1360 });
    expect(rows[1]).toMatchObject({ count: 2, grossCents: 1500, taxCents: 175, netCents: 1325 });
    expect(rows[1].types).toEqual([DividendType.JCP, DividendType.DIVIDENDO]);
  });
});

describe('copiar tabela', () => {
  it('um ano por linha, do mais recente, números com vírgula e a linha Total', () => {
    const p = buildDividendPeriods([
      div({ paymentDate: '2025-12-10', netAmount: 1234.5 }),
      div({ paymentDate: '2026-01-10', netAmount: 10 }),
    ], TODAY);
    const lines = periodsToTsv(p).split('\n');
    expect(lines[0]).toBe('Ano\tJan\tFev\tMar\tAbr\tMai\tJun\tJul\tAgo\tSet\tOut\tNov\tDez\tTotal\tMédia mensal');
    expect(lines[1]).toBe(['2026', '10,00', ...Array(11).fill(''), '10,00', '10,00'].join('\t'));
    expect(lines[2]).toBe(['2025', ...Array(11).fill(''), '1234,50', '1234,50', '1234,50'].join('\t'));
    expect(lines[3]).toBe(['Total', '10,00', ...Array(10).fill('0,00'), '1234,50', '1244,50', ''].join('\t'));
  });
});
