import { describe, expect, it } from 'vitest';
import { fullRedemptionAmount, fundShares, transactionTotal } from './transactionAmounts';

describe('total do formulário', () => {
  it('ação, FII, ETF, cripto: cotas × preço', () => {
    expect(transactionTotal(false, 100, 32.45, null)).toBeCloseTo(3245, 10);
    expect(transactionTotal(false, 0.00197802, 503233, null)).toBeCloseTo(995.40, 2);
  });

  it('renda fixa e fundo: o valor digitado', () => {
    expect(transactionTotal(true, 7, 10, 1500)).toBe(1500);
  });

  it('campos vazios = zero', () => {
    expect(transactionTotal(false, null, 10, null)).toBe(0);
    expect(transactionTotal(true, null, null, undefined)).toBe(0);
  });
});

// Esperados calculados com Decimal HALF_EVEN (Python), a mesma conta do BigDecimal da API (`TransactionService`).
describe('cotas do fundo (= o que a API grava)', () => {
  it('valor ÷ cota com 8 casas', () => {
    expect(fundShares(1000, 3.1234567)).toBe(320.158112);
    expect(fundShares(1000, 2.56)).toBe(390.625);
    expect(fundShares(12345.67, 1.00000003)).toBe(12345.66962963);
  });

  it('D8: empate na 9ª casa vai para o par, como o HALF_EVEN da API', () => {
    // 1 ÷ 512 = 0,001953125 → 0,00195312 (o 2 já é par; antes a tela mostrava 0,00195313)
    expect(fundShares(1, 512)).toBe(0.00195312);
    // 3 ÷ 512 = 0,005859375 → 0,00585938 (o 7 é ímpar: sobe)
    expect(fundShares(3, 512)).toBe(0.00585938);
  });

  it('extremos da precisão: cota com 8 casas e muitas cotas', () => {
    expect(fundShares(0.01, 99999.99999999)).toBe(0.0000001);
    expect(fundShares(250000, 0.00000001)).toBe(25000000000000);
  });

  it('sem cota ou sem valor, zero', () => {
    expect(fundShares(1000, 0)).toBe(0);
    expect(fundShares(1000, null)).toBe(0);
    expect(fundShares(null, 3.5)).toBe(0);
  });
});

describe('resgatar tudo', () => {
  it('o líquido estimado em centavos', () => {
    expect(fullRedemptionAmount(10234.567)).toBe(10234.57);
    expect(fullRedemptionAmount(950)).toBe(950);
  });
});
