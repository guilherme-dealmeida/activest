package com.primevest.domain.portfolio.dto;

import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Linha da carteira.
 *
 * <p>issuer e maturityDate vêm do papel, e existem porque a carteira passou a
 * repetir ticker: duas aplicações em CDB são duas linhas, e sem o emissor e o
 * vencimento elas ficariam idênticas na tela. Nulos para renda variável.</p>
 *
 * <p>rateType, indexer e indexerRate são a remuneração contratada na nota
 * ("110% do CDI", "IPCA + 6%"). Também nulos para renda variável.</p>
 */
public record PortfolioAssetDetailDTO(
    UUID id,
    String ticker,
    String assetName,
    String typeName,
    String categoryName,
    UUID noteId,
    String issuer,
    LocalDate maturityDate,
    RateType rateType,
    Indexer indexer,
    BigDecimal indexerRate,
    BigDecimal totalShares,
    BigDecimal averagePrice,
    BigDecimal totalInvested,
    BigDecimal currentValue,
    BigDecimal currentPrice,
    BigDecimal totalDividends,
    BigDecimal valorization,
    BigDecimal returnPercentage,
    boolean isReservation
) {}
