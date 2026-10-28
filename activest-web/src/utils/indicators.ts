import { IndicatorCode, IndicatorHistory, IndicatorPoint, IndicatorUnit, IndicatorWindow, IssuerKind } from '@/types';

/**
 * Metadados dos indicadores da tela de emissoras (docs/planos/PLANO_TELA_EMISSORES.md, 2.4).
 * As fórmulas são as do docs/planos/PLANO_EMPRESAS_BALANCOS.md, 5.1 e 5.2 — o cálculo é do
 * backend; aqui é só rótulo e explicação.
 */
export interface IndicatorMeta {
  label: string;
  name: string;
  formula: string;
}

export const INDICATOR_META: Record<IndicatorCode, IndicatorMeta> = {
  PL: { label: 'P/L', name: 'Preço / Lucro', formula: 'Preço ÷ LPA. Nulo com lucro de 12 meses negativo ou zero.' },
  PVP: { label: 'P/VP', name: 'Preço / Valor Patrimonial', formula: 'Preço ÷ VPA (ação) ou preço ÷ VP por cota do informe mensal (FII).' },
  DY: { label: 'DY', name: 'Dividend Yield', formula: 'Proventos por cota com data-com nos últimos 12 meses (dividendo, JCP, rendimento) ÷ preço. Sem provento = 0.' },
  ROE: { label: 'ROE', name: 'Retorno sobre o PL', formula: 'Lucro da controladora em 12 meses ÷ PL da controladora médio (início e fim do período).' },
  PAYOUT: { label: 'Payout', name: 'Payout', formula: 'Dividendos + JCP pagos aos acionistas em 12 meses (fluxo de caixa) ÷ lucro da controladora em 12 meses.' },
  LPA: { label: 'LPA', name: 'Lucro por Ação', formula: 'Lucro da controladora em 12 meses ÷ ações em circulação (unit: × ações da unit).' },
  VPA: { label: 'VPA', name: 'Valor Patrimonial por Ação', formula: 'PL da controladora ÷ ações em circulação (unit: × ações da unit).' },
  VP_COTA: { label: 'VP por cota', name: 'Valor Patrimonial por Cota', formula: 'VP por cota do último informe mensal da CVM.' },
  COTISTAS: { label: 'Cotistas', name: 'Número de cotistas', formula: 'Número de cotistas do último informe mensal da CVM.' },
  IMOVEIS: { label: '% Imóveis', name: 'Imóveis sobre o investido', formula: '(Imóveis + imóveis via SPE) ÷ total investido.' },
  PAPEL: { label: '% Papel', name: 'Papel sobre o investido', formula: 'Recebíveis (CRI/CRA, LCI/LCA, LH, LIG) ÷ total investido.' },
  ALAVANCAGEM: { label: 'Alavancagem', name: 'Alavancagem', formula: 'Passivo ÷ ativo total do fundo.' },
  // tela de Ativos (docs/planos/PLANO_TELA_ATIVOS.md, 2.5). PL consolidado e lucro líquido consolidado nos novos.
  PSR: { label: 'P/Receita (PSR)', name: 'Preço / Receita', formula: 'Preço ÷ (receita de 12 meses ÷ ações em circulação).' },
  P_EBIT: { label: 'P/EBIT', name: 'Preço / EBIT', formula: 'Preço ÷ (EBIT de 12 meses ÷ ações). Nulo com EBIT negativo ou zero.' },
  P_EBITDA: { label: 'P/EBITDA', name: 'Preço / EBITDA', formula: 'Preço ÷ (EBITDA de 12 meses ÷ ações). EBITDA = EBIT + depreciação e amortização (sem a exaustão dos ativos biológicos).' },
  EV_EBIT: { label: 'EV/EBIT', name: 'Valor de firma / EBIT', formula: '(Valor de mercado da companhia + dívida líquida) ÷ EBIT de 12 meses. Igual em todos os tickers da empresa.' },
  EV_EBITDA: { label: 'EV/EBITDA', name: 'Valor de firma / EBITDA', formula: '(Valor de mercado da companhia + dívida líquida) ÷ EBITDA de 12 meses. Igual em todos os tickers da empresa.' },
  P_ATIVO: { label: 'P/Ativo', name: 'Preço / Ativo', formula: 'Preço ÷ (ativo total ÷ ações).' },
  P_CAP_GIRO: { label: 'P/Cap. Giro', name: 'Preço / Capital de giro', formula: 'Preço ÷ ((ativo circulante − passivo circulante) ÷ ações). Nulo com capital de giro negativo ou zero.' },
  P_ATIVO_CIRC_LIQ: { label: 'P/Ativo Circ. Líq.', name: 'Preço / Ativo circulante líquido', formula: 'Preço ÷ ((ativo circulante − passivo total) ÷ ações), passivo total = ativo total − PL. Nulo com base negativa ou zero.' },
  DIV_LIQ_EBIT: { label: 'Dív. Líq./EBIT', name: 'Dívida líquida / EBIT', formula: '(Dívida bruta − caixa) ÷ EBIT de 12 meses.' },
  DIV_LIQ_EBITDA: { label: 'Dív. Líq./EBITDA', name: 'Dívida líquida / EBITDA', formula: '(Dívida bruta − caixa) ÷ EBITDA de 12 meses.' },
  DIV_BRUTA_PATRIMONIO: { label: 'Dív. Bruta/PL', name: 'Dívida bruta / Patrimônio', formula: 'Dívida bruta ÷ patrimônio líquido.' },
  PATRIMONIO_ATIVOS: { label: 'PL/Ativos', name: 'Patrimônio / Ativos', formula: 'Patrimônio líquido ÷ ativo total.' },
  PASSIVOS_ATIVOS: { label: 'Passivos/Ativos', name: 'Passivos / Ativos', formula: '(Ativo total − patrimônio líquido) ÷ ativo total.' },
  LIQUIDEZ_CORRENTE: { label: 'Liquidez Corrente', name: 'Liquidez corrente', formula: 'Ativo circulante ÷ passivo circulante.' },
  MARGEM_BRUTA: { label: 'Margem Bruta', name: 'Margem bruta', formula: 'Lucro bruto de 12 meses ÷ receita de 12 meses.' },
  MARGEM_EBITDA: { label: 'Margem EBITDA', name: 'Margem EBITDA', formula: 'EBITDA de 12 meses ÷ receita de 12 meses.' },
  MARGEM_EBIT: { label: 'Margem EBIT', name: 'Margem EBIT', formula: 'EBIT de 12 meses ÷ receita de 12 meses.' },
  MARGEM_LIQUIDA: { label: 'Margem Líquida', name: 'Margem líquida', formula: 'Lucro líquido de 12 meses ÷ receita de 12 meses.' },
  ROA: { label: 'ROA', name: 'Retorno sobre os ativos', formula: 'Lucro líquido de 12 meses ÷ ativo total médio (início e fim do período).' },
  ROIC: { label: 'ROIC', name: 'Retorno sobre o capital investido', formula: 'EBIT de 12 meses × (1 − IR/CSLL ÷ resultado antes dos tributos) ÷ (PL + dívida líquida) médio. Nulo com alíquota fora de 0–100%.' },
  GIRO_ATIVOS: { label: 'Giro Ativos', name: 'Giro dos ativos', formula: 'Receita de 12 meses ÷ ativo total médio (início e fim do período).' },
  CAGR_RECEITAS_5A: { label: 'CAGR Receitas 5a', name: 'Crescimento da receita em 5 anos', formula: 'Crescimento anual composto da receita de 12 meses contra a de 5 anos antes. Vazio com valor negativo ou zero.' },
  CAGR_LUCROS_5A: { label: 'CAGR Lucros 5a', name: 'Crescimento do lucro em 5 anos', formula: 'Crescimento anual composto do lucro líquido de 12 meses contra o de 5 anos antes. Vazio com valor negativo ou zero.' },
};

