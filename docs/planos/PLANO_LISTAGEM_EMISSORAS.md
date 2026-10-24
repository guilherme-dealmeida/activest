# PrimeVest — Plano da listagem de emissoras (Ações e FIIs)

> **Nota (23/10/2026):** escrito quando o projeto se chamava **PrimeVest**. Nomes de módulo (`primevest-*`), pacote
> (`com.primevest`), configuração (`primevest.*`, `PRIMEVEST_*`), contêineres e versões de migration (V1–V67) citados aqui
> são os do repositório `primevest`; no Activest viraram `activest-*`, `com.activest`, `activest.*`/`ACTIVEST_*` e as
> migrations consolidadas V1–V6 (`docs/planos/PLANO_RENOMEACAO_ACTIVEST.md`).

> **Nota de 18/10/2026:** a tela passou a se chamar **Ativos** (menu, cabeçalho e rota `/ativos`; `/emissoras` redireciona). Este plano mantém o nome e os termos da época.

> Fase: **escopo encerrado em 18/10/2026** pelo Guilherme; implementado em 17/10/2026 (L1–L6), com os ajustes da conferência — andamento na seção 9. Execução autorizada no mesmo dia, depois do
> plano fechado.
> Origem: pedido de 12/10/2026 (ESCOPO "Listagem em tela de emissoras Primevest", ETAPA Levantamento), com as
> respostas de 17/10/2026 (seção 2). Consome a tela de Emissoras (`docs/planos/PLANO_TELA_EMISSORES.md`) e os
> indicadores do escopo de balanços (`docs/planos/PLANO_EMPRESAS_BALANCOS.md`, F9).

---

## 0. Resumo

`/emissoras` sem ticker deixa de ser um aviso vazio e passa a mostrar **uma listagem do catálogo** em duas abas,
**Ações** (392 tickers) e **FIIs** (347), com logo + ticker, nome, setor/segmento, os indicadores de cada tipo e o
patrimônio líquido. A ordem padrão é o **PL decrescente**. As colunas podem ser ordenadas, as abas têm filtros (nome,
setor, faixas de indicador) e a paginação é a mesma de Transações. A coluna **AÇÃO** abre `/emissoras/{ticker}`.
A busca do topo e a página da emissora não mudam.

O backend ganha **um endpoint de listagem** que calcula os indicadores "Atual" de todos os tickers de uma aba de uma
vez, com as **mesmas regras** do `GET /assets/{ticker}/indicators` — o valor da lista tem que ser idêntico ao da
página da emissora.

---

## 1. Levantamento (12 e 17/10/2026)

### 1.1 Universo

| | Ações | FIIs |
|---|---|---|
| Ativos ativos ligados a emissor | 785 | 347 |
| Fracionários (fora — D9) | 393 | — |
| **Linhas da listagem** | **392** | **347** |
| Emissores | 307 | 346 |
| Sem nome de pregão (usa o nome do ativo — F1) | 28 | todos |

- ETF, BDR, Tesouro, Renda Fixa e Cripto não têm emissor em `issuers` e ficam fora (D6). As emissoras de renda fixa
  (`fixed_income_issuers`) existem, mas não têm indicadores.
- Fracionário = a regra que o sistema já usa (`IndicatorService.baseTicker`: termina em F, mais de 5 caracteres,
  dígito antes do F). PETR4F usa o preço, os eventos e os proventos de PETR4: os indicadores são idênticos.

### 1.2 Casos de borda (D8 — todos aparecem, com marca de "dado antigo")

| Caso | Quantos | O que mostra |
|---|---|---|
| FII com registro `FORA_DE_BOLSA` na CVM | 41 (34 negociaram nos últimos 15 dias) | tudo — informe de ago/set-2026 |
| Sem pregão há mais de 15 dias (em 17/10) | 15 ações, 26 FIIs (último negócio 31/08–30/09) | tudo; P/L, P/VP e DY com o último fechamento |
| Balanço antigo | 9 empresas / 18 tickers (último balanço 30/06/2025–31/12/2025) | tudo, calculado sobre esse balanço |
| VGRH11 | 1 | sem situação de registro nem informe: só preço e DY |
| PL negativo (passivo a descoberto) | 41 empresas | aparecem depois dos positivos na ordem padrão |

### 1.3 O que já existe e é reaproveitado

