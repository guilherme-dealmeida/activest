# PrimeVest — Plano de Testes

> Fase: **implementado em 22/09/2026** · branch `TRACK_01-ajustes_crud_inicial`
> Resultado: **234 testes verdes** em `mvn clean verify` (~52s) — 176 rápidos + 58 funcionais.
> 1 teste ignorado: o de fração de cripto, porque o catálogo semeado ainda não tem criptomoedas (F13).
> Escopo: as 4 funcionalidades minimamente funcionais — **Carteira, Alocação, Estratégia, Transações**.
> Fora do escopo agora: Autenticação, Preço Teto, Dividendos, Metas, Usuário, Rotinas com schedule.

Objetivo: os testes passam a andar junto com as regras. Cada regra nomeada no código
(A, B, C, E, F, G, H, I, J, Exemplos do escopo) ganha pelo menos um teste com o mesmo nome,
para que mudar a regra sem mudar o teste seja impossível.

---

## 0. Diagnóstico — o que existe hoje

| Arquivo | Estado | Decisão proposta |
|---|---|---|
| `AuthServiceTest.java` | só a linha `package` | apagar (Auth está fora do escopo) |
| `StrategyServiceTest.java` | vazio | apagar e reescrever |
| `PortfolioServiceTest.java` (271 l.) | escrito **antes da V28**: usa `setShares`/`setAveragePrice` em `PortfolioAsset`, que não existem mais | **não compila**; aproveitar só a intenção dos cenários |
| `TransactionServiceTest.java` (258 l.) | idem — espera posição gravada, sem `PositionCalculator`, `PositionGuard`, `FixedIncomeSettlement`, `TreasuryAllocator` | **não compila**; aproveitar a intenção (PM ponderado, venda acima do saldo) |

Infra de teste hoje:

- `pom.xml` já tem `spring-boot-starter-test`, `spring-security-test`, Testcontainers (`postgresql`, `junit-jupiter`) e **H2** (sobra — ver 1.1).
- Build roda com `-Dmaven.test.skip=true`; ao remover isso, os dois testes obsoletos quebram a compilação. Por isso a limpeza é o **passo 0**.
- Máquina: Docker **29.7.2**, Maven 3.9.11, JDK 21. Testcontainers já está no `.m2`.

---

## 1. Fundação (pré-requisito dos 4 passos)

### 1.1 Dependências
- **Remover H2.** O `Claude.md` já registra: metade do que importa é comportamento de PostgreSQL
  (`NULLS NOT DISTINCT`, `ck_strategy_reservations_target`, `ORDER BY` com `CASE`, Flyway). H2 dá falso verde.
- **Risco Docker 29 × Testcontainers.** O Boot 3.3.5 traz Testcontainers 1.19.x, que fala uma versão de API
  Docker que o Docker 29 recusa ("client version is too old"). Primeira tarefa da fundação é um *smoke test*
  que só sobe o container; se falhar, fixar `testcontainers.version` numa versão recente no `pom.xml`.
- **Failsafe** para os funcionais: `*Test` → Surefire (`mvn test`, rápido), `*IT` → Failsafe (`mvn verify`).

### 1.2 Perfil `test` (`src/test/resources/application-test.yml`)
- Datasource vem do container (`@ServiceConnection`), **Flyway ligado** (V1–V12) e `ddl-auto: validate` —
  todo teste de integração vira também prova de que entidade e migration batem.
- `spring.cache.type: none` e autoconfig de Redis excluída — nenhum teste depende de Redis.
- **Schedulers desligados.** Hoje `@EnableScheduling` está em `PrimevestApplication`. Proposta de mudança
  mínima em produção: mover para uma `SchedulingConfig` com `@ConditionalOnProperty(primevest.scheduling.enabled, matchIfMissing=true)`
  e desligar no perfil `test`. *(decisão D2)*
- `BrApiClient` e `CoinGeckoClient` sempre `@MockitoBean`/`@MockBean` — egress bloqueado e teste não pode depender de rede.

### 1.3 Esqueleto em `src/test/java/com/primevest`
```
support/
  PostgresContainer.java        container singleton (reuso entre classes, 1 subida por execução)
  JpaTestBase.java              @DataJpaTest + @AutoConfigureTestDatabase(replace = NONE) + container
  IntegrationTestBase.java      @SpringBootTest(RANDOM_PORT) + container + mocks de integração + limpeza
  Fixtures.java                 builders: usuário, carteira, posição, nota, transação, estratégia
  Tokens.java                   JWT via JwtTokenProvider.generateToken(userId, email) — não depende do /auth/login
  CatalogIds.java               resolve AssetType/Asset por NAME/ticker (nunca id fixo — regra do projeto)
domain/<feature>/                *RepositoryTest, *ServiceTest, *ControllerTest
functional/                      *IT por funcionalidade
```
- Limpeza entre testes funcionais: `TRUNCATE` das tabelas de usuário (users, portfolios, portfolio_assets,
  transactions, fixed_income_notes, strategies, strategy_reservations, dividends) preservando o catálogo semeado.
- Autenticação nos funcionais **por token gerado**, não por `/auth/register`: Auth está em aberto e não deve
  derrubar os testes das outras 4 funcionalidades.

---

## 2. Passo 1 — Testes de repositório

`@DataJpaTest` contra PostgreSQL real. Testar **consulta customizada, escopo por dono e constraint de banco**.
Derived query trivial (`findByUserId`) só entra se carregar regra.

