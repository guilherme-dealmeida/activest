# ACTIVEST_DB — Modelo de dados

> Gerado em 02/10/2026 a partir do banco de desenvolvimento (`db_activest`, Flyway na **V39**), lendo o `information_schema` e as constraints — não do texto do `Claude.md`.
> **31 tabelas** (fora `flyway_schema_history`), **43 chaves estrangeiras**. Acréscimo de 05/10/2026: `market_indices` e
> `market_index_values` (V43, seção 5a — `docs/planos/PLANO_INDICES_MERCADO.md`); V44: `asset_types.price_source` e
> `asset_price_history.source` aceitam `TESOURO` (preço oficial do Tesouro Direto). Acréscimo de 10/10/2026
> (`docs/planos/PLANO_PRECO_TETO.md`): V45 `company_quarters.dividends_paid_q/_ttm`; V46 `ceiling_prices` recriada
> (entradas do modelo PREÇO TETO V2) e `ceiling_price_quarters` (nova). Acréscimo de 13/10/2026
> (`docs/planos/PLANO_NOTIFICACOES.md`): V48 `notifications` e `notification_states` (seção 5b); V49
> `ceiling_prices.alert_enabled/alert_below_ideal/alert_last_on`; V50 `transactions.origin` aceita `SISTEMA` (venda da
> liquidação automática); V51 `stock_event_costs`. Acréscimo de 24/10/2026 (`docs/planos/PLANO_TELA_ATIVOS.md`, depois
> da consolidação em V1–V6): V7 `company_quarters` ganha `gross_profit_q/_ttm`, `depreciation_amortization_q/_ttm`,
> `pretax_income_q/_ttm`, `income_tax_q/_ttm`, `current_assets` e `current_liabilities` (nulas, no fim da tabela);
> `cvm_statement_values.metric` ganha os valores `LUCRO_BRUTO`, `DEPRECIACAO_AMORTIZACAO`, `EXAUSTAO_BIOLOGICA`,
> `LAIR`, `IR_CSLL`, `ATIVO_CIRCULANTE` e `PASSIVO_CIRCULANTE`. Diagramas em Mermaid: o GitHub e o IntelliJ (plugin Mermaid) renderizam.

**A ideia que organiza o schema:** o *catálogo* é global (o que o instrumento É — `assets`, preços, proventos, emissores; gravado pela **adm-api**), e o *papel* é do usuário (posição, transações, título de renda fixa, proventos recebidos; gravado só pela **app-api**).

Legenda das relações: `||--o{` um para muitos (FK obrigatória) · `|o--o{` um para muitos (FK opcional) · `||--o|` um para um. O rótulo é a coluna da FK.

## Sumário

