# Activest — Plano de performance das telas (lentidão geral)

> Fase: **Encerrado em 02/11/2026** — conferido pelo Guilherme no navegador ("bem melhor") e declarado encerrado por
> ele, que faz as operações de git (branch `TRACK_01-*`, commit e PR para a `develop`).
> Origem: pedido do Guilherme em 02/11/2026 (ESCOPO "Performance Activest"): todas as telas ficaram lentas; o
> aumento da base explicaria um pouco, mas está lento demais para um sistema embrionário.
> Escopo anterior com o mesmo título: `PLANO_PERFORMANCE.md` (29/10/2026, listagens de Ativos e Preço Teto, encerrado).

---

## D. Discovery

### D.1 Como foi medido (pilha Docker do dev, 02/11/2026)

1. Endpoints da app-api chamados direto (`127.0.0.1:8080`) e pelo proxy do Vite (`127.0.0.1:5173`), em série e em
   paralelo, com o usuário do `.env`.
2. Telas abertas no navegador do app (`localhost:5173`), com a Resource Timing API: carga inicial do Dashboard e
   navegação entre Carteira, Transações, Dividendos, Rentabilidade, Metas e Dashboard.
3. Banco (`pg_stat_user_tables`, `pg_statio`), CPU/memória dos containers e da VM do Docker, velocidade do
   sistema de arquivos montado (bind mount do `E:\repos\activest`).

Nada foi gravado no banco nem nos containers.

### D.2 Medições

**Backend — está rápido.** Respostas aquecidas, direto na app-api:

| Chamadas do Dashboard (9) | Em série | Em paralelo |
|---|---|---|
| tempo de cada uma | 7 – 136 ms | 15 – 148 ms |
| total das 9 | — | ~0,7 s (incluindo o `curl`) |

Outras telas: dividendos ~40 ms, transações ~47 ms, rentabilidade 30 – 50 ms por cartão, metas ~200 ms, Ativos (Ações)
~340 ms. Só a **1ª chamada depois de o cache esfriar** pesa: Ativos/Ações 2,9 s, Ativos/FIIs 1,3 s, Preço Teto
1,2 s, `/portfolio/` 1,1 s.

**Pelo front — fica 5 a 40 vezes mais lento.** As mesmas 9 chamadas do Dashboard em paralelo pelo proxy do Vite:
**600 – 970 ms cada** (contra 15 – 148 ms direto). Uma chamada trivial (`/assets/categories`, 2 bytes): 7 ms direto,
75 – 240 ms pelo proxy. O `index.html` do Vite sozinho: 350 – 480 ms.

**No navegador:**

| Momento | Tempo |
|---|---|
| Carga inicial do Dashboard (F5) até o DOM pronto | **5,5 s** — antes de a 1ª chamada à API sair |
| Módulos baixados nessa carga | **186** arquivos `.ts/.tsx` servidos um a um pelo Vite, ~2,5 s cada no pico |
| Navegação entre telas (módulos já carregados) | Dividendos 0,5 s · Metas 0,7 s · Dashboard 0,8 s · Rentabilidade 1,1 s · Transações 1,3 s · Carteira 1,8 s |

### D.3 Achados

