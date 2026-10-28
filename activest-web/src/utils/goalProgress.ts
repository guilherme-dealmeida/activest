/**
 * Barras das Metas (`GoalDetailPage`, `GoalCard`, `GoalFormModal`) — docs/planos/PLANO_TESTES_FRONT.md, 40.10.
 * O progresso vem calculado da API (`progressPct`); a tela só calcula o uso da meta de limite.
 * Testado em `goalProgress.test.ts`.
 */

/**
 * Meta de limite (não passar dele): uso = max(0, indicador) ÷ limite, em %. Nulo sem limite positivo ou sem indicador
 * (aí a tela mostra a barra do progresso).
 */
export const limitUsage = (indicator: number | string | null, limit: number | string | null): number | null =>
  limit !== null && Number(limit) > 0 && indicator !== null ? (Math.max(0, Number(indicator)) / Number(limit)) * 100 : null;

/** Cor da barra do limite: passou de 100% vermelho, de 80% amarelo, senão verde. */
export const limitBarClass = (usage: number): string =>
  usage > 100 ? 'h-full bg-danger' : usage > 80 ? 'h-full bg-yellow-500' : 'h-full bg-success';

/** Largura da barra, em %: o valor limitado a 100 (a barra não passa do trilho). */
export const barWidth = (pct: number | string): number => Math.min(100, Number(pct));
