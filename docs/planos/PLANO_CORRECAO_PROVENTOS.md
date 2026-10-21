# PrimeVest — Plano de Correção dos Proventos (catálogo e carteira)

> Fase: **concluído** (21/10/2026; execução autorizada no mesmo dia) — andamento na seção 7.
> Origem: `docs/planos/PLANO_INVESTIGACAO_PROVENTOS_DUPLICADOS.md` (seções 6 e 7), com as decisões D1–D5 tomadas
> pelo Guilherme em 20–21/10/2026.

## 1. Objetivo

Deixar o catálogo de proventos (`asset_cash_events`) e os proventos dos usuários (`dividends`) sem eventos indevidos
nem duplicados, para **qualquer usuário**, e garantir que uma carga inicial nova, com o código corrigido, já nasça
certa. Referência de verdade: o que cai na conta (B3/corretora); o Investidor10 é só complemento.

## 2. Decisões já tomadas

| # | Decisão | Data |
|---|---|---|
| D1 | Limpeza por **migration de dados** (como a V35), por critério e não por id — vale para o dev, a pilha de teste e qualquer banco | 21/10 |
| D2-1a | Os 3 rendimentos Selic de PETR4 (`dividends` 257–259) ficam MANUAL; classe F vira pendência | 21/10 |
| D2-1b | Sai do código toda a conferência por **extrato** (arquivo). Fica a comparação de proventos da integração com a Área do Investidor (API), em classe própria. V24 e V38 não se mexem | 21/10 |
| D2-2 | Tolerância de valor só contra evento de **outra fonte**, sempre preferindo o valor exato | 21/10 |
| D3 | Centavos: sem alteração (o PrimeVest bate com o Nubank) | 21/10 |
| D4 | Auditoria completa (classes A–E e G). Sem BRAPI. H não é erro | 21/10 |
| D5 | Aviso retificado do FNET: conferir na fonte se o documento antigo fica marcado como substituído antes de implementar | 21/10 |

## 3. O que corrigir (regras)

| Classe | Problema | Regra nova | Onde |
|---|---|---|---|
| A | Parcelas da B3 com arredondamento na última viram eventos duplicados (16) — e, num banco novo, a última parcela pode sumir | R1: `sameValue` só procura evento de **outra fonte**; havendo valor exato, usa o exato; consulta com ordem definida. R2: a parcela (`seq`) é numerada com o mesmo valor que vai ser gravado | `ProventosCatalogService` (`syncGroup`, `sameValue`, `Installments`) |
| B | Aviso do FNET com ISIN de recibo de subscrição (214) | R3: descartar provento do aviso com ISIN de recibo (`^BR[A-Z0-9]{4}R`) e ticker de outra classe (final ≠ 11), não só finais 12–19. R4: fundo com uma cota não aceita provento com ISIN/ticker de outra classe | `loadFund`, `target`, `RECEIPT` |
| C | Aviso do FNET retificado (data-base corrigida, mesmo valor e pagamento) gravado duas vezes (79 grupos) | R5: o documento mais novo do mesmo fundo, valor e pagamento substitui o anterior — depois da conferência da D5 | `loadFund` / `CashEventWriter` |
| D | Reserva da B3 não absorvida pelo FNET quando a data-com difere (2 grupos) | R6: FII — o aviso do FNET absorve o evento B3 do mesmo ativo, tipo, pagamento e valor, mesmo com data-com diferente | `CashEventWriter` |
| E | FNET com ISIN de cota de **outro fundo** (1.925 eventos, 59 fundos; 7 de incorporação já identificados) | R7: ISIN de cota de outro ativo vivo do catálogo (outro emissor) é descartado. Auditoria dos demais: o ISIN precisa ter sido do próprio fundo (renomeação), conferido pelo COTAHIST (`carga/data/b3/cotahist`, ISIN por pregão) | `loadFund`, `target` + script de auditoria |
| G | 17 ações sem histórico longo: a B3 devolveu lista vazia uma vez e o arquivo vazio virou estado (`KLBN.json` = `[]`) | R8: lista vazia não é estado — tenta de novo na rodada seguinte | `loadCompanyHistory` / `fetchCompanyHistory` |
| Extrato | Conferência por arquivo ainda no código | R9: remover endpoint, serviço, testes, comando do `admin.mjs` e documentação; a lógica de comparação vai para a integração | app-api, `carga/scripts`, docs |

