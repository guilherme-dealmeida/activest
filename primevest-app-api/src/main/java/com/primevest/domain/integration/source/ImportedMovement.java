package com.primevest.domain.integration.source;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Um movimento da fonte no formato comum a todos os conectores
 * (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.2 e 6.13).
 *
 * <p>O conector entrega a operacao do jeito que a pessoa a fez: uma ordem
 * executada em varios pedacos ja chega somada (R1). O pipeline nao sabe de
 * onde o movimento veio — so aplica as regras.</p>
 *
 * @param externalId    id estavel na fonte (R5): 'binance:spot:BTCBRL:&lt;orderId&gt;'
 * @param kind          o que aconteceu
 * @param asset         codigo do ativo movimentado na fonte ('BTC')
 * @param side          entrou ou saiu; nulo nos depositos em reais
 * @param grossQuantity quantidade bruta negociada, antes da taxa
 * @param quoteAsset    moeda em que a operacao foi paga ('BRL', 'USDT', 'BTC')
 * @param quoteAmount   valor pago ou recebido na moeda de cotacao, SEM a taxa
 *                      quando ela e cobrada na propria moeda de cotacao (e o
 *                      {@code quoteQty} da Binance: quantidade x preco)
 * @param feeAmount     taxa cobrada; zero ou nulo quando embutida no preco
 * @param feeAsset      moeda da taxa ('BTC', 'BRL', 'BNB'); nulo sem taxa
 * @param executedAt    instante na fonte
 * @param raw           o movimento como veio da fonte (JSON), para auditoria
 */
public record ImportedMovement(
    String externalId,
    MovementKind kind,
    String asset,
    MovementSide side,
    BigDecimal grossQuantity,
    String quoteAsset,
    BigDecimal quoteAmount,
    BigDecimal feeAmount,
    String feeAsset,
    Instant executedAt,
    String raw
) {
    public ImportedMovement {
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(executedAt, "executedAt");
        raw = raw == null ? "{}" : raw;
    }

    public boolean hasFee() {
        return feeAmount != null && feeAmount.signum() != 0;
    }
}
