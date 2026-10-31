import { describe, expect, it } from 'vitest';
import { formatProposalMoney, providerName, sourceOf } from './integrations';
import { IntegrationSource } from '@/types';

const SOURCES: IntegrationSource[] = [
  { provider: 'BINANCE', name: 'Binance', category: 'Corretora', description: 'Cripto e ações dos EUA',
    credential: 'API_KEY', logo: '/images/BINANCE.png', fiatDeposits: true, dateOnly: false, oncePerDay: false,
    available: true },
  { provider: 'B3', name: 'B3', category: 'Bolsa brasileira', description: 'Ações, FIIs',
    credential: 'DOCUMENT', logo: '/images/B3.png', fiatDeposits: false, dateOnly: true, oncePerDay: false,
    available: false },
];

describe('integrações — catálogo de fontes (PLANO_INTEGRACOES, 10.1)', () => {
  it('nome de exibição vem do catálogo; sem catálogo, o código da fonte', () => {
    expect(providerName('BINANCE', SOURCES)).toBe('Binance');
    expect(providerName('BINANCE')).toBe('BINANCE');
    expect(providerName(null, SOURCES)).toBe('');
  });

  it('o descritor traz credencial, capacidades e se está ligada', () => {
    expect(sourceOf('B3', SOURCES)?.available).toBe(false);
    expect(sourceOf('B3', SOURCES)?.credential).toBe('DOCUMENT');
    expect(sourceOf('BINANCE', SOURCES)?.fiatDeposits).toBe(true);
    expect(sourceOf('XPTO', SOURCES)).toBeUndefined();
  });
});

describe('integrações — valor da proposta na moeda dela (D10)', () => {
  it('em reais continua em R$', () => {
    expect(formatProposalMoney(800.1, 'BRL')).toContain('R$');
    expect(formatProposalMoney(800.1, undefined)).toContain('800,10');
  });

  it('ativo do exterior em US$, com até 8 casas no preço', () => {
    expect(formatProposalMoney(122.03, 'USD')).toBe('US$ 122,03');
    expect(formatProposalMoney(78.26799991, 'USD', 8)).toBe('US$ 78,26799991');
  });
});
