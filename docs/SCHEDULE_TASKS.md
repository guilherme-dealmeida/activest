# Activest — Rotinas agendadas e automáticas

> Gerado em 02/10/2026 a partir do código (`@Scheduled`, `ApplicationReadyEvent` e ouvintes do Redis) e dos
> `application.yml` dos módulos · revisado em 03/10/2026 (Markdown, caminhos `carga/`) · 05/10/2026 ([A9] índices de
> mercado, `docs/planos/PLANO_INDICES_MERCADO.md`; [A10] preço do Tesouro e a curva da renda fixa,
> `docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md`) · 10/10/2026 ([A11] cotações do catálogo pelo COTAHIST diário,
> `docs/planos/PLANO_PRECO_TETO.md`) · 21/10/2026 ([A4] situação dos avisos do FNET, recibos e retificações; [A12] situação semanal dos avisos do FNET — `docs/planos/PLANO_CORRECAO_PROVENTOS.md`, PENDENCIAS 33.3) · 13/10/2026 ([B7] abertura do dia, mensagens de administrador nas rotinas da adm-api,
> [A5] e [A11] avisando a carteira — `docs/planos/PLANO_NOTIFICACOES.md`) · 22/10/2026 (**Tarefas**: cada rotina virou
> uma tarefa no banco, com horário editável na tela `/tasks` — `docs/planos/PLANO_TAREFAS.md`; [A13] histórico de
> cripto, [A14] emissoras de RF e [A15] limpeza do histórico das tarefas entraram aqui; [B7] corrigida: só avisa o
> título vencido desde 15/10/2026) · 27/10/2026 ([A17] fundos de investimento da CVM e a série dos fundos no [A8] —
> `docs/planos/PLANO_FUNDOS_INVESTIMENTO.md`) · 29/10/2026 ([A6] passa a inativar com prova; [A18] catálogo vivo dos EUA e
> [A19] deslistados dos EUA — `docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md`).

- **Fuso** de todas as rotinas: `America/Sao_Paulo`.
- **Cron do Spring:** `segundo minuto hora dia-do-mês mês dia-da-semana`. Desde 22/10/2026 os horários ficam **no
  banco** (`tasks.cron`) e se editam na tela de Tarefas (`/tasks`, só administrador); o liga/desliga também
  (`tasks.enabled` por tarefa, `task_types.enabled` por tipo). O agendador (`TaskScheduleRegistry`) relê ao editar e a
  cada 5 min. Os horários abaixo são os do seed (V62). Cada rodada fica em `task_executions`, com resumo e log.
- **Chave geral:** `activest.scheduling.enabled` (padrão `true`). No perfil de teste é `false`: nenhuma rotina
  agendada roda nos testes, nem a recuperação na subida.
- **Subida** (22/10/2026, `docs/planos/PLANO_TAREFAS.md`, D37 e D38): roda só a tarefa marcada "Roda na subida"
  (`tasks.run_on_startup`), ativa e **sem rodada no dia** — reiniciar o serviço não repete —, e só se a rotina disser
  que há o que recuperar. Marcadas no seed: [A1], [A2], [A9], [A10], [A11] e [B7].
- **Vigência e disparo adiantado** (23/10/2026): o agendador mede a espera por um contador da máquina e o cron fala do
  relógio de parede; os dois se afastam (na VM do Docker Desktop, ~4 s por dia de espera) e o disparo chegava segundos
  antes da hora — a abertura do dia de 00:00 podia rodar às 23:59:5x com a data de ontem. Agora a vigência
  (`scheduled_for`) é o horário nominal do cron e a rodada espera o relógio chegar nela: o início é sempre igual ou
  depois da vigência.

| Módulo | Papel |
|---|---|
| **adm-api** (8081) | rotinas de **catálogo**: preços, proventos, balanços, logos, índices de mercado. Grava só catálogo e avisa a carteira pelo Redis |
| **app-api** (8080) | rotinas da **carteira**: recálculo das posições, sincronização das corretoras e abertura do dia. Única que grava dado de usuário |

**Notificações (13/10/2026).** As falhas de rotina, as fontes fora do ar e as diferenças de catálogo da adm-api viram
**mensagens de administrador** na tabela `notifications` (gravadas pela própria adm-api, D1 do
`docs/planos/PLANO_NOTIFICACOES.md`): no máximo uma por rotina (e fonte) por dia; as de catálogo ficam abertas até a
rodada seguinte não achar mais a diferença. As mensagens do usuário são da app-api.

## Linha do tempo de um dia

