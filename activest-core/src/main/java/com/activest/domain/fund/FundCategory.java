package com.activest.domain.fund;

import java.util.Locale;

/**
 * Categoria do fundo (PLANO_FUNDOS_INVESTIMENTO.md, D18 e C3): o 1º nível da classificação ANBIMA; sem ANBIMA útil, a
 * classificação CVM; sem nenhuma, Outros. Os nomes são os das linhas de {@code asset_categories} do tipo
 * FUNDO_INVESTIMENTO (V12). Regra pura.
 */
public final class FundCategory {

    public static final String MULTIMERCADO = "Multimercado";
    public static final String RENDA_FIXA = "Renda Fixa";
    public static final String PREVIDENCIA = "Previdência";
    public static final String ACOES = "Ações";
    public static final String CAMBIAL = "Cambial";
    public static final String OUTROS = "Outros";

    private FundCategory() {
    }

    public static String of(String anbima, String cvm) {
        String a = FundKeys.normalize(anbima);
        if (a.startsWith("MULTIMERCADO")) {
            return MULTIMERCADO;
        }
        if (a.startsWith("RENDA FIXA")) {
            return RENDA_FIXA;
        }
        if (a.startsWith("PREVIDENCIA")) {
            return PREVIDENCIA;
        }
        if (a.startsWith("ACOES") || a.startsWith("FECHADOS DE ACOES") || a.startsWith("FUNDOS DE MONO ACAO")) {
            return ACOES;
        }
        if (a.startsWith("CAMBIAL")) {
            return CAMBIAL;
        }
        String c = FundKeys.normalize(cvm).toUpperCase(Locale.ROOT);
        return switch (c) {
            case "MULTIMERCADO" -> MULTIMERCADO;
            case "RENDA FIXA", "REFERENCIADO", "CURTO PRAZO" -> RENDA_FIXA;
            case "ACOES", "FMP-FGTS" -> ACOES;
            case "CAMBIAL" -> CAMBIAL;
            default -> OUTROS;
        };
    }
}
