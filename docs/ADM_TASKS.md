# PrimeVest — Endpoints administrativos

> Gerado em 02/10/2026 a partir de `AdminController` (adm-api) e `ExtratoProventosController` (app-api) · revisado em
> 03/10/2026 (Markdown, caminhos `carga/`).

| Tema | Como funciona |
|---|---|
| **Acesso** | JWT de usuário com `users.is_admin = true`. Sem token: 401. Usuário comum: 403 "Somente administrador.". O login é sempre na app-api (`POST /api/v1/auth/login`, porta 8080); a adm-api (8081) só valida o mesmo token |
| **Execução** | as **rotinas** rodam em segundo plano (thread virtual) e respondem na hora `202 Accepted` com `{"status":"iniciada","rotina":"<nome>","resultado":"no log da API"}`. O resultado sai no log: `docker compose logs -f adm-api`. Exceções síncronas: `fii-cnpjs`, `assets/{id}/issuer`, `assets/{id}/unit-composition` e `proventos/extrato` |
| **Idempotência** | todas as rotinas podem ser rodadas de novo: o que já está gravado é pulado ou regravado com o mesmo valor; o que já está em `carga/data/` não é baixado de novo |
| **Swagger** | http://localhost:8081/swagger-ui.html |

**Disparo** pelo script do projeto (usa `PRIMEVEST_SENHA` do `.env`):

```bash
node carga/scripts/admin.mjs <rotina>
docker compose --profile carga run --rm tools node carga/scripts/admin.mjs <rotina>   # sem Node na máquina

# exemplos
node carga/scripts/admin.mjs cvm/import
node carga/scripts/admin.mjs assets/123/unit-composition --method PUT --body '{"ON":1,"PNA":2}'
```

## Resumo

Prefixo das rotas: `/api/v1/admin`.

| # | Método | Rota | API | Resposta | Grupo |
|---|---|---|---|---|---|
| 01 | POST | `/bootstrap` | adm | 202 | Carga inicial |
| 02 | POST | `/issuers/import` | adm | 202 | Emissores |
| 03 | POST | `/issuers/link` | adm | 202 | Emissores |
| 04 | POST | `/issuers/fii-cnpjs` | adm | 200 | Emissores (curadoria) |
| 05 | PUT | `/assets/{assetId}/issuer` | adm | 204 | Emissores (curadoria) |
| 06 | PUT | `/assets/{assetId}/unit-composition` | adm | 204 | Emissores (curadoria) |
| 07 | POST | `/b3/cotahist?from&to` | adm | 202 | Preços |
| 08 | POST | `/closing` | adm | 202 | Preços |
| 09 | POST | `/proventos` | adm | 202 | Proventos |
| 10 | POST | `/cvm/proventos?from&to` | adm | 202 | Proventos (datas) |
| 11 | POST | `/proventos/levantamento` | adm | 202 | Proventos (datas) |
| 12 | POST | `/cvm/import?from&to&force` | adm | 202 | Balanços |
| 13 | POST | `/cvm/quarters` | adm | 202 | Balanços |
| 14 | POST | `/cvm/fii-monthly?from&to&force` | adm | 202 | Informe FII |
| 15 | POST | `/logos` | adm | 202 | Logos |
| 16 | POST | `/catalog-audit` | adm | 202 | Conferência |
| 17 | POST | `/proventos/extrato` | **app** | 200 | Carteira (conferência) |

---

## Carga inicial

### [01] `POST /api/v1/admin/bootstrap`

**Serviço:** `BootstrapService.run`

**Função.** Carga inicial num banco vazio, em ordem (emissores do catálogo, ligações e units já vêm do seed V30):

| Etapa | O que faz | Mesma rotina de |
|---|---|---|
| 1 | emissores (CVM: cadastro, FCA, informe) | [02] |
| 2 | COTAHIST 2010..ano corrente (preços + ISIN) | [07] |
| 3 | proventos (B3 12 meses + acumulado, eventos societários, histórico longo B3/FNET) | [09] |
| 4 | datas de pagamento CVM 2022+ | [10] |
| 5 | datas de pagamento do levantamento | [11] |
| 6 | balanços DFP/ITR 2010+ e série trimestral | [12] |
| 7 | informe mensal de FII 2016+ | [14] |

Etapa que falha é registrada e a seguinte roda assim mesmo. Uma carga por vez. Termina no log com
`Carga inicial (...) concluida` e o resumo de cada etapa.

**Propósito.** Subir uma máquina nova só com o repositório + bucket (`data pull`) e chegar ao catálogo completo.
Nunca dispara sozinha ao subir.

**Pré-requisito.** `carga/data/` baixada do bucket (`docker compose --profile carga run --rm data pull`) e as APIs no
ar (migrations aplicadas).

---