### PortfolioAssetRepository
- `findByPortfolioIdAndAssetIdAndNoteIsNull` — acha a posição de renda variável; ignora a de mesmo ativo com nota.
- `findByPortfolioIdAndNoteId` — dois CDBs do mesmo ativo = duas linhas, cada uma achada pela sua nota.
- **Constraint `(portfolio_id, asset_id, note_id) NULLS NOT DISTINCT`** — duas posições PETR4 sem nota → violação.
- `findAllByPortfolioIdAndAssetId` — todos os títulos do ativo, com `note` carregada (fetch).
- `findAllByPortfolioIdWithAssetEagerly` — traz ações (note nula) **e** renda fixa (LEFT JOIN).
- `findOwnedById` — dono acha; **outro usuário recebe `Optional.empty()`**; tipo do ativo vem carregado.
- `findAllByAssetIdIn` — atravessa carteiras.

### TransactionRepository
- `findOwnedById` — dono acha; outro usuário não (transação → posição → carteira → usuário).
- `findAllTransactionsByUserId` — isola usuários; **inclui ação** (LEFT JOIN em nota não some com renda variável).
- `findByPortfolioIdAndAssetId` — todas as transações de todos os títulos de um Tesouro.
- `findBySaleGroupId` — partes da mesma venda.

### StrategyRepository / StrategyReservationRepository
- `unique(user_id, asset_type_id)` — mesmo tipo duas vezes → violação.
- `strategy_reservations`: `unique(user_id)`; `ck_strategy_reservations_target` — PERCENTAGE sem `target_percentage`,
  ou com as duas colunas → violação; `ReservationTypeConverter` grava 1/2 e lê de volta.

### FixedIncomeNoteRepository
- `findByIdAndUserId` — nota de outro usuário não aparece.

### AssetRepository (usado pelas Transações)
- `searchByTickerOrName` / `searchByTypeAndTickerOrName` — ordem: ticker exato → prefixo de ticker → prefixo de nome → resto;
  **vencidos por último** (caso "SELIC" que motivou a query); `PageRequest.of(0, 50)` sem `Sort` não quebra.
- `findByTickerWithTerms` — Tesouro traz `terms`; ação traz `terms` nulo.

### Schema
- Um teste que só sobe o contexto JPA já prova Flyway V1–V12 + `ddl-auto: validate`.
- Contagem do seed (12 tipos, 116 categorias, 1.264 ativos, 130 terms) — barato e pega migration quebrada.

---

## 3. Passo 2 — Testes unitários de serviço

JUnit 5 + Mockito, **sem Spring**. Ordem: primeiro os componentes de regra pura (sem repositório), depois os serviços que os orquestram.

### 3.1 Componentes de regra (maior valor, mais barato)

**PositionCalculator**
- BUY soma cotas e custo; SELL tira cotas e custo **pelo PM do momento**; PM não muda na venda.
- Exemplo do `Claude.md`: compra 100 × R$10, compra 100 × R$20, venda 100 × R$30 → **PM R$15, custo R$1.500**.
- Venda sem cotas é ignorada; venda maior que a posição zera.
- Escalas: PM 4 casas, custo 2 casas; 8 casas em cotas (cripto).
- Renda fixa (`valuedByTotal`): custo = aplicações − resgates, nunca negativo.
- `chronological`: ordena por data e, no empate, por `createdAt`.
- `sharesOn(data)`: cotas na data-com.

**PositionGuard**
- Regra A: venda sem posição com compras → `BusinessException`.
- Regra B: saldo negativo **em qualquer data** (venda retroativa que deixa venda posterior a descoberto), mensagem com data e cotas faltantes.
- Regra I: posição aberta com investido ≤ 0 → erro; liquidada ou sem transações → passa.

**FixedIncomeSettlement** (regras E, F, G, J)
- Liquidação total: valorização = resgatado − aplicado; `settled = true`; resgate < aplicado → erro (G).
- Resgate parcial que alcança o saldo, **em venda**: valorização = resgatado − aplicado + **R$ 0,01**.
- Mesma situação disparada por **compra editada/excluída** → não inventa valorização → regra E recusa.
- Regra F: resgatado == aplicado + valorização sem liquidação total → erro.
- Regra J: a venda que liquidava some/deixa de ser total → posição reabre (`settled = false`).
- Idempotência: editar o valor de uma venda chega ao mesmo resultado que lançá-la assim da primeira vez.

**TreasuryAllocator** (repositórios mockados)
- `salesOf`: reconstitui a venda do grupo somando partes (cotas, taxa) e pegando o `createdAt` mais antigo.
- FIFO: venda consome o título mais antigo e passa ao seguinte; uma venda → N partes com o mesmo `sale_group_id`.
- Taxa rateada proporcionalmente entre as partes (`prorate`), soma das partes = taxa digitada.
- **Exemplo 5** (seção 5.5): título comprado depois da venda não conta → venda de 5 em 14/09 falha.
- **Exemplo 6** (seção 5.5): compra retroativa de 31/08 vira o título mais antigo; repartição final C 4 + A 1, A reaberto, C liquidado.
- Venda acima do saldo do ativo → `BusinessException`.
- Título sem cotas é marcado liquidado, e só ele.

**StrategyReservationService.rebalance / amountToContribute**
- Percentual: `(p × total − reserva) ÷ (1 − p)`. Exemplo conferido: total 10.000, reserva 2.000,
  ideal 30% → **1.428,57**; ideal 10% → **−1.111,11**.
- `p = 100%` → `total − reserva` (sem divisão por zero).
- Por valor: `alvo − reserva` (ideal 3.000 → **1.000**); % ideal = alvo ÷ total, limitado a 100%.
- `get` cria a linha padrão se faltar (grava); `rebalance` usa padrão **em memória, sem gravar**.
- `update`: PERCENTAGE sem percentual / VALUE sem valor → erro; a coluna do outro tipo fica nula.

### 3.2 Serviços orquestradores

