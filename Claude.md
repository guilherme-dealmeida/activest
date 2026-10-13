# PrimeVest — Documentação do Projeto

> **Tagline**: DISCIPLINA LEVA À LIBERDADE

Plataforma de gestão de investimentos pessoais: dashboard, carteira, transações,
alocação, estratégia com rebalanceamento, dividendos, preço teto e metas.

> **Última revisão**: 12/10/2026 (legendas dos gráficos verticais, com o ideal da estratégia na Alocação — ver
> "Legendas dos gráficos" em Padrão Visual). No mesmo dia: (Alocação: "pizza de pizza" na aba Carteira de Investimentos — ver "Alocação" em
> Telas). No mesmo dia: (rebalanceamento e estratégia pelo valor atual, com a valorização, e não mais pelo
> custo — ver "Rebalanceamento" em Telas). Antes: 11/10/2026 (Rebalanceamento deixou de ser aba da Estratégia e virou tela própria, `/rebalance`,
> com item no menu e um link de uma tela para a outra no canto superior direito do conteúdo — ver "Estratégia" e
> "Rebalanceamento" em Telas). No mesmo dia: busca por ticker ou nome em Transações e Dividendos, com o efeito do Listar —
> `LocalAssetSearch`, ver "Busca por ticker ou nome nas listagens" em Padrão Visual). No mesmo dia: Dividendos em duas abas, Histórico de Proventos e Proventos a Receber, sem filtro
> nem coluna de situação, com busca por ticker ou nome, o ícone Listar e o aviso "Limpar filtro" — ver "Proventos recebidos × a receber"). No mesmo dia: ícones de ação
> das tabelas no amarelo do tema, numa classe só; Comprar e Vender
> também em Transações, com o mesmo preset da carteira — ver "Transações" e "Ícones de ação nas tabelas" em Telas).
> Antes: 10/10/2026 (Preço Teto pelo modelo PREÇO TETO V2: cálculo por ação com padrões do catálogo,
> entradas gravadas e resultado na leitura; payout pelo fluxo de caixa; cotações de todo o catálogo pelo COTAHIST
> diário — ver "Preço Teto" em Telas, `docs/PRECO_TETO_METODOLOGIA.md` e `docs/planos/PLANO_PRECO_TETO.md`).
> Antes: 07/10/2026 (cadastro de transação: seletor de título também na compra de Renda Fixa, com todos os
> títulos do ativo e aporte só até a véspera do vencimento; filtro do extrato depois de salvar e ícone Listar em
> Transações; colunas ATIVO com logo e TÍTULO; logo por tipo — símbolo do Tesouro Direto e "RF"; tipos de ativo na
> ordem do cadastro — ver "Transações" em Telas).
> Antes: 05/10/2026, noite (valorização da renda fixa: Tesouro pelo preço oficial, renda fixa privada pela
> curva contratada, venda de renda fixa pela Nova Transação — ver `docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md`).
> No mesmo dia: (índices de mercado: CDI, Selic, IPCA, IGP-M, INPC, TR, meta Selic, PTAX, Ibovespa e
> IFIX desde 2010, rotina das 10:00 e `/api/v1/indices` — ver `docs/planos/PLANO_INDICES_MERCADO.md`).
> No mesmo dia: cadastro de usuário (login Google em uso, cadastro com código por e-mail,
> "esqueci a senha", termos, Configurações completas, refresh token separado do acesso — ver
> `docs/planos/PLANO_CADASTRO_USUARIO.md`).
> Antes: 03/10/2026 (pilha de teste `primevest-teste` com a carga inicial completa — ver
> `docs/planos/PLANO_PRIMEVEST_TESTE.md`). No mesmo dia: organização (planos em `docs/planos/`, pendências em
> `docs/PENDENCIAS.md`, documentos de `docs/` em Markdown, carga em `carga/data` e `carga/scripts`).
> Antes: 28/09/2026 (tela de Emissoras, T1–T7 — ver `docs/planos/PLANO_TELA_EMISSORES.md`).
> Antes: 27/09/2026 (emissores e série histórica da B3, F1–F2 — ver `docs/planos/PLANO_EMPRESAS_BALANCOS.md`).
> Antes: 26/09/2026 (integrações com corretoras, F1–F6 — ver `docs/planos/PLANO_CRIPTO_BINANCE.md`).
> Antes: 24/09/2026 (integração BRAPI/B3 — ver `docs/planos/PLANO_BRAPI.md`).
> Antes: 18/09/2026. A conferência arquivo a arquivo contra o
> código é de 15/09/2026; o que entrou depois está datado no texto.
> O que este documento afirma foi verificado; o que está pendente está em
> `docs/PENDENCIAS.md`. Os planos de cada escopo ficam em `docs/planos/`.

---

## ⚡ LEIA ANTES DE DIZER QUE NÃO CONSEGUE EXECUTAR

Dirigido ao assistente, e propositalmente no topo.

O shell padrão de uma sessão Cowork é uma VM Linux isolada, sem Maven, sem
Docker, com JDK 11 e sem rede. **Isso não é o fim da história neste projeto.**

Esta máquina tem o Claude Code instalado e registrado como servidor MCP local do
app desktop (`claude-code`, configurado em 18/09/2026). Ele expõe ferramentas
`PowerShell` e `Bash` que rodam **no Windows do usuário**, com JDK 21, Maven
3.9.11, Docker Desktop e a rede dele. Compilar, subir o compose, rodar `npm` e
ler log do container funcionam por ali.

Essas ferramentas chegam **adiadas** (deferred): aparecem só pelo nome, e
precisam ser carregadas antes do primeiro uso.

```
ToolSearch: select:mcp__remote-devices__claude-code__PowerShell,mcp__remote-devices__claude-code__Bash
```

Regra prática: **antes de responder "não consigo compilar/subir/testar", procure
por `claude-code` na lista de ferramentas adiadas e carregue-a.** Se ela não
estiver lá, aí sim a limitação é real — a sessão não está vinculada a este
computador, ou o app desktop está fechado. Diga isso, e não que o projeto não
pode ser executado.

Detalhes, armadilhas da configuração e o fallback sem a ponte estão em
"Como o Claude executa comandos aqui", mais abaixo.

---

## 🧭 A decisão estrutural que explica o resto

Se você só vai ler uma seção, leia esta. Quase toda escolha estranha no schema
vem daqui.

**O catálogo é global; o papel é do usuário.**

`assets` é o catálogo: o que o instrumento É. "PETR4", "Tesouro Selic 2031",
"CDB". Não tem `user_id` de propósito — um ativo é o mesmo para todo mundo.

Só que renda fixa não cabe nisso. Um CDB não tem vencimento nem taxa próprios:
cada aplicação negocia os seus com o banco, no dia da compra. Enquanto essas
colunas viviam em `assets`, registrar dois CDBs de bancos diferentes exigia
criar dois ativos — "CDB BTG 2027", "CDB Inter 2028" — num catálogo global, que
aparecia na busca de todos os usuários.

A separação em três lugares, cada um com um dono:

| Tabela | Dono | O que guarda |
|--------|------|--------------|
| `assets` | ninguém (global) | o instrumento: ticker, nome, tipo, categoria |
| `asset_terms` | ninguém (global) | parâmetros **públicos** do papel, 1:1 com `assets`. Hoje só Tesouro Direto |
| `fixed_income_notes` | usuário | **o papel**: emissor, vencimento, forma de remuneração e a taxa contratada |

Por que `asset_terms` existe em vez de continuar como colunas em `assets`:
"Tesouro IPCA+ 2029" vence em 15/05/2029 e remunera IPCA mais spread para
qualquer investidor — isso é do papel e é público, então fica no catálogo. Mas
só 130 dos 1.264 ativos têm esses parâmetros; os outros 1.134 carregavam quatro
colunas nulas.

Por que a taxa **não** está em `asset_terms`: no Tesouro ela é do dia da compra,
não do título. Dois investidores levam o mesmo papel a IPCA + 6,2% e IPCA + 5,8%
conforme quando compraram. Taxa é sempre da nota.

Consequências que aparecem em vários pontos do sistema:

- `transactions.note_id` e `portfolio_assets.note_id`, nulos na renda variável.
- **A carteira repete ticker.** Três CDBs de emissores diferentes são três
  linhas, todas "CDB — Certificado de Depósito Bancário". Por isso
  `PortfolioAssetDetailDTO` carrega `issuer` e `maturityDate`, e a tela os
  exibe: sem eles as linhas ficam indistinguíveis.
- A unicidade da posição é `(portfolio_id, asset_id, note_id)` com
  **`NULLS NOT DISTINCT`**. Sem isso a regra sumiria para ações: no padrão SQL
  dois NULLs nunca são iguais, então duas posições de PETR4 com `note_id` nulo
  não conflitariam.
- A nota **copia** de `asset_terms` no lançamento, em vez de referenciar. O que
  vale para o cálculo é o que foi contratado; uma correção futura no catálogo não
  deve reescrever posição já registrada.

---

## 🧱 Dois serviços: carteira × administração (28/09/2026)

Plano e histórico: **`docs/planos/PLANO_ADM_API.md`**. O backend é um Maven multi-módulo com um `pom.xml` na raiz:

| Módulo | Porta | O que é |
|---|---|---|
| `primevest-core` | — | Biblioteca: **todas** as entidades JPA e repositórios, as regras de posição e de proventos usadas pelos dois lados (`PositionCalculator`, `DividendRecalculator`, `AppreciationUpdater`, `CashEventWriter`…), as migrations do Flyway, a validação do JWT, o tratamento de erro e o contrato das mensagens (`CatalogChange`, `CatalogMessaging`) |
| `primevest-integration` | — | Biblioteca (03/10/2026, `docs/planos/PLANO_INTEGRACAO_B3.md`): **integrações com fontes do investidor** — contrato da fonte (`SourceConnector`, `ImportedMovement`), credenciais cifradas (`SourceCredentials`: chave de API ou CPF; `CredentialCipher`, `CredentialVault`), pipeline de importação (`ImportPipeline` + as regras de cada fonte, `ImportRules`) e os conectores (`integration/binance/`, `integration/b3investidor/`). Pacotes com o mesmo nome de antes. Só a app-api depende dele; a orquestração (serviço, fila, confirmação) continua na app-api, porque grava pelo `TransactionService` |
| `primevest-app-api` | 8080 | **A carteira**: login, transações, carteira, proventos do usuário, estratégia, metas, preço teto, integrações com corretoras. O front só fala com ela |
| `primevest-adm-api` | 8081 | **Rotinas de administrador — o catálogo**: emissores, balanços e série trimestral da CVM, COTAHIST, fechamento diário (BRAPI/CoinGecko), eventos de proventos (B3/FNET), índices de mercado (SGS/B3), logos, conferência do catálogo, todo o `/api/v1/admin/*` (exceto o extrato). Classes em `com.primevest.adm.*` |

**A regra:** a adm-api grava **só catálogo** (`asset_cash_events`, `asset_stock_events`, `asset_price_history`, `assets`, `issuers`, balanços, `company_quarters`, `fii_monthly`, `market_index_values`);
a app-api é a **única que grava dado de usuário** (`dividends`, valorização em `portfolio_assets`, transações…). Mesmo
banco, mesmo Redis, mesmo JWT (o login é na 8080; a 8081 só valida o token). Os dois rodam o Flyway ao subir — ele
trava o banco enquanto migra, a ordem de subida não importa.

**Mensagens (Redis pub/sub):**

| Canal | Sentido | Quem reage |
|---|---|---|
| `primevest:catalogo:ativos` | adm → app: "catálogo mudou" `{origin: PROVENTOS|FECHAMENTO, assetIds}` | `PositionRefreshService` refaz proventos e valorização (PROVENTOS) ou só valorização (FECHAMENTO) das posições desses ativos |
| `primevest:proventos:sob-demanda` | app → adm: "preciso do histórico" (id do ativo), depois de cada transação | `ProventosCatalogService.syncAssetOnDemand`, que responde com "catálogo mudou" |

Sem garantia de entrega: se um dos dois estiver fora, a mensagem se perde e a **rede de segurança** acerta — a app-api
recalcula todas as posições às 21:00 (`PositionRefreshScheduler`). Nos testes as mensagens ficam desligadas
(`primevest.messaging.enabled=false`, só log) e os testes de ponta a ponta ligam os dois lados direto
(`CatalogoE2EBase`).

**Onde mexer:** regra que os dois usam (ou entidade/migration) → core; tela, API do usuário, cálculo da carteira →
app-api; fonte externa, carga, rotina agendada de catálogo → adm-api; conector de corretora, regra de importação e
credencial → primevest-integration (a orquestração e os endpoints das integrações ficam na app-api). A app-api **nunca** chama fonte externa de
catálogo; a adm-api **nunca** grava `dividends` nem `portfolio_assets`.

---

## 🚀 Como rodar

### Opção 1 — Docker (recomendada)

Sobe banco, cache, API e front de uma vez, com o banco construído do zero pelas
migrations. Não exige Java, Maven, Node nem PostgreSQL instalados na máquina.

```bash
docker compose up -d --build
```

| Serviço | URL / Porta |
|---------|-------------|
| Frontend (Vite, hot reload) | http://localhost:5173 |
| API do usuário (app-api) | http://localhost:8080 |
| Rotinas de administrador (adm-api) | http://localhost:8081 |
| Swagger UI | http://localhost:8080/swagger-ui.html e http://localhost:8081/swagger-ui.html |
| PostgreSQL | localhost:**5433** |
| Redis | localhost:6379 |
| Mailpit (caixa de e-mail do DEV) | http://localhost:8025 (SMTP 1025) — todo e-mail da app-api cai aqui, nada sai da máquina |

> A porta do banco é **5433** de propósito, para não colidir com o PostgreSQL 17
> instalado na máquina, que ocupa a 5432. No pgAdmin use `localhost:5433`.

Comandos do dia a dia:

```bash
docker compose up -d --build   # sobe tudo
docker compose down            # para tudo, mantém o banco
docker compose down -v         # para tudo e APAGA o banco (recria do zero)
docker compose logs -f app-api # logs da carteira (8080)
docker compose logs -f adm-api # logs das rotinas de catálogo (8081)
docker compose exec db psql -U postgres -d db_primevest   # psql no container
```

**Portas publicadas com prefixo `0.0.0.0:`** no `docker-compose.yml`. Sem ele o
Docker publica em dual-stack enquanto o Vite escuta em uma pilha só, e
`localhost:5173` fica pendurado sem erro. Se alguém "limpar" esse prefixo, o
front para de abrir — já aconteceu.

**Hot reload**: os poms e os `src` dos módulos e `primevest-web/` entram por bind mount (os `target/` ficam
dentro de cada contêiner). Alterar um `.tsx` recarrega o Vite. No backend, cada contêiner roda o `dev-watch.sh`
(raiz; CMD do estágio `dev`, com `MODULE` do compose): verifica `primevest-core/src` e o `src` do próprio módulo
a cada 2 s, roda `mvn -pl <módulo> -am compile` quando algo mudou (classe do core recompila e reinicia **os
dois** contêineres, ~30–50 s; o `-am` da app-api inclui a adm-api, dependência só de teste) e, se
compilar, toca o gatilho do DevTools, que reinicia o Spring — migration nova
entra junto. Compilação com erro aparece no log do container e mantém a versão
anterior no ar. Arquivo apagado não dispara sozinho: salve outro ou reinicie.

> Mexeu no `Dockerfile` ou no `dev-watch.sh`? Precisa rebuild:
> `docker compose up -d --build app-api adm-api`.

**O compile incremental já deixou passar arquivo alterado** (19/09/2026): um
controller modificado no Windows foi compilado pelo `dev-watch` sem que o
`.class` ganhasse o método novo — a API subiu sem o mapping e respondeu
`NoResourceFoundException` (que aparece como 500 ou 404, não como 405). Se um
endpoint recém-escrito "não existe" para o Spring, confira o `.class` antes de
procurar erro no código:
`docker compose exec app-api sh -c "grep -ac <trecho> primevest-app-api/target/classes/<...>.class"`.
A saída é `touch` no `.java` e `mvn -o compile` dentro do container.

**A imagem do front é `node:24` (Debian), não `node:24-alpine`** — ver "Imagem
do front" em Pontos de Atenção. Alpine continua em uso no `nginx:alpine` do
estágio `runtime` e nos containers de banco e cache.

**Mudou entidade JPA e migration juntas?** Tem que ser a mesma subida.
`ddl-auto: validate` compara entidades com o schema: se o Java mudar sem a
migration, ou o contrário, a API não sobe.

### Pilha de teste — `primevest-teste` (03/10/2026)

Segunda pilha com os mesmos 5 serviços, em portas próprias, para provar a carga inicial do zero sem tocar no dev
(`docs/planos/PLANO_PRIMEVEST_TESTE.md`). O `docker-compose.yml` não muda: a pilha é ele + o override
`docker-compose.teste.yml` + o `.env.teste` (fora do git; cópia do `.env.example` com as portas abaixo).

```powershell
# na raiz; sem -p e --env-file o comando atinge a pilha do DEV (down -v apagaria o banco do dev)
function pvt { docker compose -p primevest-teste --env-file .env.teste -f docker-compose.yml -f docker-compose.teste.yml @args }
pvt --profile carga run --rm data pull     # bucket R2 -> carga/data-teste/ (token só leitura do .env.example)
pvt up -d --build
pvt --profile carga run --rm tools node carga/scripts/admin.mjs bootstrap
docker logs -f primevest-teste-adm-api     # termina em "Carga inicial (...) concluida" (~50 min)
pvt stop                                   # para sem apagar; pvt start volta
```

| Serviço | Dev | Teste |
|---|---|---|
| db | 5433 | 5434 |
| redis | 6379 | 6380 |
| web | 5173 | 5174 |
| app-api | 8080 | 8082 |
| adm-api | 8081 | 8083 |
| mailpit | 8025 (SMTP 1025) | 8026 (SMTP 1026) |

- **Isolamento:** projeto Compose próprio (rede `primevest-teste_default`, volumes `primevest-teste_pgdata` e
  `primevest-teste_maven-repo`), contêineres `primevest-teste-*` e pasta de dados `carga/data-teste/` (no
  `.gitignore` e no `.dockerignore`) no lugar de `carga/data/`.
- **Rotinas agendadas desligadas** (`PRIMEVEST_SCHEDULING_ENABLED=false` no override): nada roda sozinho, nem a
  recuperação do fechamento na subida. O `bootstrap` não depende delas. O **worker da fila de integrações fica
  ligado** (`PRIMEVEST_INTEGRATIONS_WORKER_ENABLED=true`): ele só processa a sincronização que alguém pediu.
