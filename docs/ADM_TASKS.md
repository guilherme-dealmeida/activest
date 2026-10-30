# Activest — Endpoints administrativos

> Gerado em 02/10/2026 a partir de `AdminController` (adm-api) · revisado em 03/10/2026 (Markdown, caminhos `carga/`) ·
> 05/10/2026 ([18] índices de mercado, [19] preço do Tesouro) · 21/10/2026 ([17] situação dos avisos do FNET, no lugar
> da antiga conferência por extrato, removida — `docs/planos/PLANO_CORRECAO_PROVENTOS.md`) · 22/10/2026 (Tarefas: os
> endpoints passam pelo executor das tarefas e cada pedido vira uma rodada na tela `/tasks` — `docs/planos/PLANO_TAREFAS.md`,
> D16 e D22) · 27/10/2026 (Fundos de investimento: sem endpoint próprio — tarefa `FUND_QUOTES` e etapa do `BOOTSTRAP`) ·
> 29/10/2026 (Ativos internacionais: [16] passa a inativar com prova, [21] desfazer inativação, [22] catálogo vivo dos EUA,
> [23] deslistados dos EUA, duas etapas novas no `BOOTSTRAP` — `docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md`).

| Tema | Como funciona |
|---|---|
| **Acesso** | JWT de usuário com `users.is_admin = true`. Sem token: 401. Usuário comum: 403 "Somente administrador.". O login é sempre na app-api (`POST /api/v1/auth/login`, porta 8080); a adm-api (8081) só valida o mesmo token |
| **Execução** | desde 22/10/2026, pelo executor das **tarefas** (`TaskExecutor`): cada pedido é uma rodada da tarefa do tipo (a primeira tarefa viva do tipo), com os parâmetros da rota por cima dos atributos dela; roda em segundo plano e responde na hora `202 Accepted` com `{"status":"iniciada","task":"<nome>","result":"na tela de Tarefas e no log da API","executionId":N}` (`status` = "pulada (ja em execucao)" quando o tipo já roda — D20). O resultado (resumo, erro e o log da rodada) aparece na tela de Tarefas (`/tasks`) e no log do contêiner. A curadoria (`fii-cnpjs`, `assets/{id}/issuer`, `assets/{id}/unit-composition`) continua síncrona e responde como antes, agora registrada como rodada das tarefas `FII_CNPJ_CURATION`, `ASSET_ISSUER_LINK` e `UNIT_COMPOSITION`. Sem rota aqui, o Run da tela de Tarefas faz o mesmo |
| **Idempotência** | todas as rotinas podem ser rodadas de novo: o que já está gravado é pulado ou regravado com o mesmo valor; o que já está em `carga/data/` não é baixado de novo |
| **Swagger** | http://localhost:8081/swagger-ui.html |

**Disparo** pelo script do projeto (usa `ACTIVEST_PASSWORD` do `.env`):

```bash
node carga/scripts/admin.mjs <rotina>
docker compose --profile carga run --rm tools node carga/scripts/admin.mjs <rotina>   # sem Node na máquina

# exemplos
node carga/scripts/admin.mjs cvm/import
node carga/scripts/admin.mjs assets/123/unit-composition --method PUT --body '{"ON":1,"PNA":2}'
```

## Resumo

Prefixo das rotas: `/api/v1/admin`.

