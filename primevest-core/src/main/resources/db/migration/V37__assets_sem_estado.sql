-- M7 (PLANO_EMPRESAS_BALANCOS.md, 2.3 e F10): sai de assets o que nao e do ativo.
--   cnpj                   e do emissor: issuers.cnpj (em 28/09/2026 os 6 preenchidos eram
--                          iguais ao do emissor ligado).
--   cash_history_loaded_at estado da rotina de proventos: o historico da B3 fica em
--                          data/b3/proventos/{EMISSOR}.json e o ativo com evento B3_HISTORICO
--                          ja tem o dele; o do FII e o que esta em data/fnet e ja foi gravado.
--   logo_path              o arquivo em data/logos/{TICKER}.{ext} e o estado (AssetLogos).
--   logo_checked_at        idem; a rotina mensal procura so quem nao tem arquivo.
ALTER TABLE assets DROP COLUMN cnpj;
ALTER TABLE assets DROP COLUMN cash_history_loaded_at;
ALTER TABLE assets DROP COLUMN logo_path;
ALTER TABLE assets DROP COLUMN logo_checked_at;
