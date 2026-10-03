package com.primevest.domain.integration.source;

/**
 * O que aconteceu na fonte, ja normalizado pelo conector
 * (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.13).
 *
 * <p>So TRADE e CONVERT viram transacao na primeira versao. Os demais existem
 * para o conector nao ter de escolher o que descartar: o pipeline decide, e o
 * que ele nao importa aparece na pre-visualizacao com o motivo.</p>
 */
public enum MovementKind {
    /** Compra ou venda executada (livro de ofertas, "Comprar Cripto" com real). */
    TRADE,
    /** Troca direta entre moedas (Binance Convert). Taxa embutida no preco (R4). */
    CONVERT,
    /** Deposito em reais (PIX). Nao e transacao: serve a conciliacao (4.6). */
    FIAT_DEPOSIT,
    /** Cripto recebida de fora. Transferencia, fora da primeira versao (D6). */
    CRYPTO_DEPOSIT,
    /** Cripto enviada para fora. Transferencia, fora da primeira versao (D6). */
    CRYPTO_WITHDRAW,
    /** Rendimento de Earn/staking/airdrop. Fora da primeira versao. */
    REWARD,

    // --- B3 (docs/planos/PLANO_INTEGRACAO_B3.md, 6.6) -------------------------------------

    /** Negocios do dia, ativo e lado somados (B1). O unico da B3 que vira transacao. */
    EQUITY_TRADE,
    /** "Transferência - Liquidação": a liquidacao D+2 do negocio. Conferencia (B7). */
    SETTLEMENT,
    /** Desdobramento, grupamento, bonificacao, "Atualização": o catalogo ja aplica (B8). */
    STOCK_EVENT,
    /** Dividendo, JCP, rendimento, amortizacao — recebido ou provisionado. Conferencia (B8). */
    CASH_EVENT,
    /** Direito de subscricao e cessao de direitos (B9). */
    RIGHTS,
    /** Transferencia de custodia entre corretoras (B10). */
    CUSTODY_TRANSFER,
    /** Emprestimo de ativos (B10). */
    LENDING,
    /** Movimento de Tesouro Direto ou renda fixa (F7). */
    FIXED_INCOME,
    /** Lancamento da fonte sem regra no PrimeVest: aparece como nao importado, com o tipo que a fonte deu. */
    OTHER
}