**StrategyService**
- `updateStrategy`: full replace (apaga os antigos, grava os novos); tipo inexistente → erro.
- `updateStrategy` é o **único** caminho de escrita da estratégia (F8): soma 100% (F7), sem tipo repetido (F3), tipo inexistente → 404 (F6).
- `getRebalanceSuggestion`:
  - **Regra A**: base = tipo com maior razão valor/ideal; esse tipo fica com sugestão zero; carteira equilibrada zera todas.
  - Tipo com ideal 0 não serve de base; com dinheiro dentro dá sugestão negativa.
  - **Regra B**: ativo de tipo sem ideal fora do total.
  - **Regra C**: posição de reserva nunca entra (nos dois ramos, com e sem estratégia).
  - Sem estratégia: só distribuição atual por tipo, ideal 0.
  - Arredondamento: negativo entre −0,01 e 0 vira 0.

**PortfolioService**
- `getAllocation(excludeReservation)`: ignora liquidadas e investido zero; `true` tira reserva; percentuais somam 100; vazio → lista vazia.
- `getAllocationDetailed`: por tipo › categoria, **sempre sem reserva**.
- `getReservationAllocation`: reserva × geração de renda na mesma base do gráfico por tipo; total 0 → 0%.
- `setReservation`: posição de outro dono → `ResourceNotFoundException`; tipo não `fixed_income` → `BusinessException`; grava o **estado** pedido (idempotente).
- `getPortfolioSummary` / `getPortfolioAssets` / `getPerformance`: valor atual = custo + `appreciation`; `issuer`/`maturityDate` no DTO de renda fixa; `getDescription()` do tipo na exibição.
- `getOrCreatePortfolio`: cria se faltar.

**TransactionService**
- Ticker fora do catálogo → erro (não cria ativo).
- `resolveValores`: renda fixa com cotas/preço → erro; sem total → erro; renda variável sem cotas/preço → erro; `totalAmount` enviado é ignorado e recalculado.
- `validateSettlementFlag`: liquidação total fora de venda de renda fixa → erro.
- `resolveNote`: `noteId` em ativo não renda fixa → erro; `noteId` de outro usuário → "Título não encontrado"; compra sem `noteId` cria nota herdando de `asset_terms`; **venda sem `noteId` → recusa**.
- Regra A na criação (venda não abre posição).
- `updateTransaction`: não troca tipo/ativo; `hasNoteData` falso **não apaga** emissor/vencimento/taxa.
- `findOwnedTransaction`: transação alheia → `ResourceNotFoundException` (404, não 403).
- Roteamento Tesouro (compra/venda/edição/exclusão) e **regra H** (dois títulos do mesmo ativo na mesma data → erro; ignora a própria transação na edição).
- `afterPositionChange`: ordem saldo → liquidação → regra I → proventos → valorização (verificar com `InOrder`).

> Observação: `TransactionService` tem 805 linhas e 12 dependências. O unitário cobre **decisão e roteamento**;
> o efeito combinado (FIFO gravado, liquidação, valorização) fica para o funcional, onde é mais barato e mais fiel.

---

## 4. Passo 3 — Testes de controller

`@WebMvcTest(XController.class)` + `@Import(SecurityConfig, GlobalExceptionHandler)`, serviço mockado.
Usuário simulado com `@WithMockUser(username = "<uuid>")` — `SecurityUtils` lê o `name` como UUID.

Para **cada** endpoint das 4 funcionalidades:
- **401** sem autenticação.
- Status de sucesso e forma do JSON (nomes de campo que o front consome; `non_null` do Jackson; datas ISO).
- `userId` repassado ao serviço é o do token, **nunca** do corpo/URL.
- Mapeamento de erro: `BusinessException` → **422**, `ResourceNotFoundException` → **404**, validação → **400**.

Específicos:
- `POST/PUT /transactions`: `@NotBlank ticker`, `@NotNull type`, `@NotNull transactionDate`, `@Positive` em cotas/preço/total → 400.
- `GET /portfolio/allocation`: sem parâmetro → `excludeReservation=false`; `?excludeReservation=true` repassado.
- `PATCH /portfolio/assets/{id}/reservation`: corpo sem `isReservation` → 400.
- `PUT /strategy/reservation`: tipo nulo, percentual/valor negativo → 400.
- `PUT /strategy/`: `targetPercentage` > 100 → **deveria** ser 400 *(ver achado F2)*.
- `DELETE /strategy/{assetTypeId}` **não existe mais** → 404 (F8, F9).

---

## 5. Passo 4 — Testes funcionais (`*IT`)

`@SpringBootTest(RANDOM_PORT)` + PostgreSQL real + Flyway + catálogo semeado. Chamadas HTTP de verdade
(`TestRestTemplate` ou `WebTestClient`, que já está no classpath via webflux), token gerado por usuário.
Cada cenário: **Dado** (transações via API) → **Quando** → **Então** (asserção sobre o que as telas leem).

