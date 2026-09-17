-- Posição ganha "liquidada" e troca o valor atual pela valorização.
--
-- is_settled: posição encerrada por vencimento do título ou resgate de renda
-- fixa. Nasce falsa; a regra que a liga vem com essas funcionalidades.
--
-- appreciation substitui current_value. A lógica se inverte: antes o valor
-- atual era gravado e a valorização saía dele (valor atual - valor inicial);
-- agora a valorização é gravada e o valor atual sai dela
-- (valor inicial + valorização). Guardar os dois seria ter duas fontes para o
-- mesmo número.
--
-- Nasce em zero e não aceita nulo. Não há cópia de current_value: nas posições
-- sem cotação ele era igual a total_invested (valorização zero), e nas com
-- cotação a valorização é apurada na leitura, a partir do preço atual.
ALTER TABLE portfolio_assets
    ADD COLUMN is_settled   BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN appreciation NUMERIC(18,2) NOT NULL DEFAULT 0;

ALTER TABLE portfolio_assets DROP COLUMN current_value;
