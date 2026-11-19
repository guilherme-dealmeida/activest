# Activest — Plano do valor aplicado da renda fixa

> Fase: **Encerrado em 19/11/2026** — as 7 etapas feitas; Review do Guilherme sem observações. Commit (branch sugerida
> `TRACK_01-valor_aplicado_rf`, mensagem `TRACK_01 - Valor aplicado da renda fixa pelo aplicado em aberto`) feito por
> ele (REGRAS_CLAUDE.md 3.1).
> D1–D7 aprovadas pela recomendação do Claude em 19/11/2026 (D1 depois da comparação a × b: (a) separa principal e
> rendimento, bate com a base do IR e não fica negativo).
> Origem: dúvida de 18–19/11/2026 — o "Valor investido" do Dashboard do Activest (R$ 91.586,01) diferia do
> Investidor10 (R$ 89.960,62) em R$ 1.625,39, com os mesmos lançamentos. O item 1 da conciliação (R$ 1.800,00, a
> Caixinha Turbo Flaviane) virou o ESCOPO "Cálculo do valor aplicado para renda fixa Activest" (REGRAS_CLAUDE.md 2.9).

---

## 1. Objetivo

Na renda fixa privada (lançada por valor), o "valor investido / valor aplicado" do Activest é **a soma de todas as
compras do título** e não cai com os resgates. Nos demais tipos (ações, FIIs, Tesouro, fundos) a venda já tira custo
pelo preço médio, e o valor investido é o **custo que continua na posição**.

O objetivo é que, na renda fixa, o valor investido mostrado passe a ser o **aplicado em aberto**: o principal que ainda
está no título depois dos resgates, separado do rendimento resgatado pelos lotes (`FixedIncomeLots`) — sem mudar a
regra da posição (`Position`), as regras E/F/G nem o valor atual.

---

## 2. Discovery (18–19/11/2026)

### 2.1 Conciliação com o Investidor10 (posição a posição, API local × planilha de lançamentos)

| # | Ativo | Activest | Investidor10 | Diferença | Natureza |
|---|---|---|---|---|---|
| 1 | Caixinha Turbo Flaviane (RDB) | 4.210,00 | 2.410,00 | +1.800,00 | **regra de cálculo (este escopo)** |
| 2 | IAU | 611,32 | ~752,88 (implícito) | −141,56 | câmbio: Activest PTAX 5,0096 + taxa US$ 0,17 (Binance); I10 ~6,19 por US$ |
| 3 | KLBN4 | 2.078,74 | 2.117,59 | −38,85 | bonificação de dez/2025: Activest a custo zero (decisão de 18/11/2026, `CostBasis.BROKER`); I10 lançada como compra de 3 × 12,95 |
| 4 | Tesouro IPCA+ 2040 | 7.644,17 | 7.638,15 | +6,02 | compra de 10/02/2026: PU 1.688,82 × 1.668,82 (digitação) + R$ 0,02 de arredondamento |
| 5 | VISC11, ITSA4, HGLG11, USDC, arredondamentos | | | −0,22 | preços com casas diferentes |
| | **Total** | **91.586,01** | **89.960,62** | **+1.625,39** | |

Os itens 2 a 5 são lançamento ou decisão já tomada; ficam com o Guilherme, fora do escopo.

**Correção do que foi dito na conversa:** os resgates da Flaviane foram lançados **iguais** nos dois sistemas — líquido
de R$ 1.800,00 (1.000 + 50 em 30/07, 50 em 21/09, 700 em 28/09/2026). O Activest grava o bruto calculado
(`total_amount` R$ 1.810,14 = líquido + IR R$ 7,12 + IOF R$ 3,02) e o líquido em `net_amount`.

### 2.2 Como o valor investido da renda fixa é calculado hoje

- `PositionCalculator.calculateByTotal` (core): `totalInvested` = soma das compras; `totalSold` = soma das vendas
  (bruto). O Javadoc de `Position` explica: abater o resgatado do aplicado "misturava principal e ganho num número só"
  (o resgate traz rendimento) e tornava as regras E e F indecidíveis.
