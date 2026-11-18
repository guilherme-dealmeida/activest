# Activest — Plano dos eventos do papel em Transações

> Fase: **Encerrado em 18/11/2026** — as 7 etapas feitas, com a mudança da Review (bonificação a custo zero no preço
> médio, como a corretora, seção 8.4); commit `146d223` na `TRACK_01-bonificacao`, feito pelo Guilherme, que declarou o
> escopo encerrado. D1–D11 e D15–D18 aprovadas pela recomendação do Claude.
> Origem: dúvidas de 17/11/2026 (KLBN4: 600 cotas na Carteira × 597 somadas em Transações; onde a bonificação
> está gravada; outros ativos com bonificação), que viraram o ESCOPO "Tornar eventos em transações Activest"
> (18/11/2026, REGRAS_CLAUDE.md 2.9), ampliado para **bonificação, desdobramento e grupamento**.

---

## 1. Objetivo

Hoje a tela de Transações só mostra o que o usuário lançou (compras e vendas). Os eventos do papel (bonificação,
desdobramento e grupamento) mudam a quantidade e o custo da posição na Carteira, mas não aparecem no extrato, e a soma
das quantidades de Transações não bate com a Carteira (KLBN4: 597 × 600).

O objetivo é que **cada evento que mexeu numa posição apareça no extrato de Transações**, com quantidade e custo, de modo
que o extrato de uma posição explique a Carteira linha a linha — sem mudar a regra que hoje calcula a posição.

---

## 2. Discovery (17–18/11/2026)

### 2.1 Como a posição conta os eventos hoje

- **O evento é catálogo**, igual para todos: `asset_stock_events` (tipo, data-com, fator no formato da B3, custo
  atribuído — sempre nulo nas 6.590 linhas —, fonte `B3` ou `MASSIVE`). Quem grava é a adm-api, na tarefa de proventos
  (`DividendCatalogService.saveStockEvent`, lista `stockDividends` da B3) e, no exterior, na `FOREIGN_DIVIDENDS`
  (`ForeignCorporateEventsService`, desdobramentos do Massive).
- **O custo da bonificação informado pelo usuário é dado de usuário**: `stock_event_costs` (posição + evento → custo por
  ação), gravado pela app-api no "Informar custo" (`BonusCostService.setCost`).
- **Nada do evento vira linha de `transactions`.** A posição é calculada na leitura (`PositionCalculator`, core):
  `positionsOf` carrega numa consulta cada as transações da carteira, os eventos dos ativos (`findByAssetIds`) e os custos
  do usuário (`userCosts`); `AssetStockEvent.toStep(custoDoUsuario)` vira `StockEventStep` (custo do catálogo, senão o do
  usuário, senão zero provisório); o `Walk` percorre as transações em ordem e aplica cada evento **a partir do dia
  seguinte à data-com**, uma vez; `CorporateActionApplier` faz a conta:

| Evento | Cotas | Custo | Fração |
|---|---|---|---|
| Desdobramento (fator = % de ações novas: 100 = 1 vira 2) | × (1 + fator/100), para baixo | igual, menos a parte da fração | vendida em leilão pela B3 (o dinheiro não entra no Activest) |
| Grupamento (fator = multiplicador: 0,1 = 10 viram 1) | × fator, para baixo | igual, menos a parte da fração | idem |
| Bonificação (fator = % de ações novas: 1 = 1 a cada 100) | + cotas × fator/100, para baixo | + novas × custo atribuído (ou zero provisório) | não entra (não tinha custo) |

- Evento com a posição zerada (antes da primeira compra ou depois da venda total) não muda nada.

### 2.2 Como a tela de Transações monta a lista hoje

- `GET /api/v1/transactions/` (`TransactionService.getTransactions`) devolve **todas** as transações do usuário em
  `TransactionResponseDTO` (id UUID, tipo `BUY`/`SELL`, origem `MANUAL`/`AUTO`/`SISTEMA`…); a única tela que lê é a
  `TransactionsPage`.
- Filtros, busca, ordenação e paginação são **no navegador**: busca por ticker ou nome, Tipo de Ativo, Categoria/Ativo,
  emissora/distribuidora (renda fixa), **Origem** (Digitadas / Importadas / Automáticas), **Período** (30/60/90 dias,
  todo); `?position=` e `?ticker=` do ícone Listar; 10 por página.
- Colunas: ATIVO (logo, selo de origem), TÍTULO, DATA, TIPO ATIVO, TIPO (Compra/Venda), QUANTIDADE, PREÇO, TOTAL, AÇÕES
  (Listar, Comprar, Vender, Editar, Restaurar, Excluir).
- `transactions.type` tem a constraint `ck_transactions_type` (`BUY`, `SELL`).

