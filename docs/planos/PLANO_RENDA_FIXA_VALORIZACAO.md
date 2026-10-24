# PrimeVest — Plano da Valorização da Renda Fixa

> **Nota (23/10/2026):** escrito quando o projeto se chamava **PrimeVest**. Nomes de módulo (`primevest-*`), pacote
> (`com.primevest`), configuração (`primevest.*`, `PRIMEVEST_*`), contêineres e versões de migration (V1–V67) citados aqui
> são os do repositório `primevest`; no Activest viraram `activest-*`, `com.activest`, `activest.*`/`ACTIVEST_*` e as
> migrations consolidadas V1–V6 (`docs/planos/PLANO_RENOMEACAO_ACTIVEST.md`).

> Fase: **implementado em 05/10/2026** (R1–R7) — andamento na seção 12. Criado em 05/10/2026; decisões D1–D14 tomadas no
> mesmo dia (todas as recomendações) e execução autorizada em seguida.
> Origem: `docs/PENDENCIAS.md` 2.1 (valorização de renda fixa dá sempre zero) e 2.2, com os itens ligados 4.1/4.2
> (venda de renda fixa pela tela), 10.2–10.4 (rotina de rentabilidade, vencimento) e 23.1/23.2 (aviso de índice,
> calendário). Base nova: a série dos índices (`docs/planos/PLANO_INDICES_MERCADO.md`).

---

## 0. Resumo

Hoje toda posição de Renda Fixa e de Tesouro Direto vale **custo + zero**: nada grava `portfolio_assets.appreciation`
para quem não tem cotação. O plano separa os dois mundos, porque eles têm fontes diferentes:

| Mundo | Quem | Como passa a valer | Por quê |
|---|---|---|---|
| **Tesouro Direto** | os 130 títulos do catálogo | **preço oficial do Tesouro** (PU diário, marcação a mercado), como uma cotação | o Tesouro publica o preço de cada título todo dia; é o número que o investidor vê no app do Tesouro e na B3 |
| **Renda fixa privada** | CDB, LCI, LCA, debênture… (`RENDA_FIXA`) | **curva contratada**: taxa da nota × índice do período, dia a dia | ninguém publica o preço do CDB que você comprou no banco; o que existe é a taxa contratada |

Resultado: valor atual e rentabilidade da renda fixa na Carteira, no Dashboard e na Alocação; venda de renda fixa
pela tela (seletor do título); vencimento tratado. **Valores brutos** — IR, IOF e taxas continuam fora (regra do
projeto).

---

## 1. O que existe (conferido no código e no banco do DEV em 05/10/2026)

### 1.1 Modelo

| Peça | Estado |
|---|---|
| `fixed_income_notes` | o título do usuário: `issuer`, `maturity_date`, `rate_type` (PREFIXADO, POS_FIXADO, HIBRIDO), `indexer` (CDI, SELIC, IPCA, IGPM, INPC, TR), `indexer_rate` (% a.a. no prefixado, % do índice no pós, spread no híbrido), `purchase_date` (só Tesouro) |
| Renda fixa privada | lançada **por valor** (`isValuedByTotal`): aplicado = soma das compras, resgatado = soma das vendas; vários aportes no mesmo título são possíveis no modelo (cada compra com a sua data) |
| Tesouro | **por cota**: um título por compra (regra H), venda do ativo repartida por FIFO (`TreasuryAllocator`); PU da compra em `price_per_share` |
| `portfolio_assets.appreciation` | gravada; valor atual = aplicado + valorização − resgatado. `AppreciationUpdater` só mexe em quem tem cotação (`asset_types.price_source` ≠ MANUAL); Tesouro e Renda Fixa são `MANUAL` |
| `FixedIncomeSettlement` | único lugar que grava valorização de renda fixa: regras **E** (aplicado + valorização ≥ resgatado), **F** (só se igualam na liquidação total), **G** (valorização nunca negativa), **J** (liquidação recalculada a cada passagem); a venda "prova" quanto a posição valia (3.1.1 e 3.1.2) |
| Índices | `market_index_values` desde 2010 (CDI, Selic, IPCA, IGP-M, INPC, TR…), `MarketIndexCalculator` (produto, % do índice, spread) — rotina das 10:00 |

### 1.2 O que há na carteira do DEV

