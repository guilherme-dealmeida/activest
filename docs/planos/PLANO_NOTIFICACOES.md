# PrimeVest — Plano de Notificações (caixa de mensagens do usuário e do administrador)

> Fase: **implementado em 13/10/2026** (N1–N10; andamento na seção 12). Em execução desde 13/10/2026 (plano criado no mesmo dia; D1–D6 respondidas seguindo as recomendações e
> execução autorizada pelo Guilherme). A N7 entra agora, sobre a venda de RF de hoje (decidido em 13/10): quando o
> escopo de venda de renda fixa terminar, a liquidação se ajusta junto.
> Origem: ESCOPO "Funcionalidade de mensagens de notificação PrimeVest", levantamento de 12–13/10/2026 (respostas do
> Guilherme registradas na seção 9). Substitui a **central de avisos** adiada do `docs/planos/PLANO_BRAPI.md` (F8/B26)
> e registrada no `docs/PENDENCIAS.md` (seção 13); absorve o item 25.1 (alerta de preço) e revoga a D8 do
> `docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md` (título vencido passa a ser liquidado pelo sistema).

---

## 0. Resumo

Uma **caixa de mensagens** inspirada em e-mail, aberta por um **sino** no rodapé do menu lateral (junto de Modo,
Configurações e Sair). O sistema escreve mensagens para o usuário (carteira, proventos, eventos, integrações, Preço
Teto, renda fixa) e para os administradores (rotinas e catálogo). Cada mensagem tem dois eixos **independentes**:

| Eixo | Valores | Quem muda |
|---|---|---|
| **Estado** (como a pessoa vê e gerencia) | não lida · lida · arquivada · excluída | a pessoa (lida **só ao abrir**) |
| **Status** (situação do assunto da mensagem) | não resolvida · dispensada · resolvida — ou vazio (mensagem informativa) | resolvida: o sistema; dispensada/reabrir: a pessoa |

**Fora deste escopo:** Metas (escopo próprio, que vai usar esta central); envio por e-mail e preferências por tipo
(escopo próprio); arquivamento automático por tempo (fica em pendência); proteção geral da adm-api por `is_admin`
(outro escopo — seção 1.4).

---

## 1. Levantamento do que já existe

### 1.1 Avisos de hoje

| Onde | O que existe | Destino |
|---|---|---|
| Toast (`useToast`) | resposta a um clique, some em 4–8 s | continua (não é mensagem) |
| Menu "Integrações" | marcador amarelo `needsAttention` (consulta a cada 60 s) | **continua**; os mesmos fatos passam a gerar mensagens (D4) |
| `PortfolioPage` | selo "Vencido — lance o resgate" (`PositionValuationInfo.matured`) | sai: o título é liquidado (seção 6) |
| `CeilingPricesPage` | ícone "padrão mudou" (`changedDefaults`) | continua + mensagem (seção 5.6) |
| Logs WARN da adm-api | conferência do catálogo [A6], falhas de fonte, rótulo desconhecido, bonificação sem custo, Tesouro sem título, BRAPI sem token | viram mensagens de administrador (seção 5.7) |
| E-mails de conta (`AccountNotice`) | senha/e-mail alterado, conta excluída | ficam como estão (e-mail é outro escopo) |

### 1.2 Peças que o plano usa

| Peça | Onde | Uso |
|---|---|---|
| `CatalogChange` (Redis `primevest:catalogo:ativos`) | core / adm → app | gatilho do alerta de preço e do "padrão mudou"; ganha origem `BALANCOS` (seção 5.6) |
| `ClosingPriceService` [A1] | adm-api | já publica `FECHAMENTO` |
| `HistoricalQuotesImportService` [A11] (COTAHIST diário) | adm-api | **passa a publicar** `FECHAMENTO` com os ativos que ganharam preço (o Preço Teto vale para ativos fora da carteira) |
| `CeilingPriceRules` | app-api | calcula o **Preço Ideal** a partir das entradas gravadas (o ideal não depende do preço — seção 5.5) |
| `dividends.payment_date`, `asset_stock_events.data_com` | core | mensagens em D0 (seção 5.3 e 5.4) |
| `FixedIncomeCurve` / PU oficial do Tesouro (`asset_price_history`, `source = TESOURO`) | core / app-api | valor da liquidação automática (seção 6) |
| `BusinessCalendar` | core | data-ex dos eventos (dia útil seguinte à data-com) |
| `users.is_admin` | core | público das mensagens de administrador |

