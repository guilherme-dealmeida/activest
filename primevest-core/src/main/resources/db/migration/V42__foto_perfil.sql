-- Foto de perfil enviada pelo usuario (docs/planos/PLANO_CADASTRO_USUARIO.md, adendo de 05/10/2026).
--
-- No banco (decisao A1 do adendo): JPEG 512x512 ja reduzido e sem metadados (~30-80 KB), apagado junto
-- com a conta. Tabela 1:1 propria, e nao coluna em users, para a imagem nao ser carregada a cada leitura
-- do usuario (o filtro, a carteira e as integracoes leem users o tempo todo).
-- users.avatar_url continua sendo a foto do Google (da criacao); a enviada tem precedencia.
CREATE TABLE user_avatars (
    user_id      UUID        PRIMARY KEY,
    image        BYTEA       NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    updated_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_avatars_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_avatars_tamanho CHECK (octet_length(image) <= 1048576),
    CONSTRAINT ck_user_avatars_tipo CHECK (content_type IN ('image/jpeg'))
);
