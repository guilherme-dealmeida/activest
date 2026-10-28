import { describe, expect, it } from 'vitest';
import { barWidth, limitBarClass, limitUsage } from './goalProgress';

describe('meta de limite', () => {
  it('uso = indicador ÷ limite, em %', () => {
    expect(limitUsage(800, 1000)).toBe(80);
    expect(limitUsage(1500, 1000)).toBe(150);
    expect(limitUsage(0, 1000)).toBe(0);
  });

  it('indicador negativo conta como zero', () => {
    expect(limitUsage(-50, 1000)).toBe(0);
  });

  it('sem limite positivo ou sem indicador: sem uso (a tela mostra o progresso)', () => {
    expect(limitUsage(10, 0)).toBeNull();
    expect(limitUsage(10, null)).toBeNull();
    expect(limitUsage(null, 1000)).toBeNull();
  });

  it('cor: até 80% verde, até 100% amarelo, acima vermelho', () => {
    expect(limitBarClass(80)).toBe('h-full bg-success');
    expect(limitBarClass(81)).toBe('h-full bg-yellow-500');
    expect(limitBarClass(100)).toBe('h-full bg-yellow-500');
    expect(limitBarClass(100.01)).toBe('h-full bg-danger');
  });
});

describe('largura da barra', () => {
  it('limitada a 100', () => {
    expect(barWidth(42.5)).toBe(42.5);
    expect(barWidth(150)).toBe(100);
    expect(barWidth('87.3')).toBe(87.3);
  });
});
