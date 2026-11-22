# Activest — Plano das consultas repetidas (Metas, Carteira e Preço Teto)

> Fase: **Discovery, Impact e Planning refeitos em 21/11/2026** — o Discovery de 20/11 foi conferido contra o
> código e **3 das 6 premissas estavam erradas** (B.1, B.3, B.6). As decisões D1–D8 foram respondidas em 21/11/2026.
> **Nada foi alterado no código até 21/11/2026.**
> Origem: ESCOPO "Melhoria Activest ponytail", pedido do Guilherme em 20/11/2026 — eliminar trabalho repetido
> nas consultas do backend que atrasam as telas Metas, Preço Teto, Carteira, Ativos e Performance.
> Escopo A (regra do ticker base) concluído em 21/11/2026; este é o escopo B; o escopo C (duplicação) está
> planejado e à espera; o escopo D (perfil do Java) foi aberto em 21/11/2026 — `docs/planos/PLANO_PERFIL_JAVA.md`.

---

## 1. Objetivo

Reduzir o número de consultas repetidas a cada resposta de endpoint, sem mudar nenhum número visível ao usuário.
A resposta de cada tela continua a mesma; só a via de chegar nela deixa de ser "um loop por posição".

### Medição de base (21/11/2026, dev, warm — 2ª chamada em diante)

O que mudou em relação ao Discovery de 20/11: cada statement foi medido no PostgreSQL
(`log_min_duration_statement = 0`) e o tempo de SQL foi separado do tempo de resposta. **O SQL é 8% a 38% do
tempo das telas** — o resto é Java (hidratação do JPA, montagem de DTO, serialização) e transporte.

| Endpoint | Resposta | Consultas | **Tempo de SQL** | % do tempo | Java + transporte |
|---|---|---|---|---|---|
| `GET /goals/` | 335 ms | 149 | **33 ms** | 10% | 302 ms |
| `GET /portfolio/assets` | 139 ms | 97 | **11 ms** | 8% | 128 ms |
| `GET /ceiling-prices` | 94 ms | 39 | **12 ms** | 13% | 82 ms |
| `GET /assets/indicators?kind=COMPANY` | 294 ms | 80 | **111 ms** | 38% | 183 ms |
| `GET /assets/types` (referência) | 17 ms | ~3 | ~2 ms | — | 15 ms (custo fixo) |

O **custo fixo de uma requisição** (proxy do Vite + HTTP + JWT + validação de sessão + filtros do Spring) é de
**~17 ms**, medido por `/assets/types`.

**Consequência para o escopo:** eliminar as consultas repetidas **não** multiplica por 3 o ganho de tempo. Cada
consulta eliminada leva com ela o custo de hidratação do Hibernate, que não aparece na medição do banco. A
estimativa honesta do escopo B inteiro é de **15% a 25%** no tempo das três telas que ele toca — não os 2–3× que
a contagem de consultas sugere. O ganho que o escopo B entrega é também **código mais simples**, que é o outro
objetivo do exercício.

A tela **Ativos** é a exceção: ali o SQL é 38% do tempo (111 ms em 80 consultas), mas por causa de **3 consultas
pesadas** (trimestres 19 ms, proventos 16 ms, 14 séries de preço 22 ms), não de repetição — por isso ficou fora
do escopo B (D7) e foi para o escopo D.

---

## 2. Discovery

### B.1 Metas — o `GoalFacts` é construído uma vez por requisição, e são duas requisições

`GoalService.scopeOptions` (app-api:76) chama `GoalFactsLoader.load(userId)` uma vez.
`GoalService.list` (app-api:110) chama `GoalFactsLoader.load(userId)` uma vez.

**Correção de 20/11:** o plano falava em "2 construções completas do `GoalFacts` por request do `/goals`". Não é
verdade: `/goals` e `/goals/scope-options` são **requisições HTTP diferentes** (`GoalController.java:31` e `:47`),
e o front só chama a de scope-options quando o modal "Nova meta" abre (`GoalFormModal.tsx:88`, `enabled: isOpen`).
Cada requisição já carrega o `GoalFacts` **uma vez** — não há duplicação dentro de uma requisição, e passar o
objeto de uma requisição para a outra é impossível sem mudar a API ou cachear.

