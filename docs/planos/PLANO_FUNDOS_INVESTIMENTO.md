# Activest — Plano de Fundos de Investimento (cadastro e cota diária da CVM)

> Fase: **Encerrado** (27/10/2026) — Discovery, Impact, Planning, Implementation (F1–F9), Testing, Review (com a
> revisão do Guilherme) e Commit (mensagem sugerida; git com o Guilherme). Pendências do escopo em
> `docs/PENDENCIAS.md`, seção 39. Situação de cada etapa na seção 9 (Andamento).
> Origem: ESCOPO "Cadastro e precificação de Fundos de Investimento (CVM) no Activest" (26/10/2026). Texto-base:
> *"Cadastrar no Activest os fundos de investimento registrados na CVM, com a cota diária deles, usando o tipo de ativo
> FUNDO_INVESTIMENTO, que já existe e está vazio. [...] Este escopo precisa definir como os fundos entram no catálogo e
> como se mantêm atualizados."*
> Absorve: `docs/PENDENCIAS.md` **29.7** na parte dos fundos (tipo `MANUAL` sem série deixa o período indisponível na
> Rentabilidade).
>
> **Impostos não são "regra do projeto"** (correção de 27/10/2026): não existe regra geral escrita. Sem impostos hoje:
> B3 e cripto. Com impostos: RF (IR + IOF), ativos internacionais (IOF e IR) e proventos. Para fundos é decisão deste
> escopo (D17), não exclusão automática.

---

## 1. Objetivo

Os fundos de investimento passam a ser **ativos do catálogo** (tipo `FUNDO_INVESTIMENTO`), com cadastro vindo da CVM e
**cota diária** atualizada por rotina própria, para que a posição em fundo seja valorizada, entre na Rentabilidade e
nas Metas como os demais ativos. Hoje o catálogo só nasce da migration de seed (V4); este é o primeiro tipo cujo
catálogo é criado e mantido por **integração em tempo de execução**.

## 2. Fora do escopo

- Fundos sem cota diária no informe da CVM: FII e FIIM/ETF (já estão no catálogo pela B3), FIDC, FIP, FIAGRO, Funcine
  (ver 4.2 e D1).
- Intradiário: fundo não tem cota durante o dia (a recomendação 7 do escopo não se aplica a fundos; ver D14).
- Moeda: a cota é publicada em reais, inclusive a dos cambiais e dos que investem no exterior — o ativo nasce `BRL`,
  sem nada do escopo de moedas a consumir.

## 3. Descrição consolidada (o que o escopo traz)

| # | Pedido | Onde está tratado |
|---|---|---|
| E1 | Cadastro dos fundos no catálogo, em tempo de execução | 4.1, 4.3, D2, D3, D10 |
| E2 | Cota diária e atualização contínua (defasagem D+1 ou mais) | 4.4, D8, D9 |
| E3 | Identificação sem ticker; ligação antes/depois da RCVM 175 | 4.3, D3, D4 |
| E4 | Volume: 71,3 milhões de linhas, ~21 GB no formato atual | 4.5, D5, D6, D7 |
| E5 | Recomendações 1, 2, 3, 7 e 8 (e as guardadas 4, 5, 6), na numeração do texto do escopo | 6, D6, D7, D13, D14 |
| E6 | Rotina diária no padrão de nomes da CVM em `carga/data/cvm/fi` | 4.4, D9 |
| E7 | Impostos do fundo (IR, IOF, come-cotas) — decisão do escopo, não regra | D17 |
| E8 | Onde o fundo aparece além da transação (busca do topo, Ativos, Alocação/Estratégia/Rebalanceamento) | D18 |
| E9 | Ambiente novo: `cvm/fi` (1,4 GB) no bucket e no `data pull` | D19 |

## 4. Discovery — o que foi levantado (26/10/2026)

### 4.1 Estado atual no código e no banco

| Peça | Situação |
|---|---|
| `asset_types` | `FUNDO_INVESTIMENTO` (V2, id 6), `price_source = 'MANUAL'`, `BRL`, região `BRASIL`. **Nenhuma categoria** em `asset_categories` e **nenhuma linha de código** citando o tipo (Java, TS) |
| `PriceSource` (core) | `BRAPI`, `COINGECKO`, `TESOURO`, `MANUAL`; o Javadoc do `MANUAL` cita "fundos" como "enquanto não houver um cliente". Constraint `ck_asset_types_price_source` com os mesmos quatro valores |
| `assets` | `ticker VARCHAR(255) NOT NULL` + `uk_assets_ticker` **e** `idx_assets_ticker` — o mesmo caso de índice duplicado do `asset_price_history` |
| Ticker como chave de rota | 9 endpoints da app-api usam `/{ticker}` (`/assets/{ticker}`, `/logo`, `/prices`, `/dividends/asset/{ticker}`, indicadores, preço teto) e o front tem `/assets/:ticker` e `/ceiling-prices/:ticker`. Um ticker com `/` (CNPJ formatado) quebra essas rotas |
| `asset_price_history` | `id`, OHLC, `volume`, `source VARCHAR(15)`, `created_at`, `updated_at`; `uk_price_history_day (asset_id, reference_date)` e `idx_price_history_asset_date` com as mesmas colunas (duplicado, como o escopo mediu). `ck_price_history_source`: `BRAPI`, `COINGECKO`, `B3_COTAHIST`, `TESOURO`, `BINANCE`. Lido/gravado por **17 classes** (entre elas `PortfolioTimelineBuilder`, `BenchmarkData`, `IndicatorService`, `CompanyDataService`, as cargas de preço e o `AssetController`) |
| Fechamento diário | `ClosingPriceService` só busca ativo **com posição aberta** (`OpenPositions.assetsWithShares(PriceSource)`) — o padrão "só o que está em carteira" já existe para BRAPI e CoinGecko. Depois avisa a app-api pelo canal `activest:catalog:assets` (`FECHAMENTO`) para revalorizar |
| Carga sob demanda | Já existe para proventos: a app-api publica `activest:dividends:on-demand` depois de cada transação e a adm-api responde com "catálogo mudou". É o modelo para "histórico de cota sob demanda" (recomendação 3) |
| Cache de arquivo público | `PublicFileCache` (adm-api): conferência antes de virar arquivo (zip da CVM pela assinatura `PK`), 304, recusa com cópia em disco, `FONTE_INDISPONIVEL` e `FONTE_DESATUALIZADA` (mais de 2 dias úteis sem dado novo). Serve à rotina dos fundos sem código novo de download |
| Tarefas | 28 tipos (V2 + V8 + V9). Os modelos mais próximos: `TREASURY_PRICES` (arquivo público diário, recuperação na subida) e `B3_DAILY_QUOTES` (um arquivo por período, incremental ou com período) |
| Ativo `MANUAL` na carteira | Sem série: `GoalFactsLoader` marca `noPrice`; a Rentabilidade fica indisponível no período (PENDENCIAS 29.7) |
| Integração B3 / corretoras | Nenhuma regra para fundo (`B3EquityImportRules` e `CryptoImportRules` não tratam o tipo). A posição em fundo hoje só entraria por lançamento manual — e não há ativo para escolher |
| Cargas em lote | As cargas de preço gravam com `INSERT ... ON CONFLICT` linha a linha (`HistoricalQuotesImportService`, `TreasuryPriceService`, `CryptoHistoryService`); nenhuma usa `COPY` |

### 4.2 Quem tem cota diária no informe (a abrangência natural)

O informe diário da CVM **só traz fundos/classes FIF** (os antigos "FI" da ICVM 555). Em 09/2026: `CLASSES - FIF`
537.704 linhas, `CLASSE FIF/FAPI` 273 e `FI` 122 (fundos que ainda reportam no formato antigo). **Não aparecem** FII,
FIIM (os ETFs: BOVA11, IVVB11 e SMAL11 conferidos), FIDC, FIP, FIAGRO nem Funcine.

Consequências:

- Não há sobreposição com o catálogo atual: FII e ETF continuam vindo da B3, com ticker.
- FIDC, FIP e FIAGRO não têm cota diária na CVM; cadastrá-los sem preço seria repetir o problema do `MANUAL` (D1).

Classes **FIF em funcionamento** no cadastro: 25.410 (mais 13 FAPI). Delas, não exclusivas: público geral 5.307,
qualificado 3.275, público vazio 817; o resto é exclusivo ou para profissional. Por classificação CVM (não
exclusivas, geral + qualificado): Multimercado 3.568, Renda Fixa 3.212, Ações 2.434, FMP-FGTS 93, Cambial 61.

### 4.3 Identificação e ligação antes/depois da RCVM 175

| Medição | Resultado |
|---|---|
| Classes em funcionamento com `CNPJ_Classe` = `CNPJ_Fundo` | **33.695 de 33.792** (99,7%): na adaptação, a classe única herdou o CNPJ do fundo |
| Fundos com mais de uma classe em funcionamento | 24 |
| Continuidade na troca de layout (último dia de 11/2023 × primeiro de 12/2023) | 25.620 chaves comuns; **52 com salto de cota acima de 5%** (a investigar no Impact) |
| Chaves de 09/2026 que já existiam em 12/2020 / 12/2010 (mesmo CNPJ) | 11.631 / 3.183 de 24.977 |
| CNPJs do informe de 12/2020 encontrados no `registro_fundo.csv` | **19.528 de 19.528** |
| CNPJs do `cad_fi.csv` encontrados no `registro_fundo.csv` | 40.647 de 41.103 |
| Subclasses no informe de 09/2026 (`ID_SUBCLASSE` preenchido) | 1.023 chaves (1.009 achadas no `registro_subclasse.csv`) |
| Chaves de 09/2026 fora do `registro_classe` | 271 — estão no `registro_fundo`, 265 com situação **Cancelado** mas ainda reportando cota (a investigar) |

Conclusões para as decisões:

- **A série se liga pelo CNPJ**, sem tabela de-para: o CNPJ da classe é o CNPJ do fundo antigo em quase todos os
  casos. A "mudança de natureza" da chave afeta, na prática, as subclasses (série própria, que começa na criação
  dela) e os poucos fundos com várias classes.
- **O `registro_fundo.csv` cobre todo o histórico** (90.839 fundos, inclusive os cancelados antes da RCVM 175). O
  `cad_fi.csv` não é necessário para identificar nem para ligar séries; só acrescenta atributos antigos dos cancelados
  (classe ICVM 555, taxa de administração, ANBIMA) — ver D10.
