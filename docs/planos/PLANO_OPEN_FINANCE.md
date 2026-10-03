# PrimeVest — Plano de Integração Open Finance (Renda Fixa fora da B3)

> Fase: **planejamento** (24/09/2026) · nada implementado · branch `TRACK_01-ajustes_crud_inicial`
> Escopo: importar renda fixa que a B3 não mostra — Caixinhas (Nubank), Porquinhos (Inter), Cofrinhos (PicPay) e afins.
> Entrada: anexo 1 (`PLANO INTEGRAÇÂO OPEN FINANCE.docx`, pesquisa feita em outra IA).

Objetivo: dizer se o plano do anexo 1 se sustenta, o que falta no banco para executá-lo e como
testar com as Caixinhas do Nu antes de escrever qualquer linha de produção.

---

## 1. Veredito em uma frase

**Viável para uso pessoal, pelo Meu Pluggy — não pelo Open Finance "direto" como o anexo sugere.**
A direção (Open Finance em vez de B3, com lançamento manual de fallback) está certa; três premissas
do anexo não se sustentam e mudam o desenho: quem pode receber os dados, o nome das Caixinhas, e a
criação de uma categoria nova de "Reservas".

---

## 2. Passo 1 — Viabilidade do anexo 1

### 2.1 O que se confirma

| Afirmação do anexo | Situação |
|---|---|
| Open Finance, e não B3, é o caminho para Caixinha/Porquinho/Cofrinho | ✅ Confirmado. O Open Finance tem API de **Renda Fixa Bancária** (fase de investimentos) com tipos `CDB`, `RDB`, `LCI`, `LCA` |
| Consentimento controlado pelo cliente e revogável | ✅ Confirmado |
| Nem todo produto aparece na API com o nome do app | ✅ Confirmado — e é pior do que o anexo diz (2.2-b) |
| Manter uma coluna de **origem** do dado | ✅ Certo, e o projeto já tem o padrão (`dividends.origin` = `AUTO`/`MANUAL`) |
| Fallback manual | ✅ Já existe: é o lançamento de renda fixa de hoje |

### 2.2 O que não se sustenta (e muda o plano)

**a) O PrimeVest não pode se conectar ao Open Finance sozinho.**
Só instituições autorizadas pelo Banco Central recebem dados no Open Finance; pessoa física ou
empresa não regulada não recebe direto. O desenho "PRIMEVEST → OPEN FINANCE → Nubank/Inter/PicPay"
do anexo precisa de um intermediário regulado no meio.

Para uso pessoal, esse intermediário existe e é gratuito: **Meu Pluggy**.
- Você conecta suas contas em `meu.pluggy.ai` (fluxo oficial de Open Finance, autorizado no app do banco).
- Cria uma aplicação em `dashboard.pluggy.ai` → `client_id`/`client_secret`, habilita o conector **MeuPluggy**.
- O PrimeVest lê pela API da Pluggy (`POST /auth` → `X-API-KEY`, válido por 2 h).
- Limites: grátis por tempo indeterminado, **até 5 conexões ativas**, **somente o próprio titular**,
  atualização **a cada 24 h**. Dados: contas, extrato, cartões e **investimentos**.
- ⚠️ A conexão do conector tem de ser feita **durante o trial de 15 dias** do dashboard; depois
  disso a lista de conectores fica travada (relato da documentação do Actual Budget).

Consequência: serve para a **sua** carteira. Se um dia o PrimeVest tiver outros usuários, o custo
muda de ordem de grandeza (plano "Dados" da Pluggy a partir de R$ 2.500/mês) ou vira parceria com
instituição autorizada. Fica registrado como limite do desenho, não como tarefa.

