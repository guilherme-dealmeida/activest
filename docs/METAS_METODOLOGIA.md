# Activest — Metodologia das Metas Financeiras

Plano, decisões e andamento: `docs/planos/PLANO_METAS.md`. Este documento diz **como cada número das metas é
calculado**, de onde vem e quando é atualizado.

## 1. Princípio

O usuário define o objetivo (tipo, alvo, prazo, escopo, parâmetros e, se quiser, as premissas das projeções); o
Activest calcula situação, progresso, quanto falta e a projeção a partir da carteira. Nenhum cadastro pede valor
atual, progresso, falta, data estimada, renda atual, alocação ou concentração (G1): o campo é recusado com 400.

Sem dado suficiente a meta diz qual é a limitação — nunca mostra zero no lugar do ausente nem estimativa como
garantia (G3, G4).

## 2. Natureza de cada número (G2)

| Natureza | O que é | Exemplo |
|---|---|---|
| **Observado** | Dado registrado | transação, provento pago, cotação, índice publicado |
| **Calculado** | Regra fixa sobre dados observados | valor atual, progresso, média, rentabilidade |
| **Estimativa** | Depende de premissa | projeção, data estimada, aporte necessário |

A API devolve a natureza de cada número; a tela mostra o rótulo e pinta a estimativa no dourado.

## 3. Fontes (mesmo número das outras telas — G7)

| Dado | Fonte | Mesmo número de |
|---|---|---|
| Patrimônio (valor atual **bruto**, P2) | `PortfolioService.getPortfolioAssets` (custo + valorização − resgatado) | Card Patrimônio do Dashboard, Carteira |
| Aporte | compras − o que as vendas devolveram (líquido na RF e no Tesouro, quando existe) — D6 | Card Aportes do Dashboard |
| Renda passiva | proventos **líquidos** recebidos (D5), sem amortização e restituição de capital (devolvem capital) | Tela Dividendos |
| Rentabilidade | TWR bruto da linha do tempo da Rentabilidade (`ScopeSeries`, `ReturnMath`) — I1 = a | Tela Rentabilidade (base bruta) |
| Estratégia | `StrategyService.getRebalanceSuggestion` (base sem a reserva) | Rebalanceamento |
| Índices | `market_index_values` pelo `MarketIndexCalculator` (CDI, IPCA) e o `BenchmarkData` da Rentabilidade | Índices, Rentabilidade |

A conferência independente é `node carga/scripts/conferencia/metas.mjs` (preview × telas, sem gravar).

## 4. Escopo

Carteira total, ou por tipo, categoria, indexador (CDI, Selic, IPCA, IGP-M, INPC, prefixado, renda variável), emissor
(grupo da emissora na renda fixa; companhia/fundo nos demais), ativo ou posições escolhidas; com ou sem a reserva de
emergência (ou só ela) e com exclusão de tipos e categorias. Patrimônio exige a escolha explícita da reserva (D7);
alocação e concentração nascem **sem** a reserva, como a Alocação e o Rebalanceamento. Região e moeda ficam para o
escopo de outras moedas (D16): tudo é em reais.

## 5. Regras por tipo

| Tipo | Indicador | Sucesso |
|---|---|---|
| PAT-1 Patrimônio-alvo | Σ valor atual das posições abertas do escopo; decomposição desde a primeira transação: aportes líquidos, resultado de mercado (valor − aportes) e proventos recebidos à parte | valor ≥ alvo |
| APO-1 Acumular em aportes | compras − vendas desde o início da contagem (padrão: criação da meta) | acumulado ≥ alvo |
| APO-2 Aporte por período | aporte líquido de cada mês/ano fechado; cumprido se ≥ valor − tolerância; sequência atual e maior | sequência ≥ desejada (ou todos os períodos até o fim) |
| REN-1 Renda passiva | soma dos proventos líquidos dos **k meses fechados** ÷ k (D10: nunca ÷ meses de histórico, nunca pagamento isolado × 12); anual = soma dos 12 meses fechados | média (ou soma anual) ≥ alvo |
| REN-2 Renda cobre despesas | renda média ÷ despesa (corrigida pelo IPCA desde a data informada, se escolhido) | cobertura ≥ % desejado |
| REN-3 Renda > aportes | renda média ÷ aporte líquido médio na janela | renda ≥ aporte (aporte ≤ 0: atingida) |
| RNT-1 Rentabilidade-alvo | TWR no período (desde o início, no ano, 12 meses, entre datas); real = (1+nominal)/(1+IPCA)−1 até o último IPCA; anualizada só com ≥ 12 meses | ≥ alvo |
| RNT-2 Superar um índice | carteira − índice no mesmo intervalo (encurtado até o último dado do índice); % do índice; IPCA + x% | diferença ≥ margem |
| ALO-1 Participação | valor do item ÷ base; aportar no item `(p·T − C)/(1 − p)`, aportar fora `C/p − T`, reduzir `(C − p·T)/(1 − p)` — informativo | dentro do mínimo/máximo/faixa (limite exato dentro) |
| ALO-2 Seguir a estratégia | maior \|% atual − ideal\| por tipo; distância Σ\|desvio\|/2 | todos dentro da tolerância |
| RIS-1 Concentração | maior participação de um item da dimensão; itens acima com o excesso | nenhum item acima do limite |
| RIS-2 Queda do pico | cota ÷ pico − 1 na janela (sobre a cota: resgate não é perda) | queda ≤ limite |
| RIS-3 Qualidade dos dados | posições com cotação defasada, sem cotação, sem curva, bonificação sem custo; proventos sem data; importação aguardando | pendências ≤ máximo |
| ATV-1 Quantidade | cotas da Carteira (com desdobramento, grupamento e bonificação) | cotas ≥ alvo |
| ATV-2 Valor num ativo | valor de mercado ou custo das posições do ticker | ≥ alvo |
| ATV-3 Proventos compram cotas | provento líquido médio mensal por cota (12 meses fechados) × cotas ÷ preço | cotas/mês ≥ alvo |
| LP-1 Aposentadoria | patrimônio e renda projetados na data (nominal e em valores de hoje) | estimativa: no ritmo / atrasada só com renda desejada |