**O que sobra de fato:** `GoalFactsLoader.load` (engine/GoalFactsLoader.java:83) carrega, do zero, posições
(`findAllByPortfolioIdWithAssetEagerly`, linha 96), os detalhes da carteira (`portfolioService.getPortfolioAssets`,
linha 102 — os 97 statements da Carteira), as transações (linha 120), os proventos (linha 129), o rebalanceamento
(linha 87) e a linha do tempo (linha 86). **O custo do `/goals` é o custo da Carteira, mais a avaliação de cada
meta.** Eliminar a repetição dentro de uma carga é o caminho — não eliminar uma segunda carga.

### B.2 Metas / ScopeOptions — consultas repetidas por N scope kinds

`GoalService.scopeOptions` (app-api:74): para cada `ScopeKind` (POSITIONS, ASSET, ISSUER, TYPE) itera
`facts.positions()` e monta rótulos diferentes — o que é CPU, não SQL. `PositionInfo.label(k)` e
`PositionInfo.key(k)` são chamados 4× para cada posição.

### B.3 Preço Teto — `defaults.of` faz 9 consultas por cálculo

`CeilingPriceService.list` (app-api:152-158) chama `defaults.of(ticker, id)` **uma vez por cálculo guardado**.
`CeilingPriceDefaults.of` (app-api:97) faz, por cálculo: o ativo (`asset(ticker)`, linha 78), o emissor
(`IndicatorService.context`, linha 842), os trimestres (`IndicatorService.quarters`), a série de fechamentos do
ativo (`closes(assetId)`, linha 879 — a série **inteira**, não só o último preço), o preço atual
(`assets.current_price`), os eventos societários e os proventos do ativo, e a curva de juros (a taxa do desconto).

**Correção de 20/11:** o plano falava em "2 consultas por posição". São **9 por cálculo** (medido: 18 dos 39
statements do `/ceiling-prices` são de `defaults.of`, com 2 cálculos na base). A régua proposta no plano antigo
("SQL total ≤ 15") é **impossível** — o fixo, sem nenhum `defaults.of`, já passa de 20.

### B.4 Carteira — 1 consulta de transações por posição de renda fixa (o maior item medido)

`PositionValuationInfo.of` (app-api:114 e :138) chama `positionCalculator.chronologicalTransactions(pa)` **uma
vez por posição** — e `chronologicalTransactions` (core/PositionCalculator.java:122) é
`transactionRepository.findByPortfolioAssetIdOrderByTransactionDateDesc(pa.getId())`: **1 consulta por posição**.

Medido em 21/11: **35 statements** `select t1_0.id,t1_0.brokerage_fee,…` numa requisição de
`/portfolio/assets` — 32 posições de renda fixa (26 do Tesouro + 6 de RF privada) + as demais. Custam **1,9 ms
de banco**, mas cada uma hidrata entidades no Hibernate.

O caminho em lote já existe e é o mesmo que a Carteira usa para as posições: `PositionCalculator.positionsOf`
(core/PositionCalculator.java:216), que faz **uma** consulta para todas as transações da carteira.

### B.5 Listagem de Ativos — 3 subconsultas sobre `assets`

`IndicatorService.currentAll` (app-api:192,200,209): 3 consultas sobre a tabela `assets` (43k linhas):
`asset_categories`, `issuers`, `stock_events` — todas varridas por `IN` de até 784 tickers. A primeira chamada
é a que custa (paginação da tabela em disco).

**Correção de 20/11:** a caracterização está certa no número, errada no mecanismo. Medido em 21/11: são **80
statements e 111 ms de SQL**, e o custo está em **3 consultas pesadas**, não em repetição:
`company_quarters` com `cvm_documents` (19 ms por chamada), `asset_cash_events` (16 ms) e **14 séries de preço**
(`asset_price_history`, 22 ms somados). Não é N+1 a eliminar — é consulta pesada a Otimizar (D7, fora do escopo).

### B.6 Performance — o `fingerprint` é 1 consulta, não 7

`PortfolioTimelineBuilder.fingerprint` (app-api:161-186) é **uma** consulta com 8 sub-selects escalares (contagens
e máximos indexados), executada **antes** da checagem do cache (linha 140). O TTL da linha do tempo é de 6 horas
(linha 79) e é a impressão digital que invalida o cache quando a carteira muda.

