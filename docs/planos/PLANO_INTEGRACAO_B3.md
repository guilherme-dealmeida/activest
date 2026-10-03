# PrimeVest — Integração com a B3 (Área do Investidor): módulo `primevest-integration` e piloto com dados simulados

> Fase: **execução — F1 a F8 concluídas em 03/10/2026** (planejado e executado no mesmo dia; andamento na seção 10) ·
> branch `TRACK_01-integracao-b3`
> Escopo: trazer da B3 os dados do investidor — ativos (posição), negociações e proventos. Piloto: Guilherme
> (`guilhermeunatec@gmail.com`); o destino, como na Binance, é qualquer usuário conectar a própria conta.
> Entrada: anexo 1 (`INTEGRACAO B3.docx`, prévia feita em outra IA) e os extratos exportados da Área do Investidor
> em `carga/mocks/` (dentro do projeto, **fora do git**).

Objetivo: separar a integração da Binance num módulo próprio, decidir se dá para conectar só a conta do Guilherme e —
como não dá — desenhar a integração contra o contrato oficial da B3, alimentada por um simulador que serve os extratos
reais dele no formato das APIs. Sem inflar o banco, sem duplicar o que o PrimeVest já tem (seção 2).

---

## Termos usados

| Termo | O que é |
|---|---|
| Área do Investidor | site da B3 (`investidor.b3.com.br`) onde a pessoa física vê a posição consolidada de todas as corretoras. Substituiu o CEI em 26/11/2021 |
| APIs da Área do Investidor (B3i) | APIs da B3 que entregam a uma empresa contratante os dados dos investidores que a autorizaram |
| Licenciado | a empresa que contrata as APIs (aqui, seria o PrimeVest) |
| `documentNumber` | CPF (ou CNPJ) do investidor, só dígitos. É o que identifica o investidor em toda chamada |
| Consentimento / opt-in | autorização que o investidor dá, dentro da Área do Investidor, para o licenciado ler os dados dele. Revogável em "Minha Conta → Segurança → Aplicativos e Sites" |
| OAuth 2.0 `client_credentials` | o licenciado troca `client_id` + `client_secret` por um token de acesso (validade de 3.599 s). Não há usuário humano nesse passo: o token é **da empresa**, não do investidor |
| mTLS | TLS mútuo: além do servidor, o cliente apresenta um certificado na conexão. A B3 exige o certificado do Pacote de Acesso nas chamadas |
| Pacote de Acesso | `.zip` que a B3 envia ao licenciado: certificado (`.cer`, `.key`, `.p12` + senha), `client_id`/`client_secret` e o link de opt-in. Um por ambiente |
| Certificação × produção | ambiente de testes da B3 (`apib3i-cert.b3.com.br:2443`) e o real (`investidor.b3.com.br:2443`) |
| D-1 | o dado vai até o fechamento do dia útil anterior; a B3 libera a partir das 8h |
| Liquidação D+2 | a compra feita na bolsa é paga, e as cotas entram na custódia, 2 dias úteis depois do negócio |
| Mercado fracionário | negociação de menos de 100 ações; o ticker ganha o sufixo `F` (`PETR4F`) |
| Simulador (mock) | servidor nosso que responde nas mesmas rotas e no mesmo formato da B3, com dados que nós controlamos |

---

## 1. Veredito em uma frase

**Pela API oficial não dá para conectar só a sua conta: a B3 libera as APIs da Área do Investidor apenas para
pessoa jurídica com contrato.** O ponto 3.2 do escopo cai e o plano segue o ponto 4: a integração é escrita contra o
contrato oficial (Swagger da B3) e, até existir contrato, conversa com um **simulador** que lê os seus extratos
exportados e responde no formato documentado. Trocar o simulador pela B3 de verdade passa a ser configuração
(`B3I_*` no `.env`), não código.

Antes disso, a integração da Binance sai da `primevest-app-api` para o módulo `primevest-integration`, e a interface
genérica (`SourceConnector`, `ImportedMovement`, `ImportPipeline`) ganha o que falta para uma fonte que **não** é cripto
e **não** usa chave de API do usuário.

---

## 2. Princípios (orientação do Guilherme, 03/10/2026)

| # | Princípio | Consequência neste plano |
|---|---|---|
| P1 | **O desenho é o nosso.** O anexo 1 foi uma prévia | O anexo entra só como referência (seção 4); nenhuma estrutura dele é adotada |
| P2 | **Não inflar o banco.** | Nenhuma tabela nova e nenhuma coluna nova. A migration da B3 só acrescenta valores aceitos em dois `CHECK` (provider `B3`, status `PENDING_AUTHORIZATION`). Conferências e divergências usam o que a V25–V27 já guardam (`integration_run_items.reason`, `integration_runs.balances`) |
| P3 | **Não duplicar o que já existe.** | Eventos do papel, proventos, preços e cadastro de ativos vêm do catálogo. O que a B3 traz disso serve para **conferência**, nunca é gravado de novo |
| P4 | **Sem manipulação desnecessária; aproveitar o que temos.** | Reaproveitar código e tabelas como estão (inclusive nomes). O simulador lê as planilhas exportadas direto, sem conversor nem arquivo intermediário. O teste do piloto compara com a carteira que já existe (carga de 24/09/2026) |

---

## 3. Passo 1 — módulo `primevest-integration`

### 3.1 Onde a integração está hoje

| Peça | Módulo hoje | O que é |
|---|---|---|
| Entidades e repositórios (`IntegrationConnection`, `IntegrationCredential`, `IntegrationRun`, `IntegrationRunItem`, `IntegrationCursor`, `IntegrationCryptoExclusion`, enums) | `primevest-core` (`domain/integration/`) | modelo JPA — V25 a V27 |
| Contrato da fonte (`SourceConnector`, `ImportedMovement`, `MovementKind`, `MovementSide`, `FetchResult`, `CredentialCheck`, `MovementJson`) | `primevest-app-api` (`domain/integration/source/`) | o que todo conector entrega |
| Credenciais (`CredentialCipher`, `CredentialVault`, `ApiKeyCredentials`) | app-api (`domain/integration/credential/`) | AES-256-GCM |
| Pipeline (`ImportPipeline`, `CryptoImportRules`, `ImportPreview`, `PreviewItem`, `PreviewOutcome`, `ProposedTransaction`) | app-api (`domain/integration/pipeline/`) | regras R1–R8 e pré-visualização |
| Binance (`BinanceClient`, `BinanceConnector`, `BinanceException`) | app-api (`integration/binance/`) | o único conector |
| Orquestração e HTTP (`IntegrationService`, `IntegrationController`, `IntegrationWorker`, `IntegrationExceptionHandler`, `dto/*`, `scheduler/IntegrationSyncScheduler`) | app-api | fila, confirmação, endpoints das telas |

### 3.2 O que vai para o módulo e o que fica

`primevest-integration` é uma **biblioteca, sem `main`**, como o `primevest-core`. Depende só do core (e de
`spring-webflux`, pelo `WebClient`). **Nunca** depende da app-api nem da adm-api. Quem depende dele é a app-api.

| Vai para `primevest-integration` | Fica onde está | Por quê |
|---|---|---|
| `source/*`, `credential/*`, `pipeline/*`, `integration/binance/*` e, novo, `integration/b3investidor/*` | — | é o que "fala com a fonte e normaliza" |
| — | entidades, repositórios e migrations no **core** | regra 1 do `docs/planos/PLANO_ADM_API.md`: toda entidade JPA fica no core |
| — | `IntegrationService`, `IntegrationController`, `IntegrationWorker`, DTOs e `IntegrationSyncScheduler` na **app-api** | a confirmação grava pelo `TransactionService`, que é da app-api. Levar o serviço para o módulo criaria ciclo (módulo → app-api → módulo) e abriria exceção à regra "só a app-api grava dado de usuário" (D1) |

