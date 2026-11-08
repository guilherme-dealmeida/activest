# Activest — Plano de agrupamento de títulos na Carteira

> Fase: **Encerrado em 07/11/2026** — as 7 etapas feitas; commit feito pelo Guilherme, que declarou o escopo finalizado.
> Origem: ESCOPO "Agrupamento de titulo Activest" (07/11/2026). A descrição é a conversa POR DÚVIDAS do mesmo dia
> (dúvidas 1 a 3 e o desdobramento sobre o tamanho da mudança), que virou POR ESCOPO (REGRAS_CLAUDE.md 2.9).
> Dor: o mesmo ativo aparece em várias linhas na Carteira (ex.: Tesouro IPCA+ 2040, um por compra; vários CDBs), sem
> uma visão do total por ativo.

---

## 0. Decisões já tomadas pelo Guilherme (07/11/2026)

| # | Decisão |
|---|---|
| G1 | Na linha-mãe, o que não se soma (emissora, distribuidora, data da aplicação, vencimento, remuneração, taxa, lotes) aparece como "—". |
| G2 | A linha-mãe tem o interruptor de reserva de emergência. |
| G3 | Ações de compra e venda na linha-mãe **e** nos títulos. |
| G4 | O Top 5 do Dashboard passa a ser por ativo (soma dos títulos). |
| G5 | Ativo com um único título continua em linha simples; só agrupa ativo com mais de um título. |
| G6 | A linha-mãe tem TWR próprio, calculado pela API (TWR não se soma entre títulos). |
| G8 | (07/11/2026, revisão do escopo) **Só o Tesouro Direto é agrupado.** A Renda Fixa continua com um título por linha, como hoje (o ativo é genérico — "CDB" — e agrupá-lo juntaria produtos diferentes). G1–G7 passam a valer só para o Tesouro; no Top 5 (G4), "por ativo" segue o mesmo agrupamento da Carteira (TD somado por ativo, RF por título). |
| G9 | (07/11/2026, Review) Revisa a G1: na linha-mãe, distribuidora, aplicação e vencimento aparecem quando são os mesmos em todos os títulos (senão "—"); a TAXA é a média ponderada pelo **valor inicial** dos títulos (R1, opção a). Chevron em coluna própria, "N títulos" embaixo do nome e títulos sem recuo (R2). Logo no Top 5 com as regras da Carteira (R3). |
| G7 | (D3; restrita ao Tesouro pela G8) A reserva de emergência passa a ser **por ativo**: todos os títulos do mesmo ativo estão ou não na reserva — não pode haver um título na reserva e outro fora. Trava no backend; no front o interruptor fica só na mãe (os títulos agrupados não têm interruptor). |

---

## D. Discovery

### D.1 Como a Carteira monta as linhas hoje

- `GET /api/portfolio/assets` (`PortfolioService.getPortfolioAssets`) devolve **uma linha por posição**
  (`portfolio_assets`). Posição com título (`note_id`) é por título; sem título, por ativo
  (`TransactionService`, ~linha 433).
- Só **Renda Fixa** e **Tesouro Direto** têm título (`asset_types.fixed_income = true`). Na RF o título é escolhido pelo
  usuário (ativo genérico "CDB", "LCI"...); no TD nasce um título a cada compra. Os demais tipos já são uma linha por
  ativo, então **o agrupamento só tem efeito nas abas Renda Fixa e Tesouro Direto**.
- A tabela (`PortfolioPage.tsx`, `PortfolioTypeTable`) é plana: ordenação por coluna (`useSortable`, padrão ticker A→Z),
  Totais da aba no rodapé (`utils/portfolioTotals.ts`, testado), flags de filtro acima (`PortfolioFlags`: na RF por
  ativo, no TD por categoria — todos os títulos de um ativo caem na mesma flag).
- Colunas das abas de títulos: ATIVO, EMISSORA (RF), DISTRIBUIDORA, DATA DA APLICAÇÃO (TD), VENCIMENTO,
  REMUNERAÇÃO (RF), TAXA, COTAS (TD), INVESTIDO, RESGATADO (RF), VALOR ATUAL (+ líquido estimado com o detalhe dos
  lotes), VALORIZAÇÃO, VARIAÇÃO, RENTABILIDADE (clássica + "TWR:"), RESERVA e AÇÕES (Listar, Comprar, Vender).