| Quando | Módulo | Rotina |
|---|---|---|
| 00:00 | app | [B7] Abertura do dia (aviso dos vencidos, proventos e eventos de D0, conferências) |
| 04:00 | adm | [A15] Limpeza do histórico das tarefas |
| 05:30, dia 1 | adm | [A14] Emissoras e distribuidoras de RF + logos (mensal) |
| 03:00, domingo | adm | [A12] Situação dos avisos do FNET + proventos relendo todos os avisos (semanal) |
| a cada 5 s | app | [B5] Worker da fila de integrações |
| 06:00, dia 1 | adm | [A7] Logos (mensal) |
| 06:30, segunda | adm | [A18] Catálogo vivo dos EUA (semanal; `WRITE` decide se grava) |
| 07:00, segunda | adm | [A6] Conferência do catálogo (semanal) |
| 07:30 | adm | [A5] Balanços CVM + informe mensal de FII |
| 08:00 | adm | [A10] Preço oficial do Tesouro Direto |
| 08:30 | adm | [A11] Cotações do catálogo — COTAHIST diário (o pregão que ainda não tinha saído) |
| 08:45 | app | [B4] Sincronização diária das corretoras (enfileira) |
| 10:00 | adm | [A9] Índices de mercado (CDI, Selic, IPCA, IGP-M, INPC, TR, meta Selic, PTAX, Ibovespa, IFIX) |
| 13:30 | adm | [A16] Câmbio das moedas (PTAX do BCB) — no máximo uma rodada automática por dia |
| 19:00, seg–sex | adm | [A1] Fechamento B3 (BRAPI) |
| 19:00 | adm | [A2] Fechamento cripto (CoinGecko) |
| 19:30 | adm | [A13] Histórico de preço de cripto |
| 20:00 | adm | [A4] Proventos e eventos societários + datas de pagamento CVM |
| 21:00 | app | [B1] Rede de segurança: recálculo de todas as posições |
| 21:30 | app | [B8] Metas: fotografia diária, eventos e avisos |
| a cada 15 s | app | [B8] Reavaliação das metas dos usuários com transação gravada ou "catálogo mudou" |
| 21:30 | adm | [A11] Cotações do catálogo — COTAHIST diário |
| ao subir | adm / app | [A3] recuperação do fechamento · [A9] recuperação dos índices · [A10] recuperação do Tesouro · [A11] recuperação das cotações · [B2] verificação da carteira · [B6] recuperação da fila · [B7] abertura do dia |
| por mensagem | adm / app | [A8] "preciso do histórico" · [B3] "catálogo mudou" |

A ordem 19:00 → 20:00 → 21:00 é proposital: o recálculo da carteira roda depois de o catálogo ter recebido os preços e
os proventos do dia.

---

## Parte 1 — adm-api (catálogo)

### [A1] Fechamento B3

| | |
|---|---|
| **Quando** | 19:00, segunda a sexta |
| **Classe** | tarefa `B3_CLOSING` → `B3ClosingTask` → `ClosingPriceService.runB3Closing` |
| **Tarefa** | `B3_CLOSING` (tipo `B3_CLOSING`) — horário e liga/desliga no banco, editados na tela de Tarefas; padrão do seed: `0 0 19 * * MON-FRI` |
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
| **Classe** | tarefa `CRYPTO_CLOSING` → `CryptoClosingTask` → `ClosingPriceService.runCryptoClosing` |
| **Tarefa** | `CRYPTO_CLOSING` (tipo `CRYPTO_CLOSING`) — horário e liga/desliga no banco, editados na tela de Tarefas; padrão do seed: `0 0 19 * * *` |
| **Fonte** | CoinGecko (uma chamada para todas as moedas) |
| **Grava** | preço em reais das criptos em carteira como fechamento do dia (`asset_price_history`, source `COINGECKO`) e `assets.current_price` |
| **Ao fim** | publica "catálogo mudou" (`FECHAMENTO`) |

**Propósito.** Valorização diária das posições de cripto.

### [A3] Recuperação do fechamento na subida

| | |
|---|---|
| **Quando** | ao subir a adm-api (`ApplicationReadyEvent`), em thread virtual |
| **Classe** | `B3ClosingTask.needsRecovery` / `CryptoClosingTask.needsRecovery` (`TaskStartup`, rodada `STARTUP`) |

**Função.** Se faltar o último fechamento de algum ativo em carteira (B3 ou cripto), roda a rodada de fechamento na
hora, em segundo plano.

**Propósito.** A API roda na máquina do usuário; se ela estava desligada às 19:00, o dia não fica sem preço.

### [A4] Proventos e eventos societários (+ datas de pagamento da CVM)

