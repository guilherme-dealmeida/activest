package com.activest.domain.indicator;

/**
 * Janela dos indicadores da tela de Ativos (docs/planos/PLANO_TELA_ATIVOS.md, T8 e D14): os N ultimos exercicios
 * fechados + Atual; {@link #MAX} = toda a serie (acoes desde 2010, FII desde 2017). Constante em ingles, valor da
 * API como os periodos de cotacao e de Indices ({@code 3A}, {@code 5A}, {@code MAX}).
 */
public enum IndicatorWindow {
    Y3("3A", 3), Y5("5A", 5), MAX("MAX", 0);

    private final String label;
    private final int years;

    IndicatorWindow(String label, int years) {
        this.label = label;
        this.years = years;
    }

    public String label() {
        return label;
    }

    /** Exercicios fechados da janela; 0 = todos. */
    public int years() {
        return years;
    }
}
