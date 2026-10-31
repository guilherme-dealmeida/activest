package com.activest.domain.integration.pipeline;

import com.activest.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A transacao que um movimento vira, pronta para o TransactionService (F4).
 *
 * @param shares             quantidade liquida da taxa quando ela e cobrada no
 *                           proprio ativo (R2)
 * @param pricePerShare      valor na moeda de cotacao / quantidade bruta, 8 casas; no ativo do exterior, na moeda
 *                           dele (USD) — o TransactionService converte pela PTAX venda da data (D10)
 * @param brokerageFee       taxa na mesma moeda do preco, 2 casas
 * @param transactionDate    data de negocio no fuso de Brasilia (R3)
 * @param executedAt         instante na fonte, em UTC
 * @param grossQuantity      quantidade bruta, antes da taxa (base da comparacao com lancamentos manuais, 6.6)
 * @param sourceValue        o valor em reais que a fonte informou
 * @param roundingDifference quanto o Activest vai registrar a mais (+) ou a
 *                           menos (-) que a fonte, por arredondamento (4.5)
 * @param participant        instituicao na fonte (corretora), quando ela informa: a distribuidora do titulo do
 *                           Tesouro importado (docs/planos/PLANO_EMISSORAS_RENDA_FIXA.md, L21)
 * @param currencyCode       moeda do preco e da taxa; nulo = moeda base (reais). Ativo do exterior: a dele
 *                           (docs/planos/PLANO_INTEGRACOES.md, 10.3)
 */
public record ProposedTransaction(
    Long assetId,
    String ticker,
    TransactionType type,
    BigDecimal shares,
    BigDecimal pricePerShare,
    BigDecimal brokerageFee,
    LocalDate transactionDate,
    LocalDateTime executedAt,
    String externalId,
    BigDecimal grossQuantity,
    BigDecimal sourceValue,
    BigDecimal roundingDifference,
    String participant,
    String currencyCode
) {
    /** Com a instituicao da fonte, em reais. */
    public ProposedTransaction(Long assetId, String ticker, TransactionType type, BigDecimal shares,
                               BigDecimal pricePerShare, BigDecimal brokerageFee, LocalDate transactionDate,
                               LocalDateTime executedAt, String externalId, BigDecimal grossQuantity,
                               BigDecimal sourceValue, BigDecimal roundingDifference, String participant) {
        this(assetId, ticker, type, shares, pricePerShare, brokerageFee, transactionDate, executedAt, externalId,
            grossQuantity, sourceValue, roundingDifference, participant, null);
    }

    /** Sem a instituicao da fonte. */
    public ProposedTransaction(Long assetId, String ticker, TransactionType type, BigDecimal shares,
                               BigDecimal pricePerShare, BigDecimal brokerageFee, LocalDate transactionDate,
                               LocalDateTime executedAt, String externalId, BigDecimal grossQuantity,
                               BigDecimal sourceValue, BigDecimal roundingDifference) {
        this(assetId, ticker, type, shares, pricePerShare, brokerageFee, transactionDate, executedAt, externalId,
            grossQuantity, sourceValue, roundingDifference, null);
    }

    /** O total que o TransactionService vai gravar: quantidade x preco, 2 casas. */
    public BigDecimal totalAmount() {
        return shares.multiply(pricePerShare).setScale(2, RoundingMode.HALF_UP);
    }
}
