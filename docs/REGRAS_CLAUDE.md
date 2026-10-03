# PrimeVest — Regras de trabalho entre Guilherme e o Claude

> Gerado em 02/10/2026 · revisado em 03/10/2026 (planos em `docs/planos/`, pendências em `docs/PENDENCIAS.md`,
> carga em `carga/`).
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

**5.3** Downloads demorados (ex.: mais de meia hora) e esperas longas o Guilherme executa ele mesmo na máquina: rodar
isso pelo Claude gasta token e consome o plano. O Claude entrega os comandos prontos e depois só confere o
resultado. Ex.: `data push` para o bucket é do Guilherme.

**5.4** Quando a tabela "Quem faz" do plano disser que o Guilherme roda algo (ex.: a bateria completa numa etapa
específica), o Claude prepara e depois confere; não roda por conta própria.

**5.5** A senha de administrador do PrimeVest fica em `PRIMEVEST_SENHA` no `.env` da raiz: as rotinas de
administrador são disparadas por script (`carga/scripts/admin.mjs`) sem pedir a senha a cada vez.

**5.6** Bloqueio de rede da política da organização (403/000 do proxy) é negação de política: não se contorna.

**5.7** A regra 5.3 não vale para a conta Claude do **Gabriel** (03/10/2026): nas sessões dele, o próprio Claude
executa os downloads demorados e as esperas longas (ex.: `data pull`, `bootstrap`) e confere o resultado.

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

---

> **Não entram aqui (regra de negócio):** "RF e TD precisam estar vinculadas a um título"; "informação de catálogo vem
> de integração, não de extrato"; "adm-api grava só catálogo; app-api grava dado de usuário".