- Valor atual = aplicado + valorização − resgatado (`PortfolioService.currentValue`). A valorização gravada
  (`portfolio_assets.appreciation`, por `FixedIncomeSettlement.applyCurve`) é **de toda a vida do título**:
  saldo + resgatado − aplicado.
- Abater o resgate inteiro (como o Investidor10) daria custo **negativo** em título com ganho: CDB XP 150% CDI, aplicado
  50.000,00, resgatado 51.325,02 → −1.325,02.

### 2.3 A separação principal × rendimento já existe

`FixedIncomeLots` (core, 15/10/2026, `PLANO_VENDA_RENDA_FIXA.md`): cada aporte é um lote que rende pela curva; o resgate
consome os lotes pela regra da distribuidora (`SaleRule`: PROPORCIONAL, UEPS, PEPS) e, dentro de cada lote, tira
**aplicado e rendimento na mesma proporção** (`Open.take`). O resultado traz `applied` = "aplicado restante (soma dos
lotes)" e os lotes com o aplicado de cada um, que a Carteira já mostra no detalhe dos aportes
(`PositionValuationInfo.Info.lots`). O `PLANO_VENDA_RENDA_FIXA.md` (seção 3, linha do `PositionCalculator`) já previa o
"aplicado restante"; ele ficou só nos lotes.

Flaviane, no banco de dev (19/11/2026):

| | Hoje | Com o aplicado em aberto |
|---|---|---|
| Valor aplicado | 4.210,00 (soma das compras) | **2.434,96** (lotes: 9,89 + 44,49 + 395,43 + 1.285,15 + 700,00) |
| Resgatado (bruto) | 1.810,14 | 1.810,14 = principal 1.775,04 + rendimento 35,10 |
| Valor atual | 2.530,78 | 2.530,78 (não muda) |
| Valorização | 130,92 (de toda a vida) | 95,82 (em aberto: atual − aplicado em aberto) |
| Variação | 3,11% | 3,94% |

O rendimento realizado líquido (35,10 − 10,14 de IR e IOF = **24,96**) é exatamente a diferença que sobra contra o
Investidor10 (2.434,96 × 2.410,00), que trata o resgate líquido inteiro como principal.

Picpay Cofrinho (9.631,25) e Picpay Cashback (276,18), sem resgate: os três números já são iguais.

### 2.4 Quem lê o valor investido da renda fixa hoje

| Onde | O que usa | Observação |
|---|---|---|
| `PortfolioService.getPortfolioSummary` | soma de `totalInvested` → Dashboard "Valor investido"; também `totalReturn`/`returnPercentage` | `totalReturn` = atual + proventos − investido: na RF com resgate conta o resgatado como perda (Flaviane −1.679,22). **Não é exibido** (só no tipo do front) |
| `PortfolioService.getPortfolioAssets` → Carteira | coluna "VALOR APLICADO" (aba RF; info "a soma das compras"), RESGATADO (só RF), VALORIZAÇÃO, VARIAÇÃO (valorização ÷ aplicado), RENTABILIDADE clássica, totais da aba (`portfolioTotals.ts`), linha-mãe (`portfolioGroups.ts`, inclusive a taxa média ponderada pelo aplicado), Top 5 do Dashboard | o front só lê o DTO; mudando o DTO, tudo acompanha |
| `GoalFactsLoader` (Metas) | RF: `totalInvested − totalSold` (aplicado − resgatado **bruto**) | um **terceiro número**: Flaviane 2.399,86 |
| `PositionValuationInfo` | já roda os lotes de toda RF aberta com curva (no resumo e na Carteira) | sem custo extra de consulta |
| Rentabilidade (`/performance/wealth`) | "investido" = aportes − retiradas acumulados | **outro conceito** (fluxo de caixa), fora do escopo |
| `StrategyHoldings`, Alocação, Rebalanceamento | valor atual | não usam o investido |
| `FixedIncomeSettlement` (regras E/F/G/J), `PositionGuard`, `AppreciationUpdater` | `Position.totalInvested`/`totalSold` | **não podem mudar** (seção 4.2) |

### 2.5 Título sem curva

