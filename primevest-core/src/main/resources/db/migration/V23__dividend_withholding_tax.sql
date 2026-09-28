-- IR retido na fonte sobre proventos: bruto x liquido (PLANO_IR_PROVENTOS.md).
--
-- -----------------------------------------------------------------------------
-- O QUE A LEI MUDA FICA AQUI; O JEITO DE CALCULAR FICA NO CODIGO
-- -----------------------------------------------------------------------------
-- withholding_tax_rules guarda a aliquota de cada tipo de provento, por tipo de
-- ativo e por vigencia, com a base legal ao lado. O calculo (bruto e IR
-- truncados nos centavos, como a B3; qual data conta como credito) esta em
-- WithholdingTaxCalculator e nao muda com a lei.
--
-- Investidor pessoa fisica residente. Tipo de provento + tipo de ativo sem
-- linha vigente = "sem regra": aliquota e liquido nulos (ETF, BDR...).
--
-- base_date diz qual data do provento cai na vigencia:
--   CREDIT   data do credito ao acionista = aprovacao (approved_on); sem ela,
--            data-com; sem as duas, pagamento. E o fato gerador do JCP ("na
--            data do pagamento ou do credito", o que vier primeiro).
--   PAYMENT  data de pagamento; sem ela, data-com.
--
-- -----------------------------------------------------------------------------
-- COMO MUDAR UMA ALIQUOTA (lei nova)
-- -----------------------------------------------------------------------------
-- Uma migration nova, nunca UPDATE nesta:
--   1. fecha a linha vigente:   UPDATE withholding_tax_rules SET valid_to = <vespera>
--   2. insere a linha nova:     INSERT ... valid_from = <inicio da vigencia>
--   3. marca os proventos afetados para recalculo:
--        UPDATE dividends SET tax_checked_at = NULL WHERE type = '<tipo>' ...
-- Na subida seguinte, o DividendTaxVerifier recalcula so esses. Nenhuma
-- vigencia pode se sobrepor a outra do mesmo tipo de provento e de ativo
-- (WithholdingTaxRuleRepositoryTest confere).
CREATE TABLE withholding_tax_rules (
    id             BIGSERIAL     PRIMARY KEY,
    dividend_type  VARCHAR(30)   NOT NULL,
    asset_type_id  BIGINT        NOT NULL,
    rate           NUMERIC(6,4)  NOT NULL,
    base_date      VARCHAR(10)   NOT NULL,
    valid_from     DATE,
    valid_to       DATE,
    legal_basis    VARCHAR(200)  NOT NULL,
    CONSTRAINT fk_withholding_tax_rules_asset_type FOREIGN KEY (asset_type_id) REFERENCES asset_types (id),
    CONSTRAINT ck_withholding_tax_rules_type CHECK (dividend_type IN ('DIVIDENDO', 'JCP', 'RENDIMENTO', 'AMORTIZACAO', 'RESTITUICAO_CAPITAL')),
    CONSTRAINT ck_withholding_tax_rules_base_date CHECK (base_date IN ('CREDIT', 'PAYMENT')),
    CONSTRAINT ck_withholding_tax_rules_rate CHECK (rate >= 0 AND rate < 1),
    CONSTRAINT ck_withholding_tax_rules_validity CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_from <= valid_to)
);

CREATE INDEX idx_withholding_tax_rules_type ON withholding_tax_rules (dividend_type, asset_type_id);

-- Carga inicial. Conferida contra o extrato da B3 em 24/09/2026
-- (PLANO_IR_PROVENTOS.md, secao 2).
INSERT INTO withholding_tax_rules (dividend_type, asset_type_id, rate, base_date, valid_from, valid_to, legal_basis)
SELECT r.dividend_type, t.id, r.rate, r.base_date, r.valid_from, r.valid_to, r.legal_basis
FROM (VALUES
    ('JCP',                 'ACAO', 0.1500, 'CREDIT',  NULL::DATE,        DATE '2025-12-31', 'Lei 9.249/1995, art. 9º, § 2º'),
    ('JCP',                 'ACAO', 0.1750, 'CREDIT',  DATE '2026-01-01', NULL::DATE,        'LC 224/2025, art. 8º (art. 9º, § 2º, da Lei 9.249/1995)'),
    ('DIVIDENDO',           'ACAO', 0.0000, 'PAYMENT', NULL::DATE,        NULL::DATE,        'Isento — Lei 9.249/1995, art. 10'),
    ('RENDIMENTO',          'ACAO', 0.2250, 'PAYMENT', NULL::DATE,        NULL::DATE,        'Atualização pela Selic — renda fixa, até 180 dias (22,5%)'),
    ('RESTITUICAO_CAPITAL', 'ACAO', 0.0000, 'PAYMENT', NULL::DATE,        NULL::DATE,        'Isento — devolução de capital'),
    ('RENDIMENTO',          'FII',  0.0000, 'PAYMENT', NULL::DATE,        NULL::DATE,        'Isento — Lei 11.033/2004, art. 3º, III'),
    ('AMORTIZACAO',         'FII',  0.0000, 'PAYMENT', NULL::DATE,        NULL::DATE,        'Isento — devolução de capital')
) AS r (dividend_type, asset_type_name, rate, base_date, valid_from, valid_to, legal_basis)
JOIN asset_types t ON t.name = r.asset_type_name;


-- -----------------------------------------------------------------------------
-- dividends: o que foi retido em cada provento
-- -----------------------------------------------------------------------------
-- total_amount continua sendo o BRUTO (agora truncado nos centavos, como a B3).
--
-- withholding_rule_id   regra que calculou; nula = sem regra para o tipo
-- withholding_rate      copia da aliquota aplicada (0 = isento)
-- withholding_tax       IR retido, truncado nos centavos
-- net_amount_per_share  liquido por cota, 8 casas — so exibicao
-- net_amount            TOTAL liquido = total_amount - withholding_tax. Nunca
--                       liquido por cota x cotas: erra 1 centavo (6,15 x 6,16)
-- tax_checked_at        quando a regra foi aplicada. NULA = ainda nao
--                       calculado (diferente de "sem regra"); o
--                       DividendTaxVerifier recalcula esses na subida
ALTER TABLE dividends ADD COLUMN withholding_rule_id  BIGINT;
ALTER TABLE dividends ADD COLUMN withholding_rate     NUMERIC(6,4);
ALTER TABLE dividends ADD COLUMN withholding_tax      NUMERIC(18,2);
ALTER TABLE dividends ADD COLUMN net_amount_per_share NUMERIC(18,8);
ALTER TABLE dividends ADD COLUMN net_amount           NUMERIC(18,2);
ALTER TABLE dividends ADD COLUMN tax_checked_at       TIMESTAMP;

ALTER TABLE dividends ADD CONSTRAINT fk_dividends_withholding_rule
    FOREIGN KEY (withholding_rule_id) REFERENCES withholding_tax_rules (id);

-- Com regra, os quatro valores andam juntos; sem regra, nenhum.
ALTER TABLE dividends ADD CONSTRAINT ck_dividends_withholding CHECK (
    (withholding_rule_id IS NULL AND withholding_rate IS NULL AND withholding_tax IS NULL
        AND net_amount_per_share IS NULL AND net_amount IS NULL)
    OR (withholding_rule_id IS NOT NULL AND withholding_rate IS NOT NULL AND withholding_tax IS NOT NULL
        AND net_amount_per_share IS NOT NULL AND net_amount IS NOT NULL)
);

CREATE INDEX idx_dividends_tax_pending ON dividends (portfolio_asset_id) WHERE tax_checked_at IS NULL;
