-- Categorias de ETF (PLANO_BRAPI.md, decisao B2) — 12 linhas.
--
-- description e o tema do ETF ('Renda fixa Brasil'); name e o tema em
-- maiusculas sem acento (RENDA_FIXA_BRASIL); segment e o setor da BRAPI
-- ('Miscellaneous') ou 'Indefinido' quando a BRAPI nao informa. O mesmo tema
-- com dois segmentos vira duas linhas: a unicidade e (tipo, name, segment).
--
-- Tema atribuido a cada ETF na planilha docs/brapi/ETFs_BRAPI_2026-09-23.xlsx.
INSERT INTO asset_categories (name, description, segment, asset_type_id)
SELECT v.name::varchar, v.description::varchar, v.segment::varchar, t.id
FROM (VALUES
    ('ACOES_BRASIL', 'Ações Brasil', 'Indefinido'),
    ('ACOES_BRASIL', 'Ações Brasil', 'Miscellaneous'),
    ('ACOES_INTERNACIONAIS', 'Ações internacionais', 'Miscellaneous'),
    ('CRIPTO', 'Cripto', 'Indefinido'),
    ('CRIPTO', 'Cripto', 'Miscellaneous'),
    ('DOLAR_CAMBIO', 'Dólar / câmbio', 'Miscellaneous'),
    ('FIIS_IMOBILIARIO', 'FIIs / imobiliário (índice)', 'Miscellaneous'),
    ('OURO_PRATA_COMMODITIES', 'Ouro, prata e commodities', 'Miscellaneous'),
    ('OUTROS_ESTRATEGIA', 'Outros / estratégia', 'Indefinido'),
    ('RENDA_FIXA_BRASIL', 'Renda fixa Brasil', 'Indefinido'),
    ('RENDA_FIXA_BRASIL', 'Renda fixa Brasil', 'Miscellaneous'),
    ('RENDA_FIXA_INTERNACIONAL', 'Renda fixa internacional', 'Miscellaneous')
) AS v(name, description, segment)
JOIN asset_types t ON t.name = 'ETF'
ON CONFLICT (asset_type_id, name, segment) DO UPDATE
    SET description = EXCLUDED.description;
