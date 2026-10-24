# PrimeVest — Cadastro de Criptomoedas: importação da Binance

> **Nota (23/10/2026):** escrito quando o projeto se chamava **PrimeVest**. Nomes de módulo (`primevest-*`), pacote
> (`com.primevest`), configuração (`primevest.*`, `PRIMEVEST_*`), contêineres e versões de migration (V1–V67) citados aqui
> são os do repositório `primevest`; no Activest viraram `activest-*`, `com.activest`, `activest.*`/`ACTIVEST_*` e as
> migrations consolidadas V1–V6 (`docs/planos/PLANO_RENOMEACAO_ACTIVEST.md`).

> Fase: **levantamento** (25/09/2026) · nada implementado no PrimeVest · branch `TRACK_01-ajustes_crud_inicial`
> Escopo: importar os aportes em cripto das corretoras. A Binance é o piloto, com a conta do Guilherme;
> o destino é **qualquer usuário** conectar as próprias contas (seção 6, revisada em 25/09/2026).
> Fonte do levantamento: MCP próprio, só leitura, fora do repositório (fase 1).
> Dados reais, dumps e a carga gerada ficaram **fora do repositório** e foram descartados em 03/10/2026.

Objetivo: saber o que a Binance entrega, como isso vira `transactions` do PrimeVest e o que falta no
banco para a fase 2 (o serviço Java) importar sem duplicar.

---

## 1. Veredito em uma frase

**Os dados da Binance explicam a carteira inteira, e cabem no modelo de hoje como compras (`BUY`).**
A conciliação bateu ao satoshi no BTC e ao centavo no saldo em reais. Para a importação ser
automática e repetível, faltam as mesmas colunas de origem e idempotência já previstas no
`docs/planos/PLANO_OPEN_FINANCE.md` (seção 4.1).

---

## 2. O que foi consultado

A chave de API foi conferida em `/sapi/v1/account/apiRestrictions`: **só leitura** (`enableReading`).
Não negocia, não saca e não transfere. Não tem restrição de IP.

| Fonte | Endpoint | Resultado na conta | Vira no PrimeVest |
|---|---|---|---|
| Negociações spot | `GET /api/v3/myTrades` | 6 ordens, só `BTCBRL`, todas compras | `transactions` BUY/SELL |
| Convert | `GET /sapi/v1/convert/tradeFlow` | 1 conversão BRL → BTC | `transactions` BUY/SELL |
| Compras com real ("Comprar Cripto") | `GET /sapi/v1/fiat/payments` | 1 tentativa, status `Failed` | `transactions` BUY (se concluída) |
| Depósitos em reais | `GET /sapi/v1/fiat/orders` (tipo 0) | 8 depósitos (PIX e PicPay) | **não é transação** — só conciliação (4.6) |
| Saques em reais | `GET /sapi/v1/fiat/orders` (tipo 1) | nenhum | conciliação |
| Depósitos de cripto | `GET /sapi/v1/capital/deposit/hisrec` | nenhum | transferência (4.7) |
| Saques de cripto | `GET /sapi/v1/capital/withdraw/history` | nenhum | transferência (4.7) |
| Rendimentos Earn/staking/airdrop | `GET /sapi/v1/asset/assetDividend` | nenhum | provento em quantidade (futuro) |
| Earn flexível | `GET /sapi/v1/simple-earn/flexible/position` | nenhum | — |
| Binance Pay, P2P, "dust" | `/sapi/v1/pay/transactions`, `/sapi/v1/c2c/...`, `/sapi/v1/asset/dribblet` | nenhum | — |
| Saldos | `GET /api/v3/account`, `/sapi/v1/asset/wallet/balance` | BTC, BRL (resíduo) e USDC (resíduo) | conferência da posição |

Pares com operação foram descobertos testando 31 combinações (ativos com saldo + BTC, ETH, BNB,
USDT, SOL × BRL, USDT, BTC, USDC, FDUSD, BUSD, BNB, ETH). Só `BTCBRL` tinha negociações.

---

## 3. Conciliação

Feita por um script avulso (`converter_binance.mjs`, fora do repositório) com os dumps do MCP.

| Conferência | Resultado |
|---|---|
| BTC: soma das compras líquidas de taxa × saldo na Binance | ✅ idêntico (8 casas) |
| BRL: depósitos − valor usado nas compras × saldo em reais | ✅ idêntico (2 casas) |
| USDC com saldo e sem nenhuma movimentação que explique | ⚠️ resíduo de poucos centavos; provável brinde/promoção. Ignorado (D3) |
| Cada depósito PIX tem uma compra no mesmo dia ou no seguinte | ✅ — aporte em reais → compra de BTC |

---

## 4. Mapeamento Binance → PrimeVest

### 4.1 Negociação spot (`myTrades`) → `transactions`

| Binance | PrimeVest | Regra |
|---|---|---|
| `symbol` (`BTCBRL`) | `assets.ticker` = parte base (`BTC`), tipo `CRIPTO` | R6, R7 |
| `isBuyer` | `type` BUY / SELL | — |
| `orderId` | agrupa os fills; compõe `external_id` | R1, R5 |
| `qty` − `commission` (quando a taxa é no ativo comprado) | `shares` | R2 |
| `quoteQty` / `qty` (média ponderada dos fills) | `price_per_share` | R1 |
| `commission` × preço (em BRL) | `brokerage_fee` | R2 |
| `time` (ms, UTC) | `transaction_date` no fuso `America/Sao_Paulo` | R3 |
| `isMaker`, `id` do fill | — (fica só no log da importação) | — |

### 4.2 Convert (`tradeFlow`) → `transactions`

| Binance | PrimeVest | Regra |
|---|---|---|
| `fromAsset` = BRL → compra de `toAsset`; `toAsset` = BRL → venda de `fromAsset` | `type` + `ticker` | R6 |
| `toAmount` (compra) / `fromAmount` (venda) | `shares` | — |
| valor em BRL ÷ quantidade, 8 casas | `price_per_share` | R4 |
| — (a taxa vem embutida na cotação) | `brokerage_fee` = 0 | R4 |
| `quoteId` | `external_id` | R5 |
| `createTime` | `transaction_date` | R3 |
| `orderStatus` ≠ `SUCCESS` | ignorado | — |

