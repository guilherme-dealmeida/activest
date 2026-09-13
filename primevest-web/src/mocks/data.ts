import {
  PortfolioSummary,
  PortfolioAssetDetail,
  PortfolioAllocation,
  PortfolioAllocationDetailed,
  TransactionResponse,
  Strategy,
  Rebalance,
  CeilingPrice,
  DividendSummary,
  DividendResponse,
  GoalResponse,
  AssetType,
  AssetCategory,
  Asset,
  User,
} from '../types';

// ===== USUÁRIO MOCK =====
export const mockUser: User = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'Guilherme',
  email: 'guilherme@primevest.com',
  avatarUrl: '',
  createdAt: '2024-01-01T00:00:00',
};

// ===== TIPOS DE ATIVOS =====
export const mockAssetTypes: AssetType[] = [
  { id: 1, name: 'RENDA_FIXA', description: 'Renda Fixa', colorHex: '#8B4513' },
  { id: 2, name: 'TESOURO_DIRETO', description: 'Tesouro Direto', colorHex: '#4A90D9' },
  { id: 3, name: 'FII', description: 'Fundos Imobiliários', colorHex: '#2D5F2D' },
  { id: 4, name: 'RENDA_VARIAVEL', description: 'Renda Variável', colorHex: '#C41E3A' },
  { id: 5, name: 'INTERNACIONAL', description: 'Internacional', colorHex: '#7B68EE' },
  { id: 6, name: 'CRYPTO', description: 'Criptomoedas', colorHex: '#FF8C00' },
];

// ===== RESUMO DA CARTEIRA =====
export const mockPortfolioSummary: PortfolioSummary = {
  id: '22222222-2222-2222-2222-222222222222',
  name: 'Minha Carteira',
  totalInvested: 63312.80,
  currentValue: 64256.33,
  totalDividends: 2711.17,
  totalReturn: 3654.70,
  returnPercentage: 5.78,
};

