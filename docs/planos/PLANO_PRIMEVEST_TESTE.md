# PLANO_PRIMEVEST_TESTE.md — Pilha `primevest-teste` com a carga inicial completa

> **Escopo:** Criar container com toda carga inicial do PrimeVest · **Etapa:** Execução — **E1 a E6 concluídas em 03/10/2026**
> (planejado e executado no mesmo dia; andamento na seção 7)
> **Objetivo:** uma segunda pilha Docker, `primevest-teste`, com os mesmos 5 serviços do `primevest` (db, redis, app-api,
> adm-api, web), em portas próprias, com banco, Redis, rede e pasta de dados próprios, e o catálogo carregado do zero
> pelo caminho de máquina nova do README: bucket R2 → pasta de dados → migrations → `bootstrap`.
> A pilha `primevest` atual não é tocada.
> Execução autorizada pelo Gabriel em 03/10/2026 (E1 a E6 em sequência).

**Antecedente:** o `docs/planos/PLANO_CARGA_INICIAL.md` (v6, D7) teve uma pilha `primevest-test`, removida em 30/09 com a
decisão de fazer a prova de máquina nova em outra máquina. Este plano volta a fazê-la **nesta** máquina, a pedido.

---

## 1. Diagnóstico — o que o compose atual permite e o que impede

| Ponto | Hoje no `docker-compose.yml` | Para uma segunda pilha |
|---|---|---|
| Nome do projeto Compose | não fixado → `primevest` (nome da pasta) | `-p primevest-teste` separa rede (`primevest-teste_default`), volumes (`primevest-teste_pgdata`, `primevest-teste_maven-repo`) e imagens |
| `container_name` | **fixo** nos 5 serviços (`primevest-db`, `primevest-redis`, `primevest-app-api`, `primevest-adm-api`, `primevest-web`) | conflito de nome: precisa ser sobrescrito |
| Portas no host | já por variável: `DB_PORT`, `REDIS_PORT`, `API_PORT`, `ADM_API_PORT`, `WEB_PORT` | basta um arquivo de variáveis próprio |
| Contêiner → contêiner | pelo nome do serviço (`db:5432`, `redis:6379`, `app-api:8080`, `adm-api:8081`) | não muda: cada pilha tem a sua rede |
| Front → API | `/api/v1` relativo; o proxy do Vite repassa para `app-api:8080` | sem CORS: o `CorsConfig` não muda |
| Pasta de dados | bind mount `./carga/data:/app/data` (adm-api, leitura e escrita), `./carga/data/logos:/app/data/logos:ro` (app-api), `./carga/data:/data` (serviço `data`) | sem mudança, as duas pilhas gravam na mesma `carga/data` — D1 |
| Código | `pom.xml` e `src` por bind mount + `dev-watch.sh` | as duas pilhas rodam o mesmo código; salvar um `.java` reinicia as duas (risco R2) |
| Rotinas agendadas | ligadas (`primevest.scheduling.enabled`, padrão `true`), inclusive a recuperação do fechamento na subida da adm-api | duas pilhas chamando B3, BRAPI e CVM nos mesmos horários — D3 |
| `.gitignore` | `.env`, `.env.local`, `.env.*.local`, `/carga/data/` | `.env.teste` e `carga/data-teste/` **não** estão cobertos |
| `.dockerignore` | `.env.*`, `carga/data/` | `carga/data-teste/` entraria no contexto de build do backend (~2 GB a cada build) |

Conferido na máquina em 03/10/2026 (só leitura): Docker Compose v5.5.1; pilha `primevest` no ar com os 5 contêineres;
nenhuma pilha `primevest-test(e)` sobrando (contêiner, volume ou rede); as portas propostas na 2.1 estão livres e fora das
faixas reservadas do WinNAT/Hyper-V; `carga/data` com **2,08 GB** (o README fala em ~1,4 GB: cresceu com os relatórios da
CVM e o levantamento); disco **E: 9,3 GB livres**; disco C: (volumes do Docker) 46,8 GB livres; `primevest_pgdata` 1,6 GB.

---

## 2. Desenho

### 2.1 Portas (D4)

Muda só o lado do host. Dentro da rede de cada pilha as portas continuam as mesmas.

