package com.activest.domain.corporateaction;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Evento societario que muda a quantidade de cotas (V20).
 *
 * <p>O {@code factor} e o numero como a B3 publica, e cada tipo o le de um
 * jeito — conferido em casos reais (docs/planos/PLANO_BRAPI.md, A12):</p>
 * <ul>
 *   <li><b>DESDOBRAMENTO</b> — percentual de acoes novas: 100 = 1 vira 2
 *   (PETR 2008), 300 = 1 vira 4 (MGLU 2020), 900 = 1 vira 10 (MXRF 2017).</li>
 *   <li><b>GRUPAMENTO</b> — multiplicador: 0,1 = 10 viram 1 (MGLU 2024).</li>
 *   <li><b>BONIFICACAO</b> — percentual de acoes novas: 5 = 5 novas a cada 100
 *   (MGLU 2025).</li>
 * </ul>
 */
public enum StockEventType {
    DESDOBRAMENTO,
    GRUPAMENTO,
    BONIFICACAO;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Quantas cotas cada cota vira no evento: desdobramento 300 -> 4, grupamento 0,1 -> 0,1,
     * bonificacao 5 -> 1,05. Nulo com fator ausente ou nao positivo (o evento nao muda nada).
     *
     * <p>Serve a quantidade de acoes dos indicadores e o ajuste da cotacao historica
     * (docs/planos/PLANO_TELA_EMISSORES.md, D9): o preco anterior ao evento e dividido por essa razao.</p>
     */
    public BigDecimal quantityRatio(BigDecimal factor) {
        if (factor == null || factor.signum() <= 0) {
            return null;
        }
        return this == GRUPAMENTO ? factor : BigDecimal.ONE.add(factor.divide(HUNDRED, MathContext.DECIMAL64));
    }

    /** Rotulo da B3 para o nosso tipo. Nulo = evento que nao mexe na quantidade (ou desconhecido). */
    public static StockEventType fromSourceLabel(String label) {
        if (label == null) {
            return null;
        }
        return switch (label.trim().toUpperCase()) {
            case "DESDOBRAMENTO" -> DESDOBRAMENTO;
            case "GRUPAMENTO" -> GRUPAMENTO;
            case "BONIFICACAO", "BONIFICAÇÃO" -> BONIFICACAO;
            default -> null;
        };
    }
}
