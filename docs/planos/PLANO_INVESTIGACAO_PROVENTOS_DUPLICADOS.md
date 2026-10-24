# Plano de investigação — proventos duplicados/indevidos (divergência 2026 vs Investidor10)

> **Nota (23/10/2026):** escrito quando o projeto se chamava **PrimeVest**. Nomes de módulo (`primevest-*`), pacote
> (`com.primevest`), configuração (`primevest.*`, `PRIMEVEST_*`), contêineres e versões de migration (V1–V67) citados aqui
> são os do repositório `primevest`; no Activest viraram `activest-*`, `com.activest`, `activest.*`/`ACTIVEST_*` e as
> migrations consolidadas V1–V6 (`docs/planos/PLANO_RENOMEACAO_ACTIVEST.md`).

> ETAPA: Investigação (sem correção). Nada de git. Validação/conferência é a última etapa.

## 1. Contexto

- Usuário: guilhermeunatec@gmail.com. Comparação de proventos **recebidos** (líquido) com o Investidor10.
- 2024 e 2025 batem exatamente. **2026**: PrimeVest R$ 1.902,81 × Investidor10 R$ 1.870,87 → **+31,94**. Quantidade de cotas/ações conferida e igual.
- Banco: container `primevest-db` (Postgres 17), base `db_primevest`, usuário `postgres`.
  Acesso: `docker exec primevest-db psql -U postgres -d db_primevest -c "<sql>"` (PowerShell no Windows).
- Tabelas: `dividends` (provento do usuário: `portfolio_asset_id`, `cash_event_id`, `origin` AUTO/MANUAL,
  `shares_on_date`, `amount_per_share`, `total_amount`, `withholding_tax`, `net_amount`, `payment_date`, `ex_date`)
  → `asset_cash_events` (catálogo: `asset_id`, `type`, `data_com`, `payment_date`, `value_per_share`, `isin`,
  `related_to`, `source` B3/FNET, `source_ref`, `seq`, `fetched_at`).
  Ligação ao usuário: `dividends → portfolio_assets → portfolios → users`; ticker em `assets`.

## 2. Diagnóstico já feito (2026, recebidos por mês)

| Mês | PrimeVest | Investidor10 | Dif. | Causa |
|---|---|---|---|---|
| Jan | 147,39 | 142,75 | +4,64 | TRXF11: rendimento de **recibo de subscrição** lançado sobre as cotas |
| Mai | 225,94 | 212,27 | +13,67 | KLBN4: dividendo duplicado no catálogo |
| Ago | 263,39 | 249,73 | +13,66 | KLBN4: dividendo duplicado no catálogo |
| Mar/Abr/Jun/Set | — | — | −0,02/−0,02/+0,01/−0,01 | arredondamento (provável IR retido) |

### Caso A — KLBN4 (duplicidade no catálogo B3)
- Mesmo evento (data-com 15/12/2025, aprovado 08/12/2025, "3º Trimestre/2025", ISIN BRKLBNACNPR9, parcelas seq 2 e 3)
  existe duas vezes em `asset_cash_events`:
  - ids 910/911 (fetched 24/09) com `value_per_share` = 0,04559717**225**
  - ids 54776/54782 (fetched 02/10) com `value_per_share` = 0,04559717**224**
- Hipótese: a chave de deduplicação/upsert da carga B3 inclui `value_per_share` exato; a B3 devolveu o valor com
  variação na 11ª casa e a carga criou eventos novos. Parcela 1 (id 909, 27/02) não duplicou — investigar por quê.
- `dividends` 165/283 (mai) e 166/282 (ago) apontam um para cada evento.

### Caso B — TRXF11 (ISIN de recibo de subscrição)
- Dois avisos FNET para dez/2025, pagamento 15/01/2026:
  - id 1028: R$ 1,00, ISIN **BRTRXFCTF003** (cota) — correto.
  - id 4631: R$ 0,9292, ISIN **BRTRXFR02E15** (recibo de subscrição R02), `source_ref` 1072931, sem `approved_on` — indevido.
- Hipótese: a ingestão FNET associa o aviso ao ativo pelo fundo/ticker, sem filtrar o ISIN da classe/cota;
  avisos de recibos (R01, R02…) viram proventos da cota.

## 3. Passos da investigação