`PositionValuationInfo` devolve `SEM_CURVA` sem lotes para RF sem dados de remuneração ou com TR/série faltando. Sem
curva não há rendimento calculado: o resgate sai inteiro do principal (aplicado − resgatado, nunca abaixo de zero) —
é o mesmo que os lotes dariam sem fator. Hoje, na carteira do Guilherme, nenhuma RF aberta está sem curva.

### 2.6 Achados laterais (fora do escopo)

- `Claude.md`, "Fluxo de Transações → Portfólio", item 4, diz "Renda Fixa: custo = aplicações − resgates, nunca
  negativo"; o código faz o contrário desde a separação aplicado × resgatado. Corrigido junto com este escopo (D7).
- Itens 2 a 5 da conciliação (2.1): lançamentos e uma decisão já tomada (KLBN4).

---

## 3. Decisões (para o Guilherme)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D1** | Qual é o "valor investido" da renda fixa nas telas? | (a) **aplicado em aberto** pelos lotes; (b) aplicado − resgatado líquido (como o Investidor10); (c) manter a soma das compras | **(a)**: separa principal de rendimento, não fica negativo e é o mesmo raciocínio do custo médio das ações |
| **D2** | Onde passa a valer? | Dashboard "Valor investido"; Carteira (coluna, totais, linha-mãe, Top 5); Metas | **Todos**: um número só para o mesmo conceito (as Metas hoje têm um terceiro) |
| **D3** | A Valorização / Variação / Rentabilidade clássica da RF na Carteira | (a) **em aberto**: valor atual − aplicado em aberto (como em ações, onde o ganho da venda sai da linha); (b) manter a de toda a vida (saldo + resgatado − aplicado total) dividida pelo aplicado em aberto | **(a)**: (b) mistura ganho realizado com base em aberto e infla a % |
| **D4** | O que a Carteira mostra do que já foi resgatado | (a) manter a coluna RESGATADO (bruto) e acrescentar, na dica (i) dela, o principal e o rendimento resgatados; (b) trocar RESGATADO por "RENDIMENTO RESGATADO"; (c) tirar a coluna | **(a)**: nada some da tela, e o rendimento realizado fica visível |
| **D5** | Título sem curva (2.5) | (a) aplicado − resgatado bruto, nunca abaixo de zero; (b) manter a soma das compras | **(a)** |
| **D6** | `totalReturn`/`returnPercentage` do resumo (API, não exibidos) | (a) recalcular com o investido novo (atual + proventos − aplicado em aberto); (b) deixar como está | **(a)**: tira o erro do resgate contado como perda |
| **D7** | Documentação | corrigir o item 4 do "Fluxo" no `Claude.md` e o texto da dica "VALOR APLICADO" ("a soma das compras") | sim |

---

## 4. Impact

### 4.1 Componentes afetados (com as recomendações)

| Componente | Módulo | Mudança |
|---|---|---|
| `PositionValuationInfo.Info` | app-api | ganha `openInvested` (aplicado em aberto): soma do aplicado dos lotes; sem curva, aplicado − resgatado (D5) |
| `PortfolioService.getPortfolioAssets` | app-api | na RF privada: `totalInvested` do DTO = aplicado em aberto; `valorization` = valor atual − aplicado em aberto; `returnPercentage` acompanha; campo novo `appliedTotal` (soma das compras) e `soldPrincipal` (principal resgatado), só na RF |
| `PortfolioService.getPortfolioSummary` | app-api | `totalInvested` soma o aplicado em aberto da RF; `totalReturn`/`returnPercentage` com ele (D6) |
| `PortfolioAssetDetailDTO`, `types/index.ts` | app-api, web | os dois campos novos |
| `GoalFactsLoader` | app-api | custo da RF = `totalInvested` do DTO (sem subtrair `totalSold`) |
| `PortfolioPage.tsx` | web | dica de "VALOR APLICADO" e de VALOR ATUAL; dica de RESGATADO com principal e rendimento (D4) |
| `Claude.md`, `PENDENCIAS.md`, `README.md` | docs | 7.2 das regras; item 4 do Fluxo (D7) |

