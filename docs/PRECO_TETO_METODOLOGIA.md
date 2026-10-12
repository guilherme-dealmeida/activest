# PrimeVest — Metodologia do Preço Teto e do Preço Ideal

> Versão 1 (10/10/2026), modelo **PREÇO TETO V2** (planilha do Guilherme). Plano e decisões:
> `docs/planos/PLANO_PRECO_TETO.md`. Código: `CeilingPriceRules` (fórmulas), `CeilingPriceDefaults` (padrões) e
> `CeilingPriceService` (app-api, `domain/ceilingprice`).

**O resultado é uma estimativa** baseada no modelo e nas premissas do usuário. Não é garantia nem recomendação de
valor de mercado.

## 1. O que o modelo faz

Projeta o lucro líquido atribuível aos acionistas para três períodos e uma perpetuidade, traz tudo a valor presente
por uma taxa de desconto e divide pelo número de ações. É um modelo **de lucro**, simplificado — não é fluxo de caixa
descontado por FCFE.

## 2. Entradas

| Entrada | Padrão (sugestão; o usuário altera o que quiser) | Fonte |
|---|---|---|
| Lucro de cada um dos 4 últimos trimestres (T-3 … T) | Lucro líquido atribuído a sócios da controladora, trimestre isolado, consolidado (individual só sem consolidado). O 4º trimestre é o anual (DFP) menos o acumulado de 9 meses (ITR) | CVM — DFP/ITR (`company_quarters.net_income_parent_q`) |
| N — quantidade de ações | Ações em circulação **sem tesouraria** do último balanço com escala conferida, ajustadas por desdobramento, grupamento e bonificação posteriores. Unit: dividida pelas ações da unit | CVM — composição do capital; B3 — eventos societários |
| ROE | Média dos **3 últimos exercícios fechados**: lucro da controladora ÷ PL da controladora médio (início e fim) | CVM (mesmo número da tela de Emissoras) |
| Payout | Média dos 3 últimos exercícios fechados: **dividendos + JCP pagos** aos acionistas da controladora (DFC) ÷ lucro da controladora | CVM — DFC (desde 10/10/2026; antes era a DVA) |
| Taxa de desconto (d) | Selic meta vigente | BCB — SGS 432 |
| Crescimento na perpetuidade (G) | 3% (crescimento médio do PIB) | `primevest.preco-teto.crescimento-perpetuidade` |
| Margem de segurança (MS) | 15% | `primevest.preco-teto.margem-seguranca` |
| Preço da ação | Último fechamento | B3 — COTAHIST diário (catálogo) / BRAPI (carteira) |

Percentuais são guardados em pontos (13,75) e convertidos para fração só na conta. Os quatro lucros usam sempre o
mesmo conceito contábil. Lucro digitado pode estar em R$, mil, milhões ou bilhões e em BRL ou USD (convertido pela
PTAX de venda do fim do trimestre, ou pelo câmbio informado); o cálculo usa o valor normalizado em reais.

## 3. Fórmulas (iguais às da planilha V2)

```
LL_TTM         = LL_T-3 + LL_T-2 + LL_T-1 + LL_T
g              = ROE × (1 − Payout)
FC1            = LL_TTM                    VPL1 = FC1 ÷ (1 + d)
FC2            = FC1 × (1 + g)             VPL2 = FC2 ÷ (1 + d)²
FC3            = FC2 × (1 + g)             VPL3 = FC3 ÷ (1 + d)³
Valor terminal = FC3 × (1 + G) ÷ (d − G)   (só quando d > G)
VPL terminal   = Valor terminal ÷ (1 + d)³
Patrimônio     = VPL1 + VPL2 + VPL3 + VPL terminal      ("MARKET CAP" na planilha)
Preço Teto     = Patrimônio ÷ N
Preço Ideal    = Preço Teto × (1 − MS)
Margem         = Preço ÷ Preço da ação − 1   (do teto e do ideal)
```

**Convenção mantida da planilha:** o lucro dos últimos 12 meses (já realizado) é o FC1 e é descontado **um**
período; o valor terminal começa ao fim do terceiro período. Aritmética em `BigDecimal` (34 dígitos); arredondamento
só na exibição. Conferido contra a linha VALE3 da planilha: todos os valores iguais a 1e-12 (teste
`CeilingPriceRulesTest.planilhaV2Vale3`).

## 4. Validações e alertas

Nada é limitado nem trocado silenciosamente: prejuízo, crescimento negativo e teto negativo passam pela conta como
são.

| Nível | Quando | Efeito |
|---|---|---|
| **Bloqueio** | campo vazio; trimestre ausente; N ≤ 0; preço ≤ 0; MS < 0 ou ≥ 100%; d ≤ G; câmbio ausente | o valor e o que depende dele ficam "não calculado"; não salva |
| **Atenção** | ROE, payout, d ou G negativos ou acima de 100%; d fora de 5–30%; G acima de 6%; N mais de 20% longe do padrão; lucro digitado mais de 50% longe do da CVM; g negativo ou ≥ d; LL TTM ≤ 0; LL TTM abaixo de 3% do PL; balanço com mais de 150 dias; preço com mais de 7 dias; média de menos de 3 anos | calcula; ao salvar pede confirmação |
| **Informação** | trimestre com prejuízo (entra na soma); reapresentação (soma dos 4 ≠ 12 meses do balanço); há balanço mais novo que a janela | só aviso |

Limites em `primevest.preco-teto.alertas.*` (`application.yml` da app-api).

## 5. Guarda e atualização

- Um cálculo por ação e usuário. Ficam gravadas **só as entradas** (todas obrigatórias), com a origem de cada uma
  (padrão do catálogo ou informado) e a referência do padrão no momento; o resultado é recalculado das entradas a
  cada leitura e só muda quando o usuário salva de novo — o salvo é o retrato da última edição.
- Os padrões são lidos do catálogo na hora: o ↻ de cada campo e "Restaurar todos os padrões" trazem os de hoje
  para o formulário, sem gravar. Quando um padrão muda (Selic, balanço novo, preço), a lista e o ↻ avisam.
- A lista compara o teto e o ideal com o preço do cálculo, o fechamento do dia e o preço médio da carteira.
- Catálogo: balanços da CVM às 07:30; cotações do catálogo pelo COTAHIST diário às 21:30 e 08:30; Selic às 10:00.

## 6. Limitações

- Lucro contábil não é caixa: capex, dívida e caixa não entram; bancos e empresas de ciclo distorcem.
- O peso do valor terminal costuma passar de 70% do total; o resultado é muito sensível a d − G.
- ON e PN da mesma companhia têm o mesmo teto (o lucro e as ações são da companhia inteira).
- Payout pago no ano pode passar de 100% (dividendo de lucro de anos anteriores) e deixar g negativo.
- Dados da fonte: quantidades de ações em revisão (sem padrão de N), reapresentações de trimestre, companhias sem
  DFC de financiamento (payout nulo).
- Só ações com balanço na CVM; FII, BDR e ETF ficam fora.
