# PLANO — separação em módulos: `primevest-core`, `primevest-app-api`, `primevest-adm-api`

> **Nota (23/10/2026):** escrito quando o projeto se chamava **PrimeVest**. Nomes de módulo (`primevest-*`), pacote
> (`com.primevest`), configuração (`primevest.*`, `PRIMEVEST_*`), contêineres e versões de migration (V1–V67) citados aqui
> são os do repositório `primevest`; no Activest viraram `activest-*`, `com.activest`, `activest.*`/`ACTIVEST_*` e as
> migrations consolidadas V1–V6 (`docs/planos/PLANO_RENOMEACAO_ACTIVEST.md`).

> **ESCOPO:** separar a ingestão de dados de mercado (CVM, B3, FNET, BRAPI, CoinGecko) e as rotinas de
> administração num serviço próprio, `primevest-adm-api`, antes que as fases F7–F11 do
> docs/planos/PLANO_EMPRESAS_BALANCOS.md acrescentem mais código a elas.
> **ETAPA:** Planejamento (28/09/2026). Nada foi alterado no código.
> **Decisões (28/09/2026):** as recomendações da seção 7 foram aceitas, e o módulo `primevest-api` passa a se
> chamar **`primevest-app-api`** (A7).

---

> **Revisão de 28/09/2026 (seção 10): catálogo × carteira.** A adm-api grava só o catálogo (eventos,
> preços, emissores, balanços); a app-api é a única que grava dado do usuário (proventos, valorização). A seção 10
> prevalece sobre 3.2, 3.3, 3.4, 4.1, 4.2 e as decisões A2, A3 e A6.

## 1. Resumo

| | Hoje | Depois |
|---|---|---|
| Módulos Maven | 1 (`primevest-api`, 253 classes, 33 migrations) | 3 (`primevest-core`, `primevest-app-api`, `primevest-adm-api`), com `pom.xml` agregador na raiz |
| Executáveis | 1 contêiner `api` (8080) | `app-api` (8080) + `adm-api` (8081) — serviços e contêineres `primevest-app-api` e `primevest-adm-api` |
| Banco | 1 PostgreSQL | **o mesmo** — sem banco novo, sem sincronização |
| Login | JWT emitido pela `api` | o mesmo JWT; a `adm-api` só valida (mesmo `JWT_SECRET`) |
| Comportamento | — | **nenhuma mudança funcional**: a bateria atual (374 + 158) é a rede de segurança |

- **`primevest-core`** (biblioteca, sem `main`): o modelo JPA inteiro, os repositórios, as regras de
  posição e de proventos que os dois lados usam, as migrations, a validação do JWT e o tratamento de erro.
- **`primevest-app-api`**: o que o usuário usa — login, carteira, transações, proventos do usuário, estratégia,
  metas, preço teto, integrações com corretoras (Binance) e, na F9, a leitura de emissores e indicadores.
- **`primevest-adm-api`**: tudo o que busca dado de fora em lote e grava no catálogo — emissores, balanços,
  série trimestral, COTAHIST, fechamento diário, proventos da B3/FNET, logos, conferência do catálogo —, os
  agendamentos dessas rotinas e todo o `/api/v1/admin/*`. As fases F7, F8, F10 e F11 nascem aqui.

Os pacotes Java **não mudam de nome** (`com.primevest.domain.asset`… continuam iguais): a separação é por
arquivo movido, não por renomeação. Diff menor, histórico legível e nenhum `import` a reescrever.

---

## 2. Por que não um serviço com banco próprio

O dado da ingestão e o dado do usuário vivem nas mesmas tabelas:

- `assets.issuer_id` liga o ativo ao emissor; `assets.current_price` vem do fechamento diário;
- o COTAHIST e a BRAPI gravam em `asset_price_history`, que o gráfico do usuário lê;
- a sincronização de proventos grava o catálogo (`asset_cash_events`, `asset_stock_events`) **e recalcula
  os proventos de cada usuário** (`dividends`) e a valorização das posições (`portfolio_assets`);
- a F9 lê `company_quarters` + `assets.current_price` para o indicador.

Banco separado exigiria replicar essas tabelas nos dois sentidos. Com banco compartilhado e o modelo no
`core`, cada serviço grava o que sempre gravou.

---

## 3. Inventário — o que vai para onde

Regra geral, aplicada classe por classe na execução (o compilador confirma):

1. **Toda entidade JPA e todo repositório vão para o `core`.** As 27 entidades formam um grafo só
   (`User` → `Strategy`, `Goal`, `CeilingPrice`; `Transaction` → `IntegrationConnection`, `FixedIncomeNote`;
   `PortfolioAsset` → `Dividend`, `FixedIncomeNote`; `Asset` → `Issuer`), então separar entidade por
   serviço obrigaria a cortar relacionamentos. Enums e conversores usados por elas vão junto.
