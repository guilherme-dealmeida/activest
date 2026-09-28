package com.primevest.domain.dividend;

/**
 * Tipos de provento.
 *
 * <p>AMORTIZACAO (FII) e RESTITUICAO_CAPITAL (empresa) sao provento como os
 * outros — aparecem na lista e somam no total recebido — e, alem disso,
 * devolvem parte do capital: reduzem o custo da posicao a partir da data de
 * pagamento (PLANO_BRAPI.md, B13). Ver {@link #returnsCapital()}.</p>
 */
public enum DividendType {
    DIVIDENDO,
    JCP,
    RENDIMENTO,
    AMORTIZACAO,
    RESTITUICAO_CAPITAL;

    /** Devolve capital: alem de contar como provento, reduz o custo da posicao. */
    public boolean returnsCapital() {
        return this == AMORTIZACAO || this == RESTITUICAO_CAPITAL;
    }

    /**
     * Rotulo da B3 (e do FNET) para o nosso tipo. Nulo = rotulo desconhecido:
     * o evento e gravado e logado, mas nao gera provento ate ser mapeado.
     */
    public static DividendType fromSourceLabel(String label) {
        if (label == null) {
            return null;
        }
        String normalized = java.text.Normalizer.normalize(label.trim().toUpperCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return switch (normalized) {
            case "DIVIDENDO", "DIVIDENDOS" -> DIVIDENDO;
            case "JRS CAP PROPRIO", "JUROS SOBRE CAPITAL PROPRIO", "JCP" -> JCP;
            case "RENDIMENTO", "RENDIMENTOS" -> RENDIMENTO;
            // "AMORTIZACAO RF": rotulo da B3 para a amortizacao de cota de fundo (a B3
            // nao manda "AMORTIZACAO" puro); a mesma do FNET, completada, nao duplicada.
            case "AMORTIZACAO", "AMORTIZACOES", "AMORTIZACAO RF" -> AMORTIZACAO;
            case "RESTITUICAO DE CAPITAL", "RESTITUICAO CAPITAL", "REST CAP DIN" -> RESTITUICAO_CAPITAL;
            default -> null;
        };
    }
}
