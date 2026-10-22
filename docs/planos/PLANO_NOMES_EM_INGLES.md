# PrimeVest — Plano de Nomes Internos em Inglês

> Fase: **Estrutura básica** (22/10/2026), para ser retomada em escopo próprio. Nada implementado.
> Origem: passo 4 do ESCOPO "Criar menu Tarefas no Primevest" (D16 do `docs/planos/PLANO_TAREFAS.md`): a tela continua
> em português, e o que é interno (rotas, API, parâmetros, configuração, classes, valores gravados) segue a linguagem do
> sistema, em inglês. O escopo de Tarefas já nasce assim; este plano trata do que já existe.

---

## 1. Objetivo

Trocar para inglês os nomes internos que hoje estão em português, sem mudar nada do que o usuário lê na tela.

## 2. Levantamento inicial (22/10/2026, a conferir na retomada)

| Onde | Em português hoje | Cuidado ao trocar |
|---|---|---|
| Rotas do front | `/rentabilidade`, `/ativos`, `/ativos/:ticker`, `/emissoras` (já redireciona), `/cadastro`, `/esqueci-senha`, `/termos`, `/privacidade` | links salvos e links gravados em notificações (`/ativos/{ticker}` no payload): a rota antiga redireciona, como `/emissoras` |
| Parâmetros de URL | `aba`, `periodo`, `de`, `ate`, `tipo`, `categoria`, `ativo`, `reserva`, `base`, `indices`, `acoes.*`/`fiis.*` | links das notificações e dos cards do Dashboard; aceitar o nome antigo por um tempo |
| API da adm-api | `/proventos`, `/proventos/levantamento`, `/proventos/fnet-status`, `/tesouro`, `/cripto/historico`, `/b3/cotahist/diario`, `/indices` | `carga/scripts/admin.mjs`, `docs/ADM_TASKS.md`, README, `Claude.md`; parte delas pode ser substituída pelo `/api/v1/tasks` |
| API da app-api | `/api/v1/indices` (plural latino; a forma `/indexes` é a outra opção) | front (`api/`) |
| Configuração | `primevest.proventos.*`, `primevest.tesouro.*`, `primevest.cripto-historico.*`, `primevest.cotahist-diario.*`, `primevest.abertura.*`, `primevest.emissoras.*` | `.env`, `.env.example`, `docker-compose*.yml`, testes; com o cadastro de tarefas, os horários saem daqui |
| Canais Redis | `primevest:catalogo:ativos`, `primevest:proventos:sob-demanda` | os dois serviços trocam juntos (mensagem sem garantia de entrega) |
| Classes e pacotes | `ProventosScheduler`, `ProventosCatalogService`, `CatalogoE2EBase` e outros | só código e testes |
| Valores gravados no banco | `NotificationKind` (`ROTINA_FALHOU`…), status de notificação (`NAO_RESOLVIDA`, `RESOLVIDA`, `DISPENSADA`) | migration que regrava as linhas e os CHECKs; front que filtra por esses valores |
| Dados de catálogo | nomes de `asset_types` (`ACAO`, `RENDA_FIXA`…) | provavelmente fora: é dado, não API; resolvidos por `name` em migrations e código |

## 3. Decisões em aberto

| # | Pergunta | Recomendação |
|---|---|---|
| N1 | Quais grupos da seção 2 entram? | A definir na retomada |
| N2 | Rotas e parâmetros antigos continuam aceitos (redirecionamento) e por quanto tempo? | Redirecionar sempre, como `/emissoras` |
| N3 | Valores gravados no banco (enums) entram, com migration? | A definir |
| N4 | Ordem: por grupo, cada um fechado (bateria verde) antes do próximo? | Sim |

## 4. Etapas

A definir na retomada, por grupo da seção 2.

## 5. Quem faz

| O quê | Claude | Guilherme |
|---|---|---|
| Levantamento completo e plano | ✓ | revisa |
| Decisões N1–N4 | recomenda | decide |
| Implementação (com ordem explícita) | ✓ | — |

## 6. Andamento

- 22/10/2026 — Estrutura básica criada a partir do levantamento do `docs/planos/PLANO_TAREFAS.md` (seção 4). Retomada em
  escopo próprio.
