-- Parâmetros públicos de um título, 1:1 com assets.
--
-- Hoje só o Tesouro Direto preenche isto. "Tesouro IPCA+ 2029" vence em
-- 15/05/2029 e remunera IPCA mais spread para qualquer investidor: é
-- característica do papel, e por isso fica no catálogo. Um CDB não tem nada
-- disso — cada aplicação negocia o seu, e essa informação mora em
-- fixed_income_notes.
--
-- Tabela separada, e não colunas em assets, porque 130 dos 1264 ativos têm
-- parâmetros e os outros 1134 carregariam quatro colunas nulas.
--
-- Sem id próprio: a chave é o ativo. Um BIGSERIAL aqui só abriria espaço para
-- duas linhas de parâmetros para o mesmo papel. E ativo sem parâmetros não
-- ganha linha vazia: a ausência da linha JÁ é a informação "não tem prazo nem
-- indexador".
--
-- A taxa NÃO está aqui de propósito. No Tesouro ela é do dia da compra, não do
-- título: dois investidores levam o mesmo papel a IPCA + 6,2% e IPCA + 5,8%
-- conforme quando compraram. Taxa é da nota.
CREATE TABLE asset_terms (
    asset_id      BIGINT PRIMARY KEY,
    maturity_date DATE,
    rate_type     VARCHAR(20),
    indexer       VARCHAR(20),
    CONSTRAINT fk_asset_terms_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE,
    CONSTRAINT ck_asset_terms_rate_type CHECK (rate_type IN ('PREFIXADO', 'POS_FIXADO', 'HIBRIDO')),
    CONSTRAINT ck_asset_terms_indexer   CHECK (indexer   IN ('CDI', 'SELIC', 'IPCA', 'IGPM', 'INPC', 'TR')),
    -- Prefixado não tem índice; pós-fixado e híbrido exigem um. Sem rate_type
    -- não dá para distinguir "110% do CDI" de "CDI + 2%": os dois teriam
    -- indexer = CDI, com sentidos completamente diferentes.
    CONSTRAINT ck_asset_terms_rate_coherence CHECK (
        (rate_type IS NULL          AND indexer IS NULL)
        OR (rate_type = 'PREFIXADO' AND indexer IS NULL)
        OR (rate_type IN ('POS_FIXADO', 'HIBRIDO') AND indexer IS NOT NULL)
    )
);

CREATE INDEX idx_asset_terms_maturity ON asset_terms (maturity_date);
