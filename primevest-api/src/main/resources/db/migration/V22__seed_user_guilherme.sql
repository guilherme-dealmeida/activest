-- Usuário inicial para desenvolvimento.
--
-- O hash bcrypt vem do catálogo anterior, então a senha continua a mesma que
-- você já usava. Em produção este arquivo não deveria existir: credencial
-- versionada em repositório é credencial pública.
--
-- A carteira também é criada aqui. O cadastro por /auth/register cria uma
-- automaticamente, mas usuário semeado não passa por esse caminho — e sem
-- carteira o lançamento da primeira transação falha com "Portfolio não encontrado".
INSERT INTO users (id, name, email, password_hash, is_admin, created_at, updated_at)
VALUES (
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    'Guilherme',
    'guilhermeunatec@gmail.com',
    '$2a$10$5zNJNyyyD3SjCWEdElZjYOULe4bZEwGz2s9i/fk0Fp7LoZZdTbwVm',
    true,
    NOW(),
    NOW()
)
ON CONFLICT (email) DO UPDATE
    SET name     = EXCLUDED.name,
        is_admin = EXCLUDED.is_admin;

-- Carteira padrão do usuário, com o mesmo nome que o registro por API usaria.
INSERT INTO portfolios (id, user_id, name, total_invested, current_value, total_dividends)
SELECT
    'b1b2c3d4-e5f6-7890-abcd-ef1234567890',
    u.id,
    'Minha Carteira',
    0, 0, 0
FROM users u
WHERE u.email = 'guilhermeunatec@gmail.com'
  AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = u.id);
