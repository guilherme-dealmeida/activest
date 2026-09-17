-- Totais da posição e da carteira deixam de ser gravados: saem das transações.
--
-- 1. portfolio_assets.total_shares, average_price e total_invested saem.
--    Eram cópias do que as transações já dizem, mantidas por um recálculo que
--    rodava a cada criação, edição ou exclusão de transação. Qualquer caminho
--    que mexesse em transação sem chamar o recálculo deixava a posição errada.
--    Agora a posição é apurada na leitura (PositionCalculator), percorrendo as
--    transações em ordem cronológica:
--      - compra soma cotas e custo;
--      - venda tira cotas e tira custo pelo preço médio do momento — por isso
--        o cálculo depende da ordem e não é um SUM/AVG em SQL;
--      - preço médio = custo / cotas;
--      - tipos lançados por valor total (Renda Fixa) não têm cotas nem preço
--        médio: o custo é a soma das aplicações menos os resgates.
--
--    Não é preciso tabela associativa entre posição e transação: a ligação já
--    é transactions.portfolio_asset_id, obrigatória. Uma segunda ligação seria
--    uma segunda fonte para o mesmo fato — o mesmo problema que V26 corrigiu
--    em dividends.
--
-- 2. portfolios.total_invested e current_value saem, pelo mesmo motivo de
--    portfolios.total_dividends em V27. Ninguém as lia: o resumo da carteira
--    já somava as posições.
--
-- portfolio_assets.appreciation fica. Não é total derivado de transação: é a
-- valorização apurada por fora (a curva da renda fixa, quando existir).
--
-- V22 ainda insere total_invested e current_value no seed da carteira. Não se
-- edita migration aplicada: num banco novo V22 grava os valores e esta
-- migration derruba as colunas logo depois, como em V27.

-- Guarda: posição com custo gravado e nenhuma transação. Depois desta
-- migration o custo dela seria zero, e o valor sumiria sem ninguém ver.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM portfolio_assets pa
        WHERE COALESCE(pa.total_invested, 0) <> 0
          AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.portfolio_asset_id = pa.id)
    ) THEN
        RAISE EXCEPTION 'Existem posições com total_invested gravado e sem transações. Lance as transações delas ou apague-as antes de aplicar V28.';
    END IF;
END $$;

ALTER TABLE portfolio_assets
    DROP COLUMN total_shares,
    DROP COLUMN average_price,
    DROP COLUMN total_invested;

ALTER TABLE portfolios
    DROP COLUMN total_invested,
    DROP COLUMN current_value;
