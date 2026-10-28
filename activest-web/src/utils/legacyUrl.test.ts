import { describe, expect, it } from 'vitest';
import { isLegacyUrl, translateLegacyUrl } from './legacyUrl';

describe('rotas antigas em português', () => {
  it('redirecionam para as novas, com o resto do caminho', () => {
    expect(translateLegacyUrl('/rentabilidade', '')).toBe('/performance');
    expect(translateLegacyUrl('/ativos/PETR4', '')).toBe('/assets/PETR4');
    expect(translateLegacyUrl('/emissoras/VALE3', '')).toBe('/assets/VALE3');
    expect(translateLegacyUrl('/esqueci-senha', '')).toBe('/forgot-password');
  });

  it('não confundem prefixo de outra rota', () => {
    expect(translateLegacyUrl('/ativosx', '')).toBe('/ativosx');
  });
});

describe('parâmetros antigos', () => {
  it('Rentabilidade: nomes e valores fixos', () => {
    expect(translateLegacyUrl('/rentabilidade', '?periodo=MAX&reserva=sem&base=liquido&indices=CDI'))
      .toBe('/performance?range=MAX&reservation=without&basis=net&benchmarks=CDI');
  });

  it('o nome novo, se já veio, vale mais que o antigo', () => {
    expect(translateLegacyUrl('/performance', 'periodo=1A&range=MAX')).toBe('/performance?range=MAX');
  });

  it('Dividendos e Estratégia', () => {
    expect(translateLegacyUrl('/dividends', 'aba=a-receber')).toBe('/dividends?tab=to-receive');
    expect(translateLegacyUrl('/strategy', 'aba=reserva')).toBe('/strategy?tab=reservation');
  });

  it('listagem de ativos: estado por aba, inclusive a coluna da ordenação', () => {
    expect(translateLegacyUrl('/ativos', 'aba=acoes&acoes.ord=patrimonio.desc&acoes.pag=2&fiis.dy=6~12'))
      .toBe('/assets?tab=stocks&stocks.sort=equity.desc&stocks.page=2&fiis.dy=6%7E12');
  });

  it('parâmetro desconhecido fica como está', () => {
    expect(translateLegacyUrl('/performance', 'x=1')).toBe('/performance?x=1');
  });
});

describe('é endereço antigo?', () => {
  it('rota ou parâmetro em português', () => {
    expect(isLegacyUrl('/ativos', '')).toBe(true);
    expect(isLegacyUrl('/dividends', '?aba=a-receber')).toBe(true);
    expect(isLegacyUrl('/dividends', '?tab=to-receive')).toBe(false);
    expect(isLegacyUrl('/assets', '?stocks.dy=6~12')).toBe(false);
  });
});
