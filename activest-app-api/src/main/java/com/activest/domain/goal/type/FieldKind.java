package com.activest.domain.goal.type;

/** Tipo de valor de um campo que o usuario informa na meta (a tela monta o campo a partir dele). */
public enum FieldKind {
    /** Reais, 2 casas. */
    MONEY,
    /** Percentual em pontos (6,5 = 6,5%). */
    PERCENT,
    /** Numero com ate 8 casas (cotas). */
    NUMBER,
    INTEGER,
    DATE,
    BOOLEAN,
    /** Uma opcao da lista {@code options}. */
    CHOICE,
    /** Varias opcoes da lista {@code options}. */
    MULTI_CHOICE,
    /**
     * Ativo do catalogo, pelo id: desde a V13 o ticker e unico so por tipo (META e cripto e acao). O servidor grava
     * junto o ticker e o tipo, como rotulo ({@code ticker}, {@code assetType}).
     */
    CATALOG_ASSET,
    TEXT
}
