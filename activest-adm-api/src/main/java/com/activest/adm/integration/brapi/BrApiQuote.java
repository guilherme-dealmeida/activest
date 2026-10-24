package com.activest.adm.integration.brapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Cotacao de um ativo — o {@code results[0].data} de
 * {@code GET /api/v2/stocks/quote?symbols=X}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BrApiQuote(
    String shortName,
    String longName,
    String currency,
    BigDecimal regularMarketPrice,
    BigDecimal regularMarketDayHigh,
    BigDecimal regularMarketDayLow,
    BigDecimal regularMarketOpen,
    BigDecimal regularMarketPreviousClose,
    Long regularMarketVolume,
    BigDecimal regularMarketChange,
    BigDecimal regularMarketChangePercent,
    String regularMarketTime,
    BigDecimal marketCap,
    String logourl
) {
}
