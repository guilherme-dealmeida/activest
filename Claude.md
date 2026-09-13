# PrimeVest — Documentação do Projeto

> **Tagline**: DISCIPLINA LEVA À LIBERDADE

Plataforma de gestão de investimentos pessoais: dashboard, carteira, transações,
alocação, estratégia com rebalanceamento, dividendos, preço teto e metas.

---

## 🚀 Como rodar

### Opção 1 — Docker (recomendada)

Sobe banco, cache, API e front de uma vez, com o banco construído do zero pelas
migrations. Não exige Java, Maven, Node nem PostgreSQL instalados na máquina.

```bash
docker compose up --build
```

| Serviço | URL / Porta |
|---------|-------------|
| Frontend (Vite, hot reload) | http://localhost:5173 |
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| PostgreSQL | localhost:**5433** |
| Redis | localhost:6379 |

> A porta do banco é **5433** de propósito, para não colidir com o PostgreSQL 17
> instalado na máquina, que ocupa a 5432. Para conectar pelo pgAdmin use `localhost:5433`.

Comandos do dia a dia:

```bash
docker compose up --build      # sobe tudo (build na primeira vez)
docker compose down            # para tudo, mantém o banco
docker compose down -v         # para tudo e APAGA o banco (recria do zero na próxima subida)
docker compose logs -f api     # acompanha os logs do backend
docker compose exec db psql -U postgres -d db_primevest   # psql dentro do container
```

**Hot reload**: `primevest-api/src` e `primevest-web/` entram por bind mount.
Alterar um `.java` reinicia o Spring via DevTools; alterar um `.tsx` recarrega o Vite.

**Variáveis de ambiente**: tudo tem padrão no `docker-compose.yml`, então o projeto
sobe sem configuração. Para sobrescrever, copie `.env.example` para `.env`
(já está no `.gitignore`).

### Opção 2 — Local, sem Docker

Exige Java 21, Maven 3.9+, Node 24 e PostgreSQL 17 com o banco `db_primevest` já criado.

```powershell
# Backend — porta 8080
cd primevest-api
mvn spring-boot:run "-Dmaven.test.skip=true"

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
| Banco | PostgreSQL 17 |
| Cache | Redis 7 (cotações; `cache.type: simple` no perfil dev) |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Autenticação | JWT (jjwt 0.12.6) + Spring Security + Google OAuth2 |
| HTTP externo | Spring WebFlux / WebClient |
| Mapeamento | MapStruct 1.6.3 + Lombok |
| Documentação de API | SpringDoc OpenAPI 2.6.0 (`/swagger-ui.html`) |
| Gráficos | Recharts |
| Estado/fetch | TanStack React Query 5 |
| Rotas | React Router 6 |
| CSS | Tailwind CSS 3 |
| Testes | JUnit 5, Mockito, Spring Security Test, H2, Testcontainers |
| Build | Maven / npm |

### Integrações externas

| API | Uso | Config |
|-----|-----|--------|
| **BrAPI** | Cotações da B3 (ações, FIIs, ETFs, BDRs) | `brapi.base-url`, `BRAPI_TOKEN` |
| **CoinGecko** | Cotações de criptomoedas | `coingecko.base-url` |
| **Google OAuth2** | Login social | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` |

---

## 📁 Estrutura do Projeto

