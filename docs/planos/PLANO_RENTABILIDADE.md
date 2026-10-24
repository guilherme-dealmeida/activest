# PrimeVest — Plano de Rentabilidade Comparativa e Benchmarks

> **Nota (23/10/2026):** escrito quando o projeto se chamava **PrimeVest**. Nomes de módulo (`primevest-*`), pacote
> (`com.primevest`), configuração (`primevest.*`, `PRIMEVEST_*`), contêineres e versões de migration (V1–V67) citados aqui
> são os do repositório `primevest`; no Activest viraram `activest-*`, `com.activest`, `activest.*`/`ACTIVEST_*` e as
> migrations consolidadas V1–V6 (`docs/planos/PLANO_RENOMEACAO_ACTIVEST.md`).

> Fase: **implementado** (17/10/2026; criado no mesmo dia, ordem de execução dada após D1–D10). Metodologia em
> `docs/RENTABILIDADE_METODOLOGIA.md`; desvios do texto original na seção 13 (andamento). Decisões novas deste plano: **D1–D10** (seção 11), respondidas em 17/10/2026 — todas pela
> recomendação.
> Origem: ESCOPO "Módulo de Rentabilidade Comparativa e Benchmarks PRIMEVEST", levantamento de 17/10/2026 (dúvidas
> L1–L18, respondidas no mesmo dia — seção 10). Pendências ligadas: `docs/PENDENCIAS.md` 20.3 (histórico de cripto),
> 23.3 (comparação da carteira com os índices), 24.5 (rentabilidade histórica). Reaproveita
> `docs/planos/PLANO_INDICES_MERCADO.md` (séries e `MarketIndexCalculator`), `docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md`
> (curva) e `docs/planos/PLANO_VENDA_RENDA_FIXA.md` (lotes, líquido). O escopo de Metas vai consumir o motor daqui.

---

## 0. Resumo

Hoje a "Rentabilidade" do PrimeVest é uma conta só: **(valor atual + proventos − investido) ÷ investido**, das
posições abertas, no Dashboard; na Carteira, valorização ÷ aplicado. Não existe série no tempo, nem comparação com
índice, nem retorno real.

Com este plano:

- **Linha do tempo da carteira** calculada (nada gravado, L1): valor de cada posição em cada pregão desde a primeira
  transação, pelas fontes que já existem (COTAHIST, PU do Tesouro, curva da renda fixa, cripto), mais aportes,
  retiradas e proventos. Cache no Redis que **já existe** (`RedisConfig`, hoje sem uso).
- **Motor único de retorno**: TWR (principal), XIRR (complementar), retorno real, diferença em p.p., % do CDI,
  contribuição, risco. Dashboard, tela Rentabilidade e, depois, Metas leem dele (L13).
- **Benchmarks**: CDI, Selic, IPCA, Ibovespa, IFIX (já existem), **IPCA + x%** (L4), **IVVB11 (proxy S&P 500 em BRL)**
  (L6) e **BTC/BRL** pela Binance desde 13/10/2020 (D2).
- **Tela Rentabilidade** (abaixo de Dashboard, L17): cards, gráfico acumulado, barras por período, tabela comparativa,
  análise por classe/ativo com contribuição, mapa de calor, patrimônio nominal × real e risco.
- **Lacunas nunca somem em silêncio** (L16): toda série diz o que cobriu; sem preço, o resultado do intervalo é
  marcado como indisponível — nunca um número calculado sem a posição.

**Fora deste escopo** (seção 13 do escopo original e levantamento): recomendação de compra/venda, projeção, otimização,
cenários, comparação entre usuários, índice composto pela estratégia (L7), índices ANBIMA (IMA-B, IRF-M), IBrX, IDIV,
moeda de referência/câmbio além da PTAX, IR sobre ganho de capital em renda variável e corretagem (regra do projeto).

---

## 1. O que existe (conferido no código e no banco do DEV em 17/10/2026)

| Peça | Onde | Hoje | Neste plano |
|---|---|---|---|
| `market_indices` / `market_index_values` | core (V43), adm-api grava | CDI, SELIC, IPCA, IBOV, IFIX, IGPM, INPC, TR, SELIC_META, USD_PTAX desde 2010 (DEV: até 15/10/2026) | **reaproveitados** como benchmarks; nada muda nas tabelas |
| `MarketIndexCalculator` | core | `compound` (taxa, com `percentOfIndex` e spread) e `level` (Ibovespa, IFIX, dólar) | base das séries de benchmark; ganha a série **diária acumulada** (hoje só o acumulado de um intervalo) |
| `asset_price_history` | core (V29/V44) | ações e FIIs pelo COTAHIST desde 2010; Tesouro (`TESOURO`) desde 2010; ETF/BDR só BRAPI e só dos ativos em carteira; cripto só desde a carga (PENDENCIAS 20.3) | ETF e BDR entram no COTAHIST; cripto ganha histórico (seção 3) |
| `HistoricalQuotesImportService` | adm-api | `TYPES = Set.of("ACAO", "FII")` | + `ETF`, `BDR` (os arquivos já estão em `carga/data/b3/cotahist`: IVVB11 tem 250 pregões em 2025) |
| `PositionCalculator` | core | cotas e custo na data (`sharesOn`, com eventos societários) | usado dia a dia pela linha do tempo |
| `FixedIncomeCurve` / `FixedIncomeLots` | core | saldo bruto e lotes (líquido estimado) **numa data** (`run(..., referenceDate, ...)`) | ganha o **caminho diário** numa passada só (seção 4.2) |
| `dividends` | core | `ex_date` (data-com), `payment_date`, `total_amount` (bruto), `net_amount` (líquido); a receber já gravado | proventos entram na série pela **data-com** (L3) |
| `BusinessCalendar` | core | dias úteis conferidos contra o CDI 2010–2026 | eixo da série (pregões B3) |
| `RedisConfig` (`RedisCacheManager`) | app-api | configurado (caches `assets`, `quotes`, `portfolio`), **nenhum `@Cacheable` no código** | cache `performance` (seção 4.5) — sem infraestrutura nova |
| `GET /api/v1/portfolio/performance` | app-api | `PortfolioService.getPerformance` + `PortfolioPerformanceDTO` | **sai** (seção 6.3) — consumidores conferidos |
| Dashboard | web | card "Rentabilidade" e Top 5 com a conta simples | passa a ler o motor (TWR) |
| Carteira | web | coluna "RENT. %" = valorização ÷ aplicado | renomeada "Resultado s/ investido" |