| Peça | Onde | Uso aqui |
|---|---|---|
| Indicadores "Atual" por ticker | `IndicatorService.current` (app-api) | as regras; a leitura passa a aceitar lote (4.1) |
| Fórmulas | `IndicatorRules` | iguais, sem mudança |
| Logo | `AssetLogos.hasLogo` (core) + `AssetLogo` (web) | igual à Carteira |
| Setor B3 / segmento FII | `issuers.b3_sector`, `b3_subsector`, `b3_segment`; `issuers.segment` | colunas e filtros |
| Patrimônio líquido | `company_quarters.equity_parent` (último balanço); `fii_monthly.net_assets` (último informe) | coluna e ordem padrão |
| Abas na URL | `DividendsPage` (`?aba=`) | mesmo padrão |
| Ordenação | `hooks/useSortable` | ganha estado controlado, desempate e padrão (5.4) |
| Paginação | `components/ui/Pagination` (10 por página; 10, 25, 50, 100) | igual a Transações |
| Ícone de ação | `TABLE_ACTION_CLASS` | coluna AÇÃO |

**Custo de hoje:** `current(ticker)` lê todo o histórico de fechamentos do ticker (desde 2010) e faz ~6 consultas por
ticker. Para 392 tickers seriam ~2.400 consultas e ~1,5 milhão de linhas de preço — por isso a leitura em lote.

---

## 2. Decisões

### 2.1 Tomadas (17/10/2026)

| # | Decisão |
|---|---|
| D1 | Entra o **catálogo todo**, não só a carteira |
| D2 | "Valor patrimonial" = **patrimônio líquido**: PL da controladora (ação) e PL do informe (FII) |
| D3 | Uma linha **por ticker** (P/L, P/VP e DY mudam entre PETR3 e PETR4) |
| D4 | **Logo + ticker** numa coluna e o **nome** na coluna ao lado |
| D5 | Colunas: ações — Preço, P/L, P/VP, DY, ROE, Payout, LPA, VPA, PL; FIIs — Preço, P/VP, DY, VP/cota, Cotistas, % Imóveis, % Papel, Alavancagem, PL; mais setor/segmento |
| D6 | Abas **Ações** e **FIIs**. Renda Fixa fica fora (as emissoras de RF não têm indicadores) |
| D7 | A busca continua no topo, acima das abas, com o mesmo comportamento (leva à emissora); a listagem ocupa o espaço vazio de hoje |
| D8 | Todos aparecem; preço ou balanço/informe antigo ganha uma **marca** com a data no hover |
| D9 | **Sem fracionários** |
| D10 | Ações com **setor, subsetor e segmento** da B3; FIIs com o segmento |
| D11 | Ordem padrão **PL decrescente**; as outras colunas ordenáveis; **desempate pelo ticker** |
| D12 | **Paginação de Transações**; filtros nas abas por **nome** e **faixa de indicadores** |
| F1 | Nome: ações → **nome de pregão** (sem ele, o nome do ativo); FIIs → **nome do ativo**; **razão social no hover** |
| F2 | Setor das ações numa coluna só: **setor** em cima, "**subsetor › segmento**" embaixo, em letra menor |
| F3 | Filtros: nome por texto (ticker ou nome, sem acento e sem caixa); botão **Filtros** com painel mín–máx de **todas** as colunas numéricas e "Limpar filtros"; setor → subsetor → segmento em seleção dependente (FIIs: segmento); percentuais em pontos; PL em **R$ milhões**; com a faixa de um indicador preenchida, quem tem o indicador vazio sai; vazios sempre no fim da ordenação; aba, ordem, página e filtros **na URL**, cada aba com os seus |
| F4 | "Dado antigo": preço sem pregão há **mais de 7 dias corridos**; balanço com **mais de 6 meses** desde o fim do trimestre; informe do FII com **mais de 3 meses** |
| — | Indicadores calculados em **lote** no backend, pelas mesmas regras da página da emissora |

### 2.2 Propostas (todas resolvidas em 17/10/2026)

