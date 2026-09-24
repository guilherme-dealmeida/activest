# PrimeVest — Plano de Integração BRAPI

> Fase: planejamento (23–24/09/2026) → **executado em 24/09/2026** (seção 8) · branch `TRACK_01-ajustes_crud_inicial`
> Plano contratado: **Gratuito** (15.000 requisições/mês, 1 ativo por requisição, 1 requisição simultânea, dados a cada 30 min).
> Carteira hoje: 11 ações · 11 FIIs · 1 ETF (**23 ativos via BRAPI**) · 9 Tesouros (MANUAL) · 1 cripto (CoinGecko).
> Executado em 24/09/2026 na ordem da seção 7. O que mudou em relação ao plano está na seção 8.

Objetivo: fazer a integração que já existe **funcionar de verdade no plano gratuito**, só com preço de fechamento;
usar o que o plano libera de graça (lista de tickers, nomes, renomes, histórico curto) para completar e limpar o catálogo;
e trazer **proventos da B3** (fonte oficial, gratuita).

Planilhas de apoio (em `docs/brapi/`):
- `ETFs_BRAPI_2026-09-23.xlsx` — 219 ETFs com categoria atribuída (regra B2) + 38 excluídos.
- `BDRs_BRAPI_2026-09-23.xlsx` — 892 BDRs com cotação, proporção, setor e categoria (regra B10).
- `Criptos_CoinGecko_2026-09-24.xlsx` — 227 criptomoedas com categoria (regra B11) + 23 excluídas. Sem cotação.

---

## 0. Diagnóstico — o que existe hoje

A estrutura inicial foi mantida. Nenhuma peça saiu:

| Peça | Onde | Estado |
|---|---|---|
| `BrApiClient` (WebClient) + DTOs | `integration/brapi/` | existe; usa endpoint **v1** `/api/quote/{tickers}` |
| `PriceSource` + `asset_types.price_source` | V1, V2 | BRAPI em ACAO, FII, ETF e BDR |
| `PriceUpdateScheduler` | `scheduler/` | a cada 15 min (10h–18h) + fechamento 18:30 + cripto a cada 30 min |
| `DividendSyncScheduler` | `scheduler/` | diário 20h |
| `CoinGeckoClient` | `integration/coingecko/` | existe; mapa fixo de 14 moedas |
| Cache `quotes` (Redis, 5 min) | `RedisConfig` | existe |
| `BRAPI_TOKEN` | `application.yml`, `docker-compose.yml`, `.env.example` | lido do ambiente; `.env` está no `.gitignore` |
| Mocks nos testes | `IntegrationTestBase`, `application-test.yml` | `BrApiClient` e `CoinGeckoClient` sempre mockados |

**Mas hoje a parte BRAPI não produz resultado.** Achados confirmados contra a API real em 23/09/2026:

- **D1 — sem token.** Não existe `.env`; `BRAPI_TOKEN` chega vazio. Sem token só respondem PETR4, MGLU3, VALE3 e ITUB4 (20 req/min por IP).
- **D2 — lotes de 15 tickers.** O gratuito aceita 1 ativo por chamada; o lote volta **400 `QUOTES_PER_REQUEST_EXCEEDED`**.
  O `catch (Exception)` do cliente devolve lista vazia: **nenhum preço é atualizado e o log não diz por quê.**
- **D3 — scheduler varre o catálogo inteiro** (`findByActiveTrue` com price_source BRAPI = 1.119 ativos).
- **D4 — dividendos nunca funcionariam, em plano nenhum.**
  - Pede `range=1y` → **400 `INVALID_RANGE`** no gratuito (ranges permitidos: 1d, 5d, 1mo, 3mo).
  - Dividendos exigem Startup → **403 `FEATURE_NOT_AVAILABLE`**.
  - O DTO espera `dividends` no topo; a API devolve `results[0].dividendsData.cashDividends[]`, com `label` no lugar de `type`.
  - Resolve a **PENDENCIAS 3.2**: `lastDatePrior` **é a data-com**; `exDate` é campo à parte (dia seguinte).
- **D5 — token na URL** (`?token=`). A documentação pede header `Authorization: Bearer`.
- **D6 — endpoint v1.** A BRAPI recomenda `/api/v2/...` para integrações novas.
- **D7 — `@Cacheable` sem efeito** em `getQuote` (auto-invocação dentro da mesma classe).
- **D8 — cadência inútil.** 15 min no agendador, mas o gratuito só atualiza a cada 30 min.

---

## 1. O que o plano gratuito libera (testado com o token)

| Recurso | Endpoint | Gratuito | Gasta cota |
|---|---|---|---|
| Cotação (ação, FII, ETF, BDR, unit) | `GET /api/v2/stocks/quote?symbols=X` | ✅ 1 ativo/chamada | sim |
| Histórico diário OHLCV | `GET /api/v2/stocks/historical` | ✅ até `3mo` | sim |
| Lista de tickers (nome, tipo, setor, logo, último preço) | `GET /api/v2/tickers` | ✅ | **não** |
| Resolver renome / lista de renomes | `/api/v2/tickers/resolve`, `/renames` | ✅ | **não** |
| Cobertura por ticker, dicionário | `/api/v2/tickers/coverage`, `/api/v2/dictionary` | ✅ | **não** |
| Dividendos / JCP | `/api/v2/stocks/dividends` | ❌ Startup | — |
| SELIC, CDI, IPCA · câmbio · cripto | `/api/v2/macro`, `/currency`, `/crypto` | ❌ Startup | — |
| Estatísticas e fundamentos | `/api/v2/stocks/statistics`, `/financial-data` | ❌ Startup/Pro | — |
| FIIs detalhados, fundos, Tesouro | `/api/v2/fii/*`, `/funds/*`, `/treasury/*` | ❌ Pro | — |

Erros: 401 token · 403 plano · 400 parâmetro fora do plano · 404 ativo não encontrado · 429 cota/simultaneidade (`Retry-After`).
Toda resposta traz `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`. Requisição recusada com 400/403 **não** consome cota.

Universo na BRAPI (23/09/2026): 771 ações · 12 units · 329 FIIs · 257 ETFs · 892 BDRs · 37 Fiagro · 17 FI-Infra.

---

## 2. Decisões tomadas

### 23/09/2026
- **B1 — Carteira:** 11 ações, 11 FIIs, 1 ETF pela BRAPI; Tesouro segue MANUAL; cripto fica com outra integração.
- **B3 — Nomes das ações:** trocar pelos da BRAPI (resolve a PENDENCIAS 6.2).
- **B4 — Deslistados:** marcar `active = false` (critério em 2.1).
- **B5 — Dividendos:** em estudo (ver 5.6 — surgiu opção gratuita).
- **B6 — Plano documentado aqui**, atualizado a cada decisão.

### 24/09/2026
- **B2 — Categorias de ETF:** `description` = tema (ex.: "Renda fixa Brasil"); `name` = tema em MAIÚSCULAS sem acento
  (`RENDA_FIXA_BRASIL`); `asset_type` = ETF; `segment` = "Miscellaneous" (setor da BRAPI) ou "Indefinido" (setor vazio).
  Resultado: **12 linhas** de categoria para os 219 ETFs (aba "Categorias (12)" da planilha).
- **B7 — Incluir os ativos que só existem na BRAPI** (17 ações + 10 FIIs). Ver 2.2.
- **B8 — INHF11 é FII.** Hoje está como ação no V4; muda de tipo (ver 2.2).
- **B9 — Só preço de fechamento.** Sai a rodada intradiária. Ver seção 3.