### 4.3 Comprar Cripto com real (`fiat/payments`) → `transactions`

| Binance | PrimeVest |
|---|---|
| `cryptoCurrency` | `ticker` |
| `obtainAmount` | `shares` |
| (`sourceAmount` − `totalFee`) ÷ `obtainAmount` | `price_per_share` |
| `totalFee` | `brokerage_fee` |
| `orderNo` | `external_id` |
| `status` ≠ concluído | ignorado |

⚠️ Só havia uma compra com `status = Failed`. O texto exato do status de sucesso (`Completed`?)
precisa ser confirmado na primeira compra concluída.

### 4.4 Regras

| # | Regra | Por quê |
|---|---|---|
| R1 | **Uma ordem = uma transação.** Fills do mesmo `orderId` são somados, com preço médio ponderado | É o que a pessoa fez ("comprei R$ 500 de BTC"); o fill é detalhe da execução |
| R2 | **Taxa no ativo comprado:** `shares` = quantidade líquida; `brokerage_fee` = comissão × preço, em BRL | A posição tem de bater com o saldo da corretora. Assim, `shares × preço + taxa` = reais pagos |
| R3 | **Data no fuso de Brasília.** `time` vem em UTC | Duas compras da conta foram depois das 21h: em UTC cairiam no dia seguinte |
| R4 | **Convert:** preço = BRL ÷ quantidade; taxa 0 | O Convert não informa taxa; ela está no spread. O `inverseRatio` da Binance difere um pouco do preço efetivo |
| R5 | **`external_id` estável:** `binance:spot:<symbol>:<orderId>`, `binance:convert:<quoteId>`, `binance:fiatpay:<orderNo>` | Reimportar não duplica. No Convert usar o `quoteId` (texto), não o `orderId` (ver 5.3) |
| R6 | **Só operações com valor em reais.** Cripto → cripto (`ETHBTC`, Convert BTC → ETH) e pares contra stablecoin (`BTCUSDT`) ficam em "não mapeadas" | Precisam de valor em reais no momento da troca (D5, PENDENCIAS 15) |
| R7 | **Ticker da Binance = ticker do catálogo `CRIPTO`.** Sem correspondência, a operação é recusada e listada — nunca cria ativo | Mesma regra do cadastro manual ("ticker desconhecido é erro"). Exceções de nome (ex.: renomes da Binance) vão num de-para quando aparecerem |
| R8 | **Taxa em outro ativo** (ex.: desconto pagando em BNB) fica em "não mapeadas" | Precisa do preço do BNB em reais no momento. Não ocorre hoje |

### 4.5 Arredondamento

`total_amount` é calculado pelo `TransactionService` (`shares × price_per_share`) e gravado com 2
casas. Somando as 7 operações da conta, a diferença para os reais efetivamente pagos foi de
**1 centavo**. Aceitável; se incomodar, dá para ajustar a taxa da última operação do dia.

### 4.6 Depósitos em reais não são transação

O PIX para a Binance é o **aporte em dinheiro**; a compra de BTC é o **aporte no ativo**. O PrimeVest
registra o segundo. Os depósitos servem para conciliar (3) e, no futuro, para uma visão de
"dinheiro parado na corretora" — hoje não há onde guardar saldo em caixa por instituição.

### 4.7 Transferências de cripto

Um saque de BTC da Binance para uma carteira própria **não é venda**: a posição continua do usuário.
Um depósito vindo de outra corretora não é compra. Nenhum dos dois existe na conta hoje, mas o
modelo atual (`BUY`/`SELL` sem custódia) não sabe representá-los — ver D6.

---

## 5. O que falta no banco e na API

Checado contra `V1__schema.sql` a `V24` e o banco local em 25/09/2026 (nenhuma transação `CRIPTO`
lançada ainda).

### 5.1 Obrigatório para importar sem duplicar

São os itens 1 e 4 do `docs/planos/PLANO_OPEN_FINANCE.md` (4.1). **Fazer uma vez só, servindo às duas integrações.**

| # | Onde | Campo | Por quê |
|---|---|---|---|
| 1 | tabelas novas `integration_connections` e `integration_credentials` (por usuário) | ver 6.3 | Guardar a conexão e a chave de cada usuário, criptografada |
| 2 | `transactions` | `origin` (`MANUAL`/`AUTO`), `external_id`, `connection_id`; único `(connection_id, external_id)` | Idempotência (R5) e bloquear edição manual de linha importada |

### 5.2 Recomendado

| # | Onde | Campo | Por quê |
|---|---|---|---|
| 3 | `transactions` | `executed_at TIMESTAMP` (opcional) | Ordenar várias operações do mesmo dia e auditar contra a corretora. `transaction_date` continua sendo a data de negócio |
| 4 | custódia por posição | onde o ativo está (Binance, carteira própria, outra corretora) | Transferências (4.7) e visão por instituição. Mesma lacuna do item 7 do Open Finance |

### 5.3 Armadilhas para a fase 2

| Armadilha | Consequência |
|---|---|
| `orderId` do Convert passa de 2^53 | Em JavaScript perde precisão (o MCP foi corrigido na v0.1.1). Em Java, `long` resolve; no JSON do PrimeVest, trafegar como texto |
| `myTrades` só consulta **um par por vez** | Precisa descobrir os pares antes (saldos + depósitos + lista de cotação) ou manter a lista dos já vistos por conexão |
| `deposit/hisrec`: janela máxima de 90 dias; `convert/tradeFlow`: 30 dias | Paginar por janela |
| `fiat/orders` tem peso alto no limite por usuário | No máximo algumas chamadas por minuto; sincronização incremental (desde `last_sync_at`) |
| Relógio do servidor fora de sincronia gera erro `-1021` | Sincronizar com `/api/v3/time` e usar `recvWindow` |
| `canTrade`/`canWithdraw` em `/api/v3/account` são da **conta**, não da chave | A permissão da chave se confere em `/sapi/v1/account/apiRestrictions`. Vale checar na conexão e recusar chave que negocia ou saca |

---

## 6. Fase 2 — multiusuário desde o início (não implementar sem ordem)

