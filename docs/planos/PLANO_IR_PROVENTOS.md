# PrimeVest — Plano de IR retido na fonte sobre proventos (bruto × líquido)

> **Nota (23/10/2026):** escrito quando o projeto se chamava **PrimeVest**. Nomes de módulo (`primevest-*`), pacote
> (`com.primevest`), configuração (`primevest.*`, `PRIMEVEST_*`), contêineres e versões de migration (V1–V67) citados aqui
> são os do repositório `primevest`; no Activest viraram `activest-*`, `com.activest`, `activest.*`/`ACTIVEST_*` e as
> migrations consolidadas V1–V6 (`docs/planos/PLANO_RENOMEACAO_ACTIVEST.md`).

> Fase: **implementado** (24/09/2026) · branch `TRACK_01-ajustes_crud_inicial`
> Nomes na implementação: a tabela é a entidade `WithholdingTaxRule`; o cálculo (chamado de `WithholdingTaxRule` nas seções 4–6) ficou em `WithholdingTaxCalculator`.
> Origem: conferência do JCP de BBAS3 pago em 11/06/2026 — portal B3 mostra **R$ 14,57**, PrimeVest **R$ 17,66**.
> Revisão 1 (24/09/2026): Passo 0 conferido no portal da B3; decisões D1–D4 tomadas; pergunta sobre integração respondida (§ 9).
> Revisão 2 (24/09/2026): D4 confirmado; regras passam a morar em tabela (§ 4.1); garantia para proventos novos (§ 4.5).
> Revisão 3 (24/09/2026): D5 e D6 decididos; sem endpoint de recálculo; plano de testes (§ 5).

Objetivo: o PrimeVest mostrar o que o investidor **recebeu de fato** (líquido), sem perder o bruto,
com a mesma conta que a B3 faz — para a conferência bater centavo a centavo.

---

## 1. Diagnóstico

Os dois números estão certos; medem coisas diferentes.

- A integração grava o **valor por ação declarado pela empresa**, que é **bruto**. É dado de catálogo
  (`asset_cash_events`), igual para todo mundo.
- O extrato da B3 (Área do Investidor) mostra o **valor líquido**: bruto menos o IR retido na fonte.
  A coluna "Preço Unitário" do extrato é o bruto por ação arredondado; "Valor Total" é o líquido.
- O IR depende de quem recebe e de quando — pertence ao provento do usuário (`dividends`), não ao
  catálogo. Esse cálculo nunca foi feito: tudo no PrimeVest é bruto hoje.

---

## 2. Passo 0 — Conferência no portal da B3 (feita em 24/09/2026)

Conta da B3: **bruto = cotas × valor por ação, truncado nos centavos; IR = bruto × alíquota, truncado
nos centavos; líquido = bruto − IR.**

| # | Provento (aprovação → pagamento) | Bruto | IR | Líquido calculado | Extrato B3 | Resultado |
|---|---|---|---|---|---|---|
| V1 | PETR4 JCP 11/12/2025 → **20/02/2026** (50 × 0,47160378) | 23,58 | 15% = 3,53 | 20,05 | **20,05** | ✅ JCP creditado em 2025 paga **15%**, mesmo pago em 2026 |
| V1b | PETR4 JCP 11/12/2025 → 20/03/2026 (50 × 0,17518233) | **8,75** (hoje 8,76) | 15% = 1,31 | 7,44 | **7,44** | ✅ Confirma 15% **e** o truncamento do bruto |
| V2 | BBAS3 JCP 2026 → 11/06/2026 (2 parcelas, 125 cotas) | 10,19 + 7,46 | 17,5% = 1,78 + 1,30 | 8,41 + 6,16 | **14,57** | ✅ 17,5% em 2026 |
| V3 | PETR4 JCP 16/04/2026 → 22/06/2026 (50 × 0,31311454) | 15,65 | 17,5% = 2,73 | 12,92 | **12,92** | ✅ JCP creditado em 2026: 17,5% |
| V4 | PETR4 **Rendimento** 16/04/2026 → 22/06/2026 (50 × 0,02038398) | 1,01 | **22,5%** = 0,22 | 0,79 | **0,79** | ✅ Rendimento de ação **é tributado**, tabela de renda fixa |
| V5 | PETR4 Dividendo 11/12/2025 → 20/03/2026 (50 × 0,29642144) | 14,82 | isento | 14,82 | **14,82** | ✅ Dividendo isento |

