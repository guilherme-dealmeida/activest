-- Produtos genéricos de renda fixa.
--
-- Estes 15 são o PRODUTO, não o papel. O que varia entre duas aplicações num
-- CDB — banco, vencimento, taxa — é característica da compra e mora em
-- fixed_income_notes. Sem eles, cada aplicação viraria um ativo novo num
-- catálogo que é global: "CDB BTG 2027" apareceria na busca de todo mundo.
--
-- Ticker, nome e categoria são literais: este arquivo é o estado final do
-- catálogo, depois de todo o trabalho de classificação. Tipo e categoria são
-- resolvidos por nome — ids de BIGSERIAL não sobrevivem a um banco novo.
--
-- ON CONFLICT DO NOTHING em vez de DO UPDATE: se já existir um ativo com um
-- destes tickers, ele pode ter posição atrelada, e sobrescrever nome ou
-- categoria de um ativo em uso seria pior que não inserir.
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id)
SELECT p.ticker, p.nome, t.id, c.id
FROM (VALUES
    ('CCB'::varchar,                    'Cédula de Crédito Bancário'::varchar,                'CCB'::varchar),
    ('CDB',                             'Certificado de Depósito Bancário',                   'CDB'),
    ('CRA',                             'Certificado de Recebíveis do Agronegócio',           'CRA'),
    ('CRI',                             'Certificado de Recebíveis Imobiliários',             'CRI'),
    ('DEBENTURE',                       'Debênture',                                          'DEBENTURE'),
    ('DEBENTURE_INCENTIVADA',           'Debênture Incentivada',                              'DEBENTURE_INCENTIVADA'),
    ('DPGE',                            'Depósito a Prazo com Garantia Especial',             'DPGE'),
    ('LC',                              'Letra de Câmbio',                                    'LC'),
    ('LCA',                             'Letra de Crédito do Agronegócio',                    'LCA'),
    ('LCI',                             'Letra de Crédito Imobiliário',                       'LCI'),
    ('LF',                              'Letra Financeira',                                   'LF'),
    ('LIG',                             'Letra Imobiliária Garantida',                        'LIG'),
    ('NOTA_COMERCIAL',                  'Nota Comercial',                                     'NOTA_COMERCIAL'),
    ('POUPANCA',                        'Poupança',                                           'POUPANCA'),
    ('RDB',                             'Recibo de Depósito Bancário',                        'RDB')
) AS p(ticker, nome, categoria)
JOIN asset_types      t ON t.name = 'RENDA_FIXA'
JOIN asset_categories c ON c.asset_type_id = t.id
                       AND c.name = p.categoria
ON CONFLICT (ticker) DO NOTHING;
