# PrimeVest — Plano de Tarefas (menu Tarefas: acompanhar, executar, parar e configurar as rotinas)

> Fase: **Implementado em 22/10/2026** (T1–T7; execução autorizada pelo Guilherme no mesmo dia). Levantamento concluído em
> 22/10/2026 (D1–D28, estrutura na seção 1.7); planejamento na seção 6, D29–D36 decididas.
> Origem: ESCOPO "Criar menu Tarefas no Primevest", ETAPA LEVANTAMENTO, passos 1 a 4 do Guilherme.
> Referências: `docs/SCHEDULE_TASKS.md` (rotinas agendadas), `docs/ADM_TASKS.md` (disparo manual),
> `docs/planos/PLANO_NOTIFICACOES.md` (mensagem de rotina que falhou), `docs/planos/PLANO_ADM_API.md` (os dois serviços).

---

## 0. Resumo

Hoje **nenhuma rotina está cadastrada na base de dados**. Elas existem só no código: classe `@Scheduled` + horário em
`application.yml` de cada serviço, disparo manual por `POST /api/v1/admin/*` (adm-api, 202 "no log da API") e
resultado **só no log do contêiner**. O que fica gravado de uma rodada é, no máximo, a mensagem `ROTINA_FALHOU` das
Notificações (uma por rotina por dia). Não há status, data de início/fim, histórico, atributos nem como parar.

Tudo o que o passo 1 pede (a–h) é estrutura nova. A seção 1.4 traz o rascunho das tabelas; a seção 5, as decisões.

---

## 1. Passo 1 — como as rotinas estão hoje e o que falta

### 1.1 Como uma rotina existe hoje

| Aspecto | Hoje | Onde |
|---|---|---|
| Cadastro | não existe; a rotina é uma classe em `scheduler/` | adm-api: 11 classes em `scheduler/`; app-api: 3 em `scheduler/` + o worker das integrações |
| Horário | `@Scheduled(cron = "${primevest.<rotina>.cron:...}")`, fuso de São Paulo; valor no `application.yml` | fixo na subida: mudar exige reiniciar o serviço |
| Liga/desliga | chave geral `primevest.scheduling.enabled` + algumas `primevest.<rotina>.enabled` | `application*.yml` |
| Disparo manual | `POST /api/v1/admin/<rotina>` → `Thread.ofVirtual().start(...)`, responde 202 | `AdminController.start` (adm-api); as rotinas da app-api **não têm** disparo manual |
| Rodada dupla | trava em memória (`ReentrantLock.tryLock` / `AtomicBoolean`) em parte das rotinas: proventos, CVM, informe FII, índices, Tesouro, datas CVM, levantamento, série trimestral, bootstrap. As outras não têm | cada service |
| Parar | **não existe**: a thread do disparo manual não é guardada; a rotina agendada roda na thread do agendador | — |
| Resultado / log | `log.info/warn/error` no console; `docker compose logs -f adm-api`. Sem arquivo de log | `application*.yml` (`logging.level`) |
| Falha | `AdminNotices.routineFailed` → notificação `ROTINA_FALHOU` (público ADMIN), no máximo uma por rotina por dia | 13 pontos na adm-api; nenhum na app-api |
| Quem pode | adm-api: JWT com `users.is_admin = true` (403 aos demais) | `AdminController.requireAdmin` |

### 1.2 Inventário das rotinas

Nomes propostos no formato do passo 1e (maiúsculo, sem acento, `_`) e em inglês (passo 4 — ver D1).

**adm-api — agendadas** (todas também com disparo manual)

| # | Nome proposto | O que faz | Quando (hoje) | Disparo manual hoje | Recuperação na subida |
|---|---|---|---|---|---|
| 1 | `B3_CLOSING` | Fechamento da B3 dos ativos em carteira (BRAPI) [A1] | 19:00 seg–sex | `/closing` (junto com o 2) | sim [A3] |
| 2 | `CRYPTO_CLOSING` | Fechamento das criptos em carteira (CoinGecko) [A2] | 19:00 todo dia | `/closing` (junto com o 1) | sim [A3] |
| 3 | `CRYPTO_PRICE_HISTORY` | Histórico de preço de cripto (CoinGecko 365 dias, Binance BTC/BRL) | 19:30 | `/cripto/historico` | não |
| 4 | `DIVIDENDS_SYNC` | Proventos e eventos societários (B3, FNET) + datas de pagamento da CVM [A4] | 20:00 | `/proventos` (**sem** a etapa da CVM) | não |
| 5 | `FNET_STATUS_SYNC` | Situação dos avisos do FNET + proventos relendo todos os avisos [A12] | domingo 03:00 | `/proventos/fnet-status` (só a 1ª parte) | não |
| 6 | `CVM_STATEMENTS_IMPORT` | Balanços DFP/ITR + série trimestral + informe mensal de FII [A5] | 07:30 | `/cvm/import` e `/cvm/fii-monthly` (separados) | não |
| 7 | `TREASURY_PRICES` | Preço oficial e taxas do Tesouro Direto [A10] | 08:00 | `/tesouro` | sim |
| 8 | `B3_DAILY_QUOTES` | Cotações de todo o catálogo pelo COTAHIST diário [A11] | 08:30 e 21:30 | `/b3/cotahist/diario` | sim |
| 9 | `MARKET_INDICES` | Índices de mercado (SGS, B3) [A9] | 10:00 | `/indices` | sim |
| 10 | `CATALOG_AUDIT` | Conferência do catálogo e dos índices atrasados [A6] | segunda 07:00 | `/catalog-audit` | não |
| 11 | `ASSET_LOGOS` | Logos dos ativos [A7] | dia 1, 06:00 | `/logos` | não |
| 12 | `FIXED_INCOME_ISSUERS` | Emissoras e distribuidoras de RF (BCB, CVM) + logos delas | dia 1, 05:30 | `/fixed-income-issuers` e `/fixed-income-issuers/logos` | não |

**adm-api — só sob demanda** (sem horário)

| # | Nome proposto | O que faz | Disparo manual hoje |
|---|---|---|---|
| 13 | `BOOTSTRAP` | Carga inicial num banco vazio (9 etapas) | `/bootstrap` |
| 14 | `ISSUERS_IMPORT` | Emissores (CVM) | `/issuers/import` |
| 15 | `ISSUERS_LINK` | Ligação ativo → emissor | `/issuers/link` |
| 16 | `B3_YEARLY_QUOTES` | COTAHIST anual 2010+ | `/b3/cotahist` |
| 17 | `CVM_QUARTERS` | Recalcula a série trimestral | `/cvm/quarters` |
| 18 | `CVM_PAYMENT_DATES` | Datas de pagamento pelo relatório de proventos da CVM | `/cvm/proventos` |
| 19 | `PAYMENT_DATE_SURVEY` | Datas de pagamento do levantamento | `/proventos/levantamento` |

**app-api — agendadas** (nenhuma tem disparo manual hoje)

| # | Nome proposto | O que faz | Quando (hoje) | Na subida |
|---|---|---|---|---|
| 20 | `DAILY_OPENING` | Abertura do dia: vencidos, proventos e eventos de D0, conferências [B7] | 00:00 | sim |
| 21 | `INTEGRATIONS_DAILY_SYNC` | Enfileira a sincronização diária das corretoras [B4] | 08:45 | não |
| 22 | `POSITIONS_REFRESH` | Rede de segurança: proventos, valorização e curva de todas as posições [B1] | 21:00 | sim [B2] |

**adm-api — curadoria** (sob demanda; entram pela D4, 22/10/2026: as entradas de cada uma são atributos da tarefa)

| # | Nome proposto | O que faz | Disparo manual hoje (síncrono) | Atributos (rascunho) |
|---|---|---|---|---|
| 23 | `FII_CNPJ_CURATION` | Liga FIIs ao CNPJ do fundo (`MANUAL`) e cria o emissor que faltar, quando o ISIN não resolve | `POST /issuers/fii-cnpjs` (lista) | `LINES`: uma linha `TICKER;CNPJ` por fundo |
| 24 | `ASSET_ISSUER_LINK` | Liga (ou desfaz a ligação de) um ativo a um emissor, com a classe da ação | `PUT /assets/{id}/issuer` | `TICKER`, `ISSUER_CNPJ` (vazio = desfaz), `SHARE_CLASS` |
| 25 | `UNIT_COMPOSITION` | Grava a composição de uma unit (ex.: 1 ON + 2 PNA) | `PUT /assets/{id}/unit-composition` | `TICKER`, `COMPOSITION` (`ON=1;PNA=2`) |

O resultado que hoje volta na resposta (a situação de cada linha) passa a ser o resumo e o log da rodada (D6).

