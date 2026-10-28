import { describe, expect, it } from 'vitest';
import {
  formatCurrency, formatCurrencyPrecise, formatDate, formatFxRate, formatMoney, formatPercentage, formatQuantity,
  formatShares, formatUnitPrice, getReturnColor, toLocalISODate,
} from './format';

/** O Intl separa o símbolo do número com espaço sem quebra (U+00A0). */
const nb = (s: string) => s.replace(/ /g, ' ');

describe('moeda', () => {
  it('reais em pt-BR, 2 casas, negativo com sinal', () => {
    expect(formatCurrency(1234.5)).toBe(nb('R$ 1.234,50'));
    expect(formatCurrency(-0.4)).toBe(nb('-R$ 0,40'));
    expect(formatCurrency(1234.565)).toBe(nb('R$ 1.234,57'));
  });

  it('valor por cota com 8 casas', () => {
    expect(formatCurrencyPrecise(0.47160378)).toBe(nb('R$ 0,47160378'));
    expect(formatCurrencyPrecise(1.5, 4)).toBe(nb('R$ 1,5000'));
  });

  it('outra moeda com o símbolo do cadastro', () => {
    expect(formatMoney(1234.56, 'US$')).toBe(nb('US$ 1.234,56'));
    expect(formatMoney(-2.5, '€')).toBe(nb('-€ 2,50'));
    expect(formatMoney(0.12345678, 'US$', 2, 8)).toBe(nb('US$ 0,12345678'));
  });

  it('câmbio com 4 a 8 casas', () => {
    expect(formatFxRate(5.4321)).toBe('5,4321');
    expect(formatFxRate(5.123456789)).toBe('5,12345679');
  });
});

describe('percentual e cor do retorno', () => {
  it('pontos percentuais com 2 casas', () => {
    expect(formatPercentage(12.345)).toBe('12,35%');
    expect(formatPercentage(-0.5)).toBe('-0,50%');
  });

  it('verde, vermelho, cinza', () => {
    expect(getReturnColor(0.01)).toBe('text-success');
    expect(getReturnColor(-0.01)).toBe('text-danger');
    expect(getReturnColor(0)).toBe('text-gray-400');
  });
});

describe('quantidade e preço', () => {
  it('cotas até 4 casas; cripto até 8, sem zeros sobrando', () => {
    expect(formatShares(1234.56789)).toBe('1.234,5679');
    expect(formatShares(100)).toBe('100');
    expect(formatQuantity(0.00197802, true)).toBe('0,00197802');
    expect(formatQuantity(0.00197802, false)).toBe('0,002');
  });

  it('preço: cripto de 2 a 8 casas, os demais 2', () => {
    expect(formatUnitPrice(503233, true)).toBe(nb('R$ 503.233,00'));
    expect(formatUnitPrice(0.00007123, true)).toBe(nb('R$ 0,00007123'));
    expect(formatUnitPrice(32.456, false)).toBe(nb('R$ 32,46'));
  });
});

describe('datas', () => {
  it('"YYYY-MM-DD" da API sem voltar um dia pelo fuso', () => {
    expect(formatDate('2026-01-01')).toBe('01/01/2026');
    expect(formatDate('2026-10-27')).toBe('27/10/2026');
    expect(formatDate('')).toBe('');
  });

  it('hoje no fuso local, sem converter para UTC', () => {
    expect(toLocalISODate(new Date(2026, 9, 27, 23, 59))).toBe('2026-10-27');
    expect(toLocalISODate(new Date(2026, 0, 5, 0, 1))).toBe('2026-01-05');
  });
});