| # | Método | Rota | API | Resposta | Grupo |
|---|---|---|---|---|---|
| 01 | POST | `/bootstrap` | adm | 202 | Carga inicial |
| 02 | POST | `/issuers/import` | adm | 202 | Emissores |
| 03 | POST | `/issuers/link` | adm | 202 | Emissores |
| 04 | POST | `/issuers/fii-cnpjs` | adm | 200 | Emissores (curadoria) |
| 05 | PUT | `/assets/{assetId}/issuer` | adm | 204 | Emissores (curadoria) |
| 06 | PUT | `/assets/{assetId}/unit-composition` | adm | 204 | Emissores (curadoria) |
| 07 | POST | `/b3/cotahist?from&to` | adm | 202 | Preços |
| 07b | POST | `/b3/cotahist/diario?from&to` | adm | 202 | Preços (pregão a pregão) |
| 08 | POST | `/closing` | adm | 202 | Preços |
| 09 | POST | `/proventos[?fnet=all]` | adm | 202 | Proventos (`fnet=all` relê todos os avisos do FNET em disco, inclusive os já gravados — depois de uma limpeza como a V60) |
| 10 | POST | `/cvm/proventos?from&to` | adm | 202 | Proventos (datas) |
| 11 | POST | `/dividends/payment-date-survey` | adm | 202 | Proventos (datas) |
| 12 | POST | `/cvm/import?from&to&force` | adm | 202 | Balanços |
| 13 | POST | `/cvm/quarters` | adm | 202 | Balanços |
| 14 | POST | `/cvm/fii-monthly?from&to&force` | adm | 202 | Informe FII |
| 15 | POST | `/logos` | adm | 202 | Logos |
| 16 | POST | `/catalog-audit` | adm | 202 | Conferência |
| 17 | POST | `/dividends/fnet-status` | adm | 202 | Proventos (FNET) |
| 18 | POST | `/indices?codes&from&to&recent` | adm | 202 (400 com código desconhecido) | Índices de mercado |
| 19 | POST | `/tesouro?from&recent` | adm | 202 | Preço do Tesouro |
| 20 | GET/POST/PUT | `/currencies[/{code}]` | adm | 200 (403 sem `is_admin`) | Moedas (cadastro; câmbio pela tarefa `EXCHANGE_RATES`) |
| 21 | PUT | `/assets/{assetId}/activation` | adm | 200 (404/400/409) | Catálogo (curadoria da inativação) |
| 22 | POST | `/foreign-catalog[?write=true]` | adm | 202 | Exterior (catálogo vivo) |
| 23 | POST | `/foreign-delisted?from&to[&write=true]` | adm | 202 | Exterior (deslistados 2010+) |

---

## Carga inicial

### [01] `POST /api/v1/admin/bootstrap`

**Serviço:** `BootstrapService.run`

**Função.** Carga inicial num banco vazio, em ordem (emissores do catálogo, ligações e units já vêm do seed V30):

| Etapa | O que faz | Mesma rotina de |
|---|---|---|
| 1 | emissores (CVM: cadastro, FCA, informe) | [02] |
| 2 | COTAHIST 2010..ano corrente (preços + ISIN) | [07] |
| 3 | proventos (B3 12 meses + acumulado, eventos societários, histórico longo B3/FNET) | [09] |
| 4 | datas de pagamento CVM 2022+ | [10] |
| 5 | datas de pagamento do levantamento | [11] |
| 6 | balanços DFP/ITR 2010+ e série trimestral | [12] |
| 7 | informe mensal de FII 2016+ | [14] |
| 8 | índices de mercado 2010+ (anos fechados de `carga/data/bcb` e `carga/data/b3/indices`) | [18] |
| 9 | preço do Tesouro Direto 2010+ (`carga/data/tesouro`, ou o Tesouro Transparente se mudou) | [19] |
| … | (depois: câmbio, histórico de cripto, fundos de investimento — ver `BootstrapService`) | |
| 10 | catálogo vivo dos EUA, gravando (29/10/2026) | [22] |
| 11 | deslistados dos EUA 2010+, gravando (29/10/2026) | [23] |

Etapa que falha é registrada e a seguinte roda assim mesmo. Uma carga por vez. Termina no log com
`Carga inicial (...) concluida` e o resumo de cada etapa.

**Propósito.** Subir uma máquina nova só com o repositório + bucket (`data pull`) e chegar ao catálogo completo.
Nunca dispara sozinha ao subir.

**Pré-requisito.** `carga/data/` baixada do bucket (`docker compose --profile carga run --rm data pull`) e as APIs no
ar (migrations aplicadas).

---

## Emissores

### [02] `POST /api/v1/admin/issuers/import`

**Serviço:** `IssuerImportService.importAll` · **Grava:** `issuers`

**Função.** Companhias (cadastro da CVM + FCA dos 3 últimos anos: ativas em bolsa e as que têm ticker com raiz de ação
do catálogo), FIIs (informe mensal do ano corrente e anterior, negociados em bolsa) e classificação setorial da B3
(setor/subsetor/segmento, só para quem ainda não tem). Atualiza pelo CNPJ; nunca toca o segmento curado do FII.

**Propósito.** Cadastro de emissores para ligar ativos e importar balanços.

### [03] `POST /api/v1/admin/issuers/link`

**Serviço:** `IssuerLinkService.linkAll` · **Grava:** `assets.issuer_id / share_class / issuer_link_source`,
`unit_compositions`