### 1.3 Rodapé do menu hoje

`Sidebar.tsx`: uma linha só — foto, nome, Modo, Configurações, Sair (recolhido: tudo em coluna).

### 1.4 adm-api e não administradores (pergunta respondida em 12/10)

Os 19 endpoints da adm-api (`/api/v1/admin/*`) exigem token **e** `is_admin` (403 para os demais); o front não chama a
adm-api (só `carga/scripts/admin.mjs`). Ficam para o escopo futuro: Swagger e `/actuator` da adm-api abertos sem token
com a porta 8081 em `0.0.0.0`, e o comentário desatualizado em `User.java` ("is_admin não é consumido").

---

## 2. Regras do escopo

| # | Regra |
|---|---|
| R1 | **Estado e status são independentes.** Qualquer transição de um não mexe no outro (ex.: resolver não marca como lida; excluir uma pendência não a resolve) |
| R2 | Mensagem só vira **lida** quando a pessoa **abre** a mensagem. Abrir a tela ou ver a lista não marca nada |
| R3 | **Excluir** existe para qualquer mensagem, em qualquer estado e status. É **lógica** (fica no banco) e a mensagem some para a pessoa; não há lixeira nem "restaurar" |
| R4 | **Arquivar** é manual e reversível (desarquivar). Arquivada continua lida ou não lida. Arquivamento automático por tempo: pendente |
| R5 | "Marcar como não lida" existe (como num e-mail) |
| R6 | **Resolvida** é sempre o sistema (o assunto deixou de existir); **dispensada** é a pessoa ("não vou tratar"); dispensada pode ser **reaberta** |
| R7 | Mensagem informativa (provento pago, liquidação, alerta de preço…) tem status **vazio** |
| R8 | Excluir ou arquivar não muda a sinalização na funcionalidade (ex.: ícone de atenção no preço médio da bonificação continua) |
| R9 | Mensagens de **administrador** são vistas por todos os admins; estado é **por pessoa**, status vale **para todos** |
| R10 | Uma pendência aberta por **tipo + referência** (a rotina não repete — a **dispensada** também segura a chave, para a rotina não reabrir o que a pessoa dispensou; só a resolvida a libera); falha de fonte instável: no máximo **uma por rotina por dia** |
| R11 | Sem mensagem retroativa nas informativas de D0 (provento, desdobramento/grupamento): se o sistema não subir no dia, não há mensagem |
| R12 | Preço sempre o **preço atual** (REGRAS 2.6) |

---

## 3. Banco (migrations)

### 3.1 V48 — notificações

```sql
CREATE TABLE notifications (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    audience      VARCHAR(10)  NOT NULL,              -- USER | ADMIN
    user_id       UUID,                               -- obrigatório em USER, nulo em ADMIN
    kind          VARCHAR(40)  NOT NULL,              -- seção 4
    category      VARCHAR(20)  NOT NULL,              -- PROVENTOS, EVENTOS, RENDA_FIXA, PRECO_TETO, INTEGRACOES, CARTEIRA, SISTEMA
    severity      VARCHAR(10)  NOT NULL,              -- INFO | ATENCAO
    title         VARCHAR(200) NOT NULL,
    body          TEXT         NOT NULL,
    ref_type      VARCHAR(30),                        -- ASSET, POSITION, TRANSACTION, CEILING_PRICE, INTEGRATION_RUN, CONNECTION, STOCK_EVENT, ROUTINE...
    ref_id        VARCHAR(64),
    payload       JSONB,                              -- linhas da mensagem e ações (links, "Informar custo")
    status        VARCHAR(15),                        -- nulo (informativa) | NAO_RESOLVIDA | DISPENSADA | RESOLVIDA
    status_at     TIMESTAMP,
    status_by     UUID,                               -- quem dispensou/reabriu (nulo = sistema)
    dedup_key     VARCHAR(200),                       -- R10 / uma por ativo por dia
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_audience CHECK ((audience = 'USER' AND user_id IS NOT NULL)
                                              OR (audience = 'ADMIN' AND user_id IS NULL))
);
-- informativas "uma por dia": a chave já traz a data
CREATE UNIQUE INDEX uk_notifications_dedup ON notifications (audience, COALESCE(user_id::text, ''), dedup_key)
    WHERE dedup_key IS NOT NULL AND status IS NULL;
-- pendências: uma ABERTA por chave
CREATE UNIQUE INDEX uk_notifications_open ON notifications (audience, COALESCE(user_id::text, ''), dedup_key)
    WHERE dedup_key IS NOT NULL AND status = 'NAO_RESOLVIDA';

-- Estado por pessoa (R9). Sem linha = não lida, na Entrada.
CREATE TABLE notification_states (
    notification_id UUID      NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    user_id         UUID      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    read_at         TIMESTAMP,
    archived_at     TIMESTAMP,
    deleted_at      TIMESTAMP,                        -- exclusão lógica (R3)
    PRIMARY KEY (notification_id, user_id)
);
```