### D.2 Peças que a linha-mãe usa

| Peça | Hoje | Para a mãe |
|---|---|---|
| Valores (investido, resgatado, atual, líquido, valorização, cotas) | por posição no DTO | soma no front (mesma conta dos Totais) |
| Variação % e rentabilidade clássica | `classicReturn(...)` sobre a posição | mesma conta sobre as somas |
| TWR | `GET /performance/breakdown?level=POSITION` (níveis TYPE, CATEGORY, POSITION, ISSUER) | **não existe por ativo** → nível novo |
| Reserva | `PATCH /portfolio/assets/{id}/reservation`, uma posição por chamada, só renda fixa; título novo nasce fora da reserva | passa a valer para o ativo inteiro (G7) |
| Comprar/Vender | `presetFromPosition`: RF leva o título; TD leva a distribuidora da linha (venda do TD é PEPS por ativo + distribuidora) | preset sem título; distribuidora só se for uma só |
| Listar | `/transactions?position=<id>` | `/transactions?ticker=<ticker>` já existe (usado no TD) |

### D.3 Reserva hoje na base (consulta de 07/11/2026)

Nenhum ativo tem títulos **em aberto** misturados (parte na reserva, parte fora): CDB 2 abertos, ambos na reserva;
Tesouro Selic 2028/2029/2031 com os abertos na reserva; os demais todos fora. O único "misto" é um CDB **liquidado**
fora da reserva, sem efeito. Logo, a trava não exige correção de dados nem migration (REGRAS_CLAUDE.md 8.1).

Consequência da regra por ativo na Renda Fixa: o ativo é genérico ("CDB", "LCI"...), então **todos os CDBs** do
usuário ficariam juntos na reserva ou fora dela — motivo da G8 (só o Tesouro é agrupado).

**A Renda Fixa repete o comportamento do Tesouro?** Não por regra do sistema: na RF o aporte novo entra no **mesmo
título** (vira lote dele, uma linha só); no TD cada compra cria um título. Hoje os dois CDBs abertos são títulos
diferentes (PicPay 121% e 102% do CDI). Só haveria duas linhas do mesmo papel na RF se o usuário lançasse um título
novo com os mesmos dados de um existente em vez de aportar nele — `resolveNote` não impede título duplicado.

### D.4 Top 5 do Dashboard

`DashboardPage.tsx` (linha 151) ordena as posições por `currentValue` e pega 5 — hoje um título de TD/RF concorre
sozinho. O TWR vem do mesmo breakdown POSITION.

---

## I. Impact

| Componente | Mudança | Risco |
|---|---|---|
| `PerformanceService.breakdown` (app-api) | nível novo `ASSET` (agrupa os membros por tipo + ativo) | baixo: só acrescenta um ramo; os níveis atuais não mudam |
| `PortfolioService.setReservation` (app-api) | grava o valor em todas as posições do usuário do mesmo ativo, numa transação | baixo: mesmo endpoint e contrato; muda o alcance |
| Criação de posição (`TransactionService`, alocação do Tesouro) | título novo herda a reserva das posições abertas do mesmo ativo | médio: no Tesouro cada compra cria posição; sem herança a trava quebraria na compra seguinte |
| `PortfolioPage.tsx` (web) | linha-mãe expansível, agregados, interruptor de grupo, ações | médio: a tabela é a mais usada; ordenação, flags e Totais têm que continuar iguais |
| `DashboardPage.tsx` (web) | Top 5 por ativo + TWR por ativo | baixo |
| `utils/` (web) | função pura de agrupamento/agregados, com testes vitest | baixo |
| Tela Rentabilidade | nenhuma (continua por posição) | — |
| Banco, migrations, regras de venda/lotes/IR/IOF | nenhuma | — |

**Desempenho:** Carteira e Dashboard ganham uma chamada a mais do breakdown (nível ASSET). O contexto da rentabilidade
é recalculado por chamada (sem cache), como já acontece com POSITION e TYPE; o custo é o mesmo de um breakdown já
existente. Alternativa avaliada e descartada: devolver ASSET dentro da resposta POSITION (mudaria o contrato usado
pela tela Rentabilidade).

**Totais da aba:** continuam somando as posições — agrupar não muda o total. A regra do TWR parcial com flag
desligada continua igual.

---

## 2. Planning

### 2.1 Solução

**Backend**