/** Um grupo de cartões com subtítulo (D16). FII: um grupo só, sem título. */
export interface IndicatorGroup {
  title: string | null;
  codes: IndicatorCode[];
}

/** Ordem e grupos dos cartões (D3 da tela de emissoras; D16 da tela de Ativos). */
export const INDICATOR_GROUPS: Record<IssuerKind, IndicatorGroup[]> = {
  COMPANY: [
    { title: 'Valuation', codes: ['PL', 'PVP', 'DY', 'PSR', 'P_EBIT', 'P_EBITDA', 'EV_EBIT', 'EV_EBITDA', 'P_ATIVO',
      'P_CAP_GIRO', 'P_ATIVO_CIRC_LIQ', 'LPA', 'VPA'] },
    { title: 'Endividamento', codes: ['DIV_LIQ_EBIT', 'DIV_LIQ_EBITDA', 'DIV_BRUTA_PATRIMONIO', 'PATRIMONIO_ATIVOS',
      'PASSIVOS_ATIVOS', 'LIQUIDEZ_CORRENTE'] },
    { title: 'Eficiência', codes: ['MARGEM_BRUTA', 'MARGEM_EBITDA', 'MARGEM_EBIT', 'MARGEM_LIQUIDA'] },
    { title: 'Rentabilidade', codes: ['ROE', 'ROA', 'ROIC', 'GIRO_ATIVOS', 'PAYOUT'] },
    { title: 'Crescimento', codes: ['CAGR_RECEITAS_5A', 'CAGR_LUCROS_5A'] },
  ],
  FII: [{ title: null, codes: ['PVP', 'DY', 'VP_COTA', 'COTISTAS', 'IMOVEIS', 'PAPEL', 'ALAVANCAGEM'] }],
};

