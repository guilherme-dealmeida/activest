# Activest — Regras de trabalho entre Guilherme e o Claude

> Gerado em 02/10/2026 · revisado em 03/10/2026 (planos em `docs/planos/`, pendências em `docs/PENDENCIAS.md`,
> carga em `carga/`), em 09/10/2026 (seção 8, banco de dados) e em 10/10/2026 (5.3: o Claude executa downloads e
> esperas longas; a antiga 5.7 deixou de ser necessária), em 12/10/2026 (2.6: preço atual × fechamento) e em
> 22/10/2026 (seção 9, nomes em inglês) e em 23/10/2026 (seções 1 e 2: os dois tipos de trabalho, POR ESCOPO com as
> 7 etapas e POR DÚVIDAS; 3.2 e seção 4 ajustadas às etapas) e em 24/10/2026 (3.4, branches protegidas; 2.5, decisão pela recomendação só com aprovação explícita) e em 27/10/2026
> (4.3, testar nos dois temas sem pedir permissão).
>
> **Escopo:** só a **relação de trabalho**: como pedimos, executamos, validamos e documentamos. Regras de negócio do
> sistema não entram aqui; elas ficam no `Claude.md` e nos planos.
>
> **Fontes:** combinados registrados nas sessões, `Claude.md` (seções "LEIA ANTES", "Como o Claude executa comandos
> aqui" e "Git") e os planos em `docs/planos/`.

## Sumário

1. [Comunicação](#1-comunicação)
2. [Tipos de trabalho: POR ESCOPO e POR DÚVIDAS](#2-tipos-de-trabalho-por-escopo-e-por-dúvidas)
3. [Git](#3-git)
4. [Validação — Testing e Review](#4-validação--testing-e-review)
5. [Execução na máquina do Guilherme](#5-execução-na-máquina-do-guilherme)
6. [Arquivos, pastas e dados](#6-arquivos-pastas-e-dados)
7. [Documentação](#7-documentação)
8. [Banco de dados](#8-banco-de-dados)
9. [Nomes em inglês](#9-nomes-em-inglês)

---

## 1. Comunicação

**1.1** As respostas do Claude são sempre em português (Brasil).

**1.2** (23/10/2026) Todo pedido é de um destes dois tipos de trabalho (seção 2), e o tipo se reconhece pelo formato:

**POR ESCOPO** — mudanças a serem requisitadas e/ou funcionalidade nova:

```
ESCOPO: [TÍTULO DO ESCOPO]

DESCRIÇÃO: [DESCRIÇÃO COMPLETA DO ESCOPO]
```

O título vira o assunto do plano (`docs/planos/PLANO_<ASSUNTO>.md`) e o trabalho segue as etapas de 2.1.

**POR DÚVIDAS** — entender uma problemática, estudar uma funcionalidade ou tirar dúvidas:

```
DÚVIDAS:

1- [DÚVIDA 1]
2- [DÚVIDA 2]
...
```

O Claude responde cada dúvida pelo número dela.

## 2. Tipos de trabalho: POR ESCOPO e POR DÚVIDAS

### POR ESCOPO

**2.1** O trabalho POR ESCOPO percorre sete etapas, nesta ordem:

| # | Etapa | O que se faz | Objetivo |
|---|---|---|---|
| 1 | **Discovery** (Descoberta) | analisar a demanda, compreender o comportamento atual do sistema, identificar requisitos, regras de negócio e componentes envolvidos | entender o problema antes de propor soluções |
| 2 | **Impact** (Análise de Impacto) | identificar componentes, serviços, APIs, bancos de dados e integrações que podem ser afetados; avaliar riscos, dependências e efeitos colaterais | compreender o alcance e os riscos da mudança |
| 3 | **Planning** (Planejamento e Aprovação) | definir a solução técnica, as alterações, a estratégia de implementação e os critérios de aceitação; apresentar o plano para aprovação antes de mexer no código | garantir alinhamento sobre o que será feito e como |
| 4 | **Implementation** (Implementação) | executar as alterações aprovadas, seguindo os padrões arquiteturais, as boas práticas e as convenções do projeto | implementar a solução conforme o plano autorizado |
| 5 | **Testing** (Testes) | criar ou atualizar testes unitários e de integração, executar as validações e verificar regressões | comprovar que a solução funciona sem comprometer o que existe |
| 6 | **Review** (Revisão) | revisar o código implementado: qualidade, segurança, desempenho, aderência aos requisitos e aos padrões do projeto | identificar e corrigir problemas antes da entrega |
| 7 | **Commit** (Registro da Alteração) | conferir as alterações finais e sugerir uma mensagem de commit clara e padronizada | registrar a alteração |

**2.2** As etapas 1 a 3 **não alteram código**: terminam com o plano apresentado ao Guilherme. A Implementation (etapa 4)
só começa com **aprovação explícita** dele ("mandou executar"). Concordar com o plano, ou contestar algum ponto dele,
**não** é aprovação.

**2.3** Quando mandar executar "por etapas" do plano, o Claude faz uma etapa do plano de cada vez, na ordem, cada uma
fechada (implementada, testada e com a bateria verde) antes da próxima.

**2.4** Cada escopo tem o seu plano em `docs/planos/PLANO_<ASSUNTO>.md`; **plano novo nasce nessa pasta**. O plano
registra o resultado das etapas 1 a 3: objetivo, o que o Discovery levantou, a análise de impacto (componentes, riscos,
dependências), a solução, os critérios de aceitação, as decisões numeradas (D1, D2...) para o Guilherme responder, as
etapas do plano, a tabela "Quem faz" (Claude × Guilherme) e a seção de andamento. As decisões tomadas por ele ficam
registradas no plano, com data.

**2.5** Pontos em aberto viram perguntas (decisões) no plano: o Claude não decide sozinho o que é escolha do
Guilherme.

(24/10/2026) **Decisão pela recomendação do Claude só vale com aprovação explícita** do Guilherme, mesmo que
genérica (ex.: "O restante das decisões estão aprovadas pela sua recomendação"). Decisão ignorada ou não respondida
**não** está aprovada: continua em aberto no plano e é perguntada de novo.

**2.6** (12/10/2026) **Preço atual × preço de fechamento.** Não se criam regras de escopo baseadas na insuficiência
temporária de atualização do preço atual (hoje só há fechamento, sem intradiário). Isso é uma decisão financeira, pela
idade do projeto, e não uma decisão técnica ou funcional. As regras usam sempre o **preço atual**; o preço de
fechamento só entra numa regra de escopo de forma **explícita**, quando a funcionalidade depender exclusivamente
dele, e não do preço atual. Origem: Preço Teto (10/10) e alerta de preço das notificações (12/10), onde a mesma
pergunta voltou.

**2.7** Na etapa 7 (Commit), o Claude confere as alterações finais (só leitura: `git status`, `git diff`), confirma que a
documentação foi atualizada (7.2) e **sugere** a mensagem de commit no padrão do repositório,
`<TRACK> - <descrição em português>` (ex.: `TRACK_01 - Aba agendamentos em tarefas`). Quem comita é o Guilherme (3.1).

### POR DÚVIDAS

**2.8** O trabalho POR DÚVIDAS é respondido, não implementado: o Claude investiga (código, banco, logs, documentação) e
responde às perguntas. Nada é alterado no código.

**2.9** Se as respostas levarem a um raciocínio que pede mudança, o Claude propõe; com o OK do Guilherme, o trabalho
**vira POR ESCOPO** e segue a partir da etapa 1, aproveitando o que já foi levantado.

## 3. Git

**3.1** O git é do Guilherme. O Claude **não executa** comando de git (commit, branch, push, merge, checkout...).
Exceção: comandos só de leitura para diagnóstico são permitidos.

**3.2** O Claude **não comenta** o que está ou não comitado: nada de "segue não comitado" ou status de working tree ao
longo do trabalho. A única exceção é a etapa 7 do POR ESCOPO (2.7), em que ele confere as alterações finais e sugere a
mensagem de commit.

**3.3** (23/10/2026) **Fluxo das branches.** O trabalho do dia a dia vai para a `develop` (branches `TRACK_01-*` por
escopo, com pull request). A `uat` recebe o que vai ser testado, com uma bateria de testes própria (escopo futuro); só
depois de aprovado na `uat` o código vai para a `master`, por release. Nada vai direto para a `master`.

**3.4** (24/10/2026) **Branches protegidas no GitHub.** O ruleset "Proteção da branch principal" do repositório
`activest` cobre `develop`, `uat` e `master`: nenhuma das três aceita push direto, só merge por pull request. Origem:
em 24/10/2026 a `develop` local foi renomeada por engano e os dois commits do escopo "Alteração da tela de Ativos"
(`9039adf`, `7cd09ba`) entraram direto na `develop`, sem PR — ficaram assim, por decisão do Guilherme. O trabalho de
cada escopo nasce numa branch `TRACK_01-*` criada a partir da `develop`.

## 4. Validação — Testing e Review

**4.1** Toda implementação passa pela validação feita pelo Claude na etapa 5 (Testing): compilar, rodar os testes e o
lint, subir o ambiente e conferir o comportamento. Em seguida vêm a Review (etapa 6), com as correções que ela
apontar, e o Commit (etapa 7).

**4.2** Testes visuais — "caminho 3", escolhido pelo Guilherme:

- conferência rápida no navegador do app, com ele logado;
- testes E2E automatizados com Playwright (escopo futuro, o planejamento será informado por ele).

**4.3** (27/10/2026) Para testar, o Claude **não pede permissão nem hesita** em trocar o modo de cores do sistema
(tema claro/escuro), mesmo que a troca fique gravada na preferência do usuário: confere a tela nos dois temas e volta
ao tema em que ela estava. Origem: Evolução dos Proventos, em que o tema claro deixou de ser conferido para não mexer
na preferência gravada.

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

**5.5** A senha de administrador do Activest fica em `ACTIVEST_PASSWORD` no `.env` da raiz: as rotinas de
administrador são disparadas por script (`carga/scripts/admin.mjs`) sem pedir a senha a cada vez.

**5.6** Bloqueio de rede da política da organização (403/000 do proxy) é negação de política: não se contorna.


## 6. Arquivos, pastas e dados

**6.1** Repositório local: `E:\repos\activest`. O projeto roda por Docker Desktop no Windows (`docker compose up` na
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
| Configuração e ambiente | chaves do `application.yml` (`activest.dividends.*`, `treasury.*`), variáveis de ambiente (`ACTIVEST_PASSWORD`), nomes de canal do Redis (`activest:dividends:on-demand`) |
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
