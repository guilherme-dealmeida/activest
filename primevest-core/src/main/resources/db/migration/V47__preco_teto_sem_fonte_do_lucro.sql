-- Preço Teto (docs/planos/PLANO_PRECO_TETO.md, revisão pós-implementação de 11/10/2026): a fonte/observação do lucro
-- trimestral deixa de existir — nem a referência do documento da CVM nem a nota do usuário. A origem (PADRAO ou
-- INFORMADO) continua.

ALTER TABLE ceiling_price_quarters DROP COLUMN source_ref;
