import { describe, expect, it } from 'vitest';
import { IndicatorHistory } from '@/types';
import {
  averageOf, centsDifference, formatCnpj, formatCompactCurrency, formatCompactNumber, formatFull, formatIndicator,
  isFractional, pointsInWindow,
} from './indicators';

const yearly = (years: number[]): IndicatorHistory => ({
  ticker: 'PETR4', indicator: 'ROE', unit: 'PERCENT', period: 'YEAR',
  current: { label: 'Atual', periodEnd: null, value: 10, note: null },
  companyAverage: 9,
  averages: [{ window: '3A', fromYear: 2023, value: 11 }, { window: '5A', fromYear: 2021, value: 10 },
    { window: 'MAX', fromYear: 2015, value: 9 }],
  points: years.map(y => ({ label: String(y), periodEnd: `${y}-12-31`, value: y - 2000, note: null })),
});

describe('janelas dos indicadores', () => {
  it('anual: os últimos 3 ou 5 exercícios; MAX = todos', () => {
    const h = yearly([2019, 2020, 2021, 2022, 2023, 2024, 2025]);
    expect(pointsInWindow(h, '3A').map(p => p.label)).toEqual(['2023', '2024', '2025']);
    expect(pointsInWindow(h, '5A')).toHaveLength(5);
    expect(pointsInWindow(h, 'MAX')).toHaveLength(7);
    expect(pointsInWindow(yearly([2024, 2025]), '5A')).toHaveLength(2);
  });

  it('trimestral: do primeiro ano da janela em diante', () => {
    const h: IndicatorHistory = { ...yearly([]), period: 'QUARTER', points: [
      { label: '4T22', periodEnd: '2022-12-31', value: 1, note: null },
      { label: '1T23', periodEnd: '2023-03-31', value: 2, note: null },
      { label: '2T26', periodEnd: '2026-06-30', value: 3, note: null },
    ] };
    expect(pointsInWindow(h, '3A').map(p => p.label)).toEqual(['1T23', '2T26']);
  });

  it('média da janela; sem as médias (resposta antiga), a de MAX', () => {
    expect(averageOf(yearly([2025]), '3A')).toEqual({ value: 11, fromYear: 2023 });
    expect(averageOf({ ...yearly([2018, 2025]), averages: [] }, '5A')).toEqual({ value: 9, fromYear: 2018 });
  });
});

describe('formatos', () => {
  it('pela unidade; nulo = "—"', () => {
    expect(formatIndicator(12.345, 'PERCENT')).toBe('12,35%');
    expect(formatIndicator(0.8, 'RATIO')).toBe('0,80');
    expect(formatIndicator(3.14159, 'BRL')).toBe('R$ 3,14');
    expect(formatIndicator(3.14159, 'BRL', true)).toBe('R$ 3,1416');
    expect(formatIndicator(12888, 'COUNT')).toBe('12.888');
    expect(formatIndicator(null, 'PERCENT')).toBe('—');
  });

  it('abreviados: bi com 2 casas, mi e mil com 1', () => {
    expect(formatCompactNumber(548_490_000_000)).toBe('548,49 bi');
    expect(formatCompactNumber(1_234_567)).toBe('1,2 mi');
    expect(formatCompactNumber(845_200)).toBe('845,2 mil');
    expect(formatCompactNumber(-17_040_000_000)).toBe('-17,04 bi');
    expect(formatCompactCurrency(110_130_000_000)).toBe('R$ 110,13 bi');
    expect(formatCompactCurrency(-845_200_000)).toBe('-R$ 845,2 mi');
    expect(formatCompactCurrency(null)).toBe('—');
  });

  it('valor detalhado', () => {
    expect(formatFull(548_490_000_000, 'money')).toBe('R$ 548.490.000.000,00');
    expect(formatFull(-1234.5, 'money')).toBe('-R$ 1.234,50');
    expect(formatFull(12_888_732_761, 'count')).toBe('12.888.732.761');
  });

  it('CNPJ e fracionário', () => {
    expect(formatCnpj('33000167000101')).toBe('33.000.167/0001-01');
    expect(formatCnpj('191')).toBe('00.000.000/0001-91');
    expect(isFractional('PETR4F')).toBe(true);
    expect(isFractional('PETR4')).toBe(false);
  });
});

describe('Demonstrativos: não controladores = consolidado − controladora', () => {
  it('em centavos, sem ruído de ponto flutuante', () => {
    expect(centsDifference(0.3, 0.1)).toBe(0.2);
    expect(centsDifference(36_606_000_000.1, 36_550_000_000.05)).toBe(56_000_000.05);
    expect(centsDifference(-1_000_000, 2_000_000)).toBe(-3_000_000);
  });

  it('faltando um lado, nulo', () => {
    expect(centsDifference(null, 10)).toBeNull();
    expect(centsDifference(10, undefined)).toBeNull();
  });
});
