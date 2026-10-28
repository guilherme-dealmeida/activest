# Activest — Plano de testes do front (contas feitas no navegador)

> Fase: **Encerrado em 27/10/2026** — as 7 etapas feitas; Review finalizada e escopo declarado encerrado pelo
> Guilherme, que faz as operações de git (commit e PR da `TRACK_01-testes_front` para a `develop`). Decisões D1–D8 em
> 2.3.1 e 2.3.2. D1–D7 aprovadas pela recomendação ("concordo com todas recomendações de D1 a
> D7 — Implemente"), branch `TRACK_01-testes_front`.
> Origem: pedido do Guilherme em 27/10/2026, logo depois do escopo da Evolução dos Proventos ("já podemos atacar estas
> pendências da seção 40"): `docs/PENDENCIAS.md`, seção 40 — testes de front para as telas com cálculo. 40.1 (Vitest)
> e a parte da Evolução dos Proventos do 40.2 entraram em `docs/planos/PLANO_DIVIDENDOS_POR_PERIODO.md`.
> Objetivo: toda conta que o navegador faz e o usuário vê (ou grava) ganha teste automatizado, como as regras do
> backend já têm.

---

## D. Discovery

### D.1 O que existe

- Vitest 2.1 desde 27/10/2026 (`npm test` = `vitest run`, sem config própria, ambiente Node). Um arquivo de teste:
  `utils/dividendPeriods.test.ts` (17). Testes ficam ao lado do que testam.
- A regra da seção 40: **testar função pura**. Conta que mora dentro de componente é tirada para uma função exportada
  (em `utils/`) e o componente passa a chamá-la — o comportamento não muda. Tela inteira não se testa aqui (E2E é o
  Playwright, escopo futuro; PENDENCIAS 25.5 e 29.10).
- O lint tem `react-refresh/only-export-components`: arquivo `.tsx` que exporta componente e função ao mesmo tempo gera
  aviso. Por isso as funções extraídas vão para arquivos `.ts`.

### D.2 Levantamento, item a item (conferido no código em 27/10/2026)

| Item | Onde | A conta | Hoje |
|---|---|---|---|
| 40.2 (resto) | `pages/DividendsPage.tsx` | `addRow`: líquido = `netAmount ?? totalAmount`; IR = retido + retenção do exterior + IR estimado; soma por tipo | dentro da página |
| 40.3 | `components/performance/PerformancePanels.tsx` | `ReturnHeatmap.yearReturn`: compõe os meses (∏(1 + r) − 1) na coluna "Ano"; mês nulo → ano "—" | dentro do componente — **a única conta de rentabilidade fora do motor do backend** |
| 40.4 | `pages/PortfolioPage.tsx`, `utils/returns.ts` | rodapé: somas de investido, vendido, atual, proventos, valorização; % de valorização e rentabilidade clássica **sobre as somas**; líquido estimado (`estimatedNet ?? currentValue`); nos lotes da RF, rendimento e IOF + IR | dentro da página (`classicReturn` já é exportado) |
| 40.5 | `components/transactions/TransactionFormModal.tsx` | total = cotas × preço (RF e fundo: o valor digitado); cotas do fundo = valor ÷ cota com 8 casas (tem de bater com a API); "Resgatar tudo" preenche o líquido em centavos | dentro do modal (1.235 linhas) |
| 40.6 | `components/ui/MaskedNumberInput.tsx`, `utils/numberPaste.ts` | digitação pela direita (1050 → 10,50), teto de 15 dígitos, apagar com `allowEmpty`; colar substitui, com sinal e casas | `parsePastedNumber` já exportado; a máscara e o colar estão dentro do componente |
| 40.7 | `pages/StrategyPage.tsx`, `components/strategy/ReservationStrategyTab.tsx` | soma dos ideais = 100% (arredondada a uma casa, a mesma regra do backend); % ideal da reserva no Por Valor (min(100, alvo ÷ carteira)); Geração de Renda = total − reserva (piso 0); diferença acima/abaixo | dentro dos componentes |
| 40.8 | `pages/RebalancePage.tsx` | total a aportar e total estratégico | dentro da página |
| 40.9 | `pages/AllocationPage.tsx`, `components/charts/donut.ts`, `PieOfPieChart.tsx`, `CornerLegendDonut.tsx` | total; ordem decrescente e giro 90 → −270 (`byValueDesc` já exportado); meio-ângulo da fatia aberta descontando o espaçamento, linhas das bordas (ou do topo/base quando passa de meia volta); quanto a legenda do canto sobe sem encostar na rosca | geometria dentro dos componentes |
| 40.10 | `pages/GoalDetailPage.tsx`, `components/goals/GoalCard.tsx`, `GoalFormModal.tsx`, `GoalFieldInput.tsx` | meta de limite: uso = max(0, indicador) ÷ alvo; cor da barra (> 100 vermelho, > 80 amarelo); barra limitada a 100%; inteiro truncado | dentro dos componentes |
| 40.11 | `utils/issuerListing.ts`, `components/issuer/FinancialStatements.tsx`, `utils/indicators.ts` | filtros com a unidade (PL digitado em R$ milhões), faixa preenchida tira quem não tem o indicador, busca sem acento; ordem (vazios no fim, desempate pelo ticker); estado na URL; não controladores = consolidado − controladora em centavos; janela e média dos indicadores; abreviação | quase tudo já exportado; `minus` e `rangeFactor` não |
| 40.12 | `utils/format.ts`, `utils/cron.ts`, `utils/legacyUrl.ts`, `utils/integrations.ts`, `utils/phone.ts` | moeda, percentual, data sem fuso, `toLocalISODate`; cron ↔ formulário ↔ texto; rotas e parâmetros antigos; dígitos do CPF; telefone | já exportados |

### D.3 Achados do Discovery

- **A1 — a soma da Estratégia diverge do backend no arredondamento.** A tela confere `Math.round(soma × 10) === 1000`
  sobre a soma em ponto flutuante; o backend (`StrategyService.validateTotal`) soma em `BigDecimal` e arredonda a uma
  casa com HALF_UP. Conferido com Node em 27/10/2026:
  - 0,10 + 99,85 (= 99,95 exato) → a tela soma 99,94999999999999 e **recusa** ("Hoje está em 99,9%"); o backend aceita.
  - 32,23 + 67,82 (= 100,05 exato) → a tela soma 100,04999999999998 e **aceita**; o backend recusa com 422 (o usuário
    vê o erro do backend, então nada é gravado errado — só a mensagem da tela é que não vem).
  Correção natural: somar em centésimos inteiros e arredondar como o backend (D2).
- **A2 — `parseNumber` da listagem de Ativos** lê "1.234" (sem vírgula) como 1,234. Hoje ele lê o que vem da URL
  (`1234.5`) e os campos do painel já entregam número (máscara), então não aparece para o usuário; o teste vai
  documentar o comportamento, sem mudança.

---

## I. Impact

| Onde | O que muda | Risco |
|---|---|---|
| Páginas e componentes da tabela D.2 | a conta sai para uma função em `utils/` e o componente passa a chamá-la; o resultado na tela é o mesmo | regressão por extração mal feita → mitigação: a mesma expressão, `tsc -b`, os testes e a conferência de cada tela no navegador (claro e escuro, 4.3) |
| `utils/*.test.ts` (novos) | os testes | — |
| `pages/StrategyPage.tsx` | só se D2 = corrigir: a soma passa a ser em centésimos | muda o que a tela aceita nos casos de A1 (passa a bater com o backend) |
| `activest-web/Dockerfile` | só se D5 = sim: `npm test` antes do `npm run build` no estágio de build | build da imagem falha se um teste falhar (é a intenção); +~2 s |
| Documentação | `Claude.md` ("Testes do front"), `docs/PENDENCIAS.md` (seção 40 baixada), `README.md`, este plano | — |

Não afeta: backend, banco, API, dados, regras de negócio (salvo A1 com D2 = corrigir).

---

## 2. Planning

### 2.1 Solução

Por item: (1) mover a conta para uma função pura num `.ts` de `utils/` com nome em inglês (ex.: `utils/heatmap.ts`
→ `compoundReturns`, `utils/portfolioTotals.ts`, `utils/strategyRules.ts`, `utils/maskedNumber.ts`,
`utils/goalProgress.ts`, `utils/donutGeometry.ts`); (2) o componente importa e chama; (3) teste ao lado com os casos
do comportamento atual — os números dos testes vêm das regras documentadas no `Claude.md` e nos planos, e onde houver
número real conferido (Carteira, Rentabilidade), dele. Comportamento que os testes mostrarem estranho e não estiver em
A1/A2 vira decisão nova no plano antes de mudar (D3).

Casos mínimos por item (a lista final sai na implementação):

- **40.2** `addRow` com IR brasileiro, isento, sem regra (bruto) e provento do exterior; soma por tipo.
- **40.3** composição de meses positivos e negativos; mês nulo → nulo; ano com poucos meses (início da carteira);
  conferência com um ano real do mapa de calor.
- **40.4** rodapé com posições de ganho e perda; investido zero (sem divisão por zero); líquido estimado misto (com e sem
  `estimatedNet`); rentabilidade clássica = (valorização + proventos) ÷ investido.
- **40.5** total por cotas × preço e pelo valor; cotas do fundo em 8 casas (1.000 ÷ 3,1234567 etc.); centavos do
  "Resgatar tudo".
- **40.6** digitação 1 → 0,01, 1050 → 10,50, com 8 casas; teto de 15 dígitos; colar substitui e mantém o sinal; colar
  acima do teto não muda; texto que não é número não muda; `allowEmpty`.
- **40.7** soma 100 / 99,99 (33,33 × 3) / 99,96 (8,33 × 12) / os casos de A1; reserva por valor acima da carteira (100%),
  carteira zero; Geração de Renda nunca negativa.
- **40.8** totais com itens negativos (vender) e zero.
- **40.9** ordem e empate estável; meio-ângulo com 2 e 5 fatias; fatia > 50% (linhas do topo/base); legenda do canto
  nos três casos (ao lado, abaixo da curva, inteira embaixo).
- **40.10** uso do limite 0, 80, 81, 100, 150; indicador negativo; alvo zero; barra limitada.
- **40.11** filtros por unidade, faixa só com mín/só com máx, sem indicador; ordem com vazios; ida e volta da URL;
  `minus` em centavos; janela 3A/5A/MAX; A2 documentado.
- **40.12** moeda e percentual em pt-BR (com o espaço fino do Intl), data "YYYY-MM-DD" sem voltar um dia, cron nas
  quatro formas e os erros, rotas e parâmetros antigos, CPF válido/inválido/repetido, telefone.

### 2.2 Critérios de aceitação

1. Cada item 40.2–40.12 tem as suas contas em função pura com teste (ou está fora pela decisão D1/D4).
2. `npm test` verde; `tsc -b` e `npm run lint` sem erro nem aviso; `npm run build` ok.
3. Cada tela tocada conferida no navegador nos dois temas, com os mesmos números de antes (Carteira, Transações,
   Estratégia, Rebalanceamento, Alocação, Dashboard, Metas, Ativos, Rentabilidade, Dividendos, Tarefas).
4. A1 resolvido conforme D2.
5. `docs/PENDENCIAS.md` seção 40 baixada item a item; `Claude.md` "Testes do front" com a contagem e o que cobre.

### 2.3 Decisões (para o Guilherme responder)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D1** | Escopo | a) **todos os itens 40.2–40.12**, por etapas · b) só os de prioridade alta (40.3–40.6) agora | **a** — a extração é parecida em todos; as etapas deixam parar entre elas |
| **D2** | A1 — soma da Estratégia | a) **corrigir**: somar em centésimos inteiros e arredondar a uma casa como o backend · b) só documentar no teste | **a** — a tela e o backend passam a dizer a mesma coisa |
| **D3** | Comportamento estranho que aparecer nos testes (fora de A1/A2) | a) **vira decisão no plano antes de mudar**; o teste fixa o atual · b) corrigir direto | **a** — o escopo é testar, não mudar regra |
| **D4** | Geometria dos gráficos (pizza de pizza e legenda do canto) | a) **extrair e testar** · b) fora (é desenho, não dinheiro) | **a** — já quebrou antes (rosca sumindo no Dashboard, 12/10/2026) e é conta pura |
| **D5** | Testes no build da imagem do front | a) **`npm test` no estágio de build do Dockerfile** (imagem não sai com teste vermelho) · b) só manual | **a** |
| **D6** | Relatório de cobertura (`@vitest/coverage-v8`) | a) agora · b) **não por enquanto** | **b** — o critério é "cada conta tem teste", não um percentual |
| **D7** | Onde ficam as funções extraídas | a) **`utils/<assunto>.ts`** (padrão atual; o `.tsx` continua só com componente) · b) exportadas do próprio componente | **a** — evita os avisos do `react-refresh` |

