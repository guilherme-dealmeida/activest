# Activest — Plano da alteração da tela de Ativos

> Fase: **Encerrado em 24/10/2026** — as 7 etapas feitas; commit e push pelo Guilherme numa branch própria do TRACK_01; escopo declarado finalizado por ele (`docs/REGRAS_CLAUDE.md`,
> 2.1); nada de código até a aprovação explícita.
> Origem: pedido de 18/10/2026 (ESCOPO "Alteração da tela de Ativos (antiga Emissoras)", então no formato antigo, ETAPA
> Planejamento), com os anexos 1 a 4 (Demonstrativos, Indicadores, modal do P/L e o quadro "Informações sobre a empresa"
> do Investidor10). Retomado em 23/10/2026 (ESCOPO "Plano da alteração da tela de Ativos Activest") a partir do
> Discovery, porque dois escopos encerrados depois dele mudaram o projeto: `docs/planos/PLANO_NOMES_EM_INGLES.md`
> (22/10) e `docs/planos/PLANO_RENOMEACAO_ACTIVEST.md` (23/10).
> Consome a tela de Ativos (`docs/planos/PLANO_TELA_EMISSORES.md`), os balanços e indicadores
> (`docs/planos/PLANO_EMPRESAS_BALANCOS.md`, F9) e a seção 31 de `docs/PENDENCIAS.md` ("Novos indicadores de ações").
>
> Seções: D (Discovery), I (Impact) e 2 (Planning), de 23/10/2026; 0 e 1 são o resumo e o levantamento de 18/10, mantidos
> como registro e reconferidos na D.

---

## D. Discovery (23/10/2026) — reconferência no Activest

Conferido contra `E:\repos\activest` (branch `TRACK-01_novo_nome_activest`) e o banco `db_activest` da pilha
`activest` no ar.

### D.1 O que mudou no projeto desde 18/10

| Evento | Efeito neste escopo |
|---|---|
| Nomes em inglês (22/10, `REGRAS_CLAUDE.md` seção 9) | todo nome novo do plano (classe, teste, rota, parâmetro, chave JSON, chave de configuração, coluna, migration) nasce em inglês; valores gravados e valores de enum que trafegam na API seguem a 9.2 |
| Renomeação Activest (23/10) | módulos `activest-*`, pacote `com.activest`, chaves `activest.*`; migrations consolidadas **V1–V6** (a próxima é a **V7**, não a V60); banco novo recarregado do zero; `docs/PRIMEVEST_DB.md` virou `docs/ACTIVEST_DB.md` |
| Tarefas (22/10, `PLANO_TAREFAS.md`) | o reprocessamento dos balanços (`POST /api/v1/admin/cvm/import`) agora dispara a tarefa `CVM_STATEMENTS_IMPORT` e aparece na tela de Tarefas |
| Nova metodologia (23/10, `REGRAS_CLAUDE.md` 1–2) | o plano passa a registrar Discovery, Impact e Planning; as decisões continuam numeradas para o Guilherme responder |

### D.2 Referências do plano de 18/10 corrigidas

| No plano de 18/10 | No Activest hoje |
|---|---|
| `primevest-web/src/pages/IssuerPage.tsx`, `components/issuer/*` | `activest-web/src/pages/IssuerPage.tsx`, `activest-web/src/components/issuer/*` (mesmos nomes: `IssuerRegistry`, `FinancialStatements`, `IndicatorGrid`, `Sparkline`, `IndicatorHistoryModal`; `Toggle` ainda local em `FinancialStatements.tsx`) |
| rota `/ativos/{ticker}` | `/assets/{ticker}` (`/ativos` e `/emissoras` redirecionam) |
| `IndicatorService`, `IndicatorRules` (com `CompanyBasis`), `Indicator`, `IndicatorHistoryDTO` | iguais, em `activest-app-api/.../com/activest/domain/indicator` |
| `CvmStatements.wanted`, `FinancialMetric`, `FinancialMetricRules`, `QuarterSeries`, `CompanyQuarterService` | iguais, em `activest-adm-api/.../com/activest/adm/...`; BPA continua só com `1`, `1.01.01`, `1.01.02` |
| Migration `V60__company_quarters_contas_novas.sql` | `V7__...` com nome em inglês (ex.: `V7__company_quarters_new_accounts.sql`) |
| `IndicadoresIT`, `DadosEmpresaIT`, `DemonstrativosAnuaisIT` | `IndicatorsIT` (existe); os novos nascem em inglês (ex.: `CompanyDataIT`, `AnnualStatementsIT`); `IssuerPageIT` e `IssuerListingIT` também tocam a tela |
| janela da liquidez "no `application.yml`" | chave em inglês junto das de hoje (`activest.issuer-page.stale-data.*`), ex.: `activest.issuer-page.liquidity-days` |
| `docs/PRIMEVEST_DB.md` | `docs/ACTIVEST_DB.md` |
| `POST /api/v1/admin/cvm/import?from=2010&to=2026&force=true` | igual, agora como tarefa `CVM_STATEMENTS_IMPORT` |

Endpoints novos do plano já estavam em inglês (`/api/v1/assets/{ticker}/company-data`, `/api/v1/issuers/{id}/years`) e
os de hoje continuam (`/api/v1/assets/{ticker}/indicators[/history]`, `/api/v1/issuers/{id}/quarters`, `/months`).

### D.3 Base recarregada — os números de 18/10 continuam valendo

| Conferência | 18/10 (PrimeVest) | 23/10 (Activest) |
|---|---|---|
| `company_quarters` | 19.346 linhas, 31/12/2010 a 31/08/2026 | **igual** (369 emissores) |
| `share_counts` | — | 18.778 (igual ao PrimeVest, conferência da renomeação) |
| Layout | 348 comerciais, 19 bancos, 2 seguradoras | **igual** |
| Exercício fora de dezembro | 7 | **7** |
| PETR4, balanço de 30/06/2026 | PL 480,95 bi · ativos 1.279,02 bi · caixa 53,76 bi · dívida 366,53 bi · 12.888.732.761 ações | **iguais** |
| Preço usado na 1.2 | PETR3 66,49 / PETR4 59,68 (16/10) | hoje PETR3 65,24 / PETR4 58,68 (23/10): o valor de mercado da 1.2 é refeito no Testing |
| TIMS3 com DFP 2025 zerado (PENDENCIAS 32.2) | sim | **continua** (2025-12-31 com lucro, PL e receita 0) — a recarga não resolveu |
| Contas da CVM gravadas (`cvm_statement_values.metric`) | 15 métricas | iguais; as 6 novas (seção 2.5) ainda não existem |
| Arquivos da CVM para o reprocessamento | `carga/data/cvm` | presentes (1,6 GB: dfp, itr, fre, fii...) — o reprocessamento segue sem download |

### D.4 O que não mudou

- O pedido (T1–T5) e o comportamento atual da tela (1.1): Demonstrativos com Trimestre \| 12 meses e Últimos 8 \|
  Todos, valores abreviados com o exato no hover; card Cadastro com os mesmos campos; 7 cartões de ações e 7 de FII;
  média = pontos anuais + Atual, sempre desde 2010 (FII desde 2017).
- As decisões D1–D21 **seguem sem resposta**; nenhuma foi invalidada pelos dois escopos. Elas voltam no Planning,
  já com os nomes corrigidos.

### D.5 Pontos novos levantados (vão para o Impact/Planning)