**Achado extra — rendimentos que o PrimeVest não tem.** O extrato mostra "Rendimento" de PETR4 em
20/02/2026 (R$ 0,35) e em 20/03/2026 (R$ 0,35 e R$ 0,21), junto do JCP/dividendo de dez/2025. Esses
eventos **não existem** em `asset_cash_events`: a B3 não os publica nos dados da empresa (a
atualização pela Selic só é conhecida no pagamento). Ver § 9 e § 8.

---

## 3. Regras de IR por tipo (investidor pessoa física residente)

| Tipo | Regra | Base |
|---|---|---|
| **JCP** | **15%** se creditado até 31/12/2025; **17,5%** se creditado a partir de 01/01/2026. Data de corte = **data do crédito** ≈ `approved_on` (fallback `data_com`, depois `payment_date`). **0 de 48** JCP da base sem `approved_on` | LC 224/2025, art. 8º ("na data do pagamento ou do crédito"), efeitos pelo art. 14, III · V1, V1b, V2, V3 |
| **Dividendo** | Isento | V5 · retenção de 10% acima de R$ 50 mil/mês fora do escopo (§ 8) |
| **"Rendimento" de ações** (atualização pela Selic — BBAS3, BBSE3, CXSE3, PETR4) | Tabela regressiva de renda fixa: 22,5% (até 180 dias), 20% (181–360), 17,5% (361–720), 15% (> 720). **Decidido: 22,5% fixo** (D5) — a data de início da correção é da empresa e não vem na fonte; nos 6 eventos com pagamento conhecido, aprovação → pagamento vai de 23 a 75 dias | V4 · § 8 |
| **Rendimento de FII** | Isento (PF, cota negociada em bolsa). O FNET já traz `income_tax_exempt` | — |
| **Amortização / Restituição de capital** | Sem IR (devolução de capital; já reduz o custo, B13) | — |
| ETF, BDR, Stock, REIT, outros | Sem regra: alíquota e líquido **nulos**; a tela mostra "—" | Nenhum provento desses tipos na base |

A alíquota do JCP fica numa tabela por **vigência** (até 31/12/2025 → 15%; desde 01/01/2026 →
17,5%), para a próxima mudança de lei ser uma linha.

---

## 4. Desenho

### 4.1 Onde as regras moram — tabela `withholding_tax_rules`

Divisão: **o que a lei muda fica no banco; o jeito de calcular fica no código.**

- **Banco (dado):** tipo de provento, tipo de ativo, alíquota, qual data decide (crédito ou pagamento),
  vigência (de/até) e a base legal. Uma mudança de lei é uma **migration nova** (fecha a vigência da
  linha antiga e insere a nova) — sem mexer em Java, versionada no git como o resto do schema,
  e a base legal fica registrada ao lado da alíquota.
- **Código (método):** truncamento nos centavos, bruto − IR, qual data usar como "crédito"
  (`approved_on ?? data_com ?? payment_date`), escolha da linha vigente. Isso não muda com a lei.

Por que não só no código: a regra teria vigência e base legal espalhadas em constantes, e cada
provento não teria como dizer **qual** regra o calculou. Por que não uma tela de cadastro: mudança de
alíquota é rara e precisa de recálculo e teste; uma migration obriga os dois. A tela pode vir depois,
lendo a mesma tabela.

Estrutura:

| Coluna | Tipo | O que é |
|---|---|---|
| `id` | `BIGSERIAL` | |
| `dividend_type` | `VARCHAR(30)` | `JCP`, `DIVIDENDO`, `RENDIMENTO`, `AMORTIZACAO`, `RESTITUICAO_CAPITAL` |
| `asset_type_id` | `BIGINT` FK `asset_types` | Regra por tipo de ativo: dividendo de ação é isento, de BDR não |
| `rate` | `NUMERIC(6,4)` | 0,1750 · 0 = isento |
| `base_date` | `VARCHAR(10)` | `CREDIT` ou `PAYMENT` — qual data cai na vigência |
| `valid_from` / `valid_to` | `DATE` | Vigência; `valid_to` nulo = em vigor |
| `legal_basis` | `VARCHAR(200)` | "LC 224/2025, art. 8º" |

Carga inicial (V23), só para PF residente:

| Tipo | Ativo | Alíquota | Data | Vigência | Base legal |
|---|---|---|---|---|---|
| JCP | Ações | 15% | crédito | até 31/12/2025 | Lei 9.249/1995, art. 9º, § 2º |
| JCP | Ações | 17,5% | crédito | desde 01/01/2026 | LC 224/2025, art. 8º |
| Dividendo | Ações | isento | pagamento | — | Lei 9.249/1995, art. 10 |
| Rendimento (atualização Selic) | Ações | 22,5% | pagamento | — | Renda fixa, até 180 dias (D5) |
| Rendimento | FIIs | isento | pagamento | — | Lei 11.033/2004, art. 3º |
| Amortização | FIIs | isento | pagamento | — | Devolução de capital |
| Restituição de capital | Ações | isento | pagamento | — | Devolução de capital |

Tipo/ativo sem linha vigente (ETF, BDR...) → **sem regra**: alíquota e líquido nulos, tela mostra "—".
Um teste garante que não há duas linhas vigentes para o mesmo tipo, ativo e data.

### 4.2 O que cada provento guarda — `dividends`

| Coluna | Tipo | O que é |
|---|---|---|
| `withholding_rule_id` | `BIGINT` FK, anulável | Regra que calculou (nula = sem regra) |
| `withholding_rate` | `NUMERIC(6,4)` | Cópia da alíquota aplicada |
| `withholding_tax` | `NUMERIC(18,2)` | IR retido, truncado nos centavos |
| `net_amount_per_share` | `NUMERIC(18,8)` | Líquido por cota, 8 casas — **só exibição** |
| `net_amount` | `NUMERIC(18,2)` | **TOTAL** = `total_amount − withholding_tax` (conta da B3) |
| `tax_checked_at` | `TIMESTAMP` | Quando a regra foi aplicada. **Nulo = ainda não calculado** (diferente de "sem regra") |

`total_amount` continua sendo o **bruto**, agora **truncado** nos centavos; `amount_per_share` (8 casas)
é o VALOR BRUTO COTA. A alíquota é copiada para o provento: se a regra for corrigida, o provento só
muda quando for recalculado, e dá para ver qual alíquota foi usada.

**TOTAL nunca sai de líquido/cota × cotas** — com 8 casas por cota, erra 1 centavo em 2 dos 5 casos
conferidos:

| Caso | Líquido/cota × cotas | Conta da B3 | Extrato |
|---|---|---|---|
| BBAS3 0,05968401 × 125, 17,5% | 0,04923931 × 125 = 6,1549 → **6,15** | 7,46 − 1,30 = **6,16** | 6,16 |
| PETR4 0,47160378 × 50, 15% | 0,40086321 × 50 = 20,0431 → **20,04** | 23,58 − 3,53 = **20,05** | 20,05 |

### 4.3 O cálculo — `WithholdingTaxRule` (novo, `domain/dividend`)

Recebe o provento (tipo, ativo, `approved_on`, `data_com`, `payment_date`, valor por cota, cotas),
busca a linha vigente em `withholding_tax_rules` e devolve regra, alíquota, bruto, IR, líquido por cota
e TOTAL. As regras são poucas e mudam raramente: carregadas uma vez e guardadas em memória.

### 4.4 Onde roda — `DividendRecalculator`

Hoje ele é o **único** lugar do sistema que cria, altera ou apaga um provento (as três chamadas a
`dividendRepository.save/delete` estão nele). Muda:
- `totalAmount`: `HALF_UP` → `DOWN` (40 dos 203 proventos mudam 1 centavo);
- chama a `WithholdingTaxRule` e grava as colunas do § 4.2;
- `changed(...)` compara também regra, alíquota, IR e líquidos.

### 4.5 Garantia para proventos novos (não só a base atual)

O IR não é um preenchimento único da base: é calculado **no mesmo momento em que o provento nasce ou
muda**, porque todo caminho passa pelo `DividendRecalculator`:

| Como o provento aparece | Quem chama o recálculo |
|---|---|
| Rotina diária das 20:00 grava evento novo da B3/FNET | `ProventosSyncService.recalculatePositions` |
| Rotina sob demanda (`POST /admin/proventos`) | idem |
| Transação lançada, editada ou apagada | `TransactionService` (e o `ProventosOnDemandListener`) |
| Evento existente ganha data de pagamento ou muda | rotina → `changed(...)` detecta e recalcula |
| Compra retroativa que dá direito a provento antigo | `TransactionService` → recálculo da posição |

Três travas para nada ficar sem cálculo:
1. **Na subida da API**, um verificador (`ApplicationReadyEvent`, como já fazem o fechamento e os
   logos) recalcula as posições que têm provento com `tax_checked_at` nulo. Isso preenche os 203 de
   hoje sem passo manual e pega qualquer provento que escape no futuro.