### 24/09/2026 (segunda rodada)
- **B4 confirmado** — critério de 2.1: só os que dão 404 saem (`active = false`); os que têm cotação ficam ativos.
- **B7 confirmado** — BRAX, ESUD11 e ABCB10L **ficam de fora**. Entram 14 ações e 10 FIIs.
- **B8 confirmado** — INHF11 = HIBRIDO / Híbrido.
- **B5 — Proventos pela B3.** Plano na F5.
- **B10 — BDRs entram no catálogo** (os 892), com a categoria sugerida na planilha: `description` = setor em português
  (BDR de ação) ou "ETFs no exterior" (BDR de ETF); `name` = description em MAIÚSCULAS sem acento; `segment` = setor
  **traduzido para o português** — a tradução é feita na execução, a planilha fica como está.
- **B11 — Criptomoedas:** levantamento sem cotação para cadastrar ativos e categorias (planilha; ver 5.7).

### 24/09/2026 (terceira rodada)
- **B12 — Cripto só no fechamento:** 1 vez ao dia às 19:00, junto com a B3, gravando histórico (fecha a A8 e a PENDENCIAS 11.4).
- **B13 — Amortização (FII) e restituição de capital (empresa) contam como provento e reduzem o custo da posição.**
- **B14 — Desdobramento, grupamento e bonificação ajustam quantidade e preço médio automaticamente**, com serviço
  próprio e estrutura de testes (F6).
- **B15 — Proventos de FII/ETF com mais de 12 meses:** buscar fonte, não aceitar só manual. Primeira fonte encontrada: FNET (5.9).
- **B16 — Cotação de 23/09 dos ETFs e BDRs entra no catálogo** junto com os ativos (V17 e V18): preço atual e uma linha
  de histórico com abertura, máxima, mínima, fechamento e volume, tirados das planilhas.
- **B17 — Logos:** avaliado em 5.10; proposta na F7.

### 24/09/2026 (quarta rodada)
- **B18 — Logos numa pasta fora do banco** (fecha A13). Detalhe na F7.
- **B19 — FSPE11F, FSRF11F e PSVM11F ficam inativos** (`active = false`, na V14).
- **B20 — Ordem de execução:** catálogo → logos → token → cliente BRAPI → fechamento → proventos → desdobramentos e
  grupamentos (seção 7).
- **B21 — Fator de grupamento e bonificação (A12):** conferido num caso real como primeiro passo da F6.
- **B22 — ETFs no FNET (A14) e ticker → CNPJ (A15):** verificados e escolhidos na execução; só volta para decisão se
  nenhuma fonte servir.
- **B23 — Rotina de conferência do catálogo: criar.** Enquanto a central de avisos não existir (B26), cada diferença
  vai só para o log (WARN).
- **B24 — `.env` criado em 24/09/2026** na raiz, a partir do `.env.example`, com o `BRAPI_TOKEN` (o `.env` está no
  `.gitignore`; os demais valores são os mesmos padrões do `docker-compose.yml`).
- **B25 — Logos atualizados uma vez por mês e na entrada de ativo novo**, e exibidos **à esquerda do ticker nas
  listagens da carteira**.
- **B26 — Central de avisos (A16): adiada para o `PENDENCIAS.txt`, seção 13** (24/09/2026). Fica fora deste plano;
  até lá, conferência do catálogo e bonificação sem custo avisam só no log (WARN).
- **B27 — Bonificação sem custo informado pela B3:** as ações novas entram na hora com **custo zero provisório**, para
  a quantidade já ficar certa. Aviso, ícone na Carteira e "Informar custo" dependem da central (PENDENCIAS 13.2).

### 2.1 Critério de deslistado (B4)
Um ativo do catálogo é **deslistado** quando atende às duas condições:
1. **não aparece** na lista oficial de tickers da BRAPI (`/api/v2/tickers`), **e**
2. a **cotação dele volta 404 `NOT_FOUND`** (`/api/v2/stocks/quote`).

Quem falha só na 1 mas tem cotação é papel **sem negócio** (volume 0 no dia), não deslistado — continua ativo.
Hoje isso dá **6 ativos**, todos fracionários: AHEB3F, ESTR3F, FIGE4F, MSPA4F, PEAB3F, RPAD6F.
Os outros 27 ausentes da lista têm cotação do dia (ex.: BAUH4, JOPA3, REIT11, VGII11) e ficam ativos.
Proteção: ativo com **posição aberta não é desligado**, mesmo que atenda ao critério — a migration pula e registra.

### 2.2 Ativos novos (B7) e INHF11 (B8)
**Ações — 14 entram**, com nome da BRAPI e a categoria do papel irmão do mesmo emissor (a B3 classifica por emissor):

| Ticker | Emissor | Categoria (igual a) |
|---|---|---|
| CCTY3 | Belora RDVC City | CCTY3F — CONSUMO_CICLICO / Incorporações |
| CEEB5 | Coelba | CEEB3 — UTILIDADE_PUBLICA / Energia Elétrica |
| CTKA3, CTKA3F | Karsten | CTKA4 — CONSUMO_CICLICO / Fios e Tecidos |
| EQPA7 | Equatorial Pará | EQPA3 — UTILIDADE_PUBLICA / Energia Elétrica |
| GSHP3 | General Shopping | GSHP3F — FINANCEIRO / Exploração de Imóveis |
| HBTS5 | Habitasul | HBTS5F — FINANCEIRO / Exploração de Imóveis |
| MGEL3F | Mangels | MGEL4 — MATERIAIS_BASICOS / Artefatos de Ferro e Aço |
| MRSA5B, MRSA6B | MRS Logística | MRSA3B — BENS_INDUSTRIAIS / Transporte Ferroviário |
| MTSA3F | Metisa | MTSA4 — BENS_INDUSTRIAIS / Máq. e Equip. Construção e Agrícolas |
| RPAD5, RPAD6 | Alfa Holdings | RPAD3 — FINANCEIRO / Bancos |
| WLMM3 | WLM | WLMM4 — BENS_INDUSTRIAIS / Material de Transporte |

**Ficam de fora (confirmado)** — a BRAPI os lista como ação, mas não são:
- **BRAX** — cadastro sujo: nome "Brakes Express Inc" com o preço do ETF BRAX11 (R$ 155,39).
- **ESUD11** — FIP de infraestrutura (Energia Sustentável II); o PrimeVest não tem tipo para FIP.
- **ABCB10L** — ticker temporário do ABC Brasil (BRAPI: "TEMP"); some quando o evento societário terminar.

**FIIs — 10 entram**, classificados pelo segmento publicado (FIIs.pro, 24/09/2026) no mapa de categorias do V3:

| Ticker | Fundo | Categoria |
|---|---|---|
| APXU11 | Apex Urb | HIBRIDO / Híbrido |
| FPNG11 | Pedra Negra Renda Imobiliária | TIJOLO / Lajes Corporativas |
| IMMB11 | Immobinvest | TIJOLO / Imóveis Residenciais |
| INHF11 | Inter Hedge | HIBRIDO / Híbrido *(confirmado)* |
| JASC11 | Jasc Renda Varejo Essencial | TIJOLO / Varejo (publicado como "Renda Urbana", que não existe no V3) |
| LSOI11 | LCP Special Opportunities I | HIBRIDO / Híbrido |
| LSOP11 | CP Special Opportunities III | TIJOLO / Imóveis Residenciais |
| RBFY11 | Rio Bravo For You | TIJOLO / Imóveis Residenciais |
| RBTS11 | RB Capital TFO Situs | TIJOLO / Desenvolvimento |
| ROOF11 | Rooftop I | TIJOLO / Desenvolvimento |