### 3.2 V49 — alerta do Preço Teto (colunas novas em tabela existente: default, REGRAS 8.1)

`ceiling_prices.alert_enabled BOOLEAN NOT NULL DEFAULT FALSE`, `alert_below_ideal BOOLEAN NOT NULL DEFAULT FALSE`
(o preço estava abaixo do ideal na última conferência — rearme), `alert_last_on DATE` (último dia avisado).

### 3.3 V50 — liquidação automática

`transactions.origin` aceita `SISTEMA` (CHECK refeito: `SISTEMA` sem `external_id`, `edited_at` marca edição do
usuário). Sem coluna nova.

### 3.4 V51 — custo da bonificação informado pelo usuário (PENDENCIAS 13.2)

`stock_event_costs (portfolio_asset_id, stock_event_id, cost_per_share, created_at, updated_at)`, PK composta. Dado de
usuário (app-api); o catálogo (`asset_stock_events.assigned_cost`) não é tocado.

---

## 4. Catálogo de mensagens

| Tipo (`kind`) | Público | Categoria | Natureza | Quando | Chave (R10) | Resolve sozinha quando | Ações |
|---|---|---|---|---|---|---|---|
| `PROVENTO_PAGO` | usuário | PROVENTOS | informativa | rotina de abertura, D0 do pagamento | ativo + dia | — | Ver proventos |
| `EVENTO_APLICADO` (desdobramento, grupamento, bonificação com custo) | usuário | EVENTOS | informativa | rotina de abertura, D0 da data-ex | evento | — | Ver posição |
| `BONIFICACAO_SEM_CUSTO` | usuário | EVENTOS | pendência | evento entra (ouvinte `PROVENTOS`) e rotina de abertura | posição + evento | custo informado | **Informar custo**, Ver posição |
| `RF_LIQUIDADA` | usuário | RENDA_FIXA | informativa | rotina de abertura (liquidação feita pelo sistema) | transação | — | Ver transação |
| `RF_LIQUIDACAO_SEM_VALOR` | usuário | RENDA_FIXA | pendência | vencido sem curva (TR, sem índice) | título | resgate lançado | Lançar resgate |
| `RF_LIQUIDACAO_DIVERGENTE` | usuário | RENDA_FIXA | pendência | compra editada com a venda automática já editada à mão | título | venda bate com a curva | Ver transação |
| `PRECO_ABAIXO_IDEAL` | usuário | PRECO_TETO | informativa | preço atual novo ≤ ideal, com alerta ligado | cálculo + dia | — | Ver cálculo |
| `PRECO_TETO_PADRAO_MUDOU` | usuário | PRECO_TETO | pendência | balanço novo muda padrão de cálculo salvo | cálculo + último trimestre | cálculo salvo de novo | Ver cálculo |
| `INTEGRACAO_IMPORTOU` | usuário | INTEGRACOES | informativa | execução **agendada** confirmada com N > 0 operações | execução | — | Ver execução |
| `INTEGRACAO_AGUARDANDO` | usuário | INTEGRACOES | pendência | execução agendada parou em `AWAITING_CONFIRMATION` | execução | confirmada ou descartada | Ver execução |
| `INTEGRACAO_DIVERGENCIA` | usuário | INTEGRACOES | pendência | execução concluída com divergência | conexão | execução seguinte sem divergência | Ver execução |
| `INTEGRACAO_CONEXAO` | usuário | INTEGRACOES | pendência | `INVALID_CREDENTIALS`, `PERMISSION_TOO_BROAD`, `PENDING_AUTHORIZATION` | conexão + status | conexão `ACTIVE` | Ver integração |
| `INTEGRACAO_FALHOU` | usuário | INTEGRACOES | informativa | execução `FAILED` (agendada ou manual) | execução | — | Ver execução |
| `POSICAO_TICKER` (renomeado / sem cotação) | usuário | CARTEIRA | pendência | fan-out do aviso de admin para quem tem posição (seção 5.7) | posição + aviso | aviso de admin resolvido | Ver ativo |
| `CATALOGO_TICKER_NOVO` / `_SUMIDO` / `_RENOMEADO` | admin | SISTEMA | pendência | conferência do catálogo [A6] e fechamento [A1] | ticker | conferência seguinte não acha mais | — |
| `CATALOGO_SEM_COTACAO` | admin | SISTEMA | pendência | fechamento [A1] com 404 para ativo em carteira | ticker | o fechamento seguinte traz a cotação | Ver emissora |
| `CATALOGO_INDICE_ATRASADO` | admin | SISTEMA | pendência | conferência [A6] | índice | índice em dia | Ver índices |
| `CATALOGO_ROTULO_DESCONHECIDO` | admin | SISTEMA | pendência | rotina de proventos (`CashEventWriter`) | rótulo | rótulo mapeado | — |
| `CATALOGO_TESOURO_SEM_TITULO` | admin | SISTEMA | pendência | [A10] | título do arquivo | título no catálogo | — |
| `ROTINA_FALHOU` | admin | SISTEMA | informativa | rotina agendada ou sob demanda terminou com erro | rotina + dia | — | — |
| `FONTE_INDISPONIVEL` (FNET, CVM, B3, CoinGecko, BRAPI 401/sem token) | admin | SISTEMA | informativa | cliente desiste da fonte | rotina + fonte + dia | — | — |