2. **Mudança de lei = migration que fecha a regra, insere a nova e zera `tax_checked_at` dos proventos
   afetados** (ex.: `JCP` com crédito a partir da nova vigência). Na próxima subida, o verificador
   recalcula só esses.
3. **Teste de integração**: todo provento gravado pelo recálculo sai com `tax_checked_at` preenchido;
   e um provento de tipo sem regra sai com alíquota nula, não com zero.

**Sem endpoint de recálculo** (decidido): a carga inicial e as mudanças de lei passam pelo verificador
na subida (travas 1 e 2).

### 4.6 API

- `DividendResponseDTO` + `withholdingRate`, `withholdingTax`, `netAmountPerShare`, `netAmount`.
- `DividendSummaryDTO` + `totalNet`, `totalTax`; `byType` passa a somar líquido.
- **Carteira e Dashboard (D1): proventos líquidos** — `sumTotalAmountByPosition` e
  `sumTotalDividendsByPortfolioId` passam a somar `COALESCE(net_amount, total_amount)` (tipo sem
  regra não some do retorno). Os números de rentabilidade atuais caem.

### 4.7 Tela de Dividendos (D2, D3, D4)

Colunas:

ATIVO · DATA COM · PAGAMENTO · TIPO · COTAS ELEGÍVEIS · **VALOR BRUTO COTA** (8 casas) · **ALÍQUOTA** ·
**VALOR LÍQUIDO COTA** (8 casas) · **TOTAL** (líquido, conta da B3)

- ALÍQUOTA: "15%", "17,5%", "22,5%", "Isento"; sem regra → "—". Hover: base legal da regra.
- TOTAL no hover: "Bruto R$ x · IR R$ y".
- Cards "Total Recebido" e "Por Tipo" em **líquido**, com bruto e IR numa linha menor.
- Formatação de 8 casas: `formatCurrency` usa 2 — criar formatação própria para valor por cota.

---

## 5. Testes

Nenhum teste usa rede: a B3 responde com os arquivos reais gravados em 24/09/2026
(`src/test/resources/b3`), como no `ProventosIT`. O arquivo `company_PETR.json` já traz **exatamente**
os proventos de PETR4 conferidos no extrato (§ 2) — o teste funcional reproduz o extrato da B3 com
50 cotas, centavo a centavo.

### 5.1 Unitários — um por caminho novo

| Classe de teste | O que prova |
|---|---|
| `WithholdingTaxRuleTest` (novo) | **Conta da B3**: bruto e IR truncados nos centavos; TOTAL = bruto − IR (BBAS3 8,41 e 6,16; PETR4 20,05) e **não** líquido/cota × cotas (6,15 e 20,04 seriam errados) · **líquido/cota** com 8 casas · **fronteira da vigência**: crédito em 31/12/2025 → 15%, em 01/01/2026 → 17,5% · **data de crédito**: `approved_on`; sem ela, `data_com`; sem as duas, `payment_date` · regra com base `PAYMENT` usa o pagamento · **isento**: alíquota 0, IR 0, TOTAL = bruto · **sem regra** (ETF, ou dividendo de BDR): alíquota e líquidos nulos · data fora de toda vigência → sem regra |
| `WithholdingTaxRuleRepositoryTest` (novo, `JpaTestBase`) | A carga da V23 existe, linha a linha (§ 4.1) · **nenhuma sobreposição de vigência** para o mesmo tipo de provento e de ativo — protege toda migration futura de lei |
| `DividendRecalculatorTest` (ajuste) | Provento **criado** sai com regra, alíquota, IR, líquidos e `tax_checked_at` · provento **existente** é recalculado quando muda a regra, a alíquota, a data de pagamento ou as cotas (`changed`) · truncamento do bruto (o 47,16 atual é revisto) |
| `DividendTaxVerifierTest` (novo) | Na subida, recalcula **só** as posições com provento de `tax_checked_at` nulo · segunda execução não faz nada (idempotente) · falha numa posição não impede as outras |
| `PortfolioServiceTest` (ajuste) | Total de proventos e retorno usam o **líquido**; tipo sem regra entra pelo bruto (`COALESCE`) |
| `DividendServiceTest` (novo) | Resumo: `totalNet`, `totalTax` e `byType` em líquido |

### 5.2 Funcionais — uma por regra discutida (`ProventosIrIT`, novo)

