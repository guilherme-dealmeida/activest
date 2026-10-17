-- Catalogo de benchmarks da Rentabilidade (docs/planos/PLANO_RENTABILIDADE.md, 3.1; D1 de 17/10/2026).
--
-- Tabela fina, so referencia: os valores continuam onde estao — market_index_values (kind INDICE) ou
-- asset_price_history (kind ATIVO). Fonte, periodicidade e disponibilidade saem do indice ou do ativo apontado.
--   code             identificador estavel usado pela API (?benchmarks=CDI,IBOV,IPCA_MAIS:6)
--   category         RENDA_FIXA, JUROS, INFLACAO, ACOES_BR, FII, ACOES_EUA, CRIPTO
--   kind             INDICE (market_index_id) | ATIVO (asset_id) — exatamente um dos dois
--   accepts_spread   aceita "+ x% a.a." informado na consulta (so IPCA + x%)
--   note             o que o usuario precisa saber sobre a serie (retorno total, proxy, inicio)
--   default_selected pre-selecionado na tela (so o CDI — L5, L7)

CREATE TABLE benchmarks (
    id                BIGSERIAL     PRIMARY KEY,
    code              VARCHAR(30)   NOT NULL,
    name              VARCHAR(100)  NOT NULL,
    category          VARCHAR(20)   NOT NULL,
    kind              VARCHAR(10)   NOT NULL,
    market_index_id   BIGINT,
    asset_id          BIGINT,
    accepts_spread    BOOLEAN       NOT NULL DEFAULT FALSE,
    note              VARCHAR(400),
    default_selected  BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order        INT           NOT NULL,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uk_benchmarks_code      UNIQUE (code),
    CONSTRAINT fk_benchmarks_index     FOREIGN KEY (market_index_id) REFERENCES market_indices (id),
    CONSTRAINT fk_benchmarks_asset     FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT ck_benchmarks_category  CHECK (category IN ('RENDA_FIXA', 'JUROS', 'INFLACAO', 'ACOES_BR', 'FII',
                                                           'ACOES_EUA', 'CRIPTO')),
    CONSTRAINT ck_benchmarks_kind      CHECK ((kind = 'INDICE' AND market_index_id IS NOT NULL AND asset_id IS NULL)
                                          OR (kind = 'ATIVO' AND asset_id IS NOT NULL AND market_index_id IS NULL))
);

INSERT INTO benchmarks (code, name, category, kind, market_index_id, asset_id, accepts_spread, note,
                        default_selected, sort_order)
SELECT v.code, v.name, v.category, 'INDICE', i.id, NULL, v.spread, v.note, v.def, v.ord
FROM (VALUES
    ('CDI',       'CDI',          'RENDA_FIXA', 'CDI',   FALSE,
     'Taxa DI diária (Banco Central, SGS 12), composta por dia útil.', TRUE, 1),
    ('SELIC',     'Selic',        'JUROS',      'SELIC', FALSE,
     'Selic efetiva diária (Banco Central, SGS 11), composta por dia útil.', FALSE, 2),
    ('IPCA',      'IPCA',         'INFLACAO',   'IPCA',  FALSE,
     'Inflação oficial (IBGE, SGS 433); o mês é distribuído pelos dias úteis. Só até o último mês publicado.',
     FALSE, 3),
    ('IPCA_MAIS', 'IPCA + x%',    'INFLACAO',   'IPCA',  TRUE,
     'IPCA composto com a taxa real informada (% a.a., por dia útil, base 252). Só até o último mês publicado.',
     FALSE, 4),
    ('IBOV',      'Ibovespa',     'ACOES_BR',   'IBOV',  FALSE,
     'Índice de retorno total da B3 (os proventos das ações são reinvestidos pela metodologia do índice).',
     FALSE, 5),
    ('IFIX',      'IFIX',         'FII',        'IFIX',  FALSE,
     'Índice de retorno total da B3 para fundos imobiliários.', FALSE, 6)
) AS v(code, name, category, index_code, spread, note, def, ord)
JOIN market_indices i ON i.code = v.index_code;

INSERT INTO benchmarks (code, name, category, kind, market_index_id, asset_id, accepts_spread, note,
                        default_selected, sort_order)
SELECT v.code, v.name, v.category, 'ATIVO', NULL, a.id, FALSE, v.note, FALSE, v.ord
FROM (VALUES
    ('SP500_IVVB11', 'IVVB11 (proxy S&P 500 em BRL)', 'ACOES_EUA', 'IVVB11',
     'ETF negociado na B3 que replica o S&P 500 em reais (cotação do COTAHIST). Não é a série oficial do índice.', 7),
    ('BTC',          'Bitcoin (BTC/BRL)',             'CRIPTO',    'BTC',
     'Preço do bitcoin em reais: Binance (BTC/BRL, desde 13/10/2020) e CoinGecko nos últimos 365 dias.', 8)
) AS v(code, name, category, ticker, note, ord)
JOIN assets a ON a.ticker = v.ticker;