Não mexer (conferido): as 152 parcelas do histórico longo; proventos de data-com diferentes pagos no mesmo dia (HYPE3,
ISAE3/4, ITSA, RIAA3, WEGE3…); os 3 MANUAL de PETR4; eventos com pagamento "a definir" (H).

## 4. Etapas

| Etapa | O que | Fecha quando |
|---|---|---|
| **C1** | D5: conferir no FNET (da máquina do Guilherme) se o documento retificado marca o anterior como substituído; definir R5 | regra R5 escrita no plano |
| **C2** | Testes primeiro, com respostas reais em `src/test/resources`: KLBN (4 parcelas), RECV3, TRIS3, ETER3 (primeira carga e segunda rodada); aviso 1072931 (TRXF11); par AIEC11 1072793/1073063 e DPRO11; SMRE11; um caso de incorporação (BRIP11/VCRI11); histórico vazio que depois vem cheio | testes escritos e falhando pelo motivo certo |
| **C3** | Código: R1–R8 na adm-api/core | C2 verde, `mvn clean verify` verde |
| **C4** | Auditoria da classe E (script em `carga/scripts/conferencia/`): classificar os 1.925 eventos em renomeação × outro fundo pelo COTAHIST; resultado vira lista no plano | lista fechada, sem "a conferir" |
| **C5** | Remoção do extrato (R9) e mudança da comparação para a integração (`IntegracoesB3IT` continua verde) | `mvn clean verify` e lint verdes |
| **C6** | Prova da carga nova: `primevest-teste` do zero (bootstrap) com o código corrigido; comparar o catálogo do teste com o do dev — a diferença tem de ser exatamente as classes A–E (e o que G acrescentar) | diferença explicada evento a evento |
| **C7** | Migration de dados (V60): apaga, por critério, os `dividends` AUTO ligados aos eventos indevidos e depois os eventos (FK sem cascade). Critérios: os de C6 | dev e teste sobem; contagens de C6 batem |
| **C8** | G no dev: rodar a rotina de proventos (histórico das 17 ações); conferir que não nasce par B3 × B3_HISTORICO | 0 grupos multifonte |
| **C9** | Conferência final: 2026 do Guilherme = 1.870,83 (2024 e 2025 iguais); consultas da seção 6.2/6.3/7.3 da investigação zeradas; `contagens.sql` | números batem |
| **C10** | Documentação: `Claude.md`, `docs/PENDENCIAS.md` (F como pendência nova; 19.1 resolvida), `README.md`, `ADM_TASKS.md` ([17] sai), `SCHEDULE_TASKS.md`, `TESTES_FUNCIONAIS.md` e o andamento deste plano | docs com a data |

## 5. Quem faz

| O que | Claude | Guilherme |
|---|---|---|
| Decisões, ordem de execução, git | — | ✔ |
| C1 (consulta ao FNET pela máquina do Guilherme), C2–C9 | ✔ | — |
| `data push` do que a rotina regravar em `carga/data` (histórico das 17 ações) | prepara e confere | ✔ (chave de escrita) |
| C10 | ✔ | revisa |

## 6. Riscos

- **G traz eventos antigos que se sobrepõem aos 12 meses da B3** (mesmo provento por B3 e B3_HISTORICO): hoje há 0
  grupos assim; C8 confere depois da carga.
