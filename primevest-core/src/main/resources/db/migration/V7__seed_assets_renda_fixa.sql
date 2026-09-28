-- Produtos de renda fixa — 15 ativos.
--
-- Ticker, nome e categoria sao literais: este arquivo e o estado final do
-- catalogo, depois de todo o trabalho de classificacao. Tipo e categoria sao
-- resolvidos por NOME — ids de BIGSERIAL nao sobrevivem a um banco novo.
--
-- A categoria e resolvida pelo PAR (nome, segmento), nao so pelo nome: uma
-- categoria e esse par dentro do tipo, e nome sozinho e ambiguo — 'CONSUMO_CICLICO'
-- aparece em 21 segmentos diferentes. O IS NOT DISTINCT FROM cobre o segmento
-- nulo dos tipos que nao tem segundo nivel.
--
-- ON CONFLICT DO NOTHING, e nao DO UPDATE: se ja existir um ativo com um destes
-- tickers, ele pode ter posicao atrelada, e sobrescrever nome ou categoria de um
-- ativo em uso e pior que nao inserir.
--
-- Aqui o ativo e o PRODUTO generico ("CDB"), nao a aplicacao. Emissor,
-- vencimento e taxa de cada compra ficam em fixed_income_notes, que pertence ao
-- usuario. Era exatamente isto que faltava quando "CDB BTG 2027" virava ativo
-- global.
INSERT INTO assets (ticker, name, asset_type_id, asset_category_id)
SELECT v.ticker::varchar, v.name::varchar, t.id, c.id
FROM (VALUES
    ('CCB', 'Cédula de Crédito Bancário', 'CCB', NULL),
    ('CDB', 'Certificado de Depósito Bancário', 'CDB', NULL),
    ('CRA', 'Certificado de Recebíveis do Agronegócio', 'CRA', NULL),
    ('CRI', 'Certificado de Recebíveis Imobiliários', 'CRI', NULL),
    ('DEBENTURE', 'Debênture', 'DEBENTURE', NULL),
    ('DEBENTURE_INCENTIVADA', 'Debênture Incentivada', 'DEBENTURE_INCENTIVADA', NULL),
    ('DPGE', 'Depósito a Prazo com Garantia Especial', 'DPGE', NULL),
    ('LC', 'Letra de Câmbio', 'LC', NULL),
    ('LCA', 'Letra de Crédito do Agronegócio', 'LCA', NULL),
    ('LCI', 'Letra de Crédito Imobiliário', 'LCI', NULL),
    ('LF', 'Letra Financeira', 'LF', NULL),
    ('LIG', 'Letra Imobiliária Garantida', 'LIG', NULL),
    ('NOTA_COMERCIAL', 'Nota Comercial', 'NOTA_COMERCIAL', NULL),
    ('POUPANCA', 'Poupança', 'POUPANCA', NULL),
    ('RDB', 'Recibo de Depósito Bancário', 'RDB', NULL)
) AS v(ticker, name, cat_name, cat_segment)
JOIN asset_types t ON t.name = 'RENDA_FIXA'
LEFT JOIN asset_categories c
       ON c.asset_type_id = t.id
      AND c.name    IS NOT DISTINCT FROM v.cat_name::varchar
      AND c.segment IS NOT DISTINCT FROM v.cat_segment::varchar
ON CONFLICT (ticker) DO NOTHING;
