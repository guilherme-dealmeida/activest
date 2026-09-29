# PrimeVest — Plano da Tela de Emissoras e Indicadores

> Fase: **implementado em 28/09/2026** (T1–T9) — andamento na seção 8
> Consome a F9 do `PLANO_EMPRESAS_BALANCOS.md` (indicadores e emissor já expostos pela app-api).
> Branch de trabalho: `TRACK_01-ajustes_crud_inicial`. **Todas as decisões tomadas em 28/09/2026** (seção 7).
> Execução autorizada e concluída em 28/09/2026.

---

## 0. Resumo

**Objetivo:** uma tela que, a partir de um **ticker** (PETR4, HGLG11…), mostre o emissor — companhia aberta ou
FII —, o gráfico de cotação, os indicadores atuais do ticker com **sparkline** e **botão de histórico** (abre em
modal) e os demonstrativos (série trimestral da companhia / informe mensal do FII).

**Rota:** `/emissoras/:ticker` (e `/emissoras`, só com a busca). Item **"Emissoras" no menu, depois de "Preço
Teto"**. Ticker clicável para a tela em Carteira, Dividendos e Preço Teto.

**Backend:** indicadores, emissor e demonstrativos já existem (F9, app-api 8080). Faltam: cotação histórica
(o endpoint `GET /assets/{ticker}/history` existe mas é um esboço que devolve vazio), histórico de todos os
indicadores numa chamada (para as sparklines), `hasLogo`/site/situação no emissor, filtro de busca e o
`issuerId` nas telas que vão linkar (seção 3).

---

## 1. O que já existe (conferido no código em 28/09)

| Endpoint (app-api) | Devolve | Uso na tela |
|---|---|---|
| `GET /api/v1/assets/{ticker}/indicators` | `AssetIndicatorsDTO`: `ticker, issuerId, kind (COMPANY/FII), price, basePeriodEnd, indicators[{indicator, unit, value, note}]` | **ponto de entrada**: resolve ticker → emissor e traz os cartões |
| `GET /api/v1/issuers/{id}` | `IssuerDTO`: CNPJ, código CVM, razão social, nome de pregão, setor/subsetor/segmento B3, segmento curado (FII), segmento de listagem, mês de fim do exercício, administrador, `tickers[{ticker, shareClass, active}]` | cabeçalho e cadastro |
| `GET /api/v1/assets/{ticker}/indicators/{indicador}/history?period=YEAR\|QUARTER` | `IndicatorHistoryDTO`: `unit, current (Atual), companyAverage, points[{label, periodEnd, value, note}]` | modal de histórico |
| `GET /api/v1/assets/{ticker}/history?startDate=&endDate=` | **esboço: devolve 200 vazio** (`AssetController`) | substituído pelo B5 |
| `asset_price_history` | fechamento diário: COTAHIST desde 2010-01-04 (2,23 mi pregões) + BRAPI no dia a dia | gráfico de cotação |
| `GET /api/v1/issuers/{id}/quarters` · `/months` | série trimestral (companhia) · informe mensal (FII) | seção de demonstrativos (2.6) |

Indicadores (`Indicator`):

| Companhia | FII | Unidade |
|---|---|---|
| LPA, VPA | VP por cota | BRL (4 casas) |
| P/L, P/VP | P/VP | RATIO (2 casas) |
| ROE, Payout, DY | DY, % Imóveis, % Papel, Alavancagem | PERCENT (em pontos: 37,44) |
| — | Cotistas | COUNT |

**Todos têm histórico** na API hoje (anual e trimestral, Atual e média da empresa). Ainda assim o front guarda
um conjunto `HISTORICO_DISPONIVEL` por indicador: se um dia entrar indicador sem série, ele simplesmente não
ganha botão nem sparkline.

Comportamentos da API que a tela precisa respeitar:

- **Nulo tem motivo** (`note`): "sem informe mensal" (VGRH11), quantidade de ações em revisão (AZTE3), lucro
  negativo no P/L… A tela mostra "—" com o motivo num `InfoTooltip`, nunca 0.