## Emissores

### [02] `POST /api/v1/admin/issuers/import`

**Serviço:** `IssuerImportService.importAll` · **Grava:** `issuers`

**Função.** Companhias (cadastro da CVM + FCA dos 3 últimos anos: ativas em bolsa e as que têm ticker com raiz de ação
do catálogo), FIIs (informe mensal do ano corrente e anterior, negociados em bolsa) e classificação setorial da B3
(setor/subsetor/segmento, só para quem ainda não tem). Atualiza pelo CNPJ; nunca toca o segmento curado do FII.

**Propósito.** Cadastro de emissores para ligar ativos e importar balanços.

### [03] `POST /api/v1/admin/issuers/link`

**Serviço:** `IssuerLinkService.linkAll` · **Grava:** `assets.issuer_id / share_class / issuer_link_source`,
`unit_compositions`

**Função.** Liga ativo → emissor. Ações: FCA (ticker → CNPJ), raiz do ticker (KLBN11 leva KLBN3/KLBN4), B3 (raiz →
código CVM); fracionário segue o lote padrão; unit ganha composição pelo texto do FCA. FIIs: ISIN (COTAHIST) → informe
da CVM → CNPJ. Ligação `MANUAL` nunca é sobrescrita.

**Propósito.** Associar cada ativo à empresa/fundo para indicadores e proventos.

### [04] `POST /api/v1/admin/issuers/fii-cnpjs` — síncrono

**Corpo:** `[ {"ticker":"XPML11","cnpj":"..."}, ... ]` · **Serviço:** `IssuerLinkService.applyCuratedFunds`

**Resposta:** 200 com a situação de cada linha (confirmada, emissor criado, ticker fora do catálogo...).

**Função.** Aplica uma lista curada de CNPJs de FII: liga como `MANUAL` e cria o emissor que faltar.

**Propósito.** Corrigir o que o ISIN não resolve ou resolve errado (ex.: APTO11, XPML11 no informe de 2026).

### [05] `PUT /api/v1/admin/assets/{assetId}/issuer` — síncrono

**Corpo:** `{"issuerId": 10, "cnpj": null, "shareClass": "ON"}` · **Serviço:** `IssuerLinkService.setManual`

**Resposta:** 204; 404 se ativo/emissor não existir.

**Função.** Liga manualmente um ativo a um emissor (por `issuerId` ou por CNPJ — companhia fora dos emissores é
procurada no cadastro da CVM). Sem `issuerId` e sem `cnpj`: desfaz a ligação.

**Propósito.** Curadoria pontual de ligação.

### [06] `PUT /api/v1/admin/assets/{assetId}/unit-composition` — síncrono

**Corpo:** `{"ON": 1, "PNA": 2}` · **Serviço:** `IssuerLinkService.setUnitComposition`

**Resposta:** 204; 404 ativo inexistente; 422 estado inválido.

**Função.** Grava a composição manual de uma unit e marca a ligação `MANUAL`.

**Propósito.** Units cujo texto do FCA não dá a composição certa.

---

## Preços

### [07] `POST /api/v1/admin/b3/cotahist?from=2010&to=<ano corrente>`

**Serviço:** `HistoricalQuotesImportService.importYears` · **Validação:** `from >= 1986` e `to >= from`, senão 400 ·
**Grava:** `asset_price_history` (source `B3_COTAHIST`), `assets.isin`

**Função.** Série histórica oficial da B3 para ACAO (lote padrão e fracionário) e FII do catálogo; ticker antigo com o
mesmo ISIN entra no ativo atual. A série da B3 vence a BRAPI e não é sobrescrita depois. Zips em
`carga/data/b3/cotahist` (ano fechado em disco não vai à rede).

**Propósito.** Histórico de preço desde 2010 (gráfico de cotação, indicadores históricos) e o ISIN de cada ativo (usado
pelos proventos).

### [08] `POST /api/v1/admin/closing`

**Serviço:** `ClosingPriceService.runB3Closing` + `runCryptoClosing`

**Função.** Mesma rodada das 19:00 (BRAPI para B3, CoinGecko para cripto), na hora. Publica "catálogo mudou"
(`FECHAMENTO`).

**Propósito.** Atualizar preços fora do horário (ex.: API estava desligada).

---

## Proventos

### [09] `POST /api/v1/admin/proventos`

**Serviço:** `ProventosCatalogService.syncAll` · **Grava:** `asset_cash_events`, `asset_stock_events`;
`carga/data/b3`, `carga/data/fnet`

**Função.** Mesma rodada das 20:00 (sem a etapa da CVM): proventos e eventos societários da B3 por emissor, FNET como
fonte principal do FII, histórico longo. Publica "catálogo mudou" (`PROVENTOS`).

**Propósito.** Reprocessar proventos sob demanda.

