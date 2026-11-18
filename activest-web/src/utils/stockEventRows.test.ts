import { describe, expect, it } from 'vitest';
import { StockEventEntry } from '@/types';
import {
  fractionHint, isStockEventRow, mergeLedger, signedShares, stockEventBadge, stockEventTitle, taxCostHint, taxCostLabel,
  toStockEventRow,
} from './stockEventRows';

const klbn4: StockEventEntry = {
  portfolioAssetId: 'pos-klbn4', stockEventId: 621, ticker: 'KLBN4', assetName: 'KLABIN S.A.',
  assetTypeId: 1, assetTypeName: 'Ações', assetCategoryId: 58, assetCategoryName: 'Materiais Básicos',
  hasLogo: true, issuerId: 346, type: 'BONIFICACAO', factor: 1, ratio: 1.01, dataCom: '2025-12-17',
  exDate: '2025-12-18', source: 'B3', sharesBefore: 300, sharesAfter: 303, sharesDelta: 3, fractionShares: 0,
  costDelta: 0, taxUnitCost: 12.94564989, taxCostDelta: 38.84, taxCostOrigin: 'USER',
};

const reverse: StockEventEntry = {
  ...klbn4, stockEventId: 9, type: 'GRUPAMENTO', factor: 0.1, ratio: 0.1, dataCom: '2024-05-24', exDate: '2024-05-27',
  sharesBefore: 105, sharesAfter: 10, sharesDelta: -95, fractionShares: 0.5, costDelta: -100, taxUnitCost: null,
  taxCostDelta: null, taxCostOrigin: null,
};

const tx = (date: string, shares: number, total: number, id: string) =>
  ({ id, transactionDate: date, shares, totalAmount: total, type: 'BUY' });

describe('linha do evento do papel', () => {
  it('bonificação: quantidade com sinal, sem custo (corretora); desdobramento/grupamento sem preço nem total', () => {
    const b = toStockEventRow(klbn4);
    expect(b).toMatchObject({ kind: 'STOCK_EVENT', transactionDate: '2025-12-18', shares: 3,
      pricePerShare: 0, totalAmount: 0, origin: 'EVENT', assetType: 'Ações' });
    const r = toStockEventRow(reverse);
    expect(r).toMatchObject({ shares: -95, pricePerShare: null, totalAmount: null });
    expect(isStockEventRow(r)).toBe(true);
    expect(isStockEventRow(tx('2025-01-01', 1, 1, 'a'))).toBe(false);
  });

  it('rótulos: razão do evento, sinal e selo da fonte', () => {
    expect(stockEventTitle(klbn4)).toBe('Bonificação 1%');
    expect(stockEventTitle({ type: 'DESDOBRAMENTO', factor: 300, ratio: 4 })).toBe('Desdobramento 1 para 4');
    expect(stockEventTitle(reverse)).toBe('Grupamento 10 para 1');
    expect(signedShares(3)).toBe('+3');
    expect(signedShares(-95)).toBe('−95');
    expect(stockEventBadge('B3')).toBe('Evento B3');
    expect(stockEventBadge('MASSIVE')).toBe('Evento EUA');
  });

  it('custo do IR da bonificação: informado, da B3 ou não informado; nada fora da bonificação', () => {
    expect(taxCostLabel(klbn4)).toMatch(/^IR: R\$\s12,9456$/);
    expect(taxCostLabel({ ...klbn4, taxUnitCost: null, taxCostOrigin: 'MISSING' })).toBe('IR: não informado');
    expect(taxCostLabel(reverse)).toBeNull();
    expect(taxCostHint(klbn4)).toMatch(/R\$\s12,94564989 por ação \(informado por você\)/);
    expect(taxCostHint({ ...klbn4, taxCostOrigin: 'CATALOG' })).toContain('informado pela B3');
    expect(taxCostHint({ ...klbn4, taxUnitCost: null, taxCostOrigin: 'MISSING' })).toContain('ainda não informado');
  });

  it('fração: dica só quando houve fração, com o custo que saiu no grupamento', () => {
    expect(fractionHint(klbn4)).toBeUndefined();
    expect(fractionHint(reverse)).toContain('0,5 cota vendida em leilão');
    expect(fractionHint(reverse)).toMatch(/R\$\s100,00/);
  });

  it('KLBN4: 5 compras + a bonificação somam 600 cotas e R$ 2.078,74 (a Carteira e o Nubank)', () => {
    const buys = [
      tx('2026-05-14', 150, 507.0, 'e'), tx('2026-05-06', 147, 502.74, 'd'), tx('2025-10-10', 100, 349.0, 'c'),
      tx('2025-10-06', 100, 358.0, 'b'), tx('2025-10-03', 100, 362.0, 'a'),
    ];
    const ledger = mergeLedger(buys, [toStockEventRow(klbn4)]);
    expect(ledger.map(r => r.transactionDate)).toEqual(
      ['2026-05-14', '2026-05-06', '2025-12-18', '2025-10-10', '2025-10-06', '2025-10-03']);
    const shares = ledger.reduce((s, r) => s + (r.shares ?? 0), 0);
    const cents = ledger.reduce((s, r) => s + Math.round((r.totalAmount ?? 0) * 100), 0);
    expect(shares).toBe(600);
    expect(cents).toBe(207874);
  });

  it('mesma data: a compra feita na data ex fica acima do evento (o evento vale antes dela)', () => {
    const ledger = mergeLedger([tx('2025-12-18', 10, 30, 'x')], [toStockEventRow(klbn4)]);
    expect(ledger.map(r => (isStockEventRow(r) ? 'evento' : 'compra'))).toEqual(['compra', 'evento']);
  });
});