## 6. Projeções (Anexo A 6 do plano)

Mês a mês, juros compostos, aporte no fim do mês (`GoalProjection`, core):

- valor no prazo `PV(1+i)^n + PMT((1+i)^n − 1)/i`;
- aporte necessário `(FV − PV(1+i)^n) i / ((1+i)^n − 1)` (≤ 0 → "não precisa de aporte");
- meses até o alvo `ln((FV·i + PMT)/(PV·i + PMT)) / ln(1+i)` (sem aporte e sem rendimento, ou mais de 100 anos →
  "não alcança com as premissas atuais");
- valor real `nominal / (1 + inflação)^(n/12)`; renda estimada = patrimônio projetado × yield ÷ 12.

Premissas — automáticas (recalculadas a cada avaliação) ou informadas (fixas na meta):

| Premissa | Automática (padrão) |
|---|---|
| Retorno nominal a.a. | CDI acumulado em 12 meses (D12); opção: TWR anualizado da carteira, com ≥ 24 meses |
| Aporte mensal | média do aporte líquido dos 12 meses fechados do escopo (menos meses de posição: média deles) |
| Inflação a.a. | IPCA acumulado nos 12 últimos meses divulgados |
| Yield | renda líquida dos 12 meses fechados ÷ valor de hoje do escopo |

APO-1 e ATV-1 (sem rendimento) usam o **ritmo linear** dos 12 meses fechados (D14). Sem dado para uma premissa
automática a projeção fica suspensa com o motivo; o resto da meta segue calculado. Valores estimados em R$ saem
arredondados a R$ 100 (D19).

## 7. Situação, qualidade e ciclo

- **Crescente**: atingida → **concluída sozinha** com aviso (D13); com prazo: no ritmo × atrasada pela projeção;
  prazo passou sem atingir → prazo vencido (resultado guardado no prazo).
- **Controle** (limite, faixa, estratégia, qualidade, queda, rentabilidade): dentro × fora do objetivo; com prazo e antes
  dele, fora = em andamento. Não se concluem sozinhas.
- **Qualidade**: COMPLETA, PARCIAL (cotação com mais de 3 dias úteis — D9 —, sem cotação valorizada pelo custo — D8 —,
  sem curva, bonificação sem custo, provento sem data, importação aguardando; só marca) ou INDISPONIVEL. Preço que falta
  segue a regra da Rentabilidade (P3): é carregado (último preço, preço da compra ou valor aplicado) desde 25/10/2026;
  só fica indisponível, com o ativo nomeado, quando não há número nenhum.
- **Ciclo**: ativa, pausada (para a fotografia e os avisos), concluída (resultado congelado), arquivada; excluir só a
  arquivada (D15). Editar cria uma revisão; o tipo não muda.

## 8. Atualização, histórico e avisos

| Quando | O quê |
|---|---|
| Leitura | avaliação na hora, com os dados de hoje (uma leitura dos fatos para todas as metas do usuário) |
| Criar, editar, retomar | fotografia do dia na hora |
| Transação gravada / "catálogo mudou" | o usuário é marcado e as metas ativas dele são reavaliadas pelo worker (15 s; várias transações = uma reavaliação) |
| Tarefa `GOALS_SNAPSHOT` (21:30, depois do `POSITIONS_REFRESH`) | fotografia diária de todas as metas ativas, eventos e avisos |

- **Fotografia** (`goal_snapshots`, upsert por meta e dia): o que a meta mostrava naquele dia, com a revisão; não se
  reescreve. A **evolução** do gráfico é recalculada com os dados de hoje, inclusive antes da meta existir.
- **Eventos** (`goal_events`): criada, editada (com a definição completa da revisão), pausada, retomada, concluída,
  arquivada, desarquivada, atingida, saiu/voltou ao objetivo, prazo vencido.
- **Avisos** (categoria METAS, D20; liga/desliga por meta): concluída e prazo vencido (informativos); fora do objetivo,
  atrasada (só na virada de "no ritmo") e sem dados (pendências que se resolvem sozinhas quando a condição volta).
- **Memória de cálculo**: a API devolve `evaluation.steps`; a tela não mostra (D21, como o Preço Teto).

## 9. Limitações

- Sem conta-corrente: reinvestir provento conta como aporte (L4).
- Juros da renda fixa privada e cupons do Tesouro não são proventos: ficam fora da renda passiva (L5).
- Gestor não existe no modelo (L7); despesas pessoais são informadas na própria meta (L9).
- Cripto além do BTC tem histórico só dos últimos 365 dias (CoinGecko); tipos `MANUAL` sem cotação valem pelo custo.
- Outras moedas e metas em outra moeda: escopo próprio (D16).