**Fontes de cripto conferidas da máquina do Guilherme em 17/10/2026:** CoinGecko gratuito responde histórico só dos
**últimos 365 dias** (2020 → 401; nov/2025 → 457 pontos); Binance público (`/api/v3/klines`, sem chave) tem
**BTCBRL desde 13/10/2020** e BTCUSDT desde 17/08/2017.

**Cobertura da carteira do Guilherme (DEV, 17/10/2026):** ações, FIIs e Tesouro com preço em todo pregão desde a 1ª
compra; RF privada pela curva; **IVVB11** comprado em 29/08/2025 com histórico só desde 21/09/2026; **BTC** desde
13/11/2025 com histórico só desde 24/09/2026; **USDC** desde 13/10/2026 com um ponto. Atenção: a compra de BTC sai da
janela de 365 dias da CoinGecko por volta de **13/11/2026** — a recarga (E1) deve rodar antes, ou depender da Binance.

---

## 2. Regras do escopo

| # | Regra |
|---|---|
| R1 | **Eixo**: um ponto por pregão da B3 (`BusinessCalendar`), da primeira transação do escopo até hoje. Cripto usa o preço daquela data. O último ponto é **hoje** com o **preço atual** (REGRAS 2.6), e o valor dele tem que bater com o total da Carteira |
| R2 | **Valor de uma posição na data** (bruto): renda variável e cripto = cotas na data (`PositionCalculator.sharesOn`, com eventos) × fechamento da data; Tesouro = cotas × PU de venda da data; RF privada = saldo na curva na data. **Líquido** (D-basis, L10): RF/TD = líquido estimado dos lotes na data; renda variável = igual ao bruto (IR de ganho de capital fora do escopo) |
| R3 | **Fluxos** (não são rendimento — critério 3): compra = aporte (`total_amount`), venda = retirada (bruto: `total_amount`; líquido: `net_amount` quando houver, senão `total_amount`). Corretagem fora (regra do projeto, PENDENCIAS 7.4) |
| R4 | **Proventos** (L3): entram na série no **pregão ex** — o primeiro dia útil depois da data-com (`dividends.ex_date`), quando a cotação cai (ver andamento), pelo bruto ou pelo líquido conforme a base; o provento sai da carteira (o PrimeVest não tem conta de caixa), então é **distribuição**, não reinvestimento. Amortização e restituição de capital também são distribuição. Os totais de "recebido" continuam pela regra atual (pagamento até hoje) |
| R5 | **TWR diário**, compras no começo do dia, vendas e proventos no fim: `r_t = (V_t + W_t + D_t) / (V_{t−1} + A_t) − 1` (A aporte, W retirada, D proventos do pregão ex; ver andamento). Acumulado = `Π(1 + r_t) − 1` (composição, critério 5). Dia com denominador zero (carteira vazia) não tem retorno e não quebra a cadeia |
| R6 | **XIRR** (L2): fluxos de caixa reais — aportes (−), retiradas (+), proventos na **data de pagamento** (+), valor final + proventos a receber (+). Só com ≥ 12 meses e convergência; senão "—" com o motivo |
| R7 | **Retorno real**: `(1 + R_nom) / (1 + R_IPCA) − 1`, com o IPCA do **mesmo intervalo**, e só até o último mês publicado (L11): o resultado diz "até mm/aaaa" |
| R8 | **Diferença**: `R_carteira − R_benchmark` em p.p.; **% do CDI**: `R_carteira / R_CDI × 100`, só com CDI acumulado > 0 (D5). Benchmark sempre no **mesmo intervalo efetivo** da carteira (critério 2) |
| R9 | **Benchmark diário**: taxas diárias (CDI, Selic) compõem por dia útil; mensais (IPCA) distribuídas pelos dias úteis do mês, `(1+I)^(1/DU_mês)` (mesmo critério da curva, D6 da valorização); IPCA + x% multiplica `(1+x)^(1/252)` por dia útil; níveis (IBOV, IFIX, IVVB11, BTC) = razão entre fechamentos |
| R10 | **Lacunas** (L16, D3): dia sem negócio usa o último fechamento até **N pregões** (D3), marcado "preço do último negócio em dd/mm"; sem preço além disso, ou sem série nenhuma (tipos MANUAL, TR sem curva), o intervalo do escopo que contém a posição fica **indisponível** — nunca calculado sem ela. A resposta traz a cobertura e os ativos culpados. Série estimada da curva (IPCA do mês não divulgado) sai **marcada como estimada** |
| R11 | **Posições encerradas** (L12) entram nos dias em que existiram; fora deles o ativo não tem ponto ("não estava na carteira") |
| R12 | **Reserva** (L8): "Sem reserva" tira as posições com `is_reservation` do valor, dos fluxos e dos proventos — a marca é a de hoje, aplicada à história inteira (D9) |
| R13 | **Contribuição** (D6): em R$ = variação do valor + proventos − aportes + retiradas do ativo no período (soma exatamente o resultado da carteira); em p.p. = contribuições diárias `w_{i,t−1} × r_{i,t}` ligadas por Cariño, para somarem o TWR do período |
| R14 | **Risco** (L14): volatilidade anualizada = desvio dos retornos diários × √252; máximo drawdown sobre o índice acumulado; Sharpe e Sortino com o **CDI** como taxa livre de risco. Só com ≥ 12 meses de série válida no período; a RF pela curva subestima a volatilidade (aviso no (i)) |
| R15 | **Um motor**: card, gráfico, tabela e Dashboard saem da mesma linha do tempo e das mesmas funções (critério 12). Nenhuma tela recalcula |
| R16 | Nada é gravado por usuário (L1). Nada é inventado (critério 14) |

