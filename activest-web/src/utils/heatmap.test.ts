import { describe, expect, it } from 'vitest';
import { compoundReturns, heatmapYears } from './heatmap';

describe('compoundReturns (coluna "Ano" do mapa de calor)', () => {
  it('compõe em vez de somar', () => {
    // 1,1 × 1,1 − 1 = 21%, e não 20%
    expect(compoundReturns([10, 10])).toBeCloseTo(21, 10);
  });

  it('ganho e perda iguais não se anulam', () => {
    expect(compoundReturns([10, -10])).toBeCloseTo(-1, 10);
  });

  it('doze meses de 1% dão 12,68%', () => {
    expect(compoundReturns(Array(12).fill(1))).toBeCloseTo(12.6825030132, 9);
  });

  it('bate com o ano que o motor do backend calcula (carteira real, 2024, líquido)', () => {
    // GET /performance/periodic?granularity=MONTH e ?granularity=YEAR, range=MAX, conferido em 27/10/2026:
    // os 12 meses de 2024 compostos dão o ano que a API devolve (13,2942%, 4 casas).
    const months2024 = [0.6038, 0.5251, 0.5224, 0.2227, 0.5556, 0.3453, 0.7615, 0.7695, 1.5584, 2.7469, 1.5412, 2.432];
    expect(compoundReturns(months2024)).toBeCloseTo(13.2942, 3);
  });

  it('um mês sem dado deixa o ano sem valor', () => {
    expect(compoundReturns([1.2, null, 0.8])).toBeNull();
  });

  it('nenhum mês = sem variação', () => {
    expect(compoundReturns([])).toBe(0);
  });
});

describe('heatmapYears', () => {
  const rows = [
    { key: '2025-11', portfolio: 2, partial: true },
    { key: '2025-12', portfolio: -1, partial: false },
    { key: '2026-01', portfolio: 1.5, partial: false },
    { key: '2026-02', portfolio: null, partial: false },
    { key: '2026-03', portfolio: 0.5, partial: true },
  ];

  it('anos do mais recente ao mais antigo, com os meses de cada um', () => {
    const years = heatmapYears(rows);
    expect(years.map(y => y.year)).toEqual([2026, 2025]);
    expect([...years[1].months.keys()]).toEqual([11, 12]);
    expect(years[1].months.get(11)).toEqual({ v: 2, partial: true });
  });

  it('ano que começa no meio compõe só os meses que tem; ano com mês nulo fica sem valor', () => {
    const [y2026, y2025] = heatmapYears(rows);
    expect(y2025.yearReturn).toBeCloseTo((1.02 * 0.99 - 1) * 100, 10);
    expect(y2026.yearReturn).toBeNull();
  });
});