Textos: título curto com o ticker na frente ("PETR4 — bonificação de 10% sem custo atribuído"); o corpo explica e
diz o que fazer. Lista final dos textos sai na etapa N3 e entra no `Claude.md`.

---

## 5. Comportamento por frente

### 5.1 Tela "Notificações" (`/notifications`)

- **Rodapé do menu em duas linhas:** 1ª foto + nome; 2ª ícones **Sino**, Modo, Configurações, Sair. Sino com o
  **número de não lidas** no marcador amarelo igual ao de Integrações; menu recolhido: só o ponto. Consulta a cada
  **60 s** (`GET /notifications/summary`).
- **Layout caixa de e-mail:** lista à esquerda, leitura à direita (celular: lista → leitura). Pastas **Entrada** e
  **Arquivadas**; filtros por categoria, status (não resolvida / dispensada / resolvida / informativa) e não lidas;
  filtro **Administração** só para admin. Não lida em negrito; selo do status; data relativa.
- **Ações:** abrir (marca lida — R2), marcar como não lida, arquivar/desarquivar, excluir (confirma), dispensar /
  reabrir (só pendência), e a ação própria do tipo (seção 4). **Seleção em lote** para lida/não lida/arquivar/excluir.
- Paginação de 50; ordem: mais nova primeiro.

### 5.2 API (app-api)

| Método | Rota | Faz |
|---|---|---|
| GET | `/api/v1/notifications?folder=INBOX\|ARCHIVED&category=&status=&unread=&audience=USER\|ADMIN&page=` | lista da pessoa (ADMIN só com `is_admin`); sem as excluídas |
| GET | `/api/v1/notifications/summary` | `{unread, adminUnread}` para o sino |
| GET | `/api/v1/notifications/{id}` | detalhe (não marca lida) |
| POST | `/api/v1/notifications/{id}/open` | marca lida (R2) |
| PATCH | `/api/v1/notifications/state` | lote `{ids, action: READ\|UNREAD\|ARCHIVE\|UNARCHIVE\|DELETE}` |
| PATCH | `/api/v1/notifications/{id}/status` | `{action: DISMISS\|REOPEN}` (só pendência; admin para mensagens de admin) |

Mensagem de outro dono ou de admin para não admin: 404 (critério do `GoalService.findOwnedGoal`).

### 5.3 Provento pago (D0)

Na rotina de abertura (seção 7): `dividends` com `payment_date = hoje`, agrupados por posição → **uma mensagem por
ativo e dia** com uma linha por provento (tipo, valor por cota, cotas na data-com, bruto e líquido). Provento sem data
de pagamento ou que só ganhou a data depois dela: nada (R11).

### 5.4 Eventos societários (D0) e bonificação sem custo

- Desdobramento, grupamento e bonificação **com** custo: mensagem no dia da **data-ex** (dia útil seguinte à
  `data_com`, `BusinessCalendar`) para quem tinha posição na data-com: cotas antes → depois e preço médio ajustado.