### 2.3.1 Decisões tomadas (27/10/2026)

Todas pela recomendação, com aprovação explícita do Guilherme: **D1** = a (todos os itens, por etapas); **D2** = a
(corrigir a soma da Estratégia); **D3** = a (estranho vira decisão antes de mudar); **D4** = a (geometria testada);
**D5** = a (`npm test` no build da imagem); **D6** = b (sem cobertura por enquanto); **D7** = a (`utils/<assunto>.ts`).

### 2.3.2 Decisão nova, achada na implementação (D3: o teste fixa o atual, nada mudou)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D8** ✅ **b — alinhar (Guilherme, 27/10/2026: "Sim quero este ajuste", depois de perguntar se era difícil)** | Cotas do fundo no formulário: a prévia arredonda meio para cima no ponto flutuante; a API grava com `BigDecimal` HALF_EVEN (`TransactionService`, linha 1243). Só diferem no empate da 9ª casa (1 ÷ 512 = 0,001953125 → tela 0,00195313, API 0,00195312) | a) **deixar como está** — a prévia é informativa, quem grava é a API, e o empate exige cota com só fatores 2 e 5 (as cotas da CVM têm 8 casas e quase nunca terminam a divisão) · b) alinhar a tela ao HALF_EVEN | **a** |

### 2.4 Etapas do plano

