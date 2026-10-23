-- Nomes internos em inglês (docs/planos/PLANO_NOMES_EM_INGLES.md, E2, 22/10/2026): a estrutura é em inglês, e esta
-- era a única constraint com nome em português. Só o nome muda; a regra (content_type = image/jpeg) fica igual.

ALTER TABLE user_avatars RENAME CONSTRAINT ck_user_avatars_tipo TO ck_user_avatars_content_type;
