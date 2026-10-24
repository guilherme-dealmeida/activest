# PrimeVest — Plano dos Índices de Mercado

> **Nota (23/10/2026):** escrito quando o projeto se chamava **PrimeVest**. Nomes de módulo (`primevest-*`), pacote
> (`com.primevest`), configuração (`primevest.*`, `PRIMEVEST_*`), contêineres e versões de migration (V1–V67) citados aqui
> são os do repositório `primevest`; no Activest viraram `activest-*`, `com.activest`, `activest.*`/`ACTIVEST_*` e as
> migrations consolidadas V1–V6 (`docs/planos/PLANO_RENOMEACAO_ACTIVEST.md`).

> Fase: **implementado em 05/10/2026** (X1–X6) — andamento na seção 9. Criado em 05/10/2026; decisões D1–D10 tomadas
> no mesmo dia (todas as recomendações, seção 8) e execução autorizada em seguida.
> Origem: `docs/PENDENCIAS.md` item 5.1 (índices), que destrava o 2.1 (valorização de renda fixa dá sempre zero).

---

## 0. Resumo

| Passo | Resultado em uma linha |
|---|---|
| 1. Levantamento | 6 índices **obrigatórios** (os do enum `Indexer`: CDI, SELIC, IPCA, IGPM, INPC, TR) + 5 **complementares** para escolher (Selic meta, Ibovespa, IFIX, dólar PTAX, poupança) — seção 1 |
| 2. Fonte | **API SGS do Banco Central** para todos os macro (oficial, gratuita, sem token, testada da máquina do Guilherme em 05/10); **site da B3** para Ibovespa/IFIX. BRAPI não serve (macro é plano Startup) — seção 2 |
| 3. Estrutura | `market_indices` (catálogo, seed por migration) + `market_index_values` (série, PK índice+data), core; valor gravado **como publicado**, fator acumulado calculado no serviço — seção 3 |
| 4. Atualização | adm-api: carga histórica por endpoint/bootstrap (arquivos por ano em `carga/data`), rotina diária incremental com releitura da janela recente, recuperação na subida, conferência de atraso — seção 4 |

**Fora deste escopo:** o cálculo da valorização de renda fixa (PENDENCIAS 2.1) — este plano entrega a série e o
cálculo do acumulado entre duas datas; a curva do título, IR/IOF e o calendário de feriados ficam para o escopo da
valorização (D9).

---

## 1. Levantamento dos índices

### 1.1 Obrigatórios — os indexadores que o sistema já aceita

O enum `Indexer` (core) e as constraints `ck_asset_terms_indexer` / `ck_fixed_income_notes_indexer` já aceitam
exatamente estes seis. Sem a série deles, nota pós-fixada ou híbrida não tem como ser valorizada.

| Código | Índice | Série SGS | Periodicidade | Unidade publicada | Uso no PrimeVest |
|---|---|---|---|---|---|
| `CDI` | Taxa DI (CDI) | **12** | diária (dias úteis) | % ao dia | CDB/LCI/LCA/debêntures "% do CDI" e "CDI + x%"; benchmark da renda fixa |
| `SELIC` | Selic efetiva (over) | **11** | diária (dias úteis) | % ao dia | Tesouro Selic; benchmark |
| `IPCA` | IPCA (IBGE) | **433** | mensal (data = 1º dia do mês) | % no mês | Tesouro IPCA+, títulos IPCA+; inflação para rentabilidade real |
| `IGPM` | IGP-M (FGV) | **189** | mensal | % no mês | Tesouro IGPM+ (NTN-C), debêntures antigas |
| `INPC` | INPC (IBGE) | **188** | mensal | % no mês | títulos indexados ao INPC (raros; está no enum) |
| `TR` | Taxa Referencial | **226** | diária (período que começa no dia) | % no período | poupança, LH/LIG antigas (está no enum) |

`SELIC` = série 11 (a efetiva diária), porque é ela que remunera o Tesouro Selic. A meta do Copom é outro índice
(`SELIC_META`, abaixo).

### 1.2 Complementares — para escolher (D1)