| | |
|---|---|
| **Quando** | 20:00, todos os dias |
| **Classe** | tarefa `DIVIDENDS_SYNC` → `DividendsSyncTask` → `DividendCatalogService.syncAll` → `CvmPaymentDateService.run(ano anterior, ano corrente)` (atributo `WITH_CVM_PAYMENT_DATES`) |
| **Config** | `DIVIDENDS_SYNC` (atributos `FNET_REREAD_ALL`, `WITH_CVM_PAYMENT_DATES`) — horário e liga/desliga no banco, editados na tela de Tarefas; padrão do seed: `0 0 20 * * *`; o tipo desligado desliga também o sob demanda [A8]; `activest.dividends.scope = ALL` (todo ativo ligado a emissor + o que já esteve em carteira) ou `HELD` (só o que já esteve em carteira); `activest.dividends.pause-millis = 1000` |
| **Fontes** | B3 (proventos ~12 meses, desdobramentos, grupamentos, bonificações), FNET (avisos de FII — fonte principal do FII), CVM "Relatórios Proventos" (data de pagamento de ação, 2022+) |
| **Grava** | `asset_cash_events`, `asset_stock_events`; arquivos em `carga/data/` (`b3/proventos-12m`, `fnet`, `fnet/status` — situação de cada aviso no FNET, 21/10/2026 —, `cvm/ipe`, `cvm/proventos`) |
| **Ao fim** | publica "catálogo mudou" (origem `PROVENTOS`) → a app-api refaz proventos e valorização das posições envolvidas |

**Função.**

1. Por **emissor** (PETR3 e PETR4 são uma chamada só): busca na B3 os proventos e eventos societários e grava no
   catálogo; o acumulado de 12 meses fica em disco para a data de pagamento não se perder.
2. FII: aplica os avisos do FNET em disco antes da B3; se a B3 mostrar provento mais novo que o último aviso, consulta
   o FNET pelo CNPJ (no máximo 1×/dia por fundo) e grava a situação de cada aviso (`fnet/status`). Aviso inativo ou
   cancelado não vira evento, e o que ele já gerou passa para o aviso ativo; recibo, direito e outra classe ficam fora
   (`docs/planos/PLANO_CORRECAO_PROVENTOS.md`).
3. Histórico longo: ação pelo histórico da B3 (guardado por emissor em disco; pedido com o nome de pregão sem barra,
   e de novo se o arquivo estiver vazio e houver provento nos 12 meses), FII pelo FNET.
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
| **Classe** | tarefa `CVM_STATEMENTS_IMPORT` → `CvmStatementsImportTask` → `FinancialStatementImportService.importRecent` → `FiiMonthlyImportService.importRecent` |
| **Tarefa** | `CVM_STATEMENTS_IMPORT` (atributos por parte: `WITH_STATEMENTS`, `STATEMENTS_FROM_YEAR`/`TO_YEAR`, `WITH_FII_MONTHLY`, `FII_FROM_YEAR`/`TO_YEAR`, `FORCE`) — horário e liga/desliga no banco, editados na tela de Tarefas; padrão do seed: `0 30 7 * * *` |
| **Fonte** | Portal de dados abertos da CVM (DFP, ITR, FRE; informe mensal de FII) |
| **Grava** | `cvm_documents`, `cvm_statement_values`, `share_counts`, `company_quarters` (recalculada ao fim), `fii_monthly` |

**Função.** Ano corrente e anterior. Só baixa o que mudou (`If-Modified-Since`) e só grava documento/mês novo ou
reapresentado (versão maior). Uma parte falhar não impede a outra.

**Ao fim (desde 13/10/2026).** Com documento novo ou reapresentado, publica "catálogo mudou" (origem `BALANCOS`, lista
vazia = todos) → a app-api confere o "padrão mudou" dos cálculos de Preço Teto ([B3]).

**Propósito.** Manter balanços, série trimestral e informe de FII em dia para os indicadores (P/L, P/VP, LPA, VPA,
ROE, payout, DY...).

### [A6] Conferência do catálogo

| | |
|---|---|
| **Quando** | 07:00, toda segunda-feira |
| **Classe** | tarefa `CATALOG_AUDIT` → `CatalogAuditTask` → `CatalogAuditService.run` e `MarketIndexImportService.audit` |
| **Tarefa** | `CATALOG_AUDIT` (`activest.catalog-audit.ignored` = tickers fora de propósito) — horário e liga/desliga no banco, editados na tela de Tarefas; padrão do seed: `0 0 7 * * MON` |
| **Fonte** | lista oficial e renomes da BRAPI |
| **Grava** | desde 29/10/2026, a **inativação com prova** (`assets.active = false`, `delisted_on`, `delisting_source` `CVM` ou `LIST_GONE`; `MANUAL` nunca é tocado) e a data de saída das inativas sem data; o resto, como antes: (`notifications`, 13/10/2026) — ticker novo (uma por rodada, com a lista), sumido e renomeado (uma por ticker), índice atrasado (uma por índice); a rodada seguinte que não acha mais a diferença resolve a mensagem |

**Função.** Aponta (1) ticker novo na B3 que não está no catálogo, (2) ticker sumido (fora da lista **e** cotação
404), (3) ticker renomeado ainda ativo no catálogo, (4) índice de mercado atrasado — diário sem valor há mais de 7
dias, mensal sem o mês seguinte 45 dias depois do fim do mês de referência, ou sem série nenhuma (05/10/2026).

**Propósito.** Avisar o administrador de mudanças na bolsa. Ticker sumido ou renomeado com posição aberta também chega
a quem tem a posição ([B7] e [B3], fan-out — `docs/planos/PLANO_NOTIFICACOES.md`, 5.7).

