import { describe, expect, it } from 'vitest';
import { DONUT_END_ANGLE, DONUT_START_ANGLE, byValueDesc } from '@/components/charts/donut';
import { CORNER_LEGEND_GAP, cornerLegendPull, pieOfPieGeometry } from './donutGeometry';

describe('ordem das roscas (donut.ts)', () => {
  it('do maior para o menor, empate na ordem recebida', () => {
    const d = byValueDesc([
      { name: 'a', value: 10 }, { name: 'b', value: 30 }, { name: 'c', value: 10 }, { name: 'd', value: 20 },
    ]);
    expect(d.map(x => x.name)).toEqual(['b', 'd', 'a', 'c']);
  });

  it('não altera a lista original', () => {
    const original = [{ name: 'a', value: 1 }, { name: 'b', value: 2 }];
    byValueDesc(original);
    expect(original.map(x => x.name)).toEqual(['a', 'b']);
  });

  it('começa no topo (12 h) e dá a volta inteira no sentido horário', () => {
    expect(DONUT_START_ANGLE).toBe(90);
    expect(DONUT_START_ANGLE - DONUT_END_ANGLE).toBe(360);
  });
});

describe('pizza de pizza', () => {
  const main = [
    { name: 'Reserva de Emergência', value: 25 },
    { name: 'Geração de Renda', value: 75 },
  ];

  it('medidas pela largura: centros a 27% e 77%, raio limitado pela altura, detalhe com 75%', () => {
    const g = pieOfPieGeometry(1000, main, 'Geração de Renda');
    expect(g).toMatchObject({ cy: 220, mainCx: 270, detailCx: 770, outer: 200, detailOuter: 150 });
    // tela estreita: o raio acompanha a largura (20%)
    expect(pieOfPieGeometry(800, main, 'Geração de Renda').outer).toBe(160);
  });

  it('a fatia aberta vai primeiro e fica centrada em 0°, descontando o espaçamento das fatias', () => {
    const g = pieOfPieGeometry(1000, main, 'Reserva de Emergência');
    expect(g.ordered.map(d => d.name)).toEqual(['Reserva de Emergência', 'Geração de Renda']);
    // (360 − 2 × 2) × 25% ÷ 2 = 44,5°
    expect(g.halfAngle).toBeCloseTo(44.5, 10);
    expect(g.startAngle).toBeCloseTo(44.5, 10);
    expect(g.endAngle).toBeCloseTo(44.5 - 360, 10);
  });

  it('linhas saem das bordas da fatia', () => {
    const g = pieOfPieGeometry(1000, main, 'Reserva de Emergência');
    const rad = (44.5 * Math.PI) / 180;
    expect(g.lineStartX).toBeCloseTo(270 + 200 * Math.cos(rad), 10);
    expect(g.lineDy).toBeCloseTo(200 * Math.sin(rad), 10);
  });

  it('fatia de mais de meia volta: as linhas saem do topo e da base da rosca', () => {
    const g = pieOfPieGeometry(1000, main, 'Geração de Renda');
    expect(g.halfAngle).toBeGreaterThan(90);
    expect(g.lineStartX).toBeCloseTo(270, 10);
    expect(g.lineDy).toBeCloseTo(200, 10);
  });

  it('fatia única: sem espaçamento, meia volta de cada lado', () => {
    const g = pieOfPieGeometry(1000, [{ name: 'Geração de Renda', value: 10 }], 'Geração de Renda');
    expect(g.halfAngle).toBe(180);
  });

  it('nome que não existe ou carteira zerada: sem fatia aberta', () => {
    expect(pieOfPieGeometry(1000, main, 'Outro')).toMatchObject({ hasExpanded: false, halfAngle: 0 });
    const zero = pieOfPieGeometry(1000, [{ name: 'A', value: 0 }, { name: 'B', value: 0 }], 'A');
    expect(zero.halfAngle).toBe(0);
  });
});

describe('legenda no canto da rosca', () => {
  // área 600 × 320, rosca com 85% da menor medida: raio 136, centro em x = 300, y = 160
  it('legenda estreita, toda à direita do círculo: sobe até a altura do centro (no máximo a própria altura)', () => {
    expect(cornerLegendPull(600, 320, 0.85, 100, 120)).toBe(120);
    expect(cornerLegendPull(600, 320, 0.85, 100, 200)).toBe(160);
  });

  it('borda esquerda sobre o círculo: fica abaixo da curva naquele x', () => {
    // dx = 600 − 200 − 300 = 100 → abaixo da curva: 160 + √(136² − 100²) + 12
    const minTop = 160 + Math.sqrt(136 * 136 - 100 * 100) + CORNER_LEGEND_GAP;
    expect(cornerLegendPull(600, 320, 0.85, 200, 200)).toBeCloseTo(320 - minTop, 10);
  });

  it('legenda larga, passaria por baixo do centro: não sobe', () => {
    expect(cornerLegendPull(600, 320, 0.85, 320, 100)).toBe(-CORNER_LEGEND_GAP);
  });
});