| # | Cenário | Espera |
|---|---|---|
| F1 | PETR4, 50 cotas, rotina de proventos com a B3 gravada | **JCP 20/02/2026 = 20,05** (15%, crédito em 2025) |
| F2 | idem | **JCP 20/03/2026 = 7,44** (15%; bruto truncado 8,75, não 8,76) |
| F3 | idem | **Dividendo 20/03/2026 = 14,82** (isento) |
| F4 | idem | **JCP 22/06/2026 = 12,92** (17,5%, crédito em 2026) |
| F5 | idem | **Rendimento 22/06/2026 = 0,79** (22,5%) |
| F6 | BBAS3, 125 cotas, as duas parcelas de JCP de 11/06/2026 no catálogo | **8,41 + 6,16 = 14,57** — o caso que abriu o escopo |
| F7 | FII MXRF (B3 + FNET gravados) | Rendimento **isento**: TOTAL = bruto |
| F8 | FII com amortização (cenário do B13) | Amortização **isenta** e continua reduzindo o custo |
| F9 | Ação com restituição de capital no catálogo | **Isenta** |
| F10 | ETF DIVD11 (B3 gravada) | **Sem regra**: alíquota e líquido nulos na API; a carteira soma o bruto |
| F11 | Proventos **novos**: compra retroativa de PETR4 depois da rotina, e nova rodada da rotina | Todo provento que nasce sai com IR calculado e `tax_checked_at` preenchido |
| F12 | **Mudança de lei por migration** (simulada: fecha a regra de JCP, insere outra alíquota com nova vigência, zera `tax_checked_at` dos afetados, roda o verificador) | Só os JCP creditados na nova vigência mudam; os anteriores ficam como estavam |
| F13 | **Verificador na subida**: `tax_checked_at` zerado em todos os proventos | Todos voltam preenchidos, com os mesmos valores de F1–F10 |
| F14 | Carteira e Dashboard (`/portfolio`) | `totalDividends` e retorno com o **líquido** |
| F15 | Tela de proventos (`/dividends/`) | `recent` com bruto/cota, alíquota, líquido/cota e TOTAL; `totalNet`, `totalTax` e `byType` em líquido |

Os testes atuais que conferem total bruto (`ProventosIT.esperadoPetr4`, `EventosSocietariosIT`) passam
a usar truncamento e continuam conferindo o **bruto** (`totalAmount`); o líquido fica no `ProventosIrIT`.

A tela (colunas, 8 casas, "—", hover) é conferida no navegador na validação — os testes E2E com
Playwright são escopo futuro.

---

## 6. Fases de implementação (depois da sua ordem)

1. V23: `withholding_tax_rules` com a carga inicial + colunas em `dividends`.
2. `WithholdingTaxRule` + `WithholdingTaxRuleTest` e `WithholdingTaxRuleRepositoryTest` (§ 5.1).
3. `Dividend` + `DividendRecalculator` (truncamento, colunas, `changed`); ajustar
   `DividendRecalculatorTest` (o 47,16 pode mudar com o truncamento).
4. Verificador na subida + `DividendTaxVerifierTest`; subir e conferir os 203 proventos preenchidos.
5. DTOs, `DividendService`, somas da carteira em líquido; testes unitários restantes do § 5.1.
   `ProventosIrIT` (F1–F15) e ajuste dos testes funcionais atuais.
6. Tela de Dividendos; conferir Carteira e Dashboard.
7. Validação: compilar, todos os testes, compose, e V1–V5 na tela batendo com o extrato.
8. Atualizar `Claude.md` (proventos têm bruto e líquido) e `docs/PENDENCIAS.md` (§ 8).

---

## 7. Decisões

| # | Decisão | Situação |
|---|---|---|
| D1 | Rentabilidade da Carteira e do Dashboard com provento **líquido** | ✅ Decidido |
| D2 | Tabela: VALOR BRUTO COTA (8 casas) · ALÍQUOTA · VALOR LÍQUIDO COTA (8 casas) · TOTAL líquido | ✅ Decidido |
| D3 | Cards em líquido | ✅ Decidido |
| D4 | Valores por cota com 8 casas só para exibição; TOTAL pela conta da B3 (bruto e IR truncados nos centavos) | ✅ Decidido |
| D5 | "Rendimento" de ações a 22,5% fixo | ✅ Decidido |
| D6 | Regras em tabela no banco, mudança de lei por migration (§ 4.1) | ✅ Decidido |
| D7 | Sem endpoint de recálculo | ✅ Decidido |
| D8 | Testes unitários para os caminhos novos e funcionais para todas as regras (§ 5) | ✅ Decidido |