**INHF11** já existe no V4 como ação ("INTER HOLDING FINANCEIRA S.A.", OUTROS). Não é inserido de novo: a migration
**muda tipo, categoria e nome** da linha existente. Se houver posição nele, a alocação da carteira muda de Ações para FIIs.

---

## 3. Rotina de atualização (B9 — só fechamento)

**Quem executa hoje:** o `PriceUpdateScheduler`, uma classe Spring com `@Scheduled` **dentro da própria API**
(ligada pela `SchedulingConfig`; desligada no perfil `test`). Não há cron do sistema nem serviço separado:
**só roda enquanto o container `api` estiver de pé** (`docker compose up`). Três métodos:

| Método | Quando (hoje) | Fonte |
|---|---|---|
| `updateB3PricesDuringMarketHours` | a cada 15 min, 10h–18h, seg–sex | BRAPI |
| `updateB3PricesAtClose` | 18:30, seg–sex (+ grava `asset_price_history`) | BRAPI |
| `updateCryptoPrices` | a cada 30 min, todos os dias | CoinGecko |

O `DividendSyncScheduler` roda à parte, 20h todos os dias.

**Como fica:**
- Sai a rodada intradiária. Fica **uma rotina de fechamento às 19:00, seg–sex** (horário em propriedade).
- Em vez de `quote`, ela usa **`historical?range=5d&interval=1d`** — mesmo custo (1 requisição por ativo) e já traz
  abertura, máxima, mínima, fechamento e volume **dos últimos 5 pregões**. Grava/atualiza cada dia em
  `asset_price_history` (resolve PENDENCIAS 11.3) e põe o último fechamento em `assets.current_price`.
- **Recuperação:** como a API roda na sua máquina, se ela estiver desligada às 19h o dia fica sem preço.
  Ao subir, a API confere o último fechamento gravado; se faltar pregão, roda a rotina na hora.
  Faltando até 5 pregões, `range=5d` cobre; mais que isso, `1mo` ou `3mo` (limite do gratuito).
- Custo: **23 requisições por dia útil ≈ 506/mês (3,4% da cota)**. Sobra espaço para ~600 ativos B3 em carteira.
- Cripto (B12): mesmo horário — 1 vez ao dia às 19:00, todos os dias (cripto negocia no fim de semana), gravando o
  fechamento em `asset_price_history` (PENDENCIAS 11.4). A rodada de 30 em 30 minutos sai.

---

## 4. Fases

### F1 — Cliente v2
- `BrApiClient` passa para `/api/v2/stocks/quote` e `/api/v2/stocks/historical`, token no header `Authorization: Bearer`.
  Sai o `?token=` da URL. Continua WebClient (padrão do projeto).
- Método tipado `fetchQuote(String ticker)` → devolve `results[0].data` (`BrApiQuote`); `fetchDailyHistory(ticker, range)`
  → lista de candles. Resposta não-2xx lança `BrApiException` com status e `code` da BRAPI. Nada de `catch` devolvendo vazio.
- 429: lê `Retry-After`, espera e tenta uma vez. 401/403: loga em ERROR uma vez por rodada e interrompe.
- Loga `RateLimit-Remaining` ao fim de cada rodada. `brapi.max-symbols-per-request: 1` em propriedade.

### F2 — Rotina de fechamento (seção 3)
- Só ativos com **posição aberta** e price_source BRAPI; 1 por chamada, em sequência.
- 19:00 seg–sex + recuperação ao subir a API. Grava ativo a ativo (sem uma transação única com `sleep` dentro).
- Ticker renomeado (`changed = true`): loga antigo → novo; **não** troca sozinho (decisão manual).

