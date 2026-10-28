import { describe, expect, it } from 'vitest';
import {
  incomeShare, isStrategyTotalValid, rebalanceTotals, reservationDiff, reservationIdealPercentage, strategyTotal,
  strategyTotalLabel,
} from './strategyRules';

describe('soma dos ideais da Estratégia (regra do backend: soma arredondada a uma casa = 100)', () => {
  it('100 exato passa; valores chegam como texto do formulário', () => {
    expect(isStrategyTotalValid(['40', '35.5', '24.5'])).toBe(true);
  });

  it('divisões que não fecham exato passam (33,33 × 3 = 99,99; 8,33 × 12 = 99,96)', () => {
    expect(isStrategyTotalValid([33.33, 33.33, 33.33])).toBe(true);
    expect(isStrategyTotalValid(Array(12).fill(8.33))).toBe(true);
  });

  it('A1: 0,10 + 99,85 = 99,95 passa (a soma em ponto flutuante dava 99,9499… e a tela recusava)', () => {
    expect(isStrategyTotalValid([0.1, 99.85])).toBe(true);
  });

  it('A1: 32,23 + 67,82 = 100,05 não passa (a tela aceitava e o backend recusava)', () => {
    expect(isStrategyTotalValid([32.23, 67.82])).toBe(false);
  });

  it('fora da tolerância não passa', () => {
    expect(isStrategyTotalValid([50, 49.94])).toBe(false);
    expect(isStrategyTotalValid([60, 50])).toBe(false);
    expect(isStrategyTotalValid([])).toBe(false);
  });

  it('total exato nos centésimos e o texto da mensagem com uma casa', () => {
    expect(strategyTotal([0.1, 99.85])).toBe(99.95);
    expect(strategyTotalLabel([0.1, 99.85])).toBe('100,0');
    expect(strategyTotalLabel([50, 49.94])).toBe('99,9');
    expect(strategyTotalLabel([60, 50])).toBe('110,0');
  });
});

describe('reserva de emergência', () => {
  it('Por Porcentagem: o informado', () => {
    expect(reservationIdealPercentage(false, 50000, 15, 200000)).toBe(15);
  });

  it('Por Valor: alvo ÷ carteira, limitado a 100%; carteira zerada = 0', () => {
    expect(reservationIdealPercentage(true, 50000, 0, 200000)).toBe(25);
    expect(reservationIdealPercentage(true, 300000, 0, 200000)).toBe(100);
    expect(reservationIdealPercentage(true, 50000, 0, 0)).toBe(0);
  });

  it('Geração de Renda nunca negativa', () => {
    expect(incomeShare(200000, 50000)).toBe(150000);
    expect(incomeShare(200000, 300000)).toBe(0);
    expect(incomeShare(100, 15)).toBe(85);
  });

  it('% Diff: tamanho e lado', () => {
    expect(reservationDiff(20, 15)).toEqual({ diff: 5, abs: 5, side: 'acima' });
    expect(reservationDiff(10, 15)).toEqual({ diff: -5, abs: 5, side: 'abaixo' });
    expect(reservationDiff(15, 15)).toEqual({ diff: 0, abs: 0, side: '' });
  });
});

describe('Rebalanceamento', () => {
  it('total a aportar e total estratégico', () => {
    expect(rebalanceTotals([
      { amountToRebalance: 1200, idealValue: 10000 },
      { amountToRebalance: 0, idealValue: 5000 },
      { amountToRebalance: 300.5, idealValue: 2500.5 },
    ])).toEqual({ toInvest: 1500.5, strategic: 17500.5 });
  });

  it('sem itens', () => {
    expect(rebalanceTotals([])).toEqual({ toInvest: 0, strategic: 0 });
  });
});