**Correção de 20/11:** o plano falava em "7 count(*) para a mesma pergunta" e propunha um cache separado para a
impressão digital. Cacheá-la por 5 min economizaria ~3 ms por chamada de endpoint, mas **atrasaria em até 5 min**
a reação da tela de Performance a uma transação — é a impressão digital que invalida a cache.

### B.7 Performance — O(D × T) no `sharesOn` (latente)

`PortfolioTimelineBuilder.byShares:309`: para cada dia do eixo (até ~4.000 com `range=MAX`) chama
`positionCalculator.sharesOn(chron, steps, d)`, que re-walks a lista de transações do início (`for
(Transaction tx : chronological)`). Custo O(D × T) por posição; hoje o dev tem ~600 dias e ~30 transações,
custo imperceptível, mas para 10 anos × 50 transações × 2 posições = 1M de walk steps. É **latente**, não
visível no dev, mas quebra em dados reais. A fix: construir os pontos de quantidade uma vez e consultar
binária/por data.

---

## 3. Decisões (para o Guilherme)

| # | Pergunta | Opções | Recomendação | Resposta |
|---|---|---|---|---|
| **D1** | Ordem dos escopos? | (a) A (ticker base) → B (consultas) → C (duplicação); (b) B primeiro; (c) B+C em um só | **(a)**: o escopo A cria o "dono único" que B e C reaproveitam; é pequeno e sem risco | **(a)** — 20/11/2026 |
| **D2** | Como consolidar as cargas do `GoalFactsLoader`? | (a) passar o mesmo `GoalFacts` de `list` para `scopeOptions`; (b) cache por `userId` com TTL de 60 s; (c) **não há duplicação dentro de uma requisição** (a premissa de 20/11 estava errada) | **(c)**: cada requisição já carrega uma vez; o cache traria dado velho para uma tela que existe para ser exata | **(c)** — 21/11/2026 |
| **D3** | Onde eliminar o N+1 de emissor e tipo de ativo? | (a) `Map<Long, Issuer>` + `Map<Long, AssetType>` no `GoalService`; (b) **`JOIN FETCH a.issuer` + `LEFT JOIN FETCH a.assetType`** na própria query (`PortfolioAssetRepository.java:51`) | **(b)**: corrige Carteira, Metas, Estratégia e Rebalanceamento de uma vez — o mesmo espírito do escopo A (o dono único é a query) | **(b)** — 21/11/2026 |
| **D4** | `CeilingPriceDefaults.of` — carregar em lote? | (a) pré-carregar um `Map<Long, CeilingPriceDefaults>` por `asset_id`; (b) **deixar**: com 2 cálculos o ganho é ~9 ms de 94 ms | **(b)**: 9 consultas por cálculo × 2 = 18 de 39 statements, mas só 12 ms de SQL no total; o carregador em lote (fundamentals, trimestres, eventos e série de preços de N ativos) não se paga com 2 cálculos | **(b)** — 21/11/2026 |
| **D5** | Cachear o `fingerprint` da Performance? | (a) mapa em memória com TTL de 5 min por (`userId`, `range`); (b) **não** | **(b)**: é 1 consulta (não 7); cacheá-la atrasaria em até 5 min a reação da tela de Performance a uma transação — a impressão digital é o mecanismo de invalidação | **(b)** — 21/11/2026 |
| **D6** | Otimizar `sharesOn` (O(D×T))? | (a) latente — não tocar agora, deixar para um PR dedicado depois do escopo B; (b) incluir agora | **(a)**: não afeta o dev; é um PR separado, baixo risco, alta recompensa para dados reais | **(a)** — 20/11/2026 |
| **D7** | Escopo: quais telas entram? | (a) só Metas + Preço Teto; (b) **+ Carteira**; (c) + listagem de Ativos | **(b)**: o N+1 de 35 consultas mora na Carteira (`PositionValuationInfo`) e as Metas pagam a Carteira inteira; a listagem de Ativos é SQL pesado, não repetição — vai para o escopo D | **(b)** — 21/11/2026 |
| **D8** | Como medir o aceite? | (a) contagem de SQL + tempo de SQL antes/depois, com o JSON comparado campo a campo; (b) só o tempo de resposta | **(a)**: é a receita que o projeto já usa (`PLANO_PERFORMANCE.md`); o tempo de resposta sozinho esconde o motivo | **(a)** — 21/11/2026 |