Revisado em 25/09/2026. O teste é com a conta do Guilherme, mas o objetivo é que **qualquer usuário**
conecte as próprias contas e importe os próprios dados. Construir para um usuário e adaptar depois
custaria mais do que já nascer assim.

### 6.1 Princípios

| # | Princípio | Consequência |
|---|---|---|
| P1 | **Cada usuário conecta as próprias contas** | Credencial e conexão pertencem ao usuário (`user_id`), nunca ao servidor |
| P2 | **Só leitura, sempre** | A permissão da chave é conferida na conexão **e** a cada sincronização. Chave que negocia, saca ou transfere é recusada |
| P3 | **Um pipeline, várias fontes** | Cada corretora cripto é um conector que entrega o mesmo formato normalizado; as regras R1–R8 e a gravação ficam num lugar só |
| P4 | **Importar é idempotente** | `(connection_id, external_id)` único; reimportar nunca duplica |
| P5 | **O usuário vê antes de gravar** | Primeira importação e arquivo passam por pré-visualização; o que não mapeou fica visível |
| P6 | **Grava pelas regras do PrimeVest** | Tudo passa pelo `TransactionService`, como no lançamento manual e no `carregar.mjs` |

### 6.2 Arquitetura

```
            ┌──────────────── fontes ────────────────┐
 BinanceConnector   <Outra corretora cripto>Connector
            └───────────────┬────────────────────────┘
                            ▼   interface SourceConnector
                 validate(cred) · fetch(conn, cursor) → List<ImportedMovement> + novo cursor
                            ▼
              import/ImportPipeline        ← R1–R8, de-para de ticker, detecção de duplicata
                            ▼
              pré-visualização  ──(confirmar)──▶  TransactionService  ──▶ transactions (origin=AUTO)
                            ▲
   import/SyncWorker (fila Redis) ◀── botão "Sincronizar" · scheduler diário · nova conexão
```

- `ImportedMovement` é o formato comum: `externalId`, `kind` (TRADE, CONVERT, FIAT_DEPOSIT, CRYPTO_DEPOSIT,
  CRYPTO_WITHDRAW, REWARD), ativo, lado, quantidade bruta, taxa e ativo da taxa, valor em moeda de cotação,
  `executedAt` (UTC) e o JSON bruto para auditoria.
- Cada conector só sabe falar com a sua fonte. Nenhuma regra de negócio mora nele.
- Uma corretora nova = um conector novo + um levantamento como este (seções 2–4).

### 6.3 Dados

Substitui o item 1 de 5.1 e o item 1 da seção 4.1 do `docs/planos/PLANO_OPEN_FINANCE.md` — as duas integrações usam as mesmas tabelas.

| Tabela | Campos principais | Observação |
|---|---|---|
| `integration_connections` | `id`, `user_id`, `provider` (hoje só `BINANCE`; fonte nova entra por migration), `label` ("Binance principal", único por usuário), `status` (`ACTIVE`, `INVALID_CREDENTIALS`, `PERMISSION_TOO_BROAD`, `REVOKED`), `consent_accepted_at`, `last_sync_at`, `last_error`, timestamps | Um usuário pode ter várias; uma por conta na fonte |
| `integration_credentials` | `connection_id` (1:1), `ciphertext`, `iv`, `key_version`, `fingerprint` (últimos 4 da chave, para exibir), `validated_at`, `permissions` (JSON da última checagem) | **Nunca** sai na API. Apagada fisicamente na revogação |
| `integration_cursors` | `connection_id`, `stream` (`spot:BTCBRL`, `convert`, `fiat_orders`…), `cursor_value` (último id/instante) | Sincronização incremental — respeita os limites de peso |
| `integration_runs` | `id`, `connection_id`, `trigger_type` (MANUAL/SCHEDULED/CONNECTION), `status` (QUEUED → RUNNING → AWAITING_CONFIRMATION → SUCCEEDED; FAILED/CANCELLED), início, fim, contagens (importadas, já existentes, não mapeadas, possíveis duplicatas), `error` | Histórico visível ao usuário |
| `integration_run_items` | `run_id`, `external_id`, `outcome` (UNMAPPED/POSSIBLE_DUPLICATE/FAILED), `reason`, `raw` (JSONB) | Só para o que não entrou: é o "não mapeadas" de hoje, persistido |
| `transactions` | + `origin`, `external_id`, `connection_id`, `executed_at`, `edited_at`, `source_snapshot` (JSON com os valores vindos da fonte) | Itens 2 e 3 de 5.1/5.2; edição (6.10) |
| `integration_crypto_exclusions` | `id`, `connection_id` (FK, `ON DELETE CASCADE`), `external_id`, `asset_id` (FK), `source_snapshot` (JSONB), `excluded_at`; único `(connection_id, external_id)` | Operação de cripto importada e excluída pelo usuário: não volta na próxima sincronização e pode ser restaurada (6.10). Uma tabela por domínio: a renda fixa terá `integration_fixed_income_exclusions`, se o Open Finance vier a precisar |

Criptografia: AES-256-GCM na aplicação, com a chave-mestra em variável de ambiente
(`PRIMEVEST_CREDENTIALS_KEY`) e `key_version` para permitir rotação. O banco sozinho não abre nada.

### 6.4 Segurança e LGPD

| Tema | Como |
|---|---|
| Permissão mínima | Na conexão e a cada sync, `GET /sapi/v1/account/apiRestrictions` (equivalente em cada corretora). Qualquer permissão além de leitura → `PERMISSION_TOO_BROAD`, sync bloqueado, aviso na tela |
| IP fixo | O servidor sai por um IP estável, exibido na tela de conexão com a instrução "restrinja sua chave a este IP". Recomendado, não obrigatório |
| Segredo nunca volta | A API só devolve `fingerprint`. Trocar a chave = cadastrar de novo |
| Logs | Nunca registrar chave, segredo, assinatura nem query assinada. Filtro no `WebClient` |
| Consentimento | Texto explícito na conexão: o que é lido, para quê, por quanto tempo. Aceite gravado com data |
| Revogação | "Desconectar" apaga a credencial na hora; o usuário escolhe se mantém ou apaga as transações importadas |
| Exclusão de conta | Apaga conexões, credenciais, runs e transações em cascata |
| Auditoria | `integration_runs` + quem conectou/desconectou e quando |
| Abuso | Limite de sincronizações manuais por usuário (ex.: 1 a cada 10 min) |

