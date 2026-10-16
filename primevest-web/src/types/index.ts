/** GET /users/me (UserResponseDTO). O CPF só vem mascarado. */
export interface User {
  id: string;
  name: string;
  email: string;
  /** Foto do Google (da criação da conta). */
  avatarUrl?: string;
  /** Versão da foto enviada (epoch ms); ausente = sem foto enviada. Tem precedência sobre a do Google. */
  avatarVersion?: number;
  /** E.164 (+5541999999999). */
  phone?: string;
  /** "***.456.789-**". */
  cpfMasked?: string;
  emailVerified: boolean;
  hasPassword: boolean;
  googleLinked: boolean;
  admin: boolean;
  theme: 'DARK' | 'LIGHT';
  termsVersion?: string;
  termsAcceptedAt?: string;
  currentTermsVersion: string;
  /** A versão aceita não é a atual: abre o modal de novo aceite (D19). */
  termsPending: boolean;
  createdAt?: string;
}

/** Corpo de POST /auth/login (LoginRequestDTO). */
export interface LoginRequest {
  email: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

/** Corpo de POST /auth/signup/start (SignupStartDTO). Celular e CPF opcionais. */
export interface SignupRequest {
  name: string;
  email: string;
  phone?: string;
  cpf?: string;
  password: string;
  acceptTerms: boolean;
}

/** Resposta de todo pedido que manda código por e-mail (CodeSentDTO). */
export interface CodeSent {
  requestId: string;
  maskedEmail: string;
  resendInSeconds: number;
  expiresInSeconds: number;
}

export interface SignupStatus {
  enabled: boolean;
  termsVersion: string;
}

/** PUT /users/me: cpf ausente = não mexe; '' = tira. */
export interface UserUpdateRequest {
  name: string;
  phone?: string | null;
  cpf?: string | null;
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
   * Base do valor atual na renda fixa (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md): CURVA (taxa contratada),
   * MERCADO (preço oficial do Tesouro) ou SEM_CURVA. null fora da renda fixa.
   */
  valuationBasis?: 'CURVA' | 'MERCADO' | 'SEM_CURVA' | null;
  /** Até quando o valor vale: último CDI (curva) ou dia do preço do Tesouro. */
  valuationDate?: string | null;
  /** Motivo de não ter curva, ou a observação da inflação estimada. */
  valuationNote?: string | null;
  /** Passou do vencimento. */
  matured?: boolean;
  /** Bonificações sem custo atribuído pela B3 que pegam a posição: ícone no preço médio e "Informar custo". */
  pendingBonusCosts?: PendingBonusCost[];
  /**
   * Emissor do catálogo (companhia ou FII) — link para /emissoras/{ticker}.
   * null = sem emissor (renda fixa, ETF, BDR, cripto). Não confundir com
   * `issuer` da carteira, que é o emissor da NOTA de renda fixa.
   */
  issuerId: number | null;
  /**
   * Líquido estimado se resgatasse na data do valor: valor atual (bruto) − IOF − IR, aporte a aporte
   * (docs/planos/PLANO_VENDA_RENDA_FIXA.md, R11). null fora da renda fixa ou sem valor.
   */
  estimatedNet?: number | null;
  /** O título aporte a aporte (renda fixa privada) ou o próprio título (Tesouro). Vazia fora da renda fixa. */
  lots?: FixedIncomeLot[];
  /** Emissora e distribuidora do título (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md). null fora da renda fixa. */
  noteInfo?: NoteInfo | null;
  /** Só na tela: noteInfo.distributorName, para a coluna DISTRIBUIDORA ordenar pelo cabeçalho. */
  distributorName?: string | null;
}

/**
 * Emissora e distribuidora de um título (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, R10–R12): nome de exibição (o
 * do grupo: "Nubank"), razão social para a dica, regra de saída da distribuidora, código do papel, logo do grupo da
 * emissora e o fundo que garante (FGC/FGCOOP; null = sem garantia).
 */
export interface NoteInfo {
  issuerId: number | null;
  issuerName: string | null;
  issuerLegalName: string | null;
  issuerKind: string | null;
  distributorId: number | null;
  distributorName: string | null;
  distributorLegalName: string | null;
  distributorSaleRule: 'PROPORCIONAL' | 'UEPS' | 'PEPS' | null;
  paperCode: string | null;
  issuerLogoTicker: string | null;
  guarantee: 'FGC' | 'FGCOOP' | null;
}

/** Empresa do seletor de emissora/distribuidora (GET /fixed-income-issuers). */
export interface FixedIncomeIssuerOption {
  id: number;
  kind: string;
  /** "Grupo — razão social" */
  label: string;
  /** O nome das colunas (o do grupo). */
  displayName: string;
  legalName: string;
  groupName: string | null;
  status: string | null;
  /** Inativa, cancelada, liquidada: continua escolhível (lançamento retroativo). */
  dead: boolean;
  guarantor: 'FGC' | 'FGCOOP' | 'NENHUM' | null;
  saleRule: 'PROPORCIONAL' | 'UEPS' | 'PEPS' | null;
  cnpjRoot: string | null;
  logoTicker: string | null;
}

/** Troca de distribuidora de uma compra de Tesouro com venda ligada (plano de emissoras, 5.1). */
export interface DistributorChangeItem {
  type: 'BUY' | 'SELL';
  /** Compra: id da transação; venda: id do grupo da venda. */
  id: string;
  date: string;
  shares: number | null;
  pricePerShare: number | null;
  totalAmount: number | null;
  /** A compra editada: vai sempre. */
  locked: boolean;
}

export interface DistributorChangePreview {
  fromId: number | null;
  fromName: string | null;
  toId: number;
  toName: string;
  ticker: string;
  items: DistributorChangeItem[];
}

/** Um aporte ainda aplicado, com os impostos de um resgate na data do valor. */
export interface FixedIncomeLot {
  date: string;
  /** Dias corridos do aporte até a data do valor. */
  days: number;
  /** Aplicado que ainda está no lote. */
  applied: number | null;
  /** Saldo bruto do lote. */
  balance: number;
  income: number;
  iofRate: number;
  irRate: number;
  iof: number;
  ir: number;
  net: number;
}

/**
 * POST /transactions/sale-preview: a venda de renda fixa (pelo líquido) ou de Tesouro (cotas × PU) antes de gravar.
 * Renda fixa: bruto calculado pelos lotes, IR, IOF, o líquido disponível e se o valor passa dele (R9).
 */
export interface SalePreview {
  gross: number;
  iof: number;
  ir: number;
  net: number;
  /** Ajuste da liquidação total (R8): diferença entre o informado e a curva, somada ao bruto. */
  adjustment: number | null;
  /** Renda fixa: o líquido que dá para resgatar na data. null no Tesouro. */
  availableNet: number | null;
  /** Renda fixa: o saldo bruto na data, antes da venda. */
  grossBefore: number | null;
  /** O líquido informado passa do disponível (R9). */
  aboveAvailable: boolean;
  /** PROPORCIONAL, UEPS ou PEPS. */
  saleRule: string;
  status: string;
  reason: string | null;
  /** Renda fixa: os lotes na data, antes da venda. Tesouro: as partes da venda, por título. */
  lots: FixedIncomeLot[];
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
  /**
   * Digitada (MANUAL), importada de uma corretora (AUTO) ou criada pelo PrimeVest (SISTEMA: a liquidação automática
   * do título vencido, docs/planos/PLANO_NOTIFICACOES.md, seção 6).
   */
  origin: 'MANUAL' | 'AUTO' | 'SISTEMA';
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

