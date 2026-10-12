# PrimeVest — Rotinas agendadas e automáticas

> Gerado em 02/10/2026 a partir do código (`@Scheduled`, `ApplicationReadyEvent` e ouvintes do Redis) e dos
> `application.yml` dos módulos · revisado em 03/10/2026 (Markdown, caminhos `carga/`) · 05/10/2026 ([A9] índices de
> mercado, `docs/planos/PLANO_INDICES_MERCADO.md`; [A10] preço do Tesouro e a curva da renda fixa,
> `docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md`) · 10/10/2026 ([A11] cotações do catálogo pelo COTAHIST diário,
> `docs/planos/PLANO_PRECO_TETO.md`).

- **Fuso** de todas as rotinas: `America/Sao_Paulo`.
- **Cron do Spring:** `segundo minuto hora dia-do-mês mês dia-da-semana`. Os horários ficam em propriedade
  (`application.yml`): dá para mudar sem código.
- **Chave geral:** `primevest.scheduling.enabled` (padrão `true`). No perfil de teste é `false`: nenhuma rotina
  agendada roda nos testes.

| Módulo | Papel |
|---|---|
| **adm-api** (8081) | rotinas de **catálogo**: preços, proventos, balanços, logos, índices de mercado. Grava só catálogo e avisa a carteira pelo Redis |
| **app-api** (8080) | rotinas da **carteira**: recálculo das posições e sincronização das corretoras. Única que grava dado de usuário |

## Linha do tempo de um dia

| Quando | Módulo | Rotina |
|---|---|---|
| a cada 5 s | app | [B5] Worker da fila de integrações |
| 06:00, dia 1 | adm | [A7] Logos (mensal) |
| 07:00, segunda | adm | [A6] Conferência do catálogo (semanal) |
| 07:30 | adm | [A5] Balanços CVM + informe mensal de FII |
| 08:00 | adm | [A10] Preço oficial do Tesouro Direto |
| 08:30 | adm | [A11] Cotações do catálogo — COTAHIST diário (o pregão que ainda não tinha saído) |
| 08:45 | app | [B4] Sincronização diária das corretoras (enfileira) |
| 10:00 | adm | [A9] Índices de mercado (CDI, Selic, IPCA, IGP-M, INPC, TR, meta Selic, PTAX, Ibovespa, IFIX) |
| 19:00, seg–sex | adm | [A1] Fechamento B3 (BRAPI) |
| 19:00 | adm | [A2] Fechamento cripto (CoinGecko) |
| 20:00 | adm | [A4] Proventos e eventos societários + datas de pagamento CVM |
| 21:00 | app | [B1] Rede de segurança: recálculo de todas as posições |
| 21:30 | adm | [A11] Cotações do catálogo — COTAHIST diário |
| ao subir | adm / app | [A3] recuperação do fechamento · [A9] recuperação dos índices · [A10] recuperação do Tesouro · [A11] recuperação das cotações · [B2] verificação da carteira · [B6] recuperação da fila |
| por mensagem | adm / app | [A8] "preciso do histórico" · [B3] "catálogo mudou" |

A ordem 19:00 → 20:00 → 21:00 é proposital: o recálculo da carteira roda depois de o catálogo ter recebido os preços e
os proventos do dia.

---

## Parte 1 — adm-api (catálogo)

### [A1] Fechamento B3

| | |
|---|---|
| **Quando** | 19:00, segunda a sexta |
| **Classe** | `ClosingPriceScheduler.b3Closing` → `ClosingPriceService.runB3Closing` |
| **Config** | `primevest.closing.b3-cron = "0 0 19 * * MON-FRI"` |
| **Fonte** | BRAPI (plano gratuito: só preço de fechamento) |
| **Grava** | `asset_price_history` (OHLCV do pregão) e `assets.current_price` |
| **Ao fim** | publica "catálogo mudou" (origem `FECHAMENTO`) no Redis → a app-api revaloriza as posições desses ativos |

**Função.** Para cada ativo **em carteira** (1 chamada por ativo, em sequência), pede o histórico diário dos últimos 5
pregões (`range=5d`), grava cada pregão e põe o último fechamento como preço atual.

- Ativo sem histórico: carga inicial com 3 meses (`range=3mo`).
- Faltando mais pregões do que 5d cobre: `1mo` ou `3mo`.
- 404: pula e registra. 401/403: a rodada para.
- Ticker renomeado: só registra antigo → novo (não troca sozinho).
- Dia gravado pela série da B3 (COTAHIST) não é sobrescrito.