- **B3 contra o simulador** (`docs/planos/PLANO_INTEGRACAO_B3.md`, F5): `pvt --profile b3mock up -d --build` sobe
  também o `primevest-teste-b3-investidor-mock` (porta 8190, extratos de `carga/mocks/`); `B3I_*` no `.env.teste`;
  ponta a ponta com `node carga/scripts/b3investidor/conferir.mjs --api http://localhost:8082 --mock
  http://localhost:8190 --gabarito http://localhost:8080`. Página de opt-in do simulador: http://localhost:8190/optin.
- **Mesmo código do dev** (bind mount + `dev-watch`): salvar um `.java` do core ou da adm-api reinicia a adm-api
  do teste e interrompe uma carga em andamento — ela é retomável, basta disparar de novo.
- **Primeira execução (03/10/2026):** `data pull` em ~7,5 min (~2,2 GB), `bootstrap` em 2.949 s, todas as etapas
  ok; contagens iguais às do dev, salvo o que a janela de 12 meses da B3 e os fechamentos explicam e um emissor
  só do dev (PENDENCIAS 21.1). Números no andamento do plano.

### Opção 2 — Local, sem Docker

Exige Java 21, Maven 3.9+, Node 24 e PostgreSQL 17 com o banco `db_primevest` criado.

```powershell
# Backend — porta 8080, da raiz (o core compila junto)
mvn -pl primevest-app-api -am -Drun.module=primevest-app-api spring-boot:run "-Dmaven.test.skip=true"
# Rotinas de administrador — porta 8081 (docs/planos/PLANO_ADM_API.md)
mvn -pl primevest-adm-api -am -Drun.module=primevest-adm-api spring-boot:run "-Dmaven.test.skip=true"

# Frontend — porta 5173, proxy /api -> localhost:8080
cd primevest-web
npm run dev
```

Se `mvn` ou `npm` não forem encontrados no PowerShell:

```powershell
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
```

---

## 🛠 Stack Tecnológica

| Camada | Tecnologia |
|--------|-----------|
| Backend | Java 21 + Spring Boot 3.3.5 |
| Frontend | React 18 + TypeScript + Vite 5 |
| Banco | PostgreSQL 17 (o `NULLS NOT DISTINCT` exige 15+) |
| Cache | Redis 7 (cotações; `cache.type: simple` no perfil dev) |
| ORM | Spring Data JPA / Hibernate 6.5 |
| Migrations | Flyway |
| Autenticação | JWT (jjwt 0.12.6) + Spring Security + Google Identity Services |
| HTTP externo | Spring WebFlux / WebClient |
| Utilitários | Lombok (MapStruct está no `pom.xml` mas nenhum mapper foi escrito — o mapeamento é manual, em métodos `toDTO` nos services) |
| Documentação de API | SpringDoc OpenAPI 2.6.0 (`/swagger-ui.html`) |
| Gráficos | Recharts |
| Estado/fetch | TanStack React Query 5 |
| Rotas | React Router 6 |
| CSS | Tailwind CSS 3 |
| Testes | JUnit 5, Mockito, AssertJ, Testcontainers (PostgreSQL 17) — ver Pontos de Atenção |
| Lint (front) | ESLint 9 em flat config + `typescript-eslint` — ver Pontos de Atenção |
| Build | Maven / npm |

### Integrações externas

Plano, decisões e achados da integração: **`docs/planos/PLANO_BRAPI.md`** (executado em
24/09/2026). Rotinas (fuso de São Paulo, horários em `primevest.*` no `application.yml` de cada serviço):

| Rotina | Quando | Serviço |
|---|---|---|
| Fechamento B3 (seg–sex) e cripto (todo dia), com recuperação na subida — `ClosingPriceScheduler` | 19:00 | adm-api → "catálogo mudou" (FECHAMENTO) |
| Eventos de proventos da B3/FNET de **todo o catálogo** ligado a emissor + o que já esteve em carteira (F8; `primevest.proventos.scope`) — `ProventosScheduler` | 20:00 | adm-api → "catálogo mudou" (PROVENTOS), por emissor |
| Balanços da CVM (DFP/ITR do ano corrente e do anterior) + série trimestral + informe mensal de FII (F7) — `CvmStatementsScheduler` | 07:30 | adm-api |
| Preço oficial do Tesouro Direto (PU de venda, Tesouro Transparente), com recuperação na subida — `TreasuryPriceScheduler` (`docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md`) | 08:00 | adm-api → "catálogo mudou" (FECHAMENTO) |
| Índices de mercado, incremental com janela de revisão (30 dias nos diários, 3 meses nos mensais), com recuperação na subida — `MarketIndexScheduler` (`docs/planos/PLANO_INDICES_MERCADO.md`) | 10:00 | adm-api |
| Conferência do catálogo e dos índices atrasados (só loga) — `CatalogAuditScheduler` | seg 07:00 | adm-api |
| Cotações de **todo o catálogo** (ações e FIIs) pelo COTAHIST diário da B3, um arquivo por pregão, completando os que faltam, com recuperação na subida — `B3DailyQuotesScheduler` (`docs/planos/PLANO_PRECO_TETO.md`, 4.5; 10/10/2026) | 21:30 e 08:30 | adm-api |
| Logos (arquivos em `carga/data/logos/{TICKER}.{ext}` — o arquivo é o estado; só quem não tem arquivo; a subida não procura nada, o ativo novo é procurado no pedido "preciso do histórico") — `LogoScheduler` | dia 1, 06:00 | adm-api (a app-api só lê a pasta, `AssetLogos`) |
| Recálculo de proventos e valorização de todas as posições — `PositionRefreshScheduler` | 21:00 | app-api |
| Fila das integrações com corretoras — `IntegrationSyncScheduler` (08:45) e `IntegrationWorker` (5 s) | — | app-api |

Disparo manual para admin, **na adm-api (8081)**: `POST /api/v1/admin/{closing|proventos|catalog-audit|logos|issuers/import|issuers/link|b3/cotahist|b3/cotahist/diario|cvm/import|cvm/quarters|cvm/fii-monthly|cvm/proventos|indices|tesouro|bootstrap}` (`bootstrap` = carga inicial num banco vazio: emissores, COTAHIST, proventos, datas de pagamento da CVM, balanços, informe de FII, índices de mercado e preço do Tesouro, em ordem — docs/planos/PLANO_EMPRESAS_BALANCOS.md, 6.3; passo a passo no `README.md`)
(`b3/cotahist?from=2010&to=2026`, padrão 2010 até o ano corrente; `cvm/import?from=2010&to=2026&force=false` —
documento na mesma versão é pulado, `force=true` reprocessa a partir de `carga/data/cvm` sem baixar; ao terminar recalcula
a série trimestral, que `cvm/quarters` recalcula sozinha; `cvm/fii-monthly?from=2016&to=2026&force=false` — informe
mensal de FII em `fii_monthly`, mês na mesma versão é pulado; `indices?codes=CDI,IPCA&from=2010&to=2026` — padrão: todos,
2010 até o ano corrente; código desconhecido 400; `recent=true` = a rodada incremental das 10:00). Na **app-api (8080)** só
`POST /api/v1/admin/proventos/extrato` (importação da carteira). Script: `node carga/scripts/admin.mjs <rotina> [--json corpo.json]`
(ou `docker compose --profile carga run --rm tools node carga/scripts/admin.mjs <rotina>`) — login na app-api com a
`PRIMEVEST_SENHA` do `.env` e disparo na adm-api (ou na app-api, para o extrato); portas do `.env` (`API_PORT`, `ADM_API_PORT`).
Curadoria dos emissores (F3): `POST /api/v1/admin/issuers/fii-cnpjs` (lista `[{ticker, cnpj}]`, liga FII como `MANUAL`,
síncrono) e `PUT /api/v1/admin/assets/{id}/issuer` (`{issuerId | cnpj, shareClass}`; CNPJ que não é emissor vem do
cadastro da CVM; `shareClass` nula para o que não é ação; sem `issuerId` e sem `cnpj` desfaz) e
`PUT /api/v1/admin/assets/{id}/unit-composition` (`{"ON":1,"PNA":2}`; só ativo ligado como UNIT). Ambos gravam
`MANUAL`, que a rotina de ligação nunca sobrescreve. `admin.mjs` aceita `--method PUT --body '<json>'`.
Importação do extrato de proventos da B3 (admin, síncrona, app-api):
`POST /api/v1/admin/proventos/extrato` — ver "Extrato de proventos da B3".

Todas as fontes abaixo, menos o Google, são da **adm-api** (config em `primevest-adm-api/src/main/resources/application.yml`).

| API | Uso | Config |
|-----|-----|--------|
| **BrAPI** (plano gratuito) | Fechamento diário da B3 (OHLCV) dos ativos em carteira, lista oficial de tickers e renomes (conferência do catálogo), logos | `brapi.base-url`, `BRAPI_TOKEN` (no `.env`, header `Authorization: Bearer`) |
| **Site da B3** (sem token, não documentado) | Proventos (~12 meses), desdobramentos/grupamentos/bonificações, ticker↔ISIN, histórico longo de empresa | `b3.base-url` |
| **B3 — 12 meses acumulados** (`B3SupplementStore`) | A resposta de ~12 meses (a única com data de pagamento de ação) somada a `carga/data/b3/proventos-12m/{acoes,fundos}/{EMISSOR}.json`: o que sai da janela continua valendo, e um banco novo recebe as datas do arquivo (docs/planos/PLANO_CARGA_INICIAL.md, P1). O export inicial saiu do banco de desenvolvimento em 29/09/2026 (4.327 proventos, 503 emissores) | `primevest.data.dir` |
| **CVM — Relatório Proventos** (`CvmProventosReport`, `CvmPaymentDateService`) | Data de pagamento de 2022 em diante para o que o histórico da B3 não traz: índice IPE anual (`carga/data/cvm/ipe`), PDF de cada relatório do RAD (`carga/data/cvm/proventos/{protocolo}_{versao}.pdf`, lido com PDFBox), casado por ISIN, data-com (último dia com direitos) e valor; grava `payment_date_source = 'CVM'`. Na carga inicial e às 20:00 (ano corrente e anterior); `POST /api/v1/admin/cvm/proventos?from=2022&to=` | `cvm.base-url`, `cvm.rad-parallel-downloads` |
| **Levantamento de datas de pagamento** (`PaymentDateSurveyService`) | Datas de pagamento de proventos de ação 2010–2026 que nenhuma fonte oficial gratuita traz, levantadas em sites públicos (Investidor10, Status Invest, Fundamentus, Brapi, RI) e revisadas pelo Guilherme: `carga/data/levantamento/datas-pagamento.json`, gerado de `carga/data/levantamento/origem/*.xlsx` por `carga/scripts/levantamento/xlsx_para_json.py`. Casa por ticker (+ fracionário), tipo, data-com, parcela e valor; grava só em evento sem data, `payment_date_source = 'LEVANTAMENTO'`; linha `A_DEFINIR` não grava. Na carga inicial (depois da CVM) e `POST /api/v1/admin/proventos/levantamento` (docs/planos/PLANO_CARGA_INICIAL.md, 11) | `primevest.data.dir` |
| **FNET** (`FnetClient`, refeito na F8) | Proventos de FII além de 12 meses (avisos estruturados). O histórico sai **só de `carga/data/fnet/{id}.xml`**, por um índice local `CNPJFundo` → avisos (a carga não chama o FNET). **Fonte principal de FII** (D15 do docs/planos/PLANO_CARGA_INICIAL.md): aplicado antes da B3, e o evento que a B3 gravou como reserva passa a ser do FNET quando o aviso chega; quando a B3 mostra um provento mais novo que o último aviso gravado do fundo, o FNET é consultado pelo CNPJ (no máximo uma vez por dia por fundo) e o aviso novo é baixado (PENDENCIAS 18.10 d). A busca por CNPJ e o download ficam no client: aviso baixado vai para `carga/data/fnet` e nunca é baixado de novo; download com timeout de 5 s, até 6 tentativas com espera crescente e no máximo 3 simultâneos; lê os dois formatos de XML (o antigo, 2016–2019, com `ValorProventoCota`). Aviso ligado pelo CNPJ, sem recibo (final 12–19) e sem data-base depois do pagamento | `fnet.*`, `primevest.data.dir` |
| **Série histórica da B3 — COTAHIST** (`B3HistoricalQuotesClient`) | Preço diário não ajustado de ações (lote padrão e fracionário) e FIIs desde 2010, e o ISIN de cada ticker do catálogo. Zip por ano em `carga/data/b3/cotahist`; desde 10/10/2026 também **um zip por pregão** (`COTAHIST_DddMMaaaa.ZIP`, ~0,5 MB, em `carga/data/b3/cotahist/diario`), pela rotina das 21:30/08:30 — preço de todo o catálogo sem cota da BRAPI. Dia gravado pela B3 não é sobrescrito pela BRAPI (`asset_price_history.source`, V29) | `b3.cotahist-url` |
| **CVM dados abertos** (`CvmOpenDataClient`) | Emissores (cadastro de companhias, FCA, informe mensal de FII); informe mensal de FII 2016+ em `fii_monthly` (`CvmFiiMonthly`, partes complemento e ativo/passivo, F7); balanços DFP (2010+) e ITR (2011+) (`CvmStatements`); histórico de capital do Formulário de Referência (`CvmFreCapital`, quantidade de ações antes de 2020 e régua da escala depois). Cache em `carga/data/cvm/{cad,fca,fii,dfp,itr,fre}` — o arquivo é o estado (`docs/planos/PLANO_EMPRESAS_BALANCOS.md`) | `cvm.base-url`, `primevest.data.dir` |
| **CoinGecko** | Preço de cripto (id em `assets.price_source_ref`) e logos das moedas | `coingecko.base-url` |
| **Banco Central — API SGS** (`BcbSgsClient`, sem token) | Índices de mercado (`docs/planos/PLANO_INDICES_MERCADO.md`): CDI (série 12), Selic (11), IPCA (433), IGP-M (189), INPC (188), TR (226), meta Selic (432), dólar PTAX venda (1). Uma consulta por ano civil com `dataInicial`/`dataFinal` (série diária recusa janela de mais de 10 anos com 406; 404 = sem valores no período); timeout de 20 s e até 4 tentativas (o SGS às vezes demora). A TR repete o início com fins diferentes: fica o período de um mês cheio. Data futura não entra (a meta Selic vem preenchida até a próxima reunião do Copom). Cache `carga/data/bcb/sgs/{código}/{ano}.json`: ano fechado com arquivo só do disco; o corrente e o anterior sempre da fonte (`IndexYearCache`) | `bcb.sgs-base-url`, `indices.*`, `primevest.data.dir` |
| **Tesouro Transparente — preços e taxas do Tesouro Direto** (`TesouroPriceClient`, sem token) | `PrecoTaxaTesouroDireto.csv`: todo o histórico desde 2002 num arquivo (~14 MB, ~1 min), atualizado todo dia útil ~07:20 com o D-1; colunas tipo, vencimento, data base, taxas e PU de compra/venda/base. Cache `carga/data/tesouro/` (`PublicFileCache`, só baixa se mudou). `TreasuryPriceService` casa a linha com o título do catálogo por **tipo + vencimento** (`asset_terms`; o ano do ticker nem sempre é o do vencimento: Renda+ 2065 vence em 2084), grava o **PU de venda** (resgate antecipado; sem ele, o PU base) em `asset_price_history` (`source = TESOURO`) e o último em `assets.current_price` | `tesouro.prices-url`, `tesouro.timeout-seconds` |
| **B3 — índices** (`B3IndexClient`) | Fechamento diário do Ibovespa e do IFIX: `indexStatisticsProxy/IndexCall/GetPortfolioDay`, um ano por chamada, numa grade dia × mês com texto pt-BR. Cache `carga/data/b3/indices/{índice}/{ano}.json`, mesma regra | `b3.base-url`, `indices.*` |
| **Google Identity Services** | Login social (fluxo de ID token, sem client secret) | `VITE_GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_ID` |

**Integrações com corretoras e com a B3** (`docs/planos/PLANO_CRIPTO_BINANCE.md` e `docs/planos/PLANO_INTEGRACAO_B3.md`;
código no módulo `primevest-integration` desde 03/10/2026). **Binance**: o usuário conecta a própria conta com uma
chave **só de leitura**. **B3** (Área do Investidor): o usuário informa o CPF e autoriza o PrimeVest na B3 (opt-in);
entram as negociações de renda variável (agrupadas por dia, papel e lado — B1) e as compras de Tesouro; liquidações,
eventos do papel e proventos da B3 só são conferidos (o catálogo já os aplica). Configuração `b3i.*` / `B3I_*`
(credenciais do Pacote de Acesso e certificado, que dependem de contrato com a B3; sem `B3I_ENABLED=true` a B3 some
da tela); até lá, o simulador `b3-investidor-mock` (profile `b3mock`). Uma consulta por CPF por dia; a sincronização
diária roda às 08:45, depois de a B3 publicar o dia anterior. A credencial (chave ou CPF) é cifrada (AES-256-GCM) pelo
`CredentialCipher` com a chave-mestra `PRIMEVEST_CREDENTIALS_KEY` do `.env` e só o `CredentialVault`
grava, lê ou apaga. Sem a chave a API sobe e só as integrações ficam indisponíveis. **Perder a
chave-mestra = perder as credenciais gravadas** (os usuários recadastram). Rotação: chave nova em
`PRIMEVEST_CREDENTIALS_KEY`, a antiga em `PRIMEVEST_CREDENTIALS_PREVIOUS_KEYS` (`1:<base64>`) e
`PRIMEVEST_CREDENTIALS_KEY_VERSION` incrementada.

---

## 📁 Estrutura do Projeto

