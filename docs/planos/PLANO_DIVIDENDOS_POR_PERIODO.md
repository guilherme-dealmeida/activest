# Activest — Plano de proventos por período (mensal e anual)

> Fase: **Encerrado em 27/10/2026** — as 7 etapas feitas; Review finalizada e escopo declarado concluído pelo
> Guilherme, que faz as operações de git (branch `TRACK_01-dividendos_por_periodo`, commit e PR para a `develop`).
> Decisões D1–D11 e ajustes R1–R6 registrados em 2.3.1. Respostas do
> Guilherme às D1–D11 e a retirada dos cards registradas em 2.3.1.
> Origem: pedido de 27/10/2026 (ESCOPO "AMOSTRAGEM DE DIVIDENDOS POR PERIODO ACTIVEST").
> Dor: na auditoria dos proventos da carteira foi preciso pedir ao Claude para totalizar os valores por mês e por ano,
> para comparar com o Investidor10 e o Nubank. A tela de Dividendos só tem listas e totais gerais.
> Pedido: uma aba no menu Dividendos (pode ser a primeira) com os valores mensais e anuais dos proventos históricos e a
> receber, com gráficos. Sem nomenclatura nem desenho prévio: o importante é expor a informação, de um jeito moderno.

---

## D. Discovery

### D.1 Como a tela funciona hoje

- **Tela** (`activest-web/src/pages/DividendsPage.tsx`, 514 linhas): duas abas, **Histórico de Proventos** (padrão,
  sem parâmetro) e **Proventos a Receber** (`?tab=to-receive`). Cada aba tem dois cards (total; por tipo de provento,
  com bruto e IR embaixo), a busca por ticker ou nome (`LocalAssetSearch`), os filtros Tipo de Ativo → Ativo → Tipo e a
  tabela paginada. As duas abas ficam montadas (a inativa só escondida) e guardam os próprios filtros.
- **Dados**: uma chamada só, `GET /api/v1/dividends/` (`DividendService.getDividendSummary`). O campo `recent` traz
  **todos** os proventos da carteira (não há corte), cada um com `exDate`, `paymentDate`, `type`, `totalAmount`
  (bruto), `withholdingTax`, `netAmount`, `assetTypeId/Description`, `ticker`, `received` e `foreign` (moeda,
  câmbio e tributos do exterior; os totais já vêm em reais).
- **Filtros e somas já rodam no navegador** sobre esse `recent`: o TOTAL de cada linha é o líquido (`netAmount`) e, sem
  regra de IR, o bruto (`addRow` na tela; `Dividend.netOrGross` no backend).
- **Recebido × a receber** vem pronto do backend (`received`, `Dividend.isReceived(today)` com o "hoje" de São Paulo
  do `DividendCalendar`): recebido = pagamento até hoje (D0); sem pagamento, recebido se o evento veio só do histórico
  longo da B3 (`B3_HISTORICO`) ou é MANUAL sem evento; a receber se a fonte recente diz "a definir".
- **Dashboard**: o card Proventos mostra "Últimos 12 meses" (`last12Months`: recebidos com pagamento — ou, sem ele,
  data ex — depois de hoje − 12 meses), "Totais" e "A receber"; os dois primeiros levam a `/dividends`, o terceiro a
  `/dividends?tab=to-receive` (`DashboardPage.tsx`, linhas 197–199).
- **Gráficos já existentes que servem de referência**: barras por período e mapa de calor mês × ano da Rentabilidade
  (`components/performance/PerformanceCharts.tsx` → `PeriodicChart`; `PerformancePanels.tsx` → `ReturnHeatmap`), com
  tema escuro/claro (`useChartTheme`), balão `TipBox` e a paleta validada de `performanceFormat.ts`. Recharts 2.12 já
  é dependência.
- **Front sem executor de testes**: `package.json` tem `build` (`tsc -b && vite build`) e `lint`; não há Vitest/Jest.
  Não há exportação de CSV em nenhuma tela.

### D.2 Os dados reais (banco do dev, 27/10/2026)

Carteira do Guilherme: **236 proventos**, de 12/2024 a 12/2027 (158 rendimentos, 49 JCP, 29 dividendos), todos com
data de pagamento, nenhum em moeda estrangeira. 16 a receber (nov/2026 a dez/2027).