1. `breakdown(level=ASSET)`: chave `typeName + assetId`; a linha devolve `ticker`, `typeName`, `label` = ticker e
   `detail` = "N títulos" (ou o nome, se um só). `positionId` nulo. O cálculo do TWR é o mesmo dos outros níveis
   (série do grupo encadeada).
2. Reserva por ativo (G7), sem endpoint novo: `PATCH /api/portfolio/assets/{id}/reservation` passa a gravar o valor
   em **todas** as posições do usuário do mesmo ativo (abertas e liquidadas), numa transação — só no Tesouro Direto;
   na Renda Fixa continua por título (G8). Ao criar posição nova
   de TD (cada compra), ela herda a reserva das posições abertas do mesmo ativo. Toda outra gravação
   de `is_reservation` passa pelo mesmo serviço.

**Front — Carteira**

3. `utils/portfolioGroups.ts`: agrupa as posições da aba por `ticker` (só o Tesouro Direto — G8); grupo com 1 título →
   linha simples (G5); com 2+ → mãe com os agregados. Testes vitest.
4. Linha-mãe: chevron para abrir/fechar; ATIVO com logo + ticker + "N títulos"; colunas G1 com "—"; COTAS (TD),
   INVESTIDO, RESGATADO (RF), VALOR ATUAL e líquido estimado somados (sem o detalhe de lotes, que fica nos títulos);
   VALORIZAÇÃO somada; VARIAÇÃO e RENTABILIDADE pelas somas; "TWR:" do nível ASSET (G6).
5. Títulos (filhos) logo abaixo da mãe, recuados, iguais às linhas de hoje.
6. Interruptor (G2/G7): só na mãe nos ativos agrupados; os títulos filhos ficam sem interruptor. Linha simples
   (um título) mantém o seu. O clique usa o endpoint de sempre, que já vale para o ativo inteiro.
7. Ações da mãe (G3): Listar → `/transactions?ticker=`; Comprar/Vender → modal com o ativo, sem título (ver D4/D5).
8. Ordenação: as mães ordenam pelos valores agregados junto com as linhas simples; os títulos ficam dentro da mãe
   (ver D2).

**Front — Dashboard**

9. Top 5 (G4/G8): soma as posições do Tesouro por ativo (os demais tipos já são uma por ativo; RF fica por título), soma valor atual/investido/valorização/proventos, ordena
   pelo valor atual; rentabilidade clássica pelas somas e TWR pelo nível ASSET. Ativo agrupado mostra "N títulos" sob o
   nome (ver D6).

### 2.2 Critérios de aceitação

- C0. A aba Renda Fixa fica como hoje (um título por linha, interruptor por título).
- C1. Na aba Tesouro Direto, o Tesouro IPCA+ 2040 (vários títulos) aparece numa linha-mãe; aberta, mostra os títulos.
- C2. Ativo com um título só aparece como hoje, sem chevron.
- C3. Valores da mãe = soma dos títulos (investido, resgatado, atual, líquido, valorização, cotas); Totais da aba
  inalterados.
- C4. TWR da mãe vem da API (nível ASSET) e bate com o TWR do ativo filtrado na tela Rentabilidade.
- C5. O interruptor da mãe liga/desliga todos os títulos numa operação; títulos filhos sem interruptor;
  Estratégia/Rebalanceamento refletem.
- C5.1. Backend: não é possível deixar títulos do mesmo ativo com reserva diferente; compra nova de TD de ativo na
  reserva já nasce na reserva (testes JUnit).
- C6. Comprar/Vender/Listar da mãe abrem o modal/extrato do ativo; os dos títulos continuam como hoje.
- C7. Top 5 do Dashboard por ativo, com o TWR por ativo.
- C8. Flags, ordenação e os dois temas funcionando; testes (vitest + JUnit) verdes; build do front e da API ok.

### 2.3 Decisões (respondidas em 07/11/2026)