```
primevest/
├── docker-compose.yml           # banco + redis + app-api (8080) + adm-api (8081) + web
├── docker-compose.teste.yml     # override da pilha primevest-teste (com .env.teste, fora do git)
├── .env.example                 # variáveis (copiar para .env)
├── README.md                    # visão geral do produto
├── Claude.md                    # este arquivo — referência operacional
├── docs/
│   ├── PENDENCIAS.md            # o que ficou por fazer, com o porquê
│   ├── planos/                  # um PLANO_<ASSUNTO>.md por escopo (planos novos nascem aqui)
│   ├── REGRAS_CLAUDE.md, SCHEDULE_TASKS.md, ADM_TASKS.md, TESTES_FUNCIONAIS.md,
│   │   PRIMEVEST_DB.md, USE_CASES.md   # organização: regras de trabalho, rotinas, admin, testes, banco
│   └── brapi/, emissores/       # planilhas de apoio dos seeds
├── .claude/                     # ponte de execução: build.ps1 + logs/ (ver README de lá)
├── carga/                       # carga inicial (docs/planos/PLANO_CARGA_INICIAL.md)
│   ├── data/                    # cache das fontes e dados da carga (ignorado pelo git; cópia no bucket R2)
│   └── scripts/                 # admin.mjs, get.mjs, gerar_seed_emissores.mjs, data/ (rclone ↔ bucket R2),
│                                # levantamento/, sql/ (conferência)
│
├── pom.xml                      # agregador Maven (docs/planos/PLANO_ADM_API.md): versões, compilador, testes
├── Dockerfile                   # backend, contexto na raiz; alvos dev e runtime; ARG MODULE
├── dev-watch.sh                 # hot reload do backend (core + módulo do contêiner; na app-api, também o integration)
│
├── primevest-core/              # biblioteca compartilhada
│   └── src/
│       ├── main/java/com/primevest/
│       │   ├── domain/          # TODAS as entidades e repositórios (asset, dividend, portfolio,
│       │   │                    # transaction, issuer, strategy, goal, user...) + regras dos dois
│       │   │                    # lados: PositionCalculator, DividendRecalculator, AppreciationUpdater,
│       │   │                    # CashEventWriter, AssetLogos (leitura), catalog/ (mensagens)
│       │   ├── domain/auth/     # JwtTokenProvider (typ access/refresh), JwtAuthFilter, SessionValidator,
│       │   │                    # UserDetailsServiceImpl; domain/user/LegalTerms (versão dos termos)
│       │   ├── config/          # SecurityUtils
│       │   └── exception/       # GlobalExceptionHandler, BusinessException...
│       ├── main/resources/db/migration/   # V1 .. V44 (as duas aplicações rodam o Flyway)
│       └── test/                # test-jar: IntegrationTestBase, JpaTestBase, PostgresContainer,
│                                # Fixtures, CatalogIds, Tokens, application-test.yml; SchemaTest
│
├── primevest-integration/       # integrações (Binance, B3) — biblioteca usada só pela app-api (03/10/2026)
│   └── src/
│       ├── main/java/com/primevest/
│       │   ├── domain/integration/source/      # SourceConnector, ImportedMovement, FetchResult, CredentialCheck
│       │   ├── domain/integration/credential/  # CredentialCipher (AES-256-GCM), CredentialVault, SourceCredentials
│       │   │                                   #   (ApiKeyCredentials, InvestorDocument = CPF)
│       │   ├── domain/integration/pipeline/    # ImportPipeline, ImportRules (CryptoImportRules), pré-visualização
│       │   ├── integration/binance/            # BinanceClient, BinanceConnector
│       │   └── integration/b3investidor/       # B3InvestidorClient (token, mTLS, paginação), B3InvestidorConnector,
│       │                                       #   B3EquityImportRules (B1–B10), B3InvestidorProperties (b3i.*)
│       └── test/                # unitários + test-jar (BinanceFake, ContaSintetica, ContaSinteticaB3) para os Integracoes*IT
│
├── primevest-adm-api/           # rotinas de administrador — o catálogo (porta 8081)
│   └── src/
│       ├── main/java/com/primevest/adm/
│       │   ├── PrimevestAdmApplication.java
│       │   ├── config/          # AdmSecurityConfig, AdmSchedulingConfig, AdmSwaggerConfig,
│       │   │                    # AdmMessagingConfig (publica "catálogo mudou", ouve "sob demanda")
│       │   ├── domain/admin/    # AdminController (/api/v1/admin/*)
│       │   ├── domain/          # dividend/ProventosCatalogService, pricehistory/ClosingPriceService e
│       │   │                    # HistoricalQuotesImportService, issuer/ (import e ligação),
│       │   │                    # fundamentals/ (balanços, série trimestral), asset/ (logos, conferência)
│       │   ├── integration/     # b3/ (B3, FNET, COTAHIST), cvm/, brapi/, coingecko/, indices/ (SGS, B3), PublicFileCache
│       │   └── scheduler/       # fechamento, proventos, balanços, logos, conferência
│       └── test/                # recortes reais da B3 e da CVM (test-jar, usados pela app-api)
│
├── primevest-app-api/           # a carteira (porta 8080) — o antigo primevest-api
│   └── src/
│       ├── main/java/com/primevest/
│       │   ├── PrimevestApplication.java   # varredura exclui com.primevest.adm
│       │   ├── config/          # SecurityConfig, TermsAcceptanceFilter, CorsConfig, RedisConfig, SwaggerConfig,
│       │   │                    # PasswordEncoderConfig, SchedulingConfig, CatalogMessagingConfig
│       │   ├── domain/          # controllers, serviços e DTOs de auth (AuthService, SignupService,
│       │   │                    # PasswordResetService, AccountFactory; verification/: códigos no Redis,
│       │   │                    # RateLimiter; mail/: AccountMailer SMTP), user (UserService, UserCpfService), asset, portfolio
│       │   │                    # (PositionRefreshService), transaction, dividend (extrato),
│       │   │                    # strategy, goal, ceilingprice, integration (orquestração das
│       │   │                    # corretoras: IntegrationService, controller, worker, DTOs)
│       │   └── scheduler/       # IntegrationSyncScheduler, PositionRefreshScheduler
│       ├── main/resources/      # application.yml, -dev, -docker, -prod
│       └── test/                # functional/ (CatalogoE2EBase: adm-api só no classpath de teste)
│
└── primevest-web/
    ├── Dockerfile               # alvos: dev (vite) e runtime (nginx)
    ├── vite.config.ts           # proxy /api configurável por VITE_API_TARGET
    ├── eslint.config.js         # flat config do ESLint 9 (18/09/2026)
    ├── tailwind.config.js
    └── src/
        ├── api/                 # clients axios por domínio
        ├── components/
        │   ├── charts/
        │   ├── forms/           # AssetTypeSelect, TickerAutocomplete, FixedIncomeFields
        │   ├── auth/            # AuthShell, CodeStep (código por e-mail), TermsGate (novo aceite)
        │   ├── layout/
        │   ├── settings/        # EmailChangeModal, DeleteAccountModal, AvatarCropModal
        │   └── ui/              # Modal, MaskedNumberInput, CurrencyInput, PercentInput...
        ├── content/             # legal.ts: Termos de Uso e Política de Privacidade (provisórios)
        ├── context/             # XContext.ts (contexto + hook) e XProvider.tsx: Auth, Theme, Toast
        ├── hooks/               # useSortable, useGoogleSignIn
        ├── pages/               # 16 páginas (inclui Signup, ForgotPassword, Legal)
        ├── types/               # interfaces TypeScript
        └── utils/               # format.ts
```

---

## 🗄 Banco de Dados

### Conexão

| Ambiente | Host | Porta | Database | Usuário |
|----------|------|-------|----------|---------|
| Docker (a partir do host) | localhost | 5433 | db_primevest | postgres |
| Docker (entre containers) | db | 5432 | db_primevest | postgres |
| Local | localhost | 5432 | db_primevest | postgres |

Credenciais vêm de `DB_USERNAME` / `DB_PASSWORD`, com padrão `postgres`/`postgres`
em desenvolvimento. **Não versione credenciais reais.**

### Tabelas — 16 + integrações (V25)

| Tabela | Descrição |
|--------|-----------|
| `users` | Usuários (bcrypt em `password_hash`, ou `google_id` para OAuth). V41: `phone` (E.164, único), CPF cifrado (`cpf_ciphertext`/`cpf_iv`/`cpf_key_version`, AES-256-GCM com o id do usuário como AAD) + `cpf_hash` (HMAC, único), `email_verified_at`, `password_changed_at` (corta tokens anteriores), `terms_accepted_at`/`terms_version`, `theme`. E-mail sempre minúsculo (`uk_users_email_lower`) |
| `user_avatars` | Foto de perfil enviada (V42): JPEG 512×512 regravado pela API (sem metadados). Tabela própria para a imagem não vir em toda leitura de `users`. `users.avatar_url` continua sendo a foto do Google; a enviada tem precedência |
| `portfolios` | Carteira (1 por usuário) |
| `portfolio_assets` | A posição. Chave lógica: `(portfolio_id, asset_id, note_id)` com `NULLS NOT DISTINCT`. **Não guarda cotas, preço médio nem custo** — saem das transações (V28) |
| `transactions` | Compras e vendas. Fonte da verdade da posição. `note_id` nulo na renda variável |
| `assets` | Catálogo global: `ticker`, `name`, `asset_type_id`, `asset_category_id`, `current_price`, `active` |
| `asset_terms` | Parâmetros públicos do papel, PK = `asset_id`. Só Tesouro Direto hoje |
| `fixed_income_notes` | **O papel do usuário**: `issuer`, `maturity_date`, `rate_type`, `indexer`, `indexer_rate` |
| `asset_types` | 12 tipos |
| `asset_categories` | 80 categorias, **um nível só**: unicidade `(asset_type_id, name)` (V31). Ação: setor B3; FII: tipo de fundo (TIJOLO, PAPEL, FOF, HIBRIDO, OUTROS). O segundo nível está no emissor: segmento B3 da ação em `issuers.b3_segment`, segmento de atuação do FII em `issuers.segment` |
| `strategies` | % ideal por tipo — `unique(user_id, asset_type_id)`. É a **geração de renda**: posição de reserva nunca entra (`include_reservation` saiu na V10) |
| `strategy_reservations` | Estratégia da reserva de emergência — **uma por usuário** (`unique(user_id)`), nunca apagada. `type_reservation` 1 = por percentual (`target_percentage`), 2 = por valor (`target_value`); a outra coluna fica nula, e `ck_strategy_reservations_target` confere |
| `dividends` | Proventos. **Sem `user_id`** — ver Pontos de Atenção. `total_amount` é o **bruto** (truncado nos centavos, como a B3); `net_amount` é o **líquido** depois do IR retido (V23) |
| `withholding_tax_rules` | Alíquotas de IR retido na fonte por tipo de provento, tipo de ativo e vigência, com a base legal (V23). Lei nova = migration — ver "IR retido nos proventos" |
| `ceiling_prices` | Cálculo de preço teto de uma ação (V46, modelo PREÇO TETO V2) — `unique(user_id, asset_id)`. Guarda **só as entradas**, todas `NOT NULL`: `shares`, `roe_pct`, `payout_pct`, `discount_rate_pct`, `perpetual_growth_pct`, `safety_margin_pct` (pontos), `price`, e `inputs_origin` (JSONB: PADRAO/INFORMADO + referência e valor do padrão no "Salvar"). O resultado sai das entradas na leitura (`CeilingPriceRules`) |
| `ceiling_price_quarters` | Os 4 lucros de um cálculo (V46): valor digitado, unidade, moeda, câmbio, `value_brl` (o que entra na conta) e origem (a fonte saiu na V47). PK `(ceiling_price_id, position)` |
| `goals` | Metas de investimento |
| `asset_price_history` | Histórico de cotações. `source` (V29): `BRAPI`, `COINGECKO` ou `B3_COTAHIST` — a série da B3 vence e a BRAPI não a sobrescreve; `TESOURO` (V44): PU de venda do Tesouro Direto desde 2010 |
| `integration_connections` | Conexão do usuário com uma fonte externa (hoje só `BINANCE`); `label` único por usuário, `status`, `consent_accepted_at` (V25) |
| `integration_credentials` | Credencial **cifrada**, 1:1 com a conexão (`ciphertext`, `iv`, `key_version`, `fingerprint`). Nunca sai na API; apagada na revogação |
| `integration_cursors` | Onde cada fluxo da fonte parou (`stream`, `cursor_value`) — sincronização incremental |
| `integration_runs` / `integration_run_items` | Cada sincronização e o que dela não virou transação (não mapeado, possível duplicata, recusado) |
| `issuers` | Emissores (V28): companhia aberta ou FII, 1 por CNPJ. `kind`, `cvm_code`, setor CVM e B3, segmento de listagem; no FII, `segment` curado (só seed, a importação não toca) e `cvm_segment` informativo. `assets.issuer_id`, `share_class`, `issuer_link_source` fazem a ligação (F3). Semeado pela V30 (369 companhias, 346 FIIs, 1.139 ligações) |
| `unit_compositions` | Composição das units, `(asset_id, share_class) → quantity` (V28) |
| `cvm_documents` | Um DFP ou ITR entregue por emissor (V32): `doc_type`, `reference_date`, `version` (reapresentação substitui), `consolidated` (D6). Volume de arquivo — nenhuma migration semeia |
| `cvm_statement_values` | Métricas de cada documento (`FinancialMetric`: receita, EBIT, lucro, lucro da controladora, ativo, caixa, dívida bruta, PL, PL de não controladores, FCO, JCP e dividendos declarados, LPA por classe), em reais, por período (`period_start` nulo = posição do balanço; o DRE do ITR tem o trimestre e o acumulado) |
| `share_counts` | Quantidade de ações de cada documento, como veio: `COMPOSICAO` (DFP/ITR 2020+, com tesouraria, `scale_factor` 1 ou 1.000 conferido pelo FRE ou pelo LPA — `ShareScale` —, o que sobrar pelo documento validado mais próximo, `VIZINHO`, e depois pelo anterior ajustado pelos grupamentos/desdobramentos registrados entre as datas, `EVENTO`, V36) ou `FRE` (antes de 2020, capital integralizado do Formulário de Referência, sem tesouraria). `REVISAR` = nenhuma conferência fechou, sem fator |
| `company_quarters` | Série trimestral derivada (V33, F6), uma linha por `(issuer_id, period_end)` de cada DFP/ITR: `*_q` trimestre isolado (3 meses do ITR, ou acumulado menos o trimestre anterior — 4T = DFP − 9 meses), `*_ttm` 12 meses (anual do DFP ou soma de 4 trimestres consecutivos; faltando um, nulo), posição (PL, PL da controladora, ativo, caixa, dívida) e ações em unidades (nulas em `REVISAR`). Recalculada inteira por `CompanyQuarterService` (regras em `QuarterSeries`) ao fim de cada carga de balanços; nada próprio nela |
| `fii_monthly` | Informe mensal de FII da CVM (V34, F7), cru, uma linha por `(issuer_id, reference_month)` (primeiro dia do mês), 09/2016 em diante, só fundos emissores; `version` é a maior recebida (reapresentação substitui). Do complemento: PL, ativo, cotas, VP por cota, cotistas, taxa de administração, DY e amortização do mês (frações: 0,0068 = 0,68%). Do ativo/passivo: liquidez, total investido, imóveis, imóveis via SPE, recebíveis (CRI/CRA, LCI/LCA, LH, LIG), cotas de FII, passivo e rendimentos a distribuir (÷ cotas = rendimento estimado, **só indicador**, nunca vira `asset_cash_events`). Gravada por `FiiMonthlyImportService` (adm-api) |
| `integration_crypto_exclusions` | Operação de cripto importada e excluída pelo usuário: a sincronização não recria (regra E2 do plano) |
| `market_indices` | Cadastro dos índices de mercado (V43, seed): `code` (o mesmo nome do enum `Indexer` para CDI, SELIC, IPCA, IGPM, INPC, TR — a nota acha a série por `fixed_income_notes.indexer`, sem FK; mais SELIC_META, USD_PTAX, IBOV, IFIX), `periodicity` (DIARIA/MENSAL), `unit` (PCT_DIA, PCT_MES, PCT_PERIODO da TR, PCT_ANO da meta, PONTOS, BRL), `source` (BCB_SGS/B3) + `source_ref` (código SGS ou sigla), `history_from` (2010) |
| `market_index_values` | Série de cada índice, PK `(index_id, reference_date)`, `value` como a fonte publica (mensal no dia 1º; só até hoje). Gravada só pela adm-api (`MarketIndexImportService`); o acumulado **não** é gravado — sai do `MarketIndexCalculator` (core) |

### Asset Types — 12

| ID | name | description | price_source | fixed_income |
|----|------|-------------|--------------|--------------|
| 1 | ACAO | Ações | BRAPI | não |
| 2 | FII | FIIs | BRAPI | não |
| 3 | RENDA_FIXA | Renda Fixa | MANUAL | **sim** |
| 4 | TESOURO_DIRETO | Tesouro Direto | MANUAL | **sim** |
| 5 | ETF | ETFs | BRAPI | não |
| 6 | FUNDO_INVESTIMENTO | Fundos de Investimentos | MANUAL | não |
| 7 | BDR | BDRs | BRAPI | não |
| 8 | STOCK | Stocks | MANUAL | não |
| 9 | REIT | REITs | MANUAL | não |
| 10 | ETF_INTERNACIONAL | ETFs Internacionais | MANUAL | não |
| 11 | CRIPTO | Criptomoedas | COINGECKO | não |
| 12 | OUTROS | Outros | MANUAL | não |

> Os ids acima valem para um banco que rodou as migrations nesta ordem. **Nunca
> fixe id de `BIGSERIAL` em código ou migration** — resolva por `name`, como
> fazem todos os seeds.

`price_source` e `fixed_income` são colunas, e não lista de nomes no código, de
propósito. Antes o scheduler fazia `"CRYPTO".equals(name)`, que quebrava em
silêncio a cada renomeação ou tipo novo. A tela e os services só perguntam à
coluna.

> **Ordem** (07/10/2026): quem lista os tipos usa `AssetTypeRepository.findAllByOrderByIdAsc()` — a ordem do cadastro
> (V2). O `findAll()` sem ORDER BY devolve a ordem física da tabela, e no PostgreSQL um UPDATE regrava a linha no fim:
> depois da V44, que atualiza o Tesouro Direto, ele passou a vir por último no modal e no rebalanceamento.

> **Convenção**: em DTOs e respostas de API use `getDescription()` para exibir ao
> usuário; `getName()` é para lógica interna. `AssetCategory.getDisplayName()`
> encapsula o fallback description → name.

### Flyway — V1 a V44

O Flyway roda do zero no perfil `docker`. No perfil `dev` fica desligado, porque
o banco local é gerenciado à mão.

**Segundo reset, em 18/09/2026.** As 30 migrations anteriores voltaram a
descrever o caminho e não o resultado: criavam colunas derrubadas depois
(`current_value`, `total_shares`, `total_invested`, `dividends.asset_id`),
tornavam campos anuláveis meses adiante e classificavam os ativos em duas
etapas — o seed inseria a categoria antiga e a V23 reclassificava tudo.
Nada disso interessa a um banco novo, e cada passo intermediário era mais uma
coisa que podia falhar numa subida limpa.

