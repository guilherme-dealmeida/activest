# Activest — Plano de Cadastro de Moedas (moeda no ativo e na transação, câmbio e tributos do exterior)

> Fase: **Concluído** (26/10/2026) — escopo finalizado pelo Guilherme; o commit e o PR ficam com ele (etapa 7). Pendência
> que sobra: bases legais do seed de tributos do exterior (PENDENCIAS 38.4).
> Origem: ESCOPO "Cadastro de moedas Activest" (26/10/2026). Texto-base: *"mesmo começando só com o dólar, modelar a
> moeda como cadastro (uma tabela de moedas, com a moeda no ativo e na transação), e não como 'BRL ou USD' fixo no
> código. A segunda moeda passaria a ser só uma linha nova mais a série de câmbio dela. O BCB publica a PTAX de várias
> moedas pela mesma fonte que já é usada (SGS e PTAX)."*
> Absorve: `docs/PENDENCIAS.md` **37.1** (outras moedas, D16 de Metas) e **3.8** (IR retido de Stock/REIT/ETF). É
> pré-requisito dos escopos "ETFs americanos" e "Internacionais".

---

## 1. Objetivo

Criar a **estrutura de moedas** do Activest: cadastro de moedas (BRL base, USD a primeira estrangeira), **série de
câmbio** por moeda, **moeda no ativo, na transação e no provento**, e as **regras de tributo do exterior por data**
(IOF de câmbio, IR sobre aplicações no exterior, retenção no país de origem), para que o **líquido** dos ativos
internacionais já saia correto. Moeda nova = uma linha no cadastro + a série de câmbio dela, sem código novo.

**Princípio (Guilherme, 26/10):** mesmo o que for *consumido* no escopo Internacionais (fonte de preço, saldo em dólar na
corretora, stablecoins) tem o **insumo** entregue por este escopo — a estrutura já nasce pronta para ser usada.

**Carteira em reais até segunda ordem** (26/10): toda soma de carteira, Rentabilidade, Metas e Dashboard continua em
BRL; metas em dólar e "ver carteira em dólar" ficam para depois.

## 2. Descrição consolidada (o que os planos já pediam)

| # | Onde nasceu | O que existe hoje | O que o escopo de moedas resolve |
|---|---|---|---|
| O1 | **Metas** — D16, PENDENCIAS 37.1 | Tudo em reais; dimensão região/moeda fora de ALO-1 e RIS-1; V53 proposta e não executada | Moeda + região disponíveis para as metas (o uso nas metas é escopo próprio) |
| O2 | **Preço Teto** — D8, `ceiling_price_quarters` | `CHECK (currency IN ('BRL','USD'))`, `fx_rate`, `value_brl`, `MoneyNormalizer` | Passa a apontar para o cadastro e a ler o câmbio da série nova (**aprovado 26/10**) |
| O3 | **Índices** — `USD_PTAX` (SGS 1) | PTAX venda como índice, desde 2010, card na tela Índices | Série de câmbio por moeda (compra e venda); o índice continua (**D6 aprovada**) |
| O4 | **Tipos de ativo** — `STOCK`, `REIT`, `ETF_INTERNACIONAL` | Preço `MANUAL`, sem moeda | Moeda no ativo; preço/histórico na moeda do ativo (**aprovado 26/10**) |
| O5 | **Rentabilidade** — item 6 do levantamento; PENDENCIAS 29.3, 29.7 | "Não há moeda no sistema" | Conversão da posição em moeda estrangeira para reais por data |
| O6 | **Cripto/Binance** — PENDENCIAS 15; R6, D3 | Só entra operação em reais; USDT/USDC são cripto | Insumo: moeda de referência da stablecoin e conversão "valor em reais no instante T" (seção 6.4) |
| O7 | **Integração** — `CryptoImportRules`, `binance.quote-assets`, divergência G6 da B3 | Moedas fiduciárias por texto fixo (`"BRL"`, lista `BRL/USD/EUR`) | Reconhecer moeda pelo cadastro (seção 6.6) |
| O8 | **Proventos / IR** — PENDENCIAS 3.8 | Stock/REIT/ETF sem regra de retenção ("líquido nulo; somas usam o bruto") | Provento em moeda estrangeira com câmbio e **líquido** pelas regras do exterior (seção 5) |
| O9 | **ETFs americanos / Internacionais** | ETF americano comprado pela Binance; recadastro da Binance (37.5) | Ativo, transação, provento, câmbio e tributos prontos para uso |
| O10 | **Front** — `CurrencyInput`, `formatCurrency` (22 arquivos) | "R$" fixo | Ativo estrangeiro exibido e lançado na moeda dele (fixa, vinda do ativo); nacional sem mudança |
| O11 | **Saldo em moeda estrangeira** (novo, 26/10) | Não existe: o Activest não guarda caixa | Insumo: operação de câmbio (remessa/retorno) registrada, de onde o saldo e o custo do dólar podem ser derivados (seção 6.3) |

## 3. Fora do escopo

- Metas em outra moeda e exibição da carteira em outra moeda (26/10: "carteira em real até segunda ordem").
- **Consumo** pelos Internacionais: escolher a fonte de preço, cadastrar os ativos internacionais, importar operações
  em USD da Binance. Este escopo entrega a estrutura que eles usam.
- Declaração anual (DAA) e geração de DARF: o Activest estima o líquido, não apura o imposto oficial.

> Revogado em 26/10/2026: "IOF de câmbio, IR no exterior e retenção fora do escopo". O Guilherme discordou — os ativos
> internacionais devem ter o líquido afetado por esses tributos, na mesma metodologia da tabela de taxa por data. A
> regra "taxas e impostos fora" continua valendo para corretagem e custo de aquisição na B3 e em cripto.

## 4. Discovery — estado atual no código e no banco

| Peça | Situação (26/10/2026) |
|---|---|
| `asset_types` | `price_source`, `fixed_income`; **sem moeda, sem região**. Tipos 8 `STOCK`, 9 `REIT`, 10 `ETF_INTERNACIONAL` em `MANUAL` |
| `assets`, `asset_price_history` | preços sem moeda (implicitamente BRL) |
| `transactions` | `price_per_share`, `total_amount`, `brokerage_fee`, `net_amount`, `income_tax`, `iof_tax`, `settlement_adjustment` — BRL implícito, sem câmbio |
| `dividends`, `asset_cash_events` | valores BRL implícitos; `withholding_rate`/`withholding_tax`/`net_*` ligados a `withholding_tax_rules` |
| **Tabelas de taxa por data** (a metodologia pedida) | `withholding_tax_rules` (provento: tipo, tipo de ativo, `rate`, `base_date` CREDIT/PAYMENT, `valid_from`/`valid_to`, `legal_basis`; ex.: JCP 15% até 31/12/2025 e 17,5% desde 01/01/2026) e `fixed_income_tax_rules` (IR/IOF da RF por faixa de dias, vigência e base legal) |
| `market_indices` / `market_index_values` | `USD_PTAX` (SGS 1, venda, diária, desde 2010); tarefa A9 (10:00), arquivos em `carga/data` + bucket |
| `ceiling_price_quarters` | única tabela com moeda (`CHECK BRL/USD`) |
| Integração | `ImportedMovement.quoteAsset` (texto: `BRL`, `USDT`, `BTC`); `CryptoImportRules` compara com a constante `"BRL"`; `binance.quote-assets: BRL,USDT,USDC,FDUSD,BTC,ETH,BNB` |
| Tela de administrador | Já existe o padrão: **Tarefas** (`/tasks`, `AdminOnly`, item `adminOnly` no menu, `users.is_admin`) |
| Cálculos que somam dinheiro | Carteira, Rentabilidade, Metas, Dashboard, Alocação, Estratégia, Rebalanceamento, Dividendos, venda de RF/TD, Preço Teto — todos assumem reais |