  /** Coluna ATIVO, igual à carteira. null = tipo sem logo (renda fixa, Tesouro). */
  hasLogo: boolean | null;
  /** Com emissor, o ticker vira link para a tela de emissoras. */
  issuerId: number | null;

  // Venda de Renda Fixa e de Tesouro (docs/planos/PLANO_VENDA_RENDA_FIXA.md): totalAmount é o BRUTO; netAmount é o
  // que caiu na conta. Na renda fixa o usuário digita o líquido e o PrimeVest calcula bruto, IR e IOF; no Tesouro
  // calcula IR e IOF por título. null nas compras e nos demais tipos.
  netAmount?: number | null;
  incomeTax?: number | null;
  iofTax?: number | null;
  /** Liquidação total: diferença entre o recebido e a curva, já somada ao bruto (R8). */
  settlementAdjustment?: number | null;
  /** Emissora e distribuidora do título (plano de emissoras). `issuer` acima é o nome de exibição da emissora. */
  noteInfo?: NoteInfo | null;
}

/**
 * Um título de renda fixa do usuário, no seletor "Título" da Nova Transação.
 * Todos os títulos do ativo, inclusive liquidados e vencidos (lançamento
 * retroativo); quem recusa o aporte fora de prazo é a data.
 */
export interface NoteOption {
  noteId: string;
  issuer: string | null;
  maturityDate: string | null;
  rateType: RateType | null;
  indexer: Indexer | null;
  indexerRate: number | null;
  settled: boolean;
  matured: boolean;
  noteInfo?: NoteInfo | null;
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
  /** Emissora (fixed_income_issuers), de um tipo aceito pelo produto. Ignorada no Tesouro (Tesouro Nacional). */
  issuerId?: number | null;
  maturityDate?: string | null;
  rateType?: RateType | null;
  indexer?: Indexer | null;
  indexerRate?: number | null;
  /** Distribuidora (onde foi comprado): título novo de RF e compra e venda do Tesouro. */
  distributorId?: number | null;
  /** Código do papel na B3 (ex.: CDBA2478FYE), opcional. */
  paperCode?: string | null;
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

// ---------------------------------------------------------------- Preço Teto (docs/planos/PLANO_PRECO_TETO.md)

export type CeilingIssueLevel = 'BLOQUEIO' | 'ATENCAO' | 'INFORMACAO';

/** Aviso do cálculo: código estável, nível, campo afetado (null = geral) e mensagem. */
export interface CeilingIssue {
  code: string;
  level: CeilingIssueLevel;
  field: string | null;
  message: string;
}

export type CeilingUnit = 'UNIDADE' | 'MIL' | 'MILHAO' | 'BILHAO';
export type CeilingCurrency = 'BRL' | 'USD';

export interface CeilingQuarterInput {
  periodEnd: string;
  originalValue: number | null;
  unit: CeilingUnit;
  currency: CeilingCurrency;
  fxRate: number | null;
}

/** Entradas do formulário; percentuais em pontos (13,75). */
export interface CeilingPriceInput {
  ticker: string;
  quarters: CeilingQuarterInput[];
  shares: number | null;
  roePct: number | null;
  payoutPct: number | null;
  discountRatePct: number | null;
  perpetualGrowthPct: number | null;
  safetyMarginPct: number | null;
  price: number | null;
  notes: string | null;
  confirmWarnings: boolean;
}

export interface CeilingDefaultValue {
  value: number | null;
  /** De onde vem o padrão ("Selic meta vigente em 10/10/2026"). */
  ref: string | null;
  /** Motivo de não haver padrão, ou observação (média incompleta). */
  note: string | null;
}

export interface CeilingDefaultQuarter {
  periodEnd: string;
  label: string;
  value: number | null;
  ref: string | null;
  note: string | null;
}

export interface CeilingDefaults {
  ticker: string;
  assetName: string;
  issuerId: number | null;
  hasLogo: boolean | null;
  existingId: string | null;
  quarters: CeilingDefaultQuarter[];
  shares: CeilingDefaultValue;
  roePct: CeilingDefaultValue;
  payoutPct: CeilingDefaultValue;
  discountRatePct: CeilingDefaultValue;
  perpetualGrowthPct: CeilingDefaultValue;
  safetyMarginPct: CeilingDefaultValue;
  price: CeilingDefaultValue;
  priceDate: string | null;
  latestQuarterEnd: string | null;
  equityParent: number | null;
  catalogTtm: number | null;
  roeYears: string[];
  payoutYears: string[];
}

export interface CeilingStep {
  key: string;
  label: string;
  value: number | null;
  formula: string;
  substituted: string;
}

/** Resultado do modelo: R$ por ação com 4 casas, totais com 2, taxas em pontos. null = não calculado. */
export interface CeilingResult {
  netIncomeTtm: number | null;
  growthPct: number | null;
  fc1: number | null;
  pv1: number | null;
  fc2: number | null;
  pv2: number | null;
  fc3: number | null;
  pv3: number | null;
  terminalValue: number | null;
  terminalPv: number | null;
  equityValue: number | null;
  terminalWeightPct: number | null;
  ceilingPrice: number | null;
  idealPrice: number | null;
  ceilingMarginPct: number | null;
  idealMarginPct: number | null;
  steps: CeilingStep[];
  issues: CeilingIssue[];
}

export interface CeilingInputOrigin {
  origin: 'PADRAO' | 'INFORMADO';
  ref: string | null;
  defaultValue: number | null;
}

export interface CeilingQuarterView {
  position: number;
  periodEnd: string;
  label: string;
  originalValue: number;
  unit: CeilingUnit;
  currency: CeilingCurrency;
  fxRate: number;
  valueBrl: number;
  origin: 'PADRAO' | 'INFORMADO';
}

export interface CeilingPriceDetail {
  id: string;
  ticker: string;
  assetName: string;
  issuerId: number | null;
  hasLogo: boolean | null;
  quarters: CeilingQuarterView[];
  shares: number;
  roePct: number;
  payoutPct: number;
  discountRatePct: number;
  perpetualGrowthPct: number;
  safetyMarginPct: number;
  price: number;
  notes: string | null;
  origins: Record<string, CeilingInputOrigin>;
  calculatedAt: string;
  result: CeilingResult;
  defaults: CeilingDefaults;
  /** Campos cujo padrão mudou desde o "Salvar" (o ↻ fica destacado); "quarters" = trimestres. */
  changedDefaults: string[];
}

export type CeilingStatus = 'ABAIXO_DO_IDEAL' | 'ENTRE_IDEAL_E_TETO' | 'ACIMA_DO_TETO';

/** Linha da lista: resultado salvo e três comparações (preço do cálculo, fechamento de hoje, preço médio). */
export interface CeilingPriceListItem {
  id: string;
  ticker: string;
  assetName: string;
  /** Emissor do catálogo — link para /emissoras/{ticker}. */
  issuerId: number | null;
  hasLogo: boolean | null;
  ceilingPrice: number | null;
  idealPrice: number | null;
  /** Margem de segurança (pontos) usada no Preço Ideal. */
  safetyMarginPct: number;
  calcPrice: number;
  calcCeilingMarginPct: number | null;
  calcIdealMarginPct: number | null;
  todayPrice: number | null;
  todayPriceDate: string | null;
  todayCeilingMarginPct: number | null;
  todayIdealMarginPct: number | null;
  averagePrice: number | null;
  averageCeilingMarginPct: number | null;
  averageIdealMarginPct: number | null;
  status: CeilingStatus;
  blockingCount: number;
  warningCount: number;
  defaultsChanged: boolean;
  lastQuarterEnd: string | null;
  calculatedAt: string;
  /** "Alertar preço abaixo do ideal" ligado (docs/planos/PLANO_NOTIFICACOES.md, 5.5). */
  alertEnabled: boolean;
}

/** Recusa ao salvar (422). */
export interface CeilingRejection {
  message: string;
  issues: CeilingIssue[];
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
  /** Nula quando a fonte não informa (histórico longo da B3 — docs/PENDENCIAS.md, 3.4). */
  paymentDate: string | null;
  type: DividendType;
  /** Cotas da posição na data-com. Nula no provento MANUAL antigo. */
  sharesOnDate: number | null;
  // IR retido na fonte (docs/planos/PLANO_IR_PROVENTOS.md). Todos nulos = sem regra de IR
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
  /** Fonte do evento: B3, B3_HISTORICO ou FNET. */
  cashEventSource: string | null;
  /** Origem da data de pagamento quando não é a da fonte: CVM (relatório de proventos) ou LEVANTAMENTO. */
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
// Integrações com corretoras (docs/planos/PLANO_CRIPTO_BINANCE.md, F6; B3: docs/planos/PLANO_INTEGRACAO_B3.md, F6)
// ---------------------------------------------------------------------------

export type IntegrationProvider = 'BINANCE' | 'B3';
export type ConnectionStatus = 'ACTIVE' | 'PENDING_AUTHORIZATION' | 'INVALID_CREDENTIALS' | 'PERMISSION_TOO_BROAD'
  | 'REVOKED';
export type RunStatus = 'QUEUED' | 'RUNNING' | 'AWAITING_CONFIRMATION' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
export type RunTrigger = 'MANUAL' | 'SCHEDULED' | 'CONNECTION';
export type PreviewOutcome = 'NEW' | 'EXISTING' | 'EXCLUDED' | 'UNMAPPED' | 'POSSIBLE_DUPLICATE';
export type RunItemResolution = 'IMPORTED' | 'REPLACED' | 'KEPT_BOTH' | 'SKIPPED';
export type MovementKind = 'TRADE' | 'CONVERT' | 'FIAT_DEPOSIT' | 'CRYPTO_DEPOSIT' | 'CRYPTO_WITHDRAW' | 'REWARD'
  | 'EQUITY_TRADE' | 'SETTLEMENT' | 'STOCK_EVENT' | 'CASH_EVENT' | 'RIGHTS' | 'CUSTODY_TRANSFER' | 'LENDING'
  | 'FIXED_INCOME' | 'OTHER';
export type DuplicateDecision = 'REPLACE' | 'KEEP_BOTH' | 'SKIP';

export interface IntegrationConnection {
  id: string;
  provider: IntegrationProvider;
  label: string;
  status: ConnectionStatus;
  /** Os 4 últimos caracteres da chave (B3: os 2 últimos dígitos do CPF); o segredo nunca volta da API. */
  fingerprint: string | null;
  validatedAt: string | null;
  lastSyncAt: string | null;
  lastError: string | null;
  createdAt: string;
  lastRunId: string | null;
  lastRunStatus: RunStatus | null;
  /** Ativos que divergem da corretora na última sincronização concluída. */
  divergenceCount: number;
  /** Onde autorizar o PrimeVest na fonte, enquanto aguarda (B3: link de opt-in). */
  authorizationUrl: string | null;
}

/** Uma fonte ligada no servidor e o que ela pede para conectar. */
export interface IntegrationSource {
  provider: IntegrationProvider;
  /** API_KEY (chave e segredo, Binance) ou DOCUMENT (CPF, B3). */
  credential: 'API_KEY' | 'DOCUMENT';
  /** A fonte aceita uma consulta por dia (B3). */
  oncePerDay: boolean;
}

export interface ConnectionCreate {
  provider: IntegrationProvider;
  label: string;
  apiKey?: string;
  apiSecret?: string;
  documentNumber?: string;
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
// Tela de emissoras (docs/planos/PLANO_TELA_EMISSORES.md)
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
  dividendsPaidQ: number | null;
  revenueTtm: number | null;
  ebitTtm: number | null;
  netIncomeTtm: number | null;
  netIncomeParentTtm: number | null;
  fcoTtm: number | null;
  dividendsDeclaredTtm: number | null;
  dividendsPaidTtm: number | null;
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

// ---------------------------------------------------------------------------
// Índices de mercado (docs/planos/PLANO_INDICES_MERCADO.md)
// ---------------------------------------------------------------------------

export type IndexCode = 'SELIC' | 'CDI' | 'IPCA' | 'IBOV' | 'IFIX' | 'IGPM' | 'INPC' | 'TR' | 'SELIC_META' | 'USD_PTAX';
export type IndexView = 'MENSAL' | 'ANUAL';
export type IndexUnit = 'PCT_DIA' | 'PCT_MES' | 'PCT_PERIODO' | 'PCT_ANO' | 'PONTOS' | 'BRL';

/** GET /indices */
export interface MarketIndex {
  code: IndexCode;
  name: string;
  periodicity: 'DIARIA' | 'MENSAL';
  unit: IndexUnit;
  source: 'BCB_SGS' | 'B3';
  accumulates: boolean;
  firstDate?: string;
  lastDate?: string;
  lastValue?: number;
}

/**
 * GET /indices/{code}/chart — um card da tela Índices. Valores em `displayUnit` (CDI e Selic anualizados);
 * índice de taxa traz `accumulatedPct`, de nível `change`/`changePct`.
 */
export interface IndexChart {
  code: IndexCode;
  name: string;
  periodicity: 'DIARIA' | 'MENSAL';
  unit: IndexUnit;
  displayUnit: string;
  source: 'BCB_SGS' | 'B3';
  firstDate?: string;
  lastDate?: string;
  from?: string;
  to?: string;
  range: PriceRange | 'CUSTOM';
  interval: 'DAY' | 'WEEK' | 'MONTH';
  /** Só nos mensais: variação do mês ou acumulado em 12 meses (a.a.). */
  view?: IndexView;
  last?: number;
  min?: number;
  max?: number;
  change?: number;
  changePct?: number;
  accumulatedPct?: number;
  points: { date: string; value: number }[];
}

// ------------------------------------------------------------------ notificações (docs/planos/PLANO_NOTIFICACOES.md)

export type NotificationAudience = 'USER' | 'ADMIN';
export type NotificationCategory =
  | 'PROVENTOS' | 'EVENTOS' | 'RENDA_FIXA' | 'PRECO_TETO' | 'INTEGRACOES' | 'CARTEIRA' | 'SISTEMA';
/** Situação do assunto (pendência). Mensagem informativa vem sem status. */
export type NotificationStatus = 'NAO_RESOLVIDA' | 'DISPENSADA' | 'RESOLVIDA';
export type NotificationStateAction = 'READ' | 'UNREAD' | 'ARCHIVE' | 'UNARCHIVE' | 'DELETE';

/** Botão da mensagem: LINK vai para `target`; os demais são ações que a tela conhece (ex.: INFORMAR_CUSTO). */
export interface NotificationAction {
  type: 'LINK' | 'INFORMAR_CUSTO' | string;
  label: string;
  target?: string | null;
  params?: Record<string, unknown>;
}

export interface NotificationItem {
  id: string;
  audience: NotificationAudience;
  kind: string;
  category: NotificationCategory;
  severity: 'INFO' | 'ATENCAO';
  title: string;
  body: string;
  refType?: string | null;
  refId?: string | null;
  payload?: { lines?: string[]; actions?: NotificationAction[]; data?: Record<string, unknown> } | null;
  status?: NotificationStatus | null;
  statusAt?: string | null;
  read: boolean;
  archived: boolean;
  createdAt: string;
}

export interface NotificationPage {
  items: NotificationItem[];
  page: number;
  size: number;
  total: number;
}

export interface NotificationSummary {
  unread: number;
  adminUnread: number;
}

/** Bonificação sem custo da B3 numa posição (docs/planos/PLANO_NOTIFICACOES.md, 5.4). */
export interface PendingBonusCost {
  stockEventId: number;
  dataCom: string;
  factor: number;
  newShares: number;
  label: string;
}
