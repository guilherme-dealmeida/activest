-- Liquidação automática do título vencido (docs/planos/PLANO_NOTIFICACOES.md, seção 6; revoga a D8 do plano de
-- valorização da renda fixa). A venda que o sistema cria tem origem própria, SISTEMA: sem external_id nem snapshot
-- da fonte; edited_at marca que o usuário a editou à mão (aí a rotina não a refaz).
ALTER TABLE transactions DROP CONSTRAINT ck_transactions_origin;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_origin CHECK (
    (origin = 'MANUAL' AND external_id IS NULL AND source_snapshot IS NULL AND edited_at IS NULL)
    OR (origin = 'AUTO' AND external_id IS NOT NULL AND source_snapshot IS NOT NULL)
    OR (origin = 'SISTEMA' AND external_id IS NULL AND source_snapshot IS NULL)
);