- **Zero é dado**: DY 0 de quem não distribuiu (APXR11, PETR4 2015–2017) aparece como 0,00%.
- **Ticker sem emissor** (ETF, BDR, Tesouro, cripto, FSPE11F…) → 404 "Ativo sem emissor…"; ticker inexistente →
  404 "Ativo não encontrado". A tela distingue os dois (estado vazio, seção 2.5).
- Fracionário (PETR4F) usa preço/proventos do lote padrão; unit (KLBN11) já vem multiplicada — nada a fazer no front.
- Primeira chamada de histórico ~1,5 s (aquecimento), depois 20–70 ms.

---

## 2. A tela

### 2.1 Estrutura

```
┌───────────────────────────────────────────────────────────────────────────┐
│ [🔍 Buscar ticker…                                  ]                      │
├───────────────────────────────────────────────────────────────────────────┤
│ (logo) PETR4 · Petrobras                      R$ 47,99                     │
│        PETROLEO BRASILEIRO S.A. PETROBRAS     base: balanço 30/06/2026     │
│        [Empresa] [Nível 2]    Outros tickers: (PETR3)                      │
├───────────────────────────────────────────────────────────────────────────┤
│ COTAÇÃO  [1D] [7D] [1M] [6M] [YTD] [1A] [5A] [MAX]  [de __/__ até __/__]  │
│  área/linha do fechamento  ·  variação no período  ·  mín / máx            │
├───────────────────────────────────────────────────────────────────────────┤
│ INDICADORES                                                                │
│ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐                │
│ │ P/L      📈│ │ P/VP     📈│ │ DY       📈│ │ ROE      📈│                │
│ │ 4,64       │ │ 1,29       │ │ 7,67%      │ │ 30,31%     │                │
│ │ ╱╲_╱‾╲_╱   │ │ _╱‾╲__╱    │ │ _╱╲_╱‾╲    │ │ ╲_╱‾‾╲_╱   │  ← sparkline   │
│ └────────────┘ └────────────┘ └────────────┘ └────────────┘                │
│ ┌────────────┐ ┌────────────┐ ┌────────────┐                               │
│ │ Payout   📈│ │ LPA      📈│ │ VPA      📈│                               │
│ └────────────┘ └────────────┘ └────────────┘                               │
├───────────────────────────────────────────────────────────────────────────┤
│ CADASTRO                                                                   │
│ CNPJ · CVM · situação · site · setor › subsetor › segmento · exercício     │
├───────────────────────────────────────────────────────────────────────────┤
│ DEMONSTRATIVOS                       [Trimestre | 12 meses]  [8 | todos]   │
│ tabela: 2T26 | 1T26 | 4T25 | … × receita, EBIT, lucro, FCO, PL, dívida…    │
└───────────────────────────────────────────────────────────────────────────┘

Modal do histórico (botão 📈 ou clique na sparkline):
┌─ DY · PETR4 ───────────────────────── [Anual|Trimestral]  ‹ ›  [×] ─┐
│ Atual 7,67%   ·   Média da empresa 17,75%                            │
│ gráfico de barras 2010 … 2025 + Atual (dourado), média tracejada     │
│ tabela: 2010 | 2011 | … | 2025 | Atual | Média                       │
└──────────────────────────────────────────────────────────────────────┘
```

Ordem das seções: cabeçalho, cotação, indicadores, cadastro, demonstrativos (o que se consulta mais vem primeiro;
cadastro é referência).

### 2.2 Cabeçalho

- `AssetLogo` (com `hasLogo` — B1), ticker, nome de pregão, razão social, badge Empresa/FII e segmento de listagem.
- Preço atual e **data-base** dos indicadores (`basePeriodEnd`: "balanço de 30/06/2026" / "informe de 08/2026").
- **Outros tickers do emissor** como chips clicáveis (PETR3 ↔ PETR4; TRPL11 ↔ TRPL15). Só ativos e sem
  fracionário (`/\d+F$/`); o chip navega para `/emissoras/{ticker}` (a URL é o estado — dá para voltar e
  compartilhar).

### 2.3 Cotação (D7)

- **Atalhos:** 1D, 7D, 1M, 6M, YTD, **1A (padrão)**, 5A e **MAX**.
- **1D:** hoje só há fechamento diário, então o 1D mostra o fechamento anterior → o último fechamento (a variação
  do dia) e o resumo diz "só fechamento". Quando houver preço durante o pregão (escopo futuro), o mesmo atalho passa
  a mostrar os pontos do dia: o B5 já nasce com `interval` aceitando `INTRADAY`, e o front desenha o que vier — sem
  mudar a tela.