---

## 3. Dados e banco

### 3.1 V57 — catálogo de benchmarks (D1)

Tabela fina, só referência — os valores continuam onde estão (`market_index_values` ou `asset_price_history`):

| Coluna | Conteúdo |
|---|---|
| `code` (único) | `CDI`, `SELIC`, `IPCA`, `IPCA_MAIS`, `IBOV`, `IFIX`, `SP500_IVVB11`, `BTC` |
| `name` | "CDI", "IPCA + x%", "IVVB11 (proxy S&P 500 em BRL)", "Bitcoin (BTC/BRL)"… |
| `category` | RENDA_FIXA, JUROS, INFLACAO, ACOES_BR, FII, ACOES_EUA, CRIPTO |
| `kind` | `INDICE` (aponta `market_index_id`) ou `ATIVO` (aponta `asset_id`) — CHECK: exatamente um dos dois |
| `accepts_spread` | só `IPCA_MAIS` (a taxa x vem na consulta, informada pelo usuário) |
| `note` | o que o usuário precisa saber: "Índice de retorno total (metodologia B3)" no IBOV/IFIX; "ETF negociado na B3 que replica o S&P 500 em reais; não é a série oficial do índice" no IVVB11; fonte e início do BTC |
| `default_selected` | só `CDI` (L5, L7) |
| `sort_order`, `active` | ordem dos seletores; desligar sem apagar |

Fonte, periodicidade, primeira e última data **não** viram coluna: saem do índice ou do ativo apontado (critério 9 e
"não recriar estruturas"). Seed na própria V57, resolvendo `market_index_id` por `code` e `asset_id` por `ticker`
(convenção das migrations; nunca id literal).

Benchmark padrão de cada classe (D4, decidido: no código): coluna `default_benchmark_id` em `asset_types`? **Não** — fica no código
(`BenchmarkDefaults`), porque é regra de tela, não dado; se D4 pedir edição pelo usuário, vira tabela.

### 3.2 V58 — origem `BINANCE` no histórico de preço (D2 = a)

`ck_price_history_source` aceita `BINANCE`. Precedência como a do COTAHIST: a linha `COINGECKO` existente não é
sobrescrita; a Binance só preenche datas que faltam.

### 3.3 Cargas (adm-api)

| Carga | O quê | Como |
|---|---|---|
| COTAHIST ETF e BDR | `TYPES` + `ETF`, `BDR`; a carga anual relê os zips já em `carga/data/b3/cotahist` (sem download) e a rotina diária passa a gravar os dois tipos | `POST /api/v1/admin/b3/cotahist?from=2010` (existente) |
| Cripto — últimos 365 dias | todo ativo cripto que está ou esteve em alguma carteira, pela CoinGecko (`market_chart/range`, BRL), gravando `COINGECKO`; arquivo em `carga/data/coingecko/historico/{id}.json` → bucket. Resolve PENDENCIAS 20.3 para o que cabe na janela | rotina nova `POST /api/v1/admin/cripto/historico` + etapa do `bootstrap` |
| Cripto — antes de 365 dias (D2) | BTC pela Binance `klines` BTCBRL diário (desde 13/10/2020), `BINANCE`, arquivo `carga/data/binance/klines/BTCBRL/{ano}.json` (ano fechado só do disco, como `IndexYearCache`) | mesma rotina; cliente novo `BinanceKlinesClient` (público, sem credencial — **não** usa o `primevest-integration`, que é do usuário) |

Nenhuma série é reconstruída por conta própria (ex.: BTCUSDT × PTAX antes de 2020 fica fora — ver D2).

---

## 4. Motor (app-api, `com.primevest.domain.performance`)

Regras puras em classes sem banco (testáveis como `FixedIncomeCurve`); montagem dos dados num serviço.

### 4.1 Peças

