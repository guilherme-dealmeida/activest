import { describe, expect, it } from 'vitest';
import { logoUrl } from './logoUrl';

describe('logoUrl', () => {
  it('com o tipo, pede o logo pelo par (tipo, ticker) — META é cripto e ação do exterior (V13)', () => {
    expect(logoUrl('META', 'STOCK')).toBe('/api/v1/assets/STOCK/META/logo');
    expect(logoUrl('META', 'CRIPTO')).toBe('/api/v1/assets/CRIPTO/META/logo');
  });

  it('sem o tipo, a rota pelo ticker; o ticker vai codificado', () => {
    expect(logoUrl('PETR4')).toBe('/api/v1/assets/PETR4/logo');
    expect(logoUrl('BRK/B', null)).toBe('/api/v1/assets/BRK%2FB/logo');
  });
});