**b) "Caixinha Reserva" e "Caixinha Viagem" não vêm com esse nome.**
A API padrão de Renda Fixa Bancária **não tem campo de nome/apelido do produto**. O que vem por
aplicação é: tipo (`RDB`), CNPJ do emissor, ISIN, remuneração (indexador + % ou taxa),
emissão/vencimento/carência e o saldo (bruto, líquido, IR, IOF). O Nubank declara que as Caixinhas
são **RDBs emitidos pela Nu Financeira** ("RDB Diário/Imediato/Planejado"; contas antigas podem ter
o fundo "Nu Reserva Imediata", que viria pela API de **fundos**, não de renda fixa).

Então o provável é: N registros "RDB – Nu Financeira – 100% CDI", sem saber qual é qual Caixinha —
e talvez **mais de um RDB por Caixinha** (um por aplicação). O teste da seção 5 decide isso. O
desenho precisa de um **apelido editável** e de uma forma de agrupar.

**c) Não criar a categoria "Reservas financeiras".**
O PrimeVest já resolve isso de outro jeito, e melhor:
- `RDB`, `CDB`, `LCI`, `LCA` e `POUPANCA` **já existem no catálogo** (tipo `RENDA_FIXA`, V7).
- A posição já tem `portfolio_assets.is_reservation`, e a estratégia de reserva já existe
  (`strategy_reservations`, por % ou por valor).
- Um `asset_type` novo quebraria a Estratégia (alvo % por tipo) e duplicaria o conceito de reserva.

Caixinha = posição `RENDA_FIXA / RDB` com `is_reservation = true` quando for reserva. Sem tipo novo.

**d) "Conta / Saldo em conta" está fora do escopo.** Saldo de conta corrente não é investimento e o
escopo é renda fixa. Fica como possível fase futura.

**e) Porquinho (Inter) e Cofrinho (PicPay).** Não confirmei em fonte oficial em qual papel cada um
aplica (provavelmente CDB). Se for CDB/RDB/LCI/LCA de emissão bancária, cai na mesma API. Validar
quando conectar cada banco — o desenho não muda.

### 2.3 O que o anexo não viu

- **Histórico de só 12 meses.** O endpoint de movimentações devolve os últimos 12 meses. Uma
  Caixinha mais antiga não tem o custo reconstituível pela API → a importação precisa de uma
  **transação de saldo inicial** (compra sintética datada no início da janela, marcada como tal).