| Classe | Tipo | Faz |
|---|---|---|
| `PerformanceScope` | record | tipo / categoria / ativo (posição; na RF, o título), reserva COM/SEM, base BRUTO/LÍQUIDO |
| `PortfolioTimelineBuilder` | serviço | carrega uma vez transações, posições, proventos, preços do período e séries dos índices; monta a `PortfolioTimeline` |
| `PortfolioTimeline` | puro | por posição e por pregão: valor bruto, valor líquido, aporte, retirada, provento (data-com), situação do preço (OK, ÚLTIMO_NEGÓCIO dd/mm, ESTIMADO, SEM_PRECO), "estava na carteira" |
| `ReturnMath` | puro | TWR diário e acumulado, retornos mensais/trimestrais/anuais, real, diferença, % do CDI, janela "mês / ano / 12 meses / período" |
| `Xirr` | puro | Newton com fallback por bissecção; devolve vazio sem convergência |
| `BenchmarkSeries` | puro | série diária acumulada de cada benchmark no mesmo eixo (R9), com `firstDate` e lacunas |
| `Contribution` | puro | R$ e p.p. (Cariño) por posição, agregados por categoria e tipo; emissor como agrupamento extra na RF (L9) |
| `RiskMetrics` | puro | volatilidade, drawdown máximo (pico, vale, datas), Sharpe, Sortino |
| `Coverage` | puro | período efetivo, dias indisponíveis, ativos culpados, pontos estimados, "real até mm/aaaa" |
| `PerformanceService` | serviço | orquestra, aplica o escopo, monta os DTOs; único ponto de entrada (Dashboard incluso) |

### 4.2 Caminho diário da renda fixa (core)

`FixedIncomeLots` ganha `path(terms, moves, from, to, daily, monthly, taxes, assetId, rule)` → para cada dia útil,
saldo bruto e líquido estimado, **numa passada só** (hoje `run` refaria a curva desde o início para cada data —
quadrático). `run` passa a ser o último ponto do `path`, e os testes atuais (`FixedIncomeLotsTest`,
`FixedIncomeCurve*Test`) provam que nada mudou no valor de hoje. Tesouro: cotas × PU da data, e líquido pelos lotes do
PEPS na data (mesma regra do `TreasuryAllocator`).

### 4.3 Escopo e agregação

A carteira consolidada é a soma das posições do escopo, dia a dia; o TWR do escopo é calculado sobre essa soma (não é
média dos ativos). Classe, categoria e ativo são o mesmo cálculo com outro filtro — o drill-down
carteira → classe → ativo (E05) só troca o `PerformanceScope`.

### 4.4 Desempenho

Ordem de grandeza da carteira do Guilherme: ~600 pregões × ~50 posições. Preços do período lidos numa consulta por
tipo (não por ativo); séries dos índices uma vez. Meta: < 1 s sem cache no DEV, medido na E2.

### 4.5 Cache (L1)

Spring Cache sobre o `RedisCacheManager` existente, cache `performance` (TTL 6 h como rede de segurança), chave =
usuário + base + reserva (a linha do tempo inteira; filtros e períodos saem dela em memória). Invalidação:

| Evento | Já existe | Evicção |
|---|---|---|
| transação criada/editada/excluída | `PositionWrittenEvent` | do usuário |
| reserva ligada/desligada, custo de bonificação | serviços da carteira | do usuário |
| "catálogo mudou" FECHAMENTO / PROVENTOS / INDICES / BALANCOS | `CatalogReaction` | de todos (`allEntries`) |
| recálculo das 21:00 | `PositionRefreshScheduler` | de todos |

Nos testes o cache fica `none` (perfil `test`, como hoje).

---

## 5. API (app-api, `/api/v1/performance`)

Parâmetros comuns: `range=1M|6M|YTD|1A|5A|MAX` ou `from=&to=` (D7), `type=`, `category=`, `position=`,
`reservation=WITH|WITHOUT` (padrão WITH), `basis=NET|GROSS` (padrão NET, L10), `benchmarks=CDI,IBOV,IPCA_MAIS:6.0`.
Toda resposta traz `coverage` (período efetivo, indisponível, estimado, culpados) e `basis`/`basisApplies` (falso
quando o escopo não tem RF, TD nem JCP — o seletor some).

| Método | Endpoint | Devolve |
|---|---|---|
| GET | `/benchmarks` | catálogo (seção 3.1) com fonte, periodicidade, `firstDate`, `lastDate`, nota |
| GET | `/summary` | cards: TWR do período, benchmark principal, diferença (p.p.), real ("até mm/aaaa"), % do CDI, XIRR (≥ 12 meses), resultado em R$ |
| GET | `/series` | acumulado diário da carteira e de cada benchmark (densidade dos gráficos de cotação: diário até ~1 ano, semanal até ~5, mensal acima — `PriceSeries`), com marcação de indisponível/estimado por ponto |
| GET | `/periodic?granularity=MONTH\|QUARTER\|YEAR` | barras da carteira e dos benchmarks; com `MONTH` e o período inteiro, alimenta o mapa de calor mês × ano (L15) |
| GET | `/table` | por indicador: mês, ano, 12 meses, período e diferença para a carteira |
| GET | `/breakdown?level=TYPE\|CATEGORY\|POSITION\|ISSUER` | rentabilidade individual (TWR de cada um nos dias em que existiu), contribuição R$ e p.p., peso médio, "estava na carteira" |
| GET | `/wealth` | patrimônio nominal, patrimônio em reais do último IPCA publicado (D8), aportes e retiradas acumulados, proventos acumulados, resultado |
| GET | `/risk` | volatilidade, drawdown (com datas), Sharpe, Sortino, ou o motivo de não ter |