**Gatilho para rever a D4:** quando o usuário tiver **5 ou mais cálculos** no Preço Teto, a tela passa de 39 para
66+ statements e de 12 para ~20 ms de SQL. Aí o carregador em lote se paga. (Hoje são 2.)

---

## 4. Impact

### 4.1 Componentes afetados

| Componente | Módulo | Mudança |
|---|---|---|
| `PortfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly` | core | passa a trazer `a.issuer` e `a.assetType` junto (hoje só `asset`, `terms` e `note`) — elimina ~25 consultas de emissor e ~12 de tipo de ativo em **todas** as telas que leem a carteira |
| `PositionValuationInfo.of` | app-api | passa a usar as transações já carregadas em lote (`PositionCalculator.positionsOf`) em vez de abrir 1 consulta por posição — elimina 35 statements da Carteira |
| `GoalService.list` / `scopeOptions` | app-api | **sem mudança** (D2 = c) |
| `CeilingPriceService.list` | app-api | **sem mudança** (D4 = b) |
| `PortfolioTimelineBuilder.fingerprint` | app-api | **sem mudança** (D5 = b) |

### 4.2 O que não muda

- As posições retornadas pela Carteira e pelas Metas: os mesmos ativos, os mesmos valores, as mesmas datas.
- O Preço Teto: os 23 defaults são os mesmos; só o caminho muda.
- A listagem de Ativos: mesma ordem, mesmas colunas.
- A Performance: mesma série, mesmo risco, mesma tabela.
- **Nenhum endpoint muda de forma**: nenhum DTO, nenhum campo, nenhuma tela.

### 4.3 Riscos

| Risco | Mitigação |
|---|---|
| `JOIN FETCH a.issuer` numa query que já tem `JOIN FETCH pa.note` — combinações de join | ManyToOne não multiplica linhas (só `@OneToMany`/`@ManyToMany` fariam); `issuer` e `assetType` são ManyToOne. Conferir o `count` da query no teste |
| `LEFT JOIN FETCH a.assetType` vs `JOIN FETCH` | `LEFT`: um ativo sem tipo não pode sumir da carteira |
| `PositionValuationInfo` receber as transações de fora | a lista é a **mesma** que `positionsOf` devolve (a mesma fonte de verdade da posição); o teste da Carteira confere a soma das transações |
| Mudança de query no core afeta 4 telas de uma vez | o aceite é o **JSON idêntico campo a campo** de `/portfolio/assets`, `/goals/`, `/strategy/rebalance` e `/allocation` |
| O `sharesOn` O(D × T) continua latente | deliberadamente fora (D6) |

---

## 5. Planning

### E1 — (cancelado) `GoalFacts` compartilhado

**Cancelado pela D2 = (c).** Cada requisição já carrega o `GoalFacts` uma vez; não há duplicação a eliminar
dentro de uma requisição.

### E2 — Trazer emissor e tipo de ativo junto na query da carteira

Em `PortfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly` (core, `:51`), acrescentar
`LEFT JOIN FETCH a.assetType`, `LEFT JOIN FETCH a.assetCategory` e `LEFT JOIN FETCH a.issuer` (o `assetCategory`
entrou junto com os outros dois: é ManyToOne do mesmo jeito e é tocado na Carteira, nas Metas e na Performance).

**Aceite:** `/portfolio/assets` e `/goals/` não fazem mais as ~25 consultas de emissor nem as ~12 de tipo de
ativo; o JSON das duas telas **idêntico campo a campo**; a bateria verde.
**Feito em 21/11/2026** — ver §7.

### E3 — (adiado) `CeilingPriceDefaults` em lote

**Adiado pela D4 = (b).** Gatilho: 5 ou mais cálculos na conta do usuário. Registrado em `docs/PENDENCIAS.md`.

### E4 — Usar as transações em lote na `PositionValuationInfo`

`PositionValuationInfo.of` (app-api:114 e :138) recebe as transações já carregadas pelo
`PositionCalculator.positionsOf` (a mesma chamada que a Carteira já faz para as posições) em vez de abrir uma
consulta por posição.