| Ano (pela data de **pagamento**) | Proventos | Líquido | Bruto | IR |
|---|---|---|---|---|
| 2024 | 3 | R$ 13,07 | R$ 13,57 | R$ 0,50 |
| 2025 | 82 | R$ 1.230,60 | R$ 1.254,18 | R$ 23,58 |
| 2026 (recebido R$ 1.900,28 + a receber R$ 136,13) | 144 | R$ 2.036,41 | R$ 2.099,33 | R$ 62,92 |
| 2027 (só a receber) | 7 | R$ 80,71 | R$ 97,79 | R$ 17,08 |

**Achado que pesa no desenho — a data de referência muda o resultado.** 181 dos 236 proventos (77%) são pagos em mês
diferente do da data com. Pela **data com**, o líquido de 2025 é **R$ 1.467,07** (contra R$ 1.230,60 pelo pagamento) e
o de 2026, R$ 1.813,22. Comparar com outra plataforma só fecha se as duas usarem a mesma data e a mesma base (líquido ou
bruto) — daí D3 e D4.

---

## I. Impact

| Onde | O que muda | Risco |
|---|---|---|
| `DividendsPage.tsx` | aba nova; ordem das abas e aba padrão (D2); parâmetro `?tab=` | links salvos para `/dividends` passam a abrir a aba nova se ela for a padrão — esperado |
| `components/dividends/` (novo) | a aba e os seus componentes (cards, gráfico mensal, gráfico anual, tabela mês × ano, detalhe do mês) | — |
| `utils/dividendPeriods.ts` (novo) | agregação pura: mês/ano de referência, base, situação, séries, totais, YTD, 12 meses | é aqui que o número pode errar → é o que se testa |
| `DashboardPage.tsx` | destino dos links do card Proventos (D2) | — |
| `index.css` | overrides do tema claro para classes com opacidade novas | classe sem override some no claro (Padrão Visual) |
| Backend (app-api) | **nada**, se D8 = navegador: `recent` já traz tudo | payload cresce com o histórico — já é carregado hoje |
| Testes do front | Vitest, se D9 = sim (dependência de desenvolvimento nova) | instalar pacote pela máquina do Guilherme |
| Documentação | `Claude.md` (Telas, "Proventos recebidos × a receber"), `README.md`, `docs/PENDENCIAS.md` | — |

Não afeta: banco, migrations, adm-api, rotinas, cálculo de IR, regra de recebido × a receber, demais telas.

---

## 2. Planning

### 2.1 Solução

Aba nova **Evolução dos Proventos** (D1), primeira e padrão da tela (D2), rota `?tab=periods`, com os mesmos filtros
das outras abas e estes blocos, de cima para baixo (sem cards de resumo — 2.3.1, R1):

**A. Controles** (uma faixa, como na Rentabilidade): busca por ticker ou nome · Tipo de Ativo → Ativo · Tipo de
provento. Tudo recalcula os blocos abaixo. Sem alternância de base nem de data: sempre **líquido** e sempre pela
**data de pagamento** (D3.1, D4.1).

**B. ~~Quatro cards~~** — retirado pelo Guilherme (27/10/2026): a aba não tem cards.

**C. Proventos por mês** — barras mensais empilhadas, janela **12M | 24M | ano (seletor) | MAX** (padrão 12M para trás
e os meses a receber para a frente), com:
- cada barra dividida por **tipo de ativo**, com **FIIs na base** (a parte mais constante) e os demais tipos empilhados
  acima, e o **a receber num bloco só, no tom mais claro, no topo**, sem distinguir tipo (D5). Só o mês corrente tem
  as duas situações; os meses passados têm só o recebido e os futuros só o a receber;
- linha tracejada da média mensal da janela;
- balão: mês, total, cada parte, número de proventos; mês corrente marcado "parcial";
- clique na barra abre o detalhe do mês (bloco E).

**D. Proventos por ano** — barras por ano com a mesma divisão do bloco C (FIIs na base, demais tipos, a receber claro no
topo), valor em cima de cada barra e o
crescimento sobre o ano anterior; o ano corrente marcado "parcial"; anos futuros só com o a receber já anunciado.