- **R5 errada** apaga um provento legítimo (dois avisos diferentes com o mesmo valor e pagamento): por isso C1 antes.
- **Migration por critério** pode pegar mais do que o previsto em outro banco: C6 dá a lista esperada e C7 confere a
  contagem antes de apagar.

## 7. Andamento

- 21/10/2026: plano criado; execução autorizada pelo Guilherme no mesmo dia.
- **C1 (21/10):** o FNET marca o aviso substituído — a lista de avisos do fundo (`pesquisarGerenciadorDocumentosDados`)
  traz `status` (`AC` ativo, `IC` inativo, `CC` cancelado), `versao` e `modalidade`. Ex.: AIEC11 577983 (dez/2023) `IC`;
  198771 (jul/2021) `CC`. **R5 ficou:** aviso `IC`/`CC` não vira evento; o evento que ele já gerou é atualizado no lugar
  pelo aviso ativo do mesmo fundo, tipo, pagamento e valor (id preservado; os proventos da carteira acompanham no
  recálculo). A situação fica em `carga/data/fnet/status/{CNPJ}.json` (o arquivo é o estado, F10), gravada sempre que
  a rotina busca avisos do fundo e, para todos de uma vez, por `POST /api/v1/admin/proventos/fnet-status`.
- **C2/C3 (21/10):** `CorrecaoProventosIT` (12 testes, respostas reais da B3 e avisos reais do FNET) e o código R1–R8.
  Dois achados na execução mudaram regras do plano:
  - **R7 (classe E):** o aviso 833386 de BRIP11 (R$ 36,85, 07/02/2025) traz o ticker BRIP11 e o ISIN de VCRI11 — é
    rendimento de BRIP11 (as cotas dele pagam R$ 30–70) com o ISIN preenchido errado pelo administrador. Regra
    final: **com o ticker do fundo, vale o ticker**; o ISIN de outro fundo do catálogo só tira o provento quando o aviso
    não traz o ticker do fundo.
  - **G (PENDENCIAS 19.1):** a causa não era resposta vazia eventual: o histórico da B3 (`GetListedCashDividends`)
    **não acha nome de pregão com barra** — "KLABIN S/A", "AMBEV S/A", "CURY S/A" devolvem 0 lançamentos; "KLABIN SA",
    219; "AMBEV SA", 40; "CURY SA", 20 (conferido na B3 em 21/10). Correção: o nome vai sem a barra
    (`historyQueryName`); e lista vazia de quem tem provento nos 12 meses não é mais estado (R8).
  - **R3 (classe B):** o ISIN de recibo vence o ticker — o administrador repete o ticker da cota nas linhas dos
    recibos (ARRI11, aviso 709290: cota 0,10 e recibos R28, R29 e R30 0,03, todos "ARRI11"; GAME11, RMBS11 iguais).
  - **Busca no FNET:** o FNET não responde página vazia (a busca fica parada até o tempo limite); fundo com
    exatamente 100 avisos falhava na 2ª página. A busca para no `recordsTotal`.
- **C5 (21/10):** saíram `ExtratoProventosController`, `ExtratoProventosImportService` e `ExtratoProventosIT`; a
  comparação dos proventos que a Área do Investidor informa (API) ficou em `ProventosConferencia`, na integração, com
  `ProventosConferenciaIT` (5). `admin.mjs` só fala com a adm-api. Documentação sem a conferência por extrato
  (Claude.md, ADM_TASKS [17] — agora `proventos/fnet-status` —, USE_CASES UC23, TESTES_FUNCIONAIS, PENDENCIAS 3.4,
  3.5, 14, 18.6 e 20.5). V24 e V38 ficam (migration aplicada não se edita).
- **Bateria (21/10, antes dos últimos ajustes de R3 e da busca):** `mvn clean verify` verde — core 190; integration
  103; adm-api 116 + 60; app-api 210 + 261.