### 6.5 Execução e limites

- **Fila no Redis** (já existe no compose). Um worker processa conexões; **uma sincronização por conexão por vez**.
- **Orçamento de peso por IP, compartilhado.** Parte dos limites da Binance é por IP, e todos os usuários saem do
  mesmo servidor: um *token bucket* por provedor no Redis controla o total. Limites por conta (`fiat/orders`,
  Convert) são naturalmente por usuário.
- **Incremental:** cada `stream` retoma do seu cursor. A primeira importação busca o histórico inteiro e pode
  levar minutos — roda em segundo plano, com progresso na tela.
- **Descoberta de pares:** saldos + depósitos + pares já vistos na conexão; teste completo só na primeira vez
  e sob demanda.

### 6.6 Duplicatas com lançamentos manuais

Usuários novos provavelmente já digitaram parte das compras à mão. O pipeline compara cada movimento
importado com as transações `MANUAL` do mesmo ativo: mesmo tipo, mesma data (±1 dia, por fuso) e
quantidade dentro da taxa. Havendo candidato, o item vai para a pré-visualização como **possível
duplicata**, e o usuário escolhe: substituir o manual, manter os dois ou ignorar o importado.

### 6.7 API e telas

| Endpoint | Função |
|---|---|
| `GET /api/v1/integrations/providers` | Fontes disponíveis e o que cada uma importa |
| `GET/POST/DELETE /api/v1/integrations/connections` | Listar, conectar (valida na hora), desconectar |
| `POST /api/v1/integrations/connections/{id}/sync` | Enfileirar sincronização |
| `GET /api/v1/integrations/connections/{id}/runs` | Histórico e itens não importados |

Telas: **Integrações** (conectar com passo a passo por corretora, IP a liberar, status, última
sincronização, desconectar) e **Pré-visualização** (importar / já existe / não mapeado / possível
duplicata). Na carteira, selo de origem nas transações importadas (editáveis — 6.10).

### 6.8 Outras fontes

| Fonte | Multiusuário? | Caminho |
|---|---|---|
| Corretoras cripto com chave de API (Binance, Mercado Bitcoin, Foxbit, OKX, Bybit…) | ✅ | Um conector por corretora |
| Open Finance (caixinhas, CDBs de banco) | ⚠️ com custo | O Meu Pluggy é só para uso pessoal. Para vários usuários, contratar agregador regulado (Pluggy, Belvo ou similar) — cobrança por conexão/mês, a confirmar |
| B3 | ⚠️ fora deste plano | Sem cripto. Importação do extrato da B3 pelo PrimeVest virou escopo próprio (`docs/PENDENCIAS.md`, seção 16) |
| Qualquer instituição sem API | ⚠️ fora deste plano | Importação por arquivo: escopo próprio, junto com a B3 (`docs/PENDENCIAS.md`, 16) |

### 6.9 Ordem de entrega sugerida

| # | Entrega | Valida |
|---|---|---|
| F1 | Migrations de 6.3 + criptografia de credenciais | ✅ **Executada em 25/09/2026** — ver 6.11 |
| F1b | Precisão de 8 casas para cripto: modal, telas e preço médio (6.12) | ✅ **Executada em 25/09/2026** — ver o fim de 6.12 |
| F2 | `SourceConnector`, `ImportedMovement`, `ImportPipeline` (R1–R8) com testes sobre **dados sintéticos** no formato das respostas da Binance — nenhum dado real de conta entra no repositório | ✅ **Executada em 25/09/2026** — ver 6.14 |
| F3 | `BinanceConnector` + validação de permissão + endpoints de conexão e pré-visualização (6.7, sem a confirmação) | ✅ **Executada em 25/09/2026** — ver 6.15. Teste ponta a ponta com a conta do Guilherme aprovado |
| F4 | Pré-visualização, confirmação e detecção de duplicata | ✅ **Executada em 25/09/2026** — ver 6.16. Teste com a conta real aprovado |
| F5 | Fila, worker, orçamento de peso, cursores, scheduler | ✅ **Executada em 25/09/2026** — ver 6.17. Teste com a conta real aprovado |
| F6 | Front: Integrações + Pré-visualização + selo de origem | ✅ **Executada em 26/09/2026** — ver 6.18. Conferida no navegador com a conta real |
| F7 | Segunda corretora cripto | A interface aguenta uma fonte que não foi desenhada junto |

> A F7 original (importação por arquivo, primeiro layout o extrato da B3) saiu deste plano em
> 25/09/2026 — a B3 não tem cripto, e as regras da F2 são de cripto. Virou escopo próprio em
> `docs/PENDENCIAS.md`, seção 16. A antiga F8 passou a ser a F7.

A fase 3 (o MCP chamando o PrimeVest) deixa de ser necessária para o usuário final: a importação é
pela tela. O MCP continua como ferramenta de levantamento para cada fonte nova.

### 6.10 Edição de transações importadas (D9)

Importadas são editáveis. Quatro regras evitam que a sincronização e a edição briguem:

| # | Regra | Como |
|---|---|---|
| E1 | **Sincronizar não desfaz edição** | Ao salvar, a transação ganha `edited_at`. O pipeline encontra o `external_id`, vê a marca e não toca na linha |
| E2 | **Excluir = ignorar** | Excluir uma importada grava `(connection_id, external_id)` em `integration_crypto_exclusions`. A próxima sincronização encontra a operação na corretora, consulta a lista e não recria |
| E3 | **Aviso de divergência** | Depois de cada sincronização, compara a posição do PrimeVest com o saldo da corretora por ativo. Diferença → aviso na conexão e na posição ("PrimeVest 0,0100 BTC · Binance 0,0098 BTC") |
| E4 | **Restaurar original** | `source_snapshot` guarda o movimento da fonte. "Restaurar dados da corretora" reaplica as regras sobre ele, regrava a transação e limpa `edited_at`; para uma excluída, importa de volta **na hora** a partir do movimento guardado e apaga a exclusão (com os cursores adiante, a corretora não a devolveria sozinha — ajuste da F4) |

O selo de origem continua na transação editada ("Binance · editada"), para ficar claro que ela não é
mais idêntica à corretora.

