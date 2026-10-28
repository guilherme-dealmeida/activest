import { DividendResponse, DividendType } from '@/types';

/**
 * Totais dos cards de Dividendos (Total Recebido / a Receber e "por Tipo"; docs/planos/PLANO_IR_PROVENTOS.md, D3, e
 * docs/planos/PLANO_CADASTRO_MOEDAS.md, C6) — docs/planos/PLANO_TESTES_FRONT.md, 40.2. Testado em `dividendTotals.test.ts`.
 */

export type DividendTotals = { net: number; gross: number; tax: number };
export const ZERO_TOTALS: DividendTotals = { net: 0, gross: 0, tax: 0 };

type Amounts = Pick<DividendResponse, 'netAmount' | 'totalAmount' | 'withholdingTax' | 'foreign'>;

/** TOTAL do provento: o líquido; sem regra de IR (líquido nulo), o bruto. */
export const dividendNet = (d: Pick<DividendResponse, 'netAmount' | 'totalAmount'>): number =>
  d.netAmount ?? d.totalAmount;

/** IR do provento: retido na fonte + retenção do país de origem + IR brasileiro estimado (provento do exterior). */
export const dividendTax = (d: Pick<DividendResponse, 'withholdingTax' | 'foreign'>): number =>
  (d.withholdingTax ?? 0) + (d.foreign?.withholdingTax ?? 0) + (d.foreign?.incomeTax ?? 0);

export const addDividend = (acc: DividendTotals, d: Amounts): DividendTotals => ({
  net: acc.net + dividendNet(d),
  gross: acc.gross + (d.totalAmount ?? 0),
  tax: acc.tax + dividendTax(d),
});

/** Totais por tipo de provento, na ordem do enum, só os tipos presentes. */
export const totalsByType = (rows: (Amounts & { type: DividendType })[]): ({ type: DividendType } & DividendTotals)[] => {
  const sums = new Map<DividendType, DividendTotals>();
  rows.forEach(r => sums.set(r.type, addDividend(sums.get(r.type) ?? ZERO_TOTALS, r)));
  return Object.values(DividendType).filter(t => sums.has(t)).map(t => ({ type: t, ...(sums.get(t) as DividendTotals) }));
};
