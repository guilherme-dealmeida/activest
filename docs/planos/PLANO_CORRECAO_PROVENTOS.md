# PrimeVest — Plano de Correção dos Proventos (catálogo e carteira)

> Fase: **planejamento** (21/10/2026). Nada implementado. Implementação só com ordem explícita (REGRAS_CLAUDE.md 2.2).
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

- 21/10/2026: plano criado; aguarda a ordem de execução.
