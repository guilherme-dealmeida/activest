# Activest — Plano do ticker base (fracionário e papel inteiro)

> Fase: **Implementation, Testing e Review feitos em 21/11/2026** — E1 a E4 fechadas, bateria verde, conferência no
> dev e Review sem achados; falta o Commit (etapa 7, o git é do Guilherme).
> (REGRAS_CLAUDE.md 2.2: as etapas 1 a 3 não alteram código; a Implementation só começou com a autorização
> explícita do Guilherme em 21/11/2026.)
> Origem: pedido do Guilherme em 20/11/2026 (ESCOPO "Melhoria Activest ponytail"): auditoria de eficiência e
> duplicatas de código no projeto. Achado 1 da auditoria — a regra do "ticker base" (tirar o F do fracionário) está
> escrita **10 vezes**, em **3 variantes que discordam entre si**, e nenhuma delas trata o catálogo inteiro.
> Objetivo: **uma regra só**, com um dono no core, e os 7 tickers do catálogo que hoje são tratados de um jeito
> errado.
> Decisões: D1–D5 respondidas pelo Guilherme em 20/11/2026 (divisão em três escopos; regra unificada sem hífen;
> escopo C com backend + front + MapStruct).

---

## 1. Objetivo

O ticker do **fracionário** é o ticker do papel inteiro com um `F` no fim (`PETR4F` → `PETR4`). O sistema precisa
dessa transformação em dez lugares — para achar a série de preço do lote padrão, o logo, o ISIN do provento, a
série da linha do tempo e a data do último pregão na inativação do catálogo.

Hoje essa transformação está **reescrita em 10 lugares**, e as cópias **não são iguais**:

- **5 cópias** exigem um **dígito** antes do F (`PETR4F` → `PETR4`, `MRSA3BF` → **fica `MRSA3BF`**);
- **4 cópias** não exigem dígito, mas não olham o hífen (`MRSA3BF` → `MRSA3B`, `TRTN-F` → **`TRTN-`**);
- **1 cópia** (a da integração B3) usa regex e não olha o tamanho (`MRSA3BF` → **fica `MRSA3BF`**, `KVACUF` →
  `KVACU`).

O objetivo é: uma função só, no `activest-core`, com a regra que o catálogo prova, e as 10 (12 com as auxiliares)
cópias virando chamada. Sem mudança de tela, sem migration, sem mexer em dado gravado.

---

## 2. Discovery (20/11/2026)

### 2.1 As cópias (arquivo:linha, no código de produção)

| # | Arquivo:linha | Corpo | Família |
|---|---|---|---|
| A | `app-api/.../pricehistory/AssetPriceHistoryService.java:142` | `trim+upper`; tira F se `endsWith("F") && len>5 && isDigit(charAt(len-2))` | dígito |
| B | `app-api/.../indicator/IndicatorService.java:879` | idêntico a A | dígito |
| C | `app-api/.../performance/PortfolioTimelineBuilder.java:543` | idêntico a A | dígito |
| D | `adm-api/.../dividend/DividendCatalogService.java:587` | idêntico a A | dígito |
| E | `adm-api/.../asset/AssetLogoService.java:249` | idêntico a A | dígito |
| F | `adm-api/.../issuer/IssuerLinkService.java:481` (`base`) | `trim+upper`; tira F se `len>5 && endsWith("F")` | sem dígito |
| G | `adm-api/.../asset/CatalogDelisting.java:155` (`base`) | tira F se `endsWith("F") && len>5` (sem `trim`/`upper`) | sem dígito |
| H | `adm-api/.../asset/CatalogAuditService.java:141` (inline) | idêntico a G | sem dígito |
| I | `integration/.../b3investidor/B3EquityImportRules.java:190` | regex `.*\dF` + `strip+upper` | dígito (regex) |
| J | `core/.../issuer/ShareClass.java:16` (dentro de `fromTicker`) | tira **qualquer** F final | sem dígito, sem tamanho |
| K | `adm-api/.../pricehistory/HistoricalQuotesImportService.java:291` (`isOddLot`) | `len>5 && endsWith("F")` — a **pergunta inversa** ("isto é fracionário?"), com outro nome | sem dígito |
| L | `core/.../issuer/ShareClass.java:16` + `B3EquityImportRules.java:190` | chamadas em `AssetResolver` e no `B3InvestidorConnector` | — |

### 2.2 O que o catálogo realmente tem (conferido no banco do dev, 20/11/2026)

