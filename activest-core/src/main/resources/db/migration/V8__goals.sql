-- Metas financeiras calculadas a partir da carteira (docs/planos/PLANO_METAS.md, etapa M1).
--
-- O usuario define o objetivo (tipo, alvo, prazo, escopo, parametros); o Activest calcula situacao, progresso e
-- projecao na leitura. A tabela goals da V1 (CRUD com "valor atual" digitado) e substituida: estava vazia em
-- 24/10/2026, e a guarda abaixo recusa a migration se alguem tiver gravado meta no formato antigo.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM goals) THEN
        RAISE EXCEPTION 'V8: goals tem linhas no formato antigo; exporte-as antes de recriar a tabela (PLANO_METAS.md)';
    END IF;
END $$;

DROP TABLE goals;

-- A meta: o que o usuario decidiu. Colunas para o que a lista filtra e ordena; o resto do tipo em JSONB validado
-- pelo catalogo de tipos (D2) — tipo novo sem migration.
CREATE TABLE goals (
    id                       UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id                  UUID NOT NULL,
    name                     VARCHAR(120) NOT NULL,
    type                     VARCHAR(10) NOT NULL,
    category                 VARCHAR(20) NOT NULL,
    lifecycle                VARCHAR(12) NOT NULL DEFAULT 'ATIVA',
    lifecycle_before_archive VARCHAR(12),
    target_value             NUMERIC(18,8),
    target_date              DATE,
    scope                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    params                   JSONB NOT NULL DEFAULT '{}'::jsonb,
    notify                   BOOLEAN NOT NULL DEFAULT TRUE,
    notes                    VARCHAR(500),
    revision                 INTEGER NOT NULL DEFAULT 1,
    achieved_on              DATE,
    final_result             JSONB,
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT goals_pkey PRIMARY KEY (id),
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_goals_lifecycle CHECK ((lifecycle IN ('ATIVA', 'PAUSADA', 'CONCLUIDA', 'ARQUIVADA'))),
    CONSTRAINT ck_goals_lifecycle_before_archive CHECK (((lifecycle_before_archive IS NULL)
        OR (lifecycle_before_archive IN ('ATIVA', 'PAUSADA', 'CONCLUIDA')))),
    CONSTRAINT ck_goals_revision CHECK ((revision >= 1))
);

CREATE INDEX idx_goals_user_lifecycle ON goals (user_id, lifecycle);

-- Alteracoes e resultados da meta (criterio 13): definicao completa nas revisoes, o que aconteceu nos demais.
CREATE TABLE goal_events (
    id          BIGSERIAL,
    goal_id     UUID NOT NULL,
    kind        VARCHAR(30) NOT NULL,
    revision    INTEGER NOT NULL,
    definition  JSONB,
    detail      JSONB,
    occurred_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT goal_events_pkey PRIMARY KEY (id),
    CONSTRAINT fk_goal_events_goal FOREIGN KEY (goal_id) REFERENCES goals(id) ON DELETE CASCADE,
    CONSTRAINT ck_goal_events_kind CHECK ((kind IN ('CRIADA', 'EDITADA', 'PAUSADA', 'RETOMADA', 'CONCLUIDA',
        'ARQUIVADA', 'DESARQUIVADA', 'ATINGIDA', 'SAIU_DO_OBJETIVO', 'VOLTOU_AO_OBJETIVO', 'PRAZO_VENCIDO')))
);

CREATE INDEX idx_goal_events_goal ON goal_events (goal_id, occurred_at);

-- Fotografia diaria (D3): o que a meta mostrava no dia, com a revisao vigente. Auditoria — nao e reescrita quando um
-- dado do passado muda (a evolucao recalculada sai na leitura).
CREATE TABLE goal_snapshots (
    goal_id        UUID NOT NULL,
    reference_date DATE NOT NULL,
    revision       INTEGER NOT NULL,
    indicator      NUMERIC(18,8),
    target         NUMERIC(18,8),
    progress_pct   NUMERIC(12,2),
    situation      VARCHAR(30) NOT NULL,
    quality        VARCHAR(15) NOT NULL,
    result         JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT goal_snapshots_pkey PRIMARY KEY (goal_id, reference_date),
    CONSTRAINT fk_goal_snapshots_goal FOREIGN KEY (goal_id) REFERENCES goals(id) ON DELETE CASCADE,
    CONSTRAINT ck_goal_snapshots_quality CHECK ((quality IN ('COMPLETA', 'PARCIAL', 'INDISPONIVEL')))
);

-- Rodada diaria das metas (D24): fotografia, eventos e avisos, depois do POSITIONS_REFRESH das 21:00.
INSERT INTO task_types (type, description, service, enabled, stoppable)
VALUES ('GOALS_SNAPSHOT', 'Metas: avalia as metas ativas de cada usuário, grava a fotografia do dia, os eventos (atingiu, saiu do objetivo, prazo vencido) e os avisos.', 'APP_API', true, true);

INSERT INTO tasks (name, description, cron, enabled, task_type_id, run_on_startup)
SELECT 'GOALS_SNAPSHOT', NULL, '0 30 21 * * *', true, id, false FROM task_types WHERE type = 'GOALS_SNAPSHOT';
