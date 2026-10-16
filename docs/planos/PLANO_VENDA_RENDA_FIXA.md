# PrimeVest — Plano de Venda de Renda Fixa (lotes, regra de saída, IR e IOF)

> Fase: **planejamento** (criado em 15/10/2026; D1–D7 respondidas no mesmo dia, todas pela recomendação). Nada
> implementado. Implementação só com ordem explícita (REGRAS_CLAUDE.md 2.2).
> Origem: ESCOPO "Venda de Renda Fixa para o Primevest", levantamento de 09–15/10/2026 (seção 9). Mexe na
> liquidação automática do `docs/planos/PLANO_NOTIFICACOES.md` (seção 6, que sai) e volta a D8 do
> `docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md` para "parar e avisar". Pendências ligadas: `docs/PENDENCIAS.md` 26.7,
> 26.8 e seção 27.

---

## 0. Resumo

Hoje a renda fixa privada é um **saldo único** por título: aportes somam, resgates subtraem, o saldo rende pela curva e
o valor digitado na venda sai do saldo como se fosse bruto. Não há lotes, nem IR, nem IOF, e o "aplicado" nunca cai.

Com este plano:

| | O usuário informa | O PrimeVest calcula |
|---|---|---|
| **Compra** RF e TD | valor bruto (como hoje) | — |
| **Venda RF** (CDB, RDB, LCI…) | o **líquido** que recebeu (+ "liquidação total", se for) | bruto, IR e IOF por **lote**, pela regra de saída |
| **Venda TD** | cotas e PU (bruto, como hoje) | IR e IOF por lote do PEPS, e o líquido |

