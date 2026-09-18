package com.primevest.domain.transaction.dto;

import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transação como as telas a leem.
 *
 * <p>{@code portfolioAssetId} existe para o filtro "Listar" da carteira: o
 * ticker não identifica a posição desde que ela passou a ser por título — três
 * CDBs são três posições, todas "CDB".</p>
 *
 * <p>Os campos de grupo descrevem a venda do usuário quando ela foi dividida
 * por título (Tesouro Direto). {@code groupShares} e {@code groupBrokerageFee}
 * são o que ele digitou; {@code shares} e {@code brokerageFee} são a parte
 * desta linha. A edição reabre o formulário com os valores do GRUPO, porque é
 * a venda inteira que se edita.</p>
 */
public record TransactionResponseDTO(
    UUID id,
    UUID portfolioAssetId,
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

    // ----- venda dividida por titulo -----
    UUID saleGroupId,
    /** Posição desta linha dentro do grupo, a partir de 1. Nulo fora de grupo. */
    Integer groupPart,
    /** Quantas linhas o grupo tem. Nulo fora de grupo. */
    Integer groupParts,
    /** Quantidade e taxas da venda inteira. Iguais aos da linha fora de grupo. */
    BigDecimal groupShares,
    BigDecimal groupBrokerageFee,

    /** Venda de Renda Fixa marcada como liquidação total. */
    boolean isFullSettlement,

    // ----- dados do titulo -----
    UUID noteId,
    String issuer,
    LocalDate maturityDate,
    RateType rateType,
    Indexer indexer,
    BigDecimal indexerRate
) {}