- O CNPJ formatado não serve de ticker por causa das rotas (`/`); serve o CNPJ **sem pontuação** (14 caracteres).
- **CNPJ alfanumérico:** desde julho/2026 a Receita emite CNPJ com letras nas 12 primeiras posições (IN RFB
  2.229/2024). Chave, validação e busca têm que aceitar letras — "só dígitos" quebra com os fundos novos.

### 4.4 Os arquivos da CVM: layout, republicação e defasagem

**Layout por nome de coluna, não por período.** Os mensais vão de 2021-01 a 2023-11 com `TP_FUNDO;CNPJ_FUNDO` e de
2023-12 em diante com `TP_FUNDO_CLASSE;CNPJ_FUNDO_CLASSE;ID_SUBCLASSE`. Dentro dos anuais (2010–2020) os meses alternam
entre `CNPJ_FUNDO` sozinho (122 meses) e `TP_FUNDO;CNPJ_FUNDO` (10 meses), sem ordem. O leitor tem que achar as colunas
pelo cabeçalho de cada arquivo interno.

**A CVM regera os últimos 12 meses.** Pela data interna dos CSVs: os mensais de 2025-11 a 2026-10 têm todos a data de
17/10/2026 (dia do download); de 2025-10 para trás, cada mês ficou com a data de cerca de 11 a 12 meses depois dele
(2024-09 → 30/08/2025; 2023-12 → 30/11/2024) e não muda mais. Logo, a rotina precisa:

- baixar o mês corrente todo dia (o arquivo cresce);
- rebaixar a janela dos últimos 12 meses (retificações), com o 304 do `PublicFileCache` evitando tráfego quando não
  mudou;
- tratar o mês que "congela" como definitivo.

**Defasagem.** No arquivo de 2026-10 gerado em 17/10 (00:51): última cota em 16/10 para 49 chaves, 15/10 para 23.819,
14/10 para 1.259 e centenas com 1 a 2 semanas de atraso. O normal é **D+1 a D+2**, com uma cauda que reporta com
atraso. A cópia local em `carga/data/cvm/fi/inf_diario` é de **17/10/2026** (dados até 16/10).

**Tempo de leitura.** Uma varredura completa dos 81 ZIPs (71.294.790 linhas) num script Python de uma thread, na
máquina do Guilherme, levou **cerca de 2,5 minutos**. Isso torna viável carregar a série de um fundo "sob demanda"
direto dos ZIPs, em segundo plano, sem índice próprio (ver D5).

### 4.5 Volume por recorte (todas as chaves que já reportaram, 2010–2026)

| Recorte | Chaves | Linhas | Observação |
|---|---|---|---|
| Tudo | 52.875 (52.120 CNPJs + 1.128 subclasses) | 71,3 mi | 26.069 chaves são fundos encerrados antes da RCVM 175 (sem registro de classe) |
| Com cota desde 09/2026 ("vivos") | 26.099 | 43,1 mi (histórico inteiro deles) | |
| Exclusivos | 11.720 | 19,9 mi | |
| Profissional | 4.533 | 6,6 mi | |
| Público geral + qualificado, não exclusivos | 8.780 | 16,0 mi | ~22% das linhas |

Estimativa de espaço (a medir no Impact): no formato atual (~300 bytes/linha) os 16,0 mi do público geral + qualificado
dariam ~4,8 GB; numa linha enxuta (chave `(asset_id, reference_date)`, sem auditoria, sem índice duplicado) a ordem de
grandeza cai para ~90–120 bytes/linha com o índice, ou seja **~1,5–2 GB** para esse recorte e **~6–8,5 GB** para a
série inteira.

## 5. Requisitos (rascunho para o Impact)

- R1. Ativo de fundo no catálogo com identificador estável (D3), nome, situação e categoria pela classificação CVM.
- R2. Cadastro do fundo atualizado por rotina da adm-api a partir do `registro_fundo_classe.zip`, sem sobrescrever o
  que for curadoria (se houver).
- R3. Cota diária gravada por rotina própria (fonte nova `CVM`), lendo o informe pelo cabeçalho (4.4).
- R4. Atualização incremental: mês corrente todo dia + janela de 12 meses de retificação.
- R5. Fonte parada → `FONTE_DESATUALIZADA` e falha de download → `FONTE_INDISPONIVEL`, como no Tesouro.
- R6. Preço atual do ativo = última cota publicada; a valorização da carteira e a Rentabilidade passam a usar a série
  (sai do caso `MANUAL`).
- R7. Lançamento de compra/venda de fundo pelo usuário (D16), com busca por nome e CNPJ (D11).
- R8. Ambiente novo sobe sem depender de pasta fora do projeto: arquivos em `carga/data/cvm/fi`, espelhados no bucket.
- R9. Cargas grandes em lote (recomendação 8).

## 6. As recomendações do escopo, à luz do Discovery

| Rec. | O que o Discovery acrescenta |
|---|---|
| 1 — enxugar `asset_price_history` | Confirmado o índice duplicado (e o mesmo caso em `assets.ticker`). Mexer na linha atinge 17 classes e as cargas; tirar só o índice duplicado é barato e independente. Ver D7 |
| 2 — tabela própria para a cota | Os campos do informe (PL, captação, resgate, cotistas) não cabem em OHLC; sem tabela própria, ou se perdem, ou viram colunas nulas para todo o resto do catálogo. O custo é ensinar os 17 leitores de preço (timeline, `/prices`, valorização) a ler a cota. **Revisto em 27/10:** com D5 só os fundos em carteira têm série, o volume deixa de ser argumento e a recomendação passa a ser D6 (a) |
| 3 — cadastro completo, histórico só do que está em carteira | Viável: a varredura completa dos ZIPs leva ~2,5 min, e o padrão sob demanda já existe nos proventos. Ver D2 e D5 |
| 7 — intradiário no Redis | Não se aplica a fundos (não há cota intradiária). Ver D14 |
| 8 — recalcular só o que mudou, lote | Necessário: hoje as cargas são linha a linha. A janela de 12 meses de retificação reforça "gravar só o que mudou". Detalhe técnico no Impact/Planning |
| Guardadas (agregados, partição, TimescaleDB) | Nada no Discovery pede antecipá-las: com o recorte de D2/D5, o volume fica na casa de poucos GB |

## 7. Decisões para o Guilherme (Discovery)

> Cada uma traz a recomendação do Claude; só vale com aprovação explícita (REGRAS 2.5).

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D1** | Quais tipos de fundo entram | (a) só FIF (os que têm cota no informe diário, inclusive FAPI); (b) FIF + FIDC/FIP/FIAGRO só como cadastro, sem cota | **(a)** — FIDC/FIP/FIAGRO sem cota diária repetiriam o problema do `MANUAL`; FII e ETF já vêm da B3 |
| **D2** | Abrangência do cadastro | (a) toda chave que já reportou desde 2010 (52.875, inclusive encerrados); (b) só as vivas (~26 mil); (c) vivas sem exclusivos e sem profissional (~10 mil, contando público vazio) | **(a) no cadastro**, com exclusivo/profissional marcados e fora da busca por padrão — mesmo princípio das emissoras de RF ("empresa morta continua no cadastro" para lançamento retroativo). O peso está na cota, não no cadastro |
| **D3** | Identificação (ticker NOT NULL UNIQUE) | (a) `ticker` = CNPJ da classe sem pontuação (14 caracteres, alfanumérico desde 07/2026), e `CNPJ-ID_SUBCLASSE` para subclasse; (b) chave própria numa tabela do fundo + ticker sintético (ex.: `FI00017024`) | **(a) + tabela própria do fundo** (1:1 com `assets`, como `asset_terms`) com `cnpj` e `subclass_id` em unicidade — o ticker sem pontuação funciona nas rotas `/{ticker}` sem mudar a API; a tela mostra nome e CNPJ formatado. Chave, validação e busca aceitam o CNPJ alfanumérico (4.3) |
| **D4** | Ligação das séries antes/depois da RCVM 175 | (a) pelo CNPJ, sem de-para (subclasse com série própria desde a criação); (b) de-para curado | **(a)** — 99,7% das classes herdaram o CNPJ do fundo; os 52 saltos de cota e as 271 chaves "canceladas que reportam" são conferidos no Impact |
| **D5** | Quanto histórico de cota e quando | (a) série inteira de todos os fundos na carga inicial; (b) carga inicial de todos os vivos desde 2010; (c) sob demanda: série completa (desde 2010) só dos fundos em carteira, carregada ao lançar a primeira transação, e a cota diária só deles; (d) (c) + os últimos N dias de todos (para a busca mostrar a cota) | **(d)** com N curto (ex.: a última cota de cada fundo vivo no cadastro, sem série) — a cota do dia ajuda a lançar e a escolher; a série fica para quem tem posição. **Ajuste (27/10):** o lançamento não espera a série (varredura de ~2,5 min em segundo plano) — a cota do dia de conversão (D16) é lida na hora, só do ZIP daquele mês (segundos); a série completa segue sob demanda |
| **D6** | Onde gravar a cota | (a) `asset_price_history` (cota em `close_price`, `source = CVM`; PL, valor total, captação, resgate e cotistas ficam só no arquivo bruto em `carga/data/` e ganham tabela quando alguma funcionalidade usar); (b) tabela própria (`fund_quotes`: cota, PL, valor total, captação, resgate, cotistas), com os leitores de preço sabendo ler dela | **(a)** (revista em 27/10; antes (b)) — com D5 só os fundos em carteira têm série, então o volume não justifica tabela própria; (b) custa mudar 17 classes leitoras para dados que nenhuma funcionalidade usa hoje. Requer `CVM` no `ck_price_history_source` |
| **D7** | Enxugar `asset_price_history` (rec. 1) neste escopo | (a) só tirar os índices duplicados (`idx_price_history_asset_date`, `idx_assets_ticker`) neste escopo; (b) reformular a linha inteira aqui; (c) nada aqui, escopo próprio | **(a)**, e a reformulação da linha vira escopo próprio — com D6 (b) os fundos não dependem dela |
| **D8** | Fonte e tarefa | Nova fonte `CVM` em `PriceSource`/`ck_asset_types_price_source` (o tipo passa de `MANUAL` para `CVM`) e tarefa diária própria (nome sugerido `FUND_QUOTES`) com recuperação na subida; horário | **Sim**; horário da manhã (o arquivo da CVM é regerado de madrugada — o de 17/10 saiu às 00:51), com uma rodada automática por dia (`oncePerDay`, como o câmbio) e Run manual |
| **D9** | Arquivos e janela de atualização | (a) mês corrente todo dia + rebaixar os 12 meses anteriores (com 304); (b) só o mês corrente | **(a)** — a CVM regera os últimos 12 meses (4.4); arquivos em `carga/data/cvm/fi/inf_diario` com o nome da CVM; o cadastro (`registro_fundo_classe.zip`) baixado na mesma tarefa |
| **D10** | Dados do cadastro do fundo | Lista sugerida: denominação, CNPJ, situação e data, data de início, classificação CVM (vira a **categoria**: Renda Fixa, Ações, Multimercado, Cambial, FMP-FGTS), ANBIMA, público-alvo, exclusivo, condomínio (aberto/fechado), tributação de longo prazo, gestor, administrador (nome + CNPJ, texto), PL e data. Usar o `cad_fi.csv` para os encerrados antes da RCVM 175? | **Lista sugerida**, gestor/administrador como texto + CNPJ (sem tabela de instituições), e o `cad_fi.csv` só para preencher a classificação dos encerrados (o resto vem do `registro_fundo`) |
| **D11** | Busca na transação | (a) busca por nome e CNPJ, só vivos e não exclusivos/profissional, com opção "mostrar todos"; (b) tudo sempre | **(a)** — 52 mil fundos com nomes parecidos poluem a busca; o lançamento retroativo usa "mostrar todos" |
| **D12** | Preço atual | Última cota publicada (com a data) | **Sim**, sem regra própria pela defasagem (REGRAS 2.6); a Rentabilidade já tem a regra do preço carregado |
| **D13** | Recalcular só o que mudou (rec. 8) | — | Fica como requisito técnico do Impact/Planning (lote, gravar só a diferença na janela de 12 meses) |
| **D14** | Intradiário no Redis (rec. 7) | — | **Fora deste escopo** (fundo não tem cota intradiária) |
| **D15** | Proventos/amortização de fundo | (a) fora do escopo; (b) entra | **(a)** — o informe não traz distribuição; fica pendência se aparecer fundo que distribui |
| **D16** | Como o usuário lança a aplicação | (a) quantidade de cotas × cota (como ação); (b) valor aplicado + data de conversão, com as cotas calculadas pela cota do dia de conversão (editáveis) | **(b)** — no fundo o investidor sabe o valor e a data; as cotas saem depois. A forma exata vai para o Planning. A cota do dia de conversão vem do ZIP do mês, lido na hora (ajuste do D5) |
| **D17** | Impostos do fundo (IR regressivo, IOF até 30 dias, come-cotas) | (a) fora: bruto, cotas sem come-cotas, com a limitação registrada (tela + pendência); (b) como a RF: IR + IOF no resgate e no líquido estimado, e o come-cotas reduzindo as cotas em maio e novembro; (c) só o come-cotas (ajuste de cotas), IR/IOF fora | **(b)** — fundo de RF/multimercado usa a mesma tabela regressiva já cadastrada para a RF, e o cadastro traz `Tributacao_Longo_Prazo`. O come-cotas mexe na **quantidade de cotas**: sem ele a posição fica superavaliada e o erro cresce a cada semestre. Fundo de ações não tem come-cotas (15% no resgate). Se (b) pesar demais nesta fase, (c) + pendência do IR/IOF |
| **D18** | Onde o fundo aparece além da transação | Busca do topo (`searchByTickerOrName`), tela de Ativos (abas Ações/FIIs e página `/ativos/:ticker`), Alocação, Estratégia e Rebalanceamento (estratégia por tipo e categoria) | Busca do topo com o mesmo filtro do D11; **sem** aba nem página de fundo na tela de Ativos nesta fase (ela é de indicadores de empresa/FII) e o ticker do fundo não vira link; Alocação, Estratégia e Rebalanceamento tratam o tipo como os demais, com categoria = classificação CVM (D10) |
| **D19** | `cvm/fi` (1,4 GB) num ambiente novo | (a) fora do `--minimo` do `data.sh` (a CVM mantém `HIST/` e os mensais; a rotina rebaixa); (b) dentro do mínimo | **(a)** — nada ali é insubstituível; entra no `data pull` completo e no bucket como os demais conjuntos da CVM |

