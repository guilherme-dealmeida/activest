# Activest — Plano do Índice de Juros Futuros

> Fase: **implementado em 11/11/2026** (J1–J5) — andamento na seção 7. D1–D9, I1–I4 e P1–P4 decididas. Etapas 1 a 3 não alteram código (REGRAS_CLAUDE.md 2.2).
> Origem: chat de dúvidas de 10–11/11/2026 ("Consigo aderir o índice de juros futuros no Activest?").

## 1. Objetivo

Trazer para o Activest a **curva de juros** (taxas que o mercado precifica para cada prazo), com histórico e
atualização diária, para consulta na tela **Índices** e, depois, como insumo de outras telas (Preço Teto, Metas,
renda fixa prefixada).

"Juros futuros" não é um índice de um valor por dia como a Selic: é uma **curva** (uma taxa para cada vencimento).
Para caber no cadastro de índices sem mudar o modelo, cada **prazo fixo** (ex.: 1 ano, 2 anos, 5 anos) vira uma série
própria, de histórico contínuo. Séries por contrato (DI1F27, DI1F29...) ficam de fora: cada contrato vence e a série
acaba.

## 2. Discovery — o que existe hoje

### 2.1 Cadastro de índices

| Peça | Situação | Efeito no escopo |
|---|---|---|
| `market_indices` (core, seed na V2) | 10 índices; colunas `code`, `periodicity` (DIARIA/MENSAL), `unit`, `source`, `source_ref`, `history_from` | cabe uma série por prazo: periodicidade DIARIA, unidade `PCT_ANO` |
| `ck_market_indices_source` | só aceita `BCB_SGS` e `B3` | fonte nova pede migration que amplie a constraint |
| `market_index_values` | uma linha por índice e data (PK índice + data) | serve sem mudança |
| `SELIC_META` | já é uma taxa em % a.a. "que não acumula" (o card avisa que não há acumulado) | **precedente**: as taxas da curva se comportam igual (exibir a taxa, nunca acumular) |
| `MarketIndexImportService` (adm-api) | carga histórica por ano + incremental diária (rotina das 10:00), clientes `BcbSgsClient` e `B3IndexClient`, arquivos por ano em `carga/data` | fonte nova = cliente novo plugado aqui |
| Tela Índices (`IndicesPage.tsx`) | ordem fixa no front (`ORDER`) e tipo `IndexCode` fechado em `types/index.ts` | os códigos novos têm de entrar na ordem e no tipo |
| Quem mais lê índices | `BenchmarkData`/`Benchmark` (Rentabilidade), `GoalFactsLoader` (Metas), `FixedIncomeValuation` (renda fixa) | Impact: garantir que as taxas da curva **não** virem benchmark nem indexador por acidente |

### 2.2 Fontes levantadas (11/11/2026)

| Fonte | O que entrega | Achado |
|---|---|---|
| **B3 — Taxas Referenciais DI x Pré** (página antiga `lum-taxas-referenciais-bmf`) | taxa por prazo fixo em dias corridos | **fora do ar**: a consulta devolve erro de banco da própria B3 (SQL Server). A página nova (`sistemaswebb3-derivativos.../referenceRatesPage`) usa uma API interna, e a página de taxas da B3 **proíbe coleta automatizada, inclusive por ferramentas de IA**. O "Ajustes do pregão" foi substituído em 10/12/2025 (Comunicado Externo 006/2025-VTEC). Sem licença, não recomendo |
| **ANBIMA — ETTJ** (`est-termo/CZ.asp`) | curva prefixada, curva real (IPCA) e inflação implícita por vértice (252 a 8.316 dias úteis), parâmetros Svensson, download CSV/XLS | gratuita e é a curva de referência do mercado; **a confirmar**: até quando o histórico retroage pela consulta pública e os termos de uso |
| **Tesouro Direto — dados abertos** (`carga/data/tesouro/PrecoTaxaTesouroDireto.csv`) | taxa de compra e de venda de cada título (Prefixado, Prefixado com Juros Semestrais, IPCA+, IPCA+ com Juros Semestrais) por dia | **já está no projeto**, com histórico desde 2005 e atualização diária (rotina `TREASURY_PRICES`), licença aberta. Hoje: prefixados de 2027 a 2037 e IPCA+ de 2029 a 2060. É a curva **do título público**, não a do DI futuro: as duas andam juntas, com uma diferença pequena (prêmio e spread do Tesouro Direto) |
| **BRAPI — futuros (DI1)** | todos os vencimentos do DI1, com fechamento | **conferido em 11/11/2026**: a conta está no plano **gratuito**, que **não inclui futuros** (`futures: false`; histórico só até 3 meses, 1 ativo por chamada). Só serve com plano pago |
| **Banco Central — SGS** | séries de swap DI x pré (7805, 7806) | **descontinuadas** em 2019. Não serve |