### 5.1 TransacoesIT
| # | Cenário | Regra |
|---|---|---|
| T1 | Compra de ação cria posição; lista e busca por id | — |
| T2 | 100 × R$10, 100 × R$20, venda 100 × R$30 → PM 15, custo 1.500 em `/portfolio/assets` | PositionCalculator |
| T3 | Venda de ativo fora da carteira → 422, nada gravado | A |
| T4 | Venda sem saldo na data (e rollback) | B — **Exemplo 1** (ver 5.5) |
| T5 | Editar / excluir compra que sustentava venda → 422, compra intacta | B — **Exemplos 2 e 3** |
| T6 | Vender tudo liquida; nova compra reabre | 3.2 / J |
| T7 | Ticker inexistente → 422; ativo não criado no catálogo | — |
| T8 | CDB: compra cria nota; dois CDBs = duas linhas com emissor/vencimento distintos | NULLS NOT DISTINCT |
| T9 | CDB: resgate parcial, resgate que alcança saldo (+R$0,01), liquidação total | E, F, G |
| T10 | CDB: venda sem `noteId` → 422 | resolveNote |
| T11 | CDB: editar só quantidade/valor não apaga emissor/taxa | hasNoteData |
| T12 | Tesouro: duas compras no mesmo dia → 422 | H |
| T13 | Tesouro: venda consome FIFO em dois títulos; editar/excluir a venda age no grupo | FIFO — **Exemplo 4** |
| T14 | Tesouro: venda sem cotas suficientes até a data dela → 422 | Exemplo 5 (ver 5.5) |
| T15 | Tesouro: compra retroativa desfaz e refaz a repartição da venda | Exemplo 6 (ver 5.5) |
| T16 | Usuário B não lê, edita nem exclui transação de A → **404** | escopo |

### 5.2 CarteiraIT
- Resumo e posição batem com as transações; liquidadas fora da posição aberta.
- Valorização de ação com cotação (`current_price`) = cotas × cotação − custo; renda fixa = custo.
- Switch de reserva: renda fixa aceita (idempotente), ação → 422, posição de outro usuário → 404.

### 5.3 AlocacaoIT
- Dashboard (`/allocation`) inclui reserva; Alocação (`?excludeReservation=true`) não.
- **Total por tipo com reserva excluída == fatia "geração de renda" de `/allocation/reservation`** (a amarração que a tela assume).
- `/allocation/detailed` nunca inclui reserva; percentuais somam 100.

### 5.4 EstrategiaIT
- `PUT /strategy` full replace (único caminho): remover um tipo = PUT sem ele, somando 100%.
- Rebalanceamento com números fechados (regras A, B, C), reserva fora dos itens por tipo.
- Reserva: `GET` cria padrão para usuário sem linha; `PUT` percentual e valor; exemplo 10.000 / 2.000
  → 1.428,57 · 1.000 · −1.111,11 no card do rebalanceamento.
- Marcar um CDB como reserva muda **ao mesmo tempo** `/strategy/rebalance`, `/strategy/reservation` e `/portfolio/allocation/reservation`.

### 5.5 Exemplos oficiais do escopo

Fonte: exemplos do escopo de vendas (17/09/2026), fornecidos pelo Guilherme. Cada exemplo vira **um teste com o
nome do exemplo** (`exemplo1_...`), no `TransacoesIT`; os de Tesouro também no `TreasuryAllocatorTest`.

**Exemplos 1–3 — saldo por cota ao longo do tempo (regra B)**

Ativo: uma ação do catálogo (ex.: `PETR4`), resolvida por ticker. Preço fixo (R$ 30,00) — não muda nenhuma
asserção de quantidade. Os mesmos três exemplos rodam também sobre `Tesouro IPCA+ 2050`, que é por cota no
nível do ativo (teste parametrizado por tipo).

| Exemplo | Passos | Resultado esperado |
|---|---|---|
| **1** | Compra 10 (01/09) · Venda 8 (04/09) · **Venda 4 (11/09)** | **422** na Venda 2: "faltam 2 cotas" em 11/09/2026; Venda 2 não gravada; saldo continua 2 |
| **2** | Compra 10 (01/09) · Venda 8 (04/09) · **editar Compra 1 para 5** | **422**: "faltam 3 cotas" em 04/09/2026; Compra 1 continua com 10 (rollback) |
| **3** | Compra 10 (01/09) · Venda 8 (04/09) · **excluir Compra 1** | **422**: "faltam 8 cotas" em 04/09/2026; Compra 1 continua existindo |

Ajustes do Exemplo 1 — cada um vira um caso que, depois do ajuste, **aceita** a Venda 2:
- 1a. nova Compra 2 de **exatamente 2** cotas com data ≤ 11/09 → Venda 2 aceita (limite exato);
- 1b. editar Compra 1 para **12** cotas → Venda 2 aceita;
- 1c. editar Venda 1 para **6** cotas ("qualquer outro ajuste ao saldo do período") → Venda 2 aceita;
- 1d. contra-prova: Compra 2 de 2 cotas **em 12/09** (depois da Venda 2) → Venda 2 continua recusada.

Asserções de mensagem: data no formato dd/MM/aaaa e quantidade sem zeros à direita, como o `PositionGuard` monta.

**Exemplo 4 — venda de Tesouro repartida por FIFO (sucesso)**

Ativo: **`Tesouro IPCA+ 2050`** (V6; vencimento 15/08/2050 na V8). Cada compra do Tesouro é um título próprio,
com a taxa do dia gravada na nota.

| Passo | Data | Operação | Estado depois |
|---|---|---|---|
| 1 | 01/09/2026 | Compra 3 × R$ 830,07, IPCA + 7,09% | A: 3 |
| 2 | 15/09/2026 | Compra 4 × R$ 830,10, IPCA + 7,50% | A: 3 · B: 4 |
| 3 | 17/09/2026 | Venda 5 | **201** — A: 0, `is_settled = true` · B: 2, aberto |

Asserções: venda gravada em **2 partes** (3 de A, 2 de B) com o mesmo `sale_group_id`; a listagem de transações
mostra a venda como a operação do usuário (5 cotas); taxa rateada entre as partes somando o total digitado.

**Exemplo 5 — venda sem saldo na data dela (falha)**

