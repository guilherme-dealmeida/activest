# Activest — Metodologia da Rentabilidade

> Gerado em 17/10/2026 com a implementação do `docs/planos/PLANO_RENTABILIDADE.md` (passo 11 do escopo). Descreve o que
> o código faz; a conferência independente está em `carga/scripts/conferencia/rentabilidade.mjs`.

## 1. Linha do tempo da carteira

| Item | Regra | Onde |
|---|---|---|
| Eixo | Pregões da B3 da primeira transação até hoje, mais hoje (o ponto do preço atual, mesmo em fim de semana). Pregão = dia útil com fechamento do Ibovespa (24/12 e 31/12 têm CDI, mas a B3 não abre); depois do último Ibovespa publicado, dia útil do `BusinessCalendar` | `PortfolioTimelineBuilder.axis` |
| Valor por cota | cotas ao fim do dia (`PositionCalculator.sharesOn`, com desdobramento, grupamento e bonificação: valem a partir do dia seguinte à data-com) × fechamento do dia, arredondado no centavo por posição | `byShares` |
| Fracionário | usa o preço do lote padrão (PETR4F → PETR4) | `priceAssets` |
| Tesouro Direto | cotas × PU de venda do dia (Tesouro Transparente). Líquido: o título como um lote — rendimento sobre o PU de compra, IR e IOF pela idade no dia | `byShares` (mesma conta de `PositionValuationInfo.treasuryLot`) |
| Renda fixa privada | saldo na curva contratada no dia (`FixedIncomeLots.path`, a mesma regra da Carteira, numa passada). Líquido: IR e IOF de cada aporte pela idade no dia. Depois da última taxa publicada o saldo fica parado | `fixedIncome` |
| Hoje | preço atual (`assets.current_price`, REGRAS 2.6). O último ponto bate com o valor atual da Carteira (base bruta) | |
| Sem negócio | último fechamento até **5 pregões** antes (D3), marcado "último negócio"; além disso, o último fechamento segue **carregado** (revisão de 25/10/2026), marcado "preço carregado — último preço de dd/mm" | `staleDays` |
| Sem série | o preço da última compra até o dia (**carregado**, com a data da compra); renda fixa sem curva (TR, série ausente) ou sem título: o **valor aplicado** em aberto, como a Carteira. **Sem preço** só quando não há número nenhum (ex.: bonificação sem preço de compra) | `lastBuyPrice`, `fixedIncome`, `noCurve` |

## 2. Fluxos e proventos

- **Aporte** (compra): `total_amount`, no **começo** do dia. Transação em dia sem pregão entra no pregão seguinte.
- **Retirada** (venda): no **fim** do dia — bruto (`total_amount`) ou líquido (`net_amount`; sem ele, o total).
- **Proventos** (inclusive amortização e restituição de capital): no **pregão ex** — o primeiro dia útil depois da
  data-com, quando a cotação cai (L3). Bruto (`total_amount`) ou líquido de IR retido (`net_amount`). O provento sai da
  carteira: o Activest não tem conta de caixa, então ele é distribuição, não reinvestimento.
- Corretagem fora (regra do projeto).

## 3. Fórmulas

| Medida | Fórmula |
|---|---|
| Retorno do dia | `r_t = (V_t + W_t + D_t) / (V_{t-1} + A_t) − 1` (V valor, A aportes, W retiradas, D proventos) |
| TWR do período | `Π(1 + r_t) − 1` sobre os dias do período |
| Diferença | `R_carteira − R_benchmark`, em p.p., no mesmo período efetivo (benchmark que para antes — IPCA — compara até onde vai) |
| % do CDI | `R_carteira / R_CDI × 100`, só com CDI acumulado > 0 (D5: carteira negativa dá % negativo) |
| Real | `(1 + R_nominal) / (1 + IPCA) − 1`, os dois até o último mês de IPCA publicado |
| XIRR | taxa anual que zera os fluxos: aportes (−), retiradas (+), proventos na data de **pagamento** (+), saldo final + proventos a receber (+). Só com ≥ 12 meses |
| Contribuição | R$: `V_t + W_t + D_t − V_{t−1} − A_t` somado (fecha exatamente com o resultado). p.p.: `Σ k_t/K × ganho_g,t / (V_{t−1} + A_t)` com `k_t = ln(1+r_t)/r_t` e `K = ln(1+R)/R` (Cariño — fecha exatamente com o TWR) |
| Peso médio | média de `(V_g,t−1 + A_g,t) / (V_{t−1} + A_t)` nos dias com capital |
| Volatilidade | desvio-padrão amostral dos retornos diários × √252 |
| Máximo drawdown | maior queda do índice acumulado desde um pico |
| Sharpe | (retorno anualizado − CDI anualizado) / volatilidade; anualização por 252 dias úteis |
| Sortino | o mesmo excesso / `√(Σ min(0, r − CDI)² / n) × √252` |