| Situação | Quantos | Tirar o F? |
|---|---|---|
| Fracionário **com** lote padrão no catálogo | **384** | sim |
| Fracionário **só** no fracionário (o lote padrão não existe como ativo: `BMIN3F`, `CALI3F`, `CEDO3F`, `CEED4F`, `CRPG3F`, `DOHL3F`, `EKTR3F`, `ESTR3F`, `FIGE3F`, `FIGE4F`, `FRIO3F`, `FSPE11F`, `FSRF11F`, `FSTU11F`, `GPAR3F`, `MAPT4F`, `MGEL3F`, `MSPA3F`, `MTSA3F`, `ODER4F`, `PSVM11F`, `SNSY3F`, `TXRX3F`) | **24** | sim — o base fica órfão, e o código já trata isso (`IndicatorService.currentAll:179-186`, o `missingBases`) |
| Fracionário de **classe B** (`MRSA3BF`, `MRSA5BF`, `MRSA6BF`, `EQMA3BF`) | **4** | sim — o F vem depois de uma **letra** |
| Ticker **com hífen** (`TRTN-F`, `BANC-F`, `RITM-F`) | **3** (>5 chars; outros 15 com hífen têm ≤5 e já ficam) | **não** — `TRTN-` não é ticker |
| `KVACUF` | 1 (inativo, tipo REIT) | não — `KVACU` não existe |

No total: **867 tickers terminados em F** (724 ativos, de 43.287 ativos); 411 com 6 ou mais caracteres; 403 com
dígito antes do F; 445 com letra antes do F; 18 com hífen.

### 2.3 Nenhuma das regras existentes cobre o catálogo

| Regra | Acerto nos 384 | Nos 24 sem base | Nos 4 de classe B | Nos 3 com hífen |
|---|---|---|---|---|
| dígito (A–E, I) | ok | ok | **errado** (fica `MRSA3BF`) | ok (não tira) |
| sem dígito (F–H, J) | ok | ok | ok | **errado** (`TRTN-`) |

A regra que o catálogo pede é: **tirar o F final quando o ticker não tem hífen e o F vem depois de letra ou dígito**
(D1, 20/11/2026).

### 2.4 O que isso troca na prática (gravidade real, conferida no banco e no código)

O achado da auditoria foi apresentado como "bug". Conferido item a item, **a gravidade real é menor** — e vale
registrar antes de aprovar:

- **Logo:** os 4 fracionários de classe B **já têm arquivo** em `carga/data/logos/` (`MRSA3BF.svg`,
  `MRSA5BF.svg`, `MRSA6BF.svg`, `EQMA3BF.svg`), e o arquivo é o estado (`AssetLogos`). O 404 do BRAPI
  (`icons.brapi.dev/icons/MRSA3BF.svg`) só aconteceria com um **novo** fracionário de classe B, que ainda não
  existe.
- **Série de preço:** `AssetPriceHistoryService:69-79` usa o base **só se ele existir no catálogo**. Com a regra
  atual, `MRSA3BF` fica sem base e mostra a **própria** série do fracionário (835 pregões no dev); com a regra
  certa, passa a mostrar a do lote padrão `MRSA3B` (287 pregões) e o rótulo "preço do MRSA3B". Os outros 380
  fracionários já fazem isso. É **inconsistência entre pares**, não tela quebrada.
- **Proventos:** `DividendCatalogService` usa a mesma função para gravar e para ler (`:561` e `:565`), então é
  **autoconsistente** — o ISIN é gravado do mesmo jeito nos dois lados.
- **Inativação e auditoria do catálogo** (`CatalogDelisting:155`, `CatalogAuditService:141`, `IssuerLinkService:481`,
  `ShareClass.fromTicker`): aqui a família "sem dígito" erra os **3 com hífen** — `base("TRTN-F")` = `TRTN-`, que
  não é ticker, e a data do último pregão do papel inteiro não é achada.

**Conclusão do Discovery:** não há tela quebrada; há **7 tickers (4 de classe B + 3 com hífen) tratados de um jeito
inconsistente** e **12 cópias de uma regra** que precisam mudar junto a cada ajuste. A gravidade justifica o
escopo — é pequeno, é o primeiro a criar o padrão de "dono único" que os escopos B e C reaproveitam, e tem critério
de aceitação objetivo (a tabela do item 2.2). Mas **não é o achado mais grave da auditoria**: o pior caso real
está no escopo B.

---

## 3. Decisões (para o Guilherme)