| # | Ponto | Por quê |
|---|---|---|
| N1 | Valores das 6 métricas novas da CVM (`LUCRO_BRUTO`, `DEPRECIACAO_AMORTIZACAO`, `LAIR`, `IR_CSLL`, `ATIVO_CIRCULANTE`, `PASSIVO_CIRCULANTE`) | são **valor gravado** (`cvm_statement_values.metric`, regra 9.2): podem seguir o português das 15 de hoje (`RECEITA`, `CAIXA`...) ou nascer em inglês. A classe `FinancialMetric` já é inglês |
| N2 | Códigos dos 23 indicadores novos (`Indicator`) | trafegam na API (valor de enum, 9.2); os de hoje são siglas/português (`LPA`, `VP_COTA`, `COTISTAS`) |
| N3 | Valores da janela 3A \| 5A \| MAX na API e as chaves de `averages` | valor de enum que trafega (como `1A`/`5A` dos períodos de cotação) × chave JSON, que é nome (9.1). O plano de 18/10 propunha `{ Y3, Y5, MAX }` |
| N4 | Colunas novas de `company_quarters` | nomes em inglês (ex.: `gross_profit_q/_ttm`, `ebitda_q/_ttm`, `current_assets`, `current_liabilities`); a V7 nasce depois da consolidação, então a coluna vai para o fim da tabela (a convenção de ordem do `V1__schema.sql` não se aplica a `ALTER`) |
| N5 | Regra 2.6 (preço atual × fechamento) | indicadores e valor de mercado usam o **preço atual**; a liquidez média (D4) usa quantidade × fechamento **de cada pregão passado**, dependência explícita do histórico — registrar assim no Planning |

**Respostas do Guilherme (23/10/2026):**

| # | Resolução |
|---|---|
| N1 | **Português**, como as 15 de hoje: é valor gravado, e a própria CVM descreve as contas em português (`DS_CONTA`: "Lucro Bruto", "Ativo Circulante", "Imposto de Renda e Contribuição Social sobre o Lucro") |
| N2 | `Indicator` **não é gravado** (calculado na leitura); o código trafega na API (rota `/indicators/{indicator}/history`, JSON) e nas colunas/filtros da URL da listagem. Pela 9.2 é valor de enum que trafega: **fica no padrão de hoje, sem migração**. Os 23 novos seguem esse padrão (sigla do mercado, ou português quando não houver sigla) — a lista de códigos entra no Planning |
| N3 | **Mesmo padrão dos períodos de Índices, cotação e Rentabilidade**: constante Java `Y3`/`Y5`/`MAX` e valor na API `3A`/`5A`/`MAX` (`PriceSeries.Range`, `PerformanceService.Range`). O formato das médias no JSON é detalhado no Planning |
| N4 | De acordo: colunas no fim da tabela; a disposição pode ser revista depois da implementação |
| N5 | De acordo: **preço atual**; a liquidez média é a exceção explícita (quantidade × fechamento de cada pregão passado) |

---

## I. Impact (23/10/2026) — alcance, riscos e dependências

Medido contra o código e a pilha `activest` no ar (tempos com a base de 23/10, app-api aquecida salvo indicação).

### I.1 Componentes afetados

| Camada | Componente | Mudança | Quem mais usa |
|---|---|---|---|
| Banco (core) | `company_quarters` + entidade `CompanyQuarter` | V7 com as colunas novas; entidade junto (`ddl-auto: validate` derruba as duas APIs se só um lado mudar) | `IndicatorService`, `IssuerController`, `CeilingPriceDefaults` (Preço Teto), `carga/scripts/sql/contagens.sql` |
| Banco (dado) | `cvm_statement_values.metric` | 6 valores novos (N1); sem CHECK na coluna (`VARCHAR(40)`), nenhuma migration de constraint | só a adm-api grava |
| adm-api | `CvmStatements.wanted`, `FinancialMetric`, `FinancialMetricRules`, `QuarterSeries`, `CompanyQuarterService` | contas 3.03, 3.07, 3.08, 7.04.01, 1.01, 2.01; fluxos `_q`/`_ttm`, posições | tarefas `CVM_STATEMENTS_IMPORT` (diária/bootstrap) e `CVM_QUARTERS` |
| app-api | `Indicator`, `IndicatorRules`/`CompanyBasis`, `IndicatorService` (`Context`, `currentOf`, `currentAll`, `history*`) | 23 códigos, fórmulas, preço das outras classes, médias por janela | **`IssuerListingService`** (lista inteira por `currentAll`) e **`CeilingPriceDefaults`** (ROE e payout do Preço Teto saem do `IndicatorService`) |
| app-api | `IndicatorController`, `IssuerController`, `IndicatorHistoryDTO`, `IssuerDTOs` | parâmetro de janela (`3A`/`5A`/`MAX`, N3), `averages`, `GET /assets/{ticker}/company-data`, `GET /issuers/{id}/years`, contas novas em `quarters` | front (Ativos e listagem) |
| Configuração | `application.yml` da app-api | janela da liquidez em `activest.issuer-page.*` (nome em inglês) | — |
| Web | `IssuerPage`, `IssuerRegistry` → `CompanyData`, `FinancialStatements`, `IndicatorGrid`, `IndicatorHistoryModal`, `Sparkline`, `utils/indicators.ts`, `types/index.ts`, `api/indicators.ts`, `api/issuers.ts`; `Toggle` → `ui/` | tela | **`utils/issuerListing.ts`** usa `IndicatorCode` e `INDICATOR_META` (`Record<IndicatorCode, …>` exaustivo: o `tsc` obriga os metadados dos 23 — proteção, não risco) |
| Testes | `FinancialMetricRulesTest`, `QuarterSeriesTest`, `FinancialStatementImportIT` (adm); `IndicatorRulesTest`, `IndicatorsIT`, `IssuerPageIT`, `IssuerListingIT`, `CeilingPriceIT` (app); `IntegrationTestBase` (core, cria `company_quarters` de teste) | novos casos + regressão | — |
| Documentação | `Claude.md` (Ativos em Telas, endpoints Assets/Issuers, F9), `docs/ACTIVEST_DB.md` (`company_quarters`), `docs/ADM_TASKS.md`, `docs/PENDENCIAS.md` (31, 32.3), `README.md` | — | — |

Sem efeito: Redis (o `BALANCOS` do "catálogo mudou" já é publicado ao fim da carga de balanços), integrações de
corretora, bucket R2 e `carga/data` (nenhum arquivo novo: as contas estão nos zips da CVM já baixados), notificações,
rotas do front.

### I.2 Números medidos

| Medida | Hoje (7 indicadores de ação) | Estimativa com 30 |
|---|---|---|
| Listagem de ações `GET /assets/indicators?kind=COMPANY` | 2,1 s a frio · 0,15–0,35 s aquecida · **301 KB** | o DTO da linha leva **todos** os indicadores do `Indicator.values()` → ~1,1 MB e mais cálculo por linha |
| Histórico de PETR4 (todas as sparklines) | 18–56 ms · 7,7 KB | ~33 KB; ITUB4 (banco) 632 ms a frio |
| Atual de PETR4 `GET /assets/PETR4/indicators` | 1,0 s a frio | + preço das outras classes |
| Reprocessamento `cvm/import force=true` | ~7 min (18/10) | igual + 6 contas |
| Dado de usuário hoje no Activest | 0 posições, 0 preços teto, 8 notificações | — |
| Ativos de ação ativos por classe | ON 559, PN 110, PNA 66, PNB 15, PNC 4, UNIT 18, sem classe 13 | base do valor de mercado (D3) |

### I.3 Riscos

