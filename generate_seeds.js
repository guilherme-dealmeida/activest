const fs = require('fs');
const path = require('path');

const outDir = 'C:\\Users\\guilh\\.gemini\\antigravity\\scratch\\primevest\\primevest-api\\src\\main\\resources\\db\\migration';

// User UUID
const userId = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';
const portfolioId = 'b1b2c3d4-e5f6-7890-abcd-ef1234567890';

// We have the mock arrays from data.ts, we can just copy-paste them directly into this script to parse.
// PortfolioAssets:
const mockPortfolioAssets = [
  { id: '1', ticker: 'CDB-121', assetName: 'PF Turbinado 121% CDI', typeName: 'RENDA_FIXA', categoryName: 'CDB', totalShares: 1, averagePrice: 5860.21, totalInvested: 5860.21, isReservation: true },
  { id: '2', ticker: 'CDB-100', assetName: 'PF Cashback 100% CDI', typeName: 'RENDA_FIXA', categoryName: 'CDB', totalShares: 1, averagePrice: 5249.91, totalInvested: 5249.91, isReservation: true },
  { id: '3', ticker: 'CDB-120F', assetName: 'Nu Turbo 120% CDI Flexi', typeName: 'RENDA_FIXA', categoryName: 'CDB', totalShares: 1, averagePrice: 2440.00, totalInvested: 2440.00, isReservation: true },
  { id: '4', ticker: 'PREV-CAIXA', assetName: 'Caixa FC Prev', typeName: 'RENDA_FIXA', categoryName: 'PREVIDENCIA', totalShares: 1, averagePrice: 2681.48, totalInvested: 2681.48, isReservation: false },
  { id: '5', ticker: 'PREF-2027', assetName: 'Prefixado 2027', typeName: 'TESOURO_DIRETO', categoryName: 'PREFIXADO', totalShares: 1.5, averagePrice: 5642.38, totalInvested: 5642.38, isReservation: false },
  { id: '6', ticker: 'SELIC-2028', assetName: 'Selic 2028', typeName: 'TESOURO_DIRETO', categoryName: 'SELIC', totalShares: 0.2, averagePrice: 5447.34, totalInvested: 5447.34, isReservation: false },
  { id: '7', ticker: 'SELIC-2029', assetName: 'Selic 2029', typeName: 'TESOURO_DIRETO', categoryName: 'SELIC', totalShares: 0.2, averagePrice: 3158.54, totalInvested: 3158.54, isReservation: false },
  { id: '8', ticker: 'IPCA-2029', assetName: 'IPCA+ 2029', typeName: 'TESOURO_DIRETO', categoryName: 'IPCA_PLUS', totalShares: 0.39, averagePrice: 1258.77, totalInvested: 1258.77, isReservation: false },
  { id: '9', ticker: 'IPCA-2035', assetName: 'IPCA+ 2035', typeName: 'TESOURO_DIRETO', categoryName: 'IPCA_PLUS', totalShares: 1.5, averagePrice: 3118.42, totalInvested: 3118.42, isReservation: false },
  { id: '10', ticker: 'BTLG11', assetName: 'BTG Logístico', typeName: 'FII', categoryName: 'TIJOLO', totalShares: 25, averagePrice: 2545.65, totalInvested: 2545.65, isReservation: false },
  { id: '11', ticker: 'GARE11', assetName: 'Guardian RE', typeName: 'FII', categoryName: 'TIJOLO', totalShares: 200, averagePrice: 1719.00, totalInvested: 1719.00, isReservation: false },
  { id: '12', ticker: 'GGRC11', assetName: 'GGR Covepi', typeName: 'FII', categoryName: 'TIJOLO', totalShares: 150, averagePrice: 1474.50, totalInvested: 1474.50, isReservation: false },
  { id: '13', ticker: 'HGLG11', assetName: 'CSHG Logística', typeName: 'FII', categoryName: 'TIJOLO', totalShares: 25, averagePrice: 3772.41, totalInvested: 3772.41, isReservation: false },
  { id: '14', ticker: 'MXRF11', assetName: 'Maxi Renda', typeName: 'FII', categoryName: 'PAPEL', totalShares: 11, averagePrice: 1001.86, totalInvested: 1001.86, isReservation: false },
  { id: '15', ticker: 'BBRV11', assetName: 'BB Renda Corp', typeName: 'FII', categoryName: 'PAPEL', totalShares: 5, averagePrice: 788.70, totalInvested: 788.70, isReservation: false },
  { id: '16', ticker: 'RZTR11', assetName: 'Riza Terrax', typeName: 'FII', categoryName: 'TIJOLO', totalShares: 15, averagePrice: 2543.35, totalInvested: 2543.35, isReservation: false },
  { id: '17', ticker: 'VGHF11', assetName: 'Valora Hedge Fund', typeName: 'FII', categoryName: 'PAPEL', totalShares: 87, averagePrice: 3321.08, totalInvested: 3321.08, isReservation: false },
  { id: '18', ticker: 'BBAS3', assetName: 'Banco do Brasil', typeName: 'RENDA_VARIAVEL', categoryName: 'BANCO', totalShares: 175, averagePrice: 3843.54, totalInvested: 3843.54, isReservation: false },
  { id: '19', ticker: 'BBSE3', assetName: 'BB Seguridade', typeName: 'RENDA_VARIAVEL', categoryName: 'SEGURO', totalShares: 3, averagePrice: 1202.00, totalInvested: 1202.00, isReservation: false },
  { id: '20', ticker: 'BRBI11', assetName: 'Inter & Co', typeName: 'RENDA_VARIAVEL', categoryName: 'BANCO', totalShares: 150, averagePrice: 1402.00, totalInvested: 1402.00, isReservation: false },
  { id: '21', ticker: 'CXSE3', assetName: 'Caixa Seguridade', typeName: 'RENDA_VARIAVEL', categoryName: 'SEGURO', totalShares: 10, averagePrice: 1567.50, totalInvested: 1567.50, isReservation: false },
  { id: '22', ticker: 'SAEE4', assetName: 'Sabesp', typeName: 'RENDA_VARIAVEL', categoryName: 'ENERGIA', totalShares: 20, averagePrice: 581.05, totalInvested: 581.05, isReservation: false },
  { id: '23', ticker: 'ITSA4', assetName: 'Itaúsa', typeName: 'RENDA_VARIAVEL', categoryName: 'FINANCEIRO', totalShares: 150, averagePrice: 2106.45, totalInvested: 2106.45, isReservation: false },
  { id: '24', ticker: 'KLBN4', assetName: 'Klabin', typeName: 'RENDA_VARIAVEL', categoryName: 'COMMODITY', totalShares: 600, averagePrice: 2078.74, totalInvested: 2078.74, isReservation: false },
  { id: '25', ticker: 'PETR4', assetName: 'Petrobras PN', typeName: 'RENDA_VARIAVEL', categoryName: 'COMMODITY', totalShares: 95, averagePrice: 1528.00, totalInvested: 1528.00, isReservation: false },
  { id: '26', ticker: 'SAPR4', assetName: 'Sanepar', typeName: 'RENDA_VARIAVEL', categoryName: 'SANEAMENTO', totalShares: 100, averagePrice: 563.00, totalInvested: 563.00, isReservation: false },
  { id: '27', ticker: 'TIMS3', assetName: 'TIM', typeName: 'RENDA_VARIAVEL', categoryName: 'TELEFONIA', totalShares: 5, averagePrice: 563.00, totalInvested: 563.00, isReservation: false },
  { id: '28', ticker: 'IVVB11', assetName: 'iShares S&P 500', typeName: 'INTERNACIONAL', categoryName: 'ETF', totalShares: 5, averagePrice: 2391.12, totalInvested: 2391.12, isReservation: false },
  { id: '29', ticker: 'BTC', assetName: 'Bitcoin', typeName: 'CRYPTO', categoryName: 'BLOCKCHAIN', totalShares: 0.0083, averagePrice: 3399.50, totalInvested: 3399.50, isReservation: false },
  // Adding assets from transactions that are not in the portfolio initially
  { ticker: 'NU 100% CDI', assetName: 'Nu 100% CDI', typeName: 'RENDA_FIXA', categoryName: 'CDB' },
  { ticker: 'NU 120% CDI', assetName: 'Nu 120% CDI Flexi', typeName: 'RENDA_FIXA', categoryName: 'CDB' },
  { ticker: 'NU 200% CDI', assetName: 'Nu 200% CDI', typeName: 'RENDA_FIXA', categoryName: 'CDB' },
  { ticker: 'PP 121% CDI', assetName: 'PicPay 121% CDI', typeName: 'RENDA_FIXA', categoryName: 'CDB' },
  { ticker: 'VISC11', assetName: 'Vinci Shopping Centers', typeName: 'FII', categoryName: 'TIJOLO' },
  { ticker: 'TRXF11', assetName: 'TRX Real Estate', typeName: 'FII', categoryName: 'TIJOLO' },
  { ticker: 'XPML11', assetName: 'XP Malls', typeName: 'FII', categoryName: 'TIJOLO' },
  { ticker: 'RBRY11', assetName: 'RBR Yield', typeName: 'FII', categoryName: 'PAPEL' },
  { ticker: 'PREFIXADO 2032', assetName: 'Tesouro Prefixado 2032', typeName: 'TESOURO_DIRETO', categoryName: 'PREFIXADO' },
  { ticker: 'IPCA+ 2040', assetName: 'Tesouro IPCA+ 2040', typeName: 'TESOURO_DIRETO', categoryName: 'IPCA_PLUS' },
  { ticker: 'IPCA+ 2050', assetName: 'Tesouro IPCA+ 2050', typeName: 'TESOURO_DIRETO', categoryName: 'IPCA_PLUS' },
  { ticker: 'ISAE4', assetName: 'ISA Energia', typeName: 'RENDA_VARIAVEL', categoryName: 'ENERGIA' },
  { ticker: 'CMIG4', assetName: 'Cemig', typeName: 'RENDA_VARIAVEL', categoryName: 'ENERGIA' }
];

