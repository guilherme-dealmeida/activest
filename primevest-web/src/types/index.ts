export interface User {
  id: string;
  name: string;
  email: string;
  avatarUrl: string;
  createdAt?: string;
}

/** Corpo de POST /auth/login (LoginRequestDTO). */
export interface LoginRequest {
  email: string;
  password: string;
}

/** Corpo de POST /auth/register (RegisterRequestDTO): senha com no mínimo 8 caracteres. */
export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
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
  // Data de compra do título. Só no Tesouro Direto, onde cada compra é um
  // título novo e a venda consome do mais antigo para o mais novo.
  purchaseDate: string | null;
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
  /** Criptomoeda: quantidade e preços com até 8 casas. */
  isCrypto: boolean;
  /**
   * Logo guardado pela rotina de logos (GET /assets/{ticker}/logo). null nos
   * tipos sem logo (renda fixa, Tesouro, fundos); false mostra as iniciais.
   */
  hasLogo: boolean | null;
  /**
   * Emissor do catálogo (companhia ou FII) — link para /emissoras/{ticker}.
   * null = sem emissor (renda fixa, ETF, BDR, cripto). Não confundir com
   * `issuer` da carteira, que é o emissor da NOTA de renda fixa.
   */
  issuerId: number | null;
}

/** Resposta de PATCH /portfolio/assets/{id}/reservation. */
export interface PortfolioAssetReservation {
  id: string;
  ticker: string;
  isReservation: boolean;
}

/**
 * GET /portfolio/allocation/reservation: a carteira de hoje dividida entre
 * reserva de emergência e geração de renda. Mesma base de valor do gráfico por
 * tipo (valor atual), para os dois totais baterem lado a lado.
 */
export interface ReservationAllocation {
  totalValue: number;
  reservationValue: number;
  reservationPercentage: number;
  incomeValue: number;
  incomePercentage: number;
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
  // A categoria do ativo, para o filtro da listagem. assetCategoryName já vem
  // como rótulo (getDisplayName), nunca a chave técnica. Nulos/vazios quando o
  // ativo não tem categoria — o vínculo é opcional no catálogo.
  assetCategoryId: number | null;
  assetCategoryName: string;
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
  /** Criptomoeda: quantidade e preço com até 8 casas. */
  isCrypto: boolean;
  /** Digitada (MANUAL) ou importada de uma corretora (AUTO). */
  origin: 'MANUAL' | 'AUTO';
  /** Importada que o usuário editou; null = idêntica à corretora. */
  editedAt: string | null;
  /** Apelido da conexão de onde veio ("Binance principal"); null nas digitadas. */
  connectionLabel: string | null;
  /** Corretora da conexão ("BINANCE"); null nas digitadas. */
  connectionProvider: string | null;

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
}

export interface StrategyUpdate {
  items: { assetTypeId: number; targetPercentage: number }[];
}

/**
 * Como a estratégia de reserva define o alvo. No banco é 1 e 2
 * (strategy_reservations.type_reservation); na API, o nome.
 */
export type ReservationType = 'PERCENTAGE' | 'VALUE';

/** GET/PUT /strategy/reservation. Espelha StrategyReservationDTO. */
export interface StrategyReservation {
  id: string;
  typeReservation: ReservationType;
  /** Alvo do tipo PERCENTAGE; null no VALUE. */
  targetPercentage: number | null;
  /** Alvo do tipo VALUE; null no PERCENTAGE. */
  targetValue: number | null;
  /** A carteira inteira: reserva + geração de renda. */
  totalPortfolioValue: number;
  reservationValue: number;
  incomeValue: number;
  currentPercentage: number;
  /** No PERCENTAGE é o alvo; no VALUE, alvo ÷ carteira, limitado a 100%. */
  idealPercentage: number;
  /** Atual − ideal: negativo é abaixo do alvo. */
  diffPercentage: number;
}

export interface StrategyReservationUpdate {
  typeReservation: ReservationType;
  targetPercentage: number | null;
  targetValue: number | null;
}