**Função.** Liga ativo → emissor. Ações: FCA (ticker → CNPJ), raiz do ticker (KLBN11 leva KLBN3/KLBN4), B3 (raiz →
código CVM); fracionário segue o lote padrão; unit ganha composição pelo texto do FCA. FIIs: ISIN (COTAHIST) → informe
da CVM → CNPJ. Ligação `MANUAL` nunca é sobrescrita.

**Propósito.** Associar cada ativo à empresa/fundo para indicadores e proventos.

### [04] `POST /api/v1/admin/issuers/fii-cnpjs` — síncrono

**Corpo:** `[ {"ticker":"XPML11","cnpj":"..."}, ... ]` · **Serviço:** `IssuerLinkService.applyCuratedFunds`

**Resposta:** 200 com a situação de cada linha (confirmada, emissor criado, ticker fora do catálogo...).

**Função.** Aplica uma lista curada de CNPJs de FII: liga como `MANUAL` e cria o emissor que faltar.

**Propósito.** Corrigir o que o ISIN não resolve ou resolve errado (ex.: APTO11, XPML11 no informe de 2026).

### [05] `PUT /api/v1/admin/assets/{assetId}/issuer` — síncrono

**Corpo:** `{"issuerId": 10, "cnpj": null, "shareClass": "ON"}` · **Serviço:** `IssuerLinkService.setManual`

**Resposta:** 204; 404 se ativo/emissor não existir.

**Função.** Liga manualmente um ativo a um emissor (por `issuerId` ou por CNPJ — companhia fora dos emissores é
procurada no cadastro da CVM). Sem `issuerId` e sem `cnpj`: desfaz a ligação.

**Propósito.** Curadoria pontual de ligação.

### [06] `PUT /api/v1/admin/assets/{assetId}/unit-composition` — síncrono

**Corpo:** `{"ON": 1, "PNA": 2}` · **Serviço:** `IssuerLinkService.setUnitComposition`

**Resposta:** 204; 404 ativo inexistente; 422 estado inválido.

**Função.** Grava a composição manual de uma unit e marca a ligação `MANUAL`.

**Propósito.** Units cujo texto do FCA não dá a composição certa.

---

## Preços

### [07] `POST /api/v1/admin/b3/cotahist?from=2010&to=<ano corrente>`

**Serviço:** `HistoricalQuotesImportService.importYears` · **Validação:** `from >= 1986` e `to >= from`, senão 400 ·
**Grava:** `asset_price_history` (source `B3_COTAHIST`), `assets.isin`

**Função.** Série histórica oficial da B3 para ACAO (lote padrão e fracionário) e FII do catálogo; ticker antigo com o
mesmo ISIN entra no ativo atual. A série da B3 vence a BRAPI e não é sobrescrita depois. Zips em
`carga/data/b3/cotahist` (ano fechado em disco não vai à rede).

**Propósito.** Histórico de preço desde 2010 (gráfico de cotação, indicadores históricos) e o ISIN de cada ativo (usado
pelos proventos).

### [07b] `POST /api/v1/admin/b3/cotahist/daily?from=AAAA-MM-DD&to=AAAA-MM-DD` (10/10/2026)

**Serviço:** `HistoricalQuotesImportService.importRecent` (sem `from`) ou `importDays` · **Validação:** `to >= from` e
no máximo 366 dias, senão 400 · **Grava:** `asset_price_history` (source `B3_COTAHIST`), `assets.isin`

**Função.** O mesmo da rotina [A11] de `docs/SCHEDULE_TASKS.md`: um `COTAHIST_DddMMaaaa.ZIP` por pregão, cache em
`carga/data/b3/cotahist/diario`. Sem parâmetros, do pregão seguinte ao último gravado até hoje.

**Propósito.** Cotação de todo o catálogo em dia (Preço Teto, Emissoras) — `docs/planos/PLANO_PRECO_TETO.md`, 4.5.

### [08] `POST /api/v1/admin/closing`

**Serviço:** `ClosingPriceService.runB3Closing` + `runCryptoClosing`

**Função.** Mesma rodada das 19:00 (BRAPI para B3, CoinGecko para cripto), na hora. Publica "catálogo mudou"
(`FECHAMENTO`).

**Propósito.** Atualizar preços fora do horário (ex.: API estava desligada).

---

## Proventos

