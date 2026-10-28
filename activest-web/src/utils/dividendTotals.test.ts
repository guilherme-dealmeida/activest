import { describe, expect, it } from 'vitest';
import { DividendType } from '@/types';
import { ZERO_TOTALS, addDividend, dividendNet, dividendTax, totalsByType } from './dividendTotals';

const BRL = { foreign: null };

describe('um provento', () => {
  it('TOTAL = líquido; sem regra de IR, o bruto', () => {
    expect(dividendNet({ netAmount: 82.5, totalAmount: 100 })).toBe(82.5);
    expect(dividendNet({ netAmount: null, totalAmount: 37.4 })).toBe(37.4);
  });

  it('IR: retido no Brasil, ou retenção do exterior + IR estimado', () => {
    expect(dividendTax({ withholdingTax: 17.5, ...BRL })).toBe(17.5);
    expect(dividendTax({ withholdingTax: null, ...BRL })).toBe(0);
    expect(dividendTax({
      withholdingTax: null,
      foreign: { currencyCode: 'USD', symbol: 'US$', fxRate: 5, originalAmountPerShare: 1, originalTotalAmount: 4,
        withholdingRate: 0.3, withholdingTax: 6, withholdingLegalBasis: null, incomeTax: 0.4 },
    })).toBeCloseTo(6.4, 10);
  });
});

describe('cards', () => {
  const rows = [
    { type: DividendType.JCP, totalAmount: 100, withholdingTax: 17.5, netAmount: 82.5, ...BRL },
    { type: DividendType.DIVIDENDO, totalAmount: 50, withholdingTax: 0, netAmount: 50, ...BRL },
    { type: DividendType.RENDIMENTO, totalAmount: 20, withholdingTax: null, netAmount: null, ...BRL },
    { type: DividendType.JCP, totalAmount: 10, withholdingTax: 1.75, netAmount: 8.25, ...BRL },
  ];

  it('total: líquido, bruto e IR', () => {
    expect(rows.reduce(addDividend, ZERO_TOTALS)).toEqual({ net: 160.75, gross: 180, tax: 19.25 });
  });

  it('por tipo: na ordem do enum, só os presentes', () => {
    const byType = totalsByType(rows);
    expect(byType.map(t => t.type)).toEqual([DividendType.DIVIDENDO, DividendType.JCP, DividendType.RENDIMENTO]);
    expect(byType[1]).toEqual({ type: DividendType.JCP, net: 90.75, gross: 110, tax: 19.25 });
    expect(byType[2]).toEqual({ type: DividendType.RENDIMENTO, net: 20, gross: 20, tax: 0 });
  });
});
