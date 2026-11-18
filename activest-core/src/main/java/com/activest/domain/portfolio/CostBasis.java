package com.activest.domain.portfolio;

/**
 * Base do custo da bonificação na posição (docs/planos/PLANO_EVENTOS_TRANSACOES.md, seção 8.4).
 *
 * <ul>
 *   <li><b>BROKER</b> — como a corretora: as ações bonificadas entram <b>sem custo</b>. É a base de tudo o que as telas
 *   mostram (preço médio, valor inicial, valorização, extrato, Preço Teto, Metas). Decisão do Guilherme, 18/11/2026: o
 *   usuário acompanha pela corretora.</li>
 *   <li><b>TAX</b> — como a Receita: as ações bonificadas custam o valor capitalizado (Lei 9.249/1995, art. 10,
 *   parágrafo único) — o {@code assigned_cost} do catálogo ou o custo informado pelo usuário. É a base do IR: a dica
 *   "para o IR" da Carteira e o IR estimado da venda no exterior.</li>
 * </ul>
 *
 * <p>Desdobramento e grupamento não têm custo e saem iguais nas duas.</p>
 */
public enum CostBasis {
    BROKER,
    TAX
}