| Tipo | Títulos | Situação |
|---|---|---|
| CDB (renda fixa privada) | 3 — XP 200% do CDI, Nubank 100% do CDI (2) | **todos liquidados**: a valorização veio do resgate (R$ 1.325,02, R$ 79,86, R$ 93,89) |
| Tesouro | 12 títulos: IPCA+ 2029/2035/2050, Prefixado 2027/2032, Renda+ 2065, Selic 2028 (3) e 2029 | **9 abertos** com valorização zero; 2 Selic 2028 liquidados |

Ou seja: hoje o efeito visível é quase todo do **Tesouro**; a curva da renda fixa privada vale para os próximos CDBs
(e para quem mais usar o sistema).

### 1.3 Fonte do preço do Tesouro (testada pela rede do Guilherme em 05/10/2026)

**Tesouro Transparente — "Preços e taxas históricas dos títulos do Tesouro Direto"** (dados abertos, sem token):
`PrecoTaxaTesouroDireto.csv`, um arquivo só com **todo o histórico desde 2002**, atualizado todo dia útil
(Last-Modified 05/10/2026 10:20 GMT). **13,9 MB, 55 s para baixar.** Colunas:
`Tipo Titulo;Data Vencimento;Data Base;Taxa Compra Manha;Taxa Venda Manha;PU Compra Manha;PU Venda Manha;PU Base Manha`
— ex.: `Tesouro IPCA+;15/05/2029;02/10/2026;7,39;7,51;3952,28;3936,95;3936,95`. Data base = D-1 útil.

---

## 2. Tesouro Direto pelo preço oficial (D1–D3)

O Tesouro passa a ser tratado **como um ativo cotado**, reaproveitando o que a renda variável já tem:

| Peça | O que muda |
|---|---|
| Catálogo (adm-api) | `TreasuryPriceService`: baixa o CSV (cache em `carga/data/tesouro/PrecoTaxaTesouroDireto.csv`, sempre perguntando se mudou — `PublicFileCache`), casa cada linha com o título do catálogo por **tipo + vencimento** (`asset_terms.maturity_date` + prefixo do nome: "Tesouro IPCA+", "Tesouro Renda+ Aposentadoria Extra"…), grava o PU em `asset_price_history` (`source = TESOURO`) e o último em `assets.current_price` |
| Rotina | junto do fechamento ou própria (D3): o CSV sai pela manhã com o D-1; publica "catálogo mudou" (`FECHAMENTO`) com os títulos atualizados |
| `asset_types` | `TESOURO_DIRETO.price_source` = `TESOURO` (migration; o CHECK passa a aceitar o valor) — o `AppreciationUpdater` passa a valorizar sozinho: **cotas × PU − custo**, por título |
| Carga inicial | o CSV inteiro entra no `bootstrap` (histórico desde 2002 ou só desde 2010 — D3) |

Consequências:

- A valorização do Tesouro **pode ficar negativa** (marcação a mercado: um IPCA+ 2050 comprado a 7,09% vale menos se a
  taxa sobe). Isso é real, e é o que o app do Tesouro mostra. A regra G continua valendo só para a renda fixa
  **privada** (que é por curva e nunca cai).
- O FIFO e a regra H não mudam: o preço é do **ativo** (título público), a valorização é de cada título do usuário.
- A taxa da nota (`indexer_rate`) continua a da compra e aparece como informação ("comprado a IPCA + 7,11%").
- **Cupons** (Tesouro IPCA+ e Prefixado *com Juros Semestrais*): o PU cai na data do cupom e o dinheiro pago não
  aparece. Nenhum título da carteira paga cupom hoje (o Renda+ só paga a partir de 2065). Proposta: fora deste escopo
  (pendência registrada), ou cupom como provento (D13).

---

## 3. Renda fixa privada pela curva (D4–D10)

### 3.1 Saldo na curva, aporte a aporte

Cada título é um fluxo: compras somam, resgates subtraem, e o saldo rende **dia útil a dia útil** pela forma
contratada. O valor atual é o saldo na data de referência; a valorização gravada é a diferença para o que entrou e
saiu:

```
saldo = 0
para cada dia útil d da primeira compra até a data de referência:
    saldo += compras do dia d
    saldo -= resgates do dia d
    saldo *= fator_do_dia(d)            # da forma de remuneração, abaixo
valorização = saldo + resgatado − aplicado     # valor atual = aplicado + valorização − resgatado = saldo
```

### 3.2 Fator do dia por forma de remuneração