2. **Regra de negócio usada pelos dois lados vai para o `core`** (posição, proventos do usuário,
   valorização).
3. **Cliente de fonte externa, importador, agendamento de ingestão e endpoint de admin vão para a
   `adm-api`.**
4. **Controller, serviço de tela e DTO do usuário ficam na `app-api`.**

### 3.1 `primevest-core`

| Pacote | Classes |
|---|---|
| `domain/asset` | `Asset`, `AssetCategory`, `AssetCategoryRepository`, `AssetRepository`, `AssetTerms`, `AssetTermsRepository`, `AssetType`, `AssetTypeRepository`, `Indexer`, `PriceSource`, `RateType` + **novo** `AssetLogos` (a parte de leitura do `AssetLogoService`: `find(ticker)` e `typeHasLogo`, usada por `AssetController`, `DividendService` e `PortfolioService`) |
| `domain/ceilingprice` | `CeilingPrice`, `CeilingPriceRepository` |
| `domain/corporateaction` | todas (5): `AssetStockEvent`, `AssetStockEventRepository`, `CorporateActionApplier`, `StockEventStep`, `StockEventType` |
| `domain/dividend` | `AssetCashEvent`, `AssetCashEventRepository`, `CashEventSource`, `Dividend`, `DividendCalendar`, `DividendOrigin`, `DividendRecalculator`, `DividendRepository`, `DividendTaxVerifier`, `DividendType`, `PaymentDateSource`, `PositionDividendTotal`, `TaxBaseDate`, `WithholdingTaxCalculator`, `WithholdingTaxRule`, `WithholdingTaxRuleRepository` + **novo** `CashEventWriter` (o `saveCashEvent` que hoje está no `ProventosSyncService` e que o extrato também usa) |
| `domain/fixedincome` | todas (3) |
| `domain/goal` | `Goal`, `GoalRepository`, `GoalStatus` |
| `domain/integration` | entidades, repositórios e enums de estado: `IntegrationConnection`, `IntegrationCredential`, `IntegrationCryptoExclusion`, `IntegrationCursor`, `IntegrationCursorId`, `IntegrationRun`, `IntegrationRunItem` e os 6 repositórios; `ConnectionStatus`, `IntegrationProvider`, `RunItemOutcome`, `RunItemResolution`, `RunStatus`, `RunTrigger` |
| `domain/issuer` | `CompanyQuarter`, `CompanyQuarterRepository`, `Issuer`, `IssuerRepository`, `IssuerKind`, `IssuerLinkSource`, `ShareClass`, `StatementLayout`, `UnitComposition`, `UnitCompositionRepository` |
| `domain/portfolio` | `AppreciationUpdater`, `CapitalReturn`, `OpenPositions`, `Portfolio`, `PortfolioRepository`, `PortfolioAsset`, `PortfolioAssetRepository`, `Position`, `PositionCalculator`, `PositionEvents` |
| `domain/pricehistory` | `AssetPriceHistory`, `AssetPriceHistoryRepository`, `PriceHistorySource` |
| `domain/strategy` | `Strategy`, `StrategyRepository`, `StrategyReservation`, `StrategyReservationRepository`, `ReservationType`, `ReservationTypeConverter` |
| `domain/transaction` | `Transaction`, `TransactionRepository`, `TransactionType`, `TransactionOrigin`, `ImportOrigin`, `PositionChangedEvent`, `TreasuryAllocator` |
| `domain/user` | `User`, `UserRepository` |
| `domain/auth` | `JwtTokenProvider`, `JwtAuthFilter`, `UserDetailsServiceImpl` (validar o token nos dois serviços) |
| `config` | `SecurityUtils`; **novo** `AdminGuard` (o `requireAdmin` de hoje, reaproveitado pela `adm-api`) |
| `exception` | todas (4) |
| recursos | `db/migration` (V1–V33 e as próximas) |

Em números: ~120 classes, todas movidas sem mudança de conteúdo, mais `AssetLogos`, `CashEventWriter` e
`AdminGuard`, extraídos de classes existentes.

### 3.2 `primevest-adm-api` (nova)

