-- portfolios.total_dividends sai, pelo mesmo motivo de
-- portfolio_assets.total_dividends em V26: o total de proventos da carteira é
-- a soma de dividends.total_amount das posições dela, calculada na leitura.
-- Gravado, só ficava certo enquanto alguém lembrasse de recalculá-lo.
--
-- V22 ainda insere essa coluna no seed da carteira. Não se edita migration
-- aplicada: num banco novo V22 grava o valor e esta migration o derruba logo
-- depois.
ALTER TABLE portfolios DROP COLUMN total_dividends;
