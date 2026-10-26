# Activest — Plano de Metas Financeiras (metas calculadas a partir da carteira)

> Fase: **Commit** (etapa 7 de 7) — implementação, testes e revisão concluídos em 24/10/2026; falta a conferência no
> navegador e o commit pelo Guilherme. Mandou implementar todas as etapas do plano sem nova autorização (24/10). Nada de código até a
> aprovação explícita (2.2).
> Origem: ESCOPO "Funcionalidade Metas no Activest" (24/10/2026) — "recuperação do antigo texto do plano
> PLANO_METAS.md no primevest". Nasceu de um trabalho POR DÚVIDAS do mesmo dia (2.9).
> O plano de 13–14/10/2026 nunca foi comitado: estava só num stash do repositório antigo (`E:\repos\primevest`,
> `stash@{0}`, "On TRACK_01-metas", 14/10/2026 16:49, único arquivo `docs/planos/PLANO_METAS.md`, 605 linhas). O texto
> foi lido do stash sem mexer no git e está reproduzido **sem alteração** no **Anexo A**. A branch `TRACK_01-metas`
> não tem commit próprio (aponta para o merge do PR #15, 13/10) e não entra no trabalho.
> Substitui o CRUD manual de metas (`goals` da V1, `GoalService`, `GoalsPage`) e absorve o item **26.3** do
> `docs/PENDENCIAS.md` (Metas usando a caixa de Notificações).

---

## 1. Objetivo

O mesmo do plano de 13/10 (Anexo A, seção 0): **o usuário define o objetivo** (alvo, prazo, faixa, escopo, benchmark,
premissas que queira fixar); **o Activest calcula** situação atual, progresso, quanto falta e — quando houver dados e
premissas — a projeção, a partir da carteira. Sem dado suficiente, a meta diz qual é a limitação (nunca zero, nunca
estimativa apresentada como garantia). Catálogo inicial em 8 categorias, implementável em etapas, com arquitetura
extensível (tipo novo sem tabela, tela nem endpoint novos).

## 2. Discovery — o que mudou desde 13/10/2026

O Anexo A foi escrito antes de 9 escopos encerrados (Rentabilidade, Mudanças de tela, Venda de RF, Emissoras de RF,
Listagem e tela de Ativos, Tarefas, Correção de proventos, Nomes em inglês, Renomeação para Activest). Abaixo, cada
seção do Anexo A contra o código e o banco de hoje.

### 2.1 Metas hoje (Anexo A, 1.1) — sem mudança de conteúdo, com nomes novos

| Peça | Situação em 24/10/2026 |
|---|---|
| `goals` | Igual: agora na `V1__schema.sql` consolidada — `name`, `target_amount` `numeric(18,2)`, `current_amount` digitado, `target_date`, `status` (`ACTIVE`/`COMPLETED`/`CANCELLED`). **0 linhas** |
| Backend | `activest-core/.../domain/goal/` (`Goal`, `GoalRepository`, `GoalStatus`) e `activest-app-api/.../domain/goal/` (`GoalController` em `/api/v1/goals/`, `GoalService` com `findOwnedGoal`, 3 DTOs) |
| Front | `GoalsPage.tsx` (148 linhas), `api/goals.ts`, rota `/goals`, menu "Metas" (ícone `Flag`) entre Preço Teto e Ativos |
| Documentação | `docs/USE_CASES.md` UC08 descreve o CRUD manual |

### 2.2 Banco do Activest — a carteira está vazia

O banco novo nasceu do zero pela renomeação (23/10): `users` 1, e **0** em `portfolio_assets`, `transactions`,
`dividends`, `strategies` e `goals`. A conferência com a "carteira real do DEV" (Anexo A, 1.3 e 16 — 38 posições, 236
proventos) **não é possível hoje no Activest**. Os dados de 13/10 continuam no banco antigo, na pilha `primevest-*`
(parada como backup). O catálogo está completo (seção 2.4).

### 2.3 Rentabilidade entregue (17/10) — a etapa M4 já existe

O que o Anexo A chamava de "série histórica da carteira" (M4, `PortfolioSeries`) existe com outro nome e desenho, na
**app-api** (não no core):

| Peça | Onde | O que dá às metas |
|---|---|---|
| `PortfolioTimelineBuilder` / `PortfolioTimeline` | `activest-app-api/.../domain/performance/` | Carteira dia a dia (pregões + hoje): valor, compras, vendas e proventos por posição, bruto e líquido (`PerformanceBasis`), marca de exposta sem preço |
| `PerformanceScope` | idem | Recorte por tipo, categoria, posição, ticker, com/sem reserva, bruto/líquido — cobre parte do "escopo" da meta (Anexo A, 3) |
| `ReturnMath`, `BenchmarkSeries`, `Xirr`, `RiskMetrics` | idem | TWR (o "método da cota" da D4), comparação com índice, IPCA + x%, encadeamento até o último dado publicado, drawdown, XIRR |
| `PerformanceService` | idem | Resumo, série, periódico, tabela, contribuição, patrimônio, risco; cache em memória por usuário |
| Regra de lacuna | `docs/RENTABILIDADE_METODOLOGIA.md` | Posição exposta sem preço além de 5 pregões deixa o dia **indisponível** (TWR nulo) — diferente da D11 do Anexo A (preço carregado + `PARCIAL` acima de 10%) |

Consequências: D1 (série calculada na leitura com cache) e D4 (TWR) foram resolvidas no escopo de Rentabilidade;
RNT-1, RNT-2 e RIS-2 (onda 3) passam a ser uso do que existe; a D11 do Anexo A conflita com a regra adotada lá.

### 2.4 Cobertura de preços (Anexo A, 1.3 e L1) — resolvida em boa parte

`asset_price_history` hoje: ações, FIIs, **ETFs e BDRs pelo COTAHIST desde 2010**; Tesouro pelo PU oficial desde 2010;
**BTC pela Binance pública desde 13/10/2020**; demais criptos pela CoinGecko só nos últimos 365 dias. A L1 do Anexo A
(ETF e cripto só desde setembro/2026) vale apenas para cripto que não seja BTC e para tipos `MANUAL`.

### 2.5 Escopos que mudaram regras usadas pelas metas

| Escopo | O que mudou | Onde afeta o Anexo A |
|---|---|---|
| Venda de RF (15/10) | Compra pelo bruto, venda pelo líquido (`net_amount`, IR, IOF); Carteira com líquido estimado; Rentabilidade e Metas "pelo líquido" (decisão de 15/10) | Definição de aporte (3), V(t) bruto × líquido, L11 ("valores brutos") deixa de valer |
| Venda de RF (15/10) | Liquidação automática do título vencido **revogada**: só aviso `RF_VENCIDO` | Anexo A, 3 ("liquidação automática `SISTEMA` é venda") — só vendas antigas |
| Emissoras de RF (16/10) | `fixed_income_issuers` + grupos; emissor da RF deixa de ser texto livre | L6 resolvida; concentração por emissor pode usar empresa ou grupo |
| Mudanças de tela (17/10) | Dashboard: Aportes = compras − vendas (12 meses), Patrimônio bruto + líquido | Mesma definição da D6 recomendada; G7 (mesmo número das outras telas) |
| Tarefas (22/10) | Rotinas viraram tarefas no banco (`task_types`/`tasks`, seed por migration, handler por tipo); `POSITIONS_REFRESH` 21:00 e `DAILY_OPENING` 00:00 na app-api | "Rodada diária depois das 21:00" (Anexo A, 8) vira **tipo de tarefa novo** (ou passo de uma existente) |
| Nomes em inglês (22/10) | Nome de classe, campo, rota, coluna e constraint em inglês; valor gravado pode ser português | Tabelas/colunas do Anexo A já estão em inglês; valores (`ATIVA`, `CRIADA`…) seguem a regra de valor de registro |
| Renomeação (23/10) | Migrations consolidadas: hoje **V1 a V7** | V52/V53 do Anexo A não existem mais: a próxima é **V8** |
| Correção de proventos (21/10) | Catálogo de proventos limpo e rotina semanal do FNET | Renda passiva (REN-*) mais confiável; nada a mudar no desenho |

### 2.6 O que continua como no Anexo A

- `asset_types` **não tem região nem moeda** (D16 continua aberta).
- `NotificationCategory` não tem `METAS`; `NotificationKind` não tem tipos de meta; PENDENCIAS 26.3 em aberto.
- Cupons do Tesouro fora (PENDENCIAS 24.1, L5); gestor não existe (L7); despesas pessoais não cadastradas (L9).
- `StrategyHoldings` (valor atual pela Alocação/Rebalanceamento), `NotificationService`, `CatalogReaction`,
  `PositionWrittenEvent`, `BusinessCalendar`, `MarketIndexCalculator`: existem e são os mesmos pontos de apoio.
- PENDENCIAS 29.5 (benchmarks gravados no usuário): a meta RNT-2 guarda o próprio índice nos parâmetros, não depende dela.

### 2.7 Situação das decisões do Anexo A (D1–D22)

Pela regra 2.5 (decisão pela recomendação só com aprovação explícita):

| Situação | Decisões |
|---|---|
| Decididas pelo Guilherme (13/10) | **D5** proventos líquidos · **D7** patrimônio com/sem reserva, escolha explícita sem pré-marcação (alocação/concentração ainda aguardam) · **D13** meta atingida se conclui sozinha, com aviso |
| Resolvidas por outro escopo (a confirmar) | **D1** série na leitura com cache · **D4** TWR — ambas pelo escopo de Rentabilidade |
| Afetadas pelo que mudou | **D6** (aporte — o Dashboard já usa compras − vendas) · **D11** (lacuna — conflita com a regra da Rentabilidade) · **D12** (retorno padrão — ainda sem resposta) · **D16** (V53 vira V8+) · **D22** (as ondas — a 3 encolhe) |
| Em aberto | D2, D3, D8, D9, D10, D14, D15, D17–D21 |

## 3. Pontos levantados — respostas de 24/10/2026

| # | Ponto | Resposta do Guilherme (24/10/2026) |
|---|---|---|
| P1 | Dados para validar (a carteira do Activest está vazia) | **Trazer para o Activest todas as transações do usuário Guilherme do PrimeVest** — entra no plano como etapa própria |
| P2 | Base de valor de patrimônio e renda | **Patrimônio bruto** (substitui, para as metas, a decisão de 15/10 "Rentabilidade e Metas pelo líquido"); **renda passiva pelos proventos líquidos** — D5 de 13/10 confirmada em 24/10 |
| P3 | Lacuna de preço | **A regra da Rentabilidade** (`docs/RENTABILIDADE_METODOLOGIA.md`; desde 25/10/2026, preço carregado — seção 5.9) — a D11 do Anexo A sai |
| P4 | Reaproveitamento | ~~O motor da Rentabilidade~~ — **revisto no Impact (24/10): motor próprio das metas**, sem alterar o da Rentabilidade (seção 4.4) |
| P5 | Tamanho do escopo (17 tipos, ondas) | Para o Planning |
| P6 | Rodada diária (tarefa nova × passo do `POSITIONS_REFRESH`) | Para o Planning |

**Nota sobre P2.** No Anexo A o "líquido" aparece em dois sentidos diferentes:

- **Proventos líquidos (D5, decidida em 13/10):** provento depois do IR retido na fonte (JCP 17,5%). Vale para a renda
  passiva (REN-1, REN-2, REN-3, ATV-3, LP-1) e para o *yield* das projeções.
- **Aporte líquido (D6, em aberto):** compras − vendas. Não é imposto, é o saldo de entradas e saídas.

O Anexo A não tem desenho para o **valor líquido da carteira** (IR/IOF estimados do resgate da renda fixa e do Tesouro):
esse conceito nasceu depois, no escopo de Venda de RF (15/10). Com o P2 = bruto, o patrimônio das metas é o valor
atual bruto (o mesmo do card Patrimônio do Dashboard e da base bruta da Rentabilidade).

## 4. Impact — análise de impacto (24/10/2026)

### 4.1 Carga das transações do PrimeVest (P1)

Levantado no banco antigo (`primevest-db`, porta 5434, ligado só para a leitura e desligado em seguida; Flyway na V67):

| Dado do usuário Guilherme no PrimeVest | Linhas | Como chega ao Activest |
|---|---|---|
| `transactions` (14/06/2024 a 13/10/2026; ações, FIIs, ETF, cripto, Tesouro, renda fixa) | 117 (109 `MANUAL`, 8 `AUTO` da Binance) | **É o pedido** |
| `portfolio_assets` (posições) | 41 | Nascem das transações; `is_reservation` em **6** não vem de transação |
| `fixed_income_notes` (títulos de RF e Tesouro) | 15 | Necessárias: a transação de RF/Tesouro aponta para o título |
| `dividends` | 236 (233 `AUTO`, 3 `MANUAL`) | Os `AUTO` o Activest recalcula sozinho do catálogo; os **3 `MANUAL`** (rendimento Selic PETR4, PENDENCIAS 33.1) não |
| `strategies` / `strategy_reservations` | 5 / 1 | Não vêm de transação — sem elas ALO-2 e o Rebalanceamento ficam vazios |
| `stock_event_costs` (custo da bonificação informado) | 1 | Sem ele a posição volta a pedir o custo |
| `ceiling_prices` | 3 | Fora das metas |
| `integration_connections` (Binance) | 1 | Credencial cifrada com a chave do PrimeVest; recadastro já previsto para o escopo dos ETFs americanos |
| `goals` | 0 | — |

Compatibilidade conferida:

- **Estrutura igual**: as colunas de `transactions`, `fixed_income_notes`, `portfolio_assets`, `dividends`, `strategies`,
  `strategy_reservations`, `ceiling_prices`, `stock_event_costs`, `portfolios` e `users` são idênticas nos dois bancos
  (comparação de nome, tipo e nulidade). As regras `ck_transactions_origin` e `ck_transactions_sale_taxes` também.
- **Catálogo casa**: os 36 tickers das posições existem no Activest; as 7 empresas de RF (emissora e distribuidora) têm
  a mesma raiz de CNPJ no Activest. Os **ids não são os mesmos** (catálogo recarregado do zero): o casamento tem de ser
  por chave natural — ticker, raiz do CNPJ, nome do tipo — nunca por id.
- **Usuário e carteira**: ids diferentes nos dois bancos (o do Activest nasceu na V6); o vínculo é pelo e-mail.

Riscos e efeitos colaterais:

| # | Risco | Efeito |
|---|---|---|
| R1 | As 8 transações `AUTO` da Binance trazem `connection_id`, `external_id` e `source_snapshot`; a conexão não existe no Activest | Gravadas sem conexão, a sincronização futura da Binance (recadastro) pode não reconhecê-las como já importadas e duplicar — a deduplicação é por `(connection_id, external_id)` |
| R2 | Duas vendas repartidas do Tesouro (`sale_group_id`) e as vendas de RF com IR/IOF gravados | Copiadas como estão, ficam iguais às do PrimeVest; refeitas pelas regras (FIFO por distribuidora, lotes, IR/IOF), podem sair diferentes se alguma regra mudou depois — e a diferença é informação útil, não erro |
| R3 | Princípio do projeto: carga dos dados reais do usuário fica **fora do projeto** (`E:\carga\scripts`, `E:\carga\data`) | O script da carga não entra no repositório do Activest |
| R4 | Gravar direto no banco pula `TransactionService.afterPositionChange` (proventos, valorização, curva da RF, notificações) | Precisa de um recálculo depois (`POSITIONS_REFRESH` resolve proventos, valorização e curva) — e as notificações retroativas (provento pago, título vencido) podem nascer de uma vez na rodada de abertura |
| R5 | Recarga repetida | A carga tem de ser idempotente ou exigir carteira vazia, para não duplicar transações |
| R6 | O banco antigo é backup de conferência | Só leitura: nada é gravado nele |

### 4.2 Componentes do Activest afetados pelas metas

| Camada | Componente | Mudança | Risco |
|---|---|---|---|
| Banco | `goals` (V1) | Recriada na **V8** (guarda: recusa se houver linha — hoje 0) + `goal_events`, `goal_snapshots`; categoria e tipos de notificação; tipo de tarefa (se P6 = tarefa nova); `asset_types` região/moeda (se D16 = a) | `ddl-auto: validate` — entidade e migration na mesma subida |
| core | `domain/goal/` (`Goal`, `GoalRepository`, `GoalStatus`) | Reescritos; `User.goals` (lista `@OneToMany`) acompanha | Baixo: nada fora do pacote usa |
| core | `NotificationCategory`, `NotificationKind` | `METAS` e os tipos da D20 | Enum gravado como texto: só acrescentar |
| core | `TaskHandler`, `task_types` (seed) | Tipo novo da rodada diária, se P6 = tarefa nova | Tarefa nova aparece na tela de Tarefas |
| app-api | `domain/goal/` (`GoalController`, `GoalService`, 3 DTOs) | Substituídos: catálogo de tipos, avaliação, ciclo, preview, histórico | Contrato da API muda por inteiro (só a `GoalsPage` usa) |
| app-api | `performance/` (`PortfolioTimelineBuilder`, `PerformanceService`, `PerformanceScope`, `ReturnMath`, `BenchmarkSeries`, `RiskMetrics`) | **Consumidos** (P4): as metas leem a linha do tempo e as funções de retorno; o escopo da meta (indexador, emissor, posições escolhidas, exclusões) é mais largo que o `PerformanceScope` | Mexer no motor afeta Rentabilidade, Dashboard e Carteira (TWR) — preferir adicionar a alterar; o cache da linha do tempo é por usuário e já serve as duas telas |
| app-api | `StrategyHoldings`, `DividendRepository`/`DividendCalendar`, `MarketIndexCalculator`, `BusinessCalendar` | Lidos (G7: mesmo número das outras telas) | Nenhuma alteração prevista |
| app-api | `PositionWrittenEvent`, `CatalogReaction`, `DailyOpeningService`, `PositionsRefreshTask` | Gatilhos de reavaliação, fotografia e avisos | Trabalho extra por transação — assíncrono e agrupado (Anexo A, 8) |
| web | `GoalsPage`, `api/goals.ts`, rota `/goals`, `Sidebar` | Substituídos: visão geral, lista, nova meta, detalhe (`/goals/:id`) | Só esta tela usa |
| docs | `Claude.md`, `docs/USE_CASES.md` (UC08), `docs/ACTIVEST_DB.md`, `docs/SCHEDULE_TASKS.md`, `docs/PENDENCIAS.md` (26.3), `README.md`, `docs/METAS_METODOLOGIA.md` (novo) | Atualizar | — |
| testes | Não há teste de metas hoje | Todos novos (Anexo A, 15) | — |

Dependências: nenhuma fonte externa nova; nada na adm-api; nada no módulo `activest-integration`, exceto o R1.

### 4.3 Pontos que o Impact abre para o Planning

1. **Forma da carga (P1)**: cópia direta pelo banco (mantém as transações como estão) × relançar pela API (refaz pelas
   regras de hoje e passa pelos recálculos) — ver R2 e R4.
2. **O que vem junto das transações**: só o pedido (transações + títulos) ou também reserva (6), estratégias (5 + 1),
   proventos manuais (3), custo da bonificação (1) e preço teto (3).
3. **As 8 transações da Binance (R1)**: como `AUTO` sem conexão, ou como `MANUAL`.
4. **Onde fica o script (R3)**: fora do projeto, como a regra da carga de dados reais.
5. **Escopo da meta × `PerformanceScope`**: estender o recorte do motor ou filtrar a linha do tempo do lado das metas.
6. Mais os de antes: **P5** (17 tipos × recorte) e **P6** (rodada diária).

### 4.4 Respostas do Impact (24/10/2026)

| # | Ponto | Resposta do Guilherme |
|---|---|---|
| I1 | Motor | **Motor separado para as metas** — mais seguro que usar o da Rentabilidade (revê o P4). **Opção (a)**: motor próprio que só **lê** as peças da Rentabilidade, sem alterá-las (ver nota) |
| I2 | As 8 transações da Binance | **Ficam de fora** da carga (R1 deixa de existir; a posição de BTC da Binance não vem) |
| I3 | Onde fica o script | **Dentro do projeto, em `carga/scripts`** ("mais rastreável") — revê, para esta carga, o princípio de deixar a carga de dados reais fora do projeto (R3). Extensão: **`.mjs`** (Node), como `carga/scripts/migrations/compare.mjs`, que já lê dois bancos pelo `psql` dos contêineres (`docker exec`) |

**Nota sobre I1.** "Separado" pode ser de dois jeitos, e a diferença é a regra G7 do Anexo A (uma meta nunca mostra um
número que contradiga outra tela):

- **(a) Motor próprio que só lê** a linha do tempo (`PortfolioTimeline`) e as funções puras (`ReturnMath`,
  `BenchmarkSeries`, `RiskMetrics`) — nada da Rentabilidade é alterado; o que as metas precisarem a mais
  (indexador, emissor, posições escolhidas, projeção, fotografia) nasce no pacote das metas. Os números de
  patrimônio e rentabilidade batem com os da tela Rentabilidade por construção.
- **(b) Motor próprio do zero**, sem ler nada da Rentabilidade — isolamento total, mas TWR, lacuna e benchmark
  reescritos podem divergir da tela Rentabilidade, e a regra de lacuna do P3 teria de ser reimplementada.

## 5. Planning (24/10/2026)

### 5.1 Solução em duas partes

| Parte | O que é | Por quê primeiro |
|---|---|---|
| **C — Carga da carteira** | Script `carga/scripts/primevest/importar_carteira.mjs` que lê o banco antigo (`primevest-db`, só leitura) e relança no Activest as transações do usuário Guilherme (sem as 8 da Binance, I2), com conferência no fim | Sem carteira não há meta para validar (P1). Independe do código das metas |
| **M — Metas** | O módulo do Anexo A, com os ajustes da seção 5.2, sobre um **motor próprio das metas** que só lê a Rentabilidade (I1 = a) | — |

### 5.2 Ajustes ao Anexo A (o que vale em vez do texto de 13/10)

| Anexo A | Agora |
|---|---|
| 1.3 Cobertura, L1 | Seção 2.4: ETF/BDR desde 2010, BTC desde 2020; L1 só para outras criptos e tipos `MANUAL` |
| 2 G7, 3 "V(t)" | Patrimônio **bruto** (P2) = valor atual da Carteira/Alocação; proventos **líquidos** (D5) |
| 3 "Aporte" | Compras − o que as vendas devolveram (o líquido onde existe — renda fixa e Tesouro —, o valor da venda nos demais) — a mesma conta do card Aportes do Dashboard (`TransactionRepository.sumBuysBetween` / `sumSalesBetween`; corrigido na implementação: o texto do Planning dizia `total_amount`). A liquidação `SISTEMA` só existe em dado antigo |
| 3 "Cota da carteira", D1, D4, M4 | Não se escreve série nova: o motor das metas usa `PortfolioTimelineBuilder.timeline(userId)` e `ScopeSeries.of(timeline, filtro, PerformanceBasis.GROSS)` com o **filtro da meta** (tipo, categoria, indexador, emissor/grupo, ativo, posições escolhidas, exclusões, reserva) e as funções puras `ReturnMath`, `BenchmarkSeries`, `RiskMetrics`. Nada no pacote `performance` é alterado |
| D11 | Regra de lacuna da Rentabilidade (P3): `ScopeSeries.unavailable` — dia indisponível, rentabilidade nula com o ativo nomeado |
| L6 | Emissor da RF pelo cadastro `fixed_income_issuers`, com o grupo (`issuerGroup` da linha do tempo) |
| L11 | Sai: patrimônio bruto por decisão (P2), não por limitação |
| 8 "rodada depois das 21:00" | Tipo de tarefa novo (D24) |
| 9 V52/V53 | **V8** (metas, notificações, tarefa); região/moeda fora (D16) |
| 11, 12 | Mesmos endpoints e telas; nomes de classe, campo, rota e coluna em inglês (REGRAS 9); valores gravados (`ATIVA`, `CRIADA`…) como no Anexo A |
| 14 Etapas | Seção 5.5 |

### 5.3 Parte C — a carga em detalhe

**Caminho recomendado (D25 = a): relançar pela API do Activest**, em ordem cronológica (data, depois criação), com o
login da `ACTIVEST_PASSWORD` do `.env` (como o `admin.mjs`). Cada transação passa pelo `TransactionService` de hoje:
regras A/B/H, título de RF criado ou reaproveitado, FIFO do Tesouro por distribuidora, lotes e IR/IOF da venda de RF,
proventos, valorização e curva — sem recálculo à parte (R4 resolvido).

| Dado antigo | Como entra | Detalhe |
|---|---|---|
| Compra de ação/FII/ETF/Tesouro | `POST /transactions` | `ticker`, `shares`, `pricePerShare`, `brokerageFee`, data; Tesouro com `distributorId` (raiz do CNPJ → id do Activest) |
| Compra de RF | `POST /transactions` | Primeira compra do título: `issuerId`, `distributorId`, `maturityDate`, `rateType`, `indexer`, `indexerRate`, `paperCode`; aportes seguintes no mesmo título com o `noteId` **do Activest** (mapa título antigo → novo montado durante a carga) |
| Venda de Tesouro repartida (`sale_group_id`, 2) | Uma venda só | Soma das cotas do grupo; o FIFO do Activest reparte de novo |
| Venda de RF | `POST /transactions` | `totalAmount` = `net_amount` antigo (o líquido recebido); `isFullSettlement` quando o título antigo terminou liquidado nessa venda |
| `is_reservation` (6) | `PATCH /portfolio/assets/{id}/reservation` | Depois das transações, pela posição equivalente (ativo + título) |
| Estratégias (5 + 1) | `PUT /strategy`, `PUT /strategy/reservation` | Se D26 incluir |
| Custo de bonificação (1) | `PUT /portfolio/assets/{id}/stock-events/{eventId}/cost` | Evento casado por ticker + data-com; se D26 incluir |
| Proventos `MANUAL` (3) | **Não há endpoint**: SQL direto em `dividends` (origem `MANUAL`) | Se D26 incluir |
| Preço teto (3) | `POST /ceiling-prices` | Se D26 incluir |

Regras do script:

- **Só leitura no banco antigo**; sobe o `primevest-db` se estiver parado e o para no fim (estado devolvido como achou).
- **Recusa se a carteira do Activest não estiver vazia** (R5) — a recarga é `docker compose down -v` + subida, ou outro
  caminho que você decidir; nada de apagar transação pelo script.
- `--dry-run`: lista o que seria lançado, sem gravar.
- **Conferência no fim** (critério C3): por posição (ticker + título), cotas, custo, preço médio e status liquidado
  iguais aos do PrimeVest; total de proventos do usuário por ativo; e a lista de diferenças com o motivo, quando houver
  (regra que mudou depois de 13/10 — R2 — é informação, não falha do script).
- Saída em português, com o resumo (lançadas, puladas da Binance, diferenças).

Caminho alternativo (D25 = b): cópia direta pelo banco, por chave natural, e `POSITIONS_REFRESH` em seguida — mais
rápido, preserva as datas de criação, mas não passa pelas regras de hoje.

### 5.4 Parte M — o motor das metas

| Peça | Onde | Responsabilidade |
|---|---|---|
| `GoalTypeCatalog` + um `GoalType` por tipo | app-api, `domain/goal/type/` | Campos que o usuário informa (com validação), natureza, avaliação, projeção opcional (Anexo A, 0 e 5) |
| `GoalFacts` | app-api, `domain/goal/engine/` | Uma leitura por requisição: linha do tempo (`PortfolioTimelineBuilder`, só leitura), `StrategyHoldings` (valor atual por posição), proventos (`DividendRepository` + `DividendCalendar`), títulos e emissoras, estratégia, índices (`MarketIndexCalculator`), qualidade |
| `GoalScopeFilter` | idem | Traduz o escopo JSON da meta num `Predicate<PositionSeries>` para `ScopeSeries.of` e num filtro sobre as posições de `StrategyHoldings` |
| `GoalProjection` | core (regra pura) | Fórmulas do Anexo A, 6.2 |
| `GoalEvaluator` | app-api | Situação (Anexo A, 7.1), qualidade, natureza dos números (G2), fontes com data |
| `GoalService` / `GoalController` | app-api | API do Anexo A, 11; ciclo e revisões; `findOwnedGoal` |
| `GoalSnapshotTask` (tipo `GOALS_SNAPSHOT`) | app-api, `task/` | Fotografia diária, eventos e avisos (D24) |
| Gatilhos | app-api | `PositionWrittenEvent` e `CatalogReaction`: reavaliação assíncrona e agrupada do usuário |

**Fronteira com a Rentabilidade (critério M-A1):** nenhum arquivo de `domain/performance/` muda. Se faltar alguma coisa
pública lá, a necessidade vira pergunta antes de mexer.

### 5.5 Etapas

| Etapa | Entrega | Depende |
|---|---|---|
| **C1** | Script `importar_carteira.mjs` com `--dry-run` e conferência | — |
| **C2** | Carga executada no DEV do Activest e relatório da conferência (diferenças explicadas) | C1, D25, D26 |
| **M1** | V8 (metas, `goal_events`, `goal_snapshots`, `METAS` e tipos de notificação, tipo de tarefa `GOALS_SNAPSHOT`; sem região/moeda — D16); entidades; `GoalType`/catálogo; ciclo e revisões; API de cadastro, ciclo, `/types`, `/preview`; o CRUD antigo sai | — |
| **M2** | `GoalFacts` e `GoalScopeFilter` (escopos e dimensões, qualidade, defasagem D9, fontes com data) | M1 |
| **M3** | Tipos sem série nem projeção: PAT-1, APO-1, APO-2, REN-1, REN-2, ALO-1, ALO-2, RIS-1, RIS-3, ATV-1, ATV-2, ATV-3 | M2 |
| **M4** | Tipos sobre a série (`ScopeSeries`): RNT-1, RNT-2, RIS-2 e a decomposição/evolução recalculada | M2 |
| **M5** | `GoalProjection` e projeção nos tipos crescentes; REN-3 e LP-1 | M3, M4 |
| **M6** | Fotografia diária (`GOALS_SNAPSHOT`), eventos, gatilhos e avisos | M3 |
| **M7** | Web: visão geral, lista, nova meta (catálogo + preview), editar, ciclo | M1, M3 |
| **M8** | Web: detalhe (situação, falta, decomposição, projeção e premissas, evolução, alterações, origem e data dos dados; memória de cálculo só na API — D21) | M4, M5, M6 |
| **M9** | Documentação (`docs/METAS_METODOLOGIA.md` novo, `Claude.md`, UC08, `ACTIVEST_DB.md`, `SCHEDULE_TASKS.md`, PENDENCIAS 26.3, `README.md`) e planilha de conferência (`docs/metas/Conferencia_Metas.xlsx`) | todas |

O que entra deste escopo depende da D23: com o recorte, M4/M5 e os tipos correspondentes viram pendência.

### 5.6 Decisões para o Guilherme

**Novas (deste Planning):**

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D23** | Tamanho do escopo (P5) | (a) os 17 tipos, pelas etapas M1–M9; (b) recorte: M1–M3 + M4 (sem projeção), M5 em escopo próprio; (c) recorte menor: só PAT-1, APO-1, APO-2, REN-1, ALO-2, RIS-1 | **(a)**: o motor da Rentabilidade já dá a série (a antiga onda 3 ficou barata) e o catálogo extensível é o que justifica o desenho; as etapas fecham uma de cada vez (REGRAS 2.3) |
| **D24** | Rodada diária (P6) | (a) tipo de tarefa novo `GOALS_SNAPSHOT` (app-api), 21:30, depois do `POSITIONS_REFRESH`; (b) passo no fim do `POSITIONS_REFRESH` | **(a)**: aparece na tela de Tarefas com rodada e log próprios, liga/desliga sem tocar na rede de segurança, e uma falha nas metas não vira falha do refresh. Stop cooperativo por meta |
| **D25** | Forma da carga | (a) relançar pela API; (b) cópia pelo banco + `POSITIONS_REFRESH` | **(a)** (seção 5.3) |
| **D26** | O que vem junto das transações | marcar: reserva (6) · estratégias (5 + 1) · custo de bonificação (1) · proventos manuais (3) · preço teto (3) | **Reserva, estratégias, custo de bonificação e proventos manuais** — os quatro mudam números que as metas leem (ALO-2, PAT-1 com/sem reserva, custo, renda). Preço teto fica fora (não é meta) |
| **D27** | Avisos que a carga gera (pendências de título vencido, custo de bonificação etc.) | (a) ficam como qualquer aviso; (b) o script os arquiva no fim | **(a)**: são o estado real da carteira; os provento-pago não nascem retroativos (regra D0 das Notificações) |

**Do Anexo A, ainda em aberto** (recomendação de 13/10 revista quando o Discovery mudou o quadro):

| # | Tema | Recomendação agora |
|---|---|---|
| D1 | Série na leitura com cache | Confirmar: resolvida pela Rentabilidade (linha do tempo em cache por usuário) |
| D2 | Parâmetros em JSONB validado pelo tipo | (a), como no Anexo A |
| D3 | Fotografia diária + evolução recalculada | (a), como no Anexo A |
| D4 | Método de rentabilidade | Confirmar: TWR, o da Rentabilidade |
| D6 | Aporte = compras − vendas | (a) — o card Aportes do Dashboard já usa (17/10) |
| D7 (resto) | Base de alocação e concentração | Padrão **sem a reserva**, com escolha por meta (patrimônio já decidido: escolha explícita) |
| D8 | Posição sem cotação | (a) valor pelo custo, rotulado, `PARCIAL` |
| D9 | Limites de defasagem | Como no Anexo A (3 dias úteis; índice mensal até o dia 20) — só marca |
| D10 | Renda média com histórico curto | (a) soma ÷ janela, meses fechados |
| D11 | — | **Decidida** pelo P3 (regra da Rentabilidade) |
| D12 | Retorno padrão das projeções | (a) CDI acumulado 12 meses, com (b) TWR da carteira ≥ 24 meses e (c) taxa informada na tela |
| D14 | No ritmo × atrasada | (a) projeção; linear onde não há rendimento |
| D15 | Excluir meta | (b) só a arquivada, com confirmação |
| D16 | Região e moeda | (a) colunas em `asset_types`, na V8 |
| D17 | Despesas (REN-2) | (a) na própria meta, com correção opcional pelo IPCA |
| D18 | Posição em várias metas | Pode, com aviso de sobreposição |
| D19 | Precisão | R$ 2 casas; % 1 casa (rentabilidade 2); projeção em mês/ano, estimativas a R$ 100 |
| D20 | Avisos | Concluída, Fora do objetivo, Atrasada, Prazo vencido, Sem dados; liga/desliga por meta |
| D21 | Memória de cálculo | (a) fonte e data visíveis; fórmula num (i) "Como calculamos" em modal |
| D22 | Ondas | Substituída pela D23 |

Decididas: **D5** (proventos líquidos, 13/10, confirmada 24/10), **D7** patrimônio (13/10), **D11** (P3, 24/10),
**D13** (13/10), P1–P4 (24/10), I1–I3 (24/10), **D23–D27 pela recomendação** (aprovação explícita do Guilherme,
24/10/2026).

### 5.6.1 Respostas de 24/10/2026 (D1–D22)

| # | Decisão do Guilherme |
|---|---|
| D1 | Recomendação: linha do tempo da Rentabilidade, na leitura, com cache por usuário |
| D2 | Recomendação: colunas comuns + JSONB validado pelo tipo |
| D3 | Recomendação: fotografia diária + evolução recalculada |
| D4 | Recomendação: TWR |
| D5 | Mantida: proventos líquidos |
| D6 | Recomendação: aporte = compras − vendas |
| D7 | Mantida no patrimônio (escolha explícita); alocação e concentração pela recomendação: padrão sem a reserva, escolha por meta |
| D8 | Recomendação: sem cotação, valor pelo custo, rotulado, `PARCIAL` |
| D9 | Recomendação: 3 dias úteis; índice mensal até o dia 20; só marca |
| D10 | Recomendação: soma ÷ janela, meses fechados |
| D11 | Mantida: regra de lacuna da Rentabilidade (P3) |
| D12 | Recomendação: CDI 12 meses como padrão; TWR da carteira (≥ 24 meses) e taxa informada como opções |
| D13 | Mantida: conclui sozinha e notifica |
| D14 | Recomendação: projeção nas metas de valor; linear onde não há rendimento |
| D15 | Recomendação: excluir só a arquivada, com confirmação |
| **D16** | **Fora deste escopo: tudo em reais.** Outras moedas — inclusive metas em outra moeda — vão para um **escopo próprio**. Sem colunas de região e moeda na V8; a dimensão região/moeda não é oferecida em ALO-1 e RIS-1 (a tela diz que vem com o escopo de moedas). Hoje o dólar só aparece no Preço Teto (lucro em USD com câmbio, convertido na hora), no índice `USD_PTAX` e nas regras de importação da Binance (USD como moeda fiduciária); nenhum ativo do catálogo é cotado em dólar (STOCK, REIT e ETF internacional são `MANUAL`) |
| D17 | Recomendação: despesa na própria meta, correção opcional pelo IPCA |
| D18 | Recomendação: pode estar em várias, com aviso |
| D19 | Recomendação: R$ 2 casas; % 1 (rentabilidade 2); mês/ano; estimativas a R$ 100 |
| D20 | Recomendação: Concluída, Fora do objetivo, Atrasada, Prazo vencido, Sem dados; liga/desliga por meta |
| **D21** | **A memória de cálculo é gerada (API) mas não aparece na tela** — como o Preço Teto (`result.steps` na API, cartão retirado da tela). Fonte e data dos dados continuam visíveis. Vira pendência: exibir a memória de cálculo das metas |
| D22 | Substituída pela D23 |

Pendências a registrar no `docs/PENDENCIAS.md` na etapa M9: escopo de outras moedas (com metas em outra moeda e as
dimensões região/moeda) e a exibição da memória de cálculo das metas.

### 5.7 Critérios de aceitação

| # | Critério |
|---|---|
| C1 | `--dry-run` lista as 109 transações (117 − 8 da Binance) sem gravar nada |
| C2 | Script recusa carteira não vazia; não grava no banco antigo |
| C3 | Conferência: cotas, custo, preço médio e liquidado iguais aos do PrimeVest por posição (exceto a posição de BTC da Binance), com cada diferença explicada |
| M-A1 | Nenhum arquivo de `activest-app-api/.../domain/performance/` alterado — **exceto a revisão R1 (a) de 25/10/2026** (seção 5.9), decidida pelo Guilherme |
| M-A2 | Patrimônio de PAT-1 (carteira, com reserva) = Patrimônio bruto do Dashboard; renda de REN-1 (12 meses) = Proventos 12 meses do Dashboard; ALO-2 = Rebalanceamento; RNT-1 do período = TWR bruto da Rentabilidade no mesmo período e recorte |
| M-A3 | Cenários 1–17 e autorização do Anexo A, 15, verdes |
| M-A4 | Contrato: nenhum tipo pede valor calculável (G1); POST com esses campos → 400 |
| M-A5 | `mvn clean verify` verde nos quatro módulos; web `tsc --noEmit` e `eslint .` com 0 erro e 0 aviso |
| M-A6 | `GOALS_SNAPSHOT` na tela de Tarefas; segunda rodada no dia não duplica fotografia |
| M-A7 | Conferência no navegador com você logado (caminho 3) |

### 5.8 Quem faz

| Quem | O quê |
|---|---|
| Claude | C1, C2 (execução e relatório), M1–M9, testes, Testing e Review |
| Guilherme | Responder D1–D4, D6–D10, D12, D14–D27; aprovar a execução; conferir o relatório da carga e a planilha de exemplos; conferência no navegador; commit (REGRAS 3) |

### 5.9 Revisão de 25/10/2026 — "indisponível" na rentabilidade (decisão R1)

**O que acontece.** A regra da Rentabilidade (`PLANO_RENTABILIDADE.md`, D3/R10), que as metas seguiram por P3: um ativo
exposto sem preço por mais de 5 pregões deixa o **dia** indisponível, e um dia indisponível derruba o TWR, o
resultado, o XIRR e o risco de **todo período que o contém**. Hoje o PU oficial do Tesouro está parado em 15/10/2026
(fonte desatualizada, PENDENCIAS 34.5): de 16/10 a 23/10 os 8 títulos ficam "sem preço" — e qualquer período que passe
por esses dias some na Rentabilidade, no Dashboard, no TWR da Carteira e nas metas de rentabilidade. Nos ativos que
nunca tiveram cotação (cripto além do BTC antes de 365 dias, renda fixa sem curva) é a mesma coisa, para sempre.

| # | Opção | Efeito |
|---|---|---|
| **a** | **Carregar o último preço sem limite** (marcado "último preço de dd/mm" a partir do 6º pregão), e, sem nenhum preço anterior, usar o preço da própria compra (renda fixa sem curva: o valor aplicado, como a Carteira). O dia nunca fica indisponível; a tela mostra os números com um aviso dizendo quais ativos e desde quando estão com preço carregado | Sempre há número, igual à Carteira (que já mostra o valor com o último preço). O preço velho entra no cálculo — o aviso diz isso |
| b | Calcular sem os ativos sem preço nos dias afetados | Número aparece, mas a carteira "encolhe" e cresce nos dias da lacuna: o retorno fica distorcido sem o usuário perceber |
| c | Só aumentar o limite (ex.: 30 pregões) | Resolve o caso do Tesouro de hoje; volta a quebrar quando a lacuna passar do novo limite |

**Origem (verificado em 25/10/2026).** O "indisponível" **não vem das metas**: é a regra do escopo Rentabilidade
(17/10/2026, `PortfolioTimeline.MAX_STALE_DAYS = 5`), igual no PrimeVest (commit `401ade0`, que também mostra
"Resultado indisponível no período"). Até a decisão abaixo, este escopo não tinha alterado nenhum arquivo de `domain/performance/` —
as metas só leem a linha do tempo.

**Decisão R1 (25/10/2026, Guilherme): opção (a), tratada neste escopo.** Mudança no motor da Rentabilidade, que vale
para o sistema todo (Rentabilidade, Dashboard, Carteira e metas):

- `PriceStatus.CARRIED`: depois de 5 pregões sem negócio, o último fechamento segue carregado; sem nenhum fechamento,
  o preço da última compra até o dia; renda fixa sem curva ou sem título, o valor aplicado em aberto (como a Carteira).
  `MISSING` (dia indisponível) fica só para quando não há número nenhum.
- `ScopeSeries`: o carregado não deixa o dia indisponível; marca o ponto como o "último negócio". A lacuna sai como
  `PRECO_CARREGADO`, com a data do preço usado (`GapDTO.priceDate`).
- Tela Rentabilidade: aviso amarelo "Preço carregado" com o ativo, o intervalo e "último preço de dd/mm" (ou "valor
  aplicado").
- Testes: `PerformanceMathTest` C6b (novo) e `PerformanceIT` C6 (ativo sem histórico: preço da compra, disponível).

## 6. Andamento

| Data | Etapa | Situação |
|---|---|---|
| 13/10/2026 | (plano antigo) | Anexo A criado no PrimeVest; D5, D7, D13 decididas; nunca comitado (stash) |
| 24/10/2026 | 1 Discovery | Texto recuperado do stash (Anexo A); levantamento do Activest (seções 2 e 3) |
| 24/10/2026 | 1 Discovery | Respostas P1–P4; P5 e P6 para o Planning (seção 3); D5 confirmada (proventos líquidos) |
| 24/10/2026 | 2 Impact | Seção 4: carga das transações do PrimeVest (banco antigo lido) e componentes do Activest |
| 24/10/2026 | 2 Impact | Respostas I1–I3 (seção 4.4); I1 = (a) |
| 24/10/2026 | 3 Planning | D23–D27 aprovadas pela recomendação; D1–D22 em revisão pelo Guilherme |
| 24/10/2026 | 3 Planning | D1–D22 respondidas (5.6.1): D16 fora (tudo em reais, escopo próprio de moedas), D21 memória só na API; demais pela recomendação ou mantidas. Planning concluído |
| 24/10/2026 | 4 Implementation | Ordem de executar todas as etapas (C1–C2, M1–M9) |
| 24/10/2026 | C1 | `carga/scripts/primevest/importar_carteira.mjs` (+ `carga/scripts/README.md`); `--dry-run`: 117 transações, 8 da Binance fora, 108 lançamentos (a venda do Tesouro em 2 partes vira 1) |
| 24/10/2026 | C2 | Carga no DEV do Activest: 108 lançamentos (109 transações — a venda do Tesouro repartida de novo em 2), reserva em 6 posições, 5 estratégias + reserva, 1 custo de bonificação (KLBN4), 3 proventos manuais (PETR4); `POSITIONS_REFRESH` SUCCEEDED; **conferência: 39 posições nos dois, sem diferença** (cotas, compras, vendas bruto e líquido, liquidada, reserva, quantidade e soma líquida dos proventos). Avisos gerados: 3 `RF_VENCIDO` já resolvidos (títulos liquidados) |
| 24/10/2026 | M1–M6 | Backend: V8 (`goals` recriada, `goal_events`, `goal_snapshots`, tarefa `GOALS_SNAPSHOT`); core `Goal`, `GoalType` (17), `GoalLifecycle`, `GoalEvent`, `GoalSnapshot`, `GoalProjection`, `NotificationCategory.METAS` e `META_*`; app-api `GoalTypeCatalog`, `GoalFactsLoader`/`GoalFacts`, `GoalScope`, `GoalEvaluator` (os 17 tipos, projeção, situação, qualidade), `GoalService`/`GoalController` (catálogo, opções de escopo, preview, CRUD, ciclo, histórico, eventos), `GoalTracker` (fotografia, eventos, conclusão automática, avisos), `GoalReevaluationQueue`/`Worker`, `GoalsSnapshotTask`; o CRUD antigo saiu. Nenhum arquivo de `domain/performance/` alterado (M-A1) |
| 24/10/2026 | M7–M8 | Web: `types/goals.ts`, `api/goals.ts`, `GoalsPage` (resumo, abas por ciclo, categoria, cartões), `GoalFormModal` (categoria → tipo → formulário do catálogo com preview ao vivo), `GoalDetailPage` (situação, números rotulados, projeção e premissas, detalhamento, evolução, alterações, origem); rota `/goals/:id`; categoria Metas nas Notificações |
| 24/10/2026 | Testing | `mvn clean verify` verde: core 217; integration 103; adm-api 131 + 83; app-api 234 + 284 (novos: `GoalProjectionTest` 5, `GoalTypeCatalogTest` 6, `GoalEvaluatorTest` 13, `GoalsIT` 9; ajustados `SchemaTest` V8 e `TaskControllerIT` 27 tarefas). Web: `tsc` e `eslint` sem erro nem aviso. V8 aplicada no DEV. Conferência M-A2 (`carga/scripts/conferencia/metas.mjs`): patrimônio R$ 78.360,75 = Dashboard; proventos 12 meses R$ 2.310,12 = Dashboard; % por tipo = Rebalanceamento; rentabilidade 12M por tipo = Rentabilidade (ações 33,08%, FIIs 2,60%); a da carteira toda está indisponível nas duas telas pelo mesmo motivo (PU do Tesouro parado em 15/10 — fonte desatualizada). Criação de meta pela tela conferida no navegador (meta de teste apagada em seguida) |
| 24/10/2026 | Review | Correções achadas: o leitor de índices chamava o `MarketIndexQueryService`, cuja `BusinessException` sem dado marcava a transação de leitura para rollback (trocado por leitura direta + `MarketIndexCalculator`); aporte pela regra do Dashboard (líquido da venda onde existe — 5.2); `notify` não pode ser componente de record Java (DTO usa `notifications`). Pendências registradas em `docs/PENDENCIAS.md`, seção 37 |
| 24/10/2026 | M9 | Documentação: `docs/METAS_METODOLOGIA.md` (novo), `Claude.md`, `docs/USE_CASES.md` (UC08), `docs/ACTIVEST_DB.md`, `docs/SCHEDULE_TASKS.md` ([B8]), `docs/PENDENCIAS.md` (26.3 resolvido; seção 37), `README.md`, `carga/scripts/README.md` |
| 25/10/2026 | Review (Guilherme) | Apontamentos: (1) textos dos tipos com códigos e siglas internas; (2) layout do formulário ruim (vertical, sanfonas diferentes, lista de checkbox enorme, avisos e observações desproporcionais, "opcional"/"escolha obrigatória" no lugar de asterisco); (3) rentabilidade e resultado "indisponível" quebram as telas |
| 25/10/2026 | Review (1, 2) | (1) Textos do catálogo e rótulos da avaliação reescritos para o usuário, sem códigos (PAT-1…), siglas (L4, D7…) nem PENDENCIAS; o código do tipo saiu das telas. (2) Formulário em três colunas (Objetivo · O que a meta considera · Calculado agora), modal mais largo; seleção de itens do escopo por busca com etiquetas (`MultiPicker`, ocupa o mesmo espaço com 5 ou 500 itens); uma sanfona só (`components/ui/Collapsible`) para Excluir tipos, Premissas e Observações; reserva em botões segmentados; avisos como chave no rodapé; obrigatório com asterisco vermelho (`FieldLabel`). As alterações do Guilherme (bordas retiradas, título com a etapa, "Nome") foram mantidas no espírito |
| 25/10/2026 | Review (3) | Levantamento na seção 5.9: o "indisponível" é do sistema (Rentabilidade, 17/10; igual no PrimeVest), não das metas. Metas não mexem no motor; uma mudança na regra fica para outro escopo |
| 25/10/2026 | Review (4) | Formulário: Excluir tipos de ativo vira campo de etiquetas (sem sanfona); Premissas da projeção em coluna própria com os campos expostos (grade de 2 a 4 colunas); Observações saem da tela (PENDENCIAS 37.7); título por fase ("Nova meta - Categorias", "Nova meta - Tipos"); `Collapsible` apagado. Detalhe: linha tracejada da projeção no gráfico de evolução (metas em R$) |
| 25/10/2026 | Review (5) | (1) Premissas da projeção vêm com o valor automático no campo, mesmo antes de preencher o alvo (`POST /goals/assumptions`, teste em `GoalsIT`) ("Automático · CDI acumulado em 12 meses"); editar fixa o valor ("Informado por você · usar automático"); "Rendimento automático pelo" some quando o rendimento é informado; na meta pelo custo, o rendimento não aparece. (2) A descrição do tipo vai como parágrafo no cabeçalho do modal (`Modal.description`). (3) R1 = (a) implementada (seção 5.9) |
| 25/10/2026 | Review (6) | Listagem: card "Em andamento" (azul; `SummaryDTO.inProgress` = em andamento + estimativa); metas agrupadas por categoria (ordem do catálogo; sanfona por categoria, aberta por padrão) e, dentro de cada uma, fora do objetivo → prazo vencido → atrasada → em andamento/estimativa → no ritmo/dentro/atingida → sem dados; etiquetas de situação das metas da aba (com contagem, várias de uma vez, "Limpar"); o número do card filtra as metas dele e limpa a categoria (zero não faz nada; "Concluídas" vai para a aba) |
| 25/10/2026 | Review (7) | Indicador × alvo com grandezas diferentes: LP-1 com renda desejada passa a comparar a renda mensal estimada (valores de hoje) com o alvo — o patrimônio de hoje vira número de apoio, e o gráfico do detalhe não mistura patrimônio com fotografias e alvo em renda (`HistoryDTO.comparable`); APO-2 sem sequência-alvo compara o aporte do período corrente com o valor (antes a sequência, em períodos, aparecia contra o alvo em reais); ATV-3: "Faltam cotas" vira "Cotas a mais na carteira para chegar lá". Teste `indicatorMatchesTarget` |
| 25/10/2026 | Review (8) | O que falta, a folga e o excesso ganham unidade própria (`Evaluation.remainingUnit`): diferenças de percentuais em p.p. ("Falta: 2,64 p.p." no lugar de "Falta (p.p.): 2,64%") e renda que falta numa meta em % em R$ ("Falta de renda por mês") — o front deixa de adivinhar a unidade pelo rótulo. Card do aporte periódico mostra o fim do período como prazo (`params.endDate`) |
| 26/10/2026 | Review (9) | Linha da projeção no gráfico também nas metas de renda (REN-1/2/3: renda mensal estimada mês a mês) e de aportes acumulados (APO-1: o ritmo médio somado até o prazo ou até o alvo); PAT-1, ATV-2 e LP-1 já tinham (patrimônio). Metas de controle não têm projeção (comparam hoje com o limite). APO-2: barras por período (verde cumprido, vermelho não, dourado em andamento) contra o alvo e o alvo menos a tolerância, no lugar da lista e do "Aporte líquido acumulado" — cuja linha verde era o alvo por período desenhado sobre um acumulado |
| 26/10/2026 | Review (10) | Gráficos medem o que a meta mede: RNT-1 = a rentabilidade da meta em cada dia (período da meta terminando no dia: 12 meses móveis, no ano ou desde o início; real e anualizada como a meta) — antes, o TWR acumulado desde o início; RNT-2 = carteira e índice no período da meta, terminando em cada dia (linha do índice); RIS-2 = queda desde o pico em cada dia (o pico da janela); REN-1/3 = renda média de k meses mês a mês, com os proventos do mês em linha fraca; REN-2 = cobertura das despesas (%); APO-1 vai até hoje. `HistoryDTO.extras` (linhas de apoio) e `comparable` também falso em RNT-2 e ATV-3. Gráfico com eixo de tempo contínuo (antes por categoria: séries de densidades diferentes se deslocavam), fotografias ligadas por linha pontilhada, legenda, alvo sempre visível. Detalhamento: desvio com sinal e cor (como o Rebalanceamento), excesso e "fora" em vermelho. Meta concluída pode ser editada: editar reabre (corrige cadastro errado; se o alvo continuar atingido, conclui de novo); evento EDITADA com `reopened` (sem migration nova) |
| 26/10/2026 | Review (11) | Trecho pontilhado liga o último ponto da evolução à primeira fotografia depois dele; alvo fixo como linha verde tracejada na legenda (`HistoryDTO.fixedTarget`); REN-3: o gráfico usava 12 meses fixos e a meta a janela informada (36 → linhas descoladas) — agora a janela da meta, com o aporte médio da mesma janela como segunda linha (o alvo anda, então não vira linha fixa) e a explicação da janela no formulário; sem prazo, a projeção no gráfico vai até 5 anos (antes chegava a décadas e espremia o histórico). RNT-1: o comportamento "12 meses acumulando do começo e depois móvel" é o período "Últimos 12 meses" (a meta está em "No ano", que zera em janeiro) |
| 26/10/2026 | Review (12) | Metas de limite (não passar dele: RIS-1, RIS-2, RIS-3, ALO-2): a linha do alvo vira vermelha com o nome do limite ("Queda máxima 5,0%"), a Situação mostra o limite no lugar de "Alvo" e a barra é o uso do limite (verde até 80%, amarela até 100%, vermelha acima). RIS-2: eixo da queda para baixo (o pico é o zero, em cima) |
| 26/10/2026 | 6 Review | **Concluído** (Guilherme). Próxima: 7 Commit (mensagem sugerida; o commit é do Guilherme) |
| 24/10/2026 | 3 Planning | Seção 5: solução (carga + metas), ajustes ao Anexo A, etapas C1–C2 e M1–M9, decisões D23–D27 e as abertas do Anexo A, critérios de aceitação |

---

## Anexo A — Plano de 13–14/10/2026 (PrimeVest), recuperado do stash sem alteração

> Os nomes (PrimeVest, `primevest-*`), as migrations (V52, V53) e o estado do banco citados abaixo são os de 13/10/2026.
> O que mudou está na seção 2. Única mudança no texto: os títulos desceram um nível (# → ##) para caber no anexo.

## PrimeVest — Plano de Metas Financeiras (metas calculadas a partir da carteira)

> Fase: **planejamento** (13/10/2026). Nada implementado. Respostas de 13/10 na seção 13.1 (D5, D7, D13 decididas;
> D4 e D12 explicadas, aguardam resposta). **Depende do escopo "Rentabilidade"** (backlog do Guilherme, priorizado
> antes deste em 13/10): a série histórica da carteira, o método da cota e a comparação com índices nascem lá e as
> metas reaproveitam (seção 14).
> Origem: ESCOPO "Implementação do módulo de Metas Financeiras no Primevest", ETAPA Planejamento, 13/10/2026.
> Substitui o CRUD manual de metas que existe hoje (`goals` da V1, `GoalService`, `GoalsPage`) e absorve o item
> **26.3** do `docs/PENDENCIAS.md` (Metas usando a caixa de Notificações).

---

### 0. Resumo

**Princípio obrigatório:** o usuário define o objetivo; o PrimeVest calcula a situação atual, o progresso e — quando
houver dados e premissas — a projeção. O cadastro pede só o que é decisão do usuário (alvo, prazo, faixa, escopo,
benchmark, período, premissas que ele queira fixar). Valor atual, progresso, quanto falta, data estimada, renda atual,
alocação e concentração **nunca** são digitados.

O módulo tem quatro peças:

| Peça | O que é |
|---|---|
| **Cadastro genérico** | Uma tabela de metas com tipo, escopo e parâmetros (JSONB validado pelo tipo), ciclo de vida (ativa, pausada, concluída, arquivada) e revisões. Um tipo novo não pede tabela, tela nem endpoint novos |
| **Tipos de meta** | Uma classe por tipo (`GoalType`), registrada no catálogo: diz o que o usuário informa, como calcular o indicador, a natureza da meta (crescente, limite, faixa, sequência, estimativa), a condição de sucesso e se há projeção |
| **Fatos da carteira** | Um contexto único (`GoalFacts`) que lê posições, valor atual, transações, proventos, classificações, índices e qualidade dos dados — o mesmo critério das telas que já existem — e a **série histórica da carteira** (patrimônio e cota dia a dia), que hoje não existe e é a base de rentabilidade, benchmark e queda do pico |
| **Acompanhamento** | Avaliação na leitura (sempre atual), fotografia diária gravada (histórico e auditoria), situação automática e notificações pela caixa que já existe |

**Fora do escopo** (do pedido): ordens automáticas; garantia de rentabilidade, renda ou data; metas com dados pessoais
não cadastrados sem pedi-los; projeções sem premissas; recomendação de investimento a partir de meta atrasada; fonte
externa nova sem avaliação.

---

### 1. Levantamento — o que já existe

#### 1.1 Metas hoje

| Peça | Situação |
|---|---|
| `goals` (V1) | `name`, `target_amount`, **`current_amount` digitado**, `target_date`, `status` (`ACTIVE`, `COMPLETED`, `CANCELLED`). **0 linhas** no banco do DEV (conferido em 13/10) |
| `GoalService` / `GoalController` | CRUD; o progresso é `current_amount ÷ target_amount` com o valor atual informado pelo usuário — o oposto do princípio do escopo. `findOwnedGoal` é o modelo de autorização do projeto e continua |
| `GoalsPage` (`/goals`, menu "Metas") | Cartões com barra de progresso e modal de criação (nome, alvo, data) |

Tudo isso é substituído. Como a tabela está vazia (observação 5 do escopo), a migration recria `goals` sem guarda de
dados — mas mantém a guarda de segurança (`RAISE EXCEPTION` se houver linha), como a V46 fez com `ceiling_prices`.

#### 1.2 Dados que alimentam as metas

| Dado | Onde está | Uso nas metas | Observação |
|---|---|---|---|
| Posição (cotas, custo, preço médio) | `PositionCalculator` (core), das `transactions` | quantidade, valor aplicado, custo | Já trata desdobramento, grupamento e bonificação (`sharesOn` com `StockEventStep`) |
| Valor atual da posição | custo + `portfolio_assets.appreciation` (`StrategyHoldings.held`) | patrimônio, alocação, concentração | Mesmo critério da Alocação e do Rebalanceamento: posição liquidada vale zero |
| Reserva de emergência | `portfolio_assets.is_reservation` | excluir/isolar a reserva | 5 de 38 posições marcadas no DEV |
| Compras e vendas | `transactions` (`BUY`/`SELL`, `transaction_date`, `total_amount`, `origin` MANUAL/AUTO/SISTEMA) | aportes, retiradas, fluxos da rentabilidade | **Não há conta-corrente**: aporte novo e reinvestimento de provento são indistinguíveis (seção 10, L4) |
| Proventos | `dividends` (bruto `total_amount`, líquido `net_amount`, `payment_date`, tipo) + `DividendCalendar` | renda passiva recebida e a receber | `AMORTIZACAO` e `RESTITUICAO_CAPITAL` devolvem capital. No DEV: 236 proventos desde 12/2024 |
| Valorização da renda fixa | `FixedIncomeCurve` (curva), PU oficial do Tesouro (`asset_price_history`, `TESOURO`), `PositionValuationInfo` (base, data de referência, motivo sem curva) | valor atual e histórico da RF | Brutos, sem IR/IOF (impostos fora do projeto) |
| Cotação atual | `assets.current_price`, `price_updated_at` | valor atual, defasagem | **No Tesouro `price_updated_at` é nulo**: a data vem do PU em `asset_price_history` (via `PositionValuationInfo`) |
| Histórico de cotação | `asset_price_history` (`B3_COTAHIST` desde 2010 para ações e FIIs; `BRAPI`; `COINGECKO`; `TESOURO` desde 2010) | série histórica da carteira | Lacunas reais no DEV: seção 1.3 |
| Classificação | `asset_types` (tipo, `fixed_income`, `price_source`), `asset_categories` (setor B3 da ação, tipo do FII…), `issuers` (emissor, setor, segmento), `fixed_income_notes` (indexador, emissor em texto), `asset_terms` | escopo e dimensões de alocação/concentração | Sem moeda/região no modelo (D16); sem gestor; emissor da RF ainda é texto livre (escopo de emissoras de RF em andamento) |
| Estratégia | `strategies` (% ideal por tipo), `strategy_reservations` | aderência à estratégia | 5 estratégias no DEV; a meta **lê** a estratégia, não duplica |
| Índices | `market_index_values` + `MarketIndexCalculator` (acumulado, % do índice, spread) | benchmark, inflação, premissas | CDI, Selic, IPCA, IGP-M, INPC, IBOV, IFIX, dólar desde 2010; mensais até o último mês divulgado |
| Calendário | `BusinessCalendar` | dias úteis (anualização, defasagem) | Conferido contra o CDI 2010–2026 |
| Notificações | `NotificationService.notify` (dedup por chave), `NotificationKind` | avisos de meta | Item 26.3 das pendências |
| Gatilhos | `CatalogReaction` (catálogo mudou: `PROVENTOS`, `FECHAMENTO`, `INDICES`, `BALANCOS`), `PositionWrittenEvent`, `PositionRefreshScheduler` (21:00), `DailyOpeningService` (00:00) | atualizar fotografia e notificações | Reaproveitados; nada de rotina de dados nova |

#### 1.3 Cobertura real (banco do DEV, 13/10/2026)

| Tipo | Posições | Primeira compra | Histórico de cotação dos ativos da carteira |
|---|---|---|---|
| Ações | 11 | 28/11/2024 | COTAHIST desde 2010 — completo |
| FIIs | 11 | 28/11/2024 | COTAHIST desde 2011 — completo |
| Tesouro Direto | 10 | 29/11/2024 | PU oficial — completo para todos os títulos da carteira |
| Renda fixa | 4 | 14/06/2024 | curva (CDI, IPCA, Selic, prefixado) — sem cotação, sai do índice |
| ETF (IVVB11) | 1 | 29/08/2025 | **só BRAPI desde 21/09/2026** — o COTAHIST do projeto carrega só ações e FIIs |
| Cripto (BTC) | 1 | 13/11/2025 | **só CoinGecko desde 24/09/2026** |

Consequência: patrimônio e rentabilidade **retroativos** de ETF e cripto antes de setembro/2026 não têm cotação do dia.
O plano trata isso (D11) em vez de esconder; a carga do histórico de ETF/BDR pelo COTAHIST e de cripto vira pendência
(fonte já existente, mas avaliação de cobertura e custo fica fora deste escopo).

---

### 2. Regras gerais do módulo

| # | Regra |
|---|---|
| G1 | **O usuário define, o sistema calcula.** Nenhum campo de cadastro recebe valor atual, progresso, falta, data estimada, renda atual, alocação ou concentração. Teste automático confere o contrato de cada tipo (cenário 17) |
| G2 | **Três naturezas de número**, sempre rotuladas na API e na tela: **Observado** (dado registrado: transação, provento pago, cotação, índice publicado), **Calculado** (derivado de observados por regra fixa: valor atual, progresso, média, rentabilidade) e **Estimativa** (depende de premissa: projeção, data provável, aporte necessário) |
| G3 | **Ausente não é zero.** Dado faltante deixa o resultado `PARCIAL` (com a lista do que falta e o peso no escopo) ou `INDISPONIVEL` (cálculo suspenso, com o motivo). Só a parte que depende do dado faltante é suspensa |
| G4 | **Toda estimativa mostra as premissas** (valor, origem automática/informada, data de referência) e o selo "Estimativa — não é garantia" |
| G5 | **Limite e faixa não usam % de progresso.** Mostram posição em relação ao limite (folga, excesso, distância da faixa) |
| G6 | **Sinal, não ordem.** Meta de controle fora do objetivo mostra quanto falta/excede e quanto aportar/reduzir para voltar, como informação — nenhuma transação é criada |
| G7 | **Mesmo critério das outras telas.** Valor atual = o da Alocação/Rebalanceamento; proventos = os da tela Dividendos; acumulado de índice = o do `MarketIndexCalculator`. Uma meta nunca mostra um número que contradiga outra tela |
| G8 | **Precisão honesta.** Valores em R$ com 2 casas, percentuais com 1 casa (2 em rentabilidade), projeção em mês/ano e valores estimados arredondados (D19). Resultado parcial nunca aparece com mais casas que o completo |
| G9 | **Histórico não se apaga.** Editar o objetivo cria revisão; pausar, concluir e arquivar são eventos; a fotografia de cada dia guarda a revisão vigente |
| G10 | **Preço atual** (REGRAS 2.6): as metas usam o preço atual; nenhuma regra depende da falta de intradiário |
| G11 | **Autorização** pelo padrão `findOwnedGoal`: meta de outro dono responde 404 |

---

### 3. Definições comuns (valem para todos os tipos)

| Termo | Definição |
|---|---|
| **Escopo** | Conjunto de posições que a meta enxerga. Formas: carteira total; por **tipo** de ativo; por **categoria** (setor B3 da ação, tipo do FII…); por **indexador** (CDI, Selic, IPCA, IGP-M, INPC, prefixado; renda variável à parte); por **região/moeda** (D16); por **emissor**; por **ativo** (ticker); por **posições escolhidas** (objetivo específico, D18). Mais a opção **excluir a reserva de emergência** e **excluir tipos/categorias** |
| **V(t)** — valor atual do escopo | Σ (custo + valorização) das posições abertas do escopo, como `StrategyHoldings.held` |
| **Aporte** do período | Σ compras − Σ vendas com `transaction_date` no período (D6). Liquidação automática (`SISTEMA`) é venda. Compras e vendas aparecem também separadas |
| **Proventos recebidos** | `dividends` com `payment_date` ≤ hoje, valor **líquido** (D5). `AMORTIZACAO`/`RESTITUICAO_CAPITAL` ficam fora da renda (devolvem capital) e dentro da rentabilidade (são dinheiro recebido) |
| **Proventos a receber** | `payment_date` > hoje: **observado** (anunciado), nunca somado à renda recebida. Sem data de pagamento: listado à parte, fora das somas |
| **Mês fechado** | Mês civil já encerrado. Médias de renda e de aporte usam meses fechados; o mês corrente aparece como "em andamento" (D10) |
| **Cota da carteira** | Série diária da rentabilidade pelo método da cota (TWR, D4): `r_d = (V_d + S_d + P_d) ÷ (V_{d−1} + C_d) − 1`, com `C_d` compras do dia, `S_d` vendas do dia e `P_d` proventos pagos no dia; `Cota_d = Cota_{d−1} × (1 + r_d)`. Dia com denominador zero não entra |
| **Defasagem** | Cotação, PU, curva ou índice com data de referência mais velha que o limite da D9 |
| **Qualidade** | `COMPLETA`, `PARCIAL` (com a lista: posição sem cotação valorizada pelo custo, cotação defasada, preço carregado, título sem curva, bonificação sem custo, provento sem data, **importação de integração aguardando confirmação** — transações ainda fora da carteira) ou `INDISPONIVEL` |

---

### 4. Catálogo inicial de tipos

Os ~40 itens do escopo foram agrupados em **17 tipos**: o que muda entre itens parecidos (escopo, prazo, janela, regra
mínimo/máximo/faixa) virou **parâmetro**, não tipo novo. A coluna **Onda** diz de que peça o tipo depende (seção 14).

| Código | Nome na tela | Categoria | Natureza | Projeção | Onda |
|---|---|---|---|---|---|
| PAT-1 | Patrimônio-alvo | Patrimônio e acumulação | crescente | sim | 1 (projeção na 2) |
| APO-1 | Acumular em aportes | Aportes e disciplina | crescente | sim | 1 (projeção na 2) |
| APO-2 | Aporte mensal / anual | Aportes e disciplina | sequência | não | 1 |
| REN-1 | Renda passiva | Renda passiva | crescente | sim | 1 (projeção na 2) |
| REN-2 | Renda cobre despesas | Renda passiva | crescente | sim | 1 (projeção na 2) |
| REN-3 | Renda maior que os aportes | Objetivos de longo prazo | crescente | sim | 2 |
| RNT-1 | Rentabilidade-alvo | Rentabilidade e desempenho | crescente / limite mínimo | não | 3 |
| RNT-2 | Superar um índice (benchmark) | Rentabilidade e desempenho | limite mínimo | não | 3 |
| ALO-1 | Participação de um item | Alocação e diversificação | mínimo · máximo · faixa | não | 1 |
| ALO-2 | Seguir a estratégia | Alocação e diversificação | faixa (por tipo) | não | 1 |
| RIS-1 | Concentração máxima | Risco e concentração | limite máximo (todos os itens) | não | 1 |
| RIS-2 | Queda máxima do pico | Risco e concentração | limite máximo | não | 3 |
| RIS-3 | Qualidade dos dados | Risco e concentração | limite máximo | não | 1 |
| ATV-1 | Quantidade de um ativo | Metas por ativo | crescente | sim | 1 (projeção na 2) |
| ATV-2 | Valor em um ativo | Metas por ativo | crescente | sim | 1 (projeção na 2) |
| ATV-3 | Proventos compram cotas | Metas por ativo | crescente | não | 1 |
| LP-1 | Aposentadoria (estimativa) | Objetivos de longo prazo | estimativa | sim (é o resultado) | 2 |

#### 4.1 Rastreabilidade — cada item do escopo e o tipo que o atende

| Item do escopo | Tipo e parâmetro |
|---|---|
| 1.1 patrimônio-alvo · 1.2 até uma data · 1.4 em uma classe · 1.5 excluindo categorias (ex.: reserva) | PAT-1 (prazo opcional; escopo por tipo/categoria; exclusões) |
| 1.3 acumular em novos aportes | APO-1 |
| 2.1 renda mensal ou anual · 2.2 média em período (12 meses) · 2.3 de uma classe ou ativo | REN-1 (unidade mês/ano; janela; escopo) |
| 2.4 cobrir % das despesas | REN-2 (despesa informada na meta, D17) |
| 2.5 estimativa de renda futura | REN-1 com prazo (projeção) e LP-1 |
| 3.1 rentabilidade acumulada ou anualizada · 3.3 real positiva · 3.5 em período definido | RNT-1 (base acumulada/anualizada; nominal/real; período) — "real positiva" = alvo real 0% |
| 3.2 superar CDI, IPCA… · 3.4 acompanhar a diferença | RNT-2 (a diferença é sempre exibida; margem 0 = superar) |
| 4.1 aporte mensal/anual · 4.3 sequência de aportes | APO-2 (valor por mês/ano; sequência opcional) |
| 4.2 valor acumulado em aportes | APO-1 |
| 5.1 proporção em classes · 5.2 mínimo/máximo em classe, setor, indexador, moeda, região · 5.3 faixa de tolerância · 5.4 aumentar/reduzir | ALO-1 (dimensão, item, regra, prazo opcional para aumentar/reduzir) |
| 5.5 distribuição × estratégia | ALO-2 (lê `strategies`) |
| 6.1 limite por ativo · 6.2 por setor, emissor, gestor, classe · 6.5 sinalizar posições acima do limite | RIS-1 (dimensão; lista os itens acima). **Gestor: indisponível** (não há o dado, L7) |
| 6.3 mínimo/máximo em categorias | ALO-1 |
| 6.4 queda do pico | RIS-2 |
| 6.6 cotações desatualizadas / dados incompletos | RIS-3 |
| 7.1 quantidade · 7.2 valor investido · 7.3 renda de um ativo · 7.4 unidades compradas com proventos · 7.5 peso da posição | ATV-1 · ATV-2 · REN-1 (escopo ativo) · ATV-3 · ALO-1 (dimensão ativo) |
| 8.1 patrimônio em data futura · 8.6 objetivo específico | PAT-1 com prazo · PAT-1 com escopo "posições escolhidas" (D18) |
| 8.2 patrimônio na aposentadoria · 8.3 renda futura nominal e real | LP-1 |
| 8.4 quando a renda alcança um valor | REN-1 sem prazo → "data estimada" |
| 8.5 quando a renda supera os aportes | REN-3 |

---

### 5. Regras por tipo — o que o usuário informa e o que o sistema calcula

Em todos: o usuário informa **nome**, **tipo** (a categoria vem do tipo) e, quando o tipo pede, **escopo**, **prazo**
e **avisos** (liga/desliga). Abaixo, o que é próprio de cada um. "Sistema calcula" é sempre automático.

#### PAT-1 — Patrimônio-alvo
- **Usuário informa:** valor-alvo (R$); **com ou sem a reserva de emergência — escolha obrigatória, sem valor
  pré-marcado** (D7, 13/10); prazo (opcional); escopo e exclusões; premissas da projeção que queira fixar.
- **Sistema calcula:** `V` do escopo; progresso `V ÷ alvo × 100`; falta `max(alvo − V, 0)`; **decomposição** desde a
  primeira transação do escopo (ou desde a criação da meta, à escolha na tela, sem gravar): aportes líquidos, resultado
  de mercado (`V − aportes líquidos`) e proventos recebidos (à parte — saíram da carteira); projeção (seção 6): data
  estimada sem prazo, aporte mensal necessário com prazo.
- **Sucesso:** `V ≥ alvo` (no prazo, se houver).
- **Dados:** posições, valorização, transações, proventos; qualidade herdada das posições do escopo.

#### APO-1 — Acumular em aportes
- **Usuário informa:** valor-alvo; **início da contagem** (padrão: data de criação — pode ser retroativo); prazo
  (opcional); escopo.
- **Sistema calcula:** aporte líquido acumulado desde o início (compras e vendas separadas); progresso; falta;
  média mensal (meses fechados); aporte mensal necessário (`falta ÷ meses restantes`, sem juros — é dinheiro novo);
  data estimada pelo ritmo médio (estimativa).
- **Sucesso:** acumulado ≥ alvo. Acumulado negativo (vendeu mais que comprou) aparece negativo, com progresso 0% e o
  texto "retiradas superam os aportes".

#### APO-2 — Aporte mensal / anual
- **Usuário informa:** valor por período; periodicidade (mês/ano); início; fim (opcional); sequência desejada
  (opcional, ex.: 12 meses seguidos); tolerância (opcional, em R$ ou %).
- **Sistema calcula:** aporte líquido de cada período fechado; período cumprido se `aporte ≥ valor − tolerância`;
  sequência atual e maior sequência; % de períodos cumpridos; período corrente "em andamento" (quanto falta para
  cumprir).
- **Sucesso:** sem sequência — cada período é avaliado (situação "no ritmo" se o último fechado foi cumprido); com
  sequência — sequência atual ≥ desejada. Com fim, o resultado final é registrado no fim.

#### REN-1 — Renda passiva
- **Usuário informa:** valor-alvo e unidade (por mês ou por ano); janela da média (padrão 12 meses); escopo (carteira,
  tipo, categoria, ativo); prazo (opcional).
- **Sistema calcula:** soma dos proventos líquidos recebidos na janela (meses fechados) e a média mensal; anual = soma
  dos 12 meses (nunca um pagamento isolado × 12); a receber anunciado (separado); renda do mês corrente (parcial);
  *yield* da janela = renda da janela ÷ valor médio do escopo na janela (com a série histórica; antes dela, ÷ `V` de hoje,
  rotulado); histórico parcial quando o escopo tem menos meses que a janela (D10); projeção: data estimada em que a
  renda alcança o alvo, ou renda estimada no prazo (patrimônio projetado × *yield*), nominal e real.
- **Sucesso:** média mensal (ou soma anual) ≥ alvo.
- **Limitações:** cupons semestrais do Tesouro não existem no sistema (PENDENCIAS 24.1); juros da renda fixa privada não
  são provento (só viram dinheiro no resgate) — ficam fora da renda (L5).

#### REN-2 — Renda cobre despesas
- **Usuário informa:** despesa mensal de referência e a data dela (D17); % de cobertura desejado (padrão 100%); opção
  de corrigir a despesa pelo IPCA desde a data informada; escopo da renda.
- **Sistema calcula:** renda média mensal (como REN-1); despesa corrigida (se escolhido); cobertura `renda ÷ despesa ×
  100`; progresso `cobertura ÷ % desejado`; falta em R$ de renda; projeção como REN-1.
- **Sem despesa informada o tipo não pode ser salvo** (não há de onde calcular) — é o caso "pedir o dado" do escopo.

#### REN-3 — Renda maior que os aportes
- **Usuário informa:** janela (padrão 12 meses); escopo.
- **Sistema calcula:** renda média mensal e aporte líquido médio mensal na janela; razão `renda ÷ aporte`; data
  estimada do cruzamento (projeção da renda com as premissas; o aporte é a premissa de aporte).
- **Sucesso:** renda média ≥ aporte médio. Aporte médio ≤ 0 → "atingida" com o texto "sem aportes líquidos no período".

#### RNT-1 — Rentabilidade-alvo
- **Usuário informa:** % alvo; base (acumulada no período ou anualizada); nominal ou real (descontada do IPCA);
  período (desde o início da carteira, ano corrente, últimos 12 meses ou datas de/até); escopo.
- **Sistema calcula:** rentabilidade pela cota (D4) no período; real = `(1 + nominal) ÷ (1 + IPCA do período) − 1`,
  com o IPCA até o último mês divulgado (o período real termina nele, rotulado); anualizada =
  `(1 + R)^(252 ÷ dias úteis) − 1`, **só para período ≥ 12 meses** (abaixo disso aparece "período curto para
  anualizar"); aportes e retiradas não contam como ganho nem perda (o método neutraliza os fluxos).
- **Sucesso:** rentabilidade ≥ alvo (alvo 0% real = "rentabilidade real positiva").
- **Dados:** série histórica da carteira; qualidade = a pior qualidade dos dias do período (D11).

#### RNT-2 — Superar um índice
- **Usuário informa:** índice (CDI, Selic, IPCA, IGP-M, INPC, Ibovespa, IFIX, dólar) e forma: o próprio índice,
  **% do índice** (ex.: 110% do CDI) ou **índice + taxa** (ex.: IPCA + 5% a.a.) — as mesmas formas do
  `/indices/{code}/accumulated`; período; escopo.
- **Sistema calcula:** rentabilidade da carteira (como RNT-1) e do índice no mesmo intervalo de datas; diferença em
  pontos percentuais e relativa `(1 + R_carteira) ÷ (1 + R_índice) − 1`; % do índice `R_carteira ÷ R_índice` (só
  quando `R_índice > 0`); série das duas curvas para o gráfico.
- **Sucesso:** `R_carteira ≥ R_índice ajustado`.
- **Índice sem dado no período** (ex.: IPCA do mês corrente, índice atrasado): o período é encurtado até o último dado
  comum e isso é exibido; sem nenhum dado comum → `INDISPONIVEL` (cenário 10).

#### ALO-1 — Participação de um item
- **Usuário informa:** dimensão (tipo, categoria, indexador, região/moeda, emissor, ativo); item; regra — **mínimo**
  `p_min`, **máximo** `p_max` ou **faixa** (alvo ± tolerância, ou de/até); base (D7); prazo (opcional — para
  "aumentar/reduzir até tal data").
- **Sistema calcula:** participação atual `w = V_item ÷ V_base`; folga ou excesso em pontos e em R$; quanto aportar no
  item para atingir o mínimo `(p·T − C) ÷ (1 − p)` (a fórmula do aporte da reserva, já conferida); quanto aportar fora
  do item para voltar ao máximo `C ÷ p − T`; quanto reduzir o item `(C − p·T) ÷ (1 − p)`. Sempre "informativo — não é
  ordem".
- **Sucesso:** mínimo `w ≥ p_min`; máximo `w ≤ p_max`; faixa `p_min ≤ w ≤ p_max`. **O limite exato está dentro**
  (cenário 8). Com prazo e fora da regra antes do prazo: "em andamento"; depois do prazo: "fora do objetivo".

#### ALO-2 — Seguir a estratégia
- **Usuário informa:** tolerância em pontos percentuais (ex.: ±5 p.p.) — os percentuais ideais vêm de `strategies`.
- **Sistema calcula:** para cada tipo, % atual (base sem a reserva, igual ao Rebalanceamento) × ideal; desvio; maior
  desvio; distância total `Σ|desvio| ÷ 2`; tipos fora da faixa. Sem estratégia cadastrada → `INDISPONIVEL` com o link
  para a Estratégia.
- **Sucesso:** todos os tipos dentro da tolerância.

#### RIS-1 — Concentração máxima
- **Usuário informa:** dimensão (ativo, emissor, categoria/setor, tipo, indexador, região); limite `p_max`; base (D7).
- **Sistema calcula:** participação de cada item da dimensão; o maior; lista dos itens acima do limite com o excesso em
  pontos e em R$. Emissor: ações e FIIs por `issuers`; renda fixa pelo emissor do título (texto livre hoje — L6).
- **Sucesso:** nenhum item acima de `p_max` (o limite exato está dentro).

#### RIS-2 — Queda máxima do pico
- **Usuário informa:** limite de queda (%); janela (desde o início ou últimos N meses); escopo.
- **Sistema calcula:** queda atual `Cota_hoje ÷ máximo(Cota) − 1`, maior queda da janela, data do pico e do fundo.
  Medida **sobre a cota, não sobre o patrimônio** (D4): o patrimônio cai quando você resgata, e isso não é perda.
- **Sucesso:** queda atual ≤ limite (e a maior queda da janela é exibida). Sem série suficiente (menos de 2 meses) →
  `INDISPONIVEL`.

#### RIS-3 — Qualidade dos dados
- **Usuário informa:** máximo de pendências aceitas (padrão 0) e quais contam (cotação defasada, sem cotação, título
  sem curva, bonificação sem custo, provento sem data de pagamento, importação aguardando confirmação).
- **Sistema calcula:** lista das posições com cada pendência, a data de referência de cada dado e o peso no patrimônio.
- **Sucesso:** pendências ≤ máximo. É também a fonte da "qualidade" de todas as outras metas.

#### ATV-1 — Quantidade de um ativo
- **Usuário informa:** ativo (ticker); quantidade-alvo; prazo (opcional).
- **Sistema calcula:** cotas atuais (com desdobramento, grupamento e bonificação); progresso; falta; custo estimado da
  falta ao preço atual (estimativa); ritmo médio de compra (cotas/mês nos últimos 12 meses fechados) e data estimada.
- **Sucesso:** cotas ≥ alvo. Renda fixa privada (sem cotas) não aceita este tipo.

#### ATV-2 — Valor em um ativo
- **Usuário informa:** ativo; valor-alvo; base — **valor aplicado** (custo) ou **valor de mercado**; prazo (opcional).
- **Sistema calcula:** o valor na base escolhida; progresso; falta; projeção como PAT-1 com escopo no ativo.

#### ATV-3 — Proventos compram cotas
- **Usuário informa:** ativo; cotas por mês desejadas com os proventos (padrão 1 — o "número mágico").
- **Sistema calcula:** provento médio mensal por cota (12 meses fechados); cotas compráveis por mês hoje =
  `renda mensal média do ativo ÷ preço atual`; cotas necessárias para o alvo = `alvo × preço ÷ provento mensal por
  cota`; falta em cotas. Sem provento no período → `INDISPONIVEL` ("o ativo não pagou nos últimos 12 meses").
- **Sucesso:** cotas compráveis/mês ≥ alvo.

#### LP-1 — Aposentadoria (estimativa)
- **Usuário informa:** data de início da aposentadoria; renda mensal desejada em valores de hoje (opcional); premissas
  que queira fixar; taxa de retirada (opcional — sem ela a renda estimada é só a dos proventos).
- **Sistema calcula:** patrimônio projetado na data (nominal e real); renda estimada na data (patrimônio × *yield* de
  proventos e, se informada, × taxa de retirada), nominal e em valores de hoje; se houver renda desejada: diferença e
  aporte mensal necessário.
- **Natureza:** estimativa — não tem "atingida" antes da data; situação "no ritmo"/"abaixo do desejado" quando há renda
  desejada, senão só "estimativa".

---

### 6. Projeções

Um motor único (`GoalProjection`, regra pura no core, sem banco) atende todos os tipos com projeção.

#### 6.1 Premissas

Cada premissa tem **origem**: `AUTOMATICA` (recalculada a cada avaliação a partir dos dados) ou `INFORMADA` (fixada
pelo usuário na meta). É o mesmo desenho do Preço Teto (padrão × informado, ↻ para voltar ao automático), e a
premissa automática **não é digitada**: o usuário só a fixa se quiser.

| Premissa | Automática (padrão) | Observação |
|---|---|---|
| Retorno nominal a.a. | D12 | Convertido para o mês: `(1 + i_a)^(1/12) − 1` |
| Aporte mensal | média do aporte líquido dos 12 meses fechados do escopo | Escopo com menos de 12 meses: média dos meses com posição, rotulada |
| Inflação a.a. | IPCA acumulado em 12 meses (último divulgado) | Para valores reais |
| *Yield* de proventos a.a. | renda líquida 12 meses ÷ valor médio do escopo no período | Só tipos de renda |
| Taxa de retirada a.a. | — (só se informada) | LP-1 |

#### 6.2 Fórmulas (mês a mês, `i` mensal, `n` meses, `PMT` aporte mensal, `PV` = V de hoje, `FV` = alvo)

| Pergunta | Fórmula | Casos de borda |
|---|---|---|
| Valor no prazo | `PV(1+i)^n + PMT · ((1+i)^n − 1) ÷ i` | `i = 0` → `PV + PMT·n` |
| Aporte mensal necessário até o prazo | `(FV − PV(1+i)^n) · i ÷ ((1+i)^n − 1)` | `i = 0` → `(FV − PV) ÷ n`; resultado ≤ 0 → "não precisa de aporte com estas premissas"; prazo vencido → não se aplica |
| Meses até o alvo | `ln((FV·i + PMT) ÷ (PV·i + PMT)) ÷ ln(1+i)` | `i = 0` → `(FV − PV) ÷ PMT`; `PMT ≤ 0` e `i ≤ 0` → "não alcança com as premissas atuais"; acima de 100 anos → idem |
| Valor real | `nominal ÷ (1 + inflação)^(n/12)` | — |
| Renda estimada | `valor projetado × yield ÷ 12` (+ `× taxa de retirada ÷ 12` em LP-1) | *yield* indisponível → só patrimônio |

#### 6.3 Regras de apresentação

- Selo **Estimativa** em todo número desta seção, com as premissas ao lado e a data de referência de cada uma.
- Data estimada em **mês/ano**; valores arredondados (D19).
- Sem dado para alguma premissa automática e sem premissa informada → a projeção fica **suspensa** com o motivo
  ("sem 12 meses de aportes", "IPCA indisponível"); o resto da meta segue calculado.
- Nenhum tipo de controle (limite, faixa, qualidade, estratégia) tem projeção: o escopo diz que nem toda meta admite;
  a tela diz "esta meta não tem projeção — ela compara a situação de hoje com o limite".

---

### 7. Situação e ciclo de vida

Dois eixos independentes, como nas Notificações:

| Eixo | Valores | Quem muda |
|---|---|---|
| **Ciclo** | Ativa · Pausada · Concluída · Arquivada | o usuário; **Concluída também pelo sistema** quando atinge o alvo (D13) |
| **Situação** (só da meta ativa) | No ritmo · Em andamento · Atrasada · Prazo vencido · Dentro do objetivo · Fora do objetivo · Estimativa · Sem dados suficientes | o sistema, a cada avaliação |

#### 7.1 Situação por natureza

| Natureza | Regra |
|---|---|
| Crescente sem prazo | `V ≥ alvo` → **concluída pelo sistema** (D13); senão **Em andamento** |
| Crescente com prazo | `V ≥ alvo` → **concluída pelo sistema** (D13); prazo passou e `V < alvo` → **Prazo vencido**; senão pela projeção (D14): alcança até o prazo → **No ritmo**, não alcança → **Atrasada**; projeção suspensa → **Em andamento** (com o motivo) |
| Limite / faixa | dentro → **Dentro do objetivo**; fora → **Fora do objetivo** (com prazo e antes dele: **Em andamento**) |
| Sequência (APO-2) | último período fechado cumprido → **No ritmo**; não cumprido → **Atrasada**; sequência atingida → **concluída pelo sistema** |
| Estimativa (LP-1) | com renda desejada: **No ritmo** / **Atrasada**; sem: **Estimativa** |
| Qualquer | indicador `INDISPONIVEL` → **Sem dados suficientes** (com o que falta) |

`PARCIAL` não muda a situação: aparece como selo "dados parciais" ao lado dela.

#### 7.2 Ciclo

| Ação | Efeito |
|---|---|
| Pausar | para a fotografia diária e os avisos; a tela mostra a situação calculada na hora, rotulada "pausada" |
| Retomar | volta a fotografar; o intervalo pausado fica visível no histórico |
| Concluir | **automático** quando a meta crescente ou de sequência atinge o alvo (D13), com aviso; também manual. Congela: grava o resultado final (indicador, alvo, data, fotografia do dia) e para de calcular — se o patrimônio cair depois, a meta continua concluída. Metas de controle (limite, faixa, estratégia, qualidade, queda) e a estimativa (LP-1) **não** se concluem sozinhas: não têm ponto de chegada |
| Arquivar | sai da lista principal (filtro "Arquivadas"); mantém tudo |
| Desarquivar | volta ao ciclo anterior ao arquivamento |
| Excluir | D15 |
| Editar | nova **revisão** (seção 9); o tipo não muda (trocar de tipo = meta nova) |

---

### 8. Atualização, histórico e avisos

| Momento | O que acontece |
|---|---|
| Leitura (`GET`) | **Avaliação na hora**, com os dados atuais: a tela nunca mostra número velho. Um único `GoalFacts` por requisição atende todas as metas do usuário |
| Rodada diária, logo depois do refresh das 21:00 (`PositionRefreshScheduler`) | Fotografia do dia de cada meta ativa (upsert em `goal_snapshots`), eventos de situação (atingiu, saiu do objetivo, prazo venceu) e avisos |
| Criar, editar, mudar ciclo | Fotografia do dia na hora (com a revisão nova) |
| Transação gravada (`PositionWrittenEvent`) e "catálogo mudou" (`CatalogReaction`: preço, proventos, índices) | Reavalia as metas ativas **daquele usuário** depois do commit, assíncrono e agrupado (várias transações seguidas = uma reavaliação), atualizando a fotografia do dia e os avisos |

A periodicidade de cada dado é a da sua rotina atual (fechamento diário, PU do Tesouro, CDI diário, IPCA mensal,
proventos B3/FNET): a meta não cria rotina de coleta (escopo, DADOS 8). Cada resultado traz a **data de referência de
cada fonte** que usou.

#### 8.1 Histórico da meta (D3)

- **Fotografia diária** (`goal_snapshots`): o que a meta mostrava naquele dia, com a revisão vigente e a qualidade. É a
  auditoria — não se reescreve quando um dado do passado muda.
- **Evolução recalculada**: nos tipos cujo indicador sai da série histórica (PAT-1, APO-1, REN-1, RNT, RIS-2, ATV),
  o gráfico mostra a série recalculada desde antes da criação da meta, com o alvo de cada revisão; a fotografia aparece
  como pontos de conferência. Uma transação retroativa muda a evolução, e a diferença em relação à fotografia fica
  visível.

#### 8.2 Avisos (Notificações — PENDENCIAS 26.3)

Categoria nova `METAS` e tipos novos em `NotificationKind` (D20), gravados pelo `NotificationService` existente, com a
chave de repetição por meta e revisão. Liga/desliga por meta (padrão ligado). Pendência resolve sozinha quando a
condição volta.

---

### 9. Banco (migrations)

#### 9.1 V52 — metas

- **Guarda**: `RAISE EXCEPTION` se `goals` tiver linha (está vazia; o escopo autoriza recriar).
- `DROP TABLE goals` e criação de:

| Tabela | Colunas principais | Por quê |
|---|---|---|
| `goals` | `id`, `user_id` (FK cascade), `name`, `type` (código do catálogo), `category`, `lifecycle` (`ATIVA`/`PAUSADA`/`CONCLUIDA`/`ARQUIVADA`), `lifecycle_before_archive`, `target_value` `numeric(18,8)` (R$, %, cotas de cripto), `target_date`, `scope` JSONB, `params` JSONB (o resto do tipo, com as premissas e a origem de cada uma), `notify` (default true), `notes`, `revision`, `achieved_on` (primeira vez que atingiu), `final_result` JSONB (concluída/prazo), `created_at`, `updated_at` | Colunas para o que a lista filtra e ordena; JSONB validado pelo tipo para o que varia — tipo novo sem migration (critério 14). D2 |
| `goal_events` | `id`, `goal_id` (FK cascade), `kind` (`CRIADA`, `EDITADA`, `PAUSADA`, `RETOMADA`, `CONCLUIDA`, `ARQUIVADA`, `DESARQUIVADA`, `ATINGIDA`, `SAIU_DO_OBJETIVO`, `VOLTOU_AO_OBJETIVO`, `PRAZO_VENCIDO`), `revision`, `definition` JSONB (nome, alvo, prazo, escopo e parâmetros **completos** daquela revisão; só nos eventos que mudam a definição), `occurred_at` | Alterações e resultados auditáveis (critério 13) |
| `goal_snapshots` | PK `(goal_id, reference_date)`, `revision`, `indicator` `numeric(18,8)`, `target`, `progress_pct` (nulo em limite/faixa), `situation`, `quality`, `result` JSONB (indicador, decomposição, projeção e premissas, fontes com data, o que falta) | Fotografia diária (seção 8.1) |

Índices: `goals(user_id, lifecycle)`, `goal_events(goal_id, occurred_at)`.

#### 9.2 V53 — região e moeda dos tipos de ativo (só se D16 = a)

`asset_types.region` (`BRASIL`/`EXTERIOR`) e `currency` (`BRL`/`USD`), `NOT NULL` com default `BRASIL`/`BRL`
(REGRAS 8.1) e o seed por `name` dos tipos de fora (STOCK, REIT, ETF_INTERNACIONAL → EXTERIOR/USD; BDR → EXTERIOR/BRL;
CRIPTO → região própria `CRIPTO`), como os seeds de referência fazem. Coluna, não lista de nomes no código (mesma razão
de `price_source`).

#### 9.3 O que **não** é gravado

A série histórica da carteira (patrimônio, fluxos e cota por dia) é **calculada na leitura** (D1), como os indicadores
das emissoras e o acumulado dos índices, com cache em memória por usuário invalidado nos mesmos gatilhos da seção 8.

---

### 10. Limitações conhecidas (e como o módulo as trata)

| # | Limitação | Tratamento |
|---|---|---|
| L1 | Cripto com cotação só desde 24/09/2026; ETF só desde 21/09/2026 (o COTAHIST do projeto carrega só ações e FIIs; BDR idem) | Série histórica com preço carregado (D11) e qualidade `PARCIAL` nos dias afetados; pendência nova: avaliar a carga histórica de ETF/BDR pelo COTAHIST e de cripto |
| L2 | Tipos `MANUAL` (Stocks, REITs, ETFs internacionais, fundos, outros) sem cotação | Valor pelo custo, rotulado (D8) |
| L3 | Renda fixa sem curva (TR, título sem dados de remuneração) | Valor como está gravado, rotulado; entra em RIS-3 |
| L4 | Sem conta-corrente: reinvestimento de provento é compra como outra qualquer | "Aporte" = compras − vendas, documentado; tela mostra proventos recebidos ao lado para o usuário enxergar |
| L5 | Juros da renda fixa e cupons do Tesouro não são proventos no sistema | Fora da renda passiva (REN-*), dentro da rentabilidade (pela valorização) |
| L6 | Emissor da renda fixa ainda é texto livre | Concentração por emissor agrupa pelo texto normalizado até o escopo de emissoras de RF; depois passa ao cadastro/grupo |
| L7 | Gestor não existe no modelo | Dimensão "gestor" não oferecida; mensagem na tela de criação |
| L8 | IPCA do mês corrente não divulgado | Real e IPCA+ terminam no último mês divulgado, rotulado |
| L9 | Despesas pessoais não cadastradas | Pedidas na própria meta REN-2 (D17) |
| L10 | Proventos de posição que perdeu todas as transações somem (cascata em `dividends`) | Fotografia guarda o que se via; evolução recalculada reflete o banco atual |
| L11 | Valores brutos de IR/IOF/taxas na renda fixa (impostos fora do projeto) | Documentado na metodologia |

---

### 11. API (app-api, `/api/v1/goals`)

| Método | Endpoint | Descrição |
|---|---|---|
| GET | `/goals/types` | Catálogo: para cada tipo, categoria, natureza, campos que o usuário informa (com tipo, unidade, obrigatoriedade, padrão), escopos e dimensões aceitos, se tem projeção, dados que usa e limitações. A tela de cadastro é montada daqui |
| POST | `/goals/preview` | Avalia uma definição **sem gravar** (como o `/ceiling-prices/calculate`): o formulário mostra o valor atual calculado enquanto o usuário preenche |
| GET | `/goals?lifecycle=&category=&situation=` | Lista com a avaliação de cada meta e um resumo (ativas, atingidas, fora do objetivo, sem dados) |
| GET | `/goals/{id}` | Definição, avaliação completa (indicador, alvo, progresso ou posição no limite, falta, decomposição, projeção e premissas, fontes com data, memória de cálculo, qualidade) |
| GET | `/goals/{id}/history?from=&to=` | Evolução recalculada + fotografias + alvos por revisão |
| GET | `/goals/{id}/events` | Alterações e resultados |
| POST | `/goals` · PUT `/goals/{id}` | Cria · edita (nova revisão). Validação pelo tipo: campo desconhecido, campo calculável enviado ou alvo inválido → 400/422 com mensagem em português |
| POST | `/goals/{id}/pause` · `/resume` · `/complete` · `/archive` · `/unarchive` | Ciclo (422 em transição inválida) |
| DELETE | `/goals/{id}` | Conforme D15 |

A resposta de avaliação marca cada número com `nature: OBSERVADO | CALCULADO | ESTIMATIVA` (G2) e `quality`.

---

### 12. Tela

Mesma rota e item de menu (`/goals`, "Metas"); detalhe em `/goals/:id`. Tema dark do projeto, Recharts.

| Parte | Conteúdo |
|---|---|
| **Visão geral** | Cartões de resumo (ativas, atingidas, no ritmo, atrasadas/fora do objetivo, sem dados); filtros por categoria, situação e ciclo (Ativas · Pausadas · Concluídas · Arquivadas) |
| **Lista** | Um cartão por meta: nome, categoria, objetivo em uma frase ("Ter R$ 500 mil até dez/2035"), indicador atual, alvo, **barra de progresso** (crescente) ou **régua com o limite/faixa** (controle), quanto falta ou o que está fora, prazo, situação, selo de dados parciais e "atualizado em" |
| **Nova meta** | 1) categoria → 2) tipo (com a explicação em linguagem simples e o que o sistema vai calcular) → 3) formulário gerado do catálogo, com o **valor atual calculado ao vivo** (preview) e a frase "você não precisa informar quanto já tem" → salvar |
| **Detalhe** | Situação atual (observado/calculado), falta ou condição fora, decomposição (aporte × mercado × proventos), projeção com premissas editáveis (↻ volta ao automático), gráfico de evolução (realizado contínuo, alvo em linha, projeção tracejada e em outra cor), histórico de alterações, origem dos dados e memória de cálculo (D21) e ações do ciclo |
| **Legenda** | Observado · Calculado · Estimativa — mesma ideia de Padrão/Informado/Calculado do Preço Teto |
| **Sem dados** | Mensagem com o que falta e o atalho para resolver (Transações, Estratégia, Notificações…) |
| **Linguagem** | Sem sigla sem explicação ("rentabilidade pelo método da cota — tira o efeito dos aportes"); termos técnicos com (i) |

---

### 13. Decisões para o Guilherme

#### 13.1 Respostas de 13/10/2026

| # | Resposta |
|---|---|
| D4 | Pediu o contexto (explicado na conversa: rentabilidade simples × método da cota). Passa a ser decisão do escopo Rentabilidade |
| D5 | **Sim** — proventos líquidos |
| D7 | Metas de patrimônio: **com ou sem reserva explícito no cadastro** (campo obrigatório, sem pré-marcação). Alocação e concentração: recomendação (padrão sem reserva) mantida, aguardando confirmação |
| D12 | Pediu o contexto (explicado: é a taxa de retorno suposta na projeção, não filtro de período). Aguarda resposta |
| D13 | **Revisada**: meta atingida é **concluída sozinha e notificada** (seções 7 e 8.2 ajustadas) |
| Série histórica | É implementável hoje com os dados que existem (lacunas de ETF e cripto, seção 1.3). É o núcleo do escopo "Rentabilidade" (menu com acompanhamento de benchmark), que o Guilherme **prioriza antes deste** |

#### 13.2 Tabela de decisões

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D1** | Série histórica da carteira (patrimônio e cota por dia) | (a) calculada na leitura, com cache; (b) gravada todo dia numa tabela derivada | **(a)**: segue o princípio "indicador calculado, sem gravar"; transação retroativa aparece sozinha. Volume pequeno (≈ 40 posições × 700 dias). A curva da renda fixa ganha uma versão "série" para não recalcular dia a dia do zero |
| **D2** | Parâmetros dos tipos | (a) colunas comuns + JSONB validado pelo tipo; (b) uma tabela por tipo | **(a)**: tipo novo sem migration nem tela nova (critério 14 e observação 4) |
| **D3** | Histórico da meta | (a) fotografia diária gravada **e** evolução recalculada; (b) só fotografia; (c) só recalculada | **(a)**: a fotografia é a auditoria (o que se via), a recalculada é a verdade de hoje e cobre o período antes da criação |
| **D4** | Método de rentabilidade | (a) cota diária (TWR); (b) taxa interna de retorno (TIR); (c) as duas, com a cota oficial | **(a)**: é o método dos fundos e o único comparável com CDI/IPCA; neutraliza aportes e retiradas. Queda do pico também na cota. A TIR fica como pendência |
| **D5** | Proventos em renda e rentabilidade | líquido (depois do IR retido) · bruto | **Líquido** nos dois (é o dinheiro que entra; o IR do JCP fica na fonte). Bruto exibido ao lado na renda. Amortização/restituição: fora da renda, dentro da rentabilidade |
| **D6** | "Aporte" | (a) líquido = compras − vendas; (b) bruto = compras | **(a)**: trocar um ativo por outro não é dinheiro novo; compras e vendas aparecem separadas |
| **D7** | Base de alocação e concentração | carteira inteira · sem a reserva de emergência | **Escolha por meta, padrão sem a reserva** (igual Alocação e Rebalanceamento). Patrimônio (PAT-1): padrão carteira inteira, com "excluir reserva" |
| **D8** | Posição sem cotação nenhuma (tipos MANUAL, título sem curva) | (a) valor pelo custo, rotulado, meta `PARCIAL`; (b) meta `INDISPONIVEL` | **(a)**: o custo é dado observado, não zero; o rótulo e o peso no escopo deixam claro. Concentração/alocação avisam quando o item sem cotação pesa mais que 5% |
| **D9** | Quando um dado está defasado | limites em dias úteis | **Cotação B3/BRAPI/cripto e PU do Tesouro: mais de 3 dias úteis; curva da RF: CDI com mais de 3 dias úteis; índice mensal: mês anterior não divulgado até o dia 20** — só marca, não bloqueia |
| **D10** | Renda média com histórico menor que a janela | (a) soma ÷ janela (12), mostrando "k meses de histórico"; (b) soma ÷ k | **(a)**, só com meses fechados (o mês corrente aparece como parcial): conservador e sem anualizar pagamento isolado |
| **D11** | Dia sem cotação na série histórica (L1) | (a) último preço observado — da série ou da **própria transação** — carregado, com marca e cobertura do dia; (b) dia sem valor = período indisponível | **(a)**, com a regra: se mais de 10% do patrimônio do dia está com preço carregado há mais de 5 dias úteis, a rentabilidade do período fica `PARCIAL` e a tela diz quais ativos |
| **D12** | Retorno padrão das projeções (premissa automática) | (a) CDI acumulado 12 meses; (b) rentabilidade histórica da carteira (cota, ≥ 24 meses); (c) taxa real informada (ex.: IPCA + x%) | **(a)** como padrão neutro e observável, com (b) e (c) oferecidas na tela; nenhuma é sugestão de investimento |
| **D13** | Meta crescente que atinge o alvo | (a) continua ativa; (b) conclui sozinha | **Decidida em 13/10: (b)**, com aviso. Prazo vencido sem atingir: resultado gravado no prazo (`final_result`) e a meta fica em "Prazo vencido" até você editar, concluir ou arquivar |
| **D14** | "No ritmo" × "Atrasada" com prazo | (a) pela projeção com as premissas; (b) trajetória linear do início ao alvo | **(a)** para patrimônio, renda e valor (juros compostos); **linear** onde não há rendimento (APO-1, ATV-1 por cotas) |
| **D15** | Excluir meta | (a) só arquivar; (b) excluir de vez só a arquivada, com confirmação | **(b)**: arquivar é o caminho normal; excluir existe para engano, e só depois de arquivar |
| **D16** | Região e moeda | (a) colunas em `asset_types` (V53); (b) fora por enquanto | **(a)**: duas colunas de referência, e as metas 5.2 do escopo funcionam |
| **D17** | Despesas mensais (REN-2) | (a) informadas na própria meta, com data e correção opcional pelo IPCA; (b) cadastro de orçamento no perfil (escopo próprio) | **(a)** agora; (b) vira pendência se outras telas precisarem |
| **D18** | Objetivo específico por posições escolhidas | posição pode estar em mais de uma meta · só em uma | **Pode estar em várias** (cada meta é independente); a tela avisa a sobreposição |
| **D19** | Precisão | — | **R$ com 2 casas; percentuais com 1 (rentabilidade com 2); projeções em mês/ano e valores estimados arredondados a R$ 100** |
| **D20** | Avisos de meta | quais tipos | **Concluída** (informativa, quando o sistema conclui — D13); **Fora do objetivo** (pendência, resolve ao voltar); **Atrasada** (pendência, só na mudança de "no ritmo" para "atrasada"); **Prazo vencido** (informativa); **Sem dados** (pendência). Liga/desliga por meta, padrão ligado |
| **D21** | Memória de cálculo e origem dos dados na tela (sua dúvida no item 7 da tela) | (a) fonte e data de cada dado sempre visíveis; fórmula e números num (i) "Como calculamos" que abre um modal; (b) só fonte e data na tela, memória só na API (como o Preço Teto ficou); (c) cartão aberto | **(a)**: o critério 12 pede rastreabilidade e o 15 pede a data visível; o modal não pesa na tela, diferente do cartão que você tirou do Preço Teto |
| **D22** | Ordem das ondas | (a) as três ondas e a série histórica neste escopo, por etapas; (b) só a onda 1 agora | **(a)**, na ordem da seção 14: cada etapa fecha verde antes da próxima (REGRAS 2.3) |

---

### 14. Etapas

| Etapa | Entrega | Depende |
|---|---|---|
| **M1** | V52; entidades `Goal`, `GoalEvent`, `GoalSnapshot`; contrato `GoalType` (campos, validação, natureza, avaliação, projeção opcional) e `GoalTypeRegistry`; ciclo de vida e revisões; API de cadastro, ciclo, `/types` e `/preview`; o CRUD antigo sai | — |
| **M2** | `GoalFacts`: posições e valor atual por escopo e dimensão (tipo, categoria, indexador, emissor, ativo, posições, reserva), fluxos, proventos (recebidos, a receber, sem data), estratégia, índices, qualidade e defasagem (D8, D9), fontes com data. V53 se D16 = (a) | M1 |
| **M3** | **Onda 1** — tipos sem série nem projeção: PAT-1, APO-1, APO-2, REN-1, REN-2, ALO-1, ALO-2, RIS-1, RIS-3, ATV-1, ATV-2, ATV-3 (indicador, progresso/posição no limite, falta, situação) | M2 |
| **M4** | **Vem do escopo Rentabilidade** (priorizado em 13/10) — aqui só o uso. **Série histórica da carteira** (`PortfolioSeries`, core): patrimônio, fluxos e cota diários por escopo — fechamento **sem ajuste** do dia × cotas na data já com desdobramento/grupamento/bonificação (`sharesOn`), nunca a série ajustada da tela de cotação; preço carregado e cobertura (D11); `FixedIncomeCurve` em série; PU do Tesouro; cache e invalidação | M2 |
| **M5** | **Onda 2** — motor de projeção (seção 6) com premissas automática/informada; projeção em PAT-1, APO-1, REN-1, REN-2, ATV-1, ATV-2; tipos REN-3 e LP-1 | M3, M4 (*yield* e premissa D12 b) |
| **M6** | **Onda 3** — RNT-1, RNT-2, RIS-2 sobre a série, a cota e a comparação com índice **do escopo Rentabilidade** (as metas só aplicam alvo e situação) | M4 |
| **M7** | Fotografia diária, eventos, gatilhos (seção 8) e avisos (`NotificationKind` + categoria `METAS`) | M3 |
| **M8** | Web: visão geral e lista | M3 |
| **M9** | Web: nova meta (categoria → tipo → formulário do catálogo com preview), editar, ciclo | M1, M8 |
| **M10** | Web: detalhe (situação, falta, decomposição, projeção e premissas, evolução, alterações, origem e memória) | M5, M6, M7 |
| **M11** | Testes da seção 15 + planilha de conferência manual (`docs/metas/Conferencia_Metas.xlsx`) com exemplos calculados à mão para cada tipo | junto de cada etapa; fechamento aqui |
| **M12** | Validação com a carteira real do DEV (seção 16) e documentação: `docs/METAS_METODOLOGIA.md` (regras, fórmulas, premissas, fontes, limitações, atualização — passo 14 do escopo), `Claude.md`, `docs/PENDENCIAS.md` (26.3 resolvido; L1, TIR, orçamento), `README.md`, `docs/USE_CASES.md`, `docs/PRIMEVEST_DB.md`, `docs/SCHEDULE_TASKS.md` | todas |

#### Quem faz

| Quem | O quê |
|---|---|
| Claude | M1–M12 |
| Guilherme | responder D1–D22; conferir a planilha de exemplos; conferência no navegador (caminho 3) com a carteira real |

---

### 15. Testes (cenários mínimos do escopo)

| Cenário | Onde é provado |
|---|---|
| 1 patrimônio abaixo do alvo · 2 alvo atingido/ultrapassado (progresso > 100%, falta 0) · 3 sem prazo · 4 prazo vencido não atingido | Unidade de PAT-1 e da situação (7.1); IT de ponta a ponta com transações reais |
| 5 renda com histórico completo e parcial | Unidade de REN-1 (D10): 12 meses × 5 meses de posição; nenhum pagamento isolado anualizado |
| 6 rentabilidade com aportes e retiradas | Unidade da cota: exemplo calculado à mão em que a rentabilidade simples erra e a cota acerta; resgate não vira perda |
| 7 alocação dentro e fora da faixa · 8 concentração no limite exato e acima | Unidade de ALO-1 e RIS-1 (limite inclusivo; aporte/redução para voltar conferidos com a fórmula da reserva) |
| 9 quantidade de ativo | Unidade de ATV-1 com desdobramento e bonificação no meio |
| 10 benchmark indisponível | RNT-2 com IPCA do mês não divulgado (encurta) e índice sem nenhum dado (`INDISPONIVEL`) |
| 11 sem preço para uma posição · 12 dados insuficientes | `GoalFacts` com tipo MANUAL (custo, `PARCIAL`), cotação defasada, ALO-2 sem estratégia, ATV-3 sem proventos |
| 13 pausada, concluída, arquivada | IT do ciclo: conclusão automática ao atingir (com aviso, uma vez) e manual; transições válidas/inválidas (422), fotografia parada na pausa, resultado congelado na conclusão |
| 14 cotação, transação ou provento muda o progresso | IT: grava transação / publica "catálogo mudou" → fotografia do dia e situação mudam; aviso gerado uma vez |
| 15 objetivo alterado sem perder histórico | IT: editar alvo → revisão 2, eventos com as duas definições, fotografias antigas com a revisão 1 |
| 16 divisão por zero, negativos, bordas | Unidade: alvo 0 recusado no cadastro, escopo vazio, `V` negativo de aporte, `i = 0` nas fórmulas, `PMT ≤ 0`, carteira vazia, cota com denominador zero |
| 17 nada calculável é pedido | Teste de contrato sobre `/goals/types`: nenhum tipo declara campo de valor atual, progresso, falta, data estimada, renda atual, alocação ou concentração; `POST` com esses campos → 400 |
| Autorização | IT: meta de outro usuário → 404 em todas as rotas |

---

### 16. Validação (última etapa do Claude)

`mvn verify` (core, integration, adm-api, app-api) e, na web, `tsc --noEmit` e `eslint .` sem erro nem aviso. No
ambiente: V52 (e V53) aplicadas; rodada diária sem duplicar fotografia numa segunda subida. **Carteira real do DEV**
(11 ações, 11 FIIs, 10 títulos do Tesouro, 4 de renda fixa, IVVB11, BTC; 236 proventos): conferir, contra as telas que
já existem, o patrimônio (Dashboard), a alocação (Alocação), a renda de 12 meses (Dividendos) e a aderência
(Rebalanceamento); conferir à mão a rentabilidade pela cota de um período curto; ver o `PARCIAL` de ETF e cripto
antes de setembro/2026. Onde a conferência manual não é possível (projeções longas, série inteira), a validação é pela
planilha de exemplos e pelos testes de fórmula. Conferência no navegador com você logado.

---

### 17. Andamento

| Data | Etapa | Situação |
|---|---|---|
| 13/10/2026 | — | plano criado (levantamento do código e do banco do DEV; D1–D22 em aberto) |
| 13/10/2026 | — | respostas: D5 e D13 (revisada: conclusão automática), D7 (patrimônio com/sem reserva explícito); D4 e D12 explicadas; escopo Rentabilidade priorizado antes deste — M4 e o cálculo da M6 passam a ele |
