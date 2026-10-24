# PrimeVest → Activest — Plano de Renomeação do Projeto

> Fase: **Testing / Review (23/10/2026)** — E0–E8 implementadas e conferidas pelo Claude em 23/10/2026. Faltam a
> conferência no navegador e o recadastro da Binance (D20) pelo Guilherme, e o pull request da branch
> `TRACK-01_novo_nome_activest`.

---

## 1. Objetivo

O domínio `www.primevest.com.br` foi adquirido por outra empresa. Depois de avaliar domínio e marca, o projeto passa a
se chamar **Activest**. Este plano mede o tamanho da troca (o que muda, o que fica, riscos e custo) e propõe como
fazê-la de uma vez, sem perder o banco do DEV nem as credenciais cifradas.

## 2. Discovery — o que existe hoje

### 2.1 A marca já começou a mudar

- `primevest-web/index.html`: `<title>Activest | Disciplina leva à Liberdade</title>`.
- `Sidebar.tsx`: logo aberto `activest1.png` / `activest2.png`, com `alt="Activest — Disciplina leva à liberdade"`;
  o logo **recolhido** ainda usa `gv2.png` / `v1.png`.
- O resto do sistema (tela, e-mails, termos, código, configuração, infraestrutura, documentação) segue PrimeVest.

### 2.2 Inventário (arquivos versionados: 1.081)

| Onde | Quantidade |
|---|---|
| Arquivos com "primevest" no conteúdo | **751** |
| Ocorrências de "primevest" (qualquer caixa) | **3.833** |
| Arquivos com "primevest" no caminho | **1.007** (as 5 pastas de módulo + `docs/PRIMEVEST_DB.md` + `docs/planos/PLANO_PRIMEVEST_TESTE.md`) |
| Classes Java em `com/primevest` | **648** (100% do Java), em 8 árvores `src/{main,test}/java/com/primevest` |
| Linhas `package`/`import com.primevest` | 2.277 |
| Outras ocorrências no Java | 522 (FQCN em JPQL, `@ComponentScan`/`scanBasePackages`, `@Value`, textos, e-mails de teste) |
| "PrimeVest" em texto lido pelo usuário | 34 no front (`src/`), 72 no Java de produção (e-mails, mensagens de erro, notificações) |
| Chaves de configuração `primevest.*` distintas | 75 (yml + `@Value`/`@ConditionalOnProperty`) |
| Testes com e-mail `@primevest.test` | 45 arquivos |
| Documentação (`Claude.md`, `README.md`, `docs/`) | 38 arquivos, 667 ocorrências |
| Migrations (V1–V67) | 5 arquivos com "PrimeVest" **só em comentário** |

Por camada:

| Camada | Itens com o nome |
|---|---|
| Maven | `groupId com.primevest`, `primevest-parent`, `primevest-core`, `primevest-integration`, `primevest-adm-api`, `primevest-app-api`; `<name>PrimeVest</name>` |
| Java | pacote `com.primevest`; `PrimevestApplication`, `PrimevestAdmApplication`; `spring.application.name` |
| Front | `package.json` (`primevest-web`); localStorage `primevest_token`, `primevest_refresh_token`, `primevest-theme`, `primevest-sidebar-collapsed`, `primevest-rentabilidade-benchmarks` (antiga); evento `primevest:terms-required`; campo `primevest` no JSON da divergência das integrações |
| Ambiente (`.env`) | `PRIMEVEST_PASSWORD`, `PRIMEVEST_EMAIL`, `PRIMEVEST_HASH_KEY`, `PRIMEVEST_CREDENTIALS_KEY`, `_KEY_VERSION`, `_PREVIOUS_KEYS`, `PRIMEVEST_SCHEDULING_ENABLED`, `PRIMEVEST_INTEGRATIONS_WORKER_ENABLED`, `PRIMEVEST_API_URL`, `PRIMEVEST_ADM_URL`; `POSTGRES_DB=db_primevest`; `S3_BUCKET=primevest-data`; `MAIL_FROM=PrimeVest <nao-responda@primevest.local>`; `B3I_CLIENT_ID=primevest-teste`; default do `JWT_SECRET` |
| Redis | canais `primevest:catalog:assets`, `primevest:dividends:on-demand`, canal das tarefas; prefixo `primevest:auth:`; cache com `GenericJackson2JsonRedisSerializer` (grava `@class` com o FQCN `com.primevest...`) |
| Docker | projeto compose `primevest` (vem do **nome da pasta**), volumes `primevest_pgdata` e `primevest_maven-repo`, contêineres `primevest-db/-redis/-app-api/-adm-api/-web/-mailpit/-b3-investidor-mock`, usuário `primevest` no `Dockerfile`, caminhos `./primevest-*` no compose, `Dockerfile`, `dev-watch.sh`, `.claude/build.ps1` |
| Pilha de teste | `primevest-teste` (`-p`, `.env.teste`, `db_primevest_test`) |
| Carga | `carga/scripts` (`admin.mjs`, conferências: `primevest-db`, `db_primevest`, `PRIMEVEST_*`), simulador da B3 |
| HTTP de saída | `User-Agent: PrimeVest/1.0 (+https://github.com/guilherme-dealmeida/primevest)` |
| Banco (dados) | 4 notificações (`body`), 1 `payload` e 1 log de tarefa com o texto "PrimeVest"; banco `db_primevest` com 1.066 MB |
| Fora do repositório | pasta `E:\repos\primevest` (e `primevest-backups`), repositório GitHub `primevest`, bucket R2 `primevest-data` (2,2 GB, 30.994 arquivos), tela de consentimento do Client ID do Google, `.idea`, pasta conectada no Cowork e a memória do Claude |

