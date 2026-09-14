-- Usuários do sistema. Autenticação por email/senha (password_hash) ou Google (google_id).
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    google_id     VARCHAR(255),
    avatar_url    VARCHAR(512),
    is_admin      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_email     UNIQUE (email),
    CONSTRAINT uk_users_google_id UNIQUE (google_id)
);

CREATE INDEX idx_users_email     ON users (email);
CREATE INDEX idx_users_google_id ON users (google_id);