### 4.1 Tributos do exterior — levantamento (a conferir com a base legal no Impact)

| Tributo | Quando incide | O que o levantamento achou |
|---|---|---|
| **IOF de câmbio** | Na conversão BRL → moeda estrangeira (remessa) e na volta | Remessa para **investimento no exterior: 1,1%**. Transferência para **conta própria/disponibilidade: 1,1% até 22/05/2025 e 3,5% desde 23/05/2025** (Decretos 12.466/12.467, mantido pelo 12.499 de 11/06/2025). Ingresso sem finalidade específica: 0,38% |
| **IR sobre aplicações no exterior** (Lei 14.754/2023) | Ganho realizado (venda, resgate) e rendimentos (proventos), **desde 01/01/2024** | **15%** na Declaração de Ajuste Anual, separado dos demais rendimentos; **variação cambial integra o ganho**; perdas compensam ganhos do mesmo ano (e saldo vai para anos seguintes); imposto pago no exterior deduzível no mesmo ano. Antes de 2024: regime anterior (ganho de capital progressivo até 22,5%; proventos pelo carnê-leão) |
| **Retenção no país de origem** | Provento pago pelo ativo | EUA: retenção sobre dividendos de não residente (a alíquota e o efeito do W-8BEN a confirmar no Impact); sem retenção sobre ganho de capital |

Como as alíquotas mudam por decreto e por lei, elas entram em **tabela de taxa por data** (`valid_from`/`valid_to` +
`legal_basis`), como `withholding_tax_rules` e `fixed_income_tax_rules` — nunca fixas no código.

## 5. Tributos e líquido dos ativos internacionais (seção nova, 26/10)

Desenho preliminar para o Impact/Planning:

- **Tabela de regras do exterior por data** (nome provisório `foreign_tax_rules`): tributo (`IOF_FX`, `IR_FOREIGN`,
  `WITHHOLDING_FOREIGN`), operação/evento (remessa para investimento, conta própria, retorno, provento, venda), país ou
  moeda, tipo de ativo (opcional), `rate`, `valid_from`/`valid_to`, `legal_basis`. Seed por migration.
- **Provento em moeda estrangeira:** valor por cota na moeda do ativo + câmbio da data de pagamento → bruto em reais;
  retenção do país de origem (regra por data) → líquido; IR brasileiro de 15% com dedução do retido (estimativa,
  marcada como tal). Usa as colunas `withholding_*`/`net_*` que já existem, mais moeda e câmbio.
- **Venda:** IR estimado de 15% sobre o ganho **em reais** (custo pelo câmbio da compra, venda pelo câmbio da venda —
  a variação cambial entra), como a venda de RF já mostra bruto, IR e líquido.
- **IOF de câmbio:** pertence à **operação de câmbio** (remessa), não à compra do ativo — ver 6.3.

## 6. Respostas de 26/10 e análises pedidas

### 6.1 Decisões registradas (26/10/2026)

| # | Decisão |
|---|---|
| D1 | ✅ Moeda referenciada pelo **código ISO** (`currency_code CHAR(3)`, FK para `currencies(code)`) |
| D2 | ✅ Moeda no ativo (primária); secundária = moeda base do sistema, não gravada no ativo (análise 6.2, D15 aprovada 26/10) |
| D3 | ✅ Transação grava os valores na moeda negociada + câmbio **e** os valores em reais; complementa a D2 |
| D4 | ✅ Região entra junto (BRASIL / EXTERIOR / CRIPTO) |
| D5 | ✅ PTAX venda como padrão **só para ativos em moeda estrangeira**; ativo em BRL não mostra câmbio |
| D6 | ✅ Índice `USD_PTAX` mantido ao lado da série de câmbio nova |
| D7 | ⏳ Fonte do câmbio: conferida pela documentação no Impact (9.3) — recomendação API PTAX, a confirmar no Planning |
| D8 | ✅ Seed só BRL e USD |
| D9 | ✅ Preço Teto passa a usar o cadastro |
| D10 | ❌ "Só a estrutura no provento" recusada: provento em moeda estrangeira entra **completo**, com IOF/IR do exterior (seção 5) |
| D11 | ✅ Histórico de preço na moeda do ativo |
| D12 | ✅ Integração: só trocar os textos fixos pelo cadastro (26/10) |
| D13 | ✅ Cadastro de moedas **com tela para o administrador** (6.5) |
| D14 | ✅ Metas em dólar e carteira em dólar depois; carteira em real até segunda ordem |

### 6.2 Análise da D2 — moeda primária e secundária

O cenário descrito: a **primária** é a moeda do ativo; a **secundária** é BRL quando a primária não é BRL; ativo em BRL
não tem câmbio para o usuário escolher.