| Pacote | Classes |
|---|---|
| raiz | **novo** `PrimevestAdmApplication` |
| `domain/admin` | `AdminController` (todos os endpoints de hoje, sem mudança de caminho) |
| `domain/issuer` | `IssuerImportService`, `IssuerLinkService`, `UnitCompositionText` |
| `domain/fundamentals` | todas (7): `CompanyQuarterService`, `FinancialMetric`, `FinancialMetricRules`, `FinancialStatementImportService`, `FreCapitalHistory`, `QuarterSeries`, `ShareScale` |
| `domain/pricehistory` | `ClosingPriceService`, `HistoricalQuotesImportService` |
| `domain/dividend` | `ProventosSyncService`, `ExtratoProventosImportService` + **novo** `ProventosOnDemandSubscriber` (4.1) |
| `domain/asset` | `AssetLogoService` (download e gravação dos logos), `CatalogAuditService` |
| `integration` | `PublicFileCache`, `cvm/*` (10), `b3/*` (11: B3, FNET, `CvmFundRegistry`, COTAHIST), `brapi/*` (7), `coingecko/*` (2) |
| `scheduler` | `CatalogAuditScheduler`, `ClosingPriceScheduler`, `CvmStatementsScheduler`, `LogoScheduler`, `ProventosScheduler` |
| `config` | `SchedulingConfig`, `SwaggerConfig`, **novo** `SecurityConfig` próprio (tudo autenticado; `/api/v1/admin/**` só admin) |

~60 classes. Configuração: as chaves `primevest.data`, `issuers`, `closing`, `proventos`, `cvm-statements`,
`catalog-audit` e `logos` do `application.yml` vêm para cá.

### 3.3 `primevest-app-api` (o que fica — é o `primevest-api` de hoje, renomeado)

| Pacote | Classes |
|---|---|
| raiz | `PrimevestApplication` |
| `domain/auth` | `AuthController`, `AuthService`, `GoogleTokenVerifier`, DTOs |
| `domain/user` | `UserController`, `UserService`, DTOs |
| `domain/asset` | `AssetController`, `AssetService`, DTOs |
| `domain/portfolio` | `PortfolioController`, `PortfolioService`, `PositionGuard`, DTOs |
| `domain/transaction` | `TransactionController`, `TransactionService`, DTOs |
| `domain/dividend` | `DividendController`, `DividendService`, DTOs; `ProventosOnDemandListener` passa a **publicar** (4.1) |
| `domain/pricehistory` | `AssetPriceHistoryService`, DTO |
| `domain/strategy`, `goal`, `ceilingprice` | controllers, serviços e DTOs |
| `domain/integration` | `IntegrationController`, `IntegrationService`, `IntegrationWorker`, `credential/*`, `pipeline/*`, `source/*`, DTOs |
| `integration/binance` | todas (3) |
| `scheduler` | `IntegrationSyncScheduler` |
| `config` | `CorsConfig`, `PasswordEncoderConfig`, `RedisConfig`, `SchedulingConfig`, `SecurityConfig`, `SwaggerConfig` |

~75 classes. Na F9 entram aqui `IndicatorService`, os DTOs e os `GET /issuers/...` e `/assets/{ticker}/indicators`.

### 3.4 Testes

| Destino | O que vai |
|---|---|
| `core` (`test-jar`, usado pelos outros dois) | `support/*` (`PostgresContainer`, `IntegrationTestBase`, `JpaTestBase`, `MvcTestBase`, `Fixtures`, `CatalogIds`, `ContaSintetica`, `Tokens`, `BinanceFake`), `SchemaTest`, testes das regras do core (`PositionCalculatorTest`, `PositionCalculatorEventosTest`, `CorporateActionApplierTest`, `DividendRecalculatorTest`, `DividendCalendarTest`, `DividendTaxVerifierTest`, `WithholdingTaxCalculatorTest`, testes de repositório) |
| `adm-api` | `fundamentals/*`, `issuer/*`, `HistoricalQuotesImportIT`, `integration/b3/*`, `integration/brapi/*`, `integration/cvm/*`, `AssetLogoServiceTest`, `PaymentDateSourceIT`, funcionais que disparam rotina: `FechamentoIT`, `ProventosIT`, `ProventosAReceberIT`, `ProventosIrIT`, `ExtratoProventosIT`, `EventosSocietariosIT` (a confirmar, pelo que cada um chama) + fixtures `src/test/resources/b3` e `cvm` |
| `app-api` | controllers, serviços de tela, `integration/binance`, pipeline de importação, funcionais `AlocacaoIT`, `CarteiraIT`, `EstrategiaIT`, `TransacoesIT`, `Integracoes*IT` |

Critério para os funcionais: o teste vai para o módulo que contém o serviço que ele exercita. Um teste
que precise dos dois (ex.: lança transação pela API do usuário **e** roda a sincronização) usa os
serviços do core diretamente, sem subir as duas aplicações.

Meta: a soma final continua **374 unitários + 158 de integração**, só redistribuídos.