Itens verificados que **não** dependem do nome: a cifra das credenciais usa como AAD o id da conexão (não o nome do
sistema); o JWT não carrega o nome; o Flyway guarda só o nome dos scripts.

## 3. Impact — riscos e efeitos colaterais

Revisado em 23/10/2026 com as respostas de D1–D14.

| # | Risco | Situação depois das decisões |
|---|---|---|
| R1 | Migrations aplicadas não podem ser editadas (checksum do Flyway) | **Deixa de existir**: banco novo, migrations novas consolidadas (seção 4.2). As antigas continuam intactas na pasta `primevest` |
| R2 | Volume do Postgres ligado ao nome do projeto compose | **Resolvido pela D5**: projeto `activest` com volume próprio; `primevest_pgdata` fica parado, como backup |
| R3 | Banco `db_primevest` | **Resolvido pela D4**: `db_activest` nasce vazio |
| R4 | Cache do Redis com `@class com.primevest...` | **Deixa de existir**: Redis novo (contêiner `activest-redis`) |
| R5 | Chaves do localStorage | **Não se aplica (D8)**: banco novo exige login de novo; as chaves já nascem `activest_*` |
| R6 | Chave-mestra das credenciais | Banco novo não tem credencial cifrada. **Efeito:** as conexões de integração (Binance, B3) precisam ser **cadastradas de novo** no Activest |
| R7 | FQCN dentro de strings (JPQL, `@ComponentScan`, `scanBasePackages`) | Igual: substituição textual + subida das duas APIs + bateria completa |
| R8 | Conflito com escopo paralelo | **Não se aplica**: sem escopos paralelos até o fim deste (Guilherme, 23/10/2026) |
| R9 | Caminhos fora do repositório | Pasta nova; a antiga fica. A pasta nova precisa ser **conectada nesta sessão do Cowork** (pedido de acesso) |
| R10 | Bucket R2 não renomeia | **D6 (a)**: bucket novo `activest-data` com cópia completa; o antigo o Guilherme apaga depois |
| R11 | Documentação histórica | **D9 (a)**: docs vivos trocam; planos encerrados ganham a nota |
| **R12** | **Perda dos dados do usuário** | Aceita pelo Guilherme: lançamentos manuais de PETR4 e o preço de KLBN4 não vêm para o Activest. Ficam consultáveis no `primevest_pgdata` |
| **R13** | **Consolidação das migrations produz banco diferente do que as 67 produziam** | Coluna, constraint, índice, default ou linha de seed esquecidos quebram a subida (`ddl-auto: validate`) ou, pior, mudam dado em silêncio. Mitigação: comparação mecânica entre os dois bancos (seção 4.2, critério 3) |
| **R14** | **Recarga do catálogo do zero** | Carga completa (~47 min no ensaio de 28/09, hoje mais: proventos corrigidos, índices, Tesouro, tarefas). É também a prova real de "subir do zero" que estava prevista para outra máquina (`PLANO_CARGA_INICIAL.md`) |
| **R15** | Scripts que geram seed | `carga/scripts/gerar_seed_emissores.mjs` e `gerar_seed_emissoras_rf.mjs` escrevem a V30 e a V55: passam a gerar os arquivos novos |
| **R16** | Duas pilhas na mesma máquina | Portas iguais (5433, 6379, 8080, 8081, 5173, 8025): a pilha `primevest` só sobe com a `activest` parada, ou só o banco antigo em outra porta (D19) |