| # | Etapa | Itens | Telas para conferir |
|---|---|---|---|
| T1 | Rentabilidade e Dividendos | 40.3, 40.2 | Rentabilidade (mapa de calor), Dividendos |
| T2 | Carteira e Transações | 40.4, 40.5 | Carteira (rodapé, lotes da RF), modal de transação (compra, fundo, resgate total) |
| T3 | Campos numéricos | 40.6 | Transações, Estratégia, Preço Teto, filtros de Ativos |
| T4 | Estratégia, Rebalanceamento e Metas | 40.7 (com D2), 40.8, 40.10 | Estratégia (as duas abas), Rebalanceamento, Metas |
| T5 | Alocação e roscas | 40.9 | Alocação, Dashboard, Estratégia |
| T6 | Ativos | 40.11 | Ativos (listagem e Demonstrativos) |
| T7 | Utilitários | 40.12 | Tarefas, links antigos, Integrações |
| T8 | Fechamento | D5, documentação | — |

Cada etapa fecha com `npm test`, `tsc -b`, lint e a conferência das telas dela (nos dois temas) antes da próxima.
Testing (5), Review (6) e Commit (7) depois da T8.

### 2.5 Quem faz

| Atividade | Quem |
|---|---|
| Responder D1–D7 e aprovar a Implementation | Guilherme |
| Criar a branch `TRACK_01-testes_front` a partir da `develop` | Guilherme |
| T1–T8, Testing e Review | Claude |
| Conferência final no navegador | Guilherme |
| Commit e PR para a `develop` | Guilherme (mensagem sugerida pelo Claude na etapa 7) |