// ===== ATIVOS DA CARTEIRA (baseado na planilha) =====
export const mockPortfolioAssets: PortfolioAssetDetail[] = [
  // RENDA FIXA
  { id: '1', ticker: 'CDB-121', assetName: 'PF Turbinado 121% CDI', typeName: 'RENDA_FIXA', categoryName: 'CDB', totalShares: 1, averagePrice: 5860.21, totalInvested: 5860.21, currentValue: 5860.21, currentPrice: 5860.21, totalDividends: 1140.20, valorization: 1140.20, returnPercentage: 15.53, isReservation: true },
  { id: '2', ticker: 'CDB-100', assetName: 'PF Cashback 100% CDI', typeName: 'RENDA_FIXA', categoryName: 'CDB', totalShares: 1, averagePrice: 5249.91, totalInvested: 5249.91, currentValue: 5249.91, currentPrice: 5249.91, totalDividends: 554.63, valorization: 554.63, returnPercentage: 11.72, isReservation: true },
  { id: '3', ticker: 'CDB-120F', assetName: 'Nu Turbo 120% CDI Flexi', typeName: 'RENDA_FIXA', categoryName: 'CDB', totalShares: 1, averagePrice: 2440.00, totalInvested: 2440.00, currentValue: 2530.79, currentPrice: 2530.79, totalDividends: 80.79, valorization: 90.79, returnPercentage: 3.31, isReservation: true },
  { id: '4', ticker: 'PREV-CAIXA', assetName: 'Caixa FC Prev', typeName: 'RENDA_FIXA', categoryName: 'PREVIDENCIA', totalShares: 1, averagePrice: 2681.48, totalInvested: 2681.48, currentValue: 3485.65, currentPrice: 3485.65, totalDividends: 804.24, valorization: 804.17, returnPercentage: 14.47, isReservation: false },

  // TESOURO DIRETO
  { id: '5', ticker: 'PREF-2027', assetName: 'Prefixado 2027', typeName: 'TESOURO_DIRETO', categoryName: 'PREFIXADO', totalShares: 1.5, averagePrice: 5642.38, totalInvested: 5642.38, currentValue: 6055.84, currentPrice: 4037.23, totalDividends: 0, valorization: 413.46, returnPercentage: 18.35, isReservation: false },
  { id: '6', ticker: 'SELIC-2028', assetName: 'Selic 2028', typeName: 'TESOURO_DIRETO', categoryName: 'SELIC', totalShares: 0.2, averagePrice: 5447.34, totalInvested: 5447.34, currentValue: 5959.52, currentPrice: 29797.60, totalDividends: 0, valorization: 512.18, returnPercentage: 18.64, isReservation: false },
  { id: '7', ticker: 'SELIC-2029', assetName: 'Selic 2029', typeName: 'TESOURO_DIRETO', categoryName: 'SELIC', totalShares: 0.2, averagePrice: 3158.54, totalInvested: 3158.54, currentValue: 3953.05, currentPrice: 19765.25, totalDividends: 0, valorization: 798.45, returnPercentage: 25.28, isReservation: false },
  { id: '8', ticker: 'IPCA-2029', assetName: 'IPCA+ 2029', typeName: 'TESOURO_DIRETO', categoryName: 'IPCA_PLUS', totalShares: 0.39, averagePrice: 1258.77, totalInvested: 1258.77, currentValue: 1508.55, currentPrice: 3867.05, totalDividends: 0, valorization: 250.55, returnPercentage: 19.04, isReservation: false },
  { id: '9', ticker: 'IPCA-2035', assetName: 'IPCA+ 2035', typeName: 'TESOURO_DIRETO', categoryName: 'IPCA_PLUS', totalShares: 1.5, averagePrice: 3118.42, totalInvested: 3118.42, currentValue: 3708.55, currentPrice: 2472.37, totalDividends: 0, valorization: 589.64, returnPercentage: 4.56, isReservation: false },

  // FII
  { id: '10', ticker: 'BTLG11', assetName: 'BTG Logístico', typeName: 'FII', categoryName: 'TIJOLO', totalShares: 25, averagePrice: 2545.65, totalInvested: 2545.65, currentValue: 2476.50, currentPrice: 99.06, totalDividends: 153.00, valorization: -69.15, returnPercentage: 2.35, isReservation: false },
  { id: '11', ticker: 'GARE11', assetName: 'Guardian RE', typeName: 'FII', categoryName: 'TIJOLO', totalShares: 200, averagePrice: 1719.00, totalInvested: 1719.00, currentValue: 1676.00, currentPrice: 8.38, totalDividends: 87.15, valorization: -43.00, returnPercentage: -2.15, isReservation: false },
  { id: '12', ticker: 'GGRC11', assetName: 'GGR Covepi', typeName: 'FII', categoryName: 'TIJOLO', totalShares: 150, averagePrice: 1474.50, totalInvested: 1474.50, currentValue: 1351.50, currentPrice: 9.01, totalDividends: 0, valorization: -98.00, returnPercentage: -6.65, isReservation: false },
  { id: '13', ticker: 'HGLG11', assetName: 'CSHG Logística', typeName: 'FII', categoryName: 'TIJOLO', totalShares: 25, averagePrice: 3772.41, totalInvested: 3772.41, currentValue: 3895.50, currentPrice: 155.82, totalDividends: 373.64, valorization: 123.09, returnPercentage: 3.26, isReservation: false },
  { id: '14', ticker: 'MXRF11', assetName: 'Maxi Renda', typeName: 'FII', categoryName: 'PAPEL', totalShares: 11, averagePrice: 1001.86, totalInvested: 1001.86, currentValue: 1012.75, currentPrice: 92.07, totalDividends: 67.16, valorization: 10.89, returnPercentage: 2.40, isReservation: false },
  { id: '15', ticker: 'BBRV11', assetName: 'BB Renda Corp', typeName: 'FII', categoryName: 'PAPEL', totalShares: 5, averagePrice: 788.70, totalInvested: 788.70, currentValue: 839.35, currentPrice: 167.87, totalDividends: 0, valorization: 50.65, returnPercentage: 6.39, isReservation: false },
  { id: '16', ticker: 'RZTR11', assetName: 'Riza Terrax', typeName: 'FII', categoryName: 'TIJOLO', totalShares: 15, averagePrice: 2543.35, totalInvested: 2543.35, currentValue: 2083.95, currentPrice: 138.93, totalDividends: 166.00, valorization: -459.40, returnPercentage: -0.32, isReservation: false },
  { id: '17', ticker: 'VGHF11', assetName: 'Valora Hedge Fund', typeName: 'FII', categoryName: 'PAPEL', totalShares: 87, averagePrice: 3321.08, totalInvested: 3321.08, currentValue: 1587.00, currentPrice: 18.24, totalDividends: 338.55, valorization: -1734.08, returnPercentage: -17.05, isReservation: false },

  // RENDA VARIÁVEL
  { id: '18', ticker: 'BBAS3', assetName: 'Banco do Brasil', typeName: 'RENDA_VARIAVEL', categoryName: 'BANCO', totalShares: 175, averagePrice: 3843.54, totalInvested: 3843.54, currentValue: 3895.00, currentPrice: 22.26, totalDividends: 152.94, valorization: 51.46, returnPercentage: 5.32, isReservation: false },
  { id: '19', ticker: 'BBSE3', assetName: 'BB Seguridade', typeName: 'RENDA_VARIAVEL', categoryName: 'SEGURO', totalShares: 3, averagePrice: 1202.00, totalInvested: 1202.00, currentValue: 1417.83, currentPrice: 472.61, totalDividends: 25.94, valorization: 117.64, returnPercentage: 36.05, isReservation: false },
  { id: '20', ticker: 'BBRI11', assetName: 'Inter & Co', typeName: 'RENDA_VARIAVEL', categoryName: 'BANCO', totalShares: 150, averagePrice: 1402.00, totalInvested: 1402.00, currentValue: 1436.40, currentPrice: 9.58, totalDividends: 0, valorization: 28.00, returnPercentage: 1.07, isReservation: false },
  { id: '21', ticker: 'CXSE3', assetName: 'Caixa Seguridade', typeName: 'RENDA_VARIAVEL', categoryName: 'SEGURO', totalShares: 10, averagePrice: 1567.50, totalInvested: 1567.50, currentValue: 1642.90, currentPrice: 164.29, totalDividends: 0, valorization: 855.33, returnPercentage: 36.05, isReservation: false },
  { id: '22', ticker: 'SAEE4', assetName: 'Sabesp', typeName: 'RENDA_VARIAVEL', categoryName: 'ENERGIA', totalShares: 20, averagePrice: 581.05, totalInvested: 581.05, currentValue: 543.60, currentPrice: 27.18, totalDividends: 12.73, valorization: -38.18, returnPercentage: 12.56, isReservation: false },
  { id: '23', ticker: 'ITSA4', assetName: 'Itaúsa', typeName: 'RENDA_VARIAVEL', categoryName: 'FINANCEIRO', totalShares: 150, averagePrice: 2106.45, totalInvested: 2106.45, currentValue: 2047.50, currentPrice: 13.65, totalDividends: 0, valorization: -58.95, returnPercentage: 5.40, isReservation: false },
  { id: '24', ticker: 'KLBN4', assetName: 'Klabin', typeName: 'RENDA_VARIAVEL', categoryName: 'COMMODITY', totalShares: 600, averagePrice: 2078.74, totalInvested: 2078.74, currentValue: 2460.00, currentPrice: 4.10, totalDividends: 281.94, valorization: 381.26, returnPercentage: 9.46, isReservation: false },
  { id: '25', ticker: 'PETR4', assetName: 'Petrobras PN', typeName: 'RENDA_VARIAVEL', categoryName: 'COMMODITY', totalShares: 95, averagePrice: 1528.00, totalInvested: 1528.00, currentValue: 2410.00, currentPrice: 25.37, totalDividends: 84.93, valorization: 966.93, returnPercentage: 22.86, isReservation: false },
  { id: '26', ticker: 'SAPR4', assetName: 'Sanepar', typeName: 'RENDA_VARIAVEL', categoryName: 'SANEAMENTO', totalShares: 100, averagePrice: 563.00, totalInvested: 563.00, currentValue: 677.00, currentPrice: 6.77, totalDividends: 0, valorization: 47.55, returnPercentage: 4.17, isReservation: false },
  { id: '27', ticker: 'TIMS3', assetName: 'TIM', typeName: 'RENDA_VARIAVEL', categoryName: 'TELEFONIA', totalShares: 5, averagePrice: 563.00, totalInvested: 563.00, currentValue: 475.25, currentPrice: 95.05, totalDividends: 0, valorization: 47.55, returnPercentage: 4.17, isReservation: false },

  // INTERNACIONAL
  { id: '28', ticker: 'IVVB11', assetName: 'iShares S&P 500', typeName: 'INTERNACIONAL', categoryName: 'ETF', totalShares: 5, averagePrice: 2391.12, totalInvested: 2391.12, currentValue: 2659.05, currentPrice: 531.81, totalDividends: 0, valorization: 268.08, returnPercentage: 11.17, isReservation: false },

  // CRYPTO
  { id: '29', ticker: 'BTC', assetName: 'Bitcoin', typeName: 'CRYPTO', categoryName: 'BLOCKCHAIN', totalShares: 0.0083, averagePrice: 3399.50, totalInvested: 3399.50, currentValue: 3359.67, currentPrice: 404780.72, totalDividends: 0, valorization: -239.83, returnPercentage: -1.17, isReservation: false },
];