Desempenho e segurança: sem efeito. Comportamento funcional: nenhum — além do banco novo, a troca é de nome.

## 4. Solução

### 4.1 Renomeação (D1 = c: marca, configuração e estrutura)

| Nível | O que entra |
|---|---|
| **N1 — Marca visível** | textos de tela, e-mails, termos de uso, mensagens de erro e notificação, `MAIL_FROM`, `User-Agent`, `<name>`/`<description>` do pom, slogan **"Disciplina leva à liberdade"** (D3). **Fora:** título, logos e `public/images` — com o Guilherme (D2, D11) |
| **N2 — Configuração e ambiente** | `PRIMEVEST_*` → `ACTIVEST_*` (D7, troca direta), chaves `primevest.*` → `activest.*`, canais e prefixos do Redis, chaves do localStorage e evento do front, `package.json`, `spring.application.name`, projeto compose `activest` (`name:` fixo no compose), contêineres `activest-*`, volume `activest_pgdata`, banco `db_activest`, usuário do Dockerfile, pilha de teste `activest-teste`, e-mails de teste `@activest.test`, bucket `activest-data`, scripts de carga |
| **N3 — Estrutura** | pastas `activest-core`, `activest-integration`, `activest-adm-api`, `activest-app-api`, `activest-web`; pacote `com.activest` (648 classes); `groupId com.activest`, `activest-parent`; `ActivestApplication`, `ActivestAdmApplication`; caminhos no Dockerfile, compose, `dev-watch.sh`, `.claude/build.ps1`, `.dockerignore` |

Domínio e e-mail (D12): `activest.com.br`; remetente `Activest <contato@activest.com.br>` (no DEV o envio continua indo
para o Mailpit); `User-Agent: Activest/1.0 (+https://activest.com.br)`.

### 4.2 Migrations consolidadas (pedido do Guilherme, 23/10/2026)

As 67 migrations viram um conjunto novo em que **cada tabela nasce pronta**: um `CREATE TABLE` com as colunas,
defaults, constraints e índices finais (sem `ALTER` posterior), e cada seed é um `INSERT` com os valores finais (sem
`UPDATE`/`DELETE` corrigindo depois). Migrations só de correção de dado de banco existente (ex.: V60, limpeza de
proventos; V35, V38) deixam de existir, porque o banco novo não tem o dado que elas corrigiam.

**Fonte da verdade (D16):** o banco que as **V1–V67 produzem num banco vazio** — e não o banco do DEV, que tem dados
de integração (ativos do catálogo, emissoras do BCB/CVM, proventos) misturados com o seed.

**Método:**

1. Subir um Postgres descartável, rodar V1–V67 da pasta `primevest` e tirar o retrato: estrutura (`pg_dump
   --schema-only`) e dados de cada tabela que tem seed.
2. Escrever as migrations novas a partir desse retrato e da história das 67 (comentários de cada tabela mantidos: por
   que a coluna existe, qual plano a criou).
3. Rodar as novas noutro banco vazio e comparar com o retrato: mesmas tabelas, colunas (tipo, nulidade, default),
   constraints, índices, sequências e **as mesmas linhas** em cada tabela de seed. Diferença só se for decidida.

**Layout proposto (D15):**