O reset deixou **9 arquivos, 156 KB contra 340 KB**. Desde então entraram
V10–V12 (20/09/2026), V13–V22 (24/09/2026, integração BRAPI/B3) e V23
(24/09/2026, IR retido nos proventos), V24 (25/09/2026, extrato de proventos
da B3) V25 a V27 (25/09/2026, integrações com corretoras) V28 e V29 (27/09/2026, emissores e origem do preço histórico), V30 e V31 (27/09/2026, seed dos emissores e
categorias sem segmento), V32 (28/09/2026, balanços da CVM), V33 (28/09/2026, série trimestral), V34 (28/09/2026, informe mensal de FII), V35 a V37 (28/09/2026, amortização da B3, escala por evento e `assets` sem estado — F10), V38 (29/09/2026, catálogo sem o extrato), V39 (02/10/2026, datas de pagamento do levantamento), V40 a V42 (03–05/10/2026, integração B3, cadastro de usuário, foto de perfil) e V43 (05/10/2026, índices de mercado), V44 (05/10/2026, preço oficial do Tesouro), e o banco fica na **versão 44**.

| Arquivo | Conteúdo |
|---------|----------|
| V1 | **Schema completo**: as 14 tabelas na ordem das FKs, com constraints e índices |
| V2 | Seed dos 12 tipos |
| V3 | Seed das 116 categorias, já com `segment` (a V31 consolida e tira a coluna) |
| V4–V7 | Seed dos ativos: 782 ações, 337 FIIs, 130 Tesouro, 15 renda fixa |
| V8 | Seed dos 130 `asset_terms` |
| V9 | Usuário de desenvolvimento + carteira |
| V10 | Derruba `strategies.include_reservation`, com guarda (`RAISE EXCEPTION` se houver `true`) |
| V11 | `strategy_reservations` |
| V12 | Estratégia de reserva do usuário de desenvolvimento (percentual, 0,00%) |
| V13 | Nomes das ações com acento, da BRAPI (98 linhas) |
| V14 | 9 ativos inativos (deslistados e fundos fiscais), exceto com posição aberta |
| V15 | 14 ações e 9 FIIs novos; INHF11 vira FII Híbrido |
| V16–V17 | 12 categorias e 219 ETFs, com a cotação e o OHLCV de 23/09/2026 |
| V18 | 22 categorias e 892 BDRs, com a cotação e o OHLCV de 23/09/2026 |
| V19 | `assets.price_source_ref`, 15 categorias e 227 criptomoedas (sem cotação) |
| V20 | Proventos em duas camadas: `asset_cash_events` e `asset_stock_events` (catálogo) + `dividends.cash_event_id/shares_on_date/origin`; `assets.isin/cnpj/cash_history_loaded_at` |
| V21 | `assets.logo_path` e `logo_checked_at` (saíram na V37) |
| V22 | `asset_cash_events.seq`: parcelas iguais do mesmo provento |
| V23 | IR retido nos proventos: `withholding_tax_rules` (+ carga inicial) e `dividends.withholding_rule_id/withholding_rate/withholding_tax/net_amount_per_share/net_amount/tax_checked_at` |
| V24 | Extrato de proventos da B3: fonte `EXTRATO_B3` em `asset_cash_events.source` e `asset_cash_events.payment_date_source` (`EXTRATO_B3` ou `DEDUZIDA`; nula = data da fonte oficial) — desfeita na V38 |
| V25 | Integrações (F1 do `docs/planos/PLANO_CRIPTO_BINANCE.md`): as 6 tabelas `integration_*` e `transactions.origin` (`MANUAL`/`AUTO`), `external_id`, `connection_id`, `executed_at`, `edited_at`, `source_snapshot` (JSONB); `ck_transactions_origin` e índice único parcial `(connection_id, external_id)` |
| V26 | Confirmação da importação (F4): `integration_run_items` guarda o lote inteiro (+ `duplicate_of`, `resolution`, `transaction_id`); `integration_runs.next_cursors`, `fiat_deposited` |
| V27 | Fila das integrações (F5): `integration_runs.balances` (saldo da corretora, aviso de divergência) e índice `(status, created_at)` |
| V28 | Emissores (F1 do `docs/planos/PLANO_EMPRESAS_BALANCOS.md`): `issuers`, `unit_compositions` e `assets.issuer_id/share_class/issuer_link_source` |
| V29 | `asset_price_history.source` (`BRAPI`/`COINGECKO`/`B3_COTAHIST`), com as linhas de cripto marcadas `COINGECKO` (F2) |
| V30 | Seed dos emissores (F4/M3): 369 companhias e 346 FIIs (CNPJ literal, segmento curado do FII), 1.139 ligações ativo → emissor com classe, origem e ISIN, 36 linhas de composição de units. Gerado do banco por `carga/scripts/gerar_seed_emissores.mjs`; chave natural (CNPJ, ticker), `ON CONFLICT` preserva o que veio da fonte |
| V31 | Categoria sem segmento (F4/M4, D11): uma linha por `(tipo, name)` — os ativos das duplicadas vão para a de menor id —, `segment` sai, unicidade `(asset_type_id, name)`. 165 → 80 |
| V32 | Balanços da CVM (F5/M5): `cvm_documents`, `cvm_statement_values` (unicidade com `NULLS NOT DISTINCT` no `period_start`) e `share_counts`. Sem `cvm_account_map`: as métricas são achadas pela descrição da conta (`FinancialMetricRules`) |
| V33 | Série trimestral (F6): `company_quarters`, derivada de `cvm_statement_values` e `share_counts`; nasce vazia e é preenchida pela carga de balanços (ou `POST /api/v1/admin/cvm/quarters`) |
| V34 | Informe mensal de FII (F7/M6): `fii_monthly`, um mês por fundo emissor, cru (PL, cotas, VP por cota, cotistas, taxas do mês, carteira e passivo); nasce vazia e é preenchida por `POST /api/v1/admin/cvm/fii-monthly` e pela rotina das 07:30 |
| V35 | `AMORTIZACAO RF` da B3 vira `AMORTIZACAO`: a linha que repete a do FNET sai (completando pagamento e aprovação), a que só a B3 tem recebe o tipo |
| V36 | `share_counts.scale_check` aceita `EVENTO` |
| V37 | M7/F10: `assets` sem `cnpj` (é do emissor), `cash_history_loaded_at` (histórico se reconhece pelo gravado e por `carga/data/b3/proventos`, `carga/data/fnet`), `logo_path` e `logo_checked_at` (o arquivo em `carga/data/logos` é o estado) |
| V38 | docs/planos/PLANO_CARGA_INICIAL.md (D13): catálogo sem o extrato — eventos `EXTRATO_B3` de FII viram `FNET`, os demais saem (proventos do usuário ficam `MANUAL`), datas `EXTRATO_B3`/`DEDUZIDA` apagadas; `source` só `B3`, `B3_HISTORICO`, `FNET`; `payment_date_source` só `CVM` |
| V39 | docs/planos/PLANO_CARGA_INICIAL.md (D14, seção 11): `payment_date_source` aceita também `LEVANTAMENTO` (datas de pagamento levantadas, `carga/data/levantamento`) |
| V40 | docs/planos/PLANO_INTEGRACAO_B3.md (F2): `integration_connections` aceita `provider = B3` e `status = PENDING_AUTHORIZATION` |
| V42 | docs/planos/PLANO_CADASTRO_USUARIO.md (adendo): `user_avatars` — foto de perfil enviada (JPEG 512×512, ≤ 1 MB), 1:1 com `users`, `ON DELETE CASCADE` |
| V44 | docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md (R1): `asset_types.price_source` aceita `TESOURO` (o tipo Tesouro Direto passa a ser cotado) e `asset_price_history.source` aceita `TESOURO` |
| V45 | docs/planos/PLANO_PRECO_TETO.md (D4): `company_quarters.dividends_paid_q/_ttm` — dividendos + JCP pagos aos acionistas da controladora (DFC, métrica `DIVIDENDOS_PAGOS`), base do payout |
| V47 | docs/planos/PLANO_PRECO_TETO.md (revisão de 11/10/2026): `ceiling_price_quarters` sem `source_ref` — a fonte do lucro trimestral deixou de existir |
| V46 | docs/planos/PLANO_PRECO_TETO.md (4.1–4.2): `ceiling_prices` recriada (estava vazia; trava se tiver linha) com as entradas do modelo V2, sem `max_price` e `alert_enabled`; `ceiling_price_quarters` nova |
| V43 | docs/planos/PLANO_INDICES_MERCADO.md (X1): `market_indices` (10 índices semeados) e `market_index_values` (vazia; a série vem da adm-api) |
| V41 | docs/planos/PLANO_CADASTRO_USUARIO.md (seção 5): colunas novas de `users` (celular, CPF cifrado + hash, verificação de e-mail, `password_changed_at`, aceite dos termos, tema), índices únicos de e-mail sem caixa, celular e CPF; o usuário semeado ganha e-mail verificado e o aceite da versão `2026-10-provisorio` |

**Como o reset foi verificado** — vale registrar, porque é o método a repetir se
houver um terceiro. Construiu-se um banco `db_ref` aplicando as 30 migrations
antigas em ordem, e um `db_new` com o conjunto novo. Os dois foram comparados
por consulta normalizada e ordenada, não por `pg_dump` bruto: **108 colunas,
53 constraints, 48 índices e 1.524 linhas de seed, idênticos nos quatro eixos.**
Depois o volume foi derrubado (`down -v`) e a API subiu com `ddl-auto: validate`,
que é a prova de que as entidades JPA batem com o schema gerado.

A comparação pegou duas divergências que a leitura dos arquivos não pegaria: a
unicidade de `asset_categories` inclui `segment` (a V23 trocou a constraint), e
uma categoria é o par `(name, segment)` — `CONSUMO_CICLICO` sozinho aparece em
21 segmentos, então resolver a categoria só pelo nome, como fazia o rascunho,
apontaria 782 ações para a linha errada.

> A ordem relativa das colunas mudou nas tabelas que tinham sofrido
> `DROP COLUMN` (`portfolio_assets`, `portfolios`, `transactions`,
> `fixed_income_notes`, `dividends`): num `CREATE TABLE` novo as colunas de
> auditoria ficam no fim, em vez de onde o `ALTER` as deixou. Não afeta JPA nem
> consulta nomeada — só `SELECT *` e a saída do `pg_dump`.

Ordem das tabelas (é a ordem das FKs, não alfabética): users → asset_types →
asset_categories → assets → asset_terms → portfolios → **fixed_income_notes** →
portfolio_assets → transactions → dividends → asset_price_history → strategies →
ceiling_prices → goals, e depois **strategy_reservations** (V11).

`fixed_income_notes` precisa vir antes de `portfolio_assets` e `transactions`,
porque as duas referenciam a nota.

Estado após uma subida limpa:

| Tabela | Linhas |
|--------|--------|
| assets | 1.264 |
| asset_terms | 130 |
| asset_categories | 116 (80 depois da V31) |
| asset_types | 12 |
| users | 1 |
| portfolios | 1 |
| strategy_reservations | 1 |
| todo o resto | 0 |

**Não há seed de transação, posição ou estratégia.** Os dados de teste do
segundo reset (29 transações, 25 posições, 14 títulos, 3 estratégias) foram
descartados por decisão do usuário, com dump guardado fora do repositório.

> **Se o Flyway recusar a subida** reclamando de checksum ou de migration
> aplicada que não existe mais: o volume tem o histórico antigo.
> `docker compose down -v` e suba de novo.

### Convenções das migrations

- **Nunca editar migration já aplicada.** O Flyway guarda checksum; qualquer
  alteração quebra a próxima subida.
- **FK resolvida por nome**, nunca por id literal:
  `JOIN asset_types t ON t.name = 'ACAO'`.
- **`ON CONFLICT DO NOTHING`** nos seeds de ativo: se o ticker já existe, ele
  pode ter posição atrelada, e sobrescrever nome ou categoria de um ativo em uso
  é pior que não inserir. Nos seeds de referência (tipos, categorias) o
  `DO UPDATE` é aceitável.
- **Guarda antes de DROP destrutivo**: bloco `DO $$ ... RAISE EXCEPTION` que
  confere se a cópia dos dados deu certo antes de derrubar a coluna.
- **Migration em edição com o compose no ar é aplicada na hora.** O `dev-watch`
  reinicia o Spring a cada arquivo salvo, e o Flyway aplica a migration nova
  ainda pela metade; a correção seguinte vira *checksum mismatch* (25/09/2026,
  V25). Escreva a migration com a API parada, ou, se já aplicou uma versão
  provisória **vazia**, desfaça os objetos dela, apague a linha em
  `flyway_schema_history` e reinicie — nunca `down -v`, que apaga os dados.

---

## 🔒 Autorização — como o escopo por usuário funciona

Corrigido em 15/09/2026. **Ao escrever qualquer método novo que receba
`userId`, siga isto.**

O padrão de referência é `GoalService.findOwnedGoal`:

```java
private Goal findOwnedGoal(UUID userId, UUID goalId) {
    Goal goal = goalRepository.findById(goalId)
        .orElseThrow(() -> new ResourceNotFoundException("Meta não encontrada"));
    if (goal.getUser() == null || !goal.getUser().getId().equals(userId)) {
        throw new ResourceNotFoundException("Meta não encontrada");
    }
    return goal;
}
```

Duas regras:

1. **Registro de outro dono responde "não encontrado", nunca "sem permissão".**
   "Sem permissão" confirma que o id existe, e um id que existe é exatamente o
   que um atacante procura.
2. **Quando o dono está a um join de distância, o filtro vai para a consulta**,
   não para uma comparação depois de carregar. A linha alheia não chega a sair
   do banco.

Implementações em vigor:

| Service | Consulta escopada | Caminho até o dono |
|---------|-------------------|--------------------|
| `TransactionService` | `TransactionRepository.findOwnedById` | transação → posição → carteira → usuário |
| `CeilingPriceService` | `CeilingPriceRepository.findByIdAndUserId` | campo direto |
| `DividendService` | `DividendRepository.findByAssetIdAndOwner` | provento → posição → carteira → usuário |
| `StrategyService` | `findByUserIdAndAssetTypeId` | campo direto |

> `DividendRepository.findByAssetIdOrderByExDateDesc` foi **removido**. Era a
> consulta que devolvia os proventos de todos os usuários naquele ativo. Não
> recrie: consulta sem escopo num repositório é arma carregada.

---

## 🔑 Autenticação

Plano e decisões: **`docs/planos/PLANO_CADASTRO_USUARIO.md`** (05/10/2026).

- **Login por email/senha**: `POST /api/v1/auth/login` → `{ accessToken, refreshToken, tokenType, expiresIn }`.
  E-mail normalizado (minúsculas) em todo lugar. 10 falhas em 15 min por e-mail (ou 50 por IP) → **429**.
- **Login Google**: `POST /api/v1/auth/google` com o ID token. Caminhos: `google_id` → e-mail (vincula) →
  cria a conta (só com `SIGNUP_ENABLED=true`). Nome e foto vêm do Google **só na criação** (D5); conta nova pelo
  Google nasce com e-mail verificado e o aceite dos termos pela frase sob o botão (D17).
- **Dois tokens** (claim `typ`): `access` (24 h, header `Authorization: Bearer`) e `refresh` (7 dias, só
  `POST /auth/refresh`). Até 05/10/2026 nada os distinguia e o refresh valia como acesso de 7 dias. O front
  renova sozinho no 401 (`api/client.ts`), uma vez, e só então manda para `/login`.
- **Sessão no banco** (`SessionValidator`, core): a cada requisição autenticada o filtro confere que o usuário
  existe e que o token é posterior a `password_changed_at` — trocar ou redefinir a senha derruba as outras
  sessões (D15), inclusive os refresh tokens.
- **Termos** (`LegalTerms.CURRENT_VERSION`, core; texto em `primevest-web/src/content/legal.ts`): versão aceita
  diferente da atual → a app-api responde **403** com `errors.code = TERMS_REQUIRED` a tudo menos `GET/DELETE
  /users/me`, `/users/me/terms` e `/users/me/delete-request*` (`TermsAcceptanceFilter`); o front abre o modal de
  novo aceite (`TermsGate`). Texto hoje **fictício e provisório**.
- **Cadastro com código** (`SignupService`): passo 1 guarda o cadastro pendente no **Redis** (D3; hash da senha,
  CPF já cifrado), o código de 6 dígitos vai por e-mail; o usuário só nasce no `confirm`. Atrás de
  `SIGNUP_ENABLED` (D16). Anti-enumeração: e-mail já cadastrado responde igual e recebe o aviso "você já tem
  conta" (D10). Celular e CPF repetidos: 422.
- **Códigos** (`VerificationCodeService`): HMAC (`PRIMEVEST_HASH_KEY`) no Redis, 10 min, 5 erros, reenvio após
  60 s, 5 envios/h por e-mail. Testes: armazenamento em memória (`primevest.auth.store=memory`).
- **E-mail** (`AccountMailer` / `SmtpAccountMailer`): SMTP do `.env`; sem configuração, o **Mailpit** do compose.
- Token no frontend em `localStorage`, chaves `primevest_token` e `primevest_refresh_token`.
- Usuário semeado pela **V9**: `guilhermeunatec@gmail.com` (hash bcrypt na migration; **a senha não fica
  documentada aqui**, e em produção esse arquivo não deveria existir — credencial versionada é credencial pública).

**Google**: o fluxo usa Google Identity Services diretamente (`google.accounts.id` em `useGoogleSignIn`), com ID
token e **sem client secret**. Sem `VITE_GOOGLE_CLIENT_ID` o hook fica inerte e o botão some — de propósito, um
botão que não funciona é pior que botão nenhum. Client ID criado em 05/10/2026 (projeto `primevest-510701`,
passo a passo no apêndice A do plano); origens autorizadas `http://localhost` e `http://localhost:5173`.

---

## 🔌 Endpoints da API

### Auth (públicas)
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/v1/auth/login` | E-mail e senha → tokens |
| POST | `/api/v1/auth/google` | ID token do Google → tokens |
| POST | `/api/v1/auth/refresh` | `{refreshToken}` → tokens novos |
| GET | `/api/v1/auth/signup/enabled` | `{enabled, termsVersion}` |
| POST | `/api/v1/auth/signup/start` | `{name, email, phone?, cpf?, password, acceptTerms}` → 202 `{requestId, maskedEmail, resendInSeconds, expiresInSeconds}` |
| POST | `/api/v1/auth/signup/resend` · `/signup/confirm` | `{requestId}` · `{requestId, code}` → tokens |
| POST | `/api/v1/auth/password/forgot` · `/password/resend` · `/password/reset` | Esqueci a senha: `{email}` (202 sempre) · `{requestId}` · `{requestId, code, newPassword}` → 204 |

`POST /auth/register` (cadastro sem verificação) **saiu** em 05/10/2026.

### Portfolio
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/portfolio/` | Resumo da carteira |
| GET | `/api/v1/portfolio/assets` | Posição detalhada (com `issuer` e `maturityDate` do título) |
| GET | `/api/v1/portfolio/allocation` | Alocação por tipo. `excludeReservation=true` tira a reserva de emergência (tela Alocação); sem ele, carteira inteira (Dashboard) |
| GET | `/api/v1/portfolio/allocation/detailed` | Alocação por tipo e categoria — **sempre sem** a reserva de emergência |
| GET | `/api/v1/portfolio/allocation/reservation` | Reserva de emergência × geração de renda, pelo valor atual |
| GET | `/api/v1/portfolio/performance` | Rentabilidade |
| GET | `/api/v1/portfolio/dividends` | Dividendos da carteira |
| PATCH | `/api/v1/portfolio/assets/{id}/reservation` | Liga/desliga a reserva de emergência da posição (só renda fixa) |

