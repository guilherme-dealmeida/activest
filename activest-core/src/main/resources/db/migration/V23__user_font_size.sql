-- Tamanho da tela por usuario (docs/planos/PLANO_CUSTOMIZAR_TAMANHOS.md, 10/11/2026), no molde do tema.
-- LARGE = o tamanho de sempre (padrao, tambem para quem ja existe — REGRAS 8.1); SMALL = tudo reduzido (87,5%).

ALTER TABLE users ADD COLUMN font_size VARCHAR(10) NOT NULL DEFAULT 'LARGE';
ALTER TABLE users ADD CONSTRAINT ck_users_font_size CHECK (font_size IN ('LARGE', 'SMALL'));