### 7.1 Respostas do Guilherme (27/10/2026)

**Aprovadas pela recomendação:** D1, D2, D5 (com o ajuste de 27/10), D9, D10, D12, D13, D14, D15 e **D17 (b)** — fundo
com IR + IOF como a RF e o come-cotas reduzindo as cotas.

**Em aberto:**

| # | Comentário do Guilherme | Resposta / levantamento | Situação |
|---|---|---|---|
| D3 | O ticker é texto e deveria ser o nome ou uma descrição que não se repete; a parte numérica do CNPJ pesa demais. A subclasse não seria categoria ou outra classificação? | **Nome como chave não serve:** nas 25.423 classes FIF vivas o nome tem mediana de 83 caracteres (20.369 acima de 60, máximo 100), há 6 grupos de nomes repetidos, o nome muda com a vida do fundo e subclasses se chamam só "SUBCLASSE A". Proposta: separar **chave** (o `ticker` continua sendo o CNPJ sem pontuação, usado nas rotas e na unicidade, nunca exibido como título) de **rótulo** (para `FUNDO_INVESTIMENTO` a tela mostra o nome — e "nome da classe — Subclasse X" na subclasse — onde os demais tipos mostram o ticker, com o CNPJ formatado como dado secundário). **Subclasse não é categoria nem classificação nova:** tem cota própria no informe (1.023 chaves em 09/2026; 4.451 classes têm subclasse, até 48 numa só), então é um **ativo** próprio, como ON e PN da mesma empresa são tickers diferentes do mesmo emissor. A hierarquia fundo → classe → subclasse fica na tabela do fundo (D3); a categoria continua sendo a classificação CVM (D10) | aguardando decisão |
| D4 | Depende de D3 | — | depende de D3 |
| D6 | O rastreio pelo tipo de ativo do preço histórico basta para mudar de estratégia no futuro | Concorda com (a): além do tipo do ativo, cada linha leva `source = CVM` | aguardando confirmação de (a) |
| D7 | Há registros duplicados? Viriam de conflito entre tipos de ativo? | **Não há linha duplicada** (0 no banco; `uk_price_history_day` impede). O duplicado é o **índice**: `idx_price_history_asset_date` (191 MB) repete as colunas do `uk_price_history_day` (194 MB), e `idx_assets_ticker` repete o `uk_assets_ticker`. Veio da consolidação das migrations, não de conflito entre tipos. Tirar o redundante libera ~191 MB e deixa as gravações mais leves | aguardando confirmação de (a) |
| D8 | Existe a regra para não rodar várias vezes no dia a cada subida? | **Sim:** `TaskHandler.oncePerDay()` (do escopo de moedas) — a agendada pula se já houve rodada agendada ou de subida no dia; a subida só roda depois do horário de hoje e só se ainda não rodou; o Run manual roda sempre e não conta. Sem `oncePerDay`, a subida já não repete no mesmo dia (`startupTasksOf`). A `FUND_QUOTES` nasce com `oncePerDay = true` | aguardando confirmação |
| D11 | Depende de D3 e D4 | — | depende de D3/D4 |
| D16 | Lançar onde? | Na tela **Transações** (Nova transação), como os demais tipos: hoje o formulário pede quantidade × preço; para fundo pediria valor aplicado + data de conversão e calcularia as cotas (editáveis). Nenhuma integração atual (B3, Binance) traz fundo | aguardando decisão |
| D18 | Depende de D11 | — | depende de D11 |
| D19 | O que é "fora do `--minimo`"? | `data.sh pull --minimo` baixa do bucket só o que a origem não devolve mais ou devolve devagar (`fnet`, `b3/proventos`, `b3/proventos-12m`, `cvm/ipe`, `cvm/proventos`, `levantamento`); o `pull` sem opção baixa tudo. "Fora do mínimo" = `cvm/fi` só vem no `pull` completo (1,4 GB), porque a CVM mantém o `HIST/` e os mensais e a rotina rebaixa o que faltar | aguardando confirmação |

**Caso real conferido (27/10/2026) — fundo previdenciário do Guilherme** (no Investidor10: "FIC DE FI PREVIDENCIÁRIO
CAIXA RF 300", 03.926.431/0001-71):

- Está nos arquivos: `registro_fundo` (2 registros do mesmo CNPJ: um FIF cancelado em 2004 e o FI vivo, id 29784),
  `registro_classe` (classe 581, Renda Fixa, ANBIMA "Previdência RF Duração Livre Grau de Inv", PL R$ 4,95 bi em
  20/10/2026) e o informe diário **de 01/2010 a 10/2026** (cota de 15/10/2026: 9,680832831, a "R$ 9,70" do Investidor10).
- O nome oficial na CVM é **"CAIXA 300 PREVIDENCIÁRIO FIC DE CLASSE DE FIF RENDA FIXA - RESPONSABILIDADE LIMITADA"**.
  O nome que o Investidor10 mostra **não existe** em nenhum arquivo da CVM (nome comercial/abreviado de outra fonte,
  ou denominação antiga).
- A classe é **exclusiva, para profissional e com 1 cotista** (a seguradora Caixa Vida e Previdência): o participante
  do plano PGBL/VGBL não é cotista direto. Com o filtro recomendado no D2/D11 (esconder exclusivo/profissional), este
  fundo **sumiria da busca** — os fundos de previdência (FIE) precisam de tratamento próprio no D11.
- O CNPJ aparece em mais de um registro no `registro_fundo` (registro cancelado + vivo): a chave por CNPJ vale entre
  os registros **vivos**, e a carga tem que escolher o registro vivo.

### 7.2 Impedimentos levantados pelo Guilherme (27/10/2026)

**I1 — Fundos de previdência têm que entrar (revisão de D2 e D11).** O "esconder exclusivo/profissional" não era regra
do projeto: era parte da **recomendação do Claude** no D2/D11, e esconderia justamente fundos que o usuário lança. Nas
25.423 classes FIF vivas há **4.396 de previdência** (ANBIMA "Previdência..."), das quais 3.491 exclusivas e 4.317 para
profissional — o cotista é a seguradora, não o participante do PGBL/VGBL. **Revisão proposta:** todas as classes vivas
entram no cadastro **e na busca**, sem filtro por exclusivo ou público-alvo (ficam só como informação do fundo); os
cancelados ficam atrás do "mostrar todos" (lançamento retroativo). Vale para D2 (que estava aprovada com o filtro) e D11.

**I2 — D3 vai por (b): nome curto sem curadoria.** Curadoria manual de dezenas de milhares de fundos é inviável. Fontes
de nome curto levantadas:

| Fonte | Como | Situação |
|---|---|---|
| Abreviação automática da denominação da CVM | Regras fixas: FUNDO DE INVESTIMENTO → FI, FUNDO DE INVESTIMENTO EM COTAS → FIC, RENDA FIXA → RF, MULTIMERCADO → MULT, RESPONSABILIDADE LIMITADA → RL, CLASSE DE FIF → Classe FIF... É o que o Mais Retorno faz: "Caixa 300 Previdenciário FIC Classe FIF RF RL" | viável com os arquivos atuais |
| Denominação anterior (CVM `cad_fi_hist.zip`) | A CVM publica o histórico do cadastro, inclusive das denominações. O nome que o Investidor10 mostra ("FIC DE FI PREVIDENCIÁRIO CAIXA RF 300") é o **nome antigo**, de antes da adaptação à RCVM 175 (o Mais Retorno ainda tem a página antiga `fic-fi-previdenciario-caixa-rf-300`, que leva ao nome novo) | arquivo **não baixado** (o proxy desta sessão recusa a CVM); conferir o conteúdo |
| ANBIMA (nome comercial) | Cadastro próprio da ANBIMA, por API com registro | não levantado; nova fonte externa |

Recomendação (a decidir): rótulo = **abreviação automática** da denominação atual; a **busca** também procura nas
denominações anteriores do `cad_fi_hist.zip` (quem conhece o fundo pelo nome antigo o encontra), com o CNPJ formatado
como dado secundário. Chave continua o CNPJ (D3).

**Decisões de 27/10/2026 (aprovadas pelo Guilherme):**

- **I1 aprovado:** todas as classes vivas entram no cadastro e na busca, sem filtro por exclusivo ou público-alvo
  (ficam só como informação do fundo); cancelados atrás do "mostrar todos". Substitui o filtro do D2 e fecha o
  critério do D11.
- **D3 = (b) abreviação automática**, sem fonte nova e sem curadoria.

**`cad_fi_hist.zip` (baixado em 27/10 para `carga/data/cvm/fi/`):** 19 arquivos de histórico do cadastro antigo, entre
eles `cad_fi_hist_denom_social.csv` e **`cad_fi_hist_denom_comerc.csv` (denominação comercial — nome curto oficial da
CVM)**. Para o fundo do Guilherme:

- denominação comercial vigente: **"CAIXA FIC PREV 300 RF"** (desde 2011);
- denominação social de 2011 a 2025: "FUNDO DE INVESTIMENTO EM COTAS DE FUNDOS DE INVESTIMENTO PREVIDENCIÁRIO CAIXA
  RENDA FIXA 300" — abreviada, é exatamente o "FIC DE FI PREVIDENCIÁRIO CAIXA RF 300" do Investidor10 (ele abrevia o
  nome social **anterior** à adaptação, sem atualizar).

