# PrimeVest — Plano da alteração da tela de Ativos

> Fase: **planejamento** (18/10/2026). Aguarda as decisões da seção 2 e a ordem de execução.
> Origem: pedido de 18/10/2026 (ESCOPO "Alteração da tela de Ativos (antiga Emissoras)", ETAPA Planejamento), com os
> anexos 1 a 4 (Demonstrativos, Indicadores, modal do P/L e o quadro "Informações sobre a empresa" do Investidor10).
> Consome a tela de Ativos (`docs/planos/PLANO_TELA_EMISSORES.md`), os balanços e indicadores
> (`docs/planos/PLANO_EMPRESAS_BALANCOS.md`, F9) e a seção 31 de `docs/PENDENCIAS.md` ("Novos indicadores de ações").

---

## 0. Resumo

A página de um ativo (`/ativos/{ticker}`) muda em quatro frentes:

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

## 1. Levantamento (18/10/2026)

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

## 2. Decisões

Todas com recomendação. As que já vêm do pedido estão em 2.1.

### 2.1 Tomadas (18/10/2026)

| # | Decisão |
|---|---|
| T1 | Implementar a seção 31 inteira (31.1 a 31.4) neste escopo |
| T2 | O card "Cadastro" passa a se chamar **"Dados da empresa"**, mantém o que tem e ganha só o que a base já tem (sem buscar informação nova) |
| T3 | Demonstrativos: "12 meses" sai e entra **Anual**, com colunas por ano (2026, 2025, 2024...) |
| T4 | Demonstrativos: **Valor simples** (padrão) \| **Valor detalhado** (número completo com todas as casas) |
| T5 | Indicadores: **3A \| 5A (padrão) \| MAX** no cartão e no modal; as médias acompanham o período |

### 2.2 Para o Guilherme responder

**Dados da empresa**

| # | Pergunta | Recomendação |
|---|---|---|
| D1 | O patrimônio líquido (31.1 / A3) entra **só no card Dados da empresa** ou também como cartão de indicador com histórico? | **Só no card.** A série histórica do PL já está nos Demonstrativos; um cartão com sparkline repetiria a mesma linha |
| D2 | Itens do anexo 4 que entram | **Entram:** valor de mercado, valor de firma, PL, nº total de papéis, ativos, ativo circulante (com a 31.3), dívida bruta, dívida líquida, disponibilidade, liquidez média diária (setor, segmento e segmento de listagem já estão). **Ficam fora:** free float e tag along — não estão na base; tag along só seria presumido pela regra do segmento de listagem, e free float pede importar o FRE. Os dois vão para `docs/PENDENCIAS.md` |
| D3 | Valor de mercado de classe **sem cotação** (81 companhias com ON sem ticker, 25 com PN) e de PN em séries (34) | Classe sem cotação própria usa o **preço da outra classe listada**, com nota no hover ("ON sem negociação: avaliada pelo preço de PETR4"). PN em séries usa a série **de maior liquidez** nos últimos 2 meses. Sem nenhuma classe com preço, vazio com o motivo |
| D4 | Liquidez média diária: janela e cálculo | **Do ticker consultado**, média de **quantidade × fechamento** dos pregões dos **últimos 2 meses** (PETR4: R$ 2,33 bi × R$ 2,38 bi do Investidor10 — a diferença é do fechamento no lugar do preço médio do dia). Janela no `application.yml` da app-api |
| D5 | Quais datas aparecem no card | Rodapé com "Balanço de 30/06/2026 · preço de 16/10/2026" e o relógio de dado antigo (mesmos limites da listagem) quando um deles passar do limite |
| D6 | FII: o card muda também? | **Sim, "Dados do fundo"**: valor de mercado (cotas emitidas × preço), PL do informe, nº de cotas, ativos do informe e liquidez média diária, mais os campos de hoje (CNPJ, situação, site, segmento, administrador) |
| D7 | Valores ≥ 1 trilhão | Manter o padrão do app (**R$ 1.279,02 bi**), como nos Demonstrativos e na listagem; o detalhado mostra o número inteiro |

**Demonstrativos**