| # | Pergunta | Opções | Recomendação | Resposta |
|---|---|---|---|---|
| **D1** | Qual regra oficial de "ticker base" entra no core? | (a) tira o F quando o ticker **não tem hífen** e o F vem depois de letra ou dígito; (b) manter a regra com dígito; (c) regra por catálogo (o base é o ticker sem F **se existir** como ativo) | **(a)**: cobre os 384, os 24 e os 4 de classe B, e preserva os 3 com hífen | **(a)** — 20/11/2026 |
| **D2** | Onde mora a regra? | (a) `com.activest.domain.asset.Tickers.base(String)` no **core**; (b) em cada módulo | **(a)**: o core é lido pelos quatro módulos, e é lá que já moram `AssetResolver`, `AssetTypeNames` e o `Normalizer` | **(a)** — 20/11/2026 |
| **D3** | O que fazer com as **12 chamadas** (10 `baseTicker` + `isOddLot` + `ShareClass.fromTicker`)? | (a) todas passam pela regra única; (b) só as 10 `baseTicker` | **(a)**: `isOddLot` é a pergunta inversa e usa o mesmo predicado; `ShareClass.fromTicker` é a pior cópia hoje (tira qualquer F) | **(a)** — 20/11/2026 |
| **D4** | `KVACUF` (inativo, REIT, sem base) | (a) a regra tira o F (`KVACU`) e o código trata base inexistente como hoje (24 casos iguais); (b) exceção no código | **(a)**: `KVACUF` está inativo e sem histórico de preço; a regra é a mesma dos 24 sem base, que já são tratados | **(a)** — 20/11/2026 |
| **D5** | Documentação | corrigir `PLANO_LISTAGEM_EMISSORAS.md:46` ("termina em F, mais de 5 caracteres") e o item do `Claude.md` que descreve a regra do fracionário | sim | **sim** — 20/11/2026 |

---

## 4. Impact

### 4.1 Componentes afetados

| Componente | Módulo | Mudança |
|---|---|---|
| `com.activest.domain.asset.Tickers` | core | **novo**: `base(String)` (tira o F) e `isOddLot(String)` (a pergunta inversa), com o predicado único |
| `AssetPriceHistoryService:142` | app-api | apaga o `baseTicker` local; chama `Tickers.base` |
| `IndicatorService:879` | app-api | idem |
| `PortfolioTimelineBuilder:543` | app-api | idem |
| `DividendCatalogService:587` | adm-api | idem |
| `AssetLogoService:249` | adm-api | idem |
| `IssuerLinkService:481` | adm-api | apaga o `base` local; chama `Tickers.base` |
| `CatalogDelisting:155` | adm-api | idem |
| `CatalogAuditService:141` | adm-api | idem (inline) |
| `B3EquityImportRules:190` | integration | apaga o `baseTicker` com regex; chama `Tickers.base` |
| `ShareClass.fromTicker:16` | core | passa a usar `Tickers.base` (hoje tira qualquer F) |
| `HistoricalQuotesImportService:291` (`isOddLot`) | adm-api | passa a usar `Tickers.isOddLot` |
| `PLANO_LISTAGEM_EMISSORAS.md:46`, `Claude.md` | docs | o texto da regra (D5) |

### 4.2 O que **não** muda

- **Nenhuma migration, nenhuma coluna, nenhum dado gravado.** `assets`, `asset_price_history` e `asset_cash_events`
  ficam como estão.
- **O significado de "fracionário"** no catálogo: os 384 pares seguem pares.
- **As 24 frações sem lote padrão**: continuam sem base no catálogo e continuam tratadas pelo `missingBases` do
  `IndicatorService`.
- **A lista de Ativos (aba Ações/FIIs)**: os números não mudam — o que muda é *qual* base os 4 de classe B
  usam (a deles próprios hoje, a do lote padrão depois).
- **A rotina de fechamento, o COTAHIST, a carga inicial**: `isOddLot` muda de forma, não de efeito (os 3 com
  hífen deixam de ser tratados como fracionário — e não são).

### 4.3 Riscos

| Risco | Mitigação |
|---|---|
| **Regressão nos 407 fracionários que hoje funcionam** (384 + 24 + os 3 com hífen que não são fracionário) | o teste do item 5.2 fixa as 5 famílias com os números do banco; qualquer mudança de resultado em um deles quebra a bateria |
| `ShareClass.fromTicker` mudar de comportamento (hoje tira qualquer F, inclusive de ticker de 3 letras) | `Tickers.base` só tira F com ≥6 caracteres e sem hífen — o `fromTicker` ganha o filtro; conferir os testes de `ShareClass` |
| `isOddLot` virar falso para `TRTN-F`/`BANC-F`/`RITM-F` (que passam a ser "papel inteiro") | é o comportamento certo: esses 3 não são fracionário. Conferir a carga do COTAHIST (`HistoricalQuotesImportService`) |

---

## 5. Planning

### E1 — A regra no core

