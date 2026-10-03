package com.primevest.domain.corporateaction;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A regra de um evento societario sobre uma posicao — funcao pura: sem banco,
 * sem Spring (docs/planos/PLANO_BRAPI.md, F6).
 *
 * <table>
 *   <caption>Efeito de cada evento</caption>
 *   <tr><th>Evento</th><th>Cotas</th><th>Custo</th></tr>
 *   <tr><td>Desdobramento</td><td>x (1 + fator/100)</td><td>igual</td></tr>
 *   <tr><td>Grupamento</td><td>x fator</td><td>igual</td></tr>
 *   <tr><td>Bonificacao</td><td>+ cotas x fator/100</td><td>+ novas x custo atribuido</td></tr>
 * </table>
 *
 * <p><b>Fracao.</b> A B3 nao entrega fracao de acao: ela e vendida em leilao e
 * o dinheiro vai para o acionista. Por isso o resultado e arredondado PARA
 * BAIXO a cotas inteiras. No desdobramento e no grupamento a fracao leva junto
 * a parte proporcional do custo (grupamento de 10 para 1, fator 0,1: 100 cotas
 * viram 10 com o mesmo custo; 105 cotas dariam 10,5, ficam 10 e 1/21 do custo
 * sai com a fracao). Na bonificacao a fracao das cotas novas simplesmente nao
 * entra — ela nao tinha custo.</p>
 *
 * <p><b>Bonificacao sem custo informado</b> (B27): as cotas novas entram na
 * hora com custo zero provisorio, para a quantidade ja ficar certa. O preco
 * medio cai ate o custo ser informado.</p>
 *
 * <p>Posicao zerada nao muda: evento antes da primeira compra ou depois da
 * venda total nao afeta nada.</p>
 */
public final class CorporateActionApplier {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 10;

    private CorporateActionApplier() {
    }

    /** Cotas e custo de uma posicao num ponto da linha do tempo. */
    public record Holding(BigDecimal shares, BigDecimal cost) {
    }

    public static Holding apply(Holding holding, StockEventStep event) {
        return apply(holding, event.type(), event.factor(), event.assignedCost());
    }

    public static Holding apply(Holding holding, StockEventType type, BigDecimal factor, BigDecimal assignedCost) {
        if (holding.shares() == null || holding.shares().signum() <= 0 || factor == null || factor.signum() <= 0) {
            return holding;
        }
        return switch (type) {
            case DESDOBRAMENTO -> resize(holding, BigDecimal.ONE.add(factor.divide(HUNDRED, SCALE, RoundingMode.HALF_UP)));
            case GRUPAMENTO -> resize(holding, factor);
            case BONIFICACAO -> bonus(holding, factor, assignedCost);
        };
    }

    /** Desdobramento e grupamento: a quantidade muda na razao, o custo fica (menos a fracao). */
    private static Holding resize(Holding holding, BigDecimal ratio) {
        BigDecimal exact = holding.shares().multiply(ratio);
        BigDecimal whole = exact.setScale(0, RoundingMode.DOWN);
        if (whole.compareTo(exact) == 0) {
            return new Holding(whole, holding.cost());
        }
        if (whole.signum() == 0) {
            return new Holding(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal kept = holding.cost().multiply(whole).divide(exact, SCALE, RoundingMode.HALF_UP);
        return new Holding(whole, kept);
    }

    /** Bonificacao: cotas novas, inteiras, a custo atribuido (ou zero provisorio). */
    private static Holding bonus(Holding holding, BigDecimal percent, BigDecimal assignedCost) {
        BigDecimal added = holding.shares().multiply(percent).divide(HUNDRED, SCALE, RoundingMode.HALF_UP)
                .setScale(0, RoundingMode.DOWN);
        if (added.signum() == 0) {
            return holding;
        }
        BigDecimal unitCost = assignedCost != null ? assignedCost : BigDecimal.ZERO;
        return new Holding(holding.shares().add(added), holding.cost().add(added.multiply(unitCost)));
    }
}