**Inativação (29/10/2026, `docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md`, I5/C7 — `CatalogDelisting`).** (5) Registro
da companhia cancelado na CVM (`cad_cia_aberta`, menos "Migração de Registro") inativa os tickers dela; (6) ticker fora
da lista da BRAPI **e** sem negócio no COTAHIST há 30 pregões, sem ser renomeação, inativa (com COTAHIST velho, a regra
não roda). Data de saída: último negócio. Aviso `CATALOGO_TICKER_INATIVADO` com a prova; desfazer pela curadoria
(`PUT /api/v1/admin/assets/{id}/activation`, grava `MANUAL`). Os avisos de renomeação do exterior (chave `EUA:`) não são
fechados por esta conferência.

### [A18] Catálogo vivo dos EUA (29/10/2026)

| | |
|---|---|
| **Quando** | 06:30, toda segunda-feira (antes da conferência da B3) e Run |
| **Classe** | tarefa `FOREIGN_CATALOG` → `ForeignCatalogTask` → `ForeignCatalogService.run` |
| **Tarefa** | `FOREIGN_CATALOG` (atributo `WRITE`, nasce `false`: só a prévia — o portão da D12) — padrão do seed: `0 30 6 * * MON` |
| **Fonte** | Nasdaq Trader (`nasdaqtraded.txt`, fotografia do dia), OpenFIGI, SEC (tickers, fichas, séries dos fundos), Nareit, os BDRs do catálogo |
| **Grava** | sempre `carga/data/levantamento/internacional/catalogo-previa.json`; com `WRITE`, `assets` + `foreign_listings` (novos, atualizados, renomeados — aviso `CATALOGO_TICKER_RENOMEADO` —, inativados com `FORM_25`/`LIST_GONE` — aviso `CATALOGO_TICKER_INATIVADO`) e o logo do papel novo (o do BDR da mesma empresa) |

**Função.** C1–C5, C7 dos EUA e C9 do plano: universo, tipo, categoria, identidade pelo FIGI, saída com prova e a
curadoria da planilha (`curadoria.json`). Primeira rodada ~35 min (fichas da SEC); as semanais ~4 min.

**Propósito.** Manter o catálogo de Stock, REIT e ETF internacional sem cadastro manual.

### [A19] Deslistados dos EUA (29/10/2026, só Run e carga inicial)

| | |
|---|---|
| **Classe** | tarefa `FOREIGN_DELISTED` → `ForeignDelistedTask` → `ForeignDelistedService.run` |
| **Tarefa** | `FOREIGN_DELISTED` (atributos `FROM_YEAR` 2010, `TO_YEAR` vazio = ano corrente, `WRITE` falso) — sem horário |
| **Fonte** | SEC: índice trimestral (só as linhas de Form 25 ficam, `sec/form25`), o Form 25, o cadastro e os relatórios da empresa (ticker pelo XBRL ou texto, `sec/delisted`), séries dos fundos; Nareit |
| **Grava** | sempre `deslistados-previa.json`; com `WRITE`, os papéis de capital com ticker, inativos (`FORM_25`) |

**Função.** C6 do plano (ver Pontos de Atenção do `Claude.md`). Os Form 25 novos de quem está no catálogo vivo entram
pela [A18]. Primeira rodada de 2010 em diante: horas (SEC a 8 pedidos/s); com `sec/` cheia, minutos.

### [A7] Logos

| | |
|---|---|
| **Quando** | 06:00, dia 1 de cada mês |
| **Classe** | tarefa `ASSET_LOGOS` → `AssetLogosTask` → `AssetLogoService.sync(ONLY_MISSING, ...)` |
| **Tarefa** | `ASSET_LOGOS` (atributo `ONLY_MISSING`; `activest.logos.dir`) — horário e liga/desliga no banco, editados na tela de Tarefas; padrão do seed: `0 0 6 1 * *` |
| **Fontes** | BRAPI (ação, FII, ETF, BDR) e CoinGecko (cripto) |
| **Grava** | arquivos `carga/data/logos/{TICKER}.{ext}` (o arquivo é o estado; nada no banco) |

**Função.** Baixa o logo só dos ativos que ainda não têm arquivo; confere se veio imagem; busca sem resultado não apaga
o arquivo existente.

**Propósito.** A tela não aponta para sites de terceiros (links quebrados ou que mudam).

### [A8] Ouvinte "preciso do histórico" (por evento, não por horário)

| | |
|---|---|
| **Quando** | a cada mensagem no canal Redis `activest:dividends:on-demand`, publicada pela app-api depois de cada transação |
| **Classe** | `AdmMessagingConfig.onDemandListener` → `DividendCatalogService.requestOnDemand(assetId)` → `AssetLogoService.fetchIfMissing(assetId)` |

