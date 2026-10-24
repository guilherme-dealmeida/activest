-- Usuario administrador do ambiente de desenvolvimento, a carteira dele e a reserva da estrategia.
--
-- Consolidado em 2026-10-23 (docs/planos/PLANO_RENOMEACAO_ACTIVEST.md, 4.2): o estado final que as migrations V1–V67
-- do PrimeVest deixavam, com ids explicitos e as sequencias ajustadas no fim. Datas de auditoria ficam com o default.


-- users (1 linhas; origem: V9, V41)
INSERT INTO users (id, name, email, password_hash, google_id, avatar_url, is_admin, phone, cpf_ciphertext, cpf_iv, cpf_key_version, cpf_hash, email_verified_at, password_changed_at, terms_accepted_at, terms_version, theme) VALUES
    ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'Guilherme', 'guilhermeunatec@gmail.com', '$2a$10$5zNJNyyyD3SjCWEdElZjYOULe4bZEwGz2s9i/fk0Fp7LoZZdTbwVm', NULL, NULL, true, NULL, NULL, NULL, NULL, NULL, '2026-10-23 22:12:54.106384', NULL, '2026-10-23 22:12:54.106384', '2026-10-provisorio', 'DARK');


-- portfolios (1 linhas; origem: V9)
INSERT INTO portfolios (id, user_id, name) VALUES
    ('b1b2c3d4-e5f6-7890-abcd-ef1234567890', 'a1b2c3d4-e5f6-7890-abcd-ef1234567890', 'Minha Carteira');


-- strategy_reservations (1 linhas; origem: V12)
INSERT INTO strategy_reservations (user_id, type_reservation, target_percentage, target_value) VALUES
    ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', 1, 0.00, NULL);
