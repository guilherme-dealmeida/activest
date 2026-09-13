CREATE TABLE strategies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    asset_type_id BIGINT NOT NULL REFERENCES asset_types(id),
    target_percentage DECIMAL(5,2) NOT NULL CHECK (target_percentage >= 0 AND target_percentage <= 100),
    include_reservation BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, asset_type_id)
);
CREATE INDEX idx_strategies_user ON strategies(user_id);