- Cada aporte vira um **lote** (calculado das transações, nada gravado): data, aplicado, rendimento, dias, alíquota.
- **Regra de saída** dos lotes: PROPORCIONAL (padrão), UEPS ou PEPS. As três são implementadas; TD é PEPS fixo.
- **IR e IOF** saem de uma tabela nova, no estilo da V23 (faixa de dias, vigência, base legal), com as isenções.
- **Liquidação total** zera o título nos dois sentidos; a diferença para o estimado entra no bruto ("ajuste da
  liquidação").
- **Vencimento**: o sistema deixa de liquidar sozinho; o título para de render e a rotina **avisa**.

**Fora deste escopo:** taxa de custódia da B3 e taxas no custo de aquisição; IR de ações, FII e cripto; a integração
B3 de renda fixa (PENDENCIAS 26.7); a troca de distribuidora (PENDENCIAS 27.1, escopo de emissoras); o escopo de
Rentabilidade (que vai **usar** o líquido daqui).

---

## 1. O que existe (conferido no código em 15/10/2026)

| Peça | Onde | Hoje | Neste plano |
|---|---|---|---|
| `FixedIncomeCurve` | core | saldo único: aportes e resgates no dia, fator diário por forma de remuneração; D4 (resgate acima do saldo → R$ 0,01); vencimento para de render | continua dando o **fator do dia**; o saldo passa a ser a soma dos lotes |
| `FixedIncomeSettlement.recompute` | core | regras **E, F, G** e **J**; `is_full_settlement` → valorização = resgatado − aplicado (já fecha nos dois sentidos); resgate parcial que alcança o saldo → centavo da F | regras mantidas; "resgatado" passa a ser a soma dos **brutos calculados** |
| `PositionCalculator` | core | RF: custo = soma das compras; `totalSold` à parte | ganha aplicado **restante** e rendimento por lote (via seção 4) |
| `transactions.is_full_settlement` | V1 / `Transaction.fullSettlement` | a marca **"liquidação total"** já existe na venda de RF (DTO `isFullSettlement`) | é o indicador "Resgate total" do levantamento — nada novo a criar |
| `TreasuryAllocator` | core | venda do TD por FIFO entre os títulos; `total_amount` = cotas × PU (bruto); taxa da corretora rateada | ganha IR e IOF por parte (lote) |
| `MaturitySettlementService` | app-api | liquida o vencido com venda de origem SISTEMA; refaz a venda não editada; "compra retroativa nasce liquidada" | **vira só aviso** (seção 6) |
| `NotificationKind` | core | `RF_LIQUIDADA`, `RF_LIQUIDACAO_SEM_VALOR`, `RF_LIQUIDACAO_DIVERGENTE` | entra `RF_VENCIDO`; as três antigas param de ser emitidas (ficam no enum pelas mensagens já gravadas) |
| `withholding_tax_rules` (V23) | core | IR dos proventos por tipo de provento e vigência | **modelo** da tabela nova (seção 3.1); não é alterada |
| `transactionPreset.ts` | web | abre o formulário de transação preenchido | usado no link da notificação de vencido |
| Emissora/distribuidora | — | **não existe ainda** (escopo de emissoras com levantamento fechado, sem plano); `fixed_income_notes.issuer` é texto livre | a regra de saída "na distribuidora" depende dele — **D1** |

---

## 2. Regras do escopo

| # | Regra |
|---|---|
| R1 | **Lote** = um aporte (compra) do título. Os lotes são **calculados** da cronologia a cada passagem, nunca gravados. Todos rendem pelo mesmo fator do dia (é o mesmo título), então a soma dos lotes é o saldo da curva de hoje |
| R2 | **Compra** é lançada pelo bruto (sem imposto na entrada) |
| R3 | **Venda RF** é lançada pelo **líquido recebido**. O bruto é calculado: `total_amount` = bruto, `net_amount` = líquido digitado, mais `income_tax` (IR) e `iof_tax` (IOF). O que o usuário digitou é a fonte; o bruto é derivado e regravado a cada recálculo |
| R4 | **Venda TD** continua por cotas × PU (`total_amount` = bruto). IR e IOF por parte (lote do PEPS); `net_amount` = bruto − IR − IOF − taxa da corretora |
| R5 | **Regra de saída** dos lotes: `PROPORCIONAL` (cada lote cede a mesma fração do seu bruto), `UEPS` (mais novo primeiro), `PEPS` (mais antigo primeiro). Padrão PROPORCIONAL; TD sempre PEPS |
| R6 | **IOF** = rendimento do lote × % da tabela pelos **dias corridos** desde o aporte (1 a 29; zero a partir do 30º). **IR** = (rendimento − IOF) × alíquota pelos dias corridos (até 180: 22,5%; 181–360: 20%; 361–720: 17,5%; acima: 15%). Isenções por produto (seção 3.1) |
| R7 | **Gross-up** (líquido → bruto): PROPORCIONAL tem fórmula fechada, `bruto = líquido ÷ (1 − t)`, com `t = Σ(IOF + IR dos lotes) ÷ Σ(bruto dos lotes)` na data; UEPS/PEPS percorrem os lotes na ordem, cada lote cedendo até `bruto_lote × (1 − t_lote)` de líquido |
| R8 | **Liquidação total**: todos os lotes saem; `bruto = líquido ÷ (1 − t)` com o `t` do título inteiro na data. A diferença entre esse bruto e o saldo da curva é o **ajuste da liquidação** (gravado, exibido). Valorização = resgatado − aplicado (regra já existente); bruto abaixo do aplicado continua recusado (regra G) |
| R9 | **Venda parcial acima do líquido disponível estimado**: **alerta, não bloqueia** — "Valor acima do disponível estimado (R$ X). É o resgate total?" → [Sim, resgate total] / [Não, lançar mesmo assim]. No "lançar mesmo assim" vale a D4 atual: o saldo cai para R$ 0,01 e segue rendendo |
| R10 | **Edição/exclusão retroativa** (compra ou venda) recalcula a cronologia inteira: os brutos, IR e IOF das vendas posteriores são refeitos a partir do líquido digitado. As regras E, F e G continuam recusando o que não fechar |
| R11 | **Vencimento**: o título para de render no vencimento (D8 original) e fica "vencido, aguardando liquidação". O sistema **não cria venda**; a rotina de abertura do dia avisa (seção 6) |
| R12 | Precisão: aceitar diferença de centavos contra o extrato (D12 da valorização) |

---

## 3. Banco (migrations)

### 3.1 V52 — tabela de IR e IOF da renda fixa

Mesma filosofia da V23: **o que a lei muda fica no banco; o jeito de calcular fica no código**. Lei nova = migration
que fecha a vigência e insere a linha nova.

```sql
CREATE TABLE fixed_income_tax_rules (
    id           BIGSERIAL     PRIMARY KEY,
    tax          VARCHAR(3)    NOT NULL,          -- IR | IOF
    asset_id     BIGINT,                          -- nulo = vale para todo produto de RF e TD; preenchido = exceção do produto
    days_from    INTEGER       NOT NULL,          -- dias corridos desde o aporte, inclusive
    days_to      INTEGER,                         -- inclusive; nulo = sem teto
    rate         NUMERIC(6,4)  NOT NULL,          -- 0,2250 = 22,5%; zero = isento
    valid_from   DATE,                            -- nulo = desde sempre
    valid_to     DATE,                            -- inclusive; nulo = em vigor
    legal_basis  VARCHAR(200)  NOT NULL,
    CONSTRAINT fk_fi_tax_rules_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
    CONSTRAINT ck_fi_tax_rules_tax CHECK (tax IN ('IR', 'IOF')),
    CONSTRAINT ck_fi_tax_rules_days CHECK (days_from >= 0 AND (days_to IS NULL OR days_to >= days_from)),
    CONSTRAINT ck_fi_tax_rules_rate CHECK (rate >= 0 AND rate <= 1),
    CONSTRAINT ck_fi_tax_rules_validity CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_from <= valid_to)
);
```

Carga inicial — **base legal conferida em 15/10/2026** (seção 3.1.1):

| Imposto | Produto | Faixas | Base legal |
|---|---|---|---|
| IR | todos (asset_id nulo) — vale também para o TD | 0–180: 22,5% · 181–360: 20% · 361–720: 17,5% · 721+: 15% | Lei 11.033/2004, art. 1º, I a IV; IN RFB 1.585/2015, art. 46 (prazo contado da data de cada aplicação, §9º) |
| IOF | todos (asset_id nulo) — vale também para o TD | dia 1: 96% · 2: 93% · 3: 90% · 4: 86% · 5: 83% · 6: 80% · 7: 76% · 8: 73% · 9: 70% · 10: 66% · 11: 63% · 12: 60% · 13: 56% · 14: 53% · 15: 50% · 16: 46% · 17: 43% · 18: 40% · 19: 36% · 20: 33% · 21: 30% · 22: 26% · 23: 23% · 24: 20% · 25: 16% · 26: 13% · 27: 10% · 28: 6% · 29: 3% · 30+: 0% | Decreto 6.306/2007, art. 32, caput e § 1º, I (redação do Decreto 7.487/2011), e Anexo |
| IR | LCI, CRI | 0+: 0% | Lei 11.033/2004, art. 3º, II |
| IR | LCA, CRA | 0+: 0% | Lei 11.033/2004, art. 3º, IV (incluído pela Lei 11.311/2006) |
| IR | DEBENTURE_INCENTIVADA | 0+: 0% | Lei 12.431/2011, art. 2º, caput, I (pessoa física: 0%) |
| IR | LIG | 0+: 0% | Lei 13.097/2015, art. 90, I (pessoa física residente no país) |
| IR | POUPANCA | 0+: 0% | Lei 8.981/1995, art. 68, III |
| IOF | LCA, CRA | 0+: 0% | Decreto 6.306/2007, art. 32, § 2º, V (incluído pelo Decreto 7.487/2011) |
| IOF | CRI, DEBENTURE, DEBENTURE_INCENTIVADA, LF | 0+: 0% | Decreto 6.306/2007, art. 32, § 2º, VI (incluído pelo Decreto 7.487/2011) |
| IOF | POUPANCA | 0+: 0% | não incidência — depósito de poupança não é título ou valor mobiliário (fora do art. 32) |

#### 3.1.1 Conferência da base legal (15/10/2026)

| Ponto | Resultado | Fonte |
|---|---|---|
| Tabela regressiva de IR | vigente, sem alteração; as notas de 2025 no texto apontam a MP 1.303/2025 com "vigência encerrada" | Planalto, Lei 11.033/2004 |
| MP 1.303/2025 (tributava LCI/LCA, unificava a alíquota) | **perdeu a vigência em 08/10/2025** (retirada de pauta na Câmara); nada mudou na tributação — LCI, LCA, CRI e CRA seguem isentas para PF | Planalto (notas "vigência encerrada"); B3 Bora Investir, 08/10/2025 |
| Isenção LCI/CRI e LCA/CRA | art. 3º, II e IV da Lei 11.033/2004 | Planalto |
| Debênture incentivada | 0% para PF no art. 2º, I da Lei 12.431/2011; benefício para emissões até 31/12/2030 (§1º, redação da Lei 13.043/2014) | Planalto |
| LIG | **conferido**: isenção no art. 90, I da Lei 13.097/2015 (antes "artigo a conferir") | texto da Lei 13.097/2015 (ANTTlegis) |
| Poupança — IR | isenta pelo art. 68, III da Lei 8.981/1995 (a MP 1.303 o revogaria a partir de 01/01/2026, mas caducou) | LegJur (texto e notas) |
| IOF — tabela e alcance | 1% ao dia limitado ao rendimento, pela tabela do Anexo; vale para renda fixa (inclusive títulos públicos — TD) | Decreto 6.306/2007, art. 32 (texto e notas de redação via Vade Mecum; a tabela via referência técnica) |
| IOF — alíquota zero | **achado novo**: LCA, CRA e CDCA (§2º, V); debêntures, CRI e Letra Financeira (§2º, VI). **LCI e LIG não estão na lista** — pagam IOF se resgatadas antes de 30 dias (na prática raro, pelas carências mínimas) | Decreto 6.306/2007, art. 32, § 2º |
| IOF — poupança | não é título/valor mobiliário, então fica fora do IOF-Títulos; **conferido só em fonte secundária** — registrar como não incidência, não como alíquota zero | — |
| Tabela de IOF na prática | o extrato do Nubank de 25/07/2026 traz IOF de R$ 10,61 no aporte de 08/07 (17 dias → 43%), o que bate com o modelo do caso de conferência | seção 10 |

Corrigido em relação à versão anterior deste plano: o IOF **não** vale para todos os produtos — LCA, CRA, CRI, debêntures
(comuns e incentivadas) e LF têm alíquota zero.

Precedência: linha do produto (`asset_id`) vence a genérica do mesmo imposto. Teste de repositório confere que não há
sobreposição de faixa/vigência para o mesmo imposto e produto (como o `WithholdingTaxRuleRepositoryTest`).

### 3.2 V53 — valores da venda na transação

```sql
ALTER TABLE transactions ADD COLUMN net_amount            NUMERIC(18,2);  -- líquido: digitado (RF) ou calculado (TD)
ALTER TABLE transactions ADD COLUMN income_tax            NUMERIC(18,2);  -- IR da venda (soma dos lotes)
ALTER TABLE transactions ADD COLUMN iof_tax               NUMERIC(18,2);  -- IOF da venda (soma dos lotes)
ALTER TABLE transactions ADD COLUMN settlement_adjustment NUMERIC(18,2);  -- só na liquidação total: bruto − saldo da curva
-- Os três primeiros andam juntos: venda de RF/TD tem os três; compra e renda variável, nenhum.
```

Colunas **nulas** (só valem para venda de RF/TD), então a REGRAS 8.1 (NOT NULL com default) não se aplica a elas. O
que fazer com as **vendas já gravadas** é a **D2**.

---

## 4. Cálculo (core, regras puras)

### 4.1 `FixedIncomeTaxTable` + `FixedIncomeTaxCalculator`

- Carrega `fixed_income_tax_rules` uma vez por rodada (como as séries da `FixedIncomeValuation`).
- `taxes(assetId, aporte, resgate, rendimento)` → IOF e IR do lote, pela R6 e pela vigência na **data do resgate**.
- Arredondamento: **D3**.

### 4.2 `FixedIncomeLots` (substitui o saldo único por dentro da curva)

Repete a cronologia do título aplicando o fator do dia da `FixedIncomeCurve` a **cada lote**:

1. Compra → lote novo (data, aplicado, bruto = aplicado).
2. Venda → calcula o bruto pelo líquido (R7/R8) com a regra de saída, tira dos lotes, grava (em memória) bruto, IR,
   IOF e ajuste da venda.
3. Venda parcial acima do disponível já gravada ("lançar mesmo assim") → D4: lotes reduzidos ao centavo da regra F.
4. Vencimento → para de render (D8).

Saída: lotes abertos (data, dias, aplicado restante, bruto, rendimento, % IOF, alíquota IR, IOF, IR, líquido), saldo
bruto, **líquido estimado** e, para cada venda, bruto/IR/IOF/ajuste. A soma dos brutos tem que bater com o saldo único
de hoje (teste de regressão contra os casos do plano de valorização).

### 4.3 `SaleRule` (enum) e a origem da regra

`PROPORCIONAL | UEPS | PEPS`. Enquanto a distribuidora não existir, de onde a RF tira a regra é a **D1**. TD não lê
regra: é PEPS pelo `TreasuryAllocator`.

### 4.4 Tesouro Direto

O `TreasuryAllocator` já reparte a venda em partes por título (PEPS). Cada parte ganha:
`rendimento = (PU venda − PU compra) × cotas`; IOF e IR pela R6 com a data do título; `net_amount` = bruto − IR − IOF
− taxa da corretora. Rendimento negativo numa parte → imposto zero nela (sem compensação entre partes: a decidir na
implementação se a Receita compensa dentro da mesma venda — anotar em achados).

---

## 5. Aplicação (app-api)

| Peça | Mudança |
|---|---|
| `TransactionCreateDTO` | campo novo `netAmount` (venda RF); `totalAmount` deixa de ser aceito na venda RF (vira calculado) |
| `TransactionService` (venda RF) | grava `net_amount` digitado; roda os lotes; grava `total_amount` (bruto), IR, IOF e ajuste; R9 é do front (o back aceita e aplica a D4) |
| Recálculo em cascata (R10) | qualquer escrita no título refaz os lotes e **regrava** bruto/IR/IOF/ajuste das vendas posteriores; depois `FixedIncomeSettlement.recompute` (E, F, G) e `applyCurve` |
| `TreasuryAllocator` / venda TD | grava IR, IOF e `net_amount` em cada parte |
| Endpoint novo **prévia da venda** | `POST /api/v1/transactions/sale-preview` com o mesmo corpo da venda → `{ bruto, ir, iof, liquido, disponivelLiquido, saldoBruto, regra, lotes[] }`, sem gravar. O formulário usa para mostrar os números e disparar o alerta da R9 |
| Detalhe da posição (`PortfolioAssetDetailDTO`) | lotes abertos (seção 4.2) e líquido estimado |
| Carteira (DTO da linha de RF/TD) | `liquidoEstimado` ao lado do valor atual (bruto) |

---

## 6. Vencimento: sai a liquidação automática (revoga a seção 6 do PLANO_NOTIFICACOES)

| Ponto | Como fica |
|---|---|
| `MaturitySettlementService` | não cria nem refaz venda; não ouve mais a compra para "nascer liquidada". Na rotina de abertura (e quando chega índice/PU) ele só **avisa** |
| Mensagem | `RF_VENCIDO` — pendência por título (RF) ou por ativo da carteira (TD): "**[título] — vencido em dd/mm, lance a liquidação**", com o **líquido estimado** no corpo (ou "sem curva, informe o valor recebido" quando não há curva — absorve o caso do `RF_LIQUIDACAO_SEM_VALOR`) |
| Resolução | pelo sistema (R6 das notificações) quando a posição fica liquidada: RF com venda de liquidação total; TD sem cotas em aberto |
| Link | "Lançar liquidação" abre o formulário preenchido (`transactionPreset`): venda, título, data do vencimento, liquidação total marcada e líquido estimado (RF) ou cotas e PU oficial (TD) — **D6** |
| Carteira | o selo **"Vencido"** (`PositionValuationInfo.matured`, `PortfolioPage`) continua; muda a dica dele, que hoje diz "o PrimeVest lança o resgate sozinho na abertura do dia", para "lance a liquidação (veja as Notificações)" |
| Vendas automáticas já gravadas (origem SISTEMA, desde 13/10) | ficam; o Guilherme corrige à mão (decidido no levantamento). A origem SISTEMA continua no banco por causa delas |
| Mensagens antigas | `RF_LIQUIDADA` (informativas) ficam; `RF_LIQUIDACAO_DIVERGENTE` abertas — **D7** |

---

## 7. Telas (web)

| Tela | O que muda |
|---|---|
| **Formulário de transação — venda RF** | campo "Valor recebido (líquido)"; "Liquidação total" (já existe); painel de prévia: bruto, IOF, IR, disponível líquido estimado e a regra de saída usada; alerta da R9 |
| **Formulário — venda TD** | cotas e PU como hoje; painel de prévia com IOF, IR e líquido |
| **Transações (lista)** | venda RF/TD mostra bruto e líquido — **D4** |
| **Carteira** | RF/TD: valor atual (bruto) e líquido estimado — **D5**; dica do selo "Vencido" (seção 6); o texto da base da valorização ("Valor bruto, sem IR e IOF") passa a citar o líquido estimado |
| **Detalhe da posição** | tabela de lotes: data do aporte, dias, aplicado restante, rendimento, % IOF, alíquota IR, IR+IOF estimados, líquido |
| **Rentabilidade e Metas** (escopos futuros) | usam o **líquido** — registrado aqui para quando esses escopos forem planejados |

---

## 8. Etapas

| Etapa | Conteúdo |
|---|---|
| **V1** | V52 + `FixedIncomeTaxTable`/`FixedIncomeTaxCalculator`. Testes: faixas de IR e IOF nos limites (180/181, 360/361, 720/721, dia 29/30), isenções, vigência, IOF antes do IR, sobreposição proibida |
| **V2** | `FixedIncomeLots` + `SaleRule` (as três regras), gross-up (R7), liquidação total (R8), D4. Testes: casos à mão; **caso Nubank** (seção 10) em UEPS no centavo e em PROPORCIONAL; regressão: soma dos lotes = saldo único da curva atual nos casos do plano de valorização |
| **V3** | V53 + venda RF por líquido no `TransactionService`, recálculo em cascata (R10), endpoint de prévia, DTOs da carteira e do detalhe. Testes de serviço e funcionais (TransacoesIT) com edição retroativa e as regras E/F/G |
| **V4** | Tesouro: IR/IOF por parte no `TreasuryAllocator`, `net_amount`, prévia. Testes com venda que consome dois títulos de idades diferentes |
| **V5** | Vencimento (seção 6): `MaturitySettlementService` só avisa, `RF_VENCIDO`, resolução, dica do selo, D7 |
| **V6** | Telas (seção 7) + conferência no navegador (REGRAS 4.2) |
| **V7** | Documentação (Claude.md, PENDENCIAS 26.8 e 27, andamento deste plano; nota de revisão na seção 6 do PLANO_NOTIFICACOES e na D8 do PLANO_RENDA_FIXA_VALORIZACAO) e validação final |

### Quem faz

| O quê | Claude | Guilherme |
|---|---|---|
| Responder D1–D7 | | ✔ |
| V1–V7 (código, migrations, testes, docs) | ✔ | |
| Conferir a base legal das linhas da V52 | ✔ (pesquisa — feita em 15/10, seção 3.1.1) | ✔ (aceite) |
| Corrigir as vendas automáticas criadas desde 13/10 | | ✔ (à mão) |
| Validação final (compilar, testes, lint, subir, navegador) | ✔ | confere no app |

---

## 9. Decisões tomadas no levantamento (09–15/10/2026)

| # | Decisão |
|---|---|
| L1 | A regra de saída não é universal: Tesouro é PEPS; o Nubank (RDB, Caixinha Turbo) é **UEPS**, provado no centavo contra o extrato diário (10/10); demais instituições sem prova |
| L2 | Padrão **PROPORCIONAL**: dentro de um título ele fica sempre entre PEPS e UEPS (o erro máximo é menor que o de escolher a regra errada) |
| L3 | Compra pelo bruto; venda pelo líquido que o usuário recebeu; o PrimeVest calcula bruto, IR e IOF. IR e IOF da renda fixa e do TD **entram no escopo** (ações e cripto continuam fora) |
| L4 | `total_amount` = bruto; `net_amount` = líquido digitado, mais IR e IOF (como em `dividends`) |
| L5 | A base do cálculo é o bruto; a diferença da liquidação total entra no bruto como "ajuste da liquidação" |
| L6 | Venda parcial acima do disponível: **alerta**, não bloqueio (R9) |
| L7 | D4 nos dois sentidos pela marca de liquidação total |
| L8 | Edição/exclusão retroativa recalcula a cronologia inteira |
| L9 | Liquidação automática **sai**: título vencido só gera aviso; as vendas automáticas já criadas o Guilherme corrige à mão |
| L10 | Tabela própria de IR e IOF no estilo da V23; isenções como linha de alíquota zero |
| L11 | TD condizente: continua por cotas × PU (bruto) e passa a calcular IR, IOF e líquido |
| L12 | As três regras implementadas; atributo na **distribuidora** (padrão PROPORCIONAL, Nubank UEPS, TD PEPS fixo) — registrar sem usar foi descartado |
| L13 | Telas: carteira com bruto e líquido estimado; detalhe com lotes; venda com bruto, IOF, IR e líquido; Rentabilidade e Metas pelo líquido |

---

## 10. Casos de conferência

**Nubank — Caixinha Turbo 120% do CDI (RDB, Nu Financeira), extrato diário de 25/07/2025 a 27/07/2026.**

Aportes: 25/07/2025 R$ 543,85 · 29/12/2025 R$ 9.456,15 · 14/05/2026 R$ 2.000,00 · 08/07/2026 R$ 3.000,00.
Resgates líquidos: 09/02/2026 R$ 8.000,00 · 01/07/2026 R$ 3.000,00. Liquidação no vencimento: R$ 4.356,61.

| Regra | Resgate 09/02: bruto / IR | Resgate 01/07: bruto / IR | Em 25/07/2026: bruto / IR+IOF |
|---|---|---|---|
| **UEPS** (real) | 8.034,34 / 34,34 | 3.024,93 / 24,93 | 4.399,85 / 43,83 (extrato: 4.399,86 / 43,82) |
| PROPORCIONAL | 8.040,53 / 40,53 | 3.033,14 / 33,14 | 4.384,88 / 31,56 |
| PEPS | 8.042,22 / 42,22 | 3.039,80 / 39,80 | 4.376,33 / 23,47 |

O resgate de 01/07 inclui o rendimento do próprio dia: o bruto real é 3.024,93 (o 3.022,17 citado no levantamento
comparava o extrato de 30/06 com o de 01/07 sem esse dia). Valor investido no extrato depois dos resgates (UEPS):
2.118,29 e 1.212,71. O teste da V2 usa o extrato como gabarito com tolerância de centavos (R12).

---

## 11. Decisões D1–D7 — **tomadas em 15/10/2026: todas pela recomendação**

Resumo do que vale: **D1 (b)** — este escopo vai antes do de emissoras; as três regras ficam prontas e testadas e a RF
usa PROPORCIONAL até `fixed_income_issuers.sale_rule` existir (Nubank UEPS entra com as emissoras). **D2 (a)** — a V53
copia `net_amount = total_amount` nas vendas de RF já gravadas e o recálculo refaz o bruto. **D3** — IR e IOF truncados
nos centavos. **D4 (a)** — lista com o líquido; bruto/IR/IOF no detalhe da linha. **D5 (a)** — líquido estimado na
segunda linha sob o valor atual. **D6** — link do vencido abre o formulário preenchido. **D7** — a primeira rodada
resolve as `RF_LIQUIDACAO_DIVERGENTE` abertas.

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D1** | A distribuidora (onde mora a regra de saída) ainda não existe — o escopo de emissoras tem levantamento fechado, mas não tem plano nem código | **(a)** fazer o escopo de emissoras antes deste; **(b)** fazer este agora com as três regras implementadas e testadas, RF lendo PROPORCIONAL até a distribuidora existir — o escopo de emissoras cria `fixed_income_issuers.sale_rule` (padrão PROPORCIONAL, Nubank UEPS) e liga; **(c)** coluna provisória `sale_rule` no título, escolhida pelo usuário, migrada depois | **(b)** — este escopo não depende de emissoras para nada além de **onde ler** a regra, e o cálculo fica pronto e provado; o Nubank passa a bater exato quando as emissoras entrarem |
| **D2** | Vendas de RF **já gravadas** (antes deste escopo): o valor digitado foi tratado como bruto | **(a)** a V53 copia `net_amount = total_amount` em todas as vendas de RF (o usuário sempre digitou o que recebeu) e o primeiro recálculo refaz o bruto; **(b)** ficam como "venda antiga" (sem líquido, bruto = digitado, sem imposto) até serem editadas | **(a)** — é uma regra fixa por tipo, não dedução linha a linha (REGRAS 8.1); deixa a base inteira num só modelo. As vendas SISTEMA desde 13/10 você corrige à mão de qualquer jeito |
| **D3** | Arredondamento de IR e IOF por lote | truncar nos centavos (como a V23 faz com os proventos, imitando a B3) / arredondar meio para cima | **truncar** — mesma convenção do IR dos proventos; diferença de centavos aceita (R12) |
| **D4** | Lista de transações: valor da venda RF/TD | **(a)** uma coluna com o líquido e o bruto/IR/IOF no detalhe da linha; **(b)** duas colunas, bruto e líquido | **(a)** — não alarga a tabela, e na compra bruto = líquido (a coluna extra ficaria repetida na maioria das linhas) |
| **D5** | Carteira: onde fica o líquido estimado da RF/TD | **(a)** segunda linha sob o valor atual; **(b)** coluna nova | **(a)** — só RF e TD têm líquido diferente do bruto; uma coluna nova ficaria vazia nas outras abas |
| **D6** | Link "Lançar liquidação" da notificação de vencido | abre o formulário **preenchido** (data, liquidação total, líquido estimado / cotas e PU oficial) / só abre a tela de transações | **preenchido** — o usuário só confere com o extrato e corrige o líquido |
| **D7** | Mensagens `RF_LIQUIDACAO_DIVERGENTE` ainda abertas | a primeira rodada da rotina nova as **resolve** (o assunto — conferir a venda automática contra a curva — deixou de existir) / ficam abertas até você dispensar | **resolver** — é o sistema dizendo que o assunto acabou (R6 das notificações); as vendas em si você corrige à mão |

---

## 12. Riscos e achados

- **A curva bate no centavo; o imposto é que erra.** O erro do PROPORCIONAL num título UEPS é limitado pela diferença
  de alíquota entre os lotes × rendimento (no Nubank, R$ 6 a R$ 8 por resgate). A liquidação total zera o erro.
- **Cascata de recálculo**: editar uma compra antiga muda o bruto de todas as vendas seguintes. O teste funcional
  precisa cobrir edição de compra que faz uma venda posterior passar do saldo (regra E recusando).
- **Base legal**: conferida em 15/10/2026 (seção 3.1.1). Única ressalva: a não incidência de IOF na poupança só foi
  confirmada em fonte secundária. Lei nova muda a tabela por migration (fecha a vigência, insere a linha nova).
- **TD com parte de rendimento negativo** (venda abaixo do PU de compra num título e acima no outro): conferir na V4
  se o IR compensa entre as partes da mesma venda.
- **Integração B3 de RF** (PENDENCIAS 26.7): quando importar resgate/vencimento, tem que casar com a venda lançada
  ou com o título vencido aguardando liquidação — fora deste escopo.

---

## 13. Andamento

- 15/10/2026 — plano criado a partir do levantamento (seções 9 e 10).
- 15/10/2026 — D1–D7 respondidas pelo Guilherme, todas pela recomendação (seção 11).
- 15/10/2026 — base legal da V52 conferida no planejamento (seção 3.1.1); achado: IOF com alíquota zero para LCA, CRA,
  CRI, debêntures e LF.
- 15/10/2026 — Guilherme aceitou a tabela da seção 3.1 e mandou iniciar a implementação.
- 15/10/2026 — V1–V7 implementadas:
  - **V1** V52 (48 linhas) + `FixedIncomeTaxTable`/`FixedIncomeTaxCalculator` (core) e testes de faixas, isenções e
    sobreposição.
  - **V2** `FixedIncomeLots` + `SaleRule`: as três regras, gross-up, liquidação total com ajuste, D4. Caso Nubank em
    UEPS no centavo; PROPORCIONAL e PEPS entre as duas; regressão contra a curva de saldo único.
  - **V3** V53; venda RF pelo líquido no `TransactionService`; cascata (R10) por `FixedIncomeValuation.syncSales` antes
    das regras E/F/G; `POST /transactions/sale-preview`; `estimatedNet` e `lots` no `PortfolioAssetDetailDTO`.
    **Desvio da seção 5:** o `TransactionCreateDTO` não ganhou `netAmount` — na venda RF o próprio `totalAmount` é o
    líquido (evita mexer nos 26 pontos que constroem o DTO); a resposta traz `totalAmount` (bruto) e `netAmount`.
  - **V4** Tesouro: IR, IOF e `net_amount` por parte no `TreasuryAllocator`, prévia, completar as vendas antigas na
    subida (`fillMissingTaxes`). Rendimento negativo numa parte = imposto zero, sem compensação (PENDENCIAS 27.3).
  - **V5** `MaturitySettlementService` só avisa (`RF_VENCIDO`), resolve sozinho quando a posição liquida, D7 na
    rodada; dica do selo "Vencido" e do selo "Automática".
  - **V6** formulário (valor líquido, "Resgatar tudo" pelo líquido estimado, painel da prévia, pergunta da R9), lista
    de transações (líquido), Carteira (linha "líq." e os aportes num modal), link D6
    (`/transactions?settle=…`).
  - **V7** Claude.md, PENDENCIAS (26.8; 27.3–27.5 novos), nota de revisão na seção 6 do PLANO_NOTIFICACOES e na D8 do
    PLANO_RENDA_FIXA_VALORIZACAO.
- 15/10/2026 — PENDENCIAS 27.3 conferida (IN RFB 1.585/2015) e 27.4 respondida (tarifa da B3).
- 15/10/2026 — Guilherme corrigiu o pedido do (i): o desnecessário era o (i) do VALOR ATUAL da Carteira na RF/TD
  (retirado). Na lista de Transações, a venda RF/TD mostra o bruto e, em segunda linha, "líq.", como a Carteira (D4
  revisto, sem (i)).
- 15/10/2026 — PENDENCIAS 27.5 resolvida a pedido do Guilherme: venda parcial acima do líquido disponível lançada
  "mesmo assim" tributa o excesso — gross-up pelas alíquotas dos lotes como na R8 (`FixedIncomeLots.overflow`),
  `settlement_adjustment` = bruto − saldo da curva; o gross-up passou a incluir também os lotes ainda sem rendimento
  (R8 e D4).
