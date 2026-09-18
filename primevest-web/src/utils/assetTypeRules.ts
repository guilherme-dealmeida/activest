import { AssetType } from '@/types';

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
 * Renda fixa com emissor (CDB, LCI, LCA, debênture...).
 *
 * Nesses tipos o ATIVO já é a categoria — "CDB — Certificado de Depósito
 * Bancário" —, e é por isso que a carteira esconde a coluna CATEGORIA e que
 * tanto as flags da carteira quanto o filtro das transações passam a ser por
 * ativo nessas abas.
 */
export const isFixedIncomeNote = (type: AssetType) =>
  type.fixedIncome && type.name !== TESOURO_DIRETO;
