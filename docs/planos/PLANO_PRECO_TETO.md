# PrimeVest — Plano do Preço Teto e Preço Ideal (modelo PREÇO TETO V2)

> Fase: **implementado em 10/10/2026** (P1–P10) — andamento na seção 13. Criado e revisado no mesmo dia com as
> respostas do Guilherme (seção 12); execução autorizada em seguida, sem pausa entre etapas.
> Origem: pedido de 10/10/2026 (ESCOPO "Implementação do cálculo de Preço Teto e Preço Ideal no Primevest") e a
> planilha `PRECO_TETO_V2.xlsx` (aba `Valuation V2`, linha VALE3). Consome o que o escopo de emissoras, balanços e
> indicadores entregou (`docs/planos/PLANO_EMPRESAS_BALANCOS.md`, F5, F6 e F9) e a Selic meta do escopo de índices
> (`docs/planos/PLANO_INDICES_MERCADO.md`).

---

## 0. Resumo

**Como funciona (revisão de 10/10):** o cálculo é **por ativo, sob demanda do usuário** — ele escolhe a ação, o
formulário abre **preenchido com os padrões** (lucros da CVM, ROE e payout médios, Selic, 15%, 3%, ações, preço), ele
ajusta o que quiser (o lucro é projeção dele, não verdade contábil) e **grava**. Todas as entradas são gravadas e
nenhuma pode ficar vazia. Ao editar, voltam os valores gravados; cada campo tem um botão **↻** que traz o padrão de
hoje para o campo (só persiste ao salvar de novo). O resultado é função das entradas gravadas: só muda quando o
usuário salva de novo.

| Passo do pedido | Resultado em uma linha |
|---|---|
| 1. O que já existe | Tela/CRUD **Preço Teto manual** (`ceiling_prices`, **0 linhas** — conferido em 10/10), `company_quarters` com o **lucro da controladora por trimestre** e a **quantidade sem tesouraria**, ROE e payout do `IndicatorService`, `SELIC_META` diária — seção 1 |
| 2. Fontes | Tudo do **catálogo** (CVM, SGS/BCB, B3). Duas melhorias no catálogo: **payout** com outra base (seção 1.5, D4) e **COTAHIST diário** para ter preço de todas as ações (seção 4.5, D6) |
| 3. Lucro atribuível | `company_quarters.net_income_parent_q` (DRE "atribuído a sócios da controladora", trimestre isolado) — sugestão para os 4 campos de lucro — seção 3 |
| 4. Estrutura | `ceiling_prices` refeita: **todas as entradas gravadas** (NOT NULL), com a origem de cada uma (padrão × informado) e a referência do padrão; `ceiling_price_quarters` com os 4 lucros (valor digitado, unidade, moeda, câmbio, valor em R$) — seção 4 |
| 5–8. Cálculo e validações | `CeilingPriceRules` (fórmulas puras, BigDecimal) sobre as entradas; `CeilingPriceDefaults` monta os padrões; alertas em três níveis — seções 5 e 6 |
| 9. Tela | Lista em "Preço Teto" + página do ativo (formulário com ↻ por campo, Resultado, Memória de cálculo, Alertas); importado × informado × calculado distinguidos — seção 7 |
| 10–12. Testes, conferência, docs | Cenários 1–12 em testes; a linha VALE3 da planilha reproduzida a 1e-6; metodologia em `docs/PRECO_TETO_METODOLOGIA.md` — seções 8 e 10 |

**Achados que mudam o resultado:**

1. **Payout da Vale = 0% no PrimeVest:** ele vem da DVA, e a Vale lança tudo em "Lucros Retidos" na DVA (é a fonte;
   167 de 357 companhias no DFP 2025 têm dividendos zerados lá). O Investidor10 **não usa a DVA**: usa os proventos
   pagos sobre o lucro — seção 1.5 mostra a conta. Correção no catálogo, valendo também para a tela de Emissoras (D4).
2. **Preço parado em 25/09 para quem não está em carteira:** o fechamento diário (BRAPI) só cobre a carteira, e o
   COTAHIST foi carga manual. A B3 publica um COTAHIST **por dia** (09/10/2026: 564 KB, todas as ações) — uma rotina
   diária resolve o catálogo inteiro sem gastar cota da BRAPI (seção 4.5, D6).

**Fora deste escopo:** FCD por FCFE, Graham e Bazin; FII, BDR, ETF (D1); alerta por e-mail/notificação de preço
(D10); histórico de versões do cálculo (D9).

---

## 1. Levantamento do que já existe (passo 1)

### 1.1 Preço Teto atual — manual

| Peça | Onde | O que faz | Destino neste escopo |
|---|---|---|---|
| `ceiling_prices` (V1) | core | `user_id`, `asset_id`, `max_price`, `alert_enabled`; único por (usuário, ativo) | **Refeita** (seção 4). **0 linhas** — sem migração de dados |
| `CeilingPrice`, `CeilingPriceRepository` | core | entidade/repositório | reescritos |
| `CeilingPriceService/Controller`, DTOs | app-api | CRUD `/api/v1/ceiling-prices`, diferença % contra o preço atual | reescritos |
| `CeilingPricesPage.tsx`, `api/ceilingPrices.ts` | web | tabela com filtro por tipo, modal de cadastro manual | reescritos (seção 7) |
| `alert_enabled` | — | **não é usado em lugar nenhum** (não existe rotina de alerta) | D10 |
| UC07 em `docs/USE_CASES.md` | docs | "cadastrar/editar/excluir preço teto por ativo, com alerta" | atualizado |

### 1.2 Dados do catálogo que o modelo usa

| Entrada do pedido | Já existe? | Onde | Observação |
|---|---|---|---|
| 1. Ticker | sim | `assets` + `assets.issuer_id` | só ações com emissor (`IssuerKind.COMPANY`) têm balanço |
| 2. Ações sem tesouraria | sim | `company_quarters.shares_outstanding` (= total − tesouraria, da composição do capital/FRE, escala conferida) | ajustar por desdobramento/grupamento/bonificação posterior ao balanço, **como o `IndicatorService` já faz** (`ratio`); balanço em REVISAR usa o último conferido |
| 3. Payout | sim | `IndicatorRules` — dividendos + JCP da DVA ÷ lucro da controladora | **achado 1** — D4 |
| 4. ROE | sim | `IndicatorRules` — lucro da controladora 12m ÷ PL médio (início e fim) | média de 3 anos ainda não existe — D3 |
| 5. Lucro dos 4 trimestres | sim | `company_quarters.net_income_parent_q` | seção 3 |
| 6. Taxa de desconto | sim | `market_index_values` de `SELIC_META` (% a.a.) — **13,75% em 10/10/2026**, igual à planilha | D5 |
| 7. Margem de segurança | não | constante 15% | configuração |
| 8. Crescimento na perpetuidade | não | constante 3% | configuração |
| 9. Preço atual | parcial | `assets.current_price` (só em carteira) → último `asset_price_history.close_price` | **achado 2** — D6 |

### 1.3 Código reaproveitável

- `IndicatorService` (app-api): leitura de `company_quarters` por emissor, fator de eventos societários entre datas
  (`ctx.ratio`), quantidade de ações com o fallback do balanço em revisão, preço atual com fallback de fechamento,
  fracionário pelo lote padrão, unit pela composição. **Extrair** essa leitura para um componente comum
  (`CompanyFundamentalsReader`) usado pelos dois serviços, em vez de copiar — a regra de "quantidade de ações atual"
  tem de ser a mesma na tela de Emissoras e no Preço Teto.
