package com.primevest.domain.marketindex;

/**
 * Como o valor do indice e publicado — e, por consequencia, se e como ele se acumula
 * ({@link MarketIndexCalculator}).
 */
public enum IndexUnit {
    /** Taxa do dia, em %: CDI 0,050788 = 0,050788% no dia. Acumula por produto. */
    PCT_DIA(true, 252),
    /** Taxa do mes, em %: IPCA 0,42 = 0,42% no mes. Acumula por produto. */
    PCT_MES(true, 12),
    /** TR: taxa de um periodo de um mes que comeca no dia. Os periodos se sobrepoem — nao acumula dia a dia. */
    PCT_PERIODO(false, 0),
    /** Meta Selic: taxa anual em vigor no dia. E uma meta, nao um rendimento — nao acumula. */
    PCT_ANO(false, 0),
    /** Pontos de um indice da B3. Variacao = ultimo / base. */
    PONTOS(false, 0),
    /** Cotacao em reais (dolar PTAX). Variacao = ultimo / base. */
    BRL(false, 0);

    private final boolean compounding;
    private final int periodsPerYear;

    IndexUnit(boolean compounding, int periodsPerYear) {
        this.compounding = compounding;
        this.periodsPerYear = periodsPerYear;
    }

    /** Taxa por periodo, que se acumula multiplicando {@code (1 + taxa/100)}. */
    public boolean isCompounding() {
        return compounding;
    }

    /** Nivel (pontos, cotacao): o acumulado e a razao entre dois valores. */
    public boolean isLevel() {
        return this == PONTOS || this == BRL;
    }

    /** Periodos por ano para levar um spread anual a um periodo (252 dias uteis, 12 meses). */
    public int periodsPerYear() {
        return periodsPerYear;
    }
}