| `rate_type` | `indexer` | Exemplo | Fator do dia útil d |
|---|---|---|---|
| PREFIXADO | — | 13,5% a.a. | `(1 + 13,5%)^(1/252)` |
| POS_FIXADO | CDI | 110% do CDI | `1 + CDI(d) × 110%` (CDI do dia, % a.d., da série 12) |
| POS_FIXADO | SELIC | 100% da Selic | `1 + Selic(d) × p` |
| HIBRIDO | CDI | CDI + 2% a.a. | `(1 + CDI(d)) × (1 + 2%)^(1/252)` |
| HIBRIDO | IPCA, IGPM, INPC | IPCA + 6% a.a. | inflação do mês distribuída pelos dias úteis do mês (D6) × `(1 + 6%)^(1/252)` |
| qualquer | TR | — | sem curva (D9): fica "sem valorização calculada" |

O `MarketIndexCalculator` já faz o produto, o percentual e o spread; o que entra de novo é o fluxo com aportes e
resgates e o tratamento do mês corrente da inflação.

### 3.3 Datas

- **Dias úteis do passado** saem da própria série do CDI (o SGS só publica em dia útil): nada a calcular.
- **Data de referência** = o último dia com CDI publicado (D-1 útil de manhã): "atualizado até 02/10/2026" (D14).
  A taxa do dia d rende de d para o dia útil seguinte — o mesmo critério do CDI na B3.
- **Mês corrente da inflação**: o IPCA de outubro só sai em novembro. Os dias já corridos do mês precisam de um
  número (D5) e de quantos dias úteis o mês tem — o único lugar em que dias **futuros** importam (calendário, D7).
- **Vencimento**: depois de `maturity_date` o título para de render. Ele não é liquidado sozinho (10.4): aparece como
  **vencido — lance o resgate** (D8).

### 3.4 Encaixe com as regras E, F, G e J

A curva entra **pelo `FixedIncomeSettlement`** (10.3), como mais uma fonte da valorização:

| Situação | Quem manda |
|---|---|
| Título **liquidado** (venda total) | o resgate: valorização = resgatado − aplicado (como hoje). A curva não mexe — o ganho é realizado |
| Título **aberto**, sem resgate | a curva |
| Resgate parcial **acima** do saldo na curva | o resgate prova que valia mais (3.1.2): o saldo cai para o centavo da regra F e a curva segue dali (D4) |
| Venda **excluída** (10.2) | a curva recalcula do zero e some a valorização "que ninguém ganhou" |
| Regra G | a curva nunca é negativa (índice negativo raro, ex. IPCA de −0,32%, só reduz o saldo do mês; a regra protege o total) |

### 3.5 Quando roda (app-api — é dado do usuário)

| Gatilho | O que recalcula |
|---|---|
| criar, editar, excluir transação do título | aquele título (já passa por `afterPositionChange`) |
| índice novo na série (aviso da adm-api, D10 — PENDENCIAS 23.1) | os títulos daquele indexador, a partir da data que mudou |
| 21:00 (`PositionRefreshScheduler`, a rede de segurança) e subida da app-api | todos os títulos abertos |

Volume: algumas dezenas de títulos × ~4 mil dias úteis no pior caso — milissegundos; não há cache nem coluna nova.

---

## 4. Calendário de dias úteis (D7 — PENDENCIAS 23.2)

Só é preciso para o mês corrente da inflação (quantos dias úteis o mês tem, contando os que ainda não passaram).
Proposta: `BusinessCalendar` no core com os **feriados nacionais calculados** (fixos — 1/1, 21/4, 1/5, 7/9, 12/10,
2/11, 15/11, 20/11 desde 2024, 25/12 — e móveis pela Páscoa: Carnaval segunda e terça, Sexta-feira Santa, Corpus
Christi), que é o calendário da ANBIMA. **Prova:** teste que confere o calendário contra todas as datas do CDI de 2010
a hoje (todo dia com CDI é útil e todo dia útil tem CDI).

---

## 5. Tela

| Onde | O quê |
|---|---|
| Carteira (abas Renda Fixa e Tesouro) | valor atual, valorização e rentabilidade passam a ter número; ícone (i) com a base: "curva: 110% do CDI, atualizado até 02/10" ou "preço do Tesouro de 02/10 (PU de resgate)"; título sem curva (TR, dado faltando) mostra "—" com o motivo (PENDENCIAS 2.2) |
| Título vencido | selo "Vencido" e a orientação de lançar o resgate (D8) |
| Venda de renda fixa (D11 — PENDENCIAS 4.1/4.2) | no modal de venda de Renda Fixa, seletor dos títulos abertos do ativo (emissor · vencimento · taxa · saldo atual); "resgatar tudo" preenche o saldo na curva e marca liquidação total |
| Dashboard, Alocação, Estratégia | sem tela nova: passam a ver o valor atual da renda fixa (a reserva de emergência e o rebalanceamento seguem pelo custo, como hoje) |