Como no `PLANO_ADM_API.md`, **os pacotes Java não mudam de nome**: a separação é por arquivo movido (P4). O conector
novo nasce em `com.primevest.integration.b3investidor`, e não em `...integration.b3`, porque
`com.primevest.adm.integration.b3` já existe na adm-api e é outra coisa: dados **públicos** de catálogo (site da B3,
COTAHIST, FNET) (D2).

Testes: os unitários vão com as classes (`BinanceClientTest`, `BinanceConnectorTest`, `CryptoImportRulesTest`,
`ImportPipelineTest`, `CredentialCipherTest`). Os funcionais (`IntegracoesIT`, `IntegracoesConfirmacaoIT`,
`IntegracoesFilaIT`) e o suporte deles (`support/BinanceFake`, `support/ContaSintetica`) ficam na app-api, porque
sobem a aplicação inteira. Casos de borda (ex.: `IntegrationRepositoryTest`) o compilador decide.

Arquivos de infraestrutura que mudam junto:

| Arquivo | Mudança |
|---|---|
| `pom.xml` (raiz) | `<module>primevest-integration</module>` e a entrada em `dependencyManagement` |
| `primevest-app-api/pom.xml` | dependência do módulo |
| `Dockerfile` | `COPY primevest-integration/pom.xml` (camada do `dependency:go-offline`) e `COPY primevest-integration/src` |
| `docker-compose.yml` | volumes `./primevest-integration/{pom.xml,src}` nos serviços com hot reload |
| `dev-watch.sh` | `WATCH` passa a observar `primevest-integration/src` |
| `Claude.md`, `README.md` | tabela de módulos ("Dois serviços") e estrutura do projeto |

Critério da etapa: **nenhuma mudança funcional** — a mesma bateria verde antes e depois, e a tela de Integrações
conferida com a conexão Binance real (só leitura).

### 3.3 O que a interface genérica não aguenta hoje

A F7 do `PLANO_CRIPTO_BINANCE.md` ("segunda corretora") existia para provar que a interface aguenta uma fonte que não
foi desenhada junto. A B3 é esse teste, e o código de hoje não passa nestes pontos:

| # | Ponto de acoplamento (conferido no código em 03/10/2026) | Mudança |
|---|---|---|
| G1 | `SourceConnector.check/fetch` recebem `ApiKeyCredentials` (chave + segredo) | tipo selado `SourceCredentials`: `ApiKeyCredentials` (Binance) ou `InvestorDocument` (B3: o CPF) |
| G2 | `CredentialVault` grava chave + segredo; `fingerprint` = 4 últimos caracteres da chave | grava o JSON do tipo de credencial na mesma `integration_credentials`; na B3 o `fingerprint` mostra só os 2 últimos dígitos do CPF |
| G3 | `CryptoImportRules.map` é chamado direto pelo `ImportPipeline` e em 3 pontos do `IntegrationService` (confirmar, restaurar exclusão, montar item) | interface `ImportRules`, escolhida pelo provider; `CryptoImportRules` vira uma implementação e `B3EquityImportRules` outra |
| G4 | `MovementKind` só tem tipos de cripto | tipos da B3 (6.6) |
| G5 | `ImportedMovement` não tem mercado, corretora nem data de negócio separada do instante | campos opcionais `market`, `participant`, `tradeDate` no record (código, não banco). O resto já serve (`quoteAsset` = `BRL`; taxa nula) |
| G6 | Divergência (E3) só olha ativo `CRIPTO` e descarta `BRL/USD/EUR` | compara os ativos que o conector informa em `FetchResult.balances` — na B3, a Posição. Continua gravada em `integration_runs.balances` (V27) |
| G7 | `IntegrationProvider` e `ck_integration_connections_provider` (V25) só aceitam `BINANCE` | `B3` no enum e no `CHECK` (V40) |
| G8 | `ConnectionStatus` não tem "aguardando autorização" | `PENDING_AUTHORIZATION` no enum e no `CHECK` (V40): CPF informado, investidor ainda não autorizou na B3 |
| G9 | `ConnectionCreateDTO` tem `apiKey`/`apiSecret` | corpo por fonte (Binance: chave e segredo; B3: CPF) |
| G10 | Exclusões em `integration_crypto_exclusions` | a tabela não depende de cripto (conexão, `external_id`, ativo, snapshot): **serve à B3 como está**, sem tabela nova nem renomear (P2, P4; D11) |
| G11 | Credencial é sempre do usuário | a B3 tem também **credencial da plataforma** (`client_id`, segredo, certificado), única para o PrimeVest, no `.env` — nunca no banco |
| G12 | Sincronização manual sem limite de tempo (pedir com uma na fila devolve a mesma); rotina diária às 06:30 (`sync-cron`) para todas as fontes | horário e limite por fonte: a B3 pede **uma consulta por CPF por dia** e só libera o D-1 a partir das 8h |

**V40 em uma linha:** reescreve `ck_integration_connections_provider` e `ck_integration_connections_status`. Nada mais
no banco muda (P2).

---

## 4. Passo 2 — anexo 1 (prévia)

O anexo foi uma prévia, e o plano segue o nosso desenho (P1).

**Fica do anexo** a direção: API oficial com consentimento do investidor, nada de robô no login da B3, segredos fora do
front e a importação por arquivo como alternativa.

**Não adotamos:**

| Proposta do anexo | Por quê |
|---|---|
| Tabelas `positions`, `trades`, `movements`, `corporate_events`, `institutions`, `accounts`, `integration_connections` | O PrimeVest já tem o equivalente: a posição sai das transações (`PositionCalculator`, V28); eventos e proventos vêm do catálogo; as tabelas de integração existem desde a V25 (P2, P3) |
| Gravar eventos e proventos da B3 | Contaria duas vezes: o `PositionCalculator` já aplica desdobramento/grupamento/bonificação e o `DividendRecalculator` já gera os proventos (P3) |
| "Fase 1 — piloto com a sua própria conta B3" | Inviável para pessoa física (5.2) |
| Preço médio e custo a partir da Posição | A Posição traz preço de mercado (`closingPrice`, `updateValue`), não custo |
| Estrutura em Python (`backend/integrations/b3/auth.py`…) | O PrimeVest é Java 21 + Spring Boot multi-módulo, com o padrão de conector da Binance |

**Fatos da B3 levantados aqui, que moldam o desenho:**

- O token é **da empresa** (`client_credentials`, emitido pelo Microsoft Entra ID) e a consulta é por CPF: o PrimeVest
  precisa saber o CPF do usuário e garantir que ele é o dono (6.12, A1).
- Os contratos reais são por **período** (`referenceStartDate`/`referenceEndDate`), 20 itens por página, com `Links`.
- O histórico da Área do Investidor começa em **01/11/2019**.
- **Não há id de negócio** nas respostas: o `external_id` é montado pelo conector (B5).
- A Negociação traz a **data do negócio**; a "Transferência - Liquidação" da Movimentação vira conferência (B7).
- Contratar exige CNPJ, *self-assessment* e Security Scorecard; piso de **R$ 6.000/ano**; uma consulta por CPF por dia.
- Os contratos atuais de Negociação, Eventos Provisionados, Guia e Autorização Fintech não são públicos (6.3).

---

## 5. Passo 3 — usuário individual (`guilhermeunatec@gmail.com`)

### 5.1 Ponto 3.1 — B2B e Open Finance (registro para o futuro)

| Caminho | O que exige | Observação |
|---|---|---|
| Contratar as APIs da B3 (B2B) | CNPJ, contrato, *self-assessment*, Security Scorecard; piso de R$ 6.000/ano | Contratação: `contratacao@b3.com.br`, (11) 2565-5080. Suporte técnico: `sat@b3.com.br`. Com o contrato, este plano troca o simulador pelo endereço da B3 (6.13) |
| Open Finance (API de Renda Variável) | instituição autorizada pelo Banco Central, ou um agregador (Pluggy, Belvo) | O dado vem de cada **corretora**, não da B3 consolidada, e o histórico é de 12 meses. Para uso pessoal, o Meu Pluggy do `PLANO_OPEN_FINANCE.md` é a referência — sem compromisso (D10 do `PLANO_CRIPTO_BINANCE.md`) |

