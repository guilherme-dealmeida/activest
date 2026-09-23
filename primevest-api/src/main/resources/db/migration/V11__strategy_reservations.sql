-- -----------------------------------------------------------------------------
-- strategy_reservations
-- -----------------------------------------------------------------------------
-- Estrategia da reserva de emergencia: quanto da carteira o usuario quer em
-- posicoes marcadas como reserva (portfolio_assets.is_reservation). O resto da
-- carteira e a geracao de renda.
--
-- Uma linha por usuario, sempre (regras A e B): nasce com o usuario, nunca e
-- apagada pela aplicacao. A unicidade em user_id e a regra A no banco, e nao
-- so no codigo.
--
-- type_reservation e numerico por especificacao do escopo: 1 = por
-- percentual, 2 = por valor. No Java ele e o enum ReservationType, lido por
-- um AttributeConverter — o resto do projeto grava enum como VARCHAR, e esta
-- e a excecao.
--
-- O alvo mora em uma das duas colunas, conforme o tipo, e a outra fica nula.
-- A checagem abaixo garante isso: sem ela as duas acumulariam o que sobrou de
-- quando o usuario trocou de tipo, e o calculo dependeria de qual delas
-- alguem leu.
CREATE TABLE strategy_reservations (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID          NOT NULL,
    type_reservation  SMALLINT      NOT NULL DEFAULT 1,
    target_percentage NUMERIC(5,2),
    target_value      NUMERIC(18,2),
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_strategy_reservations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_strategy_reservations_user UNIQUE (user_id),
    CONSTRAINT ck_strategy_reservations_type CHECK (type_reservation IN (1, 2)),
    CONSTRAINT ck_strategy_reservations_target CHECK (
        (type_reservation = 1
            AND target_percentage IS NOT NULL
            AND target_percentage >= 0 AND target_percentage <= 100
            AND target_value IS NULL)
        OR
        (type_reservation = 2
            AND target_value IS NOT NULL
            AND target_value >= 0
            AND target_percentage IS NULL)
    )
);