**Função.** Fila única: sincroniza os proventos/eventos do ativo da transação e baixa o logo se ele não tiver; responde
com "catálogo mudou". Cripto traz o histórico de preço; **fundo de investimento** (27/10/2026) traz a série de cotas
desde 2010 dos ZIPs do informe em disco, só o que falta (`FundCatalogService.loadSeries`, ~1,5 min no DEV).

**Propósito.** Ativo que entra numa carteira já ganha histórico de proventos e logo, sem esperar a rotina das 20:00 nem
a do dia 1.

### [A9] Índices de mercado (05/10/2026)

| | |
|---|---|
| **Quando** | 10:00, todos os dias, e na subida da adm-api quando algum índice está atrasado ou vazio |
| **Classe** | tarefa `MARKET_INDICES` → `MarketIndicesTask` (recuperação: `needsRecovery`) → `MarketIndexImportService.importRecent` |
| **Tarefa** | `MARKET_INDICES` (atributos `INDEX_CODES`, `FROM_YEAR`, `TO_YEAR`; `indices.*` (timeout 20 s, 4 tentativas)) — horário e liga/desliga no banco, editados na tela de Tarefas; padrão do seed: `0 0 10 * * *` |
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
| **Classe** | tarefa `TREASURY_PRICES` → `TreasuryPricesTask` (recuperação: `needsRecovery`) → `TreasuryPriceService.importRecent` |
| **Tarefa** | `TREASURY_PRICES` (atributo `FROM_YEAR`; `treasury.*`) — horário e liga/desliga no banco, editados na tela de Tarefas; padrão do seed: `0 0 8 * * *` |
| **Fonte** | Tesouro Transparente, `PrecoTaxaTesouroDireto.csv` (todo o histórico, ~14 MB; só baixado se mudou) |
| **Grava** | `asset_price_history` (`source = TESOURO`, PU de venda), `assets.current_price` e `carga/data/tesouro/` |
| **Ao fim** | publica "catálogo mudou" (`FECHAMENTO`) com os títulos cujo preço mudou → a app-api revaloriza o Tesouro |

**Função.** Relê os dias a partir de 10 antes do último PU gravado, casa cada linha com o título do catálogo por tipo
\+ vencimento e grava o PU de venda (sem ele, o PU base). Título do arquivo sem título no catálogo vai para o log.

**Propósito.** Valor de mercado do Tesouro na carteira (`docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md`).

### [A16] Câmbio das moedas (26/10/2026)

| | |
|---|---|
| **Quando** | 13:30, todos os dias (depois do boletim de fechamento da PTAX, ~13h). **No máximo uma rodada automática por dia**: a subida da adm-api só roda depois das 13:30 e se a agendada não rodou; a agendada pula se a subida já rodou; o Run manual roda sempre e não conta |
| **Classe** | tarefa `EXCHANGE_RATES` → `ExchangeRatesTask` (`oncePerDay`; recuperação: `needsRecovery` = alguma moeda sem o boletim do último dia útil) → `ExchangeRateImportService.importRecent` |
| **Tarefa** | `EXCHANGE_RATES` (atributos `CURRENCY_CODES`, `FROM_DATE`) — horário e liga/desliga no banco; padrão do seed: `0 30 13 * * *` |
| **Fonte** | API PTAX do Banco Central (Olinda, `CotacaoMoedaPeriodo`), um ano por chamada e moeda, só o boletim "Fechamento" |
| **Grava** | `exchange_rates` (compra e venda) e `carga/data/bcb/ptax/{moeda}/{ano}.json` |

**Função.** Por moeda ativa com fonte `BCB_PTAX`: de (último boletim − 10 dias) até hoje; moeda sem série desde 2010.
Corrigido pela fonte é sobrescrito com log. Fonte respondeu com o boletim mais recente a mais de 2 dias úteis →
`FONTE_DESATUALIZADA` (mesma regra do Tesouro). Algo mudou → "catálogo mudou" (origem `CAMBIO`): a app-api descarta o
cache de câmbio e refaz valorização e proventos das posições na moeda.

### [A17] Fundos de investimento — cadastro e cota diária da CVM (27/10/2026)

| | |
|---|---|
| **Quando** | 07:40, todos os dias (o informe da CVM é regerado de madrugada). **No máximo uma rodada automática por dia** (`oncePerDay`, como o câmbio): a subida só substitui a agendada perdida; o Run manual roda sempre |
| **Classe** | tarefa `FUND_QUOTES` → `FundQuotesTask` (recuperação: `needsRecovery` = nenhuma cota de fundo ou a última com mais de 2 dias úteis) → `FundCatalogService.runDaily` |
| **Tarefa** | `FUND_QUOTES` (atributo `FROM_MONTH`: rebaixa o informe desde o mês; vazio = mês corrente e os 12 anteriores) — padrão do seed: `0 40 7 * * *` |
| **Fonte** | Dados abertos da CVM: `registro_fundo_classe.zip`, `cad_fi_hist.zip`, `cad_fi.csv` e `inf_diario_fi_AAAAMM.zip` (até 2020, os anuais de `HIST/`), pelo `PublicFileCache` (304 quando não mudou) |
| **Grava** | `assets` (tipo `FUNDO_INVESTIMENTO`: cria fundo novo, nome, categoria, vivo, última cota), `investment_funds`, `investment_fund_names` (todos só quando pedido; senão os dos novos), `asset_price_history` (`source = CVM`) dos fundos em carteira e `carga/data/cvm/fi/` |
| **Ao fim** | publica "catálogo mudou" (`FECHAMENTO`) com os fundos em carteira → a app-api refaz o come-cotas e a valorização |

