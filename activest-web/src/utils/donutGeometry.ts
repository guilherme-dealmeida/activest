/**
 * Geometria das roscas que não é do Recharts — docs/planos/PLANO_TESTES_FRONT.md, 40.9 (D4).
 * Testado em `donutGeometry.test.ts`.
 *
 * No Recharts o ângulo 0 é 3 h e cresce no sentido anti-horário (`components/charts/donut.ts`).
 */

// ---------------------------------------------------------------- pizza de pizza (Alocação, 12/10/2026)

/** Altura do gráfico, em px. */
export const PIE_OF_PIE_HEIGHT = 440;
/** A pizza de detalhe tem 75% do tamanho da principal, como no Excel (decisão 2). */
export const PIE_OF_PIE_DETAIL_RATIO = 0.75;
export const PIE_OF_PIE_PADDING_ANGLE = 2;
/** Centros: 27% e 77% da largura, o meio das colunas de 54% e 46% dos títulos e das legendas. */
export const PIE_OF_PIE_MAIN_CX = 0.27;
export const PIE_OF_PIE_DETAIL_CX = 0.77;

const toRad = (deg: number) => (deg * Math.PI) / 180;

export interface PieOfPieGeometry<T> {
  cy: number;
  mainCx: number;
  detailCx: number;
  outer: number;
  detailOuter: number;
  /** Fatias da principal com a aberta em primeiro. */
  ordered: T[];
  /** A fatia aberta, centrada em 0° (virada para a direita): começa no meio-ângulo e dá a volta. */
  startAngle: number;
  endAngle: number;
  /** Espaçamento entre as fatias da principal (zero com uma fatia só). */
  padding: number;
  /** Meio-ângulo da fatia aberta: (360 − espaçamento × fatias) × fração ÷ 2 — o Recharts desconta o espaçamento. */
  halfAngle: number;
  /** Linhas de ligação: saem da borda da fatia (ou do topo e da base da rosca quando ela passa de meia volta). */
  lineStartX: number;
  lineDy: number;
  /** A fatia aberta (nula se o nome não está na principal). */
  expanded: T | undefined;
  hasExpanded: boolean;
}

export const pieOfPieGeometry = <T extends { name: string; value: number }>(width: number, mainData: T[],
  expandedName: string): PieOfPieGeometry<T> => {
  const cy = PIE_OF_PIE_HEIGHT / 2;
  const mainCx = width * PIE_OF_PIE_MAIN_CX;
  const detailCx = width * PIE_OF_PIE_DETAIL_CX;
  const outer = Math.max(0, Math.min(PIE_OF_PIE_HEIGHT / 2 - 16, width * 0.2));
  const detailOuter = outer * PIE_OF_PIE_DETAIL_RATIO;

  const expanded = mainData.find(d => d.name === expandedName);
  const ordered = expanded ? [expanded, ...mainData.filter(d => d !== expanded)] : mainData;
  const mainSum = mainData.reduce((acc, d) => acc + d.value, 0);
  const fraction = expanded && mainSum > 0 ? expanded.value / mainSum : 0;
  const padding = ordered.length > 1 ? PIE_OF_PIE_PADDING_ANGLE : 0;
  const halfAngle = ((360 - padding * ordered.length) * fraction) / 2;

  // Fatia maior que meia pizza teria a borda do lado esquerdo e a linha cruzaria a rosca: aí a linha sai do topo e da
  // base da principal, como no Excel.
  const edge = toRad(Math.min(halfAngle, 90));
  return {
    cy, mainCx, detailCx, outer, detailOuter, ordered,
    startAngle: halfAngle, endAngle: halfAngle - 360, halfAngle, padding,
    lineStartX: mainCx + outer * Math.cos(edge),
    lineDy: outer * Math.sin(edge),
    expanded,
    hasExpanded: expanded !== undefined,
  };
};

// ---------------------------------------------------------------- legenda no canto (CornerLegendDonut)

/** Folga entre a rosca e a legenda, em px. */
export const CORNER_LEGEND_GAP = 12;

/**
 * Quanto a legenda do canto inferior direito sobe para o lado da rosca (em px), sem encostar nela:
 * - legenda toda à direita do círculo: sobe até a altura do centro;
 * - a borda esquerda dela cai sobre o círculo: fica abaixo da curva naquele x;
 * - passaria por baixo do centro: não sobe (vai inteira para baixo).
 * Nunca sobe mais que a própria altura (a base dela não fica acima da base da área da rosca).
 */
export const cornerLegendPull = (areaWidth: number, height: number, outerRatio: number, legendWidth: number,
  legendHeight: number): number => {
  const radius = (outerRatio * Math.min(areaWidth, height)) / 2;
  const middle = height / 2;
  // distância horizontal da borda esquerda da legenda ao centro da rosca
  const dx = areaWidth - legendWidth - areaWidth / 2;
  let minTop: number;
  if (dx >= radius) minTop = middle;
  else if (dx > 0) minTop = middle + Math.sqrt(radius * radius - dx * dx) + CORNER_LEGEND_GAP;
  else minTop = height + CORNER_LEGEND_GAP;
  return Math.min(height - minTop, legendHeight);
};
