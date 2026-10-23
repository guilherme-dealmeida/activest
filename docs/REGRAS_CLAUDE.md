# PrimeVest — Regras de trabalho entre Guilherme e o Claude

> Gerado em 02/10/2026 · revisado em 03/10/2026 (planos em `docs/planos/`, pendências em `docs/PENDENCIAS.md`,
> carga em `carga/`), em 09/10/2026 (seção 8, banco de dados) e em 10/10/2026 (5.3: o Claude executa downloads e
> esperas longas; a antiga 5.7 deixou de ser necessária), em 12/10/2026 (2.6: preço atual × fechamento) e em
> 22/10/2026 (seção 9, nomes em inglês).
>
> **Escopo:** só a **relação de trabalho**: como pedimos, executamos, validamos e documentamos. Regras de negócio do
> sistema não entram aqui; elas ficam no `Claude.md` e nos planos.
>
> **Fontes:** combinados registrados nas sessões, `Claude.md` (seções "LEIA ANTES", "Como o Claude executa comandos
> aqui" e "Git") e os planos em `docs/planos/`.

## Sumário

1. [Comunicação](#1-comunicação)
2. [Planejamento × execução](#2-planejamento--execução)
3. [Git](#3-git)
4. [Validação — a última etapa do Claude](#4-validação--a-última-etapa-do-claude)
5. [Execução na máquina do Guilherme](#5-execução-na-máquina-do-guilherme)
6. [Arquivos, pastas e dados](#6-arquivos-pastas-e-dados)
7. [Documentação](#7-documentação)
8. [Banco de dados](#8-banco-de-dados)
9. [Nomes em inglês](#9-nomes-em-inglês)

---

## 1. Comunicação

**1.1** As respostas do Claude são sempre em português (Brasil).

**1.2** Os pedidos seguem a estrutura:

| Campo | Conteúdo |
|---|---|
| `ESCOPO` | o assunto/frente de trabalho |
| `ETAPA` | em que fase o trabalho está (Planejamento, Execução, Documentação...) |
| `DESCRIÇÃO` | o que se quer |
| `PASSOS` | a lista numerada do que fazer |

O Claude responde seguindo essa estrutura e os passos na ordem dada.

## 2. Planejamento × execução

**2.1** `ETAPA: Planejamento` pede **só o plano**. Nada é implementado.

**2.2** Implementação só com **ordem explícita** do Guilherme ("mandou executar"). Concordar com o plano, ou contestar
algum ponto dele, **não** é ordem de execução.

**2.3** Quando mandar executar "por etapas", o Claude executa uma etapa de cada vez, na ordem do plano, cada uma
fechada (bateria verde) antes da próxima.

**2.4** Cada escopo tem o seu plano em `docs/planos/PLANO_<ASSUNTO>.md`; **plano novo nasce nessa pasta**. O plano
traz: objetivo, decisões numeradas (D1, D2...) para o Guilherme responder, etapas, tabela "Quem faz"
(Claude × Guilherme) e a seção de andamento. As decisões tomadas por ele ficam registradas no plano, com data.

**2.5** Pontos em aberto viram perguntas (decisões) no plano: o Claude não decide sozinho o que é escolha do
Guilherme.

**2.6** (12/10/2026) **Preço atual × preço de fechamento.** Não se criam regras de escopo baseadas na insuficiência
temporária de atualização do preço atual (hoje só há fechamento, sem intradiário). Isso é uma decisão financeira, pela
idade do projeto, e não uma decisão técnica ou funcional. As regras usam sempre o **preço atual**; o preço de
fechamento só entra numa regra de escopo de forma **explícita**, quando a funcionalidade depender exclusivamente
dele, e não do preço atual. Origem: Preço Teto (10/10) e alerta de preço das notificações (12/10), onde a mesma
pergunta voltou.

## 3. Git

**3.1** O git é do Guilherme. O Claude **não executa** comando de git (commit, branch, push, merge, checkout...).
Exceção: comandos só de leitura para diagnóstico são permitidos.

**3.2** O Claude **não comenta** o que está ou não comitado: nada de "segue não comitado", status de working tree ou
sugestão de commit ao terminar.

## 4. Validação — a última etapa do Claude

**4.1** Toda implementação termina com a validação feita pelo Claude: compilar, rodar os testes e o lint, subir o
ambiente e conferir o comportamento. Entregue isso, o assunto está encerrado.

**4.2** Testes visuais — "caminho 3", escolhido pelo Guilherme:

- conferência rápida no navegador do app, com ele logado;
- testes E2E automatizados com Playwright (escopo futuro, o planejamento será informado por ele).

## 5. Execução na máquina do Guilherme

**5.1** Antes de responder "não consigo compilar/subir/testar", o Claude carrega as ferramentas do servidor MCP local
`claude-code` (PowerShell/Bash no Windows, com JDK 21, Maven, Docker Desktop e a rede dele). Só se elas não existirem
a limitação é real; aí o Claude diz isso, e não que o projeto não pode ser executado.

**5.2** Comando que bloqueia (`spring-boot:run`, `npm run dev`) não é rodado por essa ponte: sobe desacoplado
(`docker compose up -d`) e o log é lido num comando separado. Sem a ponte, o fallback é o `.claude\build.ps1`, que
grava a saída em `.claude\logs\` para o Claude ler o log inteiro.

**5.3** Downloads e uploads demorados e esperas longas (ex.: `data pull`, `data push`, `bootstrap`) o próprio Claude
executa e confere o resultado (revisada em 10/10/2026 — antes o Guilherme rodava esses comandos na máquina dele).

**5.4** Quando a tabela "Quem faz" do plano disser que o Guilherme roda algo (ex.: a bateria completa numa etapa
específica), o Claude prepara e depois confere; não roda por conta própria.

**5.5** A senha de administrador do PrimeVest fica em `PRIMEVEST_PASSWORD` no `.env` da raiz: as rotinas de
administrador são disparadas por script (`carga/scripts/admin.mjs`) sem pedir a senha a cada vez.

**5.6** Bloqueio de rede da política da organização (403/000 do proxy) é negação de política: não se contorna.


## 6. Arquivos, pastas e dados

**6.1** Repositório local: `E:\repos\primevest`. O projeto roda por Docker Desktop no Windows (`docker compose up` na
raiz).

**6.2** Tudo o que a carga inicial precisa fica **dentro do projeto**, em `carga/`: `carga/data` (dados; cópia de
referência no bucket R2) e `carga/scripts` (scripts de carga e manutenção). Nada carregado num ambiente novo depende de
pasta fora do projeto.

**6.3** Os dados reais do Guilherme (ex.: extratos da B3 da carteira dele) ficam em `carga/mocks/`, dentro do projeto e
**fora do git** (`.gitignore`). Nada é apagado em `carga/mocks/` sem ordem. Revisado em 03/10/2026.

**6.4** Arquivo sem uso é apagado, não arquivado (nada de pastas `_old` ou backups esquecidos dentro do repositório).

**6.5** Credenciais reais não são versionadas: ficam no `.env` (o `.env.example` documenta as variáveis).

## 7. Documentação

**7.1** `Claude.md` é a documentação do projeto e afirma só o que foi verificado contra o código; o que está pendente
vai para `docs/PENDENCIAS.md`. Os dois são lidos no início do trabalho.

**7.2** Ao fechar uma implementação, o Claude atualiza `Claude.md`, `docs/PENDENCIAS.md` e `README.md` (e o andamento
do plano correspondente em `docs/planos/`), com a data.

**7.3** Os documentos de `docs/` são Markdown (`.md`); as subpastas (`planos/`, `brapi/`, `emissores/`) seguem o
formato do que guardam.

## 8. Banco de dados

**8.1** Campo novo obrigatório (`NOT NULL`) em tabela que já existe nasce com o **valor default informado** na própria
migration, que preenche as linhas existentes. Não se escreve migration com lógica customizada para deduzir o valor de
cada linha; o ajuste fino, se houver, é feito depois pelo usuário. Ex. (09/10/2026): emissora e distribuidora dos
títulos de Renda Fixa e Tesouro Direto entram como "Instituição não listada".

## 9. Nomes em inglês

**9.1** (22/10/2026) **Todo nome que o Claude cria ou renomeia é em inglês**, desde a primeira versão: não se cria nome
em português para traduzir depois. Vale para:

| Onde | O que é nome |
|---|---|
| Front | rotas (`/performance`, `/assets`), parâmetros de URL e os valores fixos que só existem na URL (`tab=reservation`, `basis=net`), páginas, componentes, hooks, funções, tipos, variáveis, chaves de `localStorage` |
| API (app-api e adm-api) | rotas e parâmetros (`/api/v1/admin/dividends/payment-date-survey`, `fnet=all`), chaves do JSON de resposta (`task`, `result`) |
| Java (produção) | pacotes, classes (inclusive a classe enum e os records), métodos, campos, constantes, variáveis |
| Testes | classes, bases, helpers e nomes de método (o `@DisplayName` fica em português) |
| Configuração e ambiente | chaves do `application.yml` (`primevest.dividends.*`, `treasury.*`), variáveis de ambiente (`PRIMEVEST_PASSWORD`), nomes de canal do Redis (`primevest:dividends:on-demand`) |
| Banco | tabelas, colunas, constraints e índices (`ck_users_cpf_complete`) |
| Pastas de recurso de teste | `treasury/`, não `tesouro/` |

**9.2** **Não é nome, e fica como está:** valor de registro gravado no banco e valor de enum que trafega na API ou na
mensagem do Redis — em português ou inglês, conforme o tipo de registro (ex.: `PROVENTOS`, `ABAIXO_DO_IDEAL`, `1A`);
nada é regravado para traduzir. Também ficam em português o que pessoas leem (tela, mensagens, logs, comentários,
Javadoc, documentação), os termos do mercado (`data_com`/`dataCom`), os nomes que vêm de fonte externa (colunas da CVM,
campos da B3, `NoticeStatus` do FNET, `B3Investidor`), os arquivos de migration já aplicados e a carga (`carga/scripts`,
pastas de `carga/data` espelhadas no bucket).

**9.3** Palavra que se escreve igual nas duas línguas e é inglês correto fica (ex.: `indices`, plural de *index*,
como em `market_indices`).

**9.4** Em código existente, nome em português encontrado no caminho é renomeado no mesmo trabalho só quando o escopo
pedir; senão vira item em `docs/PENDENCIAS.md`. Endereço já publicado (rota ou parâmetro do front) que muda de nome
continua aceito: a rota antiga redireciona e o parâmetro antigo é traduzido na chegada (`utils/legacyUrl.ts`).

Origem: `docs/planos/PLANO_NOMES_EM_INGLES.md` (diretriz do Guilherme, 22/10/2026; escopo encerrado no mesmo dia).

---

> **Não entram aqui (regra de negócio):** "RF e TD precisam estar vinculadas a um título"; "informação de catálogo vem
> de integração, não de extrato"; "adm-api grava só catálogo; app-api grava dado de usuário".