| # | Pergunta | Recomendação |
|---|---|---|
| D8 | Coluna do **ano em andamento** (2026, com balanço até 30/06) no Anual | **Acumulado do ano**, marcado no cabeçalho como "2026 · 6m" e com "acumulado de jan a jun/2026" no hover; posição (PL, ativo, caixa, dívida, ações) = último balanço. Alternativas: 12 meses até o último trimestre (mistura com 2025) ou só mostrar 2026 depois do DFP |
| D9 | Exercício fora de dezembro (7 companhias) | A coluna é o ano em que o exercício termina (exercício mar/2025–fev/2026 = 2026) |
| D10 | Quantidade de colunas no Anual | Igual ao Trimestre: **Últimos 8** \| **Todos (n)** |
| D11 | Onde vale Simples \| Detalhado | Nos Demonstrativos (ações **e** informe mensal do FII) e no card Dados da empresa, cada card com o seu seletor, padrão Simples, sem lembrar a escolha. Controle em botões como os vizinhos (Trimestre \| Anual), na posição marcada em verde no anexo 1 |
| D12 | O que muda no Detalhado | Só o que hoje é abreviado: dinheiro vira `R$ 548.490.000.000,00`, quantidade vira `12.888.732.761`. Percentuais e R$ por cota (já completos) ficam iguais |
| D13 | Demonstrativos ganham as contas novas da 31.3? | **Sim, quatro linhas:** Lucro bruto e EBITDA (fluxo), Ativo circulante e Passivo circulante (posição). LAIR e IR/CSLL ficam só no cálculo do ROIC |

**Indicadores**

| # | Pergunta | Recomendação |
|---|---|---|
| D14 | O que é a janela 3A / 5A / MAX | Os **N últimos exercícios fechados + Atual** (5A em out/2026: 2021–2025 + Atual). No Trimestral, do 1º trimestre do primeiro ano da janela até o último balanço (1T21–2T26). **MAX** = toda a série (ações desde 2010, FII desde 2017). Média = pontos anuais da janela + Atual, com a regra de hoje (nulo fora, zero dentro) |
| D15 | Seletor por cartão ou um para todos | **Um por cartão**, como no anexo 2, independentes, padrão 5A, sem lembrar a escolha. O modal abre na janela do cartão e guarda a sua ao navegar com ‹ › |
| D16 | Organização dos 30 cartões de ações | Em **grupos com subtítulo**: *Valuation* (P/L, P/VP, DY, PSR, P/EBIT, P/EBITDA, EV/EBIT, EV/EBITDA, P/Ativo, P/Cap. Giro, P/Ativo Circ. Líq., LPA, VPA); *Endividamento* (Dív. Líq./EBIT, Dív. Líq./EBITDA, Dív. Bruta/PL, PL/Ativos, Passivos/Ativos, Liquidez Corrente); *Eficiência* (Margem Bruta, Margem EBITDA, Margem EBIT, Margem Líquida); *Rentabilidade* (ROE, ROA, ROIC, Giro Ativos, Payout); *Crescimento* (CAGR Receitas 5a, CAGR Lucros 5a). FII sem grupos (7 cartões, como hoje) |
| D17 | Bancos e seguradoras (31.4) | **Esconder** os cartões que não se aplicam (cerca de 15 por banco), com uma linha "Indicadores de margem, EBIT/EBITDA, dívida e capital de giro não se aplicam a bancos e seguradoras". Alternativa: cartão com "—" e o motivo |
| D18 | P/X e EV: por ticker ou por empresa | **P/Receita, P/EBIT, P/EBITDA, P/Ativo, P/Cap. Giro e P/Ativo Circ. Líq. por ticker** (preço ÷ valor por ação, como P/L e P/VP — mudam entre PETR3 e PETR4). **EV por empresa** (valor de mercado de D3 + dívida líquida) — EV/EBIT e EV/EBITDA iguais nos dois tickers e coerentes com o "Valor de firma" do card |
| D19 | Base de ROA e ROIC | **Média do início e do fim do período**, como o ROE. ROA = lucro líquido 12m ÷ ativo total médio. ROIC = EBIT 12m × (1 − IR/CSLL ÷ LAIR) ÷ (PL + dívida líquida) médio; alíquota fora de 0–100% vira nulo com o motivo |
| D20 | CAGR 5 anos | Ponto anual = CAGR dos 5 exercícios que terminam nele; Atual = 12 meses atuais × 12 meses de 5 anos antes. Vazio com valor negativo ou zero no início ou no fim (31.2) |
| D21 | Indicadores novos na listagem (31.5) | **Fora deste escopo** — a listagem acabou de ser fechada; vira pendência (colunas e filtros opcionais) |

---

## 3. Regras

### 3.1 Fórmulas dos indicadores novos (ações)