- **7D:** últimos 7 dias corridos, diário (até 5 pregões). MAX vai até o primeiro pregão gravado do ticker — **2010-01-04 no
  máximo**, se o ativo tiver essa idade; um ativo listado em 2021 mostra desde 2021. Atalho maior que a vida do
  ativo continua clicável e mostra o que existe (sem espaço vazio no início).
- **Período específico:** campos "de / até" (datas), limitados ao intervalo existente; escolher datas desmarca o
  atalho.
- A URL guarda o período (`?periodo=5A` ou `?de=2020-03-01&ate=2020-12-31`) — voltar do navegador mantém.
- Gráfico de área (Recharts), linha dourada; tooltip com data e fechamento; resumo acima: variação no período,
  mínimo e máximo. Troca de ticker mantém o período.
- **Densidade** decidida no servidor (B5): intradiário no 1D quando existir, diário até 1 ano, semanal até 5 anos, mensal acima (último fechamento
  de cada semana/mês) — MAX tem ~4.100 pregões, não vale mandar todos. O período específico usa a mesma regra
  pelo tamanho do intervalo.
- Fracionário mostra o lote padrão (mesma regra dos indicadores).
- **Ajuste por desdobramento/grupamento/bonificação — sempre (D9):** a COTAHIST é o preço *sem ajuste* (a SBSP3,
  desdobramento 1:5 em 04/2026, cairia 80% num dia). Todo fechamento anterior a um evento de
  `asset_stock_events` é dividido pelo fator do evento (desdobramento 1:5 → ÷5; grupamento 10:1 → ×10;
  bonificação de 10% → ÷1,1), acumulando os eventos até hoje — a mesma lógica que o `IndicatorService` já usa para
  a quantidade de ações, extraída para uma regra do core. O último fechamento fica igual ao da tela. **Sem** ajuste
  por proventos (não é série de retorno total). O tooltip e o resumo indicam "preço ajustado por eventos
  societários".

### 2.4 Indicadores — sparkline e histórico em modal (D1, D2, D3)

- **Cartão por indicador**: nome curto (P/L), valor formatado pela unidade, `InfoTooltip` com a fórmula
  (seção 5.1/5.2 do plano de balanços) e, quando nulo, o motivo.
- **Sparkline** no rodapé do cartão: pontos **anuais** + Atual (até 2010), linha sem eixo; nulo = interrupção na
  linha, zero = ponto no zero; o último ponto (Atual) marcado em dourado. Vem do **B4** (todos os históricos
  anuais do ticker numa chamada, carregada junto com a página; os cartões aparecem antes, a sparkline preenche
  quando chegar).
- **Botão de histórico** (ícone de gráfico) no canto do cartão — clicar na sparkline faz o mesmo. Abre o
  **modal** (componente `Modal` existente):
  - título "DY · PETR4", Atual e média da empresa em destaque;
  - **Anual | Trimestral** (`period`); o anual já veio do B4, o trimestral é pedido ao endpoint individual na
    primeira vez (cache do react-query por ticker, indicador e período);
  - **gráfico de barras**: Atual em dourado (`#c9a84c`), **média tracejada** com rótulo; nulo = barra ausente
    com "—" no eixo e o motivo no tooltip; zero = barra no zero; negativos abaixo do eixo; no trimestral, rolagem
    horizontal acima de ~40 barras;
  - **tabela** compacta embaixo (rótulo | valor): anos, Atual e Média, como na referência "Histórico de Indicadores";
  - setas **‹ ›** (e ← → no teclado) passam para o indicador anterior/seguinte sem fechar; `Esc` fecha.
- Ordem dos cartões — companhia: P/L, P/VP, DY, ROE, Payout, LPA, VPA; FII: P/VP, DY, VP por cota, Cotistas,
  % Imóveis, % Papel, Alavancagem.

### 2.5 Cadastro (D6)

