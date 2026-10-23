-- Tarefas (docs/planos/PLANO_TAREFAS.md, seção 1.7 e T1): o cadastro das rotinas, as rodadas e o log de cada rodada.
--
-- Tipo × tarefa (D24): o tipo é a rotina (o código que faz o trabalho) e os atributos que ela aceita, com o valor
-- padrão; a tarefa é uma instância do tipo, com nome, horário e os seus valores de atributo (espelhados do tipo ao
-- criar a tarefa — D28/O7). Duas tarefas do mesmo tipo rodam a mesma rotina com atributos diferentes.
--
-- Datas e horas no fuso de São Paulo (o mesmo dos horários das rotinas), gravadas pela aplicação.

CREATE TABLE task_types (
    id          BIGSERIAL    PRIMARY KEY,
    -- chave que o código usa para achar a rotina; nunca muda (D17)
    type        VARCHAR(80)  NOT NULL,
    description TEXT         NOT NULL,
    service     VARCHAR(10)  NOT NULL,
    -- desliga todas as tarefas do tipo (D11); no DIVIDENDS_SYNC, também o pedido sob demanda (D30)
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_task_types_type UNIQUE (type),
    CONSTRAINT ck_task_types_type CHECK (type ~ '^[A-Z0-9]+(_[A-Z0-9]+)*$'),
    CONSTRAINT ck_task_types_service CHECK (service IN ('ADM_API', 'APP_API'))
);

CREATE TABLE task_type_attributes (
    id           BIGSERIAL    PRIMARY KEY,
    task_type_id BIGINT       NOT NULL,
    att_name     VARCHAR(80)  NOT NULL,
    -- valor padrão; nulo = sem padrão (para atributos de período, nulo = rodada incremental)
    att_value    TEXT,
    value_type   VARCHAR(10)  NOT NULL,
    required     BOOLEAN      NOT NULL DEFAULT FALSE,
    description  TEXT         NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_task_type_attributes_type FOREIGN KEY (task_type_id) REFERENCES task_types (id) ON DELETE CASCADE,
    CONSTRAINT uk_task_type_attributes UNIQUE (task_type_id, att_name),
    CONSTRAINT ck_task_type_attributes_name CHECK (att_name ~ '^[A-Z0-9]+(_[A-Z0-9]+)*$'),
    CONSTRAINT ck_task_type_attributes_value_type
        CHECK (value_type IN ('YEAR', 'DATE', 'BOOLEAN', 'INTEGER', 'TEXT', 'LIST'))
);

CREATE TABLE tasks (
    id           BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(80)  NOT NULL,
    -- opcional: vazia, a tela mostra a do tipo (O1)
    description  TEXT,
    -- cron do Spring (6 campos); nulo = só sob demanda
    cron         VARCHAR(120),
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    task_type_id BIGINT       NOT NULL,
    -- exclusão lógica da tarefa com rodadas (O5); sem rodadas, a linha é apagada
    deleted_at   TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tasks_type FOREIGN KEY (task_type_id) REFERENCES task_types (id),
    CONSTRAINT ck_tasks_name CHECK (name ~ '^[A-Z0-9]+(_[A-Z0-9]+)*$')
);

-- nome único entre as tarefas vivas: uma excluída não prende o nome
CREATE UNIQUE INDEX uk_tasks_name ON tasks (name) WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_type ON tasks (task_type_id);

CREATE TABLE task_attributes (
    id                     BIGSERIAL PRIMARY KEY,
    task_id                BIGINT    NOT NULL,
    -- nome, tipo do valor, obrigatório e descrição vêm do atributo do tipo (O7)
    task_type_attribute_id BIGINT    NOT NULL,
    att_value              TEXT,
    created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_task_attributes_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_attributes_type_attribute FOREIGN KEY (task_type_attribute_id)
        REFERENCES task_type_attributes (id) ON DELETE CASCADE,
    CONSTRAINT uk_task_attributes UNIQUE (task_id, task_type_attribute_id)
);

CREATE TABLE task_executions (
    id                BIGSERIAL    PRIMARY KEY,
    -- sem cascata: tarefa com rodadas só tem exclusão lógica (O5)
    task_id           BIGINT       NOT NULL,
    status            VARCHAR(12)  NOT NULL,
    trigger_type      VARCHAR(10)  NOT NULL,
    -- quem pediu a rodada (nulo = o agendador ou a subida)
    requested_by      UUID,
    -- data de vigência (1d): o horário a que a rodada pertence; na manual, o momento do pedido
    scheduled_for     TIMESTAMP    NOT NULL,
    started_at        TIMESTAMP    NOT NULL,
    finished_at       TIMESTAMP,
    stop_requested_at TIMESTAMP,
    stop_requested_by UUID,
    -- cópia dos atributos da tarefa no início da rodada
    parameters        JSONB,
    summary           TEXT,
    error_message     TEXT,
    -- linhas INFO que passaram do teto do log (D6)
    log_lines_omitted INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT fk_task_executions_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_executions_requested_by FOREIGN KEY (requested_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_task_executions_stop_by FOREIGN KEY (stop_requested_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_task_executions_status
        CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'SKIPPED')),
    CONSTRAINT ck_task_executions_trigger CHECK (trigger_type IN ('SCHEDULE', 'MANUAL', 'STARTUP')),
    -- só a rodada em execução não tem fim
    CONSTRAINT ck_task_executions_finished CHECK ((status = 'RUNNING') = (finished_at IS NULL))
);

CREATE INDEX idx_task_executions_task ON task_executions (task_id, started_at DESC);
CREATE INDEX idx_task_executions_started ON task_executions (started_at DESC);
CREATE INDEX idx_task_executions_running ON task_executions (task_id) WHERE status = 'RUNNING';

CREATE TABLE task_execution_logs (
    id           BIGSERIAL   PRIMARY KEY,
    execution_id BIGINT      NOT NULL,
    logged_at    TIMESTAMP   NOT NULL,
    level        VARCHAR(5)  NOT NULL,
    message      TEXT        NOT NULL,
    CONSTRAINT fk_task_execution_logs_execution FOREIGN KEY (execution_id)
        REFERENCES task_executions (id) ON DELETE CASCADE,
    CONSTRAINT ck_task_execution_logs_level CHECK (level IN ('INFO', 'WARN', 'ERROR'))
);

CREATE INDEX idx_task_execution_logs_execution ON task_execution_logs (execution_id, id);
CREATE INDEX idx_task_execution_logs_logged ON task_execution_logs (logged_at);