| Código | Índice | Fonte | Periodicidade | Para que serve |
|---|---|---|---|---|
| `SELIC_META` | Meta Selic (Copom) | SGS **432** | diária (vigência) | exibir "Selic hoje" (% a.a.); histórico das decisões do Copom |
| `IBOV` | Ibovespa | B3 | diária (pontos) | benchmark de ações |
| `IFIX` | IFIX | B3 | diária (pontos) | benchmark de FII |
| `USD_PTAX` | Dólar PTAX venda | SGS **1** | diária (R$) | stocks, REITs e ETFs internacionais (tipos 8, 9, 10 hoje com preço `MANUAL`), cripto em dólar |
| `POUPANCA` | Rentabilidade da poupança | SGS **195** | por dia de aniversário | benchmark popular (regra própria: rende por aniversário, não por dia útil) |

**Ficam de fora (deriváveis ou sem uso previsto):** CDI e Selic anualizados (4389, 1178 — saem da série diária),
IPCA-15 (7478), IGP-DI (190), IPCA acumulado 12 meses (13522), IDIV/SMLL (mesma fonte do IBOV; entram depois sem
mudança de estrutura, só seed).

---

## 2. Fonte — análise

### 2.1 Testes feitos em 05/10/2026 (rede do Guilherme, pela ponte local)

| Teste | Resultado |
|---|---|
| SGS `bcdata.sgs.12/dados/ultimos/3` | 200 — CDI 0,050788% a.d. em 30/09, 01/10 e 02/10/2026 |
| SGS 433, 188, 189 de 01/01/2010 até hoje | 200 — 200/200/201 linhas, último mês 08/2026 (IGP-M já com 09/2026) |
| SGS 11 e 432, janela 2016–2025 (10 anos) | 200 — 2.510 e 3.653 linhas |
| SGS 12, janela de 10 anos | **timeout em 15 s** (mesma consulta por 1 ano: 253 linhas, rápida) |
| SGS 12 e 226 com janela > 10 anos | **406 Não Aceitável** — limite do BCB para séries diárias |
| SGS 12, 4389, 1178, 195, 1 — ano de 2024 | 200 — 253/253/253/336/253 linhas |
| IBGE SIDRA, tabela 1737 (IPCA) | 200 |
| B3 `indexStatisticsProxy/IndexCall/GetPortfolioDay` (IBOV e IFIX, 2025) | 200 — grade dia × mês com o fechamento, uma chamada por índice por ano |
| SGS e SIDRA a partir do ambiente do Claude (nuvem) | 403 do proxy — bloqueio da política de egress, não se contorna (regra 5.6) |

### 2.2 Comparação

| Fonte | Cobre | Prós | Contras |
|---|---|---|---|
| **BCB SGS** (`api.bcb.gov.br/dados/serie/bcdata.sgs.{n}/dados`) | todos os macro de 1.1 e 1.2 | oficial, gratuita, sem token, JSON simples, uma API para tudo; espelha IBGE (IPCA/INPC), FGV (IGP-M) e B3 (CDI) no mesmo dia | série diária limitada a **10 anos por consulta**; instável às vezes (timeout medido) |
| IBGE SIDRA | IPCA, INPC | fonte primária | só inflação do IBGE; formato verboso; segunda API para manter |
| FGV | IGP-M | fonte primária | sem API gratuita |
| B3 (site) | CDI, Ibovespa, IFIX | fonte primária do CDI e dos índices de ação | não documentada (já usamos o mesmo site para proventos); CDI lá não é mais fácil que no SGS |
| BRAPI | Selic, CDI, IPCA (`/v2/macro`, `/prime-rate`, `/inflation`) | já integrada | **plano Startup** — o gratuito recusa (PLANO_BRAPI, seção 1) |

### 2.3 Recomendação

- **SGS como fonte única dos índices macro** (D4). SIDRA fica como reserva conhecida, sem código agora.
- **B3** para Ibovespa e IFIX, se entrarem (D1).
- Regras do cliente SGS, tiradas dos testes: consulta **por ano civil** (fica longe dos 10 anos e do timeout),
  `dataInicial`/`dataFinal` sempre informados, timeout de 20 s, até 4 tentativas com espera crescente; data em
  `dd/MM/yyyy`, valor em texto com ponto decimal; série mensal vem datada no dia 1º.

