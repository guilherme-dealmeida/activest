import { describe, expect, it } from 'vitest';
import { classicReturn } from './returns';
import { lotTotals, portfolioTotals } from './portfolioTotals';

const pos = (over: Partial<Parameters<typeof portfolioTotals>[0][number]>) => ({
  totalInvested: 0, totalSold: 0, currentValue: 0, totalDividends: 0, valorization: 0, estimatedNet: null, ...over,
});

describe('classicReturn (a "Rentabilidade" do Investidor10)', () => {
  it('(valorização + proventos) ÷ aplicado', () => {
    expect(classicReturn(150, 50, 1000)).toBeCloseTo(20, 10);
    expect(classicReturn(-300, 50, 1000)).toBeCloseTo(-25, 10);
  });

  it('sem valor aplicado não há rentabilidade', () => {
    expect(classicReturn(10, 5, 0)).toBeNull();
  });
});

describe('rodapé da Carteira', () => {
  it('somas e percentuais sobre as somas, não a média das linhas', () => {
    const t = portfolioTotals([
      pos({ totalInvested: 1000, currentValue: 1200, valorization: 200, totalDividends: 30, totalSold: 100 }),
      pos({ totalInvested: 3000, currentValue: 2700, valorization: -300, totalDividends: 90 }),
    ]);
    expect(t).toMatchObject({ invested: 4000, sold: 100, current: 3900, dividends: 120, valorization: -100 });
    // −100 ÷ 4000 = −2,5% (a média das linhas daria (20% − 10%) ÷ 2 = +5%)
    expect(t.valorizationPct).toBeCloseTo(-2.5, 10);
    // (−100 + 120) ÷ 4000 = 0,5%
    expect(t.classic).toBeCloseTo(0.5, 10);
  });

  it('sem investido: percentuais zerados, sem dividir por zero', () => {
    const t = portfolioTotals([pos({ currentValue: 10 })]);
    expect(t.valorizationPct).toBe(0);
    expect(t.classic).toBe(0);
  });

  it('líquido estimado: o da posição quando há, senão o valor atual', () => {
    const t = portfolioTotals([
      pos({ currentValue: 1000, estimatedNet: 950 }),
      pos({ currentValue: 500, estimatedNet: null }),
    ]);
    expect(t.hasNet).toBe(true);
    expect(t.net).toBe(1450);
    expect(portfolioTotals([pos({ currentValue: 500 })]).hasNet).toBe(false);
  });

  it('aba vazia', () => {
    expect(portfolioTotals([])).toMatchObject({ invested: 0, current: 0, net: 0, hasNet: false, classic: 0 });
  });
});

describe('aportes da renda fixa', () => {
  it('rendimento somado e IOF + IR somados (a tela mostra em centavos)', () => {
    const t = lotTotals([
      { income: 120.5, iof: 0, ir: 21.08 },
      { income: 10, iof: 3.2, ir: 2.25 },
    ]);
    expect(t.income).toBe(130.5);
    expect(t.taxes).toBeCloseTo(26.53, 10);
  });
});