| Passo | Data | Operação | Resultado esperado |
|---|---|---|---|
| 1 | 01/09/2026 | Compra 3 cotas × R$ 830,07, IPCA + 7,09% | 201 — título A |
| 2 | 15/09/2026 | Compra 4 cotas × R$ 830,10, IPCA + 7,50% | 201 — título B |
| 3 | 14/09/2026 | Venda 5 cotas | **422** — só 3 cotas até 14/09 (B foi comprado depois e não conta) |

Asserções: nenhuma linha de venda gravada; A e B continuam abertos com 3 e 4 cotas; a mensagem cita
a data e as cotas que faltam.

**Exemplo 6 — compra retroativa refaz o FIFO (sucesso)**

| Passo | Data | Operação | Estado depois |
|---|---|---|---|
| 1 | 01/09/2026 | Compra 3 × R$ 830,07, IPCA + 7,09% | A: 3 |
| 2 | 15/09/2026 | Compra 4 × R$ 830,10, IPCA + 7,50% | A: 3 · B: 4 |
| 3 | 17/09/2026 | Venda 5 | A: 0 (**liquidado**) · B: 2 — venda em 2 partes (3 de A, 2 de B), mesmo `sale_group_id` |
| 4 | 31/08/2026 | Compra 4 × R$ 830,05, IPCA + 6,90% | ver abaixo |

Depois do passo 4, C (31/08) passa a ser o título mais antigo:
- a) as partes antigas (3 de A, 2 de B) são **desfeitas**, e A volta a `is_settled = false`;
- b) a venda é refeita: **4 cotas de C** (C fica `is_settled = true`) e **1 cota de A**.

Estado final esperado: C = 0 (liquidado) · A = 2 (aberto) · B = 4 (aberto) · total do ativo 6 cotas;
venda com **2 partes (C 4, A 1)**, mesmo `sale_group_id` de antes, quantidade total 5 e taxa total preservadas;
B sem nenhuma parte de venda.

Observações para o teste:
- O exemplo não traz o **preço da venda**: o teste usa um valor fixo (ex.: R$ 850,00), que não muda nenhuma asserção de cotas.
- O Exemplo 6 começa pelo Exemplo 4: o estado do passo 3 é asserção intermediária do mesmo teste.
- Os exemplos entram duas vezes: no `TreasuryAllocatorTest` (repartição pura) e no `TransacoesIT` (API + banco).

### 5.6 Regras do `Claude.md` que viram teste funcional

Varredura do `Claude.md` (e do `PENDENCIAS.txt` onde ele complementa) atrás de regra das 4 funcionalidades que
ainda não estava nas seções 5.1–5.5. Cada linha foi conferida no código; a coluna "Origem" aponta a seção.

**Transações**

| # | Cenário | Origem |
|---|---|---|
| R1 | Compra e venda na **mesma data**: a ordem é a de criação — com 5 cotas de antes, lançar compra 10 e depois venda 12 no mesmo dia passa; lançar a venda 12 primeiro e a compra depois é recusado (regra B) | Fluxo → PositionCalculator ("data, depois criação") |
| R2 | Renda variável: `totalAmount` enviado é **ignorado**; gravado = cotas × preço | Fluxo / `resolveValores` |
| R3 | Renda fixa com cotas ou preço unitário → 422; sem valor total → 422 | idem |
| R4 | Cripto: quantidade **0,00012345** grava e volta com as 8 casas (nada truncado a 4) | Precisão decimal |
| R5 | Taxa do título com 3 casas (**IPCA + 6,125%**) grava e volta igual (`numeric(10,4)`) | Transações (máscaras) / Precisão |
| R6 | Tesouro: compra sem dados de título **herda** vencimento, forma e índice de `asset_terms`; o usuário só manda a taxa | Renda fixa: como a nota entra |
| R7 | A nota **copia** `asset_terms`: alterar o catálogo depois não muda a nota nem a posição já lançada | Decisão estrutural |
| R8 | CDB: `noteId` de outro usuário → 422 "Título não encontrado"; `noteId` de outro ativo → 422 | Renda fixa: como a nota entra / Autorização |
| R9 | Ação com `noteId` → 422 | idem |
| R10 | Prefixado com índice: o índice é **limpo**; pós-fixado/híbrido sem índice → 422; índice sem forma → 422 | `applyNoteFields` |
| R11 | `GET /transactions/{id}` de renda fixa devolve `assetTypeId` **e** os dados do título — é o que a tela de edição usa para reabrir o quadro | Transações (edição) |
| R12 | Tesouro: venda repartida aparece na listagem com `groupShares`/`groupBrokerageFee` = o que o usuário digitou | TreasuryAllocator |
| R13 | Renda fixa: excluir uma venda que corrigiu a valorização **reabre** a posição e **mantém** a valorização (decisão registrada em PENDENCIAS 10.2) | PENDENCIAS 10.2 / regra J |
| R14 | `transactions.updated_at` muda na edição; `created_at` não | Auditoria de datas |

**Carteira**

| # | Cenário | Origem |
|---|---|---|
| R15 | A posição **não é gravada**: `portfolio_assets` só tem identidade; cotas/PM/custo em `/portfolio/assets` saem das transações (editar uma transação muda a posição na hora) | Fluxo (V28) |
| R16 | Valorização com cotação: após compra/edição/exclusão, `appreciation` = cotas × `current_price` − custo (2 casas) | Fluxo → AppreciationUpdater |
| R17 | Renda fixa sem cotação: valor atual = custo (valorização 0) enquanto não houver resgate | Pontos de atenção |
| R18 | Tipo exibido pela **descrição** ("Ações", não "ACAO") em todo DTO da carteira | Convenção getDescription |
| R19 | A tabela repete ticker: dois CDBs do mesmo ativo = duas linhas, ids diferentes | Decisão estrutural / Carteira |
| R20 | Reserva só onde `asset_types.fixed_income = true` — decidido pela **coluna**, não pelo nome (teste marca um tipo como fixed_income no banco de teste e a reserva passa a ser aceita) | Asset Types / Carteira |

