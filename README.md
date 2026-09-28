# PrimeVest

> **Disciplina leva à liberdade.**

Sistema completo de gestão de carteira de investimentos da B3, com acompanhamento diário de ativos, gráficos de alocação, cálculo de rentabilidade e sugestões de rebalanceamento.

## 🏗️ Arquitetura

```
primevest/
├── pom.xml              # Backend: agregador Maven (Java 21 + Spring Boot 3.3)
├── primevest-core/      # Backend: modelo, regras compartilhadas, migrations
├── primevest-adm-api/   # Backend: rotinas de administrador — catálogo (porta 8081)
├── primevest-app-api/   # Backend: API do usuário, a carteira (porta 8080)
└── primevest-web/       # Frontend - React + TypeScript + Tailwind
```

## 🚀 Tecnologias

### Backend
- **Java 21** + **Spring Boot 3.3**
- **Spring Security** + **JWT** + **Google OAuth2**
- **Spring Data JPA** + **PostgreSQL**
- **Flyway** (migrations)
- **Redis** (cache de cotações)
- **WebClient** (integrações com BrAPI e CoinGecko)
- **SpringDoc OpenAPI** (Swagger)
- **Maven**

> Ainda **não há testes automatizados**: `src/test` está vazio e o build roda com
> `-Dmaven.test.skip=true`. O plano, quando a suíte nascer, é Testcontainers com
> PostgreSQL — metade do que importa aqui é comportamento de banco. Ver `PENDENCIAS.txt`.

### Frontend
- **React 18** + **TypeScript**
- **Vite** (build tool)
- **Tailwind CSS** (dark theme)
- **Recharts** (gráficos)
- **TanStack React Query** (data fetching)
- **React Router v6** (SPA routing)
- **ESLint 9** + **typescript-eslint** (`npm run lint`)
- **Google Identity Services** (login social por ID token, sem client secret)

## 📋 Funcionalidades

### Módulo Gestão de Carteira B3
- ✅ Catálogo de ativos já populado (Ações, FIIs, Tesouro, Renda Fixa, ETFs, BDRs, Cripto) — a transação seleciona do catálogo, não cria ativo novo
- ✅ Registro de compras e vendas com cálculo de preço médio
- ✅ Cálculo de rentabilidade por ativo e da carteira total — **renda variável apenas**; renda fixa ainda marca zero, por falta do cálculo de curva (ver `PENDENCIAS.txt`, item 2.1)
- ✅ Gráficos de alocação (pizza por tipo e por categoria)
- ✅ Estratégia de alocação com % ideal e sugestão de rebalanceamento
- ✅ Preço teto por ativo definido pelo usuário
- ✅ Acompanhamento de proventos (dividendos, JCP, rendimentos)
- ✅ Atualização automática de cotações (BrAPI + CoinGecko)
- ✅ Metas de investimento

### Autenticação
- ✅ Login via Google OAuth2
- ✅ Login via email/senha com JWT

## ⚙️ Setup

### Opção 1 — Docker (recomendada)

Sobe banco, cache, API e front de uma vez, com o schema construído do zero pelas
migrations. Não exige Java, Maven, Node nem PostgreSQL instalados na máquina.

```bash
cp .env.example .env
docker compose up -d --build
```

| Serviço | URL |
|---------|-----|
| Frontend (Vite, hot reload) | http://localhost:5173 |
| API | http://localhost:8080 |
| adm-api (rotinas do catálogo) | http://localhost:8081 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| PostgreSQL | localhost:**5433** |
| Redis | localhost:6379 |

> A porta do banco é 5433 de propósito, para não colidir com um PostgreSQL local
> na 5432.

### Opção 2 — Local, sem Docker

Pré-requisitos: **Java 21**, **Maven 3.9+**, **Node 24**, **PostgreSQL 17** (o
`NULLS NOT DISTINCT` usado na carteira exige 15+) com o banco `db_primevest`
criado, e Redis opcional para cache de cotações.

