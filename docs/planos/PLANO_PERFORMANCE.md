# Activest — Plano de performance das listagens (Ativos e Preço Teto)

> Fase: **Encerrado em 29/10/2026** — as 7 etapas feitas; conferido e aprovado pelo Guilherme no navegador ("Aprovado,
> ficou mais rápido") e escopo declarado encerrado por ele, que faz as operações de git (commit e PR da
> `TRACK_01-performance` para a `develop`). D1–D8 respondidas. Nenhum código foi alterado.
> Origem: pedido do Guilherme em 29/10/2026 (ESCOPO "Performance Activest"): a **listagem de ativos** e a
> **listagem de preço médio** demoram mais que as outras telas; saber se há problema sistêmico e melhorar o tempo
> de espera das duas requisições.
> Objetivo: achar a causa (não o sintoma) e baixar o tempo das duas listagens sem mudar nenhum número na tela.

---

## D. Discovery

### D.1 O que cada tela chama

| Tela | Front | Endpoint (app-api) | Backend |
|---|---|---|---|
| Ativos, sem ticker (aba Ações / FIIs) | `IssuerListingTable` → `indicatorsApi.listing` (`staleTime` 10 min) | `GET /api/v1/assets/indicators?kind=COMPANY\|FII` | `IndicatorController.listing` → `IssuerListingService.list` → `IndicatorService.currentAll` |
| Preço Teto (colunas "Preço médio") | `CeilingPricesPage` → `ceilingPricesApi.list` | `GET /api/v1/ceiling-prices` | `CeilingPriceService.list` → `CeilingPriceDefaults.of` por linha + `averagePrices` → `PortfolioService.getPortfolioAssets` |

"Listagem de preço médio" foi lida como a **lista do Preço Teto**, a única listagem com "Preço médio" em destaque
(as três comparações, D13 do `PLANO_PRECO_TETO.md`) e que, como mostra D.3, monta a Carteira inteira só para ler o
preço médio. A Carteira também tem a coluna PREÇO MÉDIO e entra na solução pela mesma causa (D1).

### D.2 Medições (pilha Docker do dev, 29/10/2026, 5 chamadas seguidas por endpoint, usuário do `.env`)

| Endpoint | Resposta | 1ª chamada | Seguintes | Consultas SQL por chamada |
|---|---|---|---|---|
| `/assets/indicators?kind=COMPANY` | 785 linhas, 301 KB | **9.320 ms** | **930 – 1.440 ms** | 146 (884 ms no banco) |
| `/assets/indicators?kind=FII` | 347 linhas, 238 KB | 917 ms | 72 – 89 ms | — |
| `/ceiling-prices` | **1 cálculo**, 0,7 KB | 1.030 ms | **265 – 490 ms** | **441** (97 ms no banco) |
| `/portfolio/assets` (Carteira) | 49 posições na tabela, 33 KB | 168 ms | 183 – 212 ms | ~430 |
| `/portfolio/`, `/dividends/`, `/assets/types` | — | — | 5 – 110 ms | — |

Comparação: as outras listagens respondem em 5 – 110 ms. Ações demora mais de 10× o tempo de FIIs, com pouco mais do
dobro de linhas.

Como as consultas foram contadas: `log_min_duration_statement = 0` ligado por alguns segundos no PostgreSQL do dev
(`ALTER SYSTEM` + `pg_reload_conf()`), uma chamada de cada endpoint entre marcadores, e a configuração **voltada ao
padrão** (`ALTER SYSTEM RESET`, conferido: `-1`). O teste do índice (D.3, causa 1) foi feito dentro de uma transação
desfeita com `ROLLBACK`. Nada ficou gravado no banco.

### D.3 Achados — a causa é sistêmica

**Causa 1 (sistêmica): o catálogo cresceu dezenas de vezes em três dias e a busca por ticker virou leitura da tabela
inteira.** `assets` tem hoje **100.185 linhas, 20 MB**: 54.784 fundos da CVM (V12, 27/10) e 18.086 Stocks/ETFs/REITs
dos EUA (V15–V17, 29/10) somaram-se aos ~2,6 mil ativos de B3, cripto, Tesouro e renda fixa. Várias consultas procuram o ativo por
`upper(a.ticker) = ?`, e **não há índice em `upper(ticker)`** (só `uk_assets_ticker_type (ticker, asset_type_id,
delisted_on)`, que não serve a `upper(...)`). Cada uma passou a ler ~75 mil linhas: **18 – 22 ms por consulta**. Com
um índice funcional, a mesma consulta leva **0,2 – 0,3 ms** (medido com o índice criado e desfeito numa transação).

