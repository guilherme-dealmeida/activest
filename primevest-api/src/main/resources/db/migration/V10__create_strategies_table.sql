-- Percentual ideal por tipo de ativo, base do rebalanceamento.
-- Um registro por usuário e tipo.
CREATE TABLE strategies (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL,
    asset_type_id       BIGINT       NOT NULL,
    target_percentage   NUMERIC(5,2) NOT NULL,
    include_reservation BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_strategies_user  FOREIGN KEY (user_id)       REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_strategies_type  FOREIGN KEY (asset_type_id) REFERENCES asset_types (id),
    CONSTRAINT uk_strategies_scope UNIQUE (user_id, asset_type_id),
    CONSTRAINT ck_strategies_percentage CHECK (target_percentage >= 0 AND target_percentage <= 100)
);

CREATE INDEX idx_strategies_user ON strategies (user_id);