// Unique tickers mapped to UUIDs for portfolio_assets
let seq = 1;
function genUUID() {
  return `c0000001-0000-0000-0000-${seq.toString().padStart(12, '0')}`;
}

const uniqueAssetsMap = new Map();
for (const item of mockPortfolioAssets) {
  if (!uniqueAssetsMap.has(item.ticker)) {
    const isRes = item.isReservation === undefined ? false : item.isReservation;
    const paId = genUUID();
    seq++;
    uniqueAssetsMap.set(item.ticker, { ...item, paId, totalShares: item.totalShares || 0, averagePrice: item.averagePrice || 0, totalInvested: item.totalInvested || 0, isReservation: isRes });
  }
}

// Generate V13 (Assets and Portfolio)
let v13 = `INSERT INTO portfolios (id, user_id, name, created_at) VALUES ('${portfolioId}', '${userId}', 'Minha Carteira', NOW());\n\n`;

for (const [ticker, item] of uniqueAssetsMap.entries()) {
  v13 += `INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('${ticker}', '${item.assetName}',
  (SELECT id FROM asset_types WHERE name = '${item.typeName}'),
  (SELECT id FROM asset_categories WHERE name = '${item.categoryName}' AND asset_type_id = (SELECT id FROM asset_types WHERE name = '${item.typeName}')),
  true);\n`;
}
v13 += '\n';