Companhia: CNPJ (formatado), código CVM, **situação do registro** (ATIVO/CANCELADA — badge), **site** (link
externo, nova aba), setor › subsetor › segmento B3, segmento de listagem, fim do exercício.
FII: CNPJ, situação, site, segmento (curado), administrador. Campo nulo não aparece.

### 2.6 Demonstrativos (D4)

**Companhia** (`/issuers/{id}/quarters`) — tabela com períodos nas colunas (mais recente à esquerda) e linhas:
receita, EBIT, lucro líquido, lucro da controladora, FCO, dividendos + JCP declarados, PL, PL da controladora,
ativo total, caixa, dívida bruta, ações em circulação.
- Alternância **Trimestre | 12 meses** (colunas `_q` × `_ttm`; as de posição — PL, caixa, dívida, ações — são as
  mesmas nas duas).
- Padrão: **últimos 8 trimestres**; "ver todos" abre a série inteira (desde 2010) com rolagem horizontal e a
  primeira coluna fixa.
- Valores em R$ compactos (R$ 110,13 bi / R$ 845,2 mi), valor exato no tooltip; nulo = "—" (ex.: 4T sem o ITR de
  9 meses).

**FII** (`/issuers/{id}/months`) — meses nas colunas, linhas: PL, VP por cota, cotas emitidas, cotistas, DY do mês,
amortização do mês, taxa de administração do mês, imóveis, imóveis via SPE, recebíveis, cotas de FII, liquidez,
passivo, rendimentos a distribuir.
- Padrão: **últimos 12 meses**; "ver todos" desde 09/2016. Taxas do mês em % (a API manda fração: 0,0068 → 0,68%).

Carregada só quando a seção entra na tela (ou ao abrir, se for o caso de rolar direto) — é a chamada mais pesada.

### 2.7 Busca e estados

- Busca no topo, autocomplete com a lógica do `TickerAutocomplete`, sem tipo obrigatório e só com ativos ligados a
  emissor (B3). Enter / escolha → `/emissoras/{ticker}`.
- `/emissoras` sem ticker: só a busca e um texto curto.
- 404 "Ativo não encontrado" → "Não achamos o ticker X". 404 "sem emissor" → "X não tem emissor cadastrado —
  indicadores só para ações e FIIs". Outros erros → `apiError` padrão com toast.
- Cada seção carrega e falha sozinha (cotação sem dado não derruba indicadores).
- Tema: dark/light existente (tokens do Tailwind; dourado da marca como destaque).

### 2.8 Pontos de entrada (D5)

Ticker clicável levando a `/emissoras/{ticker}` em **Carteira, Dividendos e Preço Teto** — só quando o ativo tem
emissor, isto é, `assets.issuer_id` preenchido; nos outros o ticker fica como texto. O front não enxerga o
`issuer_id` hoje (os DTOs dessas telas não o trazem), por isso o B6. Clique no ticker não dispara a ação da linha
(a Carteira já usa clique na linha para ir às transações).

---

## 3. Ajustes no backend (app-api; sem migration)

| # | Ajuste | Por quê |
|---|---|---|
| B1 | `IssuerDTO.Ticker` ganha `hasLogo` (de `AssetLogos`) | o `AssetLogo` exige |
| B2 | `IssuerDTO` ganha `website` e `registrationStatus` (já estão em `issuers`) | D6 |
| B3 | `GET /api/v1/assets/?query=&withIssuer=true` | a busca da tela só oferecer ações e FIIs com emissor |
| B4 | `GET /api/v1/assets/{ticker}/indicators/history?period=YEAR` — todos os históricos do ticker numa chamada (`List<IndicatorHistoryDTO>`), lendo trimestres/informes, preços e proventos **uma vez** | sparklines (D2): 7 chamadas separadas releriam tudo 7 vezes |
| B5 | **Cotação:** substituir o esboço `GET /assets/{ticker}/history` por `GET /api/v1/assets/{ticker}/prices?range=1D\|7D\|1M\|6M\|YTD\|1A\|5A\|MAX` ou `?from=&to=` → `{ticker, firstDate, lastDate, interval (INTRADAY/DAY/WEEK/MONTH), adjusted, points[{time, close}]}` (`time` = data, ou data e hora no intradiário) | D7. `firstDate`/`lastDate` limitam os campos de data no front; `interval` pela regra de 2.3; fracionário → lote padrão; sempre ajustado por eventos societários (D9; regra no core, reaproveitada pelo `IndicatorService`) |
| B6 | `issuerId` (o `assets.issuer_id`, nulo sem emissor) em `PortfolioAssetDetailDTO`, `DividendResponseDTO` e `CeilingPriceDTO` | links (D5): o front testa `issuerId != null`. Nome `issuerId`, não `issuer`: o `PortfolioAssetDetailDTO` já tem `issuer` (texto), que é o emissor da **nota** de renda fixa (o banco do CDB) — outra coisa |