**E. Mês a mês** — tabela ano × mês (anos mais recentes em cima): 12 colunas, **Total do ano** e **Média mensal** (no ano
corrente, sobre os meses decorridos), linha de total no rodapé. Célula com mapa de calor de uma cor só (dourado da
marca, intensidade pelo valor), a receber em itálico com marca; valores em R$ com 2 casas. É a tabela da auditoria:
- **clique na célula** (ou na barra do bloco C) abre o **detalhe do mês** (D6): os proventos daquele mês agrupados por
  ativo — ticker, tipo(s), quantidade, bruto, IR, líquido — com os totais e o link "Ver no Histórico" (aba Histórico ou A
  Receber filtrada pelo ativo);
- botão **copiar tabela** (D7) para colar no Excel/Sheets e conferir com as outras plataformas.

**Regras de cálculo** (em `utils/dividendPeriods.ts`):
- **C1** Valor: sempre o líquido, `netAmount ?? totalAmount` (o TOTAL da tabela). Em reais (provento do exterior já vem
  convertido).
- **C2** Mês de referência: sempre o pagamento, `paymentDate`; o recebido sem ela (histórico longo da B3) entra pela data
  com, a mesma regra do `last12Months` do Dashboard. O provento **a receber sem data de pagamento** não entra em mês
  nenhum: aparece numa linha "N proventos a receber sem data — R$ X" abaixo dos gráficos.
- **C3** Recebido × a receber: o `received` do backend, sem regra nova na tela.
- **C4** Do primeiro ao último mês com provento, todo mês aparece, com zero quando não houve nada.
- **C5** Mês e ano corrente são "parciais" (marcados no gráfico e na tabela).
- **C6** ~~Comparação do ano até hoje~~ — saiu com os cards.
- **C7** Somas em centavos inteiros (`Math.round(v × 100)`) para não acumular erro de ponto flutuante.
- **C9** (Implementation) Média mensal do ano na tabela = total do ano ÷ meses do ano dentro do intervalo de C4 (do
  primeiro provento ao último anunciado): 2024 tem 1 mês; 2027, de janeiro a dezembro (último a receber em 12/2027).
- **C10** (Implementation) Janelas 12M e 24M do gráfico mensal: até o mês corrente e mais até 6 meses à frente
  (`AHEAD_MONTHS`). Sem esse limite, o 12M ia até dez/2027 com 14 meses quase vazios; o a receber mais distante fica
  no MAX, no ano dele e na tabela. Linha da média = total dos meses da janela até o corrente ÷ quantos são (no 12M,
  R$ 2.310,12 ÷ 12 = R$ 192,51, o mesmo "últimos 12 meses" do Dashboard).
- **C11** (Implementation) Crescimento anual: no balão do gráfico anual, só entre dois anos completos (passados e com
  12 meses no intervalo); hoje nenhum par se qualifica (2024 começa em dezembro, 2026 está em andamento).
- **C8** Os filtros e a base valem para os blocos C a E; a aba guarda os próprios filtros ao trocar de aba, como as
  outras.

Cores: tons da marca para recebido × a receber; paleta categórica já validada (`performanceFormat.ts`) para tipos;
conferidas nos dois temas com o validador da skill de visualização na implementação.

### 2.2 Critérios de aceitação

1. A aba existe na posição e com o nome decididos (D1, D2), abre por `?tab=periods` e os links do Dashboard levam à aba
   certa.
2. Com a carteira do Guilherme, base Líquido, data Pagamento, sem filtro: 2025 = **R$ 1.230,60**, 2026 = **R$ 2.036,41**
   (recebido R$ 1.900,28 + a receber R$ 136,13), 2027 = **R$ 80,71**; jun/2026 = **R$ 257,58**; nov/2025 = **R$ 239,62**.
3. ~~Base Bruto e Data com~~ — saíram (D3.1, D4.1): não há alternância na tela.
4. ~~Cards~~ (saíram). No lugar, a soma da tabela bate com as outras abas: tudo o que foi recebido = **R$ 3.143,95** =
   "Total Recebido" do Histórico; tudo o que falta receber = **R$ 216,84** = "Total a Receber" (sem filtro e com o
   mesmo filtro).
