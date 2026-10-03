-- "AMORTIZACAO RF" e o rotulo da B3 para a amortizacao de cota de fundo (a B3 nao manda
-- "AMORTIZACAO" puro). Ate aqui ficava gravado sem tipo (sem provento, sem reduzir custo).
-- Passa a ser AMORTIZACAO (DividendType.fromSourceLabel). Nos eventos ja gravados:
--   * a mesma amortizacao ja veio do FNET (mesmo ativo, data-com e valor, tolerancia de
--     1e-6): a linha da B3 sai — em 28/09/2026, 88 de 103;
--   * sem par no FNET (RTEL16, VVMR11): recebe o tipo.
-- Nenhuma posicao tinha esses fundos quando a regra entrou; ainda assim um provento de
-- carteira que apontasse para a linha que sai passa para a do FNET.

CREATE TEMP TABLE amortizacao_rf_par ON COMMIT DROP AS
SELECT r.id AS rf_id,
       (SELECT o.id FROM asset_cash_events o
         WHERE o.asset_id = r.asset_id AND o.type = 'AMORTIZACAO' AND o.data_com = r.data_com
           AND abs(o.value_per_share - r.value_per_share) <= 0.000001
         ORDER BY o.id LIMIT 1) AS keep_id
  FROM asset_cash_events r
 WHERE r.type IS NULL AND upper(trim(r.label)) = 'AMORTIZACAO RF';

UPDATE asset_cash_events o
   SET payment_date = COALESCE(o.payment_date, r.payment_date),
       approved_on  = COALESCE(o.approved_on, r.approved_on),
       related_to   = COALESCE(o.related_to, r.related_to)
  FROM amortizacao_rf_par p JOIN asset_cash_events r ON r.id = p.rf_id
 WHERE p.keep_id IS NOT NULL AND o.id = p.keep_id;

UPDATE dividends d SET cash_event_id = p.keep_id
  FROM amortizacao_rf_par p
 WHERE p.keep_id IS NOT NULL AND d.cash_event_id = p.rf_id;

DELETE FROM asset_cash_events e USING amortizacao_rf_par p
 WHERE p.keep_id IS NOT NULL AND e.id = p.rf_id;

UPDATE asset_cash_events e SET type = 'AMORTIZACAO'
  FROM amortizacao_rf_par p
 WHERE p.keep_id IS NULL AND e.id = p.rf_id;
