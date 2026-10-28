/**
 * Contas do formulário de transação (`TransactionFormModal`) — docs/planos/PLANO_TESTES_FRONT.md, 40.5.
 * Testado em `transactionAmounts.test.ts`. Quem grava é a API; a tela mostra a prévia.
 */

/**
 * Total mostrado no formulário: na renda fixa e no fundo é o valor digitado (o lançamento é por valor); nos demais,
 * cotas × preço.
 */
export const transactionTotal = (byValue: boolean, shares: number | null | undefined, price: number | null | undefined,
  totalAmount: number | null | undefined): number =>
  byValue ? (totalAmount || 0) : (shares || 0) * (price || 0);

const SHARE_SCALE = 100_000_000n; // 8 casas

/**
 * Cotas do fundo = valor ÷ cota, com 8 casas e HALF_EVEN — a mesma conta da API, que é quem grava
 * (`TransactionService`: `totalAmount.divide(pricePerShare, 8, HALF_EVEN)`; docs/planos/PLANO_FUNDOS_INVESTIMENTO.md).
 * Sem cota, zero.
 *
 * Em inteiros exatos (D8 de docs/planos/PLANO_TESTES_FRONT.md, 27/10/2026): o valor tem 2 casas (`NUMERIC(18,2)`) e a
 * cota até 8 (`NUMERIC(18,8)`), então cotas × 10⁸ = centavos × 10¹⁴ ÷ (cota × 10⁸), com o resto decidindo o
 * arredondamento — meio vai para o par. Antes a tela arredondava meio para cima no ponto flutuante e, no empate da 9ª
 * casa, mostrava uma cota a mais na 8ª casa (1 ÷ 512: 0,00195313 contra os 0,00195312 gravados).
 */
export const fundShares = (totalAmount: number | null | undefined, quota: number | null | undefined): number => {
  if (!quota || quota <= 0 || !totalAmount) return 0;
  const cents = BigInt(Math.round(Math.abs(totalAmount) * 100));
  const quotaUnits = BigInt(Math.round(quota * 1e8));
  if (quotaUnits === 0n) return 0;
  const numerator = cents * 100_000_000_000_000n; // centavos × 10¹⁴
  let units = numerator / quotaUnits; // cotas × 10⁸, truncado
  const twiceRemainder = (numerator % quotaUnits) * 2n;
  if (twiceRemainder > quotaUnits || (twiceRemainder === quotaUnits && units % 2n === 1n)) units += 1n;
  const shares = Number(units / SHARE_SCALE) + Number(units % SHARE_SCALE) / 1e8;
  return totalAmount < 0 ? -shares : shares;
};

/** "Resgatar tudo": o líquido estimado do título em centavos (o valor da venda de renda fixa é o líquido). */
export const fullRedemptionAmount = (estimatedNet: number): number => Math.round(estimatedNet * 100) / 100;
