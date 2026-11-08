# Activest — Plano de calendários previsíveis nos gráficos

> Fase: **Implementado e validado em 07/11/2026** (etapas 4 a 6 feitas; falta o commit, que é do Guilherme).
> Origem: ESCOPO "Calendarios previsiveis nos graficos Activest" (07/11/2026). A descrição é a resposta à dúvida 1 do
> mesmo dia (anexos: Rentabilidade acumulada do Dashboard com 21/11, 05/12, 19/12…; Patrimônio da meta com 07/2024,
> 09/2024, 11/2024, 12/2024, 02/2025…), aceita pelo Guilherme como "exatamente o que quero".
> Dor: o eixo X dos gráficos de linha mostra datas que parecem aleatórias e mudam com a largura da tela.

---

## 0. O que o Guilherme pediu

Eixo X com marcos de **calendário** (semana, quinzena, mês, trimestre, semestre/ano), escolhidos pelo tamanho do
período mostrado, em vez das datas que o Recharts escolhe sozinho.

---

## D. Discovery

### D.1 Por que as datas parecem aleatórias

- Nos gráficos por pregão, o `XAxis` usa a data como **categoria** (`dataKey="date"`) com `minTickGap={32}`: o Recharts
  mostra um ponto a cada N para não encavalar rótulos. O rótulo é o pregão que caiu naquela posição — 21/11, 05/12,
  19/12 (a cada ~10 pregões) — e muda com a largura e com o período.
- No Patrimônio da meta, o eixo é de **tempo** (`scale="time"`), com os marcos automáticos do d3 cortados pelo
  `minTickGap={40}`: sai 11/2024, 12/2024, 02/2025, 04/2025….
- Os formatos também variam: `dd/mm` ou `mm/aa` na Rentabilidade (pela densidade), `mm/aaaa` em Índices e Ativos
  (período > 400 dias), `mm/aaaa` no Patrimônio da meta, `dd/mm` ou `mm/aaaa` em Moedas.

### D.2 Gráficos de linha com eixo de datas

| # | Gráfico | Arquivo | Dados | Eixo hoje |
|---|---|---|---|---|
| G-1 | Rentabilidade acumulada (Dashboard e tela Rentabilidade) | `components/performance/PerformanceCharts.tsx` (`CumulativeChart`), usado por `CumulativeCard` | pontos por pregão até 400 dias; acima, o último pregão de cada semana (até 5 anos) ou de cada mês (`PerformanceService.density/sample`) | categoria, `minTickGap 32` |
| G-2 | Patrimônio (tela Rentabilidade) | `PerformanceCharts.tsx` (`WealthChart`) | idem | categoria, `minTickGap 32` |
| G-3 | Índices (linha/área; barras nos mensais) | `components/indices/IndexChartCard.tsx` | diário (SELIC, CDI, IBOV…) ou mensal (IPCA, IGPM, INPC); períodos 1D–MAX | categoria, `minTickGap 32` |
| G-4 | Cotação na tela de Ativos | `components/issuer/PriceChart.tsx` | fechamento diário; 1D, 7D, 1M, 6M, YTD, 1A, 5A, MAX | categoria, `minTickGap 32` |
| G-5 | Câmbio (Moedas) | `pages/CurrenciesPage.tsx` | diário; 1A, 5A, MAX | categoria, `minTickGap 40` |
| G-6 | Patrimônio/histórico da meta | `pages/GoalDetailPage.tsx` (~linha 363) | datas como número (`t`), com projeção e fotografias | tempo, `minTickGap 40` |

**Fora do escopo** (já são por período, com rótulo próprio): barras por mês/ano da Rentabilidade (`PeriodicChart`),
Evolução dos Proventos (`EvolutionCharts`), histórico de indicadores (`IndicatorHistoryModal`, por trimestre/ano),
aportes por período da meta (barras por `period`).

### D.3 Restrições encontradas

- No eixo de **categoria**, o marco tem que ser um ponto que existe nos dados: o primeiro pregão (ou o primeiro ponto
  amostrado) de cada mês/semana/quinzena. Com densidade semanal, o "primeiro ponto de novembro" é a sexta da primeira
  semana (ex.: 07/11) — por isso, do mensal para cima, o rótulo mostra só o **mês**, nunca o dia.
- No eixo de **tempo** (G-6), o marco cai exatamente no dia 1º.
- A largura dos cartões varia (Dashboard, tela cheia, celular): sem medir a largura, um marco mensal num período de
  5 anos daria 60 rótulos encavalados.
- Não há hoje função comum de eixo nem hook de largura (`hooks/` tem só useAvatarSrc, useGoogleSignIn,
  useIntegrationSources, useSortable).

---

## I. Impact

| Componente | Mudança | Risco |
|---|---|---|
| `utils/calendarTicks.ts` (novo) | escolhe a granularidade e devolve os marcos e o formato do rótulo | baixo: função pura, testada no Vitest |
| `hooks/useElementWidth.ts` (novo) | largura do cartão (ResizeObserver) para limitar o número de marcos | baixo |
| G-1 a G-6 | `XAxis` passa a receber `ticks` + `interval={0}` + `tickFormatter` da função comum; sai o `minTickGap` | médio: são gráficos muito vistos; tooltip, legenda e linhas não mudam |
| Backend | nenhum | — |
| Dados/densidade da série | nenhuma (a amostragem semanal/mensal da Rentabilidade continua) | — |

---

## 2. Planning

### 2.1 Solução

