-- Custo da bonificação informado pelo usuário (docs/planos/PLANO_NOTIFICACOES.md, 3.4 e 5.4; PENDENCIAS 13.2).
-- A B3 não informa o custo atribuído às ações bonificadas (remarks vazio nos casos reais): elas entram com custo
-- zero provisório. O usuário informa o valor por ação do fato relevante e ele vale só para a posição dele — dado de
-- usuário, gravado pela app-api; asset_stock_events.assigned_cost (catálogo) não é tocado.
CREATE TABLE stock_event_costs (
    portfolio_asset_id UUID          NOT NULL,
    stock_event_id     BIGINT        NOT NULL,
    cost_per_share     NUMERIC(18,8) NOT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (portfolio_asset_id, stock_event_id),
    CONSTRAINT fk_stock_event_costs_position FOREIGN KEY (portfolio_asset_id)
        REFERENCES portfolio_assets (id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_event_costs_event FOREIGN KEY (stock_event_id)
        REFERENCES asset_stock_events (id) ON DELETE CASCADE,
    CONSTRAINT ck_stock_event_costs_positive CHECK (cost_per_share > 0)
);
