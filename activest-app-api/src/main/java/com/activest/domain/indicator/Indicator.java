package com.activest.domain.indicator;

import com.activest.domain.issuer.IssuerKind;

/**
 * Indicadores da F9 (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 5.1 e 5.2) e os novos de acoes da tela de Ativos
 * (docs/planos/PLANO_TELA_ATIVOS.md, 2.4 e 2.5). Nada e gravado: o {@link IndicatorService} calcula na leitura.
 *
 * <p>O codigo e valor que trafega na API (REGRAS_CLAUDE 9.2, T7): sigla do mercado ou portugues, como os de
 * sempre. Os grupos da tela (Valuation, Endividamento...) ficam no web, para a linha da listagem nao crescer.</p>
 */
public enum Indicator {

    // ------------------------------------------------------------ acoes (5.1)
    /** Lucro da controladora 12 meses / acoes em circulacao (unit: x acoes da unit). */
    LPA(IssuerKind.COMPANY, Unit.BRL, Flag.LISTED, Flag.SHARES),
    /** PL da controladora / acoes em circulacao. */
    VPA(IssuerKind.COMPANY, Unit.BRL, Flag.LISTED, Flag.SHARES),
    /** Preco / LPA; nulo com lucro 12 meses negativo ou zero. */
    PL(IssuerKind.COMPANY, Unit.RATIO, Flag.LISTED, Flag.SHARES),
    /** Preco / VPA (acao) ou preco / VP por cota do informe (FII). */
    PVP(null, Unit.RATIO, Flag.LISTED, Flag.SHARES),
    /** Lucro da controladora 12 meses / PL da controladora medio (inicio e fim — D4). */
    ROE(IssuerKind.COMPANY, Unit.PERCENT, Flag.LISTED),
    /** Dividendos + JCP pagos em 12 meses (DFC) / lucro da controladora 12 meses (base desde 10/10/2026). */
    PAYOUT(IssuerKind.COMPANY, Unit.PERCENT, Flag.LISTED),

    // ------------------------------------------------------------ os dois
    /** Proventos por cota com data-com nos 12 meses / preco (do ticker — D5). Sem provento = 0. */
    DY(null, Unit.PERCENT, Flag.LISTED),

    // ------------------------------------------------------------ FII (5.2)
    /** VP por cota do informe mensal. */
    VP_COTA(IssuerKind.FII, Unit.BRL, Flag.LISTED),
    /** Numero de cotistas do informe. */
    COTISTAS(IssuerKind.FII, Unit.COUNT, Flag.LISTED),
    /** (Imoveis + imoveis via SPE) / total investido. */
    IMOVEIS(IssuerKind.FII, Unit.PERCENT, Flag.LISTED),
    /** Recebiveis (CRI/CRA, LCI/LCA, LH, LIG) / total investido. */
    PAPEL(IssuerKind.FII, Unit.PERCENT, Flag.LISTED),
    /** Passivo / ativo. */
    ALAVANCAGEM(IssuerKind.FII, Unit.PERCENT, Flag.LISTED),