Observações:

- A tabela `treasury_offers` guarda só a **última** taxa de cada título (PK = ativo). O histórico das taxas está só no
  arquivo `PrecoTaxaTesouroDireto.csv`. Se a fonte for o Tesouro, a série sai desse arquivo.
- Os títulos com juros semestrais pagam cupom. A taxa deles não é a de um título "zero-cupom" do mesmo prazo, e
  tratá-la como se fosse dá uma diferença pequena (D4).

### 2.3 Usos possíveis (para dimensionar, não entram sem decisão)

| Onde | Uso |
|---|---|
| Índices | cards das taxas por prazo e a curva do dia |
| Preço Teto | DI/prefixado longo como alternativa à Selic na taxa de desconto |
| Metas | premissa de rendimento nas projeções (hoje CDI e IPCA) |
| Renda fixa prefixada e Tesouro Prefixado | comparar a taxa contratada com a do mercado no mesmo prazo |
| Rentabilidade | **não serve** como benchmark: é taxa esperada, não série de retorno |

## 3. Decisões do Discovery (para o Guilherme responder)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D1** | Fonte da curva | (a) B3, DI futuro; (b) ANBIMA, ETTJ; (c) Tesouro Direto, dados abertos já no projeto; (d) BRAPI, futuros | **(c)** agora: sem custo, sem restrição de uso, histórico longo e já integrado. (b) como evolução, se o histórico e os termos se confirmarem. (a) só com licença da B3; (d) só com plano pago da BRAPI. O nome do índice deixa claro que é a curva do Tesouro, não o DI1 |
| **D2** | Quais curvas | (a) só a prefixada; (b) prefixada + real (IPCA+); (c) (b) + inflação implícita (calculada das duas) | **(b)**. A inflação implícita pode vir depois, sem fonte nova |
| **D3** | Prazos (vértices) | sugestão: prefixada 1, 2, 3, 5 e 10 anos; real 2, 5, 10 e 20 anos | a sugestão. Prazo além do título mais longo do dia fica **sem valor** (não se extrapola) |
| **D4** | Títulos usados | (a) só os sem cupom (Prefixado, IPCA+); (b) também os com juros semestrais, como aproximação | **(a)** na prefixada até onde houver título sem cupom, e **(b)** só para o prazo de 10 anos, com aviso de aproximação no card |
| **D5** | Taxa do dia | (a) taxa de compra; (b) taxa de venda; (c) média das duas | **(c)**: fica mais perto da taxa de mercado |
| **D6** | Interpolação entre vencimentos | (a) linear na taxa; (b) flat-forward em dias úteis (padrão da B3 e da ANBIMA) | **(b)** |
| **D7** | Histórico | desde 2010 (como os demais índices) ou desde 2005 (início dos dados) | **2010** |
| **D8** | Tela Índices | (a) um card por prazo (9 cards); (b) um card por curva, com seletor de prazo e a visão "curva do dia" (taxa × prazo, com hoje × 1 mês × 1 ano atrás) | **(b)**, abaixo da Meta Selic |
| **D9** | Uso fora da tela Índices nesta fase | (a) nenhum (Preço Teto, Metas e renda fixa viram pendência); (b) já usar em algum deles | **(a)**, e as taxas fora da lista de benchmarks da Rentabilidade e dos indexadores |

> **Decididas em 11/11/2026:** D1–D9 todas pela recomendação (aprovação explícita do Guilherme: "sigo com todas recomendações").

## 4. Impact (11/11/2026)

### 4.1 Achado: cobertura do histórico com as decisões D3 + D4

O Tesouro Direto oferece poucos vencimentos por vez. Medi, de 2010 a 2026, em quantos dias de cada ano cada prazo
teria valor sem extrapolar (script sobre `carga/data/tesouro/PrecoTaxaTesouroDireto.csv`):

