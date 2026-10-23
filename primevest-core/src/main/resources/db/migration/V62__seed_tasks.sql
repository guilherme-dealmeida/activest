-- Seed das tarefas (docs/planos/PLANO_TAREFAS.md, T1 e 6.3): os 26 tipos (as rotinas de hoje + a limpeza do
-- histórico, D34), os atributos de cada tipo e uma tarefa por tipo, com o horário que estava no application.yml
-- (D10: daqui em diante o banco manda). FK resolvida por nome.

INSERT INTO task_types (type, service, description) VALUES
-- adm-api, agendadas
('B3_CLOSING', 'ADM_API', E'Fechamento da B3 dos ativos em carteira (BRAPI): grava o OHLCV dos últimos pregões e o preço atual, e avisa a carteira para revalorizar as posições.'),
('CRYPTO_CLOSING', 'ADM_API', E'Fechamento das criptomoedas em carteira (CoinGecko): preço em reais do dia e preço atual, e avisa a carteira para revalorizar as posições.'),
('CRYPTO_PRICE_HISTORY', 'ADM_API', E'Histórico de preço de cripto: CoinGecko nos últimos 365 dias e Binance BTC/BRL antes disso, para toda cripto que está ou esteve em carteira e para o benchmark BTC. Só preenche dia sem preço.'),
('DIVIDENDS_SYNC', 'ADM_API', E'Proventos e eventos societários do catálogo (B3 e FNET), por emissor, e as datas de pagamento do relatório de proventos da CVM; avisa a carteira para refazer os proventos das posições.'),
('FNET_STATUS_SYNC', 'ADM_API', E'Situação (ativo, inativo, cancelado) de cada aviso do FNET de todos os fundos e, em seguida, os proventos relendo todos os avisos em disco: absorve retificações e avisos inativados.'),
('CVM_STATEMENTS_IMPORT', 'ADM_API', E'Balanços da CVM (DFP e ITR), série trimestral e informe mensal de FII. Sem período: ano corrente e anterior, só o que mudou.'),
('TREASURY_PRICES', 'ADM_API', E'Preço oficial (PU de venda), taxas e oferta dos títulos do Tesouro Direto (Tesouro Transparente); avisa a carteira para revalorizar o Tesouro.'),
('B3_DAILY_QUOTES', 'ADM_API', E'Cotações de todo o catálogo (ações, FIIs, ETFs e BDRs) pelo COTAHIST diário da B3, um arquivo por pregão. Sem período: do pregão seguinte ao último gravado até hoje.'),
('MARKET_INDICES', 'ADM_API', E'Índices de mercado (CDI, Selic, IPCA, IGP-M, INPC, TR, meta Selic, PTAX, Ibovespa, IFIX) do Banco Central e da B3. Sem período: rodada incremental com janela de revisão.'),
('CATALOG_AUDIT', 'ADM_API', E'Conferência do catálogo (tickers novos, sumidos e renomeados na B3) e dos índices atrasados. Não altera o catálogo: gera mensagens de administrador.'),
('ASSET_LOGOS', 'ADM_API', E'Logos dos ativos (BRAPI e CoinGecko) em carga/data/logos.'),
('FIXED_INCOME_ISSUERS', 'ADM_API', E'Emissoras e distribuidoras de renda fixa (Banco Central e CVM) e, em seguida, os logos delas por raiz de CNPJ.'),
('TASK_HISTORY_CLEANUP', 'ADM_API', E'Limpeza do histórico das tarefas: apaga as linhas de log e as rodadas mais antigas que os prazos dos atributos.'),
-- adm-api, sob demanda
('BOOTSTRAP', 'ADM_API', E'Carga inicial num banco vazio, em ordem: emissores, COTAHIST, proventos, datas de pagamento, balanços, informe de FII, índices e Tesouro. Retomável.'),
('ISSUERS_IMPORT', 'ADM_API', E'Emissores: companhias (cadastro e FCA da CVM), FIIs (informe mensal) e a classificação setorial da B3.'),
('ISSUERS_LINK', 'ADM_API', E'Ligação ativo → emissor: ações pelo FCA, pela raiz do ticker e pela B3; FIIs pelo ISIN. Ligação manual nunca é sobrescrita.'),
('B3_YEARLY_QUOTES', 'ADM_API', E'Série histórica anual da B3 (COTAHIST): preço diário de ações e FIIs e o ISIN de cada ticker, ano a ano.'),
('CVM_QUARTERS', 'ADM_API', E'Recalcula a série trimestral das companhias a partir dos balanços já gravados.'),
('CVM_PAYMENT_DATES', 'ADM_API', E'Datas de pagamento dos proventos pelos relatórios de proventos da CVM (2022 em diante), só para eventos sem data.'),
('PAYMENT_DATE_SURVEY', 'ADM_API', E'Datas de pagamento do levantamento (carga/data/levantamento), só para eventos sem data.'),
('FII_CNPJ_CURATION', 'ADM_API', E'Curadoria: liga FIIs ao CNPJ do fundo (ligação manual) e cria o emissor que faltar, quando o ISIN não resolve.'),
('ASSET_ISSUER_LINK', 'ADM_API', E'Curadoria: liga um ativo a um emissor pelo CNPJ, com a classe da ação; sem CNPJ, desfaz a ligação.'),
('UNIT_COMPOSITION', 'ADM_API', E'Curadoria: grava a composição de uma unit (ex.: 1 ON + 2 PNA) e marca a ligação como manual.'),
-- app-api
('DAILY_OPENING', 'APP_API', E'Abertura do dia da carteira: títulos vencidos (aviso), proventos e eventos de hoje e as conferências (bonificação sem custo, avisos de catálogo, alerta de preço, padrão do Preço Teto).'),
('INTEGRATIONS_DAILY_SYNC', 'APP_API', E'Enfileira a sincronização diária das conexões com corretoras e com a B3; o worker da fila faz a busca.'),
('POSITIONS_REFRESH', 'APP_API', E'Rede de segurança da carteira: refaz proventos, valorização e a curva da renda fixa de todas as posições.');

