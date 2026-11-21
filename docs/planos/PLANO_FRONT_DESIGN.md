# PLANO — MELHORIA DO FRONT-END (tema "Instrumento")

> Escopo de 20/11/2026, concluído no mesmo dia. **Só visual**: nenhuma regra de
> negócio, cálculo, tela ou endpoint mudou. O backend não foi tocado.
>
> Antes: nenhum escopo de design do front existed — o tema era um accident e
> cada tela acrescentava a sua própria camada.

## 1. O pedido

> "Analise o design do front-end e faça as melhorias de design e código
> necessárias para que o aplicativo não fique genérico. Analise os temas da
> aplicação e proponha as melhores estéticas para serem implementadas. Faça a
> proposta para que eu escolha e, quando eu escolher, implemente. Não faça
> nenhuma alteração na lógica, de modo que o sistema continue funcionando da
> mesma forma que hoje. Quero apenas alterações visuais de front-end."

## 2. O diagnóstico (o que fazia o app parecer genérico)

| # | Achado | Onde |
|---|--------|------|
| 1 | A cor vivia em três lugares que não se conheciam: `tailwind.config.js`, um override de `html.light .text-white/NN` para cada classe com opacidade, e hex hardcoded em cada gráfico | `index.css`, `tailwind.config.js`, 8 arquivos de gráfico |
| 2 | O tema claro era a paleta escura com o texto trocado | `html.light` só reescrevia texto |
| 3 | Sem elevação: fundo `#000`, cartão `#111`, borda `#2a2a2a` — três cinzas que não se distinguiam | todo o app |
| 4 | Uma fonte só, uma voz só: o número era idêntico ao rótulo | todo o app |
| 5 | O dourado da marca não significava nada — marca, botão, aba, chip e série da carteira ao mesmo tempo | todo o app |
| 6 | O verde e o vermelho do Tailwind (`green-500`, `red-500`) não foram recalibrados para o escuro e eram o único sinal de positivo e negativo | 30+ pontos |
| 7 | Sobrou o verde do PrimeVest: `primary: '#0d2818'` | `tailwind.config.js` |
| 8 | Escala de raio e ritmo incoerentes: `rounded-xl` + `border-surface-light` + `p-5` no `Card`, `rounded-lg` + `border-white/10` + `p-6` no Dashboard | `Card.tsx`, `DashboardPage.tsx` |
| 9 | Dez (i) de informação na tela de Rentabilidade, todos do mesmo peso | `PerformancePage.tsx` |
| 10 | Movimento: zero transição, zero esqueleto de carga | todo o app |
| 11 | Em tela estreita o menu fixo de 256 px comia a tela e o conteúdo era cortado | `Sidebar.tsx` (antes: `w-64 shrink-0`) |

## 3. As três estéticas propostas

| | Nome | A ideia |
|---|---|---|
| **A** | **Instrumento** (escolhida) | O app como instrumento de leitura: grafite, fio de separação em vez de borda, dourado da marca com um papel só, número como herói, cabeçalho de tabela como placa. É o que o produto é: uma planilha de dinheiro que precisa ser lida com atenção. |
| B | Editorial | Serifa nos números, muito branco, par de fontes, colunas largas. Bonito, mas o app vira um texto — e o usuário precisa de densidade, não de respiro. |
| C | Terminal | Monoespaçada em tudo, monocromática, sem cor. Coerente, mas mata a leitura de resultado (verde/vermelho) e a marca dourada. |

## 4. A escolha (20/11/2026)

- Estética: **A — Instrumento**.
- Tema claro: **refazer de verdade** (tokens próprios, não override).
- Escopo: **todas as telas**.

## 5. Decisões