### 2.3 Eventos no catálogo e na carteira (consulta de 17/11/2026)

| Tipo | Fonte | Eventos | Desde | Até | Desde 2024 (B3) |
|---|---|---|---|---|---|
| Bonificação | B3 | 310 | 1981 | 2026-04-09 | 108 |
| Desdobramento | B3 | 356 | 1997 | 2026-08-10 | 57 |
| Desdobramento | Massive (EUA) | 1.648 | 2010 | 2026-12-01 | — |
| Grupamento | B3 | 397 | 1988 | 2026-10-30 | 78 |
| Grupamento | Massive (EUA) | 3.879 | 2010 | 2026-12-16 | — |

Na carteira do Guilherme (60 posições, 149 transações) **só um evento mexeu numa posição**: a bonificação de 1% da
KLBN4 (data-com 17/12/2025, data-ex 18/12/2025), sobre 300 cotas → +3 a R$ 12,94564989 (custo informado em
25/10/2026) = R$ 38,84. Outros 6 ativos têm bonificação no catálogo (BBAS3, CMIG4, ITSA4, PETR4, SAPR4, VALE3), todas
com data-com antes da primeira compra — não entram. Nenhum desdobramento ou grupamento cai depois de uma compra.
Conferência da KLBN4: compras 597 cotas e R$ 2.078,74 + bonificação 3 cotas e R$ 38,84 = **600 cotas e R$ 2.117,58**
(valor inicial da Carteira; preço médio R$ 3,53).

### 2.4 Quem mais lê os eventos (para não mudar comportamento)

| Onde | Para quê |
|---|---|
| `PositionCalculator` (core) | posição (cotas, preço médio, custo) e `sharesOn` |
| `DividendRecalculator` (core) | cotas na data-com dos proventos |
| `PositionGuard.validateShareLedger` (app-api) | regra B (venda sem saldo) com os eventos no meio |
| `ForeignSaleTax` (app-api) | custo médio para o IR da venda no exterior |
| `PortfolioTimelineBuilder` (app-api) | Rentabilidade dia a dia |
| `BonusCostService`, `DailyOpeningService` (app-api) | pendência "Informar custo" e aviso `EVENTO_APLICADO` na data-ex |
| `AssetPriceHistoryService`, `PriceSeries`, `IndicatorService` | ajuste das cotações e indicadores (catálogo, não posição) |
| Integração B3 (`B3EquityImportRules`) | movimento de evento do papel vira `STOCK_EVENT` e **não** é importado ("o catálogo já aplica", B8) |

### 2.5 Achados laterais (não são o escopo; ver D9)

- **Rentabilidade sem o custo informado**: `PortfolioTimelineBuilder` monta os passos com `e.toStep(null)`, ignorando
  `stock_event_costs`. Não afeta TWR (bonificação não é fluxo de caixa) nem o valor de mercado; a conferir se algum
  número de "investido" da tela usa o custo da timeline.
- **Escrita da posição sem o custo informado**: `TransactionService.afterPositionChange` usa
  `stockEventsOf(pa.getAsset())` (sem custo do usuário) na regra I e no IR do exterior. Sem efeito prático hoje
  (bonificação só vem da B3; o exterior só tem desdobramento/grupamento), mas é a mesma inconsistência.
- **Fração de leilão**: no desdobramento/grupamento com fração, a B3 vende a fração e paga em dinheiro; o Activest tira
  a fração e o custo proporcional, mas o dinheiro não entra em lugar nenhum.

---

## 3. Decisões