| # | Risco | Efeito | Mitigação |
|---|---|---|---|
| R1 | **Reprocessamento com `force` refaz as quantidades de ações** — apaga `cvm_statement_values` e `share_counts` de cada documento e reroda a conferência de escala (`ShareScale`: FRE, LPA, vizinho, evento) | se a conferência der outro resultado, LPA, VPA, P/L, P/VP e o Preço Teto mudam junto | mesmos arquivos de entrada → resultado esperado igual; retrato de `share_counts` e `company_quarters` antes e comparação depois (só colunas novas podem mudar) |
| R2 | **Listagem carrega os 30 indicadores** sem mostrar os novos (D21 fora) | payload ~4× e tempo da aba Ações maior | o lote da listagem calcular/devolver só os indicadores das colunas dela (decisão nova no Planning) |
| R3 | **Preço Teto depende do `IndicatorService`** (ROE e payout padrão) | mexer em `Context`/`currentOf` pode mudar os padrões do Preço Teto | `CeilingPriceIT` na bateria; ROE e payout não mudam de fórmula |
| R4 | Entidade × migration (`ddl-auto: validate`) | API não sobe | V7 e `CompanyQuarter` no mesmo passo; `SchemaTest`/subida no Testing |
| R5 | Colunas novas nulas até o reprocessamento | indicadores novos vazios ("sem dado") entre o deploy e a carga | A1 fecha com o reprocessamento antes das etapas de tela; num ambiente novo o `bootstrap` já preenche |
| R6 | Valor de mercado com classe sem cotação / séries PN (D3) | valor estimado | nota no hover (D3); conferência PETR4/VALE3/unit no Testing |
| R7 | Balanços zerados (TIMS3, PENDENCIAS 32.2, **continua** no Activest) | indicadores novos herdam o erro | escopo próprio; fora daqui |
| R8 | Bancos e seguradoras (19 + 2) sem contas comerciais | cartões sem sentido | D17 |
| R9 | `Claude.md` com trechos anteriores aos nomes em inglês na descrição da tela Ativos (`?periodo=`, `acoes.ord`...) | documentação desatualizada | corrigir na etapa de documentação deste escopo |

### I.4 Dependências e ordem

1. Banco + adm-api (V7, contas novas) → reprocessamento → conferência das contas (R1).
2. app-api: indicadores (depende de 1) e `company-data`/`years` (depende de 1) — independentes entre si.
3. Web: Demonstrativos e Dados da empresa (dependem de `years`/`company-data`); Indicadores (depende dos indicadores).
4. As duas APIs sobem juntas com o core novo (entidade compartilhada).

Comportamento existente que **não muda**: P/L, P/VP, DY, ROE, Payout, LPA, VPA e os 7 do FII (fórmulas iguais); média
MAX = `companyAverage` de hoje; Trimestral do Demonstrativo; listagem (colunas e filtros).

---

## 0. Resumo (18/10/2026)

A página de um ativo (`/assets/{ticker}`; em 18/10, `/ativos/{ticker}`) muda em quatro frentes:

1. **Indicadores novos de ações** (seção 31 de `docs/PENDENCIAS.md`): 14 indicadores calculados com o que já está na
   base (31.2) e 9 que pedem contas novas dos arquivos da CVM já baixados (31.3), todos com histórico desde 2010,
   sparkline e modal, como os de hoje. Bancos e seguradoras ficam "não se aplica" onde a conta não existe (31.4).
2. **Card "Dados da empresa"** (antigo "Cadastro"): mantém os campos de hoje e ganha os valores do anexo 4 que a base
   já tem — valor de mercado, valor de firma, **patrimônio líquido** (o A3 da listagem, 31.1), nº de papéis, ativos,
   ativo circulante, dívidas, disponibilidade e liquidez média diária. Free float e tag along ficam de fora (não estão
   na base).
3. **Demonstrativos:** "12 meses" vira **Anual** (colunas 2026, 2025, 2024...) e entra o seletor **Valor simples**
   (padrão) | **Valor detalhado** (o número inteiro, com todas as casas).
4. **Indicadores:** seletor **3A | 5A (padrão) | MAX** em cada cartão e no modal; sparkline, tabela e **média**
   acompanham a janela escolhida.

Nada novo é buscado fora: tudo sai de `company_quarters`, `share_counts`, `fii_monthly`, `asset_price_history`,
`issuers` e dos arquivos da CVM que já estão em `carga/data/cvm`.

---

## 1. Levantamento (18/10/2026, reconferido na seção D)

### 1.1 O que a tela tem hoje

| Peça | Onde | Observação |
|---|---|---|
| Página | `primevest-web/src/pages/IssuerPage.tsx` | cabeçalho, cotação, indicadores, cadastro, demonstrativos |
| Cadastro | `components/issuer/IssuerRegistry.tsx` | CNPJ, código CVM, situação, site, setor B3, segmento (FII), segmento de listagem, fim do exercício, administrador (FII) |
| Demonstrativos | `components/issuer/FinancialStatements.tsx` | Trimestre \| 12 meses, Últimos 8 \| Todos; valores abreviados (`formatCompactCurrency`) com o exato no hover |
| Indicadores | `components/issuer/IndicatorGrid.tsx`, `Sparkline.tsx`, `IndicatorHistoryModal.tsx` | ações: P/L, P/VP, DY, ROE, Payout, LPA, VPA; FII: P/VP, DY, VP/cota, Cotistas, % Imóveis, % Papel, Alavancagem |
| Cálculo | `IndicatorService` + `IndicatorRules` (app-api) | nada gravado; média = pontos anuais + Atual (`IndicatorRules.average`), sempre desde 2010 (FII desde 2017) |
| Série trimestral | `company_quarters` (adm-api, `CompanyQuarterService`) | trimestre isolado, 12 meses e posição; 19.346 linhas, 31/12/2010 a 31/08/2026 |
| Contas lidas da CVM | `FinancialMetricRules` + `CvmStatements.wanted` (adm-api) | RECEITA, EBIT, lucros, FCO, dividendos/JCP, PL, ativo total, caixa, dívida bruta, LPA. Do BPA só entram `1`, `1.01.01` e `1.01.02` |
| Quantidade por classe | `share_counts` (ON, PN, tesouraria por documento) | base do valor de mercado por classe |
| Volume diário | `asset_price_history.volume` | é **quantidade de títulos** (QUATOT do COTAHIST; BRAPI também em quantidade), não volume financeiro |

### 1.2 Conferência com o anexo 4 (PETR4, base de 18/10/2026)

| Item do Investidor10 | Investidor10 | PrimeVest (base) | Como |
|---|---|---|---|
| Valor de mercado | R$ 819,88 bi | **R$ 819,89 bi** | 7.442.231.382 ON × 66,49 (PETR3) + 5.446.501.379 PN × 59,68 (PETR4), fechamento de 16/10 |
| Valor de firma | R$ 1,13 tri | **R$ 1.132,66 bi** | valor de mercado + dívida bruta − caixa |
| Patrimônio líquido | R$ 480,94 bi | R$ 480,95 bi | `equity_parent` de 30/06/2026 |
| Nº total de papéis | 12,89 bi | 12,89 bi | `shares_total` |
| Ativos | R$ 1,28 tri | R$ 1.279,02 bi | `total_assets` |
| Ativo circulante | R$ 154,97 bi | — | **não está na base**: BPA 1.01 entra com a 31.3 |
| Dívida bruta | R$ 366,53 bi | R$ 366,53 bi | `gross_debt` |
| Dívida líquida | R$ 312,77 bi | R$ 312,77 bi | dívida bruta − caixa |
| Disponibilidade | R$ 53,76 bi | R$ 53,76 bi | `cash` (1.01.01 + 1.01.02) |
| Segmento de listagem | Nível 2 | Nível 2 de Governança Corporativa | `issuers.listing_segment` (já no card) |
| Free float | 60,70% | — | não está na base (o FRE baixado em `carga/data/cvm/fre` tem a distribuição do capital, mas não é importado) |
| Tag along | 100,00% | — | não está na base (só presumível pelo segmento de listagem) |
| Liquidez média diária | R$ 2,38 bi | R$ 2,33 bi (42 pregões) · R$ 2,46 bi (21) | quantidade × fechamento do ticker |
| Setor / Segmento | Petróleo, Gás e Biocombustíveis / Exploração, Refino e Distribuição | iguais | `issuers.b3_*` (já no card) |

