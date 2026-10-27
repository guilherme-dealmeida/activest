-- Provento de ativo em moeda estrangeira com líquido pelas regras do exterior (docs/planos/PLANO_CADASTRO_MOEDAS.md,
-- etapa M4, C6).
--
-- O provento de ativo do exterior não tem retenção brasileira (withholding_tax_rules): tem a retenção do país de
-- origem (foreign_withholding_*, V9) e o IR brasileiro estimado (Lei 14.754/2023), guardado em foreign_income_tax.
-- O líquido (net_amount, net_amount_per_share) é o mesmo campo dos proventos nacionais — as somas da tela, da
-- Rentabilidade e das Metas continuam lendo um campo só. A constraint de retenção passa a aceitar esse terceiro caso.

ALTER TABLE dividends ADD COLUMN foreign_income_tax NUMERIC(18,2);

ALTER TABLE dividends DROP CONSTRAINT ck_dividends_withholding;
ALTER TABLE dividends ADD CONSTRAINT ck_dividends_withholding CHECK ((
    -- sem regra (MANUAL antigo, tipo sem regra)
    ((withholding_rule_id IS NULL) AND (withholding_rate IS NULL) AND (withholding_tax IS NULL)
        AND (net_amount_per_share IS NULL) AND (net_amount IS NULL) AND (foreign_income_tax IS NULL))
    -- retenção brasileira
    OR ((withholding_rule_id IS NOT NULL) AND (withholding_rate IS NOT NULL) AND (withholding_tax IS NOT NULL)
        AND (net_amount_per_share IS NOT NULL) AND (net_amount IS NOT NULL) AND (foreign_income_tax IS NULL))
    -- exterior: retenção do país de origem (pode não haver regra) + IR brasileiro estimado
    OR ((currency_code <> 'BRL') AND (withholding_rule_id IS NULL) AND (withholding_rate IS NULL)
        AND (withholding_tax IS NULL) AND (foreign_income_tax IS NOT NULL) AND (net_amount_per_share IS NOT NULL)
        AND (net_amount IS NOT NULL))
));

ALTER TABLE dividends DROP CONSTRAINT ck_dividends_fx;
ALTER TABLE dividends ADD CONSTRAINT ck_dividends_fx CHECK ((
    ((currency_code = 'BRL') AND (fx_rate = 1) AND (original_amount_per_share IS NULL) AND (original_total_amount IS NULL)
        AND (foreign_withholding_rate IS NULL) AND (foreign_withholding_tax IS NULL) AND (foreign_tax_rule_id IS NULL)
        AND (foreign_income_tax IS NULL))
    OR ((currency_code <> 'BRL') AND (fx_rate > 0) AND (original_amount_per_share IS NOT NULL))
));

COMMENT ON COLUMN dividends.foreign_income_tax IS
    'IR brasileiro estimado sobre provento do exterior (Lei 14.754/2023 desde 2024; carnê-leão antes), já deduzida a retenção do país de origem. Estimativa: a apuração é anual.';
COMMENT ON COLUMN asset_cash_events.value_per_share IS
    'Valor por cota na moeda do ativo (assets.currency_code; docs/planos/PLANO_CADASTRO_MOEDAS.md, D11).';