5. As partes de cada barra somam o total do mês: jun/2026 = FIIs R$ 135,74 + Ações R$ 121,84 = **R$ 257,58**; dez/2026 =
   **R$ 94,65**, todo no bloco claro do a receber.
6. Os filtros (busca, tipo de ativo, ativo, tipo) mudam cards, gráficos e tabela juntos.
7. O detalhe de um mês soma exatamente o valor da célula/barra e lista os proventos por ativo.
8. "Copiar tabela" cola no Excel como uma grade (anos × meses), com números reconhecidos como números.
9. Tema escuro e claro legíveis; tela de celular sem rolagem horizontal da página (a tabela rola dentro do card). O
   menu lateral do app em 375 px é de todas as telas e fica fora deste escopo.
10. `tsc -b`, `npm run lint` e o build da imagem do front sem erro; testes da agregação verdes (se D9).

### 2.3 Decisões (para o Guilherme responder)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D1** | Nome da aba | a) **Por Período** · b) Resumo Mensal e Anual · c) Evolução · d) outro | **a** — curto, ao lado de "Histórico de Proventos" e "Proventos a Receber" |
| **D2** | Posição e aba padrão | a) **primeira e padrão** de `/dividends` (Histórico vira `?tab=received`; Dashboard: "Últimos 12 meses" → aba nova, "Totais" → Histórico, "A receber" → A Receber) · b) primeira, mas o Histórico continua padrão · c) última | **a** — é a visão-resumo; as listas viram o detalhe |
| **D3** | Base padrão | a) **Líquido**, com alternância para Bruto · b) Bruto, com alternância · c) só líquido | **a** — o líquido é o que o resto do Activest soma; o bruto existe para bater com quem mostra bruto |
| **D4** | Data de referência padrão | a) **Pagamento**, com alternância para Data com · b) Data com, com alternância · c) só pagamento | **a** — é o dinheiro na conta (extrato do Nubank). Vale conferir qual data o Investidor10 usa na tela que você compara: a diferença em 2025 é de R$ 236 |
| **D5** | Empilhamento do gráfico mensal | a) **seletor Situação / Tipo de provento / Tipo de ativo** (padrão Situação) · b) só Situação | **a** |
| **D6** | Detalhe do mês | a) **painel abaixo da tabela**, aberto pelo clique (fecha no X) · b) modal · c) sem detalhe: o clique leva ao Histórico filtrado pelo mês | **a** — compara mês a mês sem perder a tabela de vista |
| **D7** | Levar os números para fora | a) **copiar tabela** (cola no Excel/Sheets) · b) baixar CSV · c) os dois · d) nenhum | **a** — resolve a comparação sem arquivo |
| **D8** | Onde agregar | a) **no navegador**, sobre o `recent` que a tela já carrega · b) endpoint novo `GET /dividends/periods` na app-api | **a** — zero backend, mesma fonte das outras abas (os totais batem por construção); b só se o histórico ficar grande demais |
| **D9** | Testes da agregação | a) **adicionar Vitest** (dependência de desenvolvimento, `npm test`) só para `utils/dividendPeriods.ts` · b) sem teste automatizado: conferência pelos números do SQL (2.2) | **a** — é onde o número pode errar, e passa a existir teste no front |
| **D10** | Lembrar Base, Data e janela | a) **não lembra** (volta ao padrão, como a Rentabilidade desde 17/10/2026) · b) na URL · c) no navegador | **a** |
| **D11** | Card "Quem mais pagou" (Top 10 ativos da janela) | a) incluir · b) **fora deste escopo** | **b** — foco no pedido; fica anotado em PENDENCIAS |

### 2.3.1 Decisões tomadas (27/10/2026)

- **R1 — sem cards.** "Não vamos colocar os 4 cards sugeridos nesta aba": o bloco B saiu, com a regra C6 e os
  critérios dos cards.
- **D1** = **Evolução dos Proventos**.
- **D2** = a — primeira aba e padrão de `/dividends`; Histórico em `?tab=received`; Dashboard: "Últimos 12 meses" → aba
  nova, "Totais" → Histórico, "A receber" → A Receber.
