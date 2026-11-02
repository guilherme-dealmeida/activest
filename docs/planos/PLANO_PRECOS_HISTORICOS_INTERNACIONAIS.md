# Plano — Preço histórico para ativos internacionais Activest

> Aberto em 01/11/2026 (POR ESCOPO). **Escopo encerrado em 01/11/2026** (commit feito pelo Guilherme). A parte 3 é o `PLANO_PROVENTOS_INTERNACIONAIS.md`. Parte 2 de 3 da branch (a parte 1 foi o
> `PLANO_PRECOS_INTERNACIONAIS.md`, preço diário pelo Massive).

## 1. Objetivo

Série histórica de preço dos ativos internacionais (Stock, REIT, ETF internacional) **desde 2010**, como a da B3: para a
tela de Ativos (gráfico até o MAX), a Rentabilidade e as Metas (preço de cada dia desde a primeira compra) e o
lançamento retroativo. Pela descrição do Guilherme: retroagir pelo Massive grouped daily até 2010 ou, se não der,
articular outras fontes.

## 2. Discovery

### 2.1 O que existe hoje (01/11/2026)

| Item | Situação |
|---|---|
| Catálogo | 12.180 vivos e 5.906 deslistados desde 2010 (Stock 10.902, REIT 290, ETF 6.894) |
| Série gravada | Só o pregão de 30/10/2026 (`asset_price_history`, `source = MASSIVE`), 12.008 vivos; a tarefa diária segue terça a sábado |
| Massive gratuito | **2 anos corridos**: 01/11/2024 respondeu, 30/10/2024 voltou 403 "past historical entitlements" (prova de 31/10/2026) — **não chega a 2010** |
| Modelo na B3 | `B3_YEARLY_QUOTES` / `HistoricalQuotesImportService`: série desde 2010, não ajustada, um arquivo por ano em `carga/data`, idempotente; o ajuste por desdobramento é feito na leitura (tela de Ativos) |
| Uso pessoal | As fontes gratuitas e "pessoais" proíbem mostrar o dado a terceiros (PENDENCIAS 44.4) |

### 2.2 Fontes avaliadas para 2010 → 2024

| Fonte | Desde 2010? | Deslistados | Como vem | Custo | Observação |
|---|---|---|---|---|---|
| **Massive gratuito** (grouped daily) | Não: 2 anos (nov/2024 →) | Os do período | Um pedido por pregão, mercado inteiro, não ajustado | US$ 0 | ~500 pedidos a 5/min ≈ 1h40 para os 2 anos |
| **Massive pago** (Starter US$ 29, Developer US$ 79, Advanced US$ 199/mês) | Depende do plano (profundidade não confirmada nas páginas públicas; arquivos diários "flat files" só nos pagos) | Os do período | Mesmo grouped daily (sem mudar código) ou um arquivo por dia | US$ 29–199/mês | Reaproveita o cliente da parte 1 |
| **EODHD** Historian ("EOD Historical All-World") | Sim (30+ anos) | **Sim** — lista própria (`exchange-symbol-list/US?delisted=1`) e EOD até a data de saída | Um pedido por ticker (série inteira), com fechamento não ajustado e ajustado | US$ 19,99/mês (uso pessoal); 100 mil pedidos/dia | ~18 mil tickers cabem num dia; dá para assinar um mês, fazer a carga única e cancelar |
| **Tiingo** gratuito | Sim | Parcial | Um pedido por ticker | US$ 0 | Limite de símbolos por mês: serve só para poucos ativos (ex.: os da carteira), não para o catálogo |
| **Stooq** (pacote em zip) | Sim (alguns 30+ anos) | Não | Download manual com captcha, sem API | US$ 0 | Fechamento **ajustado** (a série do Activest é não ajustada) e sem deslistados |

