-- Origem da inativação de um papel (docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md, I5 e C7 — etapa F2, 29/10/2026).
--
-- A conferência do catálogo passa a inativar sozinha quando há prova (C7): CVM = registro da companhia cancelado na CVM
-- (menos migração de registro); FORM_25 = Form 25 da SEC (exterior, a partir da F4); LIST_GONE = o ticker sumiu da
-- lista oficial e está sem negócio há 30 pregões. MANUAL = decisão do administrador (inativar ou desfazer a inativação):
-- a tarefa nunca refaz o que o administrador decidiu. Nula = sem origem registrada (inativado antes da F2).
ALTER TABLE assets
    ADD COLUMN delisting_source VARCHAR(12),
    ADD CONSTRAINT ck_assets_delisting_source
        CHECK (delisting_source IS NULL OR delisting_source IN ('CVM', 'FORM_25', 'LIST_GONE', 'MANUAL'));

COMMENT ON COLUMN assets.delisting_source IS
    'Prova da inativação (I5): CVM, FORM_25, LIST_GONE ou MANUAL (decisão do administrador, nunca sobrescrita).';

-- A conferência deixa de ser "só mensagens" (B23 revogada na Impact deste plano, 28/10/2026).
UPDATE task_types
   SET description = 'Conferência do catálogo (tickers novos, sumidos e renomeados na B3) e dos índices atrasados. '
                     || 'Inativa o papel com prova formal (registro cancelado na CVM, ou fora da lista oficial e '
                     || 'sem negócio há 30 pregões) e avisa o administrador, que pode desfazer.'
 WHERE type = 'CATALOG_AUDIT';
