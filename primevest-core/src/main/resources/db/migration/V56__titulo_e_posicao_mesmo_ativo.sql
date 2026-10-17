-- Titulo e posicao no mesmo produto (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, andamento de 16/10/2026).
--
-- O produto (CDB, RDB, Tesouro IPCA+ 2050...) esta em dois lugares: na posicao (portfolio_assets.asset_id, que toda
-- posicao tem, inclusive acao e FII) e no titulo (fixed_income_notes.asset_id, que as regras do titulo usam: tipo de
-- emissora aceito, regra H do Tesouro, busca dos titulos de um ativo). Nada obrigava os dois a serem iguais: um
-- UPDATE so na posicao deixou um RDB com titulo de CDB, e a edicao recusou a emissora certa.
--
-- A FK de portfolio_assets.note_id passa a levar o produto junto: (note_id, asset_id) -> (id, asset_id) do titulo.
-- Posicao sem titulo (renda variavel, note_id nulo) nao e conferida (MATCH SIMPLE). ON UPDATE CASCADE: trocar o
-- produto do titulo leva a posicao junto; trocar so a posicao e recusado. Remover a duplicacao (o titulo ler o produto
-- da posicao) ficou como pendencia (PENDENCIAS 28.5).

ALTER TABLE fixed_income_notes ADD CONSTRAINT uk_fixed_income_notes_id_asset UNIQUE (id, asset_id);

ALTER TABLE portfolio_assets DROP CONSTRAINT fk_portfolio_assets_note;
ALTER TABLE portfolio_assets ADD CONSTRAINT fk_portfolio_assets_note
    FOREIGN KEY (note_id, asset_id) REFERENCES fixed_income_notes (id, asset_id) ON UPDATE CASCADE;