| Serviço | `primevest` | `primevest-teste` | Variável |
|---|---|---|---|
| db (PostgreSQL) | 5433 | **5434** | `DB_PORT` |
| redis | 6379 | **6380** | `REDIS_PORT` |
| web (Vite) | 5173 | **5174** | `WEB_PORT` |
| app-api | 8080 | **8082** | `API_PORT` |
| adm-api | 8081 | **8083** | `ADM_API_PORT` |

URLs do teste: http://localhost:5174 (tela), http://localhost:8082/swagger-ui.html e http://localhost:8083/swagger-ui.html,
pgAdmin em `localhost:5434`.

### 2.2 Arquivos

O `docker-compose.yml` **não muda**. A pilha de teste é o mesmo arquivo + um override + um arquivo de variáveis.

| Arquivo | Versionado | Conteúdo |
|---|---|---|
| `docker-compose.teste.yml` (novo, raiz) | sim (sem segredo) | override: `container_name` `primevest-teste-*`; pasta de dados (D1); `PRIMEVEST_SCHEDULING_ENABLED` (D3) |
| `.env.teste` (novo, raiz) | **não** | cópia do **`.env.example`** (D2/D6: traz o token só leitura do R2, `S3_ENDPOINT` e `PRIMEVEST_SENHA`) com as portas da 2.1. Nada vem do `.env`. Gerado por script, sem imprimir valores. O resto fica como no `.env.example`: `GOOGLE_CLIENT_ID` vazio (R4), `BRAPI_TOKEN` vazio (plano gratuito), `PRIMEVEST_CREDENTIALS_KEY` vazia (integrações com corretoras indisponíveis no teste), `JWT_SECRET` padrão |
| `.gitignore` | sim | + `.env.teste` e `/carga/data-teste/` |
| `.dockerignore` | sim | + `carga/data-teste/` |
| `carga/data-teste/` (D1) | não | nasce vazia; enche no `data pull` |

Esboço do override (com D1 = a, D3 = a):

```yaml
# Pilha primevest-teste (docs/planos/PLANO_PRIMEVEST_TESTE.md). Sempre com:
#   docker compose -p primevest-teste --env-file .env.teste -f docker-compose.yml -f docker-compose.teste.yml ...
services:
  db:
    container_name: primevest-teste-db
  redis:
    container_name: primevest-teste-redis
  app-api:
    container_name: primevest-teste-app-api
    environment:
      PRIMEVEST_SCHEDULING_ENABLED: "false"
    volumes:
      - ./carga/data-teste/logos:/app/data/logos:ro
  adm-api:
    container_name: primevest-teste-adm-api
    environment:
      PRIMEVEST_SCHEDULING_ENABLED: "false"
    volumes:
      - ./carga/data-teste:/app/data
  web:
    container_name: primevest-teste-web
  data:
    volumes:
      - ./carga/data-teste:/data
```

No merge do Compose, um volume do override com o mesmo destino no contêiner (`/app/data`, `/app/data/logos`, `/data`)
**substitui** o do arquivo base; os demais (`src`, `pom.xml`, `maven-repo`) ficam. Isso é conferido com `config` antes de
qualquer `up` (E1).

Atalho só da sessão do PowerShell, na raiz (`E:\repos\primevest`):

```powershell
function pvt { docker compose -p primevest-teste --env-file .env.teste -f docker-compose.yml -f docker-compose.teste.yml @args }
```

Com `--env-file` o Compose **não** lê o `.env`; sem ele, leria as portas do dev e colidiria. Todo comando da pilha de
teste passa por `pvt` (R1).

### 2.3 Carga — a ordem do README, apontada para o teste

| # | Comando | O que faz |
|---|---|---|
| 1 | `pvt --profile carga run --rm data pull` | bucket `primevest-data` → `carga/data-teste/` (rclone; D5) |
| 2 | `pvt up -d --build` | sobe os 5 contêineres; app-api e adm-api rodam o Flyway (V1–V39) num banco vazio |
| 3 | `pvt --profile carga run --rm tools node carga/scripts/admin.mjs bootstrap` | login na app-api, `POST /api/v1/admin/bootstrap` na adm-api; volta 202 na hora e a carga roda em segundo plano |
| 4 | `docker logs -f primevest-teste-adm-api` | acompanhar até "Carga inicial (...) concluida" (~1 h com a pasta cheia) |

A carga roda, em ordem: emissores, COTAHIST, proventos (FII pelo FNET, ações pela B3), datas de pagamento da CVM,
datas do levantamento, balanços com a série trimestral e informe mensal de FII. É retomável: etapa que falha (a B3 às vezes
cai) se completa rodando o passo 3 de novo. O preço atual fica no último fechamento do COTAHIST até o primeiro fechamento
pela BRAPI (que, com D3 = a, não roda sozinho).