Erros como o resto da API: escopo de outro dono → 404; período inválido → 422.

---

## 6. Telas e mudanças fora do módulo (web)

### 6.1 Rentabilidade (`/rentabilidade`, `RentabilidadePage`)

Menu logo abaixo de Dashboard (L17), ícone de tendência (`TrendingUp`). De cima para baixo:

1. **Filtros**: Tipo → Categoria → Ativo (L9; na RF, emissor como agrupamento na análise), Com/Sem reserva, período
   (atalhos + de/até), base Bruto/Líquido (só quando se aplica), benchmarks (multisseleção; IPCA + x pede o x).
   Estado na URL; última seleção no `localStorage` (D10).
2. **Aviso de cobertura** quando houver: "Sem preço de BTC entre dd/mm e dd/mm — resultado indisponível nesse
   intervalo" / "Valores com IPCA de out/2026 estimado" / "Real até set/2026".
3. **Cards**: rentabilidade (TWR), benchmark principal, diferença, real, % do CDI, e XIRR ("retorno pessoal") — cada
   um com (i) da fórmula e da base ("carteira líquida × CDI bruto" quando for o caso, L10).
4. **Gráfico acumulado** (linhas, Recharts): carteira + benchmarks, eixo compartilhado, legenda clicável, balão com os
   valores da data, faixa cinza nos intervalos indisponíveis, "período disponível: dd/mm/aaaa a dd/mm/aaaa".
5. **Barras por período** (Mensal/Trimestral/Anual).
6. **Tabela comparativa** (mês, ano, 12 meses, período, diferença).
7. **Análise por classe e ativo** (E05): tabela com drill-down, rentabilidade individual × contribuição lado a lado
   (nunca a mesma coluna), barras de contribuição positiva/negativa, encerrados com selo.
8. **Mapa de calor** mês × ano.
9. **Patrimônio** (E06): nominal × real, aportes − retiradas acumulados e o resultado separados.
10. **Risco** (E07): os quatro indicadores com o período usado; "—, exige 12 meses" quando faltar.

Padrões do projeto: tema claro com override (`text-white/NN`), `InfoTooltip`, cores de retorno
(`getReturnColor`), legendas fora do gráfico como nas demais telas (`ChartLegend`).

### 6.2 Dashboard e Carteira (L13)

- Dashboard: card "Rentabilidade" passa a ser o **TWR desde o início** (`/performance/summary?range=MAX`), com o (i)
  da métrica e link para a tela; Top 5 com a rentabilidade de cada ativo pelo mesmo motor (D9).
- Carteira: coluna "RENT. %" vira **"Resultado s/ investido"** (mesma conta de hoje, valorização ÷ aplicado), com o
  (i) dizendo que não é rentabilidade no tempo.

### 6.3 Saída do `GET /portfolio/performance`

Consumidores conferidos em 17/10/2026: nenhuma tela; `portfolioApi.getPerformance` e a interface
`PortfolioPerformance` (sem uso), uma linha do `PortfolioControllerTest` (401), `docs/USE_CASES.md` (UC04);
`carga/scripts` não chama. Sai: endpoint, `PortfolioService.getPerformance`, `PortfolioPerformanceDTO`, client, tipo e
a linha do teste; UC04 aponta para `/performance/*` (REGRAS 6.4: sem uso, apaga).

---

## 7. Etapas

A ordem pedida: primeiro a série e o motor, depois a tela, por último risco e refinamentos.

| Etapa | Conteúdo | Escopo original | Fecha com |
|---|---|---|---|
| **P1** Dados | COTAHIST com ETF e BDR (carga anual e diária); recarga de cripto (CoinGecko 365 d + Binance BTC, D2); V57 benchmarks; V58; `data push` das pastas novas | E01, E02 | IVVB11 e BTC com preço em todo pregão desde a compra no DEV; testes da adm-api |
| **P2** Linha do tempo | `PortfolioTimeline(Builder)`, `FixedIncomeLots.path`, lacunas e cobertura (R10), cache | E02, E03 | testes de regra pura + IT; valor do último ponto = total da Carteira |
| **P3** Motor | `ReturnMath`, `Xirr`, `BenchmarkSeries` (com IPCA + x), real, % CDI; **script de conferência independente** (L18) | E03, E08 | casos da seção 9 verdes + script batendo com o motor na carteira do Guilherme |
| **P4** API e Dashboard | endpoints `summary`, `series`, `periodic`, `table`, `benchmarks`; Dashboard e Carteira (6.2); saída do `/portfolio/performance` | E03, E04 | testes de controller e IT |
| **P5** Tela (comparativo) | filtros, aviso de cobertura, cards, gráfico acumulado, barras, tabela, menu | E04 | lint, `tsc`, conferência no navegador |
| **P6** Classe e ativo | `breakdown`, `Contribution`, drill-down, mapa de calor | E05 | soma das contribuições = resultado (R$) e = TWR (p.p.) |
| **P7** Patrimônio real | `wealth`, gráfico nominal × real, aportes × resultado | E06 | conferência com IPCA acumulado |
| **P8** Risco | `RiskMetrics`, `risk`, cartão de risco | E07 | casos de risco + script |
| **P9** Fechamento | bateria completa, `Claude.md`, `PENDENCIAS.md`, `README.md`, metodologia em `docs/RENTABILIDADE_METODOLOGIA.md` (fórmulas, fontes, limitações — passo 11 do escopo) | E08 | validação final (REGRAS 4.1) |