```
primevest/
├── docker-compose.yml           # banco + redis + api + web
├── .env.example                 # variáveis (copiar para .env)
├── README.md                    # visão geral do produto
├── Claude.md                    # este arquivo — referência operacional
├── generate_assets_sql.py       # gera SQL de ativos da B3
├── generate_classes.py          # gerador auxiliar de classes
├── generate_seeds.js            # gerador dos seeds V13-V18
│
├── primevest-api/
│   ├── Dockerfile               # alvos: dev (spring-boot:run) e runtime (jar)
│   ├── .dockerignore
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/primevest/
│       │   ├── config/          # SecurityConfig, CorsConfig, RedisConfig,
│       │   │                    # SwaggerConfig, SecurityUtils, PasswordEncoderConfig
│       │   ├── domain/
│       │   │   ├── asset/       # Asset, AssetType, AssetCategory, AssetService
│       │   │   ├── auth/        # AuthController/Service, JwtTokenProvider,
│       │   │   │                # JwtAuthFilter, OAuth2SuccessHandler, UserDetailsServiceImpl
│       │   │   ├── ceilingprice/# CeilingPrice + service
│       │   │   ├── dividend/    # Dividend, DividendType, service
│       │   │   ├── goal/        # Goal, GoalStatus, service        (metas)
│       │   │   ├── portfolio/   # Portfolio, PortfolioAsset, service
│       │   │   ├── pricehistory/# AssetPriceHistory + service
│       │   │   ├── strategy/    # Strategy, service (ideal + rebalanceamento)
│       │   │   ├── transaction/ # Transaction, TransactionType, service
│       │   │   └── user/        # User + service
│       │   ├── exception/       # BusinessException, ResourceNotFoundException,
│       │   │                    # GlobalExceptionHandler, ErrorResponse
│       │   ├── integration/
│       │   │   ├── brapi/       # BrApiClient + DTOs
│       │   │   └── coingecko/   # CoinGeckoClient + DTO
│       │   └── scheduler/       # PriceUpdateScheduler, DividendSyncScheduler
│       └── resources/
│           ├── application.yml          # base
│           ├── application-dev.yml      # local (Flyway OFF)
│           ├── application-docker.yml   # containers (Flyway ON)
│           ├── application-prod.yml
│           └── db/migration/            # V1 .. V19
│
└── primevest-web/
    ├── Dockerfile               # alvos: dev (vite) e runtime (nginx)
    ├── .dockerignore
    ├── vite.config.ts           # proxy /api configurável por VITE_API_TARGET
    ├── tailwind.config.js
    └── src/
        ├── api/                 # clients axios por domínio
        ├── components/          # charts/, layout/, ui/
        ├── context/             # AuthContext, ThemeContext
        ├── hooks/               # useSortable
        ├── mocks/               # data.ts — LEGADO, a remover
        ├── pages/               # 10 páginas
        ├── types/               # interfaces TypeScript
        └── utils/               # format.ts, assetTypes.ts (legado)
```

---

## 🗄 Banco de Dados

### Conexão

| Ambiente | Host | Porta | Database | Usuário |
|----------|------|-------|----------|---------|
| Docker (a partir do host) | localhost | 5433 | db_primevest | postgres |
| Docker (entre containers) | db | 5432 | db_primevest | postgres |
| Local | localhost | 5432 | db_primevest | postgres |

Credenciais vêm de variáveis de ambiente (`DB_USERNAME`, `DB_PASSWORD`), com
padrão `postgres`/`postgres` em desenvolvimento. **Não versione credenciais reais.**

### Tabelas

| Tabela | Descrição |
|--------|-----------|
| `users` | Usuários (senha bcrypt em `password_hash`, ou `google_id` para OAuth) |
| `portfolios` | Carteira (1 por usuário) |
| `portfolio_assets` | Posição consolidada: `total_shares`, `average_price`, `total_invested`, `current_value`, `is_reservation` |
| `transactions` | Compras e vendas |
| `assets` | Cadastro de ativos (`ticker`, `name`, `custom`) |
| `asset_types` | 6 tipos fixos |
| `asset_categories` | Categorias dentro de cada tipo |
| `strategies` | % ideal por tipo — `unique(user_id, asset_type_id)` |
| `dividends` | Proventos recebidos |
| `ceiling_prices` | Preço teto por ativo — `unique(user_id, asset_id)` |
| `goals` | Metas de investimento |
| `asset_price_history` | Histórico de cotações |

### Asset Types

| ID | name | description |
|----|------|-------------|
| 1 | RENDA_FIXA | Renda Fixa |
| 2 | TESOURO_DIRETO | Tesouro Direto |
| 3 | FII | Fundos Imobiliários |
| 4 | RENDA_VARIAVEL | Renda Variável (Ações) |
| 5 | INTERNACIONAL | Investimentos Internacionais |
| 6 | CRYPTO | Criptomoedas |

> **Convenção**: em DTOs e respostas de API, usar `getDescription()` para exibição
> ao usuário. `getName()` é só para lógica interna (ex: Scheduler).

### Coluna `custom` em `assets`

