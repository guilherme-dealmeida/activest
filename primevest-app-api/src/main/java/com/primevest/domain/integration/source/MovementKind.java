package com.primevest.domain.integration.source;

/**
 * O que aconteceu na fonte, ja normalizado pelo conector
 * (PLANO_CRIPTO_BINANCE.md, 6.13).
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
    REWARD
}