- `IndicatorRules` (ROE, payout, `average`) — os padrões de ROE e payout saem daqui.
- `TickerLink`, `MaskedNumberInput`, `Modal`, `Card`, `Badge`, `useSortable` (web).

### 1.4 Dados reais conferidos em 10/10/2026 (VALE3, emissor 348)

| Trimestre | Lucro da controladora (`net_income_parent_q`) | Ações sem tesouraria |
|---|---|---|
| 3T25 (30/09/2025) | 14.617.000.000 | 4.268.779.000 |
| 4T25 (31/12/2025) | **−21.048.000.000** | 4.268.779.000 |
| 1T26 (31/03/2026) | 9.953.000.000 | 4.087.908.000 |
| 2T26 (30/06/2026) | 6.847.000.000 | **4.072.366.000** (total 4.255.763.000) |
| **TTM** | **10.369.000.000** (= `net_income_parent_ttm` do catálogo) | |

Planilha × PrimeVest:

| Item | Planilha | PrimeVest | Nota |
|---|---|---|---|
| LL T-3 … LL T | 9.956.933.910 / 6.940.807.500 / 9.956.933.910 / 6.940.807.500 | 14.617 / −21.048 / 9.953 / 6.847 (mi) | projeção do Guilherme (semestre duplicado de propósito, 10/10): é o uso esperado — o lucro é premissa do usuário, o catálogo só sugere |
| LL TTM | 33.795.482.820 | 10.369.000.000 | |
| N | 4.262.140.830 | 4.072.366.000 | a planilha usa o **total**; sem tesouraria o preço sobe ~4,7% |
| Payout | 48,19% | **0%** pela DVA (Vale: 7.08.04.01 e 7.08.04.02 = 0 no DFP 2025) | achado 1 |
| ROE | 19,26% | ~14,7% (média 2023–2025: 21,1% / 15,9% / 7,1%, aprox.) | D3 |
| Desconto | 13,75% | 13,75% (`SELIC_META`) | igual |
| Preço atual | 67,63 | 70,77 (fechamento de 25/09/2026, COTAHIST) | achado 2 |

Simulação (só para dimensionar, fórmulas do pedido):

| Cenário | LL TTM | g | Preço Teto | Preço Ideal |
|---|---|---|---|---|
| Planilha, entradas da planilha | 33,80 bi | 9,98% | **82,6607** | **70,2616** |
| Dados reais + payout/ROE da planilha | 10,37 bi | 9,98% | 26,54 | 22,56 |
| Dados reais + padrões (payout DVA 0, ROE 3a ≈14,7%) | 10,37 bi | 14,69% | 28,58 | 24,29 |

O prejuízo de 4T25 (impairment) derruba o TTM — exatamente o caso que o pedido manda **não** remover (critério 5) e
sinalizar (validação 7).

### 1.5 Payout: por que o PrimeVest dá 0% e o Investidor10 não (VALE3, conferido em 10/10)

O PrimeVest calcula payout = (dividendos + JCP **da DVA**) ÷ lucro da controladora. Na DVA da Vale (DFP 2025, contas
7.08.04.01 e 7.08.04.02) os dois são 0 e tudo vai para 7.08.04.03 "Lucros Retidos" — é como a Vale preenche, não erro
de leitura. O Investidor10 usa **proventos sobre lucro**. Três bases possíveis, todas com dado que já está no projeto:

| Ano | Investidor10 | DVA (hoje) | **DFC** — "Dividendos/JCP pagos a acionistas" (6.03.04) ÷ lucro da controladora | Proventos B3 por **data-com** no ano ÷ LPA | Proventos B3 por **pagamento** no ano ÷ LPA |
|---|---|---|---|---|---|
| 2025 | 146,46% | 0% | **144,6%** | 235,4% | 140,8% |
| 2024 | 67,90% | 0% | **65,4%** | 72,3% | 65,3% |
| 2023 | 70,80% | 0% | **70,0%** | 65,4% | 68,6% |
| 2022 | 33,74% | 0% | **35,6%** | 35,4% | 34,1% |
| 2021 | 53,64% | 0% | 60,5% | 58,5% | 58,5% |
| 2018 | 41,33% | 0% | 50,9% | 40,6% | 49,2% |
| 2017 | 22,81% | 0% | 26,5% | 39,7% | 27,1% |

- Nenhuma base bate exatamente com o Investidor10 (ele não publica a fórmula; muda com lucro consolidado ×
  controladora, ações totais × sem tesouraria, data-com × pagamento). Nos 4 anos recentes, **dinheiro pago no ano ÷
  lucro** (DFC ou proventos por pagamento) fica a até 2 p.p. dele.
- "Atual" do Investidor10 (240,28%) = proventos dos últimos 12 meses ÷ lucro dos 12 meses; com os nossos dados dá
  ~220% (5,61 por ação × 4,07 bi ações ÷ 10,37 bi).
- **Payout acima de 100% é real** em 2025: a Vale pagou ~R$ 20 bi com lucro de R$ 13,8 bi (o 4T25 teve impairment).
  Com payout 146%, g = ROE × (1 − 1,46) fica **negativo** — o alerta `G_NEGATIVO` (seção 6) existe para isso, e é
  onde o usuário ajusta a premissa.
- **DFC** tem a vantagem de vir do mesmo documento e da mesma escala do lucro (sem converter por ação nem depender
  de quantidade de ações), existir para todas as companhias e para os trimestres (ITR acumulado). Precisa de uma
  métrica nova no catálogo (`DIVIDENDOS_PAGOS`, conta 6.03 por descrição) e coluna em `company_quarters`; a
  recarga é do disco (~5 min, sem download). Risco: bancos e seguradoras têm DFC em outro layout — conferir na carga.

---

## 2. Fontes (passo 2)

| Dado | Fonte primária | Já carregado por | Atualização |
|---|---|---|---|
| Lucro trimestral da controladora | CVM dados abertos — ITR e DFP (consolidado; individual só sem consolidado — D6 do plano de balanços) | adm-api, `CvmStatementsScheduler` 07:30 + `company_quarters` | diária (ano corrente e anterior) |
| Ações e tesouraria | CVM — composição do capital do ITR/DFP, conferida pelo FRE/LPA | idem (`share_counts`) | idem |
| Desdobramento/grupamento/bonificação | B3 (`asset_stock_events`) | adm-api, rotina de proventos 20:00 | diária |
| ROE, payout | derivados dos dois acima (`IndicatorRules`) | calculados na leitura | — |
| Taxa de desconto | BCB SGS 432 (`SELIC_META`) | adm-api, rotina de índices 10:00 | diária |
| Preço | BRAPI (carteira) / B3 COTAHIST (catálogo inteiro — hoje só carga manual) | adm-api | **COTAHIST diário para o catálogo inteiro — seção 4.5, D6** |
| Câmbio (só para lucro informado em dólar) | BCB SGS 1 (`USD_PTAX`, venda) | adm-api, índices | diária |

Nenhuma fonte nova é necessária. A app-api continua sem chamar fonte externa (regra do projeto): lê o catálogo.

---

## 3. Lucro líquido atribuível aos acionistas (passo 3)

