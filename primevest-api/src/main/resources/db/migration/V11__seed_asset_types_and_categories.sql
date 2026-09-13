INSERT INTO asset_types (name, description, color_hex) VALUES 
('RENDA_FIXA', 'Renda Fixa', '#8B4513'),
('TESOURO_DIRETO', 'Tesouro Direto', '#4A90D9'),
('FII', 'Fundos Imobiliários', '#2D5F2D'),
('RENDA_VARIAVEL', 'Renda Variável (Ações)', '#C41E3A'),
('INTERNACIONAL', 'Investimentos Internacionais', '#7B68EE'),
('CRYPTO', 'Criptomoedas', '#FF8C00');

-- RENDA_FIXA Categories
INSERT INTO asset_categories (asset_type_id, name, description) VALUES
((SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'), 'CDB', 'Certificado de Depósito Bancário'),
((SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'), 'LCI', 'Letra de Crédito Imobiliário'),
((SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'), 'LCA', 'Letra de Crédito do Agronegócio'),
((SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'), 'CRI', 'Certificado de Recebíveis Imobiliários'),
((SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'), 'CRA', 'Certificado de Recebíveis do Agronegócio'),
((SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'), 'DEBENTURE', 'Debêntures'),
((SELECT id FROM asset_types WHERE name = 'RENDA_FIXA'), 'PREVIDENCIA', 'Previdência Privada');

-- TESOURO_DIRETO Categories
INSERT INTO asset_categories (asset_type_id, name, description) VALUES
((SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO'), 'SELIC', 'Tesouro Selic'),
((SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO'), 'IPCA_PLUS', 'Tesouro IPCA+'),
((SELECT id FROM asset_types WHERE name = 'TESOURO_DIRETO'), 'PREFIXADO', 'Tesouro Prefixado');

-- FII Categories
INSERT INTO asset_categories (asset_type_id, name, description) VALUES
((SELECT id FROM asset_types WHERE name = 'FII'), 'TIJOLO', 'Fundos de Tijolo'),
((SELECT id FROM asset_types WHERE name = 'FII'), 'PAPEL', 'Fundos de Papel'),
((SELECT id FROM asset_types WHERE name = 'FII'), 'FUNDO_DE_FUNDOS', 'Fundos de Fundos (FOF)'),
((SELECT id FROM asset_types WHERE name = 'FII'), 'HIBRIDO', 'Fundos Híbridos');

-- RENDA_VARIAVEL Categories
INSERT INTO asset_categories (asset_type_id, name, description) VALUES
((SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'), 'BANCO', 'Bancos'),
((SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'), 'COMMODITY', 'Commodities'),
((SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'), 'SEGURO', 'Seguros'),
((SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'), 'ENERGIA', 'Energia'),
((SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'), 'FINANCEIRO', 'Setor Financeiro'),
((SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'), 'TELEFONIA', 'Telefonia'),
((SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'), 'SANEAMENTO', 'Saneamento'),
((SELECT id FROM asset_types WHERE name = 'RENDA_VARIAVEL'), 'TECNOLOGIA', 'Tecnologia');

-- INTERNACIONAL Categories
INSERT INTO asset_categories (asset_type_id, name, description) VALUES
((SELECT id FROM asset_types WHERE name = 'INTERNACIONAL'), 'ETF', 'Exchange Traded Funds'),
((SELECT id FROM asset_types WHERE name = 'INTERNACIONAL'), 'BDR', 'Brazilian Depositary Receipts');

-- CRYPTO Categories
INSERT INTO asset_categories (asset_type_id, name, description) VALUES
((SELECT id FROM asset_types WHERE name = 'CRYPTO'), 'BLOCKCHAIN', 'Blockchain / Criptoativos');