---

## 3. Estrutura

### 3.1 Modelo — migration V43 (core)

```sql
-- Catálogo dos índices: o que é e de onde vem. Seed nesta migration (dado de referência).
CREATE TABLE market_indices (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL UNIQUE,   -- CDI, SELIC, IPCA... (mesmo nome do enum Indexer)
    name         VARCHAR(100) NOT NULL,          -- "CDI — Taxa DI"
    periodicity  VARCHAR(10)  NOT NULL,          -- DIARIA | MENSAL
    unit         VARCHAR(20)  NOT NULL,          -- PCT_DIA | PCT_MES | PCT_PERIODO | PCT_ANO | PONTOS | BRL
    source       VARCHAR(20)  NOT NULL,          -- BCB_SGS | B3
    source_ref   VARCHAR(40)  NOT NULL,          -- "12" (código SGS) | "IBOV"
    history_from DATE         NOT NULL,          -- de quando a carga histórica começa (D2)
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT ck_market_indices_periodicity CHECK (periodicity IN ('DIARIA','MENSAL')),
    CONSTRAINT ck_market_indices_source      CHECK (source IN ('BCB_SGS','B3'))
);

-- A série: um valor por índice por data, exatamente como a fonte publica.
CREATE TABLE market_index_values (
    index_id       BIGINT        NOT NULL REFERENCES market_indices(id),
    reference_date DATE          NOT NULL,        -- mensal: 1º dia do mês (como o SGS)
    value          NUMERIC(18,8) NOT NULL,        -- CDI 0,050788 (% a.d.) cabe; IBOV 157.327,26 cabe
    created_at     TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT now(),
    PRIMARY KEY (index_id, reference_date)
);
```

Por que assim:

- **`code` = nome do enum `Indexer`** para os seis obrigatórios: a nota de renda fixa acha a série por
  `fixed_income_notes.indexer` sem tabela de/para. Sem FK de `indexer` para `market_indices` — as constraints
  atuais continuam valendo e nenhuma migration de RF muda.
- **FK resolvida por `code`** nos seeds e no código, nunca por id (convenção das migrations).
- **Valor como publicado** (D6): nada de fator acumulado gravado — segue o princípio já adotado nos indicadores
  (calculados em serviço, sem gravar). O volume é pequeno (CDI 2010–2026 ≈ 4.200 linhas; tudo junto < 40 mil).
- **Sem coluna de estado de processo** (nada de `loaded_at`): o que já foi carregado se reconhece pelo gravado e
  pelos arquivos em `carga/data`.
- Precisão: `numeric(18,8)`, a mesma de preços/quantidades — a regra de precisão do `Claude.md` ganha a linha
  "valores de índice".

### 3.2 Código

| Peça | Módulo | O que faz |
|---|---|---|
| `MarketIndex`, `MarketIndexValue` + repositórios | core | entidades e consultas por `code` e intervalo |
| `MarketIndexCalculator` | core | **fator acumulado** entre duas datas (produto de `1 + taxa/100`), com as variantes "% do índice" (ex.: 110% do CDI: `1 + taxa × 1,10`) e "índice + spread" — testado com valores conhecidos (ex.: CDI acumulado de 2024 confere com a calculadora do BCB). Base para o escopo da valorização da RF |
| `BcbSgsClient` | adm-api (`integration/bcb`) | consulta por ano, regras de 2.3, cache em arquivo (D3) |
| `B3IndexClient` | adm-api (`integration/b3`) | só se IBOV/IFIX entrarem (D1): `GetPortfolioDay` por índice e ano, grade dia × mês → linhas diárias |
| `MarketIndexImportService` | adm-api | carga histórica e incremental, upsert, log de revisão |
| `MarketIndexScheduler` | adm-api | rotina diária + recuperação na subida (seção 4) |
| leitura (D7) | app-api | `GET /api/v1/indices` (lista com último valor) e `GET /api/v1/indices/{code}/history?from=&to=` e `.../accumulated?from=&to=` |