### 6.11 F1 executada (25/09/2026)

| Peça | Onde |
|---|---|
| Migration | `V25__integracoes.sql`: 6 tabelas (`integration_connections`, `integration_credentials`, `integration_cursors`, `integration_runs`, `integration_run_items`, `integration_crypto_exclusions`) + `transactions.origin/external_id/connection_id/executed_at/edited_at/source_snapshot` |
| Entidades e repositórios | `domain/integration/` (conexão escopada pelo dono: `findOwnedById`, `findAllOwned`); `TransactionOrigin` em `domain/transaction/` |
| Criptografia | `domain/integration/credential/`: `CredentialCipher` (AES-256-GCM, IV de 12 bytes por cifragem, id da conexão como AAD, versão da chave para rotação), `CredentialVault` (único ponto que grava, lê, recifra e apaga), `ApiKeyCredentials` (`toString` mascarado) |
| Configuração | `PRIMEVEST_CREDENTIALS_KEY` / `_KEY_VERSION` / `_PREVIOUS_KEYS` no `.env` → `docker-compose.yml` → `primevest.integrations.credentials.*`. Sem chave a API sobe e só as integrações ficam indisponíveis; chave mal formada derruba a subida |
| Testes | `CredentialCipherTest` (9) e `IntegrationRepositoryTest` (15); `SchemaTest` na V25 |

Ajustes feitos na implementação em relação a 6.3:

- Tabelas de execução renomeadas para o mesmo prefixo das demais: `import_runs` → `integration_runs`,
  `import_run_items` → `integration_run_items`.
- `integration_cursors.cursor` → `cursor_value` (`cursor` é palavra-chave do SQL).
- `integration_connections.consent_accepted_at` (NOT NULL): o aceite do consentimento de 6.4.
- `transactions.connection_id` com `ON DELETE SET NULL`: se a conexão for apagada e o usuário mantiver
  as transações, elas seguem como importadas, só sem vínculo. `ck_transactions_origin` confere que
  manual não tem `external_id`/`source_snapshot`/`edited_at` e importada tem os dois primeiros.
- Idempotência: índice único parcial `(connection_id, external_id) WHERE connection_id IS NOT NULL`.

### 6.12 Precisão de cripto — 8 casas (F1b)

Levantado em 25/09/2026. **O banco está pronto; a aplicação não.**

| Camada | Hoje | Problema com cripto |
|---|---|---|
| Banco | `shares` e `price_per_share` em `numeric(18,8)` | ✅ Nenhum. As 8 casas existem justamente por causa de cripto (`Claude.md`, "Precisão decimal") |
| API — entrada | `TransactionCreateDTO` aceita qualquer escala; o banco guarda 8 | ✅ Nenhum |
| API — preço médio | `PositionCalculator.AVERAGE_PRICE_SCALE = 4` | ⚠️ Para BTC não pesa; para token de centavo (ex.: R$ 0,00007123) o preço médio vira R$ 0,0001 — erro de 40% |
| API — preço implícito sem cotação | `PortfolioService`, divisão com 4 casas | ⚠️ Idem |
| Modal — quantidade | `MaskedNumberInput decimals={2}` | ❌ **Impossível lançar 0,00197802 BTC**: a máscara só aceita 0,01 |
| Modal — preço unitário | `CurrencyInput`, 2 casas | ⚠️ Preço importado tem 8 casas (Convert: R$ 525.297,05592977); editar a transação truncaria |
| Tela de transações | quantidade com até 4 casas (`formatShares`); preço com 2 (`formatCurrency`) | ❌ 0,00197802 aparece como 0,002 |
| Carteira | quantidade com até 4 casas; preço médio e atual com 2 | ❌ Idem |

Proposta:

| # | Mudança | Regra |
|---|---|---|
| 1 | Modal: quando o ativo escolhido é do tipo `CRIPTO`, **quantidade e preço unitário** usam a mesma máscara dos demais tipos, com **8 casas** | Decisão do Guilherme em 25/09/2026, depois de testar a primeira versão (casas livres, digitadas com vírgula) |
| 2 | Exibição de cripto (transações e carteira): quantidade e preços com **até 8 casas, sem zeros à direita**, mínimo de 2 no preço | "0,00197802 BTC", "R$ 503.233,00", "R$ 0,00007123". Já existe `formatCurrencyPrecise` para proventos — reaproveitar |
| 3 | `AVERAGE_PRICE_SCALE` e o preço implícito passam para **8** | Casa com a coluna do banco. Não muda nada visível para ações (a tela arredonda na exibição) |
| 4 | Totais em reais continuam com 2 casas | `total_amount`, taxa, valor da posição: dinheiro é centavo |

Limite conhecido: 8 casas no **preço** dão poucos dígitos significativos para tokens abaixo de
R$ 0,000001 (alguns memecoins). Nenhum deles está na carteira; se aparecer, aumentar a escala do
preço é migration (`numeric(18,8)` → `numeric(24,12)`) — registrado, não planejado.

Quando: antes da F4, porque é na F4 que transações com 8 casas passam a entrar. Não depende da F2 e
pode ser feita a qualquer momento — e já corrige o lançamento manual de cripto de hoje.

**Executada em 25/09/2026:**

| Peça | Onde |
|---|---|
| Tipo cripto | `AssetType.isCrypto()`; `isCrypto` em `TransactionResponseDTO` e `PortfolioAssetDetailDTO`; `CRIPTO`, `CRYPTO_DECIMALS` e `isCryptoType` em `utils/assetTypeRules.ts` |
| Preço médio | `AVERAGE_PRICE_SCALE` = 8; preço implícito da carteira com 8 casas |
| Modal | Cripto: quantidade (`MaskedNumberInput`) e preço unitário (`CurrencyInput`) com `decimals={8}` — mesma máscara da direita para a esquerda dos demais tipos, teto de 9.999.999,99999999. A primeira versão (casas livres com vírgula) foi desfeita |
| Telas | `formatQuantity` (0 a 8 casas) e `formatUnitPrice` (2 a 8) em Transações e Carteira |
| Testes | preço médio de fração de centavo (`PositionCalculatorTest`); 417 verdes. Modal conferido no navegador: 0,00197802 × R$ 503.233,00 = R$ 995,40 |

