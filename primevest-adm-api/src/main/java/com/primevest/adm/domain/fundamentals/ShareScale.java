package com.primevest.adm.domain.fundamentals;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * Escala da quantidade de acoes da composicao do capital (DFP/ITR, 2020+).
 *
 * <p>O arquivo nao diz a escala: Itau informa 11.026.869 (sao 11 bilhoes), Axia
 * 2.915.428, BB 5.730.834.040 (unidades). Candidatos: 1 ou 1.000. Duas
 * conferencias, nesta ordem; vale a primeira que apontar um unico candidato:</p>
 * <ol>
 *   <li><b>FRE</b> — o capital integralizado do Formulario de Referencia, em
 *       unidades, na data do documento;</li>
 *   <li><b>LPA</b> — lucro da controladora / acoes em circulacao contra o LPA
 *       basico informado (a companhia usa a media ponderada, entao a janela e
 *       larga: Petrobras 2010, ano da capitalizacao, da 2,70 contra 3,57).</li>
 * </ol>
 * <p>Nada conclusivo: {@code REVISAR}, sem fator — ate a passada pelo documento
 * validado mais proximo ({@code VIZINHO}, no fim da carga, em
 * FinancialStatementImportService.resolveByNeighbors). Janela: razao entre 0,5 e 2 —
 * os candidatos distam 1.000x, entao sobra folga para desdobramento, recompra e
 * media ponderada sem ambiguidade.</p>
 */
public final class ShareScale {

    public enum Status { OK, REVISAR, MANUAL }

    public enum Check { FRE, LPA, VIZINHO }

    public record Decision(Integer factor, Status status, Check check) {
        static Decision undecided() {
            return new Decision(null, Status.REVISAR, null);
        }
    }

    static final List<Integer> CANDIDATES = List.of(1, 1000);
    private static final double LOW = 0.5;
    private static final double HIGH = 2.0;

    private ShareScale() {
    }

    /**
     * @param rawTotal       total de acoes como veio no arquivo
     * @param rawOutstanding total menos tesouraria, como veio
     * @param freTotal       capital integralizado do FRE na data (unidades), ou nulo
     * @param parentProfit   lucro da controladora do periodo acumulado, ou nulo
     * @param eps            LPA basico do mesmo periodo (ON, ou PN se nao houver ON), ou nulo
     */
    public static Decision decide(long rawTotal, long rawOutstanding, Long freTotal,
                                  BigDecimal parentProfit, BigDecimal eps) {
        if (rawTotal <= 0) {
            return Decision.undecided();
        }
        Integer f = byRatio(rawTotal, freTotal);
        if (f != null) {
            return new Decision(f, Status.OK, Check.FRE);
        }
        f = byEps(rawOutstanding > 0 ? rawOutstanding : rawTotal, parentProfit, eps);
        if (f != null) {
            return new Decision(f, Status.OK, Check.LPA);
        }
        return Decision.undecided();
    }

    /** Fator que o LPA aponta, para conferir a quantidade que veio do FRE (deve ser 1). */
    public static Integer byEps(long shares, BigDecimal parentProfit, BigDecimal eps) {
        if (shares <= 0 || parentProfit == null || eps == null || parentProfit.signum() == 0 || eps.signum() == 0) {
            return null;
        }
        Integer found = null;
        for (int f : CANDIDATES) {
            BigDecimal implied = parentProfit.divide(BigDecimal.valueOf(shares).multiply(BigDecimal.valueOf(f)),
                    MathContext.DECIMAL64);
            double ratio = implied.divide(eps, MathContext.DECIMAL64).doubleValue();
            if (ratio >= LOW && ratio <= HIGH) {
                if (found != null) {
                    return null;
                }
                found = f;
            }
        }
        return found;
    }

    private static Integer byRatio(long raw, Long reference) {
        if (reference == null || reference <= 0) {
            return null;
        }
        Integer found = null;
        for (int f : CANDIDATES) {
            double ratio = (double) raw * f / reference;
            if (ratio >= LOW && ratio <= HIGH) {
                if (found != null) {
                    return null;
                }
                found = f;
            }
        }
        return found;
    }
}