### 2.4 Validação

- `pvt ps`: 5 contêineres no ar, db e redis `healthy`; `docker ps`: a pilha `primevest` com o mesmo tempo de atividade de antes.
- Banco do teste na versão 39 (`flyway_schema_history`).
- Login em http://localhost:5174; Swagger nas portas 8082 e 8083.
- `carga/scripts/sql/contagens.sql` e `datas_pagamento.sql` nos dois bancos, lado a lado. Referência
  (`PLANO_CARGA_INICIAL.md`, seção 4): `cvm_documents` 19.349, `company_quarters` 19.344, `asset_stock_events` 1.053,
  emissores 1.198, `fii_monthly` 42.815, `asset_cash_events` ≈ dev (± a janela de 12 meses da B3), nenhum evento `EXTRATO_B3`.
  No PowerShell o `<` não existe e o pipe corrompe acento, então pelo `cmd`:
  `cmd /c "docker exec -i primevest-teste-db psql -U postgres -d db_primevest < carga\scripts\sql\contagens.sql"`.
- `carga/data` do dev sem arquivo novo: o teste grava só em `carga/data-teste`.

---

## 3. Decisões — respondidas em 03/10/2026

| # | Pergunta | Resposta |
|---|---|---|
| D1 | Onde fica a pasta de dados do teste? | **`carga/data-teste/`** no projeto, com as linhas no `.gitignore` e no `.dockerignore` (~2 GB do E:, sobram ~7 GB) |
| D2 | Qual credencial do R2? | **o token só leitura, que está no `.env.example`** |
| D3 | Rotinas agendadas no teste? | **desligadas** (`PRIMEVEST_SCHEDULING_ENABLED=false`). Ligar depois = tirar a variável do override e `pvt up -d` |
| D4 | Portas | **as da 2.1** |
| D5 | `data pull` completo ou `--minimo`? | **completo** |
| D6 | Origem do `.env.teste` | **criado a partir do `.env.example`**, usando o `S3_ACCESS_KEY`/`S3_SECRET_KEY` de lá (2.2) |
| D7 | Quem roda o `data pull` e espera o `bootstrap` | **o Claude**: a regra 5.3 não vale para a conta do Gabriel — registrado como regra 5.7 em `docs/REGRAS_CLAUDE.md` |
| D8 | Depois da E5, a pilha de teste fica no ar? | **`pvt stop` ao fim do plano** (mantém banco e dados; `pvt start` volta). A pilha `primevest` foi parada pelo Gabriel em 03/10 e continua parada durante o plano |
| D9 | `PRIMEVEST_SENHA` no `.env.example` | **reverter o `.env.example` depois de criar o `.env.teste`**: a senha fica só no `.env.teste` (fora do git). As linhas alteradas desde o último commit são só `PRIMEVEST_SENHA` e `S3_ENDPOINT` (conferido com `git diff`, só leitura); as duas voltam a ficar vazias por edição do arquivo, sem `git checkout` (regra 3.1), e `git diff` confirma que o arquivo ficou igual ao commit. O token só leitura do R2 já está no commit e fica |
| D10 | A E5 compara contagens teste × dev, e a pilha do dev está parada | **(a)** (03/10/2026): subir só o banco do dev (`docker start primevest-db`, sem APIs nem rotinas), rodar os SQL e parar de novo (`docker stop primevest-db`). A outra opção era comparar só com os números do `PLANO_CARGA_INICIAL.md` (seção 4, de 29–30/09) |

<details><summary>Opções que estavam na v1</summary>

D1: (a) `carga/data-teste/`; (b) fora do repositório; (c) a mesma `carga/data` do dev. D2: (a) token só leitura; (b) chave de
escrita do `.env`. D3: (a) desligadas; (b) ligadas, dobrando as chamadas à B3 e à BRAPI e cruzando a rodada das 20:00 com o
`bootstrap`. D5: (a) completo; (b) `--minimo`, que faz o `bootstrap` baixar COTAHIST e balanços pela internet.
</details>

---

## 4. Etapas e quem faz

