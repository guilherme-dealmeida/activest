# PrimeVest

> **Disciplina leva à liberdade.**

Sistema completo de gestão de carteira de investimentos da B3, com acompanhamento diário de ativos, gráficos de alocação, cálculo de rentabilidade e sugestões de rebalanceamento.

## 🏗️ Arquitetura

```
primevest/
├── primevest-api/       # Backend - Java 21 + Spring Boot 3.3
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
- **JUnit 5** + **Mockito** (testes)
- **SpringDoc OpenAPI** (Swagger)
- **Maven**

### Frontend
- **React 18** + **TypeScript**
- **Vite** (build tool)
- **Tailwind CSS** (dark theme)
- **Recharts** (gráficos)
- **TanStack React Query** (data fetching)
- **React Router v6** (SPA routing)
- **Google OAuth** (@react-oauth/google)

## 📋 Funcionalidades

### Módulo Gestão de Carteira B3
- ✅ Cadastro de ativos (Ações, FIIs, Tesouro, Renda Fixa, ETFs, BDRs, Crypto)
- ✅ Registro de compras e vendas com cálculo de preço médio
- ✅ Cálculo de rentabilidade por ativo e da carteira total
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

### Pré-requisitos
- Java 21+
- Maven 3.9+
- Node.js 18+
- PostgreSQL 15+
- Redis (opcional, para cache)

### Backend

```bash
# Criar banco de dados
createdb primevest

# Configurar variáveis de ambiente
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret
export DB_USERNAME=postgres
export DB_PASSWORD=postgres

# Rodar
cd primevest-api
mvn spring-boot:run
```

A API estará disponível em `http://localhost:8080`
Swagger UI: `http://localhost:8080/swagger-ui.html`

### Frontend

```bash
cd primevest-web
npm install
npm run dev
```

O frontend estará disponível em `http://localhost:5173`

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