Base: último balanço (Atual) ou o balanço do ponto (histórico), 12 meses = TTM de `company_quarters`, preço do ticker
na data, quantidade ajustada por eventos (as regras de hoje do P/L e do P/VP). "Médio" = média do início e do fim do
período (balanço de 12 meses antes, folga de 20 dias, como o ROE).

| Indicador | Fórmula | Unidade | Origem |
|---|---|---|---|
| P/Receita (PSR) | preço ÷ (receita 12m ÷ ações) | razão | 31.2 |
| P/EBIT | preço ÷ (EBIT 12m ÷ ações); nulo com EBIT ≤ 0 | razão | 31.2 |
| P/EBITDA | preço ÷ (EBITDA 12m ÷ ações); nulo com EBITDA ≤ 0 | razão | 31.3 |
| EV/EBIT | (valor de mercado + dívida líquida) ÷ EBIT 12m | razão | 31.2 |
| EV/EBITDA | (valor de mercado + dívida líquida) ÷ EBITDA 12m | razão | 31.3 |
| P/Ativo | preço ÷ (ativo total ÷ ações) | razão | 31.2 |
| P/Cap. Giro | preço ÷ ((ativo circ. − passivo circ.) ÷ ações); nulo com capital de giro ≤ 0 | razão | 31.3 |
| P/Ativo Circ. Líq. | preço ÷ ((ativo circ. − passivo total) ÷ ações), passivo total = ativo total − PL; nulo com base ≤ 0 | razão | 31.3 |
| Dív. Líq. / EBIT | (dívida bruta − caixa) ÷ EBIT 12m | razão | 31.2 |
| Dív. Líq. / EBITDA | (dívida bruta − caixa) ÷ EBITDA 12m | razão | 31.3 |
| Dív. Bruta / PL | dívida bruta ÷ PL | razão | 31.2 |
| PL / Ativos | PL ÷ ativo total | % | 31.2 |
| Passivos / Ativos | (ativo total − PL) ÷ ativo total | % | 31.2 |
| Liquidez Corrente | ativo circ. ÷ passivo circ. | razão | 31.3 |
| Margem Bruta | lucro bruto 12m ÷ receita 12m | % | 31.3 |
| Margem EBITDA | EBITDA 12m ÷ receita 12m | % | 31.3 |
| Margem EBIT | EBIT 12m ÷ receita 12m | % | 31.2 |
| Margem Líquida | lucro líquido 12m ÷ receita 12m | % | 31.2 |
| ROA | lucro líquido 12m ÷ ativo total médio (D19) | % | 31.2 |
| ROIC | EBIT 12m × (1 − IR/CSLL ÷ LAIR) ÷ (PL + dívida líquida) médio (D19) | % | 31.3 |
| Giro Ativos | receita 12m ÷ ativo total médio | razão | 31.2 |
| CAGR Receitas 5a | (receita 12m ÷ receita 12m de 5 anos antes)^(1/5) − 1 (D20) | % | 31.2 |
| CAGR Lucros 5a | idem com o lucro líquido 12m (D20) | % | 31.2 |

EBITDA = EBIT + depreciação, amortização e exaustão (DVA 7.04.01). Nulo sempre com o motivo no hover, nunca zero
inventado. Bancos e seguradoras: os que dependem de EBIT/EBITDA, dívida, margens e capital de giro ficam "não se
aplica" (D17); P/Receita, P/Ativo, ROA, PL/Ativos, Passivos/Ativos, Margem Líquida e CAGRs valem para eles.

### 3.2 Contas novas da CVM (31.3)

| Métrica | Conta | Demonstração | Uso |
|---|---|---|---|
| `LUCRO_BRUTO` | 3.03 | DRE | margem bruta, demonstrativos |
| `DEPRECIACAO_AMORTIZACAO` | 7.04.01 | DVA | EBITDA |
| `LAIR` | 3.07 | DRE | ROIC |
| `IR_CSLL` | 3.08 | DRE | ROIC |
| `ATIVO_CIRCULANTE` | 1.01 | BPA | liquidez, capital de giro, card, demonstrativos |
| `PASSIVO_CIRCULANTE` | 2.01 | BPP | idem |

Só layout comercial para 3.03, 7.04.01, 1.01 e 2.01 (banco e seguradora têm outro plano de contas). `company_quarters`
ganha as colunas de fluxo (`_q` e `_ttm`) e de posição, nulas até o reprocessamento (a tabela é derivada e refeita
inteira — mesma situação da V45).