Testes: `IndicadoresIT` (B4 igual aos históricos individuais), teste do `IssuerController` (B1, B2), busca com
`withIssuer` (B3), `PrecoHistoricoIT` para B5 (1D com dois fechamentos, 7D, intervalo pela janela, MAX limitado ao primeiro pregão,
fracionário, SBSP3 ajustada sem degrau, evento acumulado (dois eventos no período), grupamento, bonificação, ticker sem preço), DTOs da carteira/proventos/preço teto com `issuerId`.

## 4. Front — arquivos

| Arquivo | Conteúdo |
|---|---|
| `types/index.ts` | `IssuerKind`, `IndicatorCode`, `IndicatorUnit`, `AssetIndicators`, `IndicatorHistory`, `IndicatorPoint`, `Issuer`, `IssuerTicker`, `CompanyQuarter`, `FiiMonth`, `PriceSeries`; `issuerId` nos tipos da carteira, proventos e preço teto |
| `api/indicators.ts` | `current`, `historyAll` (B4), `history` (trimestral) |
| `api/issuers.ts` | `get`, `quarters`, `months` |
| `api/assets.ts` | `prices(ticker, range \| {from, to})`; `getAll(…, withIssuer)` |
| `utils/indicators.ts` | metadados por indicador (rótulo, fórmula, ordem, `HISTORICO_DISPONIVEL`); `formatIndicator(value, unit)`: PERCENT "37,44%", RATIO "4,64", BRL "R$ 8,54" no cartão / 4 casas na tabela, COUNT "608.345" |
| `utils/format.ts` | `formatCompactCurrency` (bi/mi/mil) para demonstrativos |
| `pages/IssuerPage.tsx` | página, rota, período da cotação na URL, estados |
| `components/issuer/IssuerSearch.tsx` | busca |
| `components/issuer/IssuerHeader.tsx` | cabeçalho + chips |
| `components/issuer/PriceChart.tsx` | atalhos, datas, gráfico, resumo |
| `components/issuer/IndicatorCard.tsx` | cartão + `Sparkline` |
| `components/issuer/IndicatorHistoryModal.tsx` | modal: alternância, ‹ ›, gráfico, tabela |
| `components/issuer/IssuerRegistry.tsx` | cadastro |
| `components/issuer/FinancialStatements.tsx` | demonstrativos (companhia e FII) |
| `App.tsx`, `Sidebar.tsx` | rota e item "Emissoras" (ícone `Building2`) depois de "Preço Teto" |
| `PortfolioPage`, `DividendsPage`, `CeilingPricesPage` | ticker como `Link` quando `issuerId != null` |

Gráficos seguem a skill de visualização (paleta, eixos, tooltip) e as cores da marca.

## 5. Fases

| Fase | Entrega | Depende |
|---|---|---|
| **T1** | Backend: B1–B6 e testes (bateria completa verde) | — |
| **T2** | Tipos, `api/`, `utils/indicators.ts`, formatação compacta | T1 |
| **T3** | Página: rota, menu, busca, cabeçalho, cadastro, estados vazios/erro | T2 |
| **T4** | Cotação: atalhos, período específico, URL, gráfico e resumo | T3 |
| **T5** | Cartões de indicadores, sparklines e modal de histórico | T3 |
| **T6** | Demonstrativos (companhia e FII) | T3 |
| **T7** | Links de ticker em Carteira, Dividendos e Preço Teto | T1, T3 |
| **T8** | Validação (última etapa): build e lint do front, bateria do backend, `docker compose up`, conferência no navegador do app logado com os casos da seção 6 | T1–T7 |
| **T9** | `Claude.md`, `PENDENCIAS.txt` (11.6 resolvido pelo B5; 18.x), andamento neste plano | T8 |