**Não mudam:** `Position`, `PositionCalculator`, `FixedIncomeLots`, `FixedIncomeSettlement`, `portfolio_assets.appreciation`
(continua de toda a vida, base das regras E/F/G), valor atual, líquido estimado, Estratégia, Alocação, Rebalanceamento,
Rentabilidade (TWR, XIRR, patrimônio), IR. Tesouro, ações, FIIs, fundos e cripto: nada. Sem migration.

### 4.2 Riscos

| Risco | Cuidado |
|---|---|
| Mudar `Position.totalInvested` quebraria o valor atual (aplicado + valorização − resgatado) e as regras E/F | a mudança fica **só na apresentação** (DTOs); `Position` e a valorização gravada não mudam |
| Aplicado em aberto depende do índice: mês de IPCA/IGP-M/INPC estimado pode mudar alguns centavos ao sair o oficial | aceitável: calculado na leitura, corrige sozinho; dito na dica |
| Título liquidado: lotes zeram | posição liquidada já fica fora das telas e do resumo |
| Desempenho | os lotes já são calculados no resumo e na Carteira (`valuationInfo.of(open)`); nenhuma consulta nova |
| Metas: o custo da RF muda (2.399,86 → 2.434,96 na Flaviane) | metas que usam custo podem mudar de situação; conferido no `GoalsIT` e no script `carga/scripts/conferencia/metas.mjs` |

---

## 5. Planning

### 5.1 Solução (com as recomendações)

1. `PositionValuationInfo.Info` + `openInvested`: com curva, `Result.applied()` (= soma dos lotes); sem curva,
   `max(0, aplicado − resgatado)`. Tesouro e fundos: nulo (já têm custo por cota).
2. `PortfolioService`: helper único `displayInvested(position, info)` — RF privada aberta → `openInvested`; demais →
   `position.totalInvested()`. Usado no resumo e na Carteira; na RF, `valorization = currentValue − displayInvested`.
3. DTO: `appliedTotal` (soma das compras) e `soldPrincipal` (aplicado total − aplicado em aberto), nulos fora da RF.
4. `GoalFactsLoader`: custo = `d.totalInvested()`.
5. Front: dicas (i) de VALOR APLICADO ("o que continua aplicado: as compras menos o principal já resgatado"), VALOR
   ATUAL ("valor aplicado + valorização") e RESGATADO (bruto, com principal e rendimento); tipos.

### 5.2 Critérios de aceitação

- Dashboard "Valor investido" = **R$ 89.810,97** no dev (91.586,01 − 4.210,00 + 2.434,96); diferença restante contra o
  Investidor10 = −149,65, explicada pelos itens 2 a 5 da seção 2.1 e pelos R$ 24,96 do rendimento líquido realizado.
- Carteira, Flaviane: VALOR APLICADO 2.434,96 = soma da coluna "Aplicado" do detalhe dos aportes; VALOR ATUAL 2.530,78
  (inalterado); VALORIZAÇÃO 95,82; VARIAÇÃO 3,94%; RESGATADO 1.810,14 com principal 1.775,04 e rendimento 35,10 na dica.
- Picpay Cofrinho e Cashback: sem mudança.
- Total da aba Renda Fixa e linha-mãe = soma das linhas.
- Valor atual, líquido estimado, Estratégia, Alocação, Rentabilidade: iguais a antes (comparação do JSON).
- Metas: custo da RF igual ao da Carteira.
- Título sem curva: aplicado − resgatado, nunca negativo (teste).
- `mvn clean verify`, `npm test` e `npm run lint` verdes; tela conferida nos dois temas (4.3).

### 5.3 Etapas do plano

| Etapa | O que |
|---|---|
| E1 | Backend: `openInvested`, helper, DTO, resumo, Metas; testes (`PortfolioServiceTest`, `PortfolioIT`, `DashboardSummaryIT`, `FixedIncomeCurveIT`, `GoalsIT`) |
| E2 | Front: tipos e dicas da Carteira; `portfolioTotals`/`portfolioGroups` conferidos com os valores novos |
| E3 | Testing completo, conferência no navegador e contra os números da seção 5.2 |
| E4 | Review e documentação (`Claude.md`, `PENDENCIAS.md`, `README.md`, andamento) |