- `custom = true` → ativo inserido manualmente pelo usuário
- `custom = false` → ativo cadastrado automaticamente (B3, Tesouro Direto)

### Flyway

O Flyway roda **do zero** (V1 → V19) no perfil `docker`. No perfil `dev` fica
desligado, porque o banco local já existe e é gerenciado à mão.

Estado após uma subida limpa, verificado contra PostgreSQL:

| Tabela | Linhas |
|--------|--------|
| assets | 1.426 (1.407 com `custom = false`) |
| portfolio_assets | 42 |
| transactions | 106 |
| strategies | 6 |
| ceiling_prices | 6 |
| dividends | 6 |
| goals | 4 |
| users | 1 |

> **Histórico**: `baseline-version: 18` foi removido do `application.yml`. Ele fazia
> o Flyway tratar um banco vazio como se já estivesse na versão 18, pulando a criação
> de todas as tabelas e seeds e rodando apenas a V19 — o projeto não subia do zero.
> Junto disso, `portfolio_assets` e `strategies` ganharam a coluna `created_at`
> (V5 e V7), que as seeds V13 e V18 já inseriam mas o schema não declarava.

---

## 🔑 Autenticação

- **Login por email/senha**: `POST /api/v1/auth/login` → `{ "accessToken": "..." }`
- **Login Google**: `POST /api/v1/auth/google` com o token do Google
- **Header nas demais chamadas**: `Authorization: Bearer <token>`
- Token guardado no frontend em `localStorage` sob a chave `primevest_token`
- Resposta 401 em qualquer chamada limpa o token e redireciona para `/login`
- Usuário semeado pela V12: `guilhermeunatec@gmail.com` (hash bcrypt na migration;
  **a senha não fica documentada aqui**)

---

## 🔌 Endpoints da API

Conferidos contra os clients do frontend (`primevest-web/src/api/`).

### Auth
| Método | Endpoint |
|--------|----------|
| POST | `/api/v1/auth/login` |
| POST | `/api/v1/auth/register` |
| POST | `/api/v1/auth/google` |

### Portfolio
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/portfolio/` | Resumo da carteira |
| GET | `/api/v1/portfolio/assets` | Ativos com posição detalhada |
| GET | `/api/v1/portfolio/allocation` | Alocação por tipo |
| GET | `/api/v1/portfolio/allocation/detailed` | Alocação por tipo e categoria |
| GET | `/api/v1/portfolio/performance` | Rentabilidade |
| GET | `/api/v1/portfolio/dividends` | Dividendos da carteira |

### Transactions
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/transactions/` | Listar |
| GET | `/api/v1/transactions/{id}` | Buscar por ID |
| POST | `/api/v1/transactions/` | Criar (recalcula portfólio) |
| PUT | `/api/v1/transactions/{id}` | Editar (recalcula portfólio) |
| DELETE | `/api/v1/transactions/{id}` | Excluir (recalcula portfólio) |
| POST | `/api/v1/transactions/recalculate-all` | Manutenção: recalcula todos os portfolio_assets |

### Strategy
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/strategy/` | Estratégia do usuário |
| PUT | `/api/v1/strategy/` | Atualizar (full replace) |
| DELETE | `/api/v1/strategy/{assetTypeId}` | Remover um tipo |
| GET | `/api/v1/strategy/rebalance` | Sugestão de rebalanceamento |

### Assets
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/assets/` | Listar — params `typeId`, `query` (autocomplete) |
| GET | `/api/v1/assets/{ticker}` | Buscar por ticker |
| GET | `/api/v1/assets/types` | Tipos de ativo |
| GET | `/api/v1/assets/categories` | Categorias — param `typeId` |