---

## 4. Pontos de acoplamento e como ficam

### 4.1 Proventos sob demanda (o único fluxo do usuário que chama a ingestão)

Hoje, `ProventosOnDemandListener` (depois do commit de uma transação) chama
`ProventosSyncService.syncAssetOnDemand(assetId)` em segundo plano: busca na B3 o histórico do emissor que
nunca foi sincronizado. Separado, a `app-api` não tem mais o cliente da B3.

**Proposta: mensagem pelo Redis** (já está no compose e nos dois serviços).
- `app-api`: o listener publica o `assetId` no canal `primevest:proventos:sob-demanda`.
- `adm-api`: `ProventosOnDemandSubscriber` assina o canal e chama o mesmo `syncAssetOnDemand`.
- Mensagem perdida (adm-api fora do ar) não quebra nada: a rotina diária das 20:00 cobre, como hoje cobre
  uma falha da B3.
- Nos testes (`spring.cache.type: none`, sem Redis): publicador vira no-op; o teste da `adm-api` chama o
  assinante direto.

Alternativa: chamada HTTP interna `app-api → adm-api`. Descartada: exige credencial de serviço e acopla a
subida de um ao outro. Depois da F8 (proventos de **todas** as empresas desde 2010), a busca sob demanda
provavelmente deixa de ser necessária — fica registrado para decidir lá.

### 4.2 Recalcular proventos e valorização do usuário a partir da ingestão

`ProventosSyncService`, `ClosingPriceService` e `ExtratoProventosImportService` chamam
`DividendRecalculator`, `AppreciationUpdater` e `OpenPositions`. Como esses vão para o `core`, a
`adm-api` continua recalculando na mesma transação — sem mudança de comportamento.

### 4.3 Logos

`AssetLogoService` hoje baixa logos **e** responde `find(ticker)` para as telas. Divide-se em
`AssetLogos` (core, leitura da pasta) e `AssetLogoService` (adm-api, download). A `app-api` monta
`./data/logos` só para leitura; a `adm-api` monta `./data` inteira. A F10 já prevê refazer logos; a
divisão não antecipa nada dela.

### 4.4 Flyway

As migrations ficam no `core` e **os dois serviços rodam o Flyway** ao subir. O Flyway trava o banco
(`pg_advisory_lock`) enquanto migra: o primeiro aplica, o segundo encontra tudo aplicado. Nenhuma ordem de
subida entre os dois. Como os dois são construídos do mesmo repositório, não há risco de um estar numa
versão de migrations diferente da do outro.

### 4.5 Autenticação

O login continua só na `app-api` (`/api/v1/auth/*`). A `adm-api` valida o mesmo token (mesmo `JWT_SECRET`) e
exige `is_admin` como hoje. `admin.mjs` passa a fazer o login na 8080 e disparar a rotina na 8081
(variável `PRIMEVEST_ADM_URL`, padrão `http://localhost:8081`).

### 4.6 Cache

Não há `@Cacheable` em uso (só o `RedisCacheManager` configurado). Nada fica desatualizado entre os
dois serviços.

---

## 5. Build, Docker e desenvolvimento

- **`pom.xml` na raiz** (agregador, pai `spring-boot-starter-parent` 3.3.5, Java 21, versões dos plugins
  Surefire/Failsafe/Jacoco que hoje estão no pom da api). Módulos: `primevest-core`, `primevest-app-api`,
  `primevest-adm-api`. O `primevest-web` fica fora do Maven, como hoje.
- **Dependências:** `core` leva JPA, Flyway, PostgreSQL, Security/JWT, validação, Lombok. `app-api` acrescenta
  web, Redis/cache, OAuth2 (Google), WebFlux só se a Binance usar. `adm-api` acrescenta web, WebFlux
  (clientes HTTP), Redis (assinante).
- **Dockerfile** vai para a raiz (contexto de build = repositório), um alvo por serviço
  (`--build-arg MODULE=primevest-app-api|primevest-adm-api`). Estágio de dependências copia os 4 poms.
- **`dev-watch.sh`**: observa `primevest-core/src` **e** `<modulo>/src`; compila com
  `mvn -pl <modulo> -am compile`. **Risco a validar na execução:** o DevTools por padrão não reinicia por
  classe de outro módulo; o core precisa entrar no classloader de reinício
  (`META-INF/spring-devtools.properties` com `restart.include`). Se não fechar bem, alternativa: o core
  compila para `target/classes` do módulo (build-helper) só no perfil de dev.
