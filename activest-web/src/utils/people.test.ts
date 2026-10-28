import { describe, expect, it } from 'vitest';
import { formatCpf, formatDateTime, isValidCpf } from './integrations';
import { formatPhone, isPhoneComplete, phoneFromApi } from './phone';

describe('CPF', () => {
  it('máscara enquanto digita', () => {
    expect(formatCpf('529')).toBe('529');
    expect(formatCpf('5299822')).toBe('529.982.2');
    expect(formatCpf('52998224725')).toBe('529.982.247-25');
    expect(formatCpf('529.982.247-2599')).toBe('529.982.247-25');
  });

  it('dígitos verificadores', () => {
    expect(isValidCpf('529.982.247-25')).toBe(true);
    expect(isValidCpf('52998224724')).toBe(false);
    expect(isValidCpf('111.111.111-11')).toBe(false);
    expect(isValidCpf('5299822472')).toBe(false);
  });
});

describe('data e hora das execuções', () => {
  it('no fuso de Brasília; sem "Z" é UTC (LocalDateTime do servidor)', () => {
    expect(formatDateTime('2026-10-27T15:30:00')).toBe(formatDateTime('2026-10-27T12:30:00-03:00'));
    expect(formatDateTime('2026-10-27T15:30:00Z')).toContain('12:30');
    expect(formatDateTime(null)).toBe('—');
  });
});

describe('celular', () => {
  it('máscara enquanto digita', () => {
    expect(formatPhone('')).toBe('');
    expect(formatPhone('41')).toBe('(41');
    expect(formatPhone('419999')).toBe('(41) 9999');
    expect(formatPhone('4133334444')).toBe('(41) 3333-4444');
    expect(formatPhone('41999998888')).toBe('(41) 99999-8888');
  });

  it('da API (E.164): Brasil mascarado, exterior como veio', () => {
    expect(phoneFromApi('+5541999998888')).toBe('(41) 99999-8888');
    expect(phoneFromApi('+14155550123')).toBe('+14155550123');
    expect(phoneFromApi(null)).toBe('');
  });

  it('completo: vazio ou 10/11 dígitos', () => {
    expect(isPhoneComplete('')).toBe(true);
    expect(isPhoneComplete('(41) 3333-4444')).toBe(true);
    expect(isPhoneComplete('(41) 99999-8888')).toBe(true);
    expect(isPhoneComplete('(41) 9999')).toBe(false);
  });
});