```bash
# Variáveis de ambiente
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export GOOGLE_CLIENT_ID=seu-client-id        # opcional; sem ele o botão do Google some

# Backend — porta 8080, da raiz
mvn -pl primevest-app-api -am -Drun.module=primevest-app-api spring-boot:run

# Frontend — porta 5173, proxy /api -> localhost:8080
cd primevest-web
npm install
npm run dev
npm run lint     # ESLint 9; hoje fecha em 0 erro
```

> Não existe `GOOGLE_CLIENT_SECRET`: o login usa o fluxo de ID token do Google
> Identity Services, que dispensa client secret.

Detalhes operacionais — schema, migrations, decisões de modelagem e armadilhas
conhecidas — estão em `Claude.md`. O que ficou por fazer está em `PENDENCIAS.txt`.

### Carga do catálogo num banco novo

As migrations criam o schema e o catálogo semeado (ativos, categorias, emissores, ligação
ativo → emissor, composição das units e o usuário administrador). O histórico — preços desde
2010, proventos, balanços da CVM, série trimestral e informe mensal de FII — entra pela
**carga inicial**, disparada à mão na adm-api (porta 8081). Ela não roda sozinha ao subir.

**Antes:**

1. **Pasta `data/`** na raiz do projeto (fora do git, ~2 GB): `cvm/`, `b3/cotahist/`,
   `b3/proventos/`, `fnet/` e `logos/`, copiada de quem já tem o banco. Com ela a carga lê
   quase tudo do disco; sem ela baixa ~2 GB da CVM e da B3, e os proventos de FII ficam só com
   os últimos 12 meses (os avisos antigos do FNET não são baixados automaticamente).
2. **`.env`** a partir do `.env.example` (banco e `JWT_SECRET`; o `BRAPI_TOKEN` só é preciso
   para o fechamento diário, não para a carga).
3. **Internet:** mesmo com `data/` cheia, os proventos dos últimos 12 meses de cada emissor vêm
   da B3 (uma chamada por segundo).
4. **Senha do administrador semeado** (`guilhermeunatec@gmail.com`, migration V9) — peça a quem
   mantém o projeto.

**Rodar:**

```bash
docker compose up -d --build        # as migrations rodam ao subir as APIs

# login na app-api (8080) e disparo na adm-api (8081)
TOKEN=$(curl -s http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"guilhermeunatec@gmail.com","password":"<senha>"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')
curl -X POST http://localhost:8081/api/v1/admin/bootstrap -H "Authorization: Bearer $TOKEN"

docker compose logs -f adm-api      # acompanhar; termina em "Carga inicial (...) concluida"
```

A carga roda, em ordem: emissores (completa o seed com as companhias e os FIIs negociados que não
estão no catálogo), COTAHIST, proventos, balanços com a série trimestral, e informe mensal de FII.
Com `data/` cheia leva **~50 min**. É retomável: se uma etapa falhar (a B3 às vezes cai), rode de
novo — o que já entrou é pulado.

**Depois:** o preço atual dos ativos vem do fechamento diário (19:00, ou na subida da adm-api se
estiver atrasado), que usa a BRAPI (`BRAPI_TOKEN`). Até lá, os indicadores usam o último
fechamento do COTAHIST. Carteiras e transações são de cada usuário e não fazem parte da carga.

## 🔌 APIs Externas

| API | Uso | Docs |
|-----|-----|------|
| **BrAPI** | Cotações B3 (Ações, FIIs, ETFs) | [brapi.dev](https://brapi.dev) |
| **CoinGecko** | Cotações Crypto | [coingecko.com](https://www.coingecko.com/en/api) |
| **Google OAuth** | Autenticação | [console.cloud.google.com](https://console.cloud.google.com) |

## 📊 Tipos de Ativos Suportados

| Tipo | Categorias |
|------|-----------|
| Renda Fixa | CDB, LCI, LCA, CRI, CRA, Debênture, Previdência |
| Tesouro Direto | SELIC, IPCA+, Prefixado |
| FII | Tijolo, Papel, Fundo de Fundos, Híbrido |
| Renda Variável | Banco, Commodity, Seguro, Energia, Financeiro, Telefonia, Saneamento, Tecnologia |
| Internacional | ETF, BDR |
| Crypto | Blockchain |

## 📝 Licença

Projeto privado.