Onde há busca por `upper(ticker)` no código de produção: `IndicatorService` (2: indicadores, histórico e listagem),
`CeilingPriceDefaults` (1), `AssetPriceHistoryService` (2: o gráfico de cotação da página do ativo),
`PortfolioTimelineBuilder` (1, com `IN`: Rentabilidade) na app-api; `ForeignDelistedService` (1) na adm-api. Todas
ganham com o mesmo índice — o efeito vai além das duas telas.

**Causa 2 — Ativos: 17 tickers fracionários saem do cálculo em lote.** `currentAll` lê tudo em 6 consultas e calcula
em memória, mas o fracionário cujo lote padrão não está na lista vai pelo "caminho de um ticker"
(`IndicatorService.currentAll`, linhas 216 – 225 → `context(ticker)`), comentado como "raro". Hoje são **17**
(BMIN3F, CALI3F, CEDO3F, CEED4F, CRPG3F, DOHL3F, EKTR3F, FIGE3F, FRIO3F, GPAR3F, MAPT4F, MGEL3F, MSPA3F, MTSA3F,
ODER4F, SNSY3F, TXRX3F) — empresas que **só têm o fracionário** no catálogo (o lote padrão não existe). Cada um custa
2 leituras de `assets` por `upper(ticker)` (causa 1) + a série inteira de fechamentos + as cotações ON/PN:

| Consulta na listagem de Ações | Vezes | Tempo |
|---|---|---|
| `SELECT … FROM assets a … WHERE upper(a.ticker) = ?` (`context`) | 17 | 311 ms |
| `SELECT id FROM assets WHERE upper(ticker) = ?` (lote padrão) | 17 | 303 ms |
| `SELECT reference_date, close_price FROM asset_price_history …` (série inteira) | 29 | 174 ms |
| as 6 consultas do lote (todas as 785 linhas) | 6 | 87 ms |
| demais | 77 | 9 ms |

Ou seja, **17 linhas (2%) custam ~790 ms dos ~1.080 ms**. FIIs não têm fracionário órfão: por isso 80 ms.

**Causa 3 — Preço Teto: a lista monta a Carteira inteira só para ler o preço médio.** `CeilingPriceService.averagePrices`
(linha 694) chama `PortfolioService.getPortfolioAssets` — posições de todos os ativos, proventos, valorização da renda
fixa e do Tesouro, bonificações pendentes — e usa só `averagePrice`. Com 1 cálculo na lista, são ~430 consultas.
Por linha, ainda, `CeilingPriceDefaults.of` faz 2 leituras por `upper(ticker)` (causa 1, ~42 ms) e lê a série inteira
de fechamentos (~21 ms). O custo cresce por linha da lista.

**Causa 4 — Carteira (e Preço Teto, que a chama): consultas N+1 em `asset_terms`.** O lado inverso do `@OneToOne`
`Asset.terms` dispara um SELECT por ativo carregado sem `LEFT JOIN FETCH a.terms` (já documentado no `Claude.md`,
"Lado inverso de `@OneToOne` não é preguiçoso"). Duas consultas da Carteira não têm o fetch:

| Consulta | Ativos carregados | SELECTs extras |
|---|---|---|
| `PortfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly` | os ativos das posições | 44 |
| `AssetStockEventRepository.findWithoutCost(BONIFICACAO)`, em `BonusCostService.pendingFor` | **as 310 bonificações sem custo do catálogo inteiro**, para depois filtrar as da carteira | ~290 |

**Primeira chamada (9,3 s):** é a mesma consulta com o cache do PostgreSQL frio — as 34 leituras sequenciais de
`assets` (20 MB cada) e as séries de fechamento vêm do disco. Sai junto com as causas 1 e 2.

**Fora do problema:** o front já guarda a listagem por 10 min (`staleTime`); as 6 consultas do lote somam 87 ms; o
índice de logos é em memória; o JSON de 301 KB é gerado em poucos ms (não há compressão — D6).

---

## I. Impact