| Ano | Pré 1a | Pré 2a | Pré 3a | Pré 5a | Pré 10a | Real 2a | Real 5a | Real 10a | Real 20a |
|---|---|---|---|---|---|---|---|---|---|
| 2010 | 100% | 100% | 0% | 0% | 94% | 0% | 63% | 100% | 82% |
| 2011 | 100% | 100% | 100% | 0% | 0% | 0% | 100% | 100% | 100% |
| 2013 | 100% | 100% | 96% | 0% | 0% | 64% | 100% | 100% | 100% |
| 2016 | 100% | 100% | 100% | 93% | 93% | 0% | 100% | 100% | 0% |
| 2021 | 100% | 100% | 100% | 0% | 0% | 0% | 100% | 100% | 100% |
| 2023 | 50% | 100% | 100% | 100% | 0% | 100% | 100% | 100% | 100% |
| 2026 | 100% | 100% | 100% | 100% | 90% | 72% | 100% | 100% | 100% |

(amostra; a tabela completa sai do mesmo script). Só com títulos sem cupom, **Pré 5a, Pré 10a e Real 2a ficam com
buracos de anos inteiros**. Medido também com as alternativas:

- usando **também os títulos com juros semestrais** em todos os prazos e **ancorando o curto da prefixada no CDI do
  dia** (prazo zero): Pré 1a a 5a e Real 5a a 20a ficam com **100%** em todos os anos; Pré 10a continua alternando
  (≈ 90% num ano, 0% no seguinte: o Tesouro lança o prefixado longo a cada 2 anos com ~10 anos exatos); Real 2a tem
  buracos em 2020–2022;
- prazos alternativos: Pré 8a = **99%**, Pré 6a e 7a ≈ 100%; Real 3a = **94%**, Real 4a = 100%.

### 4.2 Componentes afetados

| Módulo | O que muda |
|---|---|
| core — migration nova (V24) | `ck_market_indices_source` aceita a fonte nova (`TESOURO`); seed das séries da curva em `market_indices` (unidade `PCT_ANO`, DIARIA, `history_from` 2010); descrição da tarefa `MARKET_INDICES` atualizada |
| adm-api — `MarketIndexImportService` | fonte nova "curva do Tesouro": **não baixa nada**, lê o arquivo que a `TREASURY_PRICES` (08:00) já guarda em `carga/data/tesouro` com `TreasuryPriceClient.read` e calcula as taxas por prazo (interpolação flat-forward 252 com o `BusinessCalendar`, que já existe no core). Roda na rotina das 10:00, depois do Tesouro. Sem arquivos por ano (é série derivada de um arquivo que já está em `carga/data`) |
| adm-api — aviso "catálogo mudou" | hoje qualquer índice novo dispara na app-api o recálculo da curva da renda fixa privada. Mudança só nas taxas da curva **não** deve disparar esse recálculo |
| app-api — `MarketIndexQueryService` | lista e histórico já servem. O acumulado de `PCT_ANO` responde "é uma meta anual" (texto da Meta Selic): ajustar o texto para as taxas da curva. Endpoint novo: **curva do dia** (taxa × prazo em uma ou mais datas) |
| web — Índices | `IndexCode` e `ORDER` (fechados no front) ganham os códigos novos; componente novo de card por curva, com seletor de prazo, gráfico do histórico e visão "curva do dia" (hoje × 1 mês × 1 ano); conferido nos dois temas |
| **Não mudam** | `benchmarks` (tabela explícita, a curva não entra), enum `Indexer` (indexadores da renda fixa), `FixedIncomeValuation`, Metas, Preço Teto, bucket R2 |
| Docs | `Claude.md`, `docs/PENDENCIAS.md` (usos futuros: Preço Teto, Metas, renda fixa; ANBIMA; inflação implícita), `README.md` |

### 4.3 Riscos e dependências

- **Dependência da rotina do Tesouro:** sem o arquivo do dia, a curva fica no último dia calculado; a conferência de
  atraso que já existe nos índices diários (7 dias) avisa o administrador. Ambiente novo: o arquivo do Tesouro vem na
  carga inicial antes dos índices.