### 6.13 Decisões da F2 (25/09/2026)

| Ponto | Decisão |
|---|---|
| Agrupar fills | No conector: cada corretora entrega a ordem inteira; o pipeline recebe movimentos prontos |
| `ImportedMovement` | `externalId`, `kind` (TRADE, CONVERT, FIAT_DEPOSIT, CRYPTO_DEPOSIT, CRYPTO_WITHDRAW, REWARD), ativo, lado, quantidade bruta, taxa e ativo da taxa, valor em reais, `executedAt` (UTC) e JSON bruto |
| Alcance | Só monta a pré-visualização (nova / já existente / excluída / não importada, com motivo). Gravar pelo `TransactionService` fica para a F4 |
| Ticker fora do catálogo | Não importada; sem tabela de de-para até aparecer o primeiro caso real |
| Fuso | `executed_at` em UTC (como o container e os `created_at`); `transaction_date` no fuso de Brasília (R3) |
| Dados de teste | **Sintéticos**, no formato das respostas da Binance. Nenhum dado real de conta no repositório |
| Arredondamento | Até R$ 0,01 de diferença por operação entre o calculado e o cobrado (4.5) |

### 6.14 F2 executada (25/09/2026)

| Peça | Onde |
|---|---|
| Contrato da fonte | `domain/integration/source/`: `SourceConnector` (`provider`, `check`, `fetch`), `ImportedMovement`, `MovementKind`, `MovementSide`, `CredentialCheck`, `FetchResult` |
| Regras | `domain/integration/pipeline/CryptoImportRules`: R2 (taxa no ativo → quantidade líquida + taxa em reais; taxa em reais → como veio), R3 (data em Brasília, instante em UTC), R4 (Convert: preço = reais ÷ quantidade, taxa 0), R6 (só cotação em BRL), R7 (ticker do catálogo e do tipo cripto; nunca cria ativo), R8 (taxa em outra moeda → não importada), 4.5 (diferença acima de R$ 0,01 → não importada). Pura: sem banco nem relógio |
| Pipeline | `ImportPipeline.preview(conexão, movimentos)`: remove `externalId` repetido no lote, soma os depósitos em reais à parte, marca **já existente** (`TransactionRepository.findImportedIds`) e **excluída** (`findExcludedIds`) antes das regras, e classifica o resto em **nova** ou **não importada** com o motivo. Não grava nada |
| Testes | `ContaSintetica` (conta inventada que fecha: depósitos, 3 compras, 1 Convert, 1 venda); `CryptoImportRulesTest` (13), `ImportPipelineTest` (5, com a conciliação de BTC, ETH e BRL), `findImportedIds` no `IntegrationRepositoryTest` |

Decisões tomadas na implementação:

- `ImportedMovement.quoteAmount` é o valor **sem** a taxa quando ela é cobrada na própria moeda de
  cotação (é o `quoteQty` da Binance). O conector do "Comprar Cripto" (`fiat/payments`) entrega
  `sourceAmount − totalFee`, com a taxa em BRL.
- Venda com taxa cobrada no próprio ativo fica como não importada ("caso ainda não observado"):
  a Binance cobra a taxa da venda em reais, e adivinhar a regra seria pior que mostrar.
- Depósito em reais não vira linha da pré-visualização; entra em `fiatDeposited`, para a conferência
  "aportado × comprado" (4.6).
- R1 (agrupar os pedaços de uma ordem) e R5 (`externalId`) ficam no conector — são testados na F3.

### 6.15 F3 executada (25/09/2026)

Escopo ampliado em relação à tabela original: além do conector, os endpoints de conexão e de
pré-visualização entraram aqui, para o teste passar pelo mesmo caminho de qualquer usuário.

| Peça | Onde |
|---|---|
| Cliente HTTP | `integration/binance/BinanceClient`: só GET; HMAC-SHA256 da query exata; `timestamp` no relógio da Binance (`/api/v3/time`) e nova tentativa única no -1021; chave no cabeçalho; decimais como BigDecimal; mensagem de erro sem chave, segredo ou assinatura (`BinanceException`) |
| Conector | `integration/binance/BinanceConnector` (`SourceConnector`): `check` via `/sapi/v1/account/apiRestrictions` (recusa qualquer permissão além de leitura, com o motivo em português); `fetch` de negociações spot (R1: pedaços da mesma ordem somados), Convert (janelas de 30 dias com pausa), "Comprar Cripto", depósitos PIX e depósitos de cripto; cursores por fluxo |
| API | `IntegrationController` / `IntegrationService`: `GET`, `POST` (confere a chave ANTES de gravar; exige consentimento), `DELETE` (apaga a credencial, marca REVOKED) e `POST /{id}/preview` (confere a permissão de novo — P2 —, busca, aplica o pipeline, não grava transação nem cursor). Chamadas à corretora fora de transação de banco. 502 para erro da Binance, 503 para chave-mestra ausente |
| Configuração | `binance.*` no `application.yml`: início do histórico (2017-07-01; Convert 2020-01-01), pausa do Convert (1,1 s), moedas de cotação e moedas sondadas |
| Testes | `BinanceClientTest` (5), `BinanceConnectorTest` (8), `IntegracoesIT` (10, com dois usuários isolados, chave recusada, chave que negocia, permissão que muda depois de conectada, desconexão); conta fictícia em `support/BinanceFake`. 459 verdes |

Limites conhecidos desta versão (a registrar para F5):

- **Pares consultados**: moedas com saldo, as dos outros fluxos, `binance.probe-assets` e os pares já
  vistos, contra `binance.quote-assets`. Moeda comprada e vendida por inteiro fora dessas listas não
  é encontrada.
- **Ordem executada em partes em sincronizações diferentes** (ordem limitada que termina de executar
  depois da busca): com o cursor por id de negociação, os pedaços posteriores ficariam com o mesmo
  `externalId` e seriam tidos como já importados. Só afeta a busca incremental — tratar na F5.
- **Primeira busca é lenta**: o Convert pede uma janela de 30 dias por segundo desde 2020 (~75 s).
  Síncrona nesta etapa; vai para a fila na F5.