| Arquivo | Conteúdo (origem) |
|---|---|
| `V1__schema.sql` | todas as tabelas, constraints e índices finais (V1, V10, V11, V20–V29, V31–V54, V56–V59, V61, V63–V67) |
| `V2__seed_reference.sql` | tipos de ativo, categorias, benchmarks, índices de mercado, tabelas de IR/IOF, tipos de tarefa e tarefas (V2, V3, V16, V23, V43, V52, V57, V62–V65) |
| `V3__seed_issuers.sql` | emissores (V30) — antes dos ativos, que referenciam o emissor (ajuste na implementação) |
| `V4__seed_assets.sql` | catálogo de ativos já com nomes, situação, emissor e ISIN finais, parâmetros do Tesouro, histórico de preço semeado, units, IR/IOF da RF e benchmarks (V4–V8, V13–V19, V29, V30, V52, V57) |
| `V5__seed_fixed_income_issuers.sql` | emissoras/distribuidoras de RF e grupos (V54, V55 — 1,7 MB) |
| `V6__seed_dev_user.sql` | usuário administrador e a reserva da estratégia (V9, V12) |

Nomes de arquivo em inglês (regra 9.1, nome novo nasce em inglês). A regra 8.1 continua valendo para as migrations que
vierem **depois** desta consolidação.

## 5. Critérios de aceitação

1. Em `E:\repos\activest`, `git grep -i primevest` só encontra os planos encerrados (com a nota de D9) e os registros
   históricos do `Claude.md` (seção de commits de 19/09 e afins).
2. `mvn clean verify` da raiz verde; `tsc -b`, lint e build do front verdes.
3. **Comparação das migrations (seção 4.2) sem diferença não decidida.**
4. `docker compose up -d` sobe a pilha `activest` (contêineres `activest-*`, banco `db_activest`, volume
   `activest_pgdata`) com o Flyway criando V1–V6 do zero; adm-api e app-api `Started`.
5. Carga inicial completa a partir do bucket `activest-data`, sem pasta fora do projeto, com contagens das tabelas de
   catálogo conferidas contra o `primevest_pgdata` (diferença só a esperada: dados de usuário e o que a fonte publicou
   depois).
6. Login (senha e Google) com o administrador semeado; rotinas disparadas por `carga/scripts/admin.mjs` com
   `ACTIVEST_PASSWORD`; canais do Redis entregando mensagem entre as duas APIs.
7. E-mail de código no Mailpit com remetente e textos Activest; termos de uso com Activest.
8. Nenhuma tela mostra "PrimeVest".
9. A pilha `primevest` parada, com `primevest_pgdata` intacto.

## 6. Decisões

### 6.1 Respondidas (Guilherme, 23/10/2026)

| # | Pergunta | Resposta |
|---|---|---|
| D1 | Profundidade | **(c)** marca + configuração + estrutura |
| D2 | Grafia, título e logo | O Guilherme cuida do título e do logo |
| D3 | Slogan | **"Disciplina leva à liberdade"** |
| D4 | Nome do banco | **Banco novo** (`db_activest`), do zero, com migrations consolidadas e recarga; perdem-se os lançamentos manuais de PETR4 e o preço de KLBN4 |
| D5 | Projeto compose e volume | **Contêineres novos**; a pilha antiga fica desativada como backup para conferência |
| D6 | Bucket R2 | **(a)** bucket novo `activest-data`; o antigo o Guilherme apaga depois |
| D7 | Variáveis de ambiente | **(a)** troca direta |
| D8 | localStorage | Não se aplica (banco novo, login de novo) |
| D9 | Documentação | **(a)** docs vivos trocam; planos encerrados com nota |
| D10 | Textos gravados no banco | Não se aplica (banco novo) |
| D11 | `public/images` | O Guilherme cuida |
| D12 | Domínio e e-mail | `activest.com.br`; e-mail `contato@activest.com.br` |
| D13 | Momento | Sem escopos paralelos até o fim deste |
| D14 | Pasta | Criar `E:\repos\activest` e **manter** `E:\repos\primevest` |

Respondidas na segunda rodada (Guilherme, 23/10/2026):

| # | Pergunta | Resposta |
|---|---|---|
| D15 | Layout e ordem das colunas das migrations novas | **(a)**, com a consolidação da seção 4.2 ("completamente de acordo") |
| D16 | Fonte da verdade da consolidação | **(a)** o que V1–V67 produzem num banco vazio |
| D17 | Repositório no GitHub | **(a)** repositório novo `activest` — criado por ele (`https://github.com/guilherme-dealmeida/activest.git`), hoje vazio |
| D20 | Integrações no banco novo | Recadastrar a Binance (e a B3 mock) no Testing |

