import { FixedIncomeLot, PortfolioAssetDetail } from '@/types';
import { classicReturn } from './returns';

/**
 * Rodapé da Carteira (por tipo de ativo) e total dos aportes da renda fixa — docs/planos/PLANO_TESTES_FRONT.md, 40.4.
 * Testado em `portfolioTotals.test.ts`.
 */

type PositionAmounts = Pick<
  PortfolioAssetDetail,
  'totalInvested' | 'totalSold' | 'currentValue' | 'totalDividends' | 'valorization' | 'estimatedNet'
>;

export interface PortfolioTotals {
  invested: number;
  sold: number;
  current: number;
  dividends: number;
  valorization: number;
  /** Valorização ÷ investido, em % — sobre as SOMAS (17/10/2026), não a média das linhas. 0 sem investido. */
  valorizationPct: number;
  /** Rentabilidade clássica sobre as somas: (valorização + proventos) ÷ investido, em %. 0 sem investido. */
  classic: number;
  /** Alguma posição tem líquido estimado (renda fixa, Tesouro, fundo)? Só então o rodapé mostra "líq.". */
  hasNet: boolean;
  /** Líquido estimado da aba: o da posição quando há, senão o valor atual (sem curva não há estimativa). */
  net: number;
}

export const portfolioTotals = (assets: PositionAmounts[]): PortfolioTotals => {
  const sum = (f: (a: PositionAmounts) => number) => assets.reduce((s, a) => s + f(a), 0);
  const invested = sum(a => a.totalInvested);
  const valorization = sum(a => a.valorization);
  const dividends = sum(a => a.totalDividends);
  return {
    invested,
    sold: sum(a => a.totalSold),
    current: sum(a => a.currentValue),
    dividends,
    valorization,
    valorizationPct: invested > 0 ? (valorization / invested) * 100 : 0,
    classic: classicReturn(valorization, dividends, invested) ?? 0,
    hasNet: assets.some(a => a.estimatedNet != null),
    net: sum(a => a.estimatedNet ?? a.currentValue),
  };
};

export interface ForeignTotals {
  symbol: string;
  invested: number;
  current: number;
  dividends: number;
  valorization: number;
  /** Valorização ÷ investido na moeda, em %. 0 sem investido. */
  valorizationPct: number;
}

/**
 * Totais da aba na outra moeda (docs/planos/PLANO_PRECOS_INTERNACIONAIS.md, R2/R4): só quando todas as posições
 * mostradas têm os valores na mesma moeda (exterior e cripto); senão null e o rodapé fica só em reais.
 */
export const foreignTotals = (assets: Pick<PortfolioAssetDetail, 'foreignValues'>[]): ForeignTotals | null => {
  if (assets.length === 0) return null;
  const first = assets[0].foreignValues;
  if (!first) return null;
  const all = assets.map(a => a.foreignValues);
  if (all.some(v => !v || v.currencyCode !== first.currencyCode || v.totalInvested == null || v.currentValue == null)) {
    return null;
  }
  const sum = (f: (v: NonNullable<PortfolioAssetDetail['foreignValues']>) => number | null) =>
    all.reduce((s, v) => s + (f(v!) ?? 0), 0);
  const invested = sum(v => v.totalInvested);
  const current = sum(v => v.currentValue);
  const valorization = current - invested;
  return {
    symbol: first.symbol,
    invested,
    current,
    dividends: sum(v => v.totalDividends),
    valorization,
    valorizationPct: invested > 0 ? (valorization / invested) * 100 : 0,
  };
};

/** Rodapé do modal dos aportes da renda fixa: rendimento somado e IOF + IR somados. */
export const lotTotals = (lots: Pick<FixedIncomeLot, 'income' | 'iof' | 'ir'>[]): { income: number; taxes: number } => ({
  income: lots.reduce((s, l) => s + l.income, 0),
  taxes: lots.reduce((s, l) => s + l.iof + l.ir, 0),
});
