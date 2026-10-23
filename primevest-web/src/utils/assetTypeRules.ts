import { AssetType } from '@/types';
import treasuryDirectLogo from '@/assets/logos/tesouro-direto.svg';

/**
 * Regras que dependem do tipo de ativo e valem em mais de uma tela.
 *
 * Elas olham a coluna fixed_income do tipo, e não uma lista de nomes: tipo novo
 * de renda fixa já nasce com vencimento, emissor e taxa. A única exceção
 * nominal é o Tesouro Direto — é renda fixa, mas o papel é público e o ativo
 * ("Tesouro IPCA+ 2029") não faz o papel da categoria.
 */
export const TESOURO_DIRETO = 'TESOURO_DIRETO';
export const RENDA_FIXA = 'RENDA_FIXA';

/**
 * Criptomoeda: quantidade e preço unitário com até 8 casas, a escala que o
 * banco guarda (NUMERIC(18,8)). Nas listagens a API já manda isCrypto em cada
 * linha; o nome serve ao formulário, que parte do tipo escolhido.
 */
export const CRIPTO = 'CRIPTO';

/** Casas decimais de quantidade e preço unitário de cripto. */
export const CRYPTO_DECIMALS = 8;

export const isCryptoType = (type: AssetType | null | undefined) => type?.name === CRIPTO;

/**
 * Renda fixa com emissor (CDB, LCI, LCA, debênture...).
 *
 * Nesses tipos o ATIVO já é a categoria — "CDB — Certificado de Depósito
 * Bancário" —, e é por isso que a carteira esconde a coluna CATEGORIA e que
 * tanto as flags da carteira quanto o filtro das transações passam a ser por
 * ativo nessas abas.
 */
export const isFixedIncomeNote = (type: AssetType) =>
  type.fixedIncome && type.name !== TESOURO_DIRETO;

/**
 * Logo pelo TIPO, nos tipos em que o ativo não tem logo próprio.
 *
 * Tesouro Direto: o símbolo do programa, igual para todos os títulos. Renda
 * Fixa: por enquanto o texto "RF", no mesmo quadro de quem não tem logo —
 * o logo pelo emissor do título terá planejamento próprio.
 *
 * null = o tipo segue a regra do ativo (hasLogo da API).
 */
export const typeLogo = (typeName: string | null | undefined): { src?: string; label?: string } | null => {
  if (typeName === TESOURO_DIRETO) return { src: treasuryDirectLogo };
  if (typeName === RENDA_FIXA) return { label: 'RF' };
  return null;
};