**Propósito.** Manter preço e valorização da carteira atualizados uma vez ao dia, dentro do limite do plano gratuito
da BRAPI.

### [A2] Fechamento cripto

| | |
|---|---|
| **Quando** | 19:00, todos os dias (cripto negocia no fim de semana) |
| **Classe** | `ClosingPriceScheduler.cryptoClosing` → `ClosingPriceService.runCryptoClosing` |
| **Config** | `primevest.closing.crypto-cron = "0 0 19 * * *"` |
| **Fonte** | CoinGecko (uma chamada para todas as moedas) |
| **Grava** | preço em reais das criptos em carteira como fechamento do dia (`asset_price_history`, source `COINGECKO`) e `assets.current_price` |
| **Ao fim** | publica "catálogo mudou" (`FECHAMENTO`) |

**Propósito.** Valorização diária das posições de cripto.

### [A3] Recuperação do fechamento na subida

| | |
|---|---|
| **Quando** | ao subir a adm-api (`ApplicationReadyEvent`), em thread virtual |
| **Classe** | `ClosingPriceScheduler.recoverOnStartup` |

**Função.** Se faltar o último fechamento de algum ativo em carteira (B3 ou cripto), roda a rodada de fechamento na
hora, em segundo plano.

**Propósito.** A API roda na máquina do usuário; se ela estava desligada às 19:00, o dia não fica sem preço.

### [A4] Proventos e eventos societários (+ datas de pagamento da CVM)

| | |
|---|---|
| **Quando** | 20:00, todos os dias |
| **Classe** | `ProventosScheduler.syncProventos` → `ProventosCatalogService.syncAll` → `CvmPaymentDateService.run(ano anterior, ano corrente)` |
| **Config** | `primevest.proventos.cron = "0 0 20 * * *"`; `primevest.proventos.enabled` (liga/desliga a rotina e o sob demanda); `primevest.proventos.scope = ALL` (todo ativo ligado a emissor + o que já esteve em carteira) ou `HELD` (só o que já esteve em carteira); `primevest.proventos.pause-millis = 1000` |
| **Fontes** | B3 (proventos ~12 meses, desdobramentos, grupamentos, bonificações), FNET (avisos de FII — fonte principal do FII), CVM "Relatórios Proventos" (data de pagamento de ação, 2022+) |
| **Grava** | `asset_cash_events`, `asset_stock_events`; arquivos em `carga/data/` (`b3/proventos-12m`, `fnet`, `cvm/ipe`, `cvm/proventos`) |
| **Ao fim** | publica "catálogo mudou" (origem `PROVENTOS`) → a app-api refaz proventos e valorização das posições envolvidas |

**Função.**

1. Por **emissor** (PETR3 e PETR4 são uma chamada só): busca na B3 os proventos e eventos societários e grava no
   catálogo; o acumulado de 12 meses fica em disco para a data de pagamento não se perder.
2. FII: aplica os avisos do FNET em disco antes da B3; se a B3 mostrar provento mais novo que o último aviso, consulta
   o FNET pelo CNPJ (no máximo 1×/dia por fundo).
3. Histórico longo: ação pelo histórico da B3 (guardado por emissor em disco), FII pelo FNET.
4. Completa a data de pagamento que só o relatório da CVM tem (ano corrente e anterior; o índice IPE só é baixado se
   mudou).

Falha numa fonte: log ERROR e segue para o próximo emissor; nada gravado é apagado. A rodada completa não roda duas
vezes ao mesmo tempo.

**Propósito.** Catálogo de proventos completo (inclusive data de pagamento) para a carteira calcular o que o usuário
recebeu e vai receber.

### [A5] Balanços da CVM + informe mensal de FII

| | |
|---|---|
| **Quando** | 07:30, todos os dias (a CVM atualiza os arquivos de madrugada) |
| **Classe** | `CvmStatementsScheduler.importRecent` → `FinancialStatementImportService.importRecent` → `FiiMonthlyImportService.importRecent` |
| **Config** | `primevest.cvm-statements.cron = "0 30 7 * * *"`; `primevest.cvm-statements.enabled` |
| **Fonte** | Portal de dados abertos da CVM (DFP, ITR, FRE; informe mensal de FII) |
| **Grava** | `cvm_documents`, `cvm_statement_values`, `share_counts`, `company_quarters` (recalculada ao fim), `fii_monthly` |