**Alocação**

| # | Cenário | Origem |
|---|---|---|
| R21 | Valor da alocação = custo + valorização, **só posição em aberto** (liquidada e investido zero ficam fora) | Alocação |
| R22 | `/allocation/detailed`: categoria é o par (nome, segmento) com `getDisplayName`; ações de segmentos diferentes do mesmo setor não se misturam | Tabelas / asset_categories |

**Estratégia**

| # | Cenário | Origem |
|---|---|---|
| R23 | Base de valor da estratégia = **custo − resgates** (não valor de mercado): ação valorizada entra no rebalanceamento pelo custo e na alocação pelo valor atual | Reserva — como é calculada |
| R24 | `GET /strategy/rebalance` de usuário sem linha de reserva **não grava** a linha; `GET /strategy/reservation` grava | Nascimento |
| R25 | Reserva por valor: % ideal = alvo ÷ total, **limitado a 100%** (alvo maior que a carteira) | Reserva |
| R26 | Reserva: tipo trafega como `PERCENTAGE`/`VALUE` na API e grava 1/2 no banco | `type_reservation` SMALLINT |
| R27 | Reserva acima do alvo → aportar **negativo** (a tela mostra AGUARDAR), sem sugerir venda | Reserva |
| R28 | Não existe DELETE da reserva (nem da estratégia, F8) | Endpoints |

**Fica de fora, de propósito**
- Corretagem no custo/PM (PENDENCIAS 7.4): hoje `brokerage_fee` **não** entra no custo, e a pendência diz que
  deveria entrar. Teste que fixasse isso tornaria o erro permanente — entra quando a 7.4 for decidida.
- Proventos recalculados ao mudar transação (`DividendRecalculator`): depende de Dividendos, que está em aberto.
- Liquidação por vencimento (PENDENCIAS 10.4) e curva de renda fixa (2.1): não existem ainda.

**Divergências entre `Claude.md` e o código** — os testes seguem o código e os exemplos do escopo; o `Claude.md`
é corrigido na etapa final:
- "SELL ... venda maior que a posição zera" → hoje a regra B **recusa** (PENDENCIAS 7.5 já está resolvida).
- "Vender renda fixa é impossível hoje" → pela API é possível com `noteId` (regras E/F/G); o que falta é o seletor de título no front.
- Tesouro Direto (FIFO, grupos de venda, regra H) não está documentado no `Claude.md`.

---

## 6. Achados do levantamento (a decidir antes de fixar em teste)

Teste que fixa um comportamento errado o torna permanente. Estes precisam de decisão:

- **F1** — `StrategyService.deleteStrategyItem` lança `BusinessException("Strategy item not found")` → **422**, em inglês.
  O padrão do projeto é 404 para "não encontrado".
- **F2** — `StrategyUpdateDTO.items` não tem `@Valid`: o `@DecimalMin/@DecimalMax` dos itens **não é aplicado**
  (ideal de 150% passa). `assetTypeId` também não tem `@NotNull`.
- **F3** — `PUT /strategy` com o mesmo `assetTypeId` duas vezes esbarra na `unique(user_id, asset_type_id)` →
  `DataIntegrityViolationException` → **500**. Não há validação de soma ≤ 100%.
- **F4** — `updateStrategy` com usuário inexistente lança "User not found" como 422 (inglês).
- **F5** — Os exemplos do escopo não estavam no repositório. Os 6 foram registrados na seção 5.5.

---

### Decisões tomadas (22/09/2026)
- **F1 — superado pela F8:** o endpoint de DELETE sai, então não há mais o 422 a migrar.
- **F2 — colocar `@Valid`.** `@Valid` na lista `items` de `StrategyUpdateDTO` (para os limites de cada item valerem),
  `@NotNull` em `items`, `assetTypeId` e `targetPercentage`. Teste: 150% → 400; item sem tipo → 400.
- **F3 — tratar em vez de 500.** Duas camadas:
  1. `updateStrategy` recusa tipo repetido **antes** de gravar → 422 ("O tipo X aparece mais de uma vez na estratégia").
  2. `GlobalExceptionHandler` ganha um handler de `DataIntegrityViolationException` → 409, para qualquer constraint
     que escape no futuro não virar 500 genérico.
  Teste: tipo repetido → 422 e estratégia anterior intacta (rollback do full replace).
- **F4 — mensagens em português.** Escopo levantado nas 4 funcionalidades + handler global:

  | Onde | Hoje | Passa a ser |
  |---|---|---|
  | `StrategyService:53` | User not found | Usuário não encontrado |
  | `StrategyService:63` | Asset type not found: {id} | Tipo de ativo não encontrado: {id} (e vira 404) |
  | `AssetService:54,117` | Asset not found | Ativo não encontrado |
  | `GlobalExceptionHandler` | Validation Failed | Dados inválidos |
  | `GlobalExceptionHandler` | Access Denied | Acesso negado |
  | `GlobalExceptionHandler` | Authentication Failed | Falha de autenticação |
  | `GlobalExceptionHandler` | An unexpected error occurred | Ocorreu um erro inesperado |

  Os testes de controller conferem o texto dessas mensagens, para a regressão não voltar.
  Fica fora: mensagens dos domínios em aberto (Auth, Dividendos, Metas etc.) — entram quando o domínio for testado.
- **F6 — tipo de ativo inexistente no PUT da estratégia → 404** (confirmado): `ResourceNotFoundException`
  "Tipo de ativo não encontrado: {id}".
