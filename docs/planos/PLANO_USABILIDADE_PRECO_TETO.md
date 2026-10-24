# Activest — Plano de usabilidade dos valores do Preço Teto

> Fase: **Encerrado em 24/10/2026** — as 7 etapas feitas; escopo declarado encerrado pelo Guilherme, que faz o
> commit e o push da branch `TRACK_01-preco_teto_usabilidade`. Decisões D1–D9, D8 revisada e D8.2 registradas em 2.3.1.
> Origem: pedido de 24/10/2026 (ESCOPO "Usabilidade valores Preço Teto Activest"), com os anexos 1 (lucro do 4T25 no
> Preço Teto), 2 (Demonstrativos da PETR4 com o ícone de cópia ao lado dos valores) e 3 (modal "CAGR Lucros 5a" com
> as setas ‹ › marcadas).
> Dor: copiar e manipular números para calcular o preço teto.

---

## D. Discovery

### D.1 Item 1 — os campos de lucro do Preço Teto não deixam selecionar o valor

**Causa encontrada** (`activest-web/src/components/ui/MaskedNumberInput.tsx`): o campo tem `onFocus` e `onClick`
chamando `caretToEnd`, que no quadro seguinte (`requestAnimationFrame`) faz `setSelectionRange(fim, fim)` — joga o
cursor para o fim e **desfaz qualquer seleção**. Foi feito para a máscara, que cresce pela direita.

Por que parece intermitente (eventos concorrentes):

| Gesto | O que acontece | Resultado |
|---|---|---|
| Arrastar e soltar **dentro** do campo | o `mouseup` gera um `click` no input → `caretToEnd` | seleção some |
| Duplo/triplo clique | cada clique dispara `caretToEnd` | seleção some (ou pisca) |
| Arrastar e soltar **fora** do campo | o `click` vai para o elemento pai, não para o input | **seleção fica** — o "jeito muito específico" |
| Ctrl+A com o campo focado | não há clique | seleção fica |

O recálculo ao vivo (350 ms) não interfere: o valor exibido não muda, então o React não reescreve o texto.

**Achado 2 — colar também falha:** a máscara lê só dígitos e **soma** o colado ao que já está no campo (sem tudo
selecionado, colar `15.563.000.000,00` num campo com `32.705.000.000,00` vira lixo) e o **sinal se perde** (colar um
prejuízo `-17.040.000.000,00` entra positivo; o ± do `SignedNumberInput` não fica sabendo). Como a cópia do item 2
existe para colar aqui, isso entra no escopo (D3).

O componente é o mesmo em toda a aplicação: `CurrencyInput`, `PercentInput`, `SignedNumberInput` e os usos em
Transações (`TransactionFormModal`, `FixedIncomeFields`), Estratégia (`ReservationStrategyTab`), notificações
(`InformCostModal`), filtros da listagem de Ativos (`IssuerListingFilters`) e Preço Teto. A correção vale para todos.

### D.2 Itens 2 a 4 — Demonstrativos (`activest-web/src/components/issuer/FinancialStatements.tsx`)

| Hoje | Pedido |
|---|---|
| sem cópia; o valor exato só aparece no hover (simples) ou no modo detalhado | ícone de copiar ao lado de cada valor, copiando o valor detalhado (item 2) |
| `Toggle` Valor simples \| Valor detalhado (D11/D12/D23 do `PLANO_TELA_ATIVOS.md`) | sai (item 3) |
| 8 colunas na companhia, 12 no FII (`limit`) | 6 colunas por padrão (item 4.1) |
| `Toggle` Últimos 8 \| Todos (63) | sai; entram as setas ‹ › do `IndicatorHistoryModal` (item 4.2) |

Não existe hoje componente de cópia nem uso de `navigator.clipboard` no front. Os dados já chegam inteiros (todos os
trimestres/anos/meses numa chamada): a navegação é só recorte no navegador. **Nenhum backend envolvido.**