- Bonificação **sem** custo (`assigned_cost` nulo e sem `stock_event_costs`): pendência criada quando o evento entra
  (ouvinte "catálogo mudou" `PROVENTOS`) e conferida na rotina de abertura. **Informar custo** (um campo, R$ por ação)
  grava `stock_event_costs`, o `CorporateActionApplier` usa esse custo no lugar do zero provisório, o preço médio é
  recalculado e a pendência fica **resolvida**. Na Carteira, ícone de atenção ao lado do preço médio com o mesmo
  texto no (i) enquanto não houver custo (R8). Bonificações já existentes que pegam posição: D3.

### 5.5 Alerta de preço (Preço Teto)

- Lista do Preço Teto ganha a coluna **"Alerta"** à esquerda de "Ações", com interruptor igual ao da **Reserva** da
  Carteira e (i) "Alertar preço abaixo do ideal". `PATCH /api/v1/ceiling-prices/{id}/alert {enabled}`.
- Gatilho: **preço atual** novo do ativo (ouvinte `FECHAMENTO` — [A1] e, a partir deste escopo, [A11]; e a rotina de
  abertura como rede de segurança). O ideal vem das entradas gravadas (`CeilingPriceRules`; não depende do preço).
- Regra: avisa quando `preço ≤ ideal` e `alert_below_ideal = false` (cruzou para baixo) → grava `true`; quando
  `preço > ideal` → volta a `false` (rearma). **No máximo uma por ativo por dia** (`alert_last_on`). Ligar o alerta
  com o preço já abaixo: avisa na próxima atualização de preço. Salvar o cálculo de novo recalcula o estado.

### 5.6 "Padrão mudou" no Preço Teto

A carga de balanços [A5] passa a publicar "catálogo mudou" com a origem nova **`BALANCOS`** (ativos dos emissores que
ganharam trimestre). A app-api confere os cálculos salvos desses ativos e cria a pendência quando
`changedDefaults` aponta campo que vem de balanço: **trimestres, ROE, payout, ações** — o preço e a Selic ficam de
fora (mudam sempre; a Selic só pré-preenche cálculo novo). Resolve ao salvar o cálculo de novo.

### 5.7 Mensagens de administrador e o fan-out

- A adm-api grava as mensagens de admin **direto** na tabela `notifications` (`audience = ADMIN`) pelo serviço do
  core — D1. Onde hoje há `log.warn` dos casos da seção 4, passa a haver log **e** mensagem.
- Resolução automática: cada rodada da conferência [A6] resolve as pendências de admin que não achou mais.
- **Fan-out para o usuário:** ticker renomeado ou sem cotação com posição aberta → a app-api (ouvinte e rotina de
  abertura) cria `POSICAO_TICKER` para quem tem a posição, e a resolve quando o aviso de admin é resolvido — D2.

### 5.8 Integrações

Uma mensagem por execução (seção 4). Execução **manual** só gera mensagem em caso de falha e nos problemas de
conexão; as agendadas geram todas. O marcador do menu continua igual.

---

## 6. Liquidação automática do título vencido (revoga a D8 da renda fixa)

> **Revogada em 15/10/2026** pelo `docs/planos/PLANO_VENDA_RENDA_FIXA.md`, seção 6: o valor que cai na conta é o
> líquido, e só o usuário o sabe. O sistema não cria nem refaz venda; a rotina de abertura só avisa (pendência
> `RF_VENCIDO`, com o líquido estimado e o link que abre o formulário preenchido) e a mensagem se resolve quando a
> posição fica liquidada. `RF_LIQUIDADA`, `RF_LIQUIDACAO_SEM_VALOR` e `RF_LIQUIDACAO_DIVERGENTE` deixaram de ser
> emitidas; as duas pendências abertas foram resolvidas na primeira rodada (D7). O texto abaixo fica como histórico.