### Dividends
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/v1/dividends/` | Resumo de proventos |
| POST | `/api/v1/dividends/` | Registrar |
| GET | `/api/v1/dividends/asset/{ticker}` | Proventos de um ativo |

### Ceiling Prices
| Método | Endpoint |
|--------|----------|
| GET | `/api/v1/ceiling-prices/` |
| POST | `/api/v1/ceiling-prices/` |
| PUT | `/api/v1/ceiling-prices/{id}` |
| DELETE | `/api/v1/ceiling-prices/{id}` |

### Goals
| Método | Endpoint |
|--------|----------|
| GET | `/api/v1/goals/` |
| POST | `/api/v1/goals/` |
| PUT | `/api/v1/goals/{id}` |
| DELETE | `/api/v1/goals/{id}` |

---

## 📊 Telas

Rotas em `App.tsx`; tudo abaixo de `/login` é protegido por `ProtectedRoute`.

| Rota | Tela | Menu lateral |
|------|------|--------------|
| `/dashboard` | Dashboard — pizza de alocação por tipo, cards de resumo | Dashboard |
| `/portfolio` | Carteira — posição por ativo | Carteira |
| `/allocation` | Alocação — abas "Carteira de Investimentos" e "Por Categoria" | Alocação |
| `/strategy` | Estratégia — abas "Ideal Estratégico" e "Rebalanceamento" | Estratégia |
| `/transactions` | Transações — CRUD completo | Transações |
| `/ceiling-prices` | Preço Teto — CRUD | Preço Teto |
| `/dividends` | Dividendos | Dividendos |
| `/goals` | Metas | Metas |
| `/settings` | Configurações | (ícone de engrenagem no rodapé) |
| `/login` | Login (email/senha + Google) | — |

### Transações
- CRUD completo
- Autocomplete de ticker (busca a partir de 2 caracteres, debounce 300ms)
- Tipo de ativo preenchido pelo backend (`description` do AssetType)
- Filtros por período (30/60/90 dias/Tudo) e por tipo de ativo
- Paginação 10 / 25 / 50 / 100 (padrão 10)
- Campo Total (readonly) = quantidade × preço unitário
- Na edição, ticker e tipo de ativo são readonly

### Estratégia
- **Aba "Ideal Estratégico"**: tabela de percentuais editáveis + pizza lado a lado.
  Adicionar/remover tipos age só em memória; o botão Salvar persiste tudo
  (full replace: delete all + insert). Botão ↻ restaura do banco. Há indicador
  visual de alterações não salvas.
- **Aba "Rebalanceamento"**: Tipo Ativo, % Ideal, Aportado, Aportar,
  Estratégico (Aportado+Aportar), % Carteira, % Diff, Sugestão (APORTAR/AGUARDAR).

---

## 🔄 Fluxo de Transações → Portfólio

Criar, editar ou excluir uma transação dispara `recalculatePortfolioAsset(pa)`:

1. Busca **todas as transações** daquele PortfolioAsset em ordem cronológica
2. **BUY**: soma shares, recalcula preço médio ponderado, acumula custo
3. **SELL**: subtrai shares, reduz o custo proporcionalmente (pelo preço médio)
4. Atualiza `totalShares`, `averagePrice`, `totalInvested`, `currentValue`

Para correção em massa: `POST /api/v1/transactions/recalculate-all`.

---

## 📐 Padrão Visual

- **Tema**: dark por padrão, com tema claro disponível (`darkMode: 'class'`,
  alternado pelo `ThemeContext` e pelo botão sol/lua na barra lateral)
- **Background**: `#000000` (claro: `#f5f5f0`)
- **Surfaces**: `#111111` (claro: `#ffffff`)
- **Accent/Gold**: `#c9a84c`
- **Success**: `#38a169` · **Danger**: `#e53e3e`
- **Fonte**: Inter
- **Idioma da UI**: Português (Brasil)

---

## ⚠️ Pontos de Atenção

### Constraint unique em `strategies`
`unique(user_id, asset_type_id)`. No update é necessário `flush()` entre o
`deleteAll` e os novos `save`, senão dá erro 23505.

### Soma da estratégia semeada passa de 100%
A V18 semeia 30+30+30+30+5+5 = **130%**. A tela de Estratégia mostra esse total
como ideal. Decidir se é dado de exemplo a corrigir ou se a regra aceita >100%.

### `GOOGLE_CLIENT_ID` hardcoded no frontend
`primevest-web/src/main.tsx` tem `const GOOGLE_CLIENT_ID = "YOUR_GOOGLE_CLIENT_ID_HERE"`
com um TODO. O login Google pelo frontend não funciona até isso vir de
`import.meta.env.VITE_GOOGLE_CLIENT_ID`.