### 5.2 Ponto 3.2 — dá para conectar só a sua área?

| Caminho | Pessoa física | Por quê |
|---|---|---|
| API oficial, produção | ❌ | só pessoa jurídica com contrato |
| Ambiente de certificação | ❌ (provável) | o Pacote de Acesso de certificação é pedido com o documento da empresa (CNPJ nos exemplos públicos) |
| Sandbox do portal developers.b3.com.br | ⚠️ a confirmar | devolve **dados fictícios**, não os seus. Se o cadastro como pessoa física for aceito, serve para confirmar os contratos que não são públicos (opcional) |
| Robô no login da Área do Investidor | ❌ não recomendado | 2FA por e-mail, termos de uso da B3, senha de terceiro guardada |
| Open Finance / Meu Pluggy | ⚠️ futuro (5.1) | não é a B3; dado por corretora, 12 meses |
| Exportar as planilhas da Área do Investidor | ✅ | feito em 03/10/2026 (`carga/mocks/`, 6.4) — é a base do simulador |

**Conclusão: o 3.2 não se cumpre, e o plano segue o ponto 4.**

---

## 6. Passo 4 — estrutura da integração com dados simulados

### 6.1 Arquitetura

```
 carga/mocks/*.xlsx   ← extratos exportados da Área do Investidor (dado real; fora do git)
        │  lidos direto, sem conversão nem arquivo intermediário (P4)
        ▼
 b3-investidor-mock   (contêiner Python + openpyxl, profile b3mock; código em carga/scripts/b3investidor/simulador/)
   POST /{tenant}/oauth2/v2.0/token · GET /api/acesso/healthcheck
   GET /api/position/v3/... · /api/movement/v2/... · negociação · eventos · autorização · página de opt-in
        ▲
        │  B3I_BASE_URL / B3I_TOKEN_URL   (trocar pela B3 real = só .env)
 primevest-integration
   integration/b3investidor/B3InvestidorClient     ← token com cache, mTLS, paginação, 401/422/429
   integration/b3investidor/B3InvestidorConnector  ← SourceConnector: check = autorização;
        │                                             fetch = negociação + movimentação + posição; cursor por data
        ▼  ImportedMovement (em BRL) + balances (a Posição)
   pipeline/ImportPipeline + B3EquityImportRules (B1–B10)
        ▼
 primevest-app-api: IntegrationService → pré-visualização → confirmação → TransactionService
   (o mesmo caminho da Binance: fila, worker, duplicatas, exclusões, edição, divergência)
```

Os testes automatizados (Maven) não usam o simulador nem dado real: usam uma conta inventada no formato da B3,
`ContaSinteticaB3`, como a `ContaSintetica` da Binance. O simulador serve ao teste ponta a ponta com os seus extratos e
ao desenvolvimento sem contrato.

### 6.2 Cada API e o que ela vira no PrimeVest

| API B3 | Versão | Uso no PrimeVest | Grava? |
|---|---|---|---|
| Autorização Fintech | 1.0.0-rc7 | `check`: o CPF autorizou? `PENDING_AUTHORIZATION` → `ACTIVE`; revogado → `REVOKED` | só o status da conexão |
| Negociação de Ativos | 2.0.1-rc3 | compras e vendas de renda variável → `transactions` BUY/SELL | ✅ `transactions` (`origin = AUTO`) |
| Movimentação | 2.0.2-rc8 | conferência da liquidação, dos eventos e dos proventos (6.6, 6.8); Tesouro e renda fixa na F7 | só na F7 |
| Posição | 3.3.1-rc2 | divergência por ativo (E3) | não |
| Eventos Provisionados | 2.0.0-rc1 | conferência dos proventos a receber | não |
| Guia | 1.0.0-rc3 | rotina com muitos CPFs: sincroniza só quem mudou | não |
| Pacote de Acesso, Cadastro, Oferta Pública, FintechSistema, STVM | — | fora do escopo | — |

### 6.3 Contratos — confirmado × inferido

| Rota | Fonte | Situação |
|---|---|---|
| `POST https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token` com `grant_type=client_credentials`, `client_id`, `client_secret`, `scope=<id>/.default` → `{token_type, expires_in: 3599, access_token}` | artigo oficial "Autenticação Client Credentials B3i" | ✅ |
| `GET /api/acesso/healthcheck` | portal da B3 | ✅ |
| `GET /api/position/v3/{equities\|fixed-income\|treasury-bonds\|derivatives\|securities-lending}/investors/{documentNumber}?referenceStartDate&referenceEndDate&page` | Swagger 3.3.1-rc2 (cópia publicada por terceiro; conferida em 03/10/2026) | ✅ |
| `GET /api/movement/v2/{mesmos produtos}/investors/{documentNumber}?referenceStartDate&referenceEndDate&page` | Swagger 2.0.2-rc8 (idem) | ✅ |
| Negociação v2 | só o rascunho v1 (2020): `GET /api/b3i/asset-trading/v1/asset-trading/{documentNumber}/{inicio}/{fim}?pageNumber` | ⚠️ inferido: `/api/asset-trading/v2/investors/{documentNumber}?referenceStartDate&referenceEndDate&page`, com os campos do v1 |
| Eventos Provisionados v2 | só o rascunho v1 | ⚠️ inferido, campos do v1 |
| Autorização Fintech, Guia | só a descrição | ⚠️ inferido |

Para o inferido não virar retrabalho, **as rotas ficam em configuração** (`b3i.paths.*` no `application.yml`), e o
client lê o JSON tolerando campo novo. Confirmar um contrato (sandbox ou contrato B2B) muda configuração e o
mapeamento de campos, não a arquitetura.

Regras gerais do contrato: 20 itens por página; objeto `Links` (`self`, `first`, `prev`, `next`, `last` — com L
maiúsculo no Swagger, aceitar os dois); datas `AAAA-MM-DD`; erros de negócio 422.01 a 422.07 (CPF inválido,
participante não autorizado, CPF não localizado, data inválida, data não anterior a hoje, fim antes do início, início
antes de 2018); e 401, 403, 404, 429, 500, 503.

### 6.4 Os seus extratos (exportados em 03/10/2026)

O simulador lê as três planilhas **como vieram** e responde com os nomes de campo da API. Campo que a planilha não tem
(ex.: `isin`, `participantDocumentNumber`, `grossAmount`) vai **nulo** — nada é inventado nem calculado (P4), e o
conector não depende deles. O único valor de configuração é o CPF: um **CPF fictício válido** (`B3I_MOCK_DOCUMENT`),
porque as planilhas não trazem o CPF e o real não precisa aparecer em lugar nenhum.

**Negociação** — `carga/mocks/negociacao-2026-10-03-15-58-24.xlsx`, aba "Negociação": 79 linhas, de 28/11/2024 a
20/08/2026; só compras; 52 no mercado à vista e 27 no fracionário; 25 códigos (9 com `F`).

| Coluna | Campo da API (Negociação v1) |
|---|---|
| Data do Negócio | `periodList[].referenceDate` |
| Tipo de Movimentação | `side` |
| Mercado | `marketName` |
| Prazo/Vencimento (`-` → nulo) | `expirationDate` |
| Instituição | `participantName` |
| Código de Negociação | `tickerSymbol` |
| Quantidade · Preço · Valor | `tradeQuantity` · `priceValue` · `totalTradeAmount` |

**Movimentação** — `carga/mocks/movimentacao-2026-10-03-15-55-10.xlsx`: 343 linhas, de 14/06/2024 a 02/10/2026.

| Coluna | Campo da API (Movimentação v2) |
|---|---|
| Entrada/Saída | `operationType` |
| Data | `referenceDate` |
| Movimentação | `movementType` |
| Produto (`TICKER - NOME`) | `tickerSymbol` + `corporationName` |
| Instituição | `participantName` |
| Quantidade · Preço unitário · Valor da Operação (`-` → nulo) | `equitiesQuantity` · `unitPrice` · `operationValue` |