Análise: a secundária não é um atributo do ativo — ela é sempre a **moeda base do sistema** (BRL hoje, "carteira em
real até segunda ordem"). Gravá-la em cada ativo repetiria a mesma informação em todas as linhas, e no dia em que
existir "carteira em dólar" a troca seria por usuário, não por ativo. Por isso a recomendação é:

| Onde | O que guarda |
|---|---|
| `currencies` | `is_base` (uma única linha: BRL) |
| `assets.currency_code` | a **primária** (moeda em que o ativo é cotado). Default `BRL` (REGRAS 8.1) |
| `asset_types.default_currency_code` + `region` | sugestão para o cadastro do ativo e para as metas (D4) |
| transação / provento | moeda = a do ativo; `fx_rate` para a base; colunas em reais gravadas (D3). Em ativo BRL, `fx_rate = 1` e a tela não mostra o campo |

Casos de borda que a análise considerou: **BDR** e **IVVB11** são cotados em reais na B3 — primária BRL, região
EXTERIOR (exposição ≠ moeda, por isso a D4 é separada). **ETF americano comprado com USDT na Binance**: o ativo é USD;
o pagamento em stablecoin é conversão da integração (6.4), não uma terceira moeda no ativo.

### 6.3 Saldo em moeda estrangeira (insumo)

O IOF de câmbio e o "custo do dólar" nascem na **operação de câmbio** do usuário (remessa BRL → USD para a corretora,
retorno USD → BRL), não na compra do ativo. Proposta de insumo: registro de **operação de câmbio** por usuário (data,
moeda de origem e destino, valor enviado e recebido, câmbio efetivo, IOF calculado pela regra por data e finalidade —
investimento 1,1% / conta própria 3,5% —, tarifa/spread, corretora/instituição). Dele saem:

- o saldo em dólar na corretora (remessas − compras + vendas + proventos líquidos − retornos), a ser exibido pelos
  Internacionais;
- o câmbio padrão sugerido para a compra do ativo (D16: PTAX do dia × câmbio médio das remessas).

### 6.4 Stablecoins (insumo)

Continuam cripto. Insumo proposto: `assets.reference_currency_code` (USDT, USDC → USD) para a conversão poder valorar
pares contra stablecoin (PENDENCIAS 15.2) em reais e para a moeda de exposição nas metas. Não muda o preço nem a
posição delas (D17).

### 6.5 Tela de administrador (D13)

No padrão da tela Tarefas (só `is_admin`, item no fim do menu): lista de moedas (código, nome, símbolo, casas, base,
ativa, fonte e código da série), incluir/editar/ativar moeda, e por moeda a série de câmbio (último valor, cobertura,
gráfico no desenho dos Índices). A carga do câmbio vira **tipo de tarefa** na tela Tarefas (rodada diária +
recuperação + disparo manual), como os índices. A tabela de regras do exterior (seção 5) também pode ter consulta na
tela (D18).

### 6.6 Integração (D12 — explicação)

Hoje o código decide "isto é dinheiro, não ativo" por textos fixos: `CryptoImportRules` compara `quoteAsset` com a
constante `"BRL"`; a divergência da B3 ignora a lista `BRL/USD/EUR`; o `application.yml` da Binance lista `BRL` entre as
moedas de cotação. A proposta é só trocar esses textos fixos por uma consulta ao cadastro ("é moeda fiduciária
cadastrada?"). **Para o usuário nada muda**: a importação continua aceitando só operações em reais. Importar operações
em USD (ex.: o ETF americano da Binance) fica para o escopo dos ETFs americanos, que usa a estrutura daqui.

## 7. Requisitos (rascunho para o Impact)

| # | Requisito |
|---|---|
| R1 | `currencies`: código ISO, nome, símbolo, casas, `is_base`, ativa, fonte/código da série. Seed BRL + USD (D8) |
| R2 | Série de câmbio por moeda e data (compra e venda PTAX), desde 2010; tarefa diária na adm-api + recuperação + `carga/data` + bucket |
| R3 | `assets.currency_code` (default BRL) e `reference_currency_code`; `asset_types.default_currency_code` e `region` |
| R4 | Transação: moeda, `fx_rate` e valores originais + em reais (D3); PTAX venda padrão só em moeda estrangeira (D5) |
| R5 | Provento: moeda, câmbio, retenção do exterior e IR estimado pela tabela por data (seção 5) |
| R6 | Venda de ativo internacional: bruto, IR estimado 15% sobre o ganho em reais (com variação cambial), líquido |
| R7 | Tabela de regras de tributo do exterior por data (`valid_from`/`valid_to`/`legal_basis`), seed por migration |
| R8 | Operação de câmbio do usuário com IOF por finalidade e data (insumo de saldo e custo do dólar) |
| R9 | Serviço único "valor em reais na data D" (sem câmbio na data → último dia útil anterior, sinalizado; nunca zero em silêncio) |
| R10 | Preço Teto lendo o cadastro e a série (D9); listas fixas da integração trocadas pelo cadastro (se D12) |
| R11 | Tela de administrador de moedas e tipo de tarefa da carga de câmbio (D13) |
| R12 | Front: sem escolha de moeda pelo usuário. Ativo nacional igual a hoje (R$, sem câmbio); ativo estrangeiro exibido e lançado na moeda dele (fixa) com o câmbio para reais |
| R13 | Campos `NOT NULL` novos com default BRL / câmbio 1 (REGRAS 8.1); nomes em inglês (REGRAS 9) |

## 8. Decisões da segunda rodada — ✅ todas aprovadas pela recomendação (26/10/2026, "concordo com todas as recomendações")

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D2/D15** | Moeda primária × secundária | (a) secundária = moeda base do sistema (`currencies.is_base`), não gravada no ativo; (b) secundária gravada em cada ativo | **(a)** — análise 6.2 |
| **D7** | Fonte do câmbio | API PTAX (Olinda) × SGS | Conferir no Impact (histórico desde 2010, limite por chamada, compra e venda) |
| **D12** | Integração | (a) só trocar os textos fixos pelo cadastro (nada muda para o usuário); (b) também importar operações em USD agora | **(a)** — 6.6 |
| **D16** | Câmbio padrão da compra de ativo internacional | (a) PTAX venda do dia (D5); (b) câmbio médio das operações de câmbio do usuário (6.3), PTAX quando não houver | **(a)** como padrão, editável; (b) como sugestão quando houver remessa registrada |
| **D17** | Moeda de referência da stablecoin (6.4) | (a) entra agora; (b) com o escopo de cripto/Internacionais | **(a)** — é uma coluna, e cumpre o princípio do insumo |
| **D18** | Regras do exterior na tela de administrador | (a) só consulta; (b) edição; (c) só seed | **(c)** como as tabelas de taxa atuais (mudança de alíquota = migration com a base legal), consulta na tela se quiser |
| **D19** | Operação de câmbio (6.3) entra neste escopo | (a) tabela + cadastro pela tela + IOF; (b) só a tabela e as regras, tela nos Internacionais | **(b)** — insumo pronto e testado; a tela de saldo nasce junto com os ativos que o consomem |
| **D20** | IR de 15% (Lei 14.754) no líquido | (a) estimado por operação (venda/provento), sem compensar perdas; (b) estimado com compensação de perdas no ano | **(a)** com aviso de que a compensação anual pode reduzir o imposto; (b) quando houver apuração anual |
| **D21** | Operações antes de 2024 (regime anterior) | (a) regras por data também para o regime anterior; (b) só a partir de 2024 | **(a)** — a tabela por data existe para isso; a base legal do regime anterior entra no Impact |

## 9. Impact — análise de impacto (26/10/2026)

### 9.1 Princípio que reduz o impacto

Pela D3, as colunas que **já existem** em `transactions` e `dividends` continuam **em reais** (o que todos os cálculos
leem hoje). O valor original e o câmbio entram em **colunas novas**. Com isso Carteira, Rentabilidade, Metas, Dashboard,
Alocação, Estratégia, Rebalanceamento e venda de RF/TD **não mudam** na leitura de transações e proventos. O ponto que
muda é o **preço** (D11: `assets.current_price` e `asset_price_history` na moeda do ativo): todo lugar que valoriza
posição pelo preço passa a converter para reais por uma peça única (R9).

### 9.2 Componentes afetados

| Camada | Componente | Efeito | Risco |
|---|---|---|---|
| Banco | **V9** (nova migration) | `currencies` (seed BRL base + USD), `exchange_rates` (moeda, data, compra, venda, boletim, fonte; PK moeda+data), `foreign_tax_rules` (seed com base legal), `fx_operations` (D19: só tabela); colunas novas com default (REGRAS 8.1): `asset_types.default_currency_code`/`region`, `assets.currency_code`/`reference_currency_code`, `transactions.currency_code`/`fx_rate`/`original_price_per_share`/`original_total_amount`/`original_fee`, `dividends.currency_code`/`fx_rate`/`original_amount_per_share`; `ceiling_price_quarters.currency` troca o `CHECK` pela FK (D9). Seed: tipos 8/9/10 → USD/EXTERIOR, 7 (BDR) → BRL/EXTERIOR, 11 → CRIPTO; USDT/USDC → `reference_currency_code = USD` (D17) | Baixo: só acréscimos com default; nenhuma linha muda de significado (catálogo hoje 100% BRL) |
| core | entidades `Currency`, `ExchangeRate`, `ForeignTaxRule`, `FxOperation`; campos novos em `Asset`, `AssetType`, `Transaction`, `Dividend` | Hibernate valida o schema na subida | Médio: `VARCHAR(3)` × `CHAR(3)` já quebrou a validação no Preço Teto (V46) — usar `VARCHAR(3)` |
| core | **`ExchangeRateService`** (R9): "valor em reais na data D" — moeda base → 1; outra moeda → PTAX venda do dia ou do último dia útil anterior, devolvendo a data efetiva usada (lacuna sinalizada) | Peça única usada por app-api e adm-api | Feriados BR × pregões dos EUA: dia de pregão lá sem PTAX aqui usa o anterior, marcado |
| adm-api | `PtaxClient` (API PTAX/Olinda) + `ExchangeRateImportService` + **tipo de tarefa `EXCHANGE_RATES`** (V9 em `task_types`, horário próprio ou junto do `MARKET_INDICES` 10:00), recuperação na subida, arquivos `carga/data/bcb/ptax/{moeda}/{ano}.json` → bucket, entrada no `BOOTSTRAP` | Mesmo desenho de `MarketIndexImportService`/`BcbSgsClient` | **API PTAX não pôde ser chamada desta sessão** (proxy da política, 403 — REGRAS 5.6): formato e limite por chamada conferidos na Implementation pela própria adm-api na máquina do Guilherme |
| app-api | Valorização pelo preço: `PositionValuationInfo`, `PortfolioService`, `AppreciationUpdater`, `AssetService`, `PortfolioTimelineBuilder`, `BenchmarkData`, `AssetPriceHistoryService` | Preço × câmbio da data via `ExchangeRateService` quando o ativo não é BRL; ativo BRL = caminho de hoje (multiplica por 1) | Médio: Rentabilidade e Metas leem a série por dia — conversão em lote por período (cache por moeda) para não fazer uma consulta por dia |
| app-api | `TransactionService` (+ `PositionCalculator`) | Ativo estrangeiro: recebe preço/total/taxa na moeda + câmbio (padrão PTAX venda, D5/D16), grava original **e** reais; ativo BRL: recusa câmbio ≠ 1 (D5) | Edição de câmbio regrava as colunas em reais na mesma gravação |
| app-api | Venda de ativo estrangeiro | Reaproveita `net_amount`/`income_tax` e o `CHECK ck_transactions_sale_taxes` (hoje usados na RF): IR estimado 15% sobre o ganho em reais (D20), regra por data (D21) | Precisa de custo médio em reais (já existe: `PositionCalculator` trabalha em reais) |
| app-api | `DividendService`, `DividendRecalculator`, `DailyOpeningService` | Provento de ativo estrangeiro: bruto em moeda × câmbio da data de pagamento; retenção do exterior e IR BR estimado pela `foreign_tax_rules` | `withholding_tax_rules` é por tipo de ativo e só regra BR: decidir no Planning se a regra do exterior entra nela (coluna de jurisdição) ou fica só em `foreign_tax_rules` (P1) |
| app-api | `CeilingPriceDefaults` (lê `USD_PTAX` de `market_index_values`) e `MoneyNormalizer` | Passa a ler `ExchangeRateService` (D9) | Baixo; mesmo número (PTAX venda) — conferir por teste que SGS 1 = PTAX venda |
| app-api | `GoalFactsLoader` / motor das metas | Lê reais como hoje; ganha acesso a região/moeda (insumo, sem uso agora — D14) | Baixo |
| integration | `CryptoImportRules` (`"BRL"` fixo), `ImportPipeline` (`fiatDeposited`), divergência G6 (`BRL/USD/EUR`), `binance.quote-assets` | Pergunta ao cadastro "é moeda?" (D12); comportamento igual | Baixo; ITs da Binance (`ContaSintetica`) cobrem |
| API | DTOs de ativo, transação, provento: `currency`, `fxRate`, valores originais; novos endpoints de administrador `/api/v1/admin/currencies` (lista, cria, edita, ativa) e `/currencies/{code}/rates` | Contratos novos; campos existentes mantêm o significado (reais) | Front e API mudam juntos |
| Front | `CurrencyInput`/`MaskedNumberInput`, `utils/format.ts` (`formatCurrency`) — 22 arquivos usam `formatCurrency`, 5 usam `CurrencyInput` | **Não há escolha de moeda pelo usuário em lugar nenhum.** A moeda vem do ativo (D2): ativo nacional (BRL) continua exatamente como hoje — R$, sem campo de moeda e sem câmbio (D5); ativo estrangeiro mostra os valores com o símbolo da moeda dele (US$), fixo, não editável. Internamente os componentes recebem a moeda do ativo; as chamadas atuais (todas de valores em reais) não mudam | Baixo |
| Front | `TransactionFormModal` | Ativo nacional: sem mudança. Ativo estrangeiro: preço, total e taxa em US$ (moeda do ativo, não selecionável) + campo câmbio (PTAX venda pré-preenchida, editável, ↻ para voltar ao padrão como no Preço Teto) + total em reais calculado | Médio: modal já é o mais complexo (RF, TD, cripto) |
| Front | `TransactionsPage`, `PortfolioPage`, `DividendsPage` | Valor original na moeda (hover ou coluna) com o total em reais | Carteira continua em reais (D14) |
| Front | **Tela de administrador "Moedas"** (`AdminOnly`, item `adminOnly` no menu, como Tarefas) | Lista/edição de moedas e a série de câmbio com gráfico (componente dos Índices) | Baixo |
| Docs | `Claude.md`, `ACTIVEST_DB.md`, `PENDENCIAS.md` (37.1, 3.8, 15 parcial), `SCHEDULE_TASKS.md`, `ADM_TASKS.md`, `USE_CASES.md`, `README.md` | Atualização na entrega (REGRAS 7.2) | — |

### 9.3 Fonte do câmbio (D7) — o que foi conferido

A API PTAX do BCB (`olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/`) tem `Moedas`, `CotacaoMoedaDia` e
`CotacaoMoedaPeriodo`, com **compra e venda** por boletim (abertura, intermediários e **fechamento**), cotação em reais;
10 moedas (USD, EUR, GBP, JPY, CHF, CAD, AUD, DKK, NOK, SEK); histórico desde 1984 (EUR desde 2002) — cobre 2010.
**Recomendação para o Planning:** API PTAX como fonte do cadastro (boletim de fechamento, compra e venda); SGS 1 segue
no índice `USD_PTAX` (D6), com teste de que os dois coincidem.

### 9.4 Base legal dos tributos (para o seed de `foreign_tax_rules`)

| Regra | Valor levantado | Situação |
|---|---|---|
| IOF câmbio — investimento no exterior | 1,1% | Confirmar o decreto de cada vigência no seed (texto da `legal_basis`) |
| IOF câmbio — conta própria/disponibilidade | 1,1% até 22/05/2025; 3,5% desde 23/05/2025 (Decretos 12.466/12.467; 12.499) | idem |
| IOF câmbio — ingresso/retorno | 0,38% | idem |
| Retenção EUA — dividendos (com W-8BEN) | 30%; sem tratado que reduza para pessoa física; ganho de capital isento lá | Confirmado em duas fontes de mercado |
| IR Brasil — desde 01/01/2024 (Lei 14.754) | 15% anual sobre rendimentos e ganhos, variação cambial incluída, crédito do imposto pago no exterior no mesmo ano | Confirmado (texto da lei/PwC). Efeito no provento americano: 30% retido ≥ 15% → IR BR estimado 0 |
| IR Brasil — antes de 2024 (D21) | Ganho de capital progressivo (15% a 22,5%); proventos pelo carnê-leão (tabela progressiva) com crédito do retido | **A confirmar no Planning** (isenção mensal de vendas e tabela do carnê-leão por ano); a regra entra com `valid_to = 2023-12-31` |

### 9.5 Riscos e dependências

1. **Rede da sessão**: a API PTAX e os sites de legislação não respondem pelo proxy desta sessão (403, política —
   não se contorna). A conferência real fica na Implementation/Testing, com a adm-api na máquina do Guilherme.
2. **Dois câmbios do dólar** (índice SGS × série PTAX): teste de igualdade; se divergirem, a série PTAX manda nos cálculos.
3. **Desempenho da Rentabilidade/Metas**: conversão diária só para posições em moeda estrangeira, em lote e com cache.
4. **Estimativa × imposto oficial**: o IR exibido é estimativa por operação (D20) — rótulo e aviso na tela, como o
   "líquido estimado" da RF.
5. **Catálogo sem ativo em dólar hoje**: os testes precisam de ativo fictício em USD (IT), e a prova real fica com os
   ETFs americanos.
6. **Dependência inversa**: ETFs americanos/Internacionais esperam este escopo; Metas (região/moeda) só consome depois.

### 9.6 Pontos para o Planning — ✅ decididos em 26/10/2026

| # | Pergunta | Recomendação |
|---|---|---|
| P1 | Regra de retenção do exterior: em `withholding_tax_rules` (coluna de jurisdição/país) ou só em `foreign_tax_rules` | ✅ Só `foreign_tax_rules`: separa BR de exterior e não mexe na regra que já funciona |
| P2 | Horário da tarefa `EXCHANGE_RATES` | ✅ 13:30 (depois do boletim de fechamento da PTAX, ~13h), com recuperação na subida, **mas no máximo uma rodada automática por dia** (agendada ou subida; o Run manual é a única exceção) — para não repetir o caso do Tesouro (PLANO_FONTE_TESOURO A4). Desenho em 9.7 |
| P3 | Exibição do valor original nas listas (Transações, Dividendos) | ✅ Coluna de valor em reais como hoje + valor na moeda abaixo, em fonte menor, só quando a moeda não é BRL |

### 9.7 Uma rodada automática por dia (P2)

Hoje a D37 do PLANO_TAREFAS impede só a **subida** de rodar duas vezes no dia; a rodada **agendada** ainda roda mesmo
que a subida já tenha rodado. Para a tarefa de câmbio valer "uma rodada automática por dia":

| Situação | Comportamento |
|---|---|
| Sistema sobe **antes** das 13:30 | A subida **não roda** a tarefa (a agendada das 13:30 vai rodar e já pega o fechamento do dia) |
| Sistema sobe **depois** das 13:30 sem rodada no dia | A subida roda **uma vez**, no lugar da agendada perdida |
| 13:30 com rodada automática já feita no dia | A agendada pula (registra o pulo no log, sem chamar a fonte) |
| Fonte recusou ou falhou | Não tenta de novo no mesmo dia automaticamente; só o Run manual |
| Run manual | Sempre roda (exceção pedida) e **não** conta para a regra |

Implementação prevista: a regra "uma automática por dia" entra no executor de tarefas como opção do tipo de tarefa
(ligada para `EXCHANGE_RATES`), sem mudar o comportamento das outras. Estender às demais tarefas fica como pendência
para o Guilherme decidir.

## 10. Planning — solução técnica (26/10/2026)

### 10.1 Modelo de dados — `V9__currencies.sql`

Nomes em inglês (REGRAS 9); campos `NOT NULL` novos em tabela existente com default (REGRAS 8.1).

**Tabelas novas**

| Tabela | Colunas | Observação |
|---|---|---|
| `currencies` | `code VARCHAR(3)` PK (ISO 4217), `name`, `symbol` (`R$`, `US$`), `decimals SMALLINT`, `is_base BOOLEAN` (índice único parcial: só uma `true`), `active`, `rate_source VARCHAR(20)` (`NONE` na base, `BCB_PTAX`), `created_at`, `updated_at` | Seed: BRL (base, `NONE`), USD (`BCB_PTAX`) — D8 |
| `exchange_rates` | PK (`currency_code`, `reference_date`), `buy_rate NUMERIC(18,8)`, `sell_rate NUMERIC(18,8)`, `bulletin_at TIMESTAMP` (hora do boletim), `source VARCHAR(20)`, `created_at`, `updated_at`; `CHECK` taxas > 0 | Só o **boletim de fechamento** do dia (D22); moeda base não tem linha |
| `foreign_tax_rules` | `id`, `tax` (`IOF_FX`, `IR_FOREIGN`, `WITHHOLDING_FOREIGN`), `event` (`REMITTANCE_INVESTMENT`, `REMITTANCE_OWN_ACCOUNT`, `INFLOW`, `DIVIDEND`, `SALE`), `country VARCHAR(2)` (nulo = qualquer), `asset_type_id` (nulo = qualquer), `rate NUMERIC(6,4)`, `brackets JSONB` (faixas progressivas/isenção, quando houver — D23), `valid_from`, `valid_to`, `legal_basis VARCHAR(200) NOT NULL`; `CHECK` de vigência como as tabelas de taxa atuais | Seed com a seção 9.4 (P1: só aqui) |
| `fx_operations` | `id UUID`, `user_id` FK cascade, `operation_date`, `from_currency_code`, `to_currency_code`, `amount_sent NUMERIC(18,2)`, `amount_received NUMERIC(18,2)`, `effective_rate NUMERIC(18,8)`, `purpose` (`INVESTMENT`, `OWN_ACCOUNT`, `INFLOW`), `iof_rate`, `iof_amount`, `fee_amount`, `institution VARCHAR(120)`, `foreign_tax_rule_id` FK, `notes`, timestamps | D19: tabela, serviço e API sem tela (a tela nasce nos Internacionais) |

**Colunas novas**

| Tabela | Colunas (default) |
|---|---|
| `asset_types` | `default_currency_code` (`BRL`) FK, `region VARCHAR(10)` (`BRASIL`; `CHECK BRASIL/EXTERIOR/CRIPTO`). Seed: 8/9/10 → `USD`/`EXTERIOR`; 7 BDR → `BRL`/`EXTERIOR`; 11 → `BRL`/`CRIPTO` |
| `assets` | `currency_code` (`BRL`) FK, `reference_currency_code` (nulo) FK. Seed: USDT/USDC → `reference_currency_code = USD` (D17) |
| `transactions` | `currency_code` (`BRL`) FK, `fx_rate NUMERIC(18,8)` (`1`), `fx_rate_origin VARCHAR(10)` (`BASE`; `PTAX`/`INFORMED`), `original_price_per_share NUMERIC(18,8)`, `original_total_amount NUMERIC(18,2)`, `original_fee NUMERIC(18,2)` (nulos em BRL); `CHECK`: `currency_code = 'BRL'` ⇔ `fx_rate = 1` e originais nulos |
| `dividends` | `currency_code` (`BRL`), `fx_rate` (`1`), `original_amount_per_share NUMERIC(18,8)`, `original_total_amount NUMERIC(18,2)`, `foreign_withholding_rate`, `foreign_withholding_tax`, `foreign_tax_rule_id` FK (nulos em BRL) |
| `ceiling_price_quarters` | sai `ck_ceiling_price_quarters_currency`; entra FK `currency` → `currencies(code)` (D9) |
| `task_types` | linha `EXCHANGE_RATES` (`ADM_API`) e a tarefa `EXCHANGE_RATES` com cron 13:30 e `run_on_startup = true` |

Colunas que já existem (`price_per_share`, `total_amount`, `brokerage_fee`, `net_amount`, `income_tax`, `amount_per_share`,
`total_amount`/`net_*` do provento, `assets.current_price`…) **mantêm o significado**, com uma exceção decidida na D11:
`assets.current_price` e `asset_price_history` passam a estar **na moeda do ativo** (hoje todas BRL — nada muda nos dados).

### 10.2 Regras de cálculo

| # | Regra |
|---|---|
| C1 | **Câmbio da data** (`ExchangeRateService.rateOn(moeda, data, lado)`): moeda base → 1; senão o fechamento do dia ou, sem boletim, o **último boletim anterior carregado** (sem limite de dias, nunca indisponível — D27), devolvendo a data efetiva para a tela marcar "câmbio de dd/mm". Só a data anterior ao primeiro boletim da série (antes de 2010) não tem câmbio — nunca zero, nunca estimado |
| C1a | **Fonte atrasada** (D27, mesma regra do Tesouro — PLANO_FONTE_TESOURO D3): a fonte respondeu e o boletim mais recente está **mais de 2 dias úteis atrás** (`BusinessCalendar`, exclusive nos dois lados) → aviso de administrador `FONTE_DESATUALIZADA` (`AdminNotices.sourceStale`, não repete enquanto aberto), resolvido sozinho quando o boletim novo chega; `staleSince` no resumo da rodada. Os cálculos seguem com o último câmbio carregado |
| C2 | **Transação de ativo estrangeiro**: o usuário informa preço/quantidade/taxa na moeda do ativo; `fx_rate` padrão = PTAX **venda** da data (D5/D16), editável (`INFORMED`); `price_per_share = original × fx_rate` (8 casas), `total_amount = original_total × fx_rate` e `brokerage_fee = original_fee × fx_rate` (2 casas, HALF_EVEN como o resto do sistema) |
| C3 | **Ativo nacional**: requisição com moeda ≠ BRL ou câmbio ≠ 1 → 422 (D5) |
| C4 | **Valorização**: valor em reais = quantidade × preço na moeda × câmbio de venda da data (C1); hoje = último boletim |
| C5 | **Venda de ativo estrangeiro**: custo em reais pelo preço médio em reais (já existente) da quantidade vendida; ganho = `total_amount` − custo; IR estimado = ganho × regra `IR_FOREIGN`/`SALE` vigente (desde 2024: 15%; antes: D23); ganho ≤ 0 → 0; grava `income_tax` e `net_amount` (o `CHECK` de venda já exige os dois). Rótulo "estimado" e aviso da compensação anual (D20) |
| C6 | **Provento de ativo estrangeiro**: bruto na moeda × câmbio de **compra** ou **venda** da data de pagamento (D24) → bruto em reais; retenção do exterior = bruto × `WITHHOLDING_FOREIGN` do país do ativo; IR BR estimado = max(0, bruto × `IR_FOREIGN`/`DIVIDEND` − retenção); líquido = bruto − retenção − IR BR |
| C7 | **Operação de câmbio**: IOF = valor enviado × `IOF_FX` vigente pela finalidade e data; `effective_rate` = recebido ÷ (enviado − IOF − tarifa) |
| C8 | **Edição**: mudar câmbio, preço ou data regrava as colunas em reais e recalcula o que depende da posição (mesmos gatilhos de hoje) |

### 10.3 Tarefa `EXCHANGE_RATES` e a regra de uma rodada por dia (9.7)

- `TaskHandler` ganha `default boolean oncePerDay() { return false; }`. Com `true`, o `TaskExecutor`:
  - **agendada**: pula (`SKIPPED`, motivo no log) se já existe rodada `SCHEDULE` ou `STARTUP` no dia (fuso de São Paulo);
  - **subida**: só roda se o horário do cron da tarefa **já passou hoje** e não houve rodada automática no dia;
  - **manual**: roda sempre e não conta.
- Só o handler do câmbio liga `oncePerDay()`; as outras tarefas não mudam (estender = pendência).
- Handler: lê as moedas ativas com `rate_source = BCB_PTAX`; por moeda, da última data gravada (ou de 2010-01-01) até
  hoje, em janelas que a API aceite; grava o boletim de fechamento; janela de revisão dos últimos 5 dias úteis;
  arquivo por ano em `carga/data/bcb/ptax/{moeda}/{ano}.json` (ano fechado lido do disco, como `IndexYearCache`);
  entra no `BOOTSTRAP` depois dos índices; avisa "catálogo mudou" para a app-api limpar caches.

### 10.4 API

| Serviço | Rota | Uso |
|---|---|---|
| app-api | `GET /api/v1/currencies` | moedas ativas (símbolo/casas para o front) |
| app-api | `GET /api/v1/currencies/{code}/rates?from=&to=` e `/rates/on?date=` | série e câmbio padrão da data (modal de transação) |
| app-api (admin) | `GET/POST/PUT /api/v1/admin/currencies`, `PATCH .../{code}/active` | tela Moedas (só `is_admin`; moeda base não se desativa nem perde `is_base`) |
| app-api | `GET /api/v1/foreign-tax-rules` | consulta das regras (D18: sem edição) |
| app-api | `GET/POST/PUT/DELETE /api/v1/fx-operations` | operações de câmbio (D19: sem tela) |
| DTOs | ativo: `currency`, `region`; transação/provento: `currency`, `fxRate`, `fxRateOrigin`, `original*`, `foreignWithholding*` | campos existentes inalterados (em reais) |

### 10.5 Front

- **Ativo nacional: nenhuma mudança visível.**
- `CurrencyInput`/`formatCurrency` recebem a moeda (padrão `BRL`) — uso interno, sem seletor.
- `TransactionFormModal` (ativo estrangeiro): preço/taxa em US$, campo **Câmbio** com PTAX venda da data e ↻, "Total em
  reais" calculado; mensagem quando não há câmbio na data.
- Transações e Dividendos: reais como hoje + valor na moeda abaixo, em fonte menor, só fora do BRL (P3). Proventos de
  ativo estrangeiro mostram retenção do exterior e IR estimado no detalhe.
- Venda de ativo estrangeiro: bruto, IR estimado e líquido (como a venda de RF).
- **Tela Moedas** (`/currencies`, `AdminOnly`, último bloco do menu junto de Tarefas): lista (código, nome, símbolo,
  casas, base, ativa, fonte, último câmbio e data), incluir/editar/ativar, e por moeda o gráfico da série (componente
  dos Índices, compra e venda) + regras de tributo do exterior em consulta.

### 10.6 Etapas de implementação

| # | Etapa | Conteúdo | Pronto quando |
|---|---|---|---|
| **M1** | Banco e core | V9; entidades; `ExchangeRateService` (C1); `ForeignTaxRuleService`; `oncePerDay` no executor | Flyway + Hibernate validam; testes do câmbio (último carregado, base, dia sem boletim) e do executor (agendada pula, subida antes/depois do horário, manual livre) verdes |
| **M2** | Câmbio na adm-api | `PtaxClient`, import, handler `EXCHANGE_RATES`, aviso de fonte desatualizada (C1a), arquivos, `BOOTSTRAP`, push para o bucket | Carga real 2010→hoje do USD na máquina do Guilherme; **D7 confirmada**; conferência PTAX venda = índice `USD_PTAX` (SGS 1) em todos os dias |
| **M3** | Transações | C2, C3, C8 no `TransactionService`/DTOs; validações | ITs com ativo fictício em USD: compra, edição de câmbio, ativo BRL recusando câmbio |
| **M4** | Venda e proventos | C5 (IR estimado), C6 (retenção + IR BR), C7 (`fx_operations` + IOF) | ITs por vigência (antes/depois de 2024; IOF antes/depois de 23/05/2025) |
| **M5** | Valorização | C4 nos 7 pontos de leitura de preço (9.2), série em lote para Rentabilidade/Metas | Carteira/Rentabilidade/Metas com ativo BRL **idênticas** (regressão) e com ativo USD convertendo; tempo da Rentabilidade sem piora perceptível |
| **M6** | Preço Teto e integração | `CeilingPriceDefaults`/`MoneyNormalizer` pela série nova; textos fixos da integração pelo cadastro | `CeilingPriceIT` e ITs da Binance verdes sem mudança de resultado |
| **M7** | API de administração e consulta | rotas 10.4 | ITs de permissão (não admin → 403) |
| **M8** | Front | 10.5 | `tsc -b`, `eslint`, `vite build`; conferência no navegador com ele logado (caminho 3) |
| **M9** | Fechamento | `Claude.md`, `ACTIVEST_DB.md`, `PENDENCIAS.md` (37.1 e 3.8 resolvidos; pendências novas), `SCHEDULE_TASKS.md`, `ADM_TASKS.md`, `USE_CASES.md`, `README.md`, andamento | Bateria completa verde (`mvn clean verify`) |

### 10.7 Critérios de aceitação

1. Banco novo do zero sobe com BRL (base) e USD; nenhum dado existente muda de valor.
2. A tela e a API de ativos nacionais se comportam exatamente como antes (mesmos números em Carteira, Rentabilidade,
   Metas, Dashboard, Dividendos, Preço Teto).
3. A tarefa de câmbio carrega o USD desde 2010, roda no máximo uma vez por dia automaticamente e sempre no Run manual; fonte com mais de 2 dias úteis de atraso gera o aviso de fonte desatualizada, e os cálculos seguem com o último câmbio.
4. Ativo em USD: compra em US$ com PTAX venda padrão editável; reais gravados; posição valorizada pelo câmbio do dia.
5. Venda e provento de ativo em USD mostram bruto, tributos do exterior estimados por data e líquido.
6. Moeda nova = linha na tela Moedas (ou migration) + carga da série, sem código novo.
7. Só administrador vê e altera a tela Moedas.
8. Preço Teto em USD dá o mesmo resultado de antes.

### 10.8 Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| M1–M8: código, testes, compilação, ambiente | ✅ | — |
| M2: carga real do câmbio e push para o bucket | ✅ (REGRAS 5.3) | confere o resultado |
| Base legal do seed (`legal_basis` de cada regra) | prepara | **valida o texto e as alíquotas** antes da M4 |
| Conferência no navegador (caminho 3) | prepara o roteiro | ✅ logado |
| Commit, branch `TRACK_01-*`, PR para a `develop` | sugere a mensagem | ✅ |

### 10.9 Decisões do Planning — ✅ decididas em 26/10/2026 (D27 pelo Guilherme; as demais pela recomendação, com aprovação explícita)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D22** | Boletins guardados | (a) só o de fechamento; (b) todos (abertura, intermediários) | **(a)**: é o que os cálculos usam; intradiário de câmbio não tem uso hoje |
| **D23** | IR antes de 2024 | (a) regras do regime anterior: venda pelo ganho de capital progressivo (15% a 22,5%) com isenção mensal de vendas até R$ 35 mil; provento com IR BR = 0 porque a retenção dos EUA (30%) cobre a alíquota máxima do carnê-leão (27,5%); (b) não estimar antes de 2024 (marcar "regime anterior, não estimado") | **(a)**, com a base legal validada por você antes da M4 |
| **D24** | Câmbio do provento | (a) PTAX **compra** da data de pagamento (o dinheiro sai da moeda para reais); (b) PTAX venda, como o resto | **(a)**, registrando a origem; editável no provento manual |
| **D25** | Quem define a moeda do ativo | (a) vem do tipo, gravada no cadastro do catálogo (seed ou importação); muda só por migration/importação; (b) editável pelo administrador numa tela de ativo | **(a)** agora; edição de ativo pelo administrador fica para os Internacionais |
| **D26** | Arredondamento dos reais calculados | (a) 2 casas no total e 8 no preço, HALF_EVEN; (b) HALF_UP | **(a)**, o padrão atual do sistema |
| **D27** | Lacuna de câmbio na valorização (C1) | (a) último boletim anterior, marcado; (b) indisponível depois de N dias úteis sem boletim | ✅ **Decidido pelo Guilherme: nunca fica indisponível.** Usa o último câmbio carregado e a mesma regra de atraso do Tesouro: mais de 2 dias úteis sem boletim novo → aviso `FONTE_DESATUALIZADA` ao administrador (C1a) |
| **D28** | Run manual da tarefa com período | (a) atributos `currency` e `from` (como `MARKET_INDICES`); (b) sem atributos | **(a)** |
| **D29** | Ordem de execução | (a) M1→M9, uma etapa por vez, cada uma fechada (REGRAS 2.3); (b) tudo de uma vez | **(a)** |

## 11. Implementação (26/10/2026)

O que entrou, por etapa, e o que mudou em relação ao Planning:

| Etapa | Entregue |
|---|---|
| M1 | **V9** (`currencies`, `exchange_rates`, `foreign_tax_rules` com 10 regras, `fx_operations`, colunas de moeda e região, FK do Preço Teto, tarefa `EXCHANGE_RATES`); entidades em `activest-core/.../domain/currency/`; `ExchangeRateService` (C1, cache da série em memória com 10 min + `evict`), `ForeignTaxCalculator`, `ForeignTaxRuleService`; `TaskHandler.oncePerDay()` no executor. Testes: `CurrencyRepositoryTest` (7), `ForeignTaxCalculatorTest` (4), `TaskExecutorTest` (+2) |
| M2 | `PtaxClient` (Olinda, `CotacaoMoedaPeriodo`, só "Fechamento"), `ExchangeRateImportService` (incremental, 10 dias de revisão, C1a com `FONTE_DESATUALIZADA`), `ExchangeRatesTask` (`oncePerDay`), etapa "câmbio 2010+" no `BOOTSTRAP`, origem `CAMBIO` no "catálogo mudou". **Carga real no DEV**: 4.223 boletins de 04/01/2010 a 26/10/2026 em 5 s; **D7 confirmada** — os 4.222 dias até 23/10 iguais ao `USD_PTAX` (SGS 1). Agendada das 13:30 rodou depois do Run manual; a subida das 13:41 não rodou (uma automática por dia). Testes: `PtaxClientTest` (2), `ExchangeRateImportIT` (3) |
| M3 | `TransactionCurrency` (C2, C3, C8) no criar, editar e restaurar; `fxRate` no `TransactionCreateDTO`; `currency` no `TransactionResponseDTO`. Testes: `CurrencyTransactionsIT` (4) |
| M4 | `ForeignSaleTax` (C5: IR estimado + líquido na venda), `ForeignDividendCalculator` (C6, no `DividendRecalculator`), `FxOperationService` (C7). **V10** (fora do Planning): o provento do exterior não tem regra de retenção brasileira, e a `ck_dividends_withholding` exigia regra para ter líquido — a V10 cria `dividends.foreign_income_tax` e refaz as duas constraints com o caso do exterior. Testes: `ForeignTaxesIT` (4) |
| M5 | Câmbio no `AppreciationUpdater`, `PortfolioService` (`foreignQuote`) e `PortfolioTimelineBuilder` (impressão digital com `exchange_rates`); CAMBIO refaz valorização e proventos das posições na moeda. Regressão: `PortfolioIT`, `PerformanceIT`, `GoalsIT`, `DashboardSummaryIT`, `DividendsIT`, `PositionRefreshIT` iguais |
| M6 | Preço Teto lê o câmbio do cadastro (`CeilingPriceDefaults.fxRate`) e aceita qualquer moeda ativa (`MoneyNormalizer` sem enum). Integração: `FiatCurrencies` (core) no lugar da lista fixa BRL/USD/EUR e do `"BRL"` da importação cripto e do conector da Binance |
| M7 | app-api: `/api/v1/currencies`, `/currencies/{code}/rate`, `/rates`, `/foreign-tax-rules`, `/fx-operations`; **adm-api**: `/api/v1/admin/currencies` (o cadastro é catálogo, como Tarefas — proxy do Vite e do nginx). Testes: `CurrencyApiIT` (2), `CurrencyAdminIT` (1) |
| M8 | Modal de transação (câmbio só em ativo de outra moeda, ↻ para a PTAX, total em reais), Transações e Dividendos com o valor na moeda embaixo (P3), Carteira com a cotação na moeda, tela **Moedas** (`/currencies`, só administrador). `tsc -b`, `eslint` e `vite build` sem erro |
| M9 | `Claude.md`, `docs/ACTIVEST_DB.md` (5c), `docs/PENDENCIAS.md` (seção 38; 37.1 resolvido), `docs/SCHEDULE_TASKS.md` ([A16]), `docs/ADM_TASKS.md` ([20]), `docs/USE_CASES.md` (UC43–UC45), `README.md`; `carga/data/bcb/ptax` no bucket |

**Achados da implementação** (registrados também em `docs/PENDENCIAS.md`, 38):

- Saldo em EUR na Binance passaria a aparecer como divergência (a lista fixa tinha EUR; o cadastro só tinha BRL e USD,
  D8). **Resolvido em 26/10/2026, pedido do Guilherme: V11 cadastra o EUR inativo.**
- Editar uma venda de um ativo do exterior não refaz o IR das vendas de outros ativos do exterior no mesmo mês (só
  importa na isenção mensal do regime anterior a 2024) (38.3).

## 12. Andamento

| Data | Etapa | O que foi feito |
|---|---|---|
| 26/10/2026 | 1 Discovery | Levantamento em planos, pendências e código; descrição O1–O10, requisitos e D1–D14 |
| 26/10/2026 | 1 Discovery | Respostas do Guilherme (6.1): tributos do exterior entram (regra revogada), insumos para saldo/stablecoin/preço, tela de administrador; levantamento do IOF de câmbio e da Lei 14.754 (4.1); análise primária × secundária (6.2); decisões novas D15–D21 |
| 26/10/2026 | 2 Impact | D15–D21 aprovadas pela recomendação; impacto por componente (9.2), fonte do câmbio conferida pela documentação do BCB (9.3), base legal levantada (9.4), riscos (9.5) e P1–P3 para o Planning. API PTAX não testada ao vivo: proxy da sessão (403) |
| 26/10/2026 | 2 Impact | Correção do Guilherme: não há escolha de moeda no front — ativo nacional sem mudança, estrangeiro na moeda fixa do ativo (O10, R12, 9.2). P1–P3 decididos; P2 com a regra "uma rodada automática por dia" (9.7) |
| 26/10/2026 | 3 Planning | Modelo da V9 (10.1), regras C1–C8, tarefa com uma rodada automática por dia (10.3), API, front, etapas M1–M9, critérios de aceitação, Quem faz e D22–D29 |
| 26/10/2026 | 3 Planning | D27 pelo Guilherme (câmbio nunca indisponível; atraso pela regra do Tesouro — C1a); D22–D26, D28, D29 pela recomendação. Planning concluído; aguarda a ordem de execução |
| 26/10/2026 | 4–6 Implementation, Testing, Review | M1–M9 executadas (seção 11); V10 acrescentada na M4; carga real da PTAX no DEV com D7 confirmada; bateria completa e conferência registradas no fechamento |
| 26/10/2026 | 5–7 Testing, Review, Commit | Bateria completa verde: core 230, integration 103, adm-api 133 + 87 ITs, app-api 236 + 297 ITs (o `ExchangeRateImportIT` deixou de usar `@MockBean` — um contexto a mais estourava o limite de conexões do Postgres de teste; `TaskControllerIT` e `SchemaTest` com a tarefa e a V10). Front: `tsc -b`, `eslint`, `vite build` limpos. `carga/data/bcb` enviado ao bucket. Falta: conferência no navegador e validação das bases legais pelo Guilherme; commit numa `TRACK_01-*` (o working tree está na `develop`, protegida) |

| 26/10/2026 | Review | Guilherme validou o escopo, exceto as bases legais do seed de tributos do exterior, que ficam como pendência (PENDENCIAS 38.4). Pediu o EUR no cadastro: V11 (inativo) |
| 26/10/2026 | 7 Commit | Guilherme declarou o escopo finalizado; operações de git com ele |