| Etapa | Entrega | Quem |
|---|---|---|
| **E1** | `docker-compose.teste.yml`, `.env.teste` (script copia o `.env.example` e troca as portas, sem imprimir valores) e, em seguida, o `.env.example` revertido (D9), `carga/data-teste/`, linhas no `.gitignore` e no `.dockerignore`; `pvt config` confere nomes, portas e volumes **antes** de subir; `pvt --profile carga run --rm data status` confere que o token só leitura lista o bucket antes de baixar 2 GB | Claude |
| **E2** | `pvt --profile carga run --rm data pull`; conferir o tamanho de `carga/data-teste` contra o bucket (`data status`) | Claude (regra 5.7) |
| **E3** | `pvt up -d --build`; 5 contêineres, Flyway V39, tela e Swagger nas portas novas; pilha `primevest` continua parada e com os volumes intactos | Claude |
| **E4** | `bootstrap` e acompanhamento do log até "concluida" (~1 h) | Claude (regra 5.7) |
| **E5** | conferência da 2.4 (contagens e datas de pagamento, teste × dev, conforme D10) | Claude |
| **E6** | `Claude.md` (seção da pilha de teste), `README.md` e andamento deste plano; por fim `pvt stop` (D8) | Claude |

**Desfazer:** `pvt down -v` apaga contêineres, rede e volumes **só** do teste; `carga/data-teste/`, `.env.teste` e o override
saem à mão (ou pelo Claude, com a permissão de apagar).

---

## 5. Riscos

| # | Risco | Cuidado |
|---|---|---|
| R1 | Um comando sem `-p primevest-teste --env-file .env.teste` atinge a pilha do dev — `docker compose down -v` na raiz apaga o **banco do dev** | todo comando do teste passa pelo `pvt` |
| R2 | Salvar um `.java` do `primevest-core` ou da `primevest-adm-api` durante o `bootstrap` faz o `dev-watch` recompilar e o DevTools reiniciar a adm-api do teste; a thread da carga morre junto | não mexer no backend durante a carga; se acontecer, rodar o passo 3 de novo (retomável). Congelar o teste com o alvo `runtime` mudaria a estrutura pedida e esbarra no achado 6.2 — **03/10: não haverá edição de código até o fim do plano** |
| R3 | Memória: o Docker tem 7,43 GB; o dev usa ~2,1 GB e o teste usa o mesmo, mais o pico da adm-api na carga (COTAHIST, PDFs da CVM) | acompanhar com `docker stats` durante a E4 — **03/10: a pilha `primevest` está parada, o teste fica sozinho** |
| R4 | Login pelo Google: `http://localhost:5174` não está nas "Origens JavaScript autorizadas" do Client ID | `GOOGLE_CLIENT_ID` vazio no `.env.teste`: o botão some e o login é por e-mail e senha |
| R5 | `restart: unless-stopped` vale também para o teste: ele volta sozinho quando o Docker Desktop abre | `pvt stop` quando não estiver em uso |

---

## 6. Achados fora do escopo

| # | Achado | Decisão (03/10/2026) | Situação |
|---|---|---|---|
| 1 | `.env.example` com valor em `S3_ACCESS_KEY` e `S3_SECRET_KEY` (o token só leitura), contra a regra 6.5 | fica como está: é a base do `.env.teste` (D6) | mantido |
| 2 | Estágio `runtime` do `primevest-web/Dockerfile` com `proxy_pass http://api:8080`; o serviço se chama `app-api` desde a separação (`PLANO_ADM_API.md`) | alterar | ✅ 03/10: `proxy_pass http://app-api:8080` |
| 3 | `README.md` dizia que não há testes automatizados; há 54 classes `*Test` e 24 `*IT` nos três módulos | alterar (é falso) | ✅ 03/10: nota de testes reescrita, apontando para o `Claude.md` |

---

## 7. Andamento

