/**
 * Contas de retorno de uma posição que a tela faz (17/10/2026). O TWR vem do motor de rentabilidade
 * (`/performance`); aqui só a conta simples.
 */

/**
 * Rentabilidade clássica, em %: (valorização + proventos) ÷ valor aplicado — a "Rentabilidade" do Investidor10.
 * null sem valor aplicado.
 */
export const classicReturn = (valorization: number, dividends: number, invested: number): number | null =>
  invested > 0 ? ((valorization + dividends) / invested) * 100 : null;

/**
 * Chave do TWR bruto desde o início por posição: a mesma consulta na Carteira (2ª linha da RENTABILIDADE) e no Top 5
 * do Dashboard, para as duas telas mostrarem o mesmo número e dividirem o cache.
 */
export const POSITIONS_TWR_KEY = ['performance', 'positions', 'MAX', 'bruto'] as const;