## 6. Casos de conferência (T8)

| Ticker | O que conferir |
|---|---|
| PETR4 | P/L 4,64 · P/VP 1,29 · ROE 30,31% · DY 7,67% (atual); histórico DY 2015–2017 = 0, 2022 = 68,4%; payout 2025 37,44%; chip PETR3 |
| HGLG11 | P/VP 0,89 · DY 9,02% · VP 165,95 · 608.345 cotistas · imóveis 97,8% · alavancagem 14,5%; histórico a partir de 2017 |
| KLBN11 | unit: LPA/VPA multiplicados; chips KLBN3/KLBN4 |
| SBSP3 | desdobramento 04/2026: LPA 2,29, P/L 11,75 |
| AZTE3 | nulos com motivo (quantidade em revisão) — "—" + tooltip |
| VGRH11 | DY 0; demais nulos "sem informe mensal" |
| APXR11 | DY 0 como valor, não como falta |
| PETR4F | abre com os números do lote padrão |
| BOVA11 / XPTO9 | estado "sem emissor" / "não encontrado" |
| PETR4 — cotação | abre em 1A; 1D = fechamento de 24/09 → 25/09; 7D diário; MAX começa em 04/01/2010 (37,32) e termina no último fechamento (47,99 em 25/09); 1A diário, 5A semanal, MAX mensal; período específico 01/03/2020–31/12/2020 |
| Ativo listado depois de 2010 | MAX começa no primeiro pregão dele |
| SBSP3 — cotação | sem degrau de −80% em 04/2026; último fechamento igual ao preço atual |
| PETR4 — demonstrativos | 2025 12 meses: lucro da controladora 110,13 bi; 4T24 −17,04 bi |
| HGLG11 — demonstrativos | 08/2026: PL 7,57 bi, VP 165,95, 608.345 cotistas, DY do mês 0,70% |
| Modal | ‹ › percorre os 7 indicadores; Esc fecha; trimestral carrega na primeira vez |
| Links | Carteira, Dividendos e Preço Teto abrem a tela; ETF/Tesouro/cripto sem link |
| Tema claro e escuro, largura de celular | cartões em 1, 2 e 4 colunas; modal e tabelas rolam sem quebrar a página |

---

## 7. Decisões

Tomadas em 28/09/2026 com o Guilherme:

| # | Pergunta | Decisão |
|---|---|---|
| D1 | Onde o histórico abre | **Modal** |
| D2 | Tendência no cartão | **Botão de histórico + sparkline** (exige B4) |
| D3 | Ordem dos cartões | **A da seção 2.4** |
| D4 | Demonstrativos | **Entram** (2.6) |
| D5 | Links de ticker nas outras telas | **Sim** — Carteira, Dividendos, Preço Teto (B6, T7) |
| D6 | Site e situação do registro | **Entram** (B2) |
| D7 | Gráfico de cotação | **Entra**, com períodos selecionáveis (1D, 7D, 1M, 6M, YTD, 1A padrão, 5A, MAX + de/até); 1D preparado para o intradiário futuro; até 2010 só no MAX, e só se o ativo tiver essa idade (B5, 2.3) |
| D8 | Menu | **"Emissoras", depois de "Preço Teto"** |
| D9 | Ajuste da cotação histórica | **Sempre ajustada** por desdobramento, grupamento e bonificação; sem ajuste por proventos (2.3) |

Fora do escopo: média do setor, ranking/comparação entre emissores, cotação ajustada por proventos (retorno
total), edição de cadastro pela tela (rotinas de admin seguem na adm-api), testes E2E com Playwright (escopo
futuro próprio).

---

## 8. Andamento