### 3.3 Valor de mercado (D3)

`ações ON (sem tesouraria) × preço ON + ações PN (sem tesouraria) × preço PN`, quantidade do último `share_counts`
ajustada pelos eventos societários posteriores (a regra das ações em circulação de hoje). No histórico, os preços de
cada classe na data do balanço. Classe sem cotação e séries PN conforme D3. Unit não entra na conta (as classes que a
compõem já entram).

### 3.4 Demonstrativos no Anual (D8–D10)

- Uma coluna por exercício fechado (linha do DFP): fluxo = 12 meses do exercício; posição = balanço do fim do exercício.
- Ano em andamento: fluxo acumulado desde o início do exercício (soma dos trimestres isolados); posição = último balanço.
- Calculado no backend (novo `GET /api/v1/issuers/{id}/years`), não no web.

### 3.5 Janelas dos indicadores (D14)

O histórico continua vindo inteiro numa chamada (as sparklines de todos os cartões); o DTO ganha as **médias por
janela** (`averages: { Y3, Y5, MAX }`) calculadas no backend com `IndicatorRules.average`, e o web recorta os pontos
pela janela. `companyAverage` continua = MAX (compatibilidade). O rodapé do cartão passa a "média 5A 9,87 · desde 2021".

---

## 4. Backend

### 4.1 adm-api — contas novas (31.3)

| Peça | Mudança |
|---|---|
| `CvmStatements.wanted` | BPA passa a aceitar `1.01`; BPP `2.01` e DRE/DVA já entram |
| `FinancialMetric` / `FinancialMetricRules` | as 6 métricas da 3.2, só no layout comercial onde indicado |
| `QuarterSeries` / `CompanyQuarterService` | fluxos novos com trimestre isolado e 12 meses; posições novas |
| Migration `V60__company_quarters_contas_novas.sql` (core) | colunas novas em `company_quarters`, nulas (tabela derivada — regra 8.1 não se aplica: não há valor default a informar, a carga preenche) |
| Reprocessamento | `POST /api/v1/admin/cvm/import?from=2010&to=2026&force=true` (~7 min, sem download), executado pelo Claude (5.3) |

### 4.2 app-api — indicadores

| Peça | Mudança |
|---|---|
| `Indicator` | 23 códigos novos (grupo do D16 no próprio enum, para o web ordenar) |
| `IndicatorRules` / `CompanyBasis` | fórmulas da 3.1; `CompanyBasis` ganha receita, EBIT, EBITDA, lucro líquido, ativo total (início e fim), caixa, dívida, circulantes, LAIR, IR, valor de mercado |
| `IndicatorService.Context` | preços **das outras classes** do emissor (fechamentos na data, para o valor de mercado) — também no lote da listagem (`currentAll`), que segue usando o mesmo `currentOf` |
| `IndicatorHistoryDTO` | `averages` por janela (3.5) |
| Listagem | sem colunas novas (D21); conferir o tempo da aba Ações com o cálculo maior |

### 4.3 app-api — Dados da empresa e Anual

| Endpoint | Retorno |
|---|---|
| `GET /api/v1/assets/{ticker}/company-data` | valor de mercado (com nota da D3), valor de firma, PL, nº de papéis, ativos, ativo circulante, dívida bruta, dívida líquida, disponibilidade, liquidez média diária, data do balanço/informe e do preço, sinais de dado antigo. FII: o conjunto da D6 |
| `GET /api/v1/issuers/{id}/years` | linhas anuais (3.4) com `fiscalYear`, `periodEnd`, `partial` e `months` (para o "2026 · 6m") |
| `GET /api/v1/issuers/{id}/quarters` | ganha as 4 contas da D13 |

---

## 5. Web

| Componente | Mudança |
|---|---|
| `IssuerRegistry` → `CompanyData` ("Dados da empresa" / "Dados do fundo") | quadro de valores no estilo do anexo 4 (rótulo, valor e ⓘ com a fórmula), campos de cadastro de hoje embaixo, seletor Simples \| Detalhado, rodapé com as datas (D5) |
| `FinancialStatements` | Trimestre \| **Anual** (12 meses sai); Simples \| Detalhado; 4 linhas novas (D13); rodapé ajustado ("Anual: exercício fechado; ano em andamento acumulado") |
| `IndicatorGrid` | grupos (D16), cartões novos, seletor 3A \| 5A \| MAX no lugar marcado no anexo 2, sparkline e média pela janela, cartões escondidos em banco/seguradora (D17) |
| `IndicatorHistoryModal` | seletor de janela à esquerda de Anual \| Trimestral (anexo 3); barras, tabela e linha da média pela janela |
| `utils/indicators.ts` | metadados (rótulo, nome, fórmula) dos 23 indicadores; `formatFull` para o Detalhado |
| Componente comum | o `Toggle` dos Demonstrativos vira componente de `ui/` (usado em quatro lugares) |

