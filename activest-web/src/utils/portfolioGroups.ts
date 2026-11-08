import { PortfolioAssetDetail } from '@/types';
import { classicReturn } from './returns';

/**
 * Agrupamento dos títulos do Tesouro Direto por ativo — docs/planos/PLANO_AGRUPAMENTO_TITULOS.md.
 *
 * No Tesouro cada compra é um título (uma posição), então o mesmo papel aparece em várias linhas. Aqui as posições
 * de um mesmo ativo viram uma linha-mãe com os valores somados (G1) e os títulos embaixo. Ativo com um título só
 * continua em linha simples (G5). Só o Tesouro é agrupado (G8): na Renda Fixa o ativo é genérico ("CDB") e cada
 * título é um produto próprio. Testado em `portfolioGroups.test.ts`.
 */

/** Chave do ativo entre as telas: tipo (descrição, como chega no DTO da carteira e no breakdown) + ticker. */
export const assetKey = (typeDescription: string | null | undefined, ticker: string | null | undefined) =>
  `${typeDescription ?? ''}|${ticker ?? ''}`;

export type PortfolioRow<T> =
  | { kind: 'position'; key: string; position: T }
  | { kind: 'group'; key: string; group: T; children: T[] };

const sumOf = <T,>(items: T[], f: (t: T) => number) => items.reduce((s, t) => s + f(t), 0);

/** O valor quando é o mesmo em todos os títulos; senão null (a tabela mostra "—"). */
const common = <T, V>(items: T[], f: (t: T) => V | null | undefined): V | null => {
  const first = f(items[0]);
  return first != null && items.every(i => f(i) === first) ? first : null;
};

/**
 * Taxa média dos títulos ponderada pelo valor inicial (review de 07/11/2026, R1 — opção a): só os títulos com taxa e
 * valor inicial positivo; null se nenhum.
 */
export const weightedRate = (children: Pick<PortfolioAssetDetail, 'indexerRate' | 'totalInvested'>[]): number | null => {
  const rated = children.filter(c => c.indexerRate != null && c.totalInvested > 0);
  const weight = sumOf(rated, c => c.totalInvested);
  return weight > 0 ? sumOf(rated, c => c.indexerRate! * c.totalInvested) / weight : null;
};

/**
 * A linha-mãe: a primeira posição como base (ticker, nome, tipo, categoria, logo — iguais em todo o grupo) e os
 * valores somados. Distribuidora, aplicação e vencimento aparecem quando são os mesmos em todos os títulos (senão
 * "—"); a taxa é a média ponderada pelo valor inicial (review R1, revisando a G1); lotes ficam só nos títulos.
 * Variação e rentabilidade clássica sobre as somas, como nos Totais.
 */
export const aggregatePositions = <T extends PortfolioAssetDetail>(children: T[]): T => {
  const first = children[0];
  const invested = sumOf(children, c => c.totalInvested);
  const shares = sumOf(children, c => c.totalShares);
  const current = sumOf(children, c => c.currentValue);
  const valorization = sumOf(children, c => c.valorization);
  const dividends = sumOf(children, c => c.totalDividends);
  const hasNet = children.some(c => c.estimatedNet != null);
  return {
    ...first,
    id: `group:${assetKey(first.typeName, first.ticker)}`,
    noteId: null,
    issuer: common(children, c => c.issuer),
    purchaseDate: common(children, c => c.purchaseDate),
    maturityDate: common(children, c => c.maturityDate),
    rateType: common(children, c => c.rateType),
    indexer: common(children, c => c.indexer),
    indexerRate: common(children, c => c.rateType) ? weightedRate(children) : null,
    totalShares: shares,
    averagePrice: shares > 0 ? invested / shares : 0,
    totalInvested: invested,
    totalSold: sumOf(children, c => c.totalSold),
    currentValue: current,
    currentPrice: shares > 0 ? current / shares : first.currentPrice,
    totalDividends: dividends,
    valorization,
    returnPercentage: invested > 0 ? (valorization / invested) * 100 : 0,
    // a reserva do Tesouro é do ativo (G7): todos iguais; "todos" protege de um estado antigo misturado
    isReservation: children.every(c => c.isReservation),
    isSettled: children.every(c => c.isSettled),
    valuationBasis: null,
    valuationDate: null,
    valuationNote: hasNet ? 'Soma dos líquidos estimados dos títulos (o detalhe dos aportes fica em cada título).' : null,
    matured: children.every(c => c.matured),
    pendingBonusCosts: [],
    estimatedNet: hasNet ? sumOf(children, c => c.estimatedNet ?? c.currentValue) : null,
    lots: [],
    noteInfo: commonNoteInfo(children),
    foreignValues: null,
    distributorName: commonNoteInfo(children)?.distributorName ?? null,
    classicReturn: classicReturn(valorization, dividends, invested),
    twr: null,
    groupSize: children.length,
  };
};

/**
 * Linhas da tabela de uma aba: no Tesouro, ativo com 2+ títulos vira grupo; o resto (e os outros tipos) fica como
 * posição. A ordem de entrada é mantida (o grupo entra no lugar do seu primeiro título); ordenar é com quem chama.
 */
export const groupTreasuryTitles = <T extends PortfolioAssetDetail>(isTreasury: boolean, positions: T[]): PortfolioRow<T>[] => {
  if (!isTreasury) {
    return positions.map(p => ({ kind: 'position', key: p.id, position: p }));
  }
  const byAsset = new Map<string, T[]>();
  positions.forEach(p => {
    const k = assetKey(p.typeName, p.ticker);
    byAsset.set(k, [...(byAsset.get(k) ?? []), p]);
  });
  const rows: PortfolioRow<T>[] = [];
  const done = new Set<string>();
  positions.forEach(p => {
    const k = assetKey(p.typeName, p.ticker);
    if (done.has(k)) return;
    done.add(k);
    const children = byAsset.get(k)!;
    rows.push(children.length > 1
      ? { kind: 'group', key: `group:${k}`, group: aggregatePositions(children), children }
      : { kind: 'position', key: p.id, position: p });
  });
  return rows;
};

/**
 * Distribuidora comum aos títulos do grupo, para o preset de Comprar/Vender pela linha-mãe (D4): só quando todos têm
 * a mesma; senão null e o usuário escolhe no modal.
 */
export const commonNoteInfo = <T extends PortfolioAssetDetail>(children: T[]) => {
  const first = children[0]?.noteInfo;
  if (!first?.distributorId) return null;
  return children.every(c => c.noteInfo?.distributorId === first.distributorId) ? first : null;
};