O valor detalhado hoje (`formatFull`) é `R$ 52.445.000.000,00` / `-R$ 17.040.000.000,00` / ações `12.888.732.761`.

### D.3 Dúvida — por que o Preço Teto usa o lucro da controladora e não o lucro líquido?

Porque o preço teto é **por ação**, e a ação é da controladora. O "Lucro líquido" da DRE consolidada
(`Lucro/Prejuízo Consolidado do Período`) inclui a parte do lucro das controladas que pertence a **sócios não
controladores** (minoritários das subsidiárias). Essa parte nunca chega ao acionista da PETR4: não vira dividendo
dele nem patrimônio dele. O lucro "atribuído a sócios da empresa controladora" é o que divide pelas ações (é o mesmo
número do LPA que a própria companhia publica). Pelo mesmo motivo o ROE usa o PL da controladora e o payout usa
dividendos pagos aos acionistas da controladora (`docs/PRECO_TETO_METODOLOGIA.md`, linhas 20–23) — as três premissas
ficam na mesma base.

Na PETR4 a diferença é pequena (2T26: 52,50 bi × 52,45 bi; 4T25: 15,65 bi × 15,56 bi), mas há empresas com
minoritários relevantes em que usar o consolidado infla o teto.

**A confusão é de rótulo:** o cartão do Preço Teto diz "Lucro líquido atribuível aos acionistas" e a linha que bate
com ele nos Demonstrativos se chama "Lucro da controladora", enquanto a linha "Lucro líquido" (que o usuário tende a
procurar) é outro número. Proposta em D8.

---

## I. Impact

| Componente | Efeito | Risco |
|---|---|---|
| `MaskedNumberInput` | cursor ao fim só quando não houver seleção; colar substitui o valor | **médio** — usado em ~10 telas; conferir digitação, backspace, Tab e `allowEmpty` (filtros) |
| `SignedNumberInput` | colar um número negativo liga o − | baixo |
| `FinancialStatements` | sai Valor simples/detalhado e Últimos/Todos; entram 6 colunas, ‹ › e cópia | baixo — componente isolado da tela de Ativos |
| componente novo de cópia (`CopyButton`, `components/ui`) | ícone + feedback | baixo |
| `navigator.clipboard` | só funciona em **contexto seguro** (https ou `localhost`). Acessando o Activest por IP da rede (`http://192.168…:5173`), falha | médio → fallback com `document.execCommand('copy')` |
| `IndicatorHistoryModal` | nenhum (só serve de modelo visual das setas) | — |
| Backend, banco, API | nenhum | — |
| Docs | `Claude.md` (tela de Ativos e componentes de UI), `PENDENCIAS.md` 36.4 (nota do detalhado deixa de existir), `PLANO_TELA_ATIVOS.md` (D11/D12/D23 superadas — registro) | — |

Dependência: nenhuma com outros escopos. Testes de front automatizados não existem no `activest-web` (E2E com
Playwright é escopo futuro): a validação é `tsc`/lint/build + conferência no navegador.

---

## 2. Planning

### 2.1 Solução

**S1 — Seleção no campo numérico (item 1).** `caretToEnd` só move o cursor quando a seleção estiver colapsada
(`selectionStart === selectionEnd`); o `onFocus` deixa de mexer no cursor durante um arraste do mouse (o clique que
encerra o foco faz o ajuste). Arrastar, duplo clique, triplo clique e Ctrl+A passam a selecionar; Ctrl+C copia o texto
mascarado (`15.563.000.000,00`).

**S2 — Colar no campo numérico (achado 2).** `onPaste` interpreta o texto colado como número pt-BR (aceita `R$`, `%`,
espaços, pontos de milhar, vírgula decimal, sinal `-` ou `−`) e **substitui** o valor do campo, respeitando as casas
do campo. No `SignedNumberInput`, número negativo liga o ±; campo sem `allowNegative` ignora o sinal. Texto que não é
número não altera nada.