### 1.3 Números que pesam nas decisões

- **Classes sem cotação:** no último `share_counts`, 81 das 369 companhias têm ações ON sem ticker ON ativo e 25 têm PN
  sem ticker PN; 34 emissores têm PN em séries (PNA, PNB...), que o `share_counts` não separa (D3).
- **Exercício fora de dezembro:** 7 companhias (D7).
- **Layout:** 348 comerciais, 19 bancos, 2 seguradoras (31.4, D16).
- **Cartões:** ações passam de 7 para 30 cartões (D15).

---

## 2. Planning (23/10/2026) — solução

Substitui as seções 2 a 9 do plano de 18/10 (as fórmulas e as decisões foram mantidas; mudaram os nomes, a numeração
da migration, os testes, a listagem e o formato das etapas). O levantamento de 18/10 (seção 1) continua valendo, com
os números reconferidos na seção D.3.

### 2.1 Objetivo

Na página do ativo (`/assets/{ticker}`): os indicadores novos de ações (seção 31 de `docs/PENDENCIAS.md`), o card
"Dados da empresa"/"Dados do fundo", os Demonstrativos em Trimestre \| Anual com Valor simples \| detalhado e a janela
3A \| 5A \| MAX nos indicadores. Nada novo é buscado fora: tudo sai do que está na base e dos arquivos da CVM já
baixados em `carga/data/cvm`.

### 2.2 Decisões tomadas

| # | Decisão | Data |
|---|---|---|
| T1 | Implementar a seção 31 inteira (31.1 a 31.4) neste escopo | 18/10 |
| T2 | O card "Cadastro" passa a se chamar **"Dados da empresa"**, mantém o que tem e ganha só o que a base já tem | 18/10 |
| T3 | Demonstrativos: "12 meses" sai e entra **Anual**, com colunas por ano | 18/10 |
| T4 | Demonstrativos: **Valor simples** (padrão) \| **Valor detalhado** | 18/10 |
| T5 | Indicadores: **3A \| 5A (padrão) \| MAX** no cartão e no modal; as médias acompanham o período | 18/10 |
| T6 | Contas novas da CVM com valor gravado em **português**, como as de hoje (N1) | 23/10 |
| T7 | Códigos de indicador no padrão de hoje, sem migração (N2); lista na 2.4 | 23/10 |
| T8 | Janela com o padrão dos períodos de Índices/cotação: Java `Y3`/`Y5`/`MAX`, API `3A`/`5A`/`MAX` (N3) | 23/10 |
| T9 | Colunas novas no fim de `company_quarters` (N4) | 23/10 |
| T10 | Preço atual nos indicadores e no valor de mercado; liquidez média é a exceção explícita (fechamento de cada pregão passado) (N5) | 23/10 |
| **T11** | **A listagem devolve só os indicadores das colunas dela** (R2) — hoje os 7 de ação e os 7 de FII | 23/10 |
| **D23** | **Valor detalhado continua em reais, com nota no rodapé** (opção b): a CVM publica 430 de 440 companhias (DFP 2025) em R$ mil, então o detalhado termina em "000"; o rodapé dos Demonstrativos diz isso quando o detalhado está ligado. Não escolhidas: (a1) gravar a escala do documento e mostrar em R$ mil; (a2) deduzir a escala na tela | 24/10 |
| **D22** | **EBITDA sem a exaustão dos ativos biológicos** (opção a): D&A = DVA 7.04.01 menos a exaustão que a DFC traz em linha própria (6.01.01.*), gravada como `EXAUSTAO_BIOLOGICA`. Achado na conferência da A2 (KLBN11: margem EBITDA 38,67% × 26,46% do Investidor10; com a regra, 26,46% exatos, e 2022–2024 iguais). Em 2025, 5 companhias separam a exaustão; quem informa linha única "depreciação, amortização e exaustão" fica como está. A definição da ICVM 527 inclui a exaustão (opção b, não escolhida) | 24/10 |

### 2.3 Decisões D1–D21 (revistas e respondidas em 23/10)

**Respostas do Guilherme (23/10/2026):** seguir a recomendação em D1–D10, D12–D16 e D18–D20. Diferentes da
recomendação:

| # | Resposta |
|---|---|
| D11 | Simples \| Detalhado **só nos Demonstrativos** (ações e informe do FII); o card Dados da empresa fica só com o valor simples (exato no hover, como hoje). No Detalhado, **fonte menor** nos valores |
| D17 | **Esconder** os cartões que não se aplicam a bancos e seguradoras (a recomendação) |
| D21 | Fora do escopo e **nem vira pendência**: as colunas de hoje da listagem são as principais e continuam; acrescentar coluna é outro escopo |

Texto original das perguntas e recomendações:

**Dados da empresa**

| # | Pergunta | Recomendação |
|---|---|---|
| D1 | O patrimônio líquido (31.1) entra **só no card Dados da empresa** ou também como cartão de indicador com histórico? | **Só no card.** A série histórica do PL já está nos Demonstrativos |
| D2 | Itens do anexo 4 que entram | **Entram:** valor de mercado, valor de firma, PL, nº total de papéis, ativos, ativo circulante, dívida bruta, dívida líquida, disponibilidade, liquidez média diária (setor, segmento e segmento de listagem já estão). **Ficam fora:** free float e tag along (não estão na base) → `docs/PENDENCIAS.md` |
| D3 | Valor de mercado de classe **sem cotação** e de PN em séries (PNA 66, PNB 15, PNC 4 ativos) | Classe sem cotação própria usa o **preço da outra classe listada**, com nota no hover ("ON sem negociação: avaliada pelo preço de PETR4"). PN em séries usa a série **de maior liquidez** nos últimos 2 meses. Sem nenhuma classe com preço: vazio com o motivo |
| D4 | Liquidez média diária: janela e cálculo | **Do ticker consultado**, média de **quantidade × fechamento** dos pregões dos **últimos 2 meses** (T10). Janela em `activest.issuer-page.liquidity-months: 2` |
| D5 | Datas no card | Rodapé "Balanço de 30/06/2026 · preço de 23/10/2026" e o relógio de dado antigo (limites de `activest.issuer-page.stale-data.*`) |
| D6 | FII: o card muda também? | **Sim, "Dados do fundo"**: valor de mercado (cotas × preço), PL do informe, nº de cotas, ativos do informe e liquidez média diária, mais os campos de hoje |
| D7 | Valores ≥ 1 trilhão | Padrão do app (**R$ 1.279,02 bi**); o detalhado mostra o número inteiro |

**Demonstrativos**

| # | Pergunta | Recomendação |
|---|---|---|
| D8 | Coluna do **ano em andamento** no Anual | **Acumulado do ano**, cabeçalho "2026 · 6m" e hover "acumulado de jan a jun/2026"; posição = último balanço |
| D9 | Exercício fora de dezembro (7 companhias) | A coluna é o ano em que o exercício termina |
| D10 | Quantidade de colunas no Anual | Igual ao Trimestre: **Últimos 8** \| **Todos (n)** |
| D11 | Onde vale Simples \| Detalhado | Demonstrativos (ações e informe do FII) e card Dados da empresa, cada um com o seu seletor, padrão Simples, sem lembrar a escolha; botões como os vizinhos (posição verde do anexo 1) |
| D12 | O que muda no Detalhado | Só o abreviado: `R$ 548.490.000.000,00`, `12.888.732.761`; percentuais e R$ por cota iguais |
| D13 | Linhas novas nos Demonstrativos | **Quatro:** Lucro bruto e EBITDA (fluxo), Ativo circulante e Passivo circulante (posição). LAIR e IR/CSLL só no ROIC |

