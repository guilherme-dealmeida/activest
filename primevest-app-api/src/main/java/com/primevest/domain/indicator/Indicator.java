package com.primevest.domain.indicator;

import com.primevest.domain.issuer.IssuerKind;

/**
 * Indicadores da F9 (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 5.1 e 5.2). Nada e gravado: o
 * {@link IndicatorService} calcula na leitura.
 */
public enum Indicator {

    // ------------------------------------------------------------ acoes (5.1)
    /** Lucro da controladora 12 meses / acoes em circulacao (unit: x acoes da unit). */
    LPA(IssuerKind.COMPANY, Unit.BRL),
    /** PL da controladora / acoes em circulacao. */
    VPA(IssuerKind.COMPANY, Unit.BRL),
    /** Preco / LPA; nulo com lucro 12 meses negativo ou zero. */
    PL(IssuerKind.COMPANY, Unit.RATIO),
    /** Preco / VPA (acao) ou preco / VP por cota do informe (FII). */
    PVP(null, Unit.RATIO),
    /** Lucro da controladora 12 meses / PL da controladora medio (inicio e fim — D4). */
    ROE(IssuerKind.COMPANY, Unit.PERCENT),
    /** Dividendos + JCP pagos em 12 meses (DFC) / lucro da controladora 12 meses (base desde 10/10/2026). */
    PAYOUT(IssuerKind.COMPANY, Unit.PERCENT),

    // ------------------------------------------------------------ os dois
    /** Proventos por cota com data-com nos 12 meses / preco (do ticker — D5). Sem provento = 0. */
    DY(null, Unit.PERCENT),

    // ------------------------------------------------------------ FII (5.2)
    /** VP por cota do informe mensal. */
    VP_COTA(IssuerKind.FII, Unit.BRL),
    /** Numero de cotistas do informe. */
    COTISTAS(IssuerKind.FII, Unit.COUNT),
    /** (Imoveis + imoveis via SPE) / total investido. */
    IMOVEIS(IssuerKind.FII, Unit.PERCENT),
    /** Recebiveis (CRI/CRA, LCI/LCA, LH, LIG) / total investido. */
    PAPEL(IssuerKind.FII, Unit.PERCENT),
    /** Passivo / ativo. */
    ALAVANCAGEM(IssuerKind.FII, Unit.PERCENT);

    public enum Unit { PERCENT, RATIO, BRL, COUNT }

    private final IssuerKind kind;
    private final Unit unit;

    Indicator(IssuerKind kind, Unit unit) {
        this.kind = kind;
        this.unit = unit;
    }

    public Unit unit() {
        return unit;
    }

    /** Se o indicador vale para o tipo de emissor ({@code DY} vale para os dois). */
    public boolean appliesTo(IssuerKind issuerKind) {
        return kind == null || kind == issuerKind;
    }
}