`activest-core/src/main/java/com/activest/domain/asset/Tickers.java`:

```java
/** O ticker do papel inteiro a partir do fracionario: PETR4F -> PETR4. */
public static String base(String ticker) { ... }

/** O ticker e fracionario (o F final e o marcador): PETR4F -> true. */
public static boolean isOddLot(String ticker) { ... }
```

O predicado único: **terminar em `F`, ter 6 ou mais caracteres e não conter hífen**. O `base` é o ticker sem o `F`
final; o `isOddLot` é o predicado.

**Aceite:** a função existe no core, sem dependência de banco, e o `TickersTest` (E2) passa.

### E2 — O teste da regra

`activest-core/src/test/java/com/activest/domain/asset/TickersTest.java` — a tabela do item 2.2, com o resultado
esperado de cada família:

| Entrada | `base` | `isOddLot` | Por quê |
|---|---|---|---|
| `PETR4F` | `PETR4` | true | caso normal |
| `PETR4` | `PETR4` | false | papel inteiro |
| `MRSA3BF` | `MRSA3B` | true | classe B: F depois de letra |
| `EQMA3BF` | `EQMA3B` | true | classe B |
| `EKTR3F` | `EKTR3` | true | só existe no fracionário |
| `TRTN-F` | `TRTN-F` | false | hífen: não é fracionário |
| `BANC-F` | `BANC-F` | false | hífen |
| `KVACUF` | `KVACU` | true | letra antes do F (inativo no catálogo) |
| `petr4f ` | `PETR4` | true | espaço e minúsculas (o `trim`/`upper` de hoje) |
| `ABR-F` | `ABR-F` | false | hífen e 5 caracteres |

**Aceite:** o teste verde; e **a conferência de que as 5 famílias do item 2.2 continuam dando o resultado de hoje**
— 384 pares (o `base` do fracionário é o do lote padrão), 24 órfãos (o `base` não existe no catálogo, e o código
já trata), 3 com hífen (o `base` é o próprio ticker) e 4 de classe B (o `base` passa a ser o do lote padrão — **a
mudança**).

### E3 — As 12 chamadas passam pela regra

Apagar as 10 cópias locais e as 2 auxiliares, e chamar `Tickers.base` / `Tickers.isOddLot`. Cuidado em três
lugares:

1. `IssuerLinkService.base` e `CatalogDelisting.base` **não fazem `trim`/`upper`** hoje — a regra nova normaliza
   (maiúsculas). Conferir que nenhum chamador dependia do texto cru.
2. `ShareClass.fromTicker` hoje tira **qualquer** F final; passa a usar `Tickers.base`.
3. `B3EquityImportRules.baseTicker` é `public` e usada pelo `B3InvestidorConnector` — vira delegação.

**Aceite:** `grep -rn "baseTicker\|static String base(" activest-*/src/main` devolve **só** o `Tickers`; a bateria
verde (`mvn clean verify`).

### E4 — Bateria, conferência e documentação

- `mvn clean verify` verde (core, integration, adm-api, app-api) — a contagem de testes de hoje está no
  `Claude.md` ("Testes").
- `npm test` e `npm run lint` verdes (o front não muda, mas a regra atravessa a API).
- **Conferência no dev**: na tela de Ativos, abrir `MRSA3BF` e `EQMA3BF` e ver o rótulo "preço do MRSA3B" /
  "preço do EQMA3B" (o `priceTicker`); conferir `PETR4F` (não muda) e `TRTN-F` (não é fracionário).
- `Claude.md` e `PLANO_LISTAGEM_EMISSORAS.md:46` com a regra nova (D5).

---

## 6. Quem faz

| O quê | Quem |
|---|---|
| Decisões D1–D5 e aprovação do plano | Guilherme |
| E1–E4, reload do backend, testes, conferência | Claude |
| Commit (branch sugerida `TRACK_01-ticker_base`) | Guilherme |

---

## 7. Andamento

- 20/11/2026 — Discovery, Impact e Planning feitos; plano apresentado.
- 20/11/2026 — Guilherme: três escopos (A = este, B = consultas repetidas, C = duplicação e lean); D1 = (a), a
  regra unificada sem hífen; D2 = (a), dono no core; D3 = (a), as 12 chamadas; D4 = (a); D5 = sim.
- 20/11/2026 — Gravidade conferida no banco do dev: **não há tela quebrada**; são 7 tickers tratados de um jeito
  inconsistente (4 de classe B, 3 com hífen) e 12 cópias de uma regra. O achado mais grave da auditoria está no
  escopo B (consultas repetidas).
