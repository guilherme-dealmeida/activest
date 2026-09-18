-- Venda de ativos: grupo de venda, liquidacao total e a data do titulo.
--
-- 1. transactions.sale_group_id
--    No Tesouro Direto o saldo e do ATIVO, mas a posicao e do TITULO: uma venda
--    de 5 cotas pode consumir dois titulos comprados em datas diferentes. Cada
--    parte vira uma transacao, presa ao titulo que ela liquida, e o grupo e o
--    que diz que as duas partes sao a MESMA venda do usuario.
--
--    Sem o grupo, "a venda de 5 cotas" deixaria de existir como objeto: seriam
--    duas linhas soltas, editaveis em separado, e bastaria mexer numa delas
--    para furar o FIFO. Com o grupo, editar e excluir agem sobre a venda
--    inteira e a reparticao por titulo e refeita a cada mudanca.
--
--    Nulo em compra e em venda de ativo cujo saldo nao e por titulo (renda
--    variavel e Renda Fixa).
--
-- 2. transactions.is_full_settlement
--    A marca de "liquidacao total" de uma venda de Renda Fixa. Fica na propria
--    transacao porque a regra precisa dela duas vezes: para reabrir o
--    formulario com a marca no estado certo, e para reavaliar a venda quando
--    ela e editada. O is_settled da posicao e derivado daqui.
--
-- 3. fixed_income_notes.purchase_date
--    A data em que o titulo foi comprado. No Tesouro Direto e ela que ordena o
--    FIFO ("as vendas saem dos titulos mais antigos primeiro") e e ela que a
--    regra H torna unica: um ativo do Tesouro nao tem dois titulos comprados no
--    mesmo dia — se o usuario comprar de novo na mesma data, o certo e alterar
--    a quantidade da compra que ja existe.
--
--    Nula na Renda Fixa: la o mesmo titulo recebe varios aportes, inclusive no
--    mesmo dia, e o saldo e por valor, nao por cota. Por isso o indice unico e
--    parcial: ele so vale onde a data existe.
ALTER TABLE transactions
    ADD COLUMN sale_group_id      UUID,
    ADD COLUMN is_full_settlement BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_transactions_sale_group ON transactions (sale_group_id);

ALTER TABLE fixed_income_notes
    ADD COLUMN purchase_date DATE;

CREATE UNIQUE INDEX uk_fixed_income_notes_purchase
    ON fixed_income_notes (user_id, asset_id, purchase_date)
    WHERE purchase_date IS NOT NULL;
