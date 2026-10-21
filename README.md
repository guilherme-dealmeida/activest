# PrimeVest

> **Disciplina leva à liberdade.**

Sistema completo de gestão de carteira de investimentos da B3, com acompanhamento diário de ativos, gráficos de alocação, cálculo de rentabilidade e sugestões de rebalanceamento.

## 🏗️ Arquitetura

```
primevest/
├── pom.xml              # Backend: agregador Maven (Java 21 + Spring Boot 3.3)
├── primevest-core/      # Backend: modelo, regras compartilhadas, migrations
├── primevest-integration/  # Backend: integrações (Binance, B3 Área do Investidor): conectores, credenciais, importação
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
- ✅ Cálculo de rentabilidade por ativo e da carteira total — renda variável pela cotação, **Tesouro Direto pelo preço
  oficial do Tesouro** (atualizado todo dia) e **renda fixa privada pela curva da taxa contratada** (% do CDI, CDI +
  spread, prefixado, IPCA + spread), valores brutos — ver `docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md`
- ✅ Venda de renda fixa pela Nova Transação (escolhendo o título) ou pela Carteira, com "Resgatar tudo"
- ✅ Venda de renda fixa pelo **valor líquido** (15/10/2026): o PrimeVest calcula o bruto, o IR (tabela regressiva) e o
  IOF aporte a aporte, com prévia antes de gravar; no Tesouro, IR e IOF por título; a Carteira mostra o líquido
  estimado de cada título — ver `docs/planos/PLANO_VENDA_RENDA_FIXA.md`
- ✅ Gráficos de alocação (pizza por tipo e por categoria)
- ✅ Estratégia de alocação com % ideal e tela própria de Rebalanceamento (sugestão de aporte)
- ✅ Preço Teto e Preço Ideal de ações pelo modelo PREÇO TETO V2 (10/10/2026): lucros dos 4 últimos trimestres
  da CVM, crescimento sustentável (ROE × (1 − payout)), 3 períodos + perpetuidade descontados pela Selic, margem de
  segurança; padrões do catálogo editáveis, alertas e comparação com o fechamento do dia e o
  preço médio — ver `docs/PRECO_TETO_METODOLOGIA.md` e `docs/planos/PLANO_PRECO_TETO.md`
- ✅ Acompanhamento de proventos (dividendos, JCP, rendimentos), com abas de histórico (recebidos) e a receber. O
  catálogo de proventos vem da B3 e do FNET (21/10/2026: sem parcelas duplicadas, recibos de subscrição, avisos
  retificados nem outra classe do fundo; conferência independente em `carga/scripts/conferencia/proventos.mjs` —
  `docs/planos/PLANO_CORRECAO_PROVENTOS.md`)
- ✅ Atualização automática de cotações (BrAPI + CoinGecko para a carteira; COTAHIST diário da B3 para todas as
  ações, FIIs, ETFs e BDRs do catálogo, às 21:30 e 08:30; histórico de cripto às 19:30)
- ✅ Índices de mercado desde 2010 — CDI, Selic, IPCA, IGP-M, INPC, TR, meta Selic, dólar PTAX, Ibovespa e IFIX —,
  atualizados todo dia às 10:00 (Banco Central e B3), com consulta da série e do acumulado no período
  (tela **Índices**: um card por índice com período e gráfico; `/api/v1/indices`). Base da futura valorização da
  renda fixa
- ✅ Ativos (tela "Emissoras" até 18/10/2026): **listagem do catálogo** (17/10/2026) em abas Ações e FIIs, com logo, nome, setor/segmento, os
  indicadores de cada tipo e o patrimônio líquido (ordem padrão), colunas ordenáveis, filtros por nome, setor e faixa
  de indicador, estado na URL e marca de dado antigo (`docs/planos/PLANO_LISTAGEM_EMISSORAS.md`); e a página de cada
  ativo, com cotação, indicadores com histórico, cadastro e demonstrativos (`docs/planos/PLANO_TELA_EMISSORES.md`)
- ✅ Rentabilidade (17/10/2026): a carteira contra CDI, Selic, IPCA, IPCA + x%, Ibovespa, IFIX, IVVB11 (proxy do
  S&P 500 em reais) e Bitcoin — TWR, % do CDI, rentabilidade real, retorno pessoal (XIRR), barras por período, tabela
  comparativa, contribuição por classe/categoria/ativo/emissora, mapa de calor, patrimônio nominal × real e risco
  (volatilidade, drawdown, Sharpe, Sortino); recorte por tipo, categoria, ativo, com/sem reserva, bruto/líquido. Lacuna
  de preço nunca é escondida: o período fica indisponível e a tela diz o ativo (`docs/RENTABILIDADE_METODOLOGIA.md`,
  `docs/planos/PLANO_RENTABILIDADE.md`)
- ✅ Dashboard (17/10/2026): cards Patrimônio (atual bruto, líquido e investido), Aportes (compras − vendas em 12
  meses e média mensal), Rentabilidade bruta (12 meses e desde o início, em % e R$) e Proventos (12 meses, totais e a
  receber), mais o gráfico de rentabilidade acumulada de 12 meses contra o CDI
- ✅ Metas de investimento
- ✅ Notificações (13/10/2026): caixa estilo e-mail pelo sino do cabeçalho — proventos pagos no dia, eventos societários,
  título vencido (aviso com o líquido estimado e a venda preenchida — desde 15/10/2026 o PrimeVest não liquida
  sozinho), bonificação sem custo ("Informar custo"), alerta de preço abaixo do ideal,
  "padrão mudou" do Preço Teto, integrações e, para o administrador, rotinas e catálogo (`docs/planos/PLANO_NOTIFICACOES.md`)
- ✅ Integrações: Binance (chave só de leitura) e B3 — Área do Investidor (CPF + autorização na B3): as negociações
  entram depois de você conferir; proventos e eventos da B3 são conferidos com a carteira. A B3 real depende de contrato
  (Pacote de Acesso); até lá, um simulador com os seus extratos (`docs/planos/PLANO_INTEGRACAO_B3.md`)

### Autenticação e conta
- ✅ Login via Google (Google Identity Services, ID token)
- ✅ Login via email/senha com JWT (token de acesso + renovação)
- ✅ Criar conta com código de verificação por e-mail e aceite dos termos (`/cadastro`)
- ✅ Esqueci a senha com código por e-mail
- ✅ Configurações: perfil (celular, CPF cifrado), troca de e-mail com código, senha, vínculo Google, tema, excluir conta
- No DEV, todo e-mail cai no **Mailpit** (http://localhost:8025); nada sai da máquina

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
export SIGNUP_ENABLED=true                   # liga o "Criar conta"
export MAIL_HOST=localhost MAIL_PORT=1025    # SMTP (ex.: um Mailpit local) para os códigos

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
ativo → emissor, composição das units, o cadastro dos índices de mercado e o usuário administrador).
O histórico — preços desde 2010, proventos (com data de pagamento), balanços da CVM, série trimestral,
informe mensal de FII e índices de mercado — entra pela **carga inicial**, disparada à mão na adm-api. Ela não roda sozinha ao subir.
Nada depende de pasta fora do projeto (`docs/planos/PLANO_CARGA_INICIAL.md`).

**Antes:**

1. **`.env`** a partir do `.env.example`: `PRIMEVEST_SENHA` (senha do administrador semeado,
   `guilhermeunatec@gmail.com`, migration V9 — peça a quem mantém o projeto) e o **token só
   leitura** do bucket R2 (`S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`).
2. **Pasta `carga/data/`** (fora do git, ~1,4 GB) baixada do bucket `primevest-data` (Cloudflare R2):
   `cvm/`, `b3/cotahist/`, `b3/proventos/`, `b3/proventos-12m/`, `b3/indices/`, `bcb/`, `fnet/`, `levantamento/`,
   `logos/`, `tesouro/`, `coingecko/` e `binance/` (histórico de cripto). Com ela a
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
estão no catálogo), emissoras e distribuidoras de renda fixa (BCB e CVM, completando o seed —
`docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md`), COTAHIST, proventos (FII pelo FNET, ações pela B3), datas de pagamento pelos
relatórios de proventos da CVM (2022+), datas de pagamento do levantamento 2010–2026
(`carga/data/levantamento/datas-pagamento.json`, só onde ainda falta data), balanços com a série
trimestral, informe mensal de FII, o preço oficial do Tesouro Direto desde 2010 (`tesouro/`) e os índices de mercado desde 2010 (CDI, Selic, IPCA, IGP-M, INPC,
TR, meta Selic, dólar PTAX, Ibovespa e IFIX — `docs/planos/PLANO_INDICES_MERCADO.md`; ~2 min sem os
arquivos de `bcb/` e `b3/indices/`, segundos com eles).
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

A integração com a B3 se testa aqui, contra o simulador da Área do Investidor (extratos em `carga/mocks/`, fora do git;
`B3I_*` no `.env.teste`):

```powershell
pvt --profile b3mock up -d --build
node carga/scripts/b3investidor/conferir.mjs --api http://localhost:8082 --mock http://localhost:8190 --gabarito http://localhost:8080
```

## 🔌 APIs Externas

| API | Uso | Docs |
|-----|-----|------|
| **BrAPI** | Cotações B3 (Ações, FIIs, ETFs) | [brapi.dev](https://brapi.dev) |
| **CoinGecko** | Cotações Crypto e histórico dos últimos 365 dias | [coingecko.com](https://www.coingecko.com/en/api) |
| **Binance (pública)** | Histórico diário BTC/BRL desde 13/10/2020 (klines, sem chave) — benchmark da Rentabilidade | [binance-docs.github.io](https://binance-docs.github.io/apidocs/spot/en/) |
| **Banco Central — SGS** | Índices: CDI, Selic, IPCA, IGP-M, INPC, TR, meta Selic, dólar PTAX (sem token) | [dadosabertos.bcb.gov.br](https://dadosabertos.bcb.gov.br) |
| **Site da B3** | Proventos, eventos societários, série histórica (COTAHIST) e os índices Ibovespa e IFIX | [b3.com.br](https://www.b3.com.br) |
| **Tesouro Transparente** | Preço e taxa diários dos títulos do Tesouro Direto (PU de resgate) | [tesourotransparente.gov.br](https://www.tesourotransparente.gov.br) |
| **Google OAuth** | Autenticação | [console.cloud.google.com](https://console.cloud.google.com) |
| **Binance** | Operações de cripto do usuário (chave só de leitura) | [binance-docs.github.io](https://binance-docs.github.io/apidocs/spot/en/) |
| **B3 — Área do Investidor** | Negociações, movimentações, posição e proventos do investidor (OAuth2 + mTLS; depende de contrato) | [developers.b3.com.br](https://developers.b3.com.br/apis/api-area-do-investidor) |

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
