# Plano — Rotina de atualização de preços diários dos ativos internacionais Activest

> Aberto em 31/10/2026 (POR ESCOPO, a partir das dúvidas 1–3 do mesmo dia). **Escopo encerrado em 01/11/2026** (commit e push feitos pelo Guilherme). Parte 1 de 3 da branch; a parte 2 é o
> `PLANO_PRECOS_HISTORICOS_INTERNACIONAIS.md`.
> O `PLANO_ATIVOS_INTERNACIONAIS.md` foi **desconsiderado por completo** pelo Guilherme para este escopo
> (o levantamento de preço do Anexo A dele não é base aqui).

## 1. Objetivo

Rotina diária que preenche o preço dos ativos internacionais **vivos** (Stock, REIT e ETF internacional) pela
chamada **grouped daily** do Massive (ex-Polygon): uma chamada devolve o fechamento de todos os papéis dos EUA num
pregão.

**Critério de sucesso do escopo (Guilherme, 31/10/2026):** a implementação culmina na execução da rotina que preenche
todos ou a maioria dos ativos vivos para o pregão de **30/10/2026**. Se a execução falhar por problema impeditivo da
chamada grouped daily do Massive, o escopo é um insucesso.

## 2. Decisões já tomadas (31/10/2026)

| # | Decisão |
|---|---|
| A1 | Fonte: Massive grouped daily, plano gratuito (Basic), de uso pessoal; fonte paga com redistribuição só quando houver outros usuários |
| A2 | Deslistados (5.906) fora da rotina diária: entram só no problema do histórico (outro escopo) |
| A3 | Prova antes de implementar (cobertura, faltantes, horário de liberação, limite do histórico, conferência de fechamentos) |
| A4 | Histórico e proventos ficam para depois do preço diário funcionando (outros escopos) |
| A5 | Chave em `MASSIVE_API_KEY` no `.env` da raiz (colocada pelo Guilherme) |

## 3. Discovery — o que existe hoje

### 3.1 Catálogo (banco de desenvolvimento, 31/10/2026)

| Tipo | Vivos | Deslistados | Total | Com preço | Com provento |
|---|---|---|---|---|---|
| STOCK | 5.862 | 5.040 | 10.902 | 0 | 0 |
| REIT | 183 | 107 | 290 | 0 | 0 |
| ETF_INTERNACIONAL | 6.135 | 759 | 6.894 | 0 | 0 |
| **Total** | **12.180** | **5.906** | **18.086** | **0** | **0** |