### 3.1 Medir o tamanho do problema A (duplicidade) em todo o catálogo
1. Eventos "iguais" com valor diferindo só por arredondamento:
   ```sql
   select asset_id, type, data_com, payment_date, coalesce(isin,'') isin, seq,
          count(*) qtd, min(value_per_share), max(value_per_share), array_agg(id order by id) ids,
          array_agg(source) fontes
   from asset_cash_events
   group by 1,2,3,4,5,6
   having count(*) > 1 and max(value_per_share) - min(value_per_share) < 0.000001
   order by asset_id, data_com;
   ```
2. Repetir sem `seq` e sem `payment_date` para achar variações (ex.: parcela com data mudada; mesmo evento vindo de B3 e FNET).
3. Contar quantos desses duplicados geraram `dividends` (de qualquer usuário):
   `select ... from dividends where cash_event_id = any(<ids duplicados>)`.
4. Ler no código a carga B3 de proventos (primevest-adm-api / primevest-integration; procurar o upsert de `asset_cash_events`,
   ex. `grep -rn "asset_cash_events\|AssetCashEvent" --include=*.java`) e identificar a chave de unicidade usada
   (constraint no banco: `\d asset_cash_events` e migrations V20/V38/V45) — confirmar se `value_per_share` faz parte e com que escala.
5. Explicar por que a carga de 02/10 rodou de novo e por que a parcela 1 do KLBN4 não duplicou (verificar se a resposta da B3
   de 02/10 trouxe só parcelas futuras/últimos 12 meses).

### 3.2 Medir o tamanho do problema B (ISIN de recibo) em todo o catálogo
1. Eventos cujo ISIN não é o da cota/ação do ativo:
   ```sql
   select e.id, a.ticker, e.isin, e.data_com, e.payment_date, e.value_per_share, e.source, e.source_ref
   from asset_cash_events e join assets a on a.id = e.asset_id
   where e.isin is not null and substring(e.isin from 7 for 2) ~ '^R[0-9]'   -- padrão BRxxxxR0nnnn (recibos)
   order by a.ticker, e.data_com;
   ```
   Conferir também se `assets` tem coluna de ISIN para comparar diretamente `e.isin <> a.isin`.
2. Listar `dividends` ligados a esses eventos (impacto por usuário/ano).
3. Ler a ingestão do FNET (avisos de rendimento) e localizar onde o ISIN do aviso é (ou não) validado contra o ativo.
4. Verificar se o mesmo acontece com ações (B3): recibos/direitos de subscrição (ex.: ISIN de "DIR"/"REC").

### 3.3 Diferença de centavos (Mar, Abr, Jun, Set)
1. Listar os proventos desses meses com `withholding_tax > 0` e `amount_per_share` com mais de 2 casas.
2. Comparar duas regras: arredondar o total bruto e depois o IR × calcular líquido por ação e multiplicar. Ver qual reproduz o Investidor10.
3. Decidir se vale ajustar (diferença total ≈ 0,04) ou apenas registrar.

### 3.4 Conferência com o Investidor10
- Após cada hipótese, recalcular por mês e confirmar que ao excluir os eventos indevidos o total 2026 fica 1.870,87:
  ```sql
  select extract(month from d.payment_date) mes, sum(coalesce(d.net_amount,d.total_amount)) liq
  from dividends d join portfolio_assets pa on pa.id=d.portfolio_asset_id
  join portfolios p on p.id=pa.portfolio_id join users u on u.id=p.user_id
  where u.email='guilhermeunatec@gmail.com' and d.payment_date between '2026-01-01' and current_date
    and d.cash_event_id not in (54776, 54782, 4631)
  group by rollup(1) order by 1;
  ```
- Rodar a mesma conferência para 2027 (a receber) e anos anteriores, para garantir que nada mais muda.

## 4. Entregáveis da investigação
1. Lista completa de eventos duplicados (caso A) e de eventos com ISIN de recibo (caso B), com impacto em `dividends`.
2. Causa raiz no código de cada caso (arquivo/método/chave usada).
3. Proposta de correção (para outro plano, só com ordem explícita), cobrindo:
   - chave de deduplicação tolerante a escala/arredondamento (ex.: `value_per_share` arredondado a 8 casas ou fora da chave);
   - filtro de ISIN da cota/ação na ingestão FNET/B3;
   - limpeza dos eventos e `dividends` já gravados (respeitando a regra de migrations do REGRAS_CLAUDE.md e o princípio
     de que dado de catálogo vem da integração — avaliar se a limpeza é por rotina da adm-api ou por recarga);
   - eventual constraint única no banco para impedir reincidência.