Riscos R12–R16: o Guilherme considera aceitáveis — são dados de desenvolvimento, com o `primevest_pgdata` e a pasta
antiga para conferir; disputa de portas só aconteceria por erro e é reversível.

### 6.2 Abertas

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D18** | Como nasce `E:\repos\activest` sem perder o histórico do git (hoje: 86 commits, 22 branches, a `TRACK_01-metas` só local; a pasta nova tem um `.git` vazio com o remote `activest`) | (a) **pasta nova com o histórico levado**: o Guilherme dá o push de todas as branches e tags do `primevest` para o `activest` e clona o `activest` em `E:\repos\activest`; o Claude copia o que o git não leva (`.env` já com as chaves novas, `carga/mocks/`); `carga/data/` vem do bucket novo; (b) renomear a pasta antiga | **(a)** — o histórico está no `.git`, não no nome da pasta: nas duas opções ele é preservado; a (a) mantém a pasta e a pilha antigas intactas (D14, D5) e a (b) as perderia |
| **D19** | Pilha antiga para conferência | (a) parada (`stop`, sem `down`); para consultar, sobe só o banco antigo na porta 5434 (`DB_PORT=5434`), com a `activest` no ar; (b) sobe a pilha antiga inteira só com a `activest` parada | **(a)** |

Comandos da D18 (a), executados pelo Guilherme (regra 3.1):

```powershell
cd E:\repos\primevest
git push https://github.com/guilherme-dealmeida/activest.git --all
git push https://github.com/guilherme-dealmeida/activest.git --tags
# a pasta E:\repos\activest tem só um .git vazio: apagar e clonar no lugar
cd E:\repos
Remove-Item -Recurse -Force E:\repos\activest
git clone https://github.com/guilherme-dealmeida/activest.git E:\repos\activest
cd E:\repos\activest
git checkout develop
git checkout -b TRACK_01-renomeacao_activest
```

O que **não** passa para o repositório novo: pull requests e issues (ficam no `primevest`; os merges dos PRs estão nos
commits). No GitHub do `activest`, ajustar o branch padrão para o mesmo do antigo (`master`). As renomeações de pasta da
E2 mudam só a linha `package` de cada classe, então o git reconhece como renomeação e `git log --follow` continua
mostrando a história de cada arquivo.

### 6.3 Respondidas na terceira rodada e pendências antes da implementação

D18 **(a)** e D19 **(a)** aceitas (23/10/2026). Pasta `E:\repos\activest` conectada à sessão.

| # | Pendência | Quem |
|---|---|---|
| P1 | O token do R2 no `rclone` (remote `r2:`) só enxerga o `primevest-data` (listar buckets dá 403): falta criar o bucket `activest-data` e um token **Object Read & Write** dele (para a cópia), além do token **só leitura** que vai no `.env` da aplicação | Guilherme |
| P2 | `Claude.md`, `docs/REGRAS_CLAUDE.md` (metodologia de 23/10) e este plano ainda não estão em commit no `primevest`: comitar na `develop` do `primevest` e dar o push nos dois remotes, para entrarem no histórico levado ao `activest` | Claude, com o OK do Guilherme |
| P3 | Fluxo de branch: o trabalho vai na `TRACK_01-renomeacao_activest` (a partir da `develop`); ao fim, o Claude faz o merge na `develop` ou deixa a branch para o pull request do Guilherme (não há `gh` instalado: o PR é aberto pelo site) | Guilherme decide |

Resolução (23/10/2026):

- **P1** — o Guilherme estendeu o token `R2_TOKEN_PRIMEVEST` (Workers R2 Storage Bucket Item **Write**) aos buckets
  `primevest-data` e `activest-data`. Conferido: é o mesmo token do `rclone` e do `.env` (impressão digital do id e do
  segredo iguais) e já lê o `activest-data` (vazio). O token da aplicação passa a ter escrita: item para
  `PENDENCIAS.md` (token só leitura para a aplicação quando houver produção).
- **P2** — o Guilherme comitou na branch `TRACK-01_novo_nome_activest` do `primevest` (`ea2a41d`) e fez o merge na
  `develop` pelo PR #22 (`3e42a6d`).
- **P3** — o trabalho segue na branch `TRACK-01_novo_nome_activest`, agora no repositório `activest`; o fechamento é
  por pull request do Guilherme, como no PR #22.

