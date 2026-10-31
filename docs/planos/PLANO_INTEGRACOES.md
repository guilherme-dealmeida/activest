# Activest — Plano de Integrações (menu genérico e ativos internacionais da Binance)

> Fase: **4 Implementation** — aprovada em 30/10/2026 ("pode seguir para próxima etapa"), por etapas (REGRAS 2.3);
> Implementation, Testing e Review concluídos em 31/10/2026 (seção 10.11); commit sugerido no fim da 10.11. Branch `TRACK_01-integracao_global`. Discovery (seções 3–6, D1–D12),
> Impact (seção 9) e Planning (seção 10, P1–P4). O ETF foi localizado na API oficial **Binance Stocks** (seção 3.2).
> Origem: ESCOPO "Integrações Activest" (30/10/2026). Texto-base: *"Hoje o menu Integrações está erroneamente voltado e
> rotulado como Binance, porém o intuito é fazer integração com várias plataformas, temos que tornar genérico e
> individualizar somente na seleção das opções de integrações que temos disponíveis. [...] Na integração Binance eu
> tenho ativos internacionais além de criptomoedas, o Activest precisa fazer a importação destes ativos."*
> Planos de origem: `PLANO_CRIPTO_BINANCE.md` (F1–F6), `PLANO_INTEGRACAO_B3.md` (G1–G12, simulador), 
> `PLANO_CADASTRO_MOEDAS.md` (moeda na transação, stablecoin → USD), `PLANO_ATIVOS_INTERNACIONAIS.md` (catálogo de
> Stock/REIT/ETF). Pendências ligadas: PENDENCIAS 15, 17, 35.7, 37.5, 38.1, 39.6.

---

## 1. Objetivo

1. **Integrações genéricas na tela e no serviço.** O menu Integrações deixa de ser, na prática, a tela da Binance: ele
   lista conexões de qualquer fonte e só se individualiza no momento de **escolher** a integração (catálogo de fontes
   disponíveis) e no formulário de credencial daquela fonte.
2. **Binance além de cripto.** A integração da Binance passa a importar também os **ativos internacionais** que o
   usuário tem lá (ETF americano comprado em 13/10/2026), gravando-os como ativos do exterior (em USD, com câmbio e as
   regras de tributo do exterior), e não como cripto.

## 2. Fora do escopo (proposta — confirmar na D1)

- Fontes novas (outra corretora de cripto, Open Finance, corretoras internacionais): o escopo deixa a **estrutura**
  pronta para elas, mas não implementa nenhuma.
- Ligar a B3 real (depende de contrato com a B3; segue no simulador `b3mock`).
- Preço e série histórica dos ativos internacionais (escopo de preços, PENDENCIAS 38.1 / Anexo A do plano de
  internacionais): o ativo importado fica com preço `MANUAL`, como os lançados à mão hoje.
- Itens antigos da cripto: Earn/staking, transferências de cripto entre carteiras (envio e recebimento), pares contra
  cripto sem valor em reais (PENDENCIAS 15). As conversões BRL → USDC (Convert) **estão** no escopo (já são importadas).
- bStocks (mint/redeem) e Stock Transfer da Binance (D11) e proventos dos internacionais (D12).

---

## 3. Discovery — o que foi levantado (30/10/2026)

### 3.1 Por que o menu "é da Binance" hoje

O backend e o front **já têm** uma camada genérica (herança da B3, 03/10/2026), mas na tela ela não aparece:

| Onde | O que acontece | Efeito |
|---|---|---|
| `B3I_ENABLED` (docker-compose, padrão `false`) | `GET /api/v1/integrations/sources` devolve só a Binance | A tela só conhece uma fonte |
| `IntegrationsPage.tsx` | `sourceList = sources ?? ['BINANCE']`; com uma fonte, **"Conectar" pula a escolha** e abre direto o `ConnectBinanceModal` | O usuário nunca vê um catálogo de integrações |
| `IntegrationsPage.tsx` | `SOURCE_TEXT` fixo no front (`BINANCE: 'Cripto: compras, vendas...'`, `B3: ...`); estado vazio lista só as fontes ligadas | Estado vazio = texto da Binance |
| `IntegrationRunPage.tsx` | `providerCode ?? 'BINANCE'`; linha "Depósitos em reais" só se `BINANCE` | Regra de tela por nome da fonte |
| `utils/integrations.ts` | `PROVIDER_NAME` fixo (`Binance`, `B3`) | Fonte nova exige mexer no front |
| Modais | `ConnectBinanceModal` e `ConnectB3Modal` (122 linhas cada, quase iguais) | Um modal por fonte, em vez de um por **tipo de credencial** |
| `DivergenceList.tsx` | Texto fala de "troca entre criptos, rendimentos..." | Texto de cripto numa lista genérica |
| `IntegrationService` (app-api) | `switch` por fonte em 3 lugares: tipo de credencial (`API_KEY`/`DOCUMENT`), montagem da credencial, CPF do perfil | Fonte nova = mexer no serviço |
| `IntegrationNotifier` | `switch` com o nome da fonte | Idem |
| `IntegrationProvider` (enum) + `ck_integration_connections_provider` | `BINANCE`, `B3`; fonte nova por migration | Esperado (registro), mas sem metadados |
| `ImportRulesRegistry` | **Uma** regra de importação por fonte (`provider → ImportRules`) | **Bloqueia o item 2**: a Binance hoje só tem `CryptoImportRules` |

O que **já é genérico** e fica: `SourceConnector`, `ImportedMovement`, `FetchResult`, `ImportPipeline`, credenciais
cifradas (`CredentialVault`), fila/worker, cursores, pré-visualização, exclusões, notificações.

### 3.2 Onde estão os ativos internacionais na Binance (conta real, MCP só leitura, 30/10/2026)