- **`docker-compose.yml`**: serviço novo `adm-api` (porta `${ADM_API_PORT:-8081}`, mesmas variáveis de
  banco/Redis/JWT, `BRAPI_TOKEN` passa só para ele, volume `./data`); `app-api` perde `BRAPI_TOKEN` e fica com
  `./data/logos:ro`. `web` continua apontando só para a `app-api` (o front não chama admin).
- **Comandos de teste** passam a ser na raiz: `mvn clean verify` roda os três módulos; `-pl <modulo> -am`
  para um só.

---

## 6. Etapas de execução (cada uma termina com a bateria verde)

| Etapa | Entrega |
|---|---|
| **S1** | Renomear `primevest-api` → `primevest-app-api` (pasta e `artifactId`; pacotes Java iguais) + `pom.xml` raiz + `primevest-core` com o inventário 3.1 (movido) + `primevest-app-api` dependendo dele. Extrair `AssetLogos`, `CashEventWriter`, `AdminGuard`. `test-jar` do core com `support/*`. Nenhum serviço novo ainda: tudo sobe como hoje. **Bateria completa verde.** |
| **S2** | `primevest-adm-api` com o inventário 3.2 (movido da app-api) e sua `SecurityConfig`; testes de 3.4 redistribuídos; Redis sob demanda (4.1). **Bateria completa verde nos três módulos, mesma contagem.** |
| **S3** | Dockerfile na raiz, `dev-watch.sh` multi-módulo, compose com os dois serviços (`app-api` e `adm-api`; `VITE_API_TARGET` do web aponta para `app-api`); `admin.mjs` com a URL da adm-api. Subir o ambiente, conferir: login na 8080, `node admin.mjs cvm/quarters` na 8081 com o mesmo resultado de hoje (19.344 linhas), lançar uma transação e ver a mensagem sob demanda chegar na adm-api, hot reload alterando uma classe do core e uma de cada módulo. |
| **S4** | `Claude.md` (estrutura, portas, comandos, rotinas), `docs/planos/PLANO_EMPRESAS_BALANCOS.md` (F7–F11 na adm-api; F9 na app-api), `docs/PENDENCIAS.md`, README se houver. |

Estimativa: S1 e S2 são quase só movimentação de arquivos (~180 classes e ~50 testes); o trabalho real está
em S1 (dependências que o compilador revelar entre `core` e o resto) e em S3 (hot reload multi-módulo).

**Git:** os arquivos são movidos sem mudança de conteúdo sempre que possível, para o Git reconhecer como
renomeação. Commits ficam com o Guilherme — sugestão: um por etapa.

---

## 7. Decisões (todas aceitas em 28/09/2026)

| # | Decisão | Recomendação |
|---|---|---|
| A1 | **Quando:** agora (antes da F7) ou depois da F8 | **Agora.** F7 e F8 são ingestão pura; feitas antes, o código delas seria movido logo depois. A F8 reescreve proventos/FNET — melhor reescrever já no lugar definitivo |
| A2 | Proventos sob demanda | ~~Redis só no sob demanda~~ → **revisto (10.3):** Redis nos dois sentidos — "catálogo mudou" (adm → app) e "preciso do histórico" (app → adm) |
| A3 | Extrato de proventos (`/admin/proventos/extrato`) | ~~adm-api~~ → **revisto (10.2): app-api** — casa o extrato com os proventos do usuário e recalcula a carteira dele na mesma transação (a simulação desfaz tudo); é importação da carteira. Rota e exigência de admin mantidas até virar tela |
| A4 | Dois contêineres ou um só executável com os módulos como bibliotecas | **Dois**, como pedido; se o hot reload (S3) der trabalho demais, a S1+S2 já entregam a organização e a subida separada pode ficar para depois sem retrabalho |
| A5 | Nome e porta | `primevest-adm-api`, **8081** |
| A6 | Pacotes Java | ~~Mantidos~~ → **revisto (10.4):** core e app-api mantêm `com.primevest.*`; o que é só da adm-api vai para **`com.primevest.adm.*`** |
| A7 | Nome do módulo do usuário | **`primevest-app-api`** (escolhido em 28/09): par com `adm-api`; o `primevest-api` some |

---

## 8. Fora do escopo

- Qualquer mudança de regra, endpoint ou tabela: a separação é só estrutural.
- Banco separado, mensageria além do canal de 4.1, descoberta de serviço, gateway.
- Produção (imagem `runtime` continua existindo para cada serviço; hospedagem é escopo futuro).

---

## 9. Andamento