Itens conferidos e sem pendência: credencial do git no Windows lê o `activest` (`ls-remote` ok); espaço livre E: 9,7 GB
(a pasta nova precisa de ~3 GB: `carga/data` 2,2 GB, `node_modules` 131 MB, `.git` 19 MB) e C: 34,5 GB (volume e
imagens novas). No GitHub do `activest`, o branch padrão (`master`) é ajustado pelo Guilherme no site depois do push.

## 7. Etapas do plano

| Etapa | O que | Valida com |
|---|---|---|
| **E0** | Retrato do `primevest_pgdata` (contagens das tabelas de catálogo) e retrato das V1–V67 num banco descartável (seção 4.2, passo 1) | arquivos de conferência |
| **E1** | Pasta `E:\repos\activest` (D18) e acesso dela nesta sessão; pilha `primevest` parada (D19) | pasta conectada; `docker ps` |
| **E2** | N3 — pastas, pacote `com.activest`, pom, classes `*Application`, Dockerfile, compose, `dev-watch.sh`, `build.ps1` | `mvn clean verify` verde (ainda com as migrations antigas) |
| **E3** | N2 — chaves, variáveis (`.env.example` e `.env` novo), Redis, front, compose (`name: activest`), carga, pilha de teste | bateria + `tsc -b` + lint |
| **E4** | N1 — textos, e-mails, termos, `MAIL_FROM`, `User-Agent` | `git grep` (critério 8) |
| **E5** | Migrations consolidadas (seção 4.2) e geradores de seed (R15) | comparação (critério 3) + bateria |
| **E6** | Bucket `activest-data` (você cria o bucket e o token; o Claude copia e confere) | contagem e tamanho iguais ao `primevest-data` |
| **E7** | Subida do zero da pilha `activest` e carga inicial completa | critérios 4 a 7 e 9 |
| **E8** | Documentação viva (D9), `PENDENCIAS.md` (resíduos: bucket antigo, volume antigo, pasta antiga, SMTP do domínio) | revisão |
| — | Testing (inclui D20), Review e Commit | mensagem sugerida: `TRACK_01 - Renomeação PrimeVest para Activest` (ou o TRACK que você abrir no repositório novo) |

## 8. Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| E0–E5, E7, E8 | executa e confere | aprova o plano e manda executar |
| Liberar o acesso à pasta nova no Cowork | pede | aprova |
| `.env` novo | cria a partir do antigo e confere | — |
| Bucket `activest-data` | copia os 2,2 GB e confere | cria o bucket e o token |
| Repositório GitHub novo, remote, push | — | executa |
| Título, logos, `public/images` | — | executa |
| Integrações no banco novo (D20) | confere | informa a chave de API |
| Apagar bucket antigo, volume antigo e pasta antiga | só com ordem | decide quando |
| Commit | sugere a mensagem | comita |

## 9. Andamento