**S3 — Copiar nos Demonstrativos (item 2).** Ícone de copiar (lucide `Copy`, vira `Check` por ~1,5 s) à direita de
cada valor numérico; copia o valor exato no formato de D2. Componente reaproveitável `CopyButton`.

**S4 — Sem Valor simples/detalhado (item 3).** A tabela mostra sempre o valor simples (abreviado, exato no hover); o
exato sai pela cópia. Sai a nota do detalhado (D23) do rodapé; o rodapé passa a dizer que o ícone copia o valor exato
em reais.

**S5 — 6 colunas e setas (itens 4.1 e 4.2).** Janela de 6 colunas, a mais recente à esquerda; ‹ › no lugar de
Últimos/Todos, com o mesmo botão do modal de indicadores; trocar Trimestre ↔ Anual volta ao início.

### 2.2 Critérios de aceitação

1. Nos campos de lucro do Preço Teto (e em qualquer campo numérico mascarado), arrastar dentro do campo, duplo e
   triplo clique selecionam e a seleção não some; Ctrl+C copia o valor.
2. Clique simples num campo sem seleção continua levando o cursor ao fim; digitação e backspace iguais a hoje.
3. Colar `52.445.000.000,00` num campo de lucro já preenchido deixa exatamente `52.445.000.000,00`; colar
   `-17.040.000.000,00` deixa `−` ligado e o valor certo; colar `12.888.732.761` no campo de ações dá 12.888.732.761.
4. Nos Demonstrativos, cada valor tem o ícone de copiar; ao clicar, o valor de D2 vai para a área de transferência
   e o ícone confirma; colado no Preço Teto, bate com o padrão (etiqueta PADRÃO volta).
5. Não existe mais o seletor Valor simples | Valor detalhado.
6. Abrem 6 colunas; ‹ › percorrem o histórico até os limites (desabilitados nas pontas); Últimos/Todos não existe.
7. Funciona em Trimestre, Anual e no informe mensal do FII (conforme D6).
8. `tsc`, lint e build do `activest-web` verdes; conferência no navegador com a PETR4 e um FII.

### 2.3 Decisões (para o Guilherme responder)

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| **D1** | Ícone de copiar: sempre visível ou só no hover? (6 colunas × 17 linhas = 102 ícones) | a) sempre visível, discreto (cinza), dourado no hover · b) só no hover da célula | **a** — o hover não existe no celular e o anexo 2 mostra o ícone fixo |
| **D2** | Formato copiado | a) `52.445.000.000,00` (pt-BR, sem `R$`) · b) `52445000000,00` (sem milhar) · c) como exibido hoje no detalhado, `R$ 52.445.000.000,00` | **a** — cola certo no Preço Teto (com S2), no Excel/Sheets em pt-BR e é legível. Negativo com `-`; ações sem casas; percentual e R$/cota do FII com 2 casas, sem símbolo |
| **D3** | Colar no campo numérico substitui o valor (S2) entra neste escopo? | sim · não | **sim** — sem isso a cópia do item 2 só funciona selecionando tudo antes, e prejuízo entra positivo |
| **D4** | Passo das setas | a) 1 coluna por clique · b) 6 colunas (página) por clique | **b** — 63 trimestres seriam 57 cliques; com página são ~10 |
| **D5** | Sentido das setas (a mais recente fica à esquerda) | a) › mostra as mais antigas (a tabela "continua" para a direita) e ‹ volta para as recentes · b) o contrário (‹ = passado, como uma linha do tempo) | **a** — segue a ordem visual da tabela; o hover diz "Mais antigos" / "Mais recentes" |
| **D6** | Vale para o informe mensal do FII (hoje 12 colunas)? | a) 6 colunas e setas também · b) FII mantém 12 colunas, só com as setas | **a** — mesmo comportamento na tela inteira |
| **D7** | Mostrar a posição da janela ao lado das setas (ex.: "2T26 – 1T25 · 6 de 63")? | sim · não | **sim**, discreto — substitui a informação que o "Todos (63)" dava |
| **D8** | Rótulos do lucro (dúvida) | a) Preço Teto: "Lucro da controladora — 4 últimos trimestres" com ⓘ ("lucro atribuído aos sócios da controladora; exclui a parte dos não controladores; é a linha 'Lucro da controladora' dos Demonstrativos"); Demonstrativos: "Lucro líquido (consolidado)" e "Lucro da controladora", com ⓘ · b) só o ⓘ, sem trocar rótulos · c) nada | **a** — o nome igual nas duas telas resolve o "bater os valores" |
| **D9** | Setas também por teclado (← →), como no modal? | sim · não | **não** — na página, ← → rolam a tela e brigariam com os campos |