---

## 6. Etapas

| Etapa | Entrega |
|---|---|
| **R1** | Tesouro pelo preço oficial: migration (`price_source` TESOURO, `asset_price_history.source` TESOURO), `TreasuryPriceService` + cliente do CSV com cache, casamento título ↔ catálogo (com relatório do que não casou), rotina, `bootstrap`, mensagem de fechamento. Testes com recorte real do CSV |
| **R2** | `BusinessCalendar` (D7) com a prova contra o CDI |
| **R3** | `FixedIncomeCurve` (core, regra pura): fluxo com aportes/resgates, fator por forma de remuneração, inflação do mês corrente (D5/D6), vencimento (D8). Testes com casos calculados à mão e contra a calculadora do Banco Central |
| **R4** | app-api: valorização pela curva via `FixedIncomeSettlement` (seção 3.4), gatilhos (transação, 21:00, subida) |
| **R5** | aviso de índice (D10): adm-api publica `primevest:catalogo:indices` com o `changedFrom` que a carga já devolve; app-api revaloriza os títulos do indexador |
| **R6** | Tela: base do valor no (i), "—" para sem curva, selo de vencido, seletor do título na venda (D11) |
| **R7** | Carga real no DEV, conferência (seção 8), documentação e validação |

## Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| Código, migrations, testes, documentação | ✔ | |
| Primeiro download do CSV do Tesouro (~1 min) e a carga | ✔ | |
| Conferir o valor dos títulos do Tesouro contra o app do Tesouro / extrato da B3 | | ✔ |
| Decisões D1–D14 | | ✔ |
| Validação (compilar, testes, lint, subir, conferir) | ✔ | |

---

## 7. Fora do escopo

IR, IOF e taxas de custódia (valores brutos — regra do projeto); cupons do Tesouro com juros semestrais (salvo D13);
rentabilidade histórica da carteira e comparação com benchmarks (escopo de rentabilidade); importação de renda fixa
privada pela B3 ou Open Finance (PENDENCIAS 16.6 e plano próprio); poupança como produto (regra de aniversário);
quantidade × preço na renda fixa (PENDENCIAS 4.4).

---

## 8. Casos de conferência (R7)

| Caso | Esperado |
|---|---|
| CDB 100% do CDI, R$ 1.000 de 02/01/2024 a 31/12/2024 | R$ 1.108,76 (CDI 2024 = 10,875983%, já conferido na série) |
| CDB 110% do CDI no mesmo período | produto de `1 + CDI × 1,1` dia a dia |
| Prefixado 12% a.a., 252 dias úteis | exatamente +12% |
| IPCA + 6% em 2024, meses inteiros | 1,0483 × 1,06 − 1 |
| Aporte no meio do período e resgate parcial | o saldo de cada trecho rende só a partir da sua data |
| Título vencido | valor parado no vencimento, selo "Vencido" |
| Venda excluída (10.2) | a valorização volta para a curva |
| Tesouro IPCA+ 2029 do Guilherme | cotas × PU de resgate de 02/10/2026 (3.936,95) = o que o app do Tesouro mostra |
| Calendário × CDI 2010–hoje | nenhuma divergência |

---

## 9. Riscos e achados

- **Download do Tesouro é lento** (55 s para 13,9 MB) e cresce todo dia: o cache só baixa de novo quando o arquivo
  muda (uma vez por dia), dentro da rotina, em segundo plano.
- **Casamento título ↔ catálogo** pelo nome do tipo + vencimento: o Tesouro já renomeou títulos (NTN-B Principal →
  IPCA+); o casamento loga o que não achou, e título novo no Tesouro entra no catálogo por migration (como hoje).
- **IPCA do mês corrente** sempre será estimado até a divulgação: o valor "pula" no dia do IPCA oficial. É o mesmo que
  acontece no extrato dos bancos.
- **Diferença de centavos** para o extrato do banco: bancos arredondam o fator diário com 8 casas e o saldo com 2 a
  cada dia; a curva aqui não arredonda no meio. Aceitar diferença de centavos (D12).