**Função.** Ano corrente e anterior. Só baixa o que mudou (`If-Modified-Since`) e só grava documento/mês novo ou
reapresentado (versão maior). Uma parte falhar não impede a outra.

**Propósito.** Manter balanços, série trimestral e informe de FII em dia para os indicadores (P/L, P/VP, LPA, VPA,
ROE, payout, DY...).

### [A6] Conferência do catálogo

| | |
|---|---|
| **Quando** | 07:00, toda segunda-feira |
| **Classe** | `CatalogAuditScheduler.weeklyAudit` → `CatalogAuditService.run` e `MarketIndexImportService.audit` |
| **Config** | `primevest.catalog-audit.cron = "0 0 7 * * MON"`; `primevest.catalog-audit.ignored` (tickers fora de propósito) |
| **Fonte** | lista oficial e renomes da BRAPI |
| **Grava** | **nada**: cada diferença vira linha WARN no log da adm-api |

**Função.** Aponta (1) ticker novo na B3 que não está no catálogo, (2) ticker sumido (fora da lista **e** cotação
404), (3) ticker renomeado ainda ativo no catálogo, (4) índice de mercado atrasado — diário sem valor há mais de 7
dias, mensal sem o mês seguinte 45 dias depois do fim do mês de referência, ou sem série nenhuma (05/10/2026).

**Propósito.** Avisar o administrador de mudanças na bolsa sem alterar o catálogo sozinho. (A central de avisos é a
`docs/PENDENCIAS.md`, seção 13.)

### [A7] Logos

| | |
|---|---|
| **Quando** | 06:00, dia 1 de cada mês |
| **Classe** | `LogoScheduler.monthly` → `AssetLogoService.sync(true, ...)` |
| **Config** | `primevest.logos.cron = "0 0 6 1 * *"`; `primevest.logos.dir` |
| **Fontes** | BRAPI (ação, FII, ETF, BDR) e CoinGecko (cripto) |
| **Grava** | arquivos `carga/data/logos/{TICKER}.{ext}` (o arquivo é o estado; nada no banco) |

**Função.** Baixa o logo só dos ativos que ainda não têm arquivo; confere se veio imagem; busca sem resultado não apaga
o arquivo existente.

**Propósito.** A tela não aponta para sites de terceiros (links quebrados ou que mudam).

### [A8] Ouvinte "preciso do histórico" (por evento, não por horário)

| | |
|---|---|
| **Quando** | a cada mensagem no canal Redis `primevest:proventos:sob-demanda`, publicada pela app-api depois de cada transação |
| **Classe** | `AdmMessagingConfig.onDemandListener` → `ProventosCatalogService.requestOnDemand(assetId)` → `AssetLogoService.fetchIfMissing(assetId)` |

**Função.** Fila única: sincroniza os proventos/eventos do ativo da transação e baixa o logo se ele não tiver; responde
com "catálogo mudou".

**Propósito.** Ativo que entra numa carteira já ganha histórico de proventos e logo, sem esperar a rotina das 20:00 nem
a do dia 1.

### [A9] Índices de mercado (05/10/2026)

| | |
|---|---|
| **Quando** | 10:00, todos os dias, e na subida da adm-api quando algum índice está atrasado ou vazio |
| **Classe** | `MarketIndexScheduler.daily` / `recoverOnStartup` → `MarketIndexImportService.importRecent` |
| **Config** | `primevest.indices.cron = "0 0 10 * * *"`; `primevest.indices.enabled`; `indices.*` (timeout 20 s, 4 tentativas) |
| **Fontes** | API SGS do Banco Central (CDI 12, Selic 11, IPCA 433, IGP-M 189, INPC 188, TR 226, meta Selic 432, PTAX 1) e site da B3 (Ibovespa, IFIX) |
| **Grava** | `market_index_values` e os arquivos `carga/data/bcb/sgs/{código}/{ano}.json`, `carga/data/b3/indices/{índice}/{ano}.json` |

**Função.** Por índice, relê de (última data gravada − 30 dias nos diários, − 3 meses nos mensais) até hoje, ano a ano
(o ano corrente e o anterior sempre vão à fonte). Valor novo entra, valor corrigido pela fonte é sobrescrito com log
`antigo -> novo`, data futura não entra. Índice sem série faz a carga histórica desde 2010. Um índice que falha não
impede os outros. Sem mensagem para a app-api por enquanto (D8 do plano).