Linhas de Tesouro ("Tesouro …") e de CDB ("CDB - …") saem nas rotas `treasury-bonds` e `fixed-income`; as demais, em
`equities`.

| `movementType` | Linhas | Tratamento |
|---|---|---|
| Rendimento · Juros Sobre Capital Próprio · Dividendo | 147 · 35 · 26 | conferência de proventos (6.8) |
| Transferência - Liquidação | 71 | conferência da negociação (B7) |
| Atualização | 12 (crédito, sem preço) | significado a confirmar na F4; até lá, tratado como evento do papel: conferência (B8) |
| Direito de Subscrição · Direitos de Subscrição - Não Exercido | 9 · 9 | não vira transação (B9) |
| Cessão de Direitos · Cessão de Direitos - Solicitada | 7 · 7 | não vira transação (B9); o saldo zera em todos os 4 ativos |
| Bonificação em Ativos | 1 | o catálogo já aplica: conferência (B8) |
| Compra · Venda (Tesouro) | 10 · 1 | F7 |
| COMPRA / VENDA · VENCIMENTO · RESGATE ANTECIPADO (CDB) | 4 · 3 · 1 | F7 |

**Eventos** — `carga/mocks/eventos-2026-10-03-15-59-04.xlsx`, aba "Proventos a Receber": 23 linhas (+ 1 de total, que o
simulador ignora), pagamentos previstos de 07/10/2026 a 30/12/2027.

| Coluna | Campo da API (Eventos Provisionados v1) |
|---|---|
| Produto | `tickerSymbol` + `corporationName` |
| Tipo (ON, PN, Fundo) | `specificationCode` |
| Tipo de Evento | `corporateActionTypeDescription` |
| Previsão de pagamento | `PaymentDate` |
| Instituição | `participantName` |
| Conta | — (não existe na API; ignorada) |
| Quantidade · Preço unitário · Valor líquido | `eventQuantity` · `eventValue` · `netValue` |

**Posição: não exportada.** Sem ela o simulador devolve a Posição vazia e a divergência (6.7) fica desligada no piloto;
a conferência principal não depende dela (C1). Se exportada (Extratos → Posição), entra do mesmo jeito e liga a C6.

### 6.5 Negociação → `transactions`

| B3 | PrimeVest | Regra |
|---|---|---|
| `referenceDate` do período | `transaction_date` | B2 — é a data do negócio |
| `side` (compra/venda) | `type` BUY/SELL | — |
| `tickerSymbol` (`PETR4F` → `PETR4`) | ativo do catálogo | B3, B4 |
| Σ `tradeQuantity` do grupo | `shares` | B1 |
| Σ `totalTradeAmount` ÷ Σ `tradeQuantity`, 8 casas | `price_per_share` | B1 |
| — (a API não informa corretagem nem emolumentos) | `brokerage_fee` nulo | D5 |
| `participantName` | só no `source_snapshot` | D13 |
| `marketName` de opções, termo ou futuro | não importada, com motivo | B6 |
| chave do grupo | `external_id` | B5 |

### 6.6 Regras da B3 (B1–B10)

| # | Regra | Por quê |
|---|---|---|
| B1 | **Um grupo = uma transação:** negócios do mesmo dia, ativo (fracionário junto do lote padrão) e lado, somados, com preço médio ponderado | Nos seus extratos, os 79 negócios formam **71 grupos — exatamente as 71 "Transferência - Liquidação"**, nas mesmas 41 datas: a B3 liquida assim. E são as mesmas 71 transações de renda variável da carteira de hoje (carga de 24/09/2026) (P4) |
| B2 | **Data do negócio, sem fuso:** a B3 já entrega a data local | Na Binance (R3) o instante vinha em UTC; aqui não há instante |
| B3 | **Fracionário:** o sufixo `F` sai quando o ticker sem ele existe no catálogo | `PETR4F` e `PETR4` são o mesmo papel |
| B4 | **Ticker fora do catálogo = não importada**, listada com o motivo; nunca cria ativo | Mesma regra do lançamento manual e da R7 |
| B5 | **`external_id` = `b3:negociacao:<AAAA-MM-DD>:<TICKER>:<C\|V>`** | A API não tem id de negócio. Com o dado fechado em D-1, o grupo do dia não muda depois. Sem a corretora na chave: o PrimeVest não modela corretora (D13), e a mesma corretora aparece com dois nomes nos extratos (NU INVEST CORRETORA DE VALORES S.A. e NU INVESTIMENTOS S.A. - CTVM) |
| B6 | **Só mercado à vista e fracionário.** Opção, termo e futuro ficam em "não mapeadas"; `expirationDate` no à vista é ignorado (4 linhas têm) | O PrimeVest não modela derivativo |
| B7 | **"Transferência - Liquidação" não vira transação.** Serve para conferir: grupo negociado em D = liquidação em D+2 | Senão a compra entraria duas vezes |
| B8 | **Desdobramento, grupamento, bonificação, "Atualização" e proventos não viram transação**: o catálogo já aplica. Da B3 vem a conferência (P3) | O `PositionCalculator` e o `DividendRecalculator` já tratam |
| B9 | **Direitos de subscrição e cessão de direitos não viram transação**: aparecem como "não importadas" com o motivo | Direito não é posição no PrimeVest. Subscrição exercida (compra a preço de emissão) não aparece nos seus extratos; quando aparecer, vira regra |
| B10 | **Transferência entre corretoras e empréstimo de ativos:** ignorados | Não mudam a posição do investidor; o PrimeVest não modela custódia por corretora |

`MovementKind` ganha `EQUITY_TRADE` (B1), `SETTLEMENT` (B7), `STOCK_EVENT` e `CASH_EVENT` (B8), `RIGHTS` (B9),
`CUSTODY_TRANSFER` e `LENDING` (B10) e, na F7, os de Tesouro e renda fixa.

### 6.7 Posição → divergência

- Depois de cada sincronização, por ativo: cotas no PrimeVest (`PositionCalculator.sharesOn` na data da Posição) ×
  `equitiesQuantity` da B3. Diferença = aviso na conexão (E3 da Binance, que já existe).
- `FetchResult.balances` passa a levar a Posição e continua gravado em `integration_runs.balances` (G6, P2).
- A Posição é de D-1: a comparação é na **mesma data**, não em "hoje".

### 6.8 Proventos → conferência

Movimentação ("Dividendo", "Juros Sobre Capital Próprio", "Rendimento", "Amortização") e Eventos Provisionados passam
pela mesma lógica do `ExtratoProventosImportService`: `JA_EXISTE`, `SEM_DATA`, `SEM_EVENTO`, `RECUSADO`. Cada um vira
item da execução como **não importado**, com o resultado da conferência no `reason` que `integration_run_items` já
tem — sem coluna nova (P2). Nada é gravado em `dividends` nem no catálogo (P3). Um `SEM_EVENTO` é pendência do catálogo
(adm-api), não do usuário.

### 6.9 Conexão e consentimento

Com a B3 real (depois de um contrato):

1. O usuário abre **Integrações → Conectar B3**, informa o CPF e aceita o consentimento do PrimeVest.
2. A conexão nasce `PENDING_AUTHORIZATION`; o CPF vai cifrado para o `CredentialVault`, na mesma
   `integration_credentials` da Binance (D10).
3. A tela mostra o **link de opt-in** (o `Link_optin.txt` do Pacote de Acesso); o usuário autoriza na B3 com o login dele.
4. O conector consulta a Autorização Fintech: autorizado → `ACTIVE` → primeira sincronização.
5. Revogação pela B3 (Aplicativos e Sites) → a próxima checagem marca `REVOKED`.

No simulador, o link leva a uma página do próprio simulador com os botões **Autorizar** e **Revogar**, para passar
pelos mesmos estados.

### 6.10 O simulador — `carga/scripts/b3investidor/simulador/`