- **C4 (21/10) — auditoria da classe E** (`carga/scripts/conferencia/proventos.mjs --cotahist`): 1.941 eventos do FNET com
  ISIN de outro papel e sem o ticker do fundo; o COTAHIST mostra, para quase todos, o código antigo do próprio fundo
  negociado com aquele ISIN antes da renomeação (SIGR11→ALZC11, FEXC11→BTCI11, TRXL11→BTLG11, MALL11→PMLL11...) —
  **corretos**. 82 têm ISIN nunca negociado: em geral erro de preenchimento sozinho no dia (ARTE11, BTHF11, TGAR11,
  "N/A", "RBR PLUS") — **corretos**; e casos de outro papel **no mesmo pagamento da cota** — **errados**: recibos sem o
  "R" (EURO11 BREURO01M15, GGRC11 BRGGRR01M15/02M14), direito (XPML11 BRXPMLD02M12), outra classe (LPLP11 CTF016, SPG211
  CTF021) e outro fundo no mesmo CNPJ (XLPR11 ← ANGC11, R$ 770). Regras novas: **R9** (outro papel no dia da cota fica
  fora) e ISIN com "D" (direito) junto do "R". Os 4 de outro fundo do catálogo sem o ticker do fundo (BICE11←RECD11,
  RBRL11←BRIP11, RBTS11←PLRI11, VSLH11←TORD11) são avisos lançados no CNPJ errado: o fundo citado já tem o próprio evento.
- **C1 (cont.)**: a situação de todos os fundos (325) leva ~1 h — o FNET segura buscas seguidas (a resposta não vem até
  o tempo limite em ~40% das buscas em rajada). `refreshFnetStatus` passou a fazer uma busca por vez, com pausa, e
  `?missing=true` retoma só quem falta. Onde ainda não há arquivo de situação, R5 usa o critério do aviso mais novo
  (data-base até 7 dias).
- **C6 (21/10) — teste do zero:** `primevest-teste` recriado (`down -v`, V59) e carga inicial completa (3.372 s; proventos
  1.427 s, 54.856 eventos). Conferência no teste: A 0, B 0, D 0; **C 2** e **E-erro 1** — três casos que a carga nova
  ainda erra, todos com o mesmo valor do provento certo:
  - **CYCR11 (1073779) e PVBI11 (980533):** o administrador publicou o aviso sob o CNPJ de **outro** fundo (CBOP11,
    PLAG11) e depois o inativou; o XML traz o CNPJ do fundo certo. A situação era lida só do arquivo do próprio fundo.
    **R11:** a situação vale pelo id do aviso, em qualquer arquivo (`FnetClient.inactiveNoticeIds()`).
  - **EURO11 (11025, 19/04/2017):** recibo com ISIN malformado (BREURO01M15, 11 posições, sem o "R") e o **ticker da
    cota**, ao lado da cota (BREUROCTF011, R$ 1,23). **R12:** no dia em que o fundo tem a linha do ISIN da própria cota,
    linha com outro ISIN é de outro papel, mesmo com o ticker do fundo.
- **C6 — dev x teste (21/10):** o dev tinha 585 eventos de avisos inativos/cancelados. Comparados com o teste (chave
  natural): 236 com a mesma data-com e valor e **outro pagamento** no teste — a retificação que só muda o pagamento
  (MXRF11 1025775 → 1026624: 07/11 → 14/11/2025). Não era absorvida (R5 casava pelo pagamento) e o aviso ativo batia na
  chave do evento antigo. **R10:** com o aviso antigo inativo/cancelado no FNET, o evento da mesma data-com e valor recebe
  o pagamento e a referência do aviso ativo (id preservado). Os demais (data-base corrigida com o novo já gravado,
  cancelados sem substituto) não existem no teste → V60.
- `CorrecaoProventosIT` com R10–R12: 21 testes verdes. A conferência (`proventos.mjs`) passou a ler o ticker do formato
  antigo (`CodNegociacaoCota`) e aplica a regra do dia da cota antes da do ticker.
