-- Notificações (docs/planos/PLANO_NOTIFICACOES.md, seção 3.1): a caixa de mensagens do usuário e do administrador.
--
-- Dois eixos independentes (regra R1):
--   estado  — como cada pessoa vê a mensagem (lida, arquivada, excluída): notification_states, uma linha por pessoa;
--             sem linha = não lida, na Entrada;
--   status  — a situação do assunto: nulo (mensagem informativa) ou NAO_RESOLVIDA / DISPENSADA / RESOLVIDA.
--
-- Mensagem de administrador (audience ADMIN) não tem dono: todos os admins a veem, cada um com o seu estado, e o
-- status vale para todos (R9).

CREATE TABLE notifications (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    audience    VARCHAR(10)  NOT NULL,
    user_id     UUID,
    kind        VARCHAR(40)  NOT NULL,
    category    VARCHAR(20)  NOT NULL,
    severity    VARCHAR(10)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT         NOT NULL,
    ref_type    VARCHAR(30),
    ref_id      VARCHAR(64),
    -- linhas da mensagem e ações ({"lines": [...], "actions": [...]})
    payload     JSONB,
    status      VARCHAR(15),
    status_at   TIMESTAMP,
    -- quem dispensou ou reabriu; nulo = o sistema
    status_by   UUID,
    -- regra R10: a mesma chave não se repete (informativa: nunca; pendência: enquanto não resolvida)
    dedup_key   VARCHAR(200),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_audience CHECK ((audience = 'USER' AND user_id IS NOT NULL)
                                             OR (audience = 'ADMIN' AND user_id IS NULL)),
    CONSTRAINT ck_notifications_severity CHECK (severity IN ('INFO', 'ATENCAO')),
    CONSTRAINT ck_notifications_status CHECK (status IS NULL OR status IN ('NAO_RESOLVIDA', 'DISPENSADA', 'RESOLVIDA'))
);

-- Informativa: a chave já traz o que a torna única (ex.: ativo + dia) e nunca se repete.
CREATE UNIQUE INDEX uk_notifications_info_key ON notifications (audience, COALESCE(user_id::text, ''), dedup_key)
    WHERE dedup_key IS NOT NULL AND status IS NULL;

-- Pendência: uma por chave enquanto não for resolvida (dispensada também segura: a rotina não reabre o que a pessoa
-- dispensou). Resolvida libera a chave — se o problema voltar, nasce outra.
CREATE UNIQUE INDEX uk_notifications_open_key ON notifications (audience, COALESCE(user_id::text, ''), dedup_key)
    WHERE dedup_key IS NOT NULL AND status IN ('NAO_RESOLVIDA', 'DISPENSADA');

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_admin ON notifications (created_at DESC) WHERE audience = 'ADMIN';
CREATE INDEX idx_notifications_ref ON notifications (ref_type, ref_id);

CREATE TABLE notification_states (
    notification_id UUID      NOT NULL,
    user_id         UUID      NOT NULL,
    read_at         TIMESTAMP,
    archived_at     TIMESTAMP,
    -- exclusão lógica (R3): a mensagem some para a pessoa e fica no banco
    deleted_at      TIMESTAMP,
    PRIMARY KEY (notification_id, user_id),
    CONSTRAINT fk_notification_states_notification FOREIGN KEY (notification_id)
        REFERENCES notifications (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_states_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_notification_states_user ON notification_states (user_id);