> **18/11/2026 — o Guilherme aprovou D1 a D11 pela recomendação do Claude** ("seguir para a próxima etapa com as
> decisões sendo suas recomendações de D1 a D11").

| # | Pergunta | Opções | Recomendação do Claude |
|---|---|---|---|
| **D1** | A linha do evento é **calculada** ou **gravada**? | (a) **calculada na leitura** pelo mesmo `PositionCalculator`, sem tabela nova; (b) gravada em `transactions` com tipos novos (`BONUS`, `SPLIT`, `REVERSE_SPLIT`, origem `SISTEMA`) e a posição passando a ler as transações | **(a)**. A posição continua com uma fonte só (catálogo + cálculo); compra retroativa, exclusão, correção do catálogo e custo informado aparecem sozinhos; nada muda em Carteira, Rentabilidade, Proventos, Metas, IR, fundos e integrações. A (b) mexe em ~20 classes que hoje tratam `BUY`/`SELL` como dinheiro (aportes, XIRR, Metas, IR, regra B, regra K, Tesouro…), exige regerar as linhas a cada escrita e a cada mudança do catálogo e arrisca contar o evento duas vezes (seção 4.3) |
| **D2** | Quais eventos entram no extrato? | (a) só os que mexeram na posição (posição com cotas na data-com e cotas/custo mudaram); (b) todos os eventos dos ativos que o usuário tem | **(a)** — o extrato é da posição; BBAS3 1996 não diz nada a quem comprou em 2024 |
| **D3** | Data da linha | (a) **data-ex** (pregão seguinte à data-com, quando o evento passa a valer — a mesma do aviso `EVENTO_APLICADO`), com a data-com embaixo/no hover; (b) data-com | **(a)**. É o dia em que a quantidade muda; na mesma data, a linha do evento fica antes da compra do dia (a compra na data-ex não recebe) |
| **D4** | Quantidade | (a) a **variação com sinal** (+3, +900, −95) e, embaixo, "300 → 303"; (b) só a quantidade final | **(a)**: é o que faz a soma do extrato filtrado bater com a Carteira, inclusive no grupamento |
| **D5** | Preço e total | Bonificação: preço = custo por ação (catálogo ou informado), total = novas × custo (KLBN4: R$ 12,94564989 e R$ 38,84); sem custo: "custo zero provisório" + ação Informar custo. Desdobramento/grupamento: preço "—", total "—" (o custo não muda) ou a variação do custo pela fração | Como descrito, com **"—"** no desdobramento/grupamento e a parte da fração (custo que saiu) só no hover, quando houver |
| **D6** | Ações na linha do evento | Listar (posição); Informar/alterar custo (bonificação); Comprar/Vender (preset da posição); **sem** Editar e Excluir (o evento é do catálogo) | Listar + Informar custo; sem Comprar/Vender (a linha não é operação) e sem Editar/Excluir, com o (i) explicando |
| **D7** | Como filtrar | (a) nova opção no filtro Origem: "Eventos do papel"; (b) filtro novo "Tipo" (Compra / Venda / Eventos); (c) os dois | **(a)** — menos um controle; a linha ganha o selo "Evento B3" (ou "Evento EUA" no Massive) no lugar do selo de origem |
| **D8** | Fração vendida em leilão (2.5) | (a) fora do escopo, vira pendência; (b) registrar o dinheiro da fração (exige fonte do valor do leilão, que a B3 não publica na mesma API) | **(a)**: mostrar no hover que houve fração ("1,5 cota vendida em leilão pela B3, paga em dinheiro") e registrar em PENDENCIAS |
| **D9** | Achados laterais 2.5 (Rentabilidade e escrita da posição sem o custo informado) | (a) corrigir neste escopo (passar o custo do usuário nos dois pontos, com teste); (b) só registrar em PENDENCIAS | **(a)** — são duas linhas, no mesmo assunto, e deixam todas as leituras com a mesma regra do custo |
| **D10** | Avisos `EVENTO_APLICADO` e `BONIFICACAO_SEM_CUSTO` | (a) ganham o link "Ver transações" (`/transactions?position=<id>`) além do "Ver carteira"; (b) como estão | **(a)** |
| **D11** | Contrato da API | (a) **endpoint próprio** `GET /api/v1/transactions/stock-events` com um DTO de evento, e o front junta as duas listas; (b) as linhas de evento dentro do `GET /transactions/` no mesmo `TransactionResponseDTO` | **(a)**: o DTO de transação tem id editável e excluível em toda a tela; misturar abriria Editar/Excluir/Restaurar numa linha que não é transação |

---

## 4. Impact

### 4.1 Componentes afetados (com a recomendação de D1 = a e D11 = a)

| Camada | O que muda |
|---|---|
| core | `PositionCalculator`: método puro que percorre o mesmo `Walk` e devolve **cada evento aplicado** (evento, data-com, data-ex, cotas antes/depois, custo antes/depois, fração); `StockEventStep` passa a carregar o id do evento (construtor atual mantido). Sem migration |
| app-api | `TransactionService` (ou serviço novo no mesmo pacote): `stockEventEntries(userId)` com as mesmas consultas em lote do `positionsOf` (transações, eventos, custos); `TransactionController`: `GET /stock-events`; DTO novo (`StockEventEntryDTO`). D9: `PortfolioTimelineBuilder` e `afterPositionChange` com o custo do usuário. D10: links no `DailyOpeningService` e no `BonusCostService` |
| web | `types`, `api/transactions.ts`; `TransactionsPage`: lista unida (transações + eventos), linha do evento (selo, tipo, quantidade com sinal, preço/total, ações), opção no filtro Origem, busca/Listar/Período/ordenação/paginação valendo para as duas; função pura em `utils/` para juntar e ordenar (com teste Vitest); `InformCostModal`/`BonusCostBadge` passam a invalidar `['transactions']` |
| docs | `Claude.md` (Transações, endpoint, "Fluxo de Transações → Portfólio"), `docs/PENDENCIAS.md` (fração de leilão; o que ficar de fora), `README.md`, andamento deste plano |

**Não mudam**: schema, adm-api, integrações, Carteira, Dashboard, Dividendos, Rentabilidade (salvo D9), Metas, Preço
Teto, a regra de cálculo dos eventos.

### 4.2 Riscos

| Risco | Mitigação |
|---|---|
| O extrato e a Carteira divergirem (duas contas do mesmo evento) | a linha sai do **mesmo `Walk`** do `PositionCalculator`; critério de aceite compara as somas com a Carteira |
| Linha de evento tratada como transação no front (editar, excluir, preset) | tipo próprio no front e ações próprias; teste da função de junção |
| Ordem no mesmo dia | data-ex com desempate "evento antes da compra do dia" (é a ordem do cálculo) |
| Custo informado sem refletir no extrato | invalidar `['transactions']` ao salvar o custo |
| Performance | 3 consultas em lote (as mesmas da Carteira); 60 posições hoje |
| Fracionário (KLBN4F) | o evento é por ativo do catálogo e a posição é do ativo lançado: cada um com os seus eventos, como hoje |

### 4.3 Se D1 for (b) — gravar em `transactions`

Para registro do alcance: migration (`ck_transactions_type`, possivelmente colunas de evento); `TransactionType` e todos
os pontos que leem `BUY`/`SELL` como dinheiro (`PositionCalculator`, `PositionGuard`, `DividendRecalculator`,
`TreasuryAllocator`, `FixedIncomeSettlement`, `FixedIncomeValuation`, `FundTaxService`, `ForeignSaleTax`,
`PortfolioTimelineBuilder`, `GoalEvaluator`, `GoalFactsLoader`, `GoalService`, `DailyOpeningService`,
`DividendReconciliation`, regras de importação, aportes do Dashboard, XIRR); o cálculo deixaria de ler
`asset_stock_events` (ou passaria a ignorar os eventos já gravados, para não contar duas vezes); uma rotina para
regerar as linhas a cada transação, a cada evento novo ou corrigido do catálogo e a cada custo informado; a regra K
(última transação excluída apaga a posição) teria de ignorar as linhas do sistema. Estimativa: 3 a 4 vezes o trabalho
da (a), com risco em telas que hoje estão certas.

---

## 5. Planning

### 5.1 Solução (com as recomendações)

1. **Cálculo** (core): `PositionCalculator.appliedStockEvents(transações, eventos)` → lista de
   `AppliedStockEvent(eventId, tipo, fator, dataCom, dataEx, cotasAntes, cotasDepois, custoAntes, custoDepois,
   custoPorAção, origemDoCusto [CATALOGO | INFORMADO | PROVISORIO], fração)`, só os que mudaram cotas ou custo. É o
   mesmo `Walk`, gravando o antes e o depois de cada passo de evento.
2. **API** (app-api): `GET /api/v1/transactions/stock-events` → `StockEventEntryDTO` por evento aplicado, com os dados
   da posição que a linha da tela usa (posição, ticker, nome, tipo e categoria do ativo, logo, emissor, fonte do
   evento). Consultas em lote, escopadas pelo dono.
3. **Tela** (web): a lista do extrato vira a união das transações com os eventos; a linha do evento tem o selo "Evento
   B3"/"Evento EUA", TIPO "Bonificação"/"Desdobramento"/"Grupamento" (cor própria), DATA = data-ex com "data-com
   dd/mm" embaixo, QUANTIDADE com sinal e "antes → depois" embaixo, PREÇO/TOTAL conforme D5, AÇÕES conforme D6. Filtro
   Origem com "Eventos do papel"; busca, Tipo de Ativo, Categoria/Ativo, Período, Listar, ordenação e paginação valem
   para as duas.
4. **D9 e D10**, se aprovadas.

### 5.2 Critérios de aceitação

1. KLBN4 filtrada (Listar): 6 linhas — as 5 compras e "Bonificação 1% · 18/12/2025 · +3 (300 → 303) · R$ 12,94564989 ·
   R$ 38,84"; soma das quantidades = **600** (Carteira); soma dos totais = **R$ 2.117,58** (valor inicial).
2. Em qualquer posição, a soma das quantidades do extrato filtrado é igual às cotas da Carteira (teste de integração
   com compra, venda, desdobramento com fração, grupamento com fração e bonificação com e sem custo).
3. Evento antes da primeira compra ou depois da venda total não aparece (BBAS3, PETR4…).
4. Informar o custo na Carteira atualiza a linha do extrato sem recarregar a página.
5. A linha do evento não tem Editar/Excluir/Comprar/Vender; Listar e (bonificação) Informar custo funcionam.
6. Nada muda nos números de Carteira, Dashboard, Dividendos, Rentabilidade (fora D9) e Metas (`/portfolio/assets`,
   `/dividends/`, `/performance/summary` iguais antes e depois, comparação campo a campo).
7. Bateria verde (core, integration, adm-api, app-api, lint, Vitest, build) e conferência no navegador nos dois temas.

### 5.3 Etapas do plano

| # | Etapa | Conteúdo |
|---|---|---|
| **E1** | Core | `appliedStockEvents` + id do evento no `StockEventStep`; testes unitários (bonificação com/sem custo, desdobramento e grupamento com e sem fração, evento com posição zerada, evento e compra no mesmo dia, venda no meio) |
| **E2** | App-api | endpoint + DTO + serviço em lote; `TransactionsIT` (ou IT próprio) com os critérios 1–3; D9 e D10 se aprovadas, com teste |
| **E3** | Web | tipos, API, função de junção em `utils/` com Vitest, linha do evento, filtro, ações, invalidação no Informar custo; conferência no navegador nos dois temas |
| **E4** | Fechamento | Testing (bateria completa e critério 6), Review, `Claude.md`, `docs/PENDENCIAS.md`, `README.md`, andamento; sugestão da mensagem de commit |

## 8. Review (18/11/2026) — preço médio da corretora

**Apontamento do Guilherme.** O Nubank mostra o preço médio da KLBN4 em **R$ 3,46**; o Activest, em **R$ 3,53**. A
corretora é por onde o usuário acompanha a carteira, então o Activest tem de conversar com ela; e bonificação é
exatamente o assunto deste escopo.

**O que explica a diferença** (conferido): o Nubank conta as 3 ações bonificadas a **custo zero** — R$ 2.078,74 ÷ 600
= R$ 3,4646. O Activest soma o custo atribuído pela Klabin (R$ 800 milhões capitalizados ÷ 61.796.819 ações novas =
R$ 12,9456 por ação, o valor informado em 25/10/2026) — R$ 2.117,58 ÷ 600 = R$ 3,5293. A segunda é a regra da Receita
para o custo de aquisição da ação bonificada (Lei 9.249/1995, art. 10, parágrafo único); a primeira é a que a corretora
mostra. As duas contas estão certas, para usos diferentes: acompanhar com a corretora × apurar o ganho no IR.

Hoje o Activest só sabe uma das duas, e a pendência "Informar custo" empurra o usuário para a fiscal.

### 8.1 Decisões

| # | Pergunta | Opções | Recomendação do Claude |
|---|---|---|---|
| **D12** | Qual preço médio o Activest usa quando a bonificação tem custo | (a) o fiscal (custo atribuído), como hoje; (b) o da corretora (custo zero), sempre; (c) **escolha do usuário em Configurações → Preferências**: "Bonificação no preço médio: sem custo, como a corretora \| com o custo atribuído, como a Receita" | **(c)**, com o padrão **"como a corretora"** (é o número que o usuário vê todo dia). Vale para tudo que usa o custo da posição: preço médio, valor inicial, valorização, variação e rentabilidade clássica da Carteira, extrato, Preço Teto (preço médio). Cotas não mudam |
| **D13** | O outro número aparece? | (a) não; (b) **sim**: na dica (i) do preço médio da Carteira e na linha da bonificação em Transações aparece o outro valor ("Com o custo atribuído da Klabin (Receita): R$ 3,53"), sempre que a posição tiver bonificação com custo conhecido | **(b)** — o usuário acompanha pela corretora e ainda tem o número da declaração à mão |
| **D14** | E o "Informar custo" e a pendência `BONIFICACAO_SEM_CUSTO` no modo "como a corretora"? | (a) somem (o custo não entra no preço médio); (b) **continuam, mas como informativa opcional** ("informe o custo para ter o preço médio da Receita") — sem o ícone amarelo de alerta na Carteira; (c) continuam como hoje | **(b)** no modo corretora; **(c)** no modo Receita |

### 8.2 Impacto (com D12 = c, D13 = b, D14 = b)

- **Banco**: V25 — `users.bonus_cost_basis VARCHAR(10) NOT NULL DEFAULT 'BROKER'` + constraint (`BROKER`, `TAX`)
  (REGRAS 8.1: o default preenche o usuário existente). `stock_event_costs` e `asset_stock_events.assigned_cost`
  continuam guardando o custo.
- **core**: `PositionCalculator` recebe o modo do dono da carteira e, em `BROKER`, monta os passos da bonificação com
  custo zero (o custo fica guardado no passo à parte para o "outro número"); `AppliedStockEvent` com o custo usado e o
  custo atribuído; `Position` com o preço médio/custo da outra base quando houver bonificação com custo.
- **app-api**: preferência no `PUT /users/me/preferences` e no `GET /users/me`; `PortfolioAssetDetailDTO` e
  `StockEventEntryDTO` com o outro valor; `BonusCostService` (pendência informativa e sem o ícone no modo corretora);
  `DailyOpeningService` (texto do aviso de bonificação aplicada); Preço Teto (preço médio da carteira).
- **web**: Configurações → Preferências; (i) do preço médio na Carteira; linha da bonificação em Transações (preço e
  total pela base escolhida, a outra na dica); `BonusCostBadge` só no modo Receita.
- **Testes**: unitários do calculador nas duas bases; IT da preferência (Carteira, extrato, Preço Teto); Vitest da linha.
- **Risco**: trocar a preferência muda o preço médio de todas as posições com bonificação de uma vez — é o esperado;
  a Rentabilidade (TWR) não muda (não usa o custo).

### 8.3 Etapas (se aprovadas)

| # | Etapa | Conteúdo |
|---|---|---|
| **R1** | Core + banco | V25, base no calculador, testes |
| **R2** | App-api | preferência, DTOs com o outro valor, pendência e aviso, Preço Teto, IT |
| **R3** | Web | Preferências, Carteira, Transações, Vitest, conferência nos dois temas |
| **R4** | Fechamento | bateria, Review, docs |

### 8.4 Decisão do Guilherme (18/11/2026) e o que fica em aberto

**Decidido:** o Activest segue a **corretora** (Nubank conferido: preço médio R$ 3,46 e valor investido R$ 2.078,74
na KLBN4). A bonificação entra no preço médio **sem custo**, sempre. O "Informar custo" e as notificações **continuam**,
como **informativo** (o custo atribuído é o número do IR). **D12–D14 ficam sem efeito**; a D5 muda (a linha da
bonificação tem preço e total R$ 0,00).

Consequências, sem decisão a tomar:

- Carteira (KLBN4): preço médio R$ 3,46, valor inicial R$ 2.078,74, valorização −R$ 2,74, rentabilidade clássica
  3,25%. Proventos, TWR, XIRR, aportes e patrimônio não mudam (já tratavam a bonificação assim).
- Sem provento da bonificação (Proventos continua sendo dinheiro recebido).
- O custo informado (e o `assigned_cost` do catálogo, se a B3 um dia trouxer) continua gravado e vira o **custo para o
  IR**. O IR estimado da venda no exterior (`ForeignSaleTax`) continua com o custo do IR — é a regra da lei, não a da
  corretora.
- Desdobramento e grupamento não mudam (não têm custo).
- Sem migration: `BONIFICACAO_SEM_CUSTO` passa de pendência (`ATENCAO`, `pending = true`) a informativa no
  `NotificationKind` (código); hoje não há nenhuma mensagem dela nem de `EVENTO_APLICADO` no banco do dev.

| # | Pergunta | Opções | Recomendação do Claude |
|---|---|---|---|
| **D15** | Onde aparece o número do IR | (a) só na linha da bonificação em Transações; (b) **na linha da bonificação** ("IR: R$ 12,9456/ação · informado", ou "IR: custo não informado") **e na dica (i) do preço médio da Carteira** ("Para o IR, com o custo atribuído: R$ 3,53"); (c) (b) + coluna própria na Carteira | **(b)** |
| **D16** | Ícone da Carteira (`BonusCostBadge`, hoje um alerta amarelo ao lado do preço médio) | (a) sai; (b) **fica só quando o custo do IR falta, em estilo informativo** (ícone de informação, cor neutra), abrindo o Informar custo; (c) como está | **(b)** — informativo, como você pediu, sem parecer erro |
| **D17** | Mensagens da bonificação | (a) **`BONIFICACAO_SEM_CUSTO` informativa** (sem status a resolver, texto "entrou sem custo no preço médio, como na corretora; informe o custo atribuído para ter o preço médio do IR", com o botão Informar custo) e **`EVENTO_APLICADO` também para bonificação com custo conhecido** (hoje já é assim); (b) uma mensagem só por bonificação, sempre `EVENTO_APLICADO`, com o botão quando faltar o custo | **(a)** — mexe só no texto e no tipo da mensagem; uma mensagem por bonificação, como hoje |
| **D18** | Preço médio do Preço Teto | (a) **o mesmo da Carteira** (corretora, R$ 3,46); (b) o do IR | **(a)** |

> **18/11/2026 — o Guilherme aprovou D15 a D18 pela recomendação do Claude e mandou aplicar as etapas.**

### 8.5 Etapas da mudança

| # | Etapa | Conteúdo |
|---|---|---|
| **R1** | Core | bonificação a custo zero no `PositionCalculator` (preço médio e custo), com o custo do IR guardado à parte no passo e no `AppliedStockEvent`; leitura "para o IR" (mesmo percurso com o custo) para a dica e para o `ForeignSaleTax`; testes |
| **R2** | App-api | `StockEventEntryDTO` (custo do IR e origem; variação do custo 0 na bonificação); `PortfolioAssetDetailDTO` com o preço médio do IR quando houver bonificação; `NotificationKind`, `BonusCostService`, `DailyOpeningService` (textos); ajuste do `DailyOpeningIT`, `StockEventEntriesIT`, `CorporateEventsIT` |
| **R3** | Web | linha da bonificação (R$ 0,00 + "IR: …"), dica do preço médio, `BonusCostBadge` informativo, textos; Vitest; conferência nos dois temas |
| **R4** | Fechamento | bateria, Review, docs (`Claude.md`: "Bonificação sem custo", Transações, Carteira; `PENDENCIAS`) |

## 6. Quem faz

| Atividade | Claude | Guilherme |
|---|---|---|
| Discovery, Impact, Planning | ✔ | decide D1–D11 |
| Implementação, testes, review | ✔ (após "mandou executar") | aprova |
| Conferência visual | ✔ (navegador, dois temas) | confere |
| Commit | sugere a mensagem | ✔ |

## 7. Andamento

- 17/11/2026 — dúvidas respondidas (KLBN4 600 × 597: bonificação de 1% de 17/12/2025; onde está gravada; nenhum outro
  ativo da carteira com evento aplicado).
- 18/11/2026 — Discovery, Impact e Planning; D1–D11 aprovadas pela recomendação e execução autorizada.
- 18/11/2026 — **E1** core: `StockEventStep` com o id do evento (construtor de 4 argumentos mantido; `AssetStockEvent.toStep`
  passa o id); `AppliedStockEvent` (antes/depois, fração, custo por ação, data ex pelo `BusinessCalendar`);
  `PositionCalculator.appliedStockEvents` (o mesmo `Walk`, anotando cada evento que mudou cotas ou custo) e
  `appliedStockEventsOf` (lote da carteira; o carregamento virou `load`, comum com `positionsOf`);
  `PositionCalculatorAppliedEventsTest` 6 (KLBN4 real: +3, R$ 38,83694967, extrato = 600 e R$ 2.117,58; grupamento com
  fração e desdobramento depois de venda; evento fora da posição; bonificação sem custo e só-fração; compra na data-com
  × data ex; evento futuro).
- 18/11/2026 — **E2** app-api: `GET /api/v1/transactions/stock-events` (`StockEventEntryService`, `StockEventEntryDTO`
  com `BonusCostOrigin`); `TransactionControllerTest` +1 (e 401), `CorsConfigTest` com o mock novo;
  `StockEventEntriesIT` 3 (extrato = Carteira com grupamento, desdobramento e bonificação; custo informado → `USER` e
  R$ 38,84; escopo do dono e 401). **D9**: a Rentabilidade não precisou mudar — a linha do tempo só usa as cotas
  (`sharesOn`), não o custo; a escrita da posição passou a `stockEventsOf(pa)` (com o custo informado). **D10**: link
  "Ver transações" (`/transactions?position=`) nos avisos `EVENTO_APLICADO` e `BONIFICACAO_SEM_CUSTO`;
  `DailyOpeningService.dataEx` delega a `AppliedStockEvent.exDateOf`. Conferido no dev: a KLBN4 sai com +3,
  R$ 12,94564989, `USER`, R$ 38,84; as 12 ações da Carteira com cotas, valor inicial e proventos iguais ao print de
  17/11/2026.
- 18/11/2026 — **E3** web: tipos e `transactionsApi.stockEvents` (chave `['transactions','stock-events']`);
  `utils/stockEventRows.ts` (linha, rótulos, `mergeLedger`) + Vitest 5; `StockEventLine`; filtro Origem "Eventos do
  papel"; Informar/alterar custo na linha; `['transactions']` invalidado também pelo `BonusCostBadge` e pelas
  Notificações. `tsc -b`, lint e Vitest (194) verdes.
- 18/11/2026 — **E4** Testing: `mvn clean verify` verde (core 266; integration 116; adm-api 171 + 110; app-api 242 +
  319; 7 min 34 s); front: `tsc -b`, lint, Vitest 194 e `vite build` verdes. Critério 6: cotas, valor inicial e
  proventos das 12 ações conferidos contra o print da Carteira de 17/11/2026 (iguais). Review: sem achados abertos.
  Docs: `Claude.md` (cabeçalho, endpoint, Transações, "Eventos do papel no extrato", contagem de testes),
  `README.md`, `docs/PENDENCIAS.md` seção 50.
- 18/11/2026 — **Conferência visual** (navegador do app, com o Guilherme logado), nos dois temas: KLBN4 filtrada com 6
  linhas (5 compras + "Bonificação 1%" em 18/12/2025, data-com 17/12/2025, +3 com "300 → 303", R$ 12,94564989 "custo
  informado", R$ 38,84; só Listar e Informar custo); o Informar custo abre preenchido com 12,94564989 (fechado sem
  salvar); filtro Origem "Eventos do papel" deixa só a linha do evento. Ajuste: o selo "Evento B3" quebrava em duas
  linhas na tela estreita → `whitespace-nowrap`.

- 18/11/2026 — **Review do Guilherme**: o Nubank mostra o preço médio da KLBN4 em R$ 3,46 (bonificação a custo zero) e o
  Activest em R$ 3,53 (custo atribuído). A corretora é a referência do usuário: mudança no escopo, seção 8, decisões
  D12–D14 em aberto.
- 18/11/2026 — **Decisão do Guilherme**: seguir a corretora (bonificação sem custo no preço médio), mantendo o Informar
  custo e as notificações como informativo. D12–D14 sem efeito; D15–D18 em aberto (seção 8.4).
- 18/11/2026 — D15–D18 aprovadas pela recomendação; execução das etapas R1–R4 autorizada.
- 18/11/2026 — **R1** core: `CostBasis` (`BROKER` padrão, `TAX`); o `Walk` aplica a bonificação sem custo na base da
  corretora; `calculate(..., basis)`; `positionsWithTaxOf` (base da corretora de todas as posições e a do IR onde
  difere, uma carga só); `AppliedStockEvent` com `taxUnitCost`/`taxCostDelta` (o `costDelta` é o da corretora, zero na
  bonificação); `CorporateActionApplier` e `StockEventStep` com o texto novo. `PositionCalculatorAppliedEventsTest`:
  KLBN4 = R$ 2.078,74 / R$ 3,46456667 (corretora) e R$ 2.117,58 / R$ 3,52929492 (IR).
- 18/11/2026 — **R2** app-api: `PortfolioAssetDetailDTO.taxAveragePrice/taxInvested` (`PortfolioService` com
  `positionsWithTaxOf`); `StockEventEntryDTO` com `costDelta`, `taxUnitCost`, `taxCostDelta`, `taxCostOrigin`
  (`CATALOG`/`USER`/`MISSING`); `ForeignSaleTax` na base `TAX`; `BONIFICACAO_SEM_CUSTO` informativa (`INFO`, sem
  status) com o texto do IR; `EVENTO_APLICADO` da bonificação com o texto da corretora e o custo do IR. Testes:
  `DailyOpeningIT` (informativa; preço médio da corretora igual depois de informar; o do IR na Carteira),
  `StockEventEntriesIT`, `TransactionControllerTest`, `PortfolioServiceTest`; `NotificationsIT` passou a usar
  `RF_VENCIDO` como exemplo de pendência. Dev: KLBN4 com R$ 3,46456667, R$ 2.078,74, valorização −R$ 2,74
  (regravada na subida), IR R$ 3,52929492 / R$ 2.117,58.
- 18/11/2026 — **R3** web: linha da bonificação com R$ 0,00 e "IR: R$ 12,9456" / "IR: R$ 38,84" (exato e origem na
  dica; "Bonificação 1%" sem quebra); (i) do preço médio da Carteira com o do IR; `BonusCostBadge` com ícone de
  informação neutro; textos do `InformCostModal`. Vitest 195. Conferido no navegador, nos dois temas: KLBN4 na Carteira
  R$ 3,46 · R$ 2.078,74 · −R$ 2,74 · 3,25%, dica "Para o IR … R$ 3,53 (custo R$ 2.117,58)"; extrato com a linha da
  bonificação a R$ 0,00.
- 18/11/2026 — **R4** Testing: `mvn clean verify` verde (core 266; integration 116; adm-api 171 + 110; app-api 242 +
  319; 7 min 22 s); front `tsc -b`, lint, Vitest 195 e `vite build` verdes. Review: sem achados abertos. Docs:
  `Claude.md` (cabeçalho, endpoint, Transações, "Bonificação: preço médio da corretora", Notificações, testes),
  `README.md`, `docs/PENDENCIAS.md` (13.2 revisto, 50.4).
- 18/11/2026 — **Commit** `146d223` (TRACK_01-bonificacao) pelo Guilherme; escopo encerrado.