// ===== ALOCAÇÃO POR TIPO =====
export const mockAllocation: PortfolioAllocation[] = [
  { typeName: 'Tesouro Direto', colorHex: '#4A90D9', value: 19527.48, percentage: 30.39 },
  { typeName: 'FII', colorHex: '#2D5F2D', value: 19142.63, percentage: 29.79 },
  { typeName: 'Renda Variável', colorHex: '#C41E3A', value: 19567.35, percentage: 30.45 },
  { typeName: 'Renda Fixa', colorHex: '#8B4513', value: 0, percentage: 0 },
  { typeName: 'Internacional', colorHex: '#7B68EE', value: 2659.20, percentage: 4.14 },
  { typeName: 'Crypto', colorHex: '#FF8C00', value: 3359.67, percentage: 5.23 },
];

// ===== ALOCAÇÃO DETALHADA =====
export const mockAllocationDetailed: PortfolioAllocationDetailed[] = [
  {
    typeName: 'Tesouro Direto', colorHex: '#4A90D9', totalValue: 19527.48, percentage: 30.39,
    categories: [
      { categoryName: 'IPCA+', value: 17044.52, percentage: 89 },
      { categoryName: 'Prefixado', value: 2104.51, percentage: 11 },
    ],
  },
  {
    typeName: 'FII', colorHex: '#2D5F2D', totalValue: 19142.63, percentage: 29.79,
    categories: [
      { categoryName: 'Tijolo', value: 15682.35, percentage: 82 },
      { categoryName: 'Papel', value: 3460.28, percentage: 18 },
    ],
  },
  {
    typeName: 'Renda Variável', colorHex: '#C41E3A', totalValue: 19567.35, percentage: 30.45,
    categories: [
      { categoryName: 'Banco', value: 4752.97, percentage: 24 },
      { categoryName: 'Seguro', value: 2554.00, percentage: 13 },
      { categoryName: 'Energia', value: 2721.22, percentage: 14 },
      { categoryName: 'Financeiro', value: 2143.40, percentage: 11 },
      { categoryName: 'Commodity', value: 4538.74, percentage: 23 },
      { categoryName: 'Telefonia', value: 1427.85, percentage: 7 },
      { categoryName: 'Saneamento', value: 1429.17, percentage: 8 },
    ],
  },
  {
    typeName: 'Renda Fixa (Reserva)', colorHex: '#8B4513', totalValue: 25192.94, percentage: 100,
    categories: [
      { categoryName: 'CDB', value: 15640.91, percentage: 62 },
      { categoryName: 'SELIC', value: 9552.03, percentage: 38 },
    ],
  },
];

// ===== ESTRATÉGIA =====
export const mockStrategies: Strategy[] = [
  { id: '1', assetTypeId: 1, assetTypeName: 'Reserva Emergência', targetPercentage: 30.00, includeReservation: true },
  { id: '2', assetTypeId: 2, assetTypeName: 'Renda Fixa', targetPercentage: 0.00, includeReservation: false },
  { id: '3', assetTypeId: 3, assetTypeName: 'Tesouro Direto', targetPercentage: 30.00, includeReservation: false },
  { id: '4', assetTypeId: 4, assetTypeName: 'FII', targetPercentage: 30.00, includeReservation: false },
  { id: '5', assetTypeId: 5, assetTypeName: 'Renda Variável', targetPercentage: 30.00, includeReservation: false },
  { id: '6', assetTypeId: 6, assetTypeName: 'Internacional', targetPercentage: 5.00, includeReservation: false },
  { id: '7', assetTypeId: 7, assetTypeName: 'Crypto', targetPercentage: 5.00, includeReservation: false },
];

export const mockRebalance: Rebalance = {
  totalPortfolioValue: 89449.27,
  items: [
    { typeName: 'Reserva Emergência', idealPercentage: 30.00, currentPercentage: 28.16, diffPercentage: -1.84, currentValue: 25192.94, idealValue: 26834.78, amountToRebalance: 1641.84 },
    { typeName: 'Renda Fixa', idealPercentage: 0.00, currentPercentage: 0.00, diffPercentage: 0.00, currentValue: 0, idealValue: 0, amountToRebalance: 0 },
    { typeName: 'Tesouro Direto', idealPercentage: 30.00, currentPercentage: 39.87, diffPercentage: 9.87, currentValue: 19527.48, idealValue: 19567.35, amountToRebalance: 39.87 },
    { typeName: 'FII', idealPercentage: 30.00, currentPercentage: 29.42, diffPercentage: -0.58, currentValue: 19142.63, idealValue: 19567.35, amountToRebalance: 424.72 },
    { typeName: 'Renda Variável', idealPercentage: 30.00, currentPercentage: 30.45, diffPercentage: 0.45, currentValue: 19567.35, idealValue: 19567.35, amountToRebalance: 0 },
    { typeName: 'Internacional', idealPercentage: 5.00, currentPercentage: 4.14, diffPercentage: -0.86, currentValue: 2659.20, idealValue: 3261.64, amountToRebalance: 602.44 },
    { typeName: 'Crypto', idealPercentage: 5.00, currentPercentage: 5.23, diffPercentage: 0.23, currentValue: 3359.67, idealValue: 3261.64, amountToRebalance: -98.03 },
  ],
};