| Ponto | Regra |
|---|---|
| Quais | posições abertas de **Renda Fixa privada e Tesouro Direto** com vencimento ≤ hoje (no dia do vencimento já está vencido — regra do `validateBuyBeforeMaturity`) |
| Quando | rotina de abertura do dia (00:00 e na subida — seção 7); a **primeira rodada liquida todos** os já vencidos |
| Transação retroativa | compra lançada hoje de título que já venceu **nasce liquidada**: a venda automática é criada no mesmo "Salvar" (sem mensagem) |
| Transação | venda (`SELL`), data = **data do vencimento**, `is_full_settlement = true`, `origin = SISTEMA`, selo **"Automática"** na tela de Transações (filtro Origem ganha "Automática") — D4 |
| Valor | RF privada: saldo da **curva** no vencimento (`FixedIncomeCurve`); Tesouro: **PU oficial** do dia do vencimento (ou o último antes dele) × cotas, liquidando todos os títulos do ativo (FIFO por distribuidora quando o escopo de emissoras estiver feito) |
| Sem valor | título sem curva (TR, índice ausente): sem transação; pendência `RF_LIQUIDACAO_SEM_VALOR` ("informe o valor do resgate") |
| Editar / excluir | a venda automática pode ser editada (marca `edited_at`) e excluída; **excluída, a rodada seguinte liquida de novo** — corrigir é editar |
| Compra editada | refaz a venda automática se ela não foi editada à mão; se foi, a venda fica e abre `RF_LIQUIDACAO_DIVERGENTE` |
| Mensagem | `RF_LIQUIDADA`, uma por título liquidado pela rotina (inclusive a primeira rodada) |
| Carteira | o selo "Vencido — lance o resgate" deixa de existir (o título sai das posições abertas) |

**Dependência:** o escopo de **venda de renda fixa** em andamento (Guilherme, 09/10) muda o comportamento da venda;
a etapa N7 começa depois dele, para usar a venda nova. Futuro: quando a integração da B3 importar vencimento de RF/TD
(PENDENCIAS 16.5/16.6), o importado tem que casar com a venda automática (duplicata) — vai para a pendência.

---

## 7. Rotina de abertura do dia — [B7] (app-api)

| | |
|---|---|
| **Quando** | 00:00 todos os dias (`primevest.abertura.cron = "0 0 0 * * *"`) **e** na subida da app-api — "meia-noite ou o primeiro horário do dia com o sistema no ar" |
| **Faz, em ordem** | 1) liquidação dos vencidos (seção 6); 2) proventos pagos hoje (5.3); 3) eventos com data-ex hoje (5.4); 4) conferência das pendências de bonificação, do fan-out (5.7) e dos alertas de preço (5.5) |
| **Estado** | **nenhuma coluna de controle**: tudo é idempotente pelas chaves da seção 4 — rodar duas vezes no dia não duplica (princípio de carga: sem colunas de estado de processo) |

Entra no `docs/SCHEDULE_TASKS.md` (linha do tempo + seção [B7]) na implementação, com [A5] e [A11] publicando
"catálogo mudou" (`BALANCOS` e `FECHAMENTO`).

---

## 8. Etapas

| Etapa | Entrega | Depende |
|---|---|---|
| **N1** | V48; `Notification`, `NotificationState`, `NotificationService` no core (criar com chave, resolver, dispensar/reabrir, estado por pessoa); regras R1–R10 em testes de unidade | — |
| **N2** | API da seção 5.2 na app-api; público ADMIN por `is_admin`; ITs (estado × status independentes, exclusão lógica, admin compartilhado) | N1 |
| **N3** | Web: rodapé em duas linhas com o sino e contador (60 s); tela `/notifications` (pastas, filtros, leitura, lote, ações); textos | N2 |
| **N4** | Integrações: produtores e resoluções da seção 5.8 no `IntegrationService` | N1 |
| **N5** | Administrador: produtores na adm-api (5.7), resolução pela conferência; fan-out `POSICAO_TICKER` na app-api | N1 |
| **N6** | Rotina de abertura [B7] com proventos D0 e eventos D0 (5.3, 5.4 informativas) | N1 |
| **N7** | Liquidação automática (seção 6, V50) dentro da [B7] e no "Salvar" da compra retroativa | N6 + escopo de venda de RF |
| **N8** | Bonificação sem custo: V51, "Informar custo", ícone na Carteira, pendência (5.4) | N6 |
| **N9** | Preço Teto: V49, coluna Alerta, disparo pelo preço atual ([A11] publicando `FECHAMENTO`), "padrão mudou" com `BALANCOS` (5.5, 5.6) | N1 |
| **N10** | Validação (seção 10) e documentação: `Claude.md`, `docs/PENDENCIAS.md` (13 → este plano; 10.4; 25.1; arquivamento automático; adm-api), `README.md`, `docs/USE_CASES.md` (UC novo), `docs/PRIMEVEST_DB.md`, `docs/SCHEDULE_TASKS.md`, D8 do plano da renda fixa e D10 do Preço Teto marcadas como revistas | todas |

### Quem faz

| Quem | O quê |
|---|---|
| Claude | N1–N10 |
| Guilherme | responder D1–D6; terminar o escopo de venda de RF antes da N7; conferência no navegador (caminho 3) |