Respeita a regra da separação: **a adm-api grava (fonte externa, catálogo); a app-api só lê.**

---

## 4. Fluxo de atualização

### 4.1 Carga histórica

- `POST /api/v1/admin/indices?from=2010&to=2026&codes=CDI,IPCA&force=false` (adm-api, assíncrono como os demais);
  sem `codes`, todos os ativos. Rotina `indices` no `carga/scripts/admin.mjs`.
- Entra no **`bootstrap`** como última etapa (é pequena e independente; no fim, a ordem das etapas que já existiam não
  muda).
- Por índice e ano: arquivo em `carga/data/bcb/sgs/{codigo}/{ano}.json` (e `carga/data/b3/indices/{IBOV}/{ano}.json`).
  **Ano fechado com arquivo → só disco**; ano corrente → sempre a fonte. Os arquivos sobem para o R2 no próximo
  `data push` (Guilherme), e um ambiente novo carrega sem depender do SGS estar de pé (D3).
- Tempo estimado: ~11 índices × 17 anos ≈ 190 consultas na primeira vez (minutos, não horas) — dentro do que o
  Claude roda (regra 5.3 não se aplica).

### 4.2 Rotina diária (incremental)

Quando cada fonte publica: CDI e Selic do dia D saem no SGS na manhã de D+1; PTAX no próprio dia (~13h); IPCA/INPC
por volta do dia 10 do mês seguinte (9h); IGP-M nos últimos dias do mês de referência; IBOV/IFIX no fim do pregão.

- `MarketIndexScheduler`, **todo dia às 10:00** (D5): para cada índice ativo, consulta de
  *(última data gravada − janela de revisão)* até hoje — na prática, os anos que cobrem essa janela (o corrente e,
  no começo do ano, o anterior), um ano por chamada. Custo: uma ou duas chamadas por índice por dia.
- **Data futura não entra**: a meta Selic (SGS 432) vem preenchida até a próxima reunião do Copom (achado da carga
  real, 05/10).
- **Janela de revisão** (D10): 30 dias nos diários, 3 meses nos mensais. Valor que mudou é sobrescrito, com log
  `índice X, data Y: antigo → novo` (a fonte corrige raramente, mas corrige).
- **Recuperação na subida** (`ApplicationReadyEvent`, como o `ClosingPriceScheduler`): a API roda na máquina do
  Guilherme e pode ficar dias desligada; ao subir, completa o que faltou.
- **Conferência** (`CatalogAuditScheduler`, segunda 07:00, só log): índice diário sem valor há mais de 5 dias úteis,
  mensal há mais de 45 dias depois do mês de referência.
- **Aviso à app-api** (D8): quando o escopo da valorização da RF existir, a rotina publica
  `primevest:catalogo:indices {codes, fromDate}` e a app-api revaloriza as notas daquele indexador. Nesta fase o
  serviço já devolve "o que mudou" (códigos e menor data), sem canal.

### 4.3 Falhas

Fonte fora do ar → log de erro, o índice fica para a próxima rodada ou para a subida seguinte; um índice que falha
não impede os outros. Nenhum dado é apagado por falha.

---

## 5. Etapas

| Etapa | Entrega | Bateria |
|---|---|---|
| **X1** | V43 (tabelas + seed dos índices escolhidos em D1), entidades e repositórios no core, `MarketIndexCalculator` | testes de repositório e do calculador |
| **X2** | `BcbSgsClient` (+ `B3IndexClient` se D1) com cache em arquivo, retentativas e respostas reais gravadas como fixture | testes do cliente com as respostas de 05/10 |
| **X3** | `MarketIndexImportService`, endpoint admin, entrada no `bootstrap`, rotina `indices` no `admin.mjs` | IT de carga e reprocessamento (idempotência, revisão) |
| **X4** | `MarketIndexScheduler` (10:00 + subida), conferência no `CatalogAuditScheduler`, `application.yml` | IT da rodada incremental |
| **X5** | Leitura na app-api (D7) | IT dos endpoints |
| **X6** | Carga real 2010→hoje no banco do DEV, conferência dos números contra a calculadora do BCB, documentação (`Claude.md`, `PENDENCIAS.md` 5.1, `README.md`, `docs/SCHEDULE_TASKS.md`, `docs/ADM_TASKS.md`, `docs/PRIMEVEST_DB.md`) e validação final | bateria completa + subida do compose |

## Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| Código, migration, testes, documentação | ✔ | |
| Carga real (minutos) e conferência dos números | ✔ | |
| `data push` dos arquivos novos de `carga/data` para o R2 | | ✔ |
| Decisões D1–D10 | | ✔ |
| Validação (compilar, testes, lint, subir, conferir) | ✔ | |

---

## 6. Casos de conferência (X6)

| Caso | Esperado |
|---|---|
| CDI acumulado 01/01/2024–31/12/2024 | igual à Calculadora do Cidadão / BCB (fator com 8 casas) |
| IPCA acumulado 2024 | igual ao divulgado pelo IBGE (4,83%) |
| 110% do CDI em 2024 | produto de `1 + CDI × 1,10` dia a dia |
| Rodar a carga duas vezes | nenhuma linha nova, nenhuma alterada |
| Banco novo com `carga/data/bcb` presente | carga sem chamar o SGS para anos fechados |
| API desligada 3 dias | na subida, os dias que faltam entram |

---

## 7. Riscos e achados

- **SGS instável**: timeout medido em consulta de 10 anos do CDI → consulta por ano + retentativas + cache.
- **Limite de 10 anos** em série diária (406) — resolvido pela consulta por ano.
- **Egress da nuvem bloqueado** para BCB e IBGE: testes automatizados usam fixtures; a prova real roda pela ponte
  local (rede do Guilherme), como nas outras integrações.
- O site da B3 de índices não é documentado — mesmo risco que já aceitamos nos proventos.
- **IPCA do mês corrente** não existe até a divulgação: o escopo da valorização da RF terá de decidir entre
  projeção (ANBIMA) e "valor até o último IPCA publicado". Registrado aqui, decidido lá.

---

## 8. Decisões (para o Guilherme)

**Tomadas em 05/10/2026: todas conforme a recomendação** (o Guilherme concordou com D1–D10 e mandou implementar).

| # | Pergunta | Opções | Recomendação (= decisão) |
|---|---|---|---|
| **D1** | Quais índices entram? | os 6 obrigatórios + quais de: Selic meta, Ibovespa, IFIX, PTAX, poupança | 6 obrigatórios + **Selic meta, IBOV, IFIX e PTAX**; poupança depois (regra de aniversário própria) |
| **D2** | Desde quando o histórico? | 2010 (padrão do projeto) / início de cada série | **2010**, extensível pelo `from` do endpoint |
| **D3** | Guardar as respostas em arquivo? | arquivos por ano em `carga/data` + R2 / só banco | **arquivos** (regra "guardar todos os arquivos baixados"; banco novo não depende do SGS) |
| **D4** | Fonte dos macro | SGS único / SIDRA para IPCA-INPC e SGS para o resto | **SGS único** |
| **D5** | Horário da rotina | 10:00 todo dia / outro | **10:00 todo dia** + recuperação na subida |
| **D6** | Gravar fator acumulado? | só valor publicado / também o acumulado | **só o publicado**; acumulado no serviço |
| **D7** | Leitura nesta fase | endpoints sem tela / endpoints + tela de índices / nada | **endpoints sem tela**; tela no escopo de rentabilidade — **revista em 05/10/2026**: a tela entra (seção 10) |
| **D8** | Aviso "índices mudaram" para a app-api | agora (só log) / no escopo da valorização da RF | **no escopo da RF** |
| **D9** | Calendário de feriados (dias úteis futuros, IPCA pro rata) | neste escopo / no escopo da RF | **no escopo da RF** |
| **D10** | Revisão de valores já gravados | reler janela recente e sobrescrever com log / nunca sobrescrever | **reler e sobrescrever com log** (30 dias diários, 3 meses mensais) |

---

## 9. Andamento