Cobertura nas 25.423 classes FIF vivas: **10.895 (43%) têm denominação comercial**; as demais foram registradas de
2023 em diante (o histórico antigo parou na RCVM 175). Mediana de 65 caracteres (máx. 100), 4 grupos repetidos.

**Rótulo (aprovado em 27/10/2026):** base = denominação comercial vigente quando houver, senão a denominação
social atual; sobre a base, a abreviação automática. Busca por CNPJ, denominação social atual, comercial e as
anteriores (`cad_fi_hist_denom_*`). **Onde mora:** nem ticker nem máscara — o `ticker` segue o CNPJ (chave e rotas),
`assets.name` guarda a denominação social completa e o nome curto fica numa coluna da tabela do fundo, recalculada a
cada atualização do cadastro; as telas mostram o nome curto onde os demais tipos mostram o ticker.

**D16 — aprovado (27/10/2026):**

| Tipo | Quantidade | Preço | Valor | Data |
|---|---|---|---|---|
| RF | — | — | informado | data da transação |
| Ações, TD, FII... | informada | informado | calculado | data da transação |
| **FI** | **calculada** = Valor ÷ Preço (não editável; se não bater com o extrato, corrige-se o Preço) | **informado**, pré-preenchido com a cota CVM da data da transação | **informado** | data da transação (mesmo campo dos demais) |

