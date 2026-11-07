import { describe, expect, it } from 'vitest';
import { PortfolioAssetDetail } from '@/types';
import { aggregatePositions, assetKey, commonNoteInfo, groupTreasuryTitles, weightedRate } from './portfolioGroups';

const TD = 'Tesouro Direto';

const pos = (over: Partial<PortfolioAssetDetail>): PortfolioAssetDetail => ({
  id: Math.random().toString(36).slice(2),
  ticker: 'Tesouro IPCA+ 2040', assetName: 'Tesouro IPCA+ 2040', typeName: TD, categoryName: 'IPCA+',
  noteId: 'n', issuer: 'Tesouro Nacional', purchaseDate: '2026-01-02', maturityDate: '2040-08-15',
  rateType: 'HIBRIDO', indexer: 'IPCA', indexerRate: 7, totalShares: 1, averagePrice: 1000, totalInvested: 1000,
  totalSold: 0, currentValue: 1100, currentPrice: 1100, totalDividends: 0, valorization: 100, returnPercentage: 10,
  isReservation: false, isSettled: false, isCrypto: false, hasLogo: null, issuerId: null, estimatedNet: 1080,
  lots: [], noteInfo: null,
  ...over,
} as PortfolioAssetDetail);

describe('groupTreasuryTitles', () => {
  it('Tesouro: 2+ títulos do mesmo ativo viram grupo; título único fica como posição (G5)', () => {
    const a1 = pos({}); const a2 = pos({}); const b = pos({ ticker: 'Tesouro Selic 2029' });
    const rows = groupTreasuryTitles(true, [a1, b, a2]);
    expect(rows.map(r => r.kind)).toEqual(['group', 'position']);
    const g = rows[0];
    expect(g.kind === 'group' && g.children).toEqual([a1, a2]);
    expect(rows[1].kind === 'position' && rows[1].position).toBe(b);
  });

  it('fora do Tesouro nada é agrupado (G8: CDBs continuam por título)', () => {
    const rows = groupTreasuryTitles(false, [pos({ ticker: 'CDB' }), pos({ ticker: 'CDB' })]);
    expect(rows.every(r => r.kind === 'position')).toBe(true);
    expect(rows).toHaveLength(2);
  });
});

describe('aggregatePositions (linha-mãe)', () => {
  it('soma os valores e calcula as % sobre as somas', () => {
    const g = aggregatePositions([
      pos({ totalShares: 2, totalInvested: 2000, currentValue: 2400, valorization: 400, estimatedNet: 2300 }),
      pos({ totalShares: 1, totalInvested: 1000, currentValue: 900, valorization: -100, estimatedNet: null }),
    ]);
    expect(g).toMatchObject({ totalShares: 3, totalInvested: 3000, currentValue: 3300, valorization: 300 });
    expect(g.returnPercentage).toBeCloseTo(10, 10);
    expect(g.classicReturn).toBeCloseTo(10, 10);
    // líquido: o estimado de quem tem, o valor atual de quem não tem
    expect(g.estimatedNet).toBe(3200);
    expect(g.averagePrice).toBeCloseTo(1000, 10);
  });

  it('distribuidora, aplicação e vencimento: o comum aparece, o diferente fica nulo ("—"); lotes só nos títulos', () => {
    const nubank = { distributorId: 1, distributorName: 'Nubank' } as PortfolioAssetDetail['noteInfo'];
    const g = aggregatePositions([pos({ noteInfo: nubank }), pos({ purchaseDate: '2026-02-03', noteInfo: nubank })]);
    expect(g).toMatchObject({ purchaseDate: null, maturityDate: '2040-08-15', lots: [], groupSize: 2 });
    expect(g.noteInfo?.distributorName).toBe('Nubank');
    expect(g.id).toBe(`group:${assetKey(TD, 'Tesouro IPCA+ 2040')}`);
    const misto = aggregatePositions([pos({ noteInfo: nubank }), pos({ noteInfo: { ...nubank!, distributorId: 2 } })]);
    expect(misto.noteInfo).toBeNull();
  });

  it('taxa: média ponderada pelo valor inicial (R1), com o tipo de taxa comum', () => {
    const g = aggregatePositions([
      pos({ indexerRate: 7, totalInvested: 1000 }),
      pos({ indexerRate: 8, totalInvested: 3000 }),
    ]);
    // (7 × 1000 + 8 × 3000) ÷ 4000 = 7,75
    expect(g.indexerRate).toBeCloseTo(7.75, 10);
    expect(g).toMatchObject({ rateType: 'HIBRIDO', indexer: 'IPCA' });
  });

  it('reserva: ligada só se todos os títulos estão na reserva', () => {
    expect(aggregatePositions([pos({ isReservation: true }), pos({ isReservation: true })]).isReservation).toBe(true);
    expect(aggregatePositions([pos({ isReservation: true }), pos({})]).isReservation).toBe(false);
  });
});

describe('commonNoteInfo (preset de Comprar/Vender da mãe, D4)', () => {
  const info = (distributorId: number) => ({ distributorId, distributorName: `D${distributorId}` }) as PortfolioAssetDetail['noteInfo'];

  it('mesma distribuidora em todos: vai no preset', () => {
    expect(commonNoteInfo([pos({ noteInfo: info(1) }), pos({ noteInfo: info(1) })])?.distributorId).toBe(1);
  });

  it('distribuidoras diferentes: o usuário escolhe no modal', () => {
    expect(commonNoteInfo([pos({ noteInfo: info(1) }), pos({ noteInfo: info(2) })])).toBeNull();
  });
});

describe('weightedRate', () => {
  it('ignora título sem taxa ou sem valor inicial; nenhum = null', () => {
    expect(weightedRate([{ indexerRate: 6, totalInvested: 500 }, { indexerRate: null, totalInvested: 900 },
      { indexerRate: 9, totalInvested: 0 }])).toBe(6);
    expect(weightedRate([{ indexerRate: null, totalInvested: 100 }])).toBeNull();
  });
});