**Função.** Lê os cadastros e o informe (colunas pelo cabeçalho, 3 layouts), cadastra/atualiza os fundos (só o que
mudou), grava a última cota de cada chave e a série dos fundos com posição aberta nos meses baixados. CVM que recusa e
só a cópia em disco lida → `FAILED` + `FONTE_INDISPONIVEL`; CVM respondeu mas a cota mais nova tem mais de 2 dias úteis
→ `FONTE_DESATUALIZADA`. No DEV: ~1 min (13 meses); desde 2010 (`FROM_MONTH` = 2010-01 ou a etapa do `BOOTSTRAP`),
~7 min.

**Propósito.** Fundo de investimento com preço e série como os demais ativos (`docs/planos/PLANO_FUNDOS_INVESTIMENTO.md`).

### [A11] Cotações do catálogo — COTAHIST diário (10/10/2026)

| | |
|---|---|
| **Quando** | 21:30 e 08:30, todos os dias, e na subida da adm-api quando falta o pregão do último dia útil |
| **Classe** | tarefa `B3_DAILY_QUOTES` → `B3DailyQuotesTask` (recuperação: `needsRecovery`) → `HistoricalQuotesImportService.importRecent` |
| **Tarefa** | `B3_DAILY_QUOTES` (atributos `FROM_DATE`, `TO_DATE`) — horário e liga/desliga no banco, editados na tela de Tarefas; padrão do seed: `0 30 8,21 * * *` |
| **Fonte** | B3, `COTAHIST_DddMMaaaa.ZIP` (um por pregão, ~0,5 MB, todas as ações e FIIs) |
| **Grava** | `asset_price_history` (`source = B3_COTAHIST`), `assets.isin`; zips em `carga/data/b3/cotahist/diario` |
| **Ao fim** | com cotação gravada, publica "catálogo mudou" (`FECHAMENTO`, os ativos que ganharam preço) → alerta de preço do Preço Teto ([B3], 13/10/2026) |

**Função.** Do pregão seguinte ao último gravado pela B3 até hoje, um arquivo por dia útil (fim de semana não vai à B3;
feriado e arquivo ainda não publicado respondem 404 e ficam para a rodada seguinte). Mesmas regras do zip anual
(ticker, ISIN, fracionário). Lacuna maior que 31 dias usa o zip do ano.

**Propósito.** Preço de **todo o catálogo** atualizado sem cota da BRAPI (o fechamento [A1] só cobre a carteira): base
do preço do Preço Teto e da tela de Emissoras (`docs/planos/PLANO_PRECO_TETO.md`, 4.5 e D6). Primeira rodada
(10/10/2026): 26/09 a 09/10, 10 pregões, 9.909 cotações.

### [A12] Situação dos avisos do FNET (semanal)

| | |
|---|---|
| **Quando** | 03:00, todo domingo (21/10/2026) |
| **Classe** | tarefa `FNET_STATUS_SYNC` → `FnetStatusSyncTask` → `DividendCatalogService.refreshFnetStatus(..., ONLY_MISSING)` e depois `syncAll(..., true)` (`WITH_DIVIDENDS_REREAD`) |
| **Tarefa** | `FNET_STATUS_SYNC` (`fnet.status-pause-millis` = 3 s entre fundos) — horário e liga/desliga no banco, editados na tela de Tarefas; padrão do seed: `0 0 3 * * SUN` |
| **Fonte** | lista de avisos de cada fundo no FNET (`pesquisarGerenciadorDocumentosDados`: `status` AC/IC/CC) e os XML em `carga/data/fnet` |
| **Grava** | `carga/data/fnet/status/{CNPJ}.json` (situação de cada aviso) e, na rodada de proventos, o que mudou nos eventos (R10: pagamento retificado; R11: aviso inativado sob o CNPJ de outro fundo) |

**Função.** (1) Relê a situação de todos os fundos com aviso em disco (~1 h: uma busca por vez, com pausa — o FNET
segura buscas seguidas; quem falhar fica com o arquivo anterior). (2) Roda a rodada de proventos relendo **todos** os
avisos em disco (`fnet=all`, ~40 min), idempotente: absorve as retificações e não duplica.

**Propósito.** A rotina diária [A4] só relê a situação do fundo quando a B3 mostra provento novo; a retificação que só
muda o pagamento e o aviso inativado depois ficavam esperando (`docs/PENDENCIAS.md` 33.3,
`docs/planos/PLANO_CORRECAO_PROVENTOS.md`). Os arquivos de situação novos vão para o bucket no `data push`.

