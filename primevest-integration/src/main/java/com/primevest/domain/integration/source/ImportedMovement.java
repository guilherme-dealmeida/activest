package com.primevest.domain.integration.source;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Um movimento da fonte no formato comum a todos os conectores
 * (docs/planos/PLANO_CRIPTO_BINANCE.md, 6.2 e 6.13; docs/planos/PLANO_INTEGRACAO_B3.md, G5).
 *
 * <p>O conector entrega a operacao do jeito que a pessoa a fez: uma ordem
 * executada em varios pedacos ja chega somada (R1, B1). O pipeline nao sabe de
 * onde o movimento veio — so aplica as regras da fonte.</p>
 *
 * @param externalId    id estavel na fonte (R5, B5): 'binance:spot:BTCBRL:&lt;orderId&gt;',
 *                      'b3:negociacao:2025-01-02:PETR4:C'
 * @param kind          o que aconteceu
 * @param asset         codigo do ativo movimentado na fonte ('BTC', 'PETR4')
 * @param side          entrou ou saiu; nulo nos depositos em reais
 * @param grossQuantity quantidade bruta negociada, antes da taxa
 * @param quoteAsset    moeda em que a operacao foi paga ('BRL', 'USDT', 'BTC')
 * @param quoteAmount   valor pago ou recebido na moeda de cotacao, SEM a taxa
 *                      quando ela e cobrada na propria moeda de cotacao (e o
 *                      {@code quoteQty} da Binance: quantidade x preco)
 * @param feeAmount     taxa cobrada; zero ou nulo quando embutida no preco ou nao informada
 * @param feeAsset      moeda da taxa ('BTC', 'BRL', 'BNB'); nulo sem taxa
 * @param executedAt    instante na fonte. Na fonte que so informa a data (B3), o inicio
 *                      do dia no fuso de Brasilia
 * @param raw           o movimento como veio da fonte (JSON), para auditoria
 * @param market        mercado na fonte, quando ela informa ('Mercado à Vista', 'Mercado Fracionário'); nos
 *                      lancamentos da Movimentacao da B3, o tipo que a B3 deu ('Dividendo', 'Atualização')
 * @param participant   instituicao na fonte (corretora), quando ela informa
 * @param tradeDate     data de negocio, quando a fonte entrega a data e nao o instante (B3, B2)
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
    String raw,
    String market,
    String participant,
    LocalDate tradeDate
) {
    public ImportedMovement {
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(executedAt, "executedAt");
        raw = raw == null ? "{}" : raw;
    }

    /** Movimento de fonte que entrega o instante e nao informa mercado nem instituicao (cripto). */
    public ImportedMovement(String externalId, MovementKind kind, String asset, MovementSide side,
                            BigDecimal grossQuantity, String quoteAsset, BigDecimal quoteAmount,
                            BigDecimal feeAmount, String feeAsset, Instant executedAt, String raw) {
        this(externalId, kind, asset, side, grossQuantity, quoteAsset, quoteAmount, feeAmount, feeAsset,
            executedAt, raw, null, null, null);
    }

    public boolean hasFee() {
        return feeAmount != null && feeAmount.signum() != 0;
    }
}