| Data | O quê |
|---|---|
| 05/10/2026 | Plano criado; fontes testadas pela rede do Guilherme (seção 2.1). |
| 05/10/2026 | D1–D10 decididas (todas as recomendações); execução autorizada. |
| 05/10/2026 | **X1** — `V43__indices_mercado.sql` (10 índices semeados); core: `MarketIndex`, `MarketIndexValue`, repositórios, `IndexUnit` (diz se acumula e como), `IndexPoint`, `MarketIndexCalculator` (taxa: produto, % do índice, spread `(1+s)^(1/252)` ou `^(1/12)`; nível: fim ÷ base). Testes: `MarketIndexCalculatorTest` (IPCA 2024 = 4,83%), `MarketIndexRepositoryTest`, `SchemaTest` (V43, 10 índices). |
| 05/10/2026 | **X2** — adm-api `integration/indices`: `IndexYearCache` (ano fechado só do disco; corrente e anterior da fonte; 404 grava vazio; 4 tentativas com espera crescente; corpo inválido não é gravado), `BcbSgsClient`, `B3IndexClient`. Respostas reais gravadas em `src/test/resources/indices` (CDI, IPCA e TR 2024 do SGS; Ibovespa e IFIX 2024 da B3). **Achado:** a TR repete o início com fins diferentes (01/05/2024 a 31/05 = 0,0512; a 01/06 = 0,0870) — fica o período de um mês cheio. `IndexClientsTest` (8). |
| 05/10/2026 | **X3** — `MarketIndexImportService` (carga por ano, upsert com log de correção, `changedFrom` por índice para o D8, um índice que falha não para os outros), `POST /api/v1/admin/indices?codes&from&to&recent` (400 com código desconhecido), etapa 8 do `bootstrap`, exemplos no `admin.mjs`. `MarketIndexImportIT` (7) e `BootstrapServiceTest`. |
| 05/10/2026 | **X4** — `MarketIndexScheduler` (10:00, `primevest.indices.*`; recuperação na subida quando há índice atrasado ou vazio); conferência semanal (e `/admin/catalog-audit`) loga os atrasados. **Achado:** a meta Selic vem até a próxima reunião do Copom — data futura deixou de entrar (30 linhas futuras apagadas do banco do DEV). |
| 05/10/2026 | **X5** — app-api `GET /api/v1/indices`, `/{code}/history`, `/{code}/accumulated` (`MarketIndexQueryService`). `IndicesIT` (5). |
| 05/10/2026 | **X6** — **carga real** no DEV: a recuperação na subida da adm-api (com o hot reload) fez a carga histórica inteira em 125 s — CDI 4.208, Selic 4.208, IPCA 200, IGP-M 201, INPC 200, TR 6.119, meta Selic 6.122, PTAX 4.208, Ibovespa 4.156, IFIX 3.910 (desde 30/12/2010); arquivos em `carga/data/bcb/sgs` e `carga/data/b3/indices`. Depois do ajuste da TR, `admin.mjs "indices?codes=TR"` corrigiu 43 datas só do disco (0 s). **Conferência** pela app-api: CDI 2024 = 10,875983% (253 dias), IPCA 2024 = 4,831296% (IBGE: 4,83%), IPCA + 6% em 2024 = 11,121174%, Ibovespa 2024 = −9,35%; meta Selic acumulada → 422. **Bateria:** `mvn clean verify` verde — 504 unitários + 221 de integração (core 147; integration 103; adm-api 87 + 35; app-api 167 + 186). Documentação: `Claude.md`, `docs/PENDENCIAS.md` (5.1 feito, seção 23), `README.md`, `docs/SCHEDULE_TASKS.md` ([A9]), `docs/ADM_TASKS.md` ([18]), `docs/PRIMEVEST_DB.md` (5a). Pendente do Guilherme: `data push` das pastas novas (PENDENCIAS 23.6). |
| 05/10/2026 | `data push` feito pelo Guilherme; no bucket `primevest-data`: `bcb/` com 136 arquivos e `b3/indices/` com 34, os mesmos de `carga/data`. |

---

## 10. Adendo — tela Índices (05/10/2026)

