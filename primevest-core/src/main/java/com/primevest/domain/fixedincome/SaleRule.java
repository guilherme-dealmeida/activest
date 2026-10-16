package com.primevest.domain.fixedincome;

/**
 * De quais aportes (lotes) um resgate parcial sai (docs/planos/PLANO_VENDA_RENDA_FIXA.md, R5).
 *
 * <p>Nao e regra legal: cada instituicao faz de um jeito. Provado ate aqui: Tesouro Direto e PEPS; o Nubank
 * (RDB/caixinha) e UEPS, conferido no centavo contra o extrato diario (10/10/2026). O padrao e PROPORCIONAL porque,
 * dentro de um titulo, ele fica sempre entre PEPS e UEPS — o erro maximo e menor que o de escolher a regra errada.
 * Onde a regra mora (a distribuidora) vem com o escopo de emissoras (D1); ate la a renda fixa usa o padrao.</p>
 */
public enum SaleRule {
    /** Cada lote cede a mesma fracao do seu saldo bruto. */
    PROPORCIONAL,
    /** Ultimo que entra, primeiro que sai: o aporte mais novo primeiro. */
    UEPS,
    /** Primeiro que entra, primeiro que sai: o aporte mais antigo primeiro. */
    PEPS;

    public static final SaleRule DEFAULT = PROPORCIONAL;
}