| # | Decisão |
|---|--------|
| D1 | **Cor por papel semântico, em variável CSS.** `:root` e `html.light` têm os mesmos nomes de papel com valores diferentes: `bg`, `surface`, `elevated`, `raised`, `line`/`line-soft`/`line-strong`, `overlay`, `selected`, `content`…`content-4`, `accent`, `accent-text`, `on-accent`, `up`/`down`, `info`, `--chart-*`. O override `html.light .text-white/NN` — e a armadilha que ele escondia — **saiu**. |
| D2 | **`text-accent` nunca.** O dourado de preenchimento (`#c9a84c`) dá 2,2:1 sobre papel. Como texto, `text-accent-text` (escuro `#e0c274`, papel `#8a6d1f`). 134 pontos trocados; nenhum `text-accent` fica sobre dourado sólido. |
| D3 | **Cor de tipo de ativo não é cor da interface.** `asset_types.color_hex` é identidade do tipo (FII verde, ação vermelho) e tem companheira na tela (o ícone do tipo). **Não** foi harmonizada — a rosca da Alocação é a única coisa colorida da tela, e continua colorida de propósito. |
| D4 | **Duas raizes só:** `rounded-panel` (0,75 rem) e `rounded-control` (0,5 rem). 186 pontos trocados. |
| D5 | **Voz tipográfica:** cabeçalho de tabela é rótulo (11 px, semibold, maiúsculo, `tracking 0.07em`) por uma regra só (`table th` no `index.css`); rótulo de seção e de linha nos cards em 10 px maiúsculo; número grande com `tracking-tight` e `tabular-nums`. |
| D6 | **Uma moldura de gráfico só** (`src/theme/chart.ts`): grade, eixo, cursor e balão saem de `chartTheme()`. As cores categóricas (carteira, benchmarks) continuam no módulo que as conhece. |
| D7 | **O dourado tem um papel só na navegação:** item ativo do menu (trilho + texto), aba selecionada, ação de tabela. |
| D8 | **Menu em gaveta abaixo de `lg`** (1024 px): fora do fluxo, sobre um véu, fechado ao trocar de tela. O item de menu recolhido continua recolhido só na tela larga. |
| D9 | **Paleta crua do Tailwind eliminada:** amarelo/vermelho/verde/azul/laranja/âmbar/sky (70 pontos) viraram token (`accent`, `danger`, `success`, `info`). `info` é o token novo. |
| D10 | **Nenhum item de menu mudou de lugar.** Os rótulos de grupo (POSIÇÃO, ANÁLISE, PLANEJAMENTO, MERCADO, ADMINISTRAÇÃO) só separam a lista em blocos. |
| D11 | **Nenhuma regra de negócio mudou.** Nenhuma tela, cálculo, endpoint ou migration foi tocado. |
| D12 | **Sem esqueleto de carga:** o `LoadingSpinner` continua. Esqueleto é um componente novo com medição de layout — fica para um escopo próprio. |

## 6. O que foi implementado

| Etapa | O quê | Onde |
|-------|-------|------|
| 1 | Tokens de cor (dois temas), escala de raio, duração de transição | `src/index.css`, `tailwind.config.js` |
| 2 | Migração das classes do app para os tokens | 116 arquivos de `src/` |
| 3 | Tema dos gráficos unificado (8 cópias → 1) + paleta categórica validada | `src/theme/chart.ts` (novo) |
| 4 | Menu agrupado, cabeçalho com seção, layout com elevação | `components/layout/*` |
| 5 | Voz tipográfica, raio, abas com destaque dourado, paleta crua fora | `index.css`, `Card.tsx`, `DashboardPage.tsx`, 21 arquivos |
| 6 | Menu em gaveta na tela estreita | `Sidebar.tsx`, `Header.tsx`, `Layout.tsx` |

## 7. O que ficou de fora (e por quê)

| Item | Por quê |
|------|---------|
| Cor da rosca da Alocação | `asset_types.color_hex` é dado de catálogo (migration) e tem companheira na tela (o ícone do tipo). Mudar é mudança de dado, fora do escopo visual — e a paleta é identidade do tipo. **Pendência 51.1.** |
| Esqueleto de carga | Componente novo com medição de layout (D12). |
| Tela em modo "vinte", fontes com personality | Serifa em coluna de valor quebra a leitura de tabular; fora do escopo escolhido. |
| `docs/` dos planos antigos | São registro histórico; o que vale é a seção "Padrão Visual" do `CLAUDE.md`, que foi reescrita. |

## 8. Verificação

- `docker exec activest-web npx tsc -b` → limpo.
- `docker exec activest-web npx eslint .` → **0 erro, 0 aviso**.
- `docker exec activest-web npm test` → **195 testes, 21 arquivos, verdes**.
- Playwright (npm `playwright`, `channel: 'chrome'`): 16 telas × 2 temas em 1440×900 e as telas-chave em 1024/768/390. **Nenhum erro de console, nenhum `pageerror`, nenhum overflow horizontal.** A única mensagem é o 403 do widget do Google (`[GSI_LOGGER] origin not allowed`), que é do ambiente, não do app.
- Capturas em `.claude/logs/ui-final2/` (1440, dois temas), `.claude/logs/ui-narrow/` (1024/768/390) e `.claude/logs/ui-final/` (a rodada anterior).

## 9. Andamento

| Etapa | Estado |
|-------|--------|
| 1. Fundação (tokens, tema claro, paleta) | concluído |
| 2. Migração das classes | concluído |
| 3. Unificação do tema dos gráficos | concluído |
| 4. Tipografia, chrome do menu/cabeçalho, movimento | concluído |
| 5. Conferência no navegador e testes | concluído |
