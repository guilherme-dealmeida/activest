# .claude — ponte de execucao

Esta pasta existe por um motivo unico: **o Claude nao consegue executar o projeto.**

O shell que ele tem na maquina e uma VM Linux isolada do Cowork, sem Maven,
sem Docker, com JDK 11 (o projeto e 21) e com a rede bloqueada por politica de
egress (`repo.maven.apache.org` e `registry.npmjs.org` respondem 403). Ele le,
busca e edita arquivo; nao compila, nao sobe container, nao abre tela.

O que ele CONSEGUE fazer sozinho, hoje, sem nada seu:

- `tsc --noEmit` no `activest-web` (type-check completo — ferramenta JS pura
  roda com o `node_modules` do Windows)
- `eslint` (depois de um `npm install`, ver eslint.config.js)
- ler qualquer arquivo desta pasta

Dai a ideia: voce roda o build na SUA maquina, a saida cai aqui em `logs/`,
e o Claude le o log inteiro em vez de voce colar um pedaco dele.

## Uso

    .\.claude\build.ps1          # compila a API e grava logs\build-api.log
    .\.claude\build.ps1 -Test    # mvn clean test   -> logs\test-api.log
    .\.claude\build.ps1 -Verify  # mvn clean verify -> logs\verify-api.log (sobe PostgreSQL em container)
    .\.claude\build.ps1 -Docker  # + docker compose logs -> logs\docker-api.log
    .\.claude\build.ps1 -Web     # + tsc/vite build -> logs\build-web.log

Depois e so dizer "olha o log" no chat. Nao precisa colar nada.

## logs/

Ignorado pelo git (`*.log` ja estava no .gitignore da raiz). Os logs sao
sobrescritos a cada execucao — o Claude le sempre o mais recente.
