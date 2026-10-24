package com.activest.domain.fixedincome;

/**
 * Tipo da empresa do cadastro de renda fixa (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, R2 e secao 4.3).
 *
 * <p>Derivado do segmento do BCB ou da fonte da CVM. E o que o seletor de emissora filtra pelo produto
 * ({@code asset_categories.issuer_kinds}): um CDB so aceita BANCO; uma debenture, COMPANHIA.</p>
 */
public enum FixedIncomeIssuerKind {
    /** Banco multiplo, comercial, de investimento, de desenvolvimento, Caixa, BB, BNDES (BCB). */
    BANCO,
    /** SCFI, sociedade de credito imobiliario, companhia hipotecaria, APE (BCB). */
    FINANCEIRA,
    /** Cooperativa de credito singular (BCB). */
    COOPERATIVA,
    /** DTVM e CTVM (BCB), ou intermediario so da CVM. */
    CORRETORA_DISTRIBUIDORA,
    /** Emissora de CRI/CRA (informes da CVM). */
    SECURITIZADORA,
    /** Companhia aberta (cadastro da CVM): debenture e nota comercial. */
    COMPANHIA,
    /** Tesouro Nacional — linha fixa da V54, emissora unica do Tesouro Direto. */
    TESOURO,
    /** "Instituicao nao listada" — linha fixa da V54, para o raro caso fora do cadastro. */
    NAO_LISTADA;

    /** As duas linhas fixas, sem CNPJ e fora da integracao. */
    public boolean isFixed() {
        return this == TESOURO || this == NAO_LISTADA;
    }

    /** Pode aparecer no seletor de distribuidora (R5). */
    public boolean canDistribute() {
        return this == CORRETORA_DISTRIBUIDORA || this == BANCO || this == FINANCEIRA || this == COOPERATIVA
                || this == NAO_LISTADA;
    }
}
