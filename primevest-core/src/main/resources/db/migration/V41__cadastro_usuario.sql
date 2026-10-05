-- Cadastro de usuario (docs/planos/PLANO_CADASTRO_USUARIO.md, secao 5).
--
-- Tudo aqui e atributo do usuario, nao estado de processo: o cadastro pendente e os codigos de
-- verificacao ficam no Redis com TTL (D3), sem tabela auxiliar.

-- G6: e-mail sem diferenca de maiusculas. Antes do indice, normaliza o que ja existe; se duas
-- contas colidirem a migration falha no indice, de proposito — juntar contas e decisao humana.
UPDATE users SET email = lower(trim(email)) WHERE email <> lower(trim(email));

ALTER TABLE users
    -- D6: contato opcional, unico, em E.164 (+5541999999999). Sem verificacao (D8: so e-mail).
    ADD COLUMN phone               VARCHAR(20),
    -- D12/D18: CPF opcional e unico, cifrado com a chave das credenciais (AES-256-GCM, o id do
    -- usuario como dado associado). cpf_hash (HMAC-SHA256, hex) da a unicidade sem decifrar.
    ADD COLUMN cpf_ciphertext      BYTEA,
    ADD COLUMN cpf_iv              BYTEA,
    ADD COLUMN cpf_key_version     SMALLINT,
    ADD COLUMN cpf_hash            VARCHAR(64),
    -- Quando o e-mail foi confirmado (codigo, ou o Google, que ja garante).
    ADD COLUMN email_verified_at   TIMESTAMP,
    -- D15: token emitido antes disto e recusado (troca/redefinicao de senha derruba as sessoes).
    ADD COLUMN password_changed_at TIMESTAMP,
    -- D9/D19: aceite dos termos de uso e da politica de privacidade, com a versao aceita.
    ADD COLUMN terms_accepted_at   TIMESTAMP,
    ADD COLUMN terms_version       VARCHAR(30),
    -- D13: tema por usuario (o menu recolhido continua no navegador).
    ADD COLUMN theme               VARCHAR(10) NOT NULL DEFAULT 'DARK';

ALTER TABLE users ADD CONSTRAINT ck_users_theme CHECK (theme IN ('DARK', 'LIGHT'));
ALTER TABLE users ADD CONSTRAINT ck_users_cpf_completo CHECK (
    (cpf_hash IS NULL AND cpf_ciphertext IS NULL AND cpf_iv IS NULL AND cpf_key_version IS NULL)
    OR (cpf_hash IS NOT NULL AND cpf_ciphertext IS NOT NULL AND cpf_iv IS NOT NULL AND cpf_key_version IS NOT NULL));

-- uk_users_email (V1) continua; este garante a unicidade sem diferenca de caixa.
CREATE UNIQUE INDEX uk_users_email_lower ON users (lower(email));
CREATE UNIQUE INDEX uk_users_phone ON users (phone)    WHERE phone    IS NOT NULL;
CREATE UNIQUE INDEX uk_users_cpf   ON users (cpf_hash) WHERE cpf_hash IS NOT NULL;

-- Usuario semeado pela V9: e-mail tido como verificado e aceite da versao provisoria dos termos
-- (a mesma de LegalTerms.CURRENT_VERSION).
UPDATE users
   SET email_verified_at = COALESCE(email_verified_at, NOW()),
       terms_accepted_at = NOW(),
       terms_version     = '2026-10-provisorio'
 WHERE email = 'guilhermeunatec@gmail.com';
