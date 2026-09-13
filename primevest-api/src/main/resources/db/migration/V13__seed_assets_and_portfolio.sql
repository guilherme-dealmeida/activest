INSERT INTO portfolios (id, user_id, name, created_at) VALUES ('b1b2c3d4-e5f6-7890-abcd-ef1234567890', 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'Minha Carteira', NOW());

INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('CDB-121', 'PF Turbinado 121% CDI',
  (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'),
  (SELECT id FROM asset_categories WHERE name = 'CDB' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('CDB-100', 'PF Cashback 100% CDI',
  (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'),
  (SELECT id FROM asset_categories WHERE name = 'CDB' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('CDB-120F', 'Nu Turbo 120% CDI Flexi',
  (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'),
  (SELECT id FROM asset_categories WHERE name = 'CDB' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('PREV-CAIXA', 'Caixa FC Prev',
  (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'),
  (SELECT id FROM asset_categories WHERE name = 'PREVIDENCIA' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('PREF-2027', 'Prefixado 2027',
  (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO'),
  (SELECT id FROM asset_categories WHERE name = 'PREFIXADO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('SELIC-2028', 'Selic 2028',
  (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO'),
  (SELECT id FROM asset_categories WHERE name = 'SELIC' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('SELIC-2029', 'Selic 2029',
  (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO'),
  (SELECT id FROM asset_categories WHERE name = 'SELIC' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('IPCA-2029', 'IPCA+ 2029',
  (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO'),
  (SELECT id FROM asset_categories WHERE name = 'IPCA_PLUS' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('IPCA-2035', 'IPCA+ 2035',
  (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO'),
  (SELECT id FROM asset_categories WHERE name = 'IPCA_PLUS' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('BTLG11', 'BTG Logístico',
  (SELECT id FROM asset_types WHERE name = 'FII'),
  (SELECT id FROM asset_categories WHERE name = 'TIJOLO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'FII')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('GARE11', 'Guardian RE',
  (SELECT id FROM asset_types WHERE name = 'FII'),
  (SELECT id FROM asset_categories WHERE name = 'TIJOLO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'FII')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('GGRC11', 'GGR Covepi',
  (SELECT id FROM asset_types WHERE name = 'FII'),
  (SELECT id FROM asset_categories WHERE name = 'TIJOLO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'FII')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('HGLG11', 'CSHG Logística',
  (SELECT id FROM asset_types WHERE name = 'FII'),
  (SELECT id FROM asset_categories WHERE name = 'TIJOLO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'FII')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('MXRF11', 'Maxi Renda',
  (SELECT id FROM asset_types WHERE name = 'FII'),
  (SELECT id FROM asset_categories WHERE name = 'PAPEL' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'FII')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('BBRV11', 'BB Renda Corp',
  (SELECT id FROM asset_types WHERE name = 'FII'),
  (SELECT id FROM asset_categories WHERE name = 'PAPEL' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'FII')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('RZTR11', 'Riza Terrax',
  (SELECT id FROM asset_types WHERE name = 'FII'),
  (SELECT id FROM asset_categories WHERE name = 'TIJOLO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'FII')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('VGHF11', 'Valora Hedge Fund',
  (SELECT id FROM asset_types WHERE name = 'FII'),
  (SELECT id FROM asset_categories WHERE name = 'PAPEL' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'FII')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('BBAS3', 'Banco do Brasil',
  (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'),
  (SELECT id FROM asset_categories WHERE name = 'BANCO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('BBSE3', 'BB Seguridade',
  (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'),
  (SELECT id FROM asset_categories WHERE name = 'SEGURO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('BRBI11', 'Inter & Co',
  (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'),
  (SELECT id FROM asset_categories WHERE name = 'BANCO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('CXSE3', 'Caixa Seguridade',
  (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'),
  (SELECT id FROM asset_categories WHERE name = 'SEGURO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('SAEE4', 'Sabesp',
  (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'),
  (SELECT id FROM asset_categories WHERE name = 'ENERGIA' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('ITSA4', 'Itaúsa',
  (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'),
  (SELECT id FROM asset_categories WHERE name = 'FINANCEIRO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('KLBN4', 'Klabin',
  (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'),
  (SELECT id FROM asset_categories WHERE name = 'COMMODITY' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('PETR4', 'Petrobras PN',
  (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'),
  (SELECT id FROM asset_categories WHERE name = 'COMMODITY' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('SAPR4', 'Sanepar',
  (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'),
  (SELECT id FROM asset_categories WHERE name = 'SANEAMENTO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('TIMS3', 'TIM',
  (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'),
  (SELECT id FROM asset_categories WHERE name = 'TELEFONIA' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('IVVB11', 'iShares S&P 500',
  (SELECT id FROM asset_types WHERE name = 'INTERNACIONAL'),
  (SELECT id FROM asset_categories WHERE name = 'ETF' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'INTERNACIONAL')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('BTC', 'Bitcoin',
  (SELECT id FROM asset_types WHERE name = 'CRYPTO'),
  (SELECT id FROM asset_categories WHERE name = 'BLOCKCHAIN' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'CRYPTO')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('NU 100% CDI', 'Nu 100% CDI',
  (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'),
  (SELECT id FROM asset_categories WHERE name = 'CDB' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('NU 120% CDI', 'Nu 120% CDI Flexi',
  (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'),
  (SELECT id FROM asset_categories WHERE name = 'CDB' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('NU 200% CDI', 'Nu 200% CDI',
  (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'),
  (SELECT id FROM asset_categories WHERE name = 'CDB' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('PP 121% CDI', 'PicPay 121% CDI',
  (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'),
  (SELECT id FROM asset_categories WHERE name = 'CDB' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_FIXA')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('VISC11', 'Vinci Shopping Centers',
  (SELECT id FROM asset_types WHERE name = 'FII'),
  (SELECT id FROM asset_categories WHERE name = 'TIJOLO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'FII')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('TRXF11', 'TRX Real Estate',
  (SELECT id FROM asset_types WHERE name = 'FII'),
  (SELECT id FROM asset_categories WHERE name = 'TIJOLO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'FII')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('XPML11', 'XP Malls',
  (SELECT id FROM asset_types WHERE name = 'FII'),
  (SELECT id FROM asset_categories WHERE name = 'TIJOLO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'FII')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('RBRY11', 'RBR Yield',
  (SELECT id FROM asset_types WHERE name = 'FII'),
  (SELECT id FROM asset_categories WHERE name = 'PAPEL' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'FII')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('PREFIXADO 2032', 'Tesouro Prefixado 2032',
  (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO'),
  (SELECT id FROM asset_categories WHERE name = 'PREFIXADO' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('IPCA+ 2040', 'Tesouro IPCA+ 2040',
  (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO'),
  (SELECT id FROM asset_categories WHERE name = 'IPCA_PLUS' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('IPCA+ 2050', 'Tesouro IPCA+ 2050',
  (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO'),
  (SELECT id FROM asset_categories WHERE name = 'IPCA_PLUS' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('ISAE4', 'ISA Energia',
  (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'),
  (SELECT id FROM asset_categories WHERE name = 'ENERGIA' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL')),
  true);
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id, active)
VALUES ('CMIG4', 'Cemig',
  (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'),
  (SELECT id FROM asset_categories WHERE name = 'ENERGIA' AND asset_type_id = (SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL')),
  true);

INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000001', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'CDB-121'), 1, 5860.21, 5860.21, true, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000002', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'CDB-100'), 1, 5249.91, 5249.91, true, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000003', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'CDB-120F'), 1, 2440, 2440, true, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000004', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'PREV-CAIXA'), 1, 2681.48, 2681.48, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000005', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'PREF-2027'), 1.5, 5642.38, 5642.38, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000006', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'SELIC-2028'), 0.2, 5447.34, 5447.34, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000007', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'SELIC-2029'), 0.2, 3158.54, 3158.54, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000008', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'IPCA-2029'), 0.39, 1258.77, 1258.77, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000009', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'IPCA-2035'), 1.5, 3118.42, 3118.42, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000010', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'BTLG11'), 25, 2545.65, 2545.65, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000011', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'GARE11'), 200, 1719, 1719, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000012', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'GGRC11'), 150, 1474.5, 1474.5, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000013', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'HGLG11'), 25, 3772.41, 3772.41, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000014', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'MXRF11'), 11, 1001.86, 1001.86, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000015', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'BBRV11'), 5, 788.7, 788.7, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000016', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'RZTR11'), 15, 2543.35, 2543.35, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000017', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'VGHF11'), 87, 3321.08, 3321.08, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000018', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'BBAS3'), 175, 3843.54, 3843.54, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000019', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'BBSE3'), 3, 1202, 1202, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000020', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'BRBI11'), 150, 1402, 1402, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000021', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'CXSE3'), 10, 1567.5, 1567.5, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000022', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'SAEE4'), 20, 581.05, 581.05, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000023', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'ITSA4'), 150, 2106.45, 2106.45, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000024', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'KLBN4'), 600, 2078.74, 2078.74, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000025', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'PETR4'), 95, 1528, 1528, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000026', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'SAPR4'), 100, 563, 563, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000027', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'TIMS3'), 5, 563, 563, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000028', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'IVVB11'), 5, 2391.12, 2391.12, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000029', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'BTC'), 0.0083, 3399.5, 3399.5, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000030', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'NU 100% CDI'), 0, 0, 0, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000031', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'NU 120% CDI'), 0, 0, 0, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000032', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'NU 200% CDI'), 0, 0, 0, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000033', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'PP 121% CDI'), 0, 0, 0, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000034', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'VISC11'), 0, 0, 0, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000035', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'TRXF11'), 0, 0, 0, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000036', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'XPML11'), 0, 0, 0, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000037', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'RBRY11'), 0, 0, 0, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000038', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'PREFIXADO 2032'), 0, 0, 0, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000039', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'IPCA+ 2040'), 0, 0, 0, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000040', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'IPCA+ 2050'), 0, 0, 0, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000041', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'ISAE4'), 0, 0, 0, false, NOW());
INSERT INTO portfolio_assets (id, portfolio_id, asset_id, total_shares, average_price, total_invested, is_reservation, created_at)
VALUES ('c0000001-0000-0000-0000-000000000042', 'b1b2c3d4-e5f6-7890-abcd-ef1234567890', (SELECT id FROM assets WHERE ticker = 'CMIG4'), 0, 0, 0, false, NOW());