- **Taxa do Tesouro Direto ≠ DI futuro:** diferença pequena (prêmio e spread do varejo). O card explica.
- **Títulos com cupom como aproximação** (se I1 for aceita): diferença de poucos centésimos a décimos de ponto nas
  curvas inclinadas; o card avisa.
- **Volume:** ~4 mil dias × 9 prazos ≈ 36 mil linhas em `market_index_values`. Sem impacto.

### 4.4 Decisões do Impact (revisam D3/D4 aprovadas, por isso voltam a você)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **I1** | Títulos e curto da curva (revisa D4) | (a) manter D4: só sem cupom (buracos de anos em Pré 5a, Pré 10a e Real 2a); (b) usar também os com juros semestrais em todos os prazos, como aproximação, e ancorar o curto da prefixada no CDI do dia; (c) (b) com bootstrap (taxa zero-cupom calculada a partir do PU dos títulos com cupom: correto, mais trabalho; a real precisa do VNA) | **(b)**; (c) vira pendência |
| **I2** | Prazos (revisa D3) | (a) manter Pré 1/2/3/5/10 e Real 2/5/10/20, com buracos; (b) Pré 1/2/3/5/**8** e Real **3**/5/10/20 | **(b)**: cobertura de 94% a 100% em todos os prazos |
| **I3** | Como agrupar os prazos em curvas | (a) sem coluna nova: código `JUROS_PRE_1A`…, fonte `TESOURO` e `source_ref` `PRE:1` / `REAL:5` (curva e prazo lidos daí); (b) colunas novas `curve` e `tenor_years` em `market_indices` | **(a)**: sem mudança de estrutura |
| **I4** | Âncora do curto da prefixada (se I1 = b) | (a) CDI do dia anualizado (base 252); (b) Meta Selic | **(a)**: é a taxa de um dia efetivamente praticada |

> **Decididas em 11/11/2026:** I1 (b), I2 (b) com a real de **4 anos** no lugar da de 3 (100% de cobertura), I3 (a),
> I4 (a) — pela recomendação, com aprovação explícita do Guilherme. Ele confirmou também o histórico desde 2010 (D7).
> Prazos finais: **prefixada 1, 2, 3, 5 e 8 anos; real 4, 5, 10 e 20 anos**. Cobertura medida de 2010 a 2026: 100% em
> todos os prazos e anos, exceto a prefixada de 8 anos de 04 a 21/01/2010 (14 dias, sem título que alcance 8 anos).
> **Corrigido na carga (11/11/2026):** a medida do Impact usou anos corridos; em dias úteis (o cálculo real) a real de
> 4 anos também fica sem 64 dias espalhados entre 22/11/2019 e 07/08/2020 (o IPCA+ mais curto vencia em pouco mais de
> 1.008 dias úteis). PENDENCIAS 48.5.

## 5. Planning (11/11/2026)

### 5.1 Solução

**Séries (9), fonte `TESOURO`, unidade `PCT_ANO`, diárias, desde 01/01/2010:**

| Código | Nome | `source_ref` |
|---|---|---|
| `JUROS_PRE_1A` … `JUROS_PRE_8A` | Juros prefixados (Tesouro) — 1, 2, 3, 5 e 8 anos | `PRE:1`, `PRE:2`, `PRE:3`, `PRE:5`, `PRE:8` |
| `JUROS_REAL_4A` … `JUROS_REAL_20A` | Juros reais (Tesouro IPCA+) — 4, 5, 10 e 20 anos | `REAL:4`, `REAL:5`, `REAL:10`, `REAL:20` |

**Cálculo da taxa de um prazo numa data** (por dia de `Data Base` do arquivo do Tesouro):

1. Títulos do dia: prefixada = Tesouro Prefixado + Tesouro Prefixado com Juros Semestrais; real = Tesouro IPCA+ +
   Tesouro IPCA+ com Juros Semestrais. Mesmo vencimento nos dois tipos: vale o sem cupom.
2. Taxa do título = média da taxa de compra e da de venda (D5); faltando uma delas, a que existe (P1).
3. Prazo de cada título em dias úteis (`BusinessCalendar`, base 252) da data até o vencimento.
4. Prefixada: ponto de 1 dia útil = CDI do dia anualizado, `(1 + CDI/100)^252 − 1` (I4). Sem CDI gravado para a data,
   a prefixada só sai nos prazos que já têm título dos dois lados; a janela de revisão de 30 dias completa depois.
