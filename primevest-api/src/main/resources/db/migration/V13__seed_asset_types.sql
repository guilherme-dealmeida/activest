-- Tipos de ativo. Tabela de referência: a aplicação não funciona sem ela
-- (Estratégia, Alocação e o cadastro de transação dependem desses registros).
--
-- ON CONFLICT sobre name torna a migration idempotente e permite reexecutar
-- em bases que já tenham parte dos tipos, atualizando os demais campos.
INSERT INTO asset_types (name, description, color_hex, price_source, fixed_income) VALUES
    ('ACAO',               'Ações',                   '#FF0000', 'BRAPI',     false),
    ('FII',                'FIIs',                    '#008000', 'BRAPI',     false),
    ('RENDA_FIXA',         'Renda Fixa',              '#0000FF', 'MANUAL',    true),
    ('TESOURO_DIRETO',     'Tesouro Direto',          '#FFFF00', 'MANUAL',    true),
    ('ETF',                'ETFs',                    '#800080', 'BRAPI',     false),
    ('FUNDO_INVESTIMENTO', 'Fundos de Investimentos', '#A52A2A', 'MANUAL',    false),
    ('BDR',                'BDRs',                    '#FF00FF', 'BRAPI',     false),
    ('STOCK',              'Stocks',                  '#800000', 'MANUAL',    false),
    ('REIT',               'REITs',                   '#5C4033', 'MANUAL',    false),
    ('ETF_INTERNACIONAL',  'ETFs Internacionais',     '#4B0082', 'MANUAL',    false),
    ('CRIPTO',             'Criptomoedas',            '#FFA500', 'COINGECKO', false),
    ('OUTROS',             'Outros',                  '#00FFFF', 'MANUAL',    false)
ON CONFLICT (name) DO UPDATE
    SET description  = EXCLUDED.description,
        color_hex    = EXCLUDED.color_hex,
        price_source = EXCLUDED.price_source,
        fixed_income = EXCLUDED.fixed_income;