Python + `openpyxl` (o mesmo par do `carga/scripts/levantamento/xlsx_para_json.py`), no contêiner `b3-investidor-mock`,
no profile `b3mock` do `docker-compose.yml`: só sobe quando pedido. Lê as planilhas de `B3I_MOCK_DATA_DIR`
(`./carga/mocks`) na subida; sem a pasta, usa os dados sintéticos versionados em `carga/scripts/b3investidor/sintetico/`.

| Comportamento | Como |
|---|---|
| Token | `POST /{tenant}/oauth2/v2.0/token` confere `client_id`, `client_secret` e `scope` do `.env` e devolve um token de 3.599 s; as rotas da API exigem `Authorization: Bearer` (401 sem ele) |
| Rotas | as de 6.3, inclusive as inferidas, nos mesmos caminhos configurados no PrimeVest |
| Período e paginação | filtra por `referenceStartDate`/`referenceEndDate`, 20 itens por página, `Links.next` até a última |
| Erros | 422.01 a 422.07 com as mensagens da B3; 422.03 para CPF diferente do `B3I_MOCK_DOCUMENT`; 422.02 para CPF que não autorizou |
| Autorização | estado por CPF; página de opt-in com Autorizar e Revogar |
| Falhas sob demanda | variável ou parâmetro para devolver 429, 500 ou 503, e testar a nova tentativa e o `FAILED` |
| mTLS (opcional) | HTTPS exigindo certificado do cliente, com um certificado de teste gerado na hora, para provar o carregamento do `.p12` antes do contrato |

### 6.11 Conferência do piloto — como saber que deu certo

| # | Conferência | Esperado |
|---|---|---|
| C1 | Posição de renda variável, ativo a ativo: pilha `primevest-teste` depois de importar × pilha de desenvolvimento (carteira da carga de 24/09/2026) | mesmas cotas e mesmo custo (diferença de centavos só por arredondamento). É a carteira que já temos servindo de gabarito (P4) |
| C2 | Grupos negociados (B1) × "Transferência - Liquidação" | 71 = 71, nas mesmas 41 datas — **já confere nos extratos exportados** |
| C3 | Proventos: 208 recebidos (Movimentação) e 23 a receber (Eventos) × `dividends` | todos `JA_EXISTE`, ou a lista de `SEM_DATA`/`SEM_EVENTO` explicada |
| C4 | Segunda sincronização | 0 novas (B5) |
| C5 | Revogar no simulador → sincronizar | `REVOKED`, nada importado |
| C6 | Só com a Posição exportada: cotas × Posição da B3 | iguais |

### 6.12 Armadilhas

| # | Armadilha | Tratamento |
|---|---|---|
| A1 | **Dono do CPF.** A B3 só diz "este CPF autorizou o PrimeVest". Se o usuário A informar o CPF de B, e B tiver autorizado, A leria os dados de B | Com um usuário não ocorre. Antes do multiusuário: CPF único entre as conexões ativas, e confirmar com a B3 se o link de opt-in aceita um identificador de retorno. É pergunta para o contato B2B |
| A2 | Sem id de negócio | B5. Uma correção retroativa da B3 aparece como grupo novo + divergência |
| A3 | Nomes que variam: `isin` × `isinCode`, `Links` × `links`, `errors` × `Errors`, `securirtiesLendingQuantity` (erro de digitação do próprio Swagger) | o client aceita as variações |
| A4 | Planilhas: `-` no lugar de número, Quantidade dos Eventos como texto com separador de milhar, linha de total no fim dos Eventos | tratado no simulador, que entrega números à API simulada |
| A5 | O parâmetro de data inicial é obrigatório na API; histórico da B3 desde 01/11/2019; início antes de 2018 = 422.07 | a integração **não tem data inicial própria** (D8): a primeira busca pede desde o início do histórico da fonte (na B3, 01/11/2019) e as seguintes continuam do cursor. Posição comprada antes do histórico da B3 precisa de saldo inicial (D8) |
| A6 | Uma consulta por CPF por dia; D-1 só a partir das 8h | rotina da B3 depois das 08:30 e sincronização manual limitada a uma por dia (G12) |
| A7 | Token de 1 h | cache e renovação 5 a 10 min antes de vencer |
| A8 | Limite por segundo não publicado; 429 | uma requisição por vez, espera e nova tentativa, como no 429 da Binance |
| A9 | Contratos inferidos (6.3) | rotas em configuração; testes de contrato com os sintéticos |
| A10 | A pilha de desenvolvimento já tem as 71 transações de renda variável da carga de 24/09/2026 (`MANUAL`) | ali a importação daria 71 "possíveis duplicatas"; por isso o ponta a ponta roda na pilha `primevest-teste` (D12), e a de desenvolvimento vira o gabarito (C1) |

### 6.13 Configuração (`.env` → `docker-compose.yml` → `b3i.*`)

| Variável | Simulador | B3 real |
|---|---|---|
| `B3I_ENABLED` | `true` | `true` |
| `B3I_BASE_URL` | `http://b3-investidor-mock:8090` | `https://apib3i-cert.b3.com.br:2443` (certificação) ou `https://investidor.b3.com.br:2443` |
| `B3I_TOKEN_URL` | `http://b3-investidor-mock:8090/<tenant>/oauth2/v2.0/token` | `https://login.microsoftonline.com/<tenant>/oauth2/v2.0/token` |
| `B3I_CLIENT_ID`, `B3I_CLIENT_SECRET`, `B3I_SCOPE` | valores de teste | do Pacote de Acesso |
| `B3I_CERT_P12_PATH`, `B3I_CERT_P12_PASSWORD` | vazios (ou o de teste, com mTLS) | do Pacote de Acesso |
| `B3I_OPTIN_URL` | página do simulador | `Link_optin.txt` |
| `B3I_MOCK_DATA_DIR`, `B3I_MOCK_DOCUMENT` | `./carga/mocks` e um CPF fictício válido | — |

Sem `B3I_ENABLED`, a B3 some da lista de fontes e o resto sobe normalmente — o mesmo comportamento de quando falta a
chave-mestra das credenciais. Tudo documentado no `.env.example`.

---

## 7. Etapas

| # | Entrega | Valida |
|---|---|---|
| F0 | Levantamento | ✅ **03/10/2026:** Negociação, Movimentação e Eventos exportados para `carga/mocks/`; layouts conferidos (6.4). Opcionais: exportar a Posição (liga a C6) e tentar o sandbox do portal (confirma 6.3) |
| F1 | Módulo `primevest-integration` (3.2): só mover arquivos | ✅ **03/10/2026:** bateria 602 = 602 antes e depois; pilha de desenvolvimento no ar com a conexão Binance real (10.1) |
| F2 | Generalização G1–G12 + V40 (dois `CHECK`) | ✅ **03/10/2026:** 608 verdes; Binance real sincronizada com o cofre novo (10.2) |
| F3 | Simulador lendo as planilhas + dados sintéticos + `ContaSinteticaB3` (6.10) | ✅ **03/10/2026:** simulador lê os 3 extratos (79 / 343 / 23) (10.3) |
| F4 | `B3InvestidorClient`, `B3InvestidorConnector`, `B3EquityImportRules` (B1–B10) | ✅ **03/10/2026:** 667 verdes; "Atualização" confirmada; achado G13 corrigido (10.4) |
| F5 | Ponta a ponta na pilha `primevest-teste` contra o simulador com os seus extratos: conexão, pré-visualização, confirmação, conferência de proventos | ✅ **03/10/2026:** C1–C5 e C7 conferem; C6 sem a Posição (10.5) |
| F6 | Front: conectar B3 (CPF, link de autorização, estado "aguardando"), selo "B3", divergência, conferência de proventos | ✅ **03/10/2026:** `tsc`, eslint e `vite build`; tela conferida na pilha de teste (10.6) |
| F7 | Tesouro e renda fixa pela Movimentação (Tesouro 10 compras e 1 venda; CDB 8 linhas) e valor atualizado pela Posição | ✅ parcial **03/10/2026:** as 10 compras de Tesouro entram; a venda de Tesouro e os CDBs não (motivo na pré-visualização; PENDENCIAS 16.5 e 16.6); o valor pela Posição espera a Posição (10.7) |
| F8 | `Claude.md`, `docs/PENDENCIAS.md` (a 16 passa a apontar para este plano), `README.md` | ✅ **03/10/2026** (10.8) |