- **Conceito único:** `net_income_parent_q` = linha da DRE "Lucro/Prejuízo atribuído a sócios da empresa
  controladora" (`FinancialMetric` do lucro da controladora), consolidado. Os quatro trimestres saem da mesma coluna,
  então o conceito contábil é o mesmo por construção (critério 3).
- **Trimestre isolado:** coluna de 3 meses do ITR, senão acumulado − acumulado anterior; o 4T sai de DFP − ITR de 9
  meses (`QuarterSeries`). Já testado e conferido (Petrobras 2024–2025).
- **Quatro últimos:** os quatro `period_end` mais recentes do emissor, **consecutivos** (cada um 80 a 100 dias depois do
  anterior — mesma tolerância da `QuarterSeries`). Buraco na sequência ou valor nulo = **trimestre ausente**: o
  campo vem vazio com o motivo ("a CVM ainda não tem o 4T25"): não salva nem calcula o TTM e o que depende dele até o
  usuário informar o valor (cenário 5).
- **TTM:** soma dos quatro exibidos (com os substituídos). Quando a soma dos quatro do catálogo difere de
  `net_income_parent_ttm` (no DFP o catálogo usa o anual, e o ITR seguinte pode ter reapresentado um trimestre — 565
  de 4.561 exercícios, F6), a tela mostra os dois e um aviso "reapresentação"; vale a soma dos quatro, como na
  planilha.
- **Origem por trimestre (critério 4):** documento (DFP/ITR), data de referência, versão, data de entrega à CVM
  (`cvm_documents.received_at`), consolidado/individual. No informado: "informado por você" + unidade, moeda, câmbio e a nota.
- **Moeda e unidade:** a CVM publica em R$ com `ESCALA_MOEDA` (MIL/UNIDADE); a importação já normaliza para reais em
  unidade (Vale: `13814000 MIL` → 13.814.000.000). Normalização nova só para o valor **digitado** em outra unidade ou moeda (seção 4.3).

---

## 4. Estrutura de dados (passos 4 e 5)

Dado de usuário → tabelas no **core**, gravadas só pela **app-api**. Percentuais em **pontos** (13,75), como o resto
do sistema; a conversão para decimal é feita só dentro das regras (validação 1). **Todas as entradas são NOT NULL**:
o formulário vem com o padrão e não salva campo vazio.

### 4.1 Migration V45 — `ceiling_prices` refeita (tabela vazia, sem migração de dados)

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | UUID PK | |
| `user_id`, `asset_id` | FK, `unique(user_id, asset_id)` | um cálculo por ativo (D9) |
| `shares` | BIGINT NOT NULL, `> 0` | N |
| `payout_pct`, `roe_pct` | NUMERIC(9,4) NOT NULL | |
| `discount_rate_pct`, `perpetual_growth_pct`, `safety_margin_pct` | NUMERIC(9,4) NOT NULL | `safety_margin_pct` entre 0 e 100 (exclusive) |
| `price` | NUMERIC(18,8) NOT NULL, `> 0` | preço usado na comparação |
| `inputs_origin` | JSONB NOT NULL | por campo: `PADRAO` ou `INFORMADO` + a **referência do padrão no momento** (ex.: `{"discount_rate": {"origin": "PADRAO", "ref": "SELIC_META 10/10/2026", "default": 13.75}}`) — base da diferenciação visual e do aviso "o padrão mudou" |
| `notes` | VARCHAR(500) | |
| `calculated_at` | TIMESTAMP NOT NULL | quando o usuário gravou |
| `created_at`, `updated_at` | TIMESTAMP | |

Saem `max_price` (D7: o teto passa a ser calculado) e `alert_enabled` (D10). Limites de "valor extremo" são
**alerta**, não constraint (validação 5).

**Origem decidida ao salvar:** valor igual ao padrão do momento → `PADRAO` (com a referência); diferente →
`INFORMADO` (guarda também qual era o padrão, para a tela mostrar "você informou 48,19%; o padrão era 65,4%").

### 4.2 `ceiling_price_quarters` — os 4 lucros (sempre 4 linhas)

| Coluna | Tipo | Para quê |
|---|---|---|
| `ceiling_price_id` | FK CASCADE | |
| `position` | SMALLINT 1–4 | T-3 … T; PK (`ceiling_price_id`, `position`) |
| `period_end` | DATE NOT NULL | o trimestre (rótulo 4T25 etc.) |
| `original_value` | NUMERIC(24,2) NOT NULL | o número como foi digitado (pode ser negativo) |
| `unit` | VARCHAR(8) NOT NULL | `UNIDADE`, `MIL`, `MILHAO`, `BILHAO` |
| `currency` | VARCHAR(3) NOT NULL | `BRL`, `USD` (D8) |
| `fx_rate` | NUMERIC(18,8) NOT NULL | 1 em BRL; PTAX do fim do trimestre por padrão em USD, editável |
| `value_brl` | NUMERIC(24,2) NOT NULL | normalizado — o que entra no cálculo |
| `origin` | VARCHAR(10) NOT NULL | `PADRAO` (veio da CVM sem alteração) ou `INFORMADO` |
| `source_ref` | VARCHAR(300) | no padrão, o documento ("CVM ITR 30/06/2026 v1, entregue 08/08/2026"); no informado, a nota do usuário (obrigatória — "semestre duplicado", "release 3T26") |

### 4.3 Normalização (passo 5)

`value_brl = original_value × fator(unit) × fx_rate`, fatores 1 / 10³ / 10⁶ / 10⁹; em USD, padrão = `USD_PTAX` do
`period_end` (ou do último dia útil anterior), editável. Classe pura `MoneyNormalizer`, testada à parte (cenários 6
e 7). Sem PTAX na data → "câmbio não encontrado para dd/mm/aaaa; informe a taxa". O padrão da CVM vem em R$ unidade;
a tela pode exibir em milhões, mas grava o normalizado.

### 4.4 O resultado