D1, D2, D4, D5 e D6: **aprovadas pela recomendação** (Guilherme, 07/11/2026). D3: decisão própria (G7).

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| D1 | A mãe abre recolhida ou expandida? Lembra o estado? | (a) recolhida, sem lembrar; (b) expandida; (c) lembrar por navegador | **(a)** — é o objetivo do agrupamento; e a Carteira já volta ao padrão na troca de aba |
| D2 | Ordenação dos títulos dentro da mãe | (a) mesma coluna/sentido da tabela; (b) fixa (vencimento/data da aplicação) | **(a)** — coerente com o clique no cabeçalho |
| D3 | Interruptor da mãe com parte dos títulos na reserva | — | **Decidido pelo Guilherme (G7):** não existe estado misto; reserva por ativo, com trava no backend e interruptor só na mãe |
| D4 | Comprar/Vender pela mãe no Tesouro com distribuidoras diferentes | (a) preenche a distribuidora se for uma só; senão o usuário escolhe no modal; (b) nunca preenche | **(a)** |
| D5 | (sem efeito pela G8) Vender pela mãe na Renda Fixa | (a) modal sem título escolhido, o usuário escolhe no seletor de título (já existe); comprar = título novo | **(a)** |
| D6 | Top 5: mostrar "N títulos" sob o nome do ativo agrupado | (a) sim; (b) não, só o nome | **(a)** |

### 2.4 Etapas do plano

| Etapa | Conteúdo | Validação |
|---|---|---|
| E1 | Backend: nível ASSET no breakdown | JUnit (`PerformanceMathTest`/`PerformanceControllerTest`), build |
| E2 | Backend: reserva por ativo (propagação + herança na criação de posição) | JUnit (`PortfolioServiceTest` e testes de transação), build, reinício da API |
| E3 | Front: `utils/portfolioGroups.ts` + testes | vitest |
| E4 | Front: Carteira (mãe, filhos, interruptor, ações, ordenação) | lint/build, conferência no navegador (2 temas) |
| E5 | Front: Top 5 do Dashboard | lint/build, conferência |
| E6 | Docs: USE_CASES (Carteira), TESTES_FUNCIONAIS, andamento deste plano | — |

### 2.5 Quem faz

| Item | Claude | Guilherme |
|---|---|---|
| Decisões D1–D6 | recomendou | decidiu (07/11) |
| E1–E6 | implementa, testa, reinicia a API | aprova a execução |
| Conferência final na tela | — | confere |
| Commit / PR | sugere a mensagem | faz |

---

## 3. Andamento

- 07/11/2026 — Discovery, Impact e Planning feitos; decisões G1–G6 registradas; D1–D6 aguardando resposta.
- 07/11/2026 — D3 decidida pelo Guilherme (G7: reserva por ativo, trava no backend, interruptor só na mãe); D1, D2,
  D4, D5 e D6 aprovadas pela recomendação. Plano ajustado (E2 sem endpoint novo). Aguardando ordem de execução.
- 07/11/2026 — Escopo revisto pelo Guilherme (G8): só o Tesouro Direto é agrupado; Renda Fixa fica como hoje.
  Aguardando ordem de execução.
- 07/11/2026 — Guilherme mandou implementar (só TD; RF com título duplicado vira pendência — PENDENCIAS 46.1).
  E1–E6 feitas: nível `ASSET` no breakdown; reserva do Tesouro por ativo (propagação no `setReservation` e herança em
  `createTreasuryPurchase`); `utils/portfolioGroups.ts` (+7 testes); Carteira com linha-mãe recolhível, interruptor só
  na mãe e ações nas duas; Top 5 por ativo; docs (PENDENCIAS 46, USE_CASES, TESTES_FUNCIONAIS, Claude.md).
  Testing: `PortfolioIT` 16 (+3), `PerformanceIT` 7 (+1), `TransactionsIT`, `StrategyIT`, `AllocationIT` e unitários de
  performance/carteira verdes; Vitest 163/163; `tsc` sem erro novo (o `TRIGGER_HINT` não usado em `TasksPage.tsx` já
  existia). Conferido no navegador nos dois temas: Tesouro IPCA+ 2040 com 14 títulos numa mãe (TWR 1,79%), Totais
  inalterados, Top 5 com "14 títulos".
- 07/11/2026 — Review do Guilherme (G9) implementada pelas recomendações: `aggregatePositions` com os dados comuns e
  `weightedRate`; coluna do chevron; `components/ui/PositionLogo.tsx` (logo da Carteira, reaproveitado no Top 5).
  Vitest 165/165 (+2); conferido no navegador: IPCA+ 2040 com Nubank, vencimento 15/08/2040, aplicação "—" e
  IPCA + 7,28% a.a.; logos alinhados; Top 5 com logos.
- 07/11/2026 — Commit feito pelo Guilherme; escopo encerrado.