// ===== TRANSAÇÕES REAIS =====
export const mockTransactions: TransactionResponse[] = [
  { id: '1', ticker: 'IPCA+ 2029', assetName: 'Tesouro IPCA+ 2029', type: 'BUY', shares: 1, pricePerShare: 1256.77, totalAmount: 1256.77, brokerageFee: 0, transactionDate: '2024-11-27', createdAt: '2024-11-27T10:00:00' },
  { id: '2', ticker: 'PREFIXADO 2027', assetName: 'Tesouro Prefixado 2027', type: 'BUY', shares: 1, pricePerShare: 842.38, totalAmount: 842.38, brokerageFee: 0, transactionDate: '2024-11-27', createdAt: '2024-11-27T10:01:00' },
  { id: '3', ticker: 'BBAS3', assetName: 'Banco do Brasil', type: 'BUY', shares: 1, pricePerShare: 475.46, totalAmount: 475.46, brokerageFee: 0, transactionDate: '2024-11-28', createdAt: '2024-11-28T10:00:00' },
  { id: '4', ticker: 'VGHF11', assetName: 'Valora Hedge Fund', type: 'BUY', shares: 1, pricePerShare: 691.03, totalAmount: 691.03, brokerageFee: 0, transactionDate: '2024-11-28', createdAt: '2024-11-28T10:01:00' },
  { id: '5', ticker: 'NU 100% CDI', assetName: 'Nu 100% CDI', type: 'BUY', shares: 1, pricePerShare: 800.00, totalAmount: 800.00, brokerageFee: 0, transactionDate: '2024-11-29', createdAt: '2024-11-29T10:00:00' },
  { id: '6', ticker: 'MXRF11', assetName: 'Maxi Renda', type: 'BUY', shares: 1, pricePerShare: 214.89, totalAmount: 214.89, brokerageFee: 0, transactionDate: '2024-11-29', createdAt: '2024-11-29T10:01:00' },
  { id: '7', ticker: 'NU 100% CDI', assetName: 'Nu 100% CDI', type: 'BUY', shares: 1, pricePerShare: 600.00, totalAmount: 600.00, brokerageFee: 0, transactionDate: '2024-11-29', createdAt: '2024-11-29T10:02:00' },
  { id: '8', ticker: 'CMIG4', assetName: 'Cemig', type: 'BUY', shares: 1, pricePerShare: 404.39, totalAmount: 404.39, brokerageFee: 0, transactionDate: '2024-12-03', createdAt: '2024-12-03T10:00:00' },
  { id: '9', ticker: 'MXRF11', assetName: 'Maxi Renda', type: 'BUY', shares: 1, pricePerShare: 9.32, totalAmount: 9.32, brokerageFee: 0, transactionDate: '2024-12-03', createdAt: '2024-12-03T10:01:00' },
  { id: '10', ticker: 'MXRF11', assetName: 'Maxi Renda', type: 'BUY', shares: 1, pricePerShare: 607.89, totalAmount: 607.89, brokerageFee: 0, transactionDate: '2024-12-05', createdAt: '2024-12-05T10:00:00' },
  { id: '11', ticker: 'MXRF11', assetName: 'Maxi Renda', type: 'BUY', shares: 1, pricePerShare: 9.07, totalAmount: 9.07, brokerageFee: 0, transactionDate: '2024-12-05', createdAt: '2024-12-05T10:01:00' },
  { id: '12', ticker: 'SAPR4', assetName: 'Sanepar', type: 'BUY', shares: 1, pricePerShare: 563.19, totalAmount: 563.19, brokerageFee: 0, transactionDate: '2024-12-16', createdAt: '2024-12-16T10:00:00' },
  { id: '13', ticker: 'HGLG11', assetName: 'CSHG Logística', type: 'BUY', shares: 1, pricePerShare: 913.20, totalAmount: 913.20, brokerageFee: 0, transactionDate: '2024-12-16', createdAt: '2024-12-16T10:01:00' },
  { id: '14', ticker: 'VISC11', assetName: 'Vinci Shopping Centers', type: 'BUY', shares: 1, pricePerShare: 912.74, totalAmount: 912.74, brokerageFee: 0, transactionDate: '2024-12-16', createdAt: '2024-12-16T10:02:00' },
  { id: '15', ticker: 'CMIG4', assetName: 'Cemig', type: 'BUY', shares: 1, pricePerShare: 740.59, totalAmount: 740.59, brokerageFee: 0, transactionDate: '2024-12-17', createdAt: '2024-12-17T10:00:00' },
  { id: '16', ticker: 'HGLG11', assetName: 'CSHG Logística', type: 'BUY', shares: 1, pricePerShare: 588.44, totalAmount: 588.44, brokerageFee: 0, transactionDate: '2024-12-18', createdAt: '2024-12-18T10:00:00' },
  { id: '17', ticker: 'BBAS3', assetName: 'Banco do Brasil', type: 'BUY', shares: 1, pricePerShare: 740.83, totalAmount: 740.83, brokerageFee: 0, transactionDate: '2024-12-18', createdAt: '2024-12-18T10:01:00' },
  { id: '18', ticker: 'MXRF11', assetName: 'Maxi Renda', type: 'BUY', shares: 1, pricePerShare: 71.31, totalAmount: 71.31, brokerageFee: 0, transactionDate: '2024-12-18', createdAt: '2024-12-18T10:02:00' },
  { id: '19', ticker: 'IPCA+ 2035', assetName: 'Tesouro IPCA+ 2035', type: 'BUY', shares: 1, pricePerShare: 3118.42, totalAmount: 3118.42, brokerageFee: 0, transactionDate: '2025-01-02', createdAt: '2025-01-02T10:00:00' },
  { id: '20', ticker: 'CXSE3', assetName: 'Caixa Seguridade', type: 'BUY', shares: 1, pricePerShare: 1429.47, totalAmount: 1429.47, brokerageFee: 0, transactionDate: '2025-01-03', createdAt: '2025-01-03T10:00:00' },
  { id: '21', ticker: 'BBAS3', assetName: 'Banco do Brasil', type: 'BUY', shares: 1, pricePerShare: 1188.39, totalAmount: 1188.39, brokerageFee: 0, transactionDate: '2025-01-03', createdAt: '2025-01-03T10:01:00' },
  { id: '22', ticker: 'HGLG11', assetName: 'CSHG Logística', type: 'BUY', shares: 1, pricePerShare: 1528.00, totalAmount: 1528.00, brokerageFee: 0, transactionDate: '2025-01-06', createdAt: '2025-01-06T10:00:00' },
  { id: '23', ticker: 'SELIC 2029', assetName: 'Tesouro Selic 2029', type: 'BUY', shares: 1, pricePerShare: 3158.54, totalAmount: 3158.54, brokerageFee: 0, transactionDate: '2025-01-08', createdAt: '2025-01-08T10:00:00' },
  { id: '24', ticker: 'VGHF11', assetName: 'Valora Hedge Fund', type: 'BUY', shares: 1, pricePerShare: 83.52, totalAmount: 83.52, brokerageFee: 0, transactionDate: '2025-01-08', createdAt: '2025-01-08T10:01:00' },
  { id: '25', ticker: 'VGHF11', assetName: 'Valora Hedge Fund', type: 'BUY', shares: 1, pricePerShare: 52.59, totalAmount: 52.59, brokerageFee: 0, transactionDate: '2025-06-10', createdAt: '2025-06-10T10:00:00' },
  { id: '26', ticker: 'VISC11', assetName: 'Vinci Shopping Centers', type: 'BUY', shares: 1, pricePerShare: 101.21, totalAmount: 101.21, brokerageFee: 0, transactionDate: '2025-06-10', createdAt: '2025-06-10T10:01:00' },
  { id: '27', ticker: 'MXRF11', assetName: 'Maxi Renda', type: 'BUY', shares: 1, pricePerShare: 92.73, totalAmount: 92.73, brokerageFee: 0, transactionDate: '2025-06-10', createdAt: '2025-06-10T10:02:00' },
  { id: '28', ticker: 'HGLG11', assetName: 'CSHG Logística', type: 'BUY', shares: 1, pricePerShare: 155.87, totalAmount: 155.87, brokerageFee: 0, transactionDate: '2025-06-10', createdAt: '2025-06-10T10:03:00' },
  { id: '29', ticker: 'PP 121% CDI', assetName: 'PicPay 121% CDI', type: 'BUY', shares: 1, pricePerShare: 1397.03, totalAmount: 1397.03, brokerageFee: 0, transactionDate: '2025-07-08', createdAt: '2025-07-08T10:00:00' },
  { id: '30', ticker: 'IPCA+ 2040', assetName: 'Tesouro IPCA+ 2040', type: 'BUY', shares: 1, pricePerShare: 490.33, totalAmount: 490.33, brokerageFee: 0, transactionDate: '2025-07-14', createdAt: '2025-07-14T10:00:00' },
  { id: '31', ticker: 'BBAS3', assetName: 'Banco do Brasil', type: 'BUY', shares: 1, pricePerShare: 199.27, totalAmount: 199.27, brokerageFee: 0, transactionDate: '2025-07-22', createdAt: '2025-07-22T10:00:00' },
  { id: '32', ticker: 'CXSE3', assetName: 'Caixa Seguridade', type: 'BUY', shares: 1, pricePerShare: 138.55, totalAmount: 138.55, brokerageFee: 0, transactionDate: '2025-07-22', createdAt: '2025-07-22T10:01:00' },
  { id: '33', ticker: 'CMIG4', assetName: 'Cemig', type: 'BUY', shares: 1, pricePerShare: 104.64, totalAmount: 104.64, brokerageFee: 0, transactionDate: '2025-07-22', createdAt: '2025-07-22T10:02:00' },
  { id: '34', ticker: 'VGHF11', assetName: 'Valora Hedge Fund', type: 'BUY', shares: 1, pricePerShare: 30.73, totalAmount: 30.73, brokerageFee: 0, transactionDate: '2025-07-22', createdAt: '2025-07-22T10:03:00' },
  { id: '35', ticker: 'BRBI11', assetName: 'Inter & Co', type: 'BUY', shares: 1, pricePerShare: 1582.52, totalAmount: 1582.52, brokerageFee: 0, transactionDate: '2025-07-24', createdAt: '2025-07-24T10:00:00' },
  { id: '36', ticker: 'NU 120% CDI', assetName: 'Nu 120% CDI Flexi', type: 'BUY', shares: 1, pricePerShare: 543.85, totalAmount: 543.85, brokerageFee: 0, transactionDate: '2025-07-25', createdAt: '2025-07-25T10:00:00' },
  { id: '37', ticker: 'PP 121% CDI', assetName: 'PicPay 121% CDI', type: 'BUY', shares: 1, pricePerShare: 1000.00, totalAmount: 1000.00, brokerageFee: 0, transactionDate: '2025-07-25', createdAt: '2025-07-25T10:01:00' },
  { id: '38', ticker: 'NU 100% CDI', assetName: 'Nu 100% CDI', type: 'BUY', shares: 1, pricePerShare: 1000.00, totalAmount: 1000.00, brokerageFee: 0, transactionDate: '2025-08-04', createdAt: '2025-08-04T10:00:00' },
  { id: '39', ticker: 'BBAS3', assetName: 'Banco do Brasil', type: 'BUY', shares: 1, pricePerShare: 282.85, totalAmount: 282.85, brokerageFee: 0, transactionDate: '2025-08-06', createdAt: '2025-08-06T10:00:00' },
  { id: '40', ticker: 'IPCA+ 2040', assetName: 'Tesouro IPCA+ 2040', type: 'BUY', shares: 1, pricePerShare: 494.39, totalAmount: 494.39, brokerageFee: 0, transactionDate: '2025-08-11', createdAt: '2025-08-11T10:00:00' },
  { id: '41', ticker: 'NU 100% CDI', assetName: 'Nu 100% CDI', type: 'BUY', shares: 1, pricePerShare: 102.10, totalAmount: 102.10, brokerageFee: 0, transactionDate: '2025-08-11', createdAt: '2025-08-11T10:01:00' },
  { id: '42', ticker: 'NU 100% CDI', assetName: 'Nu 100% CDI', type: 'BUY', shares: 1, pricePerShare: 115.50, totalAmount: 115.50, brokerageFee: 0, transactionDate: '2025-08-12', createdAt: '2025-08-12T10:00:00' },
  { id: '43', ticker: 'NU 100% CDI', assetName: 'Nu 100% CDI', type: 'BUY', shares: 1, pricePerShare: 438.96, totalAmount: 438.96, brokerageFee: 0, transactionDate: '2025-08-15', createdAt: '2025-08-15T10:00:00' },
  { id: '44', ticker: 'VGHF11', assetName: 'Valora Hedge Fund', type: 'BUY', shares: 1, pricePerShare: 497.41, totalAmount: 497.41, brokerageFee: 0, transactionDate: '2025-08-20', createdAt: '2025-08-20T10:00:00' },
  { id: '45', ticker: 'PP 121% CDI', assetName: 'PicPay 121% CDI', type: 'BUY', shares: 1, pricePerShare: 878.19, totalAmount: 878.19, brokerageFee: 0, transactionDate: '2025-08-25', createdAt: '2025-08-25T10:00:00' },
  { id: '46', ticker: 'IVVB11', assetName: 'iShares S&P 500', type: 'BUY', shares: 1, pricePerShare: 1572.56, totalAmount: 1572.56, brokerageFee: 0, transactionDate: '2025-08-29', createdAt: '2025-08-29T10:00:00' },
  { id: '47', ticker: 'SELIC 2028', assetName: 'Tesouro Selic 2028', type: 'BUY', shares: 1, pricePerShare: 3447.34, totalAmount: 3447.34, brokerageFee: 0, transactionDate: '2025-08-29', createdAt: '2025-08-29T10:01:00' },
  { id: '48', ticker: 'PP 121% CDI', assetName: 'PicPay 121% CDI', type: 'BUY', shares: 1, pricePerShare: 3077.18, totalAmount: 3077.18, brokerageFee: 0, transactionDate: '2025-08-29', createdAt: '2025-08-29T10:02:00' },
  { id: '49', ticker: 'VGHF11', assetName: 'Valora Hedge Fund', type: 'BUY', shares: 1, pricePerShare: 966.57, totalAmount: 966.57, brokerageFee: 0, transactionDate: '2025-08-29', createdAt: '2025-08-29T10:03:00' },
  { id: '50', ticker: 'IPCA+ 2050', assetName: 'Tesouro IPCA+ 2050', type: 'BUY', shares: 1, pricePerShare: 2490.21, totalAmount: 2490.21, brokerageFee: 0, transactionDate: '2025-09-01', createdAt: '2025-09-01T10:00:00' },
  { id: '51', ticker: 'IPCA+ 2040', assetName: 'Tesouro IPCA+ 2040', type: 'BUY', shares: 1, pricePerShare: 498.05, totalAmount: 498.05, brokerageFee: 0, transactionDate: '2025-09-05', createdAt: '2025-09-05T10:00:00' },
  { id: '52', ticker: 'BTLG11', assetName: 'BTG Logístico', type: 'BUY', shares: 1, pricePerShare: 513.50, totalAmount: 513.50, brokerageFee: 0, transactionDate: '2025-09-15', createdAt: '2025-09-15T10:00:00' },
  { id: '53', ticker: 'RZTR11', assetName: 'Riza Terrax', type: 'BUY', shares: 1, pricePerShare: 465.00, totalAmount: 465.00, brokerageFee: 0, transactionDate: '2025-09-15', createdAt: '2025-09-15T10:01:00' },
  { id: '54', ticker: 'RZTR11', assetName: 'Riza Terrax', type: 'BUY', shares: 1, pricePerShare: 464.00, totalAmount: 464.00, brokerageFee: 0, transactionDate: '2025-09-15', createdAt: '2025-09-15T10:02:00' },
  { id: '55', ticker: 'ISAE4', assetName: 'ISA Energia', type: 'BUY', shares: 1, pricePerShare: 370.32, totalAmount: 370.32, brokerageFee: 0, transactionDate: '2025-09-30', createdAt: '2025-09-30T10:00:00' },
  { id: '56', ticker: 'NU 200% CDI', assetName: 'Nu 200% CDI', type: 'SELL', shares: 1, pricePerShare: 3617.71, totalAmount: 3617.71, brokerageFee: 0, transactionDate: '2025-09-30', createdAt: '2025-09-30T10:01:00' },
  { id: '57', ticker: 'IVVB11', assetName: 'iShares S&P 500', type: 'BUY', shares: 1, pricePerShare: 397.23, totalAmount: 397.23, brokerageFee: 0, transactionDate: '2025-10-01', createdAt: '2025-10-01T10:00:00' },
  { id: '58', ticker: 'ISAE4', assetName: 'ISA Energia', type: 'BUY', shares: 1, pricePerShare: 370.32, totalAmount: 370.32, brokerageFee: 0, transactionDate: '2025-10-01', createdAt: '2025-10-01T10:01:00' },
  { id: '59', ticker: 'PETR4', assetName: 'Petrobras PN', type: 'BUY', shares: 1, pricePerShare: 313.61, totalAmount: 313.61, brokerageFee: 0, transactionDate: '2025-10-01', createdAt: '2025-10-01T10:02:00' },
  { id: '60', ticker: 'RZTR11', assetName: 'Riza Terrax', type: 'BUY', shares: 1, pricePerShare: 455.50, totalAmount: 455.50, brokerageFee: 0, transactionDate: '2025-10-01', createdAt: '2025-10-01T10:03:00' },
  { id: '61', ticker: 'CMIG4', assetName: 'Cemig', type: 'BUY', shares: 1, pricePerShare: 434.14, totalAmount: 434.14, brokerageFee: 0, transactionDate: '2025-10-01', createdAt: '2025-10-01T10:04:00' },
  { id: '62', ticker: 'PETR4', assetName: 'Petrobras PN', type: 'BUY', shares: 1, pricePerShare: 311.11, totalAmount: 311.11, brokerageFee: 0, transactionDate: '2025-10-02', createdAt: '2025-10-02T10:00:00' },
  { id: '63', ticker: 'PETR4', assetName: 'Petrobras PN', type: 'BUY', shares: 1, pricePerShare: 155.30, totalAmount: 155.30, brokerageFee: 0, transactionDate: '2025-10-02', createdAt: '2025-10-02T10:01:00' },
  { id: '64', ticker: 'KLBN4', assetName: 'Klabin', type: 'BUY', shares: 1, pricePerShare: 362.12, totalAmount: 362.12, brokerageFee: 0, transactionDate: '2025-10-03', createdAt: '2025-10-03T10:00:00' },
  { id: '65', ticker: 'ISAE4', assetName: 'ISA Energia', type: 'BUY', shares: 1, pricePerShare: 121.49, totalAmount: 121.49, brokerageFee: 0, transactionDate: '2025-10-06', createdAt: '2025-10-06T10:00:00' },
  { id: '66', ticker: 'BBSE3', assetName: 'BB Seguridade', type: 'BUY', shares: 1, pricePerShare: 326.31, totalAmount: 326.31, brokerageFee: 0, transactionDate: '2025-10-06', createdAt: '2025-10-06T10:01:00' },
  { id: '67', ticker: 'KLBN4', assetName: 'Klabin', type: 'BUY', shares: 1, pricePerShare: 358.12, totalAmount: 358.12, brokerageFee: 0, transactionDate: '2025-10-06', createdAt: '2025-10-06T10:02:00' },
  { id: '68', ticker: 'KLBN4', assetName: 'Klabin', type: 'BUY', shares: 1, pricePerShare: 349.12, totalAmount: 349.12, brokerageFee: 0, transactionDate: '2025-10-10', createdAt: '2025-10-10T10:00:00' },
  { id: '69', ticker: 'PETR4', assetName: 'Petrobras PN', type: 'BUY', shares: 1, pricePerShare: 748.50, totalAmount: 748.50, brokerageFee: 0, transactionDate: '2025-10-10', createdAt: '2025-10-10T10:01:00' },
  { id: '70', ticker: 'BTLG11', assetName: 'BTG Logístico', type: 'BUY', shares: 1, pricePerShare: 511.72, totalAmount: 511.72, brokerageFee: 0, transactionDate: '2025-12-11', createdAt: '2025-12-11T10:00:00' },
  { id: '71', ticker: 'BTLG11', assetName: 'BTG Logístico', type: 'BUY', shares: 1, pricePerShare: 1019.13, totalAmount: 1019.13, brokerageFee: 0, transactionDate: '2025-12-19', createdAt: '2025-12-19T10:00:00' },
  { id: '72', ticker: 'TRXF11', assetName: 'TRX Real Estate', type: 'BUY', shares: 1, pricePerShare: 480.41, totalAmount: 480.41, brokerageFee: 0, transactionDate: '2025-12-22', createdAt: '2025-12-22T10:00:00' },
  { id: '73', ticker: 'NU 120% CDI', assetName: 'Nu 120% CDI Flexi', type: 'BUY', shares: 1, pricePerShare: 9456.15, totalAmount: 9456.15, brokerageFee: 0, transactionDate: '2025-12-29', createdAt: '2025-12-29T10:00:00' },
  { id: '74', ticker: 'GARE11', assetName: 'Guardian RE', type: 'BUY', shares: 1, pricePerShare: 441.15, totalAmount: 441.15, brokerageFee: 0, transactionDate: '2026-01-22', createdAt: '2026-01-22T10:00:00' },
  { id: '75', ticker: 'NU 100% CDI', assetName: 'Nu 100% CDI', type: 'SELL', shares: 1, pricePerShare: 883.69, totalAmount: 883.69, brokerageFee: 0, transactionDate: '2026-01-25', createdAt: '2026-01-25T10:00:00' },
  { id: '76', ticker: 'NU 100% CDI', assetName: 'Nu 100% CDI', type: 'SELL', shares: 1, pricePerShare: 733.74, totalAmount: 733.74, brokerageFee: 0, transactionDate: '2026-02-02', createdAt: '2026-02-02T10:00:00' },
  { id: '77', ticker: 'NU 120% CDI', assetName: 'Nu 120% CDI Flexi', type: 'SELL', shares: 1, pricePerShare: 8000.00, totalAmount: 8000.00, brokerageFee: 0, transactionDate: '2026-02-02', createdAt: '2026-02-02T10:01:00' },
  { id: '78', ticker: 'GARE11', assetName: 'Guardian RE', type: 'BUY', shares: 1, pricePerShare: 430.14, totalAmount: 430.14, brokerageFee: 0, transactionDate: '2026-02-10', createdAt: '2026-02-10T10:00:00' },
  { id: '79', ticker: 'TRXF11', assetName: 'TRX Real Estate', type: 'BUY', shares: 1, pricePerShare: 464.41, totalAmount: 464.41, brokerageFee: 0, transactionDate: '2026-02-10', createdAt: '2026-02-10T10:01:00' },
  { id: '80', ticker: 'PREFIXADO 2032', assetName: 'Tesouro Prefixado 2032', type: 'BUY', shares: 1, pricePerShare: 1073.26, totalAmount: 1073.26, brokerageFee: 0, transactionDate: '2026-03-16', createdAt: '2026-03-16T10:00:00' },
  { id: '81', ticker: 'GARE11', assetName: 'Guardian RE', type: 'BUY', shares: 1, pricePerShare: 848.28, totalAmount: 848.28, brokerageFee: 0, transactionDate: '2026-04-22', createdAt: '2026-04-22T10:00:00' },
  { id: '82', ticker: 'IVVB11', assetName: 'iShares S&P 500', type: 'BUY', shares: 1, pricePerShare: 398.83, totalAmount: 398.83, brokerageFee: 0, transactionDate: '2026-04-22', createdAt: '2026-04-22T10:01:00' },
  { id: '83', ticker: 'TRXF11', assetName: 'TRX Real Estate', type: 'BUY', shares: 1, pricePerShare: 918.30, totalAmount: 918.30, brokerageFee: 0, transactionDate: '2026-04-22', createdAt: '2026-04-22T10:02:00' },
  { id: '84', ticker: 'KLBN4', assetName: 'Klabin', type: 'BUY', shares: 1, pricePerShare: 502.92, totalAmount: 502.92, brokerageFee: 0, transactionDate: '2026-05-06', createdAt: '2026-05-06T10:00:00' },
  { id: '85', ticker: 'GGRC11', assetName: 'GGR Covepi', type: 'BUY', shares: 1, pricePerShare: 508.67, totalAmount: 508.67, brokerageFee: 0, transactionDate: '2026-05-14', createdAt: '2026-05-14T10:00:00' },
  { id: '86', ticker: 'NU 120% CDI', assetName: 'Nu 120% CDI Flexi', type: 'BUY', shares: 1, pricePerShare: 2000.00, totalAmount: 2000.00, brokerageFee: 0, transactionDate: '2026-05-14', createdAt: '2026-05-14T10:01:00' },
  { id: '87', ticker: 'KLBN4', assetName: 'Klabin', type: 'BUY', shares: 1, pricePerShare: 507.17, totalAmount: 507.17, brokerageFee: 0, transactionDate: '2026-05-14', createdAt: '2026-05-14T10:02:00' },
  { id: '88', ticker: 'BBAS3', assetName: 'Banco do Brasil', type: 'BUY', shares: 1, pricePerShare: 958.31, totalAmount: 958.31, brokerageFee: 0, transactionDate: '2026-06-08', createdAt: '2026-06-08T10:00:00' },
  { id: '89', ticker: 'NU 120% CDI', assetName: 'Nu 120% CDI Flexi', type: 'SELL', shares: 1, pricePerShare: 3000.00, totalAmount: 3000.00, brokerageFee: 0, transactionDate: '2026-07-01', createdAt: '2026-07-01T10:00:00' },
  { id: '90', ticker: 'GGRC11', assetName: 'GGR Covepi', type: 'BUY', shares: 1, pricePerShare: 966.32, totalAmount: 966.32, brokerageFee: 0, transactionDate: '2026-07-08', createdAt: '2026-07-08T10:00:00' },
  { id: '91', ticker: 'NU 120% CDI', assetName: 'Nu 120% CDI Flexi', type: 'BUY', shares: 1, pricePerShare: 3000.00, totalAmount: 3000.00, brokerageFee: 0, transactionDate: '2026-07-08', createdAt: '2026-07-08T10:01:00' },
  { id: '92', ticker: 'ITSA4', assetName: 'Itaúsa', type: 'BUY', shares: 1, pricePerShare: 681.23, totalAmount: 681.23, brokerageFee: 0, transactionDate: '2026-07-17', createdAt: '2026-07-17T10:00:00' },
  { id: '93', ticker: 'ITSA4', assetName: 'Itaúsa', type: 'BUY', shares: 1, pricePerShare: 680.30, totalAmount: 680.30, brokerageFee: 0, transactionDate: '2026-07-21', createdAt: '2026-07-21T10:00:00' },
  { id: '94', ticker: 'NU 120% CDI', assetName: 'Nu 120% CDI Flexi', type: 'SELL', shares: 1, pricePerShare: 4356.61, totalAmount: 4356.61, brokerageFee: 0, transactionDate: '2026-07-26', createdAt: '2026-07-26T10:00:00' },
  { id: '95', ticker: 'BTLG11', assetName: 'BTG Logístico', type: 'BUY', shares: 1, pricePerShare: 502.97, totalAmount: 502.97, brokerageFee: 0, transactionDate: '2026-07-27', createdAt: '2026-07-27T10:00:00' },
  { id: '96', ticker: 'HGLG11', assetName: 'CSHG Logística', type: 'BUY', shares: 1, pricePerShare: 588.16, totalAmount: 588.16, brokerageFee: 0, transactionDate: '2026-07-27', createdAt: '2026-07-27T10:01:00' },
  { id: '97', ticker: 'RZTR11', assetName: 'Riza Terrax', type: 'BUY', shares: 1, pricePerShare: 870.29, totalAmount: 870.29, brokerageFee: 0, transactionDate: '2026-07-27', createdAt: '2026-07-27T10:02:00' },
  { id: '98', ticker: 'TRXF11', assetName: 'TRX Real Estate', type: 'BUY', shares: 1, pricePerShare: 456.35, totalAmount: 456.35, brokerageFee: 0, transactionDate: '2026-07-27', createdAt: '2026-07-27T10:03:00' },
  { id: '99', ticker: 'TIMS3', assetName: 'TIM', type: 'BUY', shares: 1, pricePerShare: 957.32, totalAmount: 957.32, brokerageFee: 0, transactionDate: '2026-08-03', createdAt: '2026-08-03T10:00:00' },
  { id: '100', ticker: 'TIMS3', assetName: 'TIM', type: 'BUY', shares: 1, pricePerShare: 470.16, totalAmount: 470.16, brokerageFee: 0, transactionDate: '2026-08-05', createdAt: '2026-08-05T10:00:00' },
  { id: '101', ticker: 'XPML11', assetName: 'XP Malls', type: 'BUY', shares: 1, pricePerShare: 497.66, totalAmount: 497.66, brokerageFee: 0, transactionDate: '2026-08-19', createdAt: '2026-08-19T10:00:00' },
  { id: '102', ticker: 'RBRY11', assetName: 'RBR Yield', type: 'BUY', shares: 1, pricePerShare: 788.96, totalAmount: 788.96, brokerageFee: 0, transactionDate: '2026-08-19', createdAt: '2026-08-19T10:01:00' },
  { id: '103', ticker: 'TRXF11', assetName: 'TRX Real Estate', type: 'BUY', shares: 1, pricePerShare: 392.63, totalAmount: 392.63, brokerageFee: 0, transactionDate: '2026-08-19', createdAt: '2026-08-19T10:02:00' },
  { id: '104', ticker: 'XPML11', assetName: 'XP Malls', type: 'BUY', shares: 1, pricePerShare: 496.41, totalAmount: 496.41, brokerageFee: 0, transactionDate: '2026-08-19', createdAt: '2026-08-19T10:03:00' },
  { id: '105', ticker: 'ITSA4', assetName: 'Itaúsa', type: 'BUY', shares: 1, pricePerShare: 610.20, totalAmount: 610.20, brokerageFee: 0, transactionDate: '2026-08-20', createdAt: '2026-08-20T10:00:00' },
  { id: '106', ticker: 'CMIG4', assetName: 'Cemig', type: 'BUY', shares: 1, pricePerShare: 497.66, totalAmount: 497.66, brokerageFee: 0, transactionDate: '2026-08-20', createdAt: '2026-08-20T10:01:00' },
];