5. Interpolação **flat-forward 252** entre os dois pontos que cercam o prazo (prazo do vértice = anos × 252 dias
   úteis): fator `F = (1+r)^(du/252)`, `F(du) = F_a · (F_b/F_a)^((du−du_a)/(du_b−du_a))`, taxa `F^(252/du) − 1`.
6. Prazo fora dos pontos do dia: **sem valor** (não extrapola).

**Atualização:** dentro da rotina `MARKET_INDICES` (10:00), depois da `TREASURY_PRICES` (08:00). A fonte lê o
arquivo já guardado em `carga/data/tesouro` (sem download), uma vez por rodada (cache em memória enquanto a rodada
dura), e processa as séries da curva depois do CDI. Carga histórica e reprocessamento pelos mesmos endpoints/admin dos
demais índices. Sem arquivos por ano. Mudanças só em `JUROS_*` **não** publicam "catálogo mudou" para a app-api.

**App-api:** lista, histórico e gráfico como os demais índices; o acumulado das séries `JUROS_*` responde que é uma
taxa de mercado, não um rendimento (sem acumulado). Endpoint novo `GET /api/v1/indices/curves/{PRE|REAL}?dates=…`:
para cada data pedida (até 3), as taxas de todos os prazos da curva no último dia com dado até ela.

**Tela Índices:** dois cards novos logo abaixo da Meta Selic — "Juros prefixados (Tesouro)" e "Juros reais (Tesouro
IPCA+)". Cada card: seletor de prazo (padrão 5 anos, P3), gráfico do histórico com os mesmos períodos dos índices
diários, e a visão **"Curva do dia"** (taxa × prazo, linhas de hoje, 1 mês e 1 ano atrás). ⓘ explicando: é a curva dos
títulos do Tesouro Direto (não o DI futuro da B3); títulos com juros semestrais entram como aproximação; trecho sem
título que alcance o prazo fica sem linha.

### 5.2 Critérios de aceitação

1. As 9 séries gravadas de 04/01/2010 até o último dia do arquivo do Tesouro, com a cobertura da seção 5 (100%, menos
   a prefixada de 8 anos de 04 a 21/01/2010 e a real de 4 anos em 64 dias de nov/2019 a ago/2020).
2. Conferência de uma data por conta independente (script fora do código): as 9 taxas batem em 4 casas.
3. Rodada das 10:00 grava o dia novo depois do Tesouro das 08:00; rodar de novo não altera nada (idempotente).
4. Sem o arquivo do dia, a série fica no último dia e a conferência de atraso avisa o administrador depois de 7 dias.
5. Atualizar só a curva não dispara o recálculo da renda fixa nem da carteira na app-api (log).
6. Tela Índices: os dois cards com troca de prazo, períodos, curva do dia com 3 datas, ⓘ, sem acumulado; conferida nos
   dois temas.
7. Rentabilidade, Metas e renda fixa sem nenhuma série nova (benchmarks e indexadores intactos).
8. Bateria verde: testes do core, adm-api e app-api, lint e build do front.

### 5.3 Decisões do Planning

| # | Pergunta | Recomendação |
|---|---|---|
| **P1** | Título com só uma das taxas no dia (14 linhas desde 2010, todas de prefixado com juros semestrais sem taxa de compra) | usar a taxa que existe |
| **P2** | Nomes e códigos das séries (tabela 5.1) | como na tabela |
| **P3** | Prazo que o card abre | 5 anos nas duas curvas |
| **P4** | Período que o gráfico abre | o mesmo padrão dos outros cards de índice |

> **Decididas em 11/11/2026:** P1–P4 pela recomendação; execução de todas as etapas autorizada ("seguir recomendação, implemente").

### 5.4 Etapas do plano