- **D3** = c — **somente líquido** (D3.1), sem alternância para bruto.
- **D4** = c — **somente pagamento** (D4.1), sem alternância para data com.
- **D5** = "Por Situação e Por Tipo Ativo (FII sendo a base por ter mais constância)", com a observação: as duas
  situações só aparecem juntas no mês atual, então o a receber pode ser a cor mais clara sem distinguir o tipo.
  Entendimento do Claude, **a confirmar**: sem seletor; barra dividida por tipo de ativo com FIIs na base e os demais
  tipos acima; o a receber num bloco só, claro, no topo; o mesmo desenho no gráfico anual (bloco D).
- **D6** = a — painel do detalhe do mês abaixo da tabela, aprovado (confirmado de novo em 27/10/2026); ajustes ficam
  para a Review.
- **D7** = a — copiar tabela.
- **D8** = a — agregação no navegador.
- **D9** = a — Vitest para `utils/dividendPeriods.ts`, "o primeiro de muitos": testes de front para as telas com
  cálculo viraram a seção 40 de `docs/PENDENCIAS.md`, com o levantamento das telas.
- **D10** = a — não lembra as escolhas.
- **D11** — não se aplica (sem cards).

- **Ajustes da conferência do Guilherme (27/10/2026, noite)**, com prints da tela:
  - **R2** — as barras usam a **cor do tipo de ativo do cadastro** (`asset_types.color_hex`: FIIs verde, Ações
    vermelho...), a mesma da Alocação, e não a paleta da skill de visualização. O vão de 2 px separa os segmentos; o a
    receber continua cinza hachurado.
  - **R3** — um gráfico de cada vez: **Por mês | Por ano**, no lugar dos dois cards.
  - **R4** — barras mais grossas (até 44 px por mês e 72 px por ano).
  - **R5** — "Mês a mês" com a chave **Incluir a receber**: desligada, a tabela, o "Copiar tabela" e o detalhe do mês
    ficam só com o recebido.
  - Corrigido: as opções do seletor de ano saíam com texto claro em fundo branco no tema escuro (herdavam a cor do
    seletor); agora têm o fundo e o texto do tema.
  - Regra nova em `docs/REGRAS_CLAUDE.md`, 4.3: testar nos dois temas sem pedir permissão.
  - **R6** — card do detalhe do mês: coluna **Tipo Ativo** ao lado de Ativo (o tipo saiu de dentro da coluna Ativo),
    **colunas ordenáveis** (`useSortable`, padrão líquido decrescente) e **card sempre na tela** — sem mês escolhido,
    título "Proventos" e a instrução; o X limpa o mês. Clique na tabela não rola mais a tela (o card já está logo
    abaixo); clique no gráfico continua descendo até ele.

**D5 explicado.** Cada barra do gráfico mensal é o total do mês. A divisão pinta a barra em partes, e o seletor escolhe o
que as partes querem dizer — o total não muda. Jun/2026 (R$ 257,58), por exemplo:

| Dividir por | Partes da barra de jun/2026 |
|---|---|
| Tipo de provento | Rendimentos R$ 136,53 · JCP R$ 103,33 · Dividendos R$ 17,72 |
| Tipo de ativo | FIIs R$ 135,74 · Ações R$ 121,84 |
| Situação | Recebido R$ 257,58 (só faz diferença no mês corrente e nos futuros — dez/2026: R$ 94,65 a receber) |

Como a Situação só muda as barras de hoje em diante, a proposta revista é mostrar o **a receber sempre** em tom mais
claro (hachurado) em qualquer divisão, e o seletor escolher só a divisão:

| Opção | O que é |
|---|---|
| **a** | seletor **Tipo de provento / Tipo de ativo / Sem divisão** (padrão Tipo de provento), a receber sempre mais claro — **recomendação** |
| b | sem seletor: barra de uma cor só, a receber mais claro; a divisão aparece só no balão ao passar o mouse |
| c | sem seletor, sempre dividida por Tipo de provento |

### 2.4 Etapas do plano

