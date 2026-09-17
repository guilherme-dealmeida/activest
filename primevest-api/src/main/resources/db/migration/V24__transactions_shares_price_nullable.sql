-- Renda Fixa passa a ser lançada por valor total.
--
-- Um CDB, uma LCI ou uma debênture comprados no banco não têm "quantidade x
-- preço unitário" que o usuário conheça: o extrato mostra quanto foi aplicado.
-- Obrigar a digitar 1 x R$ 5.000,00 era inventar uma quantidade. Para o tipo
-- RENDA_FIXA a transação guarda só total_amount; shares e price_per_share
-- ficam nulos.
--
-- Tesouro Direto continua por quantidade x PU: o título é negociado em frações
-- com preço unitário publicado.
--
-- A regra "renda variável e Tesouro exigem quantidade e preço" vive na
-- aplicação, pelo mesmo motivo da regra da nota em V9: um CHECK precisaria
-- enxergar o tipo do ativo a partir daqui.
ALTER TABLE transactions ALTER COLUMN shares          DROP NOT NULL;
ALTER TABLE transactions ALTER COLUMN price_per_share DROP NOT NULL;
