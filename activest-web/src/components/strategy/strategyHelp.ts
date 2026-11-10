/**
 * Textos dos (i) das tabelas da Estratégia.
 *
 * As mesmas colunas aparecem em três tabelas — Ideal Estratégico, Estratégia
 * Reserva e as duas do Rebalanceamento —, e a explicação tem de ser a mesma
 * em todas. As de rebalanceamento descrevem o cálculo de StrategyService e
 * StrategyReservationService; mudou a conta lá, muda o texto aqui.
 */
export const STRATEGY_HELP = {
  currentPercentage: 'Quanto da carteira está neste bloco hoje.',
  idealPercentage: 'O percentual que você definiu como alvo.',
  diffPercentage: '% Atual − % Ideal. Negativo significa abaixo do alvo.',
  relativeDeviation:
    '% Diff ÷ % Ideal: quanto o bloco está acima ou abaixo do próprio alvo, em proporção a ele. Na sugestão por tipo, o maior desvio relativo é a referência do rebalanceamento e fica em AGUARDAR.',
  reservationType:
    'Por Porcentagem: a reserva é uma fatia da carteira. Por Valor: a reserva tem um alvo fixo em reais.',
  reservationRebalance: 'A reserva é um bloco próprio e não entra na sugestão por tipo abaixo.',
  contributed: 'Quanto o bloco vale hoje: valor aplicado + valorização − resgates (o valor atual da Alocação).',
  toContribute:
    'Quanto falta aportar para chegar ao estratégico. Negativo significa acima do alvo, e a tela não sugere venda.',
  strategic: 'Quanto o bloco deve valer depois do aporte sugerido.',
  portfolioPercentage: 'Quanto o bloco ocupa da carteira hoje.',
  suggestion: 'APORTAR quando falta valor para o alvo; AGUARDAR quando já está no alvo ou acima dele.',
  typeSuggestion:
    'APORTAR: está abaixo do ideal. COMPLEMENTAR: já está no ideal ou acima, e recebe aporte só para manter a proporção com a referência. AGUARDAR: é a referência (o maior desvio relativo) e não recebe aporte.',
  typeRebalance:
    'Calculada só com a geração de renda. A referência é o tipo que está mais acima do seu ideal, e os outros recebem aporte até ficar na mesma proporção, sem vender nada.',
} as const;

/** Aviso do ↻: diz que o rascunho foi descartado só quando havia rascunho. */
export const restoredMessage = (hadChanges: boolean) =>
  hadChanges
    ? 'Valores restaurados do banco. As alterações não salvas foram descartadas.'
    : 'Valores restaurados do banco.';