for (const [ticker, item] of uniqueAssetsMap.entries()) {
  v13 += `INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('${item.paId}', '${portfolioId}', (SELECT id FROM assets WHERE ticker = '${ticker}'), ${item.totalShares}, ${item.averagePrice}, ${item.totalInvested}, ${item.isReservation}, NOW());\n`;
}

fs.writeFileSync(path.join(outDir, 'V13__seed_assets_and_portfolio.sql'), v13);


// V14 Transactions
const v14Src = fs.readFileSync('C:\\Users\\guilh\\.gemini\\antigravity\\scratch\\primevest\\primevest-web\\src\\mocks\\data.ts', 'utf8');
const txMatch = v14Src.match(/export const mockTransactions.*?=\s*\[(.*?)\];/s);
let v14 = '';
if (txMatch) {
  // Use regex to parse out individual transactions
  const txRegex = /id:\s*'\d+',\s*ticker:\s*'([^']+)',.*?type:\s*'([^']+)',\s*shares:\s*([\d.]+),\s*pricePerShare:\s*([\d.]+),\s*totalAmount:\s*([\d.]+),\s*brokerageFee:\s*([\d.]+),\s*transactionDate:\s*'([^']+)'/g;
  let match;
  while ((match = txRegex.exec(txMatch[1])) !== null) {
    const ticker = match[1];
    let type = match[2];
    const shares = match[3];
    const pricePerShare = match[4];
    const totalAmount = match[5];
    const brokerageFee = match[6];
    const transactionDate = match[7];
    
    // Some tickers might mismatch (e.g. "IPCA+ 2029" vs "IPCA-2029")
    // In mock transactions, they are named differently sometimes.
    let mappedTicker = ticker;
    if (ticker === 'PREFIXADO 2027') mappedTicker = 'PREF-2027';
    if (ticker === 'SELIC 2028') mappedTicker = 'SELIC-2028';
    if (ticker === 'SELIC 2029') mappedTicker = 'SELIC-2029';
    if (ticker === 'IPCA+ 2029') mappedTicker = 'IPCA-2029';
    if (ticker === 'IPCA+ 2035') mappedTicker = 'IPCA-2035';
    
    if (!uniqueAssetsMap.has(mappedTicker)) {
      console.error('Ticker not found in unique assets!', mappedTicker);
      process.exit(1);
    }
    
    const paId = uniqueAssetsMap.get(mappedTicker).paId;
    v14 += `INSERT INTO transactions (id, portfolio_asset_id, type, shares, price_per_share, total_amount, brokerage_fee, transaction_date, created_at)
VALUES (gen_random_uuid(), '${paId}', '${type}', ${shares}, ${pricePerShare}, ${totalAmount}, ${brokerageFee}, '${transactionDate}', NOW());\n`;
  }
}
fs.writeFileSync(path.join(outDir, 'V14__seed_transactions.sql'), v14);