**Ao fim (desde 05/10/2026).** Se algo entrou ou mudou, publica "catálogo mudou" (origem `INDICES`) → a app-api
refaz a curva da renda fixa privada ([B3]).

**Propósito.** Série dos indexadores da renda fixa e dos benchmarks em dia — base da valorização da renda fixa
(`docs/PENDENCIAS.md` 2.1). CDI e Selic do dia anterior saem no SGS de manhã; IPCA e INPC por volta do dia 10.

---

### [A10] Preço oficial do Tesouro Direto (05/10/2026)

| | |
|---|---|
| **Quando** | 08:00, todos os dias (o arquivo sai ~07:20 com o D-1), e na subida da adm-api quando o último PU tem mais de 4 dias |
| **Classe** | `TreasuryPriceScheduler.daily` / `recoverOnStartup` → `TreasuryPriceService.importRecent` |
| **Config** | `primevest.tesouro.cron = "0 0 8 * * *"`; `primevest.tesouro.enabled`; `tesouro.*` |
| **Fonte** | Tesouro Transparente, `PrecoTaxaTesouroDireto.csv` (todo o histórico, ~14 MB; só baixado se mudou) |
| **Grava** | `asset_price_history` (`source = TESOURO`, PU de venda), `assets.current_price` e `carga/data/tesouro/` |
| **Ao fim** | publica "catálogo mudou" (`FECHAMENTO`) com os títulos cujo preço mudou → a app-api revaloriza o Tesouro |

**Função.** Relê os dias a partir de 10 antes do último PU gravado, casa cada linha com o título do catálogo por tipo
\+ vencimento e grava o PU de venda (sem ele, o PU base). Título do arquivo sem título no catálogo vai para o log.

**Propósito.** Valor de mercado do Tesouro na carteira (`docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md`).

### [A11] Cotações do catálogo — COTAHIST diário (10/10/2026)

| | |
|---|---|
| **Quando** | 21:30 e 08:30, todos os dias, e na subida da adm-api quando falta o pregão do último dia útil |
| **Classe** | `B3DailyQuotesScheduler.daily` / `recoverOnStartup` → `HistoricalQuotesImportService.importRecent` |
| **Config** | `primevest.cotahist-diario.cron = "0 30 8,21 * * *"`; `primevest.cotahist-diario.enabled` |
| **Fonte** | B3, `COTAHIST_DddMMaaaa.ZIP` (um por pregão, ~0,5 MB, todas as ações e FIIs) |
| **Grava** | `asset_price_history` (`source = B3_COTAHIST`), `assets.isin`; zips em `carga/data/b3/cotahist/diario` |

**Função.** Do pregão seguinte ao último gravado pela B3 até hoje, um arquivo por dia útil (fim de semana não vai à B3;
feriado e arquivo ainda não publicado respondem 404 e ficam para a rodada seguinte). Mesmas regras do zip anual
(ticker, ISIN, fracionário). Lacuna maior que 31 dias usa o zip do ano.

**Propósito.** Preço de **todo o catálogo** atualizado sem cota da BRAPI (o fechamento [A1] só cobre a carteira): base
do preço do Preço Teto e da tela de Emissoras (`docs/planos/PLANO_PRECO_TETO.md`, 4.5 e D6). Primeira rodada
(10/10/2026): 26/09 a 09/10, 10 pregões, 9.909 cotações.

---

## Parte 2 — app-api (carteira)

### [B1] Rede de segurança da carteira

| | |
|---|---|
| **Quando** | 21:00, todos os dias |
| **Classe** | `PositionRefreshScheduler.refreshAll` → `PositionRefreshService.refreshAll` |
| **Config** | `primevest.positions.refresh-cron = "0 0 21 * * *"` |
| **Grava** | `dividends` (proventos das posições), `portfolio_assets.appreciation` |

**Função.** Recalcula proventos e valorização de **todas** as posições — desde 05/10/2026 também a curva da renda fixa
privada (a subida da app-api, [B2], faz o mesmo).

**Propósito.** Cobrir mensagem "catálogo mudou" que se perdeu (Redis ou app-api fora do ar): o Redis pub/sub não
garante entrega.

### [B2] Verificação na subida da carteira