- Valor de mercado do Tesouro **negativo** vai aparecer na carteira (taxa subiu desde a compra) — é real, mas é
  novidade na tela.

---

## 10. Pendências que este plano fecha ou mexe

| Item | Efeito |
|---|---|
| 2.1, 2.2 | resolvidos (R1, R3–R6) |
| 4.1, 4.2 | resolvidos se D11 = incluir |
| 10.2, 10.3 | resolvidos (a curva recalcula e entra pelo `FixedIncomeSettlement`) |
| 10.4 | resolvido como selo "Vencido" (D8) |
| 23.1, 23.2 | resolvidos (R5, R2) |

---

## 11. Decisões (para o Guilherme)

**Tomadas em 05/10/2026: todas conforme a recomendação.**

| # | Pergunta | Opções | Recomendação (= decisão) |
|---|---|---|---|
| **D1** | Como valorizar o **Tesouro**? | preço oficial do Tesouro (marcação a mercado) / curva da taxa contratada / os dois lado a lado | **preço oficial**: é exato, é o que o app do Tesouro e a B3 mostram, e reaproveita a mecânica de cotação |
| **D2** | Qual PU do Tesouro? | PU de **venda** (resgate antecipado: o que você recebe hoje) / PU base / PU de compra | **PU de venda** |
| **D3** | Histórico do preço do Tesouro | desde 2002 (o CSV inteiro) / desde 2010 (padrão do projeto) / só o dia | **desde 2010**, como o resto; a rotina diária em horário próprio, depois de o CSV sair (~07:30) |
| **D4** | Resgate parcial **acima** do saldo na curva | o resgate prova o valor (regra 3.1.2, centavo de sobra) / recusar o resgate | **o resgate prova** — o banco sabe mais que a curva |
| **D5** | Inflação do **mês corrente** (ainda não divulgada) | zero até sair / repetir o último índice publicado / projeção do Focus (Banco Central) | **repetir o último publicado**, trocado pelo oficial quando sai |
| **D6** | Método do IPCA+ / IGP-M+ / INPC+ | mês inteiro distribuído pelos dias úteis do mês / data de aniversário (dia 15, método do Tesouro e da ANBIMA) | **mês distribuído pelos dias úteis** — mais simples e com diferença pequena; o Tesouro, que usa aniversário, vem pelo preço (D1) |
| **D7** | Calendário de dias úteis | feriados nacionais **calculados** (com prova contra o CDI) / tabela da ANBIMA por migration | **calculados** |
| **D8** | Título **vencido** | parar de render e mostrar "Vencido — lance o resgate" / liquidar sozinho com uma venda criada pelo sistema | **parar e avisar** — o sistema não inventa transação. **Revista em 13/10/2026** (`docs/planos/PLANO_NOTIFICACOES.md`, seção 6): o sistema **liquida** o vencido na abertura do dia, com venda de origem SISTEMA ("Automática") e mensagem. **Revista de novo em 15/10/2026** (`docs/planos/PLANO_VENDA_RENDA_FIXA.md`, seção 6): volta a **parar e avisar** — pendência `RF_VENCIDO` com o líquido estimado e o formulário preenchido; a venda é do usuário, pelo líquido |
| **D9** | Indexador **TR** (e poupança) | sem curva ("—" com o motivo) / implementar a TR por aniversário | **sem curva** por enquanto |
| **D10** | Aviso de índice (PENDENCIAS 23.1) | mensagem da adm-api para a app-api / só a rede de segurança das 21:00 | **mensagem**, com a rede de segurança atrás |
| **D11** | Seletor do título na venda (PENDENCIAS 4.1/4.2) | entra neste escopo / escopo separado | **entra**: sem ele não dá para vender renda fixa pela tela, e a curva mostra justamente quanto resgatar |
| **D12** | Precisão contra o extrato do banco | aceitar diferença de centavos / imitar o arredondamento diário de cada banco | **aceitar centavos** |
| **D13** | Cupons do Tesouro com juros semestrais | fora (pendência) / cupom vira provento do título | **fora** — nenhum título da carteira paga cupom hoje |
| **D14** | Data de referência do valor | último dia com CDI (D-1 útil, "atualizado até") / hoje, projetando o dia | **último CDI** |

---

## 12. Andamento