4. Decisão sobre o arredondamento de centavos.

## 5. IDs de referência
- `asset_cash_events`: 909, 910, 911, 54776, 54782 (KLBN4, asset_id 442); 1028, 4631 (TRXF11, asset_id 1077).
- `dividends`: 164, 165, 166, 282, 283 (KLBN4); 186, 273 (TRXF11).

## 6. Resultado da investigação (20/10/2026)

> Executada em 20/10/2026, só leitura (banco `db_primevest` na V59, código e `carga/data`). Nada foi corrigido.

### 6.1 Conferência final

| Ano | Recebido hoje | Sem os eventos indevidos | Investidor10 |
|---|---|---|---|
| 2024 | 13,07 | 13,07 | igual |
| 2025 | 1.230,60 | 1.230,60 | igual |
| 2026 (até 20/10) | 1.902,81 | **1.870,83** | 1.870,87 (−0,04, arredondamento — 6.4) |
| A receber (2026/2027) | 165,58 / 80,71 | sem mudança | — |

Os R$ 31,94 são 13,67 + 13,67 (KLBN4, caso A) + 4,64 (TRXF11, caso B) − 0,04 (centavos).

> **Atenção à consulta do item 3.4:** `d.cash_event_id not in (...)` descarta também os proventos MANUAL
> (`cash_event_id` nulo) e dá 1.869,92. A conta certa usa `d.cash_event_id is distinct from ...` (1.870,83).

### 6.2 Caso A — duplicidade no catálogo B3 (KLBN4)

**Tamanho:** 16 eventos duplicados, todos de fonte `B3`, todos gravados na rotina de 02/10/2026 às 23:02–23:21 UTC
(20:02 de São Paulo, a rotina [A4] das 20:00). 8 papéis: KLBN3, KLBN4, KLBN11, RECV3, TRIS3 e os fracionários.

| Evento | Papel | Data-com | Pagamento | Seq | Original |
|---|---|---|---|---|---|
| 54774/54780 | KLBN3 | 15/12/2025 | 20/05 e 19/08/2026 | 2, 3 | 3579/3584 |
| 54776/54782 | KLBN4 | 15/12/2025 | 20/05 e 19/08/2026 | 2, 3 | 910/911 |
| 54778/54784 | KLBN11 | 15/12/2025 | 20/05 e 19/08/2026 | 2, 3 | 3582/3587 |
| 54806 | RECV3 | 08/01/2026 | 31/12/2027 | 2 | 43413 |
| 54808 | TRIS3 | 26/12/2025 | 29/05/2026 | 2 | 49148 |
| 54775/54781, 54777/54783, 54779/54785, 54807, 54809 | fracionários (F) | idem | idem | | |

**Impacto em `dividends`:** só 282 e 283 (KLBN4, guilhermeunatec@gmail.com, R$ 13,67 cada). O banco tem 1 usuário.

**Os 152 "iguais com valor quase igual" de fonte `B3_HISTORICO` não são duplicidade:** são parcelas que a B3
publica no histórico longo (a última parcela absorve o arredondamento: ALLD3 0,482726014/015, AZEV3 11× …041 + 1× …042,
EQTL3, SOND5/6, VBBR3…). Conferido arquivo a arquivo em `carga/data/b3/proventos/` para 10 emissores: o banco tem as
mesmas linhas do arquivo. Não há o mesmo provento gravado por duas fontes (B3 × B3_HISTORICO × FNET) com tolerância
de 1e-6: 0 grupos.

**Causa raiz** (`ProventosCatalogService.syncGroup` + `sameValue`, adm-api):

1. A B3 publica o provento em parcelas com o mesmo valor, e a **última parcela vem com o arredondamento**
   (KLBN: 0,04559717225 × 3 e 0,04559717224 na 4ª, pagamento 12/11/2026; RECV3 0,341323 × 2 e 0,341322; TRIS3).