**Aceite:** `/portfolio/assets` não abre mais `SELECT … FROM transactions WHERE portfolio_asset_id = ?`
(35 statements → 1); a soma das transações devolvidas bate com a da Carteira; o JSON **idêntico**.
**Feito em 21/11/2026** — a transação de cada posição agora vem de
`PositionCalculator.chronologicalTransactionsOf(portfolioId)` (core), **uma consulta** para a carteira inteira.

### E5 — (cancelado) Cache do `fingerprint`

**Cancelado pela D5 = (b).**

### E6 — (Opcional) `sharesOn` O(D × T) — PR separado, não neste escopo

Documentar no `PortfolioTimelineBuilder.byShares` que o método `positionCalculator.sharesOn(chron, steps, d)`
tem custo O(D × T) e deve ser substituído por uma build única de pontos de quantidade antes do loop (D6).
**Não entra na bateria deste escopo**.

### E8 — A confirmação importa os movimentos na ordem do extrato

**Achado durante o aceite (21/11/2026), fora do escopo mas no mesmo arquivo:** a confirmação da importação
(`IntegrationService.doConfirm`, app-api) importava os movimentos **na ordem em que os itens foram lidos do banco**,
e essa ordem não é a ordem do extrato. Uma venda importada antes da compra que a sustenta é recusada pela Regra A
("Não há posição de PETR4 na carteira para vender") — e o extrato inteiro vem na mesma confirmação.

Foi o que quebrou a bateria: o `IntegrationsB3IT.fullFlow` falhou com 422 em três baterias seguidas e passou nas
outras. **Não era o escopo B** (o caminho da importação não toca nada do que mudou aqui — provado com log no
ponto exato da recusa). Era a ordem: no extrato sintético da B3, a compra de PETR4 (04/03/2024) estava gravada
**depois** da venda (15/05/2024) na lista de itens.

**Correção:** os itens são ordenados pelo instante do movimento (toda fonte o entrega), pela data de negócio e, no
mesmo dia, a compra vem antes da venda. A ordem antiga era arbitrária porque a gravação dos itens na
pré-visualização é ordenada, mas a leitura posterior não traz essa ordem junto.

### E7 — Medição de aceite (D8 = a)

Medir antes e depois, com `log_min_duration_statement = 0` no PostgreSQL do dev:

| Endpoint | Antes (21/11) | Depois (21/11) | Régua | Resultado |
|---|---|---|---|---|
| `/goals/` | 149 statements, 33,1 ms de SQL | **73 statements, 24,2 ms de SQL** | ≤ 110 statements | **OK** |
| `/portfolio/assets` | 97 statements, 11,3 ms de SQL | **46 statements, 8,5 ms de SQL** | ≤ 60 statements | **OK** |
| `/ceiling-prices` | 39 statements, 12 ms de SQL | inalterado | inalterado | fora do escopo |
| `/assets/indicators?kind=COMPANY` | 80 statements, 111 ms de SQL | inalterado | inalterado | fora do escopo |

**Tempo de resposta**, medido no mesmo dia e nas mesmas condições (o mesmo processo, a mesma sessão, o mesmo
banco aquecido; 5 a 8 chamadas, descartando a primeira):

| Endpoint | Antes | Depois | Ganho |
|---|---|---|---|
| `/goals/` | ~183 ms | ~147 ms | **−20%** |
| `/portfolio/assets` | ~110 ms | **89 ms** (8 chamadas: 87–95) | **−19%** |

**JSON idêntico campo a campo** (`docs/planos/PLANO_CONSULTAS_REPETIDAS.md`, E7): 5 telas comparadas com um
script que percorre todo o JSON — `/goals/` (2.543 campos), `/portfolio/assets` (2.078), `/portfolio/allocation`
(28), `/portfolio/allocation/detailed` (69) e `/strategy/rebalance` (29). **4.747 campos, 0 diferenças.**

O que sobrou (para o escopo D): `/portfolio/assets` ainda abre **7** consultas de transações por posição — as do
`BonusCostService.pendingFor`, que pergunta as transações dos títulos com bonificação sem custo — e **8** de
eventos societários, uma por ativo (`PositionCalculator.eventsOf`).

---

## 6. Quem faz