### Transactions
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/transactions/` | Listar (data decrescente; cada linha traz `hasLogo` e `issuerId`, como a carteira) |
| GET | `/api/v1/transactions/notes?ticker=` | Títulos de renda fixa do usuário no ativo, para o seletor da Nova Transação — **todos**, inclusive liquidados e vencidos (`settled`, `matured`); título sem posição fica de fora (07/10/2026) |
| GET | `/api/v1/transactions/{id}` | Buscar por ID |
| POST | `/api/v1/transactions/` | Criar (cria a nota se for renda fixa; recalcula proventos) |
| PUT | `/api/v1/transactions/{id}` | Editar (atualiza a nota; recalcula proventos) |
| DELETE | `/api/v1/transactions/{id}` | Excluir (recalcula proventos) |

### Strategy
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/strategy/` | Estratégia do usuário |
| PUT | `/api/v1/strategy/` | Atualizar (full replace) |
| GET | `/api/v1/strategy/rebalance` | Sugestão de rebalanceamento. `items` é a geração de renda por tipo; `reservation` é a linha da reserva de emergência |
| GET | `/api/v1/strategy/reservation` | Estratégia de reserva + números apurados (reserva, geração de renda, % atual, ideal, diff). Cria a linha padrão se faltar |
| PUT | `/api/v1/strategy/reservation` | Troca tipo e alvo. **Não há DELETE** |

### Assets
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/assets/` | Listar — params `typeId`, `query` (autocomplete, teto 50), `withIssuer=true` (com `query`: só ações e FIIs ligados a emissor — busca da tela de Emissoras) |
| GET | `/api/v1/assets/{ticker}` | Buscar por ticker |
| GET | `/api/v1/assets/types` | Tipos de ativo |
| GET | `/api/v1/assets/categories` | Categorias — param `typeId` |
| GET | `/api/v1/assets/{ticker}/prices` | Cotação histórica (28/09/2026, `AssetPriceHistoryService`/`PriceSeries`): `?range=1D\|7D\|1M\|6M\|YTD\|1A\|5A\|MAX` (padrão 1A) ou `?from=&to=`. **Ajustada** por desdobramento, grupamento e bonificação (preço até a data-com ÷ razão do evento; sem ajuste por proventos). Densidade: diário até ~1 ano, semanal até ~5, mensal acima; 1D = fechamento anterior → último (`closeOnly`; `INTRADAY` reservado para o futuro). Fracionário usa o lote padrão (`priceTicker`). Traz `firstDate`/`lastDate`, variação, mín/máx. Substitui o antigo esboço `/history`, que devolvia vazio |
| GET | `/api/v1/assets/{ticker}/indicators` | Indicadores atuais (F9): último balanço (ação) ou último informe (FII) com o preço atual. Ação: `LPA`, `VPA`, `PL`, `PVP`, `ROE`, `PAYOUT`, `DY`; FII: `PVP`, `DY`, `VP_COTA`, `COTISTAS`, `IMOVEIS`, `PAPEL`, `ALAVANCAGEM`. Cada um com `unit` (`PERCENT` em pontos, `RATIO`, `BRL`, `COUNT`), `value` e, quando nulo, `note` com o motivo. Ativo sem emissor (ETF, BDR…): 404 |
| GET | `/api/v1/assets/{ticker}/indicators/history` | Todos os históricos do ticker numa chamada (`List<IndicatorHistoryDTO>`, ordem do enum; lê a série uma vez) — sparklines da tela de Emissoras |
| GET | `/api/v1/assets/{ticker}/indicators/{indicador}/history` | `IndicatorHistoryDTO`: `current` (Atual), `companyAverage` (média dos anos + Atual; nulos fora, zeros dentro) e `points`; `?period=YEAR` (padrão) ou `QUARTER`. Indicador que não se aplica ao tipo: 400 |

### Issuers (F9)
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/issuers/{id}` | Cadastro do emissor (com `registrationStatus` e `website`) e tickers ligados com `hasLogo` (o `issuerId` vem em `/assets/{ticker}/indicators`) |
| GET | `/api/v1/issuers/{id}/quarters?from=` | Série trimestral da companhia (`company_quarters`); FII: 400 |
| GET | `/api/v1/issuers/{id}/months?from=` | Informe mensal do FII (`fii_monthly`); companhia: 400 |

### Dividends
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/dividends/` | Resumo de proventos: `totalDividends`, `byType`, `totalGross` e `totalTax` do **recebido** (líquidos); `totalToReceive`, `toReceiveGross`, `toReceiveTax` do **a receber**; cada provento com bruto, alíquota, IR, líquido e `received` |
| GET | `/api/v1/dividends/asset/{ticker}` | Proventos de um ativo (escopado por usuário) |

### Preço Teto (10/10/2026, `docs/planos/PLANO_PRECO_TETO.md`)
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/ceiling-prices` | Cálculos do usuário: teto, ideal e as margens contra o preço do cálculo, o fechamento de hoje e o preço médio da carteira; situação (`ABAIXO_DO_IDEAL`, `ENTRE_IDEAL_E_TETO` ou `ACIMA_DO_TETO` — sempre presente: só cálculo sem bloqueio é gravado), alertas, "padrão mudou" |
| GET | `/api/v1/ceiling-prices/defaults/{ticker}` | Padrões de hoje (4 lucros da CVM, N, ROE/payout médios de 3 exercícios, Selic meta, 3%, 15%, último fechamento) com a referência de cada um; `existingId` se já há cálculo. Só ação ligada a companhia (422) |
| POST | `/api/v1/ceiling-prices/calculate` | Calcula sem gravar: resultado, memória (`steps`) e alertas (`issues`: BLOQUEIO, ATENCAO, INFORMACAO) |
| GET | `/api/v1/ceiling-prices/{id}` | Entradas gravadas, origem de cada uma, resultado, padrões de hoje e `changedDefaults` |
| POST / PUT | `/api/v1/ceiling-prices[/{id}]` | Grava: entradas obrigatórias; bloqueio ou alerta sem `confirmWarnings` → 422 com os avisos; já existente → 409 |
| DELETE | `/api/v1/ceiling-prices/{id}` | Exclui |

### Goals
| Método | Endpoint |
|--------|----------|
| GET / POST | `/api/v1/goals/` |
| PUT / DELETE | `/api/v1/goals/{id}` |

### Índices de mercado (05/10/2026)
`docs/planos/PLANO_INDICES_MERCADO.md` (X5). Autenticado; só leitura do catálogo (`MarketIndexQueryService`).

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/indices` | Os 10 índices: `code`, `name`, `periodicity`, `unit`, `source`, `accumulates` (falso para TR e meta Selic), `firstDate`, `lastDate`, `lastValue` |
| GET | `/api/v1/indices/{code}/history?from=&to=` | Série no período (padrão: um ano até hoje), como a fonte publica |
| GET | `/api/v1/indices/{code}/chart?range=1A` ou `?from=&to=` | Card da tela Índices (`IndexChart`): janela do atalho (mensal em meses inteiros: 1A = 12 meses, 1M = o último mês, 1D e 7D recusados com 422; CDI/Selic no 1D = o último dia), pontos no valor exibido (`displayUnit`; CDI e Selic anualizados em 252 dias úteis), densidade da cotação (diário até ~1 ano, semanal até ~5, mensal acima), `last`/`min`/`max`, `accumulatedPct` (taxa) ou `change`/`changePct` (nível), `firstDate`/`lastDate` da série inteira. Mensais: `view=MENSAL` (padrão) ou `ANUAL` — cada mês com o acumulado dos 12 meses terminados nele (`IndexChart.rolling12`). Atalho inválido, de depois de até ou `view` em índice que não é mensal: 422 |
| GET | `/api/v1/indices/{code}/accumulated?from=&to=&percent=&spread=` | Acumulado: taxa diária soma os dias entre `from` e `to`; taxa mensal os meses inteiros do mês de `from` ao de `to`; nível (Ibovespa, IFIX, dólar) = último até `to` ÷ último até `from`. `percent` = % do índice (110 = 110% do CDI), `spread` = % a.a. (`(1+s)^(1/252)` por dia útil, `^(1/12)` por mês). Devolve `factor` (10 casas) e `percent` (6). Índice desconhecido 404; TR, meta Selic, período sem valores ou `percent`/`spread` em índice de nível: 422 |

Conferido com a série real em 05/10/2026: CDI 2024 = **10,875983%** (253 dias), IPCA 2024 = **4,831296%** (o IBGE
divulgou 4,83%), Ibovespa 2024 = −9,35%.

### Users (o próprio usuário)
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/users/me` | Perfil: `avatarUrl` (Google), `avatarVersion` (foto enviada), `phone`, `cpfMasked` (`***.456.789-**`), `emailVerified`, `hasPassword`, `googleLinked`, `admin`, `theme`, termos (`termsVersion`, `termsAcceptedAt`, `currentTermsVersion`, `termsPending`) |
| PUT | `/api/v1/users/me` | `{name, phone, cpf}` — `cpf` nulo não mexe, vazio tira; com conexão viva com a B3 o CPF não muda (D20) |
| PUT | `/api/v1/users/me/preferences` | `{theme: DARK\|LIGHT}` |
| PUT | `/api/v1/users/me/password` | `{currentPassword?, newPassword}` → tokens novos (derruba as outras sessões) |
| POST | `/api/v1/users/me/email` · `/email/resend` · `/email/confirm` | Troca de e-mail com código no endereço novo |
| POST · DELETE | `/api/v1/users/me/google` | Vincular (ID token) · desvincular (exige senha) |
| GET · PUT · DELETE | `/api/v1/users/me/avatar` | Foto de perfil: `PUT` multipart (`file`, JPEG/PNG, ≤ 2 MB, ≥ 128 px; a API recorta o quadrado central e regrava JPEG ≤ 512×512); `GET` devolve o JPEG (a tela busca com o token e mostra como blob; `avatarVersion` no perfil muda a cada envio); `DELETE` volta para a do Google ou as iniciais |
| POST | `/api/v1/users/me/terms` | Aceita a versão atual dos termos |
| POST · DELETE | `/api/v1/users/me/delete-request` (`/resend`) · `/api/v1/users/me` | Exclusão definitiva com código (`DELETE` com `{requestId, code}`) |

---

### Erros — o que a API responde

| Situação | Status |
|----------|--------|
| Sem token, ou token vencido | **401** (era 403 até 22/09/2026; o front só trata 401) |
| Registro de outro dono, ou id inexistente | **404** |
| Corpo inválido (Bean Validation), JSON malformado, parâmetro errado | **400** |
| Regra de negócio recusada (`BusinessException`) | **422** |
| Rota inexistente | **404** · método não suportado: **405** |
| Violação de constraint que escapou do serviço | **409** |

Mensagens em português, acentuadas — conferidas nos testes de controller.

## 📊 Telas

Rotas em `App.tsx`; tudo abaixo de `/login` é protegido por `ProtectedRoute`.

| Rota | Tela |
|------|------|
| `/dashboard` | Pizza de alocação por tipo, cards de resumo |
| `/portfolio` | Carteira — posição por ativo, com EMISSOR e VENCIMENTO |
| `/allocation` | Abas "Carteira de Investimentos" e "Por Categoria" |
| `/strategy` | Estratégia de Investimentos: abas "Ideal Estratégico" e "Estratégia Reserva"; link "Ir para Rebalanceamento" no canto superior direito, na linha das abas |
| `/rebalance` | Rebalanceamento (11/10/2026, antes aba da Estratégia): reserva e sugestão de aporte por tipo; link "Ir para Estratégia de Investimentos" no canto superior direito |
| `/transactions` | CRUD completo |
| `/ceiling-prices`, `/ceiling-prices/:ticker` | Preço Teto (10/10/2026, `docs/planos/PLANO_PRECO_TETO.md`; metodologia em `docs/PRECO_TETO_METODOLOGIA.md`). Lista (`CeilingPricesPage`): teto, ideal e três comparações (preço do cálculo, fechamento de hoje, preço médio da carteira) com margem do teto e do ideal, situação, alertas, ↻ "padrão mudou"; "Novo cálculo" pela busca de ação. Página do ativo (`CeilingPriceDetailPage`, revista pelo Guilherme em 11/10/2026): premissas primeiro, com o padrão de hoje no (i) de cada uma, ↻ por campo e "Restaurar todos os padrões" (só valem ao salvar), g calculado e G só leitura por último; 4 lucros (valor ± com sinal, unidade, BRL/USD, câmbio; sem fonte e sem a referência do documento) e o LL TTM em valor cheio; resultado ao vivo (`/calculate`), alertas, datas, observações (texto livre do usuário, `ceiling_prices.notes`); o cartão de memória de cálculo saiu da tela (a API segue devolvendo `result.steps`); legenda Padrão / Informado (dourado) / Calculado; salvar com alertas pede confirmação; sair com alteração pede confirmação |
| `/emissoras`, `/emissoras/:ticker` | Emissoras (28/09/2026, `docs/planos/PLANO_TELA_EMISSORES.md`): busca, cabeçalho com os outros tickers do emissor, cotação (atalhos + de/até, período na URL `?periodo=` / `?de=&ate=`), cartões de indicadores com sparkline e histórico em modal (Anual/Trimestral, média, ‹ ›), cadastro e demonstrativos. O ticker é link para cá em Carteira, Dividendos e Preço Teto quando o ativo tem emissor (`issuerId` nos DTOs dessas telas — não confundir com `issuer` da carteira, que é o emissor da nota de renda fixa) |
| `/indices` | Índices (05/10/2026, `docs/planos/PLANO_INDICES_MERCADO.md`, seção 10): um card por índice, um abaixo do outro, na ordem SELIC, CDI, IPCA, IBOV, IFIX, IGPM, INPC, TR, SELIC_META, USD_PTAX (`IndicesPage`), cada um com o desenho da cotação das Emissoras (`IndexChartCard`: atalhos 1D…MAX — nos mensais IPCA, IGP-M e INPC sem 1D e 7D e com o seletor Mensal (% no mês, barras) / Anual (acumulado em 12 meses, % a.a.) —, 1A padrão, de/até; o período fica no card, não na URL). CDI e Selic aparecem anualizados (% a.a.); mensais em barras; meta Selic em degraus. Resumo: acumulado no período (taxa), variação (Ibovespa, IFIX, dólar), atual/último, mín e máx. Lê `GET /api/v1/indices/{code}/chart` |
| `/dividends` | Dividendos (11/10/2026): abas Histórico de Proventos (recebidos) e Proventos a Receber — ver "Proventos recebidos × a receber" |
| `/goals` | Metas |
| `/integrations` | Integrações (Binance e, com `B3I_ENABLED`, B3): conectar (chave ou CPF + autorização na B3), sincronizar, histórico, excluídas |
| `/integrations/:id/runs/:runId` | Pré-visualização / resultado de uma sincronização (decidir duplicatas, confirmar) |
| `/settings` | Configurações (05/10/2026): Perfil (foto centralizada com envio e recorte no navegador — `AvatarCropModal`, JPEG/PNG/WebP até 5 MB, ≥ 128 px —, nome, e-mail com troca por código, celular, CPF mascarado), Acesso (alterar/definir senha, vincular/desvincular Google, termos aceitos), Preferências (tema, gravado no usuário), Zona de perigo (excluir conta com código) |
| `/login` | Login (email/senha + Google), links "Esqueci a senha" e "Criar conta" (só com o cadastro ligado) |
| `/cadastro` | Criar conta (pública): dados (celular e CPF opcionais) + aceite dos termos → código por e-mail → já entra. Ou Google |
| `/esqueci-senha` | Esqueci a senha (pública): e-mail → código + nova senha |
| `/termos`, `/privacidade` | Termos de Uso e Política de Privacidade (públicas; texto provisório em `src/content/legal.ts`) |

### Alocação
Aba **Carteira de Investimentos** (gráfico "pizza de pizza" desde 12/10/2026, `components/charts/PieOfPieChart`): um
card com duas roscas num só gráfico — à esquerda "Alocação da Reserva de Emergência" (reserva `#8a00c4` × geração de
renda `#7d7e7f`), girada para a fatia da Geração de Renda ficar virada para a direita; à direita, com 75% do tamanho,
"Alocação da Carteira (Por Tipo de Ativo)", que é essa fatia aberta por tipo. Duas linhas ligam a fatia à segunda
rosca (das bordas da fatia, ou do topo e da base da rosca quando a fatia passa de meia volta). Total no centro e
legenda vertical embaixo de cada rosca — na por tipo, com o **ideal da estratégia** de cada tipo à frente (`GET /strategy`,
chave `['strategy']`, casado pelo nome do tipo; tipo sem ideal fica sem) e, na da reserva, o ideal da Reserva de
Emergência (`GET /strategy/reservation`, `idealPercentage`: o alvo no Por Porcentagem; no Por Valor, alvo ÷ carteira,
limitado a 100%); o balão (`AllocationTooltip`) vale nas duas. Geometria em pixels a partir da largura
medida (`ResizeObserver`); a camada do gráfico é absoluta para não travar a largura mínima do layout. Abaixo de 760 px
de largura as duas viram `AllocationPieChart` empilhadas, sem as linhas. Ambas na **mesma base de valor** (valor
atual: custo + valorização − resgates, só posição em aberto). O Dashboard continua com a rosca simples.

**A tela Alocação não conta a reserva** nos gráficos por tipo nem nos de **Por
Categoria** — mesmo critério do rebalanceamento: a reserva é um bloco próprio,
com o gráfico dela ao lado. Por isso o total do gráfico por tipo é a geração de
renda, e bate com a fatia cinza do gráfico da reserva. **O Dashboard continua
mostrando a carteira inteira**, reserva incluída: ele chama o mesmo
`/allocation` sem `excludeReservation`, com outra chave de cache
(`['portfolio','allocation']` contra `['portfolio','allocation','income']`).

> `queryFn: portfolioApi.getAllocation` direto quebraria o Dashboard: o React
> Query passa o próprio contexto como primeiro argumento, e ele chegaria como
> `excludeReservation` truthy. Por isso as duas telas chamam por arrow.