## 8. Quem faz

| Etapa | Claude | Guilherme |
|---|---|---|
| F0 | conferir os layouts (feito) | exportar os extratos (feito); opcionais: Posição e sandbox |
| F1–F4 | implementar e validar (compilar, testes, lint, subir) | aprovar cada etapa |
| F5 | preparar e conferir C1–C6 | acompanhar a conexão na tela, se quiser |
| F6 | implementar e validar | conferir no navegador |
| F7 | implementar | — |
| F8 | atualizar os documentos | — |

## 9. Decisões

Orientação de 03/10/2026: seguir o nosso plano e as recomendações dele, sem inflar o banco, sem duplicar e sem
manipulação desnecessária (seção 2). D8, D12 e D15 respondidas pelo Guilherme no mesmo dia. Qualquer uma pode ser reaberta.

| # | Decisão | Situação |
|---|---|---|
| D1 | `primevest-integration` leva contrato, credencial, pipeline e conectores; a orquestração fica na app-api | ✅ 03/10/2026 |
| D2 | Pacotes mantidos; o conector da B3 em `com.primevest.integration.b3investidor` | ✅ 03/10/2026 |
| D3 | Simulador próprio, em Python, lendo as planilhas direto (sem conversor nem JSON intermediário) | ✅ 03/10/2026 — revisada pela P4 (a versão anterior tinha um conversor) |
| D4 | Negócios agrupados por dia, ativo (fracionário junto) e lado (B1) | ✅ 03/10/2026 — os extratos mostram que é como a B3 liquida, e preserva as 71 transações de hoje |
| D5 | Corretagem e emolumentos: a API não traz → `brokerage_fee` nulo, editável depois (E1) | ✅ 03/10/2026 |
| D6 | Desdobramento, grupamento, bonificação e "Atualização" vindos da B3: só conferência | ✅ 03/10/2026 (P3) |
| D7 | Proventos recebidos e a receber vindos da B3: só conferência | ✅ 03/10/2026 (P3) |
| D8 | A integração **não é limitada a uma data inicial**: busca todo o histórico que a fonte tiver. A data de início dos extratos em `carga/mocks/` (06/2024) foi só o recorte escolhido para os mocks. O limite que existe é da própria B3 (histórico desde 01/11/2019): posição comprada antes disso não tem negociação na API e precisará de compra de saldo inicial — registrado para o multiusuário | ✅ 03/10/2026 — resposta do Guilherme |
| D9 | Tesouro e renda fixa pela B3 na F7, depois da renda variável | ✅ 03/10/2026 |
| D10 | CPF cifrado no `CredentialVault`; simulador com CPF fictício | ✅ 03/10/2026 |
| D11 | Exclusões da B3 na `integration_crypto_exclusions` que já existe, sem tabela nova nem renomear | ✅ 03/10/2026 — revisada pela P2/P4 (a versão anterior propunha tabela por domínio) |
| D12 | Teste de integração ponta a ponta na pilha `primevest-teste`; a de desenvolvimento é o gabarito (C1) | ✅ 03/10/2026 — confirmada pelo Guilherme |
| D13 | Corretora fora do modelo; fica só no `source_snapshot` | ✅ 03/10/2026 (P2) |
| D14 | Dados do simulador em `carga/mocks/`, dentro do projeto e no `.gitignore` | ✅ 03/10/2026 — `.gitignore` atualizado; extratos exportados pelo Guilherme |
| D15 | O comentário da linha 4 da V30 (`V30__seed_issuers.sql`), que cita a pasta de dados antiga de onde o seed foi gerado, **fica como está**: preserva o rastro de origem do seed, e editar migration aplicada mudaria o checksum (o Flyway recusaria a subida) | ✅ 03/10/2026 — resposta do Guilherme |

## 10. Andamento

| Data | Evento |
|---|---|
| 03/10/2026 | Plano criado (Planejamento) |
| 03/10/2026 | Orientação do Guilherme: o anexo 1 é prévia; seguir o nosso plano, sem inflar o banco, sem duplicar, aproveitando o que existe → seção 2 e decisões D1–D14 |
| 03/10/2026 | A pasta de dados externa ao projeto foi descartada pelo Guilherme; as referências a ela saíram do `Claude.md`, `docs/REGRAS_CLAUDE.md` (6.3 reescrita: dados reais em `carga/mocks/`), `docs/PENDENCIAS.md`, `docs/USE_CASES.md` e dos planos `PLANO_CARGA_INICIAL`, `PLANO_CRIPTO_BINANCE`, `PLANO_EMPRESAS_BALANCOS`, `PLANO_IR_PROVENTOS` e `PLANO_OPEN_FINANCE`. Fica só o comentário da V30 (D15) |
| 03/10/2026 | Extratos de Negociação, Movimentação e Eventos exportados para `carga/mocks/`; layouts conferidos (6.4). Nada de código implementado |
| 03/10/2026 | Respostas do Guilherme: D8 (sem data inicial na integração; 06/2024 é só o recorte dos mocks), D12 (confirmada) e D15 (manter o comentário da V30). **Todas as decisões fechadas** |
| 03/10/2026 | **Execução autorizada pelo Guilherme: F1 a F8 em sequência**, sem nova autorização entre etapas; parar só diante de erro crítico ou de algo que exija a atenção dele |
| 03/10/2026 | F1 executada (10.1) |
| 03/10/2026 | F2 executada (10.2) |
| 03/10/2026 | F3 executada (10.3) |
| 03/10/2026 | F4 executada (10.4); achado G13 corrigido |
| 03/10/2026 | F5 executada (10.5): achado na C3 (conferência de proventos antes das posições) corrigido |
| 03/10/2026 | F6 executada (10.6): achado do CORS da pilha de teste corrigido |
| 03/10/2026 | F7 executada em parte (10.7) |
| 03/10/2026 | F8 executada (10.8) — **plano concluído**; bateria final 672 verdes |

### 10.1 F1 executada (03/10/2026)

| Peça | Onde |
|---|---|
| Módulo | `primevest-integration/pom.xml`: biblioteca, depende só do core (+ `spring-boot-starter-webflux`, `lombok`); publica um test-jar com `support/BinanceFake` e `support/ContaSintetica` para os `Integracoes*IT` da app-api |
| Movidos (pacotes iguais) | `domain/integration/source/` (7), `credential/` (4), `pipeline/` (6) e `integration/binance/` (3); testes `CredentialCipherTest`, `CryptoImportRulesTest`, `ImportPipelineTest`, `BinanceClientTest`, `BinanceConnectorTest` e o suporte. `IntegrationRepositoryTest` ficou na app-api (é teste de repositório do core com a base JPA) |
| Ficou na app-api | `IntegrationService`, `IntegrationController`, `IntegrationWorker`, `IntegrationExceptionHandler`, `dto/*`, `IntegrationSyncScheduler` |
| Infraestrutura | `pom.xml` da raiz (módulo + `dependencyManagement` do jar e do test-jar), `primevest-app-api/pom.xml`, `Dockerfile` (pom e src), `docker-compose.yml` (app-api: pom e src; adm-api: só o pom, que o reator exige), `dev-watch.sh` (na app-api observa e limpa também o módulo), `.dockerignore` (`carga/mocks/` fora do contexto de build) |
| Bateria | antes: core 138, adm-api 79 + 28 IT, app-api 198 + 159 IT = **602**. Depois: core 138, **integration 44**, adm-api 79 + 28 IT, app-api 154 + 159 IT = **602**, todos verdes (`.claude/logs/f1-antes.log`, `f1-depois.log`) |
| Ambiente | `docker compose up -d --build`: app-api subiu compilando o módulo pelo reator; `GET /api/v1/integrations/connections` → 200 com a conexão Binance `ACTIVE`; `GET .../runs/{última}` → 200 (`SUCCEEDED`, divergência só no USDC, a já conhecida) |
| Documentos | `Claude.md` (tabela de módulos, "Onde mexer", estrutura, integrações), `README.md` (arquitetura) |