**Causa 1 (principal): o ambiente de desenvolvimento passa o tempo todo varrendo o disco montado do Windows, que é
muito lento.** O projeto está em `E:\` e entra nos containers por bind mount. Nesse caminho cada consulta a um
arquivo custa **~2 – 3 ms** (medido: `find` em 633 arquivos do backend levou 1,1 – 1,9 s). Três vigias fazem
polling nesse disco sem parar:

| Quem | O que varre | Frequência |
|---|---|---|
| `dev-watch.sh` na app-api | ~633 arquivos (core, app-api, integration) | a cada 2 s — e a varredura leva ~1,5 s: o disco nunca descansa |
| `dev-watch.sh` na adm-api | ~626 arquivos (core, adm-api) | a cada 2 s |
| Vite (`CHOKIDAR_USEPOLLING=true`) | ~230 arquivos do front | a cada 100 ms (padrão do chokidar) |

Efeitos medidos: o processo do Vite consome **~25% de CPU parado** (1.420 minutos de CPU em 4 dias); a VM do Docker
fica com *load average* ~5,7 com 93% de CPU ocioso — processos parados esperando o disco (estado `D`). O Vite é um
processo Node de uma linha só: ocupado com o polling, demora para servir cada módulo e para repassar cada chamada da
API. Isso explica por que **todas** as telas pioraram ao mesmo tempo, inclusive as que leem pouca coisa.

Por que piorou agora: o número de arquivos vigiados cresceu com os últimos escopos (hoje ~1.500 nos três vigias) e o
custo do polling cresce junto; o container do front está no ar há 5 dias sem reiniciar.

**Causa 2: o front carrega o sistema inteiro na primeira tela.** `App.tsx` importa as 26 páginas de forma direta
(sem `React.lazy`): abrir o Dashboard baixa também Tarefas, Moedas, Configurações, Metas... (186 módulos). Em
desenvolvimento cada módulo é uma requisição ao Vite lento (causa 1); em produção vira um pacote único maior que o
necessário.

**Causa 3 (menor): chamadas repetidas na abertura.** Na carga do Dashboard: `/users/me` 4 vezes, `/funds/labels`
2 vezes, `/auth/refresh` 1 vez. Na Carteira, os logos saem um por requisição (12 chamadas de 40 – 1.150 ms, pelo
proxy lento).

**Causa 4 (menor): banco com a configuração padrão para uma base de 2 GB.** `asset_price_history` tem 8,5 milhões de
linhas (1,6 GB, puxada pelo histórico dos internacionais), mas o PostgreSQL roda com `shared_buffers = 128 MB` e
`work_mem = 4 MB` (padrões). Acerto de cache 94%. Não pesa hoje nas chamadas aquecidas, mas é o que deixa a 1ª chamada
fria em segundos (Ativos 2,9 s).

**Não é causa:** a quantidade de consultas e o tempo do backend nas telas (já corrigidos no escopo de 29/10 e
conferidos de novo aqui); memória da VM (7,6 GB, ~3 GB em cache de arquivo, sem troca).

> Observação de medição: `curl localhost:...` no Windows soma ~210 ms por conexão (tenta `::1` antes de `127.0.0.1`,
> já citado no `docker-compose.yml`). As medições acima usam `127.0.0.1`.

### D.4 Decisões para o Guilherme (Discovery)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| D1 | Como o front deve rodar no dia a dia? | (a) Vite no Docker, polling a cada 1 s em vez de 100 ms e ignorando `dist/`; (b) Vite direto no Windows (`npm run dev`, eventos de arquivo nativos, sem polling), o resto no Docker; (c) front buildado (nginx, estágio `runtime` que já existe) por padrão e o Vite só quando for mexer no front | (a) agora, por ser o menor passo; (b) como alternativa documentada |
| D2 | Hot reload do backend (`dev-watch.sh`) | (a) varredura a cada 10 s em vez de 2 s; (b) chave `DEV_WATCH` no `.env` para ligar/desligar (desligado = reinicia o container depois de mexer no Java); (c) as duas | (c) |
| D3 | Carregar as páginas sob demanda (`React.lazy` por rota) | sim / não | sim |
| D4 | Tirar as chamadas repetidas da abertura (`/users/me`, `/funds/labels`, `/auth/refresh`) | sim / não | sim |
| D5 | Ajustar o PostgreSQL do compose (`shared_buffers` 512 MB, `effective_cache_size` 2 GB, `work_mem` 16 MB) | sim / não | sim |
| D6 | 1ª chamada fria (Ativos 2,9 s, Preço Teto 1,2 s) | (a) só registrar em PENDENCIAS; (b) investigar neste escopo | (a), reavaliar depois de D1–D5 |
| D7 | Mover o projeto para dentro do WSL (ex.: `\\wsl$\...`), onde o disco dos containers é rápido | entra no escopo como orientação / fica fora | fora por ora (muda o caminho `E:\repos\activest` usado em tudo); reavaliar se D1–D2 não bastarem |

Critério de sucesso proposto (para o Planning): carga inicial do Dashboard abaixo de 2 s e navegação entre telas
abaixo de 0,5 s no dev, com o mesmo JSON das APIs.

### D.5 Decisões tomadas

- **D7** (02/11/2026): fora do escopo — o projeto continua em `E:\repos\activest`. Motivo do Guilherme: a migração
  agiliza o sistema local, mas tira o projeto do ambiente de desenvolvimento em que se trabalha a maior parte do
  tempo; e no servidor tudo já roda em container, sem relação com o ambiente local.
- **D1** (02/11/2026): Vite continua no Docker com polling, mas a cada **10 s** (hoje 100 ms). Consequência aceita:
  mudança no front pode levar até ~10 s para aparecer.
- **D2** (02/11/2026): variável **`DEV_WATCH`** no `.env` (documentada no `.env.example`), com **`false`** como
  padrão: o `dev-watch.sh` não varre `src/`. Hoje o Guilherme só mexe no front; as mudanças de backend são feitas pelo
  Claude, que aciona a recompilação/reinício da API depois de alterar Java (na validação de cada escopo).
  `DEV_WATCH=true` volta ao comportamento automático.
- **D3–D6** (02/11/2026): pela recomendação, aprovadas explicitamente pelo Guilherme ("seguir com suas
  recomendações"): D3 páginas sob demanda (`React.lazy`); D4 tirar as chamadas repetidas; D5 ajustar o PostgreSQL do
  compose; D6 1ª chamada fria só registrada em PENDENCIAS. Mandou seguir para o Impact.

---

## I. Impact

### I.1 Componentes afetados

| Decisão | Arquivo / componente | Mudança | Exige |
|---|---|---|---|
| D1 | `activest-web/vite.config.ts` | `watch.interval` e `binaryInterval` = 10 000 ms; `dist/` em `watch.ignored` | reiniciar o container `web` |
| D2 | `dev-watch.sh`, `docker-compose.yml` (app-api e adm-api), `.env.example` | variável `DEV_WATCH` (padrão `false`): sem ela o laço de varredura não sobe; a compilação da subida continua | **rebuild** das imagens `app-api` e `adm-api` (o script é copiado na imagem pelo `Dockerfile`) |
| D3 | `activest-web/src/App.tsx` | as 20 páginas internas com `React.lazy` + `Suspense`; login, cadastro, senha e termos continuam diretos (são a porta de entrada) | nada além do front |
| D4 | — | ver I.2: não há chamada repetida em produção | — |
| D5 | `docker-compose.yml` (serviço `db`) | `command: postgres -c shared_buffers=512MB -c effective_cache_size=2GB -c work_mem=16MB` | recriar o container `db` (o volume `activest_pgdata` fica; as APIs reconectam sozinhas) |
| D6 | `docs/PENDENCIAS.md` | item novo: 1ª chamada fria de Ativos (2,9 s) e Preço Teto (1,2 s) | — |
| docs | `Claude.md`, `README.md`, `docs/REGRAS_CLAUDE.md` (ver I3) | hot reload novo, `DEV_WATCH`, parâmetros do banco | — |

Nenhuma API, regra de negócio, migration ou integração é alterada; o JSON das telas não muda.

### I.2 Achado do Impact que muda a D4

Rastreando as chamadas repetidas da abertura:

- **`/users/me` 2× e `/funds/labels` 2×**: é o `React.StrictMode` (`main.tsx`), que **só em desenvolvimento** roda os
  efeitos duas vezes de propósito, para revelar efeito mal escrito. Em produção sai uma chamada de cada.
- **`/auth/refresh` + mais 2 `/users/me`**: o token de acesso estava vencido; o interceptor (`api/client.ts`) renovou
  **uma vez só** e repetiu as chamadas recusadas. É o comportamento certo.
- **Logos da Carteira um por um**: a API devolve `Cache-Control: max-age=604800` (7 dias); o navegador só busca na
  primeira vez. Lento hoje só porque passa pelo Vite lento (causa 1).

Ou seja, a D4 não tem o que corrigir em produção; tirar o `StrictMode` esconderia defeitos sem ganho real.

### I.3 Riscos e efeitos colaterais

| Risco | Efeito | Tratamento |
|---|---|---|
| D1: mudança no front leva até ~10 s para aparecer | aceito na D1 | — |
| D2: alterar Java não recompila sozinho | API segue com o código antigo até o reload | o Claude aciona o reload depois de mexer no backend (I2); `DEV_WATCH=true` volta ao automático |
| D3: 1ª abertura de cada página baixa o pedaço dela | breve "Carregando..." na primeira visita de cada tela | fallback no mesmo estilo do `ProtectedRoute` |
| D3: depois de um deploy novo, aba antiga pede um pedaço que não existe mais | tela não abre até o F5 (só em produção) | ver I4 |
| D5: +384 MB de memória para o banco | a VM do Docker tem 7,6 GB, ~3 GB em cache de arquivo | folga suficiente; `work_mem` 16 MB × ~20 conexões ≈ 320 MB no pior caso |
| D5: recriar o `db` | ~10 s fora do ar no dev | sem perda de dados (volume preservado) |

### I.4 Decisões do Impact (para o Guilherme)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| I1 | D4 diante de I.2 | (a) sai da implementação, só registrar o achado; (b) manter e desduplicar mesmo assim | (a) |
| I2 | Como o Claude aciona o reload do backend com `DEV_WATCH=false` | (a) modo `reload` no próprio `dev-watch.sh` (`docker exec activest-app-api dev-watch.sh reload`: compila o módulo e toca o gatilho do DevTools, segundos); (b) reiniciar o container (recompila tudo do zero, mais lento) | (a), com (b) quando houver arquivo apagado/renomeado (limitação já documentada no script) |
| I3 | Registrar em `docs/REGRAS_CLAUDE.md` (seção 5) o combinado: backend sem hot reload, o Claude faz o reload depois de alterar Java | sim / não | sim |
| I4 | Proteção para o pedaço antigo após deploy (D3) | (a) se o carregamento de uma página falhar, recarregar a página uma vez sozinho; (b) não tratar agora (registrar em PENDENCIAS, vale só para produção) | (a) — poucas linhas, evita tela travada |

### I.5 Decisões tomadas

- **I1–I4** (02/11/2026): todas pela recomendação, aprovadas explicitamente pelo Guilherme ("concordo com todas
  recomendações"): I1 a D4 sai da implementação (só o achado fica registrado); I2 modo `reload` no `dev-watch.sh`,
  reinício do container quando houver arquivo apagado/renomeado; I3 combinado registrado em `REGRAS_CLAUDE.md`,
  seção 5; I4 página que falha ao carregar recarrega sozinha uma vez. Mandou seguir para o Planning.

---

## P. Planning

### P.1 Solução

1. **Front com polling a cada 10 s (D1).** `vite.config.ts`: `server.watch` com `usePolling` (como hoje),
   `interval: 10000`, `binaryInterval: 10000` e `ignored: ['**/dist/**']`. Sem polling (Vite fora do Docker), nada muda.
2. **Backend sem varredura por padrão (D2, I2).** `dev-watch.sh`:
   - lê `DEV_WATCH` (padrão `false`); só com `true` sobe o laço de varredura (mantém `POLL_SECONDS`);
   - novo modo `dev-watch.sh reload`: `mvn -B -q -pl $MODULE -am compile` e, se compilar, toca o gatilho do DevTools
     (a mesma rotina do laço, extraída para uma função); compilação com erro mantém a versão no ar e sai com código ≠ 0;
   - a subida não muda (limpa `target/` e compila).
   `docker-compose.yml`: `DEV_WATCH: ${DEV_WATCH:-false}` na app-api e na adm-api. `.env.example`: a variável com
   explicação. Uso: `docker exec activest-app-api dev-watch.sh reload` (idem `activest-adm-api`).
3. **Páginas sob demanda (D3, I4).** `App.tsx`: as 21 páginas das rotas protegidas por `React.lazy` (as páginas têm
   export nomeado: `lazy(() => import('...').then(m => ({ default: m.X })))`), com um `Suspense` em volta das rotas
   protegidas e o mesmo "Carregando..." do `ProtectedRoute`. Login, cadastro, senha e termos continuam diretos.
   Função `lazyPage` com a I4: se o `import` falhar, recarrega a página uma vez (marca em `sessionStorage`, em
   try/catch, para não entrar em laço); na segunda falha, deixa o erro aparecer.
4. **PostgreSQL (D5).** Serviço `db` do compose: `command: ["postgres", "-c", "shared_buffers=512MB", "-c",
   "effective_cache_size=2GB", "-c", "work_mem=16MB"]`, com comentário do motivo.
5. **Documentação.** `docs/PENDENCIAS.md` (D6 e o achado da D4), `docs/REGRAS_CLAUDE.md` 5.x (I3), `Claude.md`
   (hot reload, `DEV_WATCH`, banco, páginas sob demanda), `README.md` (como subir/recarregar) e o andamento deste plano.

### P.2 Critérios de aceitação (dev, medidos como no Discovery)

| # | Critério | Antes |
|---|---|---|
| A1 | Carga inicial do Dashboard (F5) até o DOM pronto **< 2 s** | 5,5 s |
| A2 | Navegação entre telas já visitadas **< 0,5 s** (Dashboard, Carteira, Transações, Dividendos, Rentabilidade, Metas) | 0,5 – 1,8 s |
| A3 | Chamada trivial pelo proxy do Vite **< 30 ms** | 75 – 240 ms |
| A4 | CPU do Vite parado **< 5%**; nenhum `find` do `dev-watch.sh` rodando com `DEV_WATCH=false` | ~25% / contínuo |
| A5 | Mudança em `.tsx` aparece no navegador em até ~10 s, sem reiniciar nada | — |
| A6 | `dev-watch.sh reload` recompila e a API volta com o código novo; com erro de compilação, a versão anterior continua no ar | — |
| A7 | Banco com `shared_buffers = 512MB` e os dados intactos (contagem de `asset_price_history`, `transactions` e `users` igual antes e depois) | — |
| A8 | JSON de `/portfolio/`, `/portfolio/assets`, `/dividends/`, `/transactions/` e `/performance/summary` idêntico antes e depois | — |
| A9 | `npm run lint`, `npm test` e `npm run build` verdes; build gera um pedaço por página | — |
| A10 | Todas as rotas abrem (inclusive Tarefas e Moedas como admin, e o redirecionamento das URLs antigas), nos dois temas | — |

### P.3 Etapas do plano

| # | Etapa | Conteúdo | Validação |
|---|---|---|---|
| E0 | Referência | guardar os JSON de A8 e as contagens de A7; repetir as medições de D.2 | — |
| E1 | Backend: `DEV_WATCH` + `reload` | `dev-watch.sh`, compose, `.env.example`; rebuild de app-api e adm-api | A4 (parte do backend), A6 |
| E2 | Front: polling 10 s | `vite.config.ts`; reiniciar o `web` | A3, A4, A5 |
| E3 | Front: páginas sob demanda | `App.tsx` (`lazyPage` com I4) | A9, A10 |
| E4 | Banco | `command` do `db`; recriar o container | A7 |
| E5 | Medição final | repetir D.2; comparar com E0 | A1, A2, A8 |
| E6 | Documentação | PENDENCIAS, REGRAS 5.x, Claude.md, README, andamento | — |

Ordem pensada para medir o efeito de cada causa separadamente (backend → polling → lazy → banco).

### P.4 Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| E0–E6 (código, rebuild, recriar containers, medições, docs) | executa | — |
| Conferência no navegador ("caminho 3") | prepara | confere a fluidez das telas e aprova |
| Commit / PR (`TRACK_01-*` → `develop`) | sugere a mensagem (etapa 7) | executa |

### P.5 Andamento

- 02/11/2026 — Discovery, Impact e Planning concluídos; aguardando a ordem de execução.
- 02/11/2026 — Guilherme mandou executar. E0–E4 e E6 feitas:
  - **E0**: JSON de referência e contagens guardados fora do repositório.
  - **E1**: `dev-watch.sh` com `DEV_WATCH` (padrão `false`) e modo `reload`; compose e `.env.example`; rebuild das
    duas APIs. A6 conferido: reload com mudança real recompila e o DevTools reinicia em ~5 s; com erro de compilação
    sai com código 1 e a API segue no ar (teste com `AuthController.java` alterado e restaurado byte a byte). Sem
    `.class` alterado o DevTools não reinicia — o gatilho sozinho não basta (documentado).
  - **E2**: polling do Vite a 10 s, `dist/` ignorado; container `web` reiniciado.
  - **E3**: 20 páginas por `lazyPage` (`utils/lazyPage.ts`, com a I4) e `Suspense` no `Layout`. Lint, `tsc`, 156
    testes e build verdes; o build gera um pedaço por página (`DashboardPage` 8 KB, `IssuerPage` 60 KB...).
  - **E4**: `db` com `shared_buffers=512MB`, `effective_cache_size=2GB`, `work_mem=16MB` (conferido no `SHOW`);
    contagens intactas (`transactions` 124, `users` 1, `assets` 75.522; `asset_price_history` +2 da rotina diária).
  - **E6**: Claude.md ("Hot reload" e última revisão), README, REGRAS 5.8, PENDENCIAS 45.
- **E5 (parcial)**, medido com `127.0.0.1`:

| Critério | Antes | Depois |
|---|---|---|
| A3 chamada trivial pelo proxy do Vite | 75 – 240 ms | **13 – 15 ms** (1ª: 239 ms, conexão nova) |
| 9 chamadas do Dashboard em paralelo pelo proxy | 600 – 970 ms cada | **15 – 162 ms** cada |
| A4 CPU do Vite parado | ~25% | **~2,6%**; nenhum `find` do `dev-watch` |
| *Load average* da VM do Docker | ~5,7 | ~1,0 |
| A8 JSON | — | `dividends` e `transactions` idênticos; `portfolio`, `portfolio/assets` e `performance/summary` mudaram só pela cotação de BTC/USDC gravada pela rotina às 16:01 (entre as duas fotos; nenhum código do backend foi alterado) |

  A1/A2 (navegador) aguardam o login do Guilherme no navegador do app.
- **E5 (navegador do app, 02/11/2026, depois do login do Guilherme)**:

| Critério | Antes | Depois |
|---|---|---|
| A1 Dashboard (F5) até o DOM pronto | 5,5 s (186 módulos) | **0,59 s** (69 módulos); dados completos em 1,46 s |
| A2 navegação, tela já visitada | 0,5 – 1,8 s | **0,06 – 0,79 s** — abaixo de 0,5 s em 8 de 12 telas (Rebalanceamento 0,06, Estratégia 0,36, Alocação 0,40, Dividendos 0,41, Transações 0,45, Carteira 0,38–0,48; Dashboard 0,55–0,76, Rentabilidade 0,75, Índices 0,79, Ativos 0,72) |
| A2 primeira visita da tela (baixa o pedaço) | — | 0,7 – 3,2 s no dev (Vite transforma os módulos na 1ª vez) |

  Nas telas acima de 0,5 s o servidor responde em 35–134 ms; o resto é renderização no navegador em modo de
  desenvolvimento (PENDENCIAS 45.5). A meta A2 fica **parcialmente** atingida no dev.
- **A10**: as 19 rotas internas abriram com conteúdo (inclusive `/assets/PETR4`, Tarefas e Moedas como admin); URL
  antiga `/rentabilidade` → `/performance`; Carteira e Dividendos conferidas nos dois temas, tema escuro restaurado.
  Os 500 do console eram do proxy durante o rebuild da API (15:59–16:01); nenhum erro na app-api.
- **Review (02/11/2026)**: `ignored` do Vite soma-se aos padrões (`node_modules`, `.git`), confirmado pela CPU do Vite;
  `lazyPage` não entra em laço (marca em `sessionStorage`, limpa no sucesso; sem `sessionStorage`, não recarrega);
  `dev-watch.sh reload` com `DEV_WATCH=true` pode compilar junto com o laço (caso raro, aceito). Achado fora do escopo:
  a sessão cai quando a API reinicia (PENDENCIAS 45.4).
- 02/11/2026 — Guilherme conferiu no navegador ("bem melhor") e declarou o escopo encerrado; ele faz as operações de git.
  Mensagem de commit sugerida: `TRACK_01 - Performance do ambiente de dev: DEV_WATCH, polling do Vite a 10 s, telas sob
  demanda e memoria do PostgreSQL`.