| # | Pergunta | Situação |
|---|---|---|
| A1 | Formato do PL na coluna | **Tomada (17/10):** abreviado — `R$ 481,0 bi`, `R$ 7,57 bi`, `R$ 350,2 mi` — com o valor cheio no hover; negativo com sinal, na cor de negativo do app |
| A2 | Onde fica a marca de "dado antigo" | **Tomada (17/10):** ícone de relógio no amarelo do tema **na célula do Preço** (preço antigo: "Último pregão em dd/mm/aaaa") e **na célula do PL** (base antiga: "Balanço de 30/06/2025" ou "Informe de ago/2026") |
| A3 | A página de uma emissora (`/emissoras/{ticker}`) passa a mostrar o patrimônio líquido? | **Fora deste escopo (17/10):** vai para o escopo novo "Novos indicadores de ações", junto com os indicadores pedidos no mesmo dia — `docs/PENDENCIAS.md`, seção 31 |

---

## 3. Regras da listagem

1. **Quem entra:** `assets.active`, `issuer_id` preenchido, emissor `COMPANY` (aba Ações) ou `FII` (aba FIIs), **não
   fracionário**. Situação do registro do emissor não filtra (D8).
2. **Valores:** os mesmos do "Atual" de `GET /assets/{ticker}/indicators` — preço atual (`current_price` da BRAPI, senão o
   último fechamento), último balanço/informe, quantidade ajustada por eventos, DY do próprio ticker, nota quando o
   valor não se aplica (ex.: P/L com lucro negativo). Nota aparece no hover do "—".
3. **PL:** ação — `equity_parent` do último balanço; FII — `net_assets` do último informe. Sem base, vazio.
4. **Dado antigo** (F4), limites em `primevest.emissoras.dado-antigo.*` no `application.yml` da app-api, ajustáveis sem
   código: `preco-dias: 7`, `balanco-meses: 6`, `informe-meses: 3`. O backend devolve as datas e os dois sinais
   (`priceStale`, `baseStale`).
5. **Ordem padrão:** PL decrescente; vazios no fim; desempate pelo ticker crescente. Clique numa coluna: crescente →
   decrescente → volta ao padrão. Em qualquer ordem, vazios no fim e desempate pelo ticker. A coluna Setor ordena por
   setor, subsetor, segmento.

---

## 4. Backend (app-api)

### 4.1 Leitura em lote no `IndicatorService`

O `Context` hoje se monta sozinho, um ticker por vez. Ele passa a poder ser montado **a partir de dados já lidos**, e
o serviço ganha `currentAll(IssuerKind kind)`, que lê tudo de uma aba em poucas consultas por conjunto:

| Dado | Consulta (uma por aba) |
|---|---|
| Ativos | `assets` + `issuers` (filtro da regra 1), com o lote padrão e a composição da unit |
| Preço atual | `current_price`/`price_updated_at` + **só o último fechamento** de cada ativo (`DISTINCT ON`) |
| Eventos societários | `asset_stock_events` dos ativos da aba |
| Proventos | `asset_cash_events` com data-com nos últimos 13 meses |
| Ações | `company_quarters` + `cvm_documents` dos emissores da aba (~19 mil linhas — o ROE precisa do balanço de 12 meses antes e o fallback da quantidade, dos anteriores) |
| FIIs | último informe de cada emissor (`fii_monthly`, agora com `net_assets`) |

O "Atual" continua saindo de `currentCompany`/`currentFund` — **o mesmo código** que atende a página da emissora; só a
origem dos dados muda. O histórico (`history`, `historyAll`, `companyFundamentals`) não é tocado.

Meta: a aba inteira em **menos de 1 s** na base real. Sem cache na primeira versão; se passar disso, cache de 10 min
no Redis por aba (o dado muda no máximo duas vezes por dia: COTAHIST 21:30/08:30 e balanços).

### 4.2 Endpoint

`GET /api/v1/assets/indicators?kind=COMPANY|FII` (no `IndicatorController`, ao lado do `/{ticker}/indicators`) →
`List<IssuerListingRowDTO>`, já na ordem padrão:

```
ticker, name, legalName, issuerId, hasLogo,
sector, subsector, segment,                 // ação: B3; FII: só segment
price, priceDate, priceStale,
basePeriodEnd, baseStale,                   // fim do trimestre (ação) ou fim do mês do informe (FII)
equity,                                     // PL em R$
indicators: [{ indicator, unit, value, note }]   // os do tipo, na ordem do enum
```

Usuário autenticado, como as outras leituras de indicador. A aba inteira vai de uma vez (~100 KB); ordem, filtros e
paginação são feitos no navegador, como em Transações.

---

## 5. Web

### 5.1 Página

