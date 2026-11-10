# Plano — Customizar tamanhos no Activest

> Escopo aberto em 10/11/2026 pelo Guilherme. Etapas 1 a 3 (Discovery, Impact, Planning) feitas em 10/11/2026.
> **Implementação só com aprovação explícita** (REGRAS_CLAUDE.md 2.2).

## 1. Objetivo

Um interruptor de **tamanho da tela**, no molde do tema claro/escuro, com dois valores:

- **Grande**: o tamanho de hoje (padrão).
- **Pequena**: tudo reduzido na mesma proporção, como um zoom (fontes, espaçamentos e larguras juntos).

O controle fica no cabeçalho, ao lado do Tema (ícones **A−** / **A+**), e também em **Configurações → Preferências**,
como o tema. A escolha é guardada no usuário (vale em qualquer navegador), com cópia local para a tela de login e o
primeiro desenho.

Origem: DÚVIDAS de 10/11/2026 — o Guilherme seguiu a recomendação de "zoom geral" (fonte e espaçamento juntos) em vez
de reduzir só as letras.

## 2. Discovery — como é hoje

### 2.1 Tema (o modelo a copiar)

| Camada | Onde | O que faz |
|---|---|---|
| Banco | `users.theme VARCHAR(10) NOT NULL DEFAULT 'DARK'`, `ck_users_theme` (DARK, LIGHT) — `V1__schema.sql` | guarda o tema por usuário |
| Core | `User.theme` (`UserTheme`: DARK, LIGHT) | entidade |
| app-api | `PUT /api/v1/users/me/preferences` com `PreferencesDTO(@NotNull UserTheme theme)` → `UserService.updatePreferences`; `UserResponseDTO.theme` em `GET /users/me` | grava e devolve |
| Front | `ThemeContext.ts` / `ThemeProvider.tsx`: estado inicial do `localStorage` (`activest-theme`), aplica `dark`/`light` no `<html>`, `toggleTheme` grava no servidor se houver token; `setTheme` aplica sem gravar | aplica na tela |
| Front | `AuthProvider.tsx` chama `setTheme(u.theme)` ao carregar o usuário | o do servidor vence o cache |
| Front | `Header.tsx` (botão sol/lua) e `SettingsPage.tsx` (seção Preferências, Escuro \| Claro) | os dois controles |
| Front | `index.html`: `<html class="dark">` fixo; o tema certo entra no primeiro efeito do React | — |
| Teste | `SignupIT` confere `theme` no cadastro e o `PUT /users/me/preferences` | — |

### 2.2 Por que o zoom geral funciona aqui

- `tailwind.config.js` não personaliza a escala de fontes nem de espaçamento (só cores e a família Inter): todas as
  classes padrão (`text-sm`, `p-4`, `gap-3`, `w-64`, `h-5`…) são em `rem`. Mudar o `font-size` do `<html>` de 100%
  (16 px) para um valor menor reduz tudo junto.
- `index.css` não define `font-size` no `html`/`body`; os únicos px dele são da barra de rolagem (8 px, borda 2 px).

### 2.3 O que é fixo em px e **não** acompanharia sozinho (levantado em 10/11/2026)

| Grupo | Quantidade | Onde |
|---|---|---|
| Fonte em `text-[Npx]` | 26 em 17 arquivos (10, 11 e 13 px) | Transações (4), Notificações (5), Filtros de Ativos (2), IssuerPicker (2), Carteira, Header (contador do sino), Toggle, ChartLegend, Sparkline, IndicatorGrid, MonthYearTable, EvolutionCharts, PerformanceCharts/Panels, DistributorChangeModal, CeilingPriceDetail, GoalDetail |
| `fontSize` numérico (Recharts: eixos, rótulos, legendas) | 27 em 7 arquivos | EvolutionCharts (6), PerformanceCharts (6), GoalDetailPage (6), IndicatorHistoryModal (3), PriceChart (2), IndexChartCard (2), CurrenciesPage (2) |
| Medidas fixas `*-[Npx]` (largura/altura) | 34 | alturas de gráfico (`h-[400px]`, `h-[340px]`, `h-[560px]`), larguras mínimas de tabela (`min-w-[880px]`, `min-w-[1250px]`), `w-[72px]` do menu recolhido, `translate-x` do Toggle… |
| `height`/`width`/raios numéricos em JSX | 23 em 13 arquivos | `ResponsiveContainer height={…}`, raios das roscas, tamanhos de logo |

Gráficos que calculam a própria geometria (pizza de pizza, legenda de canto) medem a largura com `ResizeObserver`;
não dependem do tamanho da fonte do `<html>`, mas a legenda (13 px) muda de tamanho com a conversão — a conta da
legenda de canto precisa ser conferida.

## 3. Impact — o que muda e riscos