**Indicadores**

| # | Pergunta | Recomendação |
|---|---|---|
| D14 | O que é a janela 3A / 5A / MAX | Os **N últimos exercícios fechados + Atual** (5A hoje: 2021–2025 + Atual). No Trimestral, do 1º trimestre do primeiro ano da janela ao último balanço. **MAX** = toda a série (ações desde 2010, FII desde 2017). Média = pontos anuais da janela + Atual (nulo fora, zero dentro) |
| D15 | Seletor por cartão ou um para todos | **Um por cartão**, padrão 5A, sem lembrar a escolha; o modal abre na janela do cartão e guarda a sua ao navegar com ‹ › |
| D16 | Organização dos 30 cartões de ações | Grupos com subtítulo: *Valuation*, *Endividamento*, *Eficiência*, *Rentabilidade*, *Crescimento* (composição na 2.4); FII sem grupos |
| D17 | Bancos e seguradoras (19 + 2) | **Esconder** os cartões que não se aplicam, com a linha "Indicadores de margem, EBIT/EBITDA, dívida e capital de giro não se aplicam a bancos e seguradoras". Alternativa: cartão com "—" e o motivo |
| D18 | P/X e EV: por ticker ou por empresa | **P/X por ticker** (preço ÷ valor por ação, como P/L); **EV por empresa** (valor de mercado da D3 + dívida líquida), coerente com o "Valor de firma" do card |
| D19 | Base de ROA e ROIC | **Média do início e do fim do período**, como o ROE; alíquota do ROIC fora de 0–100% vira nulo com o motivo |
| D20 | CAGR 5 anos | Ponto anual = CAGR dos 5 exercícios que terminam nele; Atual = 12 meses atuais × 12 meses de 5 anos antes; vazio com valor ≤ 0 no início ou no fim |
| D21 | Indicadores novos como colunas/filtros da listagem (31.5) | **Fora deste escopo** → pendência. Com a T11, entram na listagem só quando virarem coluna |

### 2.4 Indicadores novos (T7, D16)

Fórmulas na 2.5 (as de 18/10, mantidas). Grupo no próprio enum (`Indicator.Group`, enum novo:
classe e valores em inglês, não é gravado), para o web ordenar.

| Grupo (`Group`) | Códigos (existentes em itálico) |
|---|---|
| Valuation (`VALUATION`) | *`PL`*, *`PVP`*, *`DY`*, `PSR`, `P_EBIT`, `P_EBITDA`, `EV_EBIT`, `EV_EBITDA`, `P_ATIVO`, `P_CAP_GIRO`, `P_ATIVO_CIRC_LIQ`, *`LPA`*, *`VPA`* |
| Endividamento (`DEBT`) | `DIV_LIQ_EBIT`, `DIV_LIQ_EBITDA`, `DIV_BRUTA_PATRIMONIO`, `PATRIMONIO_ATIVOS`, `PASSIVOS_ATIVOS`, `LIQUIDEZ_CORRENTE` |
| Eficiência (`EFFICIENCY`) | `MARGEM_BRUTA`, `MARGEM_EBITDA`, `MARGEM_EBIT`, `MARGEM_LIQUIDA` |
| Rentabilidade (`PROFITABILITY`) | *`ROE`*, `ROA`, `ROIC`, `GIRO_ATIVOS`, *`PAYOUT`* |
| Crescimento (`GROWTH`) | `CAGR_RECEITAS_5A`, `CAGR_LUCROS_5A` |

`PATRIMONIO_*` em vez de `PL_*`: `PL` já é o código de P/L. O enum também ganha a marca de "na listagem" (T11) para os
14 de hoje.

### 2.5 Regras

**Fórmulas** — base: último balanço (Atual) ou o do ponto, 12 meses = TTM de `company_quarters`, preço do ticker na
data, ações ajustadas por eventos (as regras de hoje do P/L e do P/VP). "Médio" = início e fim do período (balanço de
12 meses antes, folga de 20 dias, como o ROE). EBITDA = EBIT + depreciação/amortização (DVA 7.04.01). Nulo sempre com
o motivo, nunca zero inventado.

| Código | Fórmula | Unidade | Vale para banco/seguradora |
|---|---|---|---|
| `PSR` | preço ÷ (receita 12m ÷ ações) | razão | sim |
| `P_EBIT` | preço ÷ (EBIT 12m ÷ ações); nulo com EBIT ≤ 0 | razão | não |
| `P_EBITDA` | preço ÷ (EBITDA 12m ÷ ações); nulo com EBITDA ≤ 0 | razão | não |
| `EV_EBIT` | (valor de mercado + dívida líquida) ÷ EBIT 12m | razão | não |
| `EV_EBITDA` | (valor de mercado + dívida líquida) ÷ EBITDA 12m | razão | não |
| `P_ATIVO` | preço ÷ (ativo total ÷ ações) | razão | sim |
| `P_CAP_GIRO` | preço ÷ ((ativo circ. − passivo circ.) ÷ ações); nulo com capital de giro ≤ 0 | razão | não |
| `P_ATIVO_CIRC_LIQ` | preço ÷ ((ativo circ. − passivo total) ÷ ações), passivo total = ativo total − PL; nulo com base ≤ 0 | razão | não |
| `DIV_LIQ_EBIT` | (dívida bruta − caixa) ÷ EBIT 12m | razão | não |
| `DIV_LIQ_EBITDA` | (dívida bruta − caixa) ÷ EBITDA 12m | razão | não |
| `DIV_BRUTA_PATRIMONIO` | dívida bruta ÷ PL | razão | não |
| `PATRIMONIO_ATIVOS` | PL ÷ ativo total | % | sim |
| `PASSIVOS_ATIVOS` | (ativo total − PL) ÷ ativo total | % | sim |
| `LIQUIDEZ_CORRENTE` | ativo circ. ÷ passivo circ. | razão | não |
| `MARGEM_BRUTA` | lucro bruto 12m ÷ receita 12m | % | não |
| `MARGEM_EBITDA` | EBITDA 12m ÷ receita 12m | % | não |
| `MARGEM_EBIT` | EBIT 12m ÷ receita 12m | % | não |
| `MARGEM_LIQUIDA` | lucro líquido 12m ÷ receita 12m | % | sim |
| `ROA` | lucro líquido 12m ÷ ativo total médio (D19) | % | sim |
| `ROIC` | EBIT 12m × (1 − IR/CSLL ÷ LAIR) ÷ (PL + dívida líquida) médio (D19) | % | não |
| `GIRO_ATIVOS` | receita 12m ÷ ativo total médio | razão | não |
| `CAGR_RECEITAS_5A` | (receita 12m ÷ receita 12m de 5 anos antes)^(1/5) − 1 (D20) | % | sim |
| `CAGR_LUCROS_5A` | idem com o lucro líquido 12m (D20) | % | sim |

**Contas novas da CVM (T6)** — só layout comercial para 3.03, 7.04.01, 1.01 e 2.01.

| Valor em `cvm_statement_values.metric` | Conta | Demonstração | Colunas em `company_quarters` (T9) |
|---|---|---|---|
| `LUCRO_BRUTO` | 3.03 | DRE | `gross_profit_q`, `gross_profit_ttm` |
| `DEPRECIACAO_AMORTIZACAO` | 7.04.01 | DVA | `depreciation_amortization_q`, `depreciation_amortization_ttm` |
| `LAIR` | 3.07 | DRE | `pretax_income_q`, `pretax_income_ttm` |
| `IR_CSLL` | 3.08 | DRE | `income_tax_q`, `income_tax_ttm` |
| `ATIVO_CIRCULANTE` | 1.01 | BPA | `current_assets` |
| `PASSIVO_CIRCULANTE` | 2.01 | BPP | `current_liabilities` |