// ===== PREÇO TETO =====
export const mockCeilingPrices: CeilingPrice[] = [
  { id: '1', ticker: 'PETR4', assetName: 'Petrobras PN', maxPrice: 32.00, currentPrice: 25.37, diffPercentage: -20.72, alertEnabled: true, belowCeiling: true },
  { id: '2', ticker: 'BBAS3', assetName: 'Banco do Brasil', maxPrice: 25.00, currentPrice: 22.26, diffPercentage: -10.96, alertEnabled: true, belowCeiling: true },
  { id: '3', ticker: 'ITSA4', assetName: 'Itaúsa', maxPrice: 12.00, currentPrice: 13.65, diffPercentage: 13.75, alertEnabled: true, belowCeiling: false },
  { id: '4', ticker: 'HGLG11', assetName: 'CSHG Logística', maxPrice: 160.00, currentPrice: 155.82, diffPercentage: -2.61, alertEnabled: true, belowCeiling: true },
  { id: '5', ticker: 'BTLG11', assetName: 'BTG Logístico', maxPrice: 105.00, currentPrice: 99.06, diffPercentage: -5.66, alertEnabled: false, belowCeiling: true },
  { id: '6', ticker: 'KLBN4', assetName: 'Klabin', maxPrice: 4.50, currentPrice: 4.10, diffPercentage: -8.89, alertEnabled: true, belowCeiling: true },
];