| # | Etapa | Conteúdo |
|---|---|---|
| P1 | Agregação | `utils/dividendPeriods.ts` (C1–C8) e, se D9 = a, Vitest com os casos: sem pagamento recebido/a receber, troca de base e de data, meses vazios, YTD, 12 meses, centavos |
| P2 | Aba e controles | aba, ordem/padrão (D2), controles e filtros, links do Dashboard |
| P3 | Gráficos | blocos C e D, balões, tema claro/escuro, clique no mês |
| P4 | Tabela e detalhe | bloco E, detalhe do mês (D6), copiar (D7), linha dos a receber sem data |
| P5 | Fechamento | overrides de `index.css`, responsivo, documentação (`Claude.md`, `README.md`, `PENDENCIAS.md`, este plano) e o `npm test` em "Como rodar" |

Testing: `tsc -b`, lint, testes (D9), rebuild do container `activest-web`, conferência dos números de 2.2 contra o
banco e passada no navegador. Review e Commit (mensagem sugerida) em seguida.

### 2.5 Quem faz

| Atividade | Quem |
|---|---|
| Confirmar o entendimento de D5 e aprovar a Implementation | Guilherme |
| Criar a branch `TRACK_01-dividendos_por_periodo` a partir da `develop` | Guilherme |
| P1–P5, Testing (build, lint, testes, conferência com o banco) e Review | Claude |
| Conferência final no navegador logado (caminho 3) e comparação com Investidor10/Nubank | Guilherme |
| Commit e PR para a `develop` | Guilherme (sugestão de mensagem pelo Claude na etapa 7) |

### 2.6 Andamento

| Data | Etapa | Situação |
|---|---|---|
| 27/10/2026 | Discovery, Impact, Planning | feito — plano apresentado |
| 27/10/2026 | Respostas do Guilherme | cards retirados; D1, D2, D6–D10 decididas, D11 não se aplica; D3.1, D4.1 e D5 em aberto; pendência 40 (testes de front) registrada |
| 27/10/2026 | Respostas do Guilherme (2) | D3 = só líquido, D4 = só pagamento, D5 = situação + tipo de ativo com FIIs na base; entendimento de D5 aguardando confirmação |
| 27/10/2026 | Aprovação | D5 confirmada (também no gráfico anual); "pode executar" |
| 27/10/2026 | P1–P5 | `utils/dividendPeriods.ts` + 17 testes (Vitest 2.1, `npm test`); `components/dividends/` (aba, gráficos, tabela, detalhe, cores, filtros num hook só — `useDividendFilters`, usado também pelo Histórico e pelo A Receber); `DividendsPage` com três abas; Dashboard "Totais" → `?tab=received`; C9–C11; documentação |
| 27/10/2026 | Testing | `tsc -b`, `npm run lint` (0 erro, 0 aviso), `npm test` (17 verdes), `npm run build`; no navegador logado (dev, escuro): 2025 R$ 1.230,60, 2026 R$ 2.036,41, jun/2026 R$ 257,58 (bruto R$ 276,96, 20 proventos de 13 ativos), total R$ 3.360,79, média 12M R$ 192,51 — iguais ao SQL; Listar do detalhe abre o Histórico filtrado; tabela legível no claro. Achados corrigidos: o Recharts não enxerga `<Bar>` dentro de componente próprio (barras não apareciam), janela 12M longa demais (C10), tabela sem caber (valores sem "R$"), eixo Y quebrando linha |
| 27/10/2026, noite | Ajustes R2–R5 | cor do cadastro, Por mês/Por ano, barras mais grossas, "Incluir a receber", opções do seletor de ano; `tsc -b`, lint e 17 testes verdes; conferido no navegador nos dois temas (opções do seletor: fundo #111111 e texto branco no escuro, branco e #1a1a1a no claro); sem o a receber, 2026 = R$ 1.900,28 e total R$ 3.143,95 |
| 27/10/2026, noite | Ajuste R6 | detalhe do mês com Tipo Ativo, ordenação e sempre visível; `tsc -b`, lint e 17 testes verdes; conferido no navegador (vazio com a instrução; jun/2026 ordenado por Tipo Ativo, total R$ 257,58) |
| 27/10/2026 | Review | Listar do detalhe desabilitado para provento sem tipo de ativo (como na lista); celular: a página não rola na horizontal, mas o menu lateral do app ocupa a tela toda em 375 px — comportamento de todas as telas, fora deste escopo |
| 27/10/2026 | Encerramento | Review finalizada pelo Guilherme, escopo concluído; Commit: alterações conferidas e mensagem sugerida pelo Claude |