- **Dado D-1.** A posição vem com `referenceDateTime`; pelo Meu Pluggy, atualização diária.
- **Ganho colateral grande:** a `docs/PENDENCIAS.md` item 2 ("valorização de renda fixa dá sempre
  zero") **se resolve sozinha para os títulos importados** — a API entrega o saldo bruto atual, e
  `appreciation = saldo bruto − (aplicado − resgatado)`, sem precisar da curva do CDI.
- **Venda de renda fixa bloqueada (PENDENCIAS 4.2)** não atrapalha: o bloqueio é por falta do
  seletor de título no front; o importador já sabe o `note_id` de cada resgate.

---

## 3. Arquitetura proposta

```
Nubank / Inter / PicPay
        │  (Open Finance — consentimento no app do banco)
        ▼
   Meu Pluggy  (receptor autorizado; 5 conexões; D-1)
        │  API Pluggy: /auth · /items · /investments · /investments/{id}/transactions
        ▼
 integration/pluggy/PluggyClient          ← mesmo molde de integration/brapi/
        ▼
 fixedincome/OpenFinanceImportService     ← normaliza e grava
   ├─ fixed_income_notes   (1 por investimento externo, com external_id)
   ├─ portfolio_assets     (asset RDB/CDB/LCI/LCA; is_reservation editável)
   ├─ transactions         (APLICACAO→BUY, RESGATE/VENCIMENTO→SELL; saldo inicial)
   └─ appreciation         (saldo bruto − custo líquido)
        ▲
 scheduler/OpenFinanceSyncScheduler (diário) + botão "Sincronizar"
```

Credenciais (`PLUGGY_CLIENT_ID`, `PLUGGY_CLIENT_SECRET`) em variável de ambiente / `.env` do
compose — **nunca** no banco nem no repositório.

### 3.1 Mapeamento Open Finance → PrimeVest

| Open Finance (Renda Fixa Bancária) | PrimeVest | Já existe? |
|---|---|---|
| `investmentType` (CDB/RDB/LCI/LCA) | `assets.ticker` do catálogo | ✅ |
| `investmentId` | `fixed_income_notes.external_id` | ❌ |
| `brandName` + `companyCnpj` (onde está custodiado: "Nubank") | `fixed_income_notes.institution` / `institution_cnpj` | ❌ |
| `issuerInstitutionCnpjNumber` (quem emitiu: Nu Financeira) | `issuer` (texto) + `issuer_cnpj` | ⚠️ só texto |
| `isinCode` | `fixed_income_notes.isin` | ❌ |
| `dueDate` | `maturity_date` | ✅ |
| `remuneration.indexer` + `postFixedIndexerPercentage` / `preFixedRate` | `rate_type` + `indexer` + `indexer_rate` | ✅ (enum incompleto, ver 4.3) |
| `gracePeriodDate` (carência) / liquidez diária | `grace_period_date`, `liquidity` | ❌ |
| `purchaseDate`, `issueDate` | ver 4.4 | ⚠️ |
| `balances.grossAmount`, `netAmount`, `incomeTax`, `financialTransactionTax`, `referenceDateTime` | snapshot de saldo | ❌ |
| transações `APLICACAO` / `RESGATE` / `VENCIMENTO` | `transactions` BUY / SELL (`is_full_settlement` no vencimento) | ✅ |
| `transactionId` | `transactions.external_id` | ❌ |
| `incomeTax`, `financialTransactionTax` do resgate | IR/IOF da venda | ❌ |
| — (não existe na API) | apelido "Caixinha Viagem" | ❌ |

---

## 4. Passo 2 — Campos que a nossa base não tem

Checado contra `V1__schema.sql` e V2–V22. Numerados por prioridade.

### 4.1 Obrigatórios para importar sem duplicar

| # | Onde | Campo | Por quê |
|---|---|---|---|
| 1 | **tabela nova** `integration_connections` | `id`, `user_id`, `provider` (`PLUGGY`), `item_id`, `institution_name`, `status`, `consent_expires_at`, `last_sync_at`, `last_error`, timestamps | Hoje não há onde guardar a conexão. Um registro por banco conectado |
| 2 | `fixed_income_notes` | `source` (`MANUAL` / `OPEN_FINANCE`, default `MANUAL`) | A "coluna ORIGEM" do anexo. Nota importada não deve ser editável como manual |
| 3 | `fixed_income_notes` | `connection_id` (FK), `external_id` + único `(connection_id, external_id)` | Idempotência: reimportar atualiza, não duplica |
| 4 | `transactions` | `origin` (`MANUAL`/`AUTO`, igual a `dividends.origin`) e `external_id` | Idem, no nível da movimentação; e distinguir a **compra de saldo inicial** (2.3) |
| 5 | `fixed_income_notes` | `label` (apelido, VARCHAR 100) | A API não traz "Caixinha Viagem" (2.2-b). Editável pelo usuário, preservado na sincronização |

### 4.2 Necessários para o valor ficar certo

| # | Onde | Campo | Por quê |
|---|---|---|---|
| 6 | **tabela nova** `fixed_income_balances` | `note_id`, `reference_date`, `gross_amount`, `net_amount`, `income_tax`, `iof`, único `(note_id, reference_date)` | Histórico do saldo — o `asset_price_history` da renda fixa. `appreciation` passa a ser derivada do último saldo |
| 7 | `fixed_income_notes` | `institution`, `institution_cnpj` | Onde o título está ≠ quem emitiu (CDB do Banco X comprado no Inter). Hoje só há `issuer` |
| 8 | `fixed_income_notes` | `issuer_cnpj` (VARCHAR 14) | Casar emissor por CNPJ, não por texto livre (ajuda a PENDENCIAS 4.3) |

### 4.3 Ajustes em colunas que já existem

| # | Onde | Ajuste |
|---|---|---|
| 9 | `CHECK` de `indexer` (em `asset_terms` e `fixed_income_notes`) | Hoje: `CDI, SELIC, IPCA, IGPM, INPC, TR`. A API manda também `DI`, `IGP_DI`, `BCP`, `TLC`, `OUTROS`, `PRE_FIXADO`. Decidir: mapear `DI→CDI`, `IGP_M→IGPM`, `PRE_FIXADO→rate_type PREFIXADO`, e acrescentar `IGPDI` e `OUTROS` |
| 10 | `transactions` | IR e IOF do resgate (`income_tax`, `iof`, anuláveis) — hoje só há `brokerage_fee`. Sem eles, resgate líquido e bruto se confundem |

### 4.4 Armadilha no índice existente

`uk_fixed_income_notes_purchase (user_id, asset_id, purchase_date) WHERE purchase_date IS NOT NULL`
foi feito para o Tesouro. Se o importador gravar o `purchaseDate` da API em `purchase_date`,
**dois RDBs do Nu aplicados no mesmo dia violam o índice**. Manter a convenção atual: `purchase_date`
**nulo** na Renda Fixa; a data de aplicação vive na transação de compra.

### 4.5 Opcionais (só se o PrimeVest for calcular a curva por conta própria)

`calculation` (dias úteis/corridos), `rate_periodicity`, `rate_type` linear/exponencial. Com o saldo
vindo pronto da API, **não são necessários** agora.

### 4.6 O que NÃO precisa

Novo `asset_type`, nova categoria de "Reserva", liquidez em `assets`, CNPJ/ISIN em `assets`
(o ISIN é da aplicação, não do produto genérico "RDB").

---

## 5. Passo 3 — Teste com as Caixinhas do Nu

Sim, dá para testar com as suas Caixinhas — é o melhor teste possível, porque responde a pergunta
aberta do 2.2-b. É um **spike sem código de produção**: só chamadas à API e anotação do que voltou.

### 5.1 Preparação (você)
1. Criar conta no `dashboard.pluggy.ai` (inicia o trial de 15 dias) → criar aplicação → anotar
   `client_id` e `client_secret`; habilitar o conector **MeuPluggy**.
2. Criar conta no `meu.pluggy.ai` → conectar o **Nubank** por Open Finance, **marcando investimentos**
   no consentimento (a autorização é concluída no app do Nu).
3. Anotar no app do Nu, no mesmo dia: nome, saldo e tipo (normal/Turbo) de cada Caixinha.

### 5.2 Spike (assistente, com sua ordem)
1. `POST /auth` → apiKey.
2. Conectar o item do MeuPluggy e obter o `itemId`.
3. `GET /investments?itemId=…` e, para cada um, `GET /investments/{id}/transactions`.
4. Gravar o JSON bruto **fora do repositório** (`E:\carga\data\openfinance\`), como já se faz com
   os dados reais da B3.
5. Planilha de conferência em `docs/openfinance/` **sem dados pessoais** (só estrutura dos campos).

### 5.3 Perguntas que o teste precisa responder

| # | Pergunta | Muda o quê |
|---|---|---|
| T1 | Quantos investimentos vêm vs quantas Caixinhas existem? (1:1, N:1 ou tudo consolidado?) | Agrupamento por apelido (4.1-5) |
| T2 | Vem algum texto que identifique a Caixinha (`name`, `number`, metadata)? | Se vier, o apelido é preenchido automaticamente |
| T3 | Soma dos saldos da API = soma do app? Bruto ou líquido? | Qual número entra no patrimônio |
| T4 | Remuneração vem preenchida (100% CDI / 120% CDI no Turbo)? | Se o `indexer_rate` pode ser confiado |
| T5 | Quantas movimentações voltam e de quando? | Confirma a janela de 12 meses e o saldo inicial |
| T6 | O "Nu Reserva Imediata" (se você tiver) aparece como fundo? | Escopo de fundos |
| T7 | `investmentId` é o mesmo em duas consultas em dias diferentes? | Idempotência (4.1-3) |

**Critério de continuar:** T3 bate com o app (diferença só de D-1) e T7 é estável. Se T1 der
"tudo consolidado num registro só", a integração ainda serve para o **patrimônio**, mas não para
acompanhar Caixinha por Caixinha — decisão sua antes de seguir.

---

## 6. Fases de implementação (depois do teste e da sua aprovação)

| Fase | Entrega |
|---|---|
| F0 | Spike da seção 5 + relatório das respostas T1–T7 |
| F1 | Migration V23: `integration_connections`, `fixed_income_balances`, colunas da seção 4, ajuste de `CHECK` |
| F2 | `integration/pluggy/` (client, DTOs, exception) no molde de `integration/brapi/` |
| F3 | `OpenFinanceImportService`: upsert de notas, transações, saldo inicial, appreciation |
| F4 | Scheduler diário + endpoint "Sincronizar agora" |
| F5 | Front: tela de conexões, apelido editável, selo de origem na carteira, `is_reservation` |
| F6 | Atualizar `Claude.md` e `docs/PENDENCIAS.md`; subir o compose e conferir |

---

## 7. Decisões em aberto (suas)

1. **Bruto ou líquido no patrimônio?** Sugestão: bruto no valor (consistente com o resto da carteira),
   líquido e IR estimado exibidos como informação.
2. **Granularidade** (depende de T1): uma posição por RDB ou agrupar RDBs sob um apelido?
3. **Sincronização:** diária automática, botão manual, ou os dois (sugestão: os dois).
4. **Nota importada é editável?** Sugestão: só `label` e `is_reservation`; o resto vem da fonte.
5. **Conflito com lançamento manual já existente** do mesmo título: a importação substitui, ignora
   ou pede para vincular?

---

## 8. Riscos

- **Dependência de um terceiro gratuito.** Se o Meu Pluggy mudar as condições, a importação para;
  o fallback manual continua funcionando. Mitigação: nada no domínio conhece a Pluggy — só o client.
- **Consentimento expira/é revogado** → `integration_connections.status` e aviso na tela.
- **Cobertura por banco varia**; o que o Nu entrega não garante o que Inter/PicPay entregam.
- **Limite de 5 conexões** (Nubank, Inter, PicPay cabem com folga).
- Dado **sensível**: credenciais só em `.env`; JSON bruto fora do repositório.

---

## Fontes

- [Open Finance Brasil — Modelo de participação](https://openfinancebrasil.org.br/modelo-de-participacao/)
- [Open Finance Brasil — especificações OpenAPI (bank-fixed-incomes v1.1.0)](https://github.com/OpenBanking-Brasil/openapi/tree/main/swagger-apis/bank-fixed-incomes)
- [Open Finance Brasil — API Bank Fixed Incomes](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/360743060)
- [Meu Pluggy](https://www.pluggy.ai/meu-pluggy) · [repositório meu-pluggy](https://github.com/pluggyai/meu-pluggy) · [Planos e preços Pluggy](https://www.pluggy.ai/precos)
- [Pluggy — Authentication](https://docs.pluggy.ai/docs/authentication) · [Pluggy — Investments](https://docs.pluggy.ai/docs/investments)
- [Actual Budget — Pluggy.ai setup](https://actualbudget.org/docs/advanced/bank-sync/pluggyai/)
- [Nubank — Como declarar as Caixinhas (RDB / Nu Financeira)](https://nubank.com.br/nu/imposto-de-renda/como-declarar-caixinhas-nubank)
- [Nubank — Caixinhas](https://nubank.com.br/nu/caixinhas) · [Nubank — Open Finance](https://nubank.com.br/nu/open-finance-nubank)