2. `Installments.next` numera a parcela (`seq`) pelo **valor bruto da linha** — a 4ª parcela de KLBN4 ganha `seq` 1
   (912), porque o valor dela é outro.
3. Desde 28/09 (commit 4ab8249) a linha da B3 passa por `sameValue(asset, type, dataCom, value)`, feita para o FNET
   × B3 (1,03999999900318 × 1,04): devolve **o primeiro valor gravado** a menos de 0,000001. A consulta não tem
   `ORDER BY` e o PostgreSQL a resolve pelo índice `uk_asset_cash_events` (`Index Only Scan`, conferido com
   `EXPLAIN`): **o primeiro é sempre o menor valor**, e não o valor exato.
4. Na rodada de 02/10, com as 4 parcelas já gravadas, a parcela 2 (…225, seq 2) foi "ajustada" para …224 → procura
   (…224, seq 2) → não existe → **grava evento novo**. Idem parcela 3.
5. **Por que a parcela 1 não duplicou:** …225 seq 1 virou …224 seq 1, que existe (912, a parcela de 12/11) → o
   `CashEventWriter` "completou" o evento 912 (sem mudar nada) em vez de gravar. A parcela 4 (…224 seq 1) também caiu no 912.
6. **Por que só em 02/10:** a primeira carga de KLBN4 (24/09) foi antes do `sameValue` na linha da B3; com ele, a
   primeira rodada sobre o catálogo já com as parcelas é a que duplica; depois disso o par (valor, seq) passa a existir
   e as rodadas seguintes não gravam mais nada (estável, não cresce a cada noite).

**Riscos na mesma lógica, não observados no banco hoje** (os 6 grupos de parcelas "quase iguais" dos arquivos
`proventos-12m` foram conferidos — 4 duplicados acima, ETER3 correto por acaso, nenhuma parcela perdida):
- Numa **primeira carga** (banco novo ou provento novo) a última parcela é "ajustada" para o valor das anteriores e
  cai na parcela 1 → **a parcela some**.
- Ao "completar" o evento errado, o `CashEventWriter` pode trocar a data de pagamento de outra parcela quando a
  data dela veio da CVM ou do levantamento (`payment_date_source` não nulo).

### 6.3 Caso B — ISIN de recibo de subscrição (TRXF11)

**Tamanho:** 214 eventos com ISIN de recibo (`^BR....R`, ex. BRTRXFR02E15, BRALZRR27M17), em 71 FIIs, todos de fonte
`FNET` (213 RENDIMENTO, 1 AMORTIZACAO), de 2017 a 08/10/2026 (65 desde 2024). 128 deles têm, na mesma data-com,
o evento da cota — o recibo soma um segundo provento sobre a cota. Ações: nenhum caso (a B3 recente casa pelo ISIN;
o histórico longo, pela espécie ON/PN).

**Impacto em `dividends`:** só 273 (TRXF11, R$ 4,64). Os outros FIIs em carteira com eventos de recibo (BTLG11,
GARE11, MXRF11, RBRY11, TRXF11 — 10 outros —, XPML11; 27 eventos) têm data-com antes da primeira transação, então não
geraram provento — gerariam para quem tivesse cota na data.

**Causa raiz** (`ProventosCatalogService.loadFund`, adm-api):
1. O aviso 1072931 traz 5 proventos: a cota (TRXF11, BRTRXFCTF003, R$ 1,00) e 4 recibos (TRXF13, TRXF14, TRXF15 e
   **TRXF26** — BRTRXFR02E15, R$ 0,9292).
2. O filtro de recibo é só pelo **ticker**: `RECEIPT = [A-Z]{4}1[2-9]` (finais 12 a 19). TRXF13/14/15 saíram;
   **TRXF26 passou** (final 26). O ISIN do aviso não é olhado.
3. `target(assets, p)`: fundo com uma cota só recebe **qualquer** provento do aviso, sem conferir ISIN nem ticker.
4. O aviso 1073440 (republicação) trouxe a cota de novo, que já existia (1028) — por isso o evento de recibo tem
   `source_ref` 1072931 e o da cota 1073440.

> Observação fora do escopo: 1.925 eventos FNET têm ISIN de **cota de outro fundo** (`...CTF...`, ex. ALZC11 com
> BRSIGRCTF004, BTCI11 com BRFEXCCTF007, BTLG11 com BRTRXLCTF001). São, em geral, fundos renomeados/incorporados
> (ligação pelo CNPJ, como previsto); não foram auditados um a um.