`IssuerPage` sem ticker: `IssuerSearch` no topo (igual) → abas **Ações | FIIs** → barra da aba (campo de nome, botão
**Filtros** com o número de filtros ativos, "Limpar filtros", contador "N de M") → tabela → `Pagination`.
Componentes novos em `components/issuer/`: `IssuerListing` (abas e estado na URL), `IssuerListingTable`,
`IssuerListingFilters`.

### 5.2 Colunas

| Ações | FIIs |
|---|---|
| ATIVO (logo + ticker, como na Carteira) | ATIVO |
| NOME (razão social no hover) | NOME |
| SETOR (setor / subsetor › segmento) | SEGMENTO |
| PREÇO | PREÇO |
| P/L · P/VP · DY · ROE · PAYOUT · LPA · VPA | P/VP · DY · VP/COTA · COTISTAS · IMÓVEIS · PAPEL · ALAVANCAGEM |
| PATRIMÔNIO LÍQUIDO | PATRIMÔNIO LÍQUIDO |
| AÇÃO (ícone, `TABLE_ACTION_CLASS`, title "Abrir emissora") | AÇÃO |

Formatos iguais aos cartões da página da emissora (`IndicatorGrid`): percentuais com 2 casas, múltiplos com 2, R$
com 2, cotistas inteiro. Vazio = "—" com a nota no hover. PL conforme A1, marca conforme A2.

### 5.3 Filtros (F3)

- **Nome:** texto que estreita a tabela por ticker ou nome (contém), sem acento e sem caixa — a mesma normalização do
  `LocalAssetSearch`. Não navega.
- **Setor → Subsetor → Segmento** (Ações) e **Segmento** (FIIs): seleções com as opções que existem na aba; a de baixo
  só mostra o que cabe na de cima.
- **Faixas:** mín e máx por coluna numérica, com a máscara numérica do projeto. Percentuais em pontos (DY 6 = 6%), PL
  em R$ milhões, Cotistas inteiro. Faixa preenchida exclui quem tem o indicador vazio.
- Qualquer mudança de filtro volta para a página 1.

### 5.4 Estado na URL

`?aba=acoes|fiis` e, por aba, parâmetros com prefixo (`acoes.ord=dy.desc`, `acoes.pag=2`, `acoes.qtd=25`,
`acoes.nome=banco`, `acoes.setor=Financeiro`, `acoes.dy=6~12`, `fiis.pvp=~1`...). Trocar de aba mantém os filtros da
outra; voltar da emissora pelo navegador restaura a lista como estava. Ao abrir uma emissora pela lista (ícone ou
busca), só os parâmetros do gráfico (`periodo`, `de`, `ate`) seguem para a URL da emissora.

`useSortable` ganha, sem mudar quem já usa: estado inicial vindo da URL com callback de mudança, ordem padrão para o
terceiro clique e chave de desempate.

---

## 6. Testes

| Teste | O que prova |
|---|---|
| `IndicatorServiceTest` (lote) | `currentAll` = `current` ticker a ticker nas fixtures (ação comum, unit, banco, balanço em revisão, FII, sem informe) |
| `ListagemEmissorasIT` (functional) | endpoint nas duas abas: fracionário fora, inativo fora, sem emissor fora, ordem padrão com empate (PETR3/PETR4), PL negativo antes dos vazios, `priceStale`/`baseStale` nos limites, 401 sem login |
| Conferência na base real (validação) | para os 739 tickers, a linha da lista = `GET /assets/{ticker}/indicators`; tempo de cada aba |

---

## 7. Etapas

| Etapa | Entrega | Depende |
|---|---|---|
| **L1** | `IndicatorService.currentAll` (leitura em lote, `net_assets` no informe) + testes de equivalência | — |
| **L2** | DTO, endpoint, regras de nome/setor/dado antigo, limites no `application.yml` + `ListagemEmissorasIT` | L1 |
| **L3** | Web: tipos e API, `IssuerListing` (abas, tabela, colunas, marca, ação), `useSortable` estendido, paginação | L2 |
| **L4** | Web: filtros (nome, setor, faixas) e estado na URL | L3 |
| **L5** | Documentação: `Claude.md` (Emissoras em Telas, endpoint na tabela de API), `docs/PENDENCIAS.md`, `README.md`, andamento deste plano | L1–L4 |
| **L6** | Validação: compilar, bateria (core e app-api), lint e build do web, `docker compose up -d`, conferência dos 739 tickers e do tempo, conferência no navegador com o Guilherme logado | L1–L5 |

### Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| Decisões D1–D12, F1–F4 | preparou | decidiu (17/10) |
| A1–A3 | propôs | A1 e A2 confirmadas; A3 para escopo novo (17/10) |
| L1–L5 | implementa e testa | — |
| L6 validação | compila, testa, sobe, confere | confere a tela logado |

---

## 8. Riscos e limitações

- **Equivalência com a página da emissora:** o lote só vale se o "Atual" sair do mesmo código. Por isso a mudança é na
  origem dos dados do `Context`, não uma segunda implementação das regras; a conferência dos 739 tickers fecha isso.
- **Setor B3 de empresas fora de bolsa ou recém-listadas:** hoje os 307 emissores com ticker ativo têm os três níveis;
  emissor novo sem setor aparece com "—" e fica fora dos filtros de setor até a integração preencher.
- **Limite de balanço (6 meses):** no fim de março, quem ainda está no balanço de 30/09 encosta no limite enquanto o
  DFP não sai; o limite é ajustável no `application.yml`.
- **Preço da BRAPI × COTAHIST:** os ativos da carteira usam o preço da BRAPI (mais recente); o resto do catálogo, o
  fechamento do COTAHIST. É a mesma regra da página da emissora.

---

## 9. Andamento

| Etapa | Estado |
|---|---|
| Levantamento | **12–17/10/2026** — D1–D12 e F1–F4 respondidas |
| Planejamento | **17/10/2026** — plano fechado: todas as decisões tomadas; A3 e os indicadores novos foram para escopo próprio (`docs/PENDENCIAS.md`, seção 31). Aguarda ordem de execução |
| Execução | **17/10/2026** — autorizada ("Vamos à implementação"), todas as etapas sem pausa |
| L1 | **Feito.** `IndicatorService.currentAll(kind)`: uma consulta de ativos com o último fechamento (`LATERAL ... LIMIT 1`, pelo índice), composição das units, eventos societários, proventos dos últimos 12 meses, trimestres dos emissores (ação) ou o último informe (FII, `DISTINCT ON`). O `Context` ganhou um construtor com os dados já lidos e o "Atual" saiu para `currentOf`, usado pelo `current(ticker)` e pelo lote — o mesmo código. `fii_monthly.net_assets` entrou no informe lido. **Fracionário:** sai só quando o lote padrão está na lista; os 17 que só existem no fracionário (EKTR3F, FRIO3F...) ficam, pelo caminho de um ticker (`docs/PENDENCIAS.md` 32.1) — a aba Ações tem **409** linhas, não 392 |
| L2 | **Feito.** `IssuerListingRowDTO`, `IssuerListingService` (nome F1, setor/segmento, logo, marcas F4, ordem D11) e `GET /api/v1/assets/indicators?kind=` no `IndicatorController`; limites em `primevest.emissoras.dado-antigo.*` (`application.yml`). `ListagemEmissorasIT` (5): aba Ações sem fracionários e na ordem com empate e PL negativo, mesmos valores da página da emissora (5 tickers), marcas de dado antigo nos limites, aba FIIs (PL do informe, segmento, nome do ativo), 401 e 400. O teste de equivalência ficou no IT e na conferência da base real (L6), não num `IndicatorServiceTest` — o serviço precisa do banco |
| L3 | **Feito.** `IssuerListing` (abas na URL), `IssuerListingTable` (colunas 5.2, relógio de dado antigo no Preço e no PL, PL por `formatCompactCurrency` com o valor cheio no hover e negativo em vermelho, nota do indicador vazio no hover, ícone AÇÃO, `Pagination`), `IssuerPage` sem ticker mostra a listagem e a busca leva só o período do gráfico. **Ordenação** própria em `utils/issuerListing.ts` (`sortRows`/`nextSort`), em vez de estender o `useSortable`: o padrão (PL decrescente), o desempate e o estado na URL não cabiam nele sem mudar quem já o usa |
| L4 | **Feito.** `IssuerListingFilters`: setor → subsetor → segmento (FIIs: segmento) e mín–máx de cada coluna numérica. Nome por texto (ticker, nome ou razão social, sem acento). Estado por aba na URL (`acoes.dy=10.5~`, `fiis.pag=2`...); qualquer filtro volta para a página 1. **Ajuste de 17/10/2026 (pedido do Guilherme):** a faixa usa a máscara do projeto com 2 casas (cotistas sem casas) e, só nas colunas que podem ser negativas (ROE, LPA, VPA, VP/cota e PL), o `SignedNumberInput` do Preço Teto — movido para `components/ui/`. O motivo do campo próprio da primeira versão era o `MaskedNumberInput` tratar vazio e zero como a mesma coisa (vazio manda 0, 0 aparece vazio), e no filtro vazio é "sem limite" e zero é um limite; ele ganhou `allowEmpty`/`onClear` para isso, sem mudar os formulários. Conferido no navegador: ROE ≥ −5,00 (± e 500), P/VP ≥ 0,00 e o backspace esvaziando; Preço Teto com os campos e o placeholder de antes |
| Ajustes | **17/10/2026 (pedidos do Guilherme na conferência):** (1) a busca de emissora saiu do topo da listagem e foi para a direita das abas, com o "+" e o comportamento do "Novo cálculo" do Preço Teto (com ticker, continua no topo); (2) PL da tabela sempre com 2 casas também em milhões (R$ 972,63 mi), por um formatador da listagem — os demonstrativos seguem com o `formatCompactCurrency`; (3) filtros em blocos (rótulo e unidade em cima, mín e máx lado a lado, mínimo de 400 px por bloco): o campo do PL foi de ~120 px para ~214 px numa tela de 1500 px. Conferido no navegador: VALE3 + "+" abre a emissora e o "voltar" devolve o filtro. (4) Painel de filtros compactado: blocos sem borda, espaçamentos menores, fonte pequena e campos de 32 px (`SignedNumberInput` ganhou `size="sm"`; o Preço Teto segue no tamanho normal), mínimo de 300 px por bloco — numa tela de 1640 px o painel caiu de ~445 px para 289 px de altura, em 4 colunas, com o campo do PL em ~110 px. (5) 18/10/2026: o "+" e o Enter da busca (`IssuerSearch`, também no Preço Teto) só abrem a opção destacada ou um ticker que esteja nas opções; com um nome digitado ("COPASA") nada acontece — antes caía na tela "Não achamos o ticker COPASA". Conferido: COPASA + "+" e Enter ficam na lista; CSMG3 + "+" abre a emissora. (6) 18/10/2026: o Preço Teto usa a mesma busca e listava FIIs — `GET /assets/?withIssuer=true` ganhou `issuerKind` (consulta `a.issuer.kind IN :kinds`) e o `IssuerSearch` a prop `issuerKind`, que o Preço Teto passa como `COMPANY`; teste em `TelaEmissorasIT` (HGLG some, PETR4 fica, tipo inválido = 400). A busca da listagem ficou com `max-w-md` (o texto de exemplo era cortado pelo "+"; agora sobra espaço numa tela de 1640 px). (7) 18/10/2026: spinner eterno no `IssuerSearch` (Emissoras e Preço Teto) — a limpeza do efeito cancelava a busca agendada e o spinner só desligava no `finally` dela; corrigido como no `TickerAutocomplete` (desliga abaixo de 2 caracteres) e com a resposta de um texto antigo ignorada. Conferido: "VA" + apagar rápido sem spinner; VALE3 traz as opções e o spinner some |
| L5 | **Feito.** `Claude.md` (endpoint e "Emissoras" em Telas), `docs/PENDENCIAS.md` (seção 32), `README.md` (Funcionalidades) e este andamento |
| L6 | **Feito.** `mvn clean verify` verde (6 min 23 s): core 190; integration 103; adm-api 116 + 49; app-api 210 + 261 (+5 do `ListagemEmissorasIT`). `npm run lint` limpo e `npm run build` ok. **Base real:** as 409 linhas de ações e as 347 de FIIs conferidas uma a uma contra `GET /assets/{ticker}/indicators` — **0 diferenças** em preço, data da base e indicadores; a aba responde em ~150–420 ms (ações) e ~60–120 ms (FIIs), sem cache. Navegador (logado): abas, filtros vindos da URL, faixa com decimal (DY ≥ 10,5 → 9 de 409), "Limpar filtros", ordenação P/L ↑ → ↓ → padrão, relógio de balanço antigo (BSLI3: "Balanço de 30/06/2025"), ícone AÇÃO abrindo a emissora e o "voltar" restaurando o filtro; console sem erro |
| Encerramento | **18/10/2026** — escopo encerrado pelo Guilherme, depois da conferência e dos ajustes 1–7 |
