-- Venda de renda fixa e do Tesouro pelo bruto e pelo liquido (docs/planos/PLANO_VENDA_RENDA_FIXA.md, secao 3.2).
--
-- total_amount continua sendo o BRUTO que saiu do titulo (o que a curva e as regras E, F e G usam). Na renda fixa
-- privada ele passa a ser CALCULADO a partir do liquido que o usuario recebeu; no Tesouro continua sendo cotas x PU.
--
-- net_amount             liquido: digitado (renda fixa) ou calculado (Tesouro: bruto - IR - IOF - taxa)
-- income_tax             IR da venda (soma das partes/lotes), truncado nos centavos
-- iof_tax                IOF da venda, truncado nos centavos
-- settlement_adjustment  so na liquidacao total da renda fixa: bruto - saldo da curva (o "ajuste da liquidacao")
--
-- Colunas nulas: so valem para venda de renda fixa e de Tesouro (REGRAS 8.1 nao se aplica).
ALTER TABLE transactions ADD COLUMN net_amount            NUMERIC(18,2);
ALTER TABLE transactions ADD COLUMN income_tax            NUMERIC(18,2);
ALTER TABLE transactions ADD COLUMN iof_tax               NUMERIC(18,2);
ALTER TABLE transactions ADD COLUMN settlement_adjustment NUMERIC(18,2);

-- D2: o valor das vendas de renda fixa ja gravadas foi digitado pelo usuario — o que ele recebeu. Vira o liquido;
-- o bruto, o IR e o IOF sao refeitos pela rodada da curva na subida seguinte (FixedIncomeValuation). Regra fixa por
-- tipo, nao deducao linha a linha.
UPDATE transactions t
SET net_amount = t.total_amount, income_tax = 0, iof_tax = 0
FROM portfolio_assets pa
JOIN assets a ON a.id = pa.asset_id
JOIN asset_types at ON at.id = a.asset_type_id
WHERE t.portfolio_asset_id = pa.id AND t.type = 'SELL' AND at.name = 'RENDA_FIXA';

-- Liquido, IR e IOF andam juntos.
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_sale_taxes CHECK (
    (net_amount IS NULL AND income_tax IS NULL AND iof_tax IS NULL)
    OR (net_amount IS NOT NULL AND income_tax IS NOT NULL AND iof_tax IS NOT NULL)
);