### 10.2 F2 executada (03/10/2026)

| Peça | Onde |
|---|---|
| Credencial (G1, G2, G9) | `SourceCredentials` (tipo selado): `ApiKeyCredentials` (Binance) e `InvestorDocument` (B3: CPF só com dígitos, dígitos verificadores conferidos, `toString` mascarado, `fingerprint` = 2 últimos dígitos). `CredentialVault` grava e lê os dois; o JSON da Binance continua o mesmo (as credenciais gravadas seguem legíveis) |
| Contrato (G3, G8, G12) | `SourceConnector.check/fetch` com `SourceCredentials`; `rules()`; `oncePerDay()`; `CredentialCheck.awaitingAuthorization` → conexão `PENDING_AUTHORIZATION` |
| Regras (G3, G4) | `ImportRules` (interface) e `CryptoImportRules`; `ImportRulesRegistry` acha as regras pelo prefixo do `externalId`. Pipeline, confirmação, restauração de exclusão e restauração do lançamento editado usam as regras da fonte |
| Movimento (G5) | `ImportedMovement` + `market`, `participant`, `tradeDate`; `MovementKind` com os tipos da B3 |
| Divergência (G6) | ativos acompanhados, saldos em dinheiro e "saldo vazio" vêm das regras da fonte |
| Banco (P2) | `V40__integracao_b3.sql`: só os dois `CHECK` (`provider` B3, `status` `PENDING_AUTHORIZATION`). Nenhuma tabela ou coluna nova |
| Bateria | 608 verdes (602 + 6 novos de credencial por CPF) |
| Regressão Binance | sincronização manual da conexão real na pilha de desenvolvimento, com o cofre novo: 0 novas, confirmada (`.claude/logs/f2-binance-check.log`) |

### 10.3 F3 executada (03/10/2026)

| Peça | Onde |
|---|---|
| Simulador | `carga/scripts/b3investidor/simulador/simulador.py` (Python + `openpyxl`): token `client_credentials`, healthcheck, Autorização, Negociação, Movimentação (ações, Tesouro, renda fixa), Posição (vazia: não exportada), Eventos Provisionados e a página de opt-in (Autorizar / Revogar). 401 sem Bearer, 422.01–422.07, `?_fail=429\|500\|503`, 20 itens por página com `Links` |
| Dados | os três extratos de `carga/mocks/` lidos como vieram (79 negócios, 343 movimentações, 23 proventos a receber); sem eles, `carga/scripts/b3investidor/sintetico/` (corretora e valores fictícios) |
| Compose | serviço `b3-investidor-mock` (profile `b3mock`; no teste `primevest-teste-b3-investidor-mock`, porta 8190); `B3I_*` na app-api; `.env.example` documentado; `.env.teste` com os valores do simulador e uma chave-mestra só da pilha de teste |
| Conferência | `carga/scripts/b3investidor/conferir.mjs`: conecta, autoriza no simulador, sincroniza, confirma e confere C1–C5 e C7 |

### 10.4 F4 executada (03/10/2026)

| Peça | Onde |
|---|---|
| `B3InvestidorClient` | token em cache (renova 5 min antes, A7); mTLS opcional pelo `.p12`; paginação pelo parâmetro `page` enquanto `Links.next` (ou `links.next`, A3) vier; 401 → token novo e uma tentativa; 429 → espera o Retry-After (até 60 s) e uma tentativa (A8); 5xx e rede → uma tentativa; mensagens com a rota configurada, nunca o CPF |
| `B3InvestidorConnector` | `check` pela Autorização (aceito / aguardando / CPF não localizado); `fetch`: Negociação agrupada (B1, B5), Movimentação classificada, Eventos Provisionados e Posição; cursor por data (`negociacao`, `movimentacao`); fim = D-1, ou D-2 antes das 8h de Brasília (A6); rota de conferência em branco = não consultada |
| `B3EquityImportRules` | B1–B10 |
| "Atualização" (confirmado) | nos extratos é crédito sem preço com a quantidade **já em custódia** no dia (PETR4 50 em 5 datas, BBAS3 100/125/175, BBSE3 10, CXSE3 100, IVVB11 6), perto de datas de provento: registro informativo da B3, não muda a posição → conferência (B8) |
| app-api | `ProventosConferencia` (C3 pela lógica do `ExtratoProventosImportService`, resultado no motivo do item); CPF único entre conexões vivas (A1); revogação na B3 → `REVOKED` e CPF apagado (C5); rotina diária também confere a conexão que aguarda autorização; divergência com `PositionCalculator.sharesOn` e os eventos do papel (6.7); `GET /api/v1/integrations/sources`; `authorizationUrl` na conexão; worker da fila separável das rotinas (`primevest.integrations.worker.enabled`), ligado na pilha de teste |
| G13 (achado) | `TransactionService.importTransaction` só aceitava cripto. Agora aceita ativo cotado (cripto e renda variável listada) e Tesouro (F7); recusa renda fixa privada e ativo de preço manual. O `IntegracoesB3IT` pegou |
| Rotina | `sync-cron` 06:30 → **08:45** (A6), refletido em `docs/SCHEDULE_TASKS.md` |
| Testes | `B3EquityImportRulesTest`, `B3InvestidorClientTest`, `B3InvestidorConnectorTest`, `IntegracoesB3IT` e `IntegracoesIT.b3Desligada`; `ContaSinteticaB3` (conta inventada no formato da API) no test-jar |
| Bateria | core 138, integration 100, adm-api 79 + 28 IT, app-api 156 + 166 IT = **667**, todos verdes depois do G13 (`.claude/logs/f4-verify-final.log`) |

### 10.5 F5 executada (03/10/2026) — ponta a ponta na pilha `primevest-teste`

Pilha de teste com `--profile b3mock` (simulador lendo os 3 extratos de `carga/mocks/`, CPF fictício), worker da fila
ligado e rotinas desligadas (D3). Carteira do usuário de teste começando vazia; gabarito = carteira da pilha de
desenvolvimento (carga de 24/09/2026). `node carga/scripts/b3investidor/conferir.mjs --api http://localhost:8082
--mock http://localhost:8190 --gabarito http://localhost:8080` (`.claude/logs/f5-conferir.log`).

| # | Resultado |
|---|---|
| Conexão | nasce `PENDING_AUTHORIZATION` com o link de opt-in; autorizada no simulador; a sincronização seguinte a leva a `ACTIVE` |
| Pré-visualização | **81 novas** (71 grupos de renda variável + 10 compras de Tesouro), 356 não importadas (71 liquidações, 231 proventos, 13 eventos do papel, 32 direitos, 9 de renda fixa), 0 possíveis duplicatas |
| C1 | ✅ 23 ativos de renda variável: **cotas e custo iguais** ao gabarito. Proventos diferem só em KLBN4 e PETR4, pelo catálogo de cada pilha (o do teste, carregado do bucket, não tem 3 eventos de KLBN4 nem 3 "Rendimento" de PETR4 que o de desenvolvimento tem) — não é da importação |
| C2 | ✅ 71 grupos = 71 "Transferência - Liquidação", 41 = 41 datas |
| C3 | ✅ depois de confirmar: **224 de 231** proventos (208 recebidos + 23 a receber) `JA_EXISTE`; 7 `SEM_EVENTO`, todos pendência de catálogo (os 3 "Rendimento" de PETR4, o dividendo a receber de KLBN4, um "Rendimento" de MXRF11, HGLG11 e GGRC11) |
| C4 | ✅ segunda sincronização: 0 novas (só a foto do dia: 23 proventos a receber, conferidos) |
| C5 | ✅ revogado no simulador → a sincronização seguinte falha com o motivo, a conexão fica `REVOKED` e o CPF é apagado; as importadas ficam na carteira |
| C6 | — sem a Posição exportada (16.8) |
| C7 | ✅ Tesouro: com a venda de Tesouro Selic 2028 (2,52 em 26/03/2025) lançada pela tela, como a F7 orienta, os 8 títulos ficam iguais ao gabarito em cotas e custo |
| Achado | **C3 na hora errada**: a conferência rodava só na pré-visualização — na primeira importação a carteira ainda não tem as posições, e saía tudo `SEM_EVENTO`. Agora roda de novo na confirmação, depois de gravadas as operações (`IntegrationService.confirmar`) |