### [10] `POST /api/v1/admin/cvm/proventos?from=2022&to=<ano corrente>`

**Serviço:** `CvmPaymentDateService.run` · **Validação:** `from >= 2022` e `to >= from`, senão 400 · **Grava:**
`asset_cash_events.payment_date` (`payment_date_source = 'CVM'`); `carga/data/cvm/ipe`, `carga/data/cvm/proventos`

**Função.** Lê os "Relatórios Proventos" da CVM e completa a data de pagamento dos eventos **sem** data que batem em
ISIN, data-com e valor (parcelas na ordem). A data da própria fonte (B3) substitui depois.

**Propósito.** Catálogo com a data de pagamento completa (ações, 2022+).

### [11] `POST /api/v1/admin/proventos/levantamento`

**Serviço:** `PaymentDateSurveyService.run` · **Entrada:** `carga/data/levantamento/datas-pagamento.json` (gerado da
planilha por `carga/scripts/levantamento/xlsx_para_json.py`) · **Grava:** `asset_cash_events.payment_date`
(`payment_date_source = 'LEVANTAMENTO'`)

**Função.** Completa só proventos sem data, casando ticker, tipo, data-com, parcela e valor; não passa por cima de B3,
FNET nem CVM; linha `A_DEFINIR` não grava nada. Mesma etapa do bootstrap.

**Propósito.** Datas de pagamento que nenhuma fonte automática traz (2010–2021), aplicadas num banco que já existe.

### [17] `POST /api/v1/admin/proventos/extrato` — app-api, síncrono

**Serviço:** `ExtratoProventosImportService.importFor(usuário logado)` · **Corpo:** linhas do extrato de proventos da
Área do Investidor da B3

**Resposta:** 200 com a situação de cada linha (`JA_EXISTE`, sem data, sem evento, recusada...).

**Função.** **Só conferência** (desde 29/09/2026): compara o extrato com os proventos da carteira de quem chama. Nada é
gravado — evento de catálogo vem de integração.

**Propósito.** Conferir a carteira contra a B3. Fica na app-api por ser dado de usuário; continua exigindo admin até
virar tela (escopo de importações).

---

## Balanços e informe de FII

### [12] `POST /api/v1/admin/cvm/import?from=2010&to=<ano corrente>&force=false`

**Serviço:** `FinancialStatementImportService.importYears` · **Validação:** `from >= 2010` e `to >= from`, senão 400 ·
**Grava:** `cvm_documents`, `cvm_statement_values`, `share_counts` e, ao fim, `company_quarters`

**Função.** DFP e ITR das companhias emissoras (consolidado ou, na falta, individual); métricas por descrição de conta;
quantidade de ações (composição 2020+ com escala conferida, ou FRE antes de 2020). Mesma versão é pulada;
reapresentação substitui. `force=true` reprocessa a partir de `carga/data/cvm` sem baixar de novo.

**Propósito.** Base dos indicadores fundamentalistas.

### [13] `POST /api/v1/admin/cvm/quarters`

**Serviço:** `CompanyQuarterService.rebuild` · **Grava:** `company_quarters` (recalculada inteira)

**Função.** Refaz a série trimestral (trimestre isolado, 12 meses, posição) a partir dos balanços já gravados. Leva
segundos.

**Propósito.** Depois de corrigir quantidade de ações à mão (a carga [12] já faz isso sozinha ao terminar).

### [14] `POST /api/v1/admin/cvm/fii-monthly?from=2016&to=<ano corrente>&force=false`

**Serviço:** `FiiMonthlyImportService.importYears` · **Validação:** `from >= 2016` e `to >= from`, senão 400 ·
**Grava:** `fii_monthly`

**Função.** Informe mensal de FII da CVM, só fundos emissores (por CNPJ). Mês com versão igual ou maior é pulado;
`force=true` regrava a partir de `carga/data/cvm/fii`.

**Propósito.** Indicadores de FII (VP por cota, P/VP, DY, cotistas...).

---

## Logos e conferência

### [15] `POST /api/v1/admin/logos`

**Serviço:** `AssetLogoService.sync(false, ...)` · **Grava:** `carga/data/logos/{TICKER}.{ext}`

**Função.** Busca de novo o logo de **todos** os ativos (a rotina mensal busca só quem não tem). Busca sem resultado não
apaga o arquivo existente.

**Propósito.** Renovar logos trocados pelas empresas.

### [16] `POST /api/v1/admin/catalog-audit`

**Serviço:** `CatalogAuditService.run` · **Grava:** nada (linhas WARN no log)

**Função.** Mesma conferência das segundas 07:00: tickers novos, sumidos e renomeados contra a lista da BRAPI.

**Propósito.** Ver as diferenças do catálogo sem esperar a semana.