- **F7 — regra nova: a soma dos % ideais é 100%** (confirmado: só 100%, tolerando arredondamento).
  Regra, igual à que o front já aplica em `StrategyPage.tsx` (`Math.round(total * 10) === 1000`):
  **a soma arredondada para 1 casa (HALF_UP) tem que dar 100,0** — ou seja, 99,95 ≤ soma < 100,05.
  Fora disso → **422** ("A soma dos percentuais ideais deve ser 100%. Atual: X%"), estratégia anterior intacta.
  Por que 1 casa: cada item tem 2 casas (`numeric(5,2)`), e dividir 100 em partes iguais erra até 0,005 por item;
  com os 12 tipos o erro máximo é 0,06, e o pior caso real (12 × 8,33 = 99,96) ainda arredonda para 100,0.
  Front e back ficam com a mesma regra, escrita uma vez em cada lado.
  Testes: 100,00 aceita · 33,33 × 3 = 99,99 aceita · 16,67 × 6 = 100,02 aceita · 8,33 × 12 = 99,96 aceita ·
  99,94 recusa · 100,05 recusa · 60 + 50 recusa · 50 + 40 recusa · lista vazia recusa.
  **Lista vazia não se salva** → 422 (soma 0%), igual ao front.
- **F11 — sem token a resposta é 401, não 403** (confirmado; corrigido já na etapa 0). O `SecurityConfig` não
  definia entry point, então o Spring respondia **403**; o front só trata **401** (`api/client.ts`) para limpar o
  token e mandar para `/login`, e quem estava com token vencido ficava preso na tela. Conferido na API rodando:
  `GET /api/v1/portfolio/` sem token dava 403. Passa a usar `HttpStatusEntryPoint(UNAUTHORIZED)`.
  Testes: sem token → 401 · token inválido → 401 · token de outro usuário → 404/dados próprios, nunca 403.
- **F13 — o catálogo não tem nenhuma criptomoeda.** O tipo `CRIPTO` existe (id 11, price_source COINGECKO),
  mas os seeds V4–V7 trazem só ações, FIIs, Tesouro e renda fixa. O teste das 8 casas da quantidade fica
  ignorado (`Assumptions`) até existir seed de cripto; a regra em si está coberta no `PositionCalculatorTest`.
- **F12 — `POST /transactions/` responde 200, não 201** (a decidir). O teste de controller registra o 200 de
  hoje para não mudar contrato de API por conta própria; o front não depende do código. Trocar para 201 é uma
  linha no controller.
- **F10 — acentuar as mensagens** (confirmado). Só o texto muda; nenhuma regra, status ou ordem de validação.
  Os testes conferem o texto acentuado. Levantamento nas 4 funcionalidades:

  | Arquivo | Hoje (trecho) | Passa a ser |
  |---|---|---|
  | `PositionGuard` | Ajuste as compras do periodo antes de lancar esta venda | período · lançar |
  | `PositionGuard` | Nao ha posicao de X na carteira para vender | Não há posição |
  | `PositionGuard` | A posicao de X ficaria sem valor aplicado. Uma posicao em aberto… | posição (2×) |
  | `TransactionService` | Cada compra de Tesouro Direto e um titulo novo: nao se lanca sobre um titulo existente | é · título · não · lança · título |
  | `TransactionService` | A venda de Tesouro Direto e do ativo: o titulo sai… e nao se escolhe na tela | é · título · não |
  | `TransactionService` | Ja existe um titulo de X comprado em … altere a quantidade da compra que ja existe | Já · título · já |
  | `TransactionService` | Liquidacao total so existe em venda de Renda Fixa | Liquidação · só |
  | `TransactionService` | Titulo informado para um ativo que nao e de renda fixa | Título · não é |
  | `TransactionService` | Titulo nao encontrado | Título não encontrado |
  | `TransactionService` | O titulo selecionado pertence a outro ativo | título |
  | `TransactionService` | Venda de renda fixa precisa apontar qual titulo esta sendo vendido | título · está |
  | `TransactionService` | Remuneracao X precisa de um indice (CDI, IPCA, …) | Remuneração · índice |
  | `TransactionService` | Informe como o titulo remunera antes de escolher o indice | título · índice |
  | `TransactionService` | Portfolio não encontrado | Carteira não encontrada (termo da tela) |
  | `TreasuryAllocator` | … cotas compradas ate essa data | até |
  | `FixedIncomeSettlement` | Liquidacao total de X: o resgate… e menor… Em renda fixa nao existe valorizacao negativa | Liquidação · é · não · valorização |
  | `FixedIncomeSettlement` | A valorizacao de X ficaria negativa… Em renda fixa isso nao existe | valorização · não |
  | `FixedIncomeSettlement` | …passaria do valor aplicado mais a valorizacao… Ajuste as compras ou a valorizacao antes desta alteracao | valorização (2×) · alteração |
  | `FixedIncomeSettlement` | …igualaria… mais a valorizacao. Zerar a posicao so e possivel… como liquidacao total | valorização · posição · só é · liquidação |

  Somam-se às traduções da F4 (inglês → português). Mensagens já corretas ficam como estão.
  Na `Remuneracao X`, o X hoje é o nome do enum (`HIBRIDO`, `POS_FIXADO`). `RateType` não tem descrição, então a
  mensagem passa a escrever o nome por extenso ("Remuneração pós-fixada precisa de um índice…" / "híbrida"),
  sem mexer no enum.
