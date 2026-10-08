-- Indices de mercado (docs/planos/PLANO_INDICES_MERCADO.md, X1; decisoes D1-D10 de 05/10/2026).
--
-- market_indices: o cadastro de cada indice (o que e e de onde vem). Dado de referencia: semeado aqui.
--   code        mesmo nome do enum Indexer para os seis indexadores de renda fixa (CDI, SELIC, IPCA, IGPM,
--               INPC, TR): a nota acha a serie por fixed_income_notes.indexer, sem tabela de/para
--   periodicity DIARIA | MENSAL (mensal datado no dia 1o, como o SGS publica)
--   unit        como o valor e publicado: PCT_DIA, PCT_MES, PCT_PERIODO (TR: periodo de um mes que comeca no
--               dia), PCT_ANO (meta Selic), PONTOS (indices da B3), BRL (dolar)
--   source      BCB_SGS (source_ref = codigo da serie no SGS) | B3 (source_ref = sigla do indice)
--   history_from inicio da carga historica padrao (D2: 2010); o endpoint de carga aceita outro inicio
--
-- market_index_values: a serie, um valor por indice por data, exatamente como a fonte publica (D6: nada de
-- fator acumulado gravado; o acumulado e calculado no MarketIndexCalculator). Gravada so pela adm-api.

CREATE TABLE market_indices (
    id            BIGSERIAL     PRIMARY KEY,
    code          VARCHAR(20)   NOT NULL,
    name          VARCHAR(100)  NOT NULL,
    periodicity   VARCHAR(10)   NOT NULL,
    unit          VARCHAR(20)   NOT NULL,
    source        VARCHAR(20)   NOT NULL,
    source_ref    VARCHAR(40)   NOT NULL,
    history_from  DATE          NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT uk_market_indices_code        UNIQUE (code),
    CONSTRAINT uk_market_indices_source      UNIQUE (source, source_ref),
    CONSTRAINT ck_market_indices_periodicity CHECK (periodicity IN ('DIARIA', 'MENSAL')),
    CONSTRAINT ck_market_indices_unit        CHECK (unit IN ('PCT_DIA', 'PCT_MES', 'PCT_PERIODO', 'PCT_ANO',
                                                             'PONTOS', 'BRL')),
    CONSTRAINT ck_market_indices_source      CHECK (source IN ('BCB_SGS', 'B3'))
);

CREATE TABLE market_index_values (
    index_id        BIGINT         NOT NULL,
    reference_date  DATE           NOT NULL,
    value           NUMERIC(18,8)  NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT now(),
    CONSTRAINT pk_market_index_values PRIMARY KEY (index_id, reference_date),
    CONSTRAINT fk_market_index_values_index FOREIGN KEY (index_id) REFERENCES market_indices (id)
);

-- D1: os seis indexadores de renda fixa + meta Selic, Ibovespa, IFIX e dolar PTAX. Poupanca fica para depois.
INSERT INTO market_indices (code, name, periodicity, unit, source, source_ref, history_from) VALUES
    ('CDI',        'CDI — Taxa DI',                     'DIARIA', 'PCT_DIA',     'BCB_SGS', '12',   DATE '2010-01-01'),
    ('SELIC',      'Selic efetiva (over)',              'DIARIA', 'PCT_DIA',     'BCB_SGS', '11',   DATE '2010-01-01'),
    ('IPCA',       'IPCA',                              'MENSAL', 'PCT_MES',     'BCB_SGS', '433',  DATE '2010-01-01'),
    ('IGPM',       'IGP-M',                             'MENSAL', 'PCT_MES',     'BCB_SGS', '189',  DATE '2010-01-01'),
    ('INPC',       'INPC',                              'MENSAL', 'PCT_MES',     'BCB_SGS', '188',  DATE '2010-01-01'),
    ('TR',         'Taxa Referencial (TR)',             'DIARIA', 'PCT_PERIODO', 'BCB_SGS', '226',  DATE '2010-01-01'),
    ('SELIC_META', 'Meta Selic (Copom)',                'DIARIA', 'PCT_ANO',     'BCB_SGS', '432',  DATE '2010-01-01'),
    ('USD_PTAX',   'Dólar PTAX venda',                  'DIARIA', 'BRL',         'BCB_SGS', '1',    DATE '2010-01-01'),
    ('IBOV',       'Ibovespa',                          'DIARIA', 'PONTOS',      'B3',      'IBOV', DATE '2010-01-01'),
    ('IFIX',       'IFIX — Índice de Fundos Imobiliários', 'DIARIA', 'PONTOS',   'B3',      'IFIX', DATE '2010-01-01')
ON CONFLICT (code) DO NOTHING;