| | |
|---|---|
| **Quando** | ao subir a app-api (`ApplicationReadyEvent`) |
| **Classe** | `PositionRefreshService.onStartup` → `DividendTaxVerifier.onStartup` → `AppreciationUpdater.refreshAll` |

**Função.** Preenche o IR retido dos proventos que estiverem sem ele e regrava a valorização de todas as posições com
cotação.

**Propósito.** Carteira consistente logo após reinício/deploy (ex.: lei nova de IR por migration).

### [B3] Ouvinte "catálogo mudou" (por evento, não por horário)

| | |
|---|---|
| **Quando** | a cada mensagem no canal Redis `primevest:catalogo:ativos`, publicada pela adm-api (`{origin: PROVENTOS\|FECHAMENTO\|INDICES, assetIds, indexCodes}`) |
| **Classe** | `CatalogMessagingConfig` → `PositionRefreshService` |

**Função.** `PROVENTOS` → refaz proventos e valorização das posições dos ativos citados; `FECHAMENTO` → só a
valorização (inclusive Tesouro, pelo PU oficial); `INDICES` (05/10/2026) → refaz a curva de todos os títulos de renda
fixa privada em aberto (`FixedIncomeValuation`).

**Propósito.** A carteira reage ao catálogo na hora, sem esperar as 21:00.

### [B4] Sincronização diária das corretoras

| | |
|---|---|
| **Quando** | 08:45, todos os dias (até 03/10/2026, 06:30) |
| **Classe** | `IntegrationSyncScheduler.sincronizar` → `IntegrationService.enqueueScheduled` |
| **Config** | `primevest.integrations.sync-cron = "0 45 8 * * *"`; `primevest.integrations.auto-confirm-scheduled = true` |
| **Grava** | `integration_runs` (status `QUEUED`, trigger `SCHEDULED`) |

**Função.** Só **enfileira** uma execução para cada conexão `ACTIVE` ou `PENDING_AUTHORIZATION` (B3: é a checagem que
a leva a `ACTIVE` quando o investidor autorizar) que não tenha execução pendente (`QUEUED`, `RUNNING` ou
`AWAITING_CONFIRMATION`) nem tenha passado do limite de uma consulta por dia da fonte (B3). Quem busca na corretora é o worker [B5]. A execução agendada confirma sozinha
quando não há nada a decidir (nenhuma possível duplicata) e a conexão já teve uma importação confirmada pelo usuário.

**Propósito.** Trazer as operações novas das fontes (Binance e B3) todo dia. O horário é depois das 8h porque a B3 só
publica o dia anterior a partir daí (docs/planos/PLANO_INTEGRACAO_B3.md, A6).

### [B5] Worker da fila de integrações

| | |
|---|---|
| **Quando** | a cada 5 s (`fixedDelay`), primeira execução 10 s após subir |
| **Classe** | `IntegrationWorker.processar` → `IntegrationService.processNext` |
| **Config** | `primevest.integrations.worker-delay-ms = 5000`; `primevest.integrations.stale-minutes = 30` |
| **Grava** | `integration_runs`, `integration_run_items`, `integration_cursors`, `transactions` (na confirmação), `integration_connections` (status) |

**Função.** Pega as execuções `QUEUED` uma de cada vez e busca na corretora; o resultado vira pré-visualização (ou é
confirmado sozinho, ver [B4]). Execução `RUNNING` há mais de 30 min é tida como perdida.

**Propósito.** Uma fila única para todos os usuários: o limite de peso da Binance vira um orçamento só; o pedido do
usuário responde na hora.

### [B6] Recuperação da fila na subida

| | |
|---|---|
| **Quando** | ao subir a app-api (`ApplicationReadyEvent`) |
| **Classe** | `IntegrationWorker.aoSubir` → `IntegrationService.recuperarPerdidas` |

**Função.** Execução que estava `RUNNING` morreu com a instância anterior: vira `FAILED` na hora.

**Propósito.** A conexão não fica presa esperando o prazo de `stale-minutes`.

---

## Fora do agendamento (de propósito)

- **Carga inicial** (`POST /api/v1/admin/bootstrap`): **nunca** dispara sozinha ao subir; é manual (ver
  [ADM_TASKS.md](ADM_TASKS.md)).
- Série histórica COTAHIST, importação de emissores e ligação ativo → emissor: só sob demanda.
- Todas as rotinas da adm-api também têm disparo manual pelos endpoints `/api/v1/admin/*` (ver
  [ADM_TASKS.md](ADM_TASKS.md)).