**Fora do cadastro** (D4): worker da fila de integrações [B5] (a cada 5 s; o histórico dele já é `integration_runs`),
recuperação da fila na subida [B6] e os ouvintes do Redis "preciso do histórico" [A8] e "catálogo mudou" [B3] (uma
rodada por transação ou por mensagem).

### 1.3 O que o passo 1 pede × o que existe

| Item | Existe hoje? | Proposta |
|---|---|---|
| a) Status — Executada, Falhada, Executando, Encerrada | não | `task_executions.status` |
| a) Status — Agendada (nas datas de vigência da semana) | não | calculado do cron na leitura, sem gravar linha (D3) |
| b) Data início | não (só no log) | `task_executions.started_at` |
| c) Data fim | não | `task_executions.finished_at` |
| d) Data de vigência | não | `task_executions.scheduled_for`: o horário do agendamento a que a rodada pertence; no disparo manual, o momento do pedido |
| e) Nome maiúsculo, sem acento, sem espaço, sem ç | não | `tasks.name` com `CHECK (name ~ '^[A-Z0-9]+(_[A-Z0-9]+)*$')` e único |
| f) Descrição | só nos documentos | `tasks.description NOT NULL` (texto da seção 1.2, revisado por você) |
| g) Atributos chave/valor | não | `task_attributes (att_name, att_value)` |
| h) Outros necessários | — | serviço dono, cron, liga/desliga, origem do disparo, quem disparou, pedido de parada, resumo e erro da rodada (seção 1.4) |

### 1.4 Rascunho da estrutura (migration no core; as duas APIs rodam o Flyway)

> **Revisto pela seção 1.7** (modelo tipo × tarefa, D24): `tasks` e `task_attributes` mudam e entram `task_types` e
> `task_type_attributes`. `task_executions` e `task_execution_logs` continuam como abaixo.

**`tasks`** — o cadastro

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | `BIGSERIAL` PK | |
| `name` | `VARCHAR(80)` único | regra do 1e por `CHECK` |
| `description` | `TEXT NOT NULL` | |
| `service` | `VARCHAR(10)` | `ADM_API` ou `APP_API`: quem executa (o agendador e a thread estão nesse serviço) |
| `cron` | `VARCHAR(120)` nulo | nulo = só sob demanda; semeado com o valor atual do `application.yml` (D10) |
| `enabled` | `BOOLEAN NOT NULL DEFAULT TRUE` | substitui os `primevest.<rotina>.enabled` (D11) |
| `created_at`, `updated_at` | | padrão do projeto (`@PrePersist`/`@PreUpdate` + `DEFAULT NOW()`) |

**`task_attributes`** — chave/valor da tarefa (só a estrutura neste escopo — D13)

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | `BIGSERIAL` PK | |
| `task_id` | FK `tasks` `ON DELETE CASCADE` | |
| `att_name` | `VARCHAR(80)` | mesma regra do nome (`FILE_NAME`, `FILE_EXTENSION`, `PATH`) |
| `att_value` | `TEXT` | |
| | | único `(task_id, att_name)` |

**`task_executions`** — uma linha por rodada (histórico e Acompanhamento)

| Coluna | Tipo | Observação |
|---|---|---|
| `id` | `BIGSERIAL` PK | |
| `task_id` | FK `tasks` | |
| `status` | `VARCHAR(12)` | `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED` — em inglês também na tela (D2); `SCHEDULED` é só calculado (D3); `SKIPPED` se a D20 for aceita |
| `parameters` | `JSONB` nulo | cópia dos atributos usados na rodada (D19, em aberto) |
| `trigger_type` | `VARCHAR(10)` | `SCHEDULE` (horário), `MANUAL` (tela/endpoint), `STARTUP` (recuperação na subida) |
| `requested_by` | FK `users` nula | quem disparou ou mandou parar |
| `scheduled_for` | `TIMESTAMP` | data de vigência (1d) |
| `started_at` / `finished_at` | `TIMESTAMP` | `finished_at` nulo enquanto executa |
| `stop_requested_at` | `TIMESTAMP` nulo | pedido de parada (D8) |
| `summary` | `TEXT` nulo | resumo da rodada, escrito pela rotina no fim (ex.: "503 emissores, 12 eventos novos, 0 falhas") — D6 |
| `error_message` | `TEXT` nulo | a falha que encerrou a rodada — D6 |
| | | índices `(task_id, started_at DESC)` e parcial `WHERE status = 'RUNNING'` |