---

## 6. Testes

| Teste | O que prova |
|---|---|
| `FinancialMetricRulesTest` / `QuarterSeriesTest` (adm-api) | as 6 contas novas no comercial, ausentes em banco e seguradora, trimestre isolado e 12 meses do EBITDA e do lucro bruto |
| `IndicatorRulesTest` (app-api) | cada fórmula da 3.1 com os nulos e motivos (EBIT ≤ 0, alíquota fora de 0–100%, CAGR com base negativa, não se aplica) |
| `IndicadoresIT` | indicadores novos no Atual e no histórico; médias 3A/5A/MAX; valor de mercado com classe sem cotação e PN em séries |
| `DadosEmpresaIT` (novo) | `company-data` de ação, unit, banco e FII; liquidez na janela; dado antigo |
| `DemonstrativosAnuaisIT` (novo) | `years` com exercício fechado, ano em andamento e exercício fora de dezembro |
| Conferência na base real (validação) | PETR4 contra a tabela 1.2; VALE3, ITUB4 (banco) e um FII; listagem com o mesmo Atual da página e o tempo da aba |

---

## 7. Etapas

| Etapa | Entrega | Depende |
|---|---|---|
| **A1** | adm-api: contas novas (4.1), V60, testes, reprocessamento e conferência das contas na base | — |
| **A2** | app-api: indicadores novos, valor de mercado por classe, médias por janela (4.2) + testes | A1 |
| **A3** | app-api: `company-data`, `years` e as contas novas em `quarters` (4.3) + testes | A1 |
| **A4** | Web: Demonstrativos (Anual, Simples \| Detalhado, linhas novas) | A3 |
| **A5** | Web: Dados da empresa / do fundo | A3 |
| **A6** | Web: Indicadores (grupos, cartões novos, 3A/5A/MAX no cartão e no modal) | A2 |
| **A7** | Documentação: `Claude.md` (Ativos em Telas, endpoints, contas da CVM), `docs/PRIMEVEST_DB.md`, `docs/ADM_TASKS.md`, `docs/PENDENCIAS.md` (31 resolvida, free float/tag along e 31.5 como pendências), `README.md`, andamento deste plano | A1–A6 |
| **A8** | Validação: compilar, bateria (core, adm-api, app-api), lint e build do web, `docker compose up -d`, conferência da 1.2 e da listagem, conferência no navegador com o Guilherme logado | A1–A7 |

### Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| T1–T5 | registrou | decidiu (18/10) |
| D1–D21 | preparou com recomendação | decide |
| A1–A7 | implementa, testa e roda o reprocessamento | — |
| A8 validação | compila, testa, sobe, confere | confere a tela logado |

---

## 8. Riscos e limitações

- **Balanços com contas zeradas** (`docs/PENDENCIAS.md` 32.2, TIMS3 e mais uma): os indicadores novos herdam o
  problema; a investigação tem escopo próprio.
- **Contas por código:** 3.03, 7.04.01, 1.01 e 2.01 são estáveis no plano de contas comercial da CVM, mas companhias
  com DRE fora do padrão (holdings, DRE sem lucro bruto) ficam com o indicador nulo e o motivo.
- **Liquidez aproximada:** o COTAHIST guarda a quantidade, não o volume financeiro; quantidade × fechamento difere do
  volume real em dias de muita oscilação (PETR4: −2% contra o Investidor10).
- **Valor de mercado com classe sem cotação** é estimado pela outra classe (D3) — a nota no hover deixa isso claro.
- **Custo da listagem:** o lote passa a ler os preços das outras classes e calcula 30 indicadores por ação; o tempo da
  aba Ações é conferido na A8.
- **Tela mais longa:** 30 cartões de ações; os grupos (D16) e o esconder em bancos (D17) são o que mantém a leitura.

---

## 9. Andamento

| Etapa | Estado |
|---|---|
| Planejamento | **18/10/2026** — plano escrito; T1–T5 registradas; aguarda D1–D21 e a ordem de execução |