1. [Visão geral](#1-visão-geral)
2. [Usuário e carteira](#2-usuário-e-carteira)
3. [Catálogo de ativos, preços e proventos](#3-catálogo-de-ativos-preços-e-proventos)
4. [Emissores, balanços e indicadores](#4-emissores-balanços-e-indicadores)
5. [Integrações com corretoras](#5-integrações-com-corretoras)
5a. [Índices de mercado](#5a-índices-de-mercado)
6. [Descrição das tabelas](#6-descrição-das-tabelas)
5b. [Notificações](#5b-notificações)
7. [Regras de exclusão (ON DELETE)](#7-regras-de-exclusão-on-delete)

## 1. Visão geral

Todas as tabelas e relações, sem colunas.

```mermaid
erDiagram
    assets ||--o{ asset_cash_events : "asset_id"
    asset_types ||--o{ asset_categories : "asset_type_id"
    assets ||--o{ asset_price_history : "asset_id"
    assets ||--o{ asset_stock_events : "asset_id"
    assets ||--o| asset_terms : "asset_id"
    asset_categories |o--o{ assets : "asset_category_id"
    issuers |o--o{ assets : "issuer_id"
    asset_types ||--o{ assets : "asset_type_id"
    assets ||--o{ ceiling_prices : "asset_id"
    users ||--o{ ceiling_prices : "user_id"
    ceiling_prices ||--o{ ceiling_price_quarters : "ceiling_price_id"
    cvm_documents ||--o{ company_quarters : "source_doc_id"
    issuers ||--o{ company_quarters : "issuer_id"
    issuers ||--o{ cvm_documents : "issuer_id"
    cvm_documents ||--o{ cvm_statement_values : "document_id"
    withholding_tax_rules |o--o{ dividends : "withholding_rule_id"
    asset_cash_events |o--o{ dividends : "cash_event_id"
    portfolio_assets ||--o{ dividends : "portfolio_asset_id"
    issuers ||--o{ fii_monthly : "issuer_id"
    assets ||--o{ fixed_income_notes : "asset_id"
    users ||--o{ fixed_income_notes : "user_id"
    users ||--o{ goals : "user_id"
    users ||--o{ integration_connections : "user_id"
    integration_connections ||--o| integration_credentials : "connection_id"
    assets ||--o{ integration_crypto_exclusions : "asset_id"
    integration_connections ||--o{ integration_crypto_exclusions : "connection_id"
    integration_connections ||--o{ integration_cursors : "connection_id"
    transactions |o--o{ integration_run_items : "transaction_id"
    transactions |o--o{ integration_run_items : "duplicate_of"
    integration_runs ||--o{ integration_run_items : "run_id"
    integration_connections ||--o{ integration_runs : "connection_id"
    assets ||--o{ portfolio_assets : "asset_id"
    fixed_income_notes |o--o{ portfolio_assets : "note_id"
    portfolios ||--o{ portfolio_assets : "portfolio_id"
    users ||--o{ portfolios : "user_id"
    cvm_documents ||--o| share_counts : "document_id"
    asset_types ||--o{ strategies : "asset_type_id"
    users ||--o{ strategies : "user_id"
    users ||--o| strategy_reservations : "user_id"
    portfolio_assets ||--o{ transactions : "portfolio_asset_id"
    fixed_income_notes |o--o{ transactions : "note_id"
    integration_connections |o--o{ transactions : "connection_id"
    assets ||--o{ unit_compositions : "asset_id"
    asset_types ||--o{ withholding_tax_rules : "asset_type_id"
    market_indices ||--o{ market_index_values : "index_id"
```

## 2. Usuário e carteira

Tabelas: `ceiling_prices`, `ceiling_price_quarters`, `dividends`, `fixed_income_notes`, `goals`, `portfolio_assets`, `portfolios`, `strategies`, `strategy_reservations`, `transactions`, `users`.

Aparecem só como referência (de outro domínio): `asset_cash_events`, `asset_types`, `assets`, `integration_connections`, `integration_run_items`, `withholding_tax_rules`.

```mermaid
erDiagram
    ceiling_prices {
        uuid id PK "not null"
        uuid user_id FK "not null"
        bigint asset_id FK "not null"
        bigint shares "not null"
        numeric roe_pct "not null"
        numeric payout_pct "not null"
        numeric discount_rate_pct "not null"
        numeric perpetual_growth_pct "not null"
        numeric safety_margin_pct "not null"
        numeric price "not null"
        jsonb inputs_origin "not null"
        varchar notes
        timestamp calculated_at "not null"
        timestamp created_at "not null"
        timestamp updated_at "not null"
    }
    ceiling_price_quarters {
        uuid ceiling_price_id PK,FK "not null"
        smallint position PK "not null"
        date period_end "not null"
        numeric original_value "not null"
        varchar unit "not null"
        varchar currency "not null"
        numeric fx_rate "not null"
        numeric value_brl "not null"
        varchar origin "not null"
    }
    dividends {
        bigint id PK "not null"
        uuid portfolio_asset_id FK "not null"
        numeric amount_per_share "not null"
        numeric total_amount
        date ex_date "not null"
        date payment_date
        varchar type "not null"
        timestamp created_at "not null"
        bigint cash_event_id FK
        numeric shares_on_date
        varchar origin "not null"
        bigint withholding_rule_id FK
        numeric withholding_rate
        numeric withholding_tax
        numeric net_amount_per_share
        numeric net_amount
        timestamp tax_checked_at
    }
    fixed_income_notes {
        uuid id PK "not null"
        uuid user_id FK "not null"
        bigint asset_id FK "not null"
        varchar issuer
        date maturity_date
        varchar rate_type
        varchar indexer
        numeric indexer_rate
        date purchase_date
        timestamp created_at "not null"
        timestamp updated_at "not null"
    }
    goals {
        uuid id PK "not null"
        uuid user_id FK "not null"
        varchar name "not null"
        numeric target_amount "not null"
        numeric current_amount
        date target_date
        varchar status "not null"
        timestamp created_at "not null"
        timestamp updated_at "not null"
    }
    portfolio_assets {
        uuid id PK "not null"
        uuid portfolio_id FK "not null"
        bigint asset_id FK "not null"
        uuid note_id FK
        boolean is_reservation "not null"
        boolean is_settled "not null"
        numeric appreciation "not null"
        timestamp created_at "not null"
        timestamp updated_at "not null"
    }
    portfolios {
        uuid id PK "not null"
        uuid user_id FK "not null"
        varchar name "not null"
        timestamp created_at "not null"
    }
    strategies {
        uuid id PK "not null"
        uuid user_id FK "not null"
        bigint asset_type_id FK "not null"
        numeric target_percentage "not null"
    }
    strategy_reservations {
        uuid id PK "not null"
        uuid user_id FK,UK "not null"
        smallint type_reservation "not null"
        numeric target_percentage
        numeric target_value
        timestamp created_at "not null"
        timestamp updated_at "not null"
    }
    transactions {
        uuid id PK "not null"
        uuid portfolio_asset_id FK "not null"
        uuid note_id FK
        varchar type "not null"
        numeric shares
        numeric price_per_share
        numeric total_amount "not null"
        numeric brokerage_fee
        date transaction_date "not null"
        uuid sale_group_id
        boolean is_full_settlement "not null"
        timestamp created_at "not null"
        timestamp updated_at "not null"
        varchar origin "not null"
        varchar external_id
        uuid connection_id FK
        timestamp executed_at
        timestamp edited_at
        jsonb source_snapshot
    }
    users {
        uuid id PK "not null"
        varchar name "not null"
        varchar email UK "not null"
        varchar password_hash
        varchar google_id UK
        varchar avatar_url
        boolean is_admin "not null"
        timestamp created_at "not null"
        timestamp updated_at "not null"
    }
    assets ||--o{ ceiling_prices : "asset_id"
    users ||--o{ ceiling_prices : "user_id"
    ceiling_prices ||--o{ ceiling_price_quarters : "ceiling_price_id"
    withholding_tax_rules |o--o{ dividends : "withholding_rule_id"
    asset_cash_events |o--o{ dividends : "cash_event_id"
    portfolio_assets ||--o{ dividends : "portfolio_asset_id"
    assets ||--o{ fixed_income_notes : "asset_id"
    users ||--o{ fixed_income_notes : "user_id"
    users ||--o{ goals : "user_id"
    users ||--o{ integration_connections : "user_id"
    transactions |o--o{ integration_run_items : "transaction_id"
    transactions |o--o{ integration_run_items : "duplicate_of"
    assets ||--o{ portfolio_assets : "asset_id"
    fixed_income_notes |o--o{ portfolio_assets : "note_id"
    portfolios ||--o{ portfolio_assets : "portfolio_id"
    users ||--o{ portfolios : "user_id"
    asset_types ||--o{ strategies : "asset_type_id"
    users ||--o{ strategies : "user_id"
    users ||--o| strategy_reservations : "user_id"
    portfolio_assets ||--o{ transactions : "portfolio_asset_id"
    fixed_income_notes |o--o{ transactions : "note_id"
    integration_connections |o--o{ transactions : "connection_id"
```

## 3. Catálogo de ativos, preços e proventos

Tabelas: `asset_cash_events`, `asset_categories`, `asset_price_history`, `asset_stock_events`, `asset_terms`, `asset_types`, `assets`, `withholding_tax_rules`.

Aparecem só como referência (de outro domínio): `ceiling_prices`, `dividends`, `fixed_income_notes`, `integration_crypto_exclusions`, `issuers`, `portfolio_assets`, `strategies`, `unit_compositions`.

```mermaid
erDiagram
    asset_cash_events {
        bigint id PK "not null"
        bigint asset_id FK "not null"
        varchar type
        varchar label "not null"
        date data_com "not null"
        date payment_date
        numeric value_per_share "not null"
        date approved_on
        varchar related_to
        varchar isin
        boolean income_tax_exempt
        varchar source "not null"
        varchar source_ref
        timestamp fetched_at "not null"
        smallint seq "not null"
        varchar payment_date_source
    }
    asset_categories {
        bigint id PK "not null"
        varchar name "not null"
        varchar description
        bigint asset_type_id FK "not null"
    }
    asset_price_history {
        bigint id PK "not null"
        bigint asset_id FK "not null"
        date reference_date "not null"
        numeric open_price
        numeric close_price
        numeric high_price
        numeric low_price
        bigint volume
        timestamp created_at "not null"
        timestamp updated_at "not null"
        varchar source "not null"
    }
    asset_stock_events {
        bigint id PK "not null"
        bigint asset_id FK "not null"
        varchar type "not null"
        date data_com "not null"
        numeric factor "not null"
        numeric assigned_cost
        date approved_on
        varchar isin
        varchar source "not null"
        timestamp fetched_at "not null"
    }
    asset_terms {
        bigint asset_id PK,FK "not null"
        date maturity_date
        varchar rate_type
        varchar indexer
    }
    asset_types {
        bigint id PK "not null"
        varchar name UK "not null"
        varchar description
        varchar color_hex
        varchar price_source "not null"
        boolean fixed_income "not null"
    }
    assets {
        bigint id PK "not null"
        varchar ticker UK "not null"
        varchar name "not null"
        bigint asset_type_id FK "not null"
        bigint asset_category_id FK
        numeric current_price
        timestamp price_updated_at
        boolean active "not null"
        varchar price_source_ref
        varchar isin
        bigint issuer_id FK
        varchar share_class
        varchar issuer_link_source
    }
    withholding_tax_rules {
        bigint id PK "not null"
        varchar dividend_type "not null"
        bigint asset_type_id FK "not null"
        numeric rate "not null"
        varchar base_date "not null"
        date valid_from
        date valid_to
        varchar legal_basis "not null"
    }
    assets ||--o{ asset_cash_events : "asset_id"
    asset_types ||--o{ asset_categories : "asset_type_id"
    assets ||--o{ asset_price_history : "asset_id"
    assets ||--o{ asset_stock_events : "asset_id"
    assets ||--o| asset_terms : "asset_id"
    asset_categories |o--o{ assets : "asset_category_id"
    issuers |o--o{ assets : "issuer_id"
    asset_types ||--o{ assets : "asset_type_id"
    assets ||--o{ ceiling_prices : "asset_id"
    withholding_tax_rules |o--o{ dividends : "withholding_rule_id"
    asset_cash_events |o--o{ dividends : "cash_event_id"
    assets ||--o{ fixed_income_notes : "asset_id"
    assets ||--o{ integration_crypto_exclusions : "asset_id"
    assets ||--o{ portfolio_assets : "asset_id"
    asset_types ||--o{ strategies : "asset_type_id"
    assets ||--o{ unit_compositions : "asset_id"
    asset_types ||--o{ withholding_tax_rules : "asset_type_id"
```

## 4. Emissores, balanços e indicadores

Tabelas: `company_quarters`, `cvm_documents`, `cvm_statement_values`, `fii_monthly`, `issuers`, `share_counts`, `unit_compositions`.

Aparecem só como referência (de outro domínio): `assets`.

```mermaid
erDiagram
    company_quarters {
        bigint issuer_id PK,FK "not null"
        date period_end PK "not null"
        date fiscal_year_start
        bigint source_doc_id FK "not null"
        numeric revenue_q
        numeric ebit_q
        numeric net_income_q
        numeric net_income_parent_q
        numeric fco_q
        numeric dividends_declared_q
        numeric dividends_paid_q
        numeric revenue_ttm
        numeric ebit_ttm
        numeric net_income_ttm
        numeric net_income_parent_ttm
        numeric fco_ttm
        numeric dividends_declared_ttm
        numeric dividends_paid_ttm
        numeric equity
        numeric equity_parent
        numeric total_assets
        numeric cash
        numeric gross_debt
        bigint shares_total
        bigint shares_outstanding
        timestamp computed_at "not null"
        numeric gross_profit_q
        numeric gross_profit_ttm
        numeric depreciation_amortization_q
        numeric depreciation_amortization_ttm
        numeric pretax_income_q
        numeric pretax_income_ttm
        numeric income_tax_q
        numeric income_tax_ttm
        numeric current_assets
        numeric current_liabilities
    }
    cvm_documents {
        bigint id PK "not null"
        bigint issuer_id FK "not null"
        varchar doc_type "not null"
        date reference_date "not null"
        smallint version "not null"
        integer cvm_doc_id
        date received_at
        boolean consolidated "not null"
        timestamp created_at "not null"
        timestamp updated_at "not null"
    }
    cvm_statement_values {
        bigint document_id FK "not null"
        varchar metric "not null"
        date period_start
        date period_end "not null"
        numeric value "not null"
    }
    fii_monthly {
        bigint issuer_id PK,FK "not null"
        date reference_month PK "not null"
        integer version "not null"
        numeric net_assets
        numeric total_assets
        numeric shares_issued
        numeric nav_per_share
        integer shareholders
        numeric admin_fee_month
        numeric dividend_yield_month
        numeric amortization_month
        numeric liquidity_total
        numeric total_invested
        numeric real_estate_total
        numeric real_estate_companies
        numeric receivables_total
        numeric fii_shares_total
        numeric liabilities_total
        numeric income_to_distribute
        timestamp imported_at "not null"
    }
    issuers {
        bigint id PK "not null"
        varchar kind "not null"
        varchar cnpj UK "not null"
        integer cvm_code UK
        varchar legal_name "not null"
        varchar trade_name
        varchar registration_status
        varchar website
        varchar cvm_sector
        varchar b3_sector
        varchar b3_subsector
        varchar b3_segment
        varchar registration_category
        varchar listing_segment
        smallint fiscal_year_end_month
        varchar statement_layout
        varchar segment
        varchar cvm_segment
        varchar administrator_name
        varchar administrator_cnpj
        varchar management_type
        timestamp source_updated_at
        timestamp created_at "not null"
        timestamp updated_at "not null"
    }
    share_counts {
        bigint document_id PK,FK "not null"
        varchar source "not null"
        bigint ordinary_shares
        bigint preferred_shares
        bigint total_shares "not null"
        bigint ordinary_treasury
        bigint preferred_treasury
        bigint total_treasury
        integer scale_factor
        varchar scale_status "not null"
        varchar scale_check
    }
    unit_compositions {
        bigint asset_id PK,FK "not null"
        varchar share_class PK "not null"
        integer quantity "not null"
    }
    issuers |o--o{ assets : "issuer_id"
    cvm_documents ||--o{ company_quarters : "source_doc_id"
    issuers ||--o{ company_quarters : "issuer_id"
    issuers ||--o{ cvm_documents : "issuer_id"
    cvm_documents ||--o{ cvm_statement_values : "document_id"
    issuers ||--o{ fii_monthly : "issuer_id"
    cvm_documents ||--o| share_counts : "document_id"
    assets ||--o{ unit_compositions : "asset_id"
```

## 5. Integrações com corretoras

Tabelas: `integration_connections`, `integration_credentials`, `integration_crypto_exclusions`, `integration_cursors`, `integration_run_items`, `integration_runs`.

Aparecem só como referência (de outro domínio): `assets`, `transactions`, `users`.

```mermaid
erDiagram
    integration_connections {
        uuid id PK "not null"
        uuid user_id FK "not null"
        varchar provider "not null"
        varchar label "not null"
        varchar status "not null"
        timestamp consent_accepted_at "not null"
        timestamp last_sync_at
        text last_error
        timestamp created_at "not null"
        timestamp updated_at "not null"
    }
    integration_credentials {
        uuid connection_id PK,FK "not null"
        bytea ciphertext "not null"
        bytea iv "not null"
        smallint key_version "not null"
        varchar fingerprint "not null"
        jsonb permissions
        timestamp validated_at
        timestamp created_at "not null"
        timestamp updated_at "not null"
    }
    integration_crypto_exclusions {
        uuid id PK "not null"
        uuid connection_id FK "not null"
        varchar external_id "not null"
        bigint asset_id FK "not null"
        jsonb source_snapshot "not null"
        timestamp excluded_at "not null"
    }
    integration_cursors {
        uuid connection_id PK,FK "not null"
        varchar stream PK "not null"
        varchar cursor_value "not null"
        timestamp updated_at "not null"
    }
    integration_run_items {
        uuid id PK "not null"
        uuid run_id FK "not null"
        varchar external_id "not null"
        varchar outcome "not null"
        varchar reason
        jsonb raw "not null"
        timestamp created_at "not null"
        uuid duplicate_of FK
        varchar resolution
        uuid transaction_id FK
    }
    integration_runs {
        uuid id PK "not null"
        uuid connection_id FK "not null"
        varchar trigger_type "not null"
        varchar status "not null"
        timestamp started_at
        timestamp finished_at
        integer imported_count "not null"
        integer existing_count "not null"
        integer unmapped_count "not null"
        integer duplicate_count "not null"
        text error
        timestamp created_at "not null"
        jsonb next_cursors
        numeric fiat_deposited
        jsonb balances
    }
    users ||--o{ integration_connections : "user_id"
    integration_connections ||--o| integration_credentials : "connection_id"
    assets ||--o{ integration_crypto_exclusions : "asset_id"
    integration_connections ||--o{ integration_crypto_exclusions : "connection_id"
    integration_connections ||--o{ integration_cursors : "connection_id"
    transactions |o--o{ integration_run_items : "transaction_id"
    transactions |o--o{ integration_run_items : "duplicate_of"
    integration_runs ||--o{ integration_run_items : "run_id"
    integration_connections ||--o{ integration_runs : "connection_id"
    integration_connections |o--o{ transactions : "connection_id"
```

## 5a. Índices de mercado

Tabelas: `market_indices`, `market_index_values` (V43, 05/10/2026). Sem ligação com outro domínio: a nota de renda fixa
acha a série pelo nome (`fixed_income_notes.indexer` = `market_indices.code`), sem FK.

```mermaid
erDiagram
    market_indices {
        bigint id PK "not null"
        varchar code UK "not null"
        varchar name "not null"
        varchar periodicity "not null DIARIA|MENSAL"
        varchar unit "not null PCT_DIA|PCT_MES|PCT_PERIODO|PCT_ANO|PONTOS|BRL"
        varchar source "not null BCB_SGS|B3"
        varchar source_ref "not null"
        date history_from "not null"
        boolean active "not null"
    }
    market_index_values {
        bigint index_id PK,FK "not null"
        date reference_date PK "not null"
        numeric value "not null (18,8)"
    }
    market_indices ||--o{ market_index_values : "index_id"
```

## 5b. Notificações

Tabelas: `notifications`, `notification_states` (V48, 13/10/2026 — `docs/planos/PLANO_NOTIFICACOES.md`). Dois eixos
independentes: o **status** do assunto mora na mensagem (nulo = informativa; `NAO_RESOLVIDA`/`DISPENSADA`/`RESOLVIDA`), o
**estado** de cada pessoa (lida, arquivada, excluída — exclusão lógica) mora em `notification_states` (sem linha = não
lida, na Entrada). Mensagem de administrador (`audience = ADMIN`) não tem dono: todos os admins a veem, cada um com o
seu estado. `dedup_key` (prefixada pelo `kind`) barra a repetição no banco: informativa nunca se repete; pendência não
se repete enquanto não for resolvida (índices únicos parciais).

```mermaid
erDiagram
    notifications {
        uuid id PK "not null"
        varchar audience "not null USER|ADMIN"
        uuid user_id FK "obrigatorio em USER"
        varchar kind "not null"
        varchar category "not null"
        varchar severity "not null INFO|ATENCAO"
        varchar title "not null"
        text body "not null"
        varchar ref_type
        varchar ref_id
        jsonb payload "lines, actions, data"
        varchar status "nulo|NAO_RESOLVIDA|DISPENSADA|RESOLVIDA"
        timestamp status_at
        uuid status_by
        varchar dedup_key
        timestamp created_at "not null"
    }
    notification_states {
        uuid notification_id PK,FK "not null"
        uuid user_id PK,FK "not null"
        timestamp read_at
        timestamp archived_at
        timestamp deleted_at
    }
    stock_event_costs {
        uuid portfolio_asset_id PK,FK "not null"
        bigint stock_event_id PK,FK "not null"
        numeric cost_per_share "not null (18,8)"
    }
    users ||--o{ notifications : "user_id"
    notifications ||--o{ notification_states : "notification_id"
    users ||--o{ notification_states : "user_id"
    portfolio_assets ||--o{ stock_event_costs : "portfolio_asset_id"
    asset_stock_events ||--o{ stock_event_costs : "stock_event_id"
```

## 6. Descrição das tabelas

| Domínio | Tabela | Quem grava | Descrição | Unicidade |
|---|---|---|---|---|
| Usuário | `ceiling_prices` | app-api | Cálculo de preço teto (modelo PREÇO TETO V2, V46) de uma ação pelo usuário: só as entradas, todas obrigatórias — N (shares), ROE, payout, desconto, perpetuidade, margem de segurança (pontos) e preço — com a origem de cada uma (inputs_origin, JSONB: PADRAO/INFORMADO + referência do padrão). O resultado não é gravado: sai das entradas. Um por (user_id, asset_id). | (user_id, asset_id) |
| Usuário | `ceiling_price_quarters` | app-api | Os 4 lucros de um cálculo de preço teto (V46): valor digitado, unidade (UNIDADE/MIL/MILHAO/BILHAO), moeda (BRL/USD), câmbio, valor em reais (value_brl, o que entra no cálculo), origem (PADRAO = CVM sem alteração, INFORMADO). A fonte (source_ref) saiu na V47. | (ceiling_price_id, position) |
| Notificações | `notifications` | app-api e adm-api (as de administrador, D1) | Mensagem da caixa (V48): público, tipo, categoria, gravidade, texto, referência, payload (linhas e ações) e o status do assunto. Repetição barrada pela `dedup_key`. | (audience, user_id, dedup_key) parcial |
| Notificações | `notification_states` | app-api | Estado de uma mensagem para uma pessoa: lida, arquivada, excluída (lógica). | (notification_id, user_id) |
| Usuário | `stock_event_costs` | app-api | Custo por ação de uma bonificação que a B3 não informou, digitado pelo usuário para a posição dele (V51, "Informar custo"). | (portfolio_asset_id, stock_event_id) |
| Usuário | `dividends` | app-api | Proventos da posição, gerados a partir do catálogo (asset_cash_events) × cotas na data-com. Bruto (total_amount), IR retido pela regra vigente e líquido (net_amount). Sem user_id: o dono vem da posição. | — |
| Usuário | `fixed_income_notes` | app-api | O título de renda fixa do usuário (CDB, Tesouro...): emissor, vencimento, forma de remuneração, indexador e taxa contratada. Copia de asset_terms no lançamento. | — |
| Usuário | `goals` | app-api | Metas de investimento: valor alvo, valor atual, data e status. | — |
| Usuário | `portfolio_assets` | app-api | A posição: um ativo (e, na renda fixa, um título) dentro da carteira. Não guarda cotas, preço médio nem custo — saem das transações. Guarda a reserva de emergência (is_reservation), se está liquidada e a valorização. Única por (portfolio_id, asset_id, note_id) com NULLS NOT DISTINCT. | (portfolio_id, asset_id, note_id) |
| Usuário | `portfolios` | app-api | Carteira do usuário (uma por usuário). | — |
| Usuário | `strategies` | app-api | Estratégia de geração de renda: % ideal por tipo de ativo; única por (user_id, asset_type_id). Base do rebalanceamento. | (user_id, asset_type_id) |
| Usuário | `strategy_reservations` | app-api | Estratégia da reserva de emergência, uma por usuário: por percentual (type 1) ou por valor (type 2). | (user_id) |
| Usuário | `transactions` | app-api | Compras e vendas — a fonte da verdade da posição. origin MANUAL ou AUTO (importada de integração, com external_id, source_snapshot e edited_at). note_id só na renda fixa; sale_group_id agrupa a venda FIFO do Tesouro. | — |
| Usuário | `users` | seed V9 (dev) / app-api | Usuários do sistema: login por senha (bcrypt em password_hash) ou Google (google_id); is_admin libera as rotinas de administrador. | (email); (google_id) |
| Catálogo | `asset_cash_events` | adm-api | Proventos do catálogo (dividendo, JCP, rendimento, amortização...) por ativo: data-com, pagamento, valor por cota, parcela (seq). source B3, B3_HISTORICO ou FNET; payment_date_source diz de onde veio a data (nula = da fonte, CVM ou LEVANTAMENTO). | (asset_id, type, data_com, value_per_share, seq) |
| Catálogo | `asset_categories` | seeds | Categoria dentro do tipo (um nível): setor B3 da ação, tipo de FII (Tijolo, Papel...), etc. Única por (asset_type_id, name). | (asset_type_id, name) |
| Catálogo | `asset_price_history` | adm-api | Cotações diárias (OHLCV) por ativo, uma por (asset_id, reference_date). source: B3_COTAHIST (série oficial, vence), BRAPI ou COINGECKO. | (asset_id, reference_date) |
| Catálogo | `asset_stock_events` | adm-api | Eventos societários do catálogo: desdobramento, grupamento e bonificação, com fator e custo atribuído. Ajustam cotas e preço médio da posição. | (asset_id, type, data_com, factor) |
| Catálogo | `asset_terms` | seed V8 | Parâmetros públicos do papel, 1:1 com assets — hoje só Tesouro Direto (vencimento, forma de remuneração, indexador). | — |
| Catálogo | `asset_types` | seed V2 | Os 12 tipos de ativo (Ações, FIIs, Renda Fixa, Tesouro, ETF, BDR, Cripto...), com a fonte de preço (price_source) e se é renda fixa (fixed_income). | (name) |
| Catálogo | `assets` | seeds / adm-api | Catálogo global de instrumentos (sem dono): ticker, nome, tipo, categoria, preço atual, ISIN e a ligação com o emissor (issuer_id, share_class, issuer_link_source). | (ticker) |
| Catálogo | `withholding_tax_rules` | seed V23 | Alíquotas de IR retido na fonte por tipo de provento, tipo de ativo e vigência, com a base legal. Lei nova = migration. | — |
| Emissores | `company_quarters` | adm-api (derivada) | Série trimestral derivada por (issuer_id, period_end): trimestre isolado (_q), 12 meses (_ttm), posição do balanço e ações. Recalculada inteira ao fim de cada carga de balanços. Base dos indicadores. dividends_paid_q/_ttm (V45): dividendos + JCP pagos aos acionistas da controladora (DFC), base do payout desde 10/10/2026. V7 (24/10/2026): lucro bruto, D&A (DVA menos a exaustão biológica), LAIR e IR/CSLL (_q/_ttm) e ativo/passivo circulante — base dos indicadores novos da tela de Ativos. | — |
| Emissores | `cvm_documents` | adm-api | Cada DFP ou ITR entregue à CVM por emissor e data de referência; version (reapresentação substitui) e se é consolidado. | (issuer_id, doc_type, reference_date) |
| Emissores | `cvm_statement_values` | adm-api | Métricas extraídas de cada documento (receita, EBIT, lucro, PL, caixa, dívida, FCO, dividendos declarados, LPA...), em reais, por período. | (document_id, metric, period_start, period_end) |
| Emissores | `fii_monthly` | adm-api | Informe mensal de FII da CVM por (issuer_id, reference_month): PL, cotas, VP por cota, cotistas, taxas, DY, amortização, carteira e passivo. | — |
| Emissores | `issuers` | seed V30 / adm-api | Emissores: companhia aberta ou FII, um por CNPJ. Código CVM, setores CVM e B3, segmento de listagem, administrador do FII e o segmento curado do FII. | (cnpj); (cvm_code) |
| Emissores | `share_counts` | adm-api | Quantidade de ações (e em tesouraria) de cada documento, com a escala conferida (scale_factor, scale_status, scale_check). | — |
| Emissores | `unit_compositions` | seed V30 / adm-api | Composição das units: quantas ações de cada classe (ON, PN...) formam uma unit. | — |
| Índices | `market_indices` | seed V43 | Cadastro dos índices de mercado: código (o mesmo do enum Indexer para CDI, SELIC, IPCA, IGPM, INPC, TR; mais SELIC_META, USD_PTAX, IBOV, IFIX), periodicidade, unidade do valor publicado e a fonte (série do SGS ou sigla da B3). | (code); (source, source_ref) |
| Índices | `market_index_values` | adm-api | Série de cada índice, um valor por data, como a fonte publica (mensal no dia 1º). O acumulado não é gravado: sai do MarketIndexCalculator. | (index_id, reference_date) |
| Integrações | `integration_connections` | app-api | Conexão do usuário com uma corretora (hoje BINANCE): apelido único por usuário, status, aceite do consentimento, última sincronização e erro. | (user_id, label) |
| Integrações | `integration_credentials` | app-api | Credencial cifrada (AES-256-GCM) da conexão, 1:1; nunca sai na API e é apagada na revogação. | — |
| Integrações | `integration_crypto_exclusions` | app-api | Operação importada e excluída pelo usuário: a sincronização não a recria (pode ser restaurada). | (connection_id, external_id) |
| Integrações | `integration_cursors` | app-api | Onde cada fluxo (stream) da corretora parou — sincronização incremental. | — |
| Integrações | `integration_run_items` | app-api | Cada operação trazida numa execução: resultado (nova, existente, não mapeada, possível duplicata), decisão do usuário e a transação gerada. | (run_id, external_id) |
| Integrações | `integration_runs` | app-api | Cada sincronização (manual ou agendada): é também a fila do worker (QUEUED, RUNNING, AWAITING_CONFIRMATION...), com contagens, depósitos e saldos da corretora. | — |

## 7. Regras de exclusão (ON DELETE)

| Tabela | Coluna | Referencia | Ao excluir o pai |
|---|---|---|---|
| `asset_cash_events` | `asset_id` | `assets.id` | bloqueia (NO ACTION) |
| `asset_categories` | `asset_type_id` | `asset_types.id` | bloqueia (NO ACTION) |
| `asset_price_history` | `asset_id` | `assets.id` | bloqueia (NO ACTION) |
| `asset_stock_events` | `asset_id` | `assets.id` | bloqueia (NO ACTION) |
| `asset_terms` | `asset_id` | `assets.id` | CASCADE |
| `assets` | `asset_category_id` | `asset_categories.id` | bloqueia (NO ACTION) |
| `assets` | `issuer_id` | `issuers.id` | bloqueia (NO ACTION) |
| `assets` | `asset_type_id` | `asset_types.id` | bloqueia (NO ACTION) |
| `ceiling_prices` | `asset_id` | `assets.id` | bloqueia (NO ACTION) |
| `ceiling_prices` | `user_id` | `users.id` | CASCADE |
| `ceiling_price_quarters` | `ceiling_price_id` | `ceiling_prices.id` | CASCADE |
| `company_quarters` | `source_doc_id` | `cvm_documents.id` | CASCADE |
| `company_quarters` | `issuer_id` | `issuers.id` | bloqueia (NO ACTION) |
| `cvm_documents` | `issuer_id` | `issuers.id` | bloqueia (NO ACTION) |
| `cvm_statement_values` | `document_id` | `cvm_documents.id` | CASCADE |
| `dividends` | `withholding_rule_id` | `withholding_tax_rules.id` | bloqueia (NO ACTION) |
| `dividends` | `cash_event_id` | `asset_cash_events.id` | bloqueia (NO ACTION) |
| `dividends` | `portfolio_asset_id` | `portfolio_assets.id` | CASCADE |
| `fii_monthly` | `issuer_id` | `issuers.id` | bloqueia (NO ACTION) |
| `fixed_income_notes` | `asset_id` | `assets.id` | bloqueia (NO ACTION) |
| `fixed_income_notes` | `user_id` | `users.id` | CASCADE |
| `goals` | `user_id` | `users.id` | CASCADE |
| `integration_connections` | `user_id` | `users.id` | CASCADE |
| `integration_credentials` | `connection_id` | `integration_connections.id` | CASCADE |
| `integration_crypto_exclusions` | `asset_id` | `assets.id` | bloqueia (NO ACTION) |
| `integration_crypto_exclusions` | `connection_id` | `integration_connections.id` | CASCADE |
| `integration_cursors` | `connection_id` | `integration_connections.id` | CASCADE |
| `integration_run_items` | `transaction_id` | `transactions.id` | SET NULL |
| `integration_run_items` | `duplicate_of` | `transactions.id` | SET NULL |
| `integration_run_items` | `run_id` | `integration_runs.id` | CASCADE |
| `integration_runs` | `connection_id` | `integration_connections.id` | CASCADE |
| `market_index_values` | `index_id` | `market_indices.id` | bloqueia (NO ACTION) |
| `notifications` | `user_id` | `users.id` | CASCADE |
| `notification_states` | `notification_id` | `notifications.id` | CASCADE |
| `notification_states` | `user_id` | `users.id` | CASCADE |
| `stock_event_costs` | `portfolio_asset_id` | `portfolio_assets.id` | CASCADE |
| `stock_event_costs` | `stock_event_id` | `asset_stock_events.id` | CASCADE |
| `portfolio_assets` | `asset_id` | `assets.id` | bloqueia (NO ACTION) |
| `portfolio_assets` | `note_id` | `fixed_income_notes.id` | bloqueia (NO ACTION) |
| `portfolio_assets` | `portfolio_id` | `portfolios.id` | CASCADE |
| `portfolios` | `user_id` | `users.id` | CASCADE |
| `share_counts` | `document_id` | `cvm_documents.id` | CASCADE |
| `strategies` | `asset_type_id` | `asset_types.id` | bloqueia (NO ACTION) |
| `strategies` | `user_id` | `users.id` | CASCADE |
| `strategy_reservations` | `user_id` | `users.id` | CASCADE |
| `transactions` | `portfolio_asset_id` | `portfolio_assets.id` | CASCADE |
| `transactions` | `note_id` | `fixed_income_notes.id` | bloqueia (NO ACTION) |
| `transactions` | `connection_id` | `integration_connections.id` | SET NULL |
| `unit_compositions` | `asset_id` | `assets.id` | CASCADE |
| `withholding_tax_rules` | `asset_type_id` | `asset_types.id` | bloqueia (NO ACTION) |

> Apagar um usuário leva junto (CASCADE) carteira, posições, transações, títulos, proventos, estratégias, metas, preços teto e conexões. Nada do catálogo depende de usuário.
