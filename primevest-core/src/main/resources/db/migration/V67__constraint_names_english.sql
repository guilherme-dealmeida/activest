-- Nomes internos em inglês (docs/planos/PLANO_NOMES_EM_INGLES.md, E2, 22/10/2026): as duas constraints com nome em
-- português que a V66 não pegou (achadas na conferência do banco depois dela). Só o nome muda; as regras ficam iguais.

ALTER TABLE user_avatars RENAME CONSTRAINT ck_user_avatars_tamanho TO ck_user_avatars_size;
ALTER TABLE users RENAME CONSTRAINT ck_users_cpf_completo TO ck_users_cpf_complete;