| Onde | O que muda | Risco / mitigação |
|---|---|---|
| `activest-core`, migration **V18** (nova) | `CREATE INDEX idx_assets_ticker_upper ON assets (upper(ticker))` | criação em ~0,2 s nas 100 mil linhas, dentro da subida do Flyway (as duas APIs migram; o Flyway trava o banco enquanto isso). Custo de escrita desprezível (cargas de catálogo da adm-api ganham um índice a manter). `SchemaTest` passa a esperar a V18 |
| Todas as consultas `upper(ticker) = ?` (app-api e adm-api) | o plano de execução troca a leitura sequencial pelo índice | nenhuma mudança de resultado — só de caminho |
| `IndicatorService.currentAll` (app-api) | fracionário **sem lote padrão no catálogo** calculado no lote, com os próprios dados (já lidos pelas 6 consultas) | número diferente na linha desses 17 → mitigação: comparar o JSON inteiro das duas abas antes e depois (critério 3) e caso novo no `IssuerListingIT`. O caminho de um ticker continua para o fracionário cujo lote padrão existe fora da lista (hoje 0) |
| `CeilingPriceService.averagePrices` (app-api) | preço médio só das posições dos ativos da lista, pelo mesmo `PositionCalculator` | preço médio diferente → mitigação: a mesma regra de hoje (posição em aberto, sem nota, preço médio > 0) e caso no `CeilingPriceIT` |
| `PortfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly` (core) | `LEFT JOIN FETCH a.terms` | também usada por `GoalFactsLoader` e `StrategyHoldings` — ganham o mesmo; consulta com mais um JOIN 1:1 |
| `AssetStockEventRepository` + `BonusCostService.pendingFor` | consulta nova restrita aos ativos da carteira, com `LEFT JOIN FETCH a.terms`; `check` (rotina das notificações, catálogo inteiro) continua com a atual | a pendência por posição tem que sair igual → testes de `BonusCostService` / Carteira existentes + comparação do JSON de `/portfolio/assets` |
| Front | nada | — |
| Documentação | `Claude.md` (índice, "Lado inverso de `@OneToOne`", Flyway V18), `docs/PENDENCIAS.md` (D6, D7), `README.md`, este plano | — |

Não afeta: regras de negócio, telas, dados gravados, mensagens do Redis, integrações externas.

---

## 2. Planning

### 2.1 Solução

**P1 — Índice funcional em `assets (upper(ticker))`** (migration V18). Ataca a causa 1 em todo o sistema, não só nas
duas telas. Sozinho deve tirar ~600 ms da listagem de Ações e ~40 ms por linha do Preço Teto.

**P2 — Listagem de Ações: fracionário sem lote padrão no lote.** Em `currentAll`, quando o lote padrão não está na
lista **e não existe no catálogo**, a linha é calculada como as outras, com o próprio id (preço, eventos e proventos
já estão nos mapas lidos em lote) — é o que `context()` faz hoje nesse caso (`priceAsset` = o próprio id), só que sem
voltar ao banco. Fracionário cujo lote padrão existe fora da lista continua no caminho de um ticker.

**P3 — Preço Teto: preço médio sem montar a Carteira.** `averagePrices` passa a pegar só as posições dos ativos da
lista (consulta nova no `PortfolioAssetRepository`, posições sem nota) e chamar `PositionCalculator.positionsOf` para elas — o mesmo
cálculo que a Carteira usa para o `averagePrice`, sem proventos, valorização da renda fixa e bonificações.

**P4 — Carteira: fim do N+1 em `asset_terms`.** `LEFT JOIN FETCH a.terms` em `findAllByPortfolioIdWithAssetEagerly`;
em `BonusCostService.pendingFor`, consulta das bonificações sem custo **só dos ativos das posições**, com o fetch.

Estimativa (a confirmar no Testing): Ações de ~1,1 s para ~0,3 s; Preço Teto com 1 cálculo de ~0,3 s para < 0,1 s;
Carteira de ~0,2 s para ~0,1 s.

### 2.2 Critérios de aceitação

1. `upper(ticker) = ?` usa o índice (`EXPLAIN` com `Index Scan using idx_assets_ticker_upper`).
2. Tempo quente (mediana de 5 chamadas, mesma pilha e mesmo usuário da D.2): `/assets/indicators?kind=COMPANY`
   **≤ 400 ms**; `/ceiling-prices` **≤ 120 ms**; `/portfolio/assets` não pior que hoje. Primeira chamada depois de
   `docker compose restart` registrada no andamento.
3. **Nenhum número muda:** JSON de `/assets/indicators` (as duas abas), `/ceiling-prices` e `/portfolio/assets`
   idênticos antes e depois (comparação campo a campo, salvo ordem de chaves).
4. Consultas por chamada: listagem de Ações ≤ 15 (hoje 146); Preço Teto com 1 cálculo ≤ 60 (hoje 441).
5. `mvn clean verify` verde nos quatro módulos, com os casos novos (`IssuerListingIT` com fracionário sem lote
   padrão; `CeilingPriceIT` com o preço médio; `SchemaTest` na V18).
6. Telas Ativos (Ações e FIIs), Preço Teto e Carteira conferidas no navegador nos dois temas.
7. Documentação atualizada (7.2 das regras).