INSERT INTO task_type_attributes (task_type_id, att_name, att_value, value_type, required, description)
SELECT t.id, v.att_name, v.att_value, v.value_type, v.required, v.description
FROM (VALUES
    ('DIVIDENDS_SYNC', 'FNET_REREAD_ALL', 'false', 'BOOLEAN', TRUE, E'Relê todos os avisos do FNET em disco, inclusive os que já geraram evento (~40 min).'),
    ('DIVIDENDS_SYNC', 'WITH_CVM_PAYMENT_DATES', 'true', 'BOOLEAN', TRUE, E'Ao fim, completa as datas de pagamento pela CVM (ano corrente e anterior).'),
    ('FNET_STATUS_SYNC', 'ONLY_MISSING', 'false', 'BOOLEAN', TRUE, E'Só os fundos ainda sem arquivo de situação (retoma as falhas).'),
    ('FNET_STATUS_SYNC', 'WITH_DIVIDENDS_REREAD', 'true', 'BOOLEAN', TRUE, E'Depois da situação, roda os proventos relendo todos os avisos.'),
    ('CVM_STATEMENTS_IMPORT', 'WITH_STATEMENTS', 'true', 'BOOLEAN', TRUE, E'Balanços DFP/ITR e a série trimestral.'),
    ('CVM_STATEMENTS_IMPORT', 'STATEMENTS_FROM_YEAR', NULL, 'YEAR', FALSE, E'Balanços: primeiro ano (2010 em diante). Vazio = ano corrente e anterior, só o que mudou.'),
    ('CVM_STATEMENTS_IMPORT', 'STATEMENTS_TO_YEAR', NULL, 'YEAR', FALSE, E'Balanços: último ano. Vazio = ano corrente.'),
    ('CVM_STATEMENTS_IMPORT', 'WITH_FII_MONTHLY', 'true', 'BOOLEAN', TRUE, E'Também o informe mensal de FII.'),
    ('CVM_STATEMENTS_IMPORT', 'FII_FROM_YEAR', NULL, 'YEAR', FALSE, E'Informe de FII: primeiro ano (2016 em diante). Vazio = ano corrente e anterior, só o que mudou.'),
    ('CVM_STATEMENTS_IMPORT', 'FII_TO_YEAR', NULL, 'YEAR', FALSE, E'Informe de FII: último ano. Vazio = ano corrente.'),
    ('CVM_STATEMENTS_IMPORT', 'FORCE', 'false', 'BOOLEAN', TRUE, E'Reprocessa a partir de carga/data/cvm o que já está gravado na mesma versão (com período).'),
    ('TREASURY_PRICES', 'FROM_YEAR', NULL, 'YEAR', FALSE, E'Primeiro ano (2002 em diante). Vazio = rodada diária (10 dias antes do último preço gravado).'),
    ('B3_DAILY_QUOTES', 'FROM_DATE', NULL, 'DATE', FALSE, E'Primeiro pregão. Vazio = do pregão seguinte ao último gravado até hoje.'),
    ('B3_DAILY_QUOTES', 'TO_DATE', NULL, 'DATE', FALSE, E'Último pregão (até 366 dias depois do primeiro). Vazio = hoje.'),
    ('MARKET_INDICES', 'INDEX_CODES', NULL, 'LIST', FALSE, E'Códigos separados por vírgula (ex.: CDI,IPCA). Vazio = todos.'),
    ('MARKET_INDICES', 'FROM_YEAR', NULL, 'YEAR', FALSE, E'Primeiro ano. Vazio, com o último ano também vazio e sem códigos = rodada incremental (com janela de revisão).'),
    ('MARKET_INDICES', 'TO_YEAR', NULL, 'YEAR', FALSE, E'Último ano. Informado com o primeiro vazio = desde o início de cada índice.'),
    ('ASSET_LOGOS', 'ONLY_MISSING', 'true', 'BOOLEAN', TRUE, E'Só os ativos sem logo em disco.'),
    ('FIXED_INCOME_ISSUERS', 'WITH_ISSUERS', 'true', 'BOOLEAN', TRUE, E'O cadastro das emissoras e distribuidoras (Banco Central e CVM).'),
    ('FIXED_INCOME_ISSUERS', 'WITH_LOGOS', 'true', 'BOOLEAN', TRUE, E'Depois do cadastro, os logos por raiz de CNPJ.'),
    ('FIXED_INCOME_ISSUERS', 'LOGOS_REFRESH_ALL', 'false', 'BOOLEAN', TRUE, E'Baixa de novo os logos de quem já tem arquivo.'),
    ('TASK_HISTORY_CLEANUP', 'LOG_RETENTION_DAYS', '30', 'INTEGER', TRUE, E'Dias que as linhas de log de cada rodada ficam guardadas.'),
    ('TASK_HISTORY_CLEANUP', 'EXECUTION_RETENTION_DAYS', '365', 'INTEGER', TRUE, E'Dias que as rodadas ficam no histórico.'),
    ('B3_YEARLY_QUOTES', 'FROM_YEAR', '2010', 'YEAR', TRUE, E'Primeiro ano (1986 em diante).'),
    ('B3_YEARLY_QUOTES', 'TO_YEAR', NULL, 'YEAR', FALSE, E'Último ano. Vazio = ano corrente.'),
    ('CVM_PAYMENT_DATES', 'FROM_YEAR', '2022', 'YEAR', TRUE, E'Primeiro ano (2022 em diante).'),
    ('CVM_PAYMENT_DATES', 'TO_YEAR', NULL, 'YEAR', FALSE, E'Último ano. Vazio = ano corrente.'),
    ('FII_CNPJ_CURATION', 'LINES', NULL, 'TEXT', TRUE, E'Uma linha por fundo: TICKER;CNPJ (ex.: XPML11;28757546000100).'),
    ('ASSET_ISSUER_LINK', 'TICKER', NULL, 'TEXT', TRUE, E'Ticker do ativo.'),
    ('ASSET_ISSUER_LINK', 'ISSUER_CNPJ', NULL, 'TEXT', FALSE, E'CNPJ do emissor (fora dos emissores, procurado no cadastro da CVM). Vazio = desfaz a ligação.'),
    ('ASSET_ISSUER_LINK', 'SHARE_CLASS', NULL, 'TEXT', FALSE, E'Classe da ação (ON, PN, PNA, PNB, UNIT...). Vazio para o que não é ação.'),
    ('UNIT_COMPOSITION', 'TICKER', NULL, 'TEXT', TRUE, E'Ticker da unit.'),
    ('UNIT_COMPOSITION', 'COMPOSITION', NULL, 'TEXT', TRUE, E'Classe=quantidade separados por ponto e vírgula (ex.: ON=1;PNA=2).')
) AS v(type, att_name, att_value, value_type, required, description)
JOIN task_types t ON t.type = v.type;

