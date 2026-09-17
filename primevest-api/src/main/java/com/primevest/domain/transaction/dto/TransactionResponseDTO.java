package com.primevest.domain.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.transaction.TransactionType;

/**
 * Transação como a tela a lê.
 *
 * <p>assetTypeId existe porque a edição precisa reabrir o formulário no mesmo
 * estado em que ele foi preenchido, e o tipo de ativo comanda esse formulário:
 * decide o que o autocomplete busca e se o quadro do título aparece. Com só o
 * nome descritivo em mãos, a tela teria que adivinhar o tipo casando strings.</p>
 *
 * <p>Os campos do título vêm do papel (fixed_income_notes) e são nulos na renda
 * variável. Sem eles, abrir a edição de um CDB mostraria o quadro vazio — e
 * salvar por cima apagaria o que estava lá.</p>
 */
public record TransactionResponseDTO(
    UUID id,
    String ticker,
    String assetName,
    Long assetTypeId,
    String assetTypeName,
    TransactionType type,
    BigDecimal shares,
    BigDecimal pricePerShare,
    BigDecimal totalAmount,
    BigDecimal brokerageFee,
    LocalDate transactionDate,
    LocalDateTime createdAt,

    // ----- do título (renda fixa) -----
    UUID noteId,
    String issuer,
    LocalDate maturityDate,
    RateType rateType,
    Indexer indexer,
    BigDecimal indexerRate
) {}