/** Linha "Rebalanceamento Reserva de Emergência". Espelha RebalanceDTO.ReservationRebalance. */
export interface ReservationRebalance {
  typeReservation: ReservationType;
  /** null no tipo VALUE: a tela mostra "—". */
  idealPercentage: number | null;
  /** null no tipo PERCENTAGE. */
  targetValue: number | null;
  /** Aportado: o que a reserva vale hoje. */
  currentValue: number;
  /** Aportar. Negativo quando a reserva já passou do alvo. */
  amountToRebalance: number;
  /** Estratégico: a reserva depois do aporte sugerido. */
  idealValue: number;
  currentPercentage: number;
  diffPercentage: number;
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

/**
 * totalPortfolioValue e items são a geração de renda: posição de reserva não
 * entra no rebalanceamento por tipo. A reserva tem a sua linha em reservation.
 */
export interface Rebalance {
  totalPortfolioValue: number;
  items: RebalanceItem[];
  reservation: ReservationRebalance;
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
  /**
   * Emissor do catálogo (companhia ou FII) — link para /emissoras/{ticker}.
   * null = sem emissor (renda fixa, ETF, BDR, cripto). Não confundir com
   * `issuer` da carteira, que é o emissor da NOTA de renda fixa.
   */
  issuerId: number | null;
}

export interface CeilingPriceCreate {
  ticker: string;
  maxPrice: number;
  alertEnabled?: boolean;
}

export enum DividendType {
  DIVIDENDO = 'DIVIDENDO',
  JCP = 'JCP',
  RENDIMENTO = 'RENDIMENTO',
  // Devolvem capital: além de provento, reduzem o custo da posição (B13).
  AMORTIZACAO = 'AMORTIZACAO',
  RESTITUICAO_CAPITAL = 'RESTITUICAO_CAPITAL'
}

/** Rótulo de cada tipo de provento para a tela. */
export const DIVIDEND_TYPE_LABELS: Record<DividendType, string> = {
  [DividendType.DIVIDENDO]: 'Dividendo',
  [DividendType.JCP]: 'JCP',
  [DividendType.RENDIMENTO]: 'Rendimento',
  [DividendType.AMORTIZACAO]: 'Amortização',
  [DividendType.RESTITUICAO_CAPITAL]: 'Restituição de capital',
};

export interface DividendResponse {
  id: number;
  ticker: string;
  assetName: string;
  amountPerShare: number;
  /** Total BRUTO, truncado nos centavos. */
  totalAmount: number;
  exDate: string;
  /** Nula quando a fonte não informa (histórico longo da B3 — PENDENCIAS.txt, 3.4). */
  paymentDate: string | null;
  type: DividendType;
  /** Cotas da posição na data-com. Nula no provento MANUAL antigo. */
  sharesOnDate: number | null;
  // IR retido na fonte (PLANO_IR_PROVENTOS.md). Todos nulos = sem regra de IR
  // para o tipo (ex.: ETF); aí o total que vale é o bruto (totalAmount).
  /** Alíquota aplicada: 0.175 = 17,5%; 0 = isento. */
  withholdingRate: number | null;
  /** IR retido, truncado nos centavos. */
  withholdingTax: number | null;
  /** Líquido por cota, 8 casas — só exibição. */
  netAmountPerShare: number | null;
  /** TOTAL líquido = bruto − IR (conta da B3). */
  netAmount: number | null;
  /** Base legal da regra aplicada. */
  withholdingLegalBasis: string | null;
  /**
   * Já recebido hoje (fuso da B3)? false = a receber. Calculado no backend
   * (Dividend.isReceived): pagamento até hoje; sem data, conforme a origem.
   */
  received: boolean;
  /** Fonte do evento: B3, B3_HISTORICO, FNET ou EXTRATO_B3 (data-com deduzida). */
  cashEventSource: string | null;
  /** Origem da data de pagamento quando não é a da fonte: EXTRATO_B3 ou DEDUZIDA. */
  paymentDateSource: string | null;
  assetTypeId: number | null;
  /** Descrição do tipo de ativo (AssetType.getDescription). */
  assetTypeDescription: string | null;
  /** Mesma regra da carteira: null = tipo sem logo; false = iniciais. */
  hasLogo: boolean | null;
  /**
   * Emissor do catálogo (companhia ou FII) — link para /emissoras/{ticker}.
   * null = sem emissor (renda fixa, ETF, BDR, cripto). Não confundir com
   * `issuer` da carteira, que é o emissor da NOTA de renda fixa.
   */
  issuerId: number | null;
}

export interface DividendSummary {
  /** Total líquido RECEBIDO (bruto onde não há regra de IR). */
  totalDividends: number;
  /** Líquido recebido por tipo. */
  byType: Record<string, number>;
  recent: DividendResponse[];
  totalGross: number;
  totalTax: number;
  /** A receber: pagamento depois de hoje. */
  totalToReceive: number;
  toReceiveGross: number;
  toReceiveTax: number;
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

// ---------------------------------------------------------------------------
// Integrações com corretoras (PLANO_CRIPTO_BINANCE.md, F6)
// ---------------------------------------------------------------------------

export type IntegrationProvider = 'BINANCE';
export type ConnectionStatus = 'ACTIVE' | 'INVALID_CREDENTIALS' | 'PERMISSION_TOO_BROAD' | 'REVOKED';
export type RunStatus = 'QUEUED' | 'RUNNING' | 'AWAITING_CONFIRMATION' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
export type RunTrigger = 'MANUAL' | 'SCHEDULED' | 'CONNECTION';
export type PreviewOutcome = 'NEW' | 'EXISTING' | 'EXCLUDED' | 'UNMAPPED' | 'POSSIBLE_DUPLICATE';
export type RunItemResolution = 'IMPORTED' | 'REPLACED' | 'KEPT_BOTH' | 'SKIPPED';
export type MovementKind = 'TRADE' | 'CONVERT' | 'FIAT_DEPOSIT' | 'CRYPTO_DEPOSIT' | 'CRYPTO_WITHDRAW' | 'REWARD';
export type DuplicateDecision = 'REPLACE' | 'KEEP_BOTH' | 'SKIP';

export interface IntegrationConnection {
  id: string;
  provider: IntegrationProvider;
  label: string;
  status: ConnectionStatus;
  /** Os 4 últimos caracteres da chave; o segredo nunca volta da API. */
  fingerprint: string | null;
  validatedAt: string | null;
  lastSyncAt: string | null;
  lastError: string | null;
  createdAt: string;
  lastRunId: string | null;
  lastRunStatus: RunStatus | null;
  /** Ativos que divergem da corretora na última sincronização concluída. */
  divergenceCount: number;
}

export interface ConnectionCreate {
  provider: IntegrationProvider;
  label: string;
  apiKey: string;
  apiSecret: string;
  consentAccepted: boolean;
}

export interface ProposedImport {
  ticker: string;
  type: 'BUY' | 'SELL';
  shares: number;
  pricePerShare: number;
  totalAmount: number;
  brokerageFee: number;
  transactionDate: string;
  roundingDifference: number;
}

export interface DuplicateCandidate {
  transactionId: string;
  transactionDate: string;
  shares: number | null;
  pricePerShare: number | null;
}

export interface ImportRunItem {
  externalId: string;
  kind: MovementKind;
  outcome: PreviewOutcome;
  reason: string | null;
  executedAt: string;
  asset: string | null;
  side: 'BUY' | 'SELL' | null;
  grossQuantity: number | null;
  quoteAsset: string | null;
  quoteAmount: number | null;
  feeAmount: number | null;
  feeAsset: string | null;
  transaction: ProposedImport | null;
  duplicateOf: DuplicateCandidate | null;
  resolution: RunItemResolution | null;
  transactionId: string | null;
}

export interface Divergence {
  asset: string;
  primevest: number;
  exchange: number;
  difference: number;
}

export interface ImportRun {
  connectionId: string;
  connectionLabel: string;
  runId: string;
  createdAt: string;
  finishedAt: string | null;
  status: RunStatus;
  trigger: RunTrigger;
  newCount: number;
  existingCount: number;
  excludedCount: number;
  unmappedCount: number;
  duplicateCount: number;
  fiatDeposited: number | null;
  error: string | null;
  divergences: Divergence[];
  items: ImportRunItem[];
}

export interface RunSummary {
  runId: string;
  status: RunStatus;
  trigger: RunTrigger;
  createdAt: string;
  finishedAt: string | null;
  importedCount: number;
  existingCount: number;
  unmappedCount: number;
  duplicateCount: number;
  error: string | null;
}

export interface CryptoExclusion {
  id: string;
  externalId: string;
  ticker: string;
  excludedAt: string;
  executedAt: string | null;
  side: 'BUY' | 'SELL' | null;
  grossQuantity: number | null;
  quoteAmount: number | null;
}

// ---------------------------------------------------------------------------
// Tela de emissoras (PLANO_TELA_EMISSORES.md)
// ---------------------------------------------------------------------------

export type IssuerKind = 'COMPANY' | 'FII';

export type IndicatorCode =
  | 'LPA' | 'VPA' | 'PL' | 'PVP' | 'ROE' | 'PAYOUT' | 'DY'
  | 'VP_COTA' | 'COTISTAS' | 'IMOVEIS' | 'PAPEL' | 'ALAVANCAGEM';

/** PERCENT em pontos (37.44 = 37,44%); BRL e RATIO numéricos; COUNT inteiro. */
export type IndicatorUnit = 'PERCENT' | 'RATIO' | 'BRL' | 'COUNT';

export type IndicatorPeriod = 'YEAR' | 'QUARTER';

/** Um indicador atual. value null = não se aplica, com o motivo em note. */
export interface IndicatorItem {
  indicator: IndicatorCode;
  unit: IndicatorUnit;
  value: number | null;
  note: string | null;
}

/** GET /assets/{ticker}/indicators */
export interface AssetIndicators {
  ticker: string;
  issuerId: number;
  kind: IssuerKind;
  price: number | null;
  /** Data do balanço (ação) ou fim do mês do informe (FII) usado no Atual. */
  basePeriodEnd: string | null;
  indicators: IndicatorItem[];
}

export interface IndicatorPoint {
  /** "2025", "2T26" ou "Atual". */
  label: string;
  periodEnd: string | null;
  value: number | null;
  note: string | null;
}

/** GET /assets/{ticker}/indicators/{indicador}/history e /indicators/history (lista). */
export interface IndicatorHistory {
  ticker: string;
  indicator: IndicatorCode;
  unit: IndicatorUnit;
  period: IndicatorPeriod;
  current: IndicatorPoint;
  /** Média dos pontos anuais + Atual (nulos fora, zeros dentro). */
  companyAverage: number | null;
  points: IndicatorPoint[];
}

export interface IssuerTicker {
  ticker: string;
  shareClass: string | null;
  active: boolean;
  hasLogo: boolean;
}

/** GET /issuers/{id} */
export interface Issuer {
  id: number;
  kind: IssuerKind;
  cnpj: string;
  cvmCode: number | null;
  legalName: string | null;
  tradeName: string | null;
  /** ATIVO / CANCELADA (CVM). */
  registrationStatus: string | null;
  website: string | null;
  b3Sector: string | null;
  b3Subsector: string | null;
  b3Segment: string | null;
  /** Segmento curado (FII). */
  segment: string | null;
  listingSegment: string | null;
  fiscalYearEndMonth: number | null;
  administratorName: string | null;
  tickers: IssuerTicker[];
}

/** GET /issuers/{id}/quarters — _q trimestre isolado, _ttm 12 meses; o resto é posição. */
export interface CompanyQuarter {
  periodEnd: string;
  fiscalYearStart: string | null;
  revenueQ: number | null;
  ebitQ: number | null;
  netIncomeQ: number | null;
  netIncomeParentQ: number | null;
  fcoQ: number | null;
  dividendsDeclaredQ: number | null;
  revenueTtm: number | null;
  ebitTtm: number | null;
  netIncomeTtm: number | null;
  netIncomeParentTtm: number | null;
  fcoTtm: number | null;
  dividendsDeclaredTtm: number | null;
  equity: number | null;
  equityParent: number | null;
  totalAssets: number | null;
  cash: number | null;
  grossDebt: number | null;
  sharesTotal: number | null;
  sharesOutstanding: number | null;
}

/** GET /issuers/{id}/months — taxas em fração do mês (0.0068 = 0,68%). */
export interface FiiMonth {
  referenceMonth: string;
  version: number | null;
  netAssets: number | null;
  totalAssets: number | null;
  sharesIssued: number | null;
  navPerShare: number | null;
  shareholders: number | null;
  adminFeeMonth: number | null;
  dividendYieldMonth: number | null;
  amortizationMonth: number | null;
  liquidityTotal: number | null;
  totalInvested: number | null;
  realEstateTotal: number | null;
  realEstateCompanies: number | null;
  receivablesTotal: number | null;
  fiiSharesTotal: number | null;
  liabilitiesTotal: number | null;
  incomeToDistribute: number | null;
}

/** Atalhos do gráfico de cotação; 1A é o padrão. */
export type PriceRange = '1D' | '7D' | '1M' | '6M' | 'YTD' | '1A' | '5A' | 'MAX';

/** GET /assets/{ticker}/prices — ajustada por desdobramento, grupamento e bonificação. */
export interface PriceSeries {
  ticker: string;
  /** Ticker de onde vem o preço (o lote padrão, no fracionário). */
  priceTicker: string;
  firstDate: string | null;
  lastDate: string | null;
  from: string | null;
  to: string | null;
  range: PriceRange | 'CUSTOM';
  interval: 'INTRADAY' | 'DAY' | 'WEEK' | 'MONTH';
  adjusted: boolean;
  /** Só fechamento diário (sem preço durante o pregão). */
  closeOnly: boolean;
  change: number | null;
  /** Em pontos (1.03 = 1,03%). */
  changePct: number | null;
  min: number | null;
  max: number | null;
  points: { time: string; close: number }[];
}
