import { IndicatorCode, IndicatorUnit, IssuerKind } from '@/types';

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
  PAYOUT: { label: 'Payout', name: 'Payout', formula: 'Dividendos + JCP declarados em 12 meses (DVA) ÷ lucro da controladora em 12 meses.' },
  LPA: { label: 'LPA', name: 'Lucro por Ação', formula: 'Lucro da controladora em 12 meses ÷ ações em circulação (unit: × ações da unit).' },
  VPA: { label: 'VPA', name: 'Valor Patrimonial por Ação', formula: 'PL da controladora ÷ ações em circulação (unit: × ações da unit).' },
  VP_COTA: { label: 'VP por cota', name: 'Valor Patrimonial por Cota', formula: 'VP por cota do último informe mensal da CVM.' },
  COTISTAS: { label: 'Cotistas', name: 'Número de cotistas', formula: 'Número de cotistas do último informe mensal da CVM.' },
  IMOVEIS: { label: '% Imóveis', name: 'Imóveis sobre o investido', formula: '(Imóveis + imóveis via SPE) ÷ total investido.' },
  PAPEL: { label: '% Papel', name: 'Papel sobre o investido', formula: 'Recebíveis (CRI/CRA, LCI/LCA, LH, LIG) ÷ total investido.' },
  ALAVANCAGEM: { label: 'Alavancagem', name: 'Alavancagem', formula: 'Passivo ÷ ativo total do fundo.' },
};

/** Ordem dos cartões (decisão D3). */
export const INDICATOR_ORDER: Record<IssuerKind, IndicatorCode[]> = {
  COMPANY: ['PL', 'PVP', 'DY', 'ROE', 'PAYOUT', 'LPA', 'VPA'],
  FII: ['PVP', 'DY', 'VP_COTA', 'COTISTAS', 'IMOVEIS', 'PAPEL', 'ALAVANCAGEM'],
};

/**
 * Indicadores com série histórica. Hoje todos têm; indicador novo sem série
 * fica fora e não ganha botão nem sparkline.
 */
export const HISTORICO_DISPONIVEL = new Set<IndicatorCode>([
  'LPA', 'VPA', 'PL', 'PVP', 'ROE', 'PAYOUT', 'DY', 'VP_COTA', 'COTISTAS', 'IMOVEIS', 'PAPEL', 'ALAVANCAGEM',
]);

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

/** CNPJ só com dígitos → 33.000.167/0001-01. */
export const formatCnpj = (cnpj: string | null | undefined): string => {
  if (!cnpj) return '';
  const d = cnpj.replace(/\D/g, '').padStart(14, '0');
  return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12)}`;
};

/** Fracionário (PETR4F) — fica fora dos chips de "outros tickers". */
export const isFractional = (ticker: string): boolean => /\d+F$/.test(ticker);

export const MONTHS = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto',
  'setembro', 'outubro', 'novembro', 'dezembro'];