| Etapa | Estado |
|---|---|
| S1 | **Feita em 28/09/2026.** `primevest-api` → `primevest-app-api` (pasta, `artifactId`, `spring.application.name`); `pom.xml` da raiz (agregador `primevest-parent`: versões, compilador, Failsafe; `spring-boot.run.skip` ligado na raiz e desligado só nos módulos executáveis); **`primevest-core`** com 105 classes do inventário 3.1 + `AssetLogos` (novo) e as 33 migrations; `primevest-app-api` depende dele e do `test-jar`. Suporte de testes no core: `PostgresContainer`, `JpaTestBase`, `CatalogIds`, `Fixtures`, `Tokens`, `application-test.yml` e `CoreTestApplication` (fora do `test-jar`). **Bateria: core 134 unitários; app-api 240 unitários + 158 de integração = 374 + 158, a mesma de antes.** Ambiente: `app-api` no ar com o schema na V33, o front falando com ela pelo proxy, `cvm/quarters` com o mesmo resultado (19.344 linhas); hot reload conferido com uma classe do core (recompila o core e o app-api inteiro — ~1 min — e o DevTools reinicia em 3 s) |

Diferenças da S1 em relação ao texto acima:

- **`CashEventWriter` e `AdminGuard` não foram extraídos.** Quem usa os dois (`ProventosSyncService`,
  `ExtratoProventosImportService`, `AdminController`) vai inteiro para a `adm-api` na S2 (A3); no core não
  haveria segundo usuário. Só `AssetLogos` saiu (leitura dos logos, usada por telas).
- **`GlobalExceptionHandler` foi para o core sem os handlers de corretora e credencial**, que ficaram em
  `IntegrationExceptionHandler` no app-api, com precedência maior (o Spring para no primeiro advice que
  trata a exceção, e o global trata `Exception`).
- **Ficaram no app-api, por dependerem de classes dele:** `IntegrationTestBase` (faz mock de `BrApiClient`
  e `CoinGeckoClient`), `MvcTestBase` (`SecurityConfig`), `BinanceFake`, `ContaSintetica` e
  `IntegrationRepositoryTest` (credenciais). Na S2 o `IntegrationTestBase` precisa de uma versão por
  aplicação (os mocks de fonte externa são da adm-api).
- **Docker e compose vieram da S3 para a S1** só no necessário para o ambiente continuar subindo:
  `Dockerfile`, `dev-watch.sh` e `.dockerignore` na raiz (`ARG MODULE`), serviço e contêiner
  `app-api`/`primevest-app-api`, web apontando para `app-api:8080`. A S3 acrescenta a `adm-api`.
- `Claude.md` e `README.md`: só os caminhos e comandos que deixaram de valer (compose, hot reload, rodar
  local, árvore do repositório). A revisão completa segue na S4.

---

## 10. Revisão de 28/09/2026 — catálogo × carteira

Pedido do Guilherme: voltar à ideia principal — **rotinas de administrador separadas das rotinas da
carteira**. Os eventos dos ativos e o histórico deles são uma carga grande, disparada por rotina
administrativa; os proventos do usuário fazem parte da carteira dele.

### 10.1 A regra

| | adm-api (administração) | app-api (carteira) |
|---|---|---|
| Grava | **Só catálogo:** `asset_cash_events`, `asset_stock_events`, `asset_price_history`, `assets` (preço, ISIN, logo, emissor), `issuers`, balanços, `company_quarters` | **Só dado do usuário:** `dividends`, valorização em `portfolio_assets`, transações, carteira, estratégia… |
| Proventos | Busca na B3/FNET, histórico longo, rotina das 20:00, sob demanda, carga de todas as empresas (F8) | Calcula proventos e IR de cada posição a partir do catálogo (`DividendRecalculator`) |
| Preço | Fechamento diário (BRAPI/CoinGecko), COTAHIST | Atualiza a valorização das posições com o preço novo (`AppreciationUpdater`) |
| Lê do outro lado | `OpenPositions` — quais ativos alguém tem ou já teve, para saber o que buscar (até a F8 cobrir todas as empresas) | O catálogo inteiro, como hoje |

### 10.2 O que muda no inventário

- **`ProventosSyncService` se divide.** A busca e gravação de eventos vira **`ProventosCatalogService`**
  (adm-api): `syncAll`, `syncAssetOnDemand`, histórico longo, grupos por emissor. O recálculo das posições
  (`recalculatePositions`) sai dele e vira **`PositionRefreshService`** (app-api), acionado pela mensagem.
- **`ClosingPriceService` se divide igual:** grava preço e histórico (adm-api) e publica os ativos; a chamada
  a `appreciationUpdater.refreshAssets` vai para o `PositionRefreshService`.
- **`CashEventWriter` (core):** a regra de gravar/completar um evento de provento (`saveCashEvent`, com a
  precedência da fonte oficial sobre o extrato — V24). Usada pelo `ProventosCatalogService` e pelo extrato.