**D3 — resumo do Guilherme:** FI preenche a mesma coisa (o ticker), com o mesmo componente dos outros tipos; rótulo e placeholder podem ser revistos para todos os tipos. Detalhe técnico conferido: o campo continua sendo o `TickerAutocomplete`
(`TransactionFormModal`), só com rótulo, placeholder e linha do resultado próprios para fundo. Ao escolher, ele grava
no formulário **só o `ticker`** (o CNPJ), como hoje; a transação vai à API com `ticker` (`TransactionCreateDTO`) e o
backend acha o ativo. **Nada é gravado em `assets` pelo formulário**: o ativo, o nome social (`assets.name`) e o nome
curto (tabela do fundo) já existem, vindos da rotina de cadastro. Pontos para o Impact: o campo hoje aceita texto
digitado como ticker (`toUpperCase`) — para fundo, só seleção da lista; os textos que exibem o ticker (toasts "Compra de
X registrada", "Ticker (Ativo)" na edição, preset "Comprar X") passam a mostrar o nome curto.

**Respostas de 27/10/2026 (2ª rodada):** **D4, D6 (a), D8 e D19 (a) aprovados** pela recomendação.

- **D7 — esclarecimento:** nada de dado é apagado. O que existe em dobro é o **índice** (a estrutura de busca do banco,
  como o índice remissivo de um livro), não as linhas: `asset_price_history` tem dois índices idênticos sobre
  `(asset_id, reference_date)` — `uk_price_history_day` (o que garante "um preço por ativo por dia") e
  `idx_price_history_asset_date` (cópia sem função, 191 MB); `assets` tem `uk_assets_ticker` e a cópia
  `idx_assets_ticker`. A proposta é apagar só as duas cópias. Aguardando decisão.
- **D18 — "mostrar todos":** na lista da busca aparecem só os fundos vivos; no fim da lista, quando houver encerrados que
  casam com o texto, uma linha "Mostrar N fundos encerrados" os inclui (para lançamento retroativo).
- **D18/D10 — agrupamento (revisão proposta):** a classificação CVM dá só 5 grupos (Multimercado 13.617, Renda Fixa
  7.454, Ações 4.144, FMP-FGTS 93, Cambial 65, vazio 50) e mistura previdência com o resto. A classificação ANBIMA
  (66 valores; 129 vazias) tem dois níveis úteis: o 1º (Renda Fixa, Multimercados, Ações, **Previdência**, Cambial,
  Fechados...) e o completo (ex.: "Renda Fixa Duração Livre Crédito Livre"). Categorias resultantes (contagem exata das 25.423 classes FIF vivas, já com a classificação CVM cobrindo quem não tem ANBIMA): **Multimercado** 10.867, **Renda Fixa** 6.108, **Previdência** 4.396, **Ações** 3.937 (inclui "Fechados de Ações", "Mono Ação" e FMP-FGTS), **Cambial** 65 e **Outros** 50 (sem classificação ANBIMA nem CVM) — 6 categorias. Proposta: **categoria = 1º nível ANBIMA**
  (com Previdência como grupo próprio; vazia → classificação CVM) e a **classificação ANBIMA completa** como atributo do
  fundo (texto na tabela do fundo, como o segmento das ações — sem tabela nova), exibida e filtrável. Aguardando decisão.

## 8. Próximos passos

1. Guilherme responde D1–D19.
2. **Impact**: componentes afetados (core, adm-api, app-api, web), custo de D6 nos leitores de preço, os 52 saltos e as
   271 chaves canceladas que reportam, espaço medido no banco, bucket e `BOOTSTRAP`.
3. **Planning**: migration, tarefa, regras, telas, critérios de aceitação e "Quem faz".

## 9. Andamento

| Data | Etapa | O que aconteceu |
|---|---|---|
| 26/10/2026 | Discovery | Levantamento do código, do banco e dos arquivos em `carga/data/cvm/fi`; decisões D1–D16 abertas |
| 27/10/2026 | Discovery | Revisão: impostos deixam de ser tratados como regra (D17 nova); CNPJ alfanumérico no D3; D5/D16 sem esperar a série (cota do mês lida na hora); D6 revista para (a); D18 (visibilidade) e D19 (bucket) novas; numeração das recomendações alinhada ao texto do escopo (7 e 8) |
| 27/10/2026 | Discovery | Respostas: D1, D2, D5, D9, D10, D12–D15 e D17 aprovadas pela recomendação; D3 (chave × rótulo, subclasse como ativo), D6, D7, D8, D16 e D19 respondidas e aguardando confirmação; D4, D11 e D18 dependem de D3 (seção 7.1) |
| 27/10/2026 | Discovery | Impedimentos (seção 7.2): previdência tem que entrar — revisão de D2/D11 sem filtro por exclusivo/público; D3 decidido como (b) nome curto sem curadoria, fonte do nome em aberto |
| 27/10/2026 | Discovery | Aprovados I1 (previdência e todas as vivas na busca) e D3 (b) abreviação automática; `cad_fi_hist.zip` traz a denominação comercial (43% das vivas) — proposta de rótulo e onde guardar aguardando confirmação |
| 27/10/2026 | Discovery | Aprovado o rótulo: denominação comercial vigente (senão a social atual) + abreviação automática; ticker = CNPJ só como chave, nunca exibido |
| 27/10/2026 | Discovery | **D3, D11 (pelo I1) e D16 fechados.** Pendentes: D4, D6, D7, D8, D18 e D19 |
| 27/10/2026 | Discovery | D4, D6, D8 e D19 aprovados; D7 esclarecido (só índices em cópia); D18 com proposta de categoria pelo 1º nível ANBIMA (revisa D10) |
| 27/10/2026 | Discovery | D7 aprovado (apagar só as cópias de índice) |
| 27/10/2026 | Discovery | D18 aprovado (6 categorias pelo 1º nível ANBIMA) — Discovery encerrado |
| 27/10/2026 | Impact | Seção 10: componentes, conferências dos 52 saltos e das 271 chaves, riscos; decisões novas D20 e D21 |
| 27/10/2026 | Impact | D20 (a) e D21 (a) decididos; logo do tipo FI salvo em `activest-web/src/assets/logos/fundo-investimento.png`; levantamento do `GET /api/v1/assets/` sem filtro (10.6) |
| 27/10/2026 | Planning | Seção 11: modelo `V12`, regras C1–C12, seed de impostos de fundo, `FUND_QUOTES`, API, front, etapas F1–F9, critérios, Quem faz; decisões D22–D28 abertas |
| 27/10/2026 | Planning | Seed 11.3 validado pelo Guilherme (conferido: MP 1.303/2025 caducou; valem 15%/20% de come-cotas e a tabela regressiva) |
| 27/10/2026 | Planning | D22–D28 aprovadas pela recomendação; implementação autorizada (11.11) |
| 27/10/2026 | Implementation | F1 escrita (V12 aplicada no DEV às 13:49, entidades, `FundLabel`, `FundKeys`, `FundCategory`, `FundDailyReportReader` e testes); F2 escrita (`FundCatalogService`, `FundRegistry`, `CvmFundFiles`, `FundCatalogIT`). Carga real da F2 falhou duas vezes (14:07 e 14:24): edições em `src/` durante a carga reiniciaram a adm-api (devtools) — DEV com 0 fundos. Código de F3–F8 adiantado sem teste |
| 27/10/2026 | Implementation | Retomada em nova sessão: diagnóstico acima; código de F3–F8 mantido (11.11); F1 e F2 sendo fechadas antes de seguir |
| 27/10/2026 | Implementation | **F1 fechada:** `activest-core` verde em `mvn clean verify` (246 testes, +29: `FundLabelTest`, `FundKeysTest`, `FundDailyReportReaderTest` com os 3 layouts, `FundTaxTest`, `SchemaTest` na V12) |
| 27/10/2026 | Implementation | F2 — carga real no DEV (`FUND_QUOTES` com `FROM_MONTH` = 01/2010, mesma varredura do `BOOTSTRAP`, ~7 min). Dois defeitos achados e corrigidos: (1) **cota absurda no informe** (ex.: 90.335.366.100,00 num FIC em 12/2020) estourava `NUMERIC(18,8)` — cota ≥ 10^10 passa a ser descartada como dado inválido da fonte (`FundDailyReportReader.usableQuote`, também na cota da Nova transação); (2) **CNPJ com registros de vários tipos** (7.093 no `registro_fundo`, ex.: FI cancelado + FIDC vivo) fazia entrar ~600 chaves como FIDC/FIP/FII/FIIM vivos — o registro de tipo que não informa cota (D1) passa a servir só de nome (`FundRegistry.informeType`). Bateria da adm-api: `PostgresContainer` com `max_connections=300` (o contexto novo da `FundCatalogIT` passou das 100 conexões) |
| 27/10/2026 | Implementation | F2 — carga real conferida: **54.741 fundos** (53.211 classes/fundos + 1.530 subclasses), **28.342 vivos**; classes FIF/FAPI "Em Funcionamento Normal" 25.418 (medição: 25.423), pré-operacionais 1.162, em liquidação 184, 89 vivos só pela cota (cadastro cancelado); só tipos FIF, FI, FAPI, FACFIF e FMP-FGTS. Categorias dos vivos: Multimercado 12.215, Renda Fixa 6.948, Previdência 4.642, Ações 4.272, Outros 194, Cambial 71. Fundo do Guilherme: **CAIXA FIC PREV 300 RF**, Previdência, cota 9,69853027 de 23/10/2026 = arquivo (9,698530266). Tabelas: `investment_funds` 57 MB, `investment_fund_names` 42 MB (262 mil nomes), `assets` 35 MB |
| 27/10/2026 | Implementation | **F2 fechada:** `mvn -pl activest-adm-api -am clean verify` verde — core 247, adm-api 133 + 90 (`FundCatalogIT` 3, com o CNPJ de vários tipos e a cota absurda; `TaskControllerIT` com 29 tarefas). Atributo `FROM_MONTH` da `FUND_QUOTES` devolvido a vazio no DEV. A etapa "fundos" do `BOOTSTRAP` faz a mesma varredura e fica para a prova do zero na pilha de teste (F9) |
| 27/10/2026 | Implementation | **F3 fechada:** rodada diária real (mês corrente + 12 anteriores) em **54 s**, cota do fundo do Guilherme = arquivo; "gravar só o que mudou" (D13) também no cadastro — `assets` e `investment_funds` só recebem UPDATE quando algum campo muda, e os nomes de busca só são regravados inteiros quando pedido (senão só os dos fundos novos); CVM que recusa com cópia em disco termina a rodada em `FAILED` com o resumo gravado, como no Tesouro (`FundQuotesTaskTest` 5). Uma rodada automática por dia: `oncePerDay` do executor (coberto no `TaskExecutorTest`). Bateria `-pl activest-adm-api -am`: core 247, adm-api 138 + 90, verde |
| 27/10/2026 | Implementation | **F4 fechada:** série sob demanda pelo canal que já existia, `activest:dividends:on-demand`, e a mesma fila única dos proventos e da cripto (a mensagem é o id do ativo; a adm-api desvia o fundo para `loadSeries`) — no lugar do canal novo `activest:funds:on-demand` da C11, sem mudar o contrato. No DEV: série do 03.926.431/0001-71 de 04/01/2010 a 23/10/2026, **4.222 cotas em 98 s** (eram 419 s: o leitor passou a filtrar a chave antes de converter números e o CNPJ deixou de usar regex); pedido repetido = "já carregada", sem duplicar (`FundCatalogIT.series`). Bateria `-pl activest-adm-api -am` verde (core 247, adm-api 138 + 90) |
| 27/10/2026 | Implementation | **F5 fechada:** busca no DEV com 54.741 fundos — "RF 300" acha só o **CAIXA FIC PREV 300 RF**; "previnvest" (nome de 2000–2004) e o CNPJ com ou sem máscara também o acham; respostas de 26 a 260 ms (a primeira, a frio, 2,5 s); sem `query` → 400; `GET /funds/03926431000171/quote?date=2026-10-25` → cota de 23/10/2026, 9,69853027 (fim de semana carrega a última) |
| 27/10/2026 | Implementation | **F6 fechada:** regra B do fundo com as cotas retidas no come-cotas e o "resgatar tudo" pelo valor do extrato ajustado ao saldo quando a sobra vale até R$ 0,01 (`PositionGuard.validateFundLedger`); `FundTransactionsIT` 4 (compra pelo valor, valor/cota obrigatórios, resgate e bloqueio acima do saldo, ajuste do arredondamento) |
| 27/10/2026 | Implementation | **F7 fechada:** líquido estimado de um resgate total hoje (C10: `FundTax.estimate`, `estimatedNet` na Carteira e no patrimônio líquido do Dashboard, base `COTA`; Previdência = bruto com o aviso) e a prévia do resgate (`POST /transactions/sale-preview` para fundo: bruto, IOF, IR e líquido pelo PEPS); `FundTaxTest` 7, `FundTaxesIT` 4 (come-cotas de 29/05/2026 em cotas, resgate PEPS descontando o já retido, IOF de 10 dias, Previdência) |
| 27/10/2026 | Implementation | **F8:** formulário do fundo (Valor, Cota pré-preenchida, cotas calculadas só leitura, prévia do resgate no ícone do Total bruto, aviso da Previdência); nome curto no lugar do CNPJ em Carteira (aba do tipo, líquido estimado), Transações (lista, filtro, busca, mensagens), Dashboard, Rentabilidade (contribuição, filtros, avisos de cobertura) e Metas (opções, cartões, detalhe) — `assetText`/`useAssetLabel`; `tsc -b`, `eslint` e `vite build` verdes. Conferência no navegador: roteiro na seção 12, com o Guilherme |
| 27/10/2026 | Implementation | **F9:** `Claude.md` (Pontos de Atenção "Fundos de investimento", tabelas, V12, tipo 6 `CVM`, endpoints, testes), `ACTIVEST_DB.md` (5d), `PENDENCIAS.md` (39; 29.7 resolvido nos fundos), `SCHEDULE_TASKS.md` ([A17], [A8]), `ADM_TASKS.md`, `USE_CASES.md` (UC46, UC47), `README.md`; `data push` (bucket = `carga/data`, `cvm` 3,1 GB com `cvm/fi`, conferido por `data status`); `FundCatalogIT.bootstrap` cobre a etapa do `BOOTSTRAP` (prova do zero na pilha de teste: PENDENCIAS 39.10) |
| 27/10/2026 | Testing | `mvn clean verify` verde: core 248, integration 103, adm-api 138 + 91, app-api 236 + 305 (o `TaskExecutorTest.runLog`, que depende de tempo, falhou numa rodada e passou na seguinte); front `tsc -b`, `eslint`, `vite build` verdes |
| 27/10/2026 | Review | Código da sessão anterior revisado etapa a etapa; correções feitas: cota absurda, CNPJ de vários tipos, "só o que mudou" no cadastro, CVM recusada → `FAILED`, série sob demanda 4× mais rápida, regra B com come-cotas e arredondamento do resgate, líquido estimado e prévia do resgate, telas sem CNPJ. Observação: a carga real da F2 e uma rodada `B3_CLOSING` manual do Guilherme (17:39) caíram por reinício da adm-api (devtools) durante edições — a `B3_CLOSING` foi rodada de novo (SUCCEEDED, 25 ativos) |
| 27/10/2026 | Commit | Review executado pelo Guilherme; **escopo encerrado**. Mensagem sugerida: `TRACK_01 - Fundos de investimento da CVM: cadastro, cota diária, série sob demanda, lançamento por valor e cota, IR/IOF/come-cotas e nome curto nas telas` (branch `TRACK_01-*` a partir da `develop`, PR — REGRAS 3.3/3.4) |

## 10. Impact — análise de impacto (27/10/2026)

### 10.1 Resumo das decisões que entram no Impact

Chave = CNPJ sem pontuação (alfanumérico) no `ticker`, subclasse `CNPJ-ID_SUBCLASSE` como ativo próprio (D3); rótulo =
denominação comercial vigente, senão a social atual, abreviada (D3/7.2); todas as classes FIF vivas no cadastro e na
busca, cancelados no "mostrar todos" (I1/D11); série de cota só de quem está em carteira, sob demanda, mais a última
cota de cada fundo vivo (D5); cota em `asset_price_history` com `source = CVM` (D6); fonte `CVM` e tarefa
`FUND_QUOTES` com `oncePerDay` (D8); mês corrente + 12 meses de retificação (D9); categorias = 1º nível ANBIMA:
Multimercado, Renda Fixa, Previdência, Ações, Cambial, Outros (D10/D18); FI com Valor e Preço informados e Quantidade
calculada, não editável (D16); IR + IOF + come-cotas como a RF (D17); apagar as cópias de índice (D7).

### 10.2 Componentes afetados

| Camada | Componente | Impacto |
|---|---|---|
| Banco (migration nova) | `asset_types` (id 6) | `price_source` `MANUAL` → `CVM`; `ck_asset_types_price_source` e `ck_price_history_source` ganham `CVM` |
| | `asset_categories` | 6 categorias novas do tipo 6 |
| | tabela nova do fundo (1:1 com `assets`) | CNPJ, id da subclasse, classe-mãe, nome curto, denominação comercial, ANBIMA completa, classificação CVM, público-alvo, exclusivo, condomínio, tributação de longo prazo, gestor e administrador (texto + CNPJ), situação e datas, PL e data |
| | nomes anteriores | denominações sociais e comerciais do `cad_fi_hist.zip` para a busca (tabela filha ou coluna de texto — Planning) |
| | `assets` | **+~53 mil linhas** (hoje 2.626; ~400 bytes/linha → ~20 MB, mais a tabela do fundo de tamanho parecido) |
| | índices | apagar `idx_price_history_asset_date` (191 MB) e `idx_assets_ticker` (D7) |
| | `fixed_income_tax_rules` | hoje só "regra geral RF/TD" ou exceção por ativo; fundo precisa de **regime** (curto prazo, longo prazo, ações) — coluna nova ou regras por tipo (Planning) |
| core | `PriceSource` | valor `CVM` |
| | `OpenPositions.assetsWithShares(CVM)` | serve à rotina diária (só fundos em carteira) sem mudança |
| | cálculo de IR/IOF (`FixedIncomeTaxCalculator`, `FixedIncomeLots`) | reaproveitável lote a lote; **come-cotas é cálculo novo** (reduz cotas em maio/novembro, por lote) |
| adm-api | tarefa `FUND_QUOTES` (nova) | cadastro (`registro_fundo_classe.zip`, `cad_fi_hist.zip`) + informe (mês corrente e 12 anteriores) pelo `PublicFileCache`; grava a última cota de todos os vivos em `assets.current_price` e a série de quem está em carteira |
| | carga sob demanda da série | canal novo no padrão de `activest:dividends:on-demand` (`CatalogMessaging`): primeira transação de um fundo → adm-api lê os 81 ZIPs e grava a série |
| | `BootstrapService` | etapa nova "fundos": cadastro + última cota (sem série) |
| | `AssetLogoService`/`AssetLogos.typeHasLogo` | **não** incluir o tipo (53 mil buscas de logo); fundo usa o ícone do tipo |
| | `CatalogAuditService` | `assetRepository.findAll()` passa de 2,6 mil para ~55 mil linhas — conferir custo |
| | `DividendCatalogService` | filtra por `BRAPI`/`COINGECKO`: fundos ficam de fora sem mudança |
| app-api | `TransactionService` | regra do FI (Valor + Preço → Quantidade); IR/IOF/come-cotas no resgate; `importTransaction` já recusa o que não sabe descrever |
| | `GoalFactsLoader` (`MANUAL` → `noPrice`) | fundo deixa de ser `MANUAL`: Metas e Rentabilidade passam a ter série (fecha a parte de fundos do PENDENCIAS 29.7) |
| | `PortfolioTimelineBuilder`, valorização, `/prices` | leem `asset_price_history` — funcionam com `source = CVM` (D6) |
| | `AssetRepository.searchByTypeAndTickerOrName` | busca por CNPJ com/sem pontuação, nome curto, social e anteriores, com "mostrar todos"; com ~53 mil fundos o `LIKE` precisa de índice adequado (ex.: `pg_trgm`, não instalado hoje) |
| | `GET /api/v1/assets/` sem parâmetro | devolve **todos** os ativos (`getAllAssets`); com fundos vira ~55 mil itens — o front não usa sem `query`, mas o endpoint precisa de limite |
| web | `TickerAutocomplete` + `TransactionFormModal` | rótulo/placeholder, linha de resultado (nome curto + CNPJ + categoria), só seleção (hoje aceita texto digitado como ticker), campos do D16 |
| | exibição do ticker | **23 arquivos .tsx** usam `.ticker` (Carteira, Transações, Dividendos, Dashboard, Rentabilidade, Metas, Integrações...). Não há helper de rótulo: é preciso um (`assetLabel`) para fundo mostrar o nome curto |
| | Alocação / Estratégia / Rebalanceamento | tipo 6 com as 6 categorias |
| carga | `data.sh` | `cvm/fi` (1,4 GB + `cad_fi_hist.zip`) no `pull` completo, fora do `--minimo` (D19); `data push` sobe a pasta |

### 10.3 Conferências pendentes do Discovery

- **52 saltos de cota na troca de layout (11/2023 → 12/2023):** não são efeito do layout. Não há razão fixa entre as cotas
  (a maioria entre ±5% e ±13%); os casos extremos são fundos exclusivos de 1 a 4 cotistas com PL mudando junto, e um
  fundo zerado (encerramento). A série liga pelo CNPJ sem tratamento (D4 confirmado).
- **271 chaves que reportam cota em 09/2026 sem estar no `registro_classe`:** 265 são `CLASSES - FIF` de fundos
  adaptados à RCVM 175 com registro de fundo "Cancelado", 6 são `FI` antigos (4 em liquidação, 2 em funcionamento).
  Consequência: **"vivo" não pode vir só da situação do cadastro** — a regra tem que ser "reportou cota recentemente
  OU está em funcionamento no cadastro", e o nome vem do `registro_fundo` quando a classe não está no `registro_classe`.

### 10.4 Riscos e efeitos colaterais

| Risco | Efeito | Mitigação |
|---|---|---|
| Previdência e impostos (D17) | Fundo de previdência **não tem come-cotas nem IOF, e o IR é do plano** (PGBL/VGBL, tabela progressiva ou regressiva de 35% a 10% escolhida pelo participante), não do fundo. Aplicar a regra de FIF comum à categoria Previdência daria imposto errado | **D21** |
| Busca do topo (D18) | A "busca do topo" é a `IssuerSearch` da tela de Ativos: só lista ativos com emissor e abre `/ativos/:ticker`. Fundo não tem emissor nem página nesta fase (D18) | **D20** |
| Volume em `assets` | Telas e rotinas que percorrem o catálogo inteiro ficam ~20× maiores (auditoria, logos, endpoint sem filtro) | itens da 10.2; teste de desempenho no Testing |
| Busca com ~53 mil nomes longos | `LIKE '%...%'` sem índice fica lento | índice de texto (Planning) |
| Série sob demanda | 2,5 min de leitura em segundo plano por fundo novo; duas transações seguidas de fundos diferentes enfileiram | fila única na adm-api (padrão dos proventos); a cota do dia do lançamento sai na hora (D5) |
| Retificação da CVM | Cota retificada nos 12 meses muda a valorização passada | gravar só a diferença e avisar a app-api (`FECHAMENTO`) com os ativos afetados |
| Ticker = CNPJ visível | Onde a tela não usar o helper de rótulo, aparece o CNPJ cru | helper único + conferência nas 23 telas |

### 10.5 Decisões novas (Impact)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D20** | Fundo na busca da tela de Ativos | (a) não aparece nesta fase (a busca abre a página do ativo, que fundo não tem); (b) aparece e abre uma página mínima do fundo (cadastro + cota) | **(a)** — coerente com o D18 (sem página de fundo nesta fase); a página do fundo fica para um escopo próprio |
| **D21** | Impostos da categoria Previdência | (a) fora: previdência vale bruto (o IR é do plano, não do fundo), com a limitação registrada; (b) entra: regime do plano (progressiva/regressiva) informado pelo usuário por posição | **(a)** — o imposto da previdência depende do plano e da escolha do participante, dados que a CVM não tem; IR/IOF/come-cotas do D17 valem para as demais categorias |

### 10.6 Respostas do Guilherme (27/10/2026)

- **D20 = (a):** FI não tem emissor no Activest, portanto não entra na busca da tela de Ativos (`IssuerSearch` só
  lista ativos com emissor).
- **D21 = (a)** pela recomendação: Previdência vale bruto (IR do plano fora), com a limitação registrada; IR + IOF +
  come-cotas (D17) para as outras 5 categorias.
- **Logo do tipo:** o FI usa a imagem enviada pelo Guilherme (mão com saco de dinheiro, fundo azul), salva em
  `activest-web/src/assets/logos/fundo-investimento.png` (reduzida para 256×256, 53 KB). Entra como logo do tipo em
  `typeLogo` (`utils/assetTypeRules.ts`), como o do Tesouro Direto — e por isso o tipo fica fora do `ASSET_LOGOS`.
- **Endpoint sem filtro — levantamento:** é um só, `GET /api/v1/assets/` (`AssetController.listAssets`), com quatro
  comportamentos pelos parâmetros:

  | Parâmetros | Devolve | Quem usa |
  |---|---|---|
  | `query` (+ `typeId`) | busca limitada (`SEARCH_LIMIT`) | `TickerAutocomplete` (Nova transação), só com 2+ caracteres |
  | `query` + `withIssuer=true` | busca limitada, só ativos com emissor | `IssuerSearch` (tela de Ativos e Preço Teto) |
  | só `typeId` | **todos os ativos do tipo** (`getAssetsByType`) | ninguém no front nem nos scripts de `carga/scripts` |
  | nenhum | **o catálogo inteiro** (`getAllAssets`) | ninguém no front nem nos scripts de `carga/scripts` |

  Os dois últimos ficam abertos a qualquer chamada autenticada; com os fundos, "só `typeId` = 6" devolveria ~53 mil
  linhas e "nenhum" ~55 mil. Proposta para o Planning: exigir `query` (ou paginar), sem mudar o comportamento das telas.

## 11. Planning — solução técnica (27/10/2026)

Nomes em inglês (REGRAS 9); campo `NOT NULL` novo em tabela existente com default (REGRAS 8.1).

### 11.1 Modelo de dados — `V12__investment_funds.sql`

**Alterações em tabelas existentes**

| Tabela | Mudança |
|---|---|
| `asset_types` | id 6 (`FUNDO_INVESTIMENTO`): `price_source` `MANUAL` → `CVM`; `ck_asset_types_price_source` aceita `CVM` |
| `asset_price_history` | `ck_price_history_source` aceita `CVM`; **apaga `idx_price_history_asset_date`** (D7) |
| `assets` | **apaga `idx_assets_ticker`** (D7) |
| `asset_categories` | 6 linhas do tipo 6: Multimercado, Renda Fixa, Previdência, Ações, Cambial, Outros (D18) |
| `fixed_income_tax_rules` | coluna `regime VARCHAR(20) NOT NULL DEFAULT 'GENERAL'` (`CHECK GENERAL / FUND_LONG_TERM / FUND_SHORT_TERM / FUND_EQUITY`); `tax` aceita `CC` (come-cotas). Linhas atuais ficam `GENERAL` (RF e TD, sem mudança). Seed das regras de fundo (11.3) com `legal_basis` |
| `task_types` / `tasks` | tipo e tarefa `FUND_QUOTES` (adm-api), cron da manhã, `run_on_startup = true` |

**Tabelas novas**

| Tabela | Colunas | Observação |
|---|---|---|
| `investment_funds` | `asset_id` PK/FK `assets` (cascade), `cnpj VARCHAR(14)` (sem pontuação, alfanumérico), `subclass_id VARCHAR(20)` (nulo na classe), `parent_asset_id` FK `assets` (subclasse → classe), `short_name VARCHAR(120)`, `trade_name VARCHAR(120)` (denominação comercial vigente), `fund_kind VARCHAR(10)` (`FIF`/`FI`/`FAPI`), `cvm_classification`, `anbima_classification VARCHAR(80)`, `target_audience VARCHAR(20)`, `exclusive BOOLEAN`, `open_ended BOOLEAN`, `long_term_tax BOOLEAN`, `manager_name`, `manager_cnpj`, `administrator_name`, `administrator_cnpj`, `cvm_status VARCHAR(40)`, `cvm_status_since DATE`, `started_on DATE`, `net_worth NUMERIC(20,2)`, `net_worth_date DATE`, `last_quote_on DATE`, timestamps | Único (`cnpj`, `coalesce(subclass_id,'')`). `assets.name` = denominação social atual; `assets.ticker` = `cnpj` ou `cnpj-subclass_id`; `assets.active` = "vivo" (C2) |
| `investment_fund_names` | `id`, `asset_id` FK cascade, `name VARCHAR(200)`, `kind VARCHAR(10)` (`LEGAL`/`TRADE`), `valid_from`, `valid_to` | Nomes atuais e anteriores (`cad_fi_hist_denom_social` e `_denom_comerc`) — só para a busca |
| `fund_tax_events` | `id`, `user_id`, `asset_id`, `lot_transaction_id` (compra que originou o lote), `event_date`, `kind` (`COME_COTAS`), `quote NUMERIC(18,8)`, `base_quote NUMERIC(18,8)`, `shares_before`, `shares_withheld NUMERIC(18,8)`, `tax_amount NUMERIC(18,2)`, `rate NUMERIC(6,4)`, `fixed_income_tax_rule_id` FK, timestamps | Só se D22 = (a): come-cotas gravado e auditável |

Índice de busca: extensão `pg_trgm` e índice GIN sobre o texto normalizado (sem acento, maiúsculas) de
`short_name`, `assets.name` e `investment_fund_names.name`, mais o `cnpj` (D25).

### 11.2 Regras

| # | Regra |
|---|---|
| C1 | **Rótulo** (`FundLabel`): base = denominação comercial vigente; sem ela, a denominação social atual. Abreviação automática por tabela fixa, aplicada na ordem: FUNDO DE INVESTIMENTO EM COTAS DE FUNDOS DE INVESTIMENTO → FIC FI; FUNDO DE INVESTIMENTO EM COTAS → FIC; FUNDO DE INVESTIMENTO FINANCEIRO → FIF; FUNDO DE INVESTIMENTO → FI; CLASSE DE INVESTIMENTO EM COTAS → CIC; CLASSE DE → (remove "DE"); RESPONSABILIDADE LIMITADA → RL; RENDA FIXA → RF; MULTIMERCADO → MULT; CRÉDITO PRIVADO → CP; INVESTIMENTO NO EXTERIOR → IE; PREVIDENCIÁRIO → PREV; LONGO PRAZO → LP; REFERENCIADO → REF; " - " finais e espaços duplos limpos. Sem corte de tamanho (a tela usa reticências). Recalculado a cada atualização do cadastro |
| C2 | **Vivo** (`assets.active`): classe/subclasse "Em Funcionamento Normal", "Fase Pré-Operacional" ou "Em Liquidação" no cadastro **ou** com cota informada nos últimos 30 dias (D26) — cobre as 271 chaves "canceladas que reportam" (10.3). Nome, quando a classe não está no `registro_classe`, vem do `registro_fundo` |
| C3 | **Categoria**: 1º nível ANBIMA (Multimercados → Multimercado; Renda Fixa; Previdência; Ações, Fechados de Ações e Mono Ação → Ações; Cambial); sem ANBIMA útil → classificação CVM (FMP-FGTS → Ações); sem nenhuma → Outros |
| C4 | **Cota**: `close_price` = `VL_QUOTA` (arredondada a 8 casas, HALF_EVEN), OHLC e volume nulos, `source = CVM`. `assets.current_price` = última cota informada; `price_updated_at` = data dela |
| C5 | **Leitura do informe** (`FundDailyReportReader`, core): acha as colunas pelo cabeçalho de cada CSV (os 3 layouts de 4.4); chave = CNPJ sem pontuação + `ID_SUBCLASSE` quando houver; lê ZIP anual (`hist/`, até 2020) e mensal |
| C6 | **Compra de FI** (D16): informados Valor e Preço (cota); Preço pré-preenchido com a cota da data da transação (sem cota na data — fim de semana, feriado, atraso — a última anterior, com a data mostrada); `shares` = Valor ÷ Preço, 8 casas, HALF_EVEN, não editável; `total_amount` = Valor |
| C7 | **Resgate de FI** (D24): mesmos campos — Valor bruto e Preço → cotas; saída dos lotes por **PEPS**; o Activest calcula IOF, IR e líquido (como a venda de RF); cotas acima da posição → bloqueia |
| C8 | **Regime de imposto** do fundo: categoria Previdência → **sem imposto** (D21, bruto, aviso "IR do plano não calculado"); Ações → `FUND_EQUITY` (IR 15% no resgate, sem come-cotas, sem IOF); demais com `long_term_tax = true` → `FUND_LONG_TERM` (IR 22,5/20/17,5/15% por prazo, come-cotas 15%); `false` → `FUND_SHORT_TERM` (IR 22,5% até 180 dias, 20% depois, come-cotas 20%); IOF regressivo nos primeiros 30 dias (tabela `GENERAL`) para LP e CP |
| C9 | **Come-cotas**: no último dia útil de maio e de novembro, por lote vivo: se cota do dia > cota-base (compra ou último come-cotas), IR = (cota − base) × cotas × alíquota `CC` do regime; cotas retidas = IR ÷ cota; nova base = cota do dia. Cota ≤ base → sem retenção (compensação de prejuízo fora, registrada como limitação). No resgate: IR = alíquota do prazo × rendimento total das cotas resgatadas − come-cotas já retido sobre elas |
| C10 | **Posição de FI**: cotas = compras − resgates − cotas retidas no come-cotas; bruto = cotas × cota atual; líquido estimado = bruto − IR/IOF de um resgate total hoje (Previdência: líquido = bruto) |
| C11 | **Série sob demanda** (D5): primeira transação de um fundo sem série → app-api publica `activest:funds:on-demand`; a adm-api (fila única) lê os ZIPs de 2010 em diante só daquela chave e grava em lote (`JdbcTemplate.batchUpdate`, só o que falta); ao terminar, "catálogo mudou" (`FECHAMENTO`) para revalorizar |
| C12 | **Endpoint sem filtro** (D27): `GET /api/v1/assets/` exige `query` (com ou sem `typeId`/`withIssuer`); sem `query` → 400. Nenhuma tela muda (10.6) |

### 11.3 Seed das regras de imposto de fundo — ✅ validado pelo Guilherme (27/10/2026)

> Conferência de 27/10/2026: a Lei 14.754/2023, art. 17, fixa o come-cotas no último dia útil de maio e de novembro.
> A MP 1.303/2025 trocaria tudo por 17,5% único a partir de 01/01/2026, mas **perdeu a validade em outubro de 2025**
> (retirada de pauta na Câmara) — valem as alíquotas abaixo. Sites que mostram o texto "atualizado" com a MP
> (ex.: LegJur) estão desatualizados nesse ponto. Lei nova muda por migration de vigência (`valid_to`/`valid_from`),
> como as demais tabelas de imposto.

| Regime | Imposto | Faixas | Base legal (proposta) |
|---|---|---|---|
| `FUND_LONG_TERM` | IR | 0–180 d 22,5%; 181–360 d 20%; 361–720 d 17,5%; >720 d 15% | Lei 11.033/2004, art. 1º; Lei 14.754/2023 |
| `FUND_LONG_TERM` | CC | 15% | Lei 14.754/2023, art. 17 |
| `FUND_SHORT_TERM` | IR | 0–180 d 22,5%; >180 d 20% | Lei 11.033/2004, art. 1º, §3º; Lei 14.754/2023 |
| `FUND_SHORT_TERM` | CC | 20% | Lei 14.754/2023, art. 17 |
| `FUND_EQUITY` | IR | 15% | Lei 11.033/2004, art. 1º, §3º, II |
| `GENERAL` (já existe) | IOF | regressivo 30 dias | Decreto 6.306/2007, anexo |

### 11.4 Tarefa `FUND_QUOTES`

- `oncePerDay = true` (D8), cron da manhã (ex.: `0 30 7 * * *`), recuperação na subida, Run manual com atributo
  opcional `FROM_MONTH` (rebaixa a partir do mês).
- Rodada: baixa pelo `PublicFileCache` o `registro_fundo_classe.zip` e o `cad_fi_hist.zip` (cadastro, C1–C3) e os
  informes do mês corrente e dos 12 anteriores (304 quando não mudou — D9); atualiza o cadastro (cria fundo novo, marca
  vivo/encerrado, regrava nome curto e categoria); grava `current_price` de todos os vivos; para os fundos **em
  carteira** (`OpenPositions.assetsWithShares(CVM)`), grava/corrige a série nos meses baixados (só a diferença) e
  avisa a app-api com os ativos afetados.
- Fonte sem dado novo há mais de 2 dias úteis → `FONTE_DESATUALIZADA`; falha de download → `FONTE_INDISPONIVEL`.
- Come-cotas (C9): depois da cota do último dia útil de maio/novembro gravada, a app-api gera os eventos (D22) na
  revalorização.
- `BOOTSTRAP`: etapa "fundos" = cadastro + última cota de todos os vivos (sem série).

### 11.5 API

| Serviço | Rota | Uso |
|---|---|---|
| app-api | `GET /api/v1/assets/?typeId=6&query=&includeInactive=` | busca de fundo (CNPJ com/sem pontuação, nome curto, social, comercial e anteriores); resultado com `shortName`, `cnpjFormatted`, `category`, `anbimaClassification`, `active`, `exclusive`, `previdencia` |
| app-api | `GET /api/v1/funds/{ticker}/quote?date=` | cota da data (ou última anterior, com a data) para pré-preencher o Preço (C6) — lê o ZIP do mês (D23) |
| app-api | DTOs de ativo/posição/transação | `displayName` (= `shortName` no fundo, `ticker` nos demais) para o front usar em todo lugar |
| app-api | `GET /api/v1/assets/` sem `query` | 400 (C12) |

### 11.6 Front

- `utils/assetLabel.ts` (novo): `assetLabel(asset)` → `displayName`; trocar o `.ticker` exibido nos **23 arquivos**
  (10.2) pelo helper (o `ticker` continua nas rotas e requisições).
- `typeLogo('FUNDO_INVESTIMENTO')` → `@/assets/logos/fundo-investimento.png` (já salvo).
- `TickerAutocomplete`: com o tipo fundo, rótulo "Fundo", placeholder "Digite o nome ou CNPJ…", linha de resultado em
  duas linhas (nome curto; CNPJ · categoria · marcas Previdência/Exclusivo/Encerrado), "Mostrar N fundos encerrados" e
  só seleção da lista. Rótulo/placeholder genéricos para os demais tipos podem ser revistos junto (Guilherme citou).
- `TransactionFormModal` (FI): Preço (cota, pré-preenchido, editável), Valor, Quantidade calculada só leitura, data =
  Data da Transação; resgate com bruto, IOF, IR e líquido (componente da venda de RF); Previdência com o aviso de IR do
  plano.
- Carteira: aba Fundos com bruto e líquido estimado (como RF), categoria, logo do tipo.

### 11.7 Etapas de implementação

| # | Etapa | Conteúdo | Pronto quando |
|---|---|---|---|
| **F1** | Banco e core | V12; entidades; `PriceSource.CVM`; `FundLabel` (C1); `FundDailyReportReader` (C5); categorias (C3) | Flyway + Hibernate validam; testes do leitor com os 3 layouts e do rótulo (caso "CAIXA FIC PREV 300 RF") verdes |
| **F2** | Cadastro de fundos (adm-api) | importação do `registro_fundo_classe` + `cad_fi_hist` (C2), nomes para busca, etapa no `BOOTSTRAP` | Carga real: ~53 mil ativos tipo 6, vivos conferidos contra a medição (25.423 classes FIF vivas + subclasses + as 271 que reportam) |
| **F3** | Cota diária | tarefa `FUND_QUOTES` (11.4), `current_price` dos vivos, série dos fundos em carteira, avisos de fonte | Rodada real; 2ª rodada no dia pula (`oncePerDay`); cota do fundo do Guilherme = arquivo |
| **F4** | Série sob demanda | canal `activest:funds:on-demand`, fila, gravação em lote (C11) | Série 2010→hoje do 03.926.431/0001-71 gravada em minutos, sem duplicar |
| **F5** | Busca e API | índice `pg_trgm`, busca (11.5), `quote`, `displayName`, C12 | Busca "RF 300", "previnvest" e o CNPJ acham o fundo; resposta < 300 ms com ~55 mil ativos; sem `query` → 400 |
| **F6** | Transação e posição | C6, C7, C10 no `TransactionService`/posição | ITs: compra pelo valor, resgate PEPS, bloqueio acima da posição |
| **F7** | Impostos e come-cotas | regimes (C8), seed 11.3, come-cotas (C9, D22), Previdência sem imposto | ITs por regime e por data (maio/novembro, IOF < 30 dias, lote com prejuízo) |
| **F8** | Front | 11.6 | `tsc -b`, `eslint`, `vite build`; conferência no navegador com ele logado (caminho 3) |
| **F9** | Fechamento | `Claude.md`, `ACTIVEST_DB.md`, `PENDENCIAS.md` (29.7 parte fundos resolvida; pendências: página do fundo, imposto da previdência, compensação de prejuízo no come-cotas, proventos de fundo), `SCHEDULE_TASKS.md`, `ADM_TASKS.md`, `USE_CASES.md`, `data push` de `cvm/fi`, andamento | Bateria completa verde (`mvn clean verify`) |

### 11.8 Critérios de aceitação

1. Banco novo sobe e o `BOOTSTRAP` cadastra os fundos (vivos e encerrados) com a última cota dos vivos; nenhum outro
   tipo de ativo muda de valor (Carteira, Rentabilidade, Metas, Dashboard, Dividendos, Preço Teto iguais).
2. Na Nova transação, tipo Fundos: "RF 300" encontra **CAIXA FIC PREV 300 RF** (03.926.431/0001-71, Previdência); o
   Preço vem com a cota da data; Valor ÷ Preço dá as cotas, sem edição.
3. Depois da primeira compra, a série do fundo desde 2010 entra sozinha; Carteira, Rentabilidade e Metas passam a ter
   valor para o fundo.
4. Em nenhuma tela aparece o CNPJ sem pontuação no lugar do nome; o logo do tipo aparece na Carteira e em Transações.
5. Fundos de previdência entram na busca e na carteira, valem bruto e mostram o aviso de IR do plano.
6. Fundos não-previdência: resgate com IOF/IR por PEPS; come-cotas reduz as cotas em maio/novembro.
7. `FUND_QUOTES` roda no máximo uma vez por dia automaticamente e sempre no Run manual; atraso > 2 dias úteis avisa o
   administrador.
8. Fundo não aparece na busca da tela de Ativos (D20).
9. `GET /api/v1/assets/` sem `query` responde 400; as telas seguem iguais.

### 11.9 Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| F1–F8: código, testes, compilação, ambiente | ✅ | — |
| F2/F3: cargas reais e `data push` de `cvm/fi` | ✅ (REGRAS 5.3) | confere o resultado |
| Base legal e alíquotas do seed 11.3 | prepara | **valida antes da F7** |
| Conferência no navegador (caminho 3) | prepara o roteiro | ✅ logado |
| Commit, branch `TRACK_01-*`, PR para a `develop` | sugere a mensagem | ✅ |

### 11.10 Decisões do Planning

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D22** | Come-cotas: gravar ou calcular na hora | (a) gravar em `fund_tax_events`, regerado quando transação ou cota muda; (b) calcular sempre a partir das transações e da série, sem gravar | **(a)** — a quantidade de cotas é lida por Carteira, Metas, Rentabilidade e integrações; com o evento gravado todos leem o mesmo número, e o cálculo fica auditável |
| **D23** | De onde vem a cota da data no formulário | (a) app-api lê o ZIP do mês (pasta `cvm/fi` montada só leitura, como os logos; leitor no core); (b) endpoint na adm-api pelo proxy | **(a)** — sem chamada entre serviços; a adm-api segue dona da carga |
| **D24** | No resgate, o usuário informa | (a) valor **bruto** (cotas × cota) e o Activest calcula IR/IOF/líquido; (b) valor líquido (como a venda de RF) | **(a)** — no fundo o resgate é em cotas; bruto ÷ cota dá as cotas sem estimar imposto ao contrário |
| **D25** | Índice da busca | (a) `pg_trgm` + GIN no texto normalizado; (b) `LIKE` sem índice | **(a)** — 53 mil nomes longos com `%texto%` |
| **D26** | Janela do "vivo" por cota informada (C2) | 30 dias / 60 dias / 90 dias | **30 dias** — a cauda de atraso medida é de 1 a 2 semanas |
| **D27** | `GET /api/v1/assets/` sem `query` | (a) 400; (b) paginar | **(a)** — ninguém usa; paginar seria código sem uso |
| **D28** | Ordem de execução | (a) F1→F9, uma etapa por vez, cada uma fechada (REGRAS 2.3); (b) tudo de uma vez | **(a)** |

### 11.11 Respostas do Guilherme (27/10/2026)

- **D22 a D28 aprovadas pela recomendação** e implementação autorizada ("mandou implementar"): D22 (a) come-cotas
  gravado em `fund_tax_events`; D23 (a) a app-api lê o ZIP do mês; D24 (a) resgate pelo valor bruto; D25 (a) `pg_trgm`
  + GIN; D26 30 dias; D27 (a) 400 sem `query`; D28 (a) F1→F9, uma etapa por vez, cada uma fechada.
- **Código de F3–F8 escrito fora de ordem** (sessão anterior, 27/10 entre 14:14 e 14:29, sem compilar nem testar):
  **mantido**, por decisão do Guilherme; é revisado, testado e corrigido na etapa a que pertence, na ordem do D28.
- **Execução contínua (27/10/2026):** depois de F1 e F2 fechadas, o Guilherme autorizou seguir F3→F9 sem pedir
  autorização a cada etapa; ele só é acionado em problema crítico. A ordem e o fechamento de cada etapa (D28) continuam.

## 12. Roteiro da conferência no navegador (F8, caminho 3 — Guilherme logado)

1. **Nova transação → tipo Fundos de Investimentos.** Digitar "RF 300": a lista mostra **CAIXA FIC PREV 300 RF** com
   "03.926.431/0001-71 · Previdência"; digitar o CNPJ com e sem pontuação acha o mesmo fundo; "previnvest" (nome antigo)
   também. Nenhuma linha mostra o CNPJ sem pontuação como nome.
2. **Escolher o fundo.** Aparece o aviso da Previdência; a Cota vem preenchida com a da CVM da data ("Cota da CVM de
   dd/mm/aaaa"); mudar a data muda a cota; digitar outra cota mostra "Cota informada por você".
3. **Valor.** O Total mostra o valor e, embaixo, as cotas (valor ÷ cota, até 8 casas), sem campo de quantidade.
4. **Salvar.** Toast "Compra de CAIXA FIC PREV 300 RF registrada."; em Transações a linha mostra o nome curto e o logo
   do tipo (mão com o saco de dinheiro); o filtro de ativo e a busca por nome acham o fundo.
5. **Carteira → aba Fundos de Investimentos.** Nome curto, categoria Previdência, cotas, valor atual = cotas × última
   cota e "líq." igual ao bruto (passar o mouse: "Previdência: IR do plano (PGBL/VGBL) não calculado").
6. **Depois de ~2 min** (série sob demanda): Rentabilidade e Metas passam a ter o fundo, com o nome curto (contribuição
   por ativo, filtro "Ativo", opções das metas por ativo).
7. **Resgate** (ícone Vender na Carteira): "Valor resgatado (bruto)" + Cota; o ícone ao lado do "Total bruto" abre a
   prévia (bruto, IOF, IR, líquido). Num fundo não-previdência (ex.: um multimercado), IR/IOF aparecem.
8. **Tela de Ativos:** a busca do topo **não** mostra fundos (D20).
9. Se quiser, excluir as transações de teste depois — a posição some e nada fica no catálogo além do fundo e da série.