// V15 Ceiling Prices
const ceilingData = [
  { ticker: 'PETR4', max: 32.00, alert: true },
  { ticker: 'BBAS3', max: 25.00, alert: true },
  { ticker: 'ITSA4', max: 12.00, alert: true },
  { ticker: 'HGLG11', max: 160.00, alert: true },
  { ticker: 'BTLG11', max: 105.00, alert: false },
  { ticker: 'KLBN4', max: 4.50, alert: true }
];
let v15 = '';
for (const c of ceilingData) {
  v15 += `INSERT INTO ceiling_prices (id, user_id, asset_id, max_price, alert_enabled, created_at)
VALUES (gen_random_uuid(), '${userId}', (SELECT id FROM assets WHERE ticker = '${c.ticker}'), ${c.max}, ${c.alert}, NOW());\n`;
}
fs.writeFileSync(path.join(outDir, 'V15__seed_ceiling_prices.sql'), v15);

// V16 Dividends
const divData = [
  { ticker: 'PETR4', amountPerShare: 0.85, totalAmount: 80.75, exDate: '2026-08-20', paymentDate: '2026-09-05', type: 'DIVIDENDO' },
  { ticker: 'BBAS3', amountPerShare: 0.42, totalAmount: 73.50, exDate: '2026-08-15', paymentDate: '2026-09-01', type: 'DIVIDENDO' },
  { ticker: 'HGLG11', amountPerShare: 1.10, totalAmount: 27.50, exDate: '2026-08-10', paymentDate: '2026-08-20', type: 'RENDIMENTO' },
  { ticker: 'BTLG11', amountPerShare: 0.78, totalAmount: 19.50, exDate: '2026-08-10', paymentDate: '2026-08-20', type: 'RENDIMENTO' },
  { ticker: 'KLBN4', amountPerShare: 0.08, totalAmount: 48.00, exDate: '2026-07-25', paymentDate: '2026-08-10', type: 'JCP' },
  { ticker: 'ITSA4', amountPerShare: 0.15, totalAmount: 22.50, exDate: '2026-07-20', paymentDate: '2026-08-05', type: 'JCP' }
];
let v16 = '';
for (const d of divData) {
  const paId = uniqueAssetsMap.get(d.ticker).paId;
  v16 += `INSERT INTO dividends (asset_id, portfolio_asset_id, amount_per_share, total_amount, ex_date, payment_date, type, created_at)
VALUES ((SELECT id FROM assets WHERE ticker = '${d.ticker}'), '${paId}', ${d.amountPerShare}, ${d.totalAmount}, '${d.exDate}', '${d.paymentDate}', '${d.type}', NOW());\n`;
}
fs.writeFileSync(path.join(outDir, 'V16__seed_dividends.sql'), v16);

// V17 Goals
let v17 = `
INSERT INTO goals (id, user_id, name, target_amount, current_amount, target_date, status, created_at) VALUES 
(gen_random_uuid(), '${userId}', 'Reserva de Emergência 6 meses', 30000, 25192.94, '2027-01-01', 'ACTIVE', NOW()),
(gen_random_uuid(), '${userId}', 'Carteira R$ 100.000', 100000, 64256.33, '2028-01-01', 'ACTIVE', NOW()),
(gen_random_uuid(), '${userId}', 'Renda Passiva R$ 500/mês', 6000, 2711.17, '2029-01-01', 'ACTIVE', NOW()),
(gen_random_uuid(), '${userId}', 'Viagem Europa', 15000, 15000, '2026-06-01', 'COMPLETED', NOW());
`;
fs.writeFileSync(path.join(outDir, 'V17__seed_goals.sql'), v17);

// V18 Strategies
let v18 = `
INSERT INTO strategies (id, user_id, asset_type_id, target_percentage, include_reservation, created_at) VALUES
(gen_random_uuid(), '${userId}', 1, 30.00, true, NOW()),
(gen_random_uuid(), '${userId}', 2, 30.00, false, NOW()),
(gen_random_uuid(), '${userId}', 3, 30.00, false, NOW()),
(gen_random_uuid(), '${userId}', 4, 30.00, false, NOW()),
(gen_random_uuid(), '${userId}', 5, 5.00, false, NOW()),
(gen_random_uuid(), '${userId}', 6, 5.00, false, NOW());
`;
fs.writeFileSync(path.join(outDir, 'V18__seed_strategies.sql'), v18);

console.log('Successfully generated scripts');