- **`ExtratoProventosImportService` fica na app-api** (A3 revista), com um controller próprio na mesma rota
  `POST /api/v1/admin/proventos/extrato`, ainda só para admin.
- **`AdminController` fica na adm-api** com as rotinas de catálogo; `/admin/proventos/extrato` é a única
  rota `/admin` que continua na 8080.

### 10.3 Mensagens (Redis pub/sub, contrato no core)

| Canal | Sentido | Conteúdo | Quem reage |
|---|---|---|---|
| `primevest:catalogo:ativos` | adm → app | `{origem: PROVENTOS|FECHAMENTO, assetIds: [...]}` | `PositionRefreshService`: proventos + valorização (PROVENTOS) ou só valorização (FECHAMENTO) das posições desses ativos |
| `primevest:proventos:sob-demanda` | app → adm | `{assetId}` | `ProventosCatalogService.syncAssetOnDemand`, que ao terminar publica "catálogo mudou" |

- Interfaces no core: `CatalogChangePublisher` e `OnDemandRequestPublisher`. Implementação Redis em cada
  serviço; nos testes, implementação direta (chama o outro lado na hora, sem Redis).
- **Rede de segurança:** mensagem perdida (um dos dois fora do ar) não deixa a carteira errada por muito
  tempo: a app-api recalcula **todas as posições uma vez por dia** (21:00, depois do fechamento e dos
  proventos). Sem coluna de estado nova (`asset_cash_events` não tem `created_at`, e recalcular tudo é
  barato no volume atual — revisar quando houver muitos usuários).

### 10.4 Pacote da adm-api e os testes de ponta a ponta

Os 7 testes funcionais de proventos e fechamento (`ProventosIT`, `ProventosAReceberIT`, `ProventosIrIT`,
`ExtratoProventosIT`, `FechamentoIT`, `EventosSocietariosIT`, `PaymentDateSourceIT`, ~1.400 linhas)
conferem o fluxo inteiro: resposta da B3 → catálogo → proventos, IR e posição pela API do usuário. Para
continuarem conferindo isso sem reescrita:

- as classes **só da adm-api vão para `com.primevest.adm.*`** (A6 revista); core e app-api seguem em
  `com.primevest.*`;
- a app-api **exclui `com.primevest.adm` da varredura** de componentes;
- a app-api tem a adm-api como dependência **só de teste**; os 7 testes importam o
  `ProventosCatalogService`/`ClosingPriceService` e as fontes (mockadas), e as mensagens usam a
  implementação direta — B3 mockada → catálogo (código da adm) → mensagem → recálculo (código da app) →
  API do usuário, tudo no mesmo teste;
- a adm-api tem os próprios testes do catálogo (emissores, balanços, COTAHIST, clientes) e publica o jar
  comum com `classifier exec` no executável, para a app-api poder usá-lo nos testes.

### 10.5 S2 revista

1. `primevest-adm-api`: pom, `PrimevestAdmApplication` (varre `com.primevest`), configuração própria
   (porta 8081, fontes externas, rotinas), `AdmSecurityConfig`.
2. Mover para `com.primevest.adm.*`: `AdminController`, emissores (import/ligação), `fundamentals`,
   `integration/*` exceto Binance, COTAHIST, fechamento, catálogo de proventos, logos, conferência do
   catálogo, os 5 agendamentos de catálogo.
3. Dividir `ProventosSyncService` e `ClosingPriceService`; `CashEventWriter` no core.
4. App-api: `PositionRefreshService`, assinante de "catálogo mudou", publicador de "sob demanda",
   rotina diária de recálculo, controller do extrato, exclusão de `com.primevest.adm` da varredura.
5. Testes: base de integração genérica no core; mocks de fonte na adm-api; os 7 de ponta a ponta com a
   adm-api em escopo de teste. **Mesma contagem (374 + 158) mais os testes novos das mensagens.**
6. Compose: a app-api continua subindo (monta o pom da adm-api, que o reator exige); a adm-api sobe na S3.

### 10.6 Andamento da S2

