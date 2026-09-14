-- Remove o BRAX do catálogo de ações.
--
-- Ele entrou pela V18 como ('BRAX', 'BRAX', NULL): ticker sem sufixo numérico,
-- nome igual ao ticker e sem setor — o único dos 782 papéis sem classificação
-- setorial. Não é uma ação: é o ETF que replica o IBrX, negociado como BRAX11.
-- Ficou no seed de ACAO por engano.
--
-- ETFs terão seu próprio cadastro depois, sob o tipo ETF, que já existe desde a
-- V13 com price_source = BRAPI. Deixá-lo como ação até lá poluiria o
-- autocomplete e a alocação por setor com um ativo que não é o que diz ser.
--
-- Quatro tabelas referenciam assets — portfolio_assets, transactions (via
-- portfolio_assets), dividends, asset_price_history e ceiling_prices — e
-- nenhuma delas tem ON DELETE CASCADE. Se houver qualquer posição, provento ou
-- preço-teto ligado ao BRAX, este DELETE falha e a subida para, em vez de
-- apagar o vínculo em silêncio. Nesse caso a correção é remover o registro
-- dependente antes, conscientemente.
DELETE FROM assets a
USING asset_types t
WHERE t.id = a.asset_type_id
  AND t.name = 'ACAO'
  AND a.ticker = 'BRAX';