| Data | Registro |
|---|---|
| 23/10/2026 | Escopo aberto. Discovery e Impact conferidos contra o repositório, o banco (`db_primevest`, 1.066 MB) e o Docker do DEV; plano apresentado com D1–D14. |
| 23/10/2026 | Guilherme respondeu D1–D14: renomeação completa, pasta, banco, contêineres e bucket novos, migrations consolidadas, pilha antiga parada como backup, sem escopos paralelos. Plano revisto (riscos R12–R16, seção 4.2, etapas E0–E8) com D15–D20 em aberto. |
| 23/10/2026 | Segunda rodada: D15, D16, D17 e D20 aceitas; riscos R12–R16 aceitos; repositório `activest` criado (vazio). D18 revista para levar o histórico (push de todas as branches + clone) e D19 aguardando confirmação. |
| 23/10/2026 | Terceira rodada: D18 (a) e D19 (a) aceitas; git deste escopo passa ao Claude; pasta `activest` conectada. Pendências P1–P3 levantadas. |
| 23/10/2026 | P1–P3 resolvidas: token R2 com escrita nos dois buckets (conferido), plano e metodologia no PR #22 do `primevest`, branch de trabalho `TRACK-01_novo_nome_activest`. |
| 23/10/2026 | **Execução autorizada.** E1: push de todas as branches (22) do `primevest` para o `activest`, sem divergência; clone em `E:\repos\activest`, branch `TRACK-01_novo_nome_activest`; `.env` e `carga/mocks/` copiados. |
| 23/10/2026 | E0: V1–V67 aplicadas num Postgres descartável (`ref-v67`): `pg_dump --schema-only` idêntico ao banco do DEV (49 tabelas, 64 índices, 21 sequências); 20 tabelas com seed. |
| 23/10/2026 | E2–E4: pastas, pacote, pom, configuração, Redis, front, compose (`name: activest`), textos, remetente e `User-Agent` — 708 arquivos, 3.154 trocas; `mvn clean verify` verde (com as migrations antigas). |
| 23/10/2026 | E5: `carga/scripts/migrations/consolidate.mjs` gera V1–V6; `compare.mjs` contra o `ref-v67`: estrutura igual (336 objetos), dados iguais (49 tabelas), sequências iguais (21). Ajustes: seed dos emissores antes do dos ativos; `COMMENT ON` mantidos; `IN (...)` legível nas CHECK. Seeds com ids explícitos (exceção registrada nas convenções). `mvn clean verify` verde com V1–V6; build e lint do front verdes (1 aviso antigo). |
| 23/10/2026 | E6: `primevest-data` → `activest-data` por cópia no servidor (rclone): 31.000 objetos, 2,161 GiB nos dois; `rclone check`: 0 diferenças. |
| 23/10/2026 | E7: pilha `primevest` parada (`stop`, volume intacto); `data pull` do `activest-data` (30.990 arquivos); `docker compose up -d --build`: Flyway "Successfully applied 6 migrations", as duas APIs `Started`; login do administrador e `bootstrap` por `admin.mjs` com `ACTIVEST_PASSWORD`; e-mail de redefinição no Mailpit com remetente `Activest <contato@activest.com.br>`; canais `activest:*` no Redis. Carga inicial em andamento. |
| 23/10/2026 | Commits na `TRACK-01_novo_nome_activest`: `703b025` (módulos, pacote, configuração e textos) e `7f24e30` (migrations consolidadas). |
| 23/10/2026 | E7 concluída: 1ª rodada do `bootstrap` com a etapa de proventos recusada porque a DIVIDENDS_SYNC agendada (20:00) rodava junto (PENDENCIAS 35.6); 2ª rodada `SUCCEEDED` em 38 min. Catálogo igual ao do PrimeVest: `assets` 2.626, `asset_stock_events` 1.059, `company_quarters` 19.346, `cvm_documents` 19.351, `share_counts` 18.778, `treasury_offers` 128, emissoras de RF 6.922; diferenças só as esperadas — `asset_cash_events` 54.952 (+14), `issuers` 1.213 (+15), `market_index_values` +4 (dado novo das fontes), `asset_price_history` −394 e `fii_monthly` −171 (fechamentos da carteira e fundos fora do catálogo que só o DEV tinha), dados de usuário zerados. Download do Tesouro aceito com o `User-Agent` Activest. |
| 23/10/2026 | E8: `Claude.md` (revisão, "Flyway — V1 a V6", "Renomeação para Activest" em Pontos de Atenção, Git), `README.md`, `docs/*.md`, `REGRAS_CLAUDE.md` (6.1), `PENDENCIAS.md` (seção 35), nota nos 26 planos anteriores. `git grep -i primevest` fora dos planos: só as referências intencionais (origem nas migrations e no `consolidate.mjs`, `PLANO_PRIMEVEST_TESTE.md`, `primevest-backups`, repositório e pilha antigos no `Claude.md`/`PENDENCIAS.md`, a saída de conferência de 10/10 em `docs/preco-teto`). |
| 23/10/2026 | Conferência do Guilherme: login Google ok, todos os menus sem quebra, título da página corrigido por ele. Foto do Google não aparece: regra do cadastro (Google preenche a foto só na criação da conta; PENDENCIAS 35.8), não efeito da renomeação. D20 (Binance) vai para escopo próprio com os ETFs americanos (35.7). Tokens do R2: pendência 35.1. Nada vai para a `master`: fluxo develop → uat (bateria de testes, escopo futuro) → master por release, registrado em `REGRAS_CLAUDE.md` 3.3. Avisos do Tesouro na caixa de notificações: os mesmos do PrimeVest (mesmos 4 títulos e fonte sem dado novo desde 15/10). |