Não confundir com o donut da aba **Estratégia Reserva**, que mostra a
distribuição **ideal**; o % Atual da estratégia usa o mesmo valor atual desta tela (desde 12/10/2026). A
query fica sob `['portfolio', ...]`, então ligar a reserva de um ativo na
Carteira já atualiza o gráfico.

### Carteira
A tabela repete ticker, porque a posição é por título. `key` da linha é
`asset.id` (o id da posição), **não o ticker** — ticker deixou de ser único aqui.
As colunas EMISSOR e VENCIMENTO são o que distingue duas linhas "CDB".

**APLICAÇÃO** (23/09/2026): só na aba Tesouro Direto, à esquerda de
VENCIMENTO — a `purchase_date` da nota, que chega em
`PortfolioAssetDetailDTO.purchaseDate`. No Tesouro cada compra é um título
novo, então é a data que separa duas linhas do mesmo papel, e é também a ordem
em que a venda consome os títulos (FIFO).

**Reserva de emergência** (19/09/2026): primeira coluna, um switch que grava
`portfolio_assets.is_reservation` direto, sem modal. Aparece só onde
`asset_types.fixed_income` é verdadeiro — Renda Fixa e Tesouro Direto hoje, e
qualquer tipo novo marcado assim amanhã; o backend recusa o resto com 422, de
modo que esconder a coluna é conveniência, não a regra. A requisição manda o
ESTADO desejado, não um "inverta", e a chave da linha fica desabilitada
enquanto a resposta não volta: dois cliques rápidos não dependem da ordem de
chegada. O sucesso invalida `['portfolio']` e `['strategy']`: a reserva é um
bloco próprio da estratégia, e marcar um CDB muda a aba Estratégia Reserva e as
duas tabelas do rebalanceamento.

A coluna fica **fora de `COLUMNS`**: um render de coluna recebe só a posição, e
a chave precisa avisar quem está de fora que o estado mudou. Por isso o
`colSpan` de "Totais" no rodapé soma 1 quando ela aparece.

### Transações
- Modal em duas colunas: operação à esquerda, quadro do título à direita, com
  alturas independentes. Sem isso o quadro esticava o modal inteiro.
- O **tipo de ativo comanda o formulário**: restringe o autocomplete e decide se
  o quadro do título aparece.
- Autocomplete a partir de 2 caracteres, debounce 300ms, teto de 50 no servidor.
  O corte é no `@Query` com `ORDER BY` de relevância — cortar no cliente
  descartava linhas arbitrárias de um resultado sem ordem.
- **Não é possível criar ativo pela transação.** Ticker fora do catálogo é erro.
- **Regra K** (23/09/2026): excluída a última transação de uma posição, a
  posição é apagada, qualquer que seja o tipo de ativo, com o título de renda
  fixa e os proventos dela (`TransactionService.removeIfEmpty`). Antes só o
  Tesouro fazia isso, e só sem provento; nos demais tipos a posição vazia
  ficava, e a valorização de uma venda excluída continuava contando como
  patrimônio.
- Máscaras, todas sobre o mesmo `MaskedNumberInput` (dígitos lidos da direita
  para a esquerda, sem separador digitado): `CurrencyInput` (R$, 2 casas) em
  Valor, Preço Unitário e Taxas; **Quantidade com 2 casas**; e `PercentInput`
  (%) com **3 casas** no campo de taxa do título — `indexer_rate` é
  `NUMERIC(10,4)` e taxas de CDB e Tesouro vêm com fração de décimo
  ("IPCA + 6,125%"). Quantidade usava `<input type="number">` até 18/09/2026;
  a troca eliminou o caso de "1.5" digitado por quem espera vírgula virar
  quinze.
- Na edição, ticker e tipo de ativo são readonly, mas **o tipo é preenchido** a
  partir do `assetTypeId` que a transação carrega, e o quadro do título reaparece
  com os dados da nota.
- **Seletor "Título" na Renda Fixa** (07/10/2026), na compra e na venda pela Nova Transação (o Tesouro fica de
  fora: cada compra é um título novo e a venda é FIFO). Lista **todos** os títulos do ativo
  (`GET /transactions/notes`), marcando "vencido" e "liquidado" — lançamento retroativo existe. Sem saldo na lista; o
  "Resgatar tudo" da venda continua mostrando. Na **venda** a escolha é obrigatória e o quadro é somente leitura;
  na **compra** a opção padrão é "Novo Título" (quadro editável, preenchido com o catálogo) e escolher um título
  lança o aporte nele (quadro somente leitura). Voltar para "Novo Título" restaura o quadro pelo catálogo, não vazio.
- **Aporte só até a véspera do vencimento**: compra num título existente com data igual ou posterior ao vencimento
  dá "Título vencido para a data de transação escolhida" — na tela (o modal fica aberto) e na API
  (`validateBuyBeforeMaturity`, na criação com `noteId` e na edição de compra). Mesma regra do selo "Vencido".
- Trocar o tipo de ativo **mantém a operação** (antes Renda Fixa forçava Compra). O spinner do autocomplete
  desliga quando o texto cai abaixo de 2 caracteres — apagar rápido deixava o loading eterno.
- **Busca por ticker ou nome** (11/10/2026), primeira da barra de filtros (`LocalAssetSearch`): sobre todos os
  ativos do extrato, de qualquer tipo. Escolher um ativo é o Listar: ativo com uma posição só filtra como o Listar
  daquela linha (Tesouro pelo ticker, os demais pela posição); ativo com mais de uma posição (vários títulos do mesmo
  CDB, ou várias compras do mesmo Tesouro) filtra pelo ticker e mostra todos. Mesmo aviso e mesmo "Limpar filtro".
- **Filtro depois de salvar** (Nova Transação e edição) e **ícone Listar** em cada linha: o extrato mostra só
  aquela posição, com o mesmo aviso do Listar da carteira e os outros filtros de volta a "Todos". Tesouro Direto
  filtra pelo ticker (`?ticker=`, cada compra é um título); os demais pela posição (`?position=`, que na Renda Fixa
  é o título). A carteira continua só com `?position=`.
- **Ícones Comprar e Vender** em cada linha (11/10/2026), os mesmos da carteira: abrem o modal com operação, tipo e
  ativo travados. O preset sai de `presetFromPosition` (`components/transactions/transactionPreset.ts`), a mesma
  função da Carteira, a partir da posição aberta (`portfolioAssetId` casado com `GET /portfolio/assets`) — na Renda
  Fixa vai o título, com o saldo na curva do "Resgatar tudo"; no Tesouro não (compra = título novo, venda FIFO). Se a
  posição não está mais na carteira (encerrada), `presetFromTransaction` monta o preset da própria linha, sem saldo;
  a venda sem o que vender a API recusa. Depois de salvar, o extrato filtra o lançamento como na Nova Transação.
- Colunas: **ATIVO** primeiro (logo e link para a emissora, como na carteira), **TÍTULO** ao lado
  ("Emissor · venc. dd/mm/aaaa · 110,00% do CDI", com o selo "título 1/2" da venda repartida), depois DATA. A
  ordem padrão continua a da API (data decrescente).
- **Logo por tipo** (`typeLogo`, em `utils/assetTypeRules`; vale também na carteira): Tesouro Direto usa o símbolo
  do programa (`primevest-web/src/assets/logos/tesouro-direto.svg`, recortado do logo horizontal) e Renda Fixa o
  texto "RF" no quadro de quem não tem logo — o logo pelo emissor do título terá planejamento próprio.

### Estratégia
- **Ideal Estratégico**: percentuais editáveis + pizza. Adicionar/remover age em
  memória; Salvar persiste tudo (full replace). ↻ restaura do banco.
- **Estratégia Reserva** (20/09/2026): uma linha — Tipo de Estratégia (select
  Por Porcentagem / Por Valor), % Atual, % Ideal, % Diff. O input troca com o
  tipo: `PercentInput` ("%" à direita) ou `CurrencyInput` ("R$" à esquerda).
  % Ideal e % Diff reagem ao que está sendo digitado; % Atual vem do servidor.
  Donut "Distribuição Estratégica Reserva" com a distribuição **ideal**:
  reserva `#8a00c4`, geração de renda `#7d7e7f`, em % no tipo percentual e em
  R$ no tipo valor. Componente em `components/strategy/ReservationStrategyTab`.
- **Link para o Rebalanceamento** (11/10/2026): no canto superior direito do conteúdo, na linha das abas,
  "Ir para Rebalanceamento" (`components/ui/PageLink`); a tela de Rebalanceamento tem o inverso, "Ir para Estratégia
  de Investimentos", no mesmo canto. O cabeçalho (`Header.tsx`) continua só com o título. A aba Ideal Estratégico
  continua lendo `GET /strategy/rebalance` para o % Atual de cada tipo.

### Rebalanceamento
Tela própria em `/rebalance` (`RebalancePage`, 11/10/2026; até então era a terceira aba da Estratégia), com item no
menu logo abaixo de Estratégia. Mesma consulta `['strategy','rebalance']`: tudo o que invalida `['strategy']`
(Ideal Estratégico, Estratégia Reserva, switch da reserva na Carteira) atualiza a tela.
- Primeiro o card "Rebalanceamento Reserva de Emergência"
  (Tipo de Estratégia, % Ideal — "—" no tipo valor —, Aportado, Aportar,
  Estratégico, % Carteira, % Diff, Sugestão), depois "Sugestão de Aporte" por
  tipo: Tipo Ativo, % Ideal, Aportado, Aportar, Estratégico, % Carteira,
  % Diff, Sugestão (APORTAR/AGUARDAR).
- **Base = valor atual** (12/10/2026): Aportado, % Carteira e o total são o valor atual de cada posição — valor
  aplicado + valorização − resgatado, o mesmo da Alocação (`StrategyHoldings.held`). Até então era o custo, e um tipo
  que valorizou aparecia abaixo do ideal e recebia sugestão de aporte; o percentual estratégico nunca batia com a
  carteira de verdade. Vale para os itens por tipo, para a linha da reserva e para o % Atual da Estratégia Reserva e
  do Ideal Estratégico. A valorização é a gravada (cotação, PU do Tesouro, curva da renda fixa privada).

### Reserva de emergência — como é calculada
A reserva **concorre com o resto da carteira**: somam-se as posições com
`is_reservation = true`, e o restante é a **geração de renda**. As duas contas
leem de `StrategyHoldings`, que carrega posições e transações uma vez e aplica a
mesma regra de valor do rebalanceamento (valor atual: aplicado + valorização − resgatado; posição liquidada
vale zero; desde 12/10/2026, antes era o custo). Por isso a reserva nunca entra no rebalanceamento por tipo, em
nenhum dos dois ramos dele: contada lá também, o mesmo CDB apareceria duas vezes.

**Aportar**, em `StrategyReservationService.amountToContribute`:

| Tipo | Fórmula | Por quê |
|------|---------|---------|
| Por percentual | `(p × total − reserva) ÷ (1 − p)`, `p` em fração | O aporte entra no total da carteira: depois dele a reserva bate o % **exato**. Completar só `p × total` deixaria a reserva abaixo do alvo |
| Por percentual, `p = 100%` | `total − reserva` | A fórmula divide por zero; enquanto houver geração de renda nenhum aporte finito chega a 100% |
| Por valor | `alvo − reserva` | O alvo é absoluto; o total não entra |

Negativo quando a reserva está acima do alvo: aparece em vermelho e a Sugestão
fica AGUARDAR — a tela mostra o excesso, não manda vender. No tipo valor, o
**% ideal** usado pelo % Diff é `alvo ÷ total` da carteira de hoje, limitado a
100%.

Exemplo conferido de ponta a ponta: carteira R$ 10.000, reserva R$ 2.000 (20%).
Ideal 30% → aportar **R$ 1.428,57** (3.428,57 / 11.428,57 = 30%); ideal R$ 3.000
→ aportar R$ 1.000; ideal 10% → aportar **−R$ 1.111,11**.

**Nascimento**: `AuthService.createDefaults` cria carteira e estratégia de
reserva nos dois caminhos que criam usuário (e-mail e Google). O usuário semeado
recebe a sua pela V12. Usuário anterior à feature ganha a linha padrão na
primeira leitura de `GET /strategy/reservation` — a leitura do rebalanceamento,
que é somente leitura, usa o padrão em memória sem gravar.

**`type_reservation` é SMALLINT**, e não VARCHAR como os outros enums do
projeto: 1 e 2 vieram do escopo. `ReservationType` + `ReservationTypeConverter`
fazem a ponte, e na API o enum trafega pelo nome (`PERCENTAGE` / `VALUE`).

---

## 🔄 Fluxo de Transações → Portfólio

**A posição não é gravada: é calculada das transações na leitura** (V28).
`portfolio_assets` guarda só a identidade da posição (carteira, ativo, nota) e o
que não vem de transação (`appreciation`, `is_reservation`, `is_settled`).
Não existe mais recálculo de posição nem `recalculate-all`.

`PositionCalculator` é a regra única. Percorre as transações da posição em
ordem cronológica (data, depois criação):

1. **BUY**: soma cotas e custo (cotas × preço)
2. **SELL**: tira cotas e tira custo **pelo preço médio do momento**; o preço
   médio não muda. **Venda sem saldo na data é recusada** pela regra B, em
   qualquer data da linha do tempo (`PositionGuard.validateShareLedger`); o
   cálculo ainda tolera o caso porque roda sobre dados anteriores a essa regra
3. **Preço médio** = custo / cotas (4 casas); **custo** com 2 casas
4. **Renda Fixa** (`isValuedByTotal`): sem cotas nem preço médio; custo =
   aplicações − resgates, nunca negativo

Não é `SUM`/`AVG`: compra 100 a R$ 10, compra 100 a R$ 20, venda 100 a R$ 30 →
preço médio R$ 15 e custo R$ 1.500 (a média simples daria R$ 20).

`positionsOf(portfolioId, posições)` faz **uma consulta** para todas as
transações da carteira. Usado por `PortfolioService` e `StrategyService`.

**Valorização é gravada** em `portfolio_assets.appreciation`, e a tela só lê.
Valor atual = custo + `appreciation`.

- **Com cotação** (Ações, FIIs, ETFs, BDRs, Cripto): `AppreciationUpdater` grava
  cotas × cotação − custo (2 casas) quando um dos lados muda — ao fim de cada
  rodada do fechamento (`ClosingPriceService`), em `AssetService.updateAssetPrice` e ao
  criar, editar ou excluir transação. Na subida da API (`ApplicationReadyEvent`)
  todas as posições com cotação são regravadas.
- **Sem cotação** (Renda Fixa, Tesouro, tipos MANUAL): nada derivado de
  transação sobrescreve; o valor vem de fora (curva, quando existir).

**O que é regravado ao mudar transação** (`TransactionService.afterPositionChange`):
`dividends.total_amount`, que depende das cotas na data-com
(`DividendRecalculator.recalculate`, contando cotas com o mesmo
`PositionCalculator.sharesOn`), e `portfolio_assets.appreciation` das posições
com cotação.

### Tesouro Direto: um título por compra, venda por FIFO

Cada compra de Tesouro é um **título novo** (a taxa é do dia), e a regra H
proíbe dois títulos do mesmo ativo comprados na mesma data. A venda é **do
ativo**: `TreasuryAllocator` reparte as cotas do título mais antigo para o mais
novo e grava uma linha por título, todas com o mesmo `sale_group_id` — a tela
mostra a venda do usuário somando as partes (`groupShares`).

A repartição é **refeita por inteiro a cada escrita no ativo**: uma compra
lançada com data retroativa passa a ser o título mais antigo, leva as cotas que
saíam de outro e reabre título que estava liquidado. É o Exemplo 6 do escopo, e
está em `TransacoesIT.exemplo6`.

### Renda fixa: como a nota entra

`TransactionService.resolveNote` decide, em três caminhos:

| Situação | O que acontece |
|----------|----------------|
| Ativo não é renda fixa | nota nula; `noteId` no DTO é erro |
| DTO traz `noteId` | carrega **escopado pelo dono** e confere que é do mesmo ativo; numa compra, recusa data no vencimento ou depois (`validateBuyBeforeMaturity`) |
| Compra sem `noteId` | cria nota nova, herdando de `asset_terms` o que o DTO não trouxer |
| **Venda sem `noteId`** | **recusa** — ver Pontos de Atenção |

`applyNoteFields` só roda no update quando a requisição traz campos de título
(`hasNoteData`). Sem essa guarda, corrigir a quantidade de um CDB pela tela de
edição apagaria emissor, vencimento e taxa.

---

## 📐 Padrão Visual

- **Tema**: dark por padrão, claro disponível (`darkMode: 'class'`, `ThemeContext`)
  O claro é feito por **override em `index.css`** (`html.light .text-white`
  etc.), classe por classe — e o Tailwind gera uma classe por opacidade. Cor
  com opacidade sem override no claro some: `text-white/70` ficava branco
  sobre branco e apagava EMISSOR, APLICAÇÃO, VENCIMENTO e REMUNERAÇÃO na
  Carteira até 23/09/2026. Usou `text-white/NN`, `bg-surface-light/NN`,
  `hover:text-white` ou similar numa tela nova? Confira se o override existe.
- **Menu lateral** (23/09/2026, `Sidebar.tsx`): ordem Dashboard, Carteira,
  Transações, Alocação, Estratégia, Rebalanceamento (11/10/2026, ícone de balança), Dividendos, Preço Teto, Emissoras, Índices (05/10/2026, ícone de
  gráfico de linha), Metas, Integrações
  (26/09/2026, com marcador quando uma conexão pede atenção). Recolhível
  pelo botão redondo sobre a borda direita, na altura do logo: 256px ↔ 72px.
  Recolhido mostra só "PV", os ícones (nome no `title`/`aria-label`) e o rodapé
  empilhado. A escolha fica em `localStorage` (`primevest-sidebar-collapsed`),
  como o tema.
- **CORS** (`CorsConfig`): `localhost` e `127.0.0.1`, portas 5173 e 3000. Para
  o navegador são origens diferentes; até 23/09/2026 só `localhost` valia, e
  abrir a tela por `127.0.0.1` fazia o login falhar com 403 — exibido como
  "Email ou senha inválidos".
- **Erros de entrada** (`utils/loginError.ts`, login e Google): o status decide
  a frase — 401 credencial recusada; 400 email inválido; 403 origem recusada;
  sem resposta, 502/503/504 ou **500 sem corpo** (é o proxy do Vite com a API
  parada) = servidor fora do ar; 500 com mensagem = erro interno; 422 = texto
  da regra. O interceptor do `api/client.ts` não trata 401 de `/auth/*` como
  sessão vencida: antes, senha errada recarregava `/login` e apagava a mensagem.
