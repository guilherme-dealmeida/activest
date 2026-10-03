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

> **Testes**: JUnit 5, Mockito, AssertJ e Testcontainers (PostgreSQL 17), nos três módulos — unitários
> (`*Test`, Surefire) e de integração (`*IT`, Failsafe). `mvn clean test` roda os unitários;
> `mvn clean verify` roda tudo e precisa do Docker Desktop. Detalhes em `Claude.md` (Pontos de Atenção → Testes).

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
- ✅ Cálculo de rentabilidade por ativo e da carteira total — **renda variável apenas**; renda fixa ainda marca zero, por falta do cálculo de curva (ver `docs/PENDENCIAS.md`, item 2.1)
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
conhecidas — estão em `Claude.md`. O que ficou por fazer está em `docs/PENDENCIAS.md`.

### Carga do catálogo num banco novo

As migrations criam o schema e o catálogo semeado (ativos, categorias, emissores, ligação
ativo → emissor, composição das units e o usuário administrador). O histórico — preços desde
2010, proventos (com data de pagamento), balanços da CVM, série trimestral e informe mensal de
FII — entra pela **carga inicial**, disparada à mão na adm-api. Ela não roda sozinha ao subir.
Nada depende de pasta fora do projeto (`docs/planos/PLANO_CARGA_INICIAL.md`).

**Antes:**

1. **`.env`** a partir do `.env.example`: `PRIMEVEST_SENHA` (senha do administrador semeado,
   `guilhermeunatec@gmail.com`, migration V9 — peça a quem mantém o projeto) e o **token só
   leitura** do bucket R2 (`S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`).
2. **Pasta `carga/data/`** (fora do git, ~1,4 GB) baixada do bucket `primevest-data` (Cloudflare R2):
   `cvm/`, `b3/cotahist/`, `b3/proventos/`, `b3/proventos-12m/`, `fnet/`, `levantamento/` e `logos/`. Com ela a
   carga lê quase tudo do disco; sem ela baixa ~2 GB da CVM e da B3, e perde o que as fontes não
   devolvem mais (avisos antigos do FNET, datas de pagamento que já saíram da janela da B3 e as
   datas levantadas de 2010–2026, que só existem em `levantamento/`).
3. **Internet:** mesmo com `carga/data/` cheia, os proventos dos últimos 12 meses de cada emissor vêm
   da B3 (uma chamada por segundo).

**Rodar** (só Docker; os scripts rodam pelos contêineres do profile `carga`):

```bash
docker compose --profile carga run --rm data pull           # bucket -> carga/data/ (--minimo: só o essencial)
docker compose up -d --build                                # as migrations rodam ao subir as APIs
docker compose --profile carga run --rm tools node carga/scripts/admin.mjs bootstrap
docker compose logs -f adm-api      # acompanhar; termina em "Carga inicial (...) concluida"
```

A carga roda, em ordem: emissores (completa o seed com as companhias e os FIIs negociados que não
estão no catálogo), COTAHIST, proventos (FII pelo FNET, ações pela B3), datas de pagamento pelos
relatórios de proventos da CVM (2022+), datas de pagamento do levantamento 2010–2026
(`carga/data/levantamento/datas-pagamento.json`, só onde ainda falta data), balanços com a série
trimestral, e informe mensal de FII.
Com `carga/data/` cheia leva **~1 h**. É retomável: se uma etapa falhar (a B3 às vezes cai), rode de
novo — o que já entrou é pulado. Conferência: `carga/scripts/sql/contagens.sql` e
`carga/scripts/sql/datas_pagamento.sql` (ver `carga/scripts/README.md`).

**Depois:** o preço atual dos ativos vem do fechamento diário (19:00, ou na subida da adm-api se
estiver atrasado), que usa a BRAPI (`BRAPI_TOKEN`). Até lá, os indicadores usam o último
fechamento do COTAHIST. Carteiras e transações são de cada usuário e não fazem parte da carga.

**Quem mantém o bucket** (chave de escrita no `.env`): as rotinas gravam o que chega das fontes em
`carga/data/`; `docker compose --profile carga run --rm data push` sobe o que é novo (nunca apaga) e
regrava os manifests. `data status` compara `carga/data/` com o bucket.

### Pilha de teste (`primevest-teste`)

Para provar a carga do zero na mesma máquina, sem tocar no ambiente de desenvolvimento: os mesmos serviços, com
banco, Redis, rede e pasta de dados (`carga/data-teste/`) próprios e rotinas agendadas desligadas. Precisa do
`.env.teste` (cópia do `.env.example` com as portas do teste e a `PRIMEVEST_SENHA`; fora do git).

```powershell
function pvt { docker compose -p primevest-teste --env-file .env.teste -f docker-compose.yml -f docker-compose.teste.yml @args }
pvt --profile carga run --rm data pull
pvt up -d --build
pvt --profile carga run --rm tools node carga/scripts/admin.mjs bootstrap
```

Frontend em http://localhost:5174, APIs em 8082 e 8083, PostgreSQL em localhost:5434, Redis em 6380.
Detalhes em `Claude.md` e `docs/planos/PLANO_PRIMEVEST_TESTE.md`.

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