/** Ordem dos cartões, sem os grupos (navegação ‹ › do modal). */
export const INDICATOR_ORDER: Record<IssuerKind, IndicatorCode[]> = {
  COMPANY: INDICATOR_GROUPS.COMPANY.flatMap(g => g.codes),
  FII: INDICATOR_GROUPS.FII.flatMap(g => g.codes),
};

/** Janelas dos indicadores (D14); 5A é o padrão (T5). */
export const INDICATOR_WINDOWS: IndicatorWindow[] = ['3A', '5A', 'MAX'];
export const DEFAULT_WINDOW: IndicatorWindow = '5A';

/**
 * Pontos de uma janela (D14), como o backend calcula a média: no Anual, os N últimos pontos anuais; no Trimestral,
 * do 1º trimestre do primeiro ano da janela em diante. MAX = todos.
 */
export const pointsInWindow = (h: IndicatorHistory, window: IndicatorWindow): IndicatorPoint[] => {
  if (window === 'MAX') return h.points;
  if (h.period === 'YEAR') {
    const n = window === '3A' ? 3 : 5;
    return h.points.slice(Math.max(0, h.points.length - n));
  }
  const from = h.averages?.find(a => a.window === window)?.fromYear;
  return from == null ? h.points : h.points.filter(p => p.periodEnd != null && Number(p.periodEnd.slice(0, 4)) >= from);
};

/** Média e ano inicial de uma janela; sem `averages` (resposta antiga), a de MAX. */
export const averageOf = (h: IndicatorHistory, window: IndicatorWindow): { value: number | null; fromYear: number | null } => {
  const a = h.averages?.find(x => x.window === window);
  if (a) return { value: a.value, fromYear: a.fromYear };
  return { value: h.companyAverage, fromYear: h.points.length ? Number(h.points[0].label) || null : null };
};

/**
 * Indicadores com série histórica. Hoje todos têm; indicador novo sem série
 * fica fora e não ganha botão nem sparkline.
 */