| Data | O que |
|---|---|
| 03/10/2026 | v1 do plano; D1–D5 aguardando resposta |
| 03/10/2026 | v2: D1–D7 respondidas; regra 5.7 em `docs/REGRAS_CLAUDE.md`; achados 2 e 3 corrigidos. **E1–E6 aguardam a ordem de execução** |
| 03/10/2026 | v3: `S3_ENDPOINT` e `PRIMEVEST_SENHA` preenchidos no `.env.example` — o `.env.teste` sai inteiro dele; D8 e D9 abertas; `data status` antes do `pull` (E1) |
| 03/10/2026 | v4: D8 e D9 respondidas; dev parado pelo Gabriel; nenhuma edição de código até o fim do plano (R2 resolvido); D10 aberta |
| 03/10/2026 | v5: D10 = (a); **ordem de execução de E1 a E6 em sequência** |
| 03/10/2026 | E1 ✅ `docker-compose.teste.yml`, `.env.teste` (do `.env.example`, portas da 2.1), `carga/data-teste/`, `/carga/data-teste/` no `.gitignore` (o `.env.teste` já estava lá) e no `.dockerignore`; `config` com nomes `primevest-teste-*`, portas 5434/6380/8082/8083/5174, dados só em `carga/data-teste` e rotinas desligadas; `data status` lista o bucket com o token só leitura (manifest de 02/10: 28.986 arquivos, ~2,2 GB); `.env.example` igual ao último commit (`git diff`) |
| 03/10/2026 | E2 ✅ `data pull` completo em ~7,5 min (04:32–04:40 UTC); `data status` sem diferença: b3 978, cvm 3.491, fnet 23.064, levantamento 2, logos 1.451 arquivos |
| 03/10/2026 | E3 ✅ imagens `primevest-teste-*` (build do cache do dev) e `up -d`: 5 contêineres no ar (db e redis `healthy`), Flyway do zero até a **V39** sem falha, adm-api e app-api `Started`; http://localhost:5174 200, Swagger 8082 e 8083 200; login pelo front (5174 → proxy → app-api) ok, 12 tipos de ativo; nenhuma rotina na subida (recuperação do fechamento não rodou); dev parado com `primevest_pgdata` intacto |
| 03/10/2026 | E4 ✅ `bootstrap` disparado às 04:45 UTC (202) e concluído em **2.949 s (~49 min)**, todas as etapas ok: emissores (482 FIIs criados, 12 falhas de classificação da B3), COTAHIST 2010–2026 (2.233.606 pregões; o zip de 2026 foi baixado de novo da B3), proventos (653 emissores, 0 falhas, 54.676 eventos em dinheiro, 1.053 societários), datas da CVM (3.421 relatórios, 113 ilegíveis, 4.258 datas), levantamento (17.197 datas gravadas, 299 a definir), balanços (19.349 documentos), informe de FII (42.720 meses, 827 fundos). Avisos: busca no FNET sem resposta para CXRI11 e VILG11 (a B3 ficou de reserva) e 2 de fonte do PDFBox; nenhum ERROR |
| 03/10/2026 | E5 ✅ (D10 = a: só o `primevest-db` subiu e foi parado de novo). `contagens.sql` teste × dev: **iguais** em `assets` 2.625, `ligados` 1.139, `unit_compositions` 36, `asset_stock_events` 1.053, `cvm_documents` 19.349, `company_quarters` 19.344, `share_counts REVISAR` 107, `cash sem tipo` 0. Diferenças, todas explicadas: `issuers` 1.197 × 1.198 e `fii_monthly` 42.720 × 42.815 — um emissor só do dev, o FII BFC (CNPJ 31.145.875/0001-05, criado em 27/09, 95 meses de informe, nenhum ativo ligado), que o `bootstrap` não recria (PENDENCIAS 21.1); `asset_price_history` +4.773 = COTAHIST até 02/10 no teste (+4.902; o dev parou em 25/09) − 121 linhas da BRAPI e 8 da CoinGecko que só os fechamentos do dev gravaram; `asset_cash_events` 54.676 × 54.698 (−22), diferenças só em 2025–2026 (janela de 12 meses: B3 −87, B3_HISTORICO +16, FNET +49 — o teste buscou avisos mais novos). `datas_pagamento.sql`: 2010–2024 iguais em eventos e sem data; 2025–2026 de ação com −22 eventos e +6 sem data (mesma janela) |
| 03/10/2026 | E6 ✅ `Claude.md` (seção "Pilha de teste" em Como rodar, árvore e revisão), `README.md` (subseção da pilha de teste), `docs/PENDENCIAS.md` (seção 21: emissor BFC, 12 falhas de classificação, FNET sem resposta para CXRI11 e VILG11). Validação do achado 2: imagem `runtime` do front construída (`tsc -b` + `vite build` ok), rodando na rede do teste: `nginx -t` ok, `GET /` 200 e `GET /api/v1/assets/types` 401 vindo da app-api pelo `proxy_pass http://app-api:8080` (imagem de verificação removida). Pilha de teste parada com `pvt stop` (D8); dev continua parado |