- **Background**: `#000000` (claro: `#f5f5f0`) · **Surfaces**: `#111111` (claro: `#ffffff`)
- **Accent/Gold**: `#c9a84c` · **Success**: `#38a169` · **Danger**: `#e53e3e`
- **Fonte**: Inter · **Idioma da UI**: Português (Brasil)
- **Avisos**: `ToastContext` (`useToast`) — canto superior direito, empilha,
  some em 4s, variantes `success` / `error` / `info`. Criado em 19/09/2026 com
  o switch da reserva: uma ação que grava sem modal não tem onde pendurar o
  "deu certo", e até então só existia erro dentro do formulário que o causou.
  O provider fica no `main.tsx`, entre `ThemeProvider` e `AuthProvider`.
  Desde 23/09/2026 **toda gravação avisa**: compra, venda, edição e exclusão de
  transação (Carteira e Transações), Ideal Estratégico, Estratégia Reserva e o
  ↻ Restaurar (informativo). Erro dura **8s** (o resto, 4s): as mensagens de
  regra do backend são frases inteiras. O formulário de transação **não tem
  mais aviso de erro dentro do modal** — o erro sai no toast e o modal fica
  aberto para corrigir. Venda de Tesouro que o sistema repartiu entre títulos
  (`groupParts > 1` na resposta) ganha um toast informativo além do sucesso.
  `window.alert` saiu das telas; a leitura da mensagem da API é uma só, em
  `utils/apiError.ts` (`apiMessage`).
