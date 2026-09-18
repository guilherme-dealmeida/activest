export interface User {
  id: string;
  name: string;
  email: string;
  avatarUrl: string;
  createdAt?: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface AssetType {
  id: number;
  name: string;
  description: string;
  colorHex: string;
  /** Tipos com remuneração contratada: mostram os campos de renda fixa no cadastro. */
  fixedIncome: boolean;
}

export interface AssetCategory {
  id: number;
  name: string;
  description: string;
  /** FII: segmento de atuação; Ação: segmento da B3; demais tipos: null. */
  segment: string | null;
  assetTypeId: number;
  assetTypeName: string;
}

export type RateType = 'PREFIXADO' | 'POS_FIXADO' | 'HIBRIDO';
export type Indexer = 'CDI' | 'SELIC' | 'IPCA' | 'IGPM' | 'INPC' | 'TR';

export interface Asset {
  id: number;
  ticker: string;
  name: string;
  typeId: number | null;
  typeName: string;
  categoryId: number | null;
  categoryName: string;
  currentPrice: number;
  priceUpdatedAt: string;
  // Parâmetros PÚBLICOS do papel, vindos de asset_terms. Hoje só o Tesouro
  // Direto os tem: "Tesouro IPCA+ 2029" vence e remunera igual para todo mundo.
  // Nulos para ações, FIIs e para os produtos genéricos de renda fixa (CDB,
  // LCI...), cujo prazo é de cada aplicação.
  //
  // Não há indexerRate aqui: a taxa é da NOTA do usuário, nunca do catálogo —
  // no Tesouro ela depende do dia da compra, num CDB depende do banco.
  maturityDate: string | null;
  rateType: RateType | null;
  indexer: Indexer | null;
  active: boolean;
}

export interface PortfolioSummary {
  id: string;
  name: string;
  totalInvested: number;
  currentValue: number;
  totalDividends: number;
  totalReturn: number;
  returnPercentage: number;
}

export interface PortfolioAssetDetail {
  id: string;
  ticker: string;
  assetName: string;
  typeName: string;
  categoryName: string;
  // Do título. A posição passou a ser por papel, então a carteira repete
  // ticker: três CDBs de emissores diferentes são três linhas, todas "CDB —
  // Certificado de Depósito Bancário". Emissor e vencimento são o que as
  // distingue. Nulos para renda variável.
  noteId: string | null;
  issuer: string | null;
  maturityDate: string | null;
  // Remuneração contratada na nota ("110% do CDI", "IPCA + 6%"). O sentido de
  // indexerRate depende de rateType. Nulos para renda variável.
  rateType: RateType | null;
  indexer: Indexer | null;
  indexerRate: number | null;
  totalShares: number;
  averagePrice: number;
  // Nos tipos por cota é o custo que ainda está na posição, já reduzido pelas
  // vendas. Na Renda Fixa é o VALOR APLICADO — a soma das compras —, e o que
  // saiu está em totalSold: as regras E e F comparam as duas pontas, e abater
  // uma da outra tornaria ambas indecidíveis. A tabela usa rótulos diferentes
  // por isso.
  totalInvested: number;
  totalSold: number;
  currentValue: number;
  currentPrice: number;
  totalDividends: number;
  valorization: number;
  returnPercentage: number;
  isReservation: boolean;
  /** Posição encerrada: renda fixa liquidada ou título sem cotas. */
  isSettled: boolean;
}

export interface PortfolioAllocation {
  typeName: string;
  colorHex: string;
  value: number;
  percentage: number;
}

export interface CategoryAllocation {
  categoryName: string;
  value: number;
  percentage: number;
}

export interface PortfolioAllocationDetailed {
  typeName: string;
  colorHex: string;
  totalValue: number;
  percentage: number;
  categories: CategoryAllocation[];
}

export interface PortfolioPerformance {
  totalInvested: number;
  currentValue: number;
  totalDividends: number;
  totalReturn: number;
  returnPercentage: number;
  assets: { ticker: string; invested: number; currentValue: number; returnPct: number }[];
}

export enum TransactionType {
  BUY = 'BUY',
  SELL = 'SELL'
}

export interface TransactionResponse {
  id: string;
  /** A posição a que a transação pertence. É por ela que a carteira filtra o extrato. */
  portfolioAssetId: string | null;
  ticker: string;
  assetName: string;
  // O id existe porque a edição reabre o formulário no mesmo estado, e o tipo
  // de ativo comanda esse formulário. Pelo nome descritivo a tela teria que
  // adivinhar o tipo casando strings.
  assetTypeId: number | null;
  assetTypeName: string;
  type: string;
  // Nulos em Renda Fixa, que é lançada por valor total.
  shares: number | null;
  pricePerShare: number | null;
  totalAmount: number;
  brokerageFee: number;
  transactionDate: string;
  createdAt: string;

  // A venda do usuário, quando ela foi repartida entre títulos (Tesouro
  // Direto). groupShares e groupBrokerageFee são o que ele digitou; shares e
  // brokerageFee são a parte desta linha. Editar e excluir agem sobre o grupo.
  saleGroupId: string | null;
  groupPart: number | null;
  groupParts: number | null;
  groupShares: number | null;
  groupBrokerageFee: number | null;

  /** Venda de Renda Fixa marcada como liquidação total. */
  isFullSettlement: boolean;

  // Do título. Nulos na renda variável.
  noteId: string | null;
  issuer: string | null;
  maturityDate: string | null;
  rateType: RateType | null;
  indexer: Indexer | null;
  indexerRate: number | null;
}

export interface TransactionCreate {
  ticker: string;
  type: string;
  // Renda Fixa manda só totalAmount, com shares e pricePerShare nulos. Os
  // demais tipos mandam shares e pricePerShare e a API calcula o total.
  shares: number | null;
  pricePerShare: number | null;
  totalAmount?: number | null;
  brokerageFee?: number;
  transactionDate: string;
  isReservation?: boolean;

  /**
   * Venda de Renda Fixa: encerra a aplicação. A valorização é ajustada para
   * que aplicado + valorização seja exatamente o resgatado. Só existe em venda
   * de Renda Fixa.
   */
  isFullSettlement?: boolean | null;

  // Dados do título (renda fixa). Viram uma linha em fixed_income_notes: dois
  // CDBs de bancos diferentes são duas notas, cada uma com seu emissor,
  // vencimento e taxa. Renda variável deixa tudo indefinido.
  //
  // noteId aponta para uma nota que já existe, para lançar mais de uma
  // movimentação sobre o mesmo papel. Ausente numa compra de renda fixa
  // significa "papel novo": a nota nasce junto com a transação.
  noteId?: string | null;
  issuer?: string | null;
  maturityDate?: string | null;
  rateType?: RateType | null;
  indexer?: Indexer | null;
  indexerRate?: number | null;
}

export interface Strategy {
  id: string;
  assetTypeId: number;
  assetTypeName: string;
  targetPercentage: number;
  includeReservation: boolean;
}

export interface StrategyUpdate {
  items: { assetTypeId: number; targetPercentage: number; includeReservation: boolean }[];
}

export interface RebalanceItem {
  typeName: string;
  idealPercentage: number;
  currentPercentage: number;
  diffPercentage: number;
  currentValue: number;
  idealValue: number;
  amountToRebalance: number;
}

export interface Rebalance {
  totalPortfolioValue: number;
  items: RebalanceItem[];
}

export interface CeilingPrice {
  id: string;
  ticker: string;
  assetName: string;
  assetTypeName: string;
  maxPrice: number;
  currentPrice: number;
  diffPercentage: number;
  alertEnabled: boolean;
  belowCeiling: boolean;
}

export interface CeilingPriceCreate {
  ticker: string;
  maxPrice: number;
  alertEnabled?: boolean;
}

export enum DividendType {
  DIVIDENDO = 'DIVIDENDO',
  JCP = 'JCP',
  RENDIMENTO = 'RENDIMENTO'
}

export interface DividendResponse {
  id: number;
  ticker: string;
  assetName: string;
  amountPerShare: number;
  totalAmount: number;
  exDate: string;
  paymentDate: string;
  type: string;
}

export interface DividendSummary {
  totalDividends: number;
  byType: Record<string, number>;
  recent: DividendResponse[];
}

export type GoalStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export interface GoalResponse {
  id: string;
  name: string;
  targetAmount: number;
  currentAmount: number;
  progressPercentage: number;
  targetDate: string;
  status: GoalStatus;
  createdAt: string;
}

export interface GoalCreate {
  name: string;
  targetAmount: number;
  targetDate?: string;
}

export interface GoalUpdate {
  name?: string;
  targetAmount?: number;
  currentAmount?: number;
  targetDate?: string;
  status?: GoalStatus;
}

export interface PriceHistory {
  date: string;
  open: number;
  close: number;
  high: number;
  low: number;
  volume: number;
}