### 2.3.1 Decisões tomadas (24/10/2026)

| # | Decisão |
|---|---|
| D1 | ícone de copiar sempre visível, discreto (cinza), dourado no hover — recomendação aprovada |
| D2 | formato copiado `52.445.000.000,00` (pt-BR, sem `R$`; negativo com `-`; ações sem casas; % e R$/cota do FII com 2 casas, sem símbolo) — recomendação aprovada |
| D3 | colar no campo numérico substitui o valor (e liga o ± no negativo) entra no escopo — recomendação aprovada |
| D4 | setas andam 6 colunas (uma página) por clique — recomendação aprovada |
| D5 | **›** vai para os períodos mais antigos e **‹** volta para os mais novos — confirmado pelo Guilherme |
| D6 | informe mensal do FII também com 6 colunas e setas — recomendação aprovada |
| D7 | posição da janela ao lado das setas ("2T26 – 1T25 · 6 de 63") — recomendação aprovada |
| D8 | **refeita pelo Guilherme (24/10/2026):** ver D8 revisada abaixo (substitui a resposta anterior e a D8.1) |
| D9 | setas sem atalho de teclado — recomendação aprovada |

**D8 revisada (24/10/2026, Guilherme)** — ele tinha entendido que o lucro da controladora era o consolidado; com a
explicação, propôs:

| Tela | Hoje | Passa a ser | ⓘ |
|---|---|---|---|
| Demonstrativos | Lucro líquido | **Lucro líquido consolidado** | lucro do grupo inteiro (controladora + controladas), incluindo a parte que pertence aos sócios não controladores |
| Demonstrativos | Lucro da controladora | **Lucro líquido da controladora** | parte do lucro consolidado atribuída aos acionistas da companhia; é a base do LPA e do Preço Teto |
| Demonstrativos | — | **+ Lucro líquido não controladores** (linha nova, logo abaixo) | parte do lucro das controladas que pertence aos sócios minoritários delas; não chega ao acionista da companhia |
| Preço Teto | Lucro líquido atribuível aos acionistas — 4 últimos trimestres | **Lucro líquido da controladora — 4 últimos trimestres** | "Lucro líquido atribuível aos acionistas" |

Vale nas bases Trimestre e Anual (o FII não tem essas linhas).

**D8.2 — de onde sai a linha "não controladores". Decidido em 24/10/2026: (a), só a subtração na tela, sem gravar no banco.** Discovery: a CVM publica a conta ("Atribuído a
Sócios Não Controladores", filha do lucro consolidado na DRE) e o `FinancialMetricRules` já a lê para decidir a quebra,
mas **não grava** — só existem `LUCRO_LIQUIDO` e `LUCRO_CONTROLADORA` em `company_quarters`.

| Opção | Como | Efeito |
|---|---|---|
| a) | calcular na tela: consolidado − controladora (identidade contábil da DRE) | só front, escopo continua sem backend; quando a companhia só publica o individual (sem quebra), dá **R$ 0** |
| b) | gravar a métrica nova na adm-api (`FinancialMetric`, migration V7+, `company_quarters`, API) e recarregar os balanços (`CVM_STATEMENTS_IMPORT`) | valor "da fonte"; backend, migration e recarga entram no escopo |

Recomendação: **a** — o número é o mesmo (a CVM fecha consolidado = controladora + não controladores) e o escopo
continua só de tela.