---

## 6. Quem faz

| O quê | Quem |
|---|---|
| Decisões D1–D7 e aprovação do plano | Guilherme |
| E1–E4, reload do backend, testes, conferência | Claude |
| Correção dos lançamentos dos itens 2 a 5 da conciliação (se quiser) | Guilherme |
| Commit (branch sugerida `TRACK_01-valor_aplicado_rf`) | Guilherme |

---

## 7. Andamento

- 19/11/2026 — Discovery, Impact e Planning feitos; plano apresentado.
- 19/11/2026 — Guilherme: "seguir com todas as recomendações e avançar de fase". D1 = (a), D2 = todos, D3 = (a),
  D4 = (a), D5 = (a), D6 = (a), D7 = sim. Implementation iniciada.
- 19/11/2026 — **E1 (backend)**: `PositionValuationInfo.Info.openInvested` (= `FixedIncomeLots.Result.applied()`);
  `PortfolioService.displayInvested` (renda fixa: aplicado em aberto; sem curva, aplicado − resgatado, nunca negativo)
  no resumo e na Carteira; valorização da renda fixa = valor atual − aplicado em aberto; DTO com `appliedTotal` e
  `soldPrincipal`; `GoalFactsLoader` com o custo da Carteira. Testes: `PortfolioServiceTest` +4 (com curva, sem curva,
  nunca negativo, tipos por cota), `FixedIncomeCurveIT` +1 (resgate parcial: aplicado = soma dos lotes, entre
  aplicado − resgatado e o aplicado; resumo igual à linha) e dois ajustados para a valorização da tela, conferindo a
  gravada no banco (`redemptionAndExclusion`, `TransactionsIT.partialRedemptionLeavesCent`).
- 19/11/2026 — **E2 (front)**: tipos; dicas de VALOR APLICADO, RESGATADO, VALOR ATUAL e VALORIZAÇÃO (as duas
  últimas por tipo na renda fixa); RESGATADO com sublinhado pontilhado e "Principal · Rendimento" no hover.
  `portfolioTotals`/`portfolioGroups` sem mudança (somam o DTO; só o Tesouro é agrupado).
- 19/11/2026 — **E3 (Testing)**: `mvn clean verify` verde em 7 min 03 s (core 266; integration 116; adm-api 171 + 110;
  app-api 246 + 320); Vitest 195, lint 0/0, `tsc -b` limpo; app-api recarregada (`dev-watch.sh reload`). No dev:
  Caixinha Flaviane — valor aplicado 2.434,96 (= soma dos aportes), resgatado 1.810,14 (principal 1.775,04 ·
  rendimento 35,10), valor atual 2.530,78 (igual a antes), valorização 95,82, variação 3,94%; Picpay sem mudança;
  totais da aba Renda Fixa 12.342,39 / 2.145,41 / 17,38%; valor atual, líquido e alocação do Dashboard iguais. Tela
  conferida nos temas escuro e claro.
  - **Dashboard "Valor investido" = R$ 89.800,21**, e não os 89.810,97 do critério 5.2: entre a conciliação (18/11) e a
    conferência, duas compras do Tesouro IPCA+ 2040 foram editadas na base (não por este escopo): 10/02/2026, PU
    1.688,82 → 1.668,50 (506,65 → 500,55; o Investidor10 tem 1.668,82 / 500,646) e 03/10/2025, PU 1.641,87 → 1.626,33
    (492,56 → 487,90; o Investidor10 tem 1.641,86 / 492,558). Soma −10,76. Com elas, a diferença para o Investidor10
    fica em −160,41.
- 19/11/2026 — **E4 (Review e documentação)**: comentário da coluna VALOR APLICADO atualizado; `Claude.md` (revisão,
  item 4 do Fluxo corrigido — D7 —, "Valor aplicado da renda fixa", Carteira, endpoint, contagem dos testes) e
  `README.md`. `PENDENCIAS.md` sem item novo: os itens 2 a 5 da conciliação são lançamento ou decisão já tomada.
- 19/11/2026 — Review do Guilherme sem observações; escopo encerrado. As operações de git ficam com ele.