| Data | O quê |
|---|---|
| 05/10/2026 | Plano criado; fonte do Tesouro testada pela rede do Guilherme (seção 1.3). |
| 05/10/2026 | D1–D14 decididas; execução autorizada. O Guilherme cadastrou um CDB para conferência (Picpay cofrinho turbinado, 6 aportes de 07/2025 a 12/2025); estava como **híbrido CDI + 121% a.a.** e foi corrigido no banco, a pedido dele, para **pós-fixado 121% do CDI**. |
| 05/10/2026 | **R1** — `V44__tesouro_preco_oficial.sql` (`price_source` TESOURO no tipo Tesouro Direto; `asset_price_history.source` aceita TESOURO); `TesouroPriceClient` (CSV com cache em `carga/data/tesouro`, `PublicFileCache`), `TreasuryPriceService` (casa por tipo + vencimento de `asset_terms`, PU de venda — PU base quando falta —, upsert, preço atual, "catálogo mudou" FECHAMENTO), `TreasuryPriceScheduler` (08:00 + recuperação na subida), `POST /api/v1/admin/tesouro?from=&recent=`, etapa 9 do `bootstrap`. `TreasuryPriceIT` (3) com recorte real do CSV. |
| 05/10/2026 | **R2** — `BusinessCalendar` (core): feriados nacionais calculados (fixos, 20/11 desde 2024, móveis pela Páscoa). Prova: **todas as 4.208 datas do CDI de 2010 a 02/10/2026 batem com o calendário, nos dois sentidos**; `BusinessCalendarCdiTest` (2024, resposta real) e `BusinessCalendarTest`. |
| 05/10/2026 | **R3** — `FixedIncomeCurve` (core, regra pura): fluxo de aportes e resgates por dia útil, prefixado, % do CDI/Selic, CDI/Selic + spread, IPCA/IGP-M/INPC (mês distribuído pelos dias úteis, mês não divulgado = último publicado e marcado como estimado), resgate acima do saldo → centavo da regra F, vencimento, TR sem curva. `FixedIncomeCurveTest` (8). |
| 05/10/2026 | **R4** — `FixedIncomeValuation` (core: séries por rodada, referência = último CDI) e `FixedIncomeSettlement.applyCurve` (título aberto = saldo na curva; liquidado = ganho realizado; sem curva = como estava). Gatilhos: transação (`afterPositionChange`, depois das regras E/F/G), 21:00 e subida (`PositionRefreshService`). `PositionValuationInfo` + 4 campos no `PortfolioAssetDetailDTO` (`valuationBasis`, `valuationDate`, `valuationNote`, `matured`). |
| 05/10/2026 | **R5** — `CatalogChange` ganhou a origem INDICES (`indexCodes`); a carga de índices publica quando algo mudou e a app-api refaz a curva de todos os títulos abertos (a data de referência muda para todos). |
| 05/10/2026 | **R6** — Carteira: (i) no VALOR ATUAL com a base (curva + taxa + "atualizado até", ou preço do Tesouro + data, ou o motivo de não ter curva), RENT. % "—" sem curva, selo **Vencido** no vencimento. Transação: venda de Renda Fixa pela Nova Transação com o seletor do título (emissor · vencimento · taxa · saldo) e o atalho **Resgatar tudo** (saldo na curva + liquidação total), também pelo ícone Vender da Carteira. `RendaFixaCurvaIT` (6). |
| 05/10/2026 | **R7** — carga real no DEV: preço do Tesouro desde 2010, **109 títulos, 133.490 dias**, último 02/10/2026; 4 títulos antigos do arquivo sem título no catálogo (Prefixado 2010/2011/2024 e Prefixado com Juros 2010 — vencidos, ficam de fora). Valores abertos: Tesouro IPCA+ 2029 3.936,95/cota, Selic 2028 19.995,05… CDB Picpay (121% do CDI) **R$ 11.442,60, valorização R$ 1.811,35**, conferido por cálculo independente com o CDI do banco (mesmo número ao centavo). Achado: duas subidas seguidas da adm-api (hot reload) baixaram o CSV ao mesmo tempo e uma falhou no `.part`; a seguinte completou (sem efeito em uso normal). |
| 05/10/2026 | **R7** — validação: `mvn clean verify` verde com **521 unitários + 231 de integração** (core 158; integration 103; adm-api 88 + 38; app-api 172 + 193); front `tsc` + `eslint` limpos; conferido no navegador: abas Renda Fixa e Tesouro com valor atual e dica da base de cálculo, venda de CDB com seleção do título e "Resgatar tudo — saldo na curva" (nada salvo). |