### 6.4 Diferença de centavos

> **Encerrado em 21/10/2026 (D3):** sem alteração; o PrimeVest bate com o Nubank provento a provento.

Sem os eventos indevidos, a diferença de 2026 é −0,04 (PrimeVest abaixo). Regras testadas, mês a mês, sobre todos os
proventos de 2026 (variação contra a conta atual):

| Regra | Total | Bate com o Investidor10? |
|---|---|---|
| Atual: bruto truncado, IR truncado (a da B3, `PLANO_IR_PROVENTOS.md`) | 0,00 | −0,04 |
| B: líquido direto arredondado `round(q × v × (1 − alíq.), 2)` | +0,06 | mar e jun batem; abr, mai, ago e set não |
| C: bruto arredondado, IR arredondado | +0,07 | não |
| D: bruto arredondado, IR truncado | +0,21 | não |
| E: bruto truncado, IR arredondado | −0,14 | não |
| F: líquido por ação (8 casas) × cotas, arredondado | +0,06 | igual a B |
| G: líquido direto truncado | −0,18 | não |

Nenhuma regra reproduz o Investidor10 mês a mês. A regra atual é a do extrato da B3, conferida no
`PLANO_IR_PROVENTOS.md` (BBAS3: R$ 14,57).

### 6.5 Proposta de correção (para um plano próprio — só com ordem explícita)

**Caso A**
1. `sameValue`: preferir o **valor exato**; só usar a tolerância quando o evento gravado for de **outra fonte**
   (FNET × B3, o caso para o qual ela existe) e com `ORDER BY` determinístico — ou tirar `sameValue` da linha da B3.
2. Numerar a parcela com o **mesmo valor que vai ser gravado** (hoje `seq` usa o valor bruto e a chave usa o
   ajustado) — ou numerar por (tipo, data-com) na ordem do pagamento, tolerando o arredondamento da última parcela.
3. Testes com as respostas reais: KLBN (4 parcelas, última …224) na primeira carga e na segunda rodada; RECV3; TRIS3;
   ETER3. `ProventosCatalogoIT.idempotenteESemDuplicar` hoje não cobre parcela com arredondamento.
4. Constraint: manter `uk_asset_cash_events` (o valor com 11 casas faz parte do evento: as parcelas do histórico
   longo diferem por 1e-9/1e-11 e são legítimas). Reincidência fica coberta pelo código e pelos testes.

**Caso B**
1. `loadFund`: descartar o provento cujo **ISIN é de recibo** (`^BR[A-Z0-9]{4}R`), além do filtro de ticker; e trocar
   `[A-Z]{4}1[2-9]` por "final diferente de 11" quando houver ticker (cobre TRXF26).
2. `target`: com uma cota só, não aceitar provento com ISIN de recibo nem ticker de outra classe.
3. Teste com o aviso real 1072931 (TRXF11).

**Limpeza dos dados já gravados**
- Apagar os 16 eventos do caso A e os 214 do caso B, e antes os `dividends` ligados a eles (FK `cash_event_id` sem
  cascade): 282, 283 e 273. Precedente: V35 (87 amortizações duplicadas apagadas por migration). Com o código
  corrigido, a rotina não regrava. A app-api refaz o restante às 21:00 ou na subida.
- Alternativa: rotina de administrador na adm-api (apaga só o catálogo) + recálculo da app-api. Decisão em aberto.

### 6.6 Decisões para o Guilherme

- **D1** — Limpeza por migration (como a V35) ou por rotina da adm-api?
- **D2** — Caso A: tirar a tolerância da linha da B3 ou mantê-la só entre fontes diferentes?
- **D3** — Centavos (−0,04 em 2026): **decidida em 21/10/2026 — fecha sem alteração.** O Guilherme conferiu no
  Nubank (o que de fato caiu na conta) os 14 proventos em que truncar e arredondar divergem (PETR4, KLBN4, BBSE3,
  BBAS3, ISAE4, SAPR4, CMIG4): todos batem com o PrimeVest (regra da B3). O Investidor10 não é referência, só
  complemento de análise. Registro: a diferença está do lado do Investidor10 e não é de regra de arredondamento —
  em 2025 seis proventos divergiriam com arredondamento e o Investidor10 bate com a B3; em set/2026 há +0,01 sem
  nenhum provento sensível a arredondamento (dado de entrada diferente lá).