| # | Etapa | Conteúdo |
|---|---|---|
| **J1** | Core | migration V24 (constraint da fonte com `TESOURO`, seed das 9 séries, texto da tarefa `MARKET_INDICES`); `IndexSource.TESOURO`; calculadora pura da curva (pontos, âncora, flat-forward, sem extrapolação) com testes unitários |
| **J2** | Adm-api | fonte da curva do Tesouro plugada no `MarketIndexImportService` (leitura única do arquivo por rodada, CDI antes, sem cache por ano, sem aviso "catálogo mudou" para `JUROS_*`); teste de integração; carga histórica 2010→hoje e conferência dos critérios 1 e 2 |
| **J3** | App-api | texto do acumulado para `JUROS_*`; endpoint da curva do dia com DTO; testes |
| **J4** | Web | tipos e ordem dos códigos; card da curva (seletor de prazo, histórico, curva do dia, ⓘ); conferência nos dois temas |
| **J5** | Fechamento | Testing (bateria completa), Review, `Claude.md`, `docs/PENDENCIAS.md` (bootstrap dos títulos com cupom, ANBIMA, inflação implícita, usos em Preço Teto/Metas/renda fixa, DI futuro só com licença B3 ou BRAPI paga), `README.md` e andamento |

## 6. Quem faz

| Atividade | Claude | Guilherme |
|---|---|---|
| Discovery, Impact, Planning | ✔ | decide D, I e P |
| Implementação, testes, review | ✔ (após aprovação) | aprova |
| Commit | sugere a mensagem | ✔ |

## 7. Andamento

- 11/11/2026 — **J1** core: V24 (fonte `TESOURO`, 9 séries, texto da `MARKET_INDICES`), `IndexSource.TESOURO`,
  `YieldCurve` (pontos, âncora no CDI, flat-forward 252, dias úteis por tabela acumulada) + `YieldCurveTest` 5;
  `SchemaTest`/`MarketIndexRepositoryTest` com 19 índices. (A migration nasceu como V10 por engano de leitura da pasta e
  foi renomeada para V24 antes de rodar.)
- 11/11/2026 — **J2** adm-api: `TreasuryCurveSource` (lê o arquivo do Tesouro uma vez enquanto não muda; CDI da série
  gravada), `case TESOURO` no `MarketIndexImportService`, sem "catálogo mudou" para a curva, `importAll(…, false)` +
  `importCurve` e passo próprio na carga inicial depois do Tesouro; `TreasuryCurveSourceTest` 4, `MarketIndexImportIT`
  +2, `BootstrapServiceTest` ajustado. Carga real: 37.749 linhas em 4 s (4.203 dias de 04/01/2010 a 10/11/2026);
  PRE 8a com 4.189 e REAL 4a com 4.139 (seção 5). Conferência independente (script fora do código) em 15/06/2015,
  18/03/2020 e 10/11/2026: as 27 taxas iguais em 4 casas. Rodada incremental de novo: 0 novos, 0 corrigidos na curva; a
  app-api só foi avisada do PTAX.
- 11/11/2026 — **J3** app-api: texto do acumulado da curva (422), `GET /api/v1/indices/curves/{PRE|REAL}`;
  `IndicesIT` com 19 índices + `yieldCurve`.
- 11/11/2026 — **J4** web: tipos e `indicesApi.curve`; `IndexChartCard` com título/(i)/controles/rodapé opcionais;
  `YieldCurveCard` (prazo, histórico, curva do dia, (i)); cards abaixo da Meta Selic. Conferido no navegador nos dois
  temas (ajuste: eixo da curva do dia começa um ano antes do primeiro prazo, para "4a" e "5a" não se sobreporem). Nota:
  a queda e a volta da curva em out/2026 (05/10 a 26/10) estão no próprio arquivo do Tesouro, em todos os títulos.
- 11/11/2026 — **J5**: docs (`Claude.md`, `README.md`, `docs/PENDENCIAS.md` seção 48). Bateria: core 260;
  integration 116; adm-api 171 + 110; app-api 241 + 316; front lint, Vitest 189 e build — todos verdes. (Na primeira
  rodada o `MarketIndexImportIT.reconciliationBecomesMessage` ainda contava 10 avisos de atraso; passou a 19.)
  Review: sem achados além do ajuste do eixo (J4) e da cobertura da real de 4 anos (seção 5). Durante a carga, a
  porta 8080 da app-api no Windows parou de responder (dentro do container e pelo proxy do front, normal); a carga foi
  disparada com `--api http://127.0.0.1:5173`.
- 11/11/2026 — escopo aberto; Discovery feito (seções 2 e 3); BRAPI conferida (plano gratuito sem futuros); D1–D9 pela recomendação; Impact feito (seção 4); I1–I4 pela recomendação, real de 4 anos; Planning apresentado (seção 5), aguardando P1–P4 e a ordem de execução.