## 4. Benchmarks (V57)

| Código | Série | Fator do dia |
|---|---|---|
| CDI, SELIC | SGS 12 / 11, % ao dia | `Π(1 + v/100)` dos dias úteis desde o dia anterior do eixo; depois da última publicada, 1 |
| IPCA | SGS 433, % no mês | `(1 + I/100)^(1/DU_mês)` por dia útil; mês não publicado: sem fator (a série para) |
| IPCA + x% | IPCA | o fator do IPCA × `(1 + x/100)^(1/252)` por dia útil |
| IBOV, IFIX | pontos (B3) — índices de retorno total | razão entre fechamentos |
| SP500_IVVB11 | fechamento do IVVB11 (COTAHIST) | razão entre fechamentos — **proxy** do S&P 500 em reais, não a série oficial |
| BTC | fechamento BTC/BRL (CoinGecko nos últimos 365 dias, Binance desde 13/10/2020) | razão entre fechamentos |

Padrão ao filtrar uma classe (D4): Ações → IBOV; FII → IFIX; Renda fixa → CDI; Cripto → BTC; BDR → IVVB11; demais e
carteira total → CDI. **Tesouro → IPCA e IPCA + x%**, com x = taxa de compra do Tesouro IPCA+ (sem juros semestrais)
à venda que vence primeiro, no último dia do arquivo de preços do Tesouro Transparente, aplicada ao período inteiro
(não é a taxa de cada época). "À venda" = vendido nos 3 últimos dias do arquivo de vendas do Tesouro (`treasury_offers`).

## 5. Disponibilidade

Desde 25/10/2026 (`PLANO_METAS.md`, 5.9, opção a) o preço que falta é **carregado** (último preço, preço da compra ou
valor aplicado) e o dia continua disponível; a tela avisa quais ativos e desde quando, em amarelo ("Preço carregado").
Um dia só é **indisponível** quando alguma posição exposta nele (saldo ontem ou hoje, ou movimento) não tem número
nenhum nele ou na véspera. Período com dia indisponível: TWR, XIRR, contribuição e risco ficam nulos, e a tela diz quem
faltou e quando — nada é calculado sem a posição (L16). Filtrando o recorte sem o ativo, o resultado volta.

## 6. Limitações conhecidas

- Renda fixa pela curva não oscila como no resgate antecipado: volatilidade e drawdown da carteira saem menores.
- "Sem reserva" usa a marca de hoje para toda a história (D9).
- Corretagem e IR sobre ganho de capital em renda variável ficam fora.
- Desdobramento de BDR/ETF que o catálogo não conhece distorce a série (o mesmo vale para o valor de hoje).
- Comparação líquida × índice bruto: a tela avisa; para uma base equivalente, use "Bruto".
- **Dia da compra**: o índice mede de fechamento a fechamento; a carteira mede a compra a partir do preço pago. Um
  ativo que replica o índice rende diferente dele pela diferença entre o preço de compra e o fechamento do dia — em
  compras feitas em dias de queda isso se acumula (carteira só de BTC × BTC/BRL, 12 meses até 17/10/2026: −15,00% ×
  −23,41%). Decisão de 17/10/2026: manter a convenção e avisar na tela.