- **D4** — Auditar os 1.925 eventos FNET com ISIN de cota de outro fundo (6.3, observação)?

## 7. Segunda rodada (21/10/2026) — respostas a D1, D2 e D4 e varredura completa

### 7.1 D1 — por onde limpar
- Origem: tudo entrou pela rotina de proventos da adm-api (`ProventosCatalogService`); os `dividends` pela app-api
  (`DividendRecalculator`). Os arquivos de `carga/data` estão certos; o erro é de leitura.
- Banco novo com o código corrigido: carrega certo (cobrir com testes das respostas reais).
- Rodar a rotina (agendada ou à mão) **não apaga nada**: ela só grava ou completa. E a FK
  `fk_dividends_cash_event` (sem cascade) impede apagar o evento enquanto houver provento AUTO ligado a ele.
- Caminhos: (a) migration de dados, como a V35 — limpa dev e `primevest-teste` sozinha (recomendação);
  (b) rotina de saneamento na adm-api — exige também mexer na FK (migration de schema) ou um endpoint na app-api
  para apagar os proventos antes. **Decisão D1 em aberto.**

### 7.2 D2
- **D2-1, os 3 MANUAL:** `dividends` 257, 258 e 259 — PETR4 RENDIMENTO (atualização pela Selic do JCP/dividendo de
  data-com 22/12/2025), pagos em 20/02 (0,35) e 20/03/2026 (0,21 e 0,35). Vieram do extrato da B3 e a V38 os deixou
  MANUAL ao tirar `EXTRATO_B3` do catálogo. A B3 não publica esse rendimento para a data-com 22/12/2025 (publica para
  22/04/2026). Nenhum outro dono de PETR4 recebe esses valores.
- **Resquícios do extrato no catálogo:** nenhum (fonte só aceita B3, B3_HISTORICO e FNET; 0 referências do extrato).
  No código restam a conferência `POST /api/v1/admin/proventos/extrato` (`ExtratoProventosImportService`, só lê) e o
  uso dela pela integração da Área do Investidor (`ProventosConferencia`, só marca o motivo do item).
- **D2-2, tolerância:** serve para FNET × B3 (1,03999999900318 × 1,04). Dentro da própria B3 só existem valores
  "quase iguais" entre parcelas diferentes, e ali ela confunde parcela com parcela (caso A). Tirar de tudo quebraria
  o FII (o FNET grava antes e a B3 precisa reconhecer o mesmo provento). Correção: tolerância só contra evento de
  **outra fonte**, preferindo o valor exato.

### 7.3 Varredura completa (classes novas)

| Classe | O que é | Tamanho | Afeta a carteira do Guilherme |
|---|---|---|---|
| A | Parcelas da B3 duplicadas (seção 6.2) | 16 eventos | 2 proventos |
| B | Recibo de subscrição do FNET (6.3) | 214 eventos | 1 provento |
| **C** | **Aviso do FNET retificado gravado duas vezes**: mesmo fundo, valor e pagamento, data-base corrigida em 1–3 dias, documento novo (ex.: AIEC11 1072793 × 1073063; DPRO11 1184197 × 1185096; ADSH11; GGRC11) | 79 grupos | não |
| **D** | **Reserva da B3 não absorvida pelo FNET** porque a data-com difere (SMRE11 01/06 × 29/05/2026; SPG211) | 2 grupos | não |
| E | FNET com ISIN de cota de outro fundo (D4): 1.925 eventos em 59 fundos; 7 com o ISIN de **outro ativo vivo, de outro emissor** (incorporação: BRIP11←VCRI11, RBRL11←BRIP11, BICE11←RECD11, DAYM11←URHF11, RBTS11←PLRI11, RCFF11←TEPP11, VSLH11←TORD11) | 7 certos + auditoria | não |
| F | Rendimento (Selic) de ação que a B3 não publica (PETR4 22/12/2025) | a medir | 3 MANUAL |
| G | Ações sem histórico longo da B3 (PENDENCIAS 19.1: KLBN, ABEV, RENT…) | 17 ações | não (2024/2025 batem) |
| H | Eventos sem data de pagamento | 17 (2025) + 23 (2026) | não |