| O quê | Quem |
|---|---|
| Decisões D1–D8 e aprovação do plano | Guilherme |
| E2, E4, E7, reload do backend, testes, conferência | Claude |
| Commit (branch sugerida `TRACK_01-consultas_repetidas`) | Guilherme |

---

## 7. Andamento

- 20/11/2026 — Discovery, Impact e Planning feitos; plano apresentado.
- 20/11/2026 — Guilherme: D1–D6 respondidas; ordem A→B→C; D6 (sharesOn) fica para depois.
- 20/11/2026 — Medição real no dev: `/goals` 150 SQL, `/portfolio/assets` 98, `/ceiling-prices` 39,
  `/assets/indicators?kind=COMPANY` 32.
- 21/11/2026 — Escopo A concluído (`docs/planos/PLANO_TICKER_BASE.md`).
- 21/11/2026 — **Conferência do Discovery contra o código**: 3 premissas erradas (B.1, B.3, B.6) e 1
  caracterização errada (B.5). Medição com o tempo de SQL separado do tempo de resposta: o SQL é 8% a 38% do
  tempo das telas; custo fixo de 17 ms por requisição.
- 21/11/2026 — Guilherme: D2–D8 respondidas; escopo B = Carteira + Metas (D7 = b); D3 corrige na query;
  D4 sai do escopo (gatilho: 5+ cálculos); D5 sai; aceite por statements + tempo de SQL + JSON idêntico.
- 21/11/2026 — Escopo D aberto (`docs/planos/PLANO_PERFIL_JAVA.md`): 87% a 92% do tempo das telas Metas e
  Carteira está no Java, fora do alcance deste escopo.
- 21/11/2026 — **E2 e E4 implementados.** `PortfolioAssetRepository.findAllByPortfolioIdWithAssetEagerly` agora
  traz `assetType`, `assetCategory` e `issuer`; `PositionCalculator.chronologicalTransactionsOf(portfolioId)`
  (core) entrega as transações da carteira numa consulta, e a `PositionValuationInfo` (app-api) passou a usá-la
  em vez de abrir uma consulta por título. `PortfolioService` passa o `portfolioId` para a `PositionValuationInfo`.
- 21/11/2026 — **Aceite (E7)**: `/goals/` 149 → 73 statements e `/portfolio/assets` 97 → 46; o tempo de resposta
  caiu ~20% nas duas; **JSON idêntico campo a campo** nas 5 telas (4.747 campos, 0 diferenças).
- 21/11/2026 — **Armadilha encontrada**: o `mvn compile` incremental **não** recompila arquivo restaurado por
  cópia, porque o `Copy-Item` do PowerShell **preserva a data de modificação** do arquivo de origem — e o `.class`
  fica mais novo que o `.java`, então o compilador considera o fonte em dia. O sintoma é silencioso: o código
  novo não entra no ar e a medição parece "não ter mudado nada". A correção é `touch` no `.java` antes do
  `mvn -o compile`, ou passar a data: `(Get-Item $f).LastWriteTime = Get-Date`. Conferir com
  `javap -p -cp activest-core/target/classes com.activest.domain.portfolio.PositionCalculator | grep -c
  chronologicalTransactionsOf` (ou `grep -ac 'JOIN FETCH a.issuer'` na classe do repositório).
- 21/11/2026 — **E8 (bug achado no caminho):** a confirmação da importação importava os movimentos na ordem de
  leitura do banco, e uma venda antes da compra era recusada pela Regra A. **Não era o escopo B** — provado com log
  no ponto da recusa (o caminho da importação não toca nada do que mudou aqui). Corrigido: os itens são ordenados
  pelo instante do movimento, pela data de negócio e, no mesmo dia, a compra antes da venda.
- 21/11/2026 — **Bateria verde**: `mvn clean verify` — core 242 testes, adm-api 110 funcionais, app-api 242 unit
  + 319 funcionais, **0 falhas**. O `IntegrationsB3IT` (que falhava com o E2/E4) foi o que expôs o E8.
- 21/11/2026 — Dev recarregado com o código final: `/goals/` ~158 ms (era ~183) e `/portfolio/assets` ~95 ms (era
  ~110), com o **JSON idêntico campo a campo** nas 5 telas contra o estado anterior à mudança (4.747 campos, 0
  diferenças).