---

## 9. Decisões tomadas no levantamento (12–13/10/2026)

| # | Decisão |
|---|---|
| L1 | Estado e status independentes (regra geral, R1); dispensar é status |
| L2 | Sino no rodapé do menu, que passa a ter duas linhas (foto+nome / ícones); abre a **tela** de notificações inspirada em e-mail |
| L3 | Lida só ao abrir; excluir para qualquer mensagem, exclusão lógica; arquivar manual; arquivamento automático pendente |
| L4 | Contador de não lidas com o comportamento do de Integrações; atualização a cada 60 s |
| L5 | Avisos de administrador neste escopo: vistos por todos os admins, estado por pessoa, status para todos |
| L6 | Integrações: marcador continua e os eventos também viram mensagens, uma por execução; manual só falha |
| L7 | Alerta de preço entra: abaixo do **ideal**, ligado por cálculo (`alert_enabled`, default false, coluna "Alerta" como a Reserva), rearme ao voltar acima, no máximo um por ativo por dia; preço atual (REGRAS 2.6) |
| L8 | Entram bonificação sem custo, "padrão mudou", desdobramento/grupamento, ticker renomeado/sem cotação com posição |
| L9 | Título vencido: **liquidação automática** na rotina de abertura (revoga a D8); já vencidos na primeira rodada; retroativa nasce liquidada; venda "Automática" editável/excluível (excluída volta na rodada seguinte); compra editada refaz a venda não editada; sem curva → pendência; mensagem por título liquidado, nenhuma na retroativa |
| L10 | Provento: mensagem só em D0 do pagamento, pela rotina de abertura, sem retroativo; uma por ativo e dia; sem data de pagamento → nada |
| L11 | Desdobramento/grupamento no mesmo D0 (data-ex); bonificação sem custo é pendência criada quando o evento entra |
| L12 | Metas e e-mail ficam fora; `is_admin` na adm-api é outro escopo |

## 10. Decisões D1–D6 — **tomadas em 13/10/2026: todas pela recomendação**

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D1** | Quem grava as mensagens de **administrador** | (a) a adm-api direto na tabela; (b) a adm-api publica no Redis e a app-api grava | **(a)**: mensagem de sistema não é dado de usuário, e o Redis pub/sub não garante entrega |
| **D2** | Usuário com posição em ticker renomeado/sem cotação | (a) fan-out da app-api a partir do aviso de admin (5.7); (b) só o admin recebe | **(a)** |
| **D3** | Bonificações **já existentes** sem custo que pegam posição | (a) a primeira rodada cria a pendência para todas; (b) só as novas | **(a)**: o preço médio delas está com custo zero provisório |
| **D4** | Rótulo da origem da venda automática | "Automática" / "Sistema" | **"Automática"** (selo e filtro) |
| **D5** | "Padrão mudou": campos que geram mensagem | (a) só os de balanço (trimestres, ROE, payout, ações); (b) todos, inclusive preço e Selic | **(a)** |
| **D6** | Pendência de execução aguardando confirmação que você **descarta** | resolvida / dispensada | **resolvida** (o assunto deixou de existir) |

---

## 11. Validação (última etapa do Claude)

`mvn verify` (core, integration, adm-api, app-api) e, na web, `tsc --noEmit` e `eslint .` sem erro nem aviso. No
ambiente: V48–V51 aplicadas; rotina de abertura rodando na subida sem duplicar numa segunda subida; conferência no
navegador com você logado: sino e contador, abrir/arquivar/excluir/lote, dispensar/reabrir, um alerta de preço
(ligando com o ideal acima do preço atual de teste), a liquidação de um título vencido de teste e a pendência de
bonificação com "Informar custo".

## 12. Andamento