- 21/11/2026 — **Implementation autorizada** ("pode seguir com ele para a próxima etapa"). **E1**: `Tickers` criado
  em `activest-core` (`com.activest.domain.asset.Tickers`, com `base` e `isOddLot`; o predicado é F final, 6 ou
  mais caracteres, sem hífen e depois de letra ou dígito). **E2**: `TickersTest` (core) com as 5 famílias do item
  2.2 — 29 testes. **E3**: as 10 cópias locais e as 2 auxiliares apagadas; as 12 chamadas passam por `Tickers`
  (`grep` por `baseTicker`/`static String base(` devolve só o `Tickers` e o `FiatCurrencies.base`, que é a moeda
  base). **E4**: `mvn clean verify` **verde em 6 min 58 s** — core 295 (era 266; +29 do `TickersTest`),
  integration 116, adm-api 171 + 110, app-api 242 + 319; Vitest 195 em 21 arquivos e lint 0/0 (o front não muda);
  app-api e adm-api recarregados no dev.
- 21/11/2026 — **Conferência no dev** (`GET /api/v1/assets/{ticker}/prices`): `MRSA3BF` → `priceTicker` **MRSA3B**
  (era MRSA3BF — a mudança), `EQMA3BF` → **EQMA3B**, `PETR4F` → PETR4 (igual), `EKTR3F` → EKTR3F (base não existe
  no catálogo, como os 24 órfãos), `TRTN-F` → **TRTN-F** (antes daria "TRTN-", que não é ticker), `TAEE11F` →
  TAEE11 (igual).
- 21/11/2026 — Os 18 tickers com hífen foram conferidos no catálogo: são **preferred stocks norte-americanas
  "Series F"** (`MET-F` = MetLife Series F, `PSA-F` = Public Storage Series F, `TRTN-F` = Triton Series F…), do
  tipo STOCK. Nenhum é fracionário — o hífen é o que separa o papel da série, e é por isso que a regra o exclui.
  Consequência do `isOddLot` novo: eles deixam de ser tratados como fracionário na carga do COTAHIST — mas o
  `TYPES` do `HistoricalQuotesImportService` é só B3 (ACAO, FII, ETF, BDR, Tesouro), e nenhum deles é do exterior,
  então **a carga não muda** para nenhum ticker do catálogo.
- 21/11/2026 — Documentação: `Claude.md` (bloco da última revisão + a seção "Ticker base — uma regra só" em
  Pontos de Atenção), `docs/PENDENCIAS.md` (item 52, com o que ficou dos escopos B e C) e
  `docs/planos/PLANO_LISTAGEM_EMISSORAS.md` (a regra do fracionário). O `README.md` não muda: é visão de produto e
  setup, e esta alteração é interna (a única diferença visível é o rótulo "preço do MRSA3B" em 4 ativos).
- 21/11/2026 — **Review pendente** (etapa 6) e Commit (etapa 7, branch sugerida `TRACK_01-ticker_base`; o git é
  do Guilherme).
- 21/11/2026 — **Review (etapa 6)**: as 12 chamadas estão no `Tickers` (o `grep` só acha `FiatCurrencies.base`, que é a
  moeda base); nenhum import sobrou (os métodos apagados só usavam `Character`, de `java.lang`); `base(null)` devolve
  null em vez de estourar NPE — nenhum chamador passa null, e o `isOddLot(null)` continua falso como antes (o
  `isOddLot` antigo tinha a guarda `ticker != null`). **O que muda de verdade:** só os 4 de classe B (base = do lote
  padrão) e os 3 com hífen de 6 caracteres (base = o próprio ticker, em vez de "TRTN-"). Conferido que **a carga do
  COTAHIST não muda** para nenhum ticker do catálogo — os 18 com hífen são do tipo STOCK, e o `TYPES` do
  `HistoricalQuotesImportService` é só B3 — e que **a ligação de emissores não muda** para nenhum ativo: os 4 de
  classe B já estão ligados ao mesmo emissor da classe A (issuer_id 16 e 73), com a mesma `share_class`. Sem migration,
  sem dado gravado, sem risco de segurança.
- 21/11/2026 — **Commit (etapa 7)**: alterações conferidas em leitura (`git status`, `git diff --stat`): 18 arquivos
  modificados (+113 −104) e 5 novos (`Tickers`, `TickersTest` e os 3 planos). Documentação atualizada (Claude.md,
  PENDENCIAS.md, PLANO_LISTAGEM_EMISSORAS.md). Branch atual no repositório: `develop` — o escopo pede uma
  `TRACK_01-ticker_base`. **Mensagem sugerida:** `TRACK_01 - Regra do ticker base no core`. Quem comita é o Guilherme.
