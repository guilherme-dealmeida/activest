/**
 * Mapa de calor mês × ano da Rentabilidade (`ReturnHeatmap`, docs/planos/PLANO_RENTABILIDADE.md, L15) — a única conta
 * de rentabilidade feita no navegador (docs/planos/PLANO_TESTES_FRONT.md, 40.3). Testado em `heatmap.test.ts`.
 */

/** Célula do mapa: retorno do mês em % (nulo = sem dado) e se o mês está incompleto. */
export interface HeatmapCell {
  v: number | null;
  partial: boolean;
}

/** Ano do mapa: os meses (1 a 12) que a API devolveu e o retorno composto deles. */
export interface HeatmapYear {
  year: number;
  months: Map<number, HeatmapCell>;
  yearReturn: number | null;
}

/**
 * Retorno composto, em %: (∏(1 + r ÷ 100) − 1) × 100. Basta um mês sem dado para o ano ficar sem valor (nulo).
 * Lista vazia = 0 (nenhum mês, nenhuma variação).
 */
export const compoundReturns = (returns: (number | null)[]): number | null => {
  let factor = 1;
  for (const r of returns) {
    if (r == null) return null;
    factor *= 1 + r / 100;
  }
  return (factor - 1) * 100;
};

/** Linhas mensais da API (`key` = `YYYY-MM`) → anos do mais recente ao mais antigo, com a coluna "Ano" composta. */
export const heatmapYears = (rows: { key: string; portfolio: number | null; partial: boolean }[]): HeatmapYear[] => {
  const byYear = new Map<number, Map<number, HeatmapCell>>();
  rows.forEach(r => {
    const [y, m] = r.key.split('-').map(Number);
    if (!byYear.has(y)) byYear.set(y, new Map());
    byYear.get(y)!.set(m, { v: r.portfolio, partial: r.partial });
  });
  return [...byYear.keys()].sort((a, b) => b - a).map(year => {
    const months = byYear.get(year)!;
    return { year, months, yearReturn: compoundReturns([...months.values()].map(c => c.v)) };
  });
};