INSERT INTO tasks (name, cron, task_type_id)
SELECT v.name, v.cron, t.id
FROM (VALUES
    ('B3_CLOSING', '0 0 19 * * MON-FRI'),
    ('CRYPTO_CLOSING', '0 0 19 * * *'),
    ('CRYPTO_PRICE_HISTORY', '0 30 19 * * *'),
    ('DIVIDENDS_SYNC', '0 0 20 * * *'),
    ('FNET_STATUS_SYNC', '0 0 3 * * SUN'),
    ('CVM_STATEMENTS_IMPORT', '0 30 7 * * *'),
    ('TREASURY_PRICES', '0 0 8 * * *'),
    ('B3_DAILY_QUOTES', '0 30 8,21 * * *'),
    ('MARKET_INDICES', '0 0 10 * * *'),
    ('CATALOG_AUDIT', '0 0 7 * * MON'),
    ('ASSET_LOGOS', '0 0 6 1 * *'),
    ('FIXED_INCOME_ISSUERS', '0 30 5 1 * *'),
    ('TASK_HISTORY_CLEANUP', '0 0 4 * * *'),
    ('BOOTSTRAP', NULL),
    ('ISSUERS_IMPORT', NULL),
    ('ISSUERS_LINK', NULL),
    ('B3_YEARLY_QUOTES', NULL),
    ('CVM_QUARTERS', NULL),
    ('CVM_PAYMENT_DATES', NULL),
    ('PAYMENT_DATE_SURVEY', NULL),
    ('FII_CNPJ_CURATION', NULL),
    ('ASSET_ISSUER_LINK', NULL),
    ('UNIT_COMPOSITION', NULL),
    ('DAILY_OPENING', '0 0 0 * * *'),
    ('INTEGRATIONS_DAILY_SYNC', '0 45 8 * * *'),
    ('POSITIONS_REFRESH', '0 0 21 * * *')
) AS v(name, cron)
JOIN task_types t ON t.type = v.name;

-- espelho (O7): cada tarefa recebe os atributos do seu tipo, com o valor padrão
INSERT INTO task_attributes (task_id, task_type_attribute_id, att_value)
SELECT k.id, a.id, a.att_value
FROM tasks k
JOIN task_type_attributes a ON a.task_type_id = k.task_type_id;