### F3 — Catálogo (sem gastar cota)
Migrations com `INSERT`/`UPDATE` literais gerados de um retrato da BRAPI — o arquivo é o estado, o Flyway não depende de rede.
- **V13 — nomes das ações (B3).** Só `ACAO`; campo `name` da BRAPI (razão social acentuada — AALR3 "ALLIANÇA SAÚDE E
  PARTICIPAÇÕES S.A."). Onde a BRAPI devolve o próprio ticker (29 casos), mantém o atual. FIIs não entram.
- **V14 — deslistados (B4) e ativos estranhos (B19).** `active = false` nos 6 do critério 2.1 e em FSPE11F, FSRF11F e
  PSVM11F, exceto com posição aberta.
- **V15 — ativos novos (B7/B8).** 14 ações + 9 FIIs inseridos; INHF11 reclassificado de ação para FII.
- **V16 — categorias de ETF (B2).** 12 linhas (`name`, `description`, `segment`, tipo ETF).
- **V17 — ETFs.** 219 ativos, nome = `longName` da BRAPI (o `name` é o ticker), categoria pela planilha.
  Com a cotação de 23/09/2026 (B16): `current_price` e `price_updated_at` (23/09/2026 18:31) em `assets`, e uma linha
  em `asset_price_history` com abertura, máxima, mínima, fechamento e volume do dia.
- **V18 — BDRs (B10).** 22 linhas de categoria do tipo BDR e 892 ativos. Nome = empresa/fundo da planilha.
  `segment` traduzido na execução (Miscellaneous → Diversos, Finance → Finanças…; tabela de tradução na planilha).
  Para BDR de ação, `description` e `segment` saem iguais (os dois são o setor); para BDR de ETF, description =
  "ETFs no exterior" e segment = setor (212 Diversos, 1 Finanças, 10 Indefinido).
  Com a cotação de 23/09/2026 (B16), do mesmo jeito que os ETFs: preço atual + uma linha de histórico com OHLCV.
- **V19 — criptomoedas (B11).** Coluna nova `assets.price_source_ref` (id do ativo no provedor — `bitcoin`, `ethereum`),
  15 linhas de categoria do tipo CRIPTO e 227 ativos (ticker = símbolo, nome = nome CoinGecko). O `CoinGeckoClient`
  deixa o mapa fixo de 14 moedas e passa a usar essa coluna. `segment` traduzido na execução, como nos BDRs.
- **V20 — proventos (B5).** Tabelas e colunas da F5.
- **V21 — logos (B17).** Coluna `assets.logo_path` (F7).
- **Rotina de conferência (B23)** — semanal (segunda, 07:00) e sob demanda. Compara o catálogo com
  `/api/v2/tickers` (não gasta cota) e com `/api/v2/tickers/renames`. **Não grava nada no catálogo**: cada diferença
  vira uma linha no log (WARN) — a central de avisos foi adiada (B26, PENDENCIAS 13):
  - *Ticker novo na B3* — "XPTO11 (FII) apareceu na BRAPI e não está no catálogo";
  - *Ticker sumido* — só depois de conferir que a cotação dá 404 (critério 2.1); se houver posição aberta, o aviso
    diz isso;
  - *Ticker renomeado* — "BRML3 agora é ALOS3 desde 09/01/2023" (o ticker do catálogo não é trocado sozinho).
  O log traz o total da rodada. Quando a central existir, essas diferenças viram avisos nela.

### F4 — Histórico
- Coberto pela F2 (OHLCV diário). Carga inicial: `range=3mo` para cada ativo em carteira (23 requisições, uma vez).

### F5 — Proventos pela B3 (B5)

**Fonte:** o site da B3 (`https://sistemaswebb3-listados.b3.com.br`), sem token. O parâmetro de cada chamada é um JSON
em base64 no fim da URL. Os quatro endpoints abaixo foram testados em 24/09/2026:

| Uso | Endpoint | Parâmetros | O que traz |
|---|---|---|---|
| Proventos recentes de ação, unit e BDR | `listedCompaniesProxy/CompanyCall/GetListedSupplementCompany` | `issuingCompany` = 4 primeiros caracteres do ticker (PETR, TAEE, AAPL, A1AP) | ~12 meses: tipo, **data-com**, **pagamento**, valor por cota, ISIN, aprovação, referência. Mais desdobramentos, grupamentos e bonificações (histórico completo) |
| Proventos recentes de FII, ETF e Fiagro | `fundsProxy/fundsCall/GetListedSupplementFunds` | `identifierFund` = 4 primeiros (MXRF, DIVD, KNCA), `typeFund: 7`, `cnpj: "0"` | ~12 meses, mesmo formato |
| Ticker ↔ ISIN | `listedCompaniesProxy/CompanyCall/GetDetail` | `codeCVM` (vem da chamada anterior) | `otherCodes`: PETR3 = BRPETRACNOR9, PETR4 = BRPETRACNPR6 |
| Histórico longo de empresa | `listedCompaniesProxy/CompanyCall/GetListedCashDividends` | `tradingName` (vem da 1ª chamada), `pageNumber`, `pageSize` | Desde os anos 1990 (PETR: 343 lançamentos, 1996–2026). **Sem data de pagamento**; valor por lote (`quotedPerShares` = 1 hoje, 1000 nos anos 90 → dividir); espécie ON/PN no lugar do ISIN |

Visto nos testes: PETR (ON, PN, dividendo e JCP), TAEE (a unit tem ISIN próprio), MXRF (recibo de subscrição aparece
com outro ISIN, que é descartado), AAPL e A1AP (BDRs), DIVD (ETF que paga rendimento), KNCA (Fiagro).

**Modelo de dados (V20) — duas camadas: o que o papel pagou × o que você recebeu**

Sim: uma tabela sem usuário e a de hoje, com usuário. O provento é um fato **do papel** — PETR4 pagou R$ 0,4716 por
ação com data-com 21/08/2026 para todo mundo. O que muda de um usuário para outro é só **quantas cotas** ele tinha
naquela data. Por isso o fato fica uma vez no catálogo, e cada usuário tem a sua conta:

```
CATÁLOGO (global, sem user_id — igual a assets)          USUÁRIO (já existe)
┌──────────────────────────────┐                        ┌────────────────────────────────────┐
│ asset_cash_events            │ 1 ──────────────── N   │ dividends                          │
│  asset_id → assets           │                        │  portfolio_asset_id → posição      │
│  type                        │                        │  cash_event_id → asset_cash_events │ (nulo = manual)
│  data_com                    │                        │  type, ex_date (data-com),         │
│  payment_date                │                        │  payment_date, amount_per_share    │
│  value_per_share             │                        │  shares_on_date   (novo)           │
│  source, source_ref          │                        │  total_amount                      │
└──────────────────────────────┘                        │  origin: AUTO | MANUAL   (novo)    │
┌──────────────────────────────┐                        └────────────────────────────────────┘
│ asset_stock_events           │ → não gera linha por usuário: é aplicado no cálculo da posição (F6)
│  desdobramento/grupamento/   │
│  bonificação + fator         │
└──────────────────────────────┘
```

- `asset_cash_events` — `asset_id`, `type`, `data_com`, `payment_date` (nula quando a fonte não traz),
  `value_per_share` NUMERIC(18,11), `approved_on`, `related_to`, `isin`, `income_tax_exempt` (FNET informa),
  `source` (B3 / B3_HISTORICO / FNET / …), `source_ref` (id do documento na fonte), `fetched_at`.
  Única em (`asset_id`, `type`, `data_com`, `value_per_share`) — a mesma notícia vinda de duas fontes não duplica.
- `asset_stock_events` — `asset_id`, `type`, `data_com`, `factor`, `assigned_cost` (custo atribuído da bonificação,
  quando houver), `source`. Ver F6.
- `assets.isin` — preenchida pela rotina via `GetDetail`. Fracionário (…F) usa o ISIN do papel base.
- `dividends` ganha `cash_event_id` (FK, nulo no manual), `shares_on_date` (cotas na data-com, para conferência) e
  `origin`:
  - **AUTO** — criado pelo `DividendRecalculator` a partir do evento × cotas na data-com (regra de hoje, `sharesOn`).
    Recalculado quando uma transação muda ou o evento é corrigido. Única em (`portfolio_asset_id`, `cash_event_id`).
  - **MANUAL** — lançado pelo usuário, para provento que nenhuma fonte tem. O recalculador não mexe nele.
    Manual **nunca vai para o catálogo**: o catálogo é global, e dado digitado por um usuário apareceria para todos
    (mesmo motivo da PENDENCIAS 6.3).
- Vantagem: o dado externo é buscado uma vez por papel, não por usuário; uma compra retroativa só recalcula,
  sem nova chamada à B3.
- Tipos B3 → `DividendType`: DIVIDENDO → DIVIDENDO · JRS CAP PROPRIO → JCP · RENDIMENTO → RENDIMENTO ·
  **AMORTIZAÇÃO → AMORTIZACAO** · **RESTITUIÇÃO DE CAPITAL → RESTITUICAO_CAPITAL** (novos no enum). Rótulo desconhecido:
  grava o evento, loga e não gera provento até ser mapeado.

**Amortização e restituição de capital (B13)** — aparecem na lista de proventos como qualquer outro e, além disso,
devolvem parte do capital: o `PositionCalculator` desconta do custo da posição `valor por cota × cotas na data-com`,
a partir da data de pagamento. A quantidade de cotas não muda; o preço médio cai (custo ÷ cotas). O custo nunca fica
negativo — amortização maior que o custo zera o custo e o excedente é ganho. Na rentabilidade, o valor conta como
provento recebido, então o resultado total não muda: só sai do "custo" e entra em "proventos".

**Rotina**
- Diária às 20:00 (horário atual do `DividendSyncScheduler`). Consulta cada **emissor** com posição aberta ou fechada
  nos últimos 12 meses — PETR3 e PETR4 são uma chamada só. Hoje, ~20 chamadas por dia, uma por segundo.
- Carga do passado: ao lançar uma compra com data anterior ao evento mais antigo guardado do ativo, busca o histórico
  longo uma vez — empresa pela B3 (`GetListedCashDividends`), FII pelo FNET (5.9). O que nenhuma fonte cobrir fica
  para lançamento MANUAL.
- Falha (B3 fora do ar, formato mudou): log ERROR, **mantém o que já existe**, não apaga provento. Liga/desliga em
  `b3.proventos.enabled`.
- Sai a busca de dividendos pela BRAPI (`BrApiDividendResponse` e a chamada no `BrApiClient`).

**Risco:** o endpoint é o que o site da B3 usa, sem documentação — pode mudar sem aviso. Por isso o cliente fica
isolado num `B3Client` e os testes usam respostas reais gravadas: se a B3 mudar o formato, o teste com a resposta
nova quebra antes de gravar dado errado. O domínio da B3 é bloqueado no ambiente do Claude (Cowork), mas não na sua
máquina nem no Docker.

### F6 — Desdobramento, grupamento e bonificação (B14)

**Princípio:** a posição continua sendo calculada a partir das transações (PENDENCIAS 7.3, V28). Nenhuma transação
do usuário é reescrita. O evento entra no cálculo como um passo a mais na linha do tempo, na data ex (primeiro pregão
depois da data-com):

| Evento | Quantidade | Custo total | Preço médio |
|---|---|---|---|
| Desdobramento | × (1 + fator/100) | igual | ÷ (1 + fator/100) |
| Grupamento | × fator de grupamento; fração vai a leilão | igual (fração sai do custo, proporcional) | sobe na mesma razão |
| Bonificação | + cotas × fator/100 | + novas cotas × custo atribuído (sem custo informado: zero provisório — B27) | recalculado |

O fator de desdobramento da B3 é percentual: PETR 2008, fator 100,00 = 1 ação virou 2 (confirmado pelo histórico da
Petrobras). **A12 conferido na execução** com casos reais: desdobramento em % de ações novas (MGLU 2020 fator 300 =
1 vira 4; MXRF 2017 fator 900 = 1 vira 10), grupamento como multiplicador (MGLU 2024 fator 0,1 = 10 viram 1) e
bonificação em % de ações novas (MGLU 2025 fator 5; ITSA 2025 fator 2). O campo `remarks` veio vazio em todas: a B3
não informa o custo atribuído.

**Estrutura**
- `CorporateActionApplier` — função pura: recebe a posição (quantidade, custo) e um evento e devolve a posição nova.
  Sem banco, sem Spring. É onde mora a regra da tabela acima.
- `PositionCalculator` — ao percorrer as transações em ordem, intercala os `asset_stock_events` do ativo na data ex e
  chama o applier. `sharesOn` (usado pelos proventos) passa a enxergar as cotas já ajustadas.
- `StockEventSync` — grava `asset_stock_events` a partir da B3 (junto com a rotina de proventos da F5, mesma chamada).
- Transações lançadas depois do evento já estão na quantidade nova (é o que a nota de corretagem mostra); as de antes
  ficam na antiga — o applier faz a ponte. Evento antes da primeira compra ou depois da venda total não afeta nada.

**Estrutura de testes**
- `CorporateActionApplierTest` (unitário, rápido): desdobramento 1:2 e 1:3; grupamento 10:1 com e sem fração;
  bonificação 10% com e sem custo atribuído; preço médio arredondado na escala do `PositionCalculator` (4 casas).
- `PositionCalculatorTest` (unitário): compra → desdobramento → venda; compra na data-com (tem direito) × na data ex
  (não tem); dois eventos seguidos; evento antes da primeira compra; evento depois de zerar a posição; amortização (B13).
- `DividendRecalculatorTest`: provento pago depois de um desdobramento usa as cotas novas; antes, as antigas.
- `StockEventSyncTest`: resposta real da B3 gravada (PETR 2008, e um grupamento e uma bonificação reais — A12).
- `EventosSocietariosIT` (funcional, Testcontainers): lança compras, grava o evento, confere carteira, preço médio e
  proventos pelo endpoint. Segue a convenção do PLANO_TESTES: nome do método descreve a regra
  (`desdobramentoUmParaDois_dobraCotasEMantemCusto`).

### F7 — Logos (B17)
- **Não apontar para o site de terceiros** na tela: metade dos ícones da BRAPI não existe (5.10) e o endereço da
  CoinGecko muda a cada troca de imagem. Uma rotina baixa cada logo uma vez, confere se veio imagem, e guarda do
  nosso lado; `assets.logo_path` aponta para o arquivo guardado.
- **Onde guardar (B18): pasta fora do banco.** `data/logos/` na raiz do projeto, montada no container `api` como
  volume (`./data/logos:/app/data/logos` no `docker-compose.yml`) e incluída no `.gitignore`. Um arquivo por ativo
  (`{TICKER}.svg` ou `.png`, como vier da fonte). `assets.logo_path` guarda o nome do arquivo; nulo = sem logo.
- A API serve a imagem em `GET /api/v1/assets/{ticker}/logo`, com cache longo no navegador (o arquivo só muda na
  atualização mensal).
- **Na tela (B25):** componente `AssetLogo` (24 px, redondo) **à esquerda do ticker** nas duas listagens da carteira
  (`PortfolioPage.tsx` — a tabela de posições e a da reserva de emergência). As outras telas que mostram ticker
  (Transações, Proventos, Dashboard, Preço teto) ficam como estão até você pedir.
- Sem logo: o `AssetLogo` mostra as iniciais do ticker nas cores do PrimeVest (dourado #c9a84c sobre #111111).
- Ações e FIIs entram na mesma rotina (ações: 638 de 771 têm ícone na BRAPI; FIIs: 66 de 329).
- Atualização: mensal, e na inclusão de ativo novo.

### F8 — Central de avisos *(adiada — B26; registrada no `PENDENCIAS.txt`, seção 13)*

> Fora da execução deste plano. O texto abaixo fica como referência para a PENDENCIAS 13. O que entra agora: a
> bonificação sem custo usa custo zero provisório (B27) e, junto com a conferência do catálogo, avisa só no log (WARN).
Hoje o PrimeVest só tem o toast (canto superior direito, some em 4–8 s): serve para responder a um clique, não para
algo que a rotina da madrugada descobriu. Duas decisões já tomadas precisam de um aviso que **fica até ser resolvido**
— a conferência do catálogo (B23) e a bonificação sem custo atribuído (F6) — e a rotina de proventos também se
beneficia (rótulo desconhecido da B3, fonte fora do ar há dias).

- **Banco:** tabela `notices` — `id`, `user_id` (nulo = aviso do sistema, visto por todos), `kind`
  (CATALOGO_NOVO, CATALOGO_SUMIDO, CATALOGO_RENOMEADO, BONIFICACAO_SEM_CUSTO, PROVENTO_ROTULO_DESCONHECIDO,
  FONTE_INDISPONIVEL…), `severity` (INFO / ATENCAO), `title`, `message`, `ref_type` + `ref_id` (o ativo, a posição ou o
  evento), `status` (ABERTO / RESOLVIDO / DISPENSADO), `created_at`, `resolved_at`. Única para aviso aberto do mesmo
  `kind` + referência — a rotina não repete aviso.
- **API:** `GET /api/v1/notices?status=ABERTO`, `PATCH /api/v1/notices/{id}` (resolver ou dispensar).
- **Tela:** sino no `Header` com o número de avisos abertos; ao clicar, painel lateral com a lista. Cada aviso tem a
  ação que o resolve: "Ver ativo", "Dispensar" ou, na bonificação, "Informar custo".
- **Bonificação sem custo atribuído** (resposta à pergunta de 24/09):
  1. A B3 informa a bonificação, mas sem o custo das ações novas.
  2. A posição recebe as cotas novas **com custo zero provisório** — a quantidade fica certa desde o primeiro dia.
  3. A rotina cria o aviso: "PETR4 — bonificação de 10% em 12/03/2027 sem custo atribuído. Informe o valor por ação
     (está no fato relevante da empresa) para corrigir o preço médio."
  4. A linha da posição na Carteira ganha um ícone de atenção ao lado do preço médio, com o mesmo texto no balão (i).
  5. "Informar custo" abre um formulário de um campo; o valor é gravado **para a sua posição** (tabela
     `stock_event_costs`: posição, evento, custo por ação — dado digitado não vai para o catálogo, pela regra da F5),
     o preço médio é recalculado e o aviso fecha sozinho.
  Antes de tudo isso, a F6 confere se o campo `remarks` da B3 já traz o custo atribuído (A12) — se trouxer, o aviso
  só aparece quando ele faltar.

### F9 — Testes e validação
- Cliente: teste com `ExchangeFunction` falsa (sem dependência nova): 200, 400, 401, 403, 404 e 429 com `Retry-After`.
- Rotina: só posições abertas; 1 por chamada; recuperação de pregão perdido; renome não é trocado.
- Proventos: respostas reais gravadas como fixture (PETR ON/PN/JCP, TAEE unit, MXRF com recibo, AAPL BDR, DIVD ETF,
  histórico com lote de 1000); deduplicação; fracionário; rótulo desconhecido; B3 fora do ar não apaga nada.
- Eventos societários e amortização: estrutura da F6.
- Conferência do catálogo: renome não troca o ticker; diferença sai no log. Bonificação sem custo entra com custo zero
  provisório e loga WARN; logo ausente cai nas iniciais. (Testes da central ficam com a PENDENCIAS 13.)
- Migrations: Flyway + `ddl-auto: validate` no Testcontainers (já existente) prova V13–V21.
- Validação final: `mvn clean verify`, subir o `docker compose`, conferir no log uma rodada real e o `RateLimit-Remaining`.

---

## 5. Achados do levantamento

### 5.1 Catálogo × BRAPI (23/09/2026)
- **Ações:** 782 no banco × 783 na BRAPI (771 ações + 12 units). 15 só no banco; 17 só na BRAPI (2.2).
- **FIIs:** 337 no banco × 329 na BRAPI. 18 só no banco (todos com cotação — ficam); 10 só na BRAPI (2.2).
- **Categorias:** a BRAPI não substitui a classificação B3 do V3 (setor em inglês, subsetor nulo em 507 ações).
- **FSPE11F, FSRF11F, PSVM11F** estão no catálogo de ações; na BRAPI são fundos fiscais antigos (FISET) e a Porto Sudeste.

### 5.2 ETFs
257 marcados como ETF na BRAPI → **219 ETFs de fato** + 38 excluídos (10 duplicatas sem sufixo 11, 21 FI-Infra
incentivados, 7 que não são ETF). Por categoria (B2): Ações Brasil 60 · Renda fixa Brasil 59 · Ações internacionais 47 ·
Cripto 29 · Ouro, prata e commodities 11 · Renda fixa internacional 7 · Dólar/câmbio 3 · FIIs/imobiliário 2 · Outros 1.

### 5.3 BDRs (B10 — entram)
892 na BRAPI, todos ativos, nenhum a excluir: 657 de ação (sufixo 34), 223 de ETF (39), 11 com sufixos 31/32/33/35 e
1 unit (PPLA11). O catálogo não tem nenhum BDR hoje. Diferenças para os ETFs:
- A BRAPI dá **setor** (em inglês; a planilha traz tradução) e **valor de mercado** da empresa; subsetor vem vazio.
- A **proporção** BDR × ação vem no nome longo ("Repr 0.05 Sh" → 20 BDRs = 1 ação); 18 não trazem.
- Casos estranhos anotados na planilha: PPLA11 (unit de 3 BDRs), GOGL35 (Alphabet classe C), BTCV39/ETHV39
  (ETPs em coroa sueca), BUZZ39 (+96,6% no dia).
- Categoria (B10): mesma regra dos ETFs — description = setor em português ou "ETFs no exterior"; segment = setor
  traduzido na execução. Dá 22 linhas de categoria.

### 5.4 CoinGecko
Testado em 24/09/2026 sem chave: `/simple/price` responde normal (bitcoin, ethereum, solana em BRL).
**A API não está com problema; os problemas são nossos:**
- **Catálogo sem cripto** (PLANO_TESTES F13): nenhuma migration cadastra criptomoeda — resolvido pela V19 (5.7).
- **Mapa fixo de 14 moedas**, com um id vencido: MATIC → `matic-network` parou de atualizar em 02/2026; o id atual é
  `polygon-ecosystem-token` (POL).
- **Erro engolido** igual ao da BRAPI (`catch` → mapa vazio, só um log).
- **Sem histórico** (PENDENCIAS 11.4) e rodando a cada 30 min, 24/7.
- Para confirmar se está atualizando de fato: rodar `.\.claude\build.ps1 -Docker` — o log do container sai em
  `.claude/logs/docker-api.log` e dá para ler as linhas "Crypto price update complete".

### 5.5 Yahoo Finance
Não tem API oficial desde 2017. Os endpoints usados por bibliotecas como o yfinance são os do próprio site:
sem contrato, com bloqueio por excesso de chamadas (429) e mudanças sem aviso; os termos de uso não cobrem uso em sistema.
Testado em 24/09/2026 (`/v8/finance/chart/{TICKER}.SA?events=div`), respondeu para ações, FIIs e ETFs. Para dividendos:
- traz só **data ex e valor por cota** — sem data de pagamento, sem data-com e **sem o tipo** (dividendo × JCP × rendimento);
- **soma** os proventos da mesma data: PETR4 em 24/08/2026 aparece como 1,348143, que na B3 são três lançamentos
  (dividendo 0,4716 + JCP 0,6741 + JCP 0,2025, com pagamentos em novembro e dezembro);
- JCP tem imposto na fonte; sem separar o tipo, o valor líquido fica errado.

Conclusão: **não resolve dividendos no padrão do PrimeVest** (a tabela `dividends` guarda tipo, data-com e pagamento).
Serve, no máximo, como fonte de histórico de preço acima de 3 meses.

### 5.6 Fonte oficial da B3 para proventos (escolhida — B5)
O site da B3 expõe, sem token, os proventos de cada emissor
(`sistemaswebb3-listados.b3.com.br` — `GetListedSupplementCompany` para empresas e `GetListedSupplementFunds` para FIIs).
Testado em 24/09/2026 com PETR, ITSA e MXRF:
- traz **tipo** (DIVIDENDO, JRS CAP PROPRIO, RENDIMENTO), **data-com** (`lastDatePrior`), **pagamento**, valor por cota,
  data de aprovação e ISIN — é a mesma estrutura que a BRAPI repassa;
- traz também **desdobramentos, grupamentos e bonificações** (`stockDividends`), que mexem na quantidade da posição;
- cobre cerca de **12 meses** (PETR: de 12/2025 a 08/2026); para empresas há um segundo endpoint com o histórico
  desde os anos 1990, sem data de pagamento (detalhes na F5);
- gratuito e oficial, mas **não documentado** (é o endpoint que o site da B3 usa) — pode mudar sem aviso;
- busca por emissor (4 letras) e devolve por ISIN: é preciso casar ISIN com ticker (ON/PN/unit, e recibos de
  subscrição que aparecem junto nos FIIs).

Opções para B5:

| Opção | Custo | Qualidade | Risco |
|---|---|---|---|
| **B3 (site) — escolhida** | grátis | completa, oficial, 12 meses (empresas: histórico longo sem pagamento) | endpoint não documentado |
| BRAPI Startup | R$ 119,99/mês | completa, até 1 ano, documentada | nenhum técnico |
| Yahoo | grátis | sem tipo, sem pagamento, soma proventos | não oficial, bloqueios |
| Manual | grátis | a sua | trabalho recorrente |

### 5.7 Criptomoedas (B11)
Levantamento na CoinGecko em 24/09/2026, **sem cotação**: as 250 maiores por valor de mercado → **227 entram** e
23 ficam de fora (planilha `Criptos_CoinGecko_2026-09-24.xlsx`).
- **Fora:** 8 fundos e títulos do Tesouro americano tokenizados (BUIDL, USYC, OUSG…), 8 fundos e notas tokenizados
  (CLO, fundo monetário, empréstimos), 4 stablecoins com rendimento (derivativos), 1 ação tokenizada e 2 com
  símbolo em ideogramas chineses (não servem de ticker).
- **Categoria (mesma regra):** description = tema; name = tema em MAIÚSCULAS sem acento; asset_type = CRIPTO;
  segment = categoria da CoinGecko que definiu o tema (em inglês, traduzida na execução) ou Indefinido.
  15 linhas: Layer 1 52 · Stablecoins 34 · Tokens de corretora 30 · Outros 30 · Memecoins 20 · DeFi 19 ·
  Inteligência artificial 14 · Layer 2 8 · RWA 5 · Games 5 · Metais preciosos tokenizados 4 · Privacidade 3 ·
  Oráculos 2 · Pagamentos 1.
- **Tema por prioridade:** uma moeda costuma estar em várias categorias da CoinGecko; vale a primeira da ordem
  Stablecoins › Metais › Corretora › Memecoins › Privacidade › Layer 2 › Layer 1 › Oráculos › IA › RWA › DeFi › Games ›
  Pagamentos. Consequência: BNB e UNI caem em "Tokens de corretora" (a CoinGecko põe tokens de exchange descentralizada
  junto).
- **Ajustes manuais:** KAG (prata) junto do ouro tokenizado; SPX6900 entra (a CoinGecko o marca como token de ponte
  por existir em várias redes, mas é a própria moeda).
- **Ticker = símbolo** (BTC, ETH, SOL). Nenhum colide com os tickers do catálogo (ações, FIIs, ETFs, BDRs, renda fixa).
- **Id da CoinGecko** (`bitcoin`, `ethereum`) é o que a API de preço usa — vai para `assets.price_source_ref` (V19).
- A sua cripto: depois da V19, escolha na lista ao lançar a transação.

### 5.8 Segurança
- Token só no `.env` da raiz (já ignorado pelo git), lido por `BRAPI_TOKEN`. Nunca no front, na URL ou em log.
- O token foi colado na conversa de planejamento: considerar gerar um novo no painel da BRAPI antes de usar em produção.

### 5.9 Proventos de FII além de 12 meses — FNET (B15)
O **Fundos.NET** (`fnet.bmfbovespa.com.br`, sistema da B3 onde os administradores publicam os documentos dos fundos)
tem o documento **"Aviso aos Cotistas – Estruturado / Rendimentos e Amortizações"**, em XML. Testado em 24/09/2026
com o MXRF11:
- a lista (`/fnet/publico/pesquisarGerenciadorDocumentosDados`, filtrando categoria 14 e tipo 41) trouxe
  **134 avisos, de 04/2017 a 08/2026** — um por mês;
- cada documento (`/fnet/publico/downloadDocumento?id=…`) é um XML com **ISIN, ticker, data-base (data-com), valor por
  cota, data de pagamento, período de referência e se é isento de IR**; amortização vem no mesmo documento;
- é a publicação oficial do administrador do fundo — a mesma que a B3 repassa no endpoint de 12 meses.

Limites e o que falta verificar na execução:
- a busca é por **CNPJ do fundo**; é preciso casar ticker → CNPJ (candidatos: cadastro de fundos da CVM em dados
  abertos, ou o próprio FNET, que devolve o nome de pregão "FII MAXI REN" — o mesmo campo `fund` da B3);
- antes de 2017 o aviso não era estruturado (era PDF) — esse período fica para lançamento MANUAL;
- **ETFs** também publicam no FNET? Não verificado (A14). Poucos ETFs pagam (ex.: DIVD11, NDIV11), e os 12 meses da
  B3 cobrem o uso normal.
- Yahoo (5.5) fica como último recurso para FII: todo provento de FII é rendimento, então a falta de "tipo" pesa menos;
  continua sem data de pagamento.

### 5.10 Logos (B17)
Testado em 24/09/2026 carregando cada ícone:

| Fonte | Ativos | Com logo | Observação |
|---|---|---|---|
| BRAPI (`icons.brapi.dev/icons/{TICKER}.svg`) | Ações | 638 de 771 | logo da empresa |
| | Units | 9 de 12 | |
| | FIIs | 66 de 329 | |
| | ETFs (os 219) | 103 | é o logo da **gestora** (iShares, It Now, Trend, BB, Investo, Hashdex…) — 68 ETFs dividem o mesmo da iShares |
| | BDRs | 443 de 892 | 362 de ação, 81 de ETF |
| CoinGecko (`coin-images.coingecko.com`) | Criptos (227) | todas | endereço tem versão (`?1696501442`) e três tamanhos: thumb, small, large |

- **5PRE11 é um dos 116 ETFs sem ícone** — por isso o 404. As colunas de logo das planilhas de ETF e BDR foram
  montadas pelo padrão do endereço, então são **endereços candidatos**: cerca de metade não existe. A rotina da F7
  confere cada um antes de usar.
- O campo `logoUrl` da lista de tickers da BRAPI não serve para saber se há logo: devolve o genérico `BRAPI.svg` até
  para ativos que têm ícone.
- Logos são marcas das empresas e gestoras; para uso pessoal não há problema prático. A CoinGecko pede crédito
  ("dados da CoinGecko") no plano gratuito da API — conferir os termos antes de publicar o PrimeVest para outras pessoas.

---

## 6. Em aberto

Fechados em 24/09/2026: A2 (deslistado), A3 (3 fora), A4 (INHF11), A5 (proventos B3), A7 (BDRs entram),
A8 (cripto às 19:00 — B12), A9 (amortização e restituição — B13), A10 (eventos societários — B14),
A11 (FII além de 12 meses — B15).

Fechados na quarta rodada: A12 (primeiro passo da F6 — B21), A13 (pasta — B18), A14 e A15 (na execução — B22).

Fechado em 24/09/2026 (quinta rodada): A16 — central adiada para o `PENDENCIAS.txt` 13 (B26); custo zero provisório
na bonificação sem custo (B27).

**Nenhuma decisão em aberto.** Padrões assumidos que podem ser trocados a qualquer momento: conferência do catálogo
segunda às 07:00; fechamento B3 e cripto às 19:00 (cripto também no fim de semana); proventos às 20:00; logos mensal.

---

## 7. Ordem de execução e critério de pronto

Ordem decidida em 24/09/2026 (B20):

| # | Etapa | Entrega | Pronto quando |
|---|---|---|---|
| 1 | F3 — Catálogo | V13–V19 + rotina de conferência (só loga; central adiada — B26) | Flyway verde do zero e no banco atual; AALR3 acentuado; ETFs, BDRs e criptos no autocomplete, ETFs e BDRs com a cotação de 23/09; inativos da V14 fora da busca; CoinGecko sem mapa fixo |
| 2 | F7 — Logos | V21 + volume `data/logos` + rotina + `AssetLogo` na Carteira | logos baixados; na Carteira, logo à esquerda do ticker; sem ícone, as iniciais |
| 3 | Token | `.env` com `BRAPI_TOKEN` — **feito em 24/09/2026** | API recriada (`docker compose up -d`) com o token presente (sem imprimir o valor) |
| 4 | F1 — Cliente BRAPI | Cliente v2 + `BrApiException` + testes do cliente | 200/400/401/403/404/429 cobertos; nenhum `catch` devolvendo vazio |
| 5 | F2 — Fechamento | Rotina das 19:00 (B3 e cripto) + recuperação | uma rodada real grava OHLCV dos ativos em carteira e loga `RateLimit-Remaining` |
| 6 | F5 — Proventos | V20 + `B3Client` + rotina de proventos + FNET | uma rodada real grava os eventos dos emissores em carteira e os proventos das posições batem com a B3; amortização reduz o custo |
| 7 | F6 — Eventos societários | A12 + `CorporateActionApplier` + eventos no `PositionCalculator` | testes da F6 verdes; preço médio pós-desdobramento confere com a nota |
| — | Documentação | `Claude.md` e `PENDENCIAS.txt` | seções BrAPI atualizadas; itens 3.2, 6.1, 6.2, 6.4, 11.3 e 11.4 baixados; seção 13 (central) segue aberta |

**Efeito do `.env` antes da F1/F2:** com o token presente, o código atual passa a chamar a BRAPI de verdade e recebe
400 (lote de 15 tickers e `range=1y`). Nada é atualizado — como hoje — e requisição recusada não gasta cota. Só a F1/F2
resolvem; até lá, o log mostra esses erros.

---

## 8. Execução (24/09/2026)

Tudo da seção 7 foi feito, na ordem decidida. Validação: `mvn clean verify` com **319 testes verdes** (240 unitários +
79 funcionais, nenhum pulado), `tsc` e ESLint limpos no front, e uma rodada real no Docker (abaixo).

### 8.1 Entregue

| # | Etapa | O que ficou |
|---|---|---|
| 1 | F3 — Catálogo | V13 (98 nomes), V14 (9 inativos), V15 (14 ações + 9 FIIs + INHF11), V16–V17 (12 categorias, 219 ETFs com OHLCV de 23/09), V18 (22 categorias, 892 BDRs com OHLCV), V19 (`price_source_ref`, 15 categorias, 227 criptos). Busca ignora inativo. `CatalogAuditService` + `CatalogAuditScheduler` (segunda 07:00, só log; lista de ignorados em `primevest.catalog-audit.ignored`) |
| 2 | F7 — Logos | V21, `AssetLogoService` (BRAPI e CoinGecko, confere se é imagem, grava em `data/logos/`), `LogoScheduler` (dia 1 às 06:00 + ativos novos na subida), `GET /api/v1/assets/{ticker}/logo` público com cache de 7 dias, volume no `docker-compose.yml`, `data/logos/` no `.gitignore`, `AssetLogo` à esquerda do ticker na Carteira (`hasLogo` no DTO) |
| 3 | Token | `.env` (24/09) — conferido no container sem imprimir o valor |
| 4 | F1 — Cliente | `BrApiClient` v2: `fetchQuote` → `results[0].data` tipado (`BrApiQuote`), `fetchDailyHistory`, `fetchTickers`, `fetchRenames`; Bearer no header; `BrApiException` com status e `code`; 429 com `Retry-After` e uma nova tentativa; `RateLimit-Remaining` guardado. Testes com `ExchangeFunction` falsa (200/400/401/403/404/429) |
| 5 | F2/F4 — Fechamento | `ClosingPriceService` + `ClosingPriceScheduler`: B3 19:00 seg–sex, cripto 19:00 todo dia; só posições com cotas; `range` 3mo na carga inicial, 5d/1mo/3mo conforme o que falta; recuperação na subida; 401/403/429 param a rodada, 404 pula o ativo; renome só loga. Saíram `PriceUpdateScheduler` e o mapa fixo do `CoinGeckoClient` |
| 6 | F5 — Proventos | V20 (duas camadas) + V22 (parcelas); `B3Client`, `CvmFundRegistry`, `FnetClient`; `ProventosSyncService` (20:00, e sob demanda depois de cada transação, em segundo plano); `DividendRecalculator` refaz os AUTO a partir do catálogo (compra retroativa não chama a B3); AMORTIZACAO e RESTITUICAO_CAPITAL no enum e no custo. Saiu o `DividendSyncScheduler` |
| 7 | F6 — Eventos | `CorporateActionApplier` (puro), eventos intercalados no `PositionCalculator` e na regra B (`PositionGuard`), bonificação sem custo com custo zero provisório + WARN no log |
| — | Documentação | `Claude.md` (integrações, estrutura, Flyway V1–V22), `PENDENCIAS.txt` (3.2, 6.1, 6.2, 6.4, 11.3, 11.4 baixados; 11.1 e 13.2 atualizados) |

### 8.2 O que mudou em relação ao plano

- **V22 — parcelas do mesmo provento.** A B3 publica o provento pago em parcelas como linhas iguais (PETR4, data-com
  01/06/2026: dois JCP de R$ 0,35048636, pagos em 20/08 e 21/09). A unicidade da V20 (ativo, tipo, data-com, valor)
  descartava a segunda parcela. `seq` numera as linhas iguais da mesma resposta; o teste funcional pegou o erro antes
  de ir para a carteira.
- **A14 e A15 (B22).** Ticker → CNPJ pelo **informe mensal de FIIs da CVM** (dados.cvm.gov.br: ISIN → CNPJ, 1.142
  fundos), baixado sob demanda. ETF **não** entra no FNET: fica com os 12 meses da B3; o que faltar é manual.
- **Resposta da B3 embrulhada.** Com `Accept: application/json` a B3 devolve o JSON dentro de uma string JSON; o
  cliente desembrulha (achado na rodada real, com teste).
- **FNET instável.** Alguns avisos travam no download (outros saem em ~100 ms). O cliente desiste em 15 s, tenta cada
  aviso duas vezes e, com três seguidos sem resposta, para; o histórico só é marcado como carregado quando todos os
  avisos foram lidos, e a nova tentativa baixa só os que faltaram.
- **Regra I com eventos.** A venda de 150 depois de um 1:2 sobre 100 cotas era recusada ("sem valor aplicado"); a
  regra passou a olhar os eventos societários (sem as devoluções de capital, para uma amortização que zere o custo não
  recusar edição). Posição com cotas e custo zero continua na Carteira (`Position.hasInvestment`).
- **Disparo manual** (a rotina de conferência previa "sob demanda"): `POST /api/v1/admin/closing`, `/proventos`,
  `/catalog-audit` e `/logos`, só para `is_admin`, respondem 202 e o resultado sai no log.
- **ETF**: `segment` ficou "Miscellaneous"/"Indefinido" como decidido em B2 (os BDRs e as criptos saíram traduzidos,
  como decidido em B10/B11).

### 8.3 Rodada real no Docker (24/09/2026)

Com um usuário temporário de validação (PETR4, MXRF11, BOVA11 e BTC em carteira):

- **Migrations** V13–V22 aplicadas no banco do Docker; catálogo com 2.625 ativos.
- **Logos:** 1.451 de 2.471 ativos com logo (ações 625/795 com fracionários, FIIs 66/347, ETFs 103/219, BDRs 443/892,
  criptos 214/227). `GET .../PETR4/logo` → 200 `image/svg+xml`; `5PRE11` → 404 (a tela mostra as iniciais).
- **Fechamento:** 3/3 ativos B3 atualizados, 129 pregões gravados (carga inicial de 3 meses); cripto 1/1.
- **Proventos:** PETR4 com 12 proventos batendo com a B3 (as duas parcelas de 01/06 presentes, com as datas de
  pagamento certas), MXRF11 com 7 rendimentos; histórico longo da PETROBRAS carregado (161 eventos); cadastro da CVM
  carregado (MXRF11 → CNPJ 97.521.225/0001-25).
- **FNET:** instável na rodada — avisos recentes saíram em ~100 ms, vários antigos não responderam. Entraram os que
  responderam; o histórico do MXRF11 ficou sem a marca de carregado e a rotina retoma só os que faltam.
- O usuário temporário foi apagado no fim (as posições foram junto). Ficaram no catálogo os eventos, o histórico de
  preço, o ISIN e o CNPJ que a rodada gravou — são do papel, não do usuário.

### 8.4 Fica para depois (fora deste plano)

- Central de avisos — `PENDENCIAS.txt`, seção 13 (inclui "Informar custo" da bonificação).
- Lançamento MANUAL de provento pela tela: a coluna `origin` e a regra existem; não há formulário.
- Leitura do histórico de preço (gráfico) — PENDENCIAS 11.2 e 11.6.
- 13 criptos sem logo na CoinGecko: a rotina mensal tenta de novo.
