/**
 * Regras da Estratégia e do Rebalanceamento feitas na tela — docs/planos/PLANO_TESTES_FRONT.md, 40.7 e 40.8.
 * Testado em `strategyRules.test.ts`.
 */

/**
 * Soma dos percentuais ideais em CENTÉSIMOS inteiros: cada item tem duas casas (`numeric(5,2)`), e somar em ponto
 * flutuante errava o arredondamento (A1/D2: 0,10 + 99,85 virava 99,94999…).
 */
export const strategyTotalHundredths = (values: (number | string)[]): number =>
  values.reduce<number>((s, v) => s + Math.round(Number(v) * 100), 0);

/** A soma em décimos com HALF_UP — a mesma conta do backend (`StrategyService.validateTotal`: `setScale(1, HALF_UP)`). */
const toTenthsHalfUp = (hundredths: number): number => Math.sign(hundredths) * Math.floor((Math.abs(hundredths) + 5) / 10);

/** A soma vale 100%? Arredondada a UMA casa, como o backend: 33,33 × 3 = 99,99 e 8,33 × 12 = 99,96 passam. */
export const isStrategyTotalValid = (values: (number | string)[]): boolean =>
  toTenthsHalfUp(strategyTotalHundredths(values)) === 1000;

/** A soma em % (para mostrar), exata nos centésimos. */
export const strategyTotal = (values: (number | string)[]): number => strategyTotalHundredths(values) / 100;

/** A soma com uma casa, como a mensagem de erro mostra ("Hoje está em 99,9%"). */
export const strategyTotalLabel = (values: (number | string)[]): string =>
  (toTenthsHalfUp(strategyTotalHundredths(values)) / 10).toFixed(1).replace('.', ',');

/**
 * % ideal da reserva de emergência: Por Porcentagem, o informado; Por Valor, alvo ÷ carteira, limitado a 100% — a
 * mesma conta do backend. Carteira zerada: 0.
 */
export const reservationIdealPercentage = (byValue: boolean, targetValue: number, targetPercentage: number,
  portfolioTotal: number): number =>
  byValue ? (portfolioTotal > 0 ? Math.min(100, (targetValue / portfolioTotal) * 100) : 0) : targetPercentage;

/** Geração de Renda = o que sobra da carteira (ou dos 100%) depois do alvo da reserva; nunca negativa. */
export const incomeShare = (whole: number, reservation: number): number => Math.max(0, whole - reservation);

/** Coluna "% Diff" da reserva: o tamanho e o lado ("acima"/"abaixo"; igual, sem lado). */
export const reservationDiff = (currentPercentage: number, idealPercentage: number):
  { diff: number; abs: number; side: 'acima' | 'abaixo' | '' } => {
  const diff = currentPercentage - idealPercentage;
  return { diff, abs: Math.abs(diff), side: diff > 0 ? 'acima' : diff < 0 ? 'abaixo' : '' };
};

/** Rebalanceamento: quanto aportar somado e quanto a carteira passa a valer depois dos aportes. */
export const rebalanceTotals = (items: { amountToRebalance: number; idealValue: number }[]):
  { toInvest: number; strategic: number } => ({
  toInvest: items.reduce((s, i) => s + i.amountToRebalance, 0),
  strategic: items.reduce((s, i) => s + i.idealValue, 0),
});