### `ddl-auto: validate` no perfil docker
Ligado depois de conferir as 12 entidades JPA coluna a coluna contra o schema
final das migrations: **nenhuma coluna mapeada está ausente**. As colunas
`created_at` acrescentadas em `portfolio_assets` e `strategies` não são mapeadas
por entidade alguma — o validador do Hibernate ignora coluna extra no banco.
Se a subida falhar, a mensagem nomeia tabela e coluna; voltar para `none` é uma linha.

### Precisão decimal: entidades alinhadas ao schema
As entidades declaravam `precision = 19, scale = 4` em 23 colunas cujo banco dizia
outra coisa. Foi corrigido **nas anotações**, nunca no banco — mexer no schema aqui
truncaria dados. Regra em vigor, e que vale para qualquer coluna decimal nova:

| Natureza | Tipo | Colunas |
|----------|------|---------|
| Quantidades e preços unitários | `numeric(18,8)` | `total_shares`, `shares`, `price_per_share`, `average_price`, `current_price`, `max_price`, `amount_per_share`, preços do histórico |
| Valores monetários agregados | `numeric(18,2)` | `total_invested`, `current_value`, `total_dividends`, `total_amount`, `brokerage_fee`, `target_amount`, `current_amount` |
| Taxa de indexador | `numeric(10,4)` | `indexer_rate` |
| Percentual da estratégia | `numeric(5,2)` | `target_percentage` (já estava correto) |

As **8 casas decimais das quantidades existem por causa de cripto** — alinhar para 4
truncaria fração de BTC. Por isso a entidade é que cedeu, não a migration.

Nota: `precision`/`scale` só afetam a geração de DDL. Em tempo de execução quem manda
é a escala da coluna, então o alinhamento não muda valores já gravados.

### Testes esvaziados
`AuthServiceTest.java` (35 bytes) e `StrategyServiceTest.java` (39 bytes) foram
zerados para evitar erro de compilação. `PortfolioServiceTest.java` e
`TransactionServiceTest.java` têm conteúdo real e servem de modelo de estilo.

### Dados mockados no frontend
Dois arquivos legados, a remover depois de conferir quem ainda importa:
- `src/mocks/data.ts` (~40 KB)
- `src/utils/assetTypes.ts` — mapa ticker→tipo hardcoded com ~35 ativos;
  essa informação deve vir do backend

### Encoding no PowerShell
PowerShell corrompe UTF-8. Para SQL com acentos, usar escape:
```sql
UPDATE asset_types SET description = E'Ações' WHERE id = 4;
```
Dentro do Docker isso não é problema.

---

## 🌿 Git

- **Repositório**: https://github.com/guilherme-dealmeida/primevest.git
- **Branch atual**: `TRACK_01-ajustes_crud_inicial`
- **Branches**: master, uat, develop, TRACK_01-ajustes_crud_inicial
- **Regra**: não comitar sem instrução explícita do usuário

### Commits recentes (TRACK_01)
1. `TRACK_01 - Ajuste CRUD de Transações` — autocomplete, tipo de ativo do backend, PUT
2. `TRACK_01 - Inserção dos principais ativos na tabela assets por categoria` — V19, 1.420 ativos
3. `TRACK_01 - Ajustes CRUD de Estratégia` — abas, pizza, add/remove tipos, description, recálculo

### Pendente de commit
- `TransactionService.java` — recálculo automático do portfólio
- `TransactionController.java` — endpoint `recalculate-all`
- `PortfolioService.java` — description no detalhe do portfólio
- `AssetService.java` — description nos DTOs de asset/category
- `TransactionsPage.tsx` — remoção do `typeDisplayName` hardcoded
- `docker-compose.yml`, Dockerfiles, `.dockerignore`, `.env.example`, `application-docker.yml`
- `application.yml` — remoção do `baseline-version: 18`
- `V5__create_portfolio_tables.sql`, `V7__create_strategy_table.sql` — coluna `created_at`
- `vite.config.ts` — proxy configurável
- `Asset.java`, `AssetPriceHistory.java`, `CeilingPrice.java`, `Dividend.java`,
  `Goal.java`, `Portfolio.java`, `PortfolioAsset.java`, `Transaction.java` — precisão decimal
- `Claude.md` — este arquivo
