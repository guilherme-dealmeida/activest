import { StockEventEntry, StockEventKind } from '@/types';
import { formatCurrency, formatMoney, formatShares, formatUnitPrice } from './format';

/**
 * Linhas de evento do papel no extrato de Transações (docs/planos/PLANO_EVENTOS_TRANSACOES.md): bonificação,
 * desdobramento e grupamento que mexeram numa posição. A conta vem pronta da API (mesmo percurso da Carteira); aqui só
 * se monta a linha, os rótulos e a ordem junto das transações.
 */
export interface StockEventRow {
  kind: 'STOCK_EVENT';
  /** Chave da linha: o evento é por posição. */
  id: string;
  event: StockEventEntry;
  // os mesmos campos que filtros, busca e ordenação leem das transações
  portfolioAssetId: string;
  ticker: string;
  assetName: string;
  assetTypeId: number | null;
  assetTypeName: string;
  assetType: string;
  assetCategoryName: string;
  hasLogo: boolean | null;
  issuerId: number | null;
  /** Data ex: quando o evento passa a valer (D3). */
  transactionDate: string;
  type: StockEventKind;
  /** Variação com sinal (D4) — é o que faz a soma do extrato bater com a Carteira. */
  shares: number;
  /** Bonificação: 0 (entra sem custo, como na corretora — seção 8.4 do plano); null no desdobramento e no grupamento. */
  pricePerShare: number | null;
  /** Bonificação: 0; null no desdobramento e no grupamento. */
  totalAmount: number | null;
  origin: 'EVENT';
  noteId: null;
  noteInfo: null;
  issuer: null;
  maturityDate: null;
  distributorName: null;
}

export const STOCK_EVENT_NAMES: Record<StockEventKind, string> = {
  BONIFICACAO: 'Bonificação',
  DESDOBRAMENTO: 'Desdobramento',
  GRUPAMENTO: 'Grupamento',
};

export const toStockEventRow = (e: StockEventEntry): StockEventRow => ({
  kind: 'STOCK_EVENT',
  id: `event:${e.portfolioAssetId}:${e.stockEventId ?? e.dataCom}`,
  event: e,
  portfolioAssetId: e.portfolioAssetId,
  ticker: e.ticker,
  assetName: e.assetName,
  assetTypeId: e.assetTypeId,
  assetTypeName: e.assetTypeName,
  assetType: e.assetTypeName || '',
  assetCategoryName: e.assetCategoryName,
  hasLogo: e.hasLogo,
  issuerId: e.issuerId,
  transactionDate: e.exDate,
  type: e.type,
  shares: e.sharesDelta,
  pricePerShare: e.type === 'BONIFICACAO' ? 0 : null,
  totalAmount: e.type === 'BONIFICACAO' ? e.costDelta : null,
  origin: 'EVENT',
  noteId: null,
  noteInfo: null,
  issuer: null,
  maturityDate: null,
  distributorName: null,
});

/** "1 para 2", "10 para 1" — a razão em cotas, como o aviso de evento aplicado. */
export const stockEventRatio = (e: Pick<StockEventEntry, 'type' | 'factor' | 'ratio'>): string => {
  if (e.type === 'BONIFICACAO') return `${formatShares(e.factor)}%`;
  if (e.ratio == null || e.ratio <= 0) return `fator ${formatShares(e.factor)}`;
  return e.ratio >= 1 ? `1 para ${formatShares(e.ratio)}` : `${formatShares(1 / e.ratio)} para 1`;
};

/** "Bonificação 1%", "Desdobramento 1 para 2", "Grupamento 10 para 1". */
export const stockEventTitle = (e: Pick<StockEventEntry, 'type' | 'factor' | 'ratio'>): string =>
  `${STOCK_EVENT_NAMES[e.type]} ${stockEventRatio(e)}`;

/** "+3", "−95" (sinal de menos tipográfico). */
export const signedShares = (value: number): string =>
  value > 0 ? `+${formatShares(value)}` : value < 0 ? `−${formatShares(-value)}` : formatShares(0);

/** Dica da quantidade: a fração vendida em leilão (D8), quando houver. */
export const fractionHint = (e: Pick<StockEventEntry, 'fractionShares' | 'costDelta' | 'type'>): string | undefined => {
  if (!e.fractionShares || e.fractionShares <= 0) return undefined;
  const cost = e.type !== 'BONIFICACAO' && e.costDelta < 0
    ? ` O custo proporcional (${formatCurrency(-e.costDelta)}) saiu da posição junto com ela.`
    : '';
  return `Fração de ${formatShares(e.fractionShares)} cota vendida em leilão pela B3 e paga em dinheiro `
    + `(o valor recebido não entra no Activest).${cost}`;
};

/**
 * Segunda linha do PREÇO da bonificação: o custo por ação para o IR (D15), curto para caber na coluna — "IR: R$ 12,9456"
 * ou "IR: não informado". null fora da bonificação. O valor exato e a origem vão na dica ({@link taxCostHint}).
 */
export const taxCostLabel = (e: Pick<StockEventEntry, 'type' | 'taxUnitCost' | 'taxCostOrigin'>): string | null => {
  if (e.type !== 'BONIFICACAO') return null;
  if (e.taxUnitCost == null || e.taxCostOrigin === 'MISSING') return 'IR: não informado';
  return `IR: ${formatMoney(e.taxUnitCost, 'R$', 2, 4)}`;
};

/** Dica do PREÇO e do TOTAL da bonificação: por que R$ 0,00 e o custo do IR exato, com a origem. */
export const taxCostHint = (e: Pick<StockEventEntry, 'taxUnitCost' | 'taxCostOrigin'>): string => {
  const base = 'As ações bonificadas entram sem custo no preço médio, como na corretora. Para o imposto de renda, elas '
    + 'custam o valor atribuído pela empresa (está no fato relevante).';
  if (e.taxUnitCost == null || e.taxCostOrigin === 'MISSING') return `${base} Custo para o IR ainda não informado.`;
  const from = e.taxCostOrigin === 'CATALOG' ? 'informado pela B3' : 'informado por você';
  return `${base} Custo para o IR: ${formatUnitPrice(e.taxUnitCost, true)} por ação (${from}).`;
};

/** Selo da linha: a fonte do evento no catálogo. */
export const stockEventBadge = (source: string | null): string => (source === 'MASSIVE' ? 'Evento EUA' : 'Evento B3');

/**
 * Transações e eventos numa lista só, da data mais recente para a mais antiga (a ordem padrão do extrato). Na mesma
 * data a transação vem antes do evento: o evento vale a partir da data ex, ANTES de uma compra feita nela — na ordem
 * decrescente, a compra do dia fica acima. A ordem das transações entre si é a da API (a ordenação é estável).
 */
export const mergeLedger = <T extends { transactionDate: string }>(
  transactions: T[], events: StockEventRow[]): (T | StockEventRow)[] => {
  const rank = (row: T | StockEventRow) => ('kind' in row && row.kind === 'STOCK_EVENT' ? 1 : 0);
  return [...transactions, ...events].sort((a, b) => {
    if (a.transactionDate !== b.transactionDate) return a.transactionDate < b.transactionDate ? 1 : -1;
    return rank(a) - rank(b);
  });
};

export const isStockEventRow = (row: object): row is StockEventRow =>
  'kind' in row && (row as { kind: unknown }).kind === 'STOCK_EVENT';