### [09] `POST /api/v1/admin/dividends`

**Serviço:** `DividendCatalogService.syncAll` · **Grava:** `asset_cash_events`, `asset_stock_events`;
`carga/data/b3`, `carga/data/fnet`

**Função.** Mesma rodada das 20:00 (sem a etapa da CVM): proventos e eventos societários da B3 por emissor, FNET como
fonte principal do FII, histórico longo. Publica "catálogo mudou" (`PROVENTOS`).

**Propósito.** Reprocessar proventos sob demanda.

### [10] `POST /api/v1/admin/cvm/payment-dates?from=2022&to=<ano corrente>`

**Serviço:** `CvmPaymentDateService.run` · **Validação:** `from >= 2022` e `to >= from`, senão 400 · **Grava:**
`asset_cash_events.payment_date` (`payment_date_source = 'CVM'`); `carga/data/cvm/ipe`, `carga/data/cvm/proventos`

**Função.** Lê os "Relatórios Proventos" da CVM e completa a data de pagamento dos eventos **sem** data que batem em
ISIN, data-com e valor (parcelas na ordem). A data da própria fonte (B3) substitui depois.

**Propósito.** Catálogo com a data de pagamento completa (ações, 2022+).

### [11] `POST /api/v1/admin/dividends/payment-date-survey`

**Serviço:** `PaymentDateSurveyService.run` · **Entrada:** `carga/data/levantamento/datas-pagamento.json` (gerado da
planilha por `carga/scripts/levantamento/xlsx_para_json.py`) · **Grava:** `asset_cash_events.payment_date`
(`payment_date_source = 'LEVANTAMENTO'`)

**Função.** Completa só proventos sem data, casando ticker, tipo, data-com, parcela e valor; não passa por cima de B3,
FNET nem CVM; linha `A_DEFINIR` não grava nada. Mesma etapa do bootstrap.

**Propósito.** Datas de pagamento que nenhuma fonte automática traz (2010–2021), aplicadas num banco que já existe.

### [17] `POST /api/v1/admin/dividends/fnet-status` (21/10/2026)

**Serviço:** `DividendCatalogService.refreshFnetStatus` · **Grava:** `carga/data/fnet/status/{CNPJ}.json` (nada no banco)

**Função.** Para cada fundo com aviso em `carga/data/fnet`, busca no FNET a lista dos avisos de rendimentos e
amortizações e grava a situação de cada um: `AC` ativo, `IC` inativo, `CC` cancelado. Uma busca por fundo (3 a 10 s);
falha de um fundo só vira log, e rodar de novo pega as falhas. A rotina diária atualiza o arquivo do fundo sempre que
busca aviso novo.

**Propósito.** Aviso inativo ou cancelado (o fundo corrigiu a data-base e publicou outro, ou retirou o aviso) não vira
evento, e o evento que ele já tinha gerado é substituído no lugar pelo aviso novo (`docs/planos/PLANO_CORRECAO_PROVENTOS.md`,
D5/R5). Num banco novo os arquivos chegam pelo bucket, como os XML.

---

## Balanços e informe de FII

### [12] `POST /api/v1/admin/cvm/import?from=2010&to=<ano corrente>&force=false`

**Serviço:** `FinancialStatementImportService.importYears` · **Validação:** `from >= 2010` e `to >= from`, senão 400 ·
**Grava:** `cvm_documents`, `cvm_statement_values`, `share_counts` e, ao fim, `company_quarters`

**Função.** DFP e ITR das companhias emissoras (consolidado ou, na falta, individual); métricas por descrição de conta;
quantidade de ações (composição 2020+ com escala conferida, ou FRE antes de 2020). Mesma versão é pulada;
reapresentação substitui. `force=true` reprocessa a partir de `carga/data/cvm` sem baixar de novo — é o que se roda
quando entra métrica nova (24/10/2026, tela de Ativos: 6 contas novas e a exaustão biológica; ~5 a 7 min, refaz também
`share_counts`, conferido idêntico antes e depois — `docs/planos/PLANO_TELA_ATIVOS.md`, A1 e D22).

**Propósito.** Base dos indicadores fundamentalistas.

### [13] `POST /api/v1/admin/cvm/quarters`

**Serviço:** `CompanyQuarterService.rebuild` · **Grava:** `company_quarters` (recalculada inteira)