---

## Parte 2 — app-api (carteira)

### [B1] Rede de segurança da carteira

| | |
|---|---|
| **Quando** | 21:00, todos os dias |
| **Classe** | tarefa `POSITIONS_REFRESH` → `PositionsRefreshTask` → `PositionRefreshService.refreshAll` |
| **Tarefa** | `POSITIONS_REFRESH` (Stop desfaz a rodada (uma transação)) — horário e liga/desliga no banco, editados na tela de Tarefas; padrão do seed: `0 0 21 * * *` |
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
| **Quando** | a cada mensagem no canal Redis `activest:catalog:assets`, publicada pela adm-api (`{origin: PROVENTOS\|FECHAMENTO\|INDICES, assetIds, indexCodes}`) |
| **Classe** | `CatalogMessagingConfig` → `PositionRefreshService` |

**Função.** `PROVENTOS` → refaz proventos e valorização das posições dos ativos citados; `FECHAMENTO` → só a
valorização (inclusive Tesouro, pelo PU oficial); `INDICES` (05/10/2026) → refaz a curva de todos os títulos de renda
fixa privada em aberto (`FixedIncomeValuation`); `BALANCOS` (13/10/2026) → nada na carteira.

**Depois do recálculo (13/10/2026, `CatalogReaction`).** As mensagens que dependem do catálogo
(`docs/planos/PLANO_NOTIFICACOES.md`): `PROVENTOS` → bonificação sem custo vira pendência ("Informar custo");
`FECHAMENTO` → aviso de catálogo para quem tem a posição (fan-out), alerta de preço abaixo do ideal e liquidação do
Tesouro vencido que esperava o PU; `INDICES` → liquidação da RF privada vencida que esperava o índice; `BALANCOS` →
"padrão mudou" do Preço Teto. Uma reação que falha não impede as outras.

**Propósito.** A carteira reage ao catálogo na hora, sem esperar as 21:00.

### [B4] Sincronização diária das corretoras

| | |
|---|---|
| **Quando** | 08:45, todos os dias (até 03/10/2026, 06:30) |
| **Classe** | tarefa `INTEGRATIONS_DAILY_SYNC` → `IntegrationsDailySyncTask` → `IntegrationService.enqueueScheduled` |
| **Tarefa** | `INTEGRATIONS_DAILY_SYNC` (`activest.integrations.auto-confirm-scheduled = true`) — horário e liga/desliga no banco, editados na tela de Tarefas; padrão do seed: `0 45 8 * * *` |
| **Grava** | `integration_runs` (status `QUEUED`, trigger `SCHEDULED`) |

**Função.** Só **enfileira** uma execução para cada conexão `ACTIVE` ou `PENDING_AUTHORIZATION` (B3: é a checagem que
a leva a `ACTIVE` quando o investidor autorizar) que não tenha execução pendente (`QUEUED`, `RUNNING` ou
`AWAITING_CONFIRMATION`) nem tenha passado do limite de uma consulta por dia da fonte (B3). Quem busca na corretora é o worker [B5]. A execução agendada confirma sozinha
quando não há nada a decidir (nenhuma possível duplicata) e a conexão já teve uma importação confirmada pelo usuário.

**Propósito.** Trazer as operações novas das fontes (Binance e B3) todo dia. Desde 13/10/2026 cada execução agendada
gera a sua mensagem nas Notificações (importou N operações, aguardando confirmação, divergência; falha e problema de
conexão também na manual). O horário é depois das 8h porque a B3 só
publica o dia anterior a partir daí (docs/planos/PLANO_INTEGRACAO_B3.md, A6).

### [B5] Worker da fila de integrações

| | |
|---|---|
| **Quando** | a cada 5 s (`fixedDelay`), primeira execução 10 s após subir |
| **Classe** | `IntegrationWorker.processar` → `IntegrationService.processNext` |
| **Config** | `activest.integrations.worker-delay-ms = 5000`; `activest.integrations.stale-minutes = 30` |
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

### [B7] Abertura do dia (13/10/2026)

| | |
|---|---|
| **Quando** | 00:00, todos os dias, **e** na subida da app-api — "meia-noite ou o primeiro horário do dia com o sistema no ar" |
| **Classe** | tarefa `DAILY_OPENING` → `DailyOpeningTask` (na subida: `needsRecovery` sempre verdadeiro) → `DailyOpeningService.run` |
| **Tarefa** | `DAILY_OPENING` (chave geral `activest.scheduling.enabled`) — horário e liga/desliga no banco, editados na tela de Tarefas; padrão do seed: `0 0 0 * * *` |
| **Grava** | `notifications` |