| Fase | Estado |
|---|---|
| T1 | **Feita (28/09).** B1–B2: `IssuerDTO` com `registrationStatus`, `website` e `Ticker.hasLogo` (`AssetLogos`). B3: `GET /assets/?query=&withIssuer=true` (`searchWithIssuerByTickerOrName`). B4: `GET /assets/{ticker}/indicators/history?period=` — `IndicatorService.historyAll` lê a série do emissor uma vez (`Rows`) e devolve os históricos na ordem do enum. B5: `GET /assets/{ticker}/prices` substitui o esboço `/history`; regras puras em `PriceSeries` (janela de cada atalho, densidade, pontos semanais/mensais, ajuste), serviço `AssetPriceHistoryService` por SQL. D9: `StockEventType.quantityRatio` (core) — a razão de cada evento, usada pelo ajuste da cotação e agora também pelo `IndicatorService` (a conta que estava duplicada lá saiu). Ajuste: fechamento com data **até a data-com** ÷ razão acumulada dos eventos com data-com ≥ a data (evento com data-com no último pregão não ajusta nada). B6: `issuerId` em `PortfolioAssetDetailDTO`, `DividendResponseDTO`, `CeilingPriceDTO`. Testes: `PriceSeriesTest` (7), `TelaEmissorasIT` (7) |
| T2 | **Feita.** Tipos em `types/index.ts`; `api/indicators.ts`, `api/issuers.ts`, `assetsApi.prices`/`searchWithIssuer`; `utils/indicators.ts` (rótulos, fórmulas, ordem, `HISTORICO_DISPONIVEL`, formatação por unidade, compactos, CNPJ) |
| T3 | **Feita.** `pages/IssuerPage.tsx` (rotas `/emissoras` e `/emissoras/:ticker`), menu depois de Preço Teto, título no Header; `IssuerSearch`, `IssuerHeader` (chips levam o período junto), `IssuerRegistry`; estados "não encontrado" e "sem emissor" |
| T4 | **Feita.** `PriceChart`: 1D…MAX (1A padrão), de/até limitados a `firstDate`/`lastDate`, URL `?periodo=` / `?de=&ate=`, resumo (variação, mín, máx, "só fechamento", "ajustado") |
| T5 | **Feita.** `IndicatorGrid` + `Sparkline` (SVG próprio, nulo interrompe a linha) + `IndicatorHistoryModal` (Anual/Trimestral, barras com Atual dourado, média tracejada, tabela, ‹ › e ← →; o anual reaproveita o lote do B4) |
| T6 | **Feita.** `FinancialStatements`: companhia (trimestre/12 meses, últimos 8 ou todos) e FII (últimos 12 ou todos), valores compactos com o exato no tooltip |
| T7 | **Feita.** `components/issuer/TickerLink` em Carteira, Dividendos e Preço Teto |
| T8 | **Feita (28/09).** `mvn clean verify` completo verde; `tsc -b`, `eslint --max-warnings=0` e `vite build` limpos. Base real pela API: PETR4 1D = 25/09 → 28/09 (47,99 → 48,72), MAX de 04/01/2010 (37,32) em pontos mensais, 5A semanal, 7D diário; SBSP3 1A ajustado, maior queda diária −7% (sem o degrau de −80%); PETR4F → preço do PETR4; histórico em lote da PETR4 em ~0,2 s. Navegador do app (logado): PETR4, HGLG11 (P/VP 0,89, DY 9,04%, VP 165,95, 608.345 cotistas, imóveis 97,83%, alavancagem 14,5%; informe 08/2026 igual ao da seção 6), VGRH11 (DY 0, resto "—"), KLBN4 (unit/chips KLBN11 e KLBN3), BOVA11 ("sem emissor"), XPTO9 ("não encontrado"), modal com trimestral e ‹ ›, demonstrativos da PETR4 (4T24 lucro da controladora −17,04 bi), links nas três telas, tema claro. Ajustes feitos na conferência: a API omite campo nulo no JSON (`undefined`, não `null`) — os componentes comparam com `== null`; marcador da sparkline redondo; nome do FII sem repetir |
| T9 | **Feita.** `Claude.md` (endpoints e telas), `PENDENCIAS.txt` (11.6 resolvido; seção 19: histórico de proventos faltando em 17 ações — Klabin, Ambev…; menu lateral no celular; links em outras telas) |

Achado fora do escopo (PENDENCIAS 19.1): 17 ações com provento recente não têm o histórico da B3 (a B3 respondeu
vazio e o arquivo vazio virou "estado") — o DY histórico delas sai 0. A tela mostra o que o catálogo tem.