---

## 8. Fora do escopo (vai para o `docs/PENDENCIAS.md`)

- **Rendimentos de atualização pela Selic que não chegam pela integração** (ex.: PETR4 em 20/02 e
  20/03/2026). Só existem no extrato do investidor (§ 9).
- **Prazo real do "rendimento" de ações**: a tabela regressiva conta da data de início da correção,
  que não vem na fonte; o 22,5% fixo erra se um dia o pagamento passar de 180 dias.
- **Retenção de 10% sobre dividendo > R$ 50 mil/mês da mesma empresa** (Lei 15.270/2025): exige somar
  por empresa pagadora no mês e respeitar a isenção do que foi aprovado até 2025.
- **IRPFM** (imposto mínimo acima de R$ 600 mil/ano): é da declaração anual, não da fonte.
- **Perfil do investidor** (PJ, isento, não residente): o plano assume PF residente.
- **ETF, BDR, Stock, REIT**: sem regra até existir provento desses tipos.

---

## 9. Dá para trazer o líquido pela integração?

**Não pelas fontes que o projeto usa ou cogita — nenhuma delas conhece o investidor.**

| Fonte | O que traz | Líquido/IR? |
|---|---|---|
| **B3, dados da empresa** (`GetListedSupplementCompany`, `GetListedCashDividends` — o que usamos) | Valor por ação declarado, datas, tipo, ISIN, período | ❌ É informação da empresa, bruta. Não sabe quem recebe |
| **BRAPI** `/api/v2/stocks/dividends` (plano Startup) | Pelo dicionário público da BRAPI: `rate` (valor por ação), `paymentDate`, `lastDatePrior`, `approvedOn`, `label`, `relatedTo`, `isinCode`, `assetIssued`, `remarks` | ❌ Os mesmos campos da B3 (é o espelho deles). Pagar o plano não resolve. Chamada de teste em 24/09/2026: `FEATURE_NOT_AVAILABLE` no plano gratuito |
| **B3, Área do Investidor** (o extrato que você conferiu) | Movimentação do investidor com valor líquido, inclusive os rendimentos que faltam | ✅ Tem o líquido |
| **APIs da Área do Investidor** (Posição, Movimentação, Eventos Provisionados...) | O mesmo extrato, por API | ✅ provavelmente — mas é contratada por empresas (fintechs, instituições) via área comercial da B3, com consentimento do investidor. Não é caminho para projeto pessoal |

O único dado com o líquido é o do investidor. Duas saídas, que **não substituem** o cálculo deste plano:
- **Importar o extrato de Movimentação** exportado da Área do Investidor (Excel), para conferência
  automática e para trazer os rendimentos que faltam — escopo futuro, hoje em `docs/planos/PLANO_INTEGRACAO_B3.md`.
- O cálculo continua necessário para os proventos **a receber**, que ainda não estão no extrato.

---

## Fontes

- [LC 224/2025 — Planalto](https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp224.htm) (art. 8º altera o art. 9º, § 2º, da Lei 9.249/1995: 17,5% "na data do pagamento ou do crédito"; art. 14, III: efeitos a partir de 01/01/2026)
- [Novas regras no JCP a partir de 2026 — Blog da Econet](https://blog.econeteditora.com.br/novas-regras-jcp-base-calculo-irrf-2026/)
- [Tributação de dividendos 2026: Lei 15.270/2025 explicada — Dinai](https://dinai.capital/blog/tributacao-dividendos-2026-como-funciona-lc-15270)
- [Como declarar recebimento de "rendimento" de ações no IR](https://impostoderendarestituicao.com.br/irpf/como-declarar-recebimento-de-rendimento-de-acoes/) (atualização pela Selic, tributação exclusiva na fonte, em geral 22,5%)
- [Petrobras pagará dividendos e JCP com reajuste pela Selic — Seu Dinheiro](https://www.seudinheiro.com/2023/empresas/petrobras-petra4-dividendos-jpc-rens/) (22,5% sobre a atualização)
- [Integrações da Área do Investidor (APIs) — B3](https://www.b3.com.br/pt_br/produtos-e-servicos/central-depositaria/canal-com-investidores/integracoes-da-area-do-investidor-apis/)
- [APIs — Área do Investidor — B3 for Developers](https://developers.b3.com.br/apis/api-area-do-investidor)