### 2.6 Andamento

| Data | Etapa | Situação |
|---|---|---|
| 27/10/2026 | Discovery, Impact, Planning | feito — plano apresentado |
| 27/10/2026 | Aprovação | D1–D7 pela recomendação; "Implemente"; branch `TRACK_01-testes_front` criada pelo Guilherme |
| 27/10/2026 | T1 | `utils/heatmap.ts` (`compoundReturns`, `heatmapYears`) + 8 testes, um com os 12 meses reais de 2024 (líquido) contra o ano do motor (13,2942%); `utils/dividendTotals.ts` + 4 (o `taxCents` da Evolução passou a usar `dividendTax`). Conferido: mapa de calor igual, Total Recebido R$ 3.143,95 |
| 27/10/2026 | T2 | `utils/portfolioTotals.ts` + 7 (com `classicReturn`); `utils/transactionAmounts.ts` + 7. Achado: D8 (cotas do fundo, empate na 9ª casa). Conferido: rodapé da Carteira |
| 27/10/2026 | T3 | `utils/maskedNumber.ts` (`maskedChange`, `maskedDisplay`, `maskedPaste`) + 17 (com `parsePastedNumber`). Conferido no modal de transação: 1050 → 10,50; + 7 → 105,07; total 105,07 × 0,03 = R$ 3,15 |
| 27/10/2026 | T4 | `utils/strategyRules.ts` + 12, com A1 corrigido (D2): soma em centésimos e HALF_UP a uma casa; o "Total" da tabela fica verde pela mesma regra (a estratégia real, 33,33 × 3 = 99,99%, passou de vermelho para verde). `utils/goalProgress.ts` + 5. Conferido: Estratégia, Reserva (3,14% abaixo), Rebalanceamento, Metas |
| 27/10/2026 | T5 | `utils/donutGeometry.ts` (`pieOfPieGeometry`, `cornerLegendPull`) + 12 (com `byValueDesc`). Conferido: pizza de pizza (fatia > meia volta, linhas do topo e da base), Dashboard. O total da Alocação (uma soma) não foi extraído |
| 27/10/2026 | T6 | `issuerListing` + 16 (A2 documentado), `indicators` + 9 (`centsDifference` saiu do `FinancialStatements`). Conferido: listagem com DY 6–12 (96 de 409), não controladores da PETR4 |
| 27/10/2026 | T7 | `format` + 10, `cron` + 9, `legacyUrl` + 8, `people` + 6 (CPF, data/hora, celular). Conferido: horários da tela Tarefas |
| 27/10/2026 | D8 = b | `fundShares` em inteiros exatos (`BigInt`: centavos × 10¹⁴ ÷ cota × 10⁸, resto decide, meio vai para o par) — igual ao `BigDecimal` HALF_EVEN da API. Esperados dos testes calculados com Decimal HALF_EVEN do Python: 1 ÷ 512 = 0,00195312; 3 ÷ 512 = 0,00585938; 12.345,67 ÷ 1,00000003 = 12.345,66962963; extremos (0,01 ÷ 99.999,99999999; 250.000 ÷ 0,00000001). 148 testes verdes no Windows e no build Linux; conferido no navegador (o módulo no Vite devolve os mesmos valores) |
| 27/10/2026 | Encerramento | Review OK pelo Guilherme, escopo encerrado; Commit: alterações conferidas e mensagem sugerida pelo Claude |
| 27/10/2026 | T8, Testing, Review | `npm test` no estágio de build do `Dockerfile` (build Linux/UTC: 147 verdes); `tsc -b`, lint (0 erro, 0 aviso), `npm test` (147 em 15 arquivos), `npm run build`; telas conferidas no claro e no escuro (D4.3); `Claude.md`, `PENDENCIAS` (seção 40 baixada, 40.13 novo) e `README` |