### 10.6 F6 executada (03/10/2026) — front

| Peça | Onde |
|---|---|
| Fontes | `GET /api/v1/integrations/sources`: a tela oferece só o que o servidor tem ligado (a B3 com `B3I_ENABLED`); com mais de uma fonte, "Conectar" abre a escolha |
| Conectar B3 | `ConnectB3Modal`: apelido, CPF com máscara e dígitos conferidos, consentimento; o CPF vive só no estado do formulário |
| Cartão | estado "Aguardando autorização" com o link "Autorizar na B3" e o botão "Já autorizei — sincronizar"; CPF mostrado como `***.***.***-25`; revogada na fonte mostra o motivo; o marcador do menu conta a conexão aguardando |
| Pré-visualização | rótulos dos tipos da B3 (Liquidação, Provento, Evento do papel, Direito de subscrição...); data sem hora na B3; "Entrada/Saída" nos lançamentos de conferência; valor em reais; sem o cartão "Depósitos em reais" fora da Binance |
| Validação | `tsc -b`, eslint (src inteiro) e `vite build` limpos; tela conferida no navegador na pilha de teste (Ativa → resultado com 81 importadas e a conferência de proventos; revogação → Desconectada com o motivo; nova conexão → Aguardando autorização com o link) |
| Achado | **CORS da pilha de teste**: a origem `localhost:5174` não estava liberada, e todo POST/PUT/DELETE da tela de teste voltava 403 (os GET passam porque o navegador não manda `Origin` no GET da mesma origem). `CorsConfig` + `CorsConfigTest` |

### 10.7 F7 executada em parte (03/10/2026) — Tesouro e renda fixa

| O quê | Situação |
|---|---|
| Compra de Tesouro | ✅ vira transação do título do catálogo (o nome da B3 é o ticker: "Tesouro Selic 2028"), preço = valor ÷ quantidade, origem `AUTO` com o `external_id` da Movimentação. As 10 compras dos extratos conferem com o gabarito |
| Venda de Tesouro | ❌ não importada, com o motivo: o PrimeVest reparte a venda entre os títulos (FIFO) e refaz essas linhas a cada realocação, sem lugar para a origem da fonte. Lançada pela tela (PENDENCIAS 16.5) |
| CDB (renda fixa privada) | ❌ não importada: a Movimentação não traz taxa, índice nem o valor de resgate (o vencimento vem com valor zero) e o extrato tem a compra de 16/10/2024 em duas linhas iguais. Depende da Posição de renda fixa (16.6) |
| Valor atualizado pela Posição | espera a Posição exportada (16.8) |

### 10.8 F8 executada (03/10/2026) — documentos e bateria final

| Documento | O que mudou |
|---|---|
| `Claude.md` | módulo `primevest-integration` com o conector da B3, árvore, integrações, tela `/integrations`, simulador e `conferir.mjs`, variáveis `B3I_*` |
| `README.md` | integração com a B3 e o simulador na pilha de teste |
| `docs/PENDENCIAS.md` | a 16 aponta para este plano, com o que falta (16.4 a 16.9) |
| `docs/SCHEDULE_TASKS.md` | sincronização diária às 08:45, e a conexão aguardando autorização entra na rotina |
| `carga/scripts/README.md` | `b3investidor/` (simulador, sintéticos, `conferir.mjs`) |
| Bateria final | core 138, integration 103, adm-api 79 + 28 IT, app-api 158 + 166 IT = **672**, todos verdes (`.claude/logs/f8-verify.log`); front: `tsc -b`, eslint e `vite build` limpos. Pilha de teste parada com `pvt stop` (D8 do PLANO_PRIMEVEST_TESTE); a de desenvolvimento segue no ar, com a B3 desligada (sem `B3I_*` no `.env`) |

## 11. Dados do simulador (fora do git)

| Caminho | O que é |
|---|---|
| `carga/mocks/negociacao-*.xlsx` | extrato de Negociação da Área do Investidor |
| `carga/mocks/movimentacao-*.xlsx` | extrato de Movimentação |
| `carga/mocks/eventos-*.xlsx` | proventos a receber |
| `carga/mocks/posicao-*.xlsx` | (opcional) extrato de Posição |

`/carga/mocks/` está no `.gitignore`. O que é versionado é só o código do simulador e os dados sintéticos
(`carga/scripts/b3investidor/`).

---

## Fontes

B3 (oficiais):
- [B3 for Developers](https://developers.b3.com.br/) · [Catálogo das APIs da Área do Investidor](https://developers.b3.com.br/apis/api-area-do-investidor) · [FAQ](https://developers.b3.com.br/faq) · [Segurança](https://developers.b3.com.br/seguranca) · [Sandbox](https://developers.b3.com.br/duvidas/sandbox)
- [Autenticação Client Credentials B3i](https://developers.b3.com.br/index.php?id=26&option=com_content&view=article)
- [Manual Técnico APIs (atual, com a tarifa)](https://www.b3.com.br/lumis/portal/file/fileDownload.jsp?fileId=8AE490CA9358B1A70193BADF061C63AF) · [Manual Técnico APIs (2022)](https://www.b3.com.br/data/files/60/72/19/05/45CDF7104532BBF7AC094EA8/Manual%20Tecnico%20-%20APIs%20vf.pdf)
- [OC 177/2024 — nova tarifação das APIs](https://b3.com.br/data/files/D8/E6/92/49/4BAB39107A1B8539AC094EA8/OC%20177-2024%20PRE%20Nova%20tarifacao%20-%20APIs%20Area%20do%20Investidor%20da%20B3%20(PT).pdf)
- [Integrações da Área do Investidor (APIs)](https://b3.com.br/pt_br/produtos-e-servicos/central-depositaria/canal-com-investidores/integracoes-da-area-do-investidor-apis/)
- [Rascunho v1 — Negociação](https://www.b3.com.br/lumis/portal/file/fileDownload.jsp?fileId=8AA8D0CD73315593017359608A0938EC) · [Rascunho v1 — Eventos Provisionados](https://www.b3.com.br/lumis/portal/file/fileDownload.jsp?fileId=8AA8D0CD73315593017359608FDF3986)

Terceiros:
- Swagger [Posição 3.3.1-rc2](https://raw.githubusercontent.com/api-evangelist/b3-exchange/refs/heads/main/openapi/b3-exchange-investor-position-openapi.json) e [Movimentação 2.0.2-rc8](https://raw.githubusercontent.com/api-evangelist/b3-exchange/refs/heads/main/openapi/b3-exchange-investor-transactions-openapi.json) (cópias do portal publicadas por [api-evangelist/b3-exchange](https://github.com/api-evangelist/b3-exchange))
- [POC de cliente das APIs da B3 (Java)](https://github.com/felipewind/poc-b3-investor-api)
- [Kinvo — conexão com a B3, passo a passo](https://suporte.kinvo.com.br/articles/27138395-54b0-46d3-b0e8-bcaac722cdf0)
- [Open Finance Brasil — Renda Variável](https://openfinancebrasil.atlassian.net/wiki/spaces/OF/pages/144900298/Informa+es+Gerais+-+Renda+Vari+vel+-+v1.0.0-rc3.0) · [Pluggy — Investments](https://docs.pluggy.ai/docs/investments)