export const HISTORY_AVAILABLE = new Set<IndicatorCode>(Object.keys(INDICATOR_META) as IndicatorCode[]);

const nf = (min: number, max: number) =>
  new Intl.NumberFormat('pt-BR', { minimumFractionDigits: min, maximumFractionDigits: max });

/**
 * Valor pela unidade. `precise` = tabela do histórico (R$ com 4 casas).
 * null vira "—": o motivo vai no tooltip, nunca um zero inventado.
 */
export const formatIndicator = (value: number | null | undefined, unit: IndicatorUnit, precise = false): string => {
  if (value === null || value === undefined) return '—';
  switch (unit) {
    case 'PERCENT':
      return `${nf(2, 2).format(value)}%`;
    case 'RATIO':
      return nf(2, 2).format(value);
    case 'BRL':
      return `R$ ${nf(precise ? 4 : 2, precise ? 4 : 2).format(value)}`;
    case 'COUNT':
      return nf(0, 0).format(value);
  }
};

/** Rótulo curto para eixo de gráfico. */
export const formatIndicatorAxis = (value: number, unit: IndicatorUnit): string => {
  if (unit === 'COUNT') return formatCompactNumber(value);
  if (unit === 'PERCENT') return `${nf(0, 1).format(value)}%`;
  return nf(0, 2).format(value);
};

/** 1.234.567 → "1,2 mi"; 845.200 → "845,2 mil". */
export const formatCompactNumber = (value: number): string => {
  const abs = Math.abs(value);
  const sign = value < 0 ? '-' : '';
  if (abs >= 1e9) return `${sign}${nf(0, 2).format(abs / 1e9)} bi`;
  if (abs >= 1e6) return `${sign}${nf(0, 1).format(abs / 1e6)} mi`;
  if (abs >= 1e3) return `${sign}${nf(0, 1).format(abs / 1e3)} mil`;
  return `${sign}${nf(0, 2).format(abs)}`;
};

/** Valores de demonstrativo: R$ 110,13 bi / R$ 845,2 mi. */
export const formatCompactCurrency = (value: number | null | undefined): string => {
  if (value === null || value === undefined) return '—';
  const c = formatCompactNumber(value);
  return c.startsWith('-') ? `-R$ ${c.slice(1)}` : `R$ ${c}`;
};

/**
 * Valor detalhado (D12): o número inteiro, com todas as casas — R$ 548.490.000.000,00 / 12.888.732.761.
 * `money` com 2 casas; `count` sem casas.
 */
export const formatFull = (value: number | null | undefined, kind: 'money' | 'count'): string => {
  if (value === null || value === undefined) return '—';
  if (kind === 'count') return nf(0, 0).format(value);
  const f = nf(2, 2).format(Math.abs(value));
  return value < 0 ? `-R$ ${f}` : `R$ ${f}`;
};

/** CNPJ só com dígitos → 33.000.167/0001-01. */
export const formatCnpj = (cnpj: string | null | undefined): string => {
  if (!cnpj) return '';
  const d = cnpj.replace(/\D/g, '').padStart(14, '0');
  return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}`;
};

/**
 * Diferença em centavos, sem o ruído do ponto flutuante — a linha "Lucro líquido não controladores" dos Demonstrativos
 * (consolidado − controladora, calculada na tela; docs/planos/PLANO_USABILIDADE_PRECO_TETO.md). Nulo se faltar um lado.
 */
export const centsDifference = (a: number | null | undefined, b: number | null | undefined): number | null =>
  a == null || b == null ? null : Math.round((a - b) * 100) / 100;

/** Fracionário (PETR4F) — fica fora dos chips de "outros tickers". */
export const isFractional = (ticker: string): boolean => /\d+F$/.test(ticker);

export const MONTHS = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto',
  'setembro', 'outubro', 'novembro', 'dezembro'];