- Fora: saques de cripto, rendimentos (Earn) e "Vender Cripto" (`fiat/payments` tipo 1, formato não
  observado).

Teste ponta a ponta (Guilherme, pelo Swagger): `POST /api/v1/integrations/connections` com a própria
chave só de leitura → `POST /{id}/preview`. Esperado, pela conciliação da seção 3: 7 novas (6 ordens
BTCBRL + 1 Convert), 8 depósitos PIX somados, 1 "Comprar Cripto" que falhou fora da lista, saldo de
BTC igual ao da Binance.

**Resultado (25/09/2026, Swagger, conta real):** 7 novas, 0 já existentes, 0 excluídas, 0 não importadas;
depósitos PIX somados = R$ 3.599,50. Soma das quantidades = saldo de BTC da Binance, nas 8 casas. Reais:
registrado R$ 3.598,70 contra R$ 3.598,71 cobrados — 1 centavo, numa única operação (arredondamento, 4.5).
Duas compras depois das 21h caíram no dia certo pelo fuso de Brasília (R3). A compra "Comprar Cripto"
que falhou não aparece: o conector só entrega pagamento concluído.

### 6.16 F4 executada (25/09/2026)

| Peça | Onde |
|---|---|
| Migration | `V26__integracoes_confirmacao.sql`: `integration_run_items` passa a guardar o lote inteiro (outcome `NEW`/`EXISTING`/`EXCLUDED`/`UNMAPPED`/`POSSIBLE_DUPLICATE`/`FAILED`), com `duplicate_of`, `resolution` e `transaction_id`; `integration_runs.next_cursors` e `fiat_deposited` |
| Pré-visualização guardada | `POST /{id}/preview` agora cria uma execução `AWAITING_CONFIRMATION` com todos os movimentos (o `ImportedMovement` em JSON, via `MovementJson`) e os cursores novos — que **só** viram `integration_cursors` na confirmação. Nova pré-visualização cancela a anterior. `GET /{id}/runs/{runId}` relê sem ir à corretora |
| Duplicatas (6.6) | `ImportPipeline`: importada que parece com um lançamento MANUAL (mesmo ativo e tipo, data a até 1 dia, quantidade entre a líquida e a bruta com folga de 0,1%) vira `POSSIBLE_DUPLICATE`, apontando para ele; cada manual casa com uma importada só |
| Confirmação | `POST /{id}/runs/{runId}/confirm` com `{"duplicates": {"<externalId>": "REPLACE" \| "KEEP_BOTH" \| "SKIP"}}`. Grava pelo `TransactionService.importTransaction` — as mesmas regras do lançamento manual (P6) —, na ordem cronológica; REPLACE importa primeiro e apaga o manual depois. **Tudo ou nada**: uma operação recusada desfaz o lote, e a execução segue aguardando com o motivo em `error`. Sem decisão para uma duplicata, recusa. `POST .../cancel` descarta |
| Edição (6.10) | E1: editar importada grava `edited_at` (continua AUTO). E2: excluir importada grava `integration_crypto_exclusions`. E4: `POST /api/v1/transactions/{id}/restore` volta aos valores da corretora; `GET /{id}/exclusions` e `POST /{id}/exclusions/{exclusionId}/restore` (reimporta na hora) |
| Transação | `TransactionResponseDTO` ganhou `origin` e `editedAt` (selo de origem da F6) |
| Testes | `IntegracoesConfirmacaoIT` (9): confirmar, segunda busca incremental, confirmar duas vezes, pré-visualização substituída, duplicata REPLACE e SKIP, editar/restaurar, excluir/restaurar, excluir manual; duplicatas no `ImportPipelineTest` |

Fica para a F5: E3 (aviso de divergência entre a posição e o saldo da corretora), que depende da
sincronização automática para ter onde avisar.

**Teste com a conta real (25/09/2026, Swagger):** pré-visualização → confirmação: execução `SUCCEEDED`,
7 operações gravadas como `AUTO`. No banco: posição de BTC = **0,00831073** (igual ao saldo da Binance),
total R$ 3.595,39 + taxas R$ 3,31 = R$ 3.598,70; cursores gravados para `spot:BTCBRL`, `convert` e
`fiat_deposits`; valorização da posição recalculada na hora.

### 6.17 F5 executada (25/09/2026)

| Peça | Onde |
|---|---|
| Migration | `V27__integracoes_fila.sql`: `integration_runs.balances` (saldo da corretora na busca) e índice `(status, created_at)` |
| Fila | **A própria `integration_runs`**, não o Redis de 6.5: `POST /{id}/preview` responde na hora (**202**, `QUEUED`); pedir de novo com uma na fila devolve a mesma. Motivo da troca: a execução já é a unidade de trabalho, o PostgreSQL dá `FOR UPDATE SKIP LOCKED`, e os testes funcionais não têm Redis |
| Worker | `IntegrationWorker` (a cada `worker-delay-ms`, 5 s): pega a `QUEUED` mais antiga cuja conexão não tenha outra `RUNNING`, busca na corretora **fora de transação** e deixa `AWAITING_CONFIRMATION` (ou `FAILED`, com o motivo em `error`). Uma por vez — o que também torna o limite de peso um orçamento único. `RUNNING` há mais de `stale-minutes` (30) vira `FAILED`; na subida da API, todas as `RUNNING` viram `FAILED` (vale com uma instância só) |
| Peso da Binance | `BinanceClient` lê `X-MBX-USED-WEIGHT-1M`; passando de `binance.weight-threshold` (4.800 de 6.000), a próxima chamada espera o minuto virar. 429: espera o `Retry-After` e tenta uma vez. 418 (IP banido): erro |
| Rotina diária | `IntegrationSyncScheduler` (`sync-cron`, 06:30 de Brasília): enfileira as conexões ativas sem nada pendente — a que tem pré-visualização aguardando o usuário fica de fora. A execução agendada **confirma sozinha** quando não há possível duplicata e a conexão já teve uma importação confirmada pelo usuário (`auto-confirm-scheduled`); senão, fica aguardando |
| E3 — divergência | Execução confirmada traz `divergences`: por ativo, posição de cripto no PrimeVest × saldo Spot da corretora na busca (BRL fora). Na conta fictícia dos testes, aponta exatamente o Convert BTC → ETH que não é importado (R6) |
| Conexões | a lista traz `lastRunId` e `lastRunStatus` |
| Testes | `IntegracoesFilaIT` (8: fila, worker, falha, rotina diária com e sem duplicata, divergência, execução perdida, última execução na lista); peso e 429 no `BinanceClientTest`; os testes das F3/F4 passaram a sincronizar pela fila |