Pedido do Guilherme (revê a D7): menu **Índices** e uma tela com **um card por índice**, um abaixo do outro, com filtro
de período e gráfico espelhando a cotação das Emissoras. Ordem: SELIC, CDI, IPCA, IBOV, IFIX, IGPM, INPC, TR,
SELIC_META, USD_PTAX.

| Peça | O que é |
|---|---|
| Menu | "Índices", depois de "Emissoras", ícone de gráfico de linha (`LineChart` do lucide); título no cabeçalho |
| `IndicesPage` (`/indices`) | os 10 cards na ordem pedida |
| `IndexChartCard` | o desenho do `PriceChart`: título com (i) explicando fonte e unidade, atalhos 1D…MAX (1A padrão), de/até, linha de resumo e gráfico. O período fica no estado do card (são 10 períodos independentes; a URL só caberia um) |
| `GET /api/v1/indices/{code}/chart` | uma chamada por card (`IndexChart` + `MarketIndexQueryService.chart`) |

Decisões de apresentação (tomadas na implementação, pela natureza de cada série):

- **CDI e Selic anualizados** em 252 dias úteis no gráfico, no atual, mín e máx (0,055131% a.d. = 14,90% a.a.) — a taxa
  do dia sozinha não diz nada; o **acumulado** do período usa as taxas diárias de verdade.
- **Mensais (IPCA, IGP-M, INPC) em barras** (a variação do mês pode ser negativa) e em **meses inteiros**: 1A = os 12
  últimos meses publicados; 1M = o último mês; de/até arredonda o início para o dia 1º. **Sem 1D e 7D** (correção do
  Guilherme, 05/10/2026): a tela não mostra os dois atalhos e a API responde 422 se forem pedidos.
- **Mensais com seletor Mensal / Anual** (pedido do Guilherme, 05/10/2026): um dropdown no card escolhe entre a
  variação de cada mês (% no mês, barras) e o **acumulado em 12 meses** de cada mês (% a.a., área) — o "IPCA em 12
  meses" do mercado, não a taxa do mês elevada a 12. Mês sem 12 meses de história fica de fora. Na visão anual o
  resumo mostra os últimos 12 meses, mín e máx dos acumulados; o "acumulado" do período não muda. API:
  `/chart?view=MENSAL|ANUAL` (padrão MENSAL; em índice que não é mensal, 422).
- **Meta Selic em degraus** (vale até a próxima reunião do Copom); **TR** em área com 4 casas.
- **Resumo**: índice de taxa → acumulado no período + atual/último; nível (Ibovespa, IFIX, dólar) → variação em
  pontos/R$ e % (como a cotação); TR e meta Selic → só atual, mín e máx.
- **Densidade** igual à cotação: diário até ~1 ano, semanal até ~5 anos, mensal acima (o acumulado nunca usa a série
  reduzida).

| Data | Andamento |
|---|---|
| 05/10/2026 | Implementado: backend (`IndexChart`, `/chart`), front (`api/indices.ts`, tipos, `IndexChartCard`, `IndicesPage`, menu, rota, cabeçalho). Testes: `IndexChartTest` (4) e `IndicesIT.grafico`; `tsc -b` e lint sem apontamentos. |
| 05/10/2026 | Correção do Guilherme: mensais sem 1D e 7D (tela esconde, API 422). Título do card = nome do cadastro. Conferência no navegador do app com ele logado: 10 cards na ordem, IPCA 1A acumulado 4,22% e 5A 29,90%, Ibovespa 1A +33,46%, dólar −2,27%. CDI e Selic aparecem iguais porque o SGS publica a mesma taxa diária para os dois (conferido no banco: 0,050788 em 02/10/2026). `mvn clean verify` verde: 508 + 222. |
| 05/10/2026 | Visão Mensal / Anual (12 meses) nos mensais: `IndexChart.rolling12`, `view` no `/chart` e no DTO, dropdown no card. `IndexChartTest` (5) e `IndicesIT.grafico`. Conferido no navegador: IPCA 1A anual = 4,22% a.a. nos últimos 12 meses (bate com o acumulado do período), mín 3,81% e máx 5,17%. |