Liberação incremental (passo 12 do escopo): P1–P5 já entregam a comparação principal; P6–P8 entram na mesma tela.

### Quem faz

| O quê | Claude | Guilherme |
|---|---|---|
| Responder D1–D10 | | ✔ (17/10/2026) |
| P1–P9 (código, migrations, cargas, `data push`, testes, docs) | ✔ | |
| Script de conferência independente (L18) | ✔ (escreve e roda) | ✔ (confere alguns pontos com o app aberto) |
| Validação final (compilar, testes, lint, subir, navegador) | ✔ | confere no app |

---

## 8. Testes

| Camada | O que cobre |
|---|---|
| Regra pura (`ReturnMath`, `Xirr`, `BenchmarkSeries`, `Contribution`, `RiskMetrics`, `Coverage`, `PortfolioTimeline`, `FixedIncomeLots.path`) | casos da seção 9, com números fechados à mão |
| Repositório | consultas novas de preço por período e tipo, escopadas pelo dono onde houver usuário |
| Serviço / IT (Testcontainers) | carteira montada pela API → `/performance/*`; escopo de outro dono → 404; cache desligado |
| Controller | 401, 422 de período, JSON que a tela lê |
| adm-api | COTAHIST com ETF/BDR (recorte real); CoinGecko e Binance com respostas gravadas (sem rede) |
| Conferência independente | script fora do código da aplicação (`carga/scripts/conferencia/rentabilidade.py`): lê o banco e as séries, recalcula TWR, real e % CDI por outra implementação e compara com a API |

---

## 9. Casos de conferência (viram testes com o nome do caso)

| # | Caso | Esperado |
|---|---|---|
| C1 | Carteira sem movimento no período | TWR = variação do valor |
| C2 | Aporte grande no meio do período | TWR igual ao de C1 para os mesmos preços (aporte não é rendimento) |
| C3 | Retirada | idem |
| C4 | Provento: data-com antes do pagamento | sem queda falsa entre data-com e pagamento; XIRR usa o pagamento |
| C5 | Provento no fim do período ainda a receber | entra no TWR (data-com) e no valor final do XIRR |
| C6 | Histórico incompleto (BTC sem preço num intervalo) | intervalo indisponível, culpado listado; filtro sem cripto calcula |
| C7 | FII sem negócio por 2 pregões | último fechamento, marcado; acima de N pregões → C6 |
| C8 | Comparação com período maior que a carteira | benchmark recortado ao período efetivo |
| C9 | Retorno negativo; acumulado próximo de zero | sinais e % CDI corretos; sem divisão por quase zero |
| C10 | IPCA do mês corrente não publicado | real "até mês anterior" |
| C11 | Posição encerrada | aparece nos dias em que existiu; contribui |
| C12 | RF bruto × líquido | líquido ≤ bruto; base indicada |
| C13 | IVVB11 (proxy) e BTC | razão de fechamentos; rótulo de proxy |
| C14 | Consistência | o mesmo número no card, na tabela, no último ponto do gráfico e no Dashboard |
| C15 | Reserva | "Sem reserva" tira valor, fluxos e proventos das posições marcadas |
| C16 | Último ponto | valor de hoje = total da Carteira (preço atual) |

O teste de "conversão cambial para ativos internacionais" do escopo sai (levantamento, item 6): não há moeda no
sistema; o único câmbio é o que já vem embutido no IVVB11.

---

## 10. Decisões do levantamento (L1–L18, 17/10/2026)

Respondidas no mesmo dia, todas pela recomendação, com os acréscimos abaixo (já incorporados nas seções 2–7):

| # | Decisão |
|---|---|
| L1 | Série calculada, sem gravar; cache com invalidação usando a infraestrutura existente (o Redis e o `RedisCacheManager` já estão no projeto) |
| L2 | TWR principal; XIRR complementar com ≥ 12 meses e dados suficientes |
| L3 | Provento na data-com para a série; totais de recebido como hoje |
| L4 | IPCA + x% na V1, por composição (sem somar percentuais) |
| L5 | Selic disponível, não selecionada; CDI é o padrão da consolidada |
| L6 | IVVB11 rotulado "IVVB11 (proxy S&P 500 em BRL)", nunca como série oficial. BTC só com fonte confiável e série completa, verificando antes as fontes do projeto (feito: seção 1 → D2) |
| L7 | Benchmark escolhido pelo usuário, CDI pré-selecionado; índice composto pela estratégia fica para depois |
| L8 | Com/Sem reserva, padrão com; sem reserva tira do patrimônio, dos fluxos e da rentabilidade |
| L9 | Tipo → Categoria → Ativo; emissor como agrupamento extra na RF |
| L10 | Líquido padrão; bruto quando dá para calcular; a base de comparação sempre explícita ("carteira líquida × CDI bruto"); o seletor só onde a distinção existe |
| L11 | Real só até o último IPCA publicado, com aviso |
| L12 | Encerradas entram no histórico e na contribuição |
| L13 | Mesmo motor no Dashboard (TWR); Carteira "Resultado s/ investido"; `/portfolio/performance` sai depois de conferidos os consumidores (seção 6.3) |
| L14 | Os quatro indicadores, por último, com ≥ 12 meses; limitação da curva na tela |
| L15 | Mapa mês × ano; por ativo × mês na análise do ativo |
| L16 | ETF e BDR no COTAHIST; lacuna nunca excluída em silêncio — cobertura e avisos; último preço só documentado e marcado (R10, D3) |
| L17 | Menu abaixo de Dashboard |
| L18 | Script de conferência independente |