**Valor de mercado (D3)** — ações ON sem tesouraria × preço ON + PN sem tesouraria × preço PN, quantidade do último
`share_counts` ajustada pelos eventos posteriores; no histórico, os preços de cada classe na data do balanço. Unit não
entra (as classes que a compõem já entram).

**Anual (D8–D10)** — uma coluna por exercício fechado: fluxo = 12 meses do exercício, posição = balanço do fim do
exercício. Ano em andamento: soma dos trimestres isolados desde o início do exercício; posição = último balanço.
Calculado no backend.

**Janelas (T8, D14)** — o histórico continua vindo inteiro numa chamada; o DTO ganha
`averages: [{ "window": "3A", "fromYear": 2023, "value": … }, { "window": "5A", … }, { "window": "MAX", … }]`,
calculadas com `IndicatorRules.average`; o web recorta os pontos pela janela. `companyAverage` continua (= MAX). Rodapé
do cartão: "média 5A 9,87 · desde 2021".

### 2.6 Alterações

**Banco (core)**

| Peça | Mudança |
|---|---|
| `V7__company_quarters_new_accounts.sql` | 10 colunas `NUMERIC(24,2)` nulas no fim de `company_quarters` (2.5). Tabela derivada, refeita inteira pela carga: a 8.1 não se aplica (não há default a informar) |
| `CompanyQuarter` | os 10 campos |

**adm-api**

| Peça | Mudança |
|---|---|
| `CvmStatements.wanted` | BPA aceita `1.01`; BPP `2.01`; DRE `3.03`, `3.07`, `3.08`; DVA `7.04.01` |
| `FinancialMetric` / `FinancialMetricRules` | as 6 métricas, só no comercial onde indicado |
| `QuarterSeries` / `CompanyQuarterService` | fluxos com trimestre isolado e 12 meses; posições |
| Reprocessamento | `POST /api/v1/admin/cvm/import?from=2010&force=true` (tarefa `CVM_STATEMENTS_IMPORT`, ~7 min, sem download), com retrato antes/depois de `share_counts` e `company_quarters` (R1) |

**app-api**

| Peça | Mudança |
|---|---|
| `Indicator` | 23 códigos, `Group`, marca da listagem (T11) |
| `IndicatorRules` / `CompanyBasis` | fórmulas da 2.5; `CompanyBasis` ganha receita, EBIT, D&A, lucro bruto, lucro líquido, ativo total (início e fim), caixa, dívida, circulantes, LAIR, IR/CSLL, valor de mercado |
| `IndicatorService` | preços das outras classes do emissor (D3), `averages` por janela; `currentAll` calcula só os indicadores da listagem (T11) |
| `IssuerListingService` | linha só com os indicadores das colunas (T11) |
| `IndicatorHistoryDTO` | `averages` (2.5) |
| `GET /api/v1/assets/{ticker}/company-data` (novo) | valor de mercado (com a nota da D3), valor de firma, PL, nº de papéis, ativos, ativo circulante, dívida bruta e líquida, disponibilidade, liquidez média (D4), datas e sinais de dado antigo (D5); FII o conjunto da D6 |
| `GET /api/v1/issuers/{id}/years` (novo) | linhas anuais com `fiscalYear`, `periodEnd`, `partial`, `months` |
| `GET /api/v1/issuers/{id}/quarters` | + as 4 contas da D13 |
| `application.yml` | `activest.issuer-page.liquidity-months: 2` |

**Web**

| Componente | Mudança |
|---|---|
| `IssuerRegistry` → `CompanyData` | "Dados da empresa"/"Dados do fundo": quadro de valores (rótulo, valor simples com o exato no hover, ⓘ com a fórmula), campos de cadastro embaixo, rodapé com as datas (sem seletor — D11) |
| `FinancialStatements` | Trimestre \| **Anual**; Simples \| Detalhado (Detalhado com fonte menor — D11); 4 linhas novas; rodapé ajustado |
| `IndicatorGrid` | grupos, cartões novos, seletor 3A \| 5A \| MAX, sparkline e média pela janela, cartões que não se aplicam escondidos em banco/seguradora com a linha explicativa (D17) |
| `IndicatorHistoryModal` | seletor de janela à esquerda de Anual \| Trimestral; barras, tabela e média pela janela |
| `utils/indicators.ts`, `types/index.ts`, `api/indicators.ts`, `api/issuers.ts` | metadados dos 23, tipos novos (`IndicatorWindow`), `formatFull`, chamadas novas |
| `components/ui/Toggle.tsx` | o `Toggle` dos Demonstrativos vira comum (Demonstrativos e modal de indicadores) |

### 2.7 Critérios de aceitação

1. Os 23 indicadores aparecem nos cartões de ação, agrupados, com Atual, sparkline, modal e média; nulos sempre com
   o motivo.
2. Banco e seguradora (ITUB4, BBSE3): só os cartões que se aplicam, com a linha explicativa (D17).
3. 3A \| 5A \| MAX muda sparkline, média e rodapé do cartão e, no modal, barras, tabela e média; padrão 5A.
4. "Dados da empresa" de PETR4 bate com a conferência da 1.2 refeita com os preços do dia (valor de mercado, firma,
   PL, papéis, ativos, ativo circulante, dívidas, disponibilidade, liquidez) e mostra as datas; FII com "Dados do fundo";
   card sem seletor Simples \| Detalhado (D11).
5. Demonstrativos: Anual com colunas por exercício, ano em andamento marcado (D8), exercício fora de dezembro na coluna
   certa (D9); Simples \| Detalhado, com fonte menor no Detalhado (D11); 4 linhas novas.
6. P/L, P/VP, DY, ROE, Payout, LPA, VPA e os 7 de FII com os mesmos valores de antes; padrões do Preço Teto iguais.
7. Reprocessamento sem diferença em `share_counts` e nas colunas antigas de `company_quarters` (R1).
8. Listagem com as mesmas colunas e valores, resposta da aba Ações **não maior que hoje** (≤ 301 KB) e tempo igual.
9. Bateria verde (core, integration, adm-api, app-api), `tsc -b`, lint e build do web; pilha sobe com a V7.

### 2.8 Etapas do plano

| Etapa | Entrega | Depende |
|---|---|---|
| **A1** | Banco + adm-api: V7, `CompanyQuarter`, contas novas, testes; retrato, reprocessamento e comparação (R1) | — |
| **A2** | app-api: indicadores novos, valor de mercado por classe, `averages`, listagem só com as colunas (T11) + testes | A1 |
| **A3** | app-api: `company-data`, `years` e as contas novas em `quarters` + testes | A1 |
| **A4** | Web: Demonstrativos (Anual, Simples \| Detalhado, linhas novas) e `ui/Toggle` | A3 |
| **A5** | Web: Dados da empresa / do fundo | A3 |
| **A6** | Web: Indicadores (grupos, cartões, 3A/5A/MAX no cartão e no modal) | A2 |

Em seguida, as etapas 5 a 7 da metodologia:

| Etapa | O que |
|---|---|
| **Testing** | testes novos e de regressão (2.9); `mvn clean verify` da raiz; `tsc -b`, lint e build do web; `docker compose up -d --build`; conferência dos critérios 2.7 na base real (PETR4, VALE3, ITUB4, uma unit, um FII); conferência no navegador com o Guilherme logado |
| **Review** | revisão do código (qualidade, desempenho da listagem e da página, aderência às regras 2.6, 8.1 e 9), correções; documentação: `Claude.md` (Ativos em Telas — inclusive os parâmetros antigos da R9 —, endpoints, contas da CVM), `docs/ACTIVEST_DB.md`, `docs/ADM_TASKS.md`, `docs/PENDENCIAS.md` (31 resolvida, com a 31.5 encerrada sem pendência — D21; free float e tag along como pendências), `README.md`, andamento deste plano |
| **Commit** | conferência final (`git status`/`git diff`, só leitura) e mensagem sugerida: `TRACK_xx - Alteração da tela de Ativos` (o TRACK da branch que você abrir) |