- **Ícones de ação nas tabelas** (coluna AÇÕES de Carteira, Transações, Preço Teto e Estratégia; 11/10/2026): todos
  no amarelo do tema (`accent`, #c9a84c), em repouso e no hover — o hover só acende um fundo. A classe é uma só,
  `TABLE_ACTION_CLASS` (`components/ui/tableAction.ts`); ação nova em tabela usa ela. Os indicadores de status que
  não são botão (alerta e "padrões mais novos" no Preço Teto) mantêm a cor própria.
- **Busca por ticker ou nome nas listagens** (`components/ui/LocalAssetSearch.tsx`, 11/10/2026; Dividendos e
  Transações): o visual e o teclado da busca de Emissoras (`IssuerSearch`), mas as opções vêm da própria listagem, no
  navegador — um ativo fora dela deixaria a tabela vazia. Sem acento e sem caixa ("Itaú" acha "ITAU"), a partir de 2
  caracteres, ticker que começa com o texto primeiro; Enter sem destaque escolhe o ticker exato ou a primeira opção.
  Escolher tem o efeito do ícone Listar da tela.
- **Ordem das roscas** (`components/charts/donut.ts`, 12/10/2026 — regra para todo gráfico de rosca): fatias e
  legenda do maior valor para o menor (`byValueDesc`); a primeira fatia começa no topo (12 h) e as demais seguem no
  sentido horário até a menor fechar no topo (`DONUT_START_ANGLE` 90 → `DONUT_END_ANGLE` −270). Aplicada dentro de
  `AllocationPieChart`, `CornerLegendDonut` e na rosca de detalhe do `PieOfPieChart`, e na Distribuição Estratégica e
  na Estratégia Reserva. **Exceção**: a rosca principal do pizza de pizza (Reserva × Geração de Renda) mantém o giro
  com a fatia aberta virada para a rosca de detalhe; só a legenda dela segue a ordem decrescente.
- **Legendas dos gráficos de pizza/rosca** (`components/charts/ChartLegend.tsx`, 12/10/2026; Alocação, Dashboard,
  Ideal Estratégico e Estratégia Reserva): um item abaixo do outro, em colunas alinhadas (cor, nome, valor e, quando
  houver, "ideal X%"), fonte de 13 px, **fora** do gráfico, embaixo dele — a `Legend` do Recharts saiu de todas. Fora do
  gráfico ela não toma altura da rosca, e o total fica no centro exato (o antigo `pb-8` compensava a legenda de
  dentro). Por isso o card da Alocação do Dashboard deixou de ter altura fixa: a rosca tem altura mínima (240 px) e a legenda
  cresce com os tipos. No `AllocationPieChart` o `ResponsiveContainer` fica numa camada absoluta: com `height: 100%`
  de um pai sem altura definida ele mede zero e a rosca some (aconteceu no Dashboard em 12/10/2026).
  Posição: embaixo (padrão; Alocação e Estratégia) ou **no canto inferior direito** (`CornerLegendDonut`; Dashboard,
  com `legend="corner"` no `AllocationPieChart`, e as roscas da aba Por Categoria, onde nome e % ficam lado a lado, e
  não mais nas pontas do card). No canto, a rosca é centralizada (320 px de altura) e a legenda sobe para o lado dela
  só até onde não encosta — calculado pela largura da área, pelo raio e pelo tamanho da legenda; em área estreita ela
  fica inteira embaixo. Por Categoria: nome do tipo centralizado, branco, negrito, 16 px. Dashboard: card só com o
  título "Alocação da Carteira" (o subtítulo "Carteira de Investimentos" saiu). Em tela pequena (< `sm`) a da direita desce para baixo.
  Dashboard (12/10/2026): card da Alocação com 2/5 da linha e Top 5 com 3/5 (`xl:grid-cols-5`; antes 1/3 e 2/3).
- **Explicações**: `InfoTooltip` (`components/ui/InfoTooltip.tsx`) — ícone (i)
  com balão, no lugar de texto de instrução solto na tela (23/09/2026). Campos
  recebem `info` (`Input`, `MaskedNumberInput` e derivados, `Select`); `Modal`,
  `Card` e `AllocationPieChart` recebem `titleInfo`; colunas da Carteira têm
  `info`; `LabelWithInfo` monta rótulo + (i) em cabeçalho avulso. Três cuidados
  que o componente já resolve e que não devem ser desfeitos:
  - o balão vai num **portal** no body com posição fixa — dentro do modal
    (`overflow-y-auto`) ou da tabela (`overflow-x-auto`) ele seria recortado;
  - o clique para no ícone (`stopPropagation` + `preventDefault`): o (i) mora
    em `<th>` que ordena e em `<label>` de checkbox, que marcaria a caixa;
  - é `type="button"`: dentro de `<form>`, botão sem tipo é submit.
  Textos das colunas da Estratégia ficam juntos em
  `components/strategy/strategyHelp.ts`, porque as mesmas colunas aparecem em
  quatro tabelas.

---

## 💸 IR retido nos proventos (24/09/2026)

Plano completo e conferência contra o extrato da B3 em `docs/planos/PLANO_IR_PROVENTOS.md`.

- **Bruto × líquido.** A integração traz o valor por ação declarado pela
  empresa (bruto). O PrimeVest calcula o IR retido e mostra o líquido, com a
  mesma conta da B3: **bruto = valor por cota × cotas, truncado nos centavos;
  IR = bruto × alíquota, truncado nos centavos; líquido = bruto − IR.** O
  líquido por cota (8 casas) é só exibição — multiplicá-lo pelas cotas erra 1
  centavo.
- **Regras no banco, cálculo no código.** `withholding_tax_rules` guarda
  alíquota, vigência e base legal; `WithholdingTaxCalculator` guarda o método.
  Carga inicial (PF residente): JCP 15% até 31/12/2025 e 17,5% desde 01/01/2026
  **pela data do crédito** (aprovação); dividendo isento; "rendimento" de ação
  (atualização pela Selic) 22,5%; rendimento de FII, amortização e restituição
  de capital isentos. Tipo sem linha (ETF, BDR...) = sem regra: líquido nulo,
  e as somas usam o bruto.
- **Lei nova = migration**: fecha a vigência da linha antiga, insere a nova e
  zera `dividends.tax_checked_at` dos proventos afetados. Nunca UPDATE na V23.
- **Onde roda.** O `DividendRecalculator` é o único lugar que grava provento, e
  calcula o IR junto — rotina, sob demanda e transação. O `DividendTaxVerifier`,
  na subida, recalcula o que estiver com `tax_checked_at` nulo (carga inicial e
  lei nova). Não há endpoint de recálculo.
- **Carteira e Dashboard somam o líquido** (`COALESCE(net_amount, total_amount)`).

## 📅 Proventos recebidos × a receber (24/09/2026)

- **Regra** (`Dividend.isReceived(today)`): recebido = pagamento **até hoje,
  contando o próprio dia (D0)**. Sem data de pagamento: recebido se o evento veio
  só do histórico longo da B3 (`B3_HISTORICO`, que lista apenas o passado); a
  receber se veio da fonte recente com pagamento "a definir"; MANUAL sem evento
  conta como recebido. A mesma regra está nas somas do `DividendRepository`, e o
  `DividendRepositoryTest` confere que as duas batem.
- **"Hoje" é o de São Paulo**, vindo do bean `DividendCalendar` — nunca
  `CURRENT_DATE` do banco: o PostgreSQL roda em UTC e, às 21h de São Paulo, já é
  o dia seguinte. Nos testes, `@MockBean DividendCalendar` fixa a data.
- **Nada é gravado**: o provento passa de "a receber" para "recebido" sozinho
  quando chega o dia do pagamento.
- **Carteira, Dashboard e rentabilidade** somam só o recebido
  (`sumTotalAmountByPosition(portfolioId, today)`).
- **`GET /dividends/`**: `totalDividends`, `byType`, `totalGross` e `totalTax` são
  do recebido; `totalToReceive`, `toReceiveGross` e `toReceiveTax`, do a receber;
  cada provento traz `received`.
- **Tela de Dividendos** (abas desde 11/10/2026, `DividendsPage`): duas abas no
  estilo das da Estratégia, sobre o mesmo `GET /dividends/`, separadas pelo
  `received` de cada provento — por isso a tela não tem mais filtro "Situação"
  nem coluna SITUAÇÃO.
  - **Histórico de Proventos** (recebidos): cards Total Recebido | Recebido por
    Tipo e lista "Histórico de Proventos".
  - **Proventos a Receber**: cards Total a Receber | A Receber por Tipo (mesma
    soma por tipo do recebido, só sobre o a receber: Dividendos, JCP,
    Rendimentos...) e lista "Proventos a Receber".
  - Ordem padrão por PAGAMENTO (12/10/2026): decrescente no Histórico (o mais
    recente primeiro) e crescente no A Receber (o próximo a pagar primeiro); sem
    data de pagamento ("Data não informada") vai para o fim nas duas.
  - Em cada aba: busca por ticker ou nome e filtros Tipo de Ativo, Ativo e Tipo;
    os cards acompanham os filtros. A **busca** (`LocalAssetSearch`, ver "Busca por
    ticker ou nome" em Padrão Visual) procura nos ativos que têm provento na aba (sem acento e sem caixa: "Itaú" acha "ITAU";
    a partir de 2 caracteres; ticker que começa com o texto vem primeiro).
    Escolher um ativo (clique ou Enter) tem o mesmo efeito do Listar. As duas abas ficam montadas (a inativa só escondida), então cada uma
    guarda os seus filtros, página e ordenação ao trocar de aba.
  - Coluna AÇÕES com o ícone **Listar** (`TABLE_ACTION_CLASS`): filtra a aba pelo
    Tipo de Ativo e pelo Ativo da linha e volta o filtro Tipo a "Todos" (como o
    Listar das Transações). Provento sem tipo de ativo fica com o ícone desabilitado.
  - Com um ativo filtrado (pelo Listar, pela busca ou pelo seletor Ativo), a aba mostra o
    aviso do Listar das Transações, "Mostrando apenas os proventos de TICKER", com
    **Limpar filtro**, que volta Tipo de Ativo, Ativo e Tipo a "Todos".

## 🧾 Extrato de proventos da B3 (25/09/2026; só conferência desde 29/09/2026)

**Desde 29/09/2026 o extrato não grava nada** (`docs/planos/PLANO_CARGA_INICIAL.md`, regra C e D13; V38):
evento do catálogo vem de integração — FNET, B3 e relatórios de proventos da CVM. O que só o
extrato de um usuário tem é dado dele. A fonte `EXTRATO_B3` e as origens de data `EXTRATO_B3` e
`DEDUZIDA` saíram do catálogo; a V38 passou os 18 eventos de FII para `FNET` (os avisos estavam em
`carga/data/fnet`), tirou do catálogo os 3 de PETR4 (os proventos do usuário ficaram como `MANUAL`) e
apagou as datas que vinham do extrato.

- **Entrada**: `POST /api/v1/admin/proventos/extrato` (só admin, app-api), corpo `{lines[]}`
  (ativo, tipo, pagamento, cotas, líquido; opcionais bruto e data-com). Serviço:
  `ExtratoProventosImportService`, `@Transactional(readOnly = true)`.
- **Cada linha** vira um status: `JA_EXISTE` (provento da carteira com o mesmo pagamento, cotas e
  líquido); `SEM_DATA` (o provento existe, mas o evento do catálogo está sem data de pagamento — a
  integração ainda não trouxe); `SEM_EVENTO` (o catálogo não tem o provento); `RECUSADO`.
- **Origem da data** (`asset_cash_events.payment_date_source`): nula = da fonte do evento; `CVM`
  = relatório de proventos da CVM; `LEVANTAMENTO` = levantamento 2010–2026 (`carga/data/levantamento`). A data da
  fonte do evento substitui as duas (`CashEventWriter`); a CVM roda antes do levantamento na carga.
- **Tela**: PAGAMENTO com sublinhado pontilhado e hover quando a data veio da CVM ou do levantamento
  (`paymentDateSource` no `DividendResponseDTO`); sem data, "Data não informada" (D16).
- A carteira passou a vir da B3 pela API da Área do Investidor (`docs/planos/PLANO_INTEGRACAO_B3.md`; o que falta
  na PENDENCIAS 16). A integração usa esta mesma conferência para os proventos que a B3 informa. Nada fora do
  projeto é necessário para subir o PrimeVest.
- **`GlobalExceptionHandler`** trata `ResponseStatusException` com o status e o
  motivo da própria exceção. Antes ela caía no handler genérico e virava 500 —
  inclusive o 403 dos endpoints de admin.
- Testes: `ExtratoProventosIT` (6 cenários) e `PaymentDateSourceIT` (2: CVM cede à B3; FNET assume a reserva da B3).

## ⚠️ Pontos de Atenção

### Venda de renda fixa exige o título
`resolveNote` recusa venda sem `noteId`, porque criar nota nova numa venda
geraria posição negativa de um papel nunca comprado. **Pela API funciona** — é o
que os testes de resgate parcial e liquidação total fazem — e, desde 05/10/2026, **pela tela também**: o ícone
Vender da Carteira lança no título da linha, e a Nova Transação tem o seletor do título na venda de Renda Fixa
(emissor · vencimento · taxa; desde 07/10/2026 todos os títulos, sem o saldo), com o atalho "Resgatar tudo" (saldo na
curva + liquidação total).

### Valorização da renda fixa (05/10/2026)
Plano: `docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md`. Dois mundos, duas fontes:

- **Tesouro Direto = cotação.** O PU de venda oficial (V44, `TreasuryPriceService`) vira `assets.current_price` e o
  `AppreciationUpdater` grava cotas × PU − custo por título, como nas ações. Pode ficar **negativa** (marcação a
  mercado). A taxa da nota continua a da compra, só informativa. Cupons de juros semestrais não entram (PENDENCIAS 24.1).
- **Renda fixa privada = curva contratada.** `FixedIncomeCurve` (core, regra pura) faz o saldo dia útil a dia útil com
  os aportes e resgates do título: prefixado `(1+r)^(1/252)`; % do CDI/Selic `1 + taxa × p`; CDI/Selic + spread;
  IPCA/IGP-M/INPC com o mês distribuído pelos dias úteis (`BusinessCalendar`) e o mês não divulgado estimado pelo
  último publicado; resgate acima do saldo → centavo da regra F (o resgate prova o valor); vencido para de render;
  TR sem curva. Referência = último CDI publicado ("atualizado até"). `FixedIncomeSettlement.applyCurve` grava:
  aberto = saldo na curva (valorização = saldo + resgatado − aplicado, nunca negativa); liquidado = ganho do resgate;
  sem curva = como estava. Roda depois de cada transação (após as regras E/F/G), às 21:00, na subida da app-api e no
  "catálogo mudou" INDICES. Valores **brutos** (sem IR/IOF).
- **`BusinessCalendar`** (core): feriados nacionais calculados; conferido contra as 4.208 datas do CDI 2010–2026.

### `dividends` não tem dono próprio
O dono vem de `portfolio_asset_id`, que é **obrigatório** (`NOT NULL`) e
**cascateia** (FK `ON DELETE CASCADE`), desde o segundo reset das migrations
(V1, 18/09/2026). Não existe provento órfão: apagar a posição apaga os
proventos dela — é o que a regra K faz quando a última transação sai.

A tabela também não tem `asset_id`: o ativo é alcançado pela posição
(`dividends → portfolio_assets → assets`). Com `ON DELETE SET NULL` (o desenho
anterior) existiria provento sem ativo e sem dono, invisível até para quem o
recebeu. Consequência: provento de ativo que saiu da carteira só existe
enquanto a posição existir.

> Revisado em 02/10/2026: o texto anterior descrevia a FK como opcional e
> `SET NULL`, o que não vale desde a V1 do reset (conferido no banco e em
> `Dividend.portfolioAsset`, `nullable = false`).

### `assets.terms` sem cascade, de propósito
O `@OneToOne` de `Asset` para `AssetTerms` **não** tem `cascade` nem
`orphanRemoval`. Com eles, salvar um ativo destacado cujo `terms` viesse nulo —
e o scheduler de preços salva ativo o tempo todo — apagaria a linha de
`asset_terms`. Os 130 vencimentos do Tesouro não dependem de alguém lembrar de
carregar a associação antes de um `save`.

### Lado inverso de `@OneToOne` não é preguiçoso
O Hibernate precisa decidir entre objeto e `null`, então dispara um SELECT por
linha mesmo com `FetchType.LAZY`. Por isso toda consulta de `Asset` que vira DTO
usa `LEFT JOIN FETCH a.terms`. Numa lista de 1.264 ativos, a diferença é 1 contra
1.265 consultas.

### `Pageable` com `@Query` que já tem ORDER BY
Passar um `Sort` faz o Spring Data concatenar um segundo `ORDER BY` e quebrar.
Use `PageRequest.of(0, N)` sem `Sort` — é o que `AssetService` faz.

### `ddl-auto: validate` no perfil docker
Entidade e migration têm que mudar na mesma subida. Se a API não subir, a
mensagem nomeia tabela e coluna. Voltar para `none` é uma linha, mas resolve o
sintoma, não a causa.

### Precisão decimal — regra em vigor
As anotações é que cedem ao schema, nunca o contrário: mexer no banco aqui
truncaria dados.

| Natureza | Tipo | Colunas |
|----------|------|---------|
| Quantidades e preços unitários | `numeric(18,8)` | `shares`, `price_per_share`, `current_price`, `max_price`, `amount_per_share`, preços do histórico |
| Valores monetários agregados | `numeric(18,2)` | `appreciation`, `total_amount`, `brokerage_fee`, `target_amount`, `current_amount`, `target_value` |
| Taxa de indexador | `numeric(10,4)` | `indexer_rate` |
| PU do Tesouro | `numeric(18,8)` | `asset_price_history.close_price`, `assets.current_price` |
| Valor de índice de mercado | `numeric(18,8)` | `market_index_values.value` (CDI 0,050788 % a.d.; Ibovespa 132.696,63) |
| Percentual da estratégia | `numeric(5,2)` | `target_percentage` |

As **8 casas das quantidades existem por causa de cripto** — alinhar para 4
truncaria fração de BTC.

Desde 25/09/2026 (F1b do `docs/planos/PLANO_CRIPTO_BINANCE.md`) a aplicação acompanha: preço
médio com 8 casas (`PositionCalculator.AVERAGE_PRICE_SCALE`); `isCrypto` nas linhas
de transação e de carteira; na tela, cripto mostra quantidade com até 8 casas e
preço com 2 a 8 (`formatQuantity`, `formatUnitPrice`), e no modal a quantidade de
cripto e o preço unitário usam a mesma máscara com 8 casas (`decimals={8}`). Os
demais tipos não mudaram.

### Auditoria de datas — quem tem e como é preenchida
Desde a V30 (18/09/2026), `created_at` + `updated_at` existem em: `users`,
`portfolios` (só criação), `transactions`, `fixed_income_notes`, `dividends`
(só criação), `ceiling_prices`, `goals`, `portfolio_assets` e
`asset_price_history`. Ficam de fora, de propósito, as tabelas de catálogo e
referência (`assets`, `asset_terms`, `asset_types`, `asset_categories`) e
`strategies`.

O preenchimento é **manual**, por `@PrePersist` / `@PreUpdate` com
`LocalDateTime.now()` — não se usa `@CreationTimestamp` do Hibernate. Duas
consequências:

- Um INSERT feito fora do Hibernate (seed, migration, correção via psql) não
  passa pelos callbacks. Por isso toda coluna tem `NOT NULL DEFAULT NOW()` no
  banco: é o DEFAULT que segura esse caso.
- `@PreUpdate` só dispara em entidade gerenciada. Um `UPDATE` em JPQL/SQL com
  `@Modifying` passaria por cima — hoje não existe nenhum no projeto, e essa é
  a razão de não existir.

**`portfolio_assets.updated_at` se move sozinha.** `AppreciationUpdater` grava
`appreciation` a cada rodada do fechamento e na subida da API, e
isso conta como alteração da linha. A coluna responde "quando esta linha mudou",
não "quando o usuário mexeu nesta posição" — para a segunda pergunta, o que vale
é `transactions.updated_at`.

### Testes — existem desde 22/09/2026

**Em 10/10/2026 (Preço Teto, payout pela DFC, COTAHIST diário): core 158; integration 103; adm-api 89 + 40; app-api 189 + 199 — todos verdes em `mvn verify` (~6 min).** Antes, em 05/10/2026, noite (valorização da renda fixa): 521 unitários + 231 de integração, por módulo: core 158; integration 103; adm-api 88 + 38; app-api 172 + 193, todos verdes em `mvn clean verify` (~7 min). Na F11 (28/09/2026) eram 394 + 180.
O suporte comum (container do PostgreSQL, bases, fixtures, `application-test.yml`) vem do
test-jar do core. A app-api tem a adm-api **só no classpath de teste**, para os testes de ponta a ponta de
proventos e fechamento (`CatalogoE2EBase`: fonte mockada → catálogo pela adm-api → recálculo pela app-api →
API do usuário). O histórico abaixo é de antes da separação.

`src/test` deixou de estar vazio. **319 testes** em 24/09/2026, verdes em
`mvn clean verify` (~75s): 240 rapidos (`*Test`, Surefire) e 79 funcionais
(`*IT`, Failsafe). A integração BRAPI/B3 trouxe `BrApiClientTest` (ExchangeFunction
falsa), `CorporateActionApplierTest`, `PositionCalculatorEventosTest`,
`DividendRecalculatorTest`, `B3RespostasReaisTest` (respostas reais da B3 e do
FNET em `src/test/resources/b3`) e os funcionais `FechamentoIT`, `ProventosIT` e
`EventosSocietariosIT`.

| Camada | O que cobre |
|--------|-------------|
| Repositório (37) | consultas escopadas pelo dono, `NULLS NOT DISTINCT` da posição, unicidade e CHECK da reserva, regra H no índice de títulos, ordenação da busca de ativos |
| Regra pura (36) | `PositionCalculator`, `PositionGuard` (regras A, B, I), `FixedIncomeSettlement` (E, F, G, J) |
| Serviço (72) | Portfolio, Strategy, StrategyReservation, Transaction — decisão e roteamento, com repositórios mockados |
| Controller (31) | 401 sem token, 400 de validação, 404, 422, e o JSON que a tela lê |
| Funcional (58) | Transações, Carteira, Alocação e Estratégia pela API, com PostgreSQL de verdade |

**Tudo contra PostgreSQL 17 em container** (Testcontainers), com Flyway rodando
e `ddl-auto: validate` — cada teste de integração também prova que entidade e
migration continuam batendo. H2 foi removido: ele não tem `NULLS NOT DISTINCT`,
nem os CHECKs, nem o `ORDER BY` com `CASE` da busca.

Os **exemplos 1 a 6 do escopo de vendas** viraram testes com o nome do exemplo,
em `TransacoesIT`: saldo por cota em qualquer data (1 a 3), FIFO do Tesouro (4),
venda sem cotas na data (5) e a compra retroativa que refaz a repartição (6).

Como rodar:

```powershell
mvn clean test      # rapido, sem os funcionais (da raiz: os três módulos)
mvn clean verify    # tudo (precisa do Docker Desktop)
mvn -pl primevest-adm-api -am verify   # um módulo (e o que ele usa)
.\.claude\build.ps1 -Verify   # o mesmo, com log em .claude\logs\
```

> **`clean` não é opcional.** O `target/` guarda migrations de antes do reset, e
> o Flyway recusa a subida reclamando de duas V1.

> **Docker 29 exigiu Testcontainers 1.21.4** (`testcontainers.version` no
> `pom.xml`). A versão que o Spring Boot 3.3.5 gerencia (1.19.8) fala uma versão
> de API que o Docker 29 recusa, e o erro é enganoso: "Could not find a valid
> Docker environment", com o Docker Desktop rodando normalmente.

O perfil `test` (`primevest-core/src/test/resources/application-test.yml`) desliga Redis, as mensagens e as
rotinas agendadas; as fontes externas são mockadas nos testes (Binance no `FuncionalBase`, Área do Investidor da B3
no `IntegracoesB3IT` com a `ContaSinteticaB3`, B3/FNET/BRAPI/
CoinGecko no `CatalogoE2EBase` e nos testes da adm-api): nenhum teste depende de rede.

### O lint do front passou a existir em 18/09/2026
`npm run lint` falhava desde sempre com *couldn't find an eslint.config.(js|mjs|cjs)*:
faltavam **as duas** peças, o arquivo de config e o parser. O ESLint 9 abandonou o
`.eslintrc`, e `typescript-eslint` não estava no `package.json` — sem ele o ESLint
nem faz o parse de um `.tsx`. Entraram os dois.

Hoje fecha em **0 erro e 32 avisos** (35 até 23/09/2026, quando os `catch` com `any` das telas de transação passaram a usar `apiMessage`). Os avisos são quase todos `any` nos clients de
API e em blocos `catch`; `no-explicit-any` ficou como **aviso**, não erro, de
propósito — subir a régua antes de tipar as respostas transformaria o lint numa
parede que ninguém lê.

Os quatro erros do primeiro `npm run lint` foram corrigidos no mesmo dia. Três eram
cosméticos (`prefer-const` no `DataTable`, interface vazia no `LoadingSpinner`). O
quarto não: `MaskedNumberInput` fazia `const inputId = id || React.useId()`, e o
curto-circuito só chamava o `useId` quando a prop `id` vinha vazia — a quantidade de
hooks do componente mudava conforme a prop, que é exatamente o que as regras de hooks
proíbem. Passou a chamar o `useId` incondicionalmente e escolher depois. Nunca chegou
a se manifestar porque `id` é estável por instância, mas é o componente que sustenta
`CurrencyInput`, `PercentInput` e a Quantidade do modal de transação.

### `tsc -b` do front — corrigido em 23/09/2026
`npm run build` é `tsc -b && vite build`, e o `tsc -b` falhava em
`vite.config.ts` (`path`, `process`, `__dirname`): faltava `@types/node`. Isso
quebrava também a imagem `runtime` do Dockerfile, que roda `npm run build`; o
dev server nunca sentiu, porque o Vite transpila o config sem checar tipo.
Entrou `@types/node` (^24, o Node da imagem) no `devDependencies`, com
`"types": ["node"]` no `tsconfig.node.json`.

Segundo problema do mesmo arquivo: sem `noEmit` (proibido em projeto
referenciado, TS6310), o `tsc -b` **gravava `vite.config.js` e `.d.ts` ao lado
do `.ts`** — e o Vite carrega o `.js` antes, então uma edição no `.ts` passaria
a ser ignorada. Agora o `tsconfig.node.json` emite só declaração, em
`node_modules/.tmp/`, e os dois `tsbuildinfo` foram para lá também. Depois de
mudar dependência, o container precisa de `npm install` próprio
(`docker exec primevest-web npm install`): o `node_modules` dele é um volume
anônimo, não o do Windows.

### Nomes de ação sem acento
Os 782 nomes de ação vieram sem acento da importação original: `AALR3` é
"ALLIANA SADE E PARTICIPAES S.A.". Os 337 FIIs têm acento normalmente. Corrigir
exige a lista certa, de fonte com encoding correto — não dá para adivinhar.

### Encoding no PowerShell
PowerShell corrompe UTF-8. Para SQL com acentos, use escape:
```sql
UPDATE asset_types SET description = E'Ações' WHERE id = 1;
```
Dentro do Docker isso não é problema.

### Imagem do front: `node:24`, não `node:24-alpine`
Trocada em 18/09/2026. As camadas de `node:24-alpine` ficaram **corrompidas no
store local do Docker Desktop**: arquivos com o tamanho certo e o conteúdo todo
zero. O sintoma era `exec /usr/local/bin/docker-entrypoint.sh: exec format
error` e o container reiniciando em laço; `docker run --rm --entrypoint sh
node:24-alpine -c "node -v"` dava segfault e um `od -c` do entrypoint mostrava
só `\0`.

Não era arquitetura (host e imagem amd64) nem Alpine (o `alpine:3.22`,
`node:22-alpine`, `postgres:17-alpine` e `redis:7-alpine` rodam). Era só aquela
imagem. Remover e baixar de novo **não resolve**: o digest confere, então o
Docker reaproveita as camadas ruins — o pull sai sem nenhuma linha de layer, que
é a pista. Só uma purga do content store (Docker Desktop → Troubleshoot → Clean
/ Purge data) devolveria a `-alpine`, e ela apaga **todos** os volumes junto,
inclusive o `pgdata`. Se for fazer, salve o banco antes:

```powershell
docker run --rm -v primevest_pgdata:/from -v E:\repos:/to alpine tar cf /to/pgdata-backup.tar -C /from .
```

Custo de ficar no Debian: ~700 MB a mais na imagem de dev. Como o código entra
por bind mount, é download de uma vez, não custo de execução.

### Porta 8080 sequestrada pelo WinNAT
`ports are not available: ... bind: An attempt was made to access a socket in a
way forbidden by its access permissions` no Windows quase nunca é porta ocupada:
é porta **reservada** por uma faixa de exclusão do WinNAT/Hyper-V, que costuma
engolir a 8080 quando o container é derrubado para recriar.

```powershell
netstat -ano | findstr :8080                              # normalmente vazio
netsh interface ipv4 show excludedportrange protocol=tcp  # a 8080 aparece aqui
net stop winnat; net start winnat                         # como administrador
```

Se voltar a cada reboot, a saída definitiva é `API_PORT=8082` num `.env` na
raiz (a 8081 é da adm-api) — o compose já lê a variável, e o front fala com a API por `app-api:8080` na
rede interna, então nada mais muda.

Às vezes é porta ocupada de fato: em 07/10/2026 era o `MTAgentService` (MiniTool ShadowMaker, inicia com o
Windows). `Get-NetTCPConnection -LocalPort 8080` mostra o processo; `Stop-Service MTAgentService` (administrador)
libera, e `docker compose up -d app-api web` sobe os dois (o web depende do app-api e não sobe sozinho).

### Egress bloqueado
A política da organização bloqueia, entre outros: `repo.maven.apache.org`
(então não dá para compilar fora do Docker em ambiente restrito),
`api.bcb.gov.br`, `apisidra.ibge.gov.br`, `b3.com.br`, `statusinvest.com.br`, `brapi.dev`. Um 403/000 do
proxy é negação de política — não é para contornar. O bloqueio é do ambiente do assistente (nuvem): na máquina do
usuário e nos contêineres o SGS do Banco Central e o site da B3 respondem (conferido em 05/10/2026), e é dali que a
adm-api os consulta.

---

## 🤖 Como o Claude executa comandos aqui

Relevante porque explica por que existe a pasta `.claude/` e por que, em algumas
sessões, o assistente consegue compilar e em outras não.

O Claude no **Cowork** (sessão em nuvem do app desktop) enxerga as pastas
conectadas, mas o shell dele é uma **VM Linux isolada**: sem Maven, sem Docker,
com JDK 11 e com a rede barrada pela política de egress da organização —
`repo.maven.apache.org` e `registry.npmjs.org` respondem 403. Nessa configuração
ele lê, busca e edita arquivo, e só. Nem `npm install`, nem `mvn`, nem subir
container.

O que destrava é instalar o **Claude Code** no Windows e registrá-lo como
servidor MCP local do app desktop. O `claude mcp serve` expõe as ferramentas
dele (Bash, PowerShell, Read, Edit, Glob, Grep) por stdio, e a sessão em nuvem
passa a executar **no PowerShell real**, com `JAVA_HOME`, `MAVEN_HOME` e o
Docker Desktop da máquina. Configurado em 18/09/2026, em
`claude_desktop_config.json`:

```json
"mcpServers": {
  "claude-code": {
    "type": "stdio",
    "command": "C:\\Users\\<usuario>\\.local\\bin\\claude.exe",
    "args": ["mcp", "serve"]
  }
}
```

Dois detalhes que custaram tempo: o botão **Adicionar conector** das
configurações só aceita URL HTTPS e serve para servidores remotos — servidor
stdio local não entra por ali; e o app **reescreve** esse arquivo, então ele só
deve ser editado com o app fechado, sob pena de a alteração ser sobrescrita na
saída.

Comando que bloqueia (`mvn spring-boot:run`, `npm run dev`) não funciona bem por
esse caminho, porque cada chamada de ferramenta tem tempo limite. Suba
desacoplado (`docker compose up -d`) e leia o log num comando separado — que é
justamente o que o `.claude/build.ps1` faz.

Sem a ponte, o fallback é o `.claude/build.ps1` rodado à mão: ele grava a saída
em `.claude/logs/`, e o Claude lê o log inteiro em vez de depender de um trecho
colado no chat.

---

## 🌿 Git

- **Repositório**: https://github.com/guilherme-dealmeida/primevest.git
- **Branch atual**: `TRACK_01-ajustes_crud_inicial`
- **Branches**: master, uat, develop, TRACK_01-ajustes_crud_inicial
- **Git é do usuário** (19/09/2026): o assistente **não executa** comando de
  git — nem commit, nem branch, nem push — e **não comenta** o que está ou não
  comitado. Nada de "segue não comitado", nada de status de working tree, nada
  de sugerir commit ao terminar. O usuário cuida disso por fora.
- **A validação da implementação é a última etapa do assistente.** Compilar,
  rodar o lint, subir o ambiente e conferir o comportamento fecham o trabalho:
  entregue isso, o assunto acabou.

### Estado do working tree

**Limpo em 19/09/2026**, na `TRACK_01-ajustes_crud_inicial`. O que esta seção
listava como pendente foi comitado; ela agora registra em que commit cada
bloco entrou, para quem precisar voltar a um deles.

| Commit | O que entrou |
|--------|--------------|
| `2e731b3` | **Reserva de emergência**: `findOwnedById` em `PortfolioAssetRepository`, `PortfolioService.setReservation`, `PATCH /portfolio/assets/{id}/reservation` com `ReservationUpdateDTO` e `PortfolioAssetReservationDTO`; no front, `ToastContext`, `ui/Switch` e a coluna RESERVA da carteira. Sem migration — `is_reservation` já vinha da V1 |
| `c19ff7c` | Ajustes das telas de Alocação e Estratégia |
| `a63ca14` | `docs/PENDENCIAS.md` e atualizações deste documento |
| `7d8a150` | Rebalanceamento estratégico |
| `672ad13` | `.claude/` (`build.ps1`, `README.md`, `logs/` ignorado) e a entrada no `.gitignore` |

Nos mesmos commits vieram, das sessões anteriores: a separação catálogo /
título (`AssetTerms`, `FixedIncomeNote`, `note_id` em transação e posição), os
**dois resets das migrations** (33 → 22 e depois 30 → 9; as antigas em
`E:\repos\primevest-backups\migration-antiga-*`), as correções de autorização
nos quatro services, a V30 de auditoria de datas — absorvida pelo segundo
reset —, a carteira com emissor e vencimento, o modal de transação em duas
colunas com as máscaras, a troca da imagem do front para `node:24` e o lint do
front (`eslint.config.js`, `typescript-eslint` e as quatro correções).

Esta seção é um retrato do dia 19/09/2026, mantido só como histórico do que
entrou em cada commit. O estado atual do repositório é assunto do usuário.

### Limpeza
`_to_delete/` foi **apagada em 18/09/2026**. Na hora da exclusão ela já estava
praticamente vazia: as 33 migrations antigas, a migration descartada e os tarballs
temporários tinham sumido antes, e restavam só dois arquivos de teste de 34 bytes.

A pasta `primevest-api/src/main/java/com/primevest/Claude outputs/`, que continha
um único `claude_desktop_config.json` — cópia da configuração do app desktop,
sobra de sessão anterior —, também não existe mais.