---

## 11. Decisões D1–D10 — **tomadas em 17/10/2026: todas pela recomendação**

| # | Assunto | Opções | Recomendação |
|---|---|---|---|
| **D1** | Onde fica o catálogo de benchmarks | (a) tabela `benchmarks` fina, apontando índice ou ativo (3.1); (b) pôr IVVB11 e BTC em `market_indices`, copiando a série do ativo para `market_index_values`; (c) lista no código | **(a)**: não duplica série, aceita ativo como referência e é seed como os demais cadastros de referência |
| **D2** | Fonte do BTC antes da janela de 365 dias da CoinGecko | (a) Binance pública, BTCBRL diário desde 13/10/2020 (benchmark começa aí); (b) só CoinGecko 365 dias (o benchmark BTC teria só 1 ano móvel; a posição do Guilherme fica coberta se a recarga rodar antes de ~13/11/2026); (c) BTC fora | **(a)**, sem emendar BTCUSDT × PTAX antes de 2020 (seria série reconstruída) |
| **D3** | Quantos pregões sem negócio aceitar com o último fechamento | 1 / **5** / 10 | **5 pregões**, marcado "último negócio em dd/mm"; acima disso, indisponível |
| **D4** | Benchmark padrão ao filtrar uma classe | Ações → IBOV; FII → IFIX; RF e TD → CDI; Cripto → BTC; BDR → IVVB11; ETF, Fundos e demais → CDI | **a lista ao lado**, no código; o usuário troca na tela |
| **D5** | % do CDI com carteira negativa | (a) mostrar o valor negativo; (b) "—" | **(a)**: é válido com CDI > 0 e informa; "—" só com CDI acumulado ≤ 0 |
| **D6** | Contribuição | (a) R$ + p.p. ligada por Cariño (soma o TWR); (b) só R$; (c) p.p. aritmético simples (não fecha com o TWR) | **(a)** |
| **D7** | Períodos e padrão | atalhos 1M, 6M, YTD, 1A, 5A, MAX + de/até (os da cotação, sem 1D e 7D); padrão MAX ("desde o início") ou 1A | **atalhos ao lado, padrão MAX** |
| **D8** | Base do patrimônio real | reais do último IPCA publicado / reais da data inicial | **último IPCA publicado** ("em reais de set/2026") |
| **D9** | "Sem reserva" na história | a marca de hoje vale para toda a história (sem tabela nova) / guardar o histórico da marca | **marca de hoje**, com aviso no (i); histórico da marca vira pendência |
| **D10** | Guardar a seleção de benchmarks | URL + `localStorage` (por navegador) / gravar no usuário | **URL + `localStorage`**; gravar no usuário só quando Metas precisar. **Revista em 17/10/2026:** só a URL; a tela abre sempre com o CDI |

---

## 12. Riscos e achados

- **Janela da CoinGecko**: a 1ª compra de BTC do Guilherme (13/11/2025) sai da janela de 365 dias por volta de
  13/11/2026. Com D2 = Binance a série do BTC não depende dela, mas as demais cripto em carteira (ex.: USDC) só têm a
  CoinGecko: a recarga da P1 grava o arquivo em `carga/data` antes de a janela andar.
- **BDR e ETF sem eventos no catálogo**: o valor usa cotas × preço da data; desdobramento de BDR/ETF que o catálogo
  não conhece distorce a série — o mesmo problema já distorce o valor de hoje (não é novo; vira pendência se
  aparecer).
- **Volume do COTAHIST**: BDR soma ~100 mil linhas por ano desde 2010; medir tempo e tamanho na P1 e, se pesar,
  limitar a carga aos BDRs e ETFs que estiveram em alguma carteira.
- **Proventos de posição apagada** (regra K): só existem enquanto a posição existir — a série reflete isso.
- **Marca de reserva** (D9) e **corretagem fora** (PENDENCIAS 7.4) são limitações conhecidas, documentadas na
  metodologia.
- **TR e poupança sem curva** (PENDENCIAS 24.2): título assim deixa o intervalo indisponível no consolidado (R10).

---

## 13. Andamento