### 2.9 Testes

| Teste | O que prova |
|---|---|
| `FinancialMetricRulesTest`, `QuarterSeriesTest` (adm) | as 6 contas no comercial, ausentes em banco e seguradora; trimestre isolado e 12 meses do lucro bruto, D&A, LAIR e IR |
| `FinancialStatementImportIT` (adm) | importação grava as métricas novas e `company_quarters` as colunas novas |
| `IndicatorRulesTest` (app) | cada fórmula da 2.5 com os nulos e motivos (EBIT ≤ 0, alíquota fora de 0–100%, CAGR com base ≤ 0, não se aplica) |
| `IndicatorsIT` (app) | indicadores novos no Atual e no histórico; `averages` 3A/5A/MAX; valor de mercado com classe sem cotação e PN em séries |
| `CompanyDataIT` (app, novo) | `company-data` de ação, unit, banco e FII; liquidez na janela; dado antigo |
| `AnnualStatementsIT` (app, novo) | `years` com exercício fechado, ano em andamento e exercício fora de dezembro |
| `IssuerListingIT`, `IssuerPageIT`, `CeilingPriceIT` (app) | regressão: listagem só com as colunas (T11), página e padrões do Preço Teto iguais |

### 2.10 Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| T1–T11 | registrou | decidiu |
| D1–D21 | preparou com recomendação | decidiu (23/10) |
| A1–A6, reprocessamento, Testing, Review, documentação | executa e confere (só com a ordem de execução) | — |
| Conferência no navegador | prepara | confere logado |
| Branch, commit e pull request | sugere a mensagem | executa |

### 2.11 Riscos

Os da seção I.3 (R1–R9), com as mitigações de lá e os critérios 6 a 8. Limitações que ficam: liquidez aproximada
(quantidade × fechamento difere do volume financeiro real, −2% em PETR4 contra o Investidor10), valor de mercado
estimado em classe sem cotação (D3), companhias com DRE fora do padrão com indicador nulo e motivo, balanços zerados
da 32.2.

---

## 3. Andamento