1. `calendarTicks(dates, { maxTicks })` (datas ISO em ordem, ou números no eixo de tempo):
   - mede o período (primeira → última data) e escolhe a granularidade pela tabela 2.2;
   - gera as fronteiras do calendário (segunda-feira; dias 1 e 16; dia 1º do mês; jan/abr/jul/out; jan/jul; jan) e, no
     eixo de categoria, pega o **primeiro ponto da série em ou depois** de cada fronteira (sem repetir);
   - se os marcos passarem de `maxTicks`, sobe um nível (semanal → quinzenal → mensal → trimestral → semestral →
     anual) até caber;
   - devolve `{ ticks, granularity, format(date) }`.
2. `useElementWidth(ref)` dá a largura do cartão; `maxTicks = floor(largura ÷ ~70 px)`.
3. Cada gráfico G-1 a G-6 usa a função: `ticks={ticks}`, `interval={0}`, `tickFormatter={format}`.
4. Tooltip continua mostrando a data exata do ponto.

### 2.2 Granularidade proposta (aceita pelo Guilherme na dúvida 1)

| Período mostrado | Marcos | Rótulo |
|---|---|---|
| até 7 dias (1D, 7D) | cada pregão | `03/11` |
| até 1 mês | semanal (segunda-feira; no eixo de pregão, o 1º pregão da semana) | `03/11` |
| até 3 meses | quinzenal (dias 1 e 16) | `01/11`, `16/11` |
| até 1 ano | mensal (dia 1) | `nov/25` |
| até 3 anos | trimestral (jan, abr, jul, out) | `jan/26` |
| mais de 3 anos | semestral (jan, jul), subindo para anual se não couber | `jan/24` / `2024` |

### 2.3 Critérios de aceitação

- C1. Rentabilidade acumulada do Dashboard (12 meses): rótulos mensais (`dez/25`, `jan/26`, …), um por mês.
- C2. Patrimônio da meta (desde 06/2024): rótulos trimestrais ou semestrais regulares, sem pular meses ao acaso.
- C3. Índices, Ativos e Moedas seguem a tabela em todos os períodos (1D a MAX); índices mensais nunca mostram dia.
- C4. Estreitando a tela, a granularidade sobe um nível em vez de sumirem rótulos ao acaso; nada encavala.
- C5. Tooltips, legendas, linhas e barras iguais a hoje; dois temas ok.
- C6. `calendarTicks` com testes no Vitest (fronteiras, primeiro ponto da série, troca de nível, fins de semana e
  virada de ano); `tsc` e Vitest verdes.

### 2.4 Decisões (respondidas em 07/11/2026)

D1–D4: **aprovadas pela recomendação** (Guilherme, 07/11/2026: "de acordo com todas recomendações").


| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| D1 | Formato do mês | (a) `nov/25` (mês abreviado em português); (b) `11/25`; (c) `11/2025` | **(a)** — não se confunde com dia/mês |
| D2 | Mostrar sempre a primeira e a última data do período no eixo, além dos marcos | (a) não, só marcos de calendário (a data exata fica no tooltip e no rodapé "Período disponível"); (b) sim | **(a)** — o objetivo é a previsibilidade; as pontas soltas voltariam às datas "aleatórias" |
| D3 | Linhas verticais de grade nos marcos | (a) não, como hoje; (b) sim, tracejadas e discretas | **(a)** |
| D4 | Eixo anual (períodos muito longos) | (a) `2024`; (b) `jan/24` | **(a)** |

### 2.5 Etapas do plano

| Etapa | Conteúdo | Validação |
|---|---|---|
| E1 | `utils/calendarTicks.ts` + `calendarTicks.test.ts`; `hooks/useElementWidth.ts` | Vitest |
| E2 | Rentabilidade: G-1 e G-2 | conferência no Dashboard e na tela Rentabilidade (períodos 1M, 6M, 1A, MAX) |
| E3 | G-3 Índices, G-4 Ativos, G-5 Moedas | conferência em todos os períodos |
| E4 | G-6 Patrimônio da meta (eixo de tempo) | conferência |
| E5 | Docs: andamento do plano | — |

### 2.6 Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| Decisões D1–D4 | recomendou | decidiu (07/11) |
| E1–E5 | implementa, testa e confere nos dois temas | aprova a execução |
| Conferência final | — | confere |
| Commit / PR | sugere a mensagem | faz |

---

## 3. Andamento

- 07/11/2026 — Discovery, Impact e Planning feitos; D1–D4 aguardando resposta.
- 07/11/2026 — D1–D4 aprovadas pela recomendação. Aguardando ordem de execução.
- 07/11/2026 — Guilherme mandou implementar. E1–E5 feitas: `utils/calendarTicks.ts` (+14 testes; `minGranularity`
  MONTH nos índices mensais), `hooks/useElementWidth.ts` (ref de callback, para contêiner que aparece depois do
  carregamento) e `hooks/useCalendarAxis.ts`; G-1 a G-6 sem `minTickGap`, com `ticks` + `interval={0}`.
  Testing: Vitest 179/179; `tsc` sem erro novo (o `TRIGGER_HINT` de `TasksPage.tsx` já existia). Conferido no navegador:
  Dashboard 12M `dez/25 … nov/26` (estreito: `jan/26 abr/26 jul/26 out/26`); Rentabilidade 1M `13/10 19/10 26/10 03/11`
  (feriados → primeiro pregão), 6M mensal, MAX trimestral/semestral; Índices mensais; Ativos 7D por pregão, 5A
  semestral, MAX anual (`2011 … 2026`); Moedas 1A mensal; Patrimônio da meta `jul/24 out/24 … out/26`.