Rodada que estava `RUNNING` quando o serviço caiu: na subida vira `FAILED` ("serviço reiniciado durante a
execução"), como a fila de integrações já faz [B6].

Log da rodada (D6, meio-termo decidido em 22/10/2026): tabela `task_execution_logs`
(`execution_id`, `logged_at`, `level`, `message`), alimentada por um appender do Logback que grava as linhas marcadas
com o id da rodada (MDC, propagado às threads que a rotina abre — ex.: os downloads paralelos do FNET).

### 1.5 Atributos: há necessidade imediata? (passo 1g)

Candidatos que já existem como parâmetro de endpoint ou de configuração: `from`/`to` (anos ou datas), `codes` (índices),
`force` (CVM), `recent` (índices, Tesouro), `fnet=todos` (proventos), `all` (logos de RF), `primevest.proventos.scope`
(`ALL`/`HELD`), pausas entre chamadas. Nenhum é necessário para as rodadas agendadas de hoje, que rodam sem parâmetro.
Recomendação: só a estrutura (D13).

### 1.6 Executar com entradas (D12 — levantamento de 22/10/2026, revisto no mesmo dia)

Hoje os parâmetros existem só no disparo manual (`/api/v1/admin/*`). A rodada agendada roda sem nenhum: é sempre a
rodada **incremental** (o que falta desde a última vez). Com parâmetro, a rodada vira **recarga** de um período.

| Tarefa | Parâmetros de hoje | Validação de hoje |
|---|---|---|
| `B3_YEARLY_QUOTES` | `from`, `to` (anos) | `from` ≥ 1986, padrão 2010; `to` ≥ `from`, padrão ano corrente |
| `B3_DAILY_QUOTES` | `from`, `to` (datas) | até 366 dias; sem `from` = incremental |
| `CVM_STATEMENTS_IMPORT` | balanços: `from` (≥ 2010), `to`, `force`; informe FII: `from` (≥ 2016), `to`, `force` | `force=true` reprocessa sem baixar de novo |
| `CVM_PAYMENT_DATES` | `from` (≥ 2022), `to` | |
| `MARKET_INDICES` | `codes` (lista), `from`, `to`, `recent` | código desconhecido: 400 |
| `TREASURY_PRICES` | `from` (≥ 2002), `recent` | |
| `DIVIDENDS_SYNC` | `fnet=todos` (relê todos os avisos do FNET, ~40 min) | |
| `FNET_STATUS_SYNC` | `missing` (só os fundos sem arquivo de situação) | |
| `FIXED_INCOME_ISSUERS` | `all` (logos: baixa de novo quem tem) | |
| as outras 13 | nenhum | |

Implicações:

1. **As entradas podem ser atributos da tarefa** (pergunta do Guilherme, 22/10/2026): `FROM_YEAR`, `TO_YEAR`,
   `INDEX_CODES`, `FORCE`, `FNET_REREAD_ALL`… em `task_attributes`, editados na aba Configuração. O único cuidado é que
   atributo é **permanente**: a rodada agendada também o lê. Pôr `FROM_YEAR = 2010` no COTAHIST diário para uma recarga
   faria toda rodada das 08:30 e 21:30 recarregar desde 2010, até alguém apagar. Daí a D19: atributo como valor padrão
   (vazio = incremental, como hoje) e, no Run, a tela mostra os atributos preenchidos e deixa mudar **só para aquela
   rodada**, sem regravar a tarefa.
2. **Validação num lugar só:** hoje está no `AdminController`. Passa para a definição da tarefa no código (quais
   atributos ela aceita, tipo, mínimo/máximo), usada pela tela, pelos endpoints e pela curadoria. `task_attributes`
   continua chave/valor texto, como no passo 1g.
3. **Histórico com o que foi usado:** a rodada guarda uma cópia dos valores com que rodou (`task_executions.parameters`,
   JSONB) e quem pediu — atributo editado depois não reescreve o passado.
4. **Recargas são longas** (COTAHIST 2010 em diante, índices desde 2010, `fnet=todos`): pedem confirmação na tela e
   dependem do Parar (D8) para não prender a tarefa.
5. **Choque com o horário** (D20): a recarga segura a trava da tarefa; a agendada que chega nesse meio é pulada.
6. **Tarefa com duas partes** (D21): os limites de período diferem entre as partes.
7. **Endpoints de hoje** (D22): continuam, mas pelo mesmo executor, para tudo cair no histórico.
8. Na tela: Run nas duas abas (D23); nas tarefas com atributos, o Run abre os atributos preenchidos para conferir ou
   mudar só naquela rodada (D19).

### 1.7 Tipo de tarefa × tarefa (modelo do Guilherme, 22/10/2026 — D24)

A rotina vira **tipo** (o "template": o código Java que sabe fazer o trabalho e os atributos que aceita); a **tarefa** é
uma instância do tipo, com nome, descrição, horário e os seus valores de atributo. Duas tarefas do mesmo tipo rodam a
mesma rotina com atributos diferentes. Com isso não existe "mudar só para esta rodada": quem precisa de outra
configuração cria outra tarefa (a D19 deixa de existir).

**O caso do COTAHIST, como é hoje** (resposta à pergunta de 22/10/2026):

| Rotina | Quando roda | Com que entradas |
|---|---|---|
| COTAHIST **diário** | agendada 08:30 e 21:30, e na subida da adm-api se faltar o último pregão | nenhuma: do pregão seguinte ao último gravado até hoje (incremental) |
| COTAHIST diário, manual | `POST /b3/cotahist/diario` | sem nada = o mesmo incremental; ou `from`/`to` em datas (até 366 dias) |
| COTAHIST **anual** | só manual (`POST /b3/cotahist`) e na etapa 2 da carga inicial | `from`/`to` em anos (padrão 2010 até o ano corrente) |

Nenhuma rodada agendada usa entrada hoje. No modelo novo, ficaria por exemplo:

| Tarefa (instância) | Tipo | Horário | Atributos |
|---|---|---|---|
| `B3_DAILY_QUOTES` | `B3_DAILY_QUOTES` | 08:30 e 21:30 | nenhum (incremental) |
| `B3_DAILY_QUOTES_RELOAD` | `B3_DAILY_QUOTES` | sem horário | `FROM_DATE`, `TO_DATE` |
| `B3_YEARLY_QUOTES` | `B3_YEARLY_QUOTES` | sem horário | `FROM_YEAR = 2010`, `TO_YEAR` vazio (= ano corrente) |

**Estrutura proposta pelo Guilherme (22/10/2026)**, com as observações do levantamento:

| Tabela | Colunas do Guilherme | Observações do levantamento |
|---|---|---|
| `task_types` | `id`, `type` (o antigo `name` de `tasks`), `description`, `service`, `enabled`, `created_at`, `updated_at` | `type` único e com a regra de nome (1e); é a chave que o código usa para achar a rotina |
| `tasks` | `id`, `name` (editável), `cron`, `task_type_id` | (O1) `description` própria, opcional — a tarefa "recarga desde 2010" precisa dizer o que faz além do tipo (1f); vazia, a tela mostra a do tipo. (O2) `enabled` também na tarefa: desligar só a instância agendada sem desligar o tipo; o `enabled` do tipo desliga todas as instâncias de uma vez (ex.: fonte fora do ar). (O3) `created_at`/`updated_at`, padrão do projeto. (O4) `name` único e com a regra de nome. (O5) `deleted_at` — ver exclusão abaixo |
| `task_type_attributes` | `id`, `task_type_id`, `att_name`, `att_value` (padrão ou nulo) | (O6) para a tela montar o formulário e validar, faltam três colunas: `value_type` (ano, data, sim/não, texto, lista), `required` e `description`. Sem elas a tela mostra só caixas de texto. Mínimo, máximo e códigos válidos ficam no código do tipo. Único `(task_type_id, att_name)` |
| `task_attributes` | `id`, `task_id`, `att_name`, `att_value` — criados automaticamente a partir do tipo, com valor próprio | (O7) ligar a linha ao atributo do tipo (`task_type_attribute_id`, FK) em vez de repetir o `att_name`: o espelho não diverge (atributo que não existe no tipo não entra) e renomear o atributo no tipo não quebra as tarefas; o nome vem do tipo. Único `(task_id, task_type_attribute_id)` |

Regras do espelho:

- Tarefa nova: uma linha em `task_attributes` por atributo do tipo, com o valor padrão (pode ser mudado na hora).
- Atributo novo no tipo (por migration, quando um tipo ganhar uma opção): a mesma migration cria a linha nas tarefas
  que já existem, com o padrão.
- Atributo removido do tipo: sai das tarefas (`ON DELETE CASCADE`).
- Padrão alterado no tipo depois: **não** muda as tarefas que já existem — o valor delas é próprio.
- A rodada guarda a cópia dos valores com que rodou (`task_executions.parameters`).

Exclusão de tarefa (D15): apagar a instância não apaga o tipo (decidido). (O5) Como as rodadas apontam para a tarefa,
apagar a linha apagaria ou soltaria o histórico. Proposta: exclusão lógica (`tasks.deleted_at`) — some da
Configuração e da agenda, e o Acompanhamento continua mostrando as rodadas dela.

**Estrutura consolidada (22/10/2026, D24 + D28):**

| Tabela | Colunas |
|---|---|
| `task_types` | `id`, `type` (único, regra 1e; chave do código), `description`, `service` (`ADM_API`/`APP_API`), `enabled` (desliga todas as instâncias), `created_at`, `updated_at` |
| `task_type_attributes` | `id`, `task_type_id` (FK, cascade), `att_name` (regra 1e), `att_value` (padrão ou nulo), `value_type` (ano, data, sim/não, texto, lista), `required`, `description`, `created_at`, `updated_at`; único `(task_type_id, att_name)` |
| `tasks` | `id`, `name` (único, regra 1e, editável), `description` (opcional; vazia = a do tipo), `cron` (nulo = só sob demanda), `enabled`, `task_type_id` (FK), `deleted_at`, `created_at`, `updated_at` |
| `task_attributes` | `id`, `task_id` (FK, cascade), `task_type_attribute_id` (FK, cascade — nome, tipo do valor, obrigatório e descrição vêm daí), `att_value`, `created_at`, `updated_at`; único `(task_id, task_type_attribute_id)` |
| `task_executions` | como na seção 1.4, com `parameters` = cópia dos atributos da tarefa no início da rodada e o status `SKIPPED` (D20) |
| `task_execution_logs` | como na seção 1.4 (D6) |

Exclusão de tarefa (O5, parcial): sem rodadas, apagada de vez; com rodadas, `deleted_at` (some da Configuração e da
agenda; as rodadas continuam no Acompanhamento). Apagar a tarefa nunca apaga o tipo.

Consequências:

1. **Cadastro inicial:** os 25 itens da seção 1.2 viram 25 tipos, cada um com uma tarefa que reproduz o que roda hoje.
2. **Nome da tarefa editável** (revê a D17): o código acha o **tipo** pelo nome do tipo; a tarefa é achada pelo `id`.
   Renomear a tarefa deixa de quebrar o vínculo; o nome do tipo é que fica fixo.
3. **Cadastrar e Excluir** (D15) ficam mais perto de ter efetividade: cadastrar = escolher o tipo, nome, horário e
   atributos. Continuam sem efetividade neste escopo, como decidido.
4. **Trava de concorrência** (D20) é do **tipo** (D25, decidida): duas instâncias do mesmo tipo não rodam juntas; a
   que chega é `SKIPPED`.
5. **Curadoria** (23–25): cada correção pode ser uma instância nova ou a mesma instância com os atributos editados
   antes do Run (D27).
6. **Rotinas compostas** (D21): o tipo continua sendo o que a rotina faz hoje, com as etapas internas na ordem de
   hoje (ex.: proventos e, em seguida, as datas da CVM); uma etapa opcional vira atributo sim/não do tipo.

---

## 2. Passo 2 — menu "Tarefas"

- Menu lateral: `primevest-web/src/components/layout/Sidebar.tsx`. Hoje **nenhum item é condicionado a
  administrador**; a única tela que olha `user.admin` é Notificações (filtro de público).
- O front **só fala com a app-api**: o proxy do Vite (`vite.config.ts`) manda `/api` para a 8080 e nenhuma tela chama
  a adm-api (PENDENCIAS 26.4 a). A tela de Tarefas precisa de um caminho até a adm-api (D9).
- Proposta: item "Tarefas" só para administrador (`user.admin`), rota `/tasks` também protegida (D14).

## 3. Passo 3 — tela Tarefas

**Aba Acompanhamento:** uma tabela com as rodadas (`task_executions`) e as agendadas da semana (calculadas — D3):
nome, descrição (dica), status, data de vigência, início, fim, duração, origem do disparo, quem disparou; ações
**Run**, **Stop** (ativo só em `RUNNING`) e **Ver log** (conforme a D6).

**Aba Configuração:** a lista de `tasks` com **Run** (inicia uma rodada de qualquer tarefa, agendada ou não — D23),
Editar (horário, atributos, descrição; nome só leitura — D17) e Cadastrar/Excluir sem efetividade (D15).

O que cada ação exige, além da tela:

| Ação | O que falta no backend |
|---|---|
| Executar | um disparo por tarefa (hoje: 19 rotas em português/inglês, algumas juntando duas rotinas, nenhuma para a app-api); recusar se já estiver executando (nem toda rotina tem trava) |
| Parar | cancelamento cooperativo: a rotina confere o pedido de parada entre unidades de trabalho (emissor, ano, pregão, índice, fundo). Interromper a thread não basta — muitas esperas são `WebClient.block()` e travas. As rotinas são idempotentes (`docs/ADM_TASKS.md`): parar no meio e rodar de novo é seguro (D8) |
| Ver logs | resumo e erro gravados na rodada (`summary`, `error_message`) e as linhas que a rotina já escreve no log, capturadas por rodada |
| Histórico | `task_executions` |
| Editar horário | o horário passa a vir do banco: agendamento programático (`TaskScheduler` + `CronTrigger`) no lugar do `@Scheduled` fixo, reagendado quando a tarefa muda; o aviso "tarefa mudou" chega ao serviço dono (Redis, com releitura de segurança) (D10) |
| Editar nome | o nome é a chave que o código usa para achar a tarefa; renomear pela tela quebraria o vínculo, a menos que o código procure pelo `id` ou por uma chave fixa separada (D17) |

## 4. Passo 4 — nomes internos em inglês

Levantamento do que hoje está em português por dentro (a tela continua em português):

| Onde | Em português hoje | Já em inglês |
|---|---|---|
| Rotas do front | `/rentabilidade`, `/ativos`, `/ativos/:ticker`, `/emissoras` (redireciona), `/cadastro`, `/esqueci-senha`, `/termos`, `/privacidade` | `/dashboard`, `/portfolio`, `/transactions`, `/dividends`, `/strategy`, `/rebalance`, `/ceiling-prices`, `/goals`, `/indices`, `/integrations`, `/settings`, `/notifications` |
| Parâmetros de URL | `aba`, `periodo`, `de`, `ate`, `tipo`, `categoria`, `ativo`, `reserva`, `base`, `indices`, `acoes.*`/`fiis.*` | `ticker`, `position`, `settle`, `date` |
| API da app-api | — (`/api/v1/indices` é o plural latino, usado em inglês; `/indexes` seria a outra forma) | todas as demais |
| API da adm-api | `/proventos`, `/proventos/levantamento`, `/proventos/fnet-status`, `/tesouro`, `/cripto/historico`, `/b3/cotahist/diario`, `/indices` | `/closing`, `/logos`, `/catalog-audit`, `/issuers/*`, `/fixed-income-issuers*`, `/cvm/*`, `/bootstrap`, `/b3/cotahist` |
| Configuração | `primevest.proventos.*`, `primevest.tesouro.*`, `primevest.cripto-historico.*`, `primevest.cotahist-diario.*`, `primevest.abertura.*`, `primevest.emissoras.*` | `primevest.closing.*`, `primevest.indices.*`, `primevest.logos.*`, `primevest.catalog-audit.*`… |
| Canais Redis | `primevest:catalogo:ativos`, `primevest:proventos:sob-demanda` | — |
| Classes | `ProventosScheduler`, `ProventosCatalogService`… | a maioria |
| Valores de enum gravados | `NotificationKind` (`ROTINA_FALHOU`…), status `NAO_RESOLVIDA`/`RESOLVIDA`/`DISPENSADA` | `integration_runs.status` (`QUEUED`, `RUNNING`…) |

Para este escopo: rota `/tasks`, API `/api/v1/tasks`, tabelas `tasks`/`task_*`, classes `Task*`, status e origem em
inglês. O que já existe fica como está (D16, 22/10/2026) e foi para `docs/planos/PLANO_NOMES_EM_INGLES.md`, retomado em
escopo próprio.

---

## 5. Decisões em aberto

| # | Pergunta | Recomendação |
|---|---|---|
| D1 | Nome da tarefa em inglês (`B3_CLOSING`) ou português sem acento (`FECHAMENTO_B3`)? | **Decidido (22/10/2026):** inglês; a tela mostra a descrição em português |
| D2 | Status e origem da rodada: idioma no banco e na tela | **Decidido (22/10/2026):** status em inglês **no banco e na tela** (`SUCCEEDED`, `FAILED`, `RUNNING`, `CANCELLED`, e `SCHEDULED` para a agendada calculada); a origem (`SCHEDULE`, `MANUAL`, `STARTUP`) segue o mesmo critério |
| D3 | "Agendada": calculada do cron na leitura (próximos 7 dias) ou gravada como linha antecipada? | **Decidido (22/10/2026):** calculada |
| D4 | O que vira tarefa / rodada no histórico | **Decidido (22/10/2026):** entram agendada, manual e recuperação na subida; **a curadoria entra** como tarefas 23–25, com as entradas como atributos (o Guilherme, revendo a recomendação); ficam fora o worker de 5 s e os ouvintes do Redis |
| D5 | Cadastro inicial | **Decidido (22/10/2026):** uma tarefa por rotina (fechamento B3 e cripto separados) — 25 com a curadoria (D4) |
| D6 | "Ver logs": linha a linha, só resumo + erro, ou meio-termo? | **Decidido (22/10/2026): meio-termo** ("só resumo e erro" foi a primeira resposta, revista no mesmo dia: o resumo ficaria pobre; teto e retenção podem ser ajustados na prática): capturar automaticamente as linhas que a rotina **já escreve** no log (SLF4J), marcadas com o id da rodada; INFO, WARN e ERROR (DEBUG fora); teto de 2.000 linhas por rodada — passado o teto, WARN e ERROR continuam entrando e o INFO é só contado ("N linhas omitidas"); o resumo é a linha de conclusão que a rotina já escreve; linhas guardadas 30 dias, a rodada 1 ano |
| D7 | Retenção do histórico das rodadas | **Decidido (22/10/2026), junto com a D6:** rodadas 1 ano; linhas de log 30 dias |
| D8 | Parar: cancelamento cooperativo — o "Parar" só registra o pedido; a própria rotina confere o pedido entre uma unidade de trabalho e a seguinte (um emissor, um ano, um pregão, um índice) e encerra ali, sem deixar a unidade em curso pela metade (matar a thread poderia cortar uma gravação ou um download) | **Decidido (22/10/2026): cooperativo em todas as rotinas**; enquanto uma rotina não conferir o pedido, "Parar" fica desabilitado nela |
| D9 | A API `/api/v1/tasks` mora na adm-api ou na app-api? | **Decidido (22/10/2026): adm-api.** O front ganha um caminho para a 8081 (proxy de `/api/v1/tasks`); as tarefas da app-api recebem executar/parar pelo Redis |
| D10 | Horário | **Decidido (22/10/2026):** o banco manda e o `application.yml` vira só o valor semeado pela migration; edição por editor (diário, dias da semana, mensal + horário), com o cron na dica |
| D11 | Liga/desliga por tarefa | **Decidido (22/10/2026):** `tasks.enabled` substitui os `primevest.<rotina>.enabled`; a chave geral `primevest.scheduling.enabled` continua |
| D12 | Executar com entradas | **Decidido (22/10/2026):** as entradas são atributos da tarefa, no modelo tipo × tarefa (D24) |
| D13 | Atributos | **Decidido (22/10/2026):** só a estrutura, nenhum preenchido (a D12 não muda isso: parâmetro da rodada e atributo da tarefa são coisas diferentes — seção 1.6) |
| D14 | Menu | **Decidido (22/10/2026):** "Tarefas" só para administrador (item e rota), último item do menu, ícone de relógio |
| D15 | Cadastrar, editar e excluir | **Decidido (22/10/2026):** agem sobre a **instância** (tarefa); excluir a tarefa não apaga o tipo. Cadastrar/Excluir seguem sem efetividade neste escopo (botões desabilitados, "em breve"); forma da exclusão: O5 da seção 1.7 |
| D16 | Passo 4 sobre o que já existe | **Decidido (22/10/2026): só Tarefas nasce em inglês.** As rotas `/api/v1/admin/*` ficam como estão (o script `admin.mjs` continua valendo); o resto foi para `docs/planos/PLANO_NOMES_EM_INGLES.md`, escopo próprio |
| D17 | Editar o nome | **Decidido (22/10/2026):** o nome da tarefa é **editável** (o código acha a tarefa pelo `id` e o tipo por `task_types.type`) — revista com a D24 |
| D18 | Notificação `ROTINA_FALHOU` | **Decidido (22/10/2026):** continua, com link para a rodada na tela de Tarefas |
| D19 | Mudar atributos só para uma rodada | **Superada pela D24 (22/10/2026):** outra configuração = outra tarefa do mesmo tipo |
| D20 | Rodada que encontra a tarefa executando | **Decidido (22/10/2026):** registrada com o status `SKIPPED` e o motivo (ex.: "já em execução desde 19:55"); tarefas diferentes rodam juntas normalmente |
| D21 | Rotinas compostas | **Decidido (22/10/2026):** resolvida pelos atributos do tipo (D24): o tipo mantém as etapas na ordem de hoje; atributos por etapa com padrão (ex.: `STATEMENTS_FROM_YEAR`, `FII_FROM_YEAR` vazios = incremental; `WITH_CVM_PAYMENT_DATES = true`) |
| D22 | Endpoints `/api/v1/admin/*` pelo mesmo executor, gerando rodada no histórico | **Decidido (22/10/2026):** sim (inclui os 3 de curadoria, que seguem respondendo com o resultado) |
| D23 | Ação Run | **Decidido (22/10/2026):** Run em **todas** as tarefas, na aba Configuração e na aba Acompanhamento; Stop no Acompanhamento, ativo só com a tarefa em execução (pedido do Guilherme) |
| D24 | Tipo × tarefa | **Decidido (22/10/2026):** a rotina é o **tipo** (template); a tarefa é uma **instância** com nome, horário e atributos próprios; duas tarefas do mesmo tipo podem rodar a mesma rotina com atributos diferentes (seção 1.7) |
| D25 | Trava de concorrência | **Decidido (22/10/2026):** por **tipo** |
| D26 | Definição dos atributos aceitos por tipo | **Decidido (22/10/2026):** `task_type_attributes` com o valor padrão (estrutura do Guilherme, seção 1.7); em aberto as colunas a mais da O6 |
| D27 | Curadoria | **Decidido (22/10/2026):** as entradas são atributos da tarefa (espelhados do tipo); o modelo permite editar e rodar a mesma tarefa ou criar outra instância |
| D28 | Observações O1–O7 sobre a estrutura (seção 1.7) | **Decidido (22/10/2026):** O1, O2, O3, O4, O6 e O7 aceitas (O7 já carrega `value_type`/`required`/`description` da O6 pela FK); O5 **parcial**: tarefa sem rodadas é apagada de vez, tarefa com rodadas tem exclusão lógica (`deleted_at`). Regras do espelho aceitas |

---

## 6. Planejamento (22/10/2026)

> ETAPA PLANEJAMENTO: só o plano (REGRAS 2.1). Implementação só com ordem explícita, uma etapa por vez, cada uma
> fechada com a bateria verde antes da próxima (REGRAS 2.3).

### 6.1 Arquitetura

```
                 tela /tasks (admin)
                        │  /api/v1/tasks  (proxy do Vite e do nginx → 8081)
                        ▼
   adm-api ── TaskController ── TaskExecutor (core) ── handlers dos tipos ADM_API
      │            │                    │
      │            │ Run de tipo APP_API: Redis primevest:tasks:commands
      │            ▼
      │      app-api ── TaskExecutor (core) ── handlers dos tipos APP_API
      │
   banco: task_types · task_type_attributes · tasks · task_attributes · task_executions · task_execution_logs
          (Stop = task_executions.stop_requested_at, lido pela rotina no serviço onde ela roda)
```

| Peça | Onde | Papel |
|---|---|---|
| Entidades e repositórios (`com.primevest.domain.task`) | core | as 6 tabelas |
| `TaskHandler` (interface) | core | um bean por tipo, no serviço dono: `type()`, `run(TaskRunContext)` e, se houver, `recoverOnStartup()` |
| `TaskRunContext` | core | atributos da rodada (já convertidos pelo `value_type`), `checkStop()`, `summary(texto)` |
| `TaskExecutor` | core | uma rodada: trava por tipo (D25), linha `RUNNING`, MDC com o id da rodada, chama o handler, fecha em `SUCCEEDED`/`FAILED`/`CANCELLED`/`SKIPPED`, notificação `ROTINA_FALHOU` com link (D18) |
| `TaskScheduleRegistry` | core, ligado em cada serviço | agenda as tarefas com cron dos tipos do serviço (`ThreadPoolTaskScheduler` + `CronTrigger`, fuso de São Paulo) |
| `TaskLogAppender` | core | grava as linhas da rodada (D6) |
| `TaskController` (`/api/v1/tasks`) | adm-api | a API da tela (D9) |
| `TaskCommandListener` | app-api | recebe Run e "tarefa mudou" pelo Redis |

### 6.2 Etapas

**T1 — Banco e domínio (core)**

- `V61__tasks.sql`: as 6 tabelas da estrutura consolidada (seção 1.7), com os CHECKs de nome (`^[A-Z0-9]+(_[A-Z0-9]+)*$`)
  em `task_types.type`, `tasks.name` e `task_type_attributes.att_name`; `value_type` em (`YEAR`, `DATE`, `BOOLEAN`,
  `TEXT`, `LIST`); status em (`RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `SKIPPED`); `trigger_type` em (`SCHEDULE`,
  `MANUAL`, `STARTUP`); índices da seção 1.4; `tasks.name` único entre as não excluídas.
- `V62__seed_tasks.sql`: os tipos, os atributos de cada tipo (6.3) e uma tarefa por tipo, com o cron que hoje está no
  `application.yml` e os atributos espelhados. FK resolvida por nome (convenção do projeto).
- Entidades, repositórios e o serviço de cadastro: espelho dos atributos ao criar tarefa; exclusão definitiva sem
  rodadas e lógica com rodadas (O5) — o serviço existe, a tela não usa ainda (D15).
- Testes: `SchemaTest`/repositório (CHECKs, unicidade, cascata, espelho).

**T2 — Executor e migração das rotinas para handlers**

- `TaskExecutor` + `TaskHandler` + `TaskRunContext` (core).
- Os 26 tipos viram handlers (25 da seção 1.2 + `TASK_HISTORY_CLEANUP`, D34), chamando os services de hoje. As classes
  `scheduler/*` (11 na adm-api, 3 na app-api) saem; a lógica de "recuperação na subida" de cada uma vai para o
  `recoverOnStartup()` do handler, disparado com `trigger_type = STARTUP`.
- Rodada que encontra o tipo executando: `SKIPPED` com o motivo (D20). As travas internas que já existem nos services
  (`tryLock`) passam a avisar o executor em vez de só logar, para a rodada não sair `SUCCEEDED` sem ter feito nada.
- Na subida de cada serviço: rodadas `RUNNING` dos tipos dele viram `FAILED` ("serviço reiniciado durante a execução").
- `AdminController`: rotas e respostas mantidas (D16), mas pelo executor (D22); a resposta 202 ganha o `executionId`.
  As 3 de curadoria continuam síncronas e devolvendo o resultado, agora registradas como rodada.
- O ouvinte "preciso do histórico" [A8] respeita `task_types.enabled` de `DIVIDENDS_SYNC`, como hoje respeita
  `primevest.proventos.enabled` (D30). Não gera rodada (D4).
- Testes: executor (trava, `SKIPPED`, falha, órfã), um IT por grupo de handler com as fontes mockadas de hoje.

**T3 — Agendamento pelo banco**

- `TaskScheduleRegistry`: na subida, agenda cada tarefa ativa (`tasks.enabled` e `task_types.enabled`, sem
  `deleted_at`) com cron, dos tipos do serviço; reagenda ao receber "tarefa mudou" e relê tudo a cada 5 minutos (rede de
  segurança do Redis, D29). A chave geral `primevest.scheduling.enabled` continua desligando tudo (testes, pilha de teste).
- Saem do `application.yml` os `*.cron` e os `primevest.<rotina>.enabled` das rotinas (D10, D11). Ficam o
  `fixedDelay` do worker das integrações [B5] e a recuperação da fila [B6], que não são tarefas.
- Testes: reagendamento ao editar, tarefa desligada não agenda, chave geral.

**T4 — Parar e log da rodada**

- `TaskRunContext.checkStop()`: lê `stop_requested_at` (no máximo uma consulta a cada 2 s) e encerra a rodada com
  `CANCELLED`. Pontos de parada em cada rotina, entre unidades de trabalho (6.4). Enquanto um tipo não tiver ponto de
  parada, o Stop fica desabilitado nele (D8) — `task_types` não precisa de coluna: o handler declara `stoppable()`.
- `TaskLogAppender` (Logback, ligado por código no core): grava as linhas com o MDC da rodada; INFO, WARN, ERROR; teto de
  2.000 por rodada, depois só WARN/ERROR e a contagem do INFO omitido; gravação em lote (buffer, a cada 1 s), para o log
  não pesar na rotina. O MDC acompanha as threads que a rotina abre (downloads paralelos do FNET, threads virtuais).
- `summary`: cada handler grava o resumo com os números da linha de conclusão que a rotina já escreve.
- `TASK_HISTORY_CLEANUP` (D34): apaga linhas de log com mais de 30 dias e rodadas com mais de 1 ano (D7).
- Testes: parar no meio (rotina com fonte lenta mockada), teto do log, MDC em thread filha, limpeza.

**T5 — API `/api/v1/tasks` (adm-api)**

| Método | Rota | O quê |
|---|---|---|
| GET | `/api/v1/tasks` | tarefas (não excluídas) com tipo, cron, `enabled`, atributos, próxima execução, última rodada |
| GET | `/api/v1/tasks/types` | tipos com os atributos aceitos (`value_type`, `required`, `description`, padrão) |
| PUT | `/api/v1/tasks/{id}` | edita nome, descrição, cron, `enabled` e valores dos atributos; valida pelo tipo; publica "tarefa mudou" |
| POST | `/api/v1/tasks/{id}/run` | 202 com o `executionId`; tipo da app-api: comando pelo Redis e espera até 5 s a rodada aparecer (sem resposta: 503 "app-api não respondeu") |
| POST | `/api/v1/tasks/executions/{id}/stop` | grava `stop_requested_at`; 422 se não estiver `RUNNING` ou o tipo não for `stoppable` |
| GET | `/api/v1/tasks/executions?from=&to=&type=&status=&page=` | Acompanhamento: rodadas do período + as agendadas calculadas (`SCHEDULED`) até `to` (D3) |
| GET | `/api/v1/tasks/executions/{id}` | detalhe: atributos usados, resumo, erro, quem pediu |
| GET | `/api/v1/tasks/executions/{id}/logs?level=&page=` | linhas da rodada |

- Só administrador (403 aos demais, mesma regra do `AdminController`). Cadastrar e excluir não ganham rota neste escopo
  (D15).
- Proxy: no Vite, `/api/v1/tasks` → adm-api (`VITE_ADM_API_TARGET`, padrão `http://localhost:8081`; no compose
  `http://adm-api:8081`), declarado antes do `/api`; no nginx do estágio `runtime`, `location /api/v1/tasks` →
  `adm-api:8081`.
- Testes de controller (401, 403, 400, 404, 422) e IT de ponta a ponta (Run → rodada → Stop → `CANCELLED`).

**T6 — Menu e tela (front)**

- `Sidebar.tsx`: item "Tarefas" (`Clock`, lucide), último, só com `user.admin` (D14). Rota `/tasks` em `App.tsx` atrás
  de uma guarda de administrador (sem admin: volta ao Dashboard).
- `api/tasks.ts`, tipos em `types/`, `TasksPage` com abas na URL `?tab=monitoring|configuration` (parâmetros em inglês,
  D16).
- **Acompanhamento**: tabela (nome, tipo, status com o valor em inglês — D2, vigência, início, fim, duração, origem,
  quem pediu); filtros de período (padrão: 7 dias para trás e 7 para frente — D35), tipo e status; ações Run, Stop
  (só `RUNNING` e `stoppable`) e Ver log (modal: atributos usados, resumo, erro, linhas com filtro por nível); atualiza a
  cada 5 s só enquanto houver `RUNNING`, e um botão Atualizar (D33).
- **Configuração**: lista das tarefas com Run e Editar (modal: nome, descrição, horário pelo editor da D10 com o cron no
  (i), ligado/desligado, atributos com o campo pelo `value_type`); Cadastrar e Excluir desabilitados com "em breve"
  (D15). Run em tarefa com atributo obrigatório vazio: a tela avisa antes (a API recusa com 422).
- Padrões do projeto: `TABLE_ACTION_CLASS`, `InfoTooltip`, toasts, overrides do tema claro, paginação de Transações.

**T7 — Validação e documentação**

- `mvn clean verify`, `npm run lint`, `npm run build`, subir o compose, conferir no navegador (REGRAS 4).
- `Claude.md`, `docs/SCHEDULE_TASKS.md` (passa a apontar para a tela e para os tipos; corrige os dois achados de
  22/10), `docs/ADM_TASKS.md` (endpoints pelo executor), `docs/PENDENCIAS.md`, README e o andamento deste plano.

### 6.3 Atributos dos tipos no seed (V62)

Vazio = incremental, como a rodada agendada de hoje.

| Tipo | Atributo | `value_type` | Padrão | Obrigatório |
|---|---|---|---|---|
| `DIVIDENDS_SYNC` | `FNET_REREAD_ALL` | BOOLEAN | false | sim |
| | `WITH_CVM_PAYMENT_DATES` | BOOLEAN | true | sim |
| `FNET_STATUS_SYNC` | `ONLY_MISSING` | BOOLEAN | false | sim |
| | `WITH_DIVIDENDS_REREAD` | BOOLEAN | true | sim |
| `CVM_STATEMENTS_IMPORT` | `STATEMENTS_FROM_YEAR`, `STATEMENTS_TO_YEAR` | YEAR | vazio | não |
| | `FII_FROM_YEAR`, `FII_TO_YEAR` | YEAR | vazio | não |
| | `FORCE` | BOOLEAN | false | sim |
| | `WITH_FII_MONTHLY` | BOOLEAN | true | sim |
| `TREASURY_PRICES` | `FROM_YEAR` | YEAR | vazio | não |
| `B3_DAILY_QUOTES` | `FROM_DATE`, `TO_DATE` | DATE | vazio | não |
| `MARKET_INDICES` | `INDEX_CODES` | LIST | vazio (todos) | não |
| | `FROM_YEAR`, `TO_YEAR` | YEAR | vazio | não |
| `ASSET_LOGOS` | `ONLY_MISSING` | BOOLEAN | true | sim |
| `FIXED_INCOME_ISSUERS` | `WITH_LOGOS` | BOOLEAN | true | sim |
| | `LOGOS_REFRESH_ALL` | BOOLEAN | false | sim |
| `B3_YEARLY_QUOTES` | `FROM_YEAR` | YEAR | 2010 | sim |
| | `TO_YEAR` | YEAR | vazio (ano corrente) | não |
| `CVM_PAYMENT_DATES` | `FROM_YEAR` | YEAR | 2022 | sim |
| | `TO_YEAR` | YEAR | vazio (ano corrente) | não |
| `FII_CNPJ_CURATION` | `LINES` | TEXT | vazio | sim (`TICKER;CNPJ` por linha) |
| `ASSET_ISSUER_LINK` | `TICKER` | TEXT | vazio | sim |
| | `ISSUER_CNPJ` | TEXT | vazio (= desfaz a ligação) | não |
| | `SHARE_CLASS` | TEXT | vazio | não |
| `UNIT_COMPOSITION` | `TICKER` | TEXT | vazio | sim |
| | `COMPOSITION` | TEXT | vazio (`ON=1;PNA=2`) | sim |
| `TASK_HISTORY_CLEANUP` | `LOG_RETENTION_DAYS` | TEXT (número) | 30 | sim |
| | `EXECUTION_RETENTION_DAYS` | TEXT (número) | 365 | sim |
| os demais (12) | — | | | |

Diferenças que o seed corrige em relação ao disparo manual de hoje: o `DIVIDENDS_SYNC` passa a rodar a etapa da CVM
também pelo Run (padrão `true`), e o `FNET_STATUS_SYNC` faz as duas partes, como o agendado de domingo; o endpoint antigo
continua fazendo o que faz hoje (passa os atributos equivalentes ao executor).

### 6.4 Pontos de parada (T4)

| Tipo | Unidade entre as quais confere o Stop |
|---|---|
| `DIVIDENDS_SYNC`, `FNET_STATUS_SYNC` | emissor / fundo |
| `B3_DAILY_QUOTES`, `B3_YEARLY_QUOTES` | pregão / ano |
| `CVM_STATEMENTS_IMPORT`, `CVM_PAYMENT_DATES` | ano / documento |
| `MARKET_INDICES` | índice e ano |
| `TREASURY_PRICES` | ano |
| `B3_CLOSING`, `CRYPTO_PRICE_HISTORY`, `ASSET_LOGOS`, `FIXED_INCOME_ISSUERS`, `ISSUERS_IMPORT`, `ISSUERS_LINK` | ativo / emissor / fonte |
| `BOOTSTRAP` | etapa |
| `POSITIONS_REFRESH` | posição |
| `CRYPTO_CLOSING`, `CATALOG_AUDIT`, `CVM_QUARTERS`, `PAYMENT_DATE_SURVEY`, `DAILY_OPENING`, `INTEGRATIONS_DAILY_SYNC`, curadoria (3), `TASK_HISTORY_CLEANUP` | rodadas curtas ou de uma chamada só: entre as etapas internas quando houver; senão `stoppable = false` |

### 6.5 Decisões do planejamento

| # | Pergunta | Recomendação |
|---|---|---|
| D29 | Comandos para a app-api | **Decidido (22/10/2026):** Run e "tarefa mudou" pelo Redis (`primevest:tasks:commands`), releitura a cada 5 min como rede de segurança; Stop pelo banco |
| D30 | Desligar o tipo `DIVIDENDS_SYNC` | **Decidido (22/10/2026):** `task_types.enabled = false` desliga as rodadas **e** o pedido sob demanda [A8], como o `primevest.proventos.enabled=false` de hoje |
| D31 | Recuperação na subida | **Decidido (22/10/2026):** roda nas tarefas ativas e com cron do tipo, com os atributos delas |
| D32 | Atributos do seed | **Decidido (22/10/2026):** como na tabela 6.3 |
| D33 | Atualização do Acompanhamento | **Decidido (22/10/2026):** consulta a cada 5 s só enquanto houver rodada `RUNNING` (como Integrações) e botão Atualizar |
| D34 | Limpeza da retenção (D7) | **Decidido (22/10/2026):** tarefa própria, `TASK_HISTORY_CLEANUP` (adm-api, diária 04:00), visível na tela, com os prazos em atributos |
| D35 | Período padrão do Acompanhamento | **Decidido (22/10/2026):** 7 dias para trás e 7 para frente |
| D36 | Tipo pela tela | **Decidido (22/10/2026):** não neste escopo: o tipo muda por migration; `enabled` do tipo só leitura na tela |
| D37 | Recuperação na subida a cada subida (achado da validação: o Tesouro e a abertura do dia rodavam a cada reinício) | **Decidido (22/10/2026): no máximo uma vez por dia por tarefa** — a subida pula a tarefa que já teve rodada no dia (qualquer origem e status, fuso de São Paulo) |
| D38 | Quem roda na subida | **Decidido (22/10/2026), proposta do Guilherme: flag na tarefa, `tasks.run_on_startup`** (V65), editável na tela; a subida roda as tarefas marcadas, ativas e sem rodada no dia, se o handler disser que há o que recuperar (`needsRecovery()`, padrão: sempre). O seed marca as que já tinham recuperação: `B3_CLOSING`, `CRYPTO_CLOSING`, `TREASURY_PRICES`, `B3_DAILY_QUOTES`, `MARKET_INDICES`, `DAILY_OPENING`. Deixa de exigir horário (D31) |

### 6.6 Riscos

- **Troca do `@Scheduled` pelo agendamento do banco** mexe em todas as rotinas de uma vez: a T2/T3 é a etapa grande. Um
  erro deixa uma rotina sem rodar em silêncio — por isso o Acompanhamento mostra a agendada que passou do horário sem
  rodada, e o IT confere que cada tarefa do seed está agendada.
- **Hot reload** (`dev-watch`): salvar classe do core reinicia os dois serviços; a rodada em curso vira `FAILED` na
  subida, o que é o comportamento esperado.
- **Volume de log**: o lote e o teto seguram o banco; o `fnet=todos` (~40 min) é o caso a medir na T4.

## 7. Quem faz

| O quê | Claude | Guilherme |
|---|---|---|
| Levantamento e plano | ✓ | revisa |
| Decisões D1–D28, D37–D38 | recomenda | decidiu (22/10/2026) |
| Decisões do planejamento D29–D36 | recomenda | decide |
| Implementação T1–T7 (com ordem explícita, uma etapa por vez) | ✓ | — |
| Conferência visual da tela (T6) | prepara | confere no navegador |

## 8. Andamento

- 22/10/2026 — Levantamento (este documento). Achados de documentação fora do escopo, só registro: `SCHEDULE_TASKS.md`
  não traz o histórico de cripto (19:30) nem as emissoras de RF (dia 1, 05:30); o [B7] ainda descreve a liquidação
  automática, revogada em 15/10/2026.
- 22/10/2026 — Decisões do Guilherme: D9 adm-api; D6 só resumo e erro (sai `task_execution_logs`, entra
  `task_executions.summary`); D16 só Tarefas em inglês, com o plano separado `docs/planos/PLANO_NOMES_EM_INGLES.md`. D8 explicada
  na tabela, ainda em aberto.
- 22/10/2026 — D8 decidida (cooperativo em todas). D6 reaberta: proposta de meio-termo, capturando as linhas que as rotinas
  já escrevem no log, com teto por rodada.
- 22/10/2026 — D6 decidida: meio-termo (linhas INFO/WARN/ERROR que a rotina já escreve, capturadas por rodada, teto de
  2.000 com WARN/ERROR sempre gravados, linhas 30 dias, rodadas 1 ano); ajustes na prática se preciso.
- 22/10/2026 — Decididas D1, D2 (status em inglês também na tela), D3, D5, D10, D11, D13, D14 (ícone de relógio), D15, D17
  e D18; D4 decidida exceto a curadoria. D12: levantamento das implicações (seção 1.6) e decisões novas D19–D22.
- 22/10/2026 — D4 revista: a curadoria entra (tarefas 23–25, entradas como atributos). D22 decidida. D23 nova (Run em todas as
  tarefas, nas duas abas; Stop só em execução). D20 explicada (a trava é da mesma tarefa). D12/D19/D21 reescritas: entradas
  como atributos, com a opção de mudar só para a rodada.
- 22/10/2026 — D20 decidida (`SKIPPED`). Modelo tipo × tarefa do Guilherme (D24, seção 1.7): D12 resolvida, D19 superada,
  D17 pode ser revista; D21 reescrita; novas D25 (trava por tipo), D26 (definição dos atributos) e D27 (curadoria).
- 22/10/2026 — Decididas D25 (trava por tipo), D17 (nome editável), D15 (sobre a instância; excluir não apaga o tipo),
  D21/D26/D27 pela estrutura do Guilherme (`task_types`, `tasks`, `task_type_attributes`, `task_attributes` espelhada).
  Observações O1–O7 sobre a estrutura em aberto (D28).
- 22/10/2026 — D28 decidida (O1–O7; O5 parcial: sem rodadas apaga, com rodadas exclusão lógica) e regras do espelho
  aceitas. Estrutura consolidada na seção 1.7. Todas as decisões do levantamento respondidas.
- 22/10/2026 — ETAPA Planejamento: seção 6 (arquitetura, etapas T1–T7, atributos do seed, pontos de parada, riscos) e
  decisões D29–D36.
- 22/10/2026 — Decididas D29, D31, D32, D35 e D36. D30 e D34 explicadas; D33 revista (consulta só com rodada em
  execução + botão Atualizar).
- 22/10/2026 — Decididas D30, D33 e D34. Todas as decisões respondidas. Execução autorizada pelo Guilherme.
- 22/10/2026 — **T1 concluída.** `V61__tasks.sql` (6 tabelas, CHECKs de nome, status, origem e `value_type`; nome da
  tarefa único entre as vivas; `task_executions` sem cascata a partir da tarefa) e `V62__seed_tasks.sql` (26 tipos —
  23 da adm-api, 3 da app-api —, 31 atributos, 26 tarefas com os horários do `application.yml`, espelho). Pacote
  `com.primevest.domain.task` no core: entidades, repositórios, `TaskAttributeValues`, `TaskNames`, `TaskTime` (fuso de
  São Paulo) e `TaskCatalogService` (criar com espelho, editar validando nome, cron e valor pelo tipo, excluir de vez ou
  lógico). Desvios do plano: `value_type` ganhou `INTEGER` (prazos da limpeza); `task_executions` ganhou
  `stop_requested_by` e `log_lines_omitted`; a obrigatoriedade do atributo é conferida na rodada, não ao salvar (a
  curadoria nasce com os atributos obrigatórios vazios). Testes: `SchemaTest` (V62, seed), `TaskAttributeValuesTest` (3),
  `TaskCatalogServiceTest` (3). Core: 197 testes verdes em `mvn clean verify`.
- 22/10/2026 — **T2 concluída.** Core: `TaskHandler`, `TaskRunContext` (atributos tipados, `checkStop()`, resumo,
  resultado da rodada síncrona), `TaskExecutor` (bean `taskRunExecutor` — `taskExecutor` é apelido do Spring Boot):
  rodada manual, por tipo com os parâmetros do endpoint, síncrona (curadoria) e agendada; trava por tipo com `SKIPPED`;
  `TaskSkippedException` (a rotina achou a mesma rotina rodando por outro caminho — os `tryLock` que devolviam `null`);
  `TaskStoppedException` → `CANCELLED`; falha → `FAILED` + `ROTINA_FALHOU` com o link `/tasks?tab=monitoring&execution=`;
  `TaskStartup` (órfãs → `FAILED`; recuperação na subida por tipo, cada um na sua thread); `TaskSummaries` (resumo a
  partir do `Result` de cada service). adm-api: 23 handlers em `com.primevest.adm.task`; `AdminController` com as mesmas
  rotas e respostas, pelo executor, + `executionId`; [A8] respeita `task_types.enabled` do `DIVIDENDS_SYNC` (D30).
  app-api: 3 handlers em `com.primevest.task`. As 14 classes `@Scheduled` saíram; no lugar, as pontes provisórias
  `AdmTaskCron`/`AppTaskCron` (mesmos horários, até a T3). `primevest.tasks.service` em cada `application.yml`.
  Desvios: o seed ganhou `WITH_STATEMENTS` (`CVM_STATEMENTS_IMPORT`) e `WITH_ISSUERS` (`FIXED_INCOME_ISSUERS`), para os
  endpoints antigos rodarem só uma parte (33 atributos); a verificação da subida da carteira [B2] continua fora das
  tarefas (roda em todo perfil, inclusive nos testes); `MARKET_INDICES` com só o último ano = desde o início de cada
  índice (o `/indices` sem `recent`). Testes: `TaskExecutorTest` (8), `FnetStatusSyncTaskTest` (2, no lugar do
  `FnetStatusSchedulerTest`). `mvn clean verify`: core 205; integration 103; adm-api 118 + 73; app-api 210 + 263 —
  todos verdes.
- 22/10/2026 — **T3 concluída.** Core: `TaskScheduleRegistry` (um `CronTrigger` no fuso de São Paulo por tarefa ativa
  com horário e com handler no serviço; o disparo abre uma thread virtual e chama o executor; releitura a cada 5 min),
  `TaskCommand` (`RELOAD`/`RUN`, canal `primevest:tasks:commands`), `TaskCommandPublisher` (Redis em cada serviço, só
  log com as mensagens desligadas), `TaskCommands` (reagenda na hora no próprio serviço, pelo Redis no outro; recebe
  os comandos). As pontes `AdmTaskCron`/`AppTaskCron` saíram; os `*.cron` e `*.enabled` das rotinas saíram dos
  `application.yml` (o `primevest.proventos.enabled` da app-api ficou como interruptor técnico do pedido sob demanda —
  os testes desligam; quem desliga de verdade é o tipo `DIVIDENDS_SYNC`, D30). Teste: `TaskScheduleRegistryTest` (2:
  agenda só o que deve e reagenda ao editar; o horário dispara a rodada `SCHEDULE` com a vigência no minuto).
  `mvn clean verify`: core 207; integration 103; adm-api 118 + 73; app-api 210 + 263 — verdes. Dev: V61/V62 aplicadas
  na subida; agendamento com 13 tarefas na adm-api e 3 na app-api; recuperação na subida rodou `TREASURY_PRICES` e
  `DAILY_OPENING` como rodadas `STARTUP`.
- 22/10/2026 — **T4 concluída.** Parada cooperativa: `TaskRun` (a rodada da thread; `TaskRun.checkStop()` nos services,
  sem efeito fora de uma rodada; `propagate`/`propagateCall` levam a rodada e o MDC às threads que a rotina abre —
  downloads do FNET, dos relatórios da CVM e dos logos). Pontos de parada (6.4) em proventos (emissor), situação do FNET
  (fundo), COTAHIST anual e diário (ano / pregão), balanços (ano e documento), informe de FII (ano), datas da CVM (ano e
  relatório), índices (índice e ano), fechamento B3 (ativo), histórico de cripto (moeda), logos dos ativos e de RF,
  carga inicial (etapa) e `POSITIONS_REFRESH` (posição — numa transação só: o Stop desfaz a rodada inteira); 13 tipos
  com `stoppable()`. Log da rodada: `TaskLogCapture` (appender do Logback ligado por código; linhas com o MDC da rodada,
  INFO+, teto de 2.000 com WARN/ERROR sempre, contagem do omitido, gravação em lote a cada 1 s; o console não muda). A
  limpeza (`TASK_HISTORY_CLEANUP`) já estava na T2. Testes: `TaskExecutorTest` +2 (log com thread filha e teto; Stop
  pelo ponto estático). `mvn clean verify`: core 209; integration 103; adm-api 118 + 73; app-api 210 + 263 — verdes.
- 22/10/2026 — **T5 concluída.** `TaskController` (adm-api, `/api/v1/tasks`): listar tarefas e tipos, editar (reagenda
  depois do commit), Run (local ou pelo Redis, com espera de 5 s e 503 sem resposta), rodadas do período + agendadas
  calculadas, detalhe, log paginado e Stop. Proxy: Vite (`VITE_ADM_API_TARGET`, regra antes da `/api`), nginx do
  runtime (`location /api/v1/tasks`) e `web` dependendo da `adm-api` no compose. Desvios: V63
  (`task_types.stoppable`, gravado na subida por cada serviço — a adm-api precisa saber dos tipos da app-api) e V64
  (`task_type_attributes.position`: o `INSERT ... SELECT` da V62 não garantiu a ordem dos atributos). Teste:
  `TaskControllerIT` (5). `mvn clean verify`: core 209; integration 103; adm-api 118 + 78; app-api 210 + 263 — verdes
  (a primeira rodada da app-api caiu por "Could not find a valid Docker environment" durante a recriação do contêiner
  do front; repetida, verde).
- 22/10/2026 — **T6 concluída.** Menu "Tarefas" (relógio, último, só `user.admin`), rota `/tasks` atrás de `AdminOnly`,
  título no cabeçalho. `TasksPage`: abas `?tab=monitoring|configuration`; Acompanhamento (filtros de período, tipo e
  status; abre na página de "agora", com a faixa entre as agendadas e as que já começaram; Run com confirmação, Stop,
  Ver log; 5 s só com rodada em execução + Atualizar); Configuração (horário legível, próxima, situação, última rodada;
  Run, Editar, Nova/Excluir desabilitados). `TaskEditModal` (editor de horário: sem horário, todo dia — vários horários
  com o mesmo minuto —, dias da semana, mensal, cron; atributos pelo `value_type`), `TaskExecutionModal`
  (`?execution=` abre direto). `utils/cron.ts`. `tsc -b`, `npm run lint` (0 erro, 0 aviso) e `vite build` ok.
- 22/10/2026 — **T7 concluída.** Conferência no navegador (logado como administrador): Acompanhamento com as rodadas de
  hoje e a agenda da semana; Configuração; Run do `TASK_HISTORY_CLEANUP` (rodada 12, resumo e 2 linhas de log no modal);
  Run do `POSITIONS_REFRESH` (tarefa da app-api, pelo Redis: rodada 13, `positions=41`, 3 linhas de log gravadas pela
  app-api); editor de horário do `B3_DAILY_QUOTES` ("Todo dia às 08:30 e 21:30"). Documentação: `Claude.md`,
  `docs/SCHEDULE_TASKS.md` (tarefas, [A13]–[A15], [B7] corrigida), `docs/ADM_TASKS.md`, `docs/PENDENCIAS.md` (seção 34),
  `README.md`.
- 22/10/2026 — Ajuste depois da entrega, pedido do Guilherme: D37 (subida no máximo uma vez por dia por tarefa) e D38
  (flag `run_on_startup` na tarefa, V65, editável na tela — "Roda na subida"). `TaskHandler.needsRecovery()` passa a
  valer `true` por padrão (a flag decide; o handler só diz se há o que recuperar). A investigação mostrou que o
  `TREASURY_PRICES` não estava só repetindo: o último PU do Tesouro gravado é de 15/10/2026 e todas as rodadas desde
  então terminam com `titles=0` — PENDENCIAS 34.5, escopo próprio.
  Teste: `TaskExecutorTest.subida` (só tarefa marcada e ativa; uma vez por dia; rodada de ontem não conta).
  `mvn clean verify`: core 210; integration 103; adm-api 118 + 78; app-api 210 + 263 — verdes. Dev: V65 aplicada; na
  subida seguinte nenhuma rodada `STARTUP` nova (Tesouro e abertura do dia já tinham rodado no dia; os outros 4 "em
  dia, nada a recuperar").