| Etapa | Estado |
|---|---|
| Planejamento | **18/10/2026** — plano escrito; T1–T5 registradas; aguarda D1–D21 e a ordem de execução |
| Discovery | **23/10/2026** — escopo retomado no Activest a partir da etapa 1: referências conferidas contra o código e a base recarregada (seção D); números da 1.2 e da 1.3 iguais; D1–D21 continuam abertas; pontos novos N1–N5, resolvidos pelo Guilherme no mesmo dia. |
| Impact | **23/10/2026** — seção I: componentes, tempos medidos, riscos R1–R9 e ordem. Guilherme decidiu a listagem (T11). |
| Planning | **23/10/2026** — seção 2: solução, T6–T11, D1–D21 revistas, códigos dos indicadores, colunas da V7, critérios de aceitação, etapas A1–A6 + Testing/Review/Commit. D1–D21 respondidas pelo Guilherme no mesmo dia (recomendação, exceto D11: seletor só nos Demonstrativos e fonte menor no Detalhado; D21 sem pendência). Aguarda a ordem de execução |
| Implementation | **23/10/2026** — ordem do Guilherme: "implemente uma etapa por vez" |
| A1 | **23/10/2026** — feita. V7 (`company_quarters` + 10 colunas), `CompanyQuarter`, `FinancialMetric` (6 valores em português), `FinancialMetricRules` (lucro bruto, LAIR e IR/CSLL pela descrição — no banco o IR é a 3.06; D&A como filha de "Retenções" na DVA, gravada positiva; circulantes por código + descrição, só comercial), `CvmStatements.wanted` (BPA 1.01), `QuarterSeries` (4 fluxos, 2 posições; `Map.ofEntries`), `CompanyQuarterService` (INSERT com 35 colunas). Testes: `FinancialMetricRulesTest` +3, `QuarterSeriesTest` e `FinancialStatementImportIT` com as contas novas, `SchemaTest` (V7). `mvn clean verify`: core 212; integration 103; adm-api 130 + 83; app-api 210 + 263 — verdes. Subida: Flyway aplicou a V7. Reprocessamento (`cvm/import?from=2010&force=true`, tarefa `CVM_STATEMENTS_IMPORT`): 316 s, 19.351 documentos atualizados. **Comparação (R1): `share_counts` 18.778 linhas, série trimestral 19.346 linhas nas colunas antigas e 312.691 métricas antigas — idênticas antes e depois.** Métricas novas gravadas: LAIR e IR/CSLL 28.015, lucro bruto 27.165, D&A e circulantes 18.203. PETR4 em 30/06/2026: ativo circulante R$ 154,97 bi (= Investidor10), passivo circulante 181,37 bi, lucro bruto 12m 277,51 bi, D&A 12m 87,58 bi. Achados da fonte para a A2: 136 linhas com D&A 12m negativa e 51 com lucro bruto acima da receita (dado como veio da CVM) |
| A2 | **24/10/2026** — feita. `Indicator`: 23 códigos da 2.4, com as marcas `LISTED` (T11), `SHARES` e `COMMERCIAL` (os 15 que banco e seguradora não têm); `appliesTo(kind, layout)`. **Ajuste do plano:** o grupo (Valuation, Endividamento...) fica no web e não no enum — no `Item` ele aumentaria a linha da listagem. `IndicatorRules`: `Accounts` (contas da 2.5, início do período, 5 anos antes e valor de mercado) e as 23 fórmulas; PL **consolidado** e lucro líquido consolidado nos indicadores novos (coerentes com ativo, dívida e EBIT consolidados; ROE e VPA seguem com os da controladora); D&A negativa da fonte entra como veio. `IndicatorService`: valor de mercado por classe (D3: ON + PN sem tesouraria × preço de cada classe, série PN de maior liquidez em `activest.issuer-page.liquidity-months: 2`, classe sem cotação pelo preço da outra com a nota), EV atual e histórico, médias por janela (`IndicatorWindow` Y3/Y5/MAX → `averages` com `window`, `fromYear`, `value`), `statementLayout` no DTO, histórico de indicador que não se aplica a banco = 400; listagem calcula e devolve só os indicadores das colunas e lê só as colunas da F9. Testes: `IndicatorRulesTest` +5 (13), `IndicatorsIT` +4, `IssuerListingIT` (linha só com as colunas), `IssuerPageIT` (30 no histórico em lote). Bateria verde: core 212; integration 103; adm-api 130 + 83; app-api 215 + 267. Base real: PETR4 30 indicadores (P/L 5,67; EV/EBITDA 4,09; ROIC 18,12%; margem líquida 24,39%), EV igual em PETR3 e PETR4 (D18); ITUB4 banco com 15; KLBN11 (unit) e WEGE3 completos; histórico de PETR4 em 54 ms (38,7 KB). Listagem de ações: **300.959 bytes** (antes 300.960) e 0,31–0,40 s aquecida (antes 0,15–0,35 s) |
| D22 | **24/10/2026** — implementada (adm-api): métrica `EXAUSTAO_BIOLOGICA` (ajustes 6.01.01.* da DFC só de exaustão, sem depreciação/amortização na linha; `CvmStatements.wanted` passa a ler 6.01.01.*) e `DEPRECIACAO_AMORTIZACAO` = DVA − exaustão do mesmo período. `FinancialMetricRulesTest` +1 (Klabin 2025: 4.990.437 − 2.697.982 = 2.292.455 mil; Petrobras, linha única, igual). Reprocessamento: 423 s. Comparação: `share_counts`, todas as colunas de `company_quarters` menos a D&A e as 432.292 métricas (menos D&A) idênticas; D&A mudou em 289 linhas de 10 emissores (Klabin, Suzano e Suzano Holding, Eucatex, Ferbasa, BrasilAgro, Karsten, Grazziotin, Embpar, Trevisa); 272 valores de exaustão gravados. KLBN11: margem EBITDA atual 26,46% (= Investidor10); 2020, 2022, 2023 e 2024 iguais ao Investidor10; 2021 (39,50 × 38,06) e 2025 (32,72 × 35,16) diferem |
| A3 | **24/10/2026** — feita. `GET /api/v1/assets/{ticker}/company-data` (`CompanyDataService` + `IndicatorService.marketData`): valor de mercado (D3, com a nota), de firma, PL da controladora, nº total de papéis (com tesouraria, ajustado pelos eventos), ativos, ativo circulante, dívida bruta e líquida, caixa, liquidez média do ticker (quantidade × fechamento, `activest.issuer-page.liquidity-months`), datas e marcas de dado antigo (limites da listagem); FII: cotas × preço, PL, cotas e ativos do informe. `GET /api/v1/issuers/{id}/years` (`AnnualStatements`): exercício fechado pelo DFP, ano em andamento acumulado (`partial`, `months`), ano da coluna = fim do exercício. `quarters` com `grossProfitQ/Ttm`, `ebitdaQ/Ttm`, `currentAssets`, `currentLiabilities`. Testes: `CompanyDataIT` 4, `AnnualStatementsIT` 4. Bateria verde: core 212; integration 103; adm-api 131 + 83; app-api 215 + 275. Base real — PETR4 contra a 1.2: PL 480,95 bi, papéis 12.888.732.761, ativos 1.279,02 bi, ativo circulante 154,97 bi, dívida bruta 366,53 bi, líquida 312,77 bi, disponibilidade 53,76 bi e **liquidez 2,38 bi (= Investidor10)**; valor de mercado 805,13 bi e de firma 1.117,90 bi com os preços de 23/10. Anual de PETR4: 2023–2025 e "2026 · 6m". ITUB4 sem circulante, dívida e EBITDA; HGLG11 com 45.601.713 cotas. Tempo aquecido: `company-data` 59–101 ms, `years` 29–44 ms |
| A4–A6 | **24/10/2026** — feitas de uma vez (ordem do Guilherme: "A4 a A6 sem interrupções"). **A4** `FinancialStatements`: Trimestre \| Anual (`/years`, coluna "2026 · 6m" com o acumulado no hover), Valor simples \| detalhado (D11: só nos Demonstrativos, ações e FII; detalhado com fonte menor, `formatFull`), linhas novas Lucro bruto, EBITDA, Ativo e Passivo circulante (D13); `Toggle` passou para `components/ui/Toggle.tsx`. **A5** `CompanyData` ("Dados da empresa" / "Dados do fundo") no lugar do `IssuerRegistry` (apagado): quadro de valores com ⓘ da fórmula e exato no hover, nota do valor de mercado (D3), banco/seguradora sem circulante/dívidas/disponibilidade, rodapé "Balanço de … · preço de …" com o relógio de dado antigo (D5), campos do cadastro embaixo. **A6** `IndicatorGrid`: grupos Valuation, Endividamento, Eficiência, Rentabilidade, Crescimento (D16; FII sem grupo), 3A \| 5A \| MAX por cartão (padrão 5A, D15), sparkline e "média 5A … · desde 2021" pela janela, linha de banco/seguradora (D17); `IndicatorHistoryModal`: janela à esquerda de Anual \| Trimestral, barras, tabela e média pela janela, mantida ao navegar com ‹ ›. Validação: `eslint` 0 erros (1 aviso antigo, PENDENCIAS 35.5), `vite build` ok; `tsc -b` só com o erro antigo do `TasksPage.tsx` (import `InfoTooltip` sem uso depois do trecho comentado — fora deste escopo). Achado: `AuthShell.tsx` e `LegalPage.tsx` ainda mostravam "PrimeVest" no título (resíduo da renomeação) — corrigido a pedido do Guilherme (24/10) |
| Conferência no navegador | **24/10/2026** — com o Guilherme logado (Claude no navegador do app): KLBN11 com os 5 grupos, 3A/5A/MAX trocando sparkline e média ("média 3A 12,56 · desde 2023" no EV/EBIT), modal na janela do cartão, Trimestral desde 1T23, ‹ › mantendo a janela; Dados da empresa completo; Demonstrativos Anual ("2026 · 6m" com o hover do acumulado) e detalhado (R$ 10.096.669.000,00 em 11 px). ITUB4: 15 cartões, linha de banco, card sem firma/circulante/dívidas. HGLG11: Dados do fundo, informe com Simples \| Detalhado. VALE3 e PETR4 sem erro no console. Ajustes da conferência: seletor de janela passou para a linha do valor (em cartão estreito o ícone de histórico vazava), valor de firma escondido em banco/seguradora, `key` duplicada entre o card e os indicadores (aviso do React) |
| D23 | **24/10/2026** — nota no rodapé dos Demonstrativos com o detalhado ligado (a CVM publica em R$ mil) |
| Testing | **24/10/2026** — `mvn clean verify` da raiz (11 min 20 s): core 212; integration 103; adm-api 131 + 83; app-api 215 + 275 — todos verdes. Web: `npm run build` (com `tsc -b`) verde e `eslint` sem erro nem aviso — o import sem uso de `TasksPage.tsx` (PENDENCIAS 35.5) foi removido a pedido do Guilherme. `docker compose up -d --build`: as duas APIs `Started`, Flyway em dia (V7). APIs na base real: listagem de ações 300.959 bytes com só as 7 colunas; PETR4 30 indicadores, ITUB4 15; `company-data` de PETR4 igual à conferência da A3; KLBN11 margem EBITDA 26,46%. Navegador (Guilherme logado): listagem de Ativos com as colunas de sempre, PETR4 com os 5 grupos, 30 cartões, Dados da empresa e Demonstrativos; Preço Teto abrindo com todas as chamadas 200. Os 500 do console eram de quando as APIs estavam paradas para a bateria (proxy do Vite sem backend), nenhum erro na app-api depois da subida |
| Review | **24/10/2026** — revisão do código: comentários ajustados à D22 (`QuarterSeries`) e a URL antiga no Javadoc da `IssuerPage`; sem achado de lógica, segurança (endpoints novos atrás do JWT, como os vizinhos) ou desempenho (página 30–100 ms aquecida, listagem como antes). Regras conferidas: 2.6 (só a liquidez usa fechamento, explícito), 8.1 (V7 sem default: tabela derivada), 9 (nomes novos em inglês, valores gravados em português). Documentação: `Claude.md` (revisão, tabelas `cvm_statement_values`/`company_quarters`, endpoints de Assets e Issuers, tela Ativos — inclusive os parâmetros antigos da R9 —, Flyway V7, contagem da bateria e "Tela de Ativos (24/10/2026)" em Pontos de Atenção), `docs/ACTIVEST_DB.md` (V7), `docs/ADM_TASKS.md` ([12] reprocessamento), `docs/PENDENCIAS.md` (31 resolvida com a 31.5 encerrada, 32.3, 35.5 resolvida, seção 36: free float/tag along, EBITDA da Klabin em 2021/2025, comentário da V7, escala do detalhado), `README.md` |
| Commit | **24/10/2026** — mensagem sugerida e conferência final (só leitura); o Guilherme criou a branch do escopo (TRACK_01), comitou e deu o push. **Escopo declarado finalizado pelo Guilherme.** Pendências que ficam: `docs/PENDENCIAS.md` 36.1–36.4 e 32.2 a (TIMS3) |