| Data | Etapa | Situação |
|---|---|---|
| 13/10/2026 | — | plano criado a partir do levantamento |
| 13/10/2026 | — | D1–D6 pela recomendação; N7 agora sobre a venda atual; execução autorizada |
| 13/10/2026 | N1 | V48; `NotificationKind/Audience/Category/Severity/Status`, `Notification`, `NotificationState`, repositórios (caixa com `NotificationView`, contador, visíveis), `NotificationDraft` (builder com linhas, ações e dados) e `NotificationService` (`notify` com `INSERT … ON CONFLICT DO NOTHING` em transação própria, `resolve`, `resolveAllExcept`, `openOf`, status e estado em lote). `IntegrationTestBase` limpa `notifications` |
| 13/10/2026 | N2 | `NotificationController` + `NotificationBoxService` (seção 5.2; admin por `is_admin`, 404 para o resto). `NotificacoesIT` (6): chave de repetição, estado × status, arquivar, dispensar/reabrir, outro dono, admin compartilhado |
| 13/10/2026 | N3 | Rodapé do menu em duas linhas com o sino e o contador (60 s); `NotificationsPage` (`/notifications`): pastas, filtros, lista + leitura, lote, excluir com confirmação, ações (link, `InformCostModal`), Dispensar/Reabrir; título no `Header` |
| 13/10/2026 | N4 | `IntegrationNotifier` ligado no `IntegrationService` (falha × problema de conexão, conexão ok, importou, aguardando, decidida — confirmada, descartada ou substituída —, divergência, desconectada). `IntegracoesNotificacoesIT` (6) |
| 13/10/2026 | N5 | `AdminNotices` (adm-api): conferência do catálogo (ticker novo numa mensagem por rodada com a lista; sumido e renomeado por ticker; resolução pela rodada seguinte), fechamento (renomeado, sem cotação → `CATALOGO_SEM_COTACAO`, BRAPI fora/sem token), cripto (CoinGecko), índices (atrasado, fonte), Tesouro (sem título, arquivo fora), proventos (B3 e FNET fora), rótulo desconhecido (no `CashEventWriter` do core), falhas das rotinas agendadas, sob demanda e da carga inicial. `CatalogNoticeFanout` + `OpenPositions.holdersOf` (app-api). `MarketIndexImportIT.conferenciaViraMensagem` |
| 13/10/2026 | N6 | `DailyOpeningService` + `DailyOpeningScheduler` [B7]: etapas de outros escopos por `DailyOpeningStep` (liquidação antes, Preço Teto depois), proventos pagos em D0 (uma por ativo e dia), eventos com data-ex hoje; `CatalogReaction` chamadas pelo `PositionRefreshService` depois de cada "catálogo mudou" |
| 13/10/2026 | N7 | V50; `MaturitySettlementService` (abertura, `INDICES`/`FECHAMENTO` e depois do commit da gravação do usuário — `PositionWrittenEvent`); `SystemWrites`; `TransactionOrigin.SISTEMA`; `SaleIntent` com a origem do grupo; selo "Automática" e filtro em Transações; tooltip do "Vencido" na Carteira. `RendaFixaCurvaIT.vencido` passou a esperar a liquidação |
| 13/10/2026 | N8 | V51; `StockEventCost` + repositório; `PositionCalculator` usa o custo do usuário; `BonusCostService` (pendência, `pendingFor`, `setCost`) e `BonusCostController`; `pendingBonusCosts` na linha da Carteira e `BonusCostBadge` no preço médio |
| 13/10/2026 | N9 | V49; `CeilingPriceAlertService` (alerta pelo preço atual, rearme, um por dia; "padrão mudou" só com campos de balanço; resolve ao salvar); `PATCH /ceiling-prices/{id}/alert` e coluna "Alerta"; `CatalogChange.Origin.BALANCOS` publicado pelos balanços [A5]; COTAHIST diário [A11] publica `FECHAMENTO`. `PrecoTetoIT` (+2), `AberturaDoDiaIT` (8) |
| 13/10/2026 | N10 | `mvn verify` completo verde (core 158; integration 103; adm-api 89 + 41; app-api 195 + 222 — `SchemaTest` na V51); web: `tsc`, `eslint .` e `vite build` sem erro. Ambiente: V48–V51 aplicadas no DEV pelo hot reload; a abertura rodou na subida (sem vencidos; 1 pendência real: KLBN4, bonificação de 1% em 17/12/2025 sem custo); a conferência do catálogo disparada à mão gerou as mensagens de administrador (1 "98 tickers novos na B3" e 51 tickers sumidos); `GET /notifications/summary` e a lista conferidos pela API com a conta real. Documentação: `Claude.md`, `README.md`, `docs/PENDENCIAS.md` (13, 10.4, 25.1 e seção 26), `docs/SCHEDULE_TASKS.md` ([B7], [A5], [A6], [A11], [B3], [B4]), `docs/USE_CASES.md` (UC17–UC19, UC42), `docs/PRIMEVEST_DB.md` (5b), D8 da renda fixa e D10 do Preço Teto. Conferência no navegador: com o Guilherme (caminho 3) |