**Função.** Refaz a série trimestral (trimestre isolado, 12 meses, posição) a partir dos balanços já gravados. Leva
segundos.

**Propósito.** Depois de corrigir quantidade de ações à mão (a carga [12] já faz isso sozinha ao terminar).

### [14] `POST /api/v1/admin/cvm/fii-monthly?from=2016&to=<ano corrente>&force=false`

**Serviço:** `FiiMonthlyImportService.importYears` · **Validação:** `from >= 2016` e `to >= from`, senão 400 ·
**Grava:** `fii_monthly`

**Função.** Informe mensal de FII da CVM, só fundos emissores (por CNPJ). Mês com versão igual ou maior é pulado;
`force=true` regrava a partir de `carga/data/cvm/fii`.

**Propósito.** Indicadores de FII (VP por cota, P/VP, DY, cotistas...).

---

## Logos e conferência

### [15] `POST /api/v1/admin/logos`

**Serviço:** `AssetLogoService.sync(false, ...)` · **Grava:** `carga/data/logos/{TICKER}.{ext}`

**Função.** Busca de novo o logo de **todos** os ativos (a rotina mensal busca só quem não tem). Busca sem resultado não
apaga o arquivo existente.

**Propósito.** Renovar logos trocados pelas empresas.

### [16] `POST /api/v1/admin/catalog-audit`

**Serviço:** `CatalogAuditService.run` (+ `CatalogDelisting`) e `MarketIndexImportService.audit` · **Grava:** desde
29/10/2026, a inativação com prova (`assets.active`, `delisted_on`, `delisting_source`) e a data de saída das inativas sem
data; o resto são avisos de administrador

**Função.** Mesma conferência das segundas 07:00: tickers novos, sumidos e renomeados contra a lista da BRAPI, os
índices de mercado atrasados e a inativação automática do nacional (registro cancelado na CVM, menos migração; fora da
lista da BRAPI + 30 pregões sem negócio no COTAHIST, sem ser renomeação — `PLANO_ATIVOS_INTERNACIONAIS`, I5/C7).

**Propósito.** Ver as diferenças do catálogo sem esperar a semana.

---

## Índices de mercado

### [18] `POST /api/v1/admin/indices?codes=&from=&to=&recent=false`

**Serviço:** `MarketIndexImportService.importYears` (ou `importRecent` com `recent=true`) · **Grava:**
`market_index_values` e `carga/data/bcb/sgs/{código SGS}/{ano}.json`, `carga/data/b3/indices/{índice}/{ano}.json`

**Função.** Carga dos índices (`docs/planos/PLANO_INDICES_MERCADO.md`): `codes` separados por vírgula (`CDI`, `SELIC`,
`IPCA`, `IGPM`, `INPC`, `TR`, `SELIC_META`, `USD_PTAX`, `IBOV`, `IFIX`; padrão: todos), `from` (padrão: 2010, o
`history_from` de cada índice) e `to` (padrão: ano corrente). Ano fechado com arquivo não vai à fonte; valor igual é
pulado, corrigido é sobrescrito com log, data futura não entra. `recent=true` roda a rodada incremental da rotina das
10:00 e ignora o período. Código desconhecido: 400 antes de começar.

```bash
node carga/scripts/admin.mjs indices
node carga/scripts/admin.mjs "indices?codes=CDI,IPCA&from=2024"
node carga/scripts/admin.mjs "indices?recent=true"
```

**Propósito.** Carga histórica num banco existente (o `bootstrap` já faz num banco novo), estender o histórico para
antes de 2010 ou reprocessar um índice.

---

## Preço do Tesouro Direto

### [19] `POST /api/v1/admin/treasury?from=2010&recent=false`

**Serviço:** `TreasuryPriceService.importFrom` (ou `importRecent` com `recent=true`) · **Grava:** `asset_price_history`
(`source = TESOURO`), `assets.current_price`, `carga/data/tesouro/PrecoTaxaTesouroDireto.csv`

**Função.** PU de venda (resgate antecipado) de cada título do catálogo desde `from` (padrão 2010; o arquivo começa em
2002), casado por tipo + vencimento; o último dia vira o preço atual e a app-api é avisada ("catálogo mudou",
FECHAMENTO). O arquivo (~14 MB) só é baixado se mudou. `recent=true` = a rodada das 08:00.

```bash
node carga/scripts/admin.mjs treasury
node carga/scripts/admin.mjs "treasury?from=2002"
```