Fontes: [Massive — plano gratuito](https://massive.com/knowledge-base/article/on-the-free-basic-plan-when-can-i-pull-the-previous-trading-days-data),
[Massive — planos](https://massive.com/knowledge-base/article/what-are-the-different-polygon-subscriptions-i-can-use),
[preços Massive (apicostcalc)](https://apicostcalc.com/blog/polygon-massive-rebrand-api-pricing.html),
[EODHD — planos](https://eodhd.com/pricing), [EODHD — deslistados](https://eodhd.com/financial-apis/delisted-stock-companies-data-2),
[Stooq (QuantStart)](https://www.quantstart.com/articles/an-introduction-to-stooq-pricing-data/),
[Tiingo](https://www.tiingo.com/documentation/general).

**Conclusão do Discovery:** o Massive gratuito resolve só os 2 últimos anos. Para chegar a 2010 é preciso uma fonte
paga (EODHD ou Massive pago) ou aceitar a série curta.

## 3. Decisões em aberto (Discovery)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| D1 | Fonte de 2010 até nov/2024 | (a) **EODHD Historian por um mês** (US$ 19,99): carga única de todo o catálogo, vivos e deslistados, arquivos guardados em `carga/data` e no bucket; depois cancela; (b) Massive pago (US$ 29–199/mês, profundidade a confirmar); (c) sem fonte paga: só os 2 anos do Massive gratuito (+ Tiingo gratuito para os ativos em carteira); (d) Stooq | **(a)** — o menor custo, a única com deslistados, série não ajustada |
| D2 | Os 2 anos do Massive gratuito | (a) carregar já pelo grouped daily (~500 pedidos, ~1h40), independente da D1; (b) só depois da D1 | **(a)** — sem custo, mesmo cliente da parte 1 |
| D3 | Deslistados (5.906) | (a) entram, com a série até a data de saída; (b) só os vivos | **(a)** — o lançamento retroativo precisa deles (depende da D1 = a) |
| D4 | Desdobramentos/grupamentos do exterior (necessários para o gráfico ajustado da tela de Ativos) | (a) neste escopo, pela mesma fonte da série; (b) na parte 3, junto com os proventos | **(b)** — eventos (proventos e desdobramentos) ficam juntos; a série gravada é não ajustada, como a da B3 |
| D5 | Início | (a) 01/01/2010, como a B3; (b) outro | **(a)** |
| D6 | Prova antes do Impact | (a) EODHD com a chave de demonstração gratuita (só alguns tickers, ex.: AAPL) para conferir formato, não ajustado e deslistado; (b) não | **(a)** — sem custo |

### 3.1 Respostas do Guilherme (01/11/2026)

> "Executemos agora neste plano o retroativo de 2 anos (sempre ter em mente que os registros não fiquem só na base,
> mas nos arquivos) e o retroativo até 2010 fica como pendência."

| # | Resposta |
|---|---|
| D1 | **(c) por ora**: sem fonte paga neste escopo; o retroativo até 2010 vira pendência (PENDENCIAS 44.3, com as fontes da 2.2 — EODHD como a recomendada) |
| D2 | **(a)**: carregar já os 2 anos do Massive gratuito, com os arquivos de cada pregão guardados em `carga/data/massive/grouped` e enviados ao bucket (não só a base) |
| D3 | Dentro dos 2 anos, os deslistados entram até a data de saída (o pregão do dia casa com o papel listado nele) — confirmado pelo Guilherme em 01/11/2026 |
| D4 | Desdobramentos ficam para a parte 3 (eventos) — não tratado neste escopo |
| D5, D6 | Não se aplicam agora (dependiam da fonte até 2010) |

## 4. Impact e Planning (resumido — execução pedida diretamente)

| Peça | O que muda |
|---|---|
| `V20__foreign_history_quotes_task.sql` | Tarefa `FOREIGN_HISTORY_QUOTES` (só Run; `FROM_DATE` vazio = 2 anos atrás, `TO_DATE` vazio = ontem) |
| `ForeignDailyQuotesService` | `importHistory`: dia fora do plano contado (`daysOutOfPlan`) e pulado; catálogo com os deslistados de saída a partir do início do período e o papel do dia escolhido por `listedOn` (deslistado até a saída, senão o vivo); lista dos sem cotação só do pregão mais recente no log |
| `ForeignHistoryQuotesTask`, `AdminController` (`/foreign-history-quotes`), `admin.mjs` | Disparo da carga |
| Arquivos | O `MassiveClient` já guarda cada pregão em `carga/data/massive/grouped/AAAA/`; ao fim, `data push` (a pasta já está no mínimo da carga) |
| Testes | `ForeignDailyQuotesIT` (ticker reaproveitado pela data, dia fora do plano), `SchemaTest`/`TaskControllerIT` (33 tarefas) |

**Critério de aceite:** a tarefa carrega os pregões de nov/2024 até ontem (~500), com o arquivo de cada um em disco e no
bucket; a cobertura de cada dia sai no log.

## 6. Resultado da carga (01/11/2026)

| Item | Resultado |
|---|---|
| Execução 161 (`FOREIGN_HISTORY_QUOTES`, Run sem datas) | SUCCEEDED, 15:26 → 17:11 (1h45) |
| Período | 01/11/2024 a 31/10/2026: 521 dias úteis, **500 pregões** (os outros 21 sem pregão: feriados americanos); 0 dias fora do plano |
| Gravado | **5.234.575** fechamentos `MASSIVE` (5.246.583 no total com a parte 1) |
| Deslistados | **1.027** com série até a data de saída; nenhum com pregão depois dela |
| Cobertura por dia | ~9,3 mil papéis casados em nov/2024, ~9,8 mil em jun/2025, 12.009 em 30/10/2026 (papéis novos entram no catálogo ao longo do tempo) |
| Conferência | IAU: 500 pregões, US$ 51,62 em 01/11/2024 e US$ 78,25 em 13/10/2026 (dia da compra a US$ 78,268) |
| Arquivos | 500 `.json.gz` em `carga/data/massive/grouped/2024–2026/` (174 MB), enviados ao bucket (`data push`) |

## 7. Andamento

| Data | Etapa | O que aconteceu |
|---|---|---|
| 01/11/2026 | Discovery | Escopo aberto; limite do Massive gratuito (2 anos) confirmado na prova da parte 1; fontes avaliadas; D1–D6 |
| 01/11/2026 | Implementation | Respostas do Guilherme (3.1); V20, `importHistory`, tarefa, rota e atalho, testes |
| 01/11/2026 | Implementation | D3 confirmada; carga dos 2 anos executada (seção 6) |
| 01/11/2026 | Testing | `mvn clean verify` verde (com os testes novos: ticker reaproveitado pela data, dia fora do plano, 33 tarefas) |
| 01/11/2026 | Review | Documentação: `Claude.md`, `README.md`, `SCHEDULE_TASKS` [A21], `ADM_TASKS` [25], PENDENCIAS 44.3; `data push` da pasta `massive/` concluído |
| 01/11/2026 | Commit | Alterações conferidas (só leitura); mensagem sugerida ao Guilherme |
| 01/11/2026 | Commit | Guilherme comitou; escopo encerrado |