As entradas gravadas determinam o resultado inteiro, então ele **não é gravado**: `CeilingPriceRules` recalcula a
partir delas a cada leitura (é aritmética pura, sem ler o catálogo) e dá sempre o mesmo número até o usuário salvar
de novo. O que lê o catálogo é só o **padrão** (para preencher o formulário, para o ↻ e para o aviso "o padrão
mudou"). Na lista, a coluna "fechamento de hoje" e a margem contra ele são de leitura, à parte do cálculo gravado
(D13).

### 4.5 Catálogo — o que muda fora do Preço Teto

| Mudança | Módulo | Motivo |
|---|---|---|
| **Rotina diária do COTAHIST** (`COTAHIST_DddMMaaaa.ZIP` da B3, ~0,5 MB/dia, todas as ações e FIIs): baixa o arquivo do pregão à noite (nova tentativa na manhã seguinte, se a B3 ainda não publicou), grava em `asset_price_history` (`source = B3_COTAHIST`, cache em `carga/data/b3/cotahist`), e na primeira rodada completa os dias que faltam desde o último carregado (26/09 → hoje: ~10 arquivos, ~5 MB) | adm-api (`HistoricalQuotesImportService` + `CotahistQuote` já existem; falta o cliente do arquivo diário e o agendamento) | preço de todo o catálogo sem cota da BRAPI (15.000/mês não cobre 785 ações × 21 pregões); serve também à tela de Emissoras. A BRAPI segue para a carteira (dado do mesmo dia mais cedo) |
| **Payout pela DFC** (D4): métrica `DIVIDENDOS_PAGOS`, colunas `dividends_paid_q/_ttm` em `company_quarters` (migration), `IndicatorRules.payout` passa a usar | core + adm-api + app-api | seção 1.5; tela de Emissoras e Preço Teto mostram o mesmo payout |

---

### 4.6 Resumo das alterações no banco

| Tabela | Tipo | Alteração |
|---|---|---|
| `ceiling_prices` | usuário, existente (0 linhas) | recriada: saem `max_price` e `alert_enabled`; entram `shares`, `roe_pct`, `payout_pct`, `discount_rate_pct`, `perpetual_growth_pct`, `safety_margin_pct`, `price` (todos NOT NULL), `inputs_origin` (JSONB NOT NULL), `notes`, `calculated_at`; ficam `id`, `user_id`, `asset_id`, `unique(user_id, asset_id)`, `created_at`, `updated_at` |
| `ceiling_price_quarters` | usuário, **nova** | 4 linhas por cálculo (4.2) |
| `company_quarters` | catálogo, existente (derivada, recalculada inteira) | + `dividends_paid_q`, `dividends_paid_ttm` NUMERIC(24,2), nulas quando a DFC não tem a conta |
| `cvm_statement_values` | catálogo | sem mudança de estrutura: valores novos da métrica `DIVIDENDOS_PAGOS` |
| `asset_price_history` | catálogo | sem mudança de estrutura: linhas diárias novas com `source = B3_COTAHIST` |
| `market_index_values`, `share_counts`, `cvm_documents`, `assets`, transações/posições | — | só leitura (padrões, preço médio) |

Não é gravado: g, FC1–FC3, VPLs, valor terminal, patrimônio, teto, ideal e margens — saem das entradas gravadas.

## 5. Cálculo (passos 6 e 7)

### 5.1 `CeilingPriceRules` (app-api, pura, sem Spring)

Entrada: `Inputs(List<Quarter> q (4), Long n, BigDecimal payoutPct, roePct, discountPct, marginPct, growthPct, price)`.
Saída: `Result` com **cada** valor intermediário (nulo quando não calculável) + lista de `Issue` (código, nível,
campo, mensagem). Fórmulas exatamente as do pedido, na ordem da planilha:

| # | Valor | Fórmula | Coluna da planilha |
|---|---|---|---|
| 1 | LL_TTM | Σ dos 4 trimestres | J |
| 2 | g | ROE × (1 − Payout) | E (lá digitado; aqui sempre calculado) |
| 3 | FC1, VPL1 | FC1 = LL_TTM; VPL1 = FC1 ÷ (1+d) | K, N |
| 4 | FC2, VPL2 | FC1 × (1+g); ÷ (1+d)² | O, P |
| 5 | FC3, VPL3 | FC2 × (1+g); ÷ (1+d)³ | Q, R |
| 6 | Valor terminal | FC3 × (1+G) ÷ (d − G), **só se d > G** | T |
| 7 | VPL terminal | VT ÷ (1+d)³ | U |
| 8 | Valor do patrimônio | VPL1 + VPL2 + VPL3 + VPL terminal | V ("MARKET CAP") |
| 9 | Preço Teto | Patrimônio ÷ N (× ações da unit, D1) | X |
| 10 | Preço Ideal | Teto × (1 − MS) | Z |
| 11 | Margens | Teto ÷ P − 1; Ideal ÷ P − 1, **só se P > 0** | Y, AA |

- Aritmética `BigDecimal` com `MathContext.DECIMAL128`; arredondamento **só na exibição** (R$ 2 casas na tela, 4 no
  DTO; % 2 casas). Critério 2: o teste compara com a planilha a 1e-6.
- Sem `clamp`: prejuízo, g negativo, patrimônio negativo e teto negativo passam pela conta como são (validação 6) e
  geram alerta.
- "Valor do patrimônio" é o nome na tela; a planilha chama de MARKET CAP — a tela mostra o rótulo novo com o antigo
  na dica, para quem confere.

### 5.2 `CeilingPriceDefaults` (app-api) — os padrões

Usado para abrir um cálculo novo, para o ↻ de cada campo e para o aviso `PADRAO_MUDOU`. Nunca altera o que está
gravado.

| Campo | Padrão | Referência mostrada |
|---|---|---|
| 4 lucros | os 4 últimos trimestres consecutivos de `net_income_parent_q` (seção 3) | documento CVM de cada um |
| N | ações sem tesouraria do último balanço × fator dos eventos posteriores (leitor comum, 1.3); unit × composição | "balanço 30/06/2026 (sem tesouraria)" |
| ROE | média dos **3 últimos exercícios fechados** (D3, decidido) | "média 2023–2025: 21,1 / 15,9 / 7,1" |
| Payout | idem, na base de D4 | "média 2023–2025 (DFC)" |
| d | `SELIC_META` **vigente** (D5) — pré-preenche só o cálculo novo; na edição vem a gravada e o ↻ busca a vigente | "Selic meta de 10/10/2026" |
| G, MS | 3% e 15% (`primevest.preco-teto.*` no `application.yml`) | "padrão do sistema" |
| Preço | último fechamento (COTAHIST diário / BRAPI) | "fechamento de 09/10/2026" |

Ativo sem emissor `COMPANY` → "modelo só para ações com balanço na CVM" (D1); fracionário → lote padrão.

### 5.3 API (app-api)

| Método | Rota | Uso |
|---|---|---|
| GET | `/api/v1/ceiling-prices` | lista do usuário: entradas-resumo, resultado, fechamento de hoje e preço médio da carteira (se houver) com as margens contra cada um, nº de alertas, "padrão mudou" |
| GET | `/api/v1/ceiling-prices/defaults/{ticker}` | padrões de hoje com as referências (abrir um cálculo novo; ↻ de qualquer campo) |
| POST | `/api/v1/ceiling-prices/calculate` | **sem gravar**: entradas do formulário → resultado + memória + alertas (prévia enquanto digita) |
| GET | `/api/v1/ceiling-prices/{id}` | entradas gravadas + resultado + memória + alertas |
| POST / PUT | `/api/v1/ceiling-prices[/{id}]` | gravar (todas as entradas obrigatórias; com alerta de ATENÇÃO exige `confirmWarnings=true`) |
| DELETE | `/api/v1/ceiling-prices/{id}` | excluir o cálculo |

---

## 6. Validações e mensagens (passo 8)

Três níveis: **BLOQUEIO** (o valor e tudo que depende dele ficam "não calculado", com o motivo), **ATENÇÃO** (calcula e
avisa; ao salvar, pede confirmação) e **INFORMAÇÃO**. A tela lista, para cada bloqueio, **quais campos faltam e quais cálculos não puderam ser feitos** (validação 8).

| Código | Nível | Condição | Não calcula | Mensagem (resumo) |
|---|---|---|---|---|
| `TRIMESTRE_AUSENTE` | BLOQUEIO | trimestre nulo ou sequência com buraco | TTM em diante | "Falta o lucro do 4T25. Informe o valor ou aguarde o balanço." |
| `ACOES_AUSENTES` | BLOQUEIO | N nulo ou ≤ 0 | Teto, Ideal, margens | "Quantidade de ações ausente (balanço em revisão)…" |
| `DESCONTO_MENOR_IGUAL_G` | BLOQUEIO | d ≤ G | VT, VPL terminal, patrimônio, Teto, Ideal, margens | "A taxa de desconto (3,00%) precisa ser maior que o crescimento na perpetuidade (3,00%)." |
| `CAMPO_VAZIO` | BLOQUEIO (não salva) | qualquer entrada vazia (o padrão inexistente deixa o campo vazio, com o motivo) | o que depende do campo | "ROE sem histórico de balanço anual; informe o valor." |
| `PRECO_INVALIDO` | BLOQUEIO (só margens) | P nulo ou ≤ 0 | margens | "Sem preço atual: margens não calculadas." |
| `LL_TTM_NEGATIVO` | ATENÇÃO | TTM < 0 | — | "Lucro dos 12 meses negativo: o Preço Teto sai negativo e não representa valor." |
| `LL_TTM_ZERO` | ATENÇÃO | TTM = 0 | — | idem, teto zero |
| `LL_TTM_BAIXO` | ATENÇÃO | TTM positivo, mas abaixo de 3% do PL da controladora (D2) | — | "Lucro muito baixo em relação ao patrimônio: resultado pouco representativo." |
| `TRIMESTRE_COM_PREJUIZO` | INFORMAÇÃO | algum trimestre < 0 | — | "O 4T25 teve prejuízo de R$ 21,0 bi; ele entra na soma." |
| `G_NEGATIVO` | ATENÇÃO | g < 0 (payout > 100% ou ROE < 0) | — | "Crescimento sustentável negativo…" |
| `G_MAIOR_QUE_D` | ATENÇÃO | g ≥ d | — | "Crescimento explícito acima do desconto…" |
| `PREMISSA_EXTREMA` | ATENÇÃO | ROE, payout, d ou G negativo ou acima de 100%; d fora de 5–30%; G acima de 6%; N mais de 20% longe do padrão; lucro digitado mais de 50% longe do da CVM (D2) | — | "Payout de 146,0% está acima de 100%. Confira." |
| `MARGEM_INVALIDA` | BLOQUEIO (não salva) | MS negativa ou ≥ 100% | Ideal e margem do ideal | "A margem de segurança precisa estar entre 0% e 100%." |
| `REAPRESENTACAO` | ATENÇÃO | Σ 4 trimestres ≠ TTM do catálogo | — | "O balanço seguinte reapresentou um trimestre…" |
| `BALANCO_DEFASADO` | ATENÇÃO | último trimestre com mais de 150 dias | — | "Último balanço: 30/06/2026." |
| `PRECO_DEFASADO` | ATENÇÃO | preço com mais de 5 dias úteis (D6) | — | "Preço de 25/09/2026 (último disponível)." |
| `MEDIA_INCOMPLETA` | ATENÇÃO | padrão de ROE/payout com menos de 3 anos | — | "Média de 2 anos (2024–2025): a companhia não tem 3 DFPs." |
| `PADRAO_MUDOU` | INFORMAÇÃO | o padrão de hoje difere do valor gravado (Selic mudou, saiu balanço novo, preço novo) | — | "Há balanço mais recente (3T26)." — o ↻ do campo fica destacado |

Nenhuma dessas regras troca valor do usuário nem do catálogo (critério 8).

---

---

## 7. Tela (passo 9)

Menu **Preço Teto** (rota `/ceiling-prices`).

**7.1 Lista** — os cálculos gravados pelo usuário: ATIVO (logo + `TickerLink`), PREÇO TETO, PREÇO IDEAL, e três colunas
de comparação (D13), cada uma com a margem do teto e do ideal contra ela: **PREÇO DO CÁLCULO** (o gravado, com data),
**FECHAMENTO DE HOJE** (leitura do catálogo) e **PREÇO MÉDIO** (da carteira do usuário, só se ele tiver o ativo — o
mesmo do `PositionCalculator`/tela de Carteira; vazio se não tiver), situação (chip "abaixo do ideal" / "entre ideal e teto" /
"acima do teto"), ícone de alertas, ícone "padrão mudou" (ex.: saiu balanço novo), CALCULADO EM. Ordenável, filtro por
situação. Ações: abrir/editar, excluir. Botão **Novo cálculo** → busca de ticker (só ações com emissor; ticker que já
tem cálculo abre o existente).

**7.2 Página do ativo** (`/ceiling-prices/:ticker`) — formulário e resultado lado a lado (empilhados no celular):

1. **Lucros trimestrais** — 4 linhas (trimestre, valor, unidade, moeda, câmbio se USD, nota), cada uma com **↻**
   (traz o valor da CVM daquele trimestre). Botão **"Atualizar trimestres"** quando existe balanço mais novo que a
   janela gravada (traz os 4 mais recentes). Linha calculada **LL TTM**.
2. **Premissas** — N, ROE, Payout, d, G, MS, preço: cada input com **↻** à direita (aplica o padrão de hoje ao input;
   o ↻ fica destacado quando o padrão de hoje difere do gravado, com a dica "padrão hoje: 14,25% — Selic meta de
   dd/mm"). g aparece calculado (ROE × (1 − payout)), não editável.
3. **Resultado** — Preço Teto, Preço Ideal, preço usado, margens; recalcula enquanto digita (`/calculate`), grava só
   em **Salvar**. Faixa fixa: *"Estimativa baseada no modelo PREÇO TETO V2 e nas premissas acima. Não é garantia nem
   recomendação de valor de mercado."* (passo 9.9).
4. **Memória de cálculo** — FC1, VPL1, FC2, VPL2, FC3, VPL3, VT, VPL terminal, valor do patrimônio, teto, ideal, com
   o peso do terminal no total; cada linha expande "como foi calculado": a fórmula e a fórmula com os números (ex.:
   `VPL2 = 37.167.800.896 ÷ (1,1375)² = 28.725.265.757`) — passo 9.7.
5. **Alertas e dados faltantes** — seção 6, bloqueios primeiro.
6. **Datas** — calculado em, referência de cada padrão (balanço, Selic, preço) — passo 9.8.

Botão **"Restaurar todos os padrões"** no topo do formulário: aplica os padrões de hoje em todos os campos de uma
vez (lucros, N, ROE, payout, Selic vigente, G, MS, preço) — sem persistir; só vale ao salvar. O cálculo salvo é o
**retrato da última edição** (D9): campos só mudam a pedido do usuário.

**Sair com alterações não salvas** pede confirmação. **Salvar** com alerta de ATENÇÃO mostra a lista e pede "salvar
mesmo assim".

**Diferenciação visual (passo 9.6)** — legenda fixa no topo:

| Origem | Estilo |
|---|---|
| Padrão / importado (catálogo) | texto branco + etiqueta da fonte (CVM, B3, BCB) |
| Informado pelo usuário | borda/realce dourado (`#c9a84c`) + ícone de lápis; dica com o padrão da época |
| Calculado | texto cinza-claro + ícone `ƒx`, não editável |

Ligações: o ticker da lista abre Emissoras (já existe). Sem botão de Preço Teto na página da emissora (decidido em
10/10/2026).

---

## 8. Testes (passos 10 e 11)

### 8.1 Cenários do pedido → testes

| # | Cenário | Onde |
|---|---|---|
| 1 | 4 trimestres positivos | `CeilingPriceRulesTest` |
| 2 | 1 prejuízo + 3 lucros (entra na soma, alerta) | `CeilingPriceRulesTest` + `PrecoTetoIT` com o padrão real da VALE3 (4T25) |
| 3 | TTM = 0 | `CeilingPriceRulesTest` |
| 4 | TTM negativo (teto negativo, sem clamp) | `CeilingPriceRulesTest` |
| 5 | trimestre ausente (padrão vazio com motivo; não salva; informar resolve) | `CeilingPriceDefaultsIT` + `PrecoTetoIT` |
| 6 | valores em mil / milhão / bilhão | `MoneyNormalizerTest` + IT da gravação |
| 7 | moeda estrangeira (USD × PTAX do fim do trimestre; câmbio informado) | `MoneyNormalizerTest` + IT |
| 8 | d > G | `CeilingPriceRulesTest` |
| 9 | d = G e d < G (terminal não calculado, mensagem) | `CeilingPriceRulesTest` |
| 10 | N ausente e N = 0 (não salva; padrão vazio com balanço em REVISAR) | `CeilingPriceRulesTest` + IT |
| 11 | MS = 0 (Ideal = Teto) e MS > 0 | `CeilingPriceRulesTest` |
| 12 | comparação com a PREÇO TETO V2 | `CeilingPriceRulesTest.planilhaV2Vale3` (a linha da planilha, os 16 valores a 1e-6) + seção 8.2 |

Mais: campo vazio recusado (400 com a lista de campos); origem `PADRAO` × `INFORMADO` decidida ao salvar; editar
devolve exatamente o gravado; ↻ não persiste; `PADRAO_MUDOU` quando sai ITR novo/Selic muda; dono (usuário B não lê
nem edita o de A); unit e fracionário; desdobramento depois do balanço (SBSP3); ativo sem emissor;
`confirmWarnings`. Catálogo: COTAHIST diário (arquivo real de 09/10/2026 recortado, dia já existente não duplica,
completa os dias que faltam); payout pela DFC (recortes da Vale e de um banco).

### 8.2 Conferência com a planilha (passo 11)

1. **Mesmas entradas:** a linha VALE3 da planilha (com os lucros projetados pelo Guilherme) entra no formulário e
   tem de dar Teto 82,66065787730874, Ideal 70,26155919571242, margens 22,22% e 3,89%, patrimônio
   352.311.364.973,54 — no teste e na tela.
2. **Padrões reais:** script `carga/scripts/conferencia_preco_teto.mjs` chama `/defaults` + `/calculate` para VALE3,
   PETR4, ITUB4, BBAS3, TAEE11 e WEGE3 e gera `docs/preco-teto/Conferencia_PrecoTeto_V2_<data>.xlsx` no **layout
   da planilha, com as fórmulas dela**; a coluna "PrimeVest" ao lado tem de bater ao abrir no Excel.

---

## 9. Decisões

### 9.1 Tomadas (10/10/2026)

| # | Decisão |
|---|---|
| D1 | **Só ações** (tipo `ACAO` ligado a emissor — inclui units e fracionários, que são ações na B3). FII, BDR e ETF não entram |
| D3 | Padrão de ROE e payout = média dos **3 últimos exercícios fechados** (DFP); com menos de 3, média dos que houver + alerta `MEDIA_INCOMPLETA` |
| D4 | Payout pelo **fluxo de caixa** (DFC: dividendos/JCP pagos ÷ lucro da controladora), no catálogo — vale para Emissoras e Preço Teto; fallback pelos proventos pagos quando a DFC não tiver a conta. Payout acima de 100% é real e fica como está, com alerta |
| D5 | Taxa de desconto = **Selic meta vigente**, só como pré-preenchimento do cálculo novo. Na edição volta a gravada; o ↻ (ou "Restaurar todos") busca a vigente |
| D6 | **Rotina diária do COTAHIST** da B3 para o catálogo inteiro + completar os dias desde 25/09. A BRAPI segue para a carteira |
| D7 | O preço teto digitado à mão **deixa de existir**: o teto é sempre o calculado, com as premissas editáveis |
| D9 | **Um cálculo por ativo**, retrato da última edição (sobrescrito ao salvar, com `calculated_at`); sem versões. Campos só mudam a pedido do usuário: ↻ por campo e "Restaurar todos os padrões", sem persistir |
| D10 | Sai `alert_enabled` — ver 9.3 |
| D11 | (sai) — com os 4 lucros sempre gravados no cálculo, não existe "substituição fora da janela" |
| D12 | Lucros da linha VALE3 da planilha: projeção do Guilherme (semestre duplicado de propósito). É o uso esperado: os padrões são sugestão, o lucro é premissa do usuário |
| D13 | A lista compara teto e ideal com três preços: o **do cálculo**, o **fechamento de hoje** e o **preço médio** da carteira (se o usuário tiver o ativo) |
| — | Entradas **todas gravadas e obrigatórias**; o formulário vem com o padrão; ao editar voltam as gravadas |
| — | Cálculo **individual**, por ativo escolhido pelo usuário — nada é calculado para o catálogo inteiro |

### 9.2 Abertas

Nenhuma. D2 e D8 tomadas em 10/10/2026 (abaixo, e linhas na 9.1).

| # | Decisão |
|---|---|
| D2 | **Limites de alerta** (calcula; ao salvar pede confirmação; nada é bloqueado nem alterado — o usuário manipula como quiser). Todos em `primevest.preco-teto.alertas.*` no `application.yml`, ajustáveis sem código: (1) ROE, payout, taxa de desconto e crescimento na perpetuidade **negativos ou acima de 100%**; (2) taxa de desconto **fora de 5–30%**; (3) crescimento na perpetuidade **acima de 6%**; (4) quantidade de ações **mais de 20%** longe do padrão; (5) lucro de um trimestre digitado **mais de 50%** longe do da CVM; (6) crescimento sustentável negativo ou ≥ taxa de desconto; (7) lucro TTM ≤ 0; (8) lucro TTM **abaixo de 3% do PL** ("lucro muito baixo"). **Trimestre com prejuízo** entra na soma normalmente e só aparece como informação. Bloqueiam de verdade (não há como calcular): margem de segurança negativa ou ≥ 100%, quantidade de ações ≤ 0, preço ≤ 0, desconto ≤ crescimento na perpetuidade (só o valor terminal em diante) e campo vazio |
| D8 | Lucro digitado em **BRL ou USD** (PTAX do fim do trimestre, editável) |

### 9.3 O "alerta" da D10

A tabela `ceiling_prices` de hoje tem a coluna `alert_enabled` ("alerta ligado"), e a API a recebe e devolve. A tela
manda sempre `true` e **não mostra** esse campo, e nenhuma rotina do sistema avisa nada quando o preço cruza o teto —
por isso o "que alerta?". Como a tabela é refeita, a coluna simplesmente não volta. Um aviso de verdade ("VALE3
abaixo do preço ideal") seria um escopo de notificações próprio, se você quiser no futuro.

---

## 10. Etapas

| Etapa | Entrega | Depende |
|---|---|---|
| **P1** | `CeilingPriceRules` + `MoneyNormalizer` + testes dos cenários 1–4, 6–12 (inclui a linha da planilha) | — |
| **P2** | Catálogo — **COTAHIST diário** (D6): cliente do arquivo diário, rotina, completar os dias que faltam; teste com o arquivo real | — |
| **P3** | Catálogo — **payout** (D4): métrica, migration de `company_quarters`, recarga do disco, `IndicatorRules`; conferir Vale, Petrobras, um banco; tela de Emissoras igual no resto | — |
| **P4** | V45/V46 (`ceiling_prices` refeita, `ceiling_price_quarters`), entidades e repositórios no core; `SchemaTest` | — |
| **P5** | Leitor comum de fundamentos extraído do `IndicatorService` (bateria do F9 igual) + `CeilingPriceDefaults` | P3 |
| **P6** | `CeilingPriceService` + controller + validações (seção 6) + ITs (cenários 2, 5, 6, 7, 10, dono, origem, ↻) | P1, P4, P5 |
| **P7** | Web: lista, página do ativo (formulário com ↻, resultado ao vivo, memória, alertas, legenda) | P6 |
| **P8** | Conferência: script + planilha `docs/preco-teto/Conferencia_PrecoTeto_V2_<data>.xlsx` | P6 |
| **P9** | Documentação: `docs/PRECO_TETO_METODOLOGIA.md` (fórmulas, convenção FC1 = TTM descontado 1 período, padrões e fontes, alertas, limitações); `Claude.md`, `docs/PENDENCIAS.md`, `README.md`, `docs/USE_CASES.md` (UC07), `docs/PRIMEVEST_DB.md`, andamento deste plano | P1–P8 |
| **P10** | Validação final: compilar, bateria completa (core, app-api, adm-api), lint/build do web, `docker compose up -d`, conferência no navegador com o Guilherme logado (VALE3 com os números da planilha, PETR4, TAEE11) | P1–P9 |

### Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| Decisões (todas tomadas em 10/10) | preparou | decidiu |
| P1–P7, P9 | implementa e testa | — |
| P2 primeira rodada (≈10 arquivos, ~5 MB) e P3 recarga do disco (~5 min) | executa (curtos) | — |
| P8 conferência | gera a planilha | **abre no Excel e confere** |
| P10 validação | compila, testa, sobe, confere no navegador | confere a tela logado |

---

## 11. Riscos e limitações (vão para a metodologia)

- **Modelo de lucro, não de caixa:** FC = lucro contábil, sem capex, dívida ou caixa — bancos e empresas de ciclo
  sofrem distorções; o próprio "Leia-me" da planilha registra isso.
- **Convenção da planilha mantida:** o TTM já realizado é descontado um período como se fosse o lucro do próximo ano
  (observação do pedido); o terminal começa no ano 3.
- **Sensibilidade a d − G:** com Selic 13,75% e G 3% o terminal pesa ~75% do valor (VALE3: 266 de 352 bi). Uma queda
  da Selic mexe muito no teto — a tela mostra o peso do terminal.
- **Um teto por companhia:** lucro e ações são da companhia inteira, então ON e PN têm o mesmo Preço Teto (D7 do plano
  de balanços: mesmo LPA para todas as classes); a unit multiplica pela composição.
- **Qualidade da fonte:** payout da DVA (D4), quantidades em REVISAR (107 documentos; 13 companhias sem quantidade no
  último balanço), reapresentações de trimestre.

---

- **O cálculo congela as premissas do usuário:** é o que se quer (o teto é uma tese pessoal), mas a Selic, o lucro e
  o preço andam; o aviso "padrão mudou" e a coluna "fechamento de hoje" existem para o usuário revisar.

---

## 12. Revisão de 10/10/2026 — respostas do Guilherme ao plano

| Ponto | Resposta | Efeito no plano |
|---|---|---|
| Premissas "nulo = padrão" | o campo vem com o padrão carregado e **não pode ser salvo vazio** | todas as entradas NOT NULL; origem por campo (4.1) |
| "Calculado na leitura" | o cálculo é **individual**, das ações que o usuário quiser; ao editar carregam os dados salvos; ↻ ao lado de cada campo restaura o padrão do momento no input, salvo só ao persistir | seções 0, 4.4, 5.2, 7.2; D11 sai |
| Payout zerado × Investidor10 | estranhou | investigado: seção 1.5; D4 refeita |
| Preço parado | o que fazer para atualizar? | COTAHIST diário (4.5, D6, P2) |
| Planilha × dados reais | lucros projetados de propósito; o uso é empírico e personalizado | D12 fechada; 1.4 e 8.2 ajustadas |
| D3 | sim | tomada |
| D4 | sim, se for assim que se chega aos valores | refeita com a comparação (1.5) |
| D6 | não entendeu o fechamento | explicado e trocado pelo COTAHIST diário |
| D7 | sim | tomada |
| D1 | só ações | tomada |
| D2 | anomalias: acima de 100% e negativos? | proposta montada a partir disso (9.2) |
| D4, D6 | seguir recomendação (D6: situação temporária) | tomadas |
| D5 | Selic vigente, só pré-preenchimento do cálculo novo; na edição, busca de novo a vigente a pedido | tomada |
| D9 | o salvo é o retrato da edição; campos atualizados a pedido; botão para restaurar os padrões sem persistir | tomada; botão "Restaurar todos os padrões" (7.2) |
| D10 | que alerta? | explicado (9.3); coluna sai |
| D2 | trimestre com prejuízo pode ser usado (o usuário manipula como quiser); os limites mais apertados ficam como alerta | tomada (9.2) |
| D8 | ok | tomada |
| D13 | sim, e também o preço médio, se tiver em carteira | tomada; três colunas de comparação (7.1) |

---

## 13. Andamento

| Etapa | Estado |
|---|---|
| Planejamento | **10/10/2026** — plano criado e revisado com as respostas; todas as decisões tomadas; execução autorizada (todas as etapas, sem pausa) |
| P1 | **Feito.** `CeilingPriceRules` (fórmulas puras, `DECIMAL128`, sem limitar nada; avisos BLOQUEIO/ATENCAO/INFORMACAO com campo e mensagem) e `MoneyNormalizer` (unidade × câmbio). `CeilingPriceRulesTest` (14) e `MoneyNormalizerTest` (2): cenários 1–4 e 6–12; a linha VALE3 da planilha bate nos 15 valores a 1e-12 |
| P2 | **Feito.** `B3HistoricalQuotesClient.day` (`COTAHIST_DddMMaaaa.ZIP`, cache em `carga/data/b3/cotahist/diario`), `HistoricalQuotesImportService.importRecent/importDays` (mesmo leitor do zip anual; fim de semana não vai à B3; 404 fica para depois; lacuna > 31 dias usa o zip do ano), `B3DailyQuotesScheduler` (21:30 e 08:30 + recuperação na subida), `POST /admin/b3/cotahist/diario`. Testes: 2 novos em `HistoricalQuotesImportIT`. **Base real:** a recuperação na subida baixou 26/09–09/10 (10 pregões, 9.909 cotações) — VALE3 passou a ter o fechamento de 09/10 (R$ 67,90) |
| P3 | **Feito.** Métrica `DIVIDENDOS_PAGOS` (DFC 6.03.*, por descrição: dividendo/JCP pagos, sem recebidos nem não controladores; conta-mãe e filha não somam duas vezes), `CvmStatements` passa a ler 6.03, `Flow.DIVIDENDS_PAID`, **V45** (`dividends_paid_q/_ttm`), `IndicatorRules.PAYOUT` pela DFC (tela de Emissoras igual), demonstrativos com a linha nova. **Recarga** (`cvm/import?force=true`, 395 s, sem download de anos fechados): 19.351 documentos; no DFP 2025, 357 de 357 companhias com o valor (120 com zero = não pagaram). Vale 2025: **144,57%** (Investidor10 146,46%), 2024 65,41%, 2023 70,02%, atual 222,50% (I10 240,28%). Petrobras 41,05% (DVA 37,44%), Itaú 107,67%, BB 48,77%, Taesa 64,59%, WEG 84,45%. **Fallback por proventos não foi necessário:** com a DFC presente, sem conta de dividendo = 0 (não pagou); sem a DFC, nulo com motivo (PENDENCIAS 25.7) |
| P4 | **Feito.** **V46**: `ceiling_prices` recriada (trava se tiver linha; estava vazia) e `ceiling_price_quarters`; `currency` ficou `VARCHAR(3)` (CHAR quebrava a validação do Hibernate). Entidades `CeilingPrice` (quatro lucros em `@OneToMany` ordenado) e `CeilingPriceQuarter` (`@IdClass`). `SchemaTest` na V46 |
| P5 | **Feito.** Em vez de extrair uma classe, o `IndicatorService` ganhou `companyFundamentals(ticker)` — a mesma leitura da tela de Emissoras (quantidade ajustada por eventos, balanço em revisão, preço atual **com a data**, ROE e payout anuais) exposta para o Preço Teto; `CeilingPriceDefaults` monta os padrões (janela de 4 trimestres consecutivos, médias de 3 exercícios, Selic meta, PTAX, 3%/15% em `primevest.preco-teto.*`) |
| P6 | **Feito.** `CeilingPriceService` + `CeilingPriceController` (seção 5.3) + DTOs; alertas do contexto (N e lucro longe do padrão, lucro baixo × PL, reapresentação, balanço/preço defasados, média incompleta, janela desatualizada); origem PADRAO/INFORMADO decidida ao salvar; `changedDefaults`; 422 com os avisos (`Rejection`), 409 duplicado. Limites em `primevest.preco-teto.alertas.*`. `PrecoTetoIT` (6). `TelaEmissorasIT` passou a gravar o cálculo novo direto |
| P7 | **Feito.** `CeilingPricesPage` (lista: três comparações com margem do teto e do ideal, situação, alertas, "padrão mudou", busca "Novo cálculo") e `CeilingPriceDetailPage` (`/ceiling-prices/:ticker`): lucros com sinal, unidade, moeda e câmbio, fonte obrigatória quando difere da CVM, ↻ por campo, "Atualizar trimestres", "Restaurar todos os padrões", resultado ao vivo, alertas, memória de cálculo, datas, legenda; modal de confirmação dos alertas; aviso ao sair sem salvar. `SignedNumberInput` (máscara do projeto + botão de sinal), `MarginPair`, `ceilingFormat`. `IssuerSearch` aceita `placeholder`. Sem botão na página da emissora (decidido) |
| P8 | **Feito.** `carga/scripts/conferencia_preco_teto.mjs` (padrões + `/calculate` de VALE3, PETR4, ITUB4, BBAS3, TAEE11 e WEGE3, e a linha VALE3 da planilha) e `carga/scripts/conferencia_preco_teto_xlsx.py` (layout e fórmulas da V2, colunas do PrimeVest e a diferença): `docs/preco-teto/conferencia_preco_teto_2026-10-10.xlsx`. Recalculada no LibreOffice: **todas as 7 linhas CONFERE = SIM** (diferença só do arredondamento a 4 casas: < R$ 0,00005). Tetos com os padrões de 10/10: VALE3 22,89; PETR4 80,23; ITUB4 42,46; BBAS3 21,31; TAEE11 47,40; WEGE3 16,51; planilha VALE3 82,66 |
| P9 | **Feito.** `docs/PRECO_TETO_METODOLOGIA.md` (novo); `Claude.md`, `docs/PENDENCIAS.md` (seção 25, 18.11 c), `README.md`, `docs/USE_CASES.md` (UC07, UC41), `docs/PRIMEVEST_DB.md`, `docs/SCHEDULE_TASKS.md` ([A11]), `docs/ADM_TASKS.md` ([07b]), `docs/TESTES_FUNCIONAIS.md` |
| P10 | **Feito.** `mvn verify` completo verde (core 158; integration 103; adm-api 89 + 40; app-api 189 + 199), depois a rodada das classes tocadas pelos últimos ajustes (`CeilingPriceRulesTest`, `MoneyNormalizerTest`, `PrecoTetoIT`, `TelaEmissorasIT`) verde; web: `tsc --noEmit` e `eslint .` sem erro nem aviso. Ambiente: V45 e V46 aplicadas no banco de desenvolvimento; cotações recuperadas; balanços reprocessados. Navegador (Guilherme logado): VALE3 abre com os padrões (lucros da CVM com o prejuízo do 4T25, ROE 14,69%, payout 93,33%, Selic 13,75%, N 4.072.366.000, R$ 67,90 de 09/10) → teto R$ 22,89 e ideal R$ 19,46; salvar gravou com origem "Padrão" em tudo; a lista mostrou as comparações; lucro do 2T26 alterado sem fonte → bloqueio; com fonte → modal de alertas ("900% diferente da CVM") com "Salvar mesmo assim". O cálculo de teste foi apagado (a lista do Guilherme está vazia) |
| Revisão pós-uso (11/10/2026) | Pedidos do Guilherme depois de testar: **fonte do lucro trimestral removida** do back e do front (V47 apaga `ceiling_price_quarters.source_ref`; sai `NOTA_OBRIGATORIA`, `QuarterInput.note`, `QuarterView.sourceRef`); tela do ativo — ele mesmo reordenou os cartões (premissas antes dos lucros), tirou textos e o cartão de memória de cálculo (os passos seguem na API, `result.steps`) e pôs observações num cartão à parte; o Claude: LL TTM em valor cheio, ícone no Salvar, padrão de cada premissa no (i) ao lado do rótulo, sem a referência do documento da CVM e sem a borda em cada trimestre, **G só leitura** com o mesmo visual do g e por último no cartão |
| Revisão pós-uso 2 (11/10/2026) | Trimestres sem a linha "= R$ … · CVM: R$ …"; (i) no Preço Ideal (tela do cálculo e cabeçalho da lista): "Preço Teto com a margem de segurança aplicada: Preço Teto × (1 − margem de segurança). É o preço de compra sugerido, com uma folga para erros nas premissas do cálculo." (`IDEAL_PRICE_INFO`); ícone ↻ da legenda com a cor e a borda do "Informado"; botão "+" dos valores em verde (o "−" segue vermelho) |
| Revisão pós-uso 3 (11/10/2026) | Campo "Novo cálculo" da lista ganhou um botão "+" no fim (tons do acento do tema, como o "Informado"; prop `addLabel` do `IssuerSearch`). O "+" faz o mesmo que o Enter: abre o cálculo da ação destacada ou digitada; com o campo vazio, só foca o campo |
| Revisão pós-uso 4 (11/10/2026) | "+" do "Novo cálculo" com o dourado sólido dos botões primários, colado na borda do campo; lista mostra "MS 15,0%" sob o Preço Ideal (`safetyMarginPct` no `ListItem`); legenda abaixo da tabela: MS = margem de segurança usada no cálculo; T e I = margem do preço da coluna em relação ao teto e ao ideal |
| Revisão pós-uso 5 (11/10/2026) | Lista: sem a data ao lado do Preço atual; MS no formato "MS: 15,0%" e margens como "T: +8,9% · I: −7,4%" (dois pontos depois da sigla; o sinal é da margem) |
| Revisão pós-uso 6 (11/10/2026) | Tela do cálculo: margens do Teto e do Ideal dizem contra qual preço foram medidas — "margem −13,50% em relação ao preço da ação (R$ 24,64)", com o preço do formulário |