**Conferidos e legítimos (não mexer):** as 152 parcelas do histórico longo (6.2); proventos de data-com diferentes
pagos no mesmo dia (HYPE3, ISAE3/ISAE4 — conferido no Nubank —, ITSA3/4, RIAA3, WEGE3, VIVT3, SANB, CSUD3).

### 7.4 Decisões em aberto
- **D1** — limpeza por migration de dados (recomendação) ou rotina de saneamento?
- **D2-1a** — PETR4 rendimento Selic: procurar fonte de catálogo (CVM/IPE) para virar evento de todos, ou manter MANUAL?
- **D2-1b** — tirar do código a conferência por extrato (endpoint e uso na Área do Investidor)?
- **D4** — confirmada pelo Guilherme (21/10): auditoria completa. Escopo proposto: classes A a E corrigidas no código e
  limpas; F, G e H dentro do escopo ou mantidas como pendências?
- **D5** — classe C: regra "o documento mais novo do mesmo fundo, valor e pagamento substitui o anterior"?

### 7.5 Decisões de 21/10/2026 e respostas
- **D1 — decidida:** limpeza por **migration de dados** (como a V35).
- **D2-1b — decidida:** sai do código toda a conferência por extrato (endpoint `POST /api/v1/admin/proventos/extrato`,
  `ExtratoProventosImportService`, testes e documentação). As migrations já aplicadas (V24, V38) ficam: migration
  aplicada não se edita nem se apaga (o Flyway recusa a subida).
- **D2-2 — decidida:** tolerância só contra evento de outra fonte, sempre preferindo o valor exato.
- **D5 — decidida:** conferir no FNET se o documento antigo fica marcado como substituído antes de implementar a regra.
- **D2-1a — respostas:** os 3 rendimentos de PETR4 são reais (caíram na conta). FNET é só de FII. A B3 publicou o
  rendimento Selic de PETR4 para as data-com 16/04/2025 e 22/04/2026, mas não para 22/12/2025 (3T25) — em nenhum dos
  dois serviços. Os 48 relatórios de proventos da CVM da Petrobras em `carga/data/cvm/proventos` marcam "Haverá
  atualização: Sim", sem o valor. O valor só aparece nos Comunicados ao Mercado da Petrobras (IPE de 12/02/2026 e
  16/03/2026, "atualização monetária da remuneração aos acionistas relativa ao 3T25"), em texto livre.
- **F, G, H e a BRAPI:** a BRAPI repassa os proventos da B3 (mesma estrutura, `PLANO_BRAPI.md` 5.6) e só no plano
  pago. F: a B3 não tem, a BRAPI também não. G: a causa é nossa — a B3 devolveu lista vazia uma vez
  (`carga/data/b3/proventos/KLBN.json` = `[]`) e, como o arquivo é o estado, nunca mais se tentou; a fonte é a própria
  B3. H: são proventos com pagamento "a definir" pela empresa (B3 mostra 31/12/9999); nenhuma fonte tem a data, e a
  rotina a grava quando sair. Uma quarta cópia do mesmo dado (BRAPI) traria valores com outras casas — o tipo de
  quase-igual que gerou os casos A e C. Recomendação: não usar a BRAPI para proventos.
- **D2-1a — decidida (21/10):** (a) os 3 rendimentos de PETR4 ficam como MANUAL (registro do usuário); a classe F
  vira pendência, com a fonte identificada (Comunicado ao Mercado da empresa).
- **D2-1b, Área do Investidor — decidida (21/10):** a integração é por API (OAuth2 + mTLS, `B3InvestidorClient`); o
  simulador só imita essa API para teste. A comparação de proventos dela (`ProventosConferencia`) **fica**; sai só o
  extrato por arquivo. A lógica de comparação deixa a classe `ExtratoProventosImportService` e vai para uma classe
  própria da integração.
- **D4 — decidida (21/10):** sem BRAPI. G entra no escopo (correção do nosso código, fonte B3); H não precisa de
  correção (pagamento "a definir" pela empresa), só a conferência dos 6 de 2025 fora da B3 recente.
- **Próximo passo:** `docs/planos/PLANO_CORRECAO_PROVENTOS.md` (Planejamento).