- **F8 — remover o DELETE da estratégia** (confirmado). Tudo passa pelo `PUT /strategy` com a lista completa,
  e a regra dos 100% vale sempre. Sai junto: `StrategyController.deleteStrategyItem`,
  `StrategyService.deleteStrategyItem`, `StrategyRepository.deleteByUserIdAndAssetTypeId` (fica sem uso),
  `strategyApi.delete` em `primevest-web/src/api/strategy.ts` e a linha do endpoint no `Claude.md`.
  Teste: `DELETE /api/v1/strategy/{id}` → **404** (nenhuma rota casa com esse caminho — ver F9).
- **F9 — rota inexistente vira 500 hoje.** No Spring Boot 3.3, caminho sem rota lança `NoResourceFoundException`,
  e o `@ExceptionHandler(Exception.class)` do `GlobalExceptionHandler` a captura como **500**. O mesmo vale para
  método não suportado (`HttpRequestMethodNotSupportedException`) e JSON malformado (`HttpMessageNotReadableException`).
  Proposta: handlers próprios → 404 "Recurso não encontrado", 405 "Método não permitido", 400 "Corpo da requisição inválido".
  Teste de controller para cada um. **Confirmado.**
- **Schedulers:** autorizada a mudança mínima em produção — `@EnableScheduling` sai de `PrimevestApplication`
  para `SchedulingConfig` condicional (ligada por padrão, desligada no perfil `test`).
- **Exemplos do escopo:** 1 a 6 recebidos e registrados na seção 5.5.

---

## 7. Ordem de execução e critério de pronto

| Etapa | Entrega | Pronto quando |
|---|---|---|
| 0 | Apagar os 4 testes obsoletos; remover H2; Failsafe; perfil `test`; `support/`; smoke do container | `mvn test` verde com 1 teste que sobe PostgreSQL + Flyway |
| 1 | Repositórios (~35 testes) | todas as consultas customizadas e constraints da seção 2 cobertas |
| 2 | Serviços (~90 testes) | toda regra nomeada (A–J, H, Exemplos) com teste de mesmo nome |
| 3 | Controllers (~45 testes) | todo endpoint das 4 funcionalidades com 401 / sucesso / erro |
| 4 | Funcionais (~30 cenários) | tabelas da seção 5 verdes em `mvn verify` |
| — | `build.ps1` e `Claude.md` | build deixa de usar `-Dmaven.test.skip`; seção "Nenhum teste" reescrita |

Convenções: nomes de método em português descrevendo a regra
(`vendaRetroativaQueDeixaVendaPosteriorADescoberto_recusa`), `@DisplayName` com a regra
(`"Regra B — ..."`), valores monetários comparados com `isEqualByComparingTo` (nunca `equals` em `BigDecimal`),
e nenhum id de catálogo fixo — sempre resolvido por `name`/`ticker`.

---

## 8. Conferência manual do front — avisos e (i) (23/09/2026)

O front não tem teste automatizado; estes casos são conferidos na tela a cada
mudança em `TransactionFormModal`, `InfoTooltip`, `ToastContext` ou nas telas
de Carteira, Transações, Estratégia e Alocação.

### 8.1 Avisos (toast, canto superior direito)

| # | Ação | Esperado |
|---|---|---|
| T1 | Carteira › Comprar › Salvar | ✅ "Compra de {ticker} registrada." e modal fecha |
| T2 | Carteira › Vender (CDB) › Salvar | ✅ "Venda de {ticker} registrada." |
| T3 | Idem, com Liquidação total | ✅ "Resgate total de {ticker} registrado. A aplicação foi encerrada." |
| T4 | Venda de Tesouro que consome 2+ títulos | ✅ venda registrada **e** ℹ️ "…foi dividida em N títulos…" |
| T5 | Salvar com erro de regra (ex.: vender mais do que tem) | ❌ mensagem da API por 8s; modal **continua aberto** com o que foi digitado; nenhum aviso vermelho dentro do modal |
| T6 | Transações › Editar › Salvar | ✅ "Transação de {ticker} atualizada." |
| T7 | Transações › Excluir (linha simples / venda repartida) | ✅ "Transação de {ticker} excluída." / "Venda de {ticker} excluída, com os N títulos." |
| T8 | Ideal Estratégico › Salvar com soma ≠ 100% | ❌ "A soma dos percentuais deve ser 100%. Hoje está em X%." — sem `alert` |
| T9 | Ideal Estratégico › Salvar válido | ✅ "Estratégia salva." |
| T10 | Estratégia Reserva › Salvar | ✅ "Estratégia de reserva de emergência salva." |
| T11 | ↻ nas duas abas, com e sem alteração pendente | ℹ️ "Valores restaurados do banco." (+ "As alterações não salvas foram descartadas." quando havia) |

### 8.2 Balões (i)

| # | Onde | Esperado |
|---|---|---|
| I1 | Título do modal de venda de Tesouro (Carteira e Nova Transação) | (i) com a regra do FIFO; o parágrafo do rodapé não existe mais |
| I2 | Editar venda repartida | (i) no título "Editar Transação" com "dividida em N títulos" |
| I3 | Liquidação total | clicar no (i) **não** marca o checkbox |
| I4 | DADOS DO TÍTULO | texto da posição (CDB pela carteira) ou do papel (Tesouro) |
| I5 | Percentual do CDI / Taxa ao ano / Spread | exemplo no (i), nada embaixo do campo |
| I6 | Operação (Renda Fixa em Nova Transação) e Total | (i) no lugar dos textos soltos |
| I7 | Cabeçalhos da Carteira | clicar no (i) **não** ordena a coluna; balão não é cortado pela rolagem horizontal |
| I8 | Alocação e Estratégia | (i) nos títulos dos gráficos, dos cards e nos cabeçalhos |
| I9 | Qualquer (i) | abre no hover e no Tab do teclado; toque/clique fixa; Esc, clique fora ou rolar fecha; clicar no (i) dentro do formulário **não salva** |