| Consulta | Resultado |
|---|---|
| Spot (`/api/v3/account`) | só `BTC 0,00831073`, `USDC 0,00814558`, `BRL 1,31` — **nenhum ETF** |
| Carteiras (`/sapi/v1/asset/wallet/balance`, valor em BTC) | Spot 0,00831344 · **Funding 0,00148048** · demais zeradas |
| Convert 2026 | **1 conversão em 13/10/2026: 615,26 BRL → 122,15 USDC** |
| Pares spot com negociação | só `BTCBRL` (nenhum par do USDC) |
| Saques e transferências Spot↔Funding/Alpha desde 10/2026 | nenhum |

**Confirmado em 30/10/2026 (prints do app + API):** o ETF é o **IAU — iShares Gold Trust**, comprado no produto
**Binance Stocks** (aba "Ações" do app; ações e ETFs americanos negociados contra USDC, com botão "Stock Transfer").
Não é token de terceiro: é a ação/ETF "nativo" do produto (a API distingue `tokenize=false` nativo × bStocks
tokenizados). A Binance tem **API oficial só de leitura (GET) para o histórico**, família `/sapi/v1/equity/*`
([docs Trade](https://developers.binance.com/en/docs/catalog/advanced-trading-stocks-trading/api/rest-api/trade)),
e a chave atual (só leitura) já a acessa:

| Endpoint (GET, assinado, peso 1) | O que devolveu na conta real |
|---|---|
| `/sapi/v1/equity/order/history` (`startTime`, `endTime` obrigatórios; paginado, até 100) | 2 ordens: **IAU BUY MARKET FILLED** `4f4724ee-1ded-4777-96e7-3aebfcecc5a2`, `filledQty 1.55913017`, `avgFilledPrice 78.268`, `filledTotal 122.03`, `fee 0.17` (USD), `quote USDC`, 13/10/2026 16:57:19; e uma LIMIT 1,56 a 78,19 **CANCELED** (nada executado) |
| `/sapi/v1/equity/trade/history` | 1 execução `719e666d-…` da ordem acima (preço 78.268, qtd 1.55913017, total 122.03) — **sem campo de taxa** (a taxa só vem na ordem) |
| `/sapi/v1/equity/order/detail` | a ordem com `trades[]` |
| `/sapi/v1/equity/order/history` de 01/01 a 13/10/2026 | vazio (é a única compra) |

Batimento com o app: valor do trade 122,03 + taxa 0,17 = **custo total 122,20 USDC**; quantidade 1,559130168 no app ×
1,55913017 na API (a API arredonda em 8 casas — cabe no `numeric(18,8)`). Os 122,20 USDC vieram do Convert BRL → USDC
de 122,15 (13/10, 16:46) mais o resíduo de USDC que já havia. O saldo do IAU (provável carteira Funding/"CARD") é
lido por `POST /sapi/v1/asset/get-funding-asset` ou `POST /sapi/v3/asset/getUserAsset`: o `binance-mcp` foi
ampliado para isso (D5, seção 6.1) e a leitura fica para depois de o MCP ser reiniciado.

**O conector do Activest não enxerga nada disso**: lê só Spot (`/api/v3/account`), `myTrades`, Convert, fiat e
depósitos de cripto. Não há, na documentação da Binance Stocks, endpoint de **proventos** nem de **eventos
corporativos** (o IAU não paga proventos; ações pagariam).

### 3.3 O que o Activest já tem para receber o ativo internacional

- Catálogo de `STOCK`, `REIT`, `ETF_INTERNACIONAL` (V13, 29/10/2026), moeda USD, ticker único por **(ticker, tipo)**.
- Transação em moeda estrangeira: `transactions.currency_code`, `fx_rate`, `original_*` (V9, moedas); câmbio PTAX
  diário; regras de tributo do exterior (`foreign_tax_rules`).
- Stablecoin com moeda de referência: `assets.reference_currency_code` (USDT/USDC → USD, D17 de moedas).
- `ImportRules.assetTypes()` já restringe a busca do ticker aos tipos da fonte (D3 de internacionais).

### 3.4 Riscos já visíveis

1. ~~Token ≠ ETF~~ — resolvido pela D3 (é o ETF) e pela API (símbolo `IAU` = ticker do catálogo, `ETF_INTERNACIONAL`,
   NYSE Arca). Fica um risco menor: **bStocks** (ETF/ação tokenizado por "mint" na Binance) — o mint/redeem não é
   compra/venda (D11).
2. **Perna em stablecoin.** BRL → USDC (Convert) já vira compra de USDC (cripto). Se o ETF entrar, a perna USDC → ETF
   tem de virar **venda de USDC** + **compra do ETF em USD**, senão o USDC fica "fantasma" na carteira (**D6**).
3. **API nova.** A API da Binance Stocks foi lançada em 20/07/2026; o formato pode mudar e não há limite de janela
   documentado no `order/history` (o teste com 10 meses funcionou). Trade history sem taxa: a importação usa a
   **ordem** (taxa) como unidade, como a Binance cripto já faz (R1, uma ordem = uma transação).
4. **Divergência.** Hoje a divergência compara só com o Spot; incluir Funding pode gerar alertas novos para quem tem
   cripto parada na Funding.

---

## 4. Requisitos (rascunho para o Impact)

| # | Requisito |
|---|---|
| RQ1 | "Conectar" **sempre** abre a seleção de integrações, mesmo com uma fonte só |
| RQ2 | Nome, descrição, categoria, logo e tipo de credencial de cada fonte vêm do **backend** (descritor do conector), não de textos fixos no front |
| RQ3 | Um formulário de conexão por **tipo de credencial** (chave+segredo; documento/CPF), com instruções da fonte injetadas |
| RQ4 | Nenhuma regra de tela ou serviço por **nome** de fonte (`=== 'BINANCE'`, `case B3`): vira **capacidade** do descritor (ex.: "tem depósito em reais", "uma consulta por dia", "precisa autorizar na fonte") |
| RQ5 | Uma fonte pode ter **mais de uma regra de importação** (cripto, exterior...) — escolhida por movimento |
| RQ6 | A Binance importa o ativo internacional como `STOCK`/`REIT`/`ETF_INTERNACIONAL` em USD, com câmbio da data (sujeito à D3) |
| RQ7 | Pré-visualização mostra o que é cripto e o que é exterior, com o motivo do que não entrou |
| RQ8 | Sem regressão: Binance cripto e B3 (simulador) importam igual a hoje (ITs atuais verdes) |

## 5. Recomendações do Discovery

- **R1** Descritor no conector (`SourceConnector.descriptor()`: nome, descrição, categoria, credencial, capacidades) e
  `GET /sources` devolvendo isso; o front monta catálogo e formulário a partir dele.
- **R2** Mostrar no catálogo também as fontes **existentes mas desligadas** (B3 sem contrato) como "indisponível",
  para a tela já nascer com cara de várias integrações (D2).
- **R3** `ImportRules` composta: o conector diz qual regra vale para cada movimento (ex.: `ImportedMovement.domain`
  = `CRYPTO` / `FOREIGN`), e o registro passa a ser por (fonte, domínio).
- **R4** ~~Confirmar o produto~~ (feito, seção 3.2). Novo fluxo no conector da Binance: `equity` (cursor = `updatedAt`
  da última ordem), lendo `/sapi/v1/equity/order/history` com `FILLED,PARTIALLY_FILLED`, uma transação por ordem
  (`externalId` `binance:equity:<orderId>`), quantidade = `filledQty`, preço = `avgFilledPrice`, taxa = `fee` (USD),
  moeda do ativo USD; e o saldo das ações na conferência de divergência (D8).
- **R6** Regras de importação do exterior (`ForeignImportRules`): ticker procurado só em `STOCK`/`REIT`/`ETF_INTERNACIONAL`;
  nunca cria ativo; `USDC`/`USDT` valem USD pela moeda de referência do cadastro de moedas; câmbio da D10; tributos do
  exterior aplicados pelo que já existe (`foreign_tax_rules`).
- **R5** Manter o enum + migration por fonte nova (registro com constraint), pondo os metadados no código do conector.

## 6. Decisões para o Guilherme (Discovery)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D1** | Fora do escopo da seção 2 está correto? | (a) sim; (b) ajustar | (a) |
| **D2** | Fonte existente mas desligada (B3 sem contrato) aparece no catálogo? | (a) aparece como "indisponível"; (b) não aparece | (a) |
| **D3** | ETF tokenizado da Binance entra como... | (a) o próprio ETF do catálogo (`ETF_INTERNACIONAL`, USD, tributos do exterior); (b) cripto (token, sem impostos); (c) tipo novo "ativo tokenizado" | (a), com o token guardado na origem da transação — é a exposição econômica, e é a regra de tributo que você já definiu para internacionais. **Depende da D4** |
| **D4** | Em que produto/tela da Binance você comprou o ETF e qual é o nome/ticker que aparece lá? | — | Informação sua (R4) |
| **D5** | Posso ampliar o `binance-mcp` (E:\mcp\binance-mcp) para aceitar **POST de leitura** (`get-funding-asset`, `getUserAsset`) e investigar a Funding? | (a) sim, eu altero; (b) você altera; (c) não | (a) — a chave segue só de leitura |
| **D6** | Perna em stablecoin (USDC → ETF) | (a) venda de USDC + compra do ETF em USD (USDC → USD pela moeda de referência, câmbio PTAX da data); (b) ignorar o USDC e lançar só o ETF pelo valor em reais do Convert | (a) — mantém a posição de USDC certa e reaproveita o insumo de moedas |
| **D7** | Recadastro da Binance e as 8 transações do PrimeVest (PENDENCIAS 35.7, 37.5) entram neste escopo? | (a) sim, como prova final; (b) escopo à parte | (a) — a prova do ETF já exige o recadastro |
| **D8** | Divergência passa a considerar a carteira Funding? | (a) sim, Spot + Funding; (b) só Spot, como hoje | (a), se a D4 confirmar o ETF na Funding |
| **D9** | Logo das fontes no catálogo | (a) você fornece em `public/images`; (b) só a inicial, como hoje | (a) |
| **D10** | Câmbio da compra importada (USD → BRL) | (a) PTAX venda do dia da operação, como no lançamento manual (D5/D16 de moedas); (b) a taxa implícita do Convert BRL → USDC mais próximo (13/10: 5,03688) | (a) — mesma regra do manual e funciona sem Convert (USDC vindo de cripto, venda de ação); editável depois na transação |
| **D11** | bStocks (mint/redeem de ação tokenizada) e "Stock Transfer" (transferência de custódia) | (a) fora desta versão: aparecem na pré-visualização como "não importado" com o motivo; (b) entram agora | (a) — você não tem nenhum; nada a provar |
| **D12** | Proventos de ações/ETFs comprados na Binance | (a) fora: virão do catálogo quando houver fonte de proventos do exterior (PENDENCIAS 38.1); (b) buscar na Binance | (a) — a API da Binance Stocks não tem endpoint de proventos, e catálogo vem de integração de catálogo |

### 6.1 Respostas do Guilherme (30/10/2026)

| # | Resposta |
|---|---|
| D3 | **(a) É um ETF** — IAU (iShares Gold Trust), prints da carteira, do ativo e da ordem |
| D4 | Produto **Binance Stocks** (aba "Ações" do app), IAU, ordem `4f4724ee-…-3aebfcecc5a2`, mercado/compra em 13/10/2026 16:57:20, 1,559130168 a 78,268 USDC, trade 122,03 + taxa 0,17 = 122,20 USDC |
| D5 | **Sim.** Feito em 30/10/2026: `E:\mcp\binance-mcp\index.mjs` v0.2.0 com a ferramenta `binance_post_read`, que aceita POST só em `/sapi/v1/asset/get-funding-asset` e `/sapi/v3/asset/getUserAsset` (lista fechada; qualquer outro POST é recusado) e README atualizado. Passa a valer quando o MCP for reiniciado (reabrir o Claude Desktop) |

| D10 | **(a)** pela recomendação, aprovada explicitamente: PTAX venda do dia da operação, editável depois na transação |
| D11 | Pediu explicação do impacto (respondida no chat em 30/10/2026, resumo abaixo) — **continua em aberto** |
| D12 | **(a)**, nas palavras dele: provento dos nacionais é calculado (cotas na data com × evento do catálogo); o IAU não paga; quando houver preço e proventos dos internacionais no catálogo, será no mesmo estilo; a Binance não deve enviar esses valores |

**D11 — o que deixaria de ser importado com (a):** só dois movimentos que não são compra nem venda e que hoje não
existem na conta. (1) **Mint/redeem de bStocks**: transformar o IAU "nativo" em token (ou o inverso). Não muda a
exposição; com (a), aparece na pré-visualização como não importado e a posição no Activest fica como está — o saldo
nativo da Binance cai e o aviso de divergência acusa até a regra existir. (2) **Stock Transfer**: ações que entram ou
saem da Binance vindas de/para outra corretora, sem compra. A API não tem endpoint para isso (nem dá para mostrar como
não importado); o lançamento seria manual, como a transferência de custódia da B3 (B10). Compras e vendas (ordens
`FILLED`/`PARTIALLY_FILLED`) entram sempre; ordem cancelada não tem o que importar em nenhuma opção.

| D2 | **(a)** pela recomendação, aprovada explicitamente: a B3 aparece no catálogo como "indisponível" |
| D6 | **(a)** pela recomendação, aprovada explicitamente: venda de USDC + compra do ETF em USD (câmbio da D10) |
| D7 | **(a) Sim**: recadastro da Binance e as 8 transações do PrimeVest entram, como prova final |
| D1 | "Em partes": perguntou se as conversões BRL → USDC estão no escopo (receio do termo "transferências"). Esclarecido no chat em 30/10/2026 — **aguarda confirmação** |
| D8 | Perguntou que aviso é esse. Explicado no chat em 30/10/2026 — **aguarda resposta** |
| D9 | Perguntou quais fontes (os ativos já têm logo). Esclarecido: logo das **integrações** (Binance, B3) no catálogo — **aguarda resposta** |
| D11 | Continua sem entender; lembrou que o IAU foi comprado na Binance. Reexplicado no chat em 30/10/2026 — **aguarda resposta** |

**D1 — esclarecimento:** as conversões BRL → USDC (Binance Convert) **já são importadas hoje** (`CryptoImportRules`,
compra de USDC em reais) e continuam no escopo; o Convert de 13/10 entra na prova (D7). O "transferências" da seção 2
quer dizer só **envio/recebimento de cripto entre carteiras ou corretoras** (depósito e saque de cripto), que não é
compra nem venda. Texto da seção 2 a ajustar para "transferências de cripto entre carteiras (envio e recebimento)".

**D8 — o aviso de divergência (existente, regra E3/G6):** a cada sincronização, o Activest compara a quantidade que
ele calcula de cada ativo com o saldo que a fonte informa. Se não bate, o cartão da conexão em Integrações mostra
"N ativos divergem da Binance" (lista com as duas quantidades) e o ícone de Integrações no cabeçalho ganha o marcador.
Hoje a Binance compara só a carteira Spot (cripto). A D8 pergunta se o IAU (e futuras ações/ETFs da Binance) entra
nessa comparação.

**Fechamento (30/10/2026):** D9 — logos já estão em `activest-web/public/images/B3.png` e `BINANCE.png` (conferidos);
D1, D8 e D11 — *"o restante das decisões seguir com a recomendação"* (aprovação explícita, REGRAS 2.5): D1 com o texto
de "transferências" ajustado (seção 2), D8 (a) o ETF entra na conferência de divergência, D11 (a) bStocks e Stock
Transfer fora desta versão (pendência). **Discovery encerrado**; mandou seguir para a próxima etapa.

## 7. Próximos passos

1. ~~Discovery~~ encerrado (30/10/2026).
2. Impact e Planning nas seções 9 e 10: você responde P1–P4 e aprova a Implementation.
3. Reabrir o Claude Desktop antes da E1 (MCP v0.2.0).

## 8. Andamento

| Data | Etapa | O que |
|---|---|---|
| 30/10/2026 | 1 Discovery | Levantamento do código (front, app-api, integration), da conta real da Binance (MCP só leitura) e decisões D1–D9 |
| 30/10/2026 | 1 Discovery | D3–D5 respondidas; IAU localizado na API oficial Binance Stocks (`/sapi/v1/equity/*`, GET com a chave atual); `binance-mcp` v0.2.0 com POST de leitura; decisões novas D10–D12 |
| 30/10/2026 | 1 Discovery | D10 e D12 decididas; impacto da D11 explicado |
| 30/10/2026 | 1 Discovery | D2, D6 e D7 decididas; esclarecimentos de D1, D8, D9 e D11 |
| 30/10/2026 | 1 Discovery | D1, D8, D9 e D11 decididas; Discovery encerrado |
| 30/10/2026 | 2 Impact / 3 Planning | Seções 9 e 10; decisões P1–P4; aguardando aprovação para a Implementation |

---

## 9. Impact — análise de impacto (30/10/2026)

### 9.1 Achados do Impact (código conferido)

| # | Achado | Consequência |
|---|---|---|
| I1 | `TransactionService.importTransaction` **recusa** ativo com `price_source = MANUAL` (exceto Tesouro). `STOCK`/`REIT`/`ETF_INTERNACIONAL` são `MANUAL` desde a V13 | O IAU seria recusado na confirmação: a importação tem de aceitar os tipos do exterior (`AssetTypeNames.FOREIGN_LISTED`) |
| I2 | `TransactionCreateDTO.fxRate` nulo → o `TransactionCurrency` grava pela **PTAX venda da data** (moedas, D5/D16); preço, total e taxa vão na moeda do ativo | D10 sai de graça: a proposta leva preço/taxa em USD e `fxRate` nulo |
| I3 | `ImportPipeline.preview(..., ImportRules rules)` e `ImportRulesRegistry` (fonte → **uma** regra); `IntegrationService` usa `connector.rules()` em 4 lugares e `TransactionService` usa `forExternalId` no "restaurar" | Regra por **movimento** muda pipeline, registro, confirmação, restaurar e divergência |
| I4 | `ImportRules.Result` = **uma** transação por movimento | A perna em USDC (D6) não cabe num movimento só: o conector emite **dois** movimentos por ordem (ETF e USDC), cada um com o seu `externalId` |
| I5 | `CryptoImportRules` (R6) recusa cotação que não seja BRL; a regra é pura (só o catálogo entra como função) | A perna USDC cotada em USD precisa do câmbio: as regras passam a receber um **contexto** (catálogo + câmbio da data) |
| I6 | Divergência (`IntegrationService.divergences`) casa saldo e posição **pelo ticker** e só com `rules.tracksPosition` | Com cripto e ETF na mesma fonte, a chave do saldo precisa do domínio (`IAU` ação × `IAU` cripto hipotético) |
| I7 | `BinanceClient` só tem `signedGet`; o saldo das ações (D8) sai de `POST getUserAsset`/`get-funding-asset` (a confirmar na E1) | `signedPost` no cliente, limitado aos endpoints de leitura |
| I8 | Front: `IntegrationsPage` (`['BINANCE']` de reserva, pula a escolha), `IntegrationRunPage` (`'BINANCE'` de reserva, depósitos em reais por nome), `PROVIDER_NAME`/`SOURCE_TEXT` fixos, dois modais de conexão quase iguais, `DivergenceList` com texto de cripto | Seção 3.1; vira descritor vindo do backend |
| I9 | `IntegrationSourceController` devolve só as fontes **ligadas**; `IntegrationNotifier` e `IntegrationService` com `switch` por fonte | Descritor por conector; a B3 desligada aparece como indisponível (D2) |
| I10 | Nenhuma tabela nova: `IntegrationProvider` continua `BINANCE`, `B3`; `externalId` novo `binance:equity:<orderId>` cabe no formato atual | **Sem migration** |

### 9.2 Componentes afetados

| Módulo | Componente | Mudança |
|---|---|---|
| integration | `SourceConnector` | `descriptor()` (nome, descrição, categoria, credencial, logo, capacidades) e `rulesFor(ImportedMovement)` (padrão: `rules()`) |
| integration | `ImportRules`, `ImportPipeline`, `ImportRulesRegistry` | contexto (catálogo + câmbio); regra escolhida por movimento; registro por (fonte, movimento) |
| integration | `CryptoImportRules` | aceita a perna de liquidação em stablecoin cotada em moeda do cadastro (USD), convertida pelo câmbio do contexto |
| integration | **novo** `ForeignImportRules` | ticker só em `FOREIGN_LISTED`; preço/taxa em USD; nunca cria ativo; motivos legíveis |
| integration | `MovementKind` | `FOREIGN_TRADE` (ordem da Binance Stocks), `STABLECOIN_SETTLEMENT` (perna USDC), `TOKENIZED_CONVERT` (D11, não importado) |
| integration | `BinanceClient`, `BinanceConnector` | `signedPost` de leitura; fluxo `equity` (cursor por `updatedAt`) e saldo das ações |
| app-api | `IntegrationService`, `IntegrationNotifier`, `IntegrationSourceController`, `SourceDTO` | sem `switch` por fonte; `/sources` com todas as fontes e `available` |
| app-api | `TransactionService.importTransaction` | aceita `FOREIGN_LISTED` (preço em USD, `fxRate` nulo → PTAX) |
| web | `IntegrationsPage`, `IntegrationRunPage`, `utils/integrations.ts`, `types`, `api/integrations.ts` | catálogo de fontes com logo, escolha sempre aberta, sem reserva `'BINANCE'`, regras por capacidade |
| web | `ConnectBinanceModal` + `ConnectB3Modal` → `ConnectSourceModal` | um modal por tipo de credencial; instruções da fonte num bloco por fonte |
| web | `DivergenceList` | texto genérico |
| testes | `CryptoImportRulesTest`, `ImportPipelineTest`, `BinanceConnectorTest`, `BinanceFake`/`SyntheticAccount`, `IntegrationsIT`, `IntegrationsConfirmationIT`, `IntegrationsB3IT`, Vitest | casos novos (seção 10.5) |
| docs | `Claude.md`, `README.md`, `docs/PENDENCIAS.md` (15, 17, 35.7, 37.5, 38.1, 39.6 + D11/D12) | REGRAS 7.2 |

### 9.3 Dependências

- **Cadastro de moedas** (PTAX diária, `TransactionCurrency`, USDC → USD): pronto.
- **Ativos internacionais** (IAU no catálogo, `ETF_INTERNACIONAL`, NYSE Arca): pronto.
- **Chave da Binance no Activest** (D7): o Guilherme recadastra na tela, na E6.
- **MCP reiniciado** (E1): para ler o saldo das ações e o histórico de tokenização antes de codificar.

### 9.4 Riscos e efeitos colaterais

| # | Risco | Mitigação |
|---|---|---|
| X1 | Mudar o pipeline quebrar Binance cripto ou B3 | `rulesFor` com padrão `rules()`: B3 e cripto seguem iguais; ITs atuais verdes antes de seguir (RQ8) |
| X2 | Posição do IAU sem preço (preço `MANUAL`, fora do escopo) | Valorizada pelo custo, como o lançamento manual de hoje; já registrado (PENDENCIAS 38.1) |
| X3 | Diferença de câmbio na perna USDC: comprada a 5,03688 no Convert, vendida pela PTAX do dia | Pequeno resultado na cripto USDC (sem imposto); é o efeito correto da D6/D10. Editável na transação |
| X4 | Saldo das ações sem endpoint de leitura confiável | E1 confirma; se não houver, a divergência das ações fica de fora e vira pendência (avisado antes de codificar) |
| X5 | API da Binance Stocks nova (07/2026) | Cliente tolerante a campo novo; falha do fluxo `equity` não derruba os demais fluxos (erro registrado na execução) |
| X6 | Chave antiga da Binance sem acesso ao produto Stocks | A chave atual (MCP) já lê `/sapi/v1/equity/*`; a nova também só precisa de "Enable Reading" |
| X7 | Lançamento manual do IAU feito antes | Já tratado: possível duplicata na pré-visualização (6.6) |

## 10. Planning — solução técnica (30/10/2026)

### 10.1 Integrações genéricas

- **Descritor da fonte** (`SourceDescriptor` no módulo integration): `provider`, `name` ("Binance", "B3"),
  `category` ("Corretora de cripto e ações dos EUA", "Bolsa brasileira"), `description` (o que importa),
  `credential` (`API_KEY` | `DOCUMENT`), `logo` (`/images/BINANCE.png`, `/images/B3.png` — D9) e capacidades:
  `fiatDeposits`, `oncePerDay`, `authorizationUrl`.
- **`GET /api/v1/integrations/sources`** devolve **todas** as fontes com `available` (B3 = `b3i.enabled`). D2: a
  indisponível aparece no catálogo, sem botão de conectar, com "Indisponível no momento".
- **`IntegrationService` / `IntegrationNotifier`**: tipo de credencial, montagem da credencial e nome vêm do descritor;
  o CPF do perfil continua sendo a regra da credencial `DOCUMENT`.
- **Front**: "Conectar" sempre abre o catálogo (logo, nome, categoria, descrição); escolher a fonte abre o
  `ConnectSourceModal` do tipo de credencial dela, com o bloco de instruções daquela fonte (o único ponto
  individualizado, como pedido). Cartão da conexão com o logo no lugar da inicial. Estado vazio genérico. Sem reserva
  `'BINANCE'`; "Depósitos em reais" pela capacidade `fiatDeposits`. Textos de tela sem siglas internas (convenção de
  25/10).

### 10.2 Regra por movimento

- `SourceConnector.rulesFor(ImportedMovement)` (padrão `rules()`); a Binance devolve `ForeignImportRules` para
  `FOREIGN_TRADE` e `CryptoImportRules` para o resto. `ImportRulesRegistry.forMovement(m)` substitui
  `forExternalId` (o "restaurar" relê o movimento gravado).
- `ImportRules.apply(m, ImportContext ctx)`: `ctx.asset(ticker)` (nos tipos daquela regra) e `ctx.fxRate(moeda, data)`
  (PTAX venda, a mesma do `TransactionCurrency`). As regras continuam puras: o contexto é montado pela app-api.

### 10.3 Binance Stocks

- **Fluxo `equity`** (cursor `equity` = maior `updatedAt` confirmado; primeira busca desde `binance.equity-history-start`,
  padrão 2026-07-01, mês a mês): `GET /sapi/v1/equity/order/history?orderStatus=FILLED,PARTIALLY_FILLED`, páginas de 100.
- **Uma ordem → dois movimentos** (I4, D6):
  1. `binance:equity:<orderId>` — `FOREIGN_TRADE`, ativo `IAU`, lado da ordem, quantidade `filledQty`, valor
     `filledTotal`, taxa `fee` (USD), cotação `USDC`, instante `updatedAt`.
  2. `binance:equity:<orderId>:quote` — `STABLECOIN_SETTLEMENT`, ativo `USDC`, lado **oposto** (compra do ETF = venda de
     USDC), quantidade = `filledTotal + fee` na compra (122,20) e `filledTotal − fee` na venda.
- **`ForeignImportRules`**: acha o ticker em `FOREIGN_LISTED` (nunca cria); propõe preço = `filledTotal / filledQty`
  (8 casas), taxa em USD, moeda USD, `fxRate` nulo (PTAX da data, D10); cotação aceita = moeda do cadastro ou
  stablecoin com moeda de referência (USDC/USDT → USD).
- **`CryptoImportRules`**, perna `STABLECOIN_SETTLEMENT`: preço em reais = 1 × câmbio da moeda de referência na data
  (`ctx.fxRate`), sem taxa (a taxa fica no ETF).
- **`importTransaction`** aceita `FOREIGN_LISTED` (I1), repassando preço/taxa em USD.
- **Divergência (D8)**: saldo das ações pelo endpoint que a E1 confirmar, com chave `equity:IAU`; cripto continua pelo
  Spot. `tracksPosition` passa a ser a união das regras da fonte.
- **D11**: histórico de tokenização (`Tokenized Convert History`, GET, caminho a confirmar na E1) entra como
  `TOKENIZED_CONVERT`, "não importado: conversão em bStocks fora desta versão". Stock Transfer não tem endpoint.

### 10.4 Etapas (cada uma fechada — implementada, testada, bateria verde — antes da próxima, REGRAS 2.3)

| Etapa | O que | Prova |
|---|---|---|
| **E1** | Prova das fontes com o MCP reiniciado: saldo das ações (`binance_post_read`), caminho e formato do histórico de tokenização, limite de janela do `order/history` | Saldo do IAU = 1,55913017 lido por API; registrado na seção 10.7 |
| **E2** | Backend genérico: descritor, `/sources` com `available`, `IntegrationService`/`Notifier` sem `switch` | ITs atuais verdes; `/sources` com B3 indisponível |
| **E3** | Regra por movimento e `ImportContext` (pipeline, registro, confirmação, restaurar, divergência com chave por domínio) | ITs da Binance cripto e da B3 com o mesmo resultado |
| **E4** | Binance Stocks: `signedPost`, fluxo `equity`, dois movimentos por ordem, `ForeignImportRules`, perna USDC, `importTransaction` com `FOREIGN_LISTED`, saldo das ações, tokenização como não importada | Testes da seção 10.5 |
| **E5** | Front: catálogo com logos, `ConnectSourceModal`, sem reservas por nome, divergência genérica | Vitest + navegador nos dois temas (REGRAS 4.3) |
| **E6** | Prova real (D7): Guilherme recadastra a chave; sincronizar; conferir na pré-visualização e confirmar | 8 transações do PrimeVest + Convert BRL→USDC de 13/10 + venda de 122,20 USDC + compra de 1,55913017 IAU a US$ 78,268 com taxa US$ 0,17 e PTAX de 13/10; sem divergência |
| **E7** | Testing completo (`mvn clean verify`, `tsc`, `eslint`, `vite build`, Vitest), Review e docs (`Claude.md`, `README.md`, `PENDENCIAS`) | Bateria verde; mensagem de commit sugerida |

### 10.5 Testes novos

- `ForeignImportRulesTest`: compra/venda, ticker fora do catálogo, ticker que só existe como cripto, cotação não aceita.
- `CryptoImportRulesTest`: perna `STABLECOIN_SETTLEMENT` (compra e venda), sem câmbio na data.
- `BinanceConnectorTest` + `BinanceFake`: ordens da Binance Stocks (preenchida, parcial, cancelada), paginação, cursor,
  dois movimentos por ordem, tokenização.
- `ImportPipelineTest`: regra por movimento.
- `IntegrationsIT` com a `SyntheticAccount` ganhando a ordem do IAU: confirmação grava ETF em USD (PTAX) e venda de
  USDC; restaurar e excluir; divergência com `equity:IAU`.
- `IntegrationsB3IT` sem mudança de resultado; IT de `/sources` com a B3 desligada.
- Vitest: catálogo de fontes (disponível/indisponível), escolha do modal pela credencial.

### 10.6 Critérios de aceitação

1. "Conectar" sempre abre o catálogo, com logo, nome e descrição de cada fonte; a B3 sem contrato aparece como
   indisponível; nada na tela de Integrações diz "Binance" fora do cartão/modal da Binance.
2. Nenhuma regra de tela ou serviço compara o nome da fonte (`'BINANCE'`, `case B3`) fora do conector.
3. A sincronização da Binance traz o IAU como `ETF_INTERNACIONAL` em USD, câmbio PTAX venda de 13/10/2026, taxa US$ 0,17,
   e a venda correspondente de 122,20 USDC.
4. A divergência compara também as ações da Binance e não acusa nada depois da prova (E6).
5. Binance cripto e B3 (simulador) importam igual a antes; bateria inteira verde.
6. Documentação e pendências atualizadas (D11, D12, PENDENCIAS 35.7 e 37.5 baixadas).

### 10.7 Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| Reabrir o Claude Desktop para o MCP v0.2.0 (E1) | — | ✔ |
| E1–E5, E7 (código, testes, docs) | ✔ | — |
| Recadastrar a chave da Binance na tela (E6) | prepara e confere | ✔ |
| Conferência visual final | ✔ (navegador do app) | confere |
| Commit / PR | sugere a mensagem | ✔ |

### 10.8 Decisões do Planning

> MCP (pergunta de 30/10/2026): o `binance-mcp` é ferramenta do Claude para levantamento e conferência, não faz parte
> do Activest. Na aplicação, o `BinanceConnector` (Java, app-api) chama a API da Binance direto, com a chave de cada
> usuário cifrada no Activest; usuário nenhum precisa de MCP.

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **P1** | Onde fica a taxa da ordem (US$ 0,17) | (a) na compra do ETF (taxa da transação, em USD) e a venda de USDC cobre o custo total (122,20); (b) separada na perna USDC | (a) — é custo de aquisição do ETF (entra no preço médio) e a quantidade de USDC fica igual à da corretora |
| **P2** | Quantidade do IAU | (a) a da API, 8 casas (1,55913017); (b) a do app, 9 casas | (a) — é o que a fonte entrega e cabe na coluna; diferença de 0,000000002 |
| **P3** | Início do histórico da Binance Stocks na primeira busca | (a) 01/07/2026 (pouco antes do lançamento da API), configurável; (b) desde 2017 como a cripto | (a) — o produto não existia antes; evita centenas de chamadas vazias |
| **P3 (revista)** | Em 30/10/2026 o Guilherme informou que o primeiro lançamento dele na Binance foi em 11/2025. Como a integração é multiusuário, o início não segue a conta dele: a cripto já busca desde 2017 (cobre 11/2025); para a Binance Stocks, o início passa a ser o **lançamento do produto** (não só da API), confirmado na E1 consultando o `order/history` para trás. A busca é mês a mês e barata (~1 chamada por mês) | (a) lançamento do produto, confirmado na E1; (b) 01/11/2025 | (a) — aguarda resposta |
| **P4** | Execução | (a) por etapas (E1→E7), uma fechada antes da outra; (b) todas sem pausa, salvo evento crítico (como em moedas) | Escolha sua |

### 10.9 Respostas do Guilherme ao Planning (30/10/2026)

- P1, P2: pela recomendação, aprovadas explicitamente ("de acordo com todas as recomendações de P").
- P3: **(a)** — início da Binance Stocks no lançamento do produto ("a Binance não trabalhava com estes ativos antes desta data").
- P4: não tinha recomendação; sem escolha explícita, segue a regra padrão **por etapas** (REGRAS 2.3), uma fechada antes da outra.
- Mandou seguir para a Implementation.

### 10.10 E1 — prova das fontes (30/10/2026)

| Item | Resultado |
|---|---|
| Janela do `order/history` | 01/01/2024 → 30/10/2026 numa chamada só: respondeu (2 ordens). Sem limite de janela observado: a busca usa uma janela do início até agora, paginada, em vez de mês a mês |
| Lançamento do produto (P3) | `GET /sapi/v1/equity/market/exchangeInfo`: menor `listingTime` lido = **08/05/2026** (lote de 07–15/05/2026; ~261 símbolos lidos, a resposta veio truncada em 100 mil caracteres). IAU listado em 01/06/2026. Início do fluxo: `binance.equity-history-start = 2026-05-01` |
| Tokenização (D11) | `GET /sapi/v1/equity/tokenized/history` (paginado por `lastId`/`nextLastId`, até 100): vazio na conta. Mint/redeem são POST (`/tokenized/mint`, `/redeem`) — o Activest nunca chama |
| `exchangeInfo` do IAU | `stepSize 0.000000001` (9 casas): a corretora aceita 9 casas; a API de ordens devolveu 8 (P2 mantida) |
| Saldo das ações (D8) | **Fechado (31/10/2026, MCP v0.2.0):** `POST /sapi/v1/asset/get-funding-asset` devolve `{"asset":"EQ_IAU","stockTicker":"IAU","free":"1.559130168",...}` — as ações ficam na carteira **Funding**, com prefixo `EQ_` e o ticker em `stockTicker`. `getUserAsset` traz só o Spot (BRL, BTC, USDC). **Achado:** o saldo tem 9 casas e a ordem 8 (1,55913017): a divergência compara o saldo **arredondado a 8 casas** (escala da coluna), senão acusaria −0,000000002 |

**E1 fechada em 31/10/2026.**

### 10.11 Andamento da Implementation (31/10/2026)

| Etapa | Situação | O que ficou |
|---|---|---|
| E1 | ✅ fechada | Seção 10.10 |
| E2 | ✅ fechada | `SourceDescriptor`/`SourceDescriptors` (integration): nome, categoria, descrição, credencial, logo, `fiatDeposits`, `dateOnly`; `GET /sources` com todas as fontes e `available` (B3 desligada = indisponível); `IntegrationService` monta a credencial pelo tipo do descritor; `IntegrationNotifier` e a trava do CPF (`UserService`) sem nome fixo de fonte. ITs de integração verdes |
| E3 | ✅ fechada | `ImportContext` (catálogo nos tipos da regra + PTAX venda) e `ImportContextFactory`; `SourceConnector.rulesFor`/`allRules`; `ImportRules.apply(m, contexto)` e `balanceKey`; `ImportRulesRegistry.forMovement`; pipeline, confirmação, restaurar e pré-visualização pela regra do movimento; divergência com chave por domínio e saldo na escala gravada (8 casas) |
| E4 | ✅ fechada | `MovementKind` `FOREIGN_TRADE`, `STABLECOIN_SETTLEMENT`, `TOKENIZED_CONVERT`; `ForeignImportRules`; perna em stablecoin nas `CryptoImportRules`; `BinanceClient.signedPostRead` (lista fechada: `get-funding-asset`); `BinanceConnector` com fluxos `equity` e `tokenized`, saldo `equity:<ticker>` e conta sem o produto (4xx) sem derrubar a busca; `importTransaction` aceita os tipos do exterior; `ProposedTransaction.currencyCode` e `currency` na pré-visualização; `binance.equity-history-start: 2026-05-01`. Testes: `ForeignImportRulesTest` 8, `BinanceConnectorTest` +3, `SourceDescriptorsTest` 2, `IntegrationsStocksIT` 2 |
| E5 | ✅ fechada (conferida no navegador do app, temas escuro e claro, 31/10/2026) | `ConnectSourceModal` (um formulário por credencial) + `sourceHelp.tsx` (o único ponto por fonte) + `SourceLogo`; `ConnectBinanceModal`/`ConnectB3Modal` apagados; catálogo "Escolha a integração" sempre aberto em "Conectar", com logo, categoria, descrição e "Indisponível no momento"; `useIntegrationSources`; `providerName(p, fontes)`; pré-visualização com valores em US$ no ativo do exterior (`formatProposalMoney`); "Depósitos em reais" e "só data" por capacidade; `DivergenceList` genérica. `tsc`, `eslint --max-warnings=0` e Vitest (154, `integrations.test.ts` 4) verdes |
| E6 | ✅ fechada | Guilherme recadastrou a chave pela tela nova e sincronizou. A venda de 122,20 USDC passava do USDC conhecido (122,15 do Convert): resíduo de 0,05814558 USDC anterior à ordem, sem origem nas APIs (PENDENCIAS 43.3) — por decisão dele, lançado como compra de USDC com real em 12/10/2026 (a R$ 5,03688, a cotação do Convert). Confirmadas 10 importadas: 7 compras de BTC, Convert BRL→USDC 122,15, IAU 1,55913017 a US$ 78,26799991 com taxa US$ 0,17 e PTAX 5,0096 (R$ 611,32 + R$ 0,85), venda de 122,20 USDC a R$ 5,0096. Saldos batem com a Binance (BTC 0,00831073, USDC 0,00814558, IAU 1,55913017 contra 1,559130168): sem divergência |
| E7 | ✅ fechada | `mvn clean verify` (31/10/2026, 9 min 55 s): core 255; integration 116; adm-api 155 + 99; app-api 237 + 311 — todos verdes; front `tsc`, `eslint --max-warnings=0`, Vitest 154. Docs: `Claude.md` (revisão, rotas, estrutura, Pontos de Atenção, contagem), `README.md`, `PENDENCIAS` (seção 43; 17.1, 35.7, 37.5 resolvidos; 38.1 e 39.6 atualizados) |

**Review (31/10/2026):** sem pendência de correção. Conferido: o único POST da Binance é a lista fechada `READ_ONLY_POST`; restaurar uma importada do exterior regrava pelo câmbio (`applyCurrency`); B3 e cripto seguem pela regra principal (ITs sem mudança de resultado); conta sem Binance Stocks não derruba a busca.

**Commit sugerido (etapa 7):** `TRACK_01 - Integrações genéricas: catálogo de fontes com logo e formulário por credencial, regra de importação por movimento com câmbio e Binance Stocks (ações e ETFs dos EUA em USD pela PTAX, venda da stablecoin e saldo da Funding na divergência)`
