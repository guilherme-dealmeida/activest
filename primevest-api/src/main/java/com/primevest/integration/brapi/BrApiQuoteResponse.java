package com.primevest.integration.brapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO para a resposta de cotação da BrAPI (brapi.dev).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BrApiQuoteResponse {

    private List<BrApiQuote> results;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrApiQuote {
        private String symbol;
        private String shortName;
        private String longName;
        private String currency;

        @JsonProperty("regularMarketPrice")
        private BigDecimal regularMarketPrice;

        @JsonProperty("regularMarketDayHigh")
        private BigDecimal regularMarketDayHigh;

        @JsonProperty("regularMarketDayLow")
        private BigDecimal regularMarketDayLow;

        @JsonProperty("regularMarketOpen")
        private BigDecimal regularMarketOpen;

        @JsonProperty("regularMarketPreviousClose")
        private BigDecimal regularMarketPreviousClose;

        @JsonProperty("regularMarketVolume")
        private Long regularMarketVolume;

        @JsonProperty("regularMarketChange")
        private BigDecimal regularMarketChange;

        @JsonProperty("regularMarketChangePercent")
        private BigDecimal regularMarketChangePercent;

        @JsonProperty("regularMarketTime")
        private String regularMarketTime;

        @JsonProperty("marketCap")
        private Long marketCap;
    }
}
