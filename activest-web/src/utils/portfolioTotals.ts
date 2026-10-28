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

/** Rodapé do modal dos aportes da renda fixa: rendimento somado e IOF + IR somados. */
export const lotTotals = (lots: Pick<FixedIncomeLot, 'income' | 'iof' | 'ir'>[]): { income: number; taxes: number } => ({
  income: lots.reduce((s, l) => s + l.income, 0),
  taxes: lots.reduce((s, l) => s + l.iof + l.ir, 0),
});