### 2.3 Decisões (para o Guilherme responder)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D1** ✅ **a (Guilherme, 29/10/2026: "confundi preço médio com preço teto")** | Qual é a "listagem de preço médio"? | a) **a lista do Preço Teto** (`/ceiling-prices`), com a Carteira melhorando junto pela P4 · b) a Carteira (`/portfolio`) · c) outra tela | **a** — é a que mais destoa (0,3 s para 1 linha) e a P4 cobre a Carteira de qualquer forma |
| **D2** ✅ **a** | Escopo | a) **P1 – P4**, por etapas · b) só P1 (o índice) agora | **a** — P1 sozinho deixa Ações em ~0,5 s; P2 – P4 são pequenos e cada etapa fecha verde antes da próxima |
| **D3** ✅ **a** | P2 — fracionário que só existe como fracionário (17 hoje) | a) **calcular no lote com os próprios dados** · b) manter o caminho de um ticker (fica só o ganho do índice) | **a** — mesmo resultado (critério 3), sem 34 consultas e 17 séries de fechamento |
| **D4** ✅ **a** | P3 — a tela Preço Teto mostra uma linha por cálculo salvo pelo usuário (hoje 1). Para preencher a coluna "Preço médio" dessas linhas, a API monta a **Carteira inteira** (todas as posições, proventos, renda fixa, Tesouro, bonificações) e só lê o preço médio dos tickers que têm cálculo | a) **calcular só as posições das ações que têm cálculo de preço teto**, pelo mesmo `PositionCalculator` · b) continuar com a Carteira inteira | **a** — mesma regra e mesmo cálculo, sem o resto da Carteira |
| **D5** ✅ **a** | P4 — **consulta de leitura** (só exibição; nada é gravado nem carregado na base) que a Carteira faz para mostrar o ícone de "bonificação sem custo" ao lado do preço médio: hoje lê as 310 bonificações sem custo de **todas as ações do catálogo** (cada uma com um SELECT extra em `asset_terms`) e depois filtra as das posições do usuário. A rotina das notificações, que confere todos os usuários, não muda | a) **ler só as bonificações dos ativos da carteira do usuário**, com o `asset_terms` na mesma consulta · b) manter a leitura do catálogo inteiro, só sem os SELECTs extras | **a** — o custo deixa de crescer com o catálogo |
| **D6** ✅ **b** | Compressão das respostas (`server.compression`; a listagem de Ações tem 301 KB de JSON repetitivo, que comprime bem) | a) ligar agora · b) **registrar em `docs/PENDENCIAS.md`** | **b** — no dev (localhost) não muda o tempo medido; pesa quando houver rede de verdade entre front e API |
| **D7** ✅ **b** | Busca de ativos (`LIKE '%texto%'` em ~75 mil ativos, 60 – 80 ms por tecla; `pg_trgm` já instalado) | a) índice trigram neste escopo · b) **registrar em `docs/PENDENCIAS.md`** | **b** — está aceitável e não é das duas telas pedidas |
| **D8** ✅ **b** | Cache da listagem no Redis | a) agora · b) **não** | **b** — depois de P1 – P4 a listagem fica em ~0,3 s e o cache exigiria invalidar a cada fechamento e balanço |

### 2.3.1 Decisões tomadas

- **D1 = a** (29/10/2026): a tela é a lista do **Preço Teto**; o Guilherme tinha chamado de "preço médio" por engano.
  A Carteira melhora junto pela P4.
- **D2 = a, D3 = a, D6 = b, D7 = b, D8 = b** (29/10/2026, pela recomendação: "O restante siga a recomendação").
- D4 e D5: o Guilherme pediu esclarecimento (de que lista se trata a P3; que consulta é a da P4); texto das duas
  reescrito na tabela. **D4 = a, D5 = a** (29/10/2026). Implementation aprovada ("siga para implementação"), branch
  `TRACK_01-performance` criada pelo Guilherme.

### 2.4 Etapas do plano

| # | Etapa | O que | Conferência |
|---|---|---|---|
| E0 | Retrato de antes | gravar o JSON dos 4 endpoints (critério 3) e os tempos, fora do repositório | — |
| E1 | P1 | V18 + `SchemaTest` | `EXPLAIN`; tempos; JSON igual |
| E2 | P2 | `IndicatorService.currentAll` + caso no `IssuerListingIT` | tempos, consultas e JSON das duas abas; tela Ativos |
| E3 | P3 | `CeilingPriceService.averagePrices` + caso no `CeilingPriceIT` | tempos, consultas e JSON; tela Preço Teto |
| E4 | P4 | repositórios + `BonusCostService.pendingFor` | tempos, consultas e JSON; Carteira (ícone de bonificação sem custo) |
| E5 | Fechamento | `mvn clean verify`, primeira chamada depois do restart, documentação | — |