**Propósito.** Carregar o histórico num banco existente (o `bootstrap` já faz num banco novo) ou estender para antes de
2010 (`docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md`).

## Moedas

### [20] `GET/POST/PUT /api/v1/admin/currencies` (26/10/2026) — síncrono

**Serviço:** `CurrencyAdminController` (adm-api; o front chega pelo proxy `/api/v1/admin/currencies`) · **Grava:**
`currencies`

**Função.** Lista as moedas com a cobertura da série de câmbio; inclui (código ISO, nome, símbolo, casas, país, fonte
`BCB_PTAX`, ativa) e edita. A moeda base não perde a marca, não ganha fonte e não é desativada; moeda com ativo do
catálogo não é desativada. Toda mudança recarrega as moedas conhecidas das regras de importação e publica "catálogo
mudou" (CAMBIO). A série de uma moeda nova entra pela tarefa `EXCHANGE_RATES` (Run com `CURRENCY_CODES`).

```bash
node carga/scripts/admin.mjs currencies --method GET
```

**Propósito.** Moeda nova = uma linha aqui + a série de câmbio (`docs/planos/PLANO_CADASTRO_MOEDAS.md`).

## Exterior e inativação (29/10/2026, `docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md`)

### [21] `PUT /api/v1/admin/assets/{assetId}/activation` — síncrono

**Serviço:** `AssetActivationService.apply` · **Grava:** `assets.active`, `delisted_on`, `delisting_source = MANUAL`

**Corpo.** `{"active": true}` (reativa e resolve o aviso `CATALOGO_TICKER_INATIVADO`) ou
`{"active": false, "delistedOn": "AAAA-MM-DD"}`. Ativo inexistente: 404; data inválida: 400; reativar um ticker que já
tem um vivo do mesmo tipo: 409.

**Propósito.** Desfazer (ou fazer) a inativação das tarefas: a decisão do administrador nunca é refeita.

### [22] `POST /api/v1/admin/foreign-catalog[?write=true]`

**Serviço:** `ForeignCatalogService.run` (tarefa `FOREIGN_CATALOG`, segundas 06:30) · **Grava:** sempre
`carga/data/levantamento/internacional/catalogo-previa.json`; com `write=true`, `assets` (USD, país US) e
`foreign_listings`, renomeações e inativações dos EUA

**Função.** Lista do Nasdaq Trader → tipo pelo OpenFIGI → categoria (BDR, SIC, Nareit, palavras do ETF) → identidade
pelo FIGI → saída com prova (Form 25 ou 30 dias fora da lista), aplicando a curadoria `curadoria.json`.

**Propósito.** Primeira carga e conferência da planilha de revisão (`carga/scripts/levantamento/internacional_planilha.py`).

### [23] `POST /api/v1/admin/foreign-delisted?from=2010&to=<ano corrente>[&write=true]`

**Serviço:** `ForeignDelistedService.run` (tarefa `FOREIGN_DELISTED`, só Run) · **Grava:** sempre
`carga/data/levantamento/internacional/deslistados-previa.json`; com `write=true`, os papéis de capital com ticker como
inativos (`delisting_source = FORM_25`, `delisted_on` = saída do Form 25)

**Função.** Form 25/25-NSE do índice da SEC → classe, bolsa e saída → ticker (XBRL, texto, séries da SEC, Nareit) → tipo
e categoria, com a curadoria `curadoria-deslistados.json` (chave = número de acesso).

**Propósito.** O histórico de quem saiu da bolsa desde 2010, para o lançamento retroativo; a cobertura por ano e tipo
sai na planilha `internacional_deslistados.py planilha`.

---

## Fundos de investimento

### (27/10/2026) — sem endpoint próprio

O cadastro e a cota dos fundos da CVM entram pela tarefa `FUND_QUOTES` (tela `/tasks`: Run; atributo `FROM_MONTH`
para rebaixar desde um mês — ver `docs/SCHEDULE_TASKS.md`, [A17]) e pela etapa "fundos de investimento" do
`POST /api/v1/admin/bootstrap` (o informe inteiro desde 2010, para cadastrar também os encerrados; sem série). A série
de cada fundo entra sob demanda, depois da primeira transação, pelo mesmo pedido "preciso do histórico" dos proventos
(`docs/planos/PLANO_FUNDOS_INVESTIMENTO.md`, C11).