| Etapa | Estado |
|---|---|
| S2 | **Feita em 28/09/2026.** `primevest-adm-api` com 52 classes movidas para `com.primevest.adm.*` (admin, emissores, `fundamentals`, COTAHIST, fechamento, catálogo de proventos, logos, conferência do catálogo, clientes B3/FNET/CVM/BRAPI/CoinGecko, 5 agendamentos) + `PrimevestAdmApplication`, `AdmSecurityConfig`, `AdmSchedulingConfig`, `AdmSwaggerConfig`, `AdmMessagingConfig` e configuração própria (porta 8081). **Divisão catálogo × carteira:** `ProventosSyncService` → `ProventosCatalogService` (só grava eventos e publica "catálogo mudou"); `ClosingPriceService` publica em vez de revalorizar; `CashEventWriter` no core (rotina e extrato); na app-api `PositionRefreshService` (ouve o Redis, recalcula proventos/valorização; na subida faz o que `AppreciationUpdater` e `DividendTaxVerifier` faziam por conta própria no core), `PositionRefreshScheduler` (21:00), `CatalogMessagingConfig`, `ExtratoProventosController` e o ouvinte de transação publicando "preciso do histórico". Contrato das mensagens no core (`CatalogChange`, `CatalogMessaging`, `MessagingFallbackConfig`). **Bateria: 376 unitários (core 136, adm 57, app 183) + 161 de integração (adm 16, app 145) — os 374 + 158 de antes mais 2 + 3 novos, todos verdes.** |

Diferenças da S2 em relação a 10.5:

- **`spring-boot:run` por módulo com `-Drun.module=<módulo>`.** O `-am` da app-api traz a adm-api (dependência de
  teste) e subiria as duas aplicações no mesmo `mvn`; o perfil `run` de cada módulo executável só liga com a
  propriedade (o `dev-watch.sh` passa). Comando local: `mvn -pl primevest-app-api -am -Drun.module=primevest-app-api spring-boot:run`.
- **Base de testes de integração no core** (`IntegrationTestBase`, sem mocks); a Binance é mockada no
  `FuncionalBase` (app-api) e as fontes do catálogo no `CatalogoE2EBase` (4 testes de ponta a ponta:
  `ProventosIT`, `ProventosAReceberIT`, `ProventosIrIT`, `FechamentoIT`). `ExtratoProventosIT` e
  `EventosSocietariosIT` não usam a adm-api; `PaymentDateSourceIT` passou a usar o `CashEventWriter`.
- **Compose com a adm-api já na S2** (era S3): sem ela, as rotinas de catálogo (fechamento 19:00, proventos
  20:00, balanços 07:30) deixariam de rodar no ambiente. A app-api perdeu o `BRAPI_TOKEN` e monta só
  `./data/logos` (leitura). Fica para a S3: conferir hot reload da adm-api e a troca de mensagens de ponta a ponta.
- **"Catálogo mudou" dos proventos sai por emissor**, não no fim da rodada: o histórico longo do FNET pode levar
  muito tempo (PENDENCIAS 14.1, F8), e a carteira de quem tem os primeiros emissores já se acerta. Conferido no
  ambiente: `node admin.mjs proventos` na 8081 → a app-api registra "Carteira: catalogo mudou (PROVENTOS, 1
  ativos)" um segundo depois de cada emissor; `cvm/quarters` na 8081 com as mesmas 19.344 linhas; extrato
  (simulação) na 8080; hot reload da adm-api conferido com uma classe dela.

### 10.7 S3 e S4

| Etapa | Estado |
|---|---|
| S3 | **Feita em 28/09/2026** (compose e Dockerfile já vinham da S1/S2). Conferido com os dois contêineres no ar: classe do core alterada → os dois recompilam e reiniciam sozinhos (adm-api ~35 s, app-api ~55 s); "preciso do histórico" publicado no canal pelo `redis-cli` → a adm-api busca e a app-api recalcula ("catálogo mudou (PROVENTOS)"); `node admin.mjs closing` na 8081 → "catálogo mudou (FECHAMENTO)" na app-api; logo servido pela 8080 da pasta montada só para leitura; 401 sem token na 8081 e Swagger das duas. Imagem de produção dos dois módulos (`docker build --target runtime --build-arg MODULE=...`) gerada e subindo até o Tomcat |
| S4 | **Feita em 28/09/2026.** `Claude.md`: seção nova "Dois serviços: carteira × administração" (módulos, regra de escrita, mensagens, onde mexer), serviços e portas, rotinas por serviço, hot reload dos dois contêineres, árvore do repositório, testes por módulo. `docs/planos/PLANO_EMPRESAS_BALANCOS.md`: onde cada fase é feita (F7, F8, F10, F11 na adm-api; F9 na app-api; entidades e migrations no core), portas das rotas (5.4) e linha da separação no andamento. `docs/PENDENCIAS.md` 18.9 (rede de segurança a rever com muitos usuários, sob demanda a rever na F8, publicador da app-api sem teste com Redis real, recompilação dos dois contêineres). `README.md` e `admin.mjs` já vinham da S2 |

Bateria final: **376 unitários + 161 de integração, todos verdes** (a última `mvn clean verify`, depois da última
mudança de código).