Cada etapa fecha com a compilação, os testes do módulo e a conferência dela antes da próxima. Testing (5), Review (6)
e Commit (7) depois da E5.

### 2.5 Quem faz

| Atividade | Quem |
|---|---|
| Responder D1 – D8 e aprovar a Implementation | Guilherme |
| Criar a branch `TRACK_01-performance` a partir da `develop` | Guilherme |
| E0 – E5, Testing e Review | Claude |
| Conferência final no navegador | Guilherme |
| Commit e PR para a `develop` | Guilherme (mensagem sugerida pelo Claude na etapa 7) |

### 2.6 Andamento

| Data | Etapa | Situação |
|---|---|---|
| 29/10/2026 | Discovery, Impact, Planning | feito — plano apresentado |
| 29/10/2026 | Decisões | D1 = a (Preço Teto); D2, D3, D6, D7, D8 pela recomendação; D4 = a e D5 = a depois do esclarecimento; Implementation aprovada |
| 29/10/2026 | E0 | retrato de antes: JSON dos 4 endpoints gravado fora do repositório; duas tomadas seguidas idênticas (o dado não muda entre chamadas) |
| 29/10/2026 | E1 | `V18__assets_ticker_upper_index.sql` aplicada pelas duas APIs (0,2 s); `EXPLAIN` com `idx_assets_ticker_upper` (0,14 ms); `SchemaTest` na V18 (10/10 verdes). Ações 1.103 → 403 ms; JSON idêntico |
| 29/10/2026 | E2 | `IndicatorService.currentAll`: fracionário sem lote padrão no catálogo calculado no lote; caso novo `IssuerListingIT.fractionalOnly` (EKTR3F). Ações 403 → 123 ms; JSON idêntico |
| 29/10/2026 | E3 | `PortfolioService.averagePrices` + `PortfolioAssetRepository.findWithoutNoteByAssetIds`; `CeilingPriceService` casa pelo id do ativo; caso novo `CeilingPriceIT.listAveragePrice`. Preço Teto 341 → 101 ms; JSON idêntico |
| 29/10/2026 | E4 | `LEFT JOIN FETCH a.terms` em `findAllByPortfolioIdWithAssetEagerly` e `findWithoutCost`; `findWithoutCostByAssetIds` em `BonusCostService.pendingFor`. Carteira 262 → 187 ms; JSON idêntico |
| 29/10/2026 | E5 / Testing | `mvn clean verify` verde (12,5 min): core 255; integration 103; adm-api 155 + 99; app-api 237 + 309 (com `IssuerListingIT` 6, `CeilingPriceIT` 9, `DailyOpeningIT` 8). Consultas contadas de novo (2.7). Restart de banco + app-api: JSON idêntico. Front sem mudança (nem lint nem Vitest a rodar). Documentação: `Claude.md`, `docs/PENDENCIAS.md` (seção 42), `README.md` |
| 29/10/2026 | Review | diff conferido; nenhum ajuste |
| 29/10/2026 | Commit / encerramento | mensagem sugerida; conferência no navegador aprovada pelo Guilherme; escopo encerrado — commit e PR com ele |

### 2.7 Resultado (dev, 29/10/2026, mediana de 6 chamadas)

| Endpoint | Antes | Depois | Consultas antes → depois | 1ª chamada (antes → depois) |
|---|---|---|---|---|
| `/assets/indicators?kind=COMPANY` | 1.103 ms | 121 ms | 146 → 10 | 8.092 → 882 ms |
| `/assets/indicators?kind=FII` | 88 ms | 100 – 108 ms (ruído) | — | 502 → 443 ms |
| `/ceiling-prices` (1 cálculo) | 367 ms | 83 ms | 441 → 27 | 827 → 548 ms |
| `/portfolio/assets` | 262 ms | 187 ms | ~430 → 94 | 340 → 474 ms |

"1ª chamada": antes, a primeira do E0 (API no ar havia horas, cache do banco frio); depois, a primeira depois de
`docker compose restart db app-api` com a JVM recém-subida (sem JIT) — por isso a Carteira, que não lia `assets` por
ticker, não melhora nessa coluna. FIIs não mudaram (não tinham fracionário órfão). O JSON dos quatro saiu idêntico ao
do E0 em todas as tomadas (E1–E4 e depois do restart).