### 2.4 Etapas do plano

| Etapa | O que | Arquivos |
|---|---|---|
| **U1** | S1 + S2: seleção e colar no campo numérico (e o sinal no `SignedNumberInput`) | `components/ui/MaskedNumberInput.tsx`, `components/ui/SignedNumberInput.tsx`, util de parse pt-BR em `utils/` |
| **U2** | `CopyButton` (clipboard + fallback) e cópia nos Demonstrativos (S3) | `components/ui/CopyButton.tsx`, `components/issuer/FinancialStatements.tsx` |
| **U3** | S4 + S5: sem simples/detalhado, 6 colunas, ‹ ›, posição da janela | `components/issuer/FinancialStatements.tsx` |
| **U4** | D8 revisada — rótulos, ⓘ e linha de não controladores | `pages/CeilingPriceDetailPage.tsx`, `components/issuer/FinancialStatements.tsx`, `docs/PRECO_TETO_METODOLOGIA.md` |
| **U5** | Testing (tsc, lint, build, navegador) e Review; docs (`Claude.md`, `PENDENCIAS.md` 36.4, nota de registro no `PLANO_TELA_ATIVOS.md`) | — |

### 2.5 Quem faz

| Atividade | Quem |
|---|---|
| Responder D1–D9 e aprovar a Implementation | Guilherme |
| U1–U5 | Claude |
| Conferência final no navegador logado (caminho 3) | Guilherme, depois da do Claude |
| Commit e PR para a `develop` | Guilherme (sugestão de mensagem pelo Claude na etapa 7) |

### 2.6 Andamento

| Data | Etapa | Situação |
|---|---|---|
| 24/10/2026 | Discovery, Impact, Planning | feitos |
| 24/10/2026 | Decisões | D1–D9 respondidas (D5 confirmada); D8 refeita pelo Guilherme; D8.2 = (a); Implementation aprovada |
| 24/10/2026 | U1 | `MaskedNumberInput`: cursor ao fim só sem seleção, conferido no quadro seguinte (no clique o Chrome ainda informa a seleção anterior — achado no teste); colar substitui (`utils/numberPaste.ts`), `onPasteValue` liga o ± no `SignedNumberInput` |
| 24/10/2026 | U2 | `CopyButton` + `utils/clipboard.ts` (fallback `execCommand` fora de contexto seguro); cópia em todos os valores dos Demonstrativos |
| 24/10/2026 | U3 | sem Valor simples/detalhado e Últimos/Todos; janela de 6 com ‹ ›, presa nas pontas (a última mostra as 6 mais antigas); posição "2T26 – 1T25 · 6 de 63"; Trimestre ↔ Anual volta ao início |
| 24/10/2026 | U4 | Lucro líquido consolidado / da controladora / não controladores com ⓘ (Trimestre e Anual); cartão do Preço Teto renomeado com ⓘ |
| 24/10/2026 | U5 | `tsc -b` e `eslint src` verdes; `vite build` verde no container web. Navegador (PETR4): arraste, duplo e triplo clique selecionam o lucro do 4T25 e o clique simples leva o cursor ao fim; colar `52.445.000.000,00`, `-17.040.000.000,00` (liga −), `abc` e `R$ 52,45 bi` (ignorados) e `15.563.000.000,00` (volta PADRÃO); ações ignoram o sinal; 6 colunas, ‹ › até 4T10 e de volta, Anual "2026 · 6m – 2021 · 6 de 17"; cópia real do lucro da controladora 4T25 = `15.563.000.000,00` com ✓; HGLG11 com 6 meses, "6 de 117", DY `0,71`. Docs: `Claude.md`, `README.md`, `PENDENCIAS.md` 36.4/36.5, `PRECO_TETO_METODOLOGIA.md`, nota no `PLANO_TELA_ATIVOS.md` |
| 24/10/2026 | Encerramento | escopo encerrado pelo Guilherme; commit e push com ele |
