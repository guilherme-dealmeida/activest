-- Balancos da CVM (F5 / M5 do PLANO_EMPRESAS_BALANCOS.md): DFP e ITR desde 2010, so as metricas usadas
-- pelos indicadores, e a quantidade de acoes de cada documento.
--
-- Volume vindo de arquivo (data/cvm/dfp, itr, fre): num banco novo estas tabelas nascem vazias e a carga
-- inicial as preenche lendo os zips (POST /api/v1/admin/cvm/import). O arquivo e o estado.
--
-- Diferencas do plano, achadas nos arquivos reais em 28/09/2026:
--   * sem cvm_account_map: os codigos das contas variam entre companhias do mesmo layout e entre anos
--     (lucro liquido 3.09 no Itau, 3.11 no BB, 3.13 no IRB, todos no DFP 2025). As metricas sao achadas
--     pela descricao padronizada da CVM, em codigo (FinancialMetricRules);
--   * composicao do capital so existe de 2020 em diante; antes, a quantidade vem do historico de capital
--     do Formulario de Referencia (FRE), sem tesouraria;
--   * company_quarters fica para a F6, junto com o calculo que a preenche.

CREATE TABLE cvm_documents (
    id             BIGSERIAL   PRIMARY KEY,
    issuer_id      BIGINT      NOT NULL,
    doc_type       VARCHAR(3)  NOT NULL,
    reference_date DATE        NOT NULL,
    version        SMALLINT    NOT NULL,
    cvm_doc_id     INTEGER,
    received_at    DATE,
    -- D6: consolidado; individual so quando a companhia nao publica consolidado
    consolidated   BOOLEAN     NOT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT fk_cvm_documents_issuer FOREIGN KEY (issuer_id) REFERENCES issuers (id),
    CONSTRAINT ck_cvm_documents_type CHECK (doc_type IN ('DFP', 'ITR')),
    CONSTRAINT uk_cvm_documents UNIQUE (issuer_id, doc_type, reference_date)
);

CREATE INDEX ix_cvm_documents_reference ON cvm_documents (issuer_id, reference_date);

-- Uma metrica de um documento num periodo. Balanco: period_start nulo (posicao na data). DRE do ITR:
-- o trimestre e o acumulado do exercicio, dois periodos. Valores em reais; LPA em reais por acao.
CREATE TABLE cvm_statement_values (
    document_id  BIGINT         NOT NULL,
    metric       VARCHAR(40)    NOT NULL,
    period_start DATE,
    period_end   DATE           NOT NULL,
    value        NUMERIC(24, 6) NOT NULL,
    CONSTRAINT fk_cvm_statement_values_document FOREIGN KEY (document_id) REFERENCES cvm_documents (id) ON DELETE CASCADE,
    CONSTRAINT uk_cvm_statement_values UNIQUE NULLS NOT DISTINCT (document_id, metric, period_start, period_end)
);

-- Quantidade de acoes na data do documento. Como veio da fonte; em unidades = quantidade * scale_factor.
--   source COMPOSICAO: DFP/ITR (2020+), escala desconhecida, conferida pelo FRE ou pelo LPA
--                      (ShareScale); tesouraria informada
--   source FRE:        capital integralizado do FRE na data (antes de 2020), em unidades; sem tesouraria
-- scale_check VIZINHO: o que sobrou sem conferencia e decidido pelo documento validado mais proximo no
--                      tempo, anterior ou seguinte (companhia recem-listada, sem FRE nem LPA ainda).
-- scale_status REVISAR: nenhuma conferencia fechou; o fator fica nulo e o indicador nao usa a linha.
CREATE TABLE share_counts (
    document_id       BIGINT      PRIMARY KEY,
    source            VARCHAR(12) NOT NULL,
    ordinary_shares   BIGINT,
    preferred_shares  BIGINT,
    total_shares      BIGINT      NOT NULL,
    ordinary_treasury BIGINT,
    preferred_treasury BIGINT,
    total_treasury    BIGINT,
    scale_factor      INTEGER,
    scale_status      VARCHAR(10) NOT NULL,
    scale_check       VARCHAR(10),
    CONSTRAINT fk_share_counts_document FOREIGN KEY (document_id) REFERENCES cvm_documents (id) ON DELETE CASCADE,
    CONSTRAINT ck_share_counts_source CHECK (source IN ('COMPOSICAO', 'FRE')),
    CONSTRAINT ck_share_counts_status CHECK (scale_status IN ('OK', 'REVISAR', 'MANUAL')),
    CONSTRAINT ck_share_counts_factor CHECK (scale_factor IS NOT NULL OR scale_status = 'REVISAR'),
    CONSTRAINT ck_share_counts_check CHECK (scale_check IS NULL OR scale_check IN ('FRE', 'LPA', 'VIZINHO'))
);
