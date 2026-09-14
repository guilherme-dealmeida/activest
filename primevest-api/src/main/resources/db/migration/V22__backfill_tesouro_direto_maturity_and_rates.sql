-- Preenche vencimento e remuneração dos 130 títulos do Tesouro Direto
-- carregados pela V20, que entraram só com ticker, nome e tipo.
--
-- Esses dados não dependem de cotação nem de quem comprou: são definidos pelo
-- Tesouro Nacional na emissão do título. Ficarem nulos obrigava a tela a
-- adivinhar o indexador a partir do nome — exatamente o acoplamento por string
-- que a coluna price_source eliminou do scheduler.
--
-- indexer_rate continua NULL de propósito: a taxa do Tesouro é travada no
-- momento da COMPRA e varia todo dia. Ela é característica da transação, não do
-- título. Um "Tesouro IPCA+ 2035" comprado a IPCA+6,20% e outro a IPCA+5,80%
-- são o mesmo papel — por isso a taxa não cabe no catálogo.

WITH td AS (
    SELECT a.id,
           a.name,
           RIGHT(a.name, 4)::int AS ano
    FROM assets a
    JOIN asset_types t ON t.id = a.asset_type_id
    WHERE t.name = 'TESOURO_DIRETO'
),
-- Datas que não saem de fórmula nenhuma.
--
-- LFT: o vencimento migrou ao longo da série — fevereiro/janeiro/junho nas
-- primeiras emissões, dia 7 de março entre 2012 e 2017, dia 1º de março a
-- partir de 2021. E o 2024 vence em SETEMBRO, não em março: é a exceção que
-- qualquer regra por ano erraria em silêncio.
--
-- NTN-C: cada emissão tem a sua data, sem padrão de calendário.
explicito (name, vencimento) AS (VALUES
    ('Tesouro Selic 2005'::varchar, DATE '2005-02-16'),
    ('Tesouro Selic 2006',          DATE '2006-01-18'),
    ('Tesouro Selic 2007',          DATE '2007-01-17'),
    ('Tesouro Selic 2008',          DATE '2008-06-18'),
    ('Tesouro Selic 2009',          DATE '2009-03-18'),
    ('Tesouro Selic 2010',          DATE '2010-03-17'),
    ('Tesouro Selic 2011',          DATE '2011-03-16'),
    ('Tesouro Selic 2012',          DATE '2012-03-07'),
    ('Tesouro Selic 2013',          DATE '2013-03-07'),
    ('Tesouro Selic 2014',          DATE '2014-03-07'),
    ('Tesouro Selic 2015',          DATE '2015-03-07'),
    ('Tesouro Selic 2017',          DATE '2017-03-07'),
    ('Tesouro Selic 2021',          DATE '2021-03-01'),
    ('Tesouro Selic 2023',          DATE '2023-03-01'),
    ('Tesouro Selic 2024',          DATE '2024-09-01'),
    ('Tesouro Selic 2025',          DATE '2025-03-01'),
    ('Tesouro Selic 2026',          DATE '2026-03-01'),
    ('Tesouro Selic 2027',          DATE '2027-03-01'),
    ('Tesouro Selic 2028',          DATE '2028-03-01'),
    ('Tesouro Selic 2029',          DATE '2029-03-01'),
    ('Tesouro Selic 2031',          DATE '2031-03-01'),

    ('Tesouro IGPM+ com Juros Semestrais 2005', DATE '2005-12-01'),
    ('Tesouro IGPM+ com Juros Semestrais 2006', DATE '2006-12-01'),
    ('Tesouro IGPM+ com Juros Semestrais 2008', DATE '2008-04-01'),
    ('Tesouro IGPM+ com Juros Semestrais 2011', DATE '2011-03-01'),
    ('Tesouro IGPM+ com Juros Semestrais 2017', DATE '2017-07-01'),
    ('Tesouro IGPM+ com Juros Semestrais 2021', DATE '2021-04-01'),
    ('Tesouro IGPM+ com Juros Semestrais 2031', DATE '2031-01-01')
)
UPDATE assets a
SET
    -- Remuneração: determinada pela família do título, nunca ambígua.
    --   Selic (LFT)            pós-fixado puro, segue a taxa Selic
    --   Prefixado (LTN/NTN-F)  taxa fixa, sem índice
    --   IPCA+ (NTN-B)          índice + spread  -> híbrido
    --   IGPM+ (NTN-C)          idem, com IGPM
    --   Renda+ / Educa+        NTN-B com fase de conversão, mesma mecânica do IPCA+
    rate_type = CASE
        WHEN td.name LIKE 'Tesouro Selic%'     THEN 'POS_FIXADO'
        WHEN td.name LIKE 'Tesouro Prefixado%' THEN 'PREFIXADO'
        ELSE 'HIBRIDO'
    END,
    indexer = CASE
        WHEN td.name LIKE 'Tesouro Selic%'     THEN 'SELIC'
        WHEN td.name LIKE 'Tesouro Prefixado%' THEN NULL
        WHEN td.name LIKE 'Tesouro IGPM+%'     THEN 'IGPM'
        ELSE 'IPCA'
    END,

    -- Vencimento: data explícita quando existe; senão, a convenção da família
    -- aplicada sobre o ano do nome.
    maturity_date = COALESCE(
        e.vencimento,
        CASE
            -- LTN e NTN-F: 1º de janeiro, sem exceção em toda a série.
            WHEN td.name LIKE 'Tesouro Prefixado%'
                THEN make_date(td.ano, 1, 1)

            -- NTN-B: 15/05 nos anos ímpares, 15/08 nos pares. A alternância vale
            -- para as duas séries (Principal e com Juros Semestrais).
            WHEN td.name LIKE 'Tesouro IPCA+%'
                THEN make_date(td.ano, CASE WHEN td.ano % 2 = 1 THEN 5 ELSE 8 END, 15)

            -- Renda+: o ano do nome é o de CONVERSÃO, quando começam os saques.
            -- São 20 anos de pagamentos mensais, então o título só vence em 15/12
            -- do 20º ano: Renda+ 2030 paga de 01/2030 a 12/2049 e vence em
            -- 15/12/2049.
            WHEN td.name LIKE 'Tesouro Renda+%'
                THEN make_date(td.ano + 19, 12, 15)

            -- Educa+: mesma mecânica, com 5 anos de pagamentos.
            WHEN td.name LIKE 'Tesouro Educa+%'
                THEN make_date(td.ano + 4, 12, 15)
        END
    )
FROM td
LEFT JOIN explicito e ON e.name = td.name
WHERE a.id = td.id;