| Data | Etapa | O quê |
|---|---|---|
| 17/10/2026 | — | Levantamento fechado (L1–L18) e plano criado |
| 17/10/2026 | — | D1–D10 respondidas pela recomendação; ordem de execução dada ("Concordo, implemente") |
| 17/10/2026 | P1 | V57 `benchmarks` (8 linhas) e V58 (`BINANCE`); COTAHIST com ETF e BDR (recarga 2010–2026: 3.097.462 pregões; IVVB11 desde 29/04/2014); `CryptoHistoryService` + `BinanceKlinesClient` + `POST /api/v1/admin/cripto/historico` + rotina 19:30 + etapa do bootstrap + pedido "preciso do histórico" de cripto (BTC: 345 dias CoinGecko + 1.831 Binance; USDC 363); arquivos em `carga/data/coingecko/historico` e `carga/data/binance/klines` |
| 17/10/2026 | P2 | `PortfolioTimeline(Builder)`, `ScopeSeries`, `FixedIncomeLots.path` (+ `FixedIncomeValuation.path`), cobertura e lacunas |
| 17/10/2026 | P3 | `ReturnMath`, `BenchmarkSeries`, `Xirr`; conferência independente `carga/scripts/conferencia/rentabilidade.mjs` — Ações, FIIs, ETFs, Tesouro e Cripto batem com a API em TWR, CDI, % do CDI, IPCA, real e valor final (4 casas) |
| 17/10/2026 | P4 | `/api/v1/performance/{benchmarks,summary,series,periodic,table,breakdown,wealth,risk}`; Dashboard (card e Top 5 pelo motor); Carteira "Resultado s/ investido"; `/portfolio/performance` removido |
| 17/10/2026 | P5–P8 | tela `/rentabilidade` (menu abaixo de Dashboard): filtros, cobertura, cards, acumulado, barras, comparativo, "Quem fez o resultado" (classe/categoria/ativo/emissora, Cariño), mapa de calor, patrimônio nominal × real, risco |
| 17/10/2026 | P9 | testes (`PerformanceMathTest`, `FixedIncomeLotsPathTest`, `CryptoHistoryServiceTest`, `PerformanceControllerTest`, `RentabilidadeIT`, `SchemaTest` V58) — `mvn clean verify` verde: core 189; integration 103; adm-api 116 + 48; app-api 210 + 250; lint e `tsc -b` do front sem aviso; `data push` das pastas `coingecko/` e `binance/`; conferência no navegador (tela, filtro por classe com o IBOV como principal, Dashboard); Claude.md, PENDENCIAS (seção 29), README e `docs/RENTABILIDADE_METODOLOGIA.md` |
| 17/10/2026 | ajustes | pós-implementação: card do Dashboard mostra 12 meses (principal, leva a `/rentabilidade?periodo=1A`) e desde o início (descrição); filtro com/sem reserva só em Carteira total e nos tipos que podem ser reserva (`fixedIncome`); comparativo encadeia o benchmark até o último dado publicado (IPCA sai até o último mês divulgado, com `*` e nota "até dd/mm/aaaa"; a diferença compara a carteira no mesmo intervalo — `TableRowDTO.until`) |
| 17/10/2026 | ajustes | benchmark padrão do Tesouro = IPCA e IPCA + a taxa de compra do Tesouro IPCA+ à venda mais curto, aplicada ao período inteiro (decidido: taxa de hoje no período todo; RF privada só CDI; IPCA+ com o mesmo `*` do IPCA). V59 `treasury_offers` (taxa do último dia e oferta por título), `TesouroSalesClient` (arquivo de vendas do Tesouro Transparente: à venda = vendeu nos 3 últimos dias de liquidação — o arquivo de preços traz taxa até de título fora da prateleira), gravada pela carga do Tesouro das 08:00; `BenchmarkCatalogDTO.defaultsByType` (lista) + `treasuryIpca`; a tela põe os padrões no começo da lista e troca um IPCA + x% de outra taxa. Rodada no DEV: 28 títulos à venda, IPCA+ 2032 a 6,80% (15/10/2026); `data push` da pasta `tesouro/`; `mvn verify` verde (core 190; integration 103; adm-api 116 + 49; app-api 210 + 251) |
| 17/10/2026 | ajustes | escopo "Mudanças de tela": Dashboard e tela em base **bruta**, tela abre em **12M**; Carteira revista (decidido pelo Guilherme): VALORIZAÇÃO em R$ + % (a "Variação" do Investidor10) e **RENTABILIDADE** = clássica com proventos (a do Investidor10) + "TWR: x%" embaixo, também no Top 5 do Dashboard — substitui o "Resultado s/ investido" da L13; totais com as mesmas contas e o TWR do tipo; D10 revista: a tela abre só com o CDI (sem `localStorage`) |
| 17/10/2026 | decisão | Carteira cripto × BTC/BRL: a diferença vem da convenção R5 (no dia da compra a carteira mede do preço pago ao fechamento; o índice, de fechamento a fechamento). Mantida; aviso fixo no quadro de informações da tela |

### Desvios do texto do plano (17/10/2026, na implementação)

- **R4 — pregão ex em vez da data-com.** `dividends.ex_date` é a data-com (último dia com direito); a cotação cai no
  pregão seguinte. Lançar o provento na data-com criaria uma alta falsa num dia e a queda no outro — o oposto do que a
  L3 quer evitar. O provento entra no primeiro dia útil depois da data-com.
- **R5 — vendas e proventos no fim do dia.** Com a venda no começo do dia, a venda total zeraria o denominador e o
  ganho entre o fechamento da véspera e o preço de venda se perderia. Compras continuam no começo (o primeiro dia mede
  compra × fechamento).
- **4.5 — cache em memória com "impressão digital".** O `RedisCacheManager` serializa em JSON sem suporte a datas, e a
  linha do tempo (dezenas de vetores por posição) ficaria mais lenta de ler do que de calcular. A linha do tempo fica
  em memória por usuário e é refeita quando muda a impressão digital dos dados que ela usa (transações, posições,
  proventos, preços dos ativos da carteira, índices, custos de bonificação) ou em 6 h — sem depender de evento.
  Medido: ~0,6–1,2 s na primeira leitura, ~60 ms com cache.
- **Eixo pelo calendário da B3.** 24/12 e 31/12 são dias úteis bancários (há CDI), mas a B3 não abre: o eixo usa os
  dias com fechamento do Ibovespa.
- **Centavo por posição.** O valor de cada posição é arredondado no centavo, como a Carteira — o último ponto bate
  com o valor atual dela (R$ 80.980,98 no DEV).