**Função.** Em ordem: (1) **títulos vencidos** (`MaturitySettlementService`): desde 15/10/2026 só avisa — pendência
`RF_VENCIDO` com o líquido estimado no vencimento e o link "Lançar liquidação"; a venda é lançada pelo usuário
(`docs/planos/PLANO_VENDA_RENDA_FIXA.md`, seção 6); (2) **proventos pagos hoje** — uma mensagem por ativo; (3) **desdobramento, grupamento e
bonificação com custo** com data-ex hoje; (4) conferências: bonificação sem custo, aviso de catálogo para quem tem a
posição, alerta de preço e "padrão mudou" do Preço Teto. Uma etapa que falha não impede as outras.

**Sem coluna de controle.** Tudo é idempotente pelas chaves das mensagens: subir duas vezes no dia não duplica nada.
Mensagem de D0 nunca é retroativa — se o sistema não subir no dia, ela não existe.

**Propósito.** O usuário fica sabendo, na caixa de Notificações, do título vencido e do que aconteceu na carteira no dia.

---

### [B8] Metas (24/10/2026)

| | |
|---|---|
| **Quando** | 21:30, todos os dias (depois do [B1]); e, por evento, a reavaliação a cada 15 s |
| **Classe** | tarefa `GOALS_SNAPSHOT` → `GoalsSnapshotTask` → `GoalTracker.trackUser`; por evento: `GoalReevaluationQueue` (transação gravada, "catálogo mudou") → `GoalReevaluationWorker` |
| **Tarefa** | `GOALS_SNAPSHOT` (V8; Stop entre usuários e entre metas) — horário e liga/desliga no banco; padrão do seed: `0 30 21 * * *` |
| **Grava** | `goal_snapshots` (upsert do dia), `goal_events`, `goals` (conclusão automática, prazo vencido), `notifications` (categoria METAS) |

**Função.** Avalia as metas ativas de cada usuário (`docs/METAS_METODOLOGIA.md`): fotografia do dia, eventos (atingiu,
saiu/voltou ao objetivo, prazo vencido), conclusão automática da meta crescente atingida (D13) e os avisos da D20.

**Sem coluna de controle.** A fotografia é por meta e dia: rodar de novo no dia regrava a mesma linha; os avisos
dependem das chaves de repetição.

**Propósito.** Histórico auditável das metas (o que cada uma mostrava em cada dia) e avisos sem o usuário abrir a tela.

---

## Parte 3 — rotinas que não estavam neste documento (22/10/2026)

### [A13] Histórico de preço de cripto

| | |
|---|---|
| **Quando** | 19:30, todos os dias |
| **Classe** | tarefa `CRYPTO_PRICE_HISTORY` → `CryptoPriceHistoryTask` → `CryptoHistoryService.importAll` |
| **Fonte** | CoinGecko (últimos 365 dias) e Binance pública (BTC/BRL desde 13/10/2020) |
| **Grava** | `asset_price_history` (`COINGECKO`, `BINANCE` — só dia sem linha); `carga/data/coingecko/historico`, `carga/data/binance/klines` |

**Propósito.** Série de preço de toda cripto que está ou esteve em carteira e do benchmark BTC, para a Rentabilidade
(`docs/planos/PLANO_RENTABILIDADE.md`, 3.3).

### [A14] Emissoras e distribuidoras de renda fixa (mensal)

| | |
|---|---|
| **Quando** | 05:30, dia 1 de cada mês |
| **Classe** | tarefa `FIXED_INCOME_ISSUERS` → `FixedIncomeIssuersTask` → `FixedIncomeIssuerImportService.importAll` e `FixedIncomeLogoService.sync` |
| **Fontes** | Banco Central (IF.data, Instituições em funcionamento), CVM; logos do Open Finance e do `logos-bancos-br` |
| **Grava** | `fixed_income_issuers`, `fixed_income_groups`; `carga/data/logos/rf/` |

**Propósito.** Cadastro de emissoras e distribuidoras em dia (`docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md`, 4.2).

### [A15] Limpeza do histórico das tarefas

| | |
|---|---|
| **Quando** | 04:00, todos os dias |
| **Classe** | tarefa `TASK_HISTORY_CLEANUP` → `TaskHistoryCleanupTask` |
| **Grava** | apaga linhas de `task_execution_logs` com mais de `LOG_RETENTION_DAYS` (30) e rodadas terminadas com mais de `EXECUTION_RETENTION_DAYS` (365) |

**Propósito.** Retenção do histórico das tarefas (`docs/planos/PLANO_TAREFAS.md`, D7 e D34).

---

## Fora do agendamento (de propósito)

- **Carga inicial** (`POST /api/v1/admin/bootstrap`): **nunca** dispara sozinha ao subir; é manual (ver
  [ADM_TASKS.md](ADM_TASKS.md)).
- Série histórica COTAHIST, importação de emissores e ligação ativo → emissor: só sob demanda.
- Todas as rotinas da adm-api também têm disparo manual pelos endpoints `/api/v1/admin/*` (ver
  [ADM_TASKS.md](ADM_TASKS.md)).