// ===== DIVIDENDOS =====
export const mockDividendSummary: DividendSummary = {
  totalDividends: 2711.17,
  byType: { DIVIDENDO: 1845.30, JCP: 512.87, RENDIMENTO: 353.00 },
  recent: [
    { id: 1, ticker: 'PETR4', assetName: 'Petrobras PN', amountPerShare: 0.85, totalAmount: 80.75, exDate: '2026-08-20', paymentDate: '2026-09-05', type: 'DIVIDENDO' },
    { id: 2, ticker: 'BBAS3', assetName: 'Banco do Brasil', amountPerShare: 0.42, totalAmount: 73.50, exDate: '2026-08-15', paymentDate: '2026-09-01', type: 'DIVIDENDO' },
    { id: 3, ticker: 'HGLG11', assetName: 'CSHG Logística', amountPerShare: 1.10, totalAmount: 27.50, exDate: '2026-08-10', paymentDate: '2026-08-20', type: 'RENDIMENTO' },
    { id: 4, ticker: 'BTLG11', assetName: 'BTG Logístico', amountPerShare: 0.78, totalAmount: 19.50, exDate: '2026-08-10', paymentDate: '2026-08-20', type: 'RENDIMENTO' },
    { id: 5, ticker: 'KLBN4', assetName: 'Klabin', amountPerShare: 0.08, totalAmount: 48.00, exDate: '2026-07-25', paymentDate: '2026-08-10', type: 'JCP' },
    { id: 6, ticker: 'ITSA4', assetName: 'Itaúsa', amountPerShare: 0.15, totalAmount: 22.50, exDate: '2026-07-20', paymentDate: '2026-08-05', type: 'JCP' },
  ],
};

// ===== METAS =====
export const mockGoals: GoalResponse[] = [
  { id: '1', name: 'Reserva de Emergência 6 meses', targetAmount: 30000, currentAmount: 25192.94, progressPercentage: 83.98, targetDate: '2027-01-01', status: 'ACTIVE', createdAt: '2025-01-01T00:00:00' },
  { id: '2', name: 'Carteira R$ 100.000', targetAmount: 100000, currentAmount: 64256.33, progressPercentage: 64.26, targetDate: '2028-01-01', status: 'ACTIVE', createdAt: '2025-06-01T00:00:00' },
  { id: '3', name: 'Renda Passiva R$ 500/mês', targetAmount: 6000, currentAmount: 2711.17, progressPercentage: 45.19, targetDate: '2029-01-01', status: 'ACTIVE', createdAt: '2025-06-01T00:00:00' },
  { id: '4', name: 'Viagem Europa', targetAmount: 15000, currentAmount: 15000, progressPercentage: 100, targetDate: '2026-06-01', status: 'COMPLETED', createdAt: '2025-01-01T00:00:00' },
];
