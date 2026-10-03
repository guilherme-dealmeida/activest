-- Logos dos ativos (PLANO_BRAPI.md, F7, B17, B18 e B25).
--
-- O arquivo NAO mora no banco: fica na pasta data/logos/ da raiz do projeto,
-- montada no container da API. Aqui so o nome do arquivo guardado.
--
-- logo_path        nome do arquivo em data/logos ('PETR4.svg'). Nulo = sem logo;
--                  a tela mostra as iniciais do ticker.
-- logo_checked_at  quando a rotina procurou o logo pela ultima vez, achando ou
--                  nao. Nulo = nunca procurado: e assim que a rotina reconhece
--                  ativo novo na subida da API, sem varrer o catalogo inteiro.
ALTER TABLE assets ADD COLUMN logo_path       VARCHAR(100);
ALTER TABLE assets ADD COLUMN logo_checked_at TIMESTAMP;
