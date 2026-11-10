import { describe, expect, it } from 'vitest';
import { FONT_SCALE, fontSizeFromUser, fontSizeToUser, rem } from './fontScale';

describe('fontScale', () => {
  it('converte px de desenho para rem (base 16)', () => {
    expect(rem(16)).toBe('1rem');
    expect(rem(11)).toBe('0.6875rem');
    expect(rem(10)).toBe('0.625rem');
    expect(rem(13)).toBe('0.8125rem');
  });

  it('Pequena reduz 16 px para 14 px (D1)', () => {
    expect(16 * FONT_SCALE.small).toBe(14);
    expect(16 * FONT_SCALE.large).toBe(16);
  });

  it('lê e grava o valor do servidor', () => {
    expect(fontSizeFromUser('SMALL')).toBe('small');
    expect(fontSizeFromUser('LARGE')).toBe('large');
    expect(fontSizeFromUser(undefined)).toBe('large');
    expect(fontSizeFromUser('qualquer')).toBe('large');
    expect(fontSizeToUser('small')).toBe('SMALL');
    expect(fontSizeToUser('large')).toBe('LARGE');
  });
});