- **C6 — comparação final dev × teste (21/10, depois da rotina com R10–R12 nos dois):** teste com A–D 0 e dois erros que
  sobraram da carga (EURO11 11025, já existente antes de R12; e **URPR13**: o aviso 1335853 de URPR11 traz URPR11 R$ 0,30 e
  URPR13 R$ 0,078 com o **mesmo ISIN da cota** — **R13:** no aviso com a linha do ticker do fundo, linha com ticker de
  outra classe do mesmo fundo é da outra classe; sozinha no dia, vale — EXES11 20/10/2022 publicou o rendimento da cota só
  com o ticker EXES13). Dev × teste pela chave natural: o que só o dev tinha eram as classes A–E (536) + RTEL16 B3
  parcela 2 ao lado do FNET (nova regra A na conferência); 28 diferenças na 11ª casa (FNET que completou a B3 e ficou
  com o valor dela — sem efeito no provento) e 143 datas de pagamento diferentes (124 do histórico longo sem data no dev,
  que o teste recebeu da CVM/levantamento; 14 da B3 que mudou a data depois; 5 do FNET) — fora do escopo deste plano.
- **C7 (21/10):** `V60__limpeza_proventos_indevidos.sql` — 537 chaves (dev ∪ teste), aplicada no dev (536 eventos; 3
  proventos AUTO do Guilherme, R$ 31,98 = KLBN4 2 × 13,67 + TRXF11 4,64) e no teste (URPR13 e EURO11). Uma linha D
  (SPG211 29/04/2026, B3) saiu da lista: no dev o par dela era o próprio evento C apagado; no teste era o provento certo.
  Conferência no dev depois da V60: A–E **0**.
- **C8 (21/10):** rotina no dev depois da V60: 18 eventos voltaram pelo aviso ativo ou pela B3; **4 não voltaram**
  (CJCT11 amortização 07/11/2022, HAAA11 30/12/2025, MAXR11 amortização 07/10/2024, PLRI11 30/12/2024): o aviso ativo já
  tinha outra linha gravada e a rotina não relê aviso conhecido. Correção: `POST /proventos?fnet=todos` relê todos os
  avisos em disco; gravar e absorver são idempotentes e a linha que o próprio aviso já gravou (mesmo com o valor da B3)
  não duplica (`fromSameNotice`). `CorrecaoProventosIT` 24 + `ProventosCatalogoIT` 6 verdes. A pilha de teste foi
  apagada pelo Guilherme em 21/10 depois da comparação (volumes também).
  - `proventos?fnet=todos` no dev: 4 eventos novos — exatamente os 4 que faltavam; o resto relido sem duplicar.
  - B3 × B3_HISTORICO no mesmo provento: **0** grupos. Sem `B3_HISTORICO`: EQMA e MRSA (balcão; a B3 devolve histórico
    vazio — PENDENCIAS 33.4) e 6 emissores cujo histórico só tem o que os 12 meses já gravaram (não é falha).
  - Falhas da rodada: NAVT (B3 vazia, já conhecida) e rede da B3/FNET em PRIO, RECR, TGMA e TVRI11 (passageiras).
- **C9 (21/10):** conferência independente no dev: A–E **0** (54.938 eventos). Guilherme: 2024 **13,07**; 2025
  **1.230,60**; 2026 recebido até 20/10 **1.870,83**; a receber 165,58 (2026) e 80,71 (2027) — iguais aos da investigação.
  Proventos com pagamento ou data-com diferentes do evento: 0.
- **C10 (21/10):** `data push` feito. `mvn clean verify` verde: core 190; integration 103; adm-api 116 + 73; app-api
  210 + 261 (`SchemaTest` passou a esperar a V60). Docs: Claude.md, PENDENCIAS (33.3–33.5), ADM_TASKS [09],
  TESTES_FUNCIONAIS e este plano. **Plano concluído.**

