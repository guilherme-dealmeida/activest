-- IR e IOF da renda fixa e do Tesouro Direto (docs/planos/PLANO_VENDA_RENDA_FIXA.md, secao 3.1).
--
-- -----------------------------------------------------------------------------
-- O QUE A LEI MUDA FICA AQUI; O JEITO DE CALCULAR FICA NO CODIGO
-- -----------------------------------------------------------------------------
-- Mesma filosofia da V23 (IR dos proventos). Cada linha diz a aliquota de um imposto
-- para uma faixa de DIAS CORRIDOS desde o aporte, numa vigencia, com a base legal ao
-- lado. O calculo (IOF antes do IR, IR sobre o rendimento menos o IOF, truncar nos
-- centavos, lote a lote) esta em FixedIncomeTaxCalculator e nao muda com a lei.
--
-- asset_id nulo = regra geral, vale para todo produto de renda fixa e para o Tesouro
-- Direto. asset_id preenchido = excecao do produto (isencao): quando o produto tem
-- linha propria para o imposto, ela substitui a geral inteira.
--
-- Investidor pessoa fisica residente. Base legal conferida em 15/10/2026 (plano,
-- secao 3.1.1).
--
-- -----------------------------------------------------------------------------
-- COMO MUDAR UMA ALIQUOTA (lei nova)
-- -----------------------------------------------------------------------------
-- Uma migration nova, nunca UPDATE nesta:
--   1. fecha a linha vigente:  UPDATE fixed_income_tax_rules SET valid_to = <vespera> ...
--   2. insere a linha nova:    INSERT ... valid_from = <inicio da vigencia>
-- A vigencia que vale e a da DATA DO RESGATE. Nenhuma faixa pode se sobrepor a outra
-- do mesmo imposto e produto na mesma vigencia (FixedIncomeTaxRuleRepositoryTest).
CREATE TABLE fixed_income_tax_rules (
    id           BIGSERIAL     PRIMARY KEY,
    tax          VARCHAR(3)    NOT NULL,
    asset_id     BIGINT,
    days_from    INTEGER       NOT NULL,
    days_to      INTEGER,
    rate         NUMERIC(6,4)  NOT NULL,
    valid_from   DATE,
    valid_to     DATE,
    legal_basis  VARCHAR(200)  NOT NULL,
    CONSTRAINT fk_fi_tax_rules_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT ck_fi_tax_rules_tax CHECK (tax IN ('IR', 'IOF')),
    CONSTRAINT ck_fi_tax_rules_days CHECK (days_from >= 0 AND (days_to IS NULL OR days_to >= days_from)),
    CONSTRAINT ck_fi_tax_rules_rate CHECK (rate >= 0 AND rate <= 1),
    CONSTRAINT ck_fi_tax_rules_validity CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_from <= valid_to)
);

CREATE INDEX idx_fi_tax_rules_tax ON fixed_income_tax_rules (tax, asset_id);

-- IR regressivo (regra geral).
INSERT INTO fixed_income_tax_rules (tax, asset_id, days_from, days_to, rate, legal_basis) VALUES
    ('IR', NULL,   0,  180, 0.2250, 'Lei 11.033/2004, art. 1º, I; IN RFB 1.585/2015, art. 46'),
    ('IR', NULL, 181,  360, 0.2000, 'Lei 11.033/2004, art. 1º, II; IN RFB 1.585/2015, art. 46'),
    ('IR', NULL, 361,  720, 0.1750, 'Lei 11.033/2004, art. 1º, III; IN RFB 1.585/2015, art. 46'),
    ('IR', NULL, 721, NULL, 0.1500, 'Lei 11.033/2004, art. 1º, IV; IN RFB 1.585/2015, art. 46');

-- IOF regressivo (regra geral): percentual do rendimento pelo dia corrido do resgate.
-- O dia 0 (resgate no proprio dia) entra na faixa do dia 1; do 30º dia em diante, zero.
INSERT INTO fixed_income_tax_rules (tax, asset_id, days_from, days_to, rate, legal_basis)
SELECT 'IOF', NULL, CASE WHEN v.dia = 1 THEN 0 ELSE v.dia END, v.dia, v.pct / 100.0,
       'Decreto 6.306/2007, art. 32, caput e § 1º, I, e Anexo'
FROM (VALUES
    ( 1, 96), ( 2, 93), ( 3, 90), ( 4, 86), ( 5, 83), ( 6, 80), ( 7, 76), ( 8, 73), ( 9, 70), (10, 66),
    (11, 63), (12, 60), (13, 56), (14, 53), (15, 50), (16, 46), (17, 43), (18, 40), (19, 36), (20, 33),
    (21, 30), (22, 26), (23, 23), (24, 20), (25, 16), (26, 13), (27, 10), (28,  6), (29,  3)
) AS v (dia, pct);

INSERT INTO fixed_income_tax_rules (tax, asset_id, days_from, days_to, rate, legal_basis) VALUES
    ('IOF', NULL, 30, NULL, 0.0000, 'Decreto 6.306/2007, art. 32, Anexo (30 dias ou mais: 0%)');

-- Isencoes e aliquotas zero por produto. Resolvidas pelo TICKER do produto generico do
-- catalogo (V7): ids de BIGSERIAL nao sobrevivem a um banco novo.
INSERT INTO fixed_income_tax_rules (tax, asset_id, days_from, days_to, rate, legal_basis)
SELECT r.tax, a.id, 0, NULL, 0.0000, r.legal_basis
FROM (VALUES
    ('IR',  'LCI',                   'Isento — Lei 11.033/2004, art. 3º, II'),
    ('IR',  'CRI',                   'Isento — Lei 11.033/2004, art. 3º, II'),
    ('IR',  'LCA',                   'Isento — Lei 11.033/2004, art. 3º, IV'),
    ('IR',  'CRA',                   'Isento — Lei 11.033/2004, art. 3º, IV'),
    ('IR',  'DEBENTURE_INCENTIVADA', 'Alíquota zero para pessoa física — Lei 12.431/2011, art. 2º, I'),
    ('IR',  'LIG',                   'Isento — Lei 13.097/2015, art. 90, I'),
    ('IR',  'POUPANCA',              'Isento — Lei 8.981/1995, art. 68, III'),
    ('IOF', 'LCA',                   'Alíquota zero — Decreto 6.306/2007, art. 32, § 2º, V'),
    ('IOF', 'CRA',                   'Alíquota zero — Decreto 6.306/2007, art. 32, § 2º, V'),
    ('IOF', 'CRI',                   'Alíquota zero — Decreto 6.306/2007, art. 32, § 2º, VI'),
    ('IOF', 'DEBENTURE',             'Alíquota zero — Decreto 6.306/2007, art. 32, § 2º, VI'),
    ('IOF', 'DEBENTURE_INCENTIVADA', 'Alíquota zero — Decreto 6.306/2007, art. 32, § 2º, VI'),
    ('IOF', 'LF',                    'Alíquota zero — Decreto 6.306/2007, art. 32, § 2º, VI'),
    ('IOF', 'POUPANCA',              'Não incidência — depósito de poupança não é título ou valor mobiliário')
) AS r (tax, ticker, legal_basis)
JOIN assets a ON a.ticker = r.ticker;
