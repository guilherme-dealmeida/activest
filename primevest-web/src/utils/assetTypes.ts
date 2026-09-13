// Maps ticker to asset type for display purposes
const TICKER_TYPE_MAP: Record<string, string> = {
  // Tesouro Direto
  'IPCA+ 2029': 'Tesouro Direto', 'IPCA+ 2035': 'Tesouro Direto', 'IPCA+ 2040': 'Tesouro Direto', 'IPCA+ 2050': 'Tesouro Direto',
  'PREFIXADO 2027': 'Tesouro Direto', 'PREFIXADO 2032': 'Tesouro Direto',
  'SELIC 2028': 'Tesouro Direto', 'SELIC 2029': 'Tesouro Direto',
  // Renda Fixa
  'NU 100% CDI': 'Renda Fixa', 'NU 120% CDI': 'Renda Fixa', 'NU 200% CDI': 'Renda Fixa',
  'PP 121% CDI': 'Renda Fixa',
  // Renda Variável
  'BBAS3': 'Renda Variável', 'CMIG4': 'Renda Variável', 'SAPR4': 'Renda Variável',
  'CXSE3': 'Renda Variável', 'ISAE4': 'Renda Variável', 'PETR4': 'Renda Variável',
  'KLBN4': 'Renda Variável', 'BBSE3': 'Renda Variável', 'ITSA4': 'Renda Variável',
  'TIMS3': 'Renda Variável',
  // FII
  'VGHF11': 'FII', 'MXRF11': 'FII', 'HGLG11': 'FII', 'VISC11': 'FII',
  'BTLG11': 'FII', 'RZTR11': 'FII', 'GARE11': 'FII', 'GGRC11': 'FII',
  'TRXF11': 'FII', 'XPML11': 'FII', 'RBRY11': 'FII', 'BRBI11': 'FII',
  // Internacional
  'IVVB11': 'Internacional',
  // Crypto
  'BTC': 'Crypto', 'ETH': 'Crypto',
};

export function getAssetType(ticker: string): string {
  return TICKER_TYPE_MAP[ticker] || 'Outros';
}

export function getAllAssetTypes(): string[] {
  return [...new Set(Object.values(TICKER_TYPE_MAP))].sort();
}