    // ------------------------------------------------------------ acoes, tela de Ativos (PLANO_TELA_ATIVOS, 2.5)
    // Valuation
    /** Preco / (receita 12m / acoes). */
    PSR(IssuerKind.COMPANY, Unit.RATIO, Flag.SHARES),
    /** Preco / (EBIT 12m / acoes); nulo com EBIT negativo ou zero. */
    P_EBIT(IssuerKind.COMPANY, Unit.RATIO, Flag.SHARES, Flag.COMMERCIAL),
    /** Preco / (EBITDA 12m / acoes); EBITDA = EBIT + depreciacao e amortizacao (DVA). */
    P_EBITDA(IssuerKind.COMPANY, Unit.RATIO, Flag.SHARES, Flag.COMMERCIAL),
    /** (Valor de mercado + divida liquida) / EBIT 12m — por empresa (D18). */
    EV_EBIT(IssuerKind.COMPANY, Unit.RATIO, Flag.SHARES, Flag.COMMERCIAL),
    /** (Valor de mercado + divida liquida) / EBITDA 12m — por empresa (D18). */
    EV_EBITDA(IssuerKind.COMPANY, Unit.RATIO, Flag.SHARES, Flag.COMMERCIAL),
    /** Preco / (ativo total / acoes). */
    P_ATIVO(IssuerKind.COMPANY, Unit.RATIO, Flag.SHARES),
    /** Preco / ((ativo circulante - passivo circulante) / acoes); nulo com capital de giro negativo ou zero. */
    P_CAP_GIRO(IssuerKind.COMPANY, Unit.RATIO, Flag.SHARES, Flag.COMMERCIAL),
    /** Preco / ((ativo circulante - passivo total) / acoes), passivo total = ativo total - PL. */
    P_ATIVO_CIRC_LIQ(IssuerKind.COMPANY, Unit.RATIO, Flag.SHARES, Flag.COMMERCIAL),
    // Endividamento
    /** (Divida bruta - caixa) / EBIT 12m. */
    DIV_LIQ_EBIT(IssuerKind.COMPANY, Unit.RATIO, Flag.COMMERCIAL),
    /** (Divida bruta - caixa) / EBITDA 12m. */
    DIV_LIQ_EBITDA(IssuerKind.COMPANY, Unit.RATIO, Flag.COMMERCIAL),
    /** Divida bruta / PL. */
    DIV_BRUTA_PATRIMONIO(IssuerKind.COMPANY, Unit.RATIO, Flag.COMMERCIAL),
    /** PL / ativo total. */
    PATRIMONIO_ATIVOS(IssuerKind.COMPANY, Unit.PERCENT),
    /** (Ativo total - PL) / ativo total. */
    PASSIVOS_ATIVOS(IssuerKind.COMPANY, Unit.PERCENT),
    /** Ativo circulante / passivo circulante. */
    LIQUIDEZ_CORRENTE(IssuerKind.COMPANY, Unit.RATIO, Flag.COMMERCIAL),
    // Eficiencia
    /** Lucro bruto 12m / receita 12m. */
    MARGEM_BRUTA(IssuerKind.COMPANY, Unit.PERCENT, Flag.COMMERCIAL),
    /** EBITDA 12m / receita 12m. */
    MARGEM_EBITDA(IssuerKind.COMPANY, Unit.PERCENT, Flag.COMMERCIAL),
    /** EBIT 12m / receita 12m. */
    MARGEM_EBIT(IssuerKind.COMPANY, Unit.PERCENT, Flag.COMMERCIAL),
    /** Lucro liquido 12m / receita 12m. */
    MARGEM_LIQUIDA(IssuerKind.COMPANY, Unit.PERCENT),
    // Rentabilidade
    /** Lucro liquido 12m / ativo total medio (inicio e fim — D19). */
    ROA(IssuerKind.COMPANY, Unit.PERCENT),
    /** EBIT 12m x (1 - IR/CSLL / LAIR) / (PL + divida liquida) medio (D19). */
    ROIC(IssuerKind.COMPANY, Unit.PERCENT, Flag.COMMERCIAL),
    /** Receita 12m / ativo total medio. */
    GIRO_ATIVOS(IssuerKind.COMPANY, Unit.RATIO, Flag.COMMERCIAL),
    // Crescimento
    /** (Receita 12m / receita 12m de 5 anos antes)^(1/5) - 1 (D20). */
    CAGR_RECEITAS_5A(IssuerKind.COMPANY, Unit.PERCENT),
    /** Idem com o lucro liquido 12m (D20). */
    CAGR_LUCROS_5A(IssuerKind.COMPANY, Unit.PERCENT);

    public enum Unit { PERCENT, RATIO, BRL, COUNT }

    private enum Flag {
        /** Coluna da listagem de Ativos (T11: a listagem devolve so estes). */
        LISTED,
        /** Depende da quantidade de acoes (o motivo da quantidade vai junto). */
        SHARES,
        /** So no layout comercial: bancos e seguradoras nao tem (31.4, D17 — a tela esconde). */
        COMMERCIAL
    }

    private final IssuerKind kind;
    private final Unit unit;
    private final java.util.Set<Flag> flags;

    Indicator(IssuerKind kind, Unit unit, Flag... flags) {
        this.kind = kind;
        this.unit = unit;
        this.flags = flags.length == 0 ? java.util.EnumSet.noneOf(Flag.class)
                : java.util.EnumSet.copyOf(java.util.List.of(flags));
    }

    public Unit unit() {
        return unit;
    }

    /** Se o indicador vale para o tipo de emissor ({@code DY} vale para os dois). */
    public boolean appliesTo(IssuerKind issuerKind) {
        return kind == null || kind == issuerKind;
    }

    /**
     * Se vale para o tipo e o layout do balanco ({@code issuers.statement_layout}): os {@link Flag#COMMERCIAL}
     * nao se aplicam a bancos e seguradoras. Layout nulo (FII, emissor sem balanco) conta como comercial.
     */
    public boolean appliesTo(IssuerKind issuerKind, String statementLayout) {
        return appliesTo(issuerKind) && (!flags.contains(Flag.COMMERCIAL)
                || statementLayout == null || "COMERCIAL".equals(statementLayout));
    }

    /** Coluna da listagem de Ativos (T11). */
    public boolean listed() {
        return flags.contains(Flag.LISTED);
    }

    /** Depende da quantidade de acoes. */
    public boolean usesShares() {
        return flags.contains(Flag.SHARES);
    }
}