| Componente | Mudança | Risco |
|---|---|---|
| Banco | migration **V23**: coluna nova `users.font_size VARCHAR(10) NOT NULL DEFAULT 'LARGE'` + `ck_users_font_size` (LARGE, SMALL). As linhas existentes ficam `LARGE` pelo default (REGRAS 8.1) | baixo; `ddl-auto: validate` exige entidade e migration na mesma subida |
| Core | `User.fontSize` + enum `UserFontSize` (LARGE, SMALL) | — |
| app-api | `PreferencesDTO` passa a aceitar `theme` **e/ou** `fontSize` (gravação parcial: o que vier nulo não muda; os dois nulos → 400); `UserResponseDTO.fontSize` | o front de hoje manda só `theme` — continua valendo |
| Front — contexto | `FontSizeContext`/`FontSizeProvider` no molde do tema: `localStorage` `activest-font-size`, aplica no `<html>` (classe `font-small` ou atributo), grava no servidor com token; `AuthProvider` aplica o do usuário | tela "pisca" no primeiro desenho se o valor só entrar no efeito (mesmo comportamento do tema hoje) |
| Front — cabeçalho e Configurações | botão A−/A+ no `Header` ao lado do Tema; linha "Tamanho" na seção Preferências | — |
| Front — CSS | `html.font-small { font-size: 87.5% }` (ou o valor da D1) em `index.css` | — |
| Front — px fixos | os 26 `text-[Npx]` passam para `rem` (10 px → `text-[0.625rem]`, 11 → `0.6875rem`, 13 → `0.8125rem`); os 27 `fontSize` do Recharts passam a vir de um hook que lê o tamanho atual (`useChartFontSize`/`remToPx`) | esquecer um ponto deixa um texto "grande" no modo menor — conferência visual tela a tela |
| Front — medidas fixas | decisão D5 | alturas fixas de gráfico ficam iguais no modo menor (o resto encolhe em volta) |
| Testes | `SignupIT` (+ `fontSize` no cadastro e no `PUT`), teste de controller para a gravação parcial, Vitest do helper de conversão | — |
| Documentação | `Claude.md` (tabela `users`, endpoint de preferências, Padrão Visual, Telas → Configurações), `PENDENCIAS.md` se algo ficar de fora | — |

Sem efeito em: adm-api, integrações, cálculos, Redis, tarefas.

## 4. Solução proposta

1. `<html>` recebe a classe `font-small` quando o tamanho é Menor; o CSS reduz o `font-size` raiz. Tudo que é `rem`
   acompanha.
2. Os textos fixos em px viram `rem`; os gráficos leem o tamanho em px a partir do `rem` atual (o Recharts só aceita
   número).
3. Preferência no usuário (coluna nova), gravada pelo mesmo `PUT /users/me/preferences`, com o mesmo padrão de cache
   local e de "falhar ao gravar não desfaz a troca na tela" do tema.
4. Nomes em inglês (REGRAS 9): `fontSize`, `UserFontSize`, `font_size`, `activest-font-size`; valores de registro
   `LARGE`/`SMALL`.

## 5. Critérios de aceitação

- C1. O botão do cabeçalho alterna Grande ↔ Menor na hora, sem recarregar, e a escolha volta ao entrar de novo, em
  outro navegador (vem do servidor).
- C2. Configurações → Preferências mostra o tamanho atual e troca igual ao cabeçalho; os dois ficam sincronizados.
- C3. No Menor, nenhum texto de tela ou de gráfico continua no tamanho grande (conferência tela a tela, nos dois
  temas — REGRAS 4.3).
- C4. No Grande, a tela fica idêntica à de hoje (os px convertidos para `rem` dão o mesmo tamanho a 16 px).
- C5. Usuário existente e usuário novo nascem com Grande.
- C6. Login, cadastro e esqueci a senha usam o tamanho do cache local.
- C7. Testes, lint e build verdes.

## 6. Decisões para o Guilherme

| # | Pergunta | Opções | Recomendação |
|---|---|---|---|
| D1 | Quanto o Menor reduz? | (a) 87,5% — 16 → 14 px; (b) 90% — 16 → 14,4 px; (c) 81,25% — 16 → 13 px | **(a)**: valor redondo de px, redução perceptível sem ficar ilegível nas tabelas que já usam 10–11 px (viram ~9 px) |
| D2 | Controle no cabeçalho | (a) **um botão** que alterna, mostrando a ação disponível (A− no Grande, A+ no Menor), como o sol/lua do tema; (b) dois botões A− e A+ lado a lado, o ativo desabilitado | **(a)**: mesmo comportamento do tema e cabeçalho sem crescer |
| D3 | Rótulos em Configurações | (a) "Grande \| Menor"; (b) "Normal \| Compacto"; (c) outro | **(a)**: o que você usou no pedido |
| D4 | Gravação | (a) mesmo `PUT /users/me/preferences`, com `theme` e `fontSize` opcionais (parcial); (b) endpoint novo só para o tamanho | **(a)**: um lugar só para as preferências |
| D5 | Medidas fixas em px (alturas de gráfico, larguras mínimas de tabela, menu recolhido) | (a) ficam como estão, e só se converte o que a conferência visual mostrar errado; (b) converter todas para `rem` | **(a)**: são caixas, não textos; converter as larguras mínimas faria as tabelas largas encolherem menos do que deveriam no celular |
| D6 | Gráficos do Recharts | (a) fonte acompanha o tamanho (hook); (b) ficam fixos | **(a)**: senão os eixos ficam maiores que o texto da tela no Menor |
| D7 | Primeiro desenho | (a) igual ao tema (aplica no primeiro efeito do React — pode piscar um instante); (b) script inline no `index.html` que lê o `localStorage` antes do React, para tema e tamanho | **(a)** neste escopo; (b) vira pendência, porque mexe também no tema |

