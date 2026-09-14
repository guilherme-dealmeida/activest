-- Categorias de RENDA_FIXA.
--
-- Critério: a categoria é o INSTRUMENTO (quem emite e sob que regra).
-- Como o título remunera — prefixado, CDI, IPCA+, SELIC — não entra aqui:
-- é o indexador, e já tem lugar próprio em assets.indexer / assets.indexer_rate.
-- Um CDB pode ser prefixado ou IPCA+; são combinações, não categorias distintas.
--
-- A isenção de IR também não é modelada como categoria de propósito: é regra
-- tributária, muda por lei (a de CRI, CRA e debêntures incentivadas está em
-- discussão), enquanto a taxonomia de produto não deveria mudar junto.
--
-- O tipo é resolvido por name, não por id fixo, para não depender da ordem
-- em que a V13 inseriu.
INSERT INTO asset_categories (asset_type_id, name, description)
SELECT t.id, c.name, c.description
FROM asset_types t
CROSS JOIN (VALUES
    -- Bancários com garantia do FGC
    ('CDB',                   'Certificado de Depósito Bancário'),      -- negociável antes do vencimento, com deságio
    ('RDB',                   'Recibo de Depósito Bancário'),           -- inegociável, preso até o vencimento
    ('LCI',                   'Letra de Crédito Imobiliário'),          -- isenta de IR para PF
    ('LCA',                   'Letra de Crédito do Agronegócio'),       -- isenta de IR para PF
    ('LC',                    'Letra de Câmbio'),                       -- emitida por financeiras
    ('DPGE',                  'Depósito a Prazo com Garantia Especial'),-- cobertura FGC ampliada
    ('POUPANCA',              'Poupança'),                              -- rendimento definido em lei
    -- Bancários sem FGC
    ('LF',                    'Letra Financeira'),                      -- prazo mín. 24 meses, aporte alto, sem resgate antecipado
    ('LIG',                   'Letra Imobiliária Garantida'),           -- isenta de IR, patrimônio de afetação
    ('CCB',                   'Cédula de Crédito Bancário'),            -- comum em plataformas digitais
    -- Crédito privado e securitizados
    ('DEBENTURE',             'Debênture'),                             -- dívida corporativa comum
    ('DEBENTURE_INCENTIVADA', 'Debênture Incentivada'),                 -- infraestrutura, Lei 12.431
    ('CRI',                   'Certificado de Recebíveis Imobiliários'),-- isento de IR para PF
    ('CRA',                   'Certificado de Recebíveis do Agronegócio'), -- isento de IR para PF
    ('NOTA_COMERCIAL',        'Nota Comercial')                         -- dívida de curto prazo
) AS c(name, description)
WHERE t.name = 'RENDA_FIXA'
ON CONFLICT (asset_type_id, name) DO UPDATE
    SET description = EXCLUDED.description;