Limites que continuam:

- A divergência compara com a carteira Spot; saldo em Earn/Funding não entra. Posição do mesmo ativo
  em outra carteira ou corretora também aparece como diferença.
- Ordem executada em partes em sincronizações diferentes (6.15) segue em aberto.
- Com mais de uma instância da API, a recuperação "na subida" precisaria ser só por prazo.

Teste com a conta real: `POST /{id}/preview` (202) → aguardar alguns segundos → `GET /{id}/runs/{runId}`.
Como a importação já foi confirmada, a busca é incremental: esperado **0 novas** e, depois de confirmar,
divergência só no USDC (resíduo sem origem, PENDENCIAS 15.4).

**Resultado (26/09/2026, conta real):** busca incremental com 0 novas, 0 já existentes, nada a importar;
confirmada (`SUCCEEDED`), com uma única divergência: USDC 0,05814558 na Binance × 0 no PrimeVest — o
resíduo sem origem previsto. BTC sem divergência.

### 6.18 F6 executada (26/09/2026)

| Peça | Onde |
|---|---|
| Menu | Item **Integrações** (ícone `Plug`) depois de Metas. Marcador amarelo (número; ponto com o menu recolhido) quando alguma conexão tem pré-visualização aguardando, divergência ou chave com problema (`needsAttention`); consulta a cada 60 s |
| `/integrations` | Card por conexão: situação, `****fingerprint`, última sincronização e última execução, **Sincronizar**, **Ver pré-visualização/resultado**, **Histórico** (execuções), **Excluídas** (com Restaurar = reimportação imediata, E4), **Desconectar** com confirmação; conexão revogada oferece "Conectar de novo". Consulta a cada 3 s enquanto houver execução `QUEUED`/`RUNNING`, e avisa quando termina |
| Conectar Binance | Modal com o passo a passo da chave só de leitura, apelido, chave, segredo oculto (com mostrar) e consentimento. Nada vai para `localStorage`; os campos limpam ao fechar |
| `/integrations/:id/runs/:runId` | Página própria: resumo, grupos (possíveis duplicatas, novas, não mapeadas, já existentes, excluídas), decisão por duplicata (Substituir / Manter os dois / Ignorar), **Confirmar** só com todas decididas, **Descartar**; confirmada mostra a resolução de cada item com link e as divergências |
| Transações | Selo "Binance" / "Binance · editada"; filtro **Origem** (Todas/Digitadas/Importadas); ação **Restaurar dados da corretora** na importada editada; excluir importada avisa que ela não volta e onde restaurar |
| Back | `ConnectionResponseDTO.divergenceCount`; `TransactionResponseDTO.connectionLabel`/`connectionProvider`; `ImportPreviewDTO` com apelido da conexão e datas. Teste `IntegracoesFilaIT.dadosDaTela` |
| Validação | `mvn clean verify` verde (141 ITs); `tsc -b`, `eslint` e `vite build` sem erro. Conferência no navegador com a conta real (só leitura): card, histórico, excluídas, as duas execuções, divergência USDC, filtro Origem (7 importadas / 89 digitadas), selo, tema claro e menu recolhido com o ponto |

---

## 7. Decisões

Respostas do Guilherme em 25/09/2026.

| # | Decisão | Situação |
|---|---|---|
| D1 | Taxa: `shares` líquido + `brokerage_fee` em BRL (R2) | ✅ **Decidido.** A taxa é registrada, mas **não** entra no preço médio: descontos de taxas e impostos (IOF, IR, custo de aquisição) estão fora do escopo, como na renda fixa e na B3. Sem pendência nova. O `shares` líquido continua obrigatório: a taxa da Binance sai em BTC e, sem descontá-la, a posição fica maior que o saldo real |
| D2 | Agrupar fills da mesma ordem numa transação (R1) | ✅ **Decidido.** Agrupar: uma ordem = uma transação, com preço médio ponderado |
| D3 | BRL e stablecoins | ✅ **Decidido.** BRL fica fora: é dinheiro, não posição. **USDC e USDT são cripto** (Stablecoins, V19) e são tratados como qualquer outra. O resíduo de USDC da conta de teste não tem operação de origem, então não há o que importar (PENDENCIAS 15.4) |
| D4 | Carregar as 7 operações antes da fase 2 | ✅ **Decidido.** Esperar a fase 2 |
| D5 | Operações sem valor em reais | ✅ **Decidido.** Fora da primeira versão; aparecem como não importadas. Registrado em `docs/PENDENCIAS.md`, seção 15 |
| D6 | Transferências entre carteiras | ✅ **Decidido.** Fora da primeira versão |
| D7 | Próximas corretoras | ✅ **Decidido.** Um planejamento por corretora (como este). Serviço de integração com **interfaces genéricas** (`SourceConnector`, `ImportedMovement`, `ImportPipeline` — 6.2); a Binance é a primeira implementação delas |
| D8 | Produção / IP fixo | ✅ **Decidido.** Fora de escopo — o projeto está em DEV. A restrição de IP (6.4) fica como orientação opcional até existir produção |
| D9 | Transações importadas editáveis ou somente leitura | ✅ **Decidido.** **Editáveis**, com as 4 regras de 6.10 |
| D10 | Open Finance multiusuário | ✅ **Registrado.** Completamente em aberto, sem planejamento fixo. A linha de Open Finance em 6.8 é só referência, não compromisso |

---

## 8. Arquivos (fora do repositório)

| Arquivo | O que é |
|---|---|
| `binance-mcp/` | MCP só leitura (v0.1.1) |
| `binance/*.json` | dumps brutos de cada consulta (descartados em 03/10/2026) |
| `converter_binance.mjs` | aplica R1–R8 aos dumps e concilia (descartado em 03/10/2026) |
| `carga_binance_guilherme.json` | 7 operações no formato da carga B3 + conciliação + posição esperada (descartado em 03/10/2026) |