## 7. Etapas do plano

| Etapa | O que | Validação |
|---|---|---|
| E1 | Backend: V23, `UserFontSize`, `User.fontSize`, `PreferencesDTO` parcial, `UserResponseDTO.fontSize`; reload da app-api | `SignupIT` + teste da gravação parcial |
| E2 | Front: `FontSizeContext`/`Provider`, CSS do `html.font-small`, `AuthProvider`, tipo `User`, `usersApi.preferences`, botão no `Header`, linha em Configurações | troca na tela e volta após recarregar |
| E3 | Front: os 26 `text-[Npx]` para `rem` e os 27 `fontSize` do Recharts pelo hook; conferência da legenda de canto | Vitest do helper; lint; `tsc -b` |
| E4 | Conferência visual tela a tela nos dois tamanhos e dois temas; ajustes da D5 se aparecerem | C1–C6 |
| E5 | Documentação (`Claude.md`, `PENDENCIAS.md`, `README.md`, andamento deste plano) | — |

## 8. Quem faz

| Atividade | Claude | Guilherme |
|---|---|---|
| Responder D1–D7 e aprovar a execução | | ✔ |
| E1–E5, testes, lint, build, reload do backend | ✔ | |
| Conferência visual final no navegador logado | prepara | ✔ confere |
| Commit / PR | sugere a mensagem | ✔ |

## 9. Andamento

- 10/11/2026 — Discovery, Impact e Planning feitos.
- 10/11/2026 — **Decisões do Guilherme:** D3 = "Grande | Pequena" (rótulos da tela); D1, D2, D4, D5, D6 e D7 pela
  recomendação (aprovadas explicitamente). Mandou implementar.
- 10/11/2026 — **Implementado (E1–E5).** V23 (`users.font_size`), `UserFontSize`, `PreferencesDTO` parcial
  (`@AssertTrue` pede um dos dois), `UserResponseDTO.fontSize`; front com `FontSizeContext`/`FontSizeProvider`,
  `utils/fontScale.ts` (`rem()`, conversões), `html.font-small { font-size: 87.5% }`, botão A−/A+ (lucide
  `AArrowDown`/`AArrowUp`) no cabeçalho e "Tamanho: Grande | Pequena" em Configurações; os 26 `text-[Npx]` viraram
  `rem` e os 27 `fontSize` do Recharts `rem(N)` (o SVG aceita a medida CSS — sem hook, D6 atendida).
  **Testes:** `SchemaTest` (V23) e `SignupIT` (+ gravação parcial, 400 sem campo e com valor inválido) verdes; Vitest
  20 arquivos / 189 testes (novo `fontScale.test.ts`); lint 0 erro. **Conferência no navegador:** troca na hora,
  grava no servidor sem mexer no tema, volta após recarregar; eixo do gráfico a 9,625 px no Pequena; Carteira e
  Rentabilidade nos dois temas; preferências do usuário devolvidas ao estado inicial (claro, Grande).
  Pendências: PENDENCIAS 47.1 (primeiro desenho, D7) e 47.2 (medidas de caixa em px, D5).
- 10/11/2026 — **Review (pedidos do Guilherme):** (1) `AssetLogo` passou a rem e o logo das tabelas (Carteira,
  Transações, Dividendos, listagem de Ativos) de 38 para 32 — no Pequena ele ficava em 38 px e ditava a altura das
  linhas; (2) fora do escopo, a pedido: Metas voltou com o texto, o botão "Nova meta" e os 6 cards de resumo
  (retirados no commit `3f71bb3`, que deixou a tela sem como criar meta) e Tarefas perdeu a importação `TRIGGER_HINT`
  que sobrou da coluna Origem (`d7db347`). `tsc -b` e lint voltaram a 0 erro e 0 aviso; Vitest 189 verdes.
- 10/11/2026 — **Review de Metas (pedidos do Guilherme, fora do escopo):** etiquetas de situação todas marcadas por
  padrão (desmarcar filtra; tudo marcado volta a "todas"; "Limpar" remarca todas); "No ritmo" e "Dentro do objetivo"
  numa etiqueta só, "No ritmo / dentro" (como o card); "Recolher tudo" / "Expandir tudo" das categorias, à direita
  das etiquetas. `tsc -b` e lint 0; conferido no navegador.