- `asset_types.price_source` dos três tipos é `MANUAL` (enum `PriceSource`: "ativos dos EUA enquanto não houver um
  cliente para eles"). `assets.price_source_ref` vazio em todos.
- Moeda `USD`, país `US`; identidade e bolsa em `foreign_listings` (1:1).
- **378 vivos com ticker fora de `[A-Z0-9]`**: preferenciais (`ABR-E`, `AGM-D`) e classes (`AGM.A`, `BRK.B`). O
  Massive escreve esses papéis de outro jeito (ponto a conferir na prova).

### 3.2 Como os preços são gravados hoje

| Peça | Comportamento |
|---|---|
| `asset_price_history` | Um pregão por ativo (`uk_price_history_day`), OHLC + volume, `source` com check (`BRAPI`, `COINGECKO`, `B3_COTAHIST`, `TESOURO`, `BINANCE`, `CVM`) |
| `assets.current_price` / `price_updated_at` | Preço atual do ativo, lido pelas telas |
| `B3DailyQuotesTask` + `HistoricalQuotesImportService` | Modelo mais próximo: um arquivo por pregão com o mercado inteiro, casa pelo ticker, upsert em lote (5.000), do pregão seguinte ao último gravado até hoje, lacuna > 31 dias usa outro caminho, `needsRecovery` quando falta o último dia útil, `stoppable`, avisa a carteira (`CatalogChange.FECHAMENTO`) |
| `ExchangeRatesTask` | `oncePerDay`: no máximo uma execução automática por dia (decisão do escopo de moedas) |
| Tarefas | Cadastradas por migration (`task_types`, `task_type_attributes`, `tasks`, `task_attributes`), como a V16 |
| `.env` → adm-api | Por variável no `docker-compose.yml` (ex.: `OPENFIGI_API_KEY`); `MASSIVE_API_KEY` ainda não é repassada |

### 3.3 Massive (a confirmar na prova)

- Rota: `GET /v2/aggs/grouped/locale/us/market/stocks/{data}`; resposta com `T` (ticker), `o`, `h`, `l`, `c`, `v`, `t`.
- Plano Basic: 5 chamadas/min, ~2 anos de histórico, pregão disponível só depois do fechamento.
- `api.massive.com` responde da máquina do Guilherme (401 sem chave, 31/10/2026).


## 3.4 Prova (D1, 31/10/2026 — 4 chamadas, nada gravado)

| Item | Resultado |
|---|---|
| Pregão 30/10/2026 (`adjusted=false`) | HTTP 200, 12.624 papéis, 1,37 MB, 1,3 s |
| Casamento direto pelo ticker | 11.662 / 12.180 (95,7%) |
| Com a regra de preferencial (`ABR-E` → `ABRpE`; a classe `BRK.B` já vem igual) | **12.008 / 12.180 (98,6%)** — Stock 5.692/5.862, ETF 6.134/6.135, REIT 182/183 |
| Os 172 que não vieram | Quase todos Stocks sem negócio no dia (cascas de SPAC, bancos pequenos), 13 preferenciais/classes (`AGM.A`, `AHT-D`, `PCG-C`…), 1 ETF (BTCK) e 1 REIT (IOR) |
| Massive sem o catálogo | 962 papéis (warrants `.WS`, preferenciais que o catálogo não tem…) — ignorados |
| Histórico do plano gratuito | 01/11/2024 → 200; 30/10/2024 → 403 `NOT_AUTHORIZED`: **2 anos corridos** |
| Liberação do pregão (base de conhecimento do Massive) | Logo depois da **meia-noite ET** (01:00 BRT no horário de verão americano, 02:00 BRT no horário padrão); antes disso, 403. Limite 5/min, 429 ao estourar |
| Volume | Vem com casas decimais (fracionário dos EUA): arredondar para `bigint` |
| Fechamentos de exemplo (USD) | IAU 72,92 · AAPL 344,63 · SPY 755,72 · O 56,93 · BRK.B 542,49 · JPM 337,04 · VNQ 93,94 — conferência contra fonte pública com o Guilherme |

**Ajustes das recomendações pela prova:** D4 → 03:00 BRT, terça a sábado (folga nos dois horários americanos); D5 → regra
`-` → `p`; D8 → 90% confirmado (a prova deu 98,6%). Volume arredondado (HALF_UP) e registrado como pendência
(PENDENCIAS 44.1), por decisão do Guilherme em 31/10/2026.

## 4. Decisões do Discovery

**31/10/2026 — Guilherme aprovou D1–D10 pela recomendação** ("De acordo com todas as recomendações").

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| D1 | Rodar a prova (A3) agora, ainda no Discovery, sem gravar nada | (a) sim: 1 chamada do pregão de 30/10 + 1–2 para o limite do histórico; (b) só depois do Planning | **(a)** — os números dela decidem D4, D5 e D8 |
| D2 | Onde gravar | (a) `asset_price_history` com `source = MASSIVE` (check ampliado) + `current_price`/`price_updated_at`; (b) só `current_price` | **(a)** — igual à B3, e já deixa a série começar a ser montada |
| D3 | Roteamento da fonte | (a) valor novo `MASSIVE` no `PriceSource` e nos três tipos (sai do `MANUAL`); (b) manter `MANUAL` | **(a)** — o roteamento é dado no banco, como o enum pede |
| D4 | Agenda | (a) terça a sábado de manhã (horário definido pela prova), pregão anterior, recuperação na subida e `oncePerDay`; (b) noite do próprio dia | **(a)** — o gratuito só libera depois do fechamento |
| D5 | Ticker com grafia diferente (378) | (a) conversão por regra no código (`BRK.B` ↔ grafia do Massive); (b) coluna nova com o ticker da fonte | **(a)** — sem coluna nova, se a prova mostrar regra fixa |
| D6 | Vivo que não veio no dia | (a) mantém o último preço; resumo da execução com a lista; aviso ao admin só com falha da chamada ou cobertura abaixo do limite da D8; (b) aviso sempre | **(a)** |
| D7 | Dias atrasados (sistema desligado) | (a) um pedido por pregão faltante, do último gravado até ontem, até 31 dias, no ritmo de 5/min; (b) só o último pregão | **(a)** — mesmo desenho da B3 |
| D8 | Limite de "maioria" para o aceite do pregão de 30/10 | (a) ≥ 90% dos 12.180 vivos; (b) > 50%; (c) outro | **(a)**, ajustado pelo número da prova |
| D9 | Desenho do cliente | (a) interface genérica de fonte de preço do exterior, com o Massive como implementação; (b) cliente direto | **(a)** — mesmo princípio das integrações |
| D10 | Aviso à carteira | (a) publicar `CatalogChange.FECHAMENTO` com os ativos atualizados (alerta de preço, posições); (b) não | **(a)** |

## 5. Impact — alcance e riscos

### 5.1 Componentes afetados

| Onde | O que muda | Risco se esquecido |
|---|---|---|
| Banco — migration nova | `MASSIVE` nos checks `ck_asset_types_price_source` e `ck_price_history_source`; `price_source = MASSIVE` em STOCK, REIT e ETF_INTERNACIONAL; tarefa nova (`task_types`, atributos, `tasks`) como a V16 | Gravação recusada pelo check |
| `activest-core` — `PriceSource`, `PriceHistorySource` | Valor `MASSIVE` nos dois enums | Leitura do banco quebra (enum desconhecido) |
| `activest-adm-api` — cliente novo | Interface genérica de fonte de preço do exterior (D9) + implementação Massive (grouped daily, 5/min, 429/403/401) | — |
| `activest-adm-api` — serviço + tarefa nova | Casamento pelo ticker com a regra `-` → `p` (D5), upsert em lote em `asset_price_history`, `current_price`/`price_updated_at`, recuperação, `oncePerDay`, `stoppable`, `CatalogChange.FECHAMENTO` (D10) | — |
| `ForeignCatalogService` (linha 478) | Os ativos em carteira protegidos da saída automática são buscados por `PriceSource.MANUAL`: passa a `MASSIVE` | **Alto**: um ativo internacional em carteira (ex.: IAU) deixaria de ser protegido na rotina do catálogo |
| `GoalFactsLoader` (linha 111) | Hoje marca o internacional como "sem preço" por ser `MANUAL`; com `MASSIVE` as Metas passam a usar o preço | Benefício esperado; conferir o teste das Metas |
| `TransactionService.importTransaction` (linha 356) | Aceita o internacional pela lista `FOREIGN_LISTED`, não pelo `MANUAL`: segue funcionando; só o comentário fica velho | Nenhum |
| `docker-compose.yml` / `.env.example` | `MASSIVE_API_KEY` repassada à adm-api e documentada | Tarefa sem chave (401) |
| App-api — Carteira, Rentabilidade, Metas | Já convertem o preço em USD pela PTAX (`PortfolioService` 184, `PortfolioTimelineBuilder` 290): **sem mudança de código** | — |
| Telas | Nenhuma mudança: a Carteira e a tela de Ativos passam a mostrar o preço que hoje está vazio | — |
| Docs | `Claude.md`, `docs/SCHEDULE_TASKS.md`, `docs/ADM_TASKS.md`, `docs/ACTIVEST_DB.md`, `README.md`, PENDENCIAS | — |

### 5.2 Riscos e dependências

| Risco | Tratamento |
|---|---|
| Plano gratuito muda (limite, histórico, liberação) | A interface genérica (D9) troca a fonte sem mexer no serviço; a tarefa avisa o admin |
| Rodar antes da liberação (403 no dia corrente) | Dia não liberado não é falha: fica para a rodada seguinte (I5) |
| Feriado americano (resposta vazia) | Sem pregão, não é falha (I4) |
| Estourar 5/min na recuperação de vários dias | Espera de 12 s entre pedidos e nova tentativa no 429 |
| Recuperação de um dia antigo sobrescrever o preço atual | `current_price` só muda se a data for mais nova que a do preço gravado (I2) |
| Licença de uso pessoal | Registrada na A1; fonte paga quando houver outros usuários |
| Câmbio do dia | Já tratado pelo escopo de moedas (último PTAX carregado) |

### 5.3 Decisões do Impact

**31/10/2026 — Guilherme aprovou I1–I7 pela recomendação** ("ok para tudo").

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| I1 | Guardar o arquivo baixado | (a) `carga/data/massive/grouped/AAAA/AAAA-MM-DD.json.gz` (~300 KB/dia), como os arquivos da B3; (b) não guardar | **(a)** — regra de guardar o que se baixa em `carga/data` |
| I2 | Preço atual numa recuperação de dia antigo | (a) só atualiza `current_price` se o pregão for mais novo que o gravado; (b) sempre | **(a)** |
| I3 | Aviso de fonte desatualizada | (a) `FONTE_DESATUALIZADA` ao admin, mesma regra do câmbio e do Tesouro, resolvido quando o pregão chega; (b) não | **(a)** |
| I4 | Dia sem pregão nos EUA (feriado) | (a) resposta vazia = sem pregão, sem aviso; (b) calendário de feriados no código | **(a)** |
| I5 | Erros da chamada | (a) 403 do dia corrente = não liberado (próxima rodada); 403 fora do histórico = erro de entrada; 401 = para a rodada e avisa o admin; 429 = espera e repete; (b) todo erro para | **(a)** |
| I6 | Execução manual com período | (a) atributos `FROM_DATE`/`TO_DATE` como a `B3_DAILY_QUOTES` (até 31 dias, dentro dos 2 anos); é como se roda o aceite de 30/10; (b) só o último pregão | **(a)** |
| I7 | Nome da tarefa | (a) `FOREIGN_DAILY_QUOTES` (par da `B3_DAILY_QUOTES`); (b) `MASSIVE_DAILY_QUOTES` | **(a)** — o nome não amarra a fonte |

## 6. Planning — solução

### 6.1 Peças

| Módulo | Peça | O que faz |
|---|---|---|
| core (migration) | `V19__foreign_daily_quotes.sql` | `MASSIVE` em `ck_asset_types_price_source` e `ck_price_history_source`; `price_source = 'MASSIVE'` em STOCK, REIT e ETF_INTERNACIONAL; tarefa `FOREIGN_DAILY_QUOTES` (tipo, atributos `FROM_DATE`/`TO_DATE` opcionais, cron `0 0 3 * * TUE-SAT`, `run_on_startup = true`) |
| core | `PriceSource.MASSIVE`, `PriceHistorySource.MASSIVE` | Valor novo nos dois enums |
| adm-api | `ForeignQuotesProvider` (interface, D9) | `DayQuotes day(LocalDate)`: situação `OK`, `NOT_RELEASED` (403 do dia corrente), `NO_SESSION` (resposta vazia, I4) + lista de `ForeignDailyQuote(ticker, open, high, low, close, volume)` |
| adm-api | `MassiveClient` (implementa a interface) | `GET /v2/aggs/grouped/locale/us/market/stocks/{data}?adjusted=false`; chave de `activest.massive.api-key` (`MASSIVE_API_KEY`); 12 s entre pedidos; 429 → espera 60 s e repete (até 3 vezes); 401 → `MassiveAuthException`; 403 fora do histórico → erro de entrada (I5); guarda o corpo em `carga/data/massive/grouped/AAAA/AAAA-MM-DD.json.gz` (I1) e reaproveita o arquivo de um dia já baixado |
| adm-api | `ForeignDailyQuotesService` | Mapa ticker → ativo **vivo** dos três tipos, com a conversão `-` → `p` (D5); upsert em lote (`source = MASSIVE`, volume `HALF_UP`, PENDENCIAS 44.1); `current_price`/`price_updated_at` só com pregão mais novo (I2); `CatalogChange.FECHAMENTO` com os ativos atualizados (D10); resultado com gravados, não encontrados por tipo e a lista dos não encontrados; `stale()` = falta o pregão do último dia útil antes de hoje; aviso `FONTE_DESATUALIZADA` acima de 2 dias úteis (mesma regra do Tesouro, I3) e `FONTE_INDISPONIVEL` no 401 / falha de rede |
| adm-api | `ForeignDailyQuotesTask` | Sem datas: do pregão seguinte ao último `MASSIVE` gravado até ontem (no máximo 31 dias; sem nada gravado, só ontem — D7). Com datas: o período (até 31 dias, dentro dos 2 anos — I6). `oncePerDay`, `needsRecovery` = `stale()`, `stoppable` |
| adm-api | `ForeignCatalogService` (linha 478) | Proteção dos ativos em carteira passa de `PriceSource.MANUAL` para `PriceSource.MASSIVE` |
| app-api | `TransactionService` (comentário da linha 354) | Texto atualizado (o exterior deixa de ser `MANUAL`) |
| adm-api | `AdminController` | Rota `POST /api/v1/admin/foreign-daily-quotes[?from&to]` para o atalho do `admin.mjs` |
| config | `docker-compose.yml`, `.env.example` | `foreign.massive.url` / `foreign.massive.api-key` com padrão no próprio cliente (como o OpenFIGI, sem entrada no `application.yml`); `MASSIVE_API_KEY` repassada à adm-api e documentada |
| script | `carga/scripts/admin.mjs` | Atalho `foreign-daily-quotes [--from AAAA-MM-DD --to AAAA-MM-DD]` como os das outras tarefas |

### 6.2 Critérios de aceitação

| # | Critério |
|---|---|
| CA1 | `mvn clean verify` verde (testes novos: cliente com respostas gravadas da prova — OK, vazio, 403, 401, 429 —, conversão de ticker, serviço com o preço atual só para pregão mais novo e volume arredondado, tarefa com os períodos) |
| CA2 | **Aceite do escopo:** a tarefa disparada à mão para 30/10/2026 fala com o Massive sem erro e grava fechamento (`source = MASSIVE`) e preço atual em **≥ 90% dos 12.180 vivos** (a prova deu 98,6%) |
| CA3 | O arquivo `carga/data/massive/grouped/2026/2026-10-30.json.gz` existe; rodar de novo não vai à rede e não muda nada (idempotente) |
| CA4 | Nenhum deslistado recebe preço; os ativos da B3 não são tocados |
| CA5 | Na Carteira do Guilherme o IAU aparece com o preço em USD e o valor em R$ pela PTAX; a tela de Ativos de um Stock mostra o preço |
| CA6 | Com a adm-api reiniciada e o pregão de ontem faltando, a recuperação na subida roda uma vez só no dia |

### 6.3 Etapas

| # | Etapa | Conteúdo |
|---|---|---|
| E1 | Banco e configuração | V19, enums, `application.yml`, `docker-compose.yml`, `.env.example` |
| E2 | Fonte | `ForeignQuotesProvider`, `MassiveClient`, testes do cliente |
| E3 | Rotina | `ForeignDailyQuotesService`, `ForeignDailyQuotesTask`, ajuste do `ForeignCatalogService`, atalho do `admin.mjs`, testes |
| E4 | Testing | Bateria completa (CA1), subida do ambiente, execução do aceite de 30/10 (CA2–CA4), recuperação (CA6) |
| E5 | Review e documentação | Revisão do código; `Claude.md`, `README.md`, `docs/SCHEDULE_TASKS.md`, `docs/ADM_TASKS.md`, `docs/ACTIVEST_DB.md`, PENDENCIAS, andamento; `data push` da pasta `massive/` |

### 6.4 Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| E1–E3, testes e bateria | ✅ | |
| Subida do ambiente e execução do aceite de 30/10 | ✅ | |
| Conferência no navegador (CA5) e fechamentos de exemplo contra uma fonte pública | | ✅ |
| `data push` da pasta `massive/` para o bucket | ✅ | |
| Git (branch, commit, PR) | sugere a mensagem | ✅ |

## 7. Resultado (01/11/2026)

| Critério | Resultado |
|---|---|
| CA1 | Bateria verde depois dos ajustes da conferência (01/11/2026): core 255, integration 116, adm-api 165 + 240 IT, app-api 103 + 311 IT; front tsc + lint + vitest 156; testes novos: `MassiveClientTest` (6), `ForeignDailyQuotesTaskTest` (4), `ForeignDailyQuotesIT` (4); `SchemaTest` e `TaskControllerIT` com a V19 (32 tarefas) |
| **CA2** | **Execução 147 (Run manual, `FROM_DATE = 2026-10-30`): SUCCEEDED — 12.624 papéis na fonte, 12.008 de 12.180 vivos (98,6%) com fechamento e preço atual; faltaram 170 Stocks, 1 REIT, 1 ETF (PENDENCIAS 44.2)** |
| CA3 | `carga/data/massive/grouped/2026/2026-10-30.json.gz` (408 KB); execução 148 relida do disco, sem rede: 0 fechamentos e 0 preços alterados |
| CA4 | 0 deslistados com preço ou histórico `MASSIVE`; 0 linhas `MASSIVE` em ativos de outros tipos |
| CA5 | A carteira recebeu o "catálogo mudou" (FECHAMENTO, 12.008 ativos — 1 posição, o IAU). Conferência no navegador feita pelo Guilherme (01/11/2026): OK, inclusive VARIAÇÃO, segundas linhas em dólar e Totais |
| CA6 | `stale()` em dia depois do aceite (último pregão 30/10 = último dia útil antes de hoje em Nova York); a regra de uma rodada automática por dia é a mesma da `EXCHANGE_RATES` (`oncePerDay`) |

Fechamentos gravados (USD): IAU 72,92 · AAPL 344,63 · SPY 755,72 · O 56,93 · BRK.B 542,49 · JPM 337,04 · VNQ 93,94 ·
ABR-E 9,21.

**01/11/2026 — Guilherme aprovou R2–R5 pela recomendação** ("Aprovado todas recomendações") e pediu R6.

## 7.1 Conferência do Guilherme na Carteira (01/11/2026)

| # | Apontamento | Análise | Proposta | Recomendação |
|---|---|---|---|---|
| R1 | IAU "valorizado" com o preço abaixo do da compra | Não é erro: compra em 13/10 a US$ 78,268 com PTAX 5,0096 (R$ 392,09); hoje US$ 72,92 (−6,8% em dólar) com PTAX 5,4212 (+8,2%) = R$ 395,31 (+0,82% em reais). O dólar subiu mais do que o ETF caiu | Mostrar o dólar (R2) deixa a leitura clara | — |
| R2 | Valor em dólar na 2ª linha de Preço médio, Valor inicial, Valor atual, Proventos e Valorização dos internacionais | Hoje só o Preço atual tem a 2ª linha (`ForeignQuote`). A transação guarda o original em dólar (`original_price_per_share`, `original_total_amount`) | Preço médio e Valor inicial pelo original da compra; Valor atual = cotas × preço em dólar; Valorização em dólar = atual − inicial em dólar; Proventos em dólar quando houver; linha de Totais só em reais | **Sim, como proposto** |
| R3 | BTC sem casas decimais | O preço diário de cripto vem do `simple/price` da CoinGecko sem `precision`: para valores altos ela arredonda para inteiro (R$ 421.583,00 em 31/10; o histórico, de outra rota, tem casas) | Pedir `precision=full` no `CoinGeckoClient` | **Sim** |
| R4 | Dólar na 2ª linha das criptomoedas | Cripto é cotada em reais (`currency_code = BRL`) pela CoinGecko | (a) converter reais → dólar pela PTAX do dia; (b) pedir também `usd` à CoinGecko e guardar o preço em dólar | **(a)** — sem fonte nova nem coluna nova; mesma PTAX dos internacionais |
| R6 | (01/11/2026, pedido do Guilherme na conferência) Valorização com informação demais | — | Coluna nova **VARIAÇÃO** depois da VALORIZAÇÃO com a % (em reais e, embaixo, na moeda); VALORIZAÇÃO fica só com o valor (reais e, embaixo, a moeda); Totais com a 2ª linha na moeda no exterior e na cripto | Feito |
| R5 | Onde entra | R2–R4 são telas e fonte fora do que este escopo planejou | (a) neste escopo, como ajuste da Review; (b) escopo novo | **(a)** — o aceite deste escopo já passou; os ajustes são pequenos e da mesma conferência |

## 8. Andamento

| Data | Etapa | O que aconteceu |
|---|---|---|
| 31/10/2026 | Discovery | Escopo aberto; catálogo medido; peças de preço da B3, tarefas e moedas lidas; plano criado com D1–D10 |
| 31/10/2026 | Discovery | D1–D10 aprovadas; prova feita (98,6% dos vivos no pregão de 30/10); Discovery concluído |
| 31/10/2026 | Impact | Volume arredondado com pendência (44.1); componentes e riscos levantados; decisões I1–I7 |
| 31/10/2026 | Planning | I1–I7 aprovadas; solução, critérios de aceitação (CA1–CA6), etapas E1–E5 e "Quem faz" escritos |
| 01/11/2026 | Implementation | Ordem do Guilherme: executar E1–E5 sem interrupções salvo problema crítico. V19, enums, `ForeignQuotesProvider`, `MassiveClient`, `ForeignDailyQuotesService`, `ForeignDailyQuotesTask`, rota e atalho, ajuste do `ForeignCatalogService` |
| 01/11/2026 | Testing | Testes novos verdes; V19 aplicada no banco de desenvolvimento (adm-api e app-api recriadas); aceite de 30/10: 98,6% (seção 7) |
| 01/11/2026 | Review | Aviso à carteira movido para um `finally` (a rodada que para no meio ainda avisa o que gravou); documentação: `Claude.md`, `README.md`, `SCHEDULE_TASKS` [A20], `ADM_TASKS` [24], `ACTIVEST_DB`, PENDENCIAS 44.1–44.4 |
| 01/11/2026 | Review | Conferência na Carteira: R2 (`ForeignValuesService`, campo `foreignValues` no DTO da Carteira, segunda linha na moeda), R3 (`precision=full` na CoinGecko), R4 (cripto em dólar pela PTAX), R6 (coluna VARIAÇÃO, Totais na moeda); testes: `ForeignValuesServiceTest` (3), `foreignTotals` (2); front tsc + lint + vitest (156) verdes |
| 01/11/2026 | Review | Guilherme conferiu a Carteira (OK) e rodou o fechamento de cripto à mão: BTC com casas decimais (R3 OK) |
| 01/11/2026 | Testing | Bateria completa de novo com os ajustes: a 1ª tentativa perdeu o Docker no meio dos IT da adm-api ("Could not find a valid Docker environment", sem asserção falhando); refeita a adm-api e a app-api: verde |
| 01/11/2026 | Commit | Alterações conferidas (`git status`/`git diff`, só leitura); mensagem sugerida ao Guilherme |
| 01/11/2026 | Commit | Guilherme criou a branch, comitou e deu push; escopo encerrado |
